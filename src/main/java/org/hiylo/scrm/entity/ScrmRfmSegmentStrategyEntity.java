/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmSegmentStrategyEntity.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM RFM 分群策略实体。
 * <p>
 * 针对指定分群大类或具体 RFM 编码定义运营策略: 策略类型 (留存/激活/挽回/升级/维持)
 * 与推荐动作列表 (JSON 数组), 供前端按客户分群展示对应运营建议。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_rfm_segment_strategy", schema = "scrm", indexes = {
        @Index(name = "idx_rfm_strategy_category", columnList = "segment_category"),
        @Index(name = "idx_rfm_strategy_enabled", columnList = "enabled")
})
@Data
public class ScrmRfmSegmentStrategyEntity {

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

    /** 策略名称 */
    @Column(name = "strategy_name", nullable = false, length = 200)
    private String strategyName;

    /** 目标分群大类: CHAMPION/LOYAL/POTENTIAL/NEW/AT_RISK/LOST/HIBERNATING/NORMAL */
    @Column(name = "segment_category", nullable = false, length = 30)
    private String segmentCategory;

    /** 目标 RFM 编码 (可空, 为空表示该大类所有编码) */
    @Column(name = "segment_code", length = 20)
    private String segmentCode;

    /** 策略类型: RETAIN 留存 / ACTIVATE 激活 / RECOVER 挽回 / UPGRADE 升级 / MAINTAIN 维持 */
    @Column(name = "strategy_type", nullable = false, length = 30)
    private String strategyType;

    /** 策略描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 推荐动作列表 (JSON 数组, 如 [{action:"SEND_MESSAGE", params:{}}]) */
    @Column(name = "actions", nullable = false, columnDefinition = "TEXT")
    private String actions;

    /** 是否启用 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 优先级 (数字越大越优先) */
    @Column(name = "priority")
    private Integer priority;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
