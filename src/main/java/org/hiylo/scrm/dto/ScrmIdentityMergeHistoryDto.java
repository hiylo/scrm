/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeHistoryDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户身份合并历史 DTO。
 * <p>
 * 对应 {@code ScrmIdentityMergeHistoryEntity} 的业务字段, 主要用于查询返回。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmIdentityMergeHistoryDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 任务 ID */
    @NotNull(message = "任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 源客户 ID (被合并的客户) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceCustomerId;

    /** 源客户名称 (可空) */
    @Size(max = 200, message = "源客户名称长度不能超过 200")
    private String sourceCustomerName;

    /** 目标客户 ID (合并后保留的客户) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetCustomerId;

    /** 目标客户名称 (可空) */
    @Size(max = 200, message = "目标客户名称长度不能超过 200")
    private String targetCustomerName;

    /** 合并身份数 */
    private Integer mergedIdentitiesCount;

    /** 合并交易数 */
    private Integer mergedTransactionsCount;

    /** 合并的标签 (可空, 逗号分隔) */
    @Size(max = 1000, message = "合并的标签长度不能超过 1000")
    private String mergedTags;

    /** JSON 字段变更详情 (可空) */
    private String fieldChanges;

    /** 合并的身份列表 JSON (可空) */
    @Size(max = 1000, message = "合并的身份列表长度不能超过 1000")
    private String identitiesMerged;

    /** 合并前 LTV */
    private Double preMergeLtv;

    /** 合并后 LTV */
    private Double postMergeLtv;

    /** 数据完整性检查 */
    private Boolean dataIntegrityChecked;

    /** 数据完整性检查是否通过 */
    private Boolean dataIntegrityPassed;

    /** 是否可回滚 */
    private Boolean rollbackAvailable;

    /** 是否已回滚 */
    private Boolean rolledBack;

    /** 回滚时间 (可空) */
    private LocalDateTime rolledBackAt;

    /** 回滚操作人 (可空) */
    @Size(max = 100, message = "回滚操作人长度不能超过 100")
    private String rolledBackBy;

    /** 合并时间 */
    private LocalDateTime mergedAt;

    /** 合并操作人 (可空) */
    @Size(max = 100, message = "合并操作人长度不能超过 100")
    private String mergedBy;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;
}
