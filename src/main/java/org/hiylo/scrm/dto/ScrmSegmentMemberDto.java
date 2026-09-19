/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentMemberDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户分群成员 DTO。
 * <p>
 * 对应 {@code ScrmSegmentMemberEntity} 的业务字段, 手动添加 / 批量添加成员接口入参。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmSegmentMemberDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分群 ID */
    @NotNull(message = "分群 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long segmentId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 客户等级 (可空) */
    @Size(max = 50, message = "客户等级长度不能超过 50")
    private String customerLevel;

    /** 加入时间 (可空, 未填则取当前时间) */
    private LocalDateTime joinedAt;

    /** 匹配分数 (0-1, 默认 1.0) */
    private Double matchScore;

    /** 匹配详情 (JSON, 可空) */
    private String matchDetails;

    /** 是否当前成员 (默认 true) */
    private Boolean isCurrentMember;

    /** 来源: AUTO / MANUAL / IMPORT (默认 MANUAL) */
    @Pattern(regexp = "AUTO|MANUAL|IMPORT", message = "来源仅支持 AUTO/MANUAL/IMPORT")
    private String source;

    /** 离开时间 (查询返回) */
    private LocalDateTime leftAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
