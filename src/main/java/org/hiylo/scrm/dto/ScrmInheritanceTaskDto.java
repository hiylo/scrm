/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInheritanceTaskDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * 离职继承任务 DTO。
 * <p>
 * 用于创建、查询、启动与完成任务接口的参数传递。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInheritanceTaskDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;

    /** 离职人 userId */
    @NotBlank(message = "离职人 userId 不能为空")
    @Size(max = 100, message = "离职人 userId 长度不能超过 100")
    private String fromUserId;

    /** 接收人 userId */
    @NotBlank(message = "接收人 userId 不能为空")
    @Size(max = 100, message = "接收人 userId 长度不能超过 100")
    private String toUserId;

    /** 指定平台类型 (可空, 空表示全平台) */
    @Size(max = 30, message = "平台类型长度不能超过 30")
    @Pattern(regexp = "^$|wework|douyin|kuaishou|xiaohongshu|bilibili|wechat_personal",
            message = "平台类型仅支持 wework/douyin/kuaishou/xiaohongshu/bilibili/wechat_personal")
    private String platformType;

    /** 任务状态: PENDING/RUNNING/COMPLETED/FAILED/PARTIAL */
    @Size(max = 20, message = "任务状态长度不能超过 20")
    @Pattern(regexp = "PENDING|RUNNING|COMPLETED|FAILED|PARTIAL",
            message = "任务状态仅支持 PENDING/RUNNING/COMPLETED/FAILED/PARTIAL")
    private String status;

    /** 总明细数 */
    private Integer totalItems;

    /** 成功明细数 */
    private Integer successItems;

    /** 失败明细数 */
    private Integer failItems;

    /** 任务开始时间 */
    private LocalDateTime startedAt;

    /** 任务完成时间 */
    private LocalDateTime completedAt;

    /** 任务创建人 userId */
    @Size(max = 100, message = "创建人 userId 长度不能超过 100")
    private String createdBy;

    /** 任务备注 */
    @Size(max = 500, message = "任务备注长度不能超过 500")
    private String note;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
