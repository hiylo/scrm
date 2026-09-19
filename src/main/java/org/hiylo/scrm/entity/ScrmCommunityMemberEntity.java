/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityMemberEntity.java
 * Date : 2026/08/04 08:40:58
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

import java.time.LocalDateTime;

/**
 * SCRM 社群成员实体。
 * <p>
 * 记录社群内单个成员的画像与状态: 群角色 (群主/管理员/成员/访客), 入群方式, 活跃度,
 * 邀请关系, 以及在群状态 (在群/被移除/已退群/禁言)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_community_member", schema = "scrm", indexes = {
        @Index(name = "idx_community_member_community", columnList = "community_id"),
        @Index(name = "idx_community_member_customer", columnList = "customer_id"),
        @Index(name = "idx_community_member_status", columnList = "status"),
        @Index(name = "idx_community_member_active", columnList = "community_id,is_active")
})
@Data
public class ScrmCommunityMemberEntity {

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

    /** 社群 ID (引用 scrm_community.id) */
    @Column(name = "community_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long communityId;

    /** 关联客户 ID (可空, 引用 scrm_customer.id) */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 成员名称 */
    @Column(name = "member_name", nullable = false, length = 200)
    private String memberName;

    /** 群昵称 (可空) */
    @Column(name = "member_alias", length = 200)
    private String memberAlias;

    /** 平台 UID (可空) */
    @Column(name = "platform_uid", length = 200)
    private String platformUid;

    /** 群角色: OWNER/ADMIN/MEMBER/GUEST (默认 MEMBER) */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    /** 入群方式: INVITED/QR_CODE/SEARCH/OTHER (默认 INVITED) */
    @Column(name = "join_type", nullable = false, length = 20)
    private String joinType;

    /** 入群时间 */
    @Column(name = "join_at", nullable = false)
    private LocalDateTime joinAt;

    /** 最后活跃时间 (可空) */
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    /** 消息数 (默认 0) */
    @Column(name = "message_count")
    private Integer messageCount;

    /** 是否活跃 (默认 true) */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** 邀请人 (可空) */
    @Column(name = "invited_by", length = 100)
    private String invitedBy;

    /** 状态: ACTIVE/REMOVED/LEFT/MUTED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 退群时间 (可空) */
    @Column(name = "left_at")
    private LocalDateTime leftAt;
}
