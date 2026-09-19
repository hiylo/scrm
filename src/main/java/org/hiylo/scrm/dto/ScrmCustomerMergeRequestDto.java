/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerMergeRequestDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SCRM 客户合并请求 DTO。
 * <p>
 * 携带主客户 ID 与待合并客户 ID 列表, 主客户为合并后保留的客户,
 * 其余客户将被标记为已合并并引用到主客户。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCustomerMergeRequestDto {

    /** 主客户 ID (合并后保留的客户) */
    @NotNull(message = "主客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long primaryCustomerId;

    /** 待合并客户 ID 列表 (将被合并到主客户) */
    @NotEmpty(message = "待合并客户 ID 列表不能为空")
    private List<Long> customerIds;

    /** 合并策略: MANUAL(手动) / AUTO_MERGE(自动) */
    @Size(max = 20, message = "合并策略长度不能超过 20")
    private String mergeStrategy;

    /** 匹配条件描述 (如: exact:nickname+phone) */
    @Size(max = 200, message = "匹配条件长度不能超过 200")
    private String matchCriteria;
}
