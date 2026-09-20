/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractSignDto.java
 * Date : 2026/08/05 08:55:12
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

/**
 * SCRM 合同签署请求 DTO。
 * <p>
 * 签署后合同状态由 PENDING_SIGNATURE 流转至 SIGNED, 记录签署人、签署方式与签署文件 URL,
 * 同时填充签署日期与生效日期 (缺省为签署当天)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmContractSignDto {

    /** 合同 ID */
    @NotNull(message = "合同 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 签署人 ID */
    @NotBlank(message = "签署人 ID 不能为空")
    @Size(max = 100, message = "签署人 ID 长度不能超过 100")
    private String signerId;

    /** 签署人名称 */
    @NotBlank(message = "签署人名称不能为空")
    @Size(max = 100, message = "签署人名称长度不能超过 100")
    private String signerName;

    /** 签署方式: ELECTRONIC (电子) / PAPER (纸质) / STAMP (盖章) */
    @NotBlank(message = "签署方式不能为空")
    @Pattern(regexp = "ELECTRONIC|PAPER|STAMP",
            message = "签署方式仅支持 ELECTRONIC/PAPER/STAMP")
    private String signatureMethod;

    /** 签署文件 URL (可空) */
    @Size(max = 500, message = "签署文件 URL 长度不能超过 500")
    private String signatureUrl;
}
