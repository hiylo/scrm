/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 黑名单条目 DTO。
 * <p>
 * 用于黑名单增删改查接口入参与返回。listType / targetType / reason / source / addedBy 必填,
 * riskLevel / status / isPermanent 等缺省时由 Service 填默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmBlacklistDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 名单类型: BLACKLIST / GRAYLIST / WHITELIST / WATCHLIST */
    @NotBlank(message = "名单类型不能为空")
    @Pattern(regexp = "BLACKLIST|GRAYLIST|WHITELIST|WATCHLIST",
            message = "名单类型仅支持 BLACKLIST/GRAYLIST/WHITELIST/WATCHLIST")
    private String listType;

    /** 目标类型: CUSTOMER / PHONE / EMAIL / IP / DEVICE / ID_CARD / BANK_CARD / ADDRESS / WECHAT_ID / COMPANY */
    @NotBlank(message = "目标类型不能为空")
    @Size(max = 30, message = "目标类型长度不能超过 30")
    private String targetType;

    /** 目标值 */
    @NotBlank(message = "目标值不能为空")
    @Size(max = 500, message = "目标值长度不能超过 500")
    private String targetValue;

    /** 目标名称 (可空) */
    @Size(max = 200, message = "目标名称长度不能超过 200")
    private String targetName;

    /** 关联客户 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 加入原因 */
    @NotBlank(message = "加入原因不能为空")
    @Size(max = 1000, message = "加入原因长度不能超过 1000")
    private String reason;

    /** 风险等级: LOW / MEDIUM / HIGH / CRITICAL */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL",
            message = "风险等级仅支持 LOW/MEDIUM/HIGH/CRITICAL")
    private String riskLevel;

    /** 风险评分 0-100 (可空) */
    private Double riskScore;

    /** 风险标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "风险标签长度不能超过 500")
    private String riskTags;

    /** 来源: MANUAL / AUTO / RULE / EXTERNAL / REPORT / SYSTEM */
    @NotBlank(message = "来源不能为空")
    @Pattern(regexp = "MANUAL|AUTO|RULE|EXTERNAL|REPORT|SYSTEM",
            message = "来源仅支持 MANUAL/AUTO/RULE/EXTERNAL/REPORT/SYSTEM")
    private String source;

    /** 来源详情 (可空) */
    @Size(max = 500, message = "来源详情长度不能超过 500")
    private String sourceDetail;

    /** 证据描述 (可空) */
    @Size(max = 2000, message = "证据描述长度不能超过 2000")
    private String evidence;

    /** 关联风险事件 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long relatedEventId;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 到期日期 (可空, null 表示永久) */
    private LocalDate expiryDate;

    /** 是否永久 (默认 FALSE) */
    private Boolean isPermanent;

    /** 状态: ACTIVE / EXPIRED / REMOVED / APPEALED / RESTORED */
    @Pattern(regexp = "ACTIVE|EXPIRED|REMOVED|APPEALED|RESTORED",
            message = "状态仅支持 ACTIVE/EXPIRED/REMOVED/APPEALED/RESTORED")
    private String status;

    /** 添加人 */
    @NotBlank(message = "添加人不能为空")
    @Size(max = 100, message = "添加人长度不能超过 100")
    private String addedBy;

    /** 添加时间 (查询返回) */
    private LocalDateTime addedAt;

    /** 备注 (可空) */
    @Size(max = 1000, message = "备注长度不能超过 1000")
    private String notes;

    /** JSON 附加数据 (可空) */
    private String metadata;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
