/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractTemplateEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 合同模板实体。
 * <p>
 * 合同模板封装一类合同的标准化条款与变量定义, 包含合同类型 (销售/服务/合作/NDA 等)、
 * 模板内容 (支持变量占位符渲染)、变量定义 (JSON)、条款配置 (JSON) 与版本/状态/使用统计。
 * 基于模板可快速创建合同实例并自动渲染合同内容。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_contract_template", schema = "scrm", indexes = {
        @Index(name = "idx_contract_template_type", columnList = "contract_type"),
        @Index(name = "idx_contract_template_status", columnList = "status")
})
@Data
public class ScrmContractTemplateEntity {

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

    /** 合同类型: SALES / SERVICE / PARTNERSHIP / NDA / RESELLER / AGENCY / MAINTENANCE / RENTAL / PURCHASE / CUSTOM */
    @Column(name = "contract_type", nullable = false, length = 30)
    private String contractType;

    /** 合同模板内容 (支持变量占位符渲染, 如 {{customerName}}) */
    @Column(name = "template_content", nullable = false, columnDefinition = "TEXT")
    private String templateContent;

    /** JSON 变量定义: [{name, type, defaultValue, required}] (可空) */
    @Column(name = "variables", columnDefinition = "TEXT")
    private String variables;

    /** 适用商品 ID 逗号分隔 (可空) */
    @Column(name = "applicable_products", length = 500)
    private String applicableProducts;

    /** JSON 条款配置: [{clauseName, isRequired, editable}] (可空) */
    @Column(name = "clauses", columnDefinition = "TEXT")
    private String clauses;

    /** 模板版本号 (业务版本, 每次更新递增) */
    @Column(name = "template_version", nullable = false)
    private Integer templateVersion;

    /** 状态: ACTIVE / INACTIVE / DRAFT */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 使用次数 (基于此模板创建的合同数) */
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount;

    /** 最后使用时间 (可空) */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 审核人 (可空) */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    /** 审核时间 (可空) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
