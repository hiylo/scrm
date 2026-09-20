/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerIdentityEntity.java
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
 * SCRM 客户身份标识实体。
 * <p>
 * 记录客户跨平台的身份标识, 一个客户可拥有多个身份 (手机号 / 邮箱 / 微信 openid /
 * 抖音 openid 等), 用于跨平台身份合并与统一识别。每个客户仅能有一个主身份
 * ({@link #isPrimary}), 身份来源由 {@link #source} 标识。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_identity", schema = "scrm", indexes = {
        @Index(name = "idx_customer_identity_customer", columnList = "customer_id"),
        @Index(name = "idx_customer_identity_type_value", columnList = "identity_type,identity_value"),
        @Index(name = "idx_customer_identity_platform", columnList = "platform"),
        @Index(name = "idx_customer_identity_primary", columnList = "customer_id,is_primary")
})
@Data
public class ScrmCustomerIdentityEntity {

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

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空, 冗余字段) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 身份类型: PHONE/EMAIL/WECHAT_OPENID/WECHAT_UNIONID/WORK_WECHAT_EXTERNAL_ID/DOUYIN_OPENID/KUAISHOU_OPENID/XIAOHONGSHU_OPENID/WEIBO_UID/ALIPAY_USERID/QQ_OPENID/ID_CARD/PASSPORT/USERNAME/DEVICE_ID/CUSTOM */
    @Column(name = "identity_type", nullable = false, length = 30)
    private String identityType;

    /** 身份值 */
    @Column(name = "identity_value", nullable = false, length = 500)
    private String identityValue;

    /** 平台 (可空): WECHAT/WORK_WECHAT/DOUYIN/KUAISHOU/XIAOHONGSHU/WEIBO/ALIPAY/QQ/WEB/APP */
    @Column(name = "platform", length = 30)
    private String platform;

    /** 是否主身份 */
    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    /** 是否验证 */
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified;

    /** 验证时间 (可空) */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** 来源: REGISTRATION/IMPORT/MERGE/OAUTH/MANUAL/SYSTEM */
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    /** 最后使用时间 (可空) */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** JSON 附加数据 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /** 是否活跃 */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
