/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionTaskEntity.java
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
 * 工商税务巡检任务实体。
 * <p>
 * 对存量/在服务客户企业登记「企业 + 周期任务」的巡检配置: 工商状态走第三方 API
 * (inspectBusiness), 税务状态走公开公告抓取 (inspectTax)。调度本身复用
 * {@code scrm_scheduled_task} (TaskScheduler) 统一执行, 本表只登记巡检维度的
 * 企业 / 周期 / 开关 / 上次执行游标。<b>API key 不落此表</b>, 一律走环境变量配置。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_inspection_task", schema = "scrm", indexes = {
        @Index(name = "idx_inspection_task_customer", columnList = "customer_id"),
        @Index(name = "idx_inspection_task_owner", columnList = "owner_account_id"),
        @Index(name = "idx_inspection_task_enabled", columnList = "is_enabled,cycle_type"),
        @Index(name = "idx_inspection_task_credit", columnList = "credit_code")
})
@Data
public class ScrmInspectionTaskEntity {

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

    // ==================== 巡检业务字段 ====================

    /** 关联客户 ID（引用 scrm_customer.id, 可空; 仅服务自有/委托客户, 前端建任务时强校验） */
    @Column(name = "customer_id")
    private Long customerId;

    /** 巡检主体（公司）ID（V14）；未关联时沿用任务内 enterpriseName/creditCode */
    @Column(name = "company_id")
    private Long companyId;

    /** 企业名称（第三方工商 API 查询主键之一） */
    @Column(name = "enterprise_name", nullable = false, length = 200)
    private String enterpriseName;

    /** 统一社会信用代码（工商 API 查询主键之一, 可空名称兜底） */
    @Column(name = "credit_code", length = 32)
    private String creditCode;

    /** 法定代表人 */
    @Column(name = "legal_person", length = 100)
    private String legalPerson;

    /** 归属账号 ID（引用 scrm_account.id, 数据隔离） */
    @Column(name = "owner_account_id")
    private Long ownerAccountId;

    /** 巡检周期: DAILY / WEEKLY / QUARTERLY / MANUAL（默认 QUARTERLY, 对应 500 家 × 每季度 1 次口径） */
    @Column(name = "cycle_type", nullable = false, length = 20)
    private String cycleType = "QUARTERLY";

    /** 是否启用工商状态 API 巡检（默认 true） */
    @Column(name = "inspect_business", nullable = false)
    private Boolean inspectBusiness = true;

    /** 是否启用税务公开公告抓取（默认 true） */
    @Column(name = "inspect_tax", nullable = false)
    private Boolean inspectTax = true;

    /** 是否启用巡检（默认 true） */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    /** 上次工商巡检时间（增量游标） */
    @Column(name = "last_business_run_at")
    private LocalDateTime lastBusinessRunAt;

    /** 上次税务抓取时间（增量游标） */
    @Column(name = "last_tax_run_at")
    private LocalDateTime lastTaxRunAt;

    /** 上次整体巡检时间 */
    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    /** 上次执行结果: SUCCESS / PARTIAL / FAILED */
    @Column(name = "last_result", length = 20)
    private String lastResult;

    /** 上次错误信息（可空） */
    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    /** 累计执行次数（默认 0） */
    @Column(name = "run_count", nullable = false)
    private Integer runCount = 0;

    /** 累计告警次数（默认 0） */
    @Column(name = "alert_count", nullable = false)
    private Integer alertCount = 0;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;
}