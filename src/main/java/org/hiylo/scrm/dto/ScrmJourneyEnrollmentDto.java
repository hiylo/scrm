/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyEnrollmentDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 旅程入营记录 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmJourneyEnrollmentDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属旅程 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long journeyId;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户昵称 */
    @Size(max = 200, message = "客户昵称长度不能超过 200")
    private String customerNickname;

    /** 当前步骤 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentStepId;

    /** 入营来源: EVENT / MANUAL / API */
    @Size(max = 50, message = "入营来源长度不能超过 50")
    @Pattern(regexp = "EVENT|MANUAL|API", message = "入营来源仅支持 EVENT/MANUAL/API")
    private String entrySource;

    /** 入营状态: ACTIVE / COMPLETED / EXITED / FAILED */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|COMPLETED|EXITED|FAILED", message = "状态仅支持 ACTIVE/COMPLETED/EXITED/FAILED")
    private String status;

    /** 入营时间 */
    @NotNull(message = "入营时间不能为空")
    private LocalDateTime enteredAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 退出时间 */
    private LocalDateTime exitedAt;

    /** 退出原因 */
    @Size(max = 500, message = "退出原因长度不能超过 500")
    private String exitReason;

    /** 上一步执行时间 */
    private LocalDateTime lastStepAt;

    /** 下一步执行时间 */
    private LocalDateTime nextStepAt;

    /** 进度百分比 (0-100) */
    private Integer progress;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
