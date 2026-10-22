/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCallRecordEntity.java
 * Date : 2026/10/22 10:00:00
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
 * SCRM 销售通话记录实体。
 * <p>
 * 记录销售手机端的呼出 / 呼入 / 未接 / 拒接通话，来源区分：
 * <ul>
 *   <li>{@code APP_CALL}    - App 内点号拨打（自建话单，无需系统权限）</li>
 *   <li>{@code CALL_LOG}    - 读取系统通话记录（需 READ_CALL_LOG 且安装器 allowlist）</li>
 *   <li>{@code SCREENING}   - 来电识别角色（ROLE_CALL_SCREENING，无需 READ_CALL_LOG）</li>
 *   <li>{@code MANUAL}      - 手工补录</li>
 * </ul>
 * 客户端去重键为 {@code (deviceId, callLogId)}；无法取得系统话单 ID 时
 * 服务端以 {@code (deviceId, peerHash, startAt)} 近似去重。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_call_record", schema = "scrm", indexes = {
        @Index(name = "idx_call_record_user_time", columnList = "user_id,start_at"),
        @Index(name = "idx_call_record_customer", columnList = "customer_id"),
        @Index(name = "uk_call_record_dev_log", columnList = "device_id,call_log_id", unique = true)
})
@Data
public class ScrmCallRecordEntity {

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

    /** 归属用户 ID（关联 scrm_user.id） */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 上报设备 ID（App 安装时生成的唯一标识） */
    @Column(name = "device_id", length = 128)
    private String deviceId;

    /**
     * 关联客户/线索 ID（按 peer 号码匹配, 可为空待认领）。
     * 与 {@link #customerType} 一起确定归属对象。
     */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 关联对象类型：CUSTOMER(正式客户) / PUBLIC_SEA(公海线索)，可空表示未关联 */
    @Column(name = "customer_type", length = 20)
    private String customerType;

    /** 通话方向: OUT(呼出) / IN(呼入) / MISSED(未接) / REJECTED(拒接) */
    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    /** 对端号码（落库前建议脱敏, 由服务层处理） */
    @Column(name = "peer_number", length = 32)
    private String peerNumber;

    /** 对端号码哈希（SHA-256, 用于不落明文的匹配与统计） */
    @Column(name = "peer_hash", length = 64)
    private String peerHash;

    /** 通话开始时间 */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** 通话时长（秒, 仅接通后计, 未接通为 0） */
    @Column(name = "duration_sec", nullable = false)
    private Integer durationSec;

    /** 是否接通 */
    @Column(name = "connected", nullable = false)
    private Boolean connected;

    /** 来源: APP_CALL / CALL_LOG / SCREENING / MANUAL */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    /** 系统 CallLog._ID（与 device_id 构成客户端去重唯一键） */
    @Column(name = "call_log_id")
    private Long callLogId;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;
}
