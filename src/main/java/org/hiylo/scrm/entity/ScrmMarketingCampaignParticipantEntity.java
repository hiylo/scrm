/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignParticipantEntity.java
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

import java.time.LocalDateTime;

/**
 * SCRM 营销活动参与者实体。
 * <p>
 * 记录单个客户在某一渠道下的活动参与轨迹: {@link #actions} (JSON 数组: [VIEW/CLICK/SHARE/
 * PURCHASE/COMMENT]) 描述参与动作序列, {@link #converted} 标记是否转化, {@link #conversionValue}
 * 记录转化金额。参与记录是活动 ROI 与渠道效果分析的数据基础。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_marketing_campaign_participant", schema = "scrm", indexes = {
        @Index(name = "idx_mkt_participant_campaign", columnList = "campaign_id"),
        @Index(name = "idx_mkt_participant_customer", columnList = "customer_id"),
        @Index(name = "idx_mkt_participant_channel", columnList = "channel"),
        @Index(name = "idx_mkt_participant_converted", columnList = "converted")
})
@Data
public class ScrmMarketingCampaignParticipantEntity {

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

    /** 活动 ID */
    @Column(name = "campaign_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 参与渠道: WECHAT / WORK_WECHAT / SMS / EMAIL / DOUYIN / KUAISHOU / XIAOHONGSHU / BILIBILI */
    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    /** 参与时间 */
    @Column(name = "participated_at", nullable = false)
    private LocalDateTime participatedAt;

    /** 参与动作 JSON 数组: [VIEW / CLICK / SHARE / PURCHASE / COMMENT] */
    @Column(name = "actions", length = 500)
    private String actions;

    /** 是否转化 (默认 FALSE) */
    @Column(name = "converted", nullable = false)
    private Boolean converted;

    /** 转化时间 (可空) */
    @Column(name = "converted_at")
    private LocalDateTime convertedAt;

    /** 转化金额 (默认 0) */
    @Column(name = "conversion_value")
    private Double conversionValue;

    /** 回复内容 (可空) */
    @Column(name = "response_content", length = 500)
    private String responseContent;
}
