/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerProfileDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户画像 DTO。
 * <p>
 * 对应 {@code ScrmCustomerProfileEntity} 的业务字段, 创建/更新接口入参。
 * profileType: BASIC/STANDARD/DETAILED/PREDICTIVE/BEHAVIORAL; tags 为 JSON 文本
 * ([{tagCode,tagName,tagValue,valueType}])。customerId 唯一。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCustomerProfileDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 画像类型: BASIC/STANDARD/DETAILED/PREDICTIVE/BEHAVIORAL (默认 STANDARD) */
    @Pattern(regexp = "BASIC|STANDARD|DETAILED|PREDICTIVE|BEHAVIORAL",
            message = "画像类型仅支持 BASIC/STANDARD/DETAILED/PREDICTIVE/BEHAVIORAL")
    private String profileType;

    /** 画像完整度 0-100 (查询返回) */
    private Double profileScore;

    /** 完整度% (查询返回) */
    private Double completenessPercent;

    /** 标签 JSON (可空): [{tagCode,tagName,tagValue,valueType}] */
    private String tags;

    /** 标签数 (查询返回) */
    private Integer tagCount;

    /** 手动标签 (可空) */
    @Size(max = 2000, message = "手动标签长度不能超过 2000")
    private String manualTags;

    /** 推导标签 (可空) */
    @Size(max = 2000, message = "推导标签长度不能超过 2000")
    private String derivedTags;

    /** 客群 (可空) */
    @Size(max = 500, message = "客群长度不能超过 500")
    private String segments;

    /** 画像角色 (可空) */
    @Size(max = 500, message = "画像角色长度不能超过 500")
    private String personas;

    /** 生命周期阶段 (可空) */
    @Size(max = 50, message = "生命周期阶段长度不能超过 50")
    private String lifecycleStage;

    /** 价值层级 (可空) */
    @Size(max = 50, message = "价值层级长度不能超过 50")
    private String valueTier;

    /** 风险等级 (可空) */
    @Size(max = 20, message = "风险等级长度不能超过 20")
    private String riskLevel;

    /** 参与度 0-100 (查询返回) */
    private Double engagementScore;

    /** 忠诚度 0-100 (查询返回) */
    private Double loyaltyScore;

    /** 满意度 0-100 (查询返回) */
    private Double satisfactionScore;

    /** 流失风险 0-100 (查询返回) */
    private Double churnRiskScore;

    /** 预测 LTV (查询返回) */
    private Double ltvPredicted;

    /** 实际 LTV (查询返回) */
    private Double ltvActual;

    /** LTV 置信度 (查询返回) */
    private Double ltvConfidence;

    /** 下一步最佳行动 (可空) */
    @Size(max = 500, message = "下一步最佳行动长度不能超过 500")
    private String nextBestAction;

    /** 下一步最佳产品 (可空) */
    @Size(max = 200, message = "下一步最佳产品长度不能超过 200")
    private String nextBestProduct;

    /** 偏好渠道 (可空) */
    @Size(max = 50, message = "偏好渠道长度不能超过 50")
    private String preferredChannel;

    /** 偏好时间 (可空) */
    @Size(max = 50, message = "偏好时间长度不能超过 50")
    private String preferredTime;

    /** 偏好语言 (可空) */
    @Size(max = 20, message = "偏好语言长度不能超过 20")
    private String preferredLanguage;

    /** 时区 (可空) */
    @Size(max = 50, message = "时区长度不能超过 50")
    private String timezone;

    /** 最近互动时间 (可空) */
    private LocalDateTime lastInteractionAt;

    /** 最近购买时间 (可空) */
    private LocalDateTime lastPurchaseAt;

    /** 最近活动时间 (可空) */
    private LocalDateTime lastActivityAt;

    /** 互动次数 (查询返回) */
    private Integer interactionCount;

    /** 购买次数 (查询返回) */
    private Integer purchaseCount;

    /** 总消费金额 (查询返回) */
    private Double totalSpent;

    /** 平均订单金额 (查询返回) */
    private Double avgOrderValue;

    /** 首次购买时间 (可空) */
    private LocalDateTime firstPurchaseAt;

    /** 获客渠道 (可空) */
    @Size(max = 100, message = "获客渠道长度不能超过 100")
    private String acquisitionChannel;

    /** 推荐来源 (可空) */
    @Size(max = 200, message = "推荐来源长度不能超过 200")
    private String referralSource;

    /** 自定义属性 JSON (可空) */
    private String attributes;

    /** 计算时间 (查询返回) */
    private LocalDateTime computedAt;

    /** 最后更新人 (可空) */
    @Size(max = 100, message = "最后更新人长度不能超过 100")
    private String lastUpdatedBy;

    /** 是否已验证 (默认 FALSE) */
    private Boolean isVerified;

    /** 验证时间 (查询返回) */
    private LocalDateTime verifiedAt;

    /** 验证人 (可空) */
    @Size(max = 100, message = "验证人长度不能超过 100")
    private String verifiedBy;

    /** 备注 (可空) */
    @Size(max = 1000, message = "备注长度不能超过 1000")
    private String notes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
