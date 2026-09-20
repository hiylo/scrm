/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiIntentDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM AI 意图 DTO。
 * <p>
 * 对应 {@code ScrmAiIntentEntity} 的业务字段, 创建/更新接口入参。
 * intentCategory 仅支持 INQUIRY/COMPLAINT/PURCHASE/SUPPORT/FEEDBACK/GREETING/
 * FAREWELL/QUESTION/REQUEST, suggestedAction 仅支持
 * RECOMMEND_PRODUCT/CREATE_TICKET/ASSIGN_AGENT/ESCALATE/NONE。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAiIntentDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 意图名称 */
    @NotBlank(message = "意图名称不能为空")
    @Size(max = 200, message = "意图名称长度不能超过 200")
    private String intentName;

    /** 意图类别: INQUIRY / COMPLAINT / PURCHASE / SUPPORT / FEEDBACK / GREETING / FAREWELL / QUESTION / REQUEST */
    @NotBlank(message = "意图类别不能为空")
    @Pattern(regexp = "INQUIRY|COMPLAINT|PURCHASE|SUPPORT|FEEDBACK|GREETING|FAREWELL|QUESTION|REQUEST",
            message = "意图类别仅支持 INQUIRY/COMPLAINT/PURCHASE/SUPPORT/FEEDBACK/GREETING/FAREWELL/QUESTION/REQUEST")
    private String intentCategory;

    /** 关键词 (逗号分隔) */
    @Size(max = 1000, message = "关键词长度不能超过 1000")
    private String keywords;

    /** 示例文本 (JSON 数组) */
    private String examples;

    /** 推荐回复模板 */
    @Size(max = 2000, message = "推荐回复模板长度不能超过 2000")
    private String responseTemplate;

    /** 建议动作: RECOMMEND_PRODUCT / CREATE_TICKET / ASSIGN_AGENT / ESCALATE / NONE */
    @Pattern(regexp = "RECOMMEND_PRODUCT|CREATE_TICKET|ASSIGN_AGENT|ESCALATE|NONE",
            message = "建议动作仅支持 RECOMMEND_PRODUCT/CREATE_TICKET/ASSIGN_AGENT/ESCALATE/NONE")
    private String suggestedAction;

    /** 优先级 (数字越大越优先, 默认 0) */
    private Integer priority;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 匹配次数 (查询返回) */
    private Integer matchCount;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
