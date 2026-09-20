/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFunnelStageEntity.java
 * Date : 2026/07/29 21:19:51
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
 * SCRM 销售漏斗阶段实体。
 * <p>
 * 描述漏斗中的一个阶段, 按 stageOrder 升序排列。isClosedStage=true 表示成单阶段
 * (商机进入此阶段时 status 置 WON), isLostStage=true 表示输单阶段 (status 置 LOST)。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_funnel_stage", schema = "scrm", indexes = {
        @Index(name = "idx_funnel_stage_funnel_id", columnList = "funnel_id"),
        @Index(name = "idx_funnel_stage_order", columnList = "funnel_id,stage_order")
})
@Data
public class ScrmFunnelStageEntity {

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

    /** 所属漏斗 ID */
    @Column(name = "funnel_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long funnelId;

    /** 阶段名称 */
    @Column(name = "stage_name", nullable = false, length = 100)
    private String stageName;

    /** 阶段顺序 (数字越小越靠前) */
    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder;

    /** 阶段描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 进入条件 (业务描述) */
    @Column(name = "enter_condition", length = 500)
    private String enterCondition;

    /** 退出条件 (业务描述) */
    @Column(name = "exit_condition", length = 500)
    private String exitCondition;

    /** 是否为成单阶段 (商机进入此阶段时 status 置 WON) */
    @Column(name = "is_closed_stage", nullable = false)
    private Boolean isClosedStage;

    /** 是否为输单阶段 (商机进入此阶段时 status 置 LOST) */
    @Column(name = "is_lost_stage", nullable = false)
    private Boolean isLostStage;

    /** 成交概率 (0-100) */
    @Column(name = "probability")
    private Integer probability;
}
