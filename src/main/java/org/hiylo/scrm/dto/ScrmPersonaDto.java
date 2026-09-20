/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPersonaDto.java
 * Date : 2026/07/27 02:41:22
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
 * SCRM 人设 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmPersonaDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 人设 ID（与 scrm-server 执行侧共享，业务唯一） */
    @NotBlank(message = "人设 ID 不能为空")
    @Size(max = 100, message = "人设 ID 长度不能超过 100")
    private String personaId;

    /** 归属账号 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 昵称 */
    @Size(max = 200, message = "昵称长度不能超过 200")
    private String nickname;

    /** 头像 URL */
    @Size(max = 500, message = "头像 URL 长度不能超过 500")
    private String avatarUrl;

    /** 性别：MALE / FEMALE / UNKNOWN */
    @Size(max = 20, message = "性别长度不能超过 20")
    @Pattern(regexp = "MALE|FEMALE|UNKNOWN", message = "性别仅支持 MALE/FEMALE/UNKNOWN")
    private String gender;

    /** 年龄段 */
    @Size(max = 30, message = "年龄段长度不能超过 30")
    private String ageRange;

    /** 地区 */
    @Size(max = 100, message = "地区长度不能超过 100")
    private String region;

    /** 个性签名 */
    @Size(max = 500, message = "个性签名长度不能超过 500")
    private String signature;

    /** 话术风格标签（JSON 数组字符串） */
    private String styleTags;

    /** 话术模板 ID（JSON 数组字符串） */
    private String scriptTemplateIds;

    /** 自定义标签（JSON 数组字符串） */
    private String tags;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
