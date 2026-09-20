/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPublicSeaCustomerEntity.java
 * Date : 2026/07/27 02:41:22
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
 * 客户公海池实体。
 * <p>
 * 公海池集中管理来自各渠道的线索, 支持销售自主领取、管理员分配、超时回收与转移。
 * 通过 {@code lifecycle} (生命周期) 与 {@code status} (公海状态) 双状态字段刻画
 * 线索在公海池中的流转过程:
 * <ul>
 *   <li>{@code status=AVAILABLE}: 可被领取/分配</li>
 *   <li>{@code status=ASSIGNED}: 已分配给销售</li>
 *   <li>{@code status=LOCKED}: 临时锁定 (如正在跟进)</li>
 *   <li>{@code status=RECALLED}: 已被回收 (可再次分配)</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_public_sea_customer", schema = "scrm", indexes = {
        @Index(name = "idx_public_sea_platform_uid", columnList = "platform_type,platform_customer_uid"),
        @Index(name = "idx_public_sea_assigned_to", columnList = "assigned_to"),
        @Index(name = "idx_public_sea_status", columnList = "status"),
        @Index(name = "idx_public_sea_lifecycle", columnList = "lifecycle"),
        @Index(name = "idx_public_sea_expire", columnList = "assignment_expire_at")
})
@Data
public class ScrmPublicSeaCustomerEntity {

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
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /** 平台类型 (wework/douyin/kuaishou/xiaohongshu/bilibili/wechat_personal) */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /** 平台客户唯一标识 */
    @Column(name = "platform_customer_uid", nullable = false, length = 200)
    private String platformCustomerUid;

    /** 客户昵称 */
    @Column(name = "nickname", length = 200)
    private String nickname;

    /** 客户头像 URL */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** 来源渠道 (直播/广告/搜索/裂变等) */
    @Column(name = "source_channel", length = 100)
    private String sourceChannel;

    /** 来源渠道码 ID (可空, 关联渠道码记录) */
    @Column(name = "source_channel_code_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceChannelCodeId;

    /** 生命周期: NEW/PROSPECT/ACTIVE/DORMANT/CHURNED */
    @Column(name = "lifecycle", nullable = false, length = 20)
    private String lifecycle;

    /** 客户标签 (逗号分隔) */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 备注 */
    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    /** 当前归属人 userId (可空, AVAILABLE 状态时为空) */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** 最近一次分配时间 */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** 分配过期时间 (超时自动回收) */
    @Column(name = "assignment_expire_at")
    private LocalDateTime assignmentExpireAt;

    /** 被回收次数 (累计, 用于评估线索质量) */
    @Column(name = "recall_count", nullable = false)
    private Integer recallCount;

    /** 最近一次分配时间 (用于分配历史) */
    @Column(name = "last_assigned_at")
    private LocalDateTime lastAssignedAt;

    /** 公海状态: AVAILABLE/ASSIGNED/LOCKED/RECALLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
