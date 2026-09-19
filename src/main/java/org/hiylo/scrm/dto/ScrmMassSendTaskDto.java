/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMassSendTaskDto.java
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
 * SCRM 群发任务 DTO, 含目标客户筛选参数。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMassSendTaskDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;

    /** 平台类型 */
    @NotBlank(message = "平台类型不能为空")
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 关联消息模板 ID（可空） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageTemplateId;

    /** 群发内容文本 */
    @NotBlank(message = "群发内容不能为空")
    private String content;

    /** 目标类型: ALL(全量) / SEGMENT(分群) / TAG(按标签) / LIST(指定列表) */
    @NotBlank(message = "目标类型不能为空")
    @Size(max = 20, message = "目标类型长度不能超过 20")
    @Pattern(regexp = "ALL|SEGMENT|TAG|LIST",
            message = "目标类型仅支持 ALL/SEGMENT/TAG/LIST")
    private String targetType;

    /** 目标筛选条件 JSON (targetType=TAG/SEGMENT/LIST 时使用) */
    private String targetFilter;

    /** 发送账号 ID */
    @NotNull(message = "发送账号 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderAccountId;

    /** 任务状态: DRAFT / PENDING / RUNNING / PAUSED / COMPLETED / FAILED */
    @Size(max = 20, message = "任务状态长度不能超过 20")
    @Pattern(regexp = "DRAFT|PENDING|RUNNING|PAUSED|COMPLETED|FAILED",
            message = "任务状态仅支持 DRAFT/PENDING/RUNNING/PAUSED/COMPLETED/FAILED")
    private String status;

    /** 目标客户总数 */
    private Integer totalCount;

    /** 已发送数 */
    private Integer sentCount;

    /** 发送成功数 */
    private Integer successCount;

    /** 发送失败数 */
    private Integer failCount;

    /** 计划发送时间 */
    private LocalDateTime scheduledAt;

    /** 实际开始发送时间 */
    private LocalDateTime startedAt;

    /** 发送完成时间 */
    private LocalDateTime completedAt;

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
