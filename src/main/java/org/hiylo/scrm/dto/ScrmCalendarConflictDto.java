/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCalendarConflictDto.java
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

import java.time.LocalDateTime;

/**
 * SCRM 营销日历冲突检测 DTO。
 * <p>
 * 对应 {@code ScrmCalendarConflictEntity} 的业务字段, 用于冲突解决等接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCalendarConflictDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 冲突一方事件 ID */
    @NotNull(message = "事件1 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long event1Id;

    /** 冲突另一方事件 ID */
    @NotNull(message = "事件2 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long event2Id;

    /** 冲突类型: TIME_OVERLAP/CHANNEL_CONFLICT/RESOURCE_CONFLICT/AUDIENCE_OVERLAP/BUDGET_EXCEED */
    @NotBlank(message = "冲突类型不能为空")
    @Pattern(regexp = "TIME_OVERLAP|CHANNEL_CONFLICT|RESOURCE_CONFLICT|AUDIENCE_OVERLAP|BUDGET_EXCEED",
            message = "冲突类型仅支持 TIME_OVERLAP/CHANNEL_CONFLICT/RESOURCE_CONFLICT/AUDIENCE_OVERLAP/BUDGET_EXCEED")
    private String conflictType;

    /** 严重程度: WARNING/ERROR/INFO (默认 WARNING) */
    @Pattern(regexp = "WARNING|ERROR|INFO|",
            message = "严重程度仅支持 WARNING/ERROR/INFO")
    private String severity;

    /** 冲突描述 (可空) */
    @Size(max = 500, message = "冲突描述长度不能超过 500")
    private String description;

    /** 重叠渠道 (逗号分隔, 可空) */
    @Size(max = 500, message = "重叠渠道长度不能超过 500")
    private String overlappingChannels;

    /** 重叠客群 (可空) */
    @Size(max = 500, message = "重叠客群长度不能超过 500")
    private String overlappingAudience;

    /** 解决状态: UNRESOLVED/RESOLVED/IGNORED (默认 UNRESOLVED) */
    @Pattern(regexp = "UNRESOLVED|RESOLVED|IGNORED|",
            message = "解决状态仅支持 UNRESOLVED/RESOLVED/IGNORED")
    private String resolvedStatus;

    /** 解决人 (可空) */
    @Size(max = 100, message = "解决人长度不能超过 100")
    private String resolvedBy;

    /** 解决时间 (可空) */
    private LocalDateTime resolvedAt;

    /** 解决备注 (可空) */
    @Size(max = 500, message = "解决备注长度不能超过 500")
    private String resolutionNote;

    /** 检测时间 (查询返回) */
    private LocalDateTime detectedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
