/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLicenseOcrTaskDto.java
 * Date : 2026/10/23 14:00:00
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
import java.util.List;
import java.util.Map;

/**
 * 证照 OCR 识别任务 DTO。
 * <p>
 * 承载证照识别任务的创建参数与展示结果（识别结果 JSON 脱敏后/复核状态）。创建时
 * {@code licenseType} 必填, {@code side} 仅身份证类型允许, 其余字段可空。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmLicenseOcrTaskDto {

    // ==================== 创建参数 ====================

    /** 关联客户 ID（可空） */
    private Long customerId;

    /** 目标档案域业务单号（合同/发票等, 可空） */
    private Long businessId;

    /**
     * 业务主体（公司）ID（可空）。
     * <p>为空时服务层自动从 {@code customerId} 所属公司推导；显式传入则以其为准。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long companyId;

    /** 识别类型: license / idcard / account_license / general */
    @NotBlank(message = "识别类型不能为空")
    @Pattern(regexp = "license|idcard|account_license|general",
            message = "识别类型仅支持 license/idcard/account_license/general")
    private String licenseType;

    /** 身份证翻面（仅 idcard）: front / back */
    @Pattern(regexp = "front|back", message = "翻面仅支持 front/back")
    private String side;

    // ==================== 只读字段（查询返回） ====================

    /** 任务 ID */
    private Long id;

    /** 归属账号 ID */
    private Long ownerAccountId;

    /** 原始文件名 */
    private String fileName;

    /** 证照原图/PDF 对象存储 key */
    private String objectKey;

    /** 请求链路标识 */
    private String requestId;

    /** 实际执行模式 */
    private String mode;

    /** 引擎标识 */
    private String engine;

    /** 服务端模板判定结果 */
    private String docType;

    /** 任务状态: PENDING / SUCCEEDED / PARTIAL / FAILED */
    private String taskStatus;

    /** OCR 顶层 ok */
    private Boolean overallOk;

    /** 识别字段列表（脱敏后展示, 敏感值打码） */
    private List<Map<String, Object>> fields;

    /** 交叉校验结果 */
    private Map<String, Object> verification;

    /** OCR 全文（内部复核用, 含敏感字段原文; 需复核权限） */
    private String rawText;

    /** 复核状态: NONE / REQUIRED / REVIEWED */
    private String reviewStatus;

    /** 待复核字段清单 */
    private List<Map<String, Object>> reviewFields;

    /** 复核备注 */
    private String reviewRemark;

    /** 复核人 */
    private String reviewer;

    /** 复核时间 */
    private LocalDateTime reviewedAt;

    /** 错误信息（脱敏） */
    private String errorMessage;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}