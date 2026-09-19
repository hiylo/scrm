/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpportunityStageHistoryEntity.java
 * Date : 2026/07/29 21:19:51
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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;
import jakarta.persistence.PreUpdate;

/**
 * SCRM 商机阶段变更历史实体。
 * <p>
 * 每次商机阶段推进时由 {@code ScrmOpportunityService.changeStage} 写入一条记录,
 * 记录变更前后阶段、操作人、备注与停留天数, 支撑漏斗转化分析与阶段停留时长统计。
 * 首次进入阶段时 fromStageId 为空。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_opportunity_stage_history", schema = "scrm", indexes = {
        @Index(name = "idx_opp_stage_hist_opp_id", columnList = "opportunity_id"),
        @Index(name = "idx_opp_stage_hist_changed_at", columnList = "changed_at")
})
@Data
public class ScrmOpportunityStageHistoryEntity {

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 商机 ID */
    @Column(name = "opportunity_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long opportunityId;

    /** 变更前阶段 ID (首次为空) */
    @Column(name = "from_stage_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromStageId;

    /** 变更后阶段 ID */
    @Column(name = "to_stage_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toStageId;

    /** 变更操作人用户 ID */
    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    /** 变更时间 */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** 变更备注 */
    @Column(name = "note", length = 500)
    private String note;

    /** 在上一阶段停留天数 */
    @Column(name = "duration_days")
    private Integer durationDays;

    /**
     * 插入前自动设置变更时间。
     */
    @PrePersist
    protected void onCreate() {
        if (this.changedAt == null) {
            this.changedAt = LocalDateTime.now();
        }
    }

    /** 创建时间 (内存字段: 本表无 created_at 列, 审计时间由 changed_at 承担) */
    @Transient
    private LocalDateTime createdAt;

}
