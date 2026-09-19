/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChannelCodeScanDto.java
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
 * SCRM 渠道活码扫码记录 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmChannelCodeScanDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 渠道活码 ID */
    @NotNull(message = "渠道活码 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long channelCodeId;

    /** 扫码者唯一标识 */
    @Size(max = 200, message = "扫码者标识长度不能超过 200")
    private String scannerUid;

    /** 扫码者昵称 */
    @Size(max = 200, message = "扫码者昵称长度不能超过 200")
    private String scannerNickname;

    /** 分配到的账号 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assignedAccountId;

    /** 扫码者 IP */
    @Size(max = 50, message = "IP 长度不能超过 50")
    private String ip;

    /** 扫码者 User-Agent */
    @Size(max = 500, message = "User-Agent 长度不能超过 500")
    private String userAgent;

    /** 扫码时间 */
    private LocalDateTime scannedAt;

    /** 添加状态: PENDING / ADDED / REJECTED */
    @NotBlank(message = "添加状态不能为空")
    @Size(max = 20, message = "添加状态长度不能超过 20")
    @Pattern(regexp = "PENDING|ADDED|REJECTED",
            message = "添加状态仅支持 PENDING/ADDED/REJECTED")
    private String added;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
