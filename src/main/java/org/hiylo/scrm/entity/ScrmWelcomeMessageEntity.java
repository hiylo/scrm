/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWelcomeMessageEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 企微欢迎语配置实体。
 * <p>
 * 描述新客户添加时的自动欢迎语规则, 支持按账号 / 渠道活码绑定, 支持文本 / 图片 / 链接 /
 * 小程序 / 混合等多种消息类型, 支持变量替换 (昵称 / 时间等) 与生效时段、冷却期。
 * 匹配优先级: 渠道活码 > 账号 > 全局, 同优先级取 priority 高者。
 * </p>
 * <p>
 * messageType:
 * <ul>
 *   <li>TEXT: 纯文本</li>
 *   <li>IMAGE: 图片</li>
 *   <li>LINK: 图文链接</li>
 *   <li>MINIPROGRAM: 小程序</li>
 *   <li>MIXED: 混合 (content + secondaryMessages)</li>
 * </ul>
 * status: ACTIVE / INACTIVE
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_welcome_message", schema = "scrm", indexes = {
        @Index(name = "idx_welcome_message_account", columnList = "account_id"),
        @Index(name = "idx_welcome_message_channel_code", columnList = "channel_code_id"),
        @Index(name = "idx_welcome_message_platform", columnList = "platform_type"),
        @Index(name = "idx_welcome_message_status", columnList = "status")
})
@Data
public class ScrmWelcomeMessageEntity {

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

    /** 规则名称 */
    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    /** 绑定账号 ID（可空, 空=所有账号） */
    @Column(name = "account_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 绑定渠道活码 ID（可空, 空=非渠道来源） */
    @Column(name = "channel_code_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long channelCodeId;

    /** 适用平台（默认 wework） */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /** 消息类型: TEXT / IMAGE / LINK / MINIPROGRAM / MIXED */
    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    /** 文本内容（支持 ${nickname} 等变量） */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 图片/文件 URL（可空） */
    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    /** 链接标题（可空） */
    @Column(name = "link_title", length = 200)
    private String linkTitle;

    /** 链接 URL（可空） */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** 链接描述（可空） */
    @Column(name = "link_desc", length = 500)
    private String linkDesc;

    /** 小程序标题（可空） */
    @Column(name = "miniprogram_title", length = 200)
    private String miniprogramTitle;

    /** 小程序 AppId（可空） */
    @Column(name = "miniprogram_app_id", length = 100)
    private String miniprogramAppId;

    /** 小程序页面路径（可空） */
    @Column(name = "miniprogram_page", length = 500)
    private String miniprogramPage;

    /** 跟进消息序列（可空, JSON 数组, 欢迎语后的二次触达消息） */
    @Column(name = "secondary_messages", columnDefinition = "TEXT")
    private String secondaryMessages;

    /** 延迟发送秒数（默认 0, 立即发送） */
    @Column(name = "delay_seconds")
    private Integer delaySeconds;

    /** 同一客户冷却期分钟数（默认 0, 不限制） */
    @Column(name = "cooldown_minutes")
    private Integer cooldownMinutes;

    /** 生效开始时间 HH:mm（可空, 空=不限时段） */
    @Column(name = "effective_time_start", length = 5)
    private String effectiveTimeStart;

    /** 生效结束时间 HH:mm（可空, 空=不限时段） */
    @Column(name = "effective_time_end", length = 5)
    private String effectiveTimeEnd;

    /** 周末是否生效（默认 true） */
    @Column(name = "weekend_enabled", nullable = false)
    private Boolean weekendEnabled;

    /** 优先级（默认 0, 多规则时取最高者） */
    @Column(name = "priority")
    private Integer priority;

    /** 状态: ACTIVE / INACTIVE */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 触发次数（默认 0） */
    @Column(name = "trigger_count")
    private Integer triggerCount;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
