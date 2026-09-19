/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityEntity.java
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
 * SCRM 社群实体。
 * <p>
 * 描述一个社群/群组的元信息与运营指标: 平台来源 (微信/企微/QQ/Discord 等), 群类型
 * (客户/粉丝/VIP/区域/兴趣/产品), 群主与管理员, 实时成员数与活跃度评分。
 * activityScore 取值 0-100, 由 {@code ScrmCommunityService.updateActivityScore} 综合消息数 /
 * 活跃成员 / 新增成员计算刷新。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_community", schema = "scrm", indexes = {
        @Index(name = "idx_community_platform_type", columnList = "platform_type"),
        @Index(name = "idx_community_type", columnList = "community_type"),
        @Index(name = "idx_community_status", columnList = "status"),
        @Index(name = "idx_community_owner", columnList = "owner_id")
})
@Data
public class ScrmCommunityEntity {

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

    /** 社群名称 */
    @Column(name = "community_name", nullable = false, length = 200)
    private String communityName;

    /** 平台类型: WECHAT/WORK_WECHAT/QQ/DISCORD/OTHER */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /** 社群类型: CUSTOMER/FAN/VIP/REGION/INTEREST/PRODUCT */
    @Column(name = "community_type", nullable = false, length = 30)
    private String communityType;

    /** 平台群 ID (可空, 平台侧群标识) */
    @Column(name = "room_id", length = 200)
    private String roomId;

    /** 群二维码 URL (可空) */
    @Column(name = "qr_code", length = 500)
    private String qrCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 群主 ID */
    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    /** 群主名称 (可空) */
    @Column(name = "owner_name", length = 100)
    private String ownerName;

    /** 群管理员 ID (可空) */
    @Column(name = "manager_id", length = 100)
    private String managerId;

    /** 群管理员名称 (可空) */
    @Column(name = "manager_name", length = 100)
    private String managerName;

    /** 成员数 (默认 0) */
    @Column(name = "member_count")
    private Integer memberCount;

    /** 最大成员数 (默认 500) */
    @Column(name = "max_members")
    private Integer maxMembers;

    /** 活跃成员数 (默认 0) */
    @Column(name = "active_members")
    private Integer activeMembers;

    /** 今日新增成员数 (默认 0) */
    @Column(name = "today_new_members")
    private Integer todayNewMembers;

    /** 今日消息数 (默认 0) */
    @Column(name = "today_messages")
    private Integer todayMessages;

    /** 活跃度评分 0-100 (默认 0) */
    @Column(name = "activity_score")
    private Double activityScore;

    /** 标签 (逗号分隔, 可空) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 状态: ACTIVE/INACTIVE/DISSOLVED (默认 ACTIVE) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 建群时间 (可空) */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
