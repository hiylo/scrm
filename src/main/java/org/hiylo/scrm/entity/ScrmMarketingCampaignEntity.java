/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignEntity.java
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
 * SCRM 营销活动实体。
 * <p>
 * 区别于单次执行的营销任务 ({@code ScrmCampaignEntity}), 活动是多渠道、多阶段、有预算和
 * ROI 追踪的营销主体。{@link #channels} 以逗号分隔存储多渠道枚举
 * (WECHAT/WORK_WECHAT/SMS/EMAIL/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI), 详细渠道配置由
 * {@code ScrmMarketingCampaignChannelEntity} 维护。
 * </p>
 * <p>
 * 状态流转: DRAFT (草稿) → SCHEDULED (已排期) → RUNNING (进行中) → PAUSED (暂停) /
 * COMPLETED (已完成) / CANCELLED (已取消)。{@link #budget} / {@link #actualCost} 用于
 * ROI 计算, {@link #metricsJson} 缓存活动汇总指标。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_marketing_campaign", schema = "scrm", indexes = {
        @Index(name = "idx_mkt_campaign_type", columnList = "campaign_type"),
        @Index(name = "idx_mkt_campaign_status", columnList = "status"),
        @Index(name = "idx_mkt_campaign_manager", columnList = "manager_id"),
        @Index(name = "idx_mkt_campaign_date_range", columnList = "start_date,end_date")
})
@Data
public class ScrmMarketingCampaignEntity {

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

    /** 活动名称 */
    @Column(name = "campaign_name", nullable = false, length = 200)
    private String campaignName;

    /** 活动类型: PROMOTION / NEW_PRODUCT / SEASONAL / RETENTION / ACQUISITION / BRAND_AWARENESS / FLASH_SALE */
    @Column(name = "campaign_type", nullable = false, length = 30)
    private String campaignType;

    /** 活动描述 (可空) */
    @Column(name = "description", length = 1000)
    private String description;

    /** 活动目标 (可空) */
    @Column(name = "objective", length = 500)
    private String objective;

    /** 目标客群条件 JSON (可空) */
    @Column(name = "target_segment", length = 500)
    private String targetSegment;

    /** 渠道 (逗号分隔: WECHAT/WORK_WECHAT/SMS/EMAIL/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI) */
    @Column(name = "channels", nullable = false, length = 500)
    private String channels;

    /** 开始日期 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 结束日期 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 预算 (默认 0) */
    @Column(name = "budget")
    private Double budget;

    /** 实际花费 (默认 0) */
    @Column(name = "actual_cost")
    private Double actualCost;

    /** 状态: DRAFT / SCHEDULED / RUNNING / PAUSED / COMPLETED / CANCELLED (默认 DRAFT) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 负责人 ID (可空) */
    @Column(name = "manager_id", length = 100)
    private String managerId;

    /** 负责人名称 (可空) */
    @Column(name = "manager_name", length = 100)
    private String managerName;

    /** 优先级 (默认 0, 数字越大越优先) */
    @Column(name = "priority")
    private Integer priority;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 活动指标 JSON (缓存汇总指标, 可空) */
    @Column(name = "metrics_json", columnDefinition = "TEXT")
    private String metricsJson;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
