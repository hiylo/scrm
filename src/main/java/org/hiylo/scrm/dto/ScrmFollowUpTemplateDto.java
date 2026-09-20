/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpTemplateDto.java
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
 * SCRM 跟进模板 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmFollowUpTemplateDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

    /** 跟进类型: CALL/MESSAGE/VISIT/EMAIL/MEETING/OTHER */
    @NotBlank(message = "跟进类型不能为空")
    @Size(max = 30, message = "跟进类型长度不能超过 30")
    @Pattern(regexp = "CALL|MESSAGE|VISIT|EMAIL|MEETING|OTHER",
            message = "跟进类型仅支持 CALL/MESSAGE/VISIT/EMAIL/MEETING/OTHER")
    private String taskType;

    /** 标题模板 */
    @NotBlank(message = "标题模板不能为空")
    @Size(max = 200, message = "标题模板长度不能超过 200")
    private String titleTemplate;

    /** 内容模板 */
    private String contentTemplate;

    /** 默认优先级: HIGH/MEDIUM/LOW */
    @Size(max = 10, message = "优先级长度不能超过 10")
    @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "优先级仅支持 HIGH/MEDIUM/LOW")
    private String defaultPriority;

    /** 默认提醒分钟数 */
    private Integer defaultReminderMinutes;

    /** 平台类型 */
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 场景: NEW_CUSTOMER/DORMANT_REACTIVATE/AFTER_SALE/BIRTHDAY/MEMBERSHIP_RENEWAL */
    @Size(max = 50, message = "场景长度不能超过 50")
    @Pattern(regexp = "NEW_CUSTOMER|DORMANT_REACTIVATE|AFTER_SALE|BIRTHDAY|MEMBERSHIP_RENEWAL|",
            message = "场景仅支持 NEW_CUSTOMER/DORMANT_REACTIVATE/AFTER_SALE/BIRTHDAY/MEMBERSHIP_RENEWAL")
    private String scenario;

    /** 是否启用 */
    private Boolean enabled;

    /** 累计应用次数 */
    private Integer useCount;

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
