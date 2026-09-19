/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChannelCodeEntity.java
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
 * SCRM 渠道活码实体。
 * <p>
 * 生成渠道二维码/活码, 客户扫码后按 codeType 规则自动分配到指定账号, 并追踪来源。
 * codeType:
 * <ul>
 *   <li>SINGLE: 单账号, 扫码分配到 redirectAccountId</li>
 *   <li>MULTI: 多账号, 按 assignRule 分配规则分配</li>
 *   <li>ROUND_ROBIN: 轮询, 按 assignRule 中的账号列表轮流分配</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_channel_code", schema = "scrm", indexes = {
        @Index(name = "idx_channel_code_status", columnList = "status"),
        @Index(name = "idx_channel_code_platform", columnList = "platform_type")
})
@Data
public class ScrmChannelCodeEntity {

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

    /** 活码名称 */
    @Column(name = "code_name", nullable = false, length = 200)
    private String codeName;

    /** 活码类型: SINGLE(单账号) / MULTI(多账号) / ROUND_ROBIN(轮询) */
    @Column(name = "code_type", nullable = false, length = 20)
    private String codeType;

    /** 平台类型 */
    @Column(name = "platform_type", nullable = false, length = 30)
    private String platformType;

    /** 二维码图片 URL（可空） */
    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    /** SINGLE 类型重定向账号 ID（可空, SINGLE 类型用） */
    @Column(name = "redirect_account_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long redirectAccountId;

    /** MULTI 类型分配规则 JSON（可空, 含账号 ID 列表与权重） */
    @Column(name = "assign_rule", columnDefinition = "TEXT")
    private String assignRule;

    /** 欢迎语（可空） */
    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage;

    /** 标签（可空, 逗号分隔） */
    @Column(name = "tags", length = 500)
    private String tags;

    /** 状态: ACTIVE / INACTIVE */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 累计扫码数（默认 0） */
    @Column(name = "scan_count")
    private Integer scanCount;

    /** 累计添加数（默认 0） */
    @Column(name = "add_count")
    private Integer addCount;

    /** 过期时间（可空, 空表示永久有效） */
    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    /** 创建人（可空） */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
