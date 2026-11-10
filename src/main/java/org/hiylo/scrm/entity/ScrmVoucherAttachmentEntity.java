/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherAttachmentEntity.java
 * Date : 2026/11/10 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
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

import java.time.LocalDateTime;

/**
 * 凭证原始单据（电子附件）实体（V28）。
 * <p>
 * 只存元数据与对象存储 key，文件本体落在 {@code scrm.storage.*}（MinIO/OSS），
 * 与 {@link ScrmCustomerDocumentEntity} 同口径：二进制不进库，备份体积与恢复时间
 * 不随附件量线性膨胀，下载走预签名 URL 直连存储，不经应用进程中转。
 * </p>
 * <p>
 * <b>与 {@code ScrmVoucherEntity.attachmentCount} 的关系</b>：
 * 那个字段是会计手工声明的「纸质附件张数」，本实体是「真实可下载的电子单据」。
 * 两者语义不同、并存且互不覆盖——手上有 3 张纸质单据但只扫描了 2 份时，
 * 两个数字本来就不该相等。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_voucher_attachment", schema = "scrm", indexes = {
        @Index(name = "idx_voucher_attachment_voucher", columnList = "voucher_id,uploaded_at"),
        @Index(name = "idx_voucher_attachment_company", columnList = "company_id,voucher_id")
})
@Data
public class ScrmVoucherAttachmentEntity {

    /** 单据类型：发票 */
    public static final String DOC_INVOICE = "INVOICE";
    /** 单据类型：银行回单 */
    public static final String DOC_BANK_RECEIPT = "BANK_RECEIPT";
    /** 单据类型：合同 */
    public static final String DOC_CONTRACT = "CONTRACT";
    /** 单据类型：工资表 */
    public static final String DOC_PAYROLL = "PAYROLL";
    /** 单据类型：收据 */
    public static final String DOC_RECEIPT = "RECEIPT";
    /** 单据类型：其他 */
    public static final String DOC_OTHER = "OTHER";

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

    /** 乐观锁版本号 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** 所属凭证 ID */
    @Column(name = "voucher_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long voucherId;

    /** 冗余公司主体 ID（按主体过滤免 join，越权校验直接可用） */
    @Column(name = "company_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long companyId;

    /** 对象存储 key（文件本体定位，不存二进制） */
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    /** 原始文件名（净化后，仅用于展示与下载建议名） */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** MIME 类型 */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** 单据类型：INVOICE/BANK_RECEIPT/CONTRACT/PAYROLL/RECEIPT/OTHER */
    @Column(name = "doc_type", nullable = false, length = 40)
    private String docType = DOC_OTHER;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 上传人（登录用户 ID） */
    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    /** 上传时间 */
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    /** 落库前补默认值：时间戳、版本号、默认单据类型。 */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (docType == null || docType.isBlank()) {
            docType = DOC_OTHER;
        }
        if (uploadedAt == null) {
            uploadedAt = now;
        }
    }

    /** 更新前刷新更新时间。 */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
