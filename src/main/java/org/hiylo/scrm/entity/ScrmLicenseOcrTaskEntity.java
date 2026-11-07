/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmLicenseOcrTaskEntity.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;
import org.hiylo.scrm.config.EncryptedStringConverter;

import java.time.LocalDateTime;

/**
 * 证照 OCR 识别任务实体。
 * <p>
 * 一次证照上传 = 一条任务, 承接「上传 → OCR 识别 → 结果落库 → 低置信度字段人工复核」
 * 全链路（立项方案 §2 模块②A + T07 契约）。识别结果（fields/verification/lines/全文）
 * 含身份证号、银行账号等敏感字段, 整列用 {@link EncryptedStringConverter} AES-GCM
 * 加密落库; 日志与错误信息一律脱敏, {@code verification.problems} 不回显客户侧界面。
 * </p>
 * <p>
 * 复核语义复用 QualityInspection 的「低置信度 → 人工复核」约定（T07 契约 §7）:
 * 必填字段 {@code status != ok}（missing / low_confidence / unverified / invalid）
 * 或顶层 {@code verification.ok=false} → {@code reviewStatus=REQUIRED},
 * 复核通过后 {@code REVIEWED} 并记录复核字段清单/复核人/时间。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_license_ocr_task", schema = "scrm", indexes = {
        @Index(name = "idx_license_ocr_customer", columnList = "customer_id"),
        @Index(name = "idx_license_ocr_business", columnList = "business_id"),
        @Index(name = "idx_license_ocr_owner", columnList = "owner_account_id"),
        @Index(name = "idx_license_ocr_status", columnList = "task_status"),
        @Index(name = "idx_license_ocr_review", columnList = "review_status"),
        @Index(name = "idx_license_ocr_type", columnList = "license_type")
})
@Data
public class ScrmLicenseOcrTaskEntity {

    // ==================== 公共字段 ====================

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 创建时间 */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 乐观锁版本号（并发更新保护, 后写入者触发 OptimisticLockException） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间、版本号初值与任务/复核状态默认值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (taskStatus == null || taskStatus.isBlank()) {
            taskStatus = "PENDING";
        }
        if (reviewStatus == null || reviewStatus.isBlank()) {
            reviewStatus = "NONE";
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 归属与目标 ====================

    /** 关联客户 ID（引用 scrm_customer.id, 可空） */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 业务主体（公司）ID（V14）；识别成功后可直接回填公司档案 */
    @Column(name = "company_id")
    private Long companyId;

    /** 目标档案域业务单号（合同/发票等, 一期不做强外键; 可空） */
    @Column(name = "business_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long businessId;

    /** 归属账号 ID（引用 scrm_account.id, 数据隔离） */
    @Column(name = "owner_account_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerAccountId;

    // ==================== 识别参数 ====================

    /** 识别类型: license(营业执照) / idcard(身份证) / account_license(开户许可证) / general(通用) */
    @Column(name = "license_type", nullable = false, length = 30)
    private String licenseType;

    /** 身份证翻面: front(人像面) / back(国徽面), 其他类型为空 */
    @Column(name = "side", length = 10)
    private String side;

    /** 原始文件名（展示用） */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** 证照原图/PDF 对象存储 key（ConversationMedia/影像域） */
    @Column(name = "object_key", length = 500)
    private String objectKey;

    // ==================== 识别结果 ====================

    /** 调用方幂等/链路标识（request_id 原样带回） */
    @Column(name = "request_id", length = 100)
    private String requestId;

    /** 实际执行模式: auto / vl / ocr */
    @Column(name = "mode", length = 20)
    private String mode;

    /**
     * 引擎标识。
     * <p>
     * 本地视觉为 {@code local-vision:<model>}（模型 id 走网关时形如
     * {@code group/sensenova-pool}），外部 OCR 为服务端返回的引擎名，
     * 均不受本服务控制，故列宽取 100（V16 从 30 放宽）。
     * </p>
     */
    @Column(name = "engine", length = 100)
    private String engine;

    /** 服务端模板判定结果 */
    @Column(name = "doc_type", length = 30)
    private String docType;

    /** 任务状态: PENDING / SUCCEEDED(无需复核) / PARTIAL(需复核) / FAILED */
    @Column(name = "task_status", nullable = false, length = 20)
    private String taskStatus;

    /** OCR 顶层 ok（false 表示引擎缺件/图片损坏） */
    @Column(name = "overall_ok")
    private Boolean overallOk;

    /** 识别字段列表 JSON（含 confidence/verified/status, 敏感字段整列加密） */
    @Column(name = "fields_json", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String fieldsJson;

    /** 交叉校验结果 JSON {ok,checked,problems} */
    @Column(name = "verification_json", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String verificationJson;

    /** 原始文本行 JSON（坐标+置信度, 人工复核阅读原文用） */
    @Column(name = "lines_json", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String linesJson;

    /** OCR 全文（人工复核参考） */
    @Column(name = "raw_text", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String rawText;

    /** 非致命提示 JSON（如 doc_side_unknown） */
    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    // ==================== 复核（低置信度人工复核） ====================

    /** 复核状态: NONE(无需复核) / REQUIRED(低置信必填/校验不过→转人工) / REVIEWED(已复核通过) */
    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus;

    /** 待复核字段清单 JSON [{key,name,status,problems}] */
    @Column(name = "review_fields_json", columnDefinition = "TEXT")
    private String reviewFieldsJson;

    /** 复核备注（可空） */
    @Column(name = "review_remark", length = 1000)
    private String reviewRemark;

    /** 复核人（UserContext 用户名） */
    @Column(name = "reviewer", length = 100)
    private String reviewer;

    /** 复核时间 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // ==================== 失败与审计 ====================

    /** 错误信息（调用失败 / ok=false 时; 脱敏, 不含令牌与完整敏感字段） */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 创建人（UserContext 用户名） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}