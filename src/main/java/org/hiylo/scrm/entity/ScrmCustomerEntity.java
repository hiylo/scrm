/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerEntity.java
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
 * SCRM 客户实体。
 * <p>
 * 描述平台上的一个客户，归属到某个运营账号，可选绑定人设。
 * 通过 lifecycle 字段记录客户生命周期阶段。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer", schema = "scrm", indexes = {
        @Index(name = "idx_customer_platform_uid", columnList = "platform_type,platform_customer_uid"),
        @Index(name = "idx_customer_owner", columnList = "owner_account_id"),
        @Index(name = "idx_customer_lifecycle", columnList = "lifecycle")
})
@Data
public class ScrmCustomerEntity {

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

    /**
     * 平台类型（联系方式之一）。
     * <p>wework / wechat_personal / douyin / kuaishou / xiaohongshu / bilibili。
     * 纯电话或线下沟通、尚未加任何平台好友的联系人为空。</p>
     */
    @Column(name = "platform_type", length = 30)
    private String platformType;

    /**
     * 平台账号唯一标识（wxid / 企微 userid / openid 等）。
     * <p><b>不可填手机号</b>——手机号是独立字段 {@link #phone}；两者并存不互斥。</p>
     */
    @Column(name = "platform_customer_uid", length = 200)
    private String platformCustomerUid;

    /** 客户昵称 */
    @Column(name = "nickname", length = 200)
    private String nickname;

    /** 客户头像 URL */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** 归属账号 ID（引用 scrm_account.id） */
    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    /** 绑定人设 ID（可空） */
    @Column(name = "persona_id", length = 100)
    private String personaId;

    /** 生命周期：NEW / ACTIVE / DORMANT / LOST */
    @Column(name = "lifecycle", nullable = false, length = 20)
    private String lifecycle;

    /** 最后交互时间 */
    @Column(name = "last_interaction_at")
    private LocalDateTime lastInteractionAt;

    /** 下次跟进时间 (用于跟进提醒调度) */
    @Column(name = "next_follow_up_at")
    private LocalDateTime nextFollowUpAt;

    /** 客户备注 (业务员私有备注, 仅当前账号可见) */
    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    // ==================== 公司主体与联系方式（V14）====================

    /**
     * 所属公司主体 ID（引用 {@code scrm_company.id}）。
     * <p>一个联系人只属一家公司；为空表示尚未关联主体（待认领）。</p>
     */
    @Column(name = "company_id")
    private Long companyId;

    /**
     * 在公司内的角色。
     * <p>BOSS(老板/法人) / FINANCE(财务) / CASHIER(出纳) / ADMIN(行政) /
     * AGENT(经办/中介) / OTHER。</p>
     */
    @Column(name = "contact_role", length = 30)
    private String contactRole;

    /**
     * 手机号（独立字段）。
     * <p>与平台账号并存不互斥：微信好友也可留手机号，纯电话客户可以没有平台账号。
     * 作为撞单检测与按号码检索的依据，写入前请归一化为纯数字（可带 +86）。</p>
     */
    @Column(name = "phone", length = 32)
    private String phone;

    /**
     * 获客渠道（客户「从哪来」）。
     * <p>抖音广告 / 朋友介绍 / 地推 / 官网 / 渠道活码等。<b>与联系方式是不同维度</b>：
     * 联系方式见 {@link #platformType} 与 {@link #phone}。</p>
     */
    @Column(name = "source_channel", length = 100)
    private String sourceChannel;

    /** 来源渠道活码 ID（引用 {@code scrm_channel_code.id}） */
    @Column(name = "source_channel_code_id")
    private Long sourceChannelCodeId;
}
