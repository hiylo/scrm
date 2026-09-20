/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpRecordDto.java
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
 * SCRM 跟进记录 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmFollowUpRecordDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联任务 ID (可空, 空表示独立跟进记录) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 冗余客户昵称 */
    @Size(max = 200, message = "客户昵称长度不能超过 200")
    private String customerName;

    /** 接触方式: CALL/MESSAGE/VISIT/EMAIL/MEETING/OTHER */
    @NotBlank(message = "接触方式不能为空")
    @Size(max = 30, message = "接触方式长度不能超过 30")
    @Pattern(regexp = "CALL|MESSAGE|VISIT|EMAIL|MEETING|OTHER",
            message = "接触方式仅支持 CALL/MESSAGE/VISIT/EMAIL/MEETING/OTHER")
    private String contactMethod;

    /** 接触结果: REACHED/NO_ANSWER/LEFT_MESSAGE/FAILED */
    @NotBlank(message = "接触结果不能为空")
    @Size(max = 30, message = "接触结果长度不能超过 30")
    @Pattern(regexp = "REACHED|NO_ANSWER|LEFT_MESSAGE|FAILED",
            message = "接触结果仅支持 REACHED/NO_ANSWER/LEFT_MESSAGE/FAILED")
    private String contactResult;

    /** 跟进内容 */
    @NotBlank(message = "跟进内容不能为空")
    private String content;

    /** 通话/会话时长 (分钟) */
    private Integer durationMinutes;

    /** 客户情绪: POSITIVE/NEUTRAL/NEGATIVE */
    @Size(max = 20, message = "情绪长度不能超过 20")
    @Pattern(regexp = "POSITIVE|NEUTRAL|NEGATIVE|",
            message = "情绪仅支持 POSITIVE/NEUTRAL/NEGATIVE")
    private String sentiment;

    /** 后续行动计划 */
    @Size(max = 500, message = "后续行动长度不能超过 500")
    private String nextAction;

    /** 下次跟进时间 */
    private LocalDateTime nextFollowUpAt;

    /** 记录人 userId */
    @NotBlank(message = "记录人不能为空")
    @Size(max = 100, message = "记录人长度不能超过 100")
    private String recordedBy;

    /** 跟进发生时间 */
    private LocalDateTime recordedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
