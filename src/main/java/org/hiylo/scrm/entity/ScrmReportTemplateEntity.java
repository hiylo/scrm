/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReportTemplateEntity.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 自定义报表模板实体。
 * <p>
 * 描述一张自定义报表的配置: 数据源、维度、指标 (含聚合方式)、筛选条件、时间范围字段与图表类型。
 * dimensions / metrics / filters 以 JSON 字符串存储, 执行时由
 * {@code ScrmCustomReportService.executeReport} 解析并动态构建 SQL。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_report_template", schema = "scrm", indexes = {
        @Index(name = "idx_report_template_type", columnList = "report_type"),
        @Index(name = "idx_report_template_public", columnList = "is_public")
})
@Data
public class ScrmReportTemplateEntity {

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

    /** 报表类型：CUSTOMER / OPPORTUNITY / CAMPAIGN / CONVERSATION / MASS_SEND / ARCHIVE / GENERAL */
    @Column(name = "report_type", nullable = false, length = 30)
    private String reportType;

    /** 数据源表名 (如 scrm_customer / scrm_opportunity) */
    @Column(name = "data_source", nullable = false, length = 50)
    private String dataSource;

    /** 维度字段 JSON 数组 (如 ["lifecycle", "platform_type"]) */
    @Column(name = "dimensions", nullable = false, columnDefinition = "TEXT")
    private String dimensions;

    /** 指标字段 JSON 数组 (如 [{"field":"amount", "aggregation":"SUM"}]) */
    @Column(name = "metrics", nullable = false, columnDefinition = "TEXT")
    private String metrics;

    /** 筛选条件 JSON (可空, 如 {"status":"OPEN"}) */
    @Column(name = "filters", columnDefinition = "TEXT")
    private String filters;

    /** 时间范围字段 (可空, 如 create_time / sent_at) */
    @Column(name = "time_range_field", length = 100)
    private String timeRangeField;

    /** 图表类型：TABLE / BAR / LINE / PIE */
    @Column(name = "chart_type", length = 30)
    private String chartType;

    /** 模板描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 是否公开 (公开模板账号下所有用户可见) */
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    /** 创建人用户 ID */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** 最近执行时间 */
    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;
}
