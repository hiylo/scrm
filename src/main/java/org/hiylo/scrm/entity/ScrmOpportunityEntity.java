/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpportunityEntity.java
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
 * SCRM 商机实体。
 * <p>
 * 描述一笔销售商机, 关联客户与漏斗, 当前所处阶段由 currentStageId 标识。
 * 通过 {@link ScrmOpportunityStageHistoryEntity} 记录阶段推进轨迹。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_opportunity", schema = "scrm", indexes = {
        @Index(name = "idx_opportunity_customer_id", columnList = "customer_id"),
        @Index(name = "idx_opportunity_funnel_id", columnList = "funnel_id"),
        @Index(name = "idx_opportunity_stage_id", columnList = "current_stage_id"),
        @Index(name = "idx_opportunity_status", columnList = "status"),
        @Index(name = "idx_opportunity_owner", columnList = "owner_user_id")
})
@Data
public class ScrmOpportunityEntity {

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

    /** 商机名称 */
    @Column(name = "opportunity_name", nullable = false, length = 200)
    private String opportunityName;

    /** 关联客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 所属漏斗 ID */
    @Column(name = "funnel_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long funnelId;

    /** 当前阶段 ID */
    @Column(name = "current_stage_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentStageId;

    /** 商机金额 */
    @Column(name = "amount")
    private Double amount;

    /** 预计成交日期 */
    @Column(name = "expected_close_date")
    private LocalDateTime expectedCloseDate;

    /** 成交概率 (0-100) */
    @Column(name = "probability")
    private Integer probability;

    /** 负责人用户 ID */
    @Column(name = "owner_user_id", nullable = false, length = 100)
    private String ownerUserId;

    /** 商机状态：OPEN / WON / LOST / STALLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 商机来源 */
    @Column(name = "source", length = 100)
    private String source;

    /** 竞争对手 */
    @Column(name = "competitor", length = 200)
    private String competitor;

    /** 备注 */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** 成交时间 */
    @Column(name = "won_at")
    private LocalDateTime wonAt;

    /** 输单时间 */
    @Column(name = "lost_at")
    private LocalDateTime lostAt;

    /** 输单原因 */
    @Column(name = "lost_reason", length = 500)
    private String lostReason;
}
