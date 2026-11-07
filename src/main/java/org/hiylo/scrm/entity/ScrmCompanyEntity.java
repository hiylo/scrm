/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmCompanyEntity.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公司主体实体（财税业务主键）。
 * <p>
 * 财税系统的服务单元是「企业/个体户」，而不是「微信联系人」。本实体承接：
 * </p>
 * <ul>
 *   <li><b>主体档案</b>：企业名称、统一社会信用代码（GB32100）、法人、注册资本、成立日期、住所</li>
 *   <li><b>状态监控</b>：工商状态（在营/异常/注销/吊销）、税务状态（正常/非正常户/欠税），
 *       供模块① 巡检写入并驱动异常告警</li>
 *   <li><b>服务关系</b>：服务类型（代账/注册/注销/资质）、服务状态、服务起止日期，
 *       供模块② 续费与流失预警使用</li>
 *   <li><b>来源与核验</b>：手工录入 / 证照 OCR / 批量导入 / 巡检发现，是否已核验</li>
 * </ul>
 * <p>
 * <b>与联系人（{@link ScrmCustomerEntity}）的关系</b>：一个公司下可挂多个联系人
 * （老板、多名财务、纯电话/线下联系人），联系人的 {@code companyId} 指向本实体；
 * 联系人只属一家公司（单归属，不做多公司关联）。
 * </p>
 * <p>
 * <b>两个维度必须区分</b>（2026-10-23 设计确认）：
 * </p>
 * <ul>
 *   <li>获客渠道 = {@code sourceChannel} / {@code sourceChannelCodeId}（客户「从哪来」）</li>
 *   <li>联系方式 = 联系人的 {@code platformType} + {@code platformCustomerUid}（「怎么联系」）</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_company", schema = "scrm", indexes = {
        @Index(name = "idx_company_owner", columnList = "owner_account_id"),
        @Index(name = "idx_company_name", columnList = "company_name"),
        @Index(name = "idx_company_service_status", columnList = "service_status"),
        @Index(name = "idx_company_business_status", columnList = "business_status")
})
@Data
public class ScrmCompanyEntity {

    /** 主体类型: 企业 */
    public static final String TYPE_ENTERPRISE = "ENTERPRISE";

    /** 主体类型: 个体工商户 */
    public static final String TYPE_INDIVIDUAL_BUSINESS = "INDIVIDUAL_BUSINESS";

    /** 主体类型: 个人独资企业 */
    public static final String TYPE_SOLE_PROPRIETORSHIP = "SOLE_PROPRIETORSHIP";

    /** 主体类型: 个人（无营业执照） */
    public static final String TYPE_INDIVIDUAL = "INDIVIDUAL";

    /** 工商状态: 在营 */
    public static final String BUSINESS_ACTIVE = "ACTIVE";

    /** 工商状态: 经营异常 */
    public static final String BUSINESS_ABNORMAL = "ABNORMAL";

    /** 工商状态: 已注销 */
    public static final String BUSINESS_CANCELLED = "CANCELLED";

    /** 工商状态: 已吊销 */
    public static final String BUSINESS_REVOKED = "REVOKED";

    /** 工商状态: 已迁出 */
    public static final String BUSINESS_MOVED = "MOVED";

    /** 工商状态: 未知 */
    public static final String BUSINESS_UNKNOWN = "UNKNOWN";

    /** 税务状态: 正常 */
    public static final String TAX_NORMAL = "NORMAL";

    /** 税务状态: 非正常户 */
    public static final String TAX_ABNORMAL = "ABNORMAL";

    /** 税务状态: 欠税 */
    public static final String TAX_ARREARS = "ARREARS";

    /** 税务状态: 已注销 */
    public static final String TAX_CANCELLED = "CANCELLED";

    /** 税务状态: 未知 */
    public static final String TAX_UNKNOWN = "UNKNOWN";

    /** 服务状态: 待启动 */
    public static final String SERVICE_PENDING = "PENDING";

    /** 服务状态: 服务中 */
    public static final String SERVICE_SERVING = "SERVING";

    /** 服务状态: 暂停 */
    public static final String SERVICE_SUSPENDED = "SUSPENDED";

    /** 服务状态: 已终止 */
    public static final String SERVICE_TERMINATED = "TERMINATED";

    /** 来源: 手工录入 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 来源: 证照 OCR 识别 */
    public static final String SOURCE_OCR = "OCR";

    /** 来源: 批量导入 */
    public static final String SOURCE_IMPORT = "IMPORT";

    /** 来源: 巡检发现 */
    public static final String SOURCE_INSPECTION = "INSPECTION";

