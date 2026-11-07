/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmLicenseOcrService.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.util.SensitiveDataUtils;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmLicenseOcrTaskDto;
import org.hiylo.scrm.entity.ScrmLicenseOcrTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.feign.OcrApiException;
import org.hiylo.scrm.feign.OcrRecognizer;
import org.hiylo.scrm.feign.dto.ocr.OcrField;
import org.hiylo.scrm.feign.dto.ocr.OcrRecognizeResponse;
import org.hiylo.scrm.repository.ScrmLicenseOcrTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.criteria.Predicate;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 证照 OCR 识别任务服务（模块② 核心）。
 * <p>
 * 承载「证照上传 → OCR 识别 → 结果落库 → 低置信度字段人工复核」全链路:
 * </p>
 * <ul>
 *   <li><b>上传识别</b>: multipart 证照经 {@link OcrRecognizeClient} 调 OCR 服务
 *       {@code POST /api/ocr/recognize}, 原图先入对象存储（影像域, 契约 §7.4）</li>
 *   <li><b>结果落库</b>: fields/verification/lines/全文与复核标记落
 *       {@code scrm_license_ocr_task}, 敏感字段（身份证号/银行账号等）随 JSON 列
 *       整列 AES-GCM 加密（{@code EncryptedStringConverter}）, 日志全程脱敏</li>
 *   <li><b>低置信度复核</b>: 必填字段 {@code status != ok} 或顶层
 *       {@code verification.ok=false} → {@code reviewStatus=REQUIRED} 并记录待复核
 *       字段清单; 复核通过 → {@code REVIEWED}（复核语义对齐 T07 契约 §7 与
 *       QualityInspection 的人工复核约定, 复核通过才允许写业务档案域）</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmLicenseOcrService {

    /** 任务状态: 待识别 */
    public static final String STATUS_PENDING = "PENDING";

    /** 任务状态: 识别成功且无需复核 */
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";

    /** 任务状态: 识别成功但存在待核对项（需人工复核） */
    public static final String STATUS_PARTIAL = "PARTIAL";

    /** 任务状态: 识别失败（调用失败或引擎侧 ok=false） */
    public static final String STATUS_FAILED = "FAILED";

    /** 复核状态: 无需复核 */
    public static final String REVIEW_NONE = "NONE";

    /** 复核状态: 需人工复核（低置信必填 / 校验不过 / verification.ok=false） */
    public static final String REVIEW_REQUIRED = "REQUIRED";

    /** 复核状态: 已复核通过 */
    public static final String REVIEW_REVIEWED = "REVIEWED";

    /** 合法识别类型（对齐 T07 契约 §2.2） */
    private static final List<String> VALID_TYPES = List.of(
            "license", "idcard", "account_license", "general");

    /** 对象存储（影像域, 原图/PDF 存证） */
    private final org.hiylo.scrm.storage.ObjectStorage objectStorage;

    /** 对象存储配置 */
    private final org.hiylo.scrm.storage.StorageProperties storageProperties;

    /** OCR 识别客户端 */
    private final OcrRecognizer ocrRecognizeClient;

    /** 证照 OCR 任务数据访问层 */
    private final ScrmLicenseOcrTaskRepository taskRepository;

    /** 公司主体归属解析器（业务数据自动挂公司） */
    private final CompanyResolver companyResolver;

    /** JSON 序列化/反序列化 */
    private final ObjectMapper objectMapper;

    /** 财税数据范围（会计只看到自己负责主体的证照识别任务） */
    private final org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    /** objectKey 前缀（证照影像命名空间, 与 ConversationMedia 区分） */
    private static final String OBJECT_KEY_PREFIX = "scrm/license/";

    /** 证照影像 objectKey 年月目录格式 */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    // ============================================================
    // 上传 → OCR → 落库
    // ============================================================

    /**
     * 证照上传并识别（同步链路: 上传 → OCR 调用 → 落库 → 复核标记）。
     * <p>
     * OCR 服务不可用/识别失败不抛业务异常中断, 而是落 FAILED 任务并携带脱敏错误,
     * 由前端提示人工处理（弱依赖, 不阻断业务）。
     * </p>
     *
     * @param file   证照图片/PDF
     * @param dto    任务参数（licenseType 必填; customerId/businessId/side 可空）
     * @return 落库后的任务
     * @throws ScrmException 参数非法 / 文件读取失败
     */
    @Transactional
    public ScrmLicenseOcrTaskEntity uploadAndRecognize(MultipartFile file, ScrmLicenseOcrTaskDto dto)
            throws ScrmException {
        validateCreate(dto);
        ScrmLicenseOcrTaskEntity entity = new ScrmLicenseOcrTaskEntity();
        entity.setLicenseType(dto.getLicenseType());
        entity.setSide(dto.getSide());
        entity.setCustomerId(dto.getCustomerId());
        // 业务数据自动挂公司（联系人或显式公司 ID）
        entity.setCompanyId(companyResolver.resolve(dto.getCompanyId(), dto.getCustomerId()));
        entity.setBusinessId(dto.getBusinessId());
        entity.setOwnerAccountId(parseUserId());
        entity.setFileName(file != null ? file.getOriginalFilename() : null);
        entity.setCreatedBy(UserContext.getUsername());
        entity.setTaskStatus(STATUS_PENDING);
        entity.setReviewStatus(REVIEW_NONE);
        try {
            // 1) 原图先入影像域（OCR 服务无状态不留图, deploy.md §7 红线）
            entity.setObjectKey(uploadToStorage(file));
            // 2) 调 OCR 识别
            String requestId = "license-" + entity.getId() + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            OcrRecognizeResponse response = ocrRecognizeClient.recognize(
                    file, dto.getLicenseType(), requestId);
            // 3) 结果落库 + 复核标记
            applyRecognizeResult(entity, response);
        } catch (OcrApiException e) {
            log.warn("[证照OCR] 识别调用失败: type={}, fileName={}, err={}",
                    dto.getLicenseType(), safeFileName(entity.getFileName()),
                    e.getMessage());
            entity.setTaskStatus(STATUS_FAILED);
            entity.setReviewStatus(REVIEW_REQUIRED);
            entity.setErrorMessage(truncate(e.getMessage(), 1000));
        } catch (Exception e) {
            log.warn("[证照OCR] 识别流程异常: type={}, fileName={}, err={}",
                    dto.getLicenseType(), safeFileName(entity.getFileName()),
                    e.getMessage());
            entity.setTaskStatus(STATUS_FAILED);
            entity.setReviewStatus(REVIEW_REQUIRED);
            entity.setErrorMessage(truncate("识别流程异常: " + e.getMessage(), 1000));
        }
        entity = taskRepository.save(entity);
        log.info("[证照OCR] 识别任务落库: id={}, type={}, status={}, review={}",
                entity.getId(), entity.getLicenseType(), entity.getTaskStatus(),
                entity.getReviewStatus());
        return entity;
    }

    /**
     * 将 OCR 识别响应应用到实体（字段/校验/全文落库 + 复核标记）。
     *
     * @param entity   任务实体
     * @param response OCR 统一响应
     */
    private void applyRecognizeResult(ScrmLicenseOcrTaskEntity entity, OcrRecognizeResponse response) {
        entity.setRequestId(response.getRequestId());
        // mode/engine/docType 均来自识别引擎（本地模型 id 或外部服务返回），长度不受本服务控制。
        // 列宽有限，超长时必须收敛，否则整条任务因 DataException 落库失败（表现为 500）。
        entity.setMode(truncateForColumn(response.getMode(), 20, "mode"));
        entity.setEngine(truncateForColumn(response.getEngine(), 100, "engine"));
        entity.setDocType(truncateForColumn(response.getDocType(), 30, "docType"));
        entity.setOverallOk(response.getOk());
        entity.setFieldsJson(toJsonSafe(response.getFields()));
        entity.setVerificationJson(toJsonSafe(response.getVerification()));
        entity.setLinesJson(toJsonSafe(response.getLines()));
        entity.setRawText(response.getText());
        entity.setWarningsJson(toJsonSafe(response.getWarnings()));

        if (!response.isSuccess()) {
            // 引擎侧失败（ok=false + error）: 落 FAILED, 转人工
            entity.setTaskStatus(STATUS_FAILED);
            entity.setReviewStatus(REVIEW_REQUIRED);
            entity.setErrorMessage(truncate(response.getError(), 1000));
            return;
        }
        // 低置信度复核判定（T07 契约 §7: 必填 status!=ok 或 verification.ok=false → 复核）
        List<Map<String, Object>> reviewFields = collectReviewFields(response);
        boolean verificationFailed = response.getVerification() != null
                && Boolean.FALSE.equals(response.getVerification().getOk());
        if (reviewFields.isEmpty() && !verificationFailed) {
            entity.setTaskStatus(STATUS_SUCCEEDED);
            entity.setReviewStatus(REVIEW_NONE);
            entity.setReviewFieldsJson(null);
        } else {
            entity.setTaskStatus(STATUS_PARTIAL);
            entity.setReviewStatus(REVIEW_REQUIRED);
            entity.setReviewFieldsJson(toJsonSafe(reviewFields));
        }
    }

    /**
     * 聚合待复核字段（必填 且 status != ok; 或任意字段 invalid）。
     * <p>
     * 返回 [{key, name, status, problems}] 清单, 供复核队列展示与人工逐项核对。
     * </p>
     *
     * @param response OCR 统一响应
     * @return 待复核字段清单（可空列表）
     */
    private List<Map<String, Object>> collectReviewFields(OcrRecognizeResponse response) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (response.getFields() == null) {
            return result;
        }
        for (OcrField field : response.getFields()) {
            boolean required = Boolean.TRUE.equals(field.getRequired());
            String status = field.getStatus();
            boolean statusNotOk = status != null && !"ok".equals(status);
            boolean invalid = "invalid".equals(status);
            if ((required && statusNotOk) || invalid) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", field.getKey());
                item.put("name", field.getName());
                item.put("status", status);
                item.put("problems", field.getProblems() == null ? List.of() : field.getProblems());
                result.add(item);
            }
        }
        return result;
    }

    // ============================================================
    // 查询
    // ============================================================

    /**
     * 查询任务详情（含识别结果）。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmLicenseOcrTaskEntity get(Long id) throws ScrmException {
        return taskRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("证照识别任务不存在: id=" + id));
    }

    /**
     * 分页查询任务, 支持按状态/类型/复核状态/关键字过滤。
     *
     * @param licenseType 识别类型过滤（可空）
     * @param taskStatus  任务状态过滤（可空）
     * @param reviewStatus 复核状态过滤（可空）
     * @param ownerAccountId 归属账号过滤（可空）
     * @param pageable    分页参数
     * @return 任务分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmLicenseOcrTaskEntity> list(String licenseType, String taskStatus,
                                               String reviewStatus, Long ownerAccountId,
                                               Pageable pageable) {
        Specification<ScrmLicenseOcrTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (licenseType != null && !licenseType.isBlank()) {
                predicates.add(cb.equal(root.get("licenseType"), licenseType));
            }
            if (taskStatus != null && !taskStatus.isBlank()) {
                predicates.add(cb.equal(root.get("taskStatus"), taskStatus));
            }
            if (reviewStatus != null && !reviewStatus.isBlank()) {
                predicates.add(cb.equal(root.get("reviewStatus"), reviewStatus));
            }
            if (ownerAccountId != null) {
                predicates.add(cb.equal(root.get("ownerAccountId"), ownerAccountId));
            }
            // 财税数据范围：会计只能看到自己负责主体的证照任务。
            // company_id 为空的历史任务对会计不可见（无法判定归属，按不可见处理）。
            List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
            if (accessibleCompanyIds != null) {
                if (accessibleCompanyIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(accessibleCompanyIds));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 查询待复核队列（reviewStatus=REQUIRED）。
     *
     * @param pageable 分页参数
     * @return 待复核任务分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmLicenseOcrTaskEntity> listPendingReview(Pageable pageable) {
        // 待复核队列同样要过会计数据范围，否则它是列表过滤旁边的一个绕过口。
        // 走 Specification 而不是 findByReviewStatus，才能把 company_id 白名单下推到 SQL。
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        return taskRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("reviewStatus"), REVIEW_REQUIRED));
            if (accessibleCompanyIds != null) {
                if (accessibleCompanyIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(accessibleCompanyIds));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    /**
     * 校验任务归属主体是否在会计的数据范围内（详情/复核类端点用）。
     * <p>
     * 列表过滤挡不住「直接拼任务 ID」的访问，因此按 ID 取数的端点必须单独校验。
     * 任务的 {@code company_id} 为空时无法判定归属，对会计按不可见处理（fail-closed）。
     * </p>
     *
     * @param taskId 任务 ID
     * @throws ScrmException 任务不存在或无权访问
     */
    @Transactional(readOnly = true)
    public void assertTaskAccessible(Long taskId) throws ScrmException {
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        if (accessibleCompanyIds == null) {
            return;
        }
        ScrmLicenseOcrTaskEntity task = get(taskId);
        Long companyId = task.getCompanyId();
        if (companyId == null || !accessibleCompanyIds.contains(companyId)) {
            throw ScrmException.forbidden("无权访问该证照识别任务: " + taskId);
        }
    }

    // ============================================================
    // 低置信度字段人工复核（复用 QualityInspection 语义）
    // ============================================================

    /**
     * 人工复核低置信度字段。
     * <p>
     * 复核通过（复核人为当前操作人）→ {@code reviewStatus=REVIEWED},
     * 记录复核人/时间/备注; 只有已复核通过的任务才允许后续写入业务档案域
     * （契约 §7: 复核通过才写 Customer360/Contract/Invoice）。
     * </p>
     *
     * @param id     任务 ID
     * @param passed 是否复核通过（false 保持 REQUIRED 并记录备注）
     * @param remark 复核备注（可空）
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许复核
     */
    @Transactional
    public ScrmLicenseOcrTaskEntity review(Long id, boolean passed, String remark) throws ScrmException {
        ScrmLicenseOcrTaskEntity entity = get(id);
        if (!REVIEW_REQUIRED.equals(entity.getReviewStatus())) {
            throw ScrmException.badRequest("该任务不处于待复核状态: id=" + id
                    + ", reviewStatus=" + entity.getReviewStatus());
        }
        if (passed) {
            entity.setReviewStatus(REVIEW_REVIEWED);
        }
        entity.setReviewRemark(truncate(remark, 1000));
        entity.setReviewer(UserContext.getUsername());
        entity.setReviewedAt(LocalDateTime.now());
        entity = taskRepository.save(entity);
        log.info("[证照OCR] 复核完成: id={}, passed={}, reviewer={}",
                id, passed, UserContext.getUsername());
        return entity;
    }

    // ============================================================
    // DTO 转换与脱敏
    // ============================================================

    /**
     * 实体转 DTO（识别字段与全文按敏感字段脱敏, 防止回显客户侧）。
     *
     * @param entity 任务实体
     * @return DTO
     */
    public ScrmLicenseOcrTaskDto toDto(ScrmLicenseOcrTaskEntity entity) {
        ScrmLicenseOcrTaskDto dto = new ScrmLicenseOcrTaskDto();
        dto.setId(entity.getId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCompanyId(entity.getCompanyId());
        dto.setBusinessId(entity.getBusinessId());
        dto.setOwnerAccountId(entity.getOwnerAccountId());
        dto.setLicenseType(entity.getLicenseType());
        dto.setSide(entity.getSide());
        dto.setFileName(entity.getFileName());
        dto.setObjectKey(entity.getObjectKey());
        dto.setRequestId(entity.getRequestId());
        dto.setMode(entity.getMode());
        dto.setEngine(entity.getEngine());
        dto.setDocType(entity.getDocType());
        dto.setTaskStatus(entity.getTaskStatus());
        dto.setOverallOk(entity.getOverallOk());
        dto.setFields(desensitizeFields(entity.getFieldsJson()));
        dto.setVerification(parseJsonMap(entity.getVerificationJson()));
        dto.setRawText(entity.getRawText());
        dto.setReviewStatus(entity.getReviewStatus());
        dto.setReviewFields(parseReviewFields(entity.getReviewFieldsJson()));
        dto.setReviewRemark(entity.getReviewRemark());
        dto.setReviewer(entity.getReviewer());
        dto.setReviewedAt(entity.getReviewedAt());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    /**
     * 识别字段列表脱敏（身份证号/银行账号/手机号等敏感值打码后再展示）。
     *
     * @param fieldsJson 字段 JSON
     * @return 脱敏后的字段列表
     */
    private List<Map<String, Object>> desensitizeFields(String fieldsJson) {
        List<OcrField> fields;
        try {
            fields = objectMapper.readValue(fieldsJson, new TypeReference<List<OcrField>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        if (fields == null) {
            return result;
        }
        for (OcrField field : fields) {
            if (field == null || field.getKey() == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", field.getKey());
            item.put("name", field.getName());
            item.put("value", maskSensitive(field.getKey(), field.getValue()));
            item.put("confidence", field.getConfidence());
            item.put("verified", field.getVerified());
            item.put("source", field.getSource());
            item.put("required", field.getRequired());
            item.put("status", field.getStatus());
            result.add(item);
        }
        return result;
    }

    /**
     * 按字段键对识别值脱敏（敏感键名打码, 其余原样）。
     *
     * @param key   字段键
     * @param value 识别值
     * @return 脱敏值（null 原样返回）
     */
    private String maskSensitive(String key, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String k = key == null ? "" : key.toLowerCase();
        if (k.contains("id_card_no") || k.contains("idcard")
                || k.contains("身份证号") || k.contains("certificate_no")) {
            return SensitiveDataUtils.maskIdCard(value);
        }
        if (k.contains("account_no") || k.contains("bank_account")
                || k.contains("账号") || k.contains("card_no")) {
            return SensitiveDataUtils.maskBankCard(value);
        }
        if (k.contains("phone") || k.contains("mobile")) {
            return SensitiveDataUtils.maskPhone(value);
        }
        if (k.contains("name") || k.contentEquals("account_name")
                || k.contentEquals("legal_representative")) {
            return SensitiveDataUtils.maskName(value);
        }
        return value;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 证照原图/PDF 入影像域（对象存储）。
     *
     * @param file 上传文件
     * @return objectKey
     * @throws ScrmException 文件读取/上传失败
     */
    private String uploadToStorage(MultipartFile file) throws ScrmException {
        if (file == null || file.isEmpty()) {
            throw ScrmException.badRequest("证照文件不能为空");
        }
        String objectKey = OBJECT_KEY_PREFIX + LocalDateTime.now().format(MONTH_FORMATTER)
                + "/" + UUID.randomUUID().toString().replace("-", "")
                + "_" + sanitizeFileName(file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            objectStorage.upload(storageProperties.getBucket(), objectKey,
                    inputStream, file.getSize(), file.getContentType());
            log.info("[证照OCR] 证照影像已存: bucket={}, objectKey={}, size={}",
                    storageProperties.getBucket(), objectKey, file.getSize());
            return objectKey;
        } catch (IOException e) {
            throw new ScrmException("SCRM_LICENSE_OCR_UPLOAD_FAILED",
                    "读取证照文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 净化客户端上传文件名（防路径穿越与响应头注入, 同会话媒体服务约定）。
     *
     * @param fileName 原始文件名
     * @return 净化后的文件名
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "license";
        }
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("..", "").replace("\"", "").replace("\r", "").replace("\n", "").trim();
        return name.isEmpty() ? "license" : name;
    }

    /**
     * 创建参数校验。
     *
     * @param dto 任务参数
     * @throws ScrmException 参数非法
     */
    private void validateCreate(ScrmLicenseOcrTaskDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("证照识别任务参数不能为空");
        }
        String type = dto.getLicenseType();
        if (type == null || type.isBlank()) {
            throw ScrmException.badRequest("识别类型不能为空");
        }
        if (!VALID_TYPES.contains(type)) {
            throw ScrmException.badRequest("识别类型仅支持 license/idcard/account_license/general");
        }
        if ("idcard".equals(type)
                && dto.getSide() != null && !dto.getSide().isBlank()
                && !"front".equals(dto.getSide()) && !"back".equals(dto.getSide())) {
            throw ScrmException.badRequest("身份证翻面仅支持 front/back");
        }
    }

    /**
     * JSON 安全序列化（null → null）。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     */
    private String toJsonSafe(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[证照OCR] 结果 JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 JSON 为 Map（null/非法 → null）。
     *
     * @param json JSON 字符串
     * @return Map
     */
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析待复核字段清单 JSON。
     *
     * @param json JSON 字符串
     * @return 字段清单（null/非法 → 空列表）
     */
    private List<Map<String, Object>> parseReviewFields(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 从请求上下文取当前用户 ID 作为默认归属账号。
     *
     * @return 用户 ID（未登录调度上下文时为 null）
     */
    private Long parseUserId() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 日志用文件名脱敏（长文件截断, 不含路径）。
     *
     * @param fileName 原始文件名
     * @return 脱敏值
     */
    private String safeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.length() > 120 ? fileName.substring(0, 120) + "..." : fileName;
    }

    /**
     * 截断长文本。
     *
     * @param value 原文
     * @param max   最大长度
     * @return 截断后的文本
     */
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 按列宽收敛外部传入的短标识字段。
     * <p>
     * 识别引擎标识（mode / engine / docType）来自本地模型配置或外部 OCR 服务，
     * 长度不受本服务控制。超长时若直接落库会触发 DataException 导致整条任务失败；
     * 这里做最后一道收敛并<b>记警告</b>（不静默），保证可观测。
     * </p>
     *
     * @param value     原始值（可空）
     * @param maxLength 列宽
     * @param fieldName 字段名（日志用）
     * @return 收敛后的值（未超长时原样返回）
     */
    private String truncateForColumn(String value, int maxLength, String fieldName) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("[证照OCR] 字段超长已收敛: field={}, maxLength={}, actual={}, head={}",
                fieldName, maxLength, value.length(),
                value.substring(0, Math.min(40, value.length())));
        return value.substring(0, maxLength);
    }
}