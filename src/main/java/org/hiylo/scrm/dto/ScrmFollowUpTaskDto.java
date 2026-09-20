/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpTaskDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 跟进任务 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmFollowUpTaskDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 冗余客户昵称 */
    @Size(max = 200, message = "客户昵称长度不能超过 200")
    private String customerName;

    /** 关联账号 ID（可空） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 负责人 userId */
    @NotBlank(message = "负责人不能为空")
    @Size(max = 100, message = "负责人 ID 长度不能超过 100")
    private String assigneeId;

    /** 负责人名称 */
    @Size(max = 100, message = "负责人名称长度不能超过 100")
    private String assigneeName;

    /** 跟进类型: CALL/MESSAGE/VISIT/EMAIL/MEETING/OTHER */
    @NotBlank(message = "跟进类型不能为空")
    @Size(max = 30, message = "跟进类型长度不能超过 30")
    @Pattern(regexp = "CALL|MESSAGE|VISIT|EMAIL|MEETING|OTHER",
            message = "跟进类型仅支持 CALL/MESSAGE/VISIT/EMAIL/MEETING/OTHER")
    private String taskType;

    /** 任务标题 */
    @NotBlank(message = "任务标题不能为空")
    @Size(max = 200, message = "任务标题长度不能超过 200")
    private String title;

    /** 跟进内容/备注 */
    private String content;

    /** 计划跟进时间 */
    @NotNull(message = "计划跟进时间不能为空")
    private LocalDateTime plannedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 状态: PENDING/IN_PROGRESS/COMPLETED/CANCELLED/OVERDUE */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "PENDING|IN_PROGRESS|COMPLETED|CANCELLED|OVERDUE",
            message = "状态仅支持 PENDING/IN_PROGRESS/COMPLETED/CANCELLED/OVERDUE")
    private String status;

    /** 优先级: HIGH/MEDIUM/LOW */
    @Size(max = 10, message = "优先级长度不能超过 10")
    @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "优先级仅支持 HIGH/MEDIUM/LOW")
    private String priority;

    /** 提前提醒分钟数 */
    private Integer reminderMinutes;

    /** 是否已提醒 */
    private Boolean reminded;

    /** 跟进结果 */
    @Size(max = 500, message = "跟进结果长度不能超过 500")
    private String followUpResult;

    /** 下次跟进时间 */
    private LocalDateTime nextFollowUpAt;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
