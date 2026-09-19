/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceRedFlushDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 发票红冲请求 DTO。
 * <p>
 * 基于原发票创建一张金额为负的红冲发票 (类别 RED), 关联原发票 ID, 并将原发票状态流转至 RED_FLUSHED。
 * 红冲原因必填, 便于审计追溯。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmInvoiceRedFlushDto {

    /** 原发票 ID */
    @NotNull(message = "发票 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceId;

    /** 红冲原因 */
    @NotBlank(message = "红冲原因不能为空")
    @Size(max = 500, message = "红冲原因长度不能超过 500")
    private String reason;

    /** 红冲人 (可空) */
    @Size(max = 100, message = "红冲人长度不能超过 100")
    private String redFlushedBy;
}
