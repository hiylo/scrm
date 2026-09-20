/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentMemberEntity.java
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
 * SCRM 客户分群成员实体。
 * <p>
 * 记录客户 ({@link #customerId}) 在分群 ({@link #segmentId}) 中的成员关系。
 * 动态分群通过 {@link #isCurrentMember} 标记当前成员, 当客户不再满足分群条件时置为
 * false 并记录 {@link #leftAt}, 用于分群成员流失追踪。
 * </p>
 * <p>
 * {@link #matchScore} 标注条件匹配分数 (0-1), {@link #matchDetails} 记录哪些条件匹配
 * (JSON 详情)。{@link #source} 标注成员来源 (AUTO 自动计算 / MANUAL 手动添加 / IMPORT 导入)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_segment_member", schema = "scrm", indexes = {
        @Index(name = "idx_segment_member_segment", columnList = "segment_id"),
        @Index(name = "idx_segment_member_customer", columnList = "customer_id"),
        @Index(name = "idx_segment_member_unique", columnList = "segment_id,customer_id", unique = true),
        @Index(name = "idx_segment_member_current", columnList = "is_current_member")
})
@Data
public class ScrmSegmentMemberEntity {

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

    /** 分群 ID */
    @Column(name = "segment_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long segmentId;

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 客户等级 (冗余, 便于列表展示) */
    @Column(name = "customer_level", length = 50)
    private String customerLevel;

    /** 加入时间 */
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    /** 离开时间 (可空, 用于动态分群成员流失追踪) */
    @Column(name = "left_at")
    private LocalDateTime leftAt;

    /** 匹配分数 (0-1, 默认 1.0) */
    @Column(name = "match_score")
    private Double matchScore;

    /** 匹配详情 (JSON, 记录哪些条件匹配) */
    @Column(name = "match_details", columnDefinition = "TEXT")
    private String matchDetails;

    /** 是否当前成员 (默认 true, 动态分群流失后置 false) */
    @Column(name = "is_current_member", nullable = false)
    private Boolean isCurrentMember;

    /** 来源: AUTO 自动计算 / MANUAL 手动添加 / IMPORT 导入 (默认 AUTO) */
    @Column(name = "source", nullable = false, length = 20)
    private String source;
}
