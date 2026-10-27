/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerDocumentEntity.java
 * Date : 2026/10/25 16:00:00
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户资料归档实体（资料域）。
 * <p>
 * 只存元数据与对象存储 key，文件本体落在 {@code scrm.storage.*}（MinIO/OSS）。
 * 这样做的原因是：二进制进库会让备份体积和恢复时间随资料量线性膨胀，
 * 而对象存储天然支持预签名直连下载，不必让应用进程中转大文件。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_document", schema = "scrm", indexes = {
        @Index(name = "idx_document_company_type", columnList = "company_id,doc_type"),
        @Index(name = "idx_document_status_expire", columnList = "status,expire_date"),
        @Index(name = "idx_document_object_key", columnList = "object_key")
})
@Data
public class ScrmCustomerDocumentEntity {

    /** 状态：有效 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：资料自身已过期（身份证到期、合同到期等，不是文件被删） */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** 状态：已归档封存 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 资料类型：营业执照 */
    public static final String TYPE_BUSINESS_LICENSE = "BUSINESS_LICENSE";
    /** 资料类型：开户许可证 */
    public static final String TYPE_BANK_PERMIT = "BANK_PERMIT";
    /** 资料类型：法人身份证 */
    public static final String TYPE_LEGAL_ID_CARD = "LEGAL_ID_CARD";
    /** 资料类型：公司章程 */
    public static final String TYPE_ARTICLES = "ARTICLES";
    /** 资料类型：租赁合同 */
    public static final String TYPE_LEASE_CONTRACT = "LEASE_CONTRACT";
    /** 资料类型：印章备案 */
    public static final String TYPE_SEAL_RECORD = "SEAL_RECORD";
    /** 资料类型：银行对账单 */
    public static final String TYPE_BANK_STATEMENT = "BANK_STATEMENT";
    /** 资料类型：财务报表 */
    public static final String TYPE_FINANCIAL_REPORT = "FINANCIAL_REPORT";
    /** 资料类型：涉税表单 */
    public static final String TYPE_TAX_FORM = "TAX_FORM";
    /** 资料类型：其他 */
    public static final String TYPE_OTHER = "OTHER";

    /**
     * 必备资料清单。
     * <p>
     * 这 5 类是代账接户与年检的硬性要求，缺任何一项都会导致无法开户、
     * 无法申报或工商年检被退回，因此单独用来算「资料齐全度」。
     * 其余类型（印章备案、对账单等）按需归档，不计入必备。
     * </p>
     */
    public static final List<String> REQUIRED_TYPES = List.of(
            TYPE_BUSINESS_LICENSE,
            TYPE_BANK_PERMIT,
            TYPE_LEGAL_ID_CARD,
            TYPE_ARTICLES,
            TYPE_LEASE_CONTRACT);

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

    /** 所属公司主体 ID */
    @Column(name = "company_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long companyId;

    /** 公司名称（冗余） */
    @Column(name = "company_name", length = 200)
    private String companyName;

    /** 资料类型 */
    @Column(name = "doc_type", nullable = false, length = 40)
    private String docType;

    /** 资料名称，如「营业执照（正本）」 */
    @Column(name = "doc_name", nullable = false, length = 200)
    private String docName;

    /** 对象存储 key */
    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    /** 原始文件名 */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** MIME 类型 */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** 资料签发/起始日期 */
    @Column(name = "issue_date")
    private LocalDate issueDate;

    /** 资料到期日（空表示长期有效） */
    @Column(name = "expire_date")
    private LocalDate expireDate;

    /** 状态：ACTIVE/EXPIRED/ARCHIVED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 上传人 */
    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    /** 上传时间 */
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    /**
     * 持久化前回调：填充时间与版本初值。
     */
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
        if (status == null) {
            status = STATUS_ACTIVE;
        }
        if (uploadedAt == null) {
            uploadedAt = now;
        }
    }

    /**
     * 更新前回调：刷新更新时间。
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
