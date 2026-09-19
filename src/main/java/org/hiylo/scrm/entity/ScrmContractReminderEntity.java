/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractReminderEntity.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * SCRM 合同提醒实体。
 * <p>
 * 合同提醒封装到期/续约/付款/复评/自定义等提醒场景, 支持多渠道 (邮件/短信/企微/APP) 发送、
 * 重复提醒配置与需操作行为 (通知/续约/复评/审批)。提醒由定时任务扫描并模拟发送,
 * 支持批量发送与待发送查询, 支持标记已处理。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_contract_reminder", schema = "scrm", indexes = {
        @Index(name = "idx_contract_reminder_contract", columnList = "contract_id"),
        @Index(name = "idx_contract_reminder_status", columnList = "status"),
        @Index(name = "idx_contract_reminder_date", columnList = "reminder_date"),
        @Index(name = "idx_contract_reminder_pending", columnList = "status,reminder_date")
})
@Data
public class ScrmContractReminderEntity {

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

    /** 合同 ID */
    @Column(name = "contract_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 提醒类型: EXPIRY / PAYMENT / RENEWAL / REVIEW / CUSTOM */
    @Column(name = "reminder_type", nullable = false, length = 30)
    private String reminderType;

    /** 提醒日期 */
    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    /** 提醒时间 (可空) */
    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    /** 提醒标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 提醒消息 (可空) */
    @Column(name = "message", length = 1000)
    private String message;

    /** 接收人逗号分隔 (可空) */
    @Column(name = "recipients", length = 500)
    private String recipients;

    /** 渠道逗号分隔: EMAIL / SMS / WECHAT / APP (可空) */
    @Column(name = "channels", length = 200)
    private String channels;

    /** 状态: PENDING / SENT / FAILED / CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 发送时间 (可空) */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** 已发送次数 */
    @Column(name = "sent_count", nullable = false)
    private Integer sentCount;

    /** 失败次数 */
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;

    /** 响应次数 */
    @Column(name = "response_count", nullable = false)
    private Integer responseCount;

    /** 是否重复提醒 */
    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring;

    /** JSON 重复配置 (可空) */
    @Column(name = "recurring_config", columnDefinition = "TEXT")
    private String recurringConfig;

    /** 需要操作: NOTIFY / RENEW / REVIEW / APPROVE / NONE */
    @Column(name = "action_required", nullable = false, length = 20)
    private String actionRequired;

    /** 操作 URL (可空) */
    @Column(name = "action_url", length = 500)
    private String actionUrl;

    /** 是否已处理 */
    @Column(name = "action_taken", nullable = false)
    private Boolean actionTaken;

    /** 处理时间 (可空) */
    @Column(name = "action_taken_at")
    private LocalDateTime actionTakenAt;

    /** 处理人 (可空) */
    @Column(name = "action_taken_by", length = 100)
    private String actionTakenBy;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
