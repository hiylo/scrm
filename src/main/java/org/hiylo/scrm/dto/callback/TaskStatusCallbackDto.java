/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : TaskStatusCallbackDto.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto.callback;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务状态回调 DTO，外部自动化执行引擎在任务执行状态变更时回调携带的数据。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class TaskStatusCallbackDto {

    /** 行为流 ID */
    private Long flowId;

    /** 行为会话 ID */
    private String sessionId;

    /** 任务状态：SUCCESS / FAILED / RUNNING */
    private String status;

    /** 错误码（FAILED 时携带） */
    private String errorCode;

    /** 错误信息（FAILED 时携带） */
    private String errorMessage;

    /** 任务完成时间 */
    private LocalDateTime completedAt;
}
