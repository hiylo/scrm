/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmPolicyPushTaskDto.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 政策推送任务 DTO（政策匹配推送域）。
 * <p>
 * 承载政策推送任务的创建/更新/流转参数: 匹配规则（税种/规模/地区/标签/分群）+
 * 二级人工闸门（auditMode/auditStatus）+ 状态机动作（提交审核/通过/驳回/发布/重试）。
 * 预计算快照（snapshotCustomerIds）为只读字段, 由草稿期预计算回填。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmPolicyPushTaskDto {

    /** 任务 ID */
    private Long id;

    /** 任务编号 PP-YYYYMMDD-序号 */
    private String taskNo;

    /** 政策知识条目 ID */
    @NotNull(message = "政策知识条目 ID 不能为空")
    private Long policyArticleId;

    /** 政策文号（可空, 服务端从知识条目冗余） */
    private String policyCode;

    /** 政策标题（可空, 服务端从知识条目冗余） */
    private String policyTitle;

    /** 适用税种: VAT / IIT / CIT / SOCIAL_SECURITY / STAMP / ALL */
    @Pattern(regexp = "VAT|IIT|CIT|SOCIAL_SECURITY|STAMP|ALL",
            message = "适用税种仅支持 VAT/IIT/CIT/SOCIAL_SECURITY/STAMP/ALL")
    private String taxType;

    /** 规模口径: MICRO / SMALL / MEDIUM / LARGE / LISTED / ALL */
    @Pattern(regexp = "MICRO|SMALL|MEDIUM|LARGE|LISTED|ALL", message = "规模口径仅支持 MICRO/SMALL/MEDIUM/LARGE/LISTED/ALL")
    private String sizeRange;

    /** 适用地区（空=全国） */
    @Size(max = 100, message = "地区长度不能超过 100")
    private String region;

    /** 目标标签编码（逗号分隔, 可空） */
    @Size(max = 500, message = "标签编码列表长度不能超过 500")
    private String tagCodes;

    /** 复用分群（可空） */
    private Long segmentId;

    /** 政策生效日期 */
    private LocalDate effectiveFrom;

    /** 政策失效日期 */
    private LocalDate effectiveUntil;

    /** 二级人工闸门: SAMPLING / FULL */
    @Pattern(regexp = "SAMPLING|FULL", message = "审核模式仅支持 SAMPLING/FULL")
    private String auditMode;

    /** 闸门状态: PENDING / SAMPLING_AUDIT / APPROVED / REJECTED / CANCELED */
    @Pattern(regexp = "PENDING|SAMPLING_AUDIT|APPROVED|REJECTED|CANCELED",
            message = "闸门状态仅支持 PENDING/SAMPLING_AUDIT/APPROVED/REJECTED/CANCELED")
    private String auditStatus;

    /** 审核人（服务端从知识条目审核人冗余） */
    private String auditedBy;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    /** 预计算客户数 */
    private Integer expectedRecipientCount;

    /** 预计算命中客户 ID 快照（逗号分隔, 只读） */
    private String snapshotCustomerIds;

    /** 任务状态: DRAFT / PENDING_AUDIT / APPROVED / PUBLISHING / COMPLETED / FAILED / REJECTED / CANCELED */
    @Pattern(regexp = "DRAFT|PENDING_AUDIT|APPROVED|PUBLISHING|COMPLETED|FAILED|REJECTED|CANCELED",
            message = "任务状态仅支持 DRAFT/PENDING_AUDIT/APPROVED/PUBLISHING/COMPLETED/FAILED/REJECTED/CANCELED")
    private String status;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 失败原因 */
    private String failReason;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建时间 */
    private LocalDateTime createTime;
}
