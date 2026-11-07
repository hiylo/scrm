/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionSnapshotEntity.java
 * Date : 2026/10/23 09:00:00
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
 * 工商税务巡检结果快照实体。
 * <p>
 * 落库每次巡检的结构化结果, 同一企业同一维度只保留最新一条 ({@code isLatest=true}),
 * 通过 {@code stateHash} (SHA-256 结构化状态 JSON) 与上一快照比对, <b>哈希变化才告警</b>。
 * 首次入库视为基线快照 ({@code changed=false}, 不告警)。明细 JSON 中证件号码等敏感字段
 * 按项目红线脱敏后落库。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_inspection_snapshot", schema = "scrm", indexes = {
        @Index(name = "idx_inspection_snapshot_task", columnList = "task_id"),
        @Index(name = "idx_inspection_snapshot_latest", columnList = "task_id,dimension,is_latest"),
        @Index(name = "idx_inspection_snapshot_hash", columnList = "state_hash")
})
@Data
public class ScrmInspectionSnapshotEntity {

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

    // ==================== 快照业务字段 ====================

    /** 巡检任务 ID（引用 scrm_inspection_task.id） */
    @Column(name = "task_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 巡检维度: BUSINESS(工商状态 API) / TAX(税务公开公告) */
    @Column(name = "dimension", nullable = false, length = 20)
    private String dimension;

    /** 统一社会信用代码（查询主键） */
    @Column(name = "credit_code", length = 32)
    private String creditCode;

    /** 企业名称 */
    @Column(name = "enterprise_name", length = 200)
    private String enterpriseName;

    /** 结构化状态 JSON 的 SHA-256 哈希（变化比对用） */
    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    /** 是否该企业该维度最新快照（服务层维护唯一最新） */
    @Column(name = "is_latest", nullable = false)
    private Boolean isLatest = true;

    /** 巡检时间 */
    @Column(name = "inspect_at", nullable = false)
    private LocalDateTime inspectAt;

    /** 数据来源: QIXIN / QCC / GD_TAX / ZJ_TAX / MANUAL */
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    /** 归一化状态（如 在营/注销/吊销/正常/欠税/非正常户, 供看板聚合） */
    @Column(name = "status", length = 30)
    private String status;

    /** 状态明细 JSON（含欠税金额/所属期/公告机关/公告日期等, 证件号码脱敏后落库） */
    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    /** 相对上一快照是否发生变化（基线快照为 false） */
    @Column(name = "changed", nullable = false)
    private Boolean changed = false;

    /** 抓取/查询错误信息（可空） */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}