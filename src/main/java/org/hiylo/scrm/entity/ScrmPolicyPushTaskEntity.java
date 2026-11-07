/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmPolicyPushTaskEntity.java
 * Date : 2026/10/23 12:30:00
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
 * 政策推送任务实体（政策匹配推送域）。
 * <p>
 * 政策知识条目（{@code KnowledgeArticle}, articleType=POLICY）发布后, 按匹配规则
 * （税种/规模/地区/标签, 可关联 Segment）在草稿期预计算命中客户快照
 * （{@link #snapshotCustomerIds}, 逗号分隔）, 经<b>二级人工闸门</b>
 * （auditMode=SAMPLING 抽样 / FULL 全量; auditStatus PENDING → SAMPLING_AUDIT →
 * APPROVED / REJECTED）后发布, 发布为幂等操作（同一任务仅一稿两发: NotificationCenter
 * 推内部销售/会计 + 客户触达模板, 客户直发待企微凭据到位, 首期仅 APP 预览 + 人工转发）。
 * 状态机: DRAFT → PENDING_AUDIT → APPROVED → PUBLISHING → COMPLETED / FAILED;
 * 撤销 → CANCELED; 驳回 → REJECTED（可改后重提）。
 * 配额: 同一客户 30 天内至多收到 2 条政策推送（发布侧按客户聚合计数, 服务层保证）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_policy_push_task", schema = "scrm", indexes = {
        @Index(name = "idx_policy_push_article", columnList = "policy_article_id"),
        @Index(name = "idx_policy_push_status", columnList = "status")
})
@Data
public class ScrmPolicyPushTaskEntity {

    // ==================== 公共字段 ====================

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

    /** 乐观锁版本号（并发更新保护, 后写入者触发 OptimisticLockException） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间与版本号初值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 政策推送任务业务字段 ====================

    /** 任务编号: PP-YYYYMMDD-序号 */
    @Column(name = "task_no", nullable = false, length = 50)
    private String taskNo;

    /** 政策知识条目 ID（引用 scrm_knowledge_article.id, articleType=POLICY） */
    @Column(name = "policy_article_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long policyArticleId;

    /** 政策文号（快捷检索, 冗余） */
    @Column(name = "policy_code", length = 50)
    private String policyCode;

    /** 政策标题（冗余, 便于列表） */
    @Column(name = "policy_title", length = 500)
    private String policyTitle;

    /** 适用税种: VAT / IIT / CIT / SOCIAL_SECURITY / STAMP / ALL */
    @Column(name = "tax_type", length = 30)
    private String taxType = "ALL";

    /** 规模口径: MICRO / SMALL / MEDIUM / LARGE / LISTED / ALL（首期仅做 LISTED vs 非上市两档） */
    @Column(name = "size_range", length = 30)
    private String sizeRange = "ALL";

    /** 适用地区（空=全国） */
    @Column(name = "region", length = 100)
    private String region;

    /** 目标标签编码（逗号分隔, 可空） */
    @Column(name = "tag_codes", length = 500)
    private String tagCodes;

    /** 复用分群（可空; 优先级: segment 命中 > 标签/税种组合条件） */
    @Column(name = "segment_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long segmentId;

    /** 政策生效日期（快照窗口） */
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    /** 政策失效日期（快照窗口） */
    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    /** 二级人工闸门: SAMPLING(默认, 抽样核验) / FULL(全量核验, 重大政策强制) */
    @Column(name = "audit_mode", nullable = false, length = 20)
    private String auditMode = "SAMPLING";

    /** 闸门状态: PENDING / SAMPLING_AUDIT / APPROVED / REJECTED / CANCELED */
    @Column(name = "audit_status", nullable = false, length = 20)
    private String auditStatus = "PENDING";

    /** 审核人（沿用知识条目审核人, 不重复审批流） */
    @Column(name = "audited_by", length = 100)
    private String auditedBy;

    /** 审核时间 */
    @Column(name = "audited_at")
    private LocalDateTime auditedAt;

    /** 预计算客户数 */
    @Column(name = "expected_recipient_count", nullable = false)
    private Integer expectedRecipientCount = 0;

    /** 草稿期预计算命中客户 ID 快照（逗号分隔, 发布直接用, 不在线上逐客户跑条件） */
    @Column(name = "snapshot_customer_ids", columnDefinition = "TEXT")
    private String snapshotCustomerIds;

    /** 任务状态: DRAFT / PENDING_AUDIT / APPROVED / PUBLISHING / COMPLETED / FAILED / REJECTED / CANCELED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    /** 发布时间 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 完成时间 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 失败原因（通道/明细失败汇总） */
    @Column(name = "fail_reason", length = 500)
    private String failReason;

    /** 备注 */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 创建人 */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
