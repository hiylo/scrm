/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmImportTemplateEntity.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 数据导入模板实体。
 * <p>
 * 定义批量导入的列映射 (columns JSON)、校验规则 (validationRules JSON)、去重字段与
 * "存在则更新" 策略, 支撑客户/联系人/跟进记录/标签/商品/订单等多类型数据的标准化导入。
 * usageCount 记录模板被引用次数, enabled 控制模板是否可被选用。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_import_template", schema = "scrm", indexes = {
        @Index(name = "idx_import_tpl_datatype", columnList = "data_type"),
        @Index(name = "idx_import_tpl_enabled", columnList = "enabled")
})
@Data
public class ScrmImportTemplateEntity {

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

    /** 乐观锁版本号（并发更新保护，后写入者触发 OptimisticLockException） */
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
        if (enabled == null) {
            enabled = true;
        }
        if (updateIfExists == null) {
            updateIfExists = false;
        }
        if (usageCount == null) {
            usageCount = 0;
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

    /** 数据类型: CUSTOMER/CONTACT/FOLLOW_RECORD/TAG/PRODUCT/ORDER/OTHER */
    @Column(name = "data_type", nullable = false, length = 30)
    private String dataType;

    /** 模板描述（可空） */
    @Column(name = "description", length = 500)
    private String description;

    /** 列定义 JSON: [{name,field,type,required,enum}] */
    @Column(name = "columns", nullable = false, columnDefinition = "TEXT")
    private String columns;

    /** 示例文件 URL（可空） */
    @Column(name = "sample_file_url", length = 500)
    private String sampleFileUrl;

    /** 校验规则 JSON（可空） */
    @Column(name = "validation_rules", columnDefinition = "TEXT")
    private String validationRules;

    /** 去重字段（可空） */
    @Column(name = "deduplication_key", length = 200)
    private String deduplicationKey;

    /** 存在则更新（默认 FALSE） */
    @Column(name = "update_if_exists", nullable = false)
    private Boolean updateIfExists;

    /** 是否启用（默认 TRUE） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 使用次数（默认 0） */
    @Column(name = "usage_count")
    private Integer usageCount;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
