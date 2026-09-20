/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReportResultEntity.java
 * Date : 2026/08/04 08:40:58
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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;
import jakarta.persistence.PreUpdate;

/**
 * SCRM 报表执行结果实体。
 * <p>
 * 每次执行报表模板时写入一条记录, 保存执行人、时间范围、结果数据 (JSON)、行数与状态。
 * 执行失败时 status=FAILED 并记录 errorMessage。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_report_result", schema = "scrm", indexes = {
        @Index(name = "idx_report_result_template_id", columnList = "template_id"),
        @Index(name = "idx_report_result_run_at", columnList = "run_at")
})
@Data
public class ScrmReportResultEntity {

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 报表模板 ID */
    @Column(name = "template_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 执行人用户 ID */
    @Column(name = "run_by", nullable = false, length = 100)
    private String runBy;

    /** 执行时间 */
    @Column(name = "run_at", nullable = false)
    private LocalDateTime runAt;

    /** 时间范围起始 (可空) */
    @Column(name = "time_range_start")
    private LocalDateTime timeRangeStart;

    /** 时间范围结束 (可空) */
    @Column(name = "time_range_end")
    private LocalDateTime timeRangeEnd;

    /** 结果数据 JSON */
    @Column(name = "result_data", nullable = false, columnDefinition = "TEXT")
    private String resultData;

    /** 结果行数 */
    @Column(name = "row_count")
    private Integer rowCount;

    /** 执行状态：SUCCESS / FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 错误信息 (执行失败时填充) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /**
     * 插入前自动设置执行时间。
     */
    @PrePersist
    protected void onCreate() {
        if (this.runAt == null) {
            this.runAt = LocalDateTime.now();
        }
    }

    /** 创建时间 (内存字段: 本表无 created_at 列, 执行时间由 run_at 承担) */
    @Transient
    private LocalDateTime createdAt;

    /** 更新时间 (内存字段: 本表无 updated_at 列) */
    @Transient
    private LocalDateTime updatedAt;

    /**
     * 更新前回调：自动刷新更新时间
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
