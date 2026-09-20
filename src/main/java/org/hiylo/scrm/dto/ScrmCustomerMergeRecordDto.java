/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerMergeRecordDto.java
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
 * SCRM 客户合并记录 DTO。
 * <p>
 * 对应 {@code ScrmCustomerMergeRecordEntity} 的业务字段, 用于合并记录查询与展示。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCustomerMergeRecordDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 保留的主客户 ID */
    @NotNull(message = "主客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long primaryCustomerId;

    /** 被合并的客户 ID, 逗号分隔 */
    @NotBlank(message = "被合并客户 ID 列表不能为空")
    @Size(max = 500, message = "被合并客户 ID 列表长度不能超过 500")
    private String mergedCustomerIds;

    /** 合并策略: MANUAL 手动 / AUTO 自动 */
    @NotBlank(message = "合并策略不能为空")
    @Size(max = 20, message = "合并策略长度不能超过 20")
    @Pattern(regexp = "MANUAL|AUTO", message = "合并策略仅支持 MANUAL/AUTO")
    private String mergeStrategy;

    /** 匹配依据: PHONE / NAME_PLATFORM / UID */
    @NotBlank(message = "匹配依据不能为空")
    @Size(max = 200, message = "匹配依据长度不能超过 200")
    private String matchCriteria;

    /** 匹配详情 JSON */
    private String matchDetail;

    /** 状态: PENDING/COMPLETED/REVERTED */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "PENDING|COMPLETED|REVERTED|",
            message = "状态仅支持 PENDING/COMPLETED/REVERTED")
    private String status;

    /** 合并操作人 */
    @Size(max = 100, message = "合并操作人长度不能超过 100")
    private String mergedBy;

    /** 合并时间 */
    private LocalDateTime mergedAt;

    /** 撤销时间 */
    private LocalDateTime revertedAt;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String note;
}
