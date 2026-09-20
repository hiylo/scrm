/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExportTaskEntity.java
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 数据导出任务实体。
 * <p>
 * 记录单次批量导出的查询条件 (queryCondition/filters JSON)、选中字段、文件类型与
 * 状态流转 (PENDING → EXPORTING → SUCCESS/FAILED/CANCELLED), 汇总导出记录数与文件大小。
 * 支撑客户/联系人/跟进记录等多类型数据的标准化导出。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_export_task", schema = "scrm", indexes = {
        @Index(name = "idx_export_task_status", columnList = "status"),
        @Index(name = "idx_export_task_datatype", columnList = "data_type")
})
@Data
public class ScrmExportTaskEntity {

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
        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
        if (fileType == null || fileType.isBlank()) {
            fileType = "EXCEL";
        }
        if (totalRecords == null) {
            totalRecords = 0;
        }
        if (exportedRecords == null) {
            exportedRecords = 0;
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

    /** 任务名称 */
    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 数据类型: CUSTOMER/CONTACT/FOLLOW_RECORD/TAG/PRODUCT/ORDER/OTHER */
    @Column(name = "data_type", nullable = false, length = 30)
    private String dataType;

    /** 查询条件 JSON（可空） */
    @Column(name = "query_condition", columnDefinition = "TEXT")
    private String queryCondition;

    /** 逗号分隔选中字段（可空） */
    @Column(name = "selected_fields", length = 1000)
    private String selectedFields;

    /** 过滤条件 JSON（可空） */
    @Column(name = "filters", columnDefinition = "TEXT")
    private String filters;

    /** 状态: PENDING/EXPORTING/SUCCESS/FAILED/CANCELLED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 导出文件路径（可空） */
    @Column(name = "file_path", length = 500)
    private String filePath;

    /** 导出文件名称（可空） */
    @Column(name = "file_name", length = 200)
    private String fileName;

    /** 文件类型: CSV/EXCEL/JSON (默认 EXCEL) */
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    /** 总记录数 */
    @Column(name = "total_records")
    private Integer totalRecords;

    /** 已导出记录数 */
    @Column(name = "exported_records")
    private Integer exportedRecords;

    /** 文件大小 KB（可空） */
    @Column(name = "file_size")
    private Integer fileSize;

    /** 开始执行时间（可空） */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 结束执行时间（可空） */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 执行耗时毫秒（可空） */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 错误信息（可空） */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 触发人 */
    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;
}
