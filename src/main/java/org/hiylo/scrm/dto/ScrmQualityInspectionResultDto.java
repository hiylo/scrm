/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionResultDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 质检结果 DTO。
 * <p>
 * 质检结果查询/执行接口的输出表示, 由 {@code ScrmQualityInspectionResultEntity} 转换而来。
 * id 字段使用 {@link ToStringSerializer} 序列化为字符串, 避免前端 Long 精度丢失。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmQualityInspectionResultDto {

    /** 结果 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联质检任务 (可空, 单会话质检时为空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 关联会话 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 客户 ID（可空） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称（可空） */
    private String customerName;

    /** 被质检人 ID（账号 ID 字符串） */
    private String assigneeId;

    /** 被质检人名称 */
    private String assigneeName;

    /** 质检总分 (0-100) */
    private Double totalScore;

    /** 是否通过 */
    private Boolean passed;

    /** 各规则质检结果 JSON: [{ruleId,ruleName,category,score,passed,detail}] */
    private String ruleResults;

    /** 发现的问题列表 JSON（可空） */
    private String issuesFound;

    /** 改进建议（可空） */
    private String suggestions;

    /** 质检时间 */
    private LocalDateTime inspectedAt;

    /** 质检人类型: AI/MANUAL */
    private String inspectorType;

    /** 质检人 ID（可空） */
    private String inspectorId;
}
