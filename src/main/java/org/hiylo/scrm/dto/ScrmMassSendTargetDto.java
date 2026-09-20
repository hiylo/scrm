/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMassSendTargetDto.java
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
 * SCRM 群发目标明细 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmMassSendTargetDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 群发任务 ID */
    @NotNull(message = "群发任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户昵称 */
    @Size(max = 200, message = "客户昵称长度不能超过 200")
    private String customerNickname;

    /** 平台客户唯一标识 */
    @Size(max = 200, message = "平台客户唯一标识长度不能超过 200")
    private String platformCustomerUid;

    /** 发送状态: PENDING / SENT / FAILED */
    @NotBlank(message = "发送状态不能为空")
    @Size(max = 20, message = "发送状态长度不能超过 20")
    @Pattern(regexp = "PENDING|SENT|FAILED",
            message = "发送状态仅支持 PENDING/SENT/FAILED")
    private String status;

    /** 发送失败原因 */
    @Size(max = 500, message = "失败原因长度不能超过 500")
    private String errorMessage;

    /** 实际发送时间 */
    private LocalDateTime sentAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
