/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncConfigDto.java
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
 * SCRM 外部联系人同步配置 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmExternalContactSyncConfigDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 配置名称 */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 200, message = "配置名称长度不能超过 200")
    private String configName;

    /** 平台: WORK_WECHAT / DOUYIN / KUAISHOU / XIAOHONGSHU / OTHER */
    @NotBlank(message = "平台不能为空")
    @Size(max = 30, message = "平台长度不能超过 30")
    @Pattern(regexp = "WORK_WECHAT|DOUYIN|KUAISHOU|XIAOHONGSHU|OTHER",
            message = "平台仅支持 WORK_WECHAT/DOUYIN/KUAISHOU/XIAOHONGSHU/OTHER")
    private String platform;

    /** 企业 ID (可空) */
    @Size(max = 200, message = "企业 ID 长度不能超过 200")
    private String corpId;

    /** 应用 ID (可空) */
    @Size(max = 100, message = "应用 ID 长度不能超过 100")
    private String agentId;

    /** 应用密钥 (加密存储, 可空) */
    @Size(max = 500, message = "应用密钥长度不能超过 500")
    private String secret;

    /** 同步模式: INCREMENTAL / FULL */
    @Size(max = 20, message = "同步模式长度不能超过 20")
    @Pattern(regexp = "INCREMENTAL|FULL",
            message = "同步模式仅支持 INCREMENTAL/FULL")
    private String syncMode;

    /** 同步方向: ONE_WAY_IN / ONE_WAY_OUT / BIDIRECTIONAL */
    @Size(max = 20, message = "同步方向长度不能超过 20")
    @Pattern(regexp = "ONE_WAY_IN|ONE_WAY_OUT|BIDIRECTIONAL",
            message = "同步方向仅支持 ONE_WAY_IN/ONE_WAY_OUT/BIDIRECTIONAL")
    private String syncDirection;

    /** 同步频率: REALTIME / HOURLY / DAILY / WEEKLY / MANUAL */
    @Size(max = 20, message = "同步频率长度不能超过 20")
    @Pattern(regexp = "REALTIME|HOURLY|DAILY|WEEKLY|MANUAL",
            message = "同步频率仅支持 REALTIME/HOURLY/DAILY/WEEKLY/MANUAL")
    private String syncFrequency;

    /** 最后同步时间 (可空) */
    private LocalDateTime lastSyncAt;

    /** 最后同步状态 (可空): SUCCESS / FAILED / PARTIAL */
    @Size(max = 20, message = "最后同步状态长度不能超过 20")
    @Pattern(regexp = "SUCCESS|FAILED|PARTIAL",
            message = "最后同步状态仅支持 SUCCESS/FAILED/PARTIAL")
    private String lastSyncStatus;

    /** 最后同步数量 */
    private Integer lastSyncCount;

    /** 是否自动创建客户 */
    private Boolean autoCreateCustomer;

    /** 是否自动合并重复联系人 */
    private Boolean autoMergeDuplicate;

    /** JSON 字段映射配置 (可空) */
    private String fieldMapping;

    /** 是否启用 */
    private Boolean enabled;

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
