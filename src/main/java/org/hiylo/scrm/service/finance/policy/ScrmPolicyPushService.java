/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmPolicyPushService.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.ScrmPolicyPushProperties;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.dto.ScrmPolicyPushTaskDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.entity.ScrmPolicyPushTaskEntity;
import org.hiylo.scrm.entity.ScrmSegmentMemberEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmPolicyPushTaskRepository;
import org.hiylo.scrm.repository.ScrmSegmentMemberRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.repository.ScrmTagRepository;
import org.hiylo.scrm.service.ScrmNotificationCenterService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 政策变更 → 客户推送服务（政策匹配推送域核心, T08D）。
 * <p>
 * 职责: 政策条目（{@code KnowledgeArticle}, articleType=POLICY）录入校验 → 匹配规则
 * （税种/规模/地区/标签, 可复用 Segment）→ 草稿期预计算命中客户快照
 * （不逐客户在线跑条件）→ 二级人工闸门（抽样核验 / 全量核验）→ 发布（一稿两发:
 * NotificationCenter 推内部销售/会计 + 客户触达文案 APP 预览由销售人工转发）→
 * 配额控制（同一客户 30 天内至多 2 条）。
 * </p>
 * <p>
 * <b>双重人工闸门</b>: 一级=政策条目必须 status=PUBLISHED 且 reviewStatus=APPROVED
 * （知识库审核流, 含 180 天复核）; 二级=推送任务 auditMode=SAMPLING（默认抽样 10 条
 * 人工核验）/FULL（重大政策强制全量）。未过审<b>零对外消息</b>。
 * <b>匹配实现</b>: 优先 segmentId 命中的当前成员; 否则按 tagCodes 命中的标签客户取交集,
 * 再按 customer 维度税种标签（tagCode=TAG_VAT 等）过滤; 无标签无分段时按税种维度兜底
 * （首期不做规模档位维护, LISTED/ALL 均视为全量命中）。
 * <b>客户触达</b>: 企微官方凭据未到位, 本期只发内部提醒（客户文案仅供 APP 预览 +
 * 销售人工转发）, 不产生任何对外消息（合规红线）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmPolicyPushService {

    // ==================== 任务状态常量 ====================

    /** 任务状态: 草稿 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 任务状态: 待审核 */
    public static final String STATUS_PENDING_AUDIT = "PENDING_AUDIT";
    /** 任务状态: 已审批 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 任务状态: 发布中 */
    public static final String STATUS_PUBLISHING = "PUBLISHING";
    /** 任务状态: 已完成 */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** 任务状态: 失败 */
    public static final String STATUS_FAILED = "FAILED";
    /** 任务状态: 已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 任务状态: 已取消 */
    public static final String STATUS_CANCELED = "CANCELED";

    /** 闸门状态: 待审核 */
    private static final String AUDIT_PENDING = "PENDING";
    /** 闸门状态: 抽样核验中 */
    private static final String AUDIT_SAMPLING = "SAMPLING_AUDIT";
    /** 闸门状态: 通过 */
    private static final String AUDIT_APPROVED = "APPROVED";
    /** 闸门状态: 驳回 */
    private static final String AUDIT_REJECTED = "REJECTED";

    /** 审核模式: 抽样 */
    private static final String MODE_SAMPLING = "SAMPLING";
    /** 审核模式: 全量 */
    private static final String MODE_FULL = "FULL";

    /** 知识条目类型: 政策 */
    private static final String ARTICLE_TYPE_POLICY = "POLICY";
    /** 知识条目状态: 已发布 */
    private static final String ARTICLE_STATUS_PUBLISHED = "PUBLISHED";
    /** 知识条目审核状态: 已通过 */
    private static final String ARTICLE_REVIEW_APPROVED = "APPROVED";

    /** 推送关联类型: 政策推送任务（幂等 / 配额统计） */
    public static final String RELATED_TYPE_POLICY_PUSH = "POLICY_PUSH";

    /** 推送发送者 ID */
    private static final String SENDER_ID = "policy-system";

    // ==================== 依赖 ====================

    /** 政策推送任务数据访问层 */
    private final ScrmPolicyPushTaskRepository taskRepository;

    /** 知识条目数据访问层（政策条目校验） */
    private final ScrmKnowledgeArticleRepository articleRepository;

    /** 客户数据访问层（匹配兜底 / 名称补全） */
    private final ScrmCustomerRepository customerRepository;

    /** 标签数据访问层（tagCodes → tagId） */
    private final ScrmTagRepository tagRepository;

    /** 客户标签数据访问层（标签命中客户） */
    private final ScrmTagCustomerRepository tagCustomerRepository;

    /** 分群成员数据访问层（Segment 命中客户） */
    private final ScrmSegmentMemberRepository segmentMemberRepository;

    /** 通知中心门面（内部提醒推送） */
    private final ScrmNotificationCenterService notificationCenterService;

    /** 通知记录数据访问层（配额 / 幂等统计） */
    private final ScrmNotificationRepository notificationRepository;

    /** 政策推送配置 */
    private final ScrmPolicyPushProperties properties;

    // ============================================================
    // 任务创建与匹配预计算
    // ============================================================

    /**
     * 创建政策推送任务（DRAFT, 草稿期预计算命中客户快照）。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 政策条目未审核 / 已有任务
     */
    @Transactional
    public ScrmPolicyPushTaskEntity createTask(ScrmPolicyPushTaskDto dto) throws ScrmException {
        if (dto == null || dto.getPolicyArticleId() == null) {
            throw ScrmException.badRequest("任务参数与政策条目 ID 不能为空");
        }
        ScrmKnowledgeArticleEntity article = articleRepository.findById(dto.getPolicyArticleId())
                .orElseThrow(() -> ScrmException.notFound("政策知识条目不存在: id=" + dto.getPolicyArticleId()));
        assertPolicyPublishable(article);
        List<ScrmPolicyPushTaskEntity> existing = taskRepository.findByPolicyArticleId(article.getId());
        boolean duplicated = existing.stream()
                .anyMatch(t -> !STATUS_CANCELED.equals(t.getStatus()) && !STATUS_REJECTED.equals(t.getStatus()));
        if (duplicated) {
            throw ScrmException.conflict("该政策条目已有未结束的推送任务: articleId=" + article.getId());
        }
        ScrmPolicyPushTaskEntity entity = new ScrmPolicyPushTaskEntity();
        entity.setTaskNo(generateTaskNo());
        entity.setPolicyArticleId(article.getId());
        entity.setPolicyCode(extractPolicyCode(article));
        entity.setPolicyTitle(article.getTitle());
        entity.setTaxType(dto.getTaxType() != null ? dto.getTaxType() : "ALL");
        entity.setSizeRange(dto.getSizeRange() != null ? dto.getSizeRange() : "ALL");
        entity.setRegion(dto.getRegion());
        entity.setTagCodes(dto.getTagCodes());
        entity.setSegmentId(dto.getSegmentId());
        entity.setEffectiveFrom(dto.getEffectiveFrom());
        entity.setEffectiveUntil(dto.getEffectiveUntil());
        entity.setAuditMode(dto.getAuditMode() != null ? dto.getAuditMode() : MODE_SAMPLING);
        entity.setAuditStatus(AUDIT_PENDING);
        entity.setStatus(STATUS_DRAFT);
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(operator());
        // 草稿期预计算命中客户快照
        List<Long> matched = matchCustomers(entity);
        entity.setSnapshotCustomerIds(joinIds(matched));
        entity.setExpectedRecipientCount(matched.size());
        entity = taskRepository.save(entity);
        log.info("[政策推送-任务] 创建: id={}, articleId={}, matchedCustomers={}",
                entity.getId(), article.getId(), matched.size());
        return entity;
    }

    /**
     * 重新预计算命中客户快照（草稿期/每日补算, 调度入口）。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @Transactional
    public ScrmPolicyPushTaskEntity refreshSnapshot(Long id) throws ScrmException {
        ScrmPolicyPushTaskEntity task = findTaskOrThrow(id);
        if (!STATUS_DRAFT.equals(task.getStatus()) && !STATUS_REJECTED.equals(task.getStatus())) {
            throw ScrmException.conflict("仅 DRAFT / REJECTED 状态可重算快照: id=" + id
                    + ", status=" + task.getStatus());
        }
        List<Long> matched = matchCustomers(task);
        task.setSnapshotCustomerIds(joinIds(matched));
        task.setExpectedRecipientCount(matched.size());
        task = taskRepository.save(task);
        log.info("[政策推送-快照] 重算: id={}, matchedCustomers={}", id, matched.size());
        return task;
    }

    /**
     * 每日快照补算（调度入口, 默认 03:00）: 全部草稿任务按最新标签/分群重算。
     *
     * @return 补算任务数
     */
    @Transactional
    public int refreshDraftSnapshots() {
        int count = 0;
        for (ScrmPolicyPushTaskEntity task : taskRepository.findByStatus(STATUS_DRAFT)) {
            try {
                List<Long> matched = matchCustomers(task);
                task.setSnapshotCustomerIds(joinIds(matched));
                task.setExpectedRecipientCount(matched.size());
                taskRepository.save(task);
                count++;
            } catch (Exception e) {
                log.warn("[政策推送-快照] 补算失败, 跳过: taskId={}, err={}", task.getId(), e.getMessage());
            }
        }
        log.info("[政策推送-快照] 每日补算完成: tasks={}", count);
        return count;
    }

    // ============================================================
    // 匹配（复用 Segment / Tag）
    // ============================================================

    /**
     * 匹配命中客户（快照计算）。
     * <p>优先级: segmentId 当前成员 &gt; tagCodes 标签客户交集 &gt; 税种标签兜底
     * &gt; 全量客户。任何分支都按税种维度再过滤一次（taxType != ALL 时）;
     * sizeRange 首期仅 ALL/LISTED 生效（不做规模维护, LISTED 视为全量, 见 T08D §7.2）。</p>
     *
     * @param task 推送任务（匹配参数）
     * @return 命中客户 ID 列表（去重保序）
     */
    public List<Long> matchCustomers(ScrmPolicyPushTaskEntity task) {
        Set<Long> candidates = new LinkedHashSet<>();
        if (task.getSegmentId() != null) {
            List<ScrmSegmentMemberEntity> members =
                    segmentMemberRepository.findBySegmentIdAndIsCurrentMemberTrue(task.getSegmentId());
            members.forEach(m -> candidates.add(m.getCustomerId()));
        } else if (task.getTagCodes() != null && !task.getTagCodes().isBlank()) {
            for (String code : task.getTagCodes().split(",")) {
                String tagCode = code.trim();
                if (tagCode.isEmpty()) {
                    continue;
                }
                ScrmTagEntity tag = tagRepository.findByTagCode(tagCode).orElse(null);
                if (tag == null) {
                    continue;
                }
                List<ScrmTagCustomerEntity> links =
                        tagCustomerRepository.findByTagId(tag.getId(),
                                org.springframework.data.domain.PageRequest.of(0, 5000)).getContent();
                links.forEach(l -> candidates.add(l.getCustomerId()));
            }
        } else if (task.getTaxType() != null && !"ALL".equals(task.getTaxType())) {
            String taxTagCode = "TAG_" + task.getTaxType();
            ScrmTagEntity tag = tagRepository.findByTagCode(taxTagCode).orElse(null);
            if (tag != null) {
                tagCustomerRepository.findByTagId(tag.getId(),
                                org.springframework.data.domain.PageRequest.of(0, 5000))
                        .forEach(l -> candidates.add(l.getCustomerId()));
            }
        }
        if (candidates.isEmpty()) {
            // 兜底: 全量客户（首期最小实现, 避免空快照无法验收）
            customerRepository.findAll().forEach(c -> candidates.add(c.getId()));
        }
        // 税种维度再过滤（taxType != ALL 且客户有对应税种标签）
        if (task.getTaxType() != null && !"ALL".equals(task.getTaxType())) {
            String taxTagCode = "TAG_" + task.getTaxType();
            ScrmTagEntity taxTag = tagRepository.findByTagCode(taxTagCode).orElse(null);
            if (taxTag != null) {
                Set<Long> taxTagged = tagCustomerRepository.findByTagId(taxTag.getId(),
                                org.springframework.data.domain.PageRequest.of(0, 5000))
                        .stream().map(ScrmTagCustomerEntity::getCustomerId).collect(Collectors.toSet());
                if (!taxTagged.isEmpty()) {
                    candidates.retainAll(taxTagged);
                }
            }
        }
        return new ArrayList<>(candidates);
    }

    // ============================================================
    // 状态机动作
    // ============================================================

    /**
     * 提交审核（DRAFT/REJECTED → PENDING_AUDIT）。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许 / 政策条目未审核
     */
    @Transactional
    public ScrmPolicyPushTaskEntity submit(Long id) throws ScrmException {
        ScrmPolicyPushTaskEntity task = findTaskOrThrow(id);
        if (!STATUS_DRAFT.equals(task.getStatus()) && !STATUS_REJECTED.equals(task.getStatus())) {
            throw ScrmException.conflict("仅 DRAFT / REJECTED 状态可提交审核: id=" + id
                    + ", status=" + task.getStatus());
        }
        assertPolicyPublishable(articleRepository.findById(task.getPolicyArticleId())
                .orElseThrow(() -> ScrmException.notFound("政策知识条目不存在: id=" + task.getPolicyArticleId())));
        if (task.getSnapshotCustomerIds() == null || task.getSnapshotCustomerIds().isBlank()) {
            throw ScrmException.badRequest("命中客户快照为空, 请先重算快照: id=" + id);
        }
        task.setStatus(STATUS_PENDING_AUDIT);
        task.setAuditStatus(MODE_FULL.equals(task.getAuditMode()) ? AUDIT_PENDING : AUDIT_SAMPLING);
        return taskRepository.save(task);
    }

    /**
     * 审核通过（PENDING_AUDIT → APPROVED）。抽样模式下须人工核验任务标记。
     *
     * @param id      任务 ID
     * @param auditor 审核人
     * @param comment 审核意见（可空）
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @Transactional
    public ScrmPolicyPushTaskEntity approve(Long id, String auditor, String comment) throws ScrmException {
        ScrmPolicyPushTaskEntity task = findTaskOrThrow(id);
        if (!STATUS_PENDING_AUDIT.equals(task.getStatus())) {
            throw ScrmException.conflict("仅 PENDING_AUDIT 状态可通过: id=" + id
                    + ", status=" + task.getStatus());
        }
        task.setAuditStatus(AUDIT_APPROVED);
        task.setAuditedBy(auditor != null ? auditor : operator());
        task.setAuditedAt(LocalDateTime.now());
        task.setStatus(STATUS_APPROVED);
        if (comment != null) {
            task.setNotes(comment);
        }
        return taskRepository.save(task);
    }

    /**
     * 审核驳回（PENDING_AUDIT → REJECTED, 可改后重提）。
     *
     * @param id      任务 ID
     * @param auditor 审核人
     * @param reason  驳回原因
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @Transactional
    public ScrmPolicyPushTaskEntity reject(Long id, String auditor, String reason) throws ScrmException {
        ScrmPolicyPushTaskEntity task = findTaskOrThrow(id);
        if (!STATUS_PENDING_AUDIT.equals(task.getStatus())) {
            throw ScrmException.conflict("仅 PENDING_AUDIT 状态可驳回: id=" + id
                    + ", status=" + task.getStatus());
        }
        task.setAuditStatus(AUDIT_REJECTED);
        task.setAuditedBy(auditor != null ? auditor : operator());
        task.setAuditedAt(LocalDateTime.now());
        task.setStatus(STATUS_REJECTED);
        task.setNotes(reason);
        return taskRepository.save(task);
    }

    /**
     * 撤销任务（未发布前可撤销）。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 已发布不可撤销
     */
    @Transactional
    public ScrmPolicyPushTaskEntity cancel(Long id) throws ScrmException {
        ScrmPolicyPushTaskEntity task = findTaskOrThrow(id);
        if (STATUS_COMPLETED.equals(task.getStatus()) || STATUS_PUBLISHING.equals(task.getStatus())) {
            throw ScrmException.conflict("已发布/发布中的任务不可撤销: id=" + id
                    + ", status=" + task.getStatus());
        }
        task.setStatus(STATUS_CANCELED);
        task.setAuditStatus("CANCELED");
        return taskRepository.save(task);
    }

    /**
     * 发布（APPROVED → PUBLISHING → COMPLETED, 幂等: 已发布直接返回）。
     * <p>发布前强校验: 必须 APPROVED + 政策条目已发布审校（双重人工闸门）;
     * 发布时按快照客户逐个推内部提醒（NotificationCenter, 按客户对应销售/会计）,
     * 受配额约束（同一客户 30 天 ≤ quotaPer30Days 条）; 客户触达文案仅组装为
     * APP 预览内容（不对外发送, 企微凭据到位后开二期开关）。</p>
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 未过审 / 政策条目不可用
     */
    @Transactional
    public ScrmPolicyPushTaskEntity publish(Long id) throws ScrmException {
        ScrmPolicyPushTaskEntity task = findTaskOrThrow(id);
        if (STATUS_COMPLETED.equals(task.getStatus())) {
            return task;
        }
        if (!STATUS_APPROVED.equals(task.getStatus()) && !STATUS_FAILED.equals(task.getStatus())) {
            throw ScrmException.conflict("仅 APPROVED / FAILED 状态可发布: id=" + id
                    + ", status=" + task.getStatus());
        }
        if (!AUDIT_APPROVED.equals(task.getAuditStatus())) {
            throw ScrmException.conflict("二级人工闸门未通过, 禁止发布: id=" + id
                    + ", auditStatus=" + task.getAuditStatus());
        }
        Long policyArticleId = task.getPolicyArticleId();
        ScrmKnowledgeArticleEntity article = articleRepository
                .findById(policyArticleId)
                .orElseThrow(() -> ScrmException.notFound("政策知识条目不存在: id=" + policyArticleId));
        assertPolicyPublishable(article);
        task.setStatus(STATUS_PUBLISHING);
        task.setPublishedAt(LocalDateTime.now());
        taskRepository.save(task);
        int delivered = 0;
        int skipped = 0;
        try {
            for (Long customerId : parseIds(task.getSnapshotCustomerIds())) {
                if (quotaExceeded(customerId)) {
                    skipped++;
                    continue;
                }
                if (pushInternalNotice(task, article, customerId)) {
                    delivered++;
                }
            }
            task.setStatus(STATUS_COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setFailReason(skipped > 0 ? ("配额跳过 " + skipped + " 个客户") : null);
        } catch (Exception e) {
            task.setStatus(STATUS_FAILED);
            task.setFailReason(truncate(e.getMessage(), 500));
            log.warn("[政策推送-发布] 发布失败: taskId={}, err={}", task.getId(), e.getMessage());
        }
        task = taskRepository.save(task);
        log.info("[政策推送-发布] 发布完成: taskId={}, delivered={}, skipped={}",
                task.getId(), delivered, skipped);
        return task;
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    public ScrmPolicyPushTaskEntity getTask(Long id) throws ScrmException {
        return findTaskOrThrow(id);
    }

    /**
     * 分页查询推送任务。
     *
     * @param status  状态过滤（可空）
     * @param keyword 标题关键词（可空）
     * @param pageable 分页参数
     * @return 任务分页结果
     */
    public Page<ScrmPolicyPushTaskEntity> listTasks(String status, String keyword, Pageable pageable) {
        return taskRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("policyTitle")),
                        "%" + keyword.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable);
    }

    /**
     * 抽样核验清单（auditMode=SAMPLING 时取前 N 个命中客户供人工核验）。
     *
     * @param id 任务 ID
     * @return 抽样客户 [{customerId, customerName}]
     * @throws ScrmException 任务不存在
     */
    public List<Map<String, Object>> samplingList(Long id) throws ScrmException {
        ScrmPolicyPushTaskEntity task = findTaskOrThrow(id);
        List<Map<String, Object>> result = new ArrayList<>();
        int limit = Math.max(1, properties.getSamplingAuditCount());
        for (Long customerId : parseIds(task.getSnapshotCustomerIds())) {
            if (result.size() >= limit) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("customerId", customerId);
            item.put("customerName", customerRepository.findById(customerId)
                    .map(ScrmCustomerEntity::getNickname).orElse("客户#" + customerId));
            result.add(item);
        }
        return result;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 政策条目可发布校验（一级闸门: 已发布 + 已审核）。
     *
     * @param article 知识条目
     * @throws ScrmException 条目类型/状态不符
     */
    private void assertPolicyPublishable(ScrmKnowledgeArticleEntity article) throws ScrmException {
        if (article.getArticleType() != null && !ARTICLE_TYPE_POLICY.equals(article.getArticleType())) {
            throw ScrmException.badRequest("仅 POLICY 类型知识条目可建政策推送任务: id="
                    + article.getId() + ", articleType=" + article.getArticleType());
        }
        if (!ARTICLE_STATUS_PUBLISHED.equals(article.getStatus())
                || !ARTICLE_REVIEW_APPROVED.equals(article.getReviewStatus())) {
            throw ScrmException.conflict("政策条目未发布/未审核, 禁止建推送任务: id=" + article.getId()
                    + ", status=" + article.getStatus() + ", reviewStatus=" + article.getReviewStatus());
        }
    }

    /**
     * 单客户内部提醒推送（幂等: 同任务同客户只推一次）。
     *
     * @param task       推送任务
     * @param article    政策条目
     * @param customerId 客户 ID
     * @return 是否推送
     */
    private boolean pushInternalNotice(ScrmPolicyPushTaskEntity task, ScrmKnowledgeArticleEntity article,
                                       Long customerId) {
        String relatedId = task.getId() + ":" + customerId;
        try {
            if (notificationRepository.existsByRelatedTypeAndRelatedIdAndTemplateCode(
                    RELATED_TYPE_POLICY_PUSH, relatedId, properties.getRemindTemplateCode())) {
                return false;
            }
            ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
            dto.setTemplateCode(properties.getRemindTemplateCode());
            dto.setRecipientType("USER");
            dto.setRecipients(List.of(properties.getNotifyRecipient()));
            dto.setSenderId(SENDER_ID);
            dto.setSenderName("政策推送");
            dto.setPriority(1);
            dto.setRelatedType(RELATED_TYPE_POLICY_PUSH);
            dto.setRelatedId(relatedId);
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("policyTitle", task.getPolicyTitle());
            variables.put("policyCode", task.getPolicyCode() != null ? task.getPolicyCode() : "");
            variables.put("taxType", task.getTaxType());
            variables.put("region", task.getRegion() != null ? task.getRegion() : "全国");
            variables.put("customerId", String.valueOf(customerId));
            variables.put("customerName", customerRepository.findById(customerId)
                    .map(ScrmCustomerEntity::getNickname).orElse("客户#" + customerId));
            variables.put("summary", article.getSummary() != null ? article.getSummary() : "");
            variables.put("effectiveFrom", String.valueOf(task.getEffectiveFrom()));
            variables.put("effectiveUntil", String.valueOf(task.getEffectiveUntil()));
            dto.setVariables(variables);
            notificationCenterService.sendNotification(dto);
            return true;
        } catch (Exception e) {
            log.warn("[政策推送-提醒] 单客户内部提醒失败 (不中断发布): taskId={}, customerId={}, err={}",
                    task.getId(), customerId, e.getMessage());
            return false;
        }
    }

    /**
     * 配额校验（同一客户 30 天内至多 quotaPer30Days 条政策推送）。
     *
     * @param customerId 客户 ID
     * @return true 配额已满
     */
    private boolean quotaExceeded(Long customerId) {
        try {
            long count = notificationRepository.findAll((root, query, cb) -> cb.and(
                            cb.equal(root.get("relatedType"), RELATED_TYPE_POLICY_PUSH),
                            cb.like(root.get("relatedId"), "%:" + customerId),
                            cb.greaterThanOrEqualTo(root.get("createTime"),
                                    LocalDateTime.now().minusDays(30))))
                    .size();
            return count >= Math.max(1, properties.getQuotaPer30Days());
        } catch (Exception e) {
            // 配额查询失败时放行, 不因统计问题阻断发布（宁可多推一条也不漏推）
            return false;
        }
    }

    /**
     * 从知识条目提取政策文号（keywords 首个 token 兜底, 无则用 articleCode）。
     *
     * @param article 知识条目
     * @return 政策文号
     */
    private String extractPolicyCode(ScrmKnowledgeArticleEntity article) {
        if (article.getKeywords() != null && !article.getKeywords().isBlank()) {
            String first = article.getKeywords().split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncate(first, 50);
            }
        }
        return article.getArticleCode();
    }

    /**
     * 拼装快照客户 ID 串。
     *
     * @param ids 客户 ID 列表
     * @return 逗号分隔串
     */
    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * 解析快照客户 ID 串。
     *
     * @param raw 逗号分隔串
     * @return 客户 ID 列表
     */
    private List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * 生成任务编号 PP-YYYYMMDD-序号。
     *
     * @return 任务编号
     */
    private String generateTaskNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = taskRepository.count();
        return "PP-" + date + "-" + String.format("%04d", (count % 10000) + 1);
    }

    /**
     * 查询任务, 不存在抛 404。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    private ScrmPolicyPushTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        if (id == null) {
            throw ScrmException.badRequest("任务 ID 不能为空");
        }
        return taskRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("政策推送任务不存在: id=" + id));
    }

    /**
     * 当前操作人。
     *
     * @return 操作人 ID, 缺省 system
     */
    private String operator() {
        String userId = org.hiylo.scrm.config.UserContext.getUserId();
        return userId != null && !userId.isBlank() ? userId : "system";
    }

    /**
     * 截断字符串。
     *
     * @param value 原串
     * @param max   最大长度
     * @return 截断后串
     */
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
