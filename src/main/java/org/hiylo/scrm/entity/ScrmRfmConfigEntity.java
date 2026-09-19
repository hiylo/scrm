/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmConfigEntity.java
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
 * SCRM RFM 配置实体。
 * <p>
 * 定义 RFM 模型分析参数: R/F/M 权重、高/低阈值与数据源。一个账号下可存在多个配置,
 * 其中 isDefault=true 的配置为批量计算时的默认配置。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_rfm_config", schema = "scrm", indexes = {
        @Index(name = "idx_rfm_config_default", columnList = "is_default"),
        @Index(name = "idx_rfm_config_enabled", columnList = "enabled")
})
@Data
public class ScrmRfmConfigEntity {

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

    /** 配置名称 */
    @Column(name = "config_name", nullable = false, length = 200)
    private String configName;

    /** 配置描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** R 权重 (最近消费权重, 默认 0.3) */
    @Column(name = "r_weight", nullable = false)
    private Double rWeight;

    /** F 权重 (消费频率权重, 默认 0.3) */
    @Column(name = "f_weight", nullable = false)
    private Double fWeight;

    /** M 权重 (消费金额权重, 默认 0.4) */
    @Column(name = "m_weight", nullable = false)
    private Double mWeight;

    /** R 高/低阈值天数 (最近互动/消费距今天数 <= 阈值则 R 高) */
    @Column(name = "r_threshold", nullable = false)
    private Integer rThreshold;

    /** F 高/低阈值次数 (消费/互动次数 > 阈值则 F 高) */
    @Column(name = "f_threshold", nullable = false)
    private Integer fThreshold;

    /** M 高/低阈值金额 (消费/互动总金额 > 阈值则 M 高) */
    @Column(name = "m_threshold", nullable = false)
    private Double mThreshold;

    /** R 数据源: LAST_INTERACTION 最近互动 / LAST_ORDER 最近消费 */
    @Column(name = "recency_source", nullable = false, length = 50)
    private String recencySource;

    /** M 数据源: TOTAL_SPENT 累计消费 / MANUAL 手动录入 */
    @Column(name = "monetary_source", nullable = false, length = 50)
    private String monetarySource;

    /** 是否为默认配置（账号下仅一个默认配置） */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 最近一次计算时间 */
    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
