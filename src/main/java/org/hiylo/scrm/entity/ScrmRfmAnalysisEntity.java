/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmAnalysisEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM RFM 分析结果实体。
 * <p>
 * 记录单个客户在指定配置下的 RFM 分析结果: 原始 R/F/M 值、1-5 评分、
 * RFM 分群编码 (如 "111")、分群名称、分群大类与综合价值分。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_rfm_analysis", schema = "scrm", indexes = {
        @Index(name = "idx_rfm_analysis_customer", columnList = "customer_id"),
        @Index(name = "idx_rfm_analysis_config", columnList = "config_id"),
        @Index(name = "idx_rfm_analysis_segment", columnList = "rfm_segment"),
        @Index(name = "idx_rfm_analysis_category", columnList = "segment_category"),
        @Index(name = "idx_rfm_analysis_value", columnList = "value_score")
})
@Data
public class ScrmRfmAnalysisEntity {

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

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余便于展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 使用的 RFM 配置 ID */
    @Column(name = "config_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 最近互动/消费距今天数 */
    @Column(name = "recency_days", nullable = false)
    private Integer recencyDays;

    /** 消费/互动次数 */
    @Column(name = "frequency", nullable = false)
    private Integer frequency;

    /** 消费/互动总金额 */
    @Column(name = "monetary", nullable = false)
    private Double monetary;

    /** R 评分 (1-5, 越大越好) */
    @Column(name = "r_score", nullable = false)
    private Integer rScore;

    /** F 评分 (1-5, 越大越好) */
    @Column(name = "f_score", nullable = false)
    private Integer fScore;

    /** M 评分 (1-5, 越大越好) */
    @Column(name = "m_score", nullable = false)
    private Integer mScore;

    /** RFM 分群编码 (如 "111", "101") */
    @Column(name = "rfm_segment", nullable = false, length = 20)
    private String rfmSegment;

    /** 分群名称 (如 "重要价值客户") */
    @Column(name = "segment_name", nullable = false, length = 50)
    private String segmentName;

    /** 分群大类: CHAMPION/LOYAL/POTENTIAL/NEW/AT_RISK/LOST/HIBERNATING/NORMAL */
    @Column(name = "segment_category", nullable = false, length = 30)
    private String segmentCategory;

    /** 综合价值分 (0-100) */
    @Column(name = "value_score", nullable = false)
    private Double valueScore;

    /** 计算时间 */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
