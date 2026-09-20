/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerJourneyDto.java
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
 * SCRM 营销 SOP 客户旅程 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCustomerJourneyDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 旅程名称 */
    @NotBlank(message = "旅程名称不能为空")
    @Size(max = 200, message = "旅程名称长度不能超过 200")
    private String journeyName;

    /** 旅程描述 */
    @Size(max = 500, message = "旅程描述长度不能超过 500")
    private String description;

    /** 旅程目标 (如 新客转化 / 激活沉睡 / 复购引导) */
    @Size(max = 200, message = "旅程目标长度不能超过 200")
    private String goal;

    /** 入旅程条件 (JSON) */
    @NotBlank(message = "入旅程条件不能为空")
    private String entryCondition;

    /** 入旅程方式: EVENT / MANUAL / API */
    @Size(max = 20, message = "入旅程方式长度不能超过 20")
    @Pattern(regexp = "EVENT|MANUAL|API", message = "入旅程方式仅支持 EVENT/MANUAL/API")
    private String entryType;

    /** 旅程状态: DRAFT / PUBLISHED / PAUSED / ARCHIVED */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "DRAFT|PUBLISHED|PAUSED|ARCHIVED", message = "状态仅支持 DRAFT/PUBLISHED/PAUSED/ARCHIVED")
    private String status;

    /** 入旅程客户数 */
    private Integer enrolledCount;

    /** 完成旅程客户数 */
    private Integer completedCount;

    /** 退出旅程客户数 */
    private Integer exitedCount;

    /** 转化率 */
    private Double conversionRate;

    /** 旅程业务版本号 */
    private Integer journeyVersion;

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
