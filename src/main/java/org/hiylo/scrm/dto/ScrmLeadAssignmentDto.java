/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadAssignmentDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * 线索分配流水 DTO。
 * <p>
 * 用于记录与查询线索在公海池中的分配/领取/转移/回收轨迹。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmLeadAssignmentDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 公海客户 ID */
    @NotNull(message = "公海客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publicSeaCustomerId;

    /** 被分配人 userId */
    @NotBlank(message = "被分配人 userId 不能为空")
    @Size(max = 100, message = "被分配人 userId 长度不能超过 100")
    private String assignedTo;

    /** 分配人 userId (CLAIM 类型时与 assigned_to 相同) */
    @Size(max = 100, message = "分配人 userId 长度不能超过 100")
    private String assignedBy;

    /** 分配类型: CLAIM(领取)/ASSIGN(分配)/TRANSFER(转移) */
    @NotBlank(message = "分配类型不能为空")
    @Size(max = 20, message = "分配类型长度不能超过 20")
    @Pattern(regexp = "CLAIM|ASSIGN|TRANSFER",
            message = "分配类型仅支持 CLAIM/ASSIGN/TRANSFER")
    private String assignmentType;

    /** 上一手归属人 userId (TRANSFER 时记录, 可空) */
    @Size(max = 100, message = "上一手归属人 userId 长度不能超过 100")
    private String previousOwner;

    /** 分配状态: ACTIVE/RECALLED/TRANSFERRED/CONVERTED */
    @Size(max = 20, message = "分配状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|RECALLED|TRANSFERRED|CONVERTED",
            message = "分配状态仅支持 ACTIVE/RECALLED/TRANSFERRED/CONVERTED")
    private String status;

    /** 分配时间 */
    private LocalDateTime assignedAt;

    /** 回收时间 */
    private LocalDateTime recalledAt;

    /** 分配过期时间 */
    private LocalDateTime expireAt;

    /** 分配备注 */
    @Size(max = 500, message = "分配备注长度不能超过 500")
    private String note;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
