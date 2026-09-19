/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionResultEntity.java
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
 * SCRM 质检结果实体。
 * <p>
 * 单会话质检明细, 记录总分、是否通过、各规则评分明细 (ruleResults JSON) 与改进建议。
 * taskId 为空表示单会话即时质检, 非空表示由质检任务批量产生。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_quality_inspection_result", schema = "scrm", indexes = {
        @Index(name = "idx_qi_result_task", columnList = "task_id"),
        @Index(name = "idx_qi_result_conv", columnList = "conversation_id"),
        @Index(name = "idx_qi_result_assignee", columnList = "assignee_id"),
        @Index(name = "idx_qi_result_inspected", columnList = "inspected_at")
})
@Data
public class ScrmQualityInspectionResultEntity {

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

    /** 乐观锁版本号（并发更新保护，后写入者触发 OptimisticLockException） */
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
        if (inspectorType == null || inspectorType.isBlank()) {
            inspectorType = "AI";
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

    /** 关联质检任务 (可空, 单会话质检时为空) */
    @Column(name = "task_id")
    private Long taskId;

    /** 关联会话 ID */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** 客户 ID（可空） */
    @Column(name = "customer_id")
    private Long customerId;

    /** 客户名称（可空） */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 被质检人 ID（账号 ID 字符串） */
    @Column(name = "assignee_id", length = 100)
    private String assigneeId;

    /** 被质检人名称 */
    @Column(name = "assignee_name", length = 100)
    private String assigneeName;

    /** 质检总分 (0-100) */
    @Column(name = "total_score", nullable = false)
    private Double totalScore;

    /** 是否通过 */
    @Column(name = "passed", nullable = false)
    private Boolean passed;

    /** 各规则质检结果 JSON: [{ruleId,ruleName,category,score,passed,detail}] */
    @Column(name = "rule_results", nullable = false, columnDefinition = "TEXT")
    private String ruleResults;

    /** 发现的问题列表 JSON（可空） */
    @Column(name = "issues_found", columnDefinition = "TEXT")
    private String issuesFound;

    /** 改进建议（可空） */
    @Column(name = "suggestions", length = 2000)
    private String suggestions;

    /** 质检时间 */
    @Column(name = "inspected_at", nullable = false)
    private LocalDateTime inspectedAt;

    /** 质检人类型: AI/MANUAL (默认 AI) */
    @Column(name = "inspector_type", nullable = false, length = 20)
    private String inspectorType;

    /** 质检人 ID（可空） */
    @Column(name = "inspector_id", length = 100)
    private String inspectorId;
}
