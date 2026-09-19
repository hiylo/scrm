/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyStepEntity.java
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
 * SCRM 旅程步骤实体。
 * <p>
 * 描述旅程中的一个步骤, 按 stepOrder 升序排列。stepType 决定步骤动作:
 * <ul>
 *   <li>SEND_MESSAGE 发送消息 (config: messageTemplateId / content / delayMinutes)</li>
 *   <li>WAIT 等待 (config: durationHours)</li>
 *   <li>CONDITION 条件分支 (config: field / operator / value / trueNextStep / falseNextStep)</li>
 *   <li>ADD_TAG 打标签 (config: tagIds)</li>
 *   <li>SET_LIFECYCLE 改生命周期 (config: lifecycle)</li>
 *   <li>WEBHOOK 回调通知 (config: url / method / body)</li>
 *   <li>END 结束旅程</li>
 * </ul>
 * CONDITION 类型步骤的下一步由 config 中的 trueNextStep / falseNextStep 决定,
 * 其他类型步骤的下一步由 nextStepId 字段决定。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_journey_step", schema = "scrm", indexes = {
        @Index(name = "idx_journey_step_journey_id", columnList = "journey_id"),
        @Index(name = "idx_journey_step_order", columnList = "journey_id,step_order")
})
@Data
public class ScrmJourneyStepEntity {

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

    /** 所属旅程 ID */
    @Column(name = "journey_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long journeyId;

    /** 步骤名称 */
    @Column(name = "step_name", nullable = false, length = 200)
    private String stepName;

    /** 步骤类型: SEND_MESSAGE / WAIT / CONDITION / ADD_TAG / SET_LIFECYCLE / WEBHOOK / END */
    @Column(name = "step_type", nullable = false, length = 30)
    private String stepType;

    /** 步骤顺序 (数字越小越靠前) */
    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    /** 步骤配置 (JSON, 内容随 stepType 变化) */
    @Column(name = "config", nullable = false, columnDefinition = "TEXT")
    private String config;

    /** 下一步 ID (CONDITION 类型由 config 中的 trueNextStep / falseNextStep 决定) */
    @Column(name = "next_step_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long nextStepId;

    /** 是否为入口步骤 (客户入旅程后从此步骤开始) */
    @Column(name = "is_entry_point", nullable = false)
    private Boolean isEntryPoint;

    /** 步骤描述 */
    @Column(name = "description", length = 500)
    private String description;
}
