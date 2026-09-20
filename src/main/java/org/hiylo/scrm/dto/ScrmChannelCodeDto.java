/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChannelCodeDto.java
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
 * SCRM 渠道活码 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmChannelCodeDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 活码名称 */
    @NotBlank(message = "活码名称不能为空")
    @Size(max = 200, message = "活码名称长度不能超过 200")
    private String codeName;

    /** 活码类型: SINGLE(单账号) / MULTI(多账号) / ROUND_ROBIN(轮询) */
    @NotBlank(message = "活码类型不能为空")
    @Size(max = 20, message = "活码类型长度不能超过 20")
    @Pattern(regexp = "SINGLE|MULTI|ROUND_ROBIN",
            message = "活码类型仅支持 SINGLE/MULTI/ROUND_ROBIN")
    private String codeType;

    /** 平台类型 */
    @NotBlank(message = "平台类型不能为空")
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 二维码图片 URL（可空） */
    @Size(max = 500, message = "二维码 URL 长度不能超过 500")
    private String qrCodeUrl;

    /** SINGLE 类型重定向账号 ID（可空） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long redirectAccountId;

    /** MULTI 类型分配规则 JSON（可空） */
    private String assignRule;

    /** 欢迎语（可空） */
    private String welcomeMessage;

    /** 标签（可空, 逗号分隔） */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 状态: ACTIVE / INACTIVE */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|INACTIVE",
            message = "状态仅支持 ACTIVE/INACTIVE")
    private String status;

    /** 累计扫码数 */
    private Integer scanCount;

    /** 累计添加数 */
    private Integer addCount;

    /** 过期时间（可空） */
    private LocalDateTime expireAt;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
