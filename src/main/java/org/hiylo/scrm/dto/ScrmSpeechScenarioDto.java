/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechScenarioDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 销售话术场景 DTO。
 * <p>
 * 对应 {@code ScrmSpeechScenarioEntity} 的业务字段, 创建/更新接口入参。
 * scenarioCategory 以枚举字符串校验合法性; priority / speechCount / avgRating /
 * usageCount / successRate / enabled 缺省时由服务端填充默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSpeechScenarioDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 场景名称 */
    @NotBlank(message = "场景名称不能为空")
    @Size(max = 200, message = "场景名称长度不能超过 200")
    private String scenarioName;

    /** 场景编码 (全局唯一) */
    @NotBlank(message = "场景编码不能为空")
    @Size(max = 50, message = "场景编码长度不能超过 50")
    private String scenarioCode;

    /** 场景类别: GREETING/INQUIRY/PITCH/OBJECTION/CLOSING/FOLLOW_UP/CROSS_SELL/UP_SELL/RETENTION/RECOVERY/APPOINTMENT/REFERRAL/THANK_YOU/APOLOGY */
    @NotBlank(message = "场景类别不能为空")
    @Pattern(regexp = "GREETING|INQUIRY|PITCH|OBJECTION|CLOSING|FOLLOW_UP|CROSS_SELL|UP_SELL|"
            + "RETENTION|RECOVERY|APPOINTMENT|REFERRAL|THANK_YOU|APOLOGY",
            message = "场景类别仅支持 GREETING/INQUIRY/PITCH/OBJECTION/CLOSING/FOLLOW_UP/CROSS_SELL/UP_SELL/"
                    + "RETENTION/RECOVERY/APPOINTMENT/REFERRAL/THANK_YOU/APOLOGY")
    private String scenarioCategory;

    /** 场景描述 (可空) */
    @Size(max = 500, message = "场景描述长度不能超过 500")
    private String description;

    /** 触发条件 JSON: {customerStage,productCategory,channel,timeOfDay,sentiment} (可空) */
    private String triggerConditions;

    /** 适用产品 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用产品长度不能超过 500")
    private String applicableProducts;

    /** 适用渠道 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用渠道长度不能超过 500")
    private String applicableChannels;

    /** 客户阶段: NEW/ACTIVE/AT_RISK/CHURNED/VIP/PROSPECT (可空) */
    @Pattern(regexp = "NEW|ACTIVE|AT_RISK|CHURNED|VIP|PROSPECT|",
            message = "客户阶段仅支持 NEW/ACTIVE/AT_RISK/CHURNED/VIP/PROSPECT")
    private String customerStage;

    /** 优先级 (默认 0) */
    private Integer priority;

    /** 话术数 (查询返回) */
    private Integer speechCount;

    /** 平均评分 (查询返回) */
    private Double avgRating;

    /** 使用次数 (查询返回) */
    private Integer usageCount;

    /** 成功率 (查询返回) */
    private Double successRate;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
