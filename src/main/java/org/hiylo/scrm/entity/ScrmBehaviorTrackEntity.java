/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBehaviorTrackEntity.java
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
 * SCRM 客户行为轨迹实体。
 * <p>
 * 记录客户在各触点的单次行为事件: 行为类型 (浏览/点击/搜索/加购/下单/支付等)、触点
 * (网站/APP/企微/抖音等)、页面上下文、设备环境、UTM 归因参数与转化标记。
 * {@link #metadata} (TEXT JSON) 承载附加数据 (productId/searchKeyword/formFields 等)。
 * </p>
 * <p>
 * {@link #isConversion} 标记是否为转化行为, {@link #conversionValue} 记录转化价值,
 * {@link #funnelStage} 标记行为所处漏斗阶段 (AWARENESS/INTEREST/DESIRE/ACTION/RETENTION)。
 * 同一会话 ({@link #sessionId}) 内的事件可聚合为行为路径。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_behavior_track", schema = "scrm", indexes = {
        @Index(name = "idx_behavior_track_customer", columnList = "customer_id"),
        @Index(name = "idx_behavior_track_type", columnList = "behavior_type"),
        @Index(name = "idx_behavior_track_touchpoint", columnList = "touchpoint"),
        @Index(name = "idx_behavior_track_time", columnList = "behavior_time"),
        @Index(name = "idx_behavior_track_session", columnList = "session_id"),
        @Index(name = "idx_behavior_track_conversion", columnList = "is_conversion"),
        @Index(name = "idx_behavior_track_funnel", columnList = "funnel_stage")
})
@Data
public class ScrmBehaviorTrackEntity {

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
        if (isConversion == null) {
            isConversion = false;
        }
        if (conversionValue == null) {
            conversionValue = 0.0;
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

    /** 关联账号 ID (可空) */
    @Column(name = "account_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 行为类型: PAGE_VIEW/CLICK/SCROLL/SEARCH/FORM_SUBMIT/VIDEO_PLAY/VIDEO_COMPLETE/SHARE/FAVORITE/COMMENT/PURCHASE/ADD_TO_CART/REMOVE_FROM_CART/CHECKOUT/PAYMENT/LOGIN/LOGOUT/DOWNLOAD/UPLOAD/CALL/MESSAGE_SEND/MESSAGE_READ/APPOINTMENT/CANCEL/REFUND/REVIEW */
    @Column(name = "behavior_type", nullable = false, length = 50)
    private String behaviorType;

/** 触点: WEBSITE/APP/WECHAT_OFFICIAL/WECHAT_MINI/WORK_WECHAT/DOUYIN/KUAISHOU/XIAOHONGSHU/STORE/PHONE/EMAIL/SMS/OTHER
         * */
    @Column(name = "touchpoint", nullable = false, length = 50)
    private String touchpoint;

    /** 页面 URL (可空) */
    @Column(name = "page_url", length = 500)
    private String pageUrl;

    /** 页面标题 (可空) */
    @Column(name = "page_title", length = 200)
    private String pageTitle;

    /** 来源 (可空) */
    @Column(name = "referrer", length = 500)
    private String referrer;

    /** 行为发生时间 */
    @Column(name = "behavior_time", nullable = false)
    private LocalDateTime behaviorTime;

    /** 停留时长 (秒, 可空) */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** 设备类型: MOBILE/PC/TABLET/TV/OTHER (可空) */
    @Column(name = "device_type", length = 30)
    private String deviceType;

    /** 操作系统 (可空) */
    @Column(name = "os", length = 50)
    private String os;

    /** 浏览器 (可空) */
    @Column(name = "browser", length = 100)
    private String browser;

    /** 应用版本 (可空) */
    @Column(name = "app_version", length = 50)
    private String appVersion;

    /** 客户端 IP (可空) */
    @Column(name = "ip", length = 100)
    private String ip;

    /** 地理位置 (可空) */
    @Column(name = "location", length = 200)
    private String location;

    /** 会话 ID (可空, 用于聚合行为路径) */
    @Column(name = "session_id", length = 200)
    private String sessionId;

    /** JSON 附加数据 (可空): {productId, searchKeyword, formFields, ...} */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

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

    /** 转化价值 (默认 0) */
    @Column(name = "conversion_value")
    private Double conversionValue;

    /** 是否转化行为 (默认 false) */
    @Column(name = "is_conversion", nullable = false)
    private Boolean isConversion;

    /** 漏斗阶段: AWARENESS/INTEREST/DESIRE/ACTION/RETENTION (可空) */
    @Column(name = "funnel_stage", length = 30)
    private String funnelStage;
}