    /** 来源: 客户转化 */
    public static final String SOURCE_CUSTOMER_CONVERT = "CUSTOMER_CONVERT";

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 创建时间 */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 乐观锁版本号（并发更新保护） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** 归属账号 ID（数据隔离维度） */
    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    /** 企业名称（营业执照登记名称） */
    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    /** 统一社会信用代码（GB32100，唯一，可空：个体户/未核验） */
    @Column(name = "credit_code", length = 32)
    private String creditCode;

    /** 主体类型: ENTERPRISE / INDIVIDUAL_BUSINESS / SOLE_PROPRIETORSHIP / INDIVIDUAL */
    @Column(name = "company_type", nullable = false, length = 30)
    private String companyType = TYPE_ENTERPRISE;

    /** 法定代表人 */
    @Column(name = "legal_representative", length = 100)
    private String legalRepresentative;

    /** 注册资本（原样保留单位，如「人民币100万元整」） */
    @Column(name = "registered_capital", length = 100)
    private String registeredCapital;

    /** 成立日期 */
    @Column(name = "establish_date")
    private LocalDate establishDate;

    /** 注册地址 */
    @Column(name = "registered_address", length = 500)
    private String registeredAddress;

    /** 所属行业 */
    @Column(name = "industry", length = 100)
    private String industry;

    /** 人员规模 */
    @Column(name = "employee_scale", length = 30)
    private String employeeScale;

    /** 工商状态: ACTIVE / ABNORMAL / CANCELLED / REVOKED / MOVED / UNKNOWN */
    @Column(name = "business_status", nullable = false, length = 30)
    private String businessStatus = BUSINESS_ACTIVE;

    /** 税务状态: NORMAL / ABNORMAL / ARREARS / CANCELLED / UNKNOWN */
    @Column(name = "tax_status", nullable = false, length = 30)
    private String taxStatus = TAX_NORMAL;

    /** 工商状态最近变更时间（巡检比对用，只在状态变化时刷新） */
    @Column(name = "business_status_changed_at")
    private LocalDateTime businessStatusChangedAt;

    /** 服务类型（逗号分隔多值）: BOOKKEEPING,REGISTRATION,DEREGISTRATION,QUALIFICATION,TAX_CONSULTING */
    @Column(name = "service_type", length = 50)
    private String serviceType;

    /** 服务状态: PENDING / SERVING / SUSPENDED / TERMINATED */
    @Column(name = "service_status", nullable = false, length = 30)
    private String serviceStatus = SERVICE_PENDING;

    /** 服务开始日期 */
    @Column(name = "service_start_date")
    private LocalDate serviceStartDate;

    /** 服务结束日期（续费预警依据） */
    @Column(name = "service_end_date")
    private LocalDate serviceEndDate;

    /** 来源: MANUAL / OCR / IMPORT / INSPECTION / CUSTOMER_CONVERT */
    @Column(name = "source", nullable = false, length = 30)
    private String source = SOURCE_MANUAL;

    /** 来源单据 ID（证照识别任务 ID / 巡检任务 ID 等） */
    @Column(name = "source_ref_id")
    private Long sourceRefId;

    /** 是否已核验（营业执照 OCR 通过或人工确认） */
    @Column(name = "verified", nullable = false)
    private Boolean verified = false;

    /** 核验时间 */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * 负责会计的用户 ID。
     * <p>
     * 代账场景的核心分派维度：报税/续费/政策提醒优先发给该会计，
     * 为空（未指派）时回落到 {@code scrm.*.notify-recipient} 配置的默认收件人，
     * 因此存量公司不指派也能正常工作。
     * </p>
     */
    @Column(name = "accountant_id")
    private Long accountantId;

    /** 负责会计姓名（冗余字段，避免列表页回查用户表） */
    @Column(name = "accountant_name", length = 100)
    private String accountantName;

    /** 备注 */
    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * 持久化前回调: 自动填充创建/更新时间、版本号初值与默认状态。
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
        if (companyType == null || companyType.isBlank()) {
            companyType = TYPE_ENTERPRISE;
        }
        if (businessStatus == null || businessStatus.isBlank()) {
            businessStatus = BUSINESS_ACTIVE;
        }
        if (taxStatus == null || taxStatus.isBlank()) {
            taxStatus = TAX_NORMAL;
        }
        if (serviceStatus == null || serviceStatus.isBlank()) {
            serviceStatus = SERVICE_PENDING;
        }
        if (source == null || source.isBlank()) {
            source = SOURCE_MANUAL;
        }
        if (verified == null) {
            verified = false;
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间。
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
