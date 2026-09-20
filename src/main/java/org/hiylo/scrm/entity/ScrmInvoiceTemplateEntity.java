/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceTemplateEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 发票模板实体。
 * <p>
 * 封装一类发票的标准化配置, 包含发票类型、默认税率、默认明细项 (JSON)、
 * 适用商品、必填字段与启停状态。基于模板可快速创建发票申请, 并自动填充默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_invoice_template", schema = "scrm", indexes = {
        @Index(name = "idx_invoice_template_type", columnList = "invoice_type"),
        @Index(name = "idx_invoice_template_enabled", columnList = "enabled")
})
@Data
public class ScrmInvoiceTemplateEntity {

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
     * 持久化前回调: 自动填充创建/更新时间与版本号初值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /** 模板名称 */
    @Column(name = "template_name", nullable = false, length = 200)
    private String templateName;

    /** 模板编码 (唯一) */
    @Column(name = "template_code", nullable = false, length = 50)
    private String templateCode;

    /** 模板描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 发票类型: GENERAL / SPECIAL / ELECTRONIC / PLAIN_DIGITAL / RED_REDUCED */
    @Column(name = "invoice_type", nullable = false, length = 30)
    private String invoiceType;

    /** 默认税率 (0-1, 如 0.13) */
    @Column(name = "default_tax_rate")
    private Double defaultTaxRate;

    /** JSON 默认明细项: [{name,spec,unit,quantity,price,amount,taxRate,taxAmount}] (可空) */
    @Column(name = "default_items", columnDefinition = "TEXT")
    private String defaultItems;

    /** 适用商品 ID 逗号分隔 (可空) */
    @Column(name = "applicable_products", length = 500)
    private String applicableProducts;

    /** 默认备注 (可空) */
    @Column(name = "remarks", length = 500)
    private String remarks;

    /** 必填字段逗号分隔 (可空) */
    @Column(name = "required_fields", length = 500)
    private String requiredFields;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 使用次数 (基于此模板创建的发票数) */
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
