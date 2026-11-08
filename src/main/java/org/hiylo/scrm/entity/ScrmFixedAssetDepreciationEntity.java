/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmFixedAssetDepreciationEntity.java
 * Date : 2026/11/08 00:00:00
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 固定资产折旧记录实体（V27）。
 * <p>同资产同期间唯一（幂等）；每期由 {@code ScrmFixedAssetService}
 * 直线法计提并联动生成凭证草稿。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_fixed_asset_depreciation", schema = "scrm")
@Data
public class ScrmFixedAssetDepreciationEntity {

    /** 主键 ID（Snowflake） */
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

    /** 资产卡片 ID */
    @Column(name = "asset_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assetId;

    /** 所属期年份 */
    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    /** 所属期月份 */
    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    /** 当月折旧额 */
    @Column(name = "depreciation_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal depreciationAmount;

    /** 提完当月后的累计折旧 */
    @Column(name = "accumulated_after", nullable = false, precision = 16, scale = 2)
    private BigDecimal accumulatedAfter;

    /** 摘要 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 持久化前回调 */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
    }

    /** 更新前回调 */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}