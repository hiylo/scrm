/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeTaskDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 客户身份合并任务 DTO。
 * <p>
 * 对应 {@code ScrmIdentityMergeTaskEntity} 的业务字段, 用于创建 / 查询合并任务。
 * mergeType 与 status 以 Pattern 校验合法性。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmIdentityMergeTaskDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;

    /** 源客户 ID (被合并的客户) */
    @NotNull(message = "源客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceCustomerId;

    /** 源客户名称 (可空) */
    @Size(max = 200, message = "源客户名称长度不能超过 200")
    private String sourceCustomerName;

    /** 目标客户 ID (合并后保留的客户) */
    @NotNull(message = "目标客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetCustomerId;

    /** 目标客户名称 (可空) */
    @Size(max = 200, message = "目标客户名称长度不能超过 200")
    private String targetCustomerName;

    /** 合并类型: MANUAL/AUTO/SUGGESTED (可空, 默认 MANUAL) */
    @Pattern(regexp = "MANUAL|AUTO|SUGGESTED|", message = "合并类型非法")
    private String mergeType;

    /** 匹配原因 (可空, 逗号分隔) */
    @Size(max = 1000, message = "匹配原因长度不能超过 1000")
    private String matchReasons;

    /** 匹配分数 (0~1, 可空) */
    private Double matchScore;

    /** 匹配字段 JSON (可空) */
    @Size(max = 500, message = "匹配字段长度不能超过 500")
    private String matchedFields;

    /** JSON 合并配置 (可空): {fieldStrategy:[{field,strategy}]} */
    private String mergeConfig;

    /** 冲突字段 JSON (可空) */
    @Size(max = 1000, message = "冲突字段长度不能超过 1000")
    private String conflictFields;

    /** 状态 (查询返回): PENDING/REVIEWING/APPROVED/IN_PROGRESS/COMPLETED/FAILED/CANCELLED/REJECTED */
    private String status;

    /** 审核意见 (可空) */
    @Size(max = 500, message = "审核意见长度不能超过 500")
    private String reviewComment;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;
}
