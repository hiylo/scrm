/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmImportTaskEntity.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 数据导入任务实体。
 * <p>
 * 记录单次批量导入的文件信息、状态流转 (PENDING → VALIDATING → IMPORTING →
 * SUCCESS/PARTIAL/FAILED/CANCELLED) 与记录级统计 (总数/有效/无效/已导入/失败/跳过)。
 * options 承载导入选项 JSON, errorDetails 承载错误明细 JSON 供排查回溯。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_import_task", schema = "scrm", indexes = {
        @Index(name = "idx_import_task_status", columnList = "status"),
        @Index(name = "idx_import_task_datatype", columnList = "data_type")
})
@Data
public class ScrmImportTaskEntity {

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
            fileType = "CSV";
        }
        if (totalRecords == null) {
            totalRecords = 0;
        }
        if (validRecords == null) {
            validRecords = 0;
        }
        if (invalidRecords == null) {
            invalidRecords = 0;
        }
        if (importedRecords == null) {
            importedRecords = 0;
        }
        if (failedRecords == null) {
            failedRecords = 0;
        }
        if (skippedRecords == null) {
            skippedRecords = 0;
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

    /** 关联导入模板 ID（可空） */
    @Column(name = "template_id")
    private Long templateId;

    /** 任务名称 */
    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    /** 数据类型: CUSTOMER/CONTACT/FOLLOW_RECORD/TAG/PRODUCT/ORDER/OTHER */
    @Column(name = "data_type", nullable = false, length = 30)
    private String dataType;

    /** 文件路径 */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /** 文件名称 */
    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    /** 文件大小 KB（可空） */
    @Column(name = "file_size")
    private Integer fileSize;

    /** 文件类型: CSV/EXCEL/JSON (默认 CSV) */
    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    /** 状态: PENDING/VALIDATING/IMPORTING/SUCCESS/PARTIAL/FAILED/CANCELLED (默认 PENDING) */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 总记录数 */
    @Column(name = "total_records")
    private Integer totalRecords;

    /** 有效记录数 */
    @Column(name = "valid_records")
    private Integer validRecords;

    /** 无效记录数 */
    @Column(name = "invalid_records")
    private Integer invalidRecords;

    /** 已导入记录数 */
    @Column(name = "imported_records")
    private Integer importedRecords;

    /** 失败记录数 */
    @Column(name = "failed_records")
    private Integer failedRecords;

    /** 跳过记录数 */
    @Column(name = "skipped_records")
    private Integer skippedRecords;

    /** 错误报告路径（可空） */
    @Column(name = "error_report", length = 500)
    private String errorReport;

    /** 错误详情 JSON（可空） */
    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    /** 开始执行时间（可空） */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 结束执行时间（可空） */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 执行耗时毫秒（可空） */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 触发人 */
    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    /** 导入选项 JSON（可空） */
    @Column(name = "options", columnDefinition = "TEXT")
    private String options;
}
