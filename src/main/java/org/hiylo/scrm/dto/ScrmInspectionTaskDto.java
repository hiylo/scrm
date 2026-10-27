/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInspectionTaskDto.java
 * Date : 2026/10/23 09:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工商税务巡检任务 DTO。
 * <p>
 * 承载巡检任务的创建/更新/展示参数（企业 + 周期任务配置）。{@code creditCode}
 * 为第三方工商 API 主键, 创建时与 {@code enterpriseName} 至少填一个。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInspectionTaskDto {

    /** 任务 ID（查询返回时填充） */
    private Long id;

    /** 关联客户 ID（引用 scrm_customer.id, 可空） */
    private Long customerId;

    /** 企业名称 */
    @NotBlank(message = "企业名称不能为空")
    @Size(max = 200, message = "企业名称长度不能超过 200")
    private String enterpriseName;

    /** 统一社会信用代码（可空, 与企业名称至少填一个） */
    @Size(max = 32, message = "统一社会信用代码长度不能超过 32")
    private String creditCode;

    /** 法定代表人 */
    @Size(max = 100, message = "法定代表人长度不能超过 100")
    private String legalPerson;

    /** 归属账号 ID（可空, 默认当前操作人账号） */
    private Long ownerAccountId;

    /** 巡检周期: DAILY / WEEKLY / QUARTERLY / MANUAL */
    @Pattern(regexp = "DAILY|WEEKLY|QUARTERLY|MANUAL", message = "巡检周期仅支持 DAILY/WEEKLY/QUARTERLY/MANUAL")
    private String cycleType;

    /** 是否启用工商状态 API 巡检 */
    private Boolean inspectBusiness;

    /** 是否启用税务公开公告抓取 */
    private Boolean inspectTax;

    /** 是否启用（默认 true） */
    private Boolean isEnabled;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    // ==================== 只读字段（查询返回） ====================

    /** 上次工商巡检时间 */
    private LocalDateTime lastBusinessRunAt;

    /** 上次税务抓取时间 */
    private LocalDateTime lastTaxRunAt;

    /** 上次整体巡检时间 */
    private LocalDateTime lastRunAt;

    /** 上次执行结果: SUCCESS / PARTIAL / FAILED */
    private String lastResult;

    /** 上次错误信息 */
    private String lastErrorMessage;

    /** 累计执行次数 */
    private Integer runCount;

    /** 累计告警次数 */
    private Integer alertCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}