/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmCustomerDocumentService.java
 * Date : 2026/10/25 16:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.document;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmCustomerDocumentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerDocumentRepository;
import org.hiylo.scrm.storage.ObjectStorage;
import org.hiylo.scrm.storage.StorageException;
import org.hiylo.scrm.storage.StorageProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户资料归档服务（资料域）。
 * <p>
 * <b>核心不是「能传文件」，而是「能回答某户还缺哪些必备资料」。</b>
 * 因此除上传/下载/删除外，本服务提供：
 * </p>
 * <ul>
 *   <li>{@code checklist}：按必备类型算某户齐全度，缺哪项一目了然；</li>
 *   <li>{@code overview}：所有在服企业的资料齐全度总览，用于找出手续不全的户；</li>
 *   <li>{@code advanceExpired}：把过期资料标为 EXPIRED（身份证到期、合同到期）。</li>
 * </ul>
 * <p>
 * <b>删除语义</b>：删行 + 删对象存储对象。对象删除失败只记日志不阻断事务——
 * 否则一个存储抖动会让用户永远删不掉一条错记录；留下的是孤儿对象，
 * 由对象存储生命周期策略清理，比数据不一致安全。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmCustomerDocumentService {

    /** objectKey 前缀（资料域独立目录，便于按前缀做生命周期策略） */
    private static final String OBJECT_KEY_PREFIX = "scrm/document/";

    /** objectKey 年月目录格式 */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 单文件大小上限（100MB，与 multipart 配置一致） */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /** 资料仓库 */
    private final ScrmCustomerDocumentRepository documentRepository;

    /** 公司主体仓库（校验归属、补公司名） */
    private final ScrmCompanyRepository companyRepository;

    /** 对象存储（复用既有 MinIO/OSS 抽象，不新建存储层） */
    private final ObjectStorage objectStorage;

    /** 对象存储配置 */
    private final StorageProperties storageProperties;

    /** 财税数据范围（会计只看到自己负责主体的资料） */
    private final org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    // ============================================================
    // 上传 / 下载 / 删除
    // ============================================================

    /**
     * 上传资料并归档。
     *
     * @param companyId 公司主体 ID
     * @param docType   资料类型
     * @param docName   资料名称（空则用原文件名）
     * @param file      文件
     * @param issueDate 资料签发日（可空）
     * @param expireDate 资料到期日（可空）
     * @param remark    备注（可空）
     * @return 归档后的资料记录
     * @throws ScrmException 参数非法 / 主体不存在 / 文件为空 / 存储失败
     */
    @Transactional
    public ScrmCustomerDocumentEntity upload(Long companyId, String docType, String docName,
                                             MultipartFile file, LocalDate issueDate,
                                             LocalDate expireDate, String remark)
            throws ScrmException {
        if (companyId == null) {
            throw ScrmException.badRequest("公司主体不能为空");
        }
        if (docType == null || docType.isBlank()) {
            throw ScrmException.badRequest("资料类型不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw ScrmException.badRequest("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ScrmException.badRequest("文件超过单份上限 100MB: " + file.getSize() + " 字节");
        }
        ScrmCompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        String originalName = sanitizeFileName(file.getOriginalFilename());
        String objectKey = OBJECT_KEY_PREFIX + LocalDateTime.now().format(MONTH_FORMATTER)
                + "/" + UUID.randomUUID().toString().replace("-", "")
                + "_" + originalName;

        try (InputStream inputStream = file.getInputStream()) {
            objectStorage.upload(storageProperties.getBucket(), objectKey,
                    inputStream, file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MEDIA_UPLOAD_FAILED,
                    "读取上传文件失败: " + e.getMessage(), e);
        } catch (StorageException e) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MEDIA_UPLOAD_FAILED,
                    "资料上传对象存储失败: " + e.getMessage(), e);
        }

        ScrmCustomerDocumentEntity entity = new ScrmCustomerDocumentEntity();
        entity.setCompanyId(companyId);
        entity.setCompanyName(company.getCompanyName());
        entity.setDocType(docType);
        entity.setDocName(docName == null || docName.isBlank() ? originalName : docName.trim());
        entity.setObjectKey(objectKey);
        entity.setFileName(originalName);
        entity.setFileSize(file.getSize());
        entity.setContentType(file.getContentType());
        entity.setIssueDate(issueDate);
        entity.setExpireDate(expireDate);
        entity.setStatus(ScrmCustomerDocumentEntity.STATUS_ACTIVE);
        entity.setRemark(remark);
        entity.setUploadedBy(operator());
        entity.setUploadedAt(LocalDateTime.now());

        ScrmCustomerDocumentEntity saved = documentRepository.save(entity);
        log.info("[资料归档] 上传成功: companyId={}, docType={}, objectKey={}, size={}",
                companyId, docType, objectKey, file.getSize());
        return saved;
    }

    /**
     * 生成下载预签名 URL。
     *
     * @param id 资料 ID
     * @return 预签名 URL（默认有效期取配置）
     * @throws ScrmException 资料不存在 / 生成失败
     */
    @Transactional(readOnly = true)
    public String presignDownload(Long id) throws ScrmException {
        ScrmCustomerDocumentEntity entity = findOrThrow(id);
        try {
            return objectStorage.presignedGetUrl(storageProperties.getBucket(),
                    entity.getObjectKey(), storageProperties.getPresignExpirySeconds());
        } catch (StorageException e) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MEDIA_PRESIGN_FAILED,
                    "生成下载链接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除资料（删行 + 删对象存储对象）。
     *
     * @param id 资料 ID
     * @throws ScrmException 资料不存在
     */
    @Transactional
    public void delete(Long id) throws ScrmException {
        ScrmCustomerDocumentEntity entity = findOrThrow(id);
        // 先删库再删对象：即使对象删除失败，也不会留下指向已删数据的记录
        documentRepository.delete(entity);
        try {
            objectStorage.delete(storageProperties.getBucket(), entity.getObjectKey());
        } catch (StorageException e) {
            // 不阻断事务：孤儿对象由存储生命周期策略清理，比"删不掉记录"影响更小
            log.warn("[资料归档] 对象删除失败(已删记录, 孤儿对象待清理): objectKey={}, err={}",
                    entity.getObjectKey(), e.getMessage());
        }
    }

    /**
     * 更新资料元数据（不换文件）。
     *
     * @param id      资料 ID
     * @param docName 名称（可空）
     * @param docType 类型（可空）
     * @param issueDate 签发日（可空）
     * @param expireDate 到期日（可空）
     * @param remark  备注（可空）
     * @param status  状态（可空）
     * @return 更新后的资料
     * @throws ScrmException 资料不存在
     */
    @Transactional
    public ScrmCustomerDocumentEntity update(Long id, String docName, String docType,
                                             LocalDate issueDate, LocalDate expireDate,
                                             String remark, String status) throws ScrmException {
        ScrmCustomerDocumentEntity entity = findOrThrow(id);
        if (docName != null && !docName.isBlank()) {
            entity.setDocName(docName.trim());
        }
        if (docType != null && !docType.isBlank()) {
            entity.setDocType(docType);
        }
        if (issueDate != null) {
            entity.setIssueDate(issueDate);
        }
        if (expireDate != null) {
            entity.setExpireDate(expireDate);
        }
        if (remark != null) {
            entity.setRemark(remark);
        }
        if (status != null && !status.isBlank()) {
            entity.setStatus(status);
        }
        return documentRepository.save(entity);
    }

    // ============================================================
    // 查询 / 齐全度
    // ============================================================

    /**
     * 分页查询资料。
     *
     * @param companyId 主体过滤（可空）
     * @param docType   类型过滤（可空）
     * @param status    状态过滤（可空）
     * @param keyword   资料名/文件名关键字（可空）
     * @param pageable  分页
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerDocumentEntity> list(Long companyId, String docType, String status,
                                                 String keyword, Pageable pageable) {
        // 财税数据范围：会计只能看到自己负责主体的资料
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        return documentRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accessibleCompanyIds != null) {
                if (accessibleCompanyIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(accessibleCompanyIds));
                }
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (docType != null && !docType.isBlank()) {
                predicates.add(cb.equal(root.get("docType"), docType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("docName")), like),
                        cb.like(cb.lower(root.get("fileName")), like),
                        cb.like(cb.lower(root.get("companyName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    /**
     * 查询某公司的资料齐全度清单。
     * <p>
     * 这是本域最有用的一个视图：代账接户时要一眼看出「这户还差什么」，
     * 而不是自己逐条比对必备清单。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @return 必备项逐项状态 + 齐全率 + 该公司全部资料
     * @throws ScrmException 主体不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checklist(Long companyId) throws ScrmException {
        ScrmCompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        List<Map<String, Object>> items = new ArrayList<>();
        int present = 0;
        for (String requiredType : ScrmCustomerDocumentEntity.REQUIRED_TYPES) {
            List<ScrmCustomerDocumentEntity> docs = documentRepository
                    .findByCompanyIdAndDocTypeAndStatus(companyId, requiredType,
                            ScrmCustomerDocumentEntity.STATUS_ACTIVE);
            boolean ok = !docs.isEmpty();
            if (ok) {
                present++;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docType", requiredType);
            item.put("present", ok);
            item.put("count", docs.size());
            // 到期日取最早的一份，便于提示「快过期了」
            item.put("nearestExpireDate", docs.stream()
                    .map(ScrmCustomerDocumentEntity::getExpireDate)
                    .filter(java.util.Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .map(String::valueOf).orElse(null));
            items.add(item);
        }

        int total = ScrmCustomerDocumentEntity.REQUIRED_TYPES.size();
        List<String> missing = items.stream()
                .filter(i -> Boolean.FALSE.equals(i.get("present")))
                .map(i -> (String) i.get("docType"))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", String.valueOf(companyId));
        result.put("companyName", company.getCompanyName());
        result.put("requiredTotal", total);
        result.put("requiredPresent", present);
        result.put("completeness", total == 0 ? 100 : Math.round(present * 100f / total));
        result.put("missingTypes", missing);
        result.put("items", items);
        result.put("documents", documentRepository.findByCompanyIdOrderByUploadedAtDesc(companyId));
        return result;
    }

    /**
     * 全量资料齐全度总览（找出资料不全的在服企业）。
     *
     * @return 每户的齐全度摘要 + 汇总
     */
    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        List<ScrmCompanyEntity> companies = companyRepository.findAll();
        int requiredTotal = ScrmCustomerDocumentEntity.REQUIRED_TYPES.size();
        List<Map<String, Object>> rows = new ArrayList<>();
        int complete = 0;
        int incomplete = 0;

        for (ScrmCompanyEntity company : companies) {
            // 已终止服务的公司不再催资料
            if (ScrmCompanyEntity.SERVICE_TERMINATED.equals(company.getServiceStatus())) {
                continue;
            }
            int present = 0;
            List<String> missing = new ArrayList<>();
            for (String requiredType : ScrmCustomerDocumentEntity.REQUIRED_TYPES) {
                long count = documentRepository.countByCompanyIdAndDocTypeAndStatus(
                        company.getId(), requiredType, ScrmCustomerDocumentEntity.STATUS_ACTIVE);
                if (count > 0) {
                    present++;
                } else {
                    missing.add(requiredType);
                }
            }
            boolean ok = missing.isEmpty();
            if (ok) {
                complete++;
            } else {
                incomplete++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("companyId", String.valueOf(company.getId()));
            row.put("companyName", company.getCompanyName());
            row.put("requiredTotal", requiredTotal);
            row.put("requiredPresent", present);
            row.put("completeness", requiredTotal == 0 ? 100 : Math.round(present * 100f / requiredTotal));
            row.put("complete", ok);
            row.put("missingTypes", missing);
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyTotal", rows.size());
        result.put("complete", complete);
        result.put("incomplete", incomplete);
        result.put("rows", rows);
        return result;
    }

    /**
     * 查询资料详情。
     *
     * @param id 资料 ID
     * @return 资料
     * @throws ScrmException 不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerDocumentEntity get(Long id) throws ScrmException {
        return findOrThrow(id);
    }

    /**
     * 把到期日已过的有效资料推进为 EXPIRED（调度入口，每日）。
     *
     * @return 推进条数
     */
    @Transactional
    public int advanceExpired() {
        LocalDate today = LocalDate.now();
        List<ScrmCustomerDocumentEntity> targets = documentRepository.findByStatusAndExpireDateBetween(
                ScrmCustomerDocumentEntity.STATUS_ACTIVE,
                LocalDate.of(2000, 1, 1), today.minusDays(1));
        for (ScrmCustomerDocumentEntity entity : targets) {
            entity.setStatus(ScrmCustomerDocumentEntity.STATUS_EXPIRED);
            documentRepository.save(entity);
        }
        if (!targets.isEmpty()) {
            log.info("[资料归档] 过期推进 {} 条", targets.size());
        }
        return targets.size();
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 查询资料或抛 404。 */
    private ScrmCustomerDocumentEntity findOrThrow(Long id) throws ScrmException {
        return documentRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("资料不存在: " + id));
    }

    /**
     * 净化上传文件名（防路径穿越与响应头注入，同会话媒体服务约定）。
     *
     * @param fileName 原始文件名
     * @return 净化后的文件名
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }
        String base = fileName.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[\\r\\n\"]", "");
        base = base.replaceAll("[^\\w.\\u4e00-\\u9fa5-]", "_");
        if (base.isBlank() || ".".equals(base) || "..".equals(base)) {
            base = "document";
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    /** 当前操作人。 */
    private String operator() {
        String userId = UserContext.getUserId();
        return userId != null && !userId.isBlank() ? userId : "SYSTEM";
    }
}
