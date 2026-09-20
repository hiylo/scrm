/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactMappingDto.java
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
 * SCRM 外部联系人映射 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmExternalContactMappingDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 平台: WORK_WECHAT / DOUYIN / KUAISHOU / XIAOHONGSHU / OTHER */
    @NotBlank(message = "平台不能为空")
    @Size(max = 30, message = "平台长度不能超过 30")
    @Pattern(regexp = "WORK_WECHAT|DOUYIN|KUAISHOU|XIAOHONGSHU|OTHER",
            message = "平台仅支持 WORK_WECHAT/DOUYIN/KUAISHOU/XIAOHONGSHU/OTHER")
    private String platform;

    /** 平台外部联系人 ID */
    @NotBlank(message = "外部联系人 ID 不能为空")
    @Size(max = 200, message = "外部联系人 ID 长度不能超过 200")
    private String externalContactId;

    /** 外部用户 ID (可空) */
    @Size(max = 200, message = "外部用户 ID 长度不能超过 200")
    private String externalUserId;

    /** SCRM 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 外部联系人名称 (可空) */
    @Size(max = 200, message = "外部联系人名称长度不能超过 200")
    private String externalName;

    /** 外部联系人头像 URL (可空) */
    @Size(max = 500, message = "外部联系人头像 URL 长度不能超过 500")
    private String externalAvatar;

    /** 外部企业 ID (可空) */
    @Size(max = 200, message = "外部企业 ID 长度不能超过 200")
    private String externalCorpId;

    /** 联合 ID (可空) */
    @Size(max = 200, message = "联合 ID 长度不能超过 200")
    private String unionId;

    /** 开放平台 ID (可空) */
    @Size(max = 200, message = "开放平台 ID 长度不能超过 200")
    private String openId;

    /** 跟进人 ID (可空) */
    @Size(max = 100, message = "跟进人 ID 长度不能超过 100")
    private String followUserId;

    /** 跟进状态 (可空): NORMAL / TRANSFERRED / LOST */
    @Size(max = 20, message = "跟进状态长度不能超过 20")
    @Pattern(regexp = "NORMAL|TRANSFERRED|LOST",
            message = "跟进状态仅支持 NORMAL/TRANSFERRED/LOST")
    private String followStatus;

    /** 最后同步时间 */
    private LocalDateTime lastSyncAt;

    /** 同步状态: ACTIVE / DELETED / MERGED */
    @Size(max = 20, message = "同步状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|DELETED|MERGED",
            message = "同步状态仅支持 ACTIVE/DELETED/MERGED")
    private String syncStatus;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
