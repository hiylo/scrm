/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInheritanceItemDto.java
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
 * 离职继承明细 DTO。
 * <p>
 * 用于查询任务明细与重试失败项接口的参数传递。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInheritanceItemDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 任务 ID */
    @NotNull(message = "任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 明细类型: CUSTOMER/GROUP/CONVERSATION */
    @NotBlank(message = "明细类型不能为空")
    @Size(max = 20, message = "明细类型长度不能超过 20")
    @Pattern(regexp = "CUSTOMER|GROUP|CONVERSATION",
            message = "明细类型仅支持 CUSTOMER/GROUP/CONVERSATION")
    private String itemType;

    /** 明细对象 ID (客户ID/群ID/会话ID) */
    @NotNull(message = "明细对象 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /** 明细标签 (客户昵称/群名, 便于展示) */
    @Size(max = 200, message = "明细标签长度不能超过 200")
    private String itemLabel;

    /** 明细状态: PENDING/SUCCESS/FAILED/SKIPPED */
    @Size(max = 20, message = "明细状态长度不能超过 20")
    @Pattern(regexp = "PENDING|SUCCESS|FAILED|SKIPPED",
            message = "明细状态仅支持 PENDING/SUCCESS/FAILED/SKIPPED")
    private String status;

    /** 失败原因 (FAILED 状态时记录) */
    @Size(max = 500, message = "失败原因长度不能超过 500")
    private String errorMessage;

    /** 处理时间 */
    private LocalDateTime processedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
