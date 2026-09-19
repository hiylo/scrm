/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyRuleEntity.java
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
 * SCRM 消息自动回复规则实体。
 * <p>
 * 定义消息自动回复引擎的规则配置, 支持关键词回复 (KEYWORD)、欢迎语 (WELCOME)、
 * 超时回复 (TIMEOUT)、非工作时间回复 (OFFLINE)、表单回复 (FORM)、事件回复 (EVENT)
 * 与条件回复 (CONDITIONAL) 七种规则类型。由 {@code ScrmAutoReplyService} 在收到客户
 * 消息时按 priority 升序评估, 命中后返回 replyContent (或渲染后的模板内容)。
 * </p>
 * <p>
 * keywords 为逗号分隔的关键词列表 (或 REGEX/FUZZY 类型的正则表达式), matchType 决定
 * 匹配方式 (EXACT/CONTAINS/STARTS_WITH/ENDS_WITH/REGEX/FUZZY), matchScope 决定匹配
 * 范围 (MESSAGE/FULL_TEXT/SUBJECT)。workTimeOnly=TRUE 时仅在工作时间触发, 由
 * workTimeStart/workTimeEnd/workDays 定义工作时段。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_auto_reply_rule", schema = "scrm", indexes = {
        @Index(name = "idx_auto_reply_rule_type", columnList = "rule_type"),
        @Index(name = "idx_auto_reply_rule_enabled", columnList = "enabled"),
        @Index(name = "idx_auto_reply_rule_priority", columnList = "priority"),
        @Index(name = "idx_auto_reply_rule_fallback", columnList = "fallback_rule")
})
@Data
public class ScrmAutoReplyRuleEntity {

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
    @Column(name = "rule_name", length = 200, nullable = false)
    private String ruleName;

    /** 规则类型: KEYWORD/WELCOME/TIMEOUT/OFFLINE/FORM/EVENT/CONDITIONAL */
    @Column(name = "rule_type", length = 30, nullable = false)
    private String ruleType;

    /** 规则描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 匹配方式: EXACT/CONTAINS/STARTS_WITH/ENDS_WITH/REGEX/FUZZY（默认 EXACT） */
    @Column(name = "match_type", length = 20, nullable = false)
    private String matchType;

    /** 关键词（逗号分隔）或正则表达式（REGEX/FUZZY 时使用） */
    @Column(name = "keywords", length = 1000)
    private String keywords;

    /** 匹配范围: MESSAGE/FULL_TEXT/SUBJECT（默认 MESSAGE） */
    @Column(name = "match_scope", length = 20, nullable = false)
    private String matchScope;

    /** 回复类型: TEXT/IMAGE/LINK/FILE/TEMPLATE/HTML/RICH_TEXT（默认 TEXT） */
    @Column(name = "reply_type", length = 20, nullable = false)
    private String replyType;

    /** 回复内容 */
    @Column(name = "reply_content", columnDefinition = "TEXT", nullable = false)
    private String replyContent;

    /** 回复模板 ID（replyType=TEMPLATE 时引用 scrm_auto_reply_template.id） */
    @Column(name = "reply_template_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyTemplateId;

    /** 媒体文件 URL（replyType=IMAGE/FILE 时使用） */
    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    /** 链接 URL（replyType=LINK 时使用） */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /** 链接标题 */
    @Column(name = "link_title", length = 200)
    private String linkTitle;

    /** 链接描述 */
    @Column(name = "link_description", length = 500)
    private String linkDescription;

    /** 链接缩略图 URL */
    @Column(name = "link_thumbnail", length = 500)
    private String linkThumbnail;

    /** 适用渠道（逗号分隔: WECHAT/WORK_WECHAT/WEB/APP/SMS/EMAIL） */
    @Column(name = "applicable_channels", length = 500)
    private String applicableChannels;

    /** 适用账号 ID（逗号分隔） */
    @Column(name = "applicable_accounts", length = 500)
    private String applicableAccounts;

    /** 仅工作时间触发（默认 FALSE） */
    @Column(name = "work_time_only", nullable = false)
    private Boolean workTimeOnly;

    /** 工作时间开始（HH:mm） */
    @Column(name = "work_time_start", length = 10)
    private String workTimeStart;

    /** 工作时间结束（HH:mm） */
    @Column(name = "work_time_end", length = 10)
    private String workTimeEnd;

    /** 工作日（1-7 逗号分隔, 1=周一） */
    @Column(name = "work_days", length = 50)
    private String workDays;

    /** 超时秒数（TIMEOUT 类型使用） */
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    /** 优先级（数字越小越优先, 默认 0） */
    @Column(name = "priority")
    private Integer priority;

    /** 每客户最大触发次数（0=无限） */
    @Column(name = "max_trigger_per_customer")
    private Integer maxTriggerPerCustomer;

    /** 冷却时间（分钟, 0=无冷却） */
    @Column(name = "cooldown_minutes")
    private Integer cooldownMinutes;

    /** 是否兜底规则（默认 FALSE, 兜底规则在无其他规则匹配时触发） */
    @Column(name = "fallback_rule", nullable = false)
    private Boolean fallbackRule;

    /** 是否启用（默认 TRUE） */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 触发次数（命中累计） */
    @Column(name = "trigger_count")
    private Integer triggerCount;

    /** 最近触发时间 */
    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
