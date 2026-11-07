/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmCompanyDto.java
 * Date : 2026/10/23 16:30:00
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 公司主体参数/视图 DTO。
 * <p>
 * 财税业务的业务主键：报税、证照、巡检、续约、合同均以公司为主体。本 DTO 同时
 * 用于创建/更新入参与详情返回（{@code contacts} 仅详情返回时填充）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCompanyDto {

    // ==================== 主键与审计 ====================

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 归属账号 ID（数据隔离，语义与 {@code scrm_customer.owner_account_id} 一致，
     * 即 {@code scrm_account.id}，<b>创建时必填</b>）。
     * <p>注意：不要传登录用户 ID——用户 ID 与账号 ID 是两个 ID 空间，混用会导致
     * 主体与联系人归属不一致、联系人挂靠被跨归属校验拒绝。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerAccountId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;

    // ==================== 主体基本信息 ====================

    /** 企业名称（营业执照登记名称） */
    @NotBlank(message = "企业名称不能为空")
    @Size(max = 200, message = "企业名称长度不能超过 200")
    private String companyName;

    /** 统一社会信用代码（GB32100，同一归属内唯一，可空：个体户/未核验） */
    @Size(max = 32, message = "统一社会信用代码长度不能超过 32")
    private String creditCode;

    /** 主体类型：ENTERPRISE / INDIVIDUAL_BUSINESS / SOLE_PROPRIETORSHIP / INDIVIDUAL */
    @Pattern(regexp = "ENTERPRISE|INDIVIDUAL_BUSINESS|SOLE_PROPRIETORSHIP|INDIVIDUAL",
            message = "主体类型仅支持 ENTERPRISE/INDIVIDUAL_BUSINESS/SOLE_PROPRIETORSHIP/INDIVIDUAL")
    private String companyType;

    /** 法定代表人 */
    @Size(max = 100, message = "法定代表人长度不能超过 100")
    private String legalRepresentative;

    /** 注册资本（原样保留单位，如「人民币100万元整」） */
    @Size(max = 100, message = "注册资本长度不能超过 100")
    private String registeredCapital;

    /** 成立日期 */
    private LocalDate establishDate;

    /** 注册地址 */
    @Size(max = 500, message = "注册地址长度不能超过 500")
    private String registeredAddress;

    /** 所属行业 */
    @Size(max = 100, message = "行业长度不能超过 100")
    private String industry;

    /** 人员规模 */
    @Size(max = 30, message = "人员规模长度不能超过 30")
    private String employeeScale;

    // ==================== 工商/税务状态 ====================

    /** 工商状态：ACTIVE / ABNORMAL / CANCELLED / REVOKED / MOVED / UNKNOWN */
    @Pattern(regexp = "ACTIVE|ABNORMAL|CANCELLED|REVOKED|MOVED|UNKNOWN",
            message = "工商状态仅支持 ACTIVE/ABNORMAL/CANCELLED/REVOKED/MOVED/UNKNOWN")
    private String businessStatus;

    /** 税务状态：NORMAL / ABNORMAL / ARREARS / CANCELLED / UNKNOWN */
    @Pattern(regexp = "NORMAL|ABNORMAL|ARREARS|CANCELLED|UNKNOWN",
            message = "税务状态仅支持 NORMAL/ABNORMAL/ARREARS/CANCELLED/UNKNOWN")
    private String taxStatus;

    /** 工商状态最近变更时间 */
    private LocalDateTime businessStatusChangedAt;

    // ==================== 服务关系 ====================

    /** 服务类型（逗号分隔多值）：BOOKKEEPING,REGISTRATION,DEREGISTRATION,QUALIFICATION,TAX_CONSULTING */
    @Size(max = 50, message = "服务类型长度不能超过 50")
    private String serviceType;

    /** 服务状态：PENDING / SERVING / SUSPENDED / TERMINATED */
    @Pattern(regexp = "PENDING|SERVING|SUSPENDED|TERMINATED",
            message = "服务状态仅支持 PENDING/SERVING/SUSPENDED/TERMINATED")
    private String serviceStatus;

    /** 服务开始日期 */
    private LocalDate serviceStartDate;

    /** 服务结束日期（续费预警依据） */
    private LocalDate serviceEndDate;

    // ==================== 来源与核验 ====================

    /** 来源：MANUAL / OCR / IMPORT / INSPECTION / CUSTOMER_CONVERT */
    @Pattern(regexp = "MANUAL|OCR|IMPORT|INSPECTION|CUSTOMER_CONVERT",
            message = "来源仅支持 MANUAL/OCR/IMPORT/INSPECTION/CUSTOMER_CONVERT")
    private String source;

    /** 来源单据 ID（证照识别任务 ID 等） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceRefId;

    /** 是否已核验 */
    private Boolean verified;

    /** 核验时间 */
    private LocalDateTime verifiedAt;

    /**
     * 负责会计的用户 ID（代账分派维度）。
     * <p>为空表示未指派，相关提醒回落到配置的默认收件人。</p>
     */
    private Long accountantId;

    /** 负责会计姓名（冗余，便于列表直接展示） */
    private String accountantName;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private String createdBy;

    // ==================== 详情聚合（只读）====================

    /** 联系人列表（仅详情返回时填充） */
    private List<ScrmCustomerDto> contacts;

    /** 联系人数量（列表返回时填充） */
    private Long contactCount;
}
