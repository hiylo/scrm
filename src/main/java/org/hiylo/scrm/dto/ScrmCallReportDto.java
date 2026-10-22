/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCallReportDto.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通话记录上报 DTO。
 * <p>
 * 销售 App 在每次通话结束后上报一条记录；服务端按
 * {@code (deviceId, callLogId)} 幂等去重，再按对端号码匹配客户。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCallReportDto {

    /** 设备 ID（App 首次启动生成并绑定） */
    @NotBlank(message = "设备 ID 不能为空")
    @Size(max = 128, message = "设备 ID 长度不能超过 128")
    private String deviceId;

    /** 通话方向: OUT / IN / MISSED / REJECTED */
    @NotBlank(message = "通话方向不能为空")
    @Size(max = 16, message = "通话方向长度不能超过 16")
    private String direction;

    /** 对端号码（明文, 服务端会哈希并脱敏落库） */
    @NotBlank(message = "对端号码不能为空")
    @Size(max = 32, message = "对端号码长度不能超过 32")
    private String peerNumber;

    /** 通话开始时间（ISO 格式, 客户端转 UTC 上报或带时区偏移） */
    @NotNull(message = "通话开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    /** 通话时长（秒, 0 表示未接通） */
    @Min(value = 0, message = "通话时长不能为负")
    @Max(value = 86400, message = "通话时长不能超过 24 小时")
    private Integer durationSec;

    /** 是否接通 */
    private Boolean connected;

    /** 来源: APP_CALL / CALL_LOG / SCREENING / MANUAL */
    @NotBlank(message = "来源不能为空")
    @Size(max = 16, message = "来源长度不能超过 16")
    private String source;

    /** 系统 CallLog._ID（可空, 无系统话单 ID 时服务端按近似去重） */
    private Long callLogId;

    /** 备注（可选） */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 可选: 客户端已匹配到的归属对象 ID（若 App 内发起, 已知归属） */
    private Long customerId;

    /** 可选: 归属对象类型（CUSTOMER / PUBLIC_SEA），与 customerId 配对使用 */
    private String customerType;
}
