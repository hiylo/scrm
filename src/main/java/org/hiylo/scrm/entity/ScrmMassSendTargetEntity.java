/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMassSendTargetEntity.java
 * Date : 2026/07/29 21:19:51
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
 * SCRM 群发目标明细实体。
 * <p>
 * 记录群发任务中每个目标客户的发送状态, 由任务发布时按 targetType 筛选生成。
 * 发送状态: PENDING(待发送) / SENT(已发送) / FAILED(发送失败)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_mass_send_target", schema = "scrm", indexes = {
        @Index(name = "idx_mass_send_target_task", columnList = "task_id"),
        @Index(name = "idx_mass_send_target_status", columnList = "status"),
        @Index(name = "idx_mass_send_target_customer", columnList = "customer_id")
})
@Data
public class ScrmMassSendTargetEntity {

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

    /** 群发任务 ID（引用 scrm_mass_send_task.id） */
    @Column(name = "task_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 客户 ID（引用 scrm_customer.id） */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户昵称（可空, 冗余存储便于列表展示） */
    @Column(name = "customer_nickname", length = 200)
    private String customerNickname;

    /** 平台客户唯一标识（可空, 冗余存储便于发送侧寻址） */
    @Column(name = "platform_customer_uid", length = 200)
    private String platformCustomerUid;

    /** 发送状态: PENDING / SENT / FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 发送失败原因（可空） */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 实际发送时间（可空, 未发送时为 null） */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
