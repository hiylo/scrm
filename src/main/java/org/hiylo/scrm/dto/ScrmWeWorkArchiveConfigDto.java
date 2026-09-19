/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkArchiveConfigDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企微会话存档配置 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWeWorkArchiveConfigDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 配置名称 */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 200, message = "配置名称长度不能超过 200")
    private String configName;

    /** 企业 corpid */
    @NotBlank(message = "企业 corpid 不能为空")
    @Size(max = 100, message = "corpid 长度不能超过 100")
    private String corpId;

    /** 应用 agentid（可空） */
    @Size(max = 100, message = "agentid 长度不能超过 100")
    private String agentId;

    /** 会话存档 secret */
    @NotBlank(message = "会话存档 secret 不能为空")
    @Size(max = 500, message = "secret 长度不能超过 500")
    private String secret;

    /** RSA 私钥 PEM */
    @NotBlank(message = "RSA 私钥不能为空")
    @Size(max = 2000, message = "私钥长度不能超过 2000")
    private String privateKey;

    /** 企微会话存档 SDK 路径（可空） */
    @Size(max = 500, message = "SDK 路径长度不能超过 500")
    private String sdkLibPath;

    /** 状态: ACTIVE / INACTIVE / ERROR */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 最后拉取的 seq */
    private Long lastSeq;

    /** 最后拉取时间 */
    private LocalDateTime lastFetchAt;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
