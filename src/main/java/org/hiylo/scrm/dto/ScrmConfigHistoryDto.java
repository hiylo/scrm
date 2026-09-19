/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConfigHistoryDto.java
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

import java.time.LocalDateTime;

/**
 * SCRM 配置变更历史 DTO。
 * <p>
 * 对应 {@code ScrmConfigHistoryEntity} 的业务字段, 用于历史查询接口返回与手动记录场景入参。
 * changeType / reviewStatus 以枚举字符串校验合法性。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmConfigHistoryDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 配置 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 配置键 */
    @NotBlank(message = "配置键不能为空")
    @Size(max = 200, message = "配置键长度不能超过 200")
    private String configKey;

    /** 配置名称 (可空, 快照) */
    @Size(max = 200, message = "配置名称长度不能超过 200")
    private String configName;

    /** 配置分组 (可空, 快照) */
    @Size(max = 100, message = "配置分组长度不能超过 100")
    private String configGroup;

    /** 旧值 (可空, TEXT) */
    private String oldValue;

    /** 新值 (可空, TEXT) */
    private String newValue;

    /** 旧显示值 (可空) */
    @Size(max = 2000, message = "旧显示值长度不能超过 2000")
    private String oldDisplayValue;

    /** 新显示值 (可空) */
    @Size(max = 2000, message = "新显示值长度不能超过 2000")
    private String newDisplayValue;

    /** 变更类型: CREATE/UPDATE/DELETE/ENABLE/DISABLE/IMPORT/EXPORT/RESET */
    @NotBlank(message = "变更类型不能为空")
    @Pattern(regexp = "CREATE|UPDATE|DELETE|ENABLE|DISABLE|IMPORT|EXPORT|RESET",
            message = "变更类型仅支持 CREATE/UPDATE/DELETE/ENABLE/DISABLE/IMPORT/EXPORT/RESET")
    private String changeType;

    /** 变更原因 (可空) */
    @Size(max = 500, message = "变更原因长度不能超过 500")
    private String changeReason;

    /** 变更人 */
    @NotBlank(message = "变更人不能为空")
    @Size(max = 100, message = "变更人长度不能超过 100")
    private String changedBy;

    /** 变更时间 */
    private LocalDateTime changedAt;

    /** IP 地址 (可空) */
    @Size(max = 50, message = "IP 地址长度不能超过 50")
    private String ipAddress;

    /** User-Agent (可空) */
    @Size(max = 500, message = "User-Agent 长度不能超过 500")
    private String userAgent;

    /** 会话 ID (可空) */
    @Size(max = 200, message = "会话 ID 长度不能超过 200")
    private String sessionId;

    /** 可回滚 (默认 TRUE) */
    private Boolean rollbackPossible;

    /** 回滚关联历史 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long rollbackById;

    /** 已回滚 (默认 FALSE) */
    private Boolean isRolledBack;

    /** 回滚时间 (可空) */
    private LocalDateTime rolledBackAt;

    /** 回滚人 (可空) */
    @Size(max = 100, message = "回滚人长度不能超过 100")
    private String rolledBackBy;

    /** 审核状态: PENDING/APPROVED/REJECTED (可空) */
    @Pattern(regexp = "^$|PENDING|APPROVED|REJECTED",
            message = "审核状态仅支持 PENDING/APPROVED/REJECTED")
    private String reviewStatus;

    /** 审核人 (可空) */
    @Size(max = 100, message = "审核人长度不能超过 100")
    private String reviewedBy;

    /** 审核时间 (可空) */
    private LocalDateTime reviewedAt;

    /** 附加数据 JSON (可空, TEXT) */
    private String metadata;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;
}
