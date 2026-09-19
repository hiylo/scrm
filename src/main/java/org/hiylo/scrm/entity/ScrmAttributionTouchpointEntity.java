/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionTouchpointEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 营销效果归因触点实体。
 * <p>
 * 记录客户旅程中的单个营销触点事件: 触点类型 {@link #touchpointType}
 * (AD_CLICK / AD_VIEW / EMAIL_OPEN / EMAIL_CLICK / SMS_CLICK / WECHAT_MESSAGE / WEB_VISIT /
 * SEARCH / REFERRAL / SOCIAL_POST / DIRECT / STORE_VISIT / CALL / CONTENT_VIEW),
 * 渠道 {@link #channel} (SEARCH / SOCIAL / EMAIL / SMS / WECHAT / DIRECT / REFERRAL / STORE / AD / OTHER),
 * 关联营销活动 / 内容, 触点时间, UTM 归因参数, 着陆页, 设备与会话信息。
 * </p>
 * <p>
 * {@link #touchpointOrder} 标记同一客户触点链中的顺序 (从 1 开始, 按触点时间升序)。
 * 归因计算后, {@link #isAttributed} 标记该触点是否被纳入归因, {@link #attributionWeight}
 * 记录归因权重 (0-1), {@link #attributionValue} 记录归因分配到的转化价值。
 * {@link #metadata} (TEXT JSON) 承载附加数据。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_attribution_touchpoint", schema = "scrm", indexes = {
        @Index(name = "idx_attribution_touchpoint_customer", columnList = "customer_id"),
        @Index(name = "idx_attribution_touchpoint_type", columnList = "touchpoint_type"),
        @Index(name = "idx_attribution_touchpoint_channel", columnList = "channel"),
        @Index(name = "idx_attribution_touchpoint_campaign", columnList = "campaign_id"),
        @Index(name = "idx_attribution_touchpoint_time", columnList = "touchpoint_time"),
        @Index(name = "idx_attribution_touchpoint_session", columnList = "session_id"),
        @Index(name = "idx_attribution_touchpoint_attributed", columnList = "is_attributed")
})
@Data
public class ScrmAttributionTouchpointEntity {

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
        if (isAttributed == null) {
            isAttributed = false;
        }
        if (attributionWeight == null) {
            attributionWeight = 0.0;
        }
        if (attributionValue == null) {
            attributionValue = 0.0;
        }
        if (touchpointValue == null) {
            touchpointValue = 0.0;
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

    /** 客户名称 (冗余, 便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 触点顺序 (从 1 开始, 按触点时间升序) */
    @Column(name = "touchpoint_order", nullable = false)
    private Integer touchpointOrder;

    /** 触点类型: AD_CLICK/AD_VIEW/EMAIL_OPEN/EMAIL_CLICK/SMS_CLICK/WECHAT_MESSAGE/WEB_VISIT/SEARCH/REFERRAL/SOCIAL_POST/DIRECT/STORE_VISIT/CALL/CONTENT_VIEW */
    @Column(name = "touchpoint_type", nullable = false, length = 50)
    private String touchpointType;

    /** 渠道: SEARCH/SOCIAL/EMAIL/SMS/WECHAT/DIRECT/REFERRAL/STORE/AD/OTHER */
    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    /** 营销活动 ID (可空) */
    @Column(name = "campaign_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 营销活动名称 (可空) */
    @Column(name = "campaign_name", length = 200)
    private String campaignName;

    /** 关联内容 ID (可空) */
    @Column(name = "content_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contentId;

    /** 触点时间 */
    @Column(name = "touchpoint_time", nullable = false)
    private LocalDateTime touchpointTime;

    /** 触点价值 (默认 0) */
    @Column(name = "touchpoint_value")
    private Double touchpointValue;

    /** UTM 来源 (可空) */
    @Column(name = "utm_source", length = 100)
    private String utmSource;

    /** UTM 媒介 (可空) */
    @Column(name = "utm_medium", length = 100)
    private String utmMedium;

    /** UTM 活动 (可空) */
    @Column(name = "utm_campaign", length = 200)
    private String utmCampaign;

    /** UTM 内容 (可空) */
    @Column(name = "utm_content", length = 200)
    private String utmContent;

    /** UTM 关键词 (可空) */
    @Column(name = "utm_term", length = 200)
    private String utmTerm;

    /** 着陆页 (可空) */
    @Column(name = "landing_page", length = 500)
    private String landingPage;

    /** 来源 (可空) */
    @Column(name = "referrer", length = 500)
    private String referrer;

    /** 设备类型 (可空): MOBILE/PC/TABLET/TV/OTHER */
    @Column(name = "device_type", length = 30)
    private String deviceType;

    /** 会话 ID (可空, 用于聚合转化路径) */
    @Column(name = "session_id", length = 200)
    private String sessionId;

    /** JSON 附加数据 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 是否被归因 (默认 false) */
    @Column(name = "is_attributed", nullable = false)
    private Boolean isAttributed;

    /** 归因权重 (默认 0, 0-1) */
    @Column(name = "attribution_weight")
    private Double attributionWeight;

    /** 归因价值 (默认 0, 由转化价值按权重分配) */
    @Column(name = "attribution_value")
    private Double attributionValue;
}
