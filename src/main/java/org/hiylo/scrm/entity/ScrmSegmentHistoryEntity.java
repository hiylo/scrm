/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentHistoryEntity.java
 * Date : 2026/08/04 08:40:58
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

/**
 * SCRM 客户分群历史快照实体。
 * <p>
 * 记录分群 ({@link #segmentId}) 在 {@link #snapshotDate} 当日的成员快照, 用于分群趋势分析
 * 与成员变化追踪。{@link #addedCount} / {@link #removedCount} 记录当日新增 / 流失成员数,
 * {@link #avgOrderCount} / {@link #avgTotalAmount} / {@link #avgEngagementScore} 记录当日
 * 成员的平均消费指标。
 * </p>
 * <p>
 * {@link #topLevels} / {@link #topTags} 以 JSON 记录当日成员的等级分布与标签分布, 便于
 * 分群画像趋势分析。每个分群每日仅一条快照 (segment_id + snapshot_date 唯一)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_segment_history", schema = "scrm", indexes = {
        @Index(name = "idx_segment_history_segment", columnList = "segment_id"),
        @Index(name = "idx_segment_history_unique", columnList = "segment_id,snapshot_date", unique = true)
})
@Data
public class ScrmSegmentHistoryEntity {

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

    /** 分群 ID */
    @Column(name = "segment_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long segmentId;

    /** 快照日期 */
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    /** 当日成员数 */
    @Column(name = "member_count", nullable = false)
    private Integer memberCount;

    /** 新增成员数 (默认 0) */
    @Column(name = "added_count")
    private Integer addedCount;

    /** 流失成员数 (默认 0) */
    @Column(name = "removed_count")
    private Integer removedCount;

    /** 平均订单数 (默认 0) */
    @Column(name = "avg_order_count")
    private Double avgOrderCount;

    /** 平均消费金额 (默认 0) */
    @Column(name = "avg_total_amount")
    private Double avgTotalAmount;

    /** 平均互动分 (默认 0) */
    @Column(name = "avg_engagement_score")
    private Double avgEngagementScore;

    /** 等级分布 JSON (可空) */
    @Column(name = "top_levels", length = 500)
    private String topLevels;

    /** 标签分布 JSON (可空) */
    @Column(name = "top_tags", length = 500)
    private String topTags;

    /** 计算时间 */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
