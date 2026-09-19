/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChurnWarningDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户流失预警 DTO。
 * <p>
 * 用于预警处理 (resolve/ignore/escalate) 接口入参, 以及预警详情查询返回。
 * riskFactors 为 JSON 数组字符串: {@code [{factor, value, detail}]}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmChurnWarningDto {

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

    /** 触发规则 ID */
    @NotNull(message = "规则 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 触发规则名称 (可空) */
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 风险等级: HIGH / MEDIUM / LOW */
    @NotBlank(message = "风险等级不能为空")
    @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "风险等级仅支持 HIGH/MEDIUM/LOW")
    private String riskLevel;

    /** 风险分 (0-100, 越高越危险) */
    @NotNull(message = "风险分不能为空")
    private Double riskScore;

    /** 风险因子列表 JSON: [{factor, value, detail}] */
    @NotBlank(message = "风险因子不能为空")
    private String riskFactors;

    /** 预警状态: ACTIVE / RESOLVED / IGNORED / ESCALATED (默认 ACTIVE) */
    @Pattern(regexp = "ACTIVE|RESOLVED|IGNORED|ESCALATED",
            message = "预警状态仅支持 ACTIVE/RESOLVED/IGNORED/ESCALATED")
    private String status;

    /** 预警动作类型 */
    @NotBlank(message = "预警动作不能为空")
    private String actionType;

    /** 动作执行结果 (可空) */
    @Size(max = 500, message = "动作执行结果长度不能超过 500")
    private String actionResult;

    /** 动作执行时间 (可空) */
    private LocalDateTime actionExecutedAt;

    /** 负责人 ID (可空) */
    @Size(max = 100, message = "负责人 ID 长度不能超过 100")
    private String assigneeId;

    /** 负责人名称 (可空) */
    @Size(max = 100, message = "负责人名称长度不能超过 100")
    private String assigneeName;

    /** 处理说明 (可空) */
    @Size(max = 500, message = "处理说明长度不能超过 500")
    private String resolutionNote;

    /** 处理时间 (可空) */
    private LocalDateTime resolvedAt;

    /** 处理人 (可空) */
    @Size(max = 100, message = "处理人长度不能超过 100")
    private String resolvedBy;

    /** 检测时间 */
    private LocalDateTime detectedAt;

    /** 最后互动时间 (可空) */
    private LocalDateTime lastInteractionAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
