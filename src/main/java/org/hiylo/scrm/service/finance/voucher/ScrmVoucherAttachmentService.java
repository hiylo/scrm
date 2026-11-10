/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherAttachmentService.java
 * Date : 2026/11/10 16:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmVoucherAttachmentEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmVoucherAttachmentRepository;
import org.hiylo.scrm.repository.ScrmVoucherRepository;
import org.hiylo.scrm.service.finance.FinanceDataScope;
import org.hiylo.scrm.storage.ObjectStorage;
import org.hiylo.scrm.storage.StorageException;
import org.hiylo.scrm.storage.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 凭证原始单据服务（V28）。
 * <p>
 * <b>为什么需要它</b>：记账凭证的原始单据（发票、银行回单、合同、工资表）
 * 是法定留存物——审计、税务检查、客户对账都要能翻出来。此前系统只有
 * {@code attachment_count} 这个手填数字，等于"知道有几张，但一张都拿不出来"。
 * </p>
 * <p>
 * <b>删除语义（与资料归档一致）</b>：先删库再删对象存储；对象删除失败只记日志
 * 不阻断事务——否则存储抖动会让用户永远删不掉一条错记录。留下的是孤儿对象，
 * 由存储生命周期策略清理，比数据不一致安全。
 * </p>
 * <p>
 * <b>越权防线</b>：每个方法都先反查凭证归属主体，再过
 * {@link FinanceDataScope#canAccessCompany(Long)}，
 * 会计改一个凭证 ID 也拿不到别家的单据。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmVoucherAttachmentService {

    /** objectKey 前缀（凭证单据独立目录，便于按前缀做生命周期策略） */
    private static final String OBJECT_KEY_PREFIX = "scrm/voucher/";

    /** objectKey 年月目录格式 */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 单文件大小上限（100MB，与 multipart 配置一致） */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /** 允许的单据类型（白名单，不接受前端任意字符串） */
    private static final Set<String> ALLOWED_DOC_TYPES = Set.of(
            ScrmVoucherAttachmentEntity.DOC_INVOICE,
            ScrmVoucherAttachmentEntity.DOC_BANK_RECEIPT,
            ScrmVoucherAttachmentEntity.DOC_CONTRACT,
            ScrmVoucherAttachmentEntity.DOC_PAYROLL,
            ScrmVoucherAttachmentEntity.DOC_RECEIPT,
            ScrmVoucherAttachmentEntity.DOC_OTHER);

    /** 单据仓储 */
    private final ScrmVoucherAttachmentRepository attachmentRepository;

    /** 凭证仓储（反查归属主体与存在性） */
    private final ScrmVoucherRepository voucherRepository;

    /** 对象存储（复用既有 MinIO/OSS 抽象，不新建存储层） */
    private final ObjectStorage objectStorage;

    /** 对象存储配置 */
    private final StorageProperties storageProperties;

    /** 财税数据范围（会计只能操作自己负责主体的单据） */
    private final FinanceDataScope financeDataScope;

    // ============================================================
    // 上传 / 列表 / 下载 / 删除
    // ============================================================

    /**
     * 给凭证挂一份原始单据。
     *
     * @param voucherId 凭证 ID
     * @param file      文件
     * @param docType   单据类型（可空，默认 OTHER；非法值回落 OTHER）
     * @param remark    备注（可空）
     * @return 挂载后的单据记录
     * @throws ScrmException 凭证不存在 / 无权访问 / 文件为空或超限 / 存储失败
     */
    @Transactional
    public ScrmVoucherAttachmentEntity upload(Long voucherId, MultipartFile file,
                                              String docType, String remark) throws ScrmException {
        if (voucherId == null) {
            throw ScrmException.badRequest("凭证 ID 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw ScrmException.badRequest("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ScrmException.badRequest("文件超过单份上限 100MB: " + file.getSize() + " 字节");
        }
        ScrmVoucherEntity voucher = findVoucherOrThrow(voucherId);
        assertAccessible(voucher.getCompanyId());

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
                    "单据上传对象存储失败: " + e.getMessage(), e);
        }

        ScrmVoucherAttachmentEntity entity = new ScrmVoucherAttachmentEntity();
        entity.setVoucherId(voucherId);
        entity.setCompanyId(voucher.getCompanyId());
        entity.setObjectKey(objectKey);
        entity.setFileName(originalName);
        entity.setFileSize(file.getSize());
        entity.setContentType(file.getContentType());
        entity.setDocType(normalizeDocType(docType));
        entity.setRemark(remark);
        entity.setUploadedBy(operator());
        entity.setUploadedAt(LocalDateTime.now());

        ScrmVoucherAttachmentEntity saved = attachmentRepository.save(entity);
        log.info("[凭证单据] 挂载成功: voucherId={}, companyId={}, docType={}, objectKey={}, size={}",
                voucherId, voucher.getCompanyId(), saved.getDocType(), objectKey, file.getSize());
        return saved;
    }

    /**
     * 查某张凭证的全部原始单据。
     *
     * @param voucherId 凭证 ID
     * @return 单据列表（上传时间倒序）
     * @throws ScrmException 凭证不存在 / 无权访问
     */
    @Transactional(readOnly = true)
    public List<ScrmVoucherAttachmentEntity> list(Long voucherId) throws ScrmException {
        ScrmVoucherEntity voucher = findVoucherOrThrow(voucherId);
        assertAccessible(voucher.getCompanyId());
        return attachmentRepository.findByVoucherIdOrderByUploadedAtDesc(voucherId);
    }

    /**
     * 批量统计多张凭证的单据数（列表页一次 group by，避免 N+1）。
     *
     * @param voucherIds 凭证 ID 集合
     * @return voucherId(字符串) → 单据数；无单据的凭证不会出现在 Map 里
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> countByVoucherIds(List<Long> voucherIds) {
        Map<String, Integer> result = new HashMap<>();
        if (voucherIds == null || voucherIds.isEmpty()) {
            return result;
        }
        for (Object[] row : attachmentRepository.countGroupByVoucherIds(voucherIds)) {
            result.put(String.valueOf(row[0]), ((Number) row[1]).intValue());
        }
        return result;
    }

    /**
     * 生成单据的下载预签名 URL（直连对象存储，不经应用进程中转）。
     *
     * @param id 单据 ID
     * @return 预签名 URL
     * @throws ScrmException 单据不存在 / 无权访问 / 生成失败
     */
    @Transactional(readOnly = true)
    public String presignDownload(Long id) throws ScrmException {
        ScrmVoucherAttachmentEntity entity = findOrThrow(id);
        assertAccessible(entity.getCompanyId());
        try {
            return objectStorage.presignedGetUrl(storageProperties.getBucket(),
                    entity.getObjectKey(), storageProperties.getPresignExpirySeconds());
        } catch (StorageException e) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MEDIA_PRESIGN_FAILED,
                    "生成下载链接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除一份单据（删行 + 删对象存储对象）。
     *
     * @param id 单据 ID
     * @throws ScrmException 单据不存在 / 无权访问
     */
    @Transactional
    public void delete(Long id) throws ScrmException {
        ScrmVoucherAttachmentEntity entity = findOrThrow(id);
        assertAccessible(entity.getCompanyId());
        // 先删库再删对象：即使对象删除失败，也不会留下指向已删数据的记录
        attachmentRepository.delete(entity);
        try {
            objectStorage.delete(storageProperties.getBucket(), entity.getObjectKey());
        } catch (StorageException e) {
            // 不阻断事务：孤儿对象由存储生命周期策略清理，比"删不掉记录"影响更小
            log.warn("[凭证单据] 对象删除失败(已删记录, 孤儿对象待清理): objectKey={}, err={}",
                    entity.getObjectKey(), e.getMessage());
        }
        log.info("[凭证单据] 删除成功: id={}, voucherId={}, objectKey={}",
                id, entity.getVoucherId(), entity.getObjectKey());
    }

    /**
     * 清理某张凭证的全部单据（凭证物理删除时级联调用）。
     * <p>
     * 对象存储删除失败只记日志，不阻断调用方事务——凭证本身必须能删掉。
     * </p>
     *
     * @param voucherId 凭证 ID
     * @return 清理的单据行数
     */
    @Transactional
    public int deleteByVoucherId(Long voucherId) {
        List<String> objectKeys = new ArrayList<>(
                attachmentRepository.findObjectKeysByVoucherId(voucherId));
        int deleted = attachmentRepository.deleteByVoucherId(voucherId);
        for (String objectKey : objectKeys) {
            try {
                objectStorage.delete(storageProperties.getBucket(), objectKey);
            } catch (StorageException e) {
                log.warn("[凭证单据] 级联删对象失败(孤儿对象待清理): objectKey={}, err={}",
                        objectKey, e.getMessage());
            }
        }
        if (deleted > 0) {
            log.info("[凭证单据] 级联清理完成: voucherId={}, 删除 {} 份", voucherId, deleted);
        }
        return deleted;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 查凭证或抛 404。 */
    private ScrmVoucherEntity findVoucherOrThrow(Long voucherId) throws ScrmException {
        return voucherRepository.findById(voucherId)
                .orElseThrow(() -> ScrmException.notFound("凭证不存在: " + voucherId));
    }

    /** 查单据或抛 404。 */
    private ScrmVoucherAttachmentEntity findOrThrow(Long id) throws ScrmException {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("原始单据不存在: " + id));
    }

    /**
     * 校验当前用户有权访问该主体。
     *
     * @param companyId 公司主体 ID
     * @throws ScrmException 无权访问
     */
    private void assertAccessible(Long companyId) throws ScrmException {
        if (!financeDataScope.canAccessCompany(companyId)) {
            throw ScrmException.forbidden("无权访问该主体: " + companyId);
        }
    }

    /**
     * 规范化单据类型：白名单外一律回落 OTHER。
     * <p>
     * 不做参数白名单校验的话，前端随便传个字符串就能写进库，
     * 后续按类型筛选/统计就会脏。
     * </p>
     *
     * @param docType 原始类型
     * @return 白名单内的类型
     */
    private String normalizeDocType(String docType) {
        if (docType == null || docType.isBlank()) {
            return ScrmVoucherAttachmentEntity.DOC_OTHER;
        }
        String normalized = docType.trim().toUpperCase();
        return ALLOWED_DOC_TYPES.contains(normalized)
                ? normalized : ScrmVoucherAttachmentEntity.DOC_OTHER;
    }

    /**
     * 净化上传文件名（防路径穿越与响应头注入，同资料归档/会话媒体约定）。
     *
     * @param fileName 原始文件名
     * @return 净化后的文件名
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment";
        }
        String base = fileName.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[\\r\\n\"]", "");
        base = base.replaceAll("[^\\w.\\u4e00-\\u9fa5-]", "_");
        if (base.isBlank() || ".".equals(base) || "..".equals(base)) {
            base = "attachment";
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    /** 当前操作人。 */
    private String operator() {
        String userId = UserContext.getUserId();
        return userId != null && !userId.isBlank() ? userId : "SYSTEM";
    }
}
