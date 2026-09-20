/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationBatchDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 通知批次 DTO。
 * <p>
 * 对应 {@code ScrmNotificationBatchEntity} 的业务字段, 用于批次查询返回。
 * 批次进度计数 (sentCount / successCount / failedCount / readCount) 由
 * {@code ScrmNotificationCenterService.processBatch} 推进刷新。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmNotificationBatchDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 批次名称 */
    @NotBlank(message = "批次名称不能为空")
    @Size(max = 200, message = "批次名称长度不能超过 200")
    private String batchName;

    /** 来源模板编码 (可空) */
    @Size(max = 100, message = "模板编码长度不能超过 100")
    private String templateCode;

    /** 通知渠道: IN_APP/EMAIL/SMS/PUSH/WEBHOOK */
    @NotBlank(message = "通知渠道不能为空")
    @Pattern(regexp = "IN_APP|EMAIL|SMS|PUSH|WEBHOOK",
            message = "通知渠道仅支持 IN_APP/EMAIL/SMS/PUSH/WEBHOOK")
    private String channel;

    /** 分类: SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION */
    @NotBlank(message = "分类不能为空")
    @Pattern(regexp = "SYSTEM|MARKETING|SERVICE|ALERT|REMINDER|VERIFICATION",
            message = "分类仅支持 SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION")
    private String category;

    /** 触发人 */
    @NotBlank(message = "触发人不能为空")
    @Size(max = 100, message = "触发人长度不能超过 100")
    private String triggeredBy;

    /** 总数 (查询返回) */
    private Integer totalCount;

    /** 已发送数 (查询返回) */
    private Integer sentCount;

    /** 成功数 (查询返回) */
    private Integer successCount;

    /** 失败数 (查询返回) */
    private Integer failedCount;

    /** 已读数 (查询返回) */
    private Integer readCount;

    /** 状态: PENDING/SENDING/COMPLETED/FAILED/CANCELLED (查询返回) */
    private String status;

    /** 开始时间 (查询返回) */
    private LocalDateTime startTime;

    /** 结束时间 (查询返回) */
    private LocalDateTime endTime;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
