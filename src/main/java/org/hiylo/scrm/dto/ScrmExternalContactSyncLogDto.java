/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncLogDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 外部联系人同步日志 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmExternalContactSyncLogDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 同步任务 ID */
    @NotNull(message = "同步任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 外部联系人 ID */
    @NotBlank(message = "外部联系人 ID 不能为空")
    @Size(max = 200, message = "外部联系人 ID 长度不能超过 200")
    private String externalContactId;

    /** 外部联系人名称 (可空) */
    @Size(max = 200, message = "外部联系人名称长度不能超过 200")
    private String externalName;

    /** 外部联系人头像 URL (可空) */
    @Size(max = 500, message = "外部联系人头像 URL 长度不能超过 500")
    private String externalAvatar;

    /** 操作类型: CREATE / UPDATE / DELETE / SKIP / MERGE */
    @NotBlank(message = "操作类型不能为空")
    @Size(max = 20, message = "操作类型长度不能超过 20")
    @Pattern(regexp = "CREATE|UPDATE|DELETE|SKIP|MERGE",
            message = "操作类型仅支持 CREATE/UPDATE/DELETE/SKIP/MERGE")
    private String operationType;

    /** 关联客户 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** JSON 字段变更详情 (可空) */
    private String fieldChanges;

    /** 处理状态: SUCCESS / FAILED / SKIPPED */
    @Size(max = 20, message = "处理状态长度不能超过 20")
    @Pattern(regexp = "SUCCESS|FAILED|SKIPPED",
            message = "处理状态仅支持 SUCCESS/FAILED/SKIPPED")
    private String status;

    /** 错误信息 (可空) */
    @Size(max = 500, message = "错误信息长度不能超过 500")
    private String errorMessage;

    /** 处理时间 */
    private LocalDateTime processedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
