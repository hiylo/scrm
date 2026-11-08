/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmFixedAssetEntity.java
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
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 固定资产卡片实体（V27）。
 * <p>
 * 直线法折旧：月折旧 = 原值 × (1 − 残值率) / 寿命月数；最后一个折旧月补差
 * 使累计 = 原值 × (1 − 残值率)。计提后联动 {@code ScrmVoucherService}
 * 生成「借：费用 贷：1602 累计折旧」凭证草稿。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_fixed_asset", schema = "scrm", indexes = {
        @Index(name = "idx_fixed_asset_company", columnList = "company_id"),
        @Index(name = "idx_fixed_asset_company_status", columnList = "company_id,status")
})
@Data
public class ScrmFixedAssetEntity {

    /** 状态：在用 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 状态：已报废 */
    public static final String STATUS_SCRAPPED = "SCRAPPED";

    /** 默认残值率：5% */
    public static final BigDecimal DEFAULT_RESIDUAL_RATE = new BigDecimal("0.0500");

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

    /** 归属公司主体 ID */
    @Column(name = "company_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long companyId;

    /** 公司主体名称（冗余展示） */
    @Column(name = "company_name", length = 200)
    private String companyName;

    /** 资产名称 */
    @Column(name = "asset_name", nullable = false, length = 200)
    private String assetName;

    /** 资产类别：MACHINERY / ELECTRONIC / FURNITURE / VEHICLE / OTHER */
    @Column(name = "asset_category", nullable = false, length = 30)
    private String assetCategory = "OTHER";

    /** 原值 */
    @Column(name = "original_value", nullable = false, precision = 16, scale = 2)
    private BigDecimal originalValue;

    /** 残值率（默认 5%） */
    @Column(name = "residual_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal residualRate = DEFAULT_RESIDUAL_RATE;

    /** 使用寿命（月） */
    @Column(name = "useful_life_months", nullable = false)
    private Integer usefulLifeMonths;

    /** 启用日期（开始计提月） */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** 购买日期 */
    @Column(name = "bought_date")
    private LocalDate boughtDate;

    /** 累计已提折旧 */
    @Column(name = "accumulated_depreciation", nullable = false, precision = 16, scale = 2)
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    /** 净值 = 原值 − 累计折旧 */
    @Column(name = "net_value", nullable = false, precision = 16, scale = 2)
    private BigDecimal netValue;

    /** 状态：ACTIVE / SCRAPPED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    /** 备注 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** 持久化前回调 */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (residualRate == null) {
            residualRate = DEFAULT_RESIDUAL_RATE;
        }
        if (accumulatedDepreciation == null) {
            accumulatedDepreciation = BigDecimal.ZERO;
        }
        if (status == null || status.isBlank()) {
            status = STATUS_ACTIVE;
        }
        if (netValue == null && originalValue != null) {
            netValue = originalValue.subtract(accumulatedDepreciation);
        }
    }

    /** 更新前回调 */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
        if (originalValue != null && accumulatedDepreciation != null) {
            netValue = originalValue.subtract(accumulatedDepreciation);
        }
    }
}