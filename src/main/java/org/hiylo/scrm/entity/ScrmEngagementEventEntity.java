/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementEventEntity.java
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
 * SCRM 互动行为事件实体。
 * <p>
 * 记录每一次客户互动行为: 关联客户 {@link #customerId}, 行为类型 {@link #behaviorType},
 * 发生渠道 {@link #channel}, 发生时间 {@link #eventTime} (业务时间, 可早于入库时间用于补录),
 * 命中规则 {@link #ruleId}, 经规则计算后的得分 {@link #points}。
 * </p>
 * <p>
 * {@link #processed} 标记事件是否已计入评分 (默认 true, 由 {@code recordEvent} 写入时同步计算)。
 * {@link #metadata} 以 JSON 字符串承载附加数据 (如表单字段、订单号等), 类型为 TEXT。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_engagement_event", schema = "scrm", indexes = {
        @Index(name = "idx_engagement_event_customer", columnList = "customer_id"),
        @Index(name = "idx_engagement_event_behavior", columnList = "behavior_type"),
        @Index(name = "idx_engagement_event_channel", columnList = "channel"),
        @Index(name = "idx_engagement_event_time", columnList = "event_time"),
        @Index(name = "idx_engagement_event_rule", columnList = "rule_id")
})
@Data
public class ScrmEngagementEventEntity {

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

    /** 冗余客户名称 */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 行为类型: PAGE_VIEW/MESSAGE_SEND/MESSAGE_REPLY/CALL/EMAIL_OPEN/EMAIL_CLICK/LINK_CLICK/FORM_SUBMIT/PURCHASE/SHARE/FAVORITE/COMMENT/LOGIN/SEARCH/DOWNLOAD/APPOINTMENT */
    @Column(name = "behavior_type", nullable = false, length = 50)
    private String behaviorType;

    /** 发生渠道: WECHAT/WEB/APP/EMAIL/PHONE/STORE/OTHER (可空) */
    @Column(name = "channel", length = 30)
    private String channel;

    /** 事件发生时间 (业务时间, 可早于入库时间用于补录) */
    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    /** 得分 (命中规则后计算得出, 默认 0) */
    @Column(name = "points", nullable = false)
    private Integer points;

    /** 命中规则 ID (可空, 表示未匹配到规则) */
    @Column(name = "rule_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 会话 ID (可空) */
    @Column(name = "session_id", length = 200)
    private String sessionId;

    /** 页面 URL (可空) */
    @Column(name = "page_url", length = 500)
    private String pageUrl;

    /** 来源 (可空) */
    @Column(name = "referrer", length = 500)
    private String referrer;

    /** User-Agent (可空) */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** 设备类型: MOBILE/PC/TABLET/OTHER (可空) */
    @Column(name = "device_type", length = 30)
    private String deviceType;

    /** 地理位置 (可空) */
    @Column(name = "location", length = 200)
    private String location;

    /** JSON 附加数据 (可空, 如表单字段、订单号等) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 客户端 IP (可空) */
    @Column(name = "ip", length = 100)
    private String ip;

    /** 是否已计入评分 (默认 true, 由 recordEvent 同步计算) */
    @Column(name = "processed", nullable = false)
    private Boolean processed;
}
