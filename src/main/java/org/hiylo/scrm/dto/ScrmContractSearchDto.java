/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractSearchDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;

/**
 * SCRM 合同搜索 DTO。
 * <p>
 * 用于合同列表的多条件搜索, 支持按编号/名称/类型/状态/客户/相对方/签订日期范围/到期日期范围/
 * 金额范围/销售人员/关键词过滤, 并支持排序字段指定。所有字段均可空, 为空时不参与过滤。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmContractSearchDto {

    /** 合同编号 (模糊匹配, 可空) */
    private String contractNo;

    /** 合同名称 (模糊匹配, 可空) */
    private String contractName;

    /** 合同类型: SALES / PURCHASE / SERVICE / LEASE / NDA / PARTNERSHIP / EMPLOYMENT / OTHER (可空) */
    private String contractType;

    /** 合同状态: DRAFT / REVIEW / PENDING_SIGN / SIGNED / ACTIVE / EXPIRING / EXPIRED / TERMINATED / CANCELLED (可空) */
    private String contractStatus;

    /** 客户 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 相对方名称 (模糊匹配, 可空) */
    private String counterparty;

    /** 签订日期范围 - 起 (可空) */
    private LocalDate signDateStart;

    /** 签订日期范围 - 止 (可空) */
    private LocalDate signDateEnd;

    /** 到期日期范围 - 起 (可空) */
    private LocalDate expiryDateStart;

    /** 到期日期范围 - 止 (可空) */
    private LocalDate expiryDateEnd;

    /** 金额范围 - 最小值 (可空) */
    private Double minAmount;

    /** 金额范围 - 最大值 (可空) */
    private Double maxAmount;

    /** 销售人员 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long salesId;

    /** 关键词 (模糊匹配合同名称/编号/客户名称, 可空) */
    private String keyword;

    /** 排序字段: createTime / contractAmount / endDate / contractNo (可空, 默认 createTime 降序) */
    private String sortBy;
}
