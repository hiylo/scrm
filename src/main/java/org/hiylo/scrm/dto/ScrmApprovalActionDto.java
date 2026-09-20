/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalActionDto.java
 * Date : 2026/08/05 08:55:12
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

/**
 * SCRM 审批操作 DTO。
 * <p>
 * 同意 / 驳回 / 转交 / 加签 / 抄送 / 催办 / 评论接口入参。instanceId 与 action 必填;
 * comment 为审批意见; actionData 为操作附带数据 JSON (如转交目标人 / 加签用户列表 / 抄送人)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmApprovalActionDto {

    /** 审批实例 ID */
    @NotNull(message = "审批实例 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    /** 操作类型: APPROVE/REJECT/TRANSFER/COUNTERSIGN/CC/URGE/COMMENT */
    @NotBlank(message = "操作类型不能为空")
    @Pattern(regexp = "APPROVE|REJECT|TRANSFER|COUNTERSIGN|CC|URGE|COMMENT",
            message = "操作类型仅支持 APPROVE/REJECT/TRANSFER/COUNTERSIGN/CC/URGE/COMMENT")
    private String action;

    /** 操作人 ID */
    @NotBlank(message = "操作人 ID 不能为空")
    @Size(max = 100, message = "操作人 ID 长度不能超过 100")
    private String operatorId;

    /** 操作人名称 (可空) */
    @Size(max = 100, message = "操作人名称长度不能超过 100")
    private String operatorName;

    /** 操作人角色 (可空) */
    @Size(max = 100, message = "操作人角色长度不能超过 100")
    private String operatorRole;

    /** 审批意见 (可空) */
    @Size(max = 2000, message = "审批意见长度不能超过 2000")
    private String comment;

    /** 操作数据 JSON: {transferredTo,countersignUsers,ccUsers} (可空) */
    private String actionData;

    /** 附件 (可空, 逗号分隔 URL) */
    @Size(max = 1000, message = "附件长度不能超过 1000")
    private String attachments;
}
