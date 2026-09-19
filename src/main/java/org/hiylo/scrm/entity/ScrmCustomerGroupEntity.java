/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerGroupEntity.java
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
 * SCRM 客户分组实体。
 * <p>
 * 用于将客户按业务维度分组（如「高价值客户」「待激活客户」），
 * 归属到运营账号便于批量触达。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_group", schema = "scrm", indexes = {
        @Index(name = "idx_customer_group_owner", columnList = "owner_account_id"),
        @Index(name = "idx_customer_group_platform_group_uid", columnList = "platform_group_uid")
})
@Data
public class ScrmCustomerGroupEntity {

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

    /** 分组名称 */
    @Column(name = "group_name", nullable = false, length = 200)
    private String groupName;

    /** 分组描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 归属账号 ID（引用 scrm_account.id） */
    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    /** 平台群组唯一标识（企微 chatId） */
    @Column(name = "platform_group_uid", length = 200)
    private String platformGroupUid;

    /** 平台类型（wework / dingtalk / feishu） */
    @Column(name = "platform_type", length = 50)
    private String platformType;

    /** 群成员数量 */
    @Column(name = "member_count")
    private Integer memberCount;

    /** 群主企微 userid */
    @Column(name = "owner_user_id", length = 200)
    private String ownerUserId;
}
