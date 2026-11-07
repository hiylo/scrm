/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionRunEntity.java
 * Date : 2026/10/24 16:30:00
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
 * 巡检/抓取执行历史。
 * <p>
 * 每完成一轮工商巡检或税务公告抓取写一条，记录扫描量、成功/失败/变化数、
 * 跳过原因与耗时，供运营回溯「过去几天到底跑没跑、跑成什么样」。
 * </p>
 * <p>
 * 此前只有任务上的 {@code last_result} 单点值，无法回答「上周有没有跳过」「连续失败几天了」。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_inspection_run", schema = "scrm", indexes = {
        @Index(name = "idx_inspection_run_type_time", columnList = "run_type,started_at"),
        @Index(name = "idx_inspection_run_status", columnList = "status")
})
@Data
public class ScrmInspectionRunEntity {

    /** 执行类型：工商 API 巡检 */
    public static final String TYPE_BUSINESS = "BUSINESS";

    /** 执行类型：税务公告抓取 */
    public static final String TYPE_TAX = "TAX";

    /** 触发方式：定时调度 */
    public static final String TRIGGER_SCHEDULED = "SCHEDULED";

    /** 触发方式：手动触发 */
    public static final String TRIGGER_MANUAL = "MANUAL";

    /** 结果：全部成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 结果：部分成功 */
    public static final String STATUS_PARTIAL = "PARTIAL";

    /** 结果：失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 结果：按开关整体跳过（非失败） */
    public static final String STATUS_SKIPPED = "SKIPPED";

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

    /** 乐观锁版本号 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** 执行类型：BUSINESS / TAX */
    @Column(name = "run_type", nullable = false, length = 20)
    private String runType;

    /** 触发方式：SCHEDULED / MANUAL */
    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    /** 结果：SUCCESS / PARTIAL / FAILED / SKIPPED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 扫描的任务数 */
    @Column(name = "task_scanned", nullable = false)
    private Integer taskScanned = 0;

    /** 工商: 成功任务数; 税务: 命中记录数 */
    @Column(name = "success_count", nullable = false)
    private Integer successCount = 0;

    /** 失败任务数 */
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    /** 工商: 状态变化数; 税务: 验证码阻断数 */
    @Column(name = "changed_count", nullable = false)
    private Integer changedCount = 0;

    /** 整体跳过原因（如启信宝未启用） */
    @Column(name = "skip_reason", length = 255)
    private String skipReason;

    /** 错误信息 */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 补充指标 JSON（税务: recordsMatched/captchaBlocked） */
    @Column(name = "detail", length = 2000)
    private String detail;

    /** 手动触发时的操作人 */
    @Column(name = "operator", length = 100)
    private String operator;

    /** 开始时间 */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 结束时间 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** 本轮耗时（毫秒） */
    @Column(name = "cost_ms")
    private Long costMs;

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
}
