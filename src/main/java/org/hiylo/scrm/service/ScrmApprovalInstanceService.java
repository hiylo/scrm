/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalInstanceService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmApprovalActionDto;
import org.hiylo.scrm.dto.ScrmApprovalSubmitDto;
import org.hiylo.scrm.entity.ScrmApprovalFlowEntity;
import org.hiylo.scrm.entity.ScrmApprovalInstanceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmApprovalInstanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * SCRM 审批实例与流转服务。
 * <p>
 * 承载审批实例子域: 提交审批创建实例并初始化流程、实例查询 (详情/编号/分页/我的待审批/我提交的/
 * 我已审批的)、撤回/催办/加急/取消, 以及节点处理与流转引擎 (同意后处理当前节点、流转到下一节点、
 * 条件路由、自动审批检查、超时处理、完成实例)。同时托管实例共享常量 (实例状态/操作类型/操作人类型)、
 * 实例编号生成、按主键查找实例、审批人列表解析、节点历史追加与耗时计算等辅助方法, 供操作兄弟类
 * 以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmApprovalInstanceService {

    // ==================== 实例状态 (共享) ====================

    /** 实例状态: PENDING 待提交 */
    static final String INSTANCE_PENDING = "PENDING";
    /** 实例状态: APPROVING 审批中 */
    static final String INSTANCE_APPROVING = "APPROVING";
    /** 实例状态: APPROVED 已通过 */
    static final String INSTANCE_APPROVED = "APPROVED";
    /** 实例状态: REJECTED 已驳回 */
    static final String INSTANCE_REJECTED = "REJECTED";
    /** 实例状态: CANCELLED 已取消 */
    static final String INSTANCE_CANCELLED = "CANCELLED";
    /** 实例状态: TRANSFERRED 已转交 */
    static final String INSTANCE_TRANSFERRED = "TRANSFERRED";
    /** 实例状态: TIMEOUT 已超时 */
    static final String INSTANCE_TIMEOUT = "TIMEOUT";
    /** 实例状态: WITHDRAWN 已撤回 */
    static final String INSTANCE_WITHDRAWN = "WITHDRAWN";

    // ==================== 操作类型 (共享) ====================

    /** 操作类型: SUBMIT 提交 */
    static final String ACTION_SUBMIT = "SUBMIT";
    /** 操作类型: APPROVE 同意 */
    static final String ACTION_APPROVE = "APPROVE";
    /** 操作类型: REJECT 驳回 */
    static final String ACTION_REJECT = "REJECT";
    /** 操作类型: TRANSFER 转交 */
    static final String ACTION_TRANSFER = "TRANSFER";
    /** 操作类型: COUNTERSIGN 加签 */
    static final String ACTION_COUNTERSIGN = "COUNTERSIGN";
    /** 操作类型: CC 抄送 */
    static final String ACTION_CC = "CC";
    /** 操作类型: WITHDRAW 撤回 */
    static final String ACTION_WITHDRAW = "WITHDRAW";
    /** 操作类型: RESUBMIT 重新提交 */
    static final String ACTION_RESUBMIT = "RESUBMIT";
    /** 操作类型: TIMEOUT 超时 */
    static final String ACTION_TIMEOUT = "TIMEOUT";
    /** 操作类型: URGE 催办 */
    static final String ACTION_URGE = "URGE";
    /** 操作类型: COMMENT 评论 */
    static final String ACTION_COMMENT = "COMMENT";
    /** 操作类型: AUTO_APPROVE 自动审批 */
    static final String ACTION_AUTO_APPROVE = "AUTO_APPROVE";

    // ==================== 操作人类型 (共享) ====================

    /** 操作人类型: APPLICANT 申请人 */
    static final String OPERATOR_APPLICANT = "APPLICANT";
    /** 操作人类型: APPROVER 审批人 */
    static final String OPERATOR_APPROVER = "APPROVER";
    /** 操作人类型: CC 抄送人 */
    static final String OPERATOR_CC = "CC";
    /** 操作人类型: SYSTEM 系统 */
    static final String OPERATOR_SYSTEM = "SYSTEM";

    /** 默认优先级: 0 表示普通 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 实例编号格式 */
    private static final String INSTANCE_NO_PREFIX = "AP";
    /** 实例编号日期格式: yyyyMMdd */
    private static final DateTimeFormatter INSTANCE_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 审批实例数据访问层 */
    private final ScrmApprovalInstanceRepository instanceRepository;

    /** JSON 映射器 */
    private final ObjectMapper objectMapper;

    /** 审批流程定义服务 (流程查找 / 业务类型与流程常量 / JSON 解析) */
    private final ScrmApprovalFlowService flowService;

    /** 审批操作日志服务 (日志记录) */
    private final ScrmApprovalLogService logService;

    // ============================================================
    // 实例管理
    // ============================================================

    /**
     * 提交审批 (创建实例 → 初始化流程 → 执行起始节点 → 流转到第一个审批节点)。
     * <p>流程须为 ACTIVE 状态, 自动生成实例编号, 递增流程使用次数。</p>
     *
     * @param submitDto 提交参数
     * @return 创建后的实例
     * @throws ScrmException 流程不存在 / 状态非法 / 参数非法
     */
    @Transactional
    public ScrmApprovalInstanceEntity submit(ScrmApprovalSubmitDto submitDto) throws ScrmException {
        if (submitDto == null) {
            throw ScrmException.badRequest("提交参数不能为空");
        }
        if (submitDto.getFlowId() == null) {
            throw ScrmException.badRequest("流程 ID 不能为空");
        }
        if (submitDto.getBusinessType() == null || submitDto.getBusinessType().isBlank()) {
            throw ScrmException.badRequest("业务类型不能为空");
        }
        if (!ScrmApprovalFlowService.VALID_BUSINESS_TYPES.contains(submitDto.getBusinessType())) {
            throw ScrmException.badRequest("业务类型非法: " + submitDto.getBusinessType());
        }
        ScrmApprovalFlowEntity flow = flowService.findFlowOrThrow(submitDto.getFlowId());
        if (!ScrmApprovalFlowService.FLOW_STATUS_ACTIVE.equals(flow.getStatus())) {
            throw ScrmException.conflict("仅 ACTIVE 状态的流程可提交审批: id=" + flow.getId()
                    + ", status=" + flow.getStatus());
        }
        // 校验节点定义完整性
        List<Map<String, Object>> nodes = flowService.parseJsonList(flow.getNodes(), "nodes");
        if (nodes.isEmpty()) {
            throw ScrmException.badRequest("流程节点定义为空: flowId=" + flow.getId());
        }
        boolean urgent = Boolean.TRUE.equals(submitDto.getUrgent());
        if (urgent && !Boolean.TRUE.equals(flow.getAllowUrgent())) {
            throw ScrmException.conflict("当前流程不允许加急: flowId=" + flow.getId());
        }
        ScrmApprovalInstanceEntity instance = new ScrmApprovalInstanceEntity();
        instance.setInstanceNo(generateInstanceNo());
        instance.setFlowId(flow.getId());
        instance.setFlowName(flow.getFlowName());
        instance.setFlowType(flow.getFlowType());
        instance.setBusinessType(submitDto.getBusinessType());
        instance.setBusinessId(submitDto.getBusinessId());
        instance.setBusinessTitle(submitDto.getBusinessTitle());
        instance.setBusinessData(submitDto.getBusinessData());
        instance.setApplicantId(submitDto.getApplicantId());
        instance.setApplicantName(submitDto.getApplicantName());
        instance.setApplicantDept(submitDto.getApplicantDept());
        instance.setApplicantRole(submitDto.getApplicantRole());
        instance.setStatus(INSTANCE_PENDING);
        instance.setPriority(DEFAULT_PRIORITY);
        instance.setIsUrgent(urgent);
        instance.setUrgentReason(urgent ? submitDto.getUrgentReason() : null);
        instance.setStartedAt(LocalDateTime.now());
        instance.setIsOverdue(Boolean.FALSE);
        instance.setAttachmentUrls(submitDto.getAttachmentUrls());
        instance.setCcUsers(submitDto.getCcUsers());
        instance.setVariables(submitDto.getVariables());
        instance.setCreatedBy(submitDto.getCreatedBy() != null ? submitDto.getCreatedBy() : submitDto.getApplicantId());
        instance.setNotifiedAt(LocalDateTime.now());
        instance.setLastActivityAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);
        // 递增流程使用次数
        flowService.incrementUsage(flow.getId());
        // 记录提交日志
        logService.recordLog(instance, null, null, null, ACTION_SUBMIT,
                submitDto.getApplicantId(), submitDto.getApplicantName(), submitDto.getApplicantRole(),
                OPERATOR_APPLICANT, null, submitDto.getBusinessData(), null, null, false);
        // 初始化流程: 执行起始节点并流转到第一个审批节点
        try {
            initializeFlow(instance, flow);
        } catch (ScrmException e) {
            log.warn("提交审批后初始化流程失败: instanceId={}, flowId={}, err={}",
                    instance.getId(), flow.getId(), e.getMessage());
        }
        log.info("提交审批: instanceId={}, instanceNo={}, flowId={}, businessType={}, applicantId={}",
                instance.getId(), instance.getInstanceNo(), flow.getId(),
                submitDto.getBusinessType(), submitDto.getApplicantId());
        return instanceRepository.findById(instance.getId()).orElse(instance);
    }

    /**
     * 查询实例详情。
     *
     * @param id 实例 ID
     * @return 实例实体
     * @throws ScrmException 实例不存在
     */
    @Transactional(readOnly = true)
    public ScrmApprovalInstanceEntity getInstance(Long id) throws ScrmException {
        return findInstanceOrThrow(id);
    }

    /**
     * 按实例编号查询实例。
     *
     * @param instanceNo 实例编号
     * @return 实例实体
     * @throws ScrmException 实例不存在
     */
    @Transactional(readOnly = true)
    public ScrmApprovalInstanceEntity getInstanceByNo(String instanceNo) throws ScrmException {
        if (instanceNo == null || instanceNo.isBlank()) {
            throw ScrmException.badRequest("实例编号不能为空");
        }
        return instanceRepository.findByInstanceNo(instanceNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "审批实例不存在: instanceNo=" + instanceNo));
    }

    /**
     * 分页查询实例, 支持按流程 / 业务类型 / 状态 / 申请人 / 审批人 / 加急 / 时间范围过滤。
     *
     * @param flowId       流程 ID 过滤（可空）
     * @param businessType 业务类型过滤（可空）
     * @param status       状态过滤（可空）
     * @param applicantId  申请人 ID 过滤（可空）
     * @param approverId   审批人 ID 过滤（可空, 模糊匹配当前审批人列表）
     * @param isUrgent     是否加急过滤（可空）
     * @param startTime    开始时间起始 (含, 可空)
     * @param endTime      开始时间截止 (含, 可空)
     * @param pageable     分页参数
     * @return 实例分页结果 (按 startedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmApprovalInstanceEntity> listInstances(Long flowId, String businessType, String status,
                                                            String applicantId, String approverId, Boolean isUrgent,
                                                            LocalDateTime startTime, LocalDateTime endTime,
                                                            Pageable pageable) {
        Specification<ScrmApprovalInstanceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (flowId != null) {
                predicates.add(cb.equal(root.get("flowId"), flowId));
            }
            if (businessType != null && !businessType.isBlank()) {
                predicates.add(cb.equal(root.get("businessType"), businessType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (applicantId != null && !applicantId.isBlank()) {
                predicates.add(cb.equal(root.get("applicantId"), applicantId));
            }
            if (approverId != null && !approverId.isBlank()) {
                predicates.add(cb.like(root.get("currentApproverIds"), "%" + approverId + "%"));
            }
            if (isUrgent != null) {
                predicates.add(cb.equal(root.get("isUrgent"), isUrgent));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("startedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return instanceRepository.findAll(spec, pageable);
    }

    /**
     * 我的待审批 (按审批人 ID 查询当前审批人列表包含该 ID 的待审批实例)。
     *
     * @param approverId 审批人 ID
     * @param pageable   分页参数
     * @return 实例分页结果 (按加急优先、优先级降序、开始时间升序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmApprovalInstanceEntity> getMyPending(String approverId, Pageable pageable) {
        if (approverId == null || approverId.isBlank()) {
            throw ScrmException.badRequest("审批人 ID 不能为空");
        }
        return instanceRepository.findPendingByApprover(approverId, pageable);
    }

    /**
     * 我提交的 (按申请人 ID 分页查询, 可按状态过滤)。
     *
     * @param applicantId 申请人 ID
     * @param status      状态过滤（可空）
     * @param pageable    分页参数
     * @return 实例分页结果 (按 startedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmApprovalInstanceEntity> getMySubmitted(String applicantId, String status, Pageable pageable) {
        if (applicantId == null || applicantId.isBlank()) {
            throw ScrmException.badRequest("申请人 ID 不能为空");
        }
        return instanceRepository.findByApplicantAndStatus(
                 applicantId,
                (status == null || status.isBlank()) ? null : status, pageable);
    }

    /**
     * 我已审批的 (查询审批人参与过的实例)。
     *
     * @param approverId 审批人 ID
     * @param pageable   分页参数
     * @return 实例分页结果 (按 startedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmApprovalInstanceEntity> getMyApproved(String approverId, Pageable pageable) {
        if (approverId == null || approverId.isBlank()) {
            throw ScrmException.badRequest("审批人 ID 不能为空");
        }
        return instanceRepository.findApprovedByApprover(approverId, pageable);
    }

    /**
     * 撤回审批 (仅申请人可撤回, 仅 PENDING / APPROVING 状态可撤回)。
     *
     * @param id          实例 ID
     * @param applicantId 申请人 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 申请人不匹配 / 状态非法
     */
    @Transactional
    public ScrmApprovalInstanceEntity withdraw(Long id, String applicantId) throws ScrmException {
        ScrmApprovalInstanceEntity instance = findInstanceOrThrow(id);
        if (applicantId == null || !applicantId.equals(instance.getApplicantId())) {
            throw ScrmException.forbidden("仅申请人可撤回审批: instanceId=" + id);
        }
        if (!INSTANCE_PENDING.equals(instance.getStatus()) && !INSTANCE_APPROVING.equals(instance.getStatus())) {
            throw ScrmException.conflict(
                    "仅 PENDING / APPROVING 状态可撤回: id=" + id + ", status=" + instance.getStatus());
        }
        String previousStatus = instance.getStatus();
        instance.setStatus(INSTANCE_WITHDRAWN);
        instance.setWithdrawnAt(LocalDateTime.now());
        instance.setCompletedAt(LocalDateTime.now());
        instance.setDurationHours(calcDurationHours(instance.getStartedAt(), instance.getCompletedAt()));
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, instance.getCurrentNodeId(), instance.getCurrentNodeName(),
                instance.getCurrentNodeType(), ACTION_WITHDRAW, applicantId, instance.getApplicantName(),
                instance.getApplicantRole(), OPERATOR_APPLICANT, "撤回审批 (此前状态: " + previousStatus + ")", null,
                instance.getCurrentNodeId(), null, false);
        log.info("撤回审批: instanceId={}, applicantId={}", id, applicantId);
        return instance;
    }

    /**
     * 催办审批 (通知当前审批人)。
     *
     * @param id        实例 ID
     * @param urgerId   催办人 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @Transactional
    public ScrmApprovalInstanceEntity urge(Long id, String urgerId) throws ScrmException {
        ScrmApprovalInstanceEntity instance = findInstanceOrThrow(id);
        if (!INSTANCE_PENDING.equals(instance.getStatus()) && !INSTANCE_APPROVING.equals(instance.getStatus())) {
            throw ScrmException.conflict(
                    "仅 PENDING / APPROVING 状态可催办: id=" + id + ", status=" + instance.getStatus());
        }
        instance.setNotifiedAt(LocalDateTime.now());
        instance.setLastActivityAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, instance.getCurrentNodeId(), instance.getCurrentNodeName(),
                instance.getCurrentNodeType(), ACTION_URGE, urgerId, null, null, OPERATOR_APPLICANT,
                "催办审批", null, null, null, false);
        log.info("催办审批: instanceId={}, urgerId={}", id, urgerId);
        return instance;
    }

    /**
     * 标记加急 (仅 PENDING / APPROVING 状态可标记)。
     *
     * @param id     实例 ID
     * @param reason 加急原因
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 流程不允许加急
     */
    @Transactional
    public ScrmApprovalInstanceEntity markUrgent(Long id, String reason) throws ScrmException {
        ScrmApprovalInstanceEntity instance = findInstanceOrThrow(id);
        ScrmApprovalFlowEntity flow = flowService.findFlowOrThrow(instance.getFlowId());
        if (!Boolean.TRUE.equals(flow.getAllowUrgent())) {
            throw ScrmException.conflict("当前流程不允许加急: flowId=" + flow.getId());
        }
        if (!INSTANCE_PENDING.equals(instance.getStatus()) && !INSTANCE_APPROVING.equals(instance.getStatus())) {
            throw ScrmException.conflict(
                    "仅 PENDING / APPROVING 状态可标记加急: id=" + id + ", status=" + instance.getStatus());
        }
        instance.setIsUrgent(Boolean.TRUE);
        instance.setUrgentReason(reason);
        instance.setLastActivityAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);
        log.info("标记加急: instanceId={}, reason={}", id, reason);
        return instance;
    }

    /**
     * 取消审批 (系统/管理员取消, 区别于申请人撤回)。
     *
     * @param id     实例 ID
     * @param reason 取消原因
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @Transactional
    public ScrmApprovalInstanceEntity cancel(Long id, String reason) throws ScrmException {
        ScrmApprovalInstanceEntity instance = findInstanceOrThrow(id);
        if (INSTANCE_APPROVED.equals(instance.getStatus())
                || INSTANCE_REJECTED.equals(instance.getStatus())
                || INSTANCE_CANCELLED.equals(instance.getStatus())
                || INSTANCE_WITHDRAWN.equals(instance.getStatus())) {
            throw ScrmException.conflict("已完成的实例不可取消: id=" + id + ", status=" + instance.getStatus());
        }
        instance.setStatus(INSTANCE_CANCELLED);
        instance.setRejectedReason(reason);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setDurationHours(calcDurationHours(instance.getStartedAt(), instance.getCompletedAt()));
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, instance.getCurrentNodeId(), instance.getCurrentNodeName(),
                instance.getCurrentNodeType(), ACTION_TIMEOUT, null, null, null, OPERATOR_SYSTEM,
                "取消审批: " + (reason != null ? reason : ""), null, null, null, true);
        log.info("取消审批: instanceId={}, reason={}", id, reason);
        return instance;
    }

    // ============================================================
    // 节点处理与流转
    // ============================================================

    /**
     * 处理当前节点 (审批同意时调用: 记录日志 → 移除当前审批人 → 全部审批完毕则流转下一节点)。
     *
     * @param instance  实例实体
     * @param actionDto 操作参数
     * @return 更新后的实例
     * @throws ScrmException 节点不存在 / 流转失败
     */
    @Transactional
    public ScrmApprovalInstanceEntity processNode(ScrmApprovalInstanceEntity instance,
                                                    ScrmApprovalActionDto actionDto) throws ScrmException {
        ScrmApprovalFlowEntity flow = flowService.findFlowOrThrow(instance.getFlowId());
        String currentNodeId = instance.getCurrentNodeId();
        Map<String, Object> currentNode = currentNodeId == null
                ? Collections.emptyMap() : findNode(flow, currentNodeId);
        String previousNodeId = currentNodeId;
        // 从当前审批人列表中移除操作人
        List<String> approvers = new ArrayList<>(parseApproverIds(instance.getCurrentApproverIds()));
        approvers.remove(actionDto.getOperatorId());
        boolean allApproved = approvers.isEmpty();
        instance.setCurrentApproverIds(joinApproverIds(approvers));
        instance.setLastActivityAt(LocalDateTime.now());
        // 追加节点历史
        appendNodeHistory(instance, currentNodeId, actionDto.getOperatorId(),
                actionDto.getOperatorName(), ACTION_APPROVE, actionDto.getComment());
        instance = instanceRepository.save(instance);
        // 记录审批日志
        logService.recordLog(instance, currentNodeId, flowService.toStr(currentNode.get("nodeName")),
                flowService.toStr(currentNode.get("nodeType")),
                ACTION_APPROVE, actionDto.getOperatorId(), actionDto.getOperatorName(), actionDto.getOperatorRole(),
                OPERATOR_APPROVER, actionDto.getComment(), actionDto.getActionData(),
                previousNodeId, allApproved ? null : currentNodeId, false);
        // 若当前节点所有审批人均已同意, 流转到下一节点
        if (allApproved) {
            instance = moveToNextNode(instance.getId());
        } else {
            // 仍有其他审批人待处理, 确保状态为 APPROVING
            if (!INSTANCE_APPROVING.equals(instance.getStatus())) {
                instance.setStatus(INSTANCE_APPROVING);
                instance = instanceRepository.save(instance);
            }
        }
        log.info("处理审批节点: instanceId={}, nodeId={}, operatorId={}, allApproved={}",
                instance.getId(), currentNodeId, actionDto.getOperatorId(), allApproved);
        return instance;
    }

    /**
     * 流转到下一节点 (根据条件路由找到下一节点并执行)。
     * <p>若当前为 END 节点或无下一节点, 完成实例。</p>
     *
     * @param instanceId 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 流程不存在 / 节点不存在
     */
    @Transactional
    public ScrmApprovalInstanceEntity moveToNextNode(Long instanceId) throws ScrmException {
        ScrmApprovalInstanceEntity instance = findInstanceOrThrow(instanceId);
        ScrmApprovalFlowEntity flow = flowService.findFlowOrThrow(instance.getFlowId());
        String currentNodeId = instance.getCurrentNodeId();
        Map<String, Object> currentNode = currentNodeId == null
                ? Collections.emptyMap() : findNode(flow, currentNodeId);
        String currentNodeType = flowService.toStr(currentNode.get("nodeType"));
        // 若当前节点为 END, 直接完成
        if (ScrmApprovalFlowService.NODE_END.equals(currentNodeType)) {
            return completeInstance(instance);
        }
        // 查找下一节点: 优先按条件路由, 其次按节点顺序
        String nextNodeId = resolveNextNodeId(flow, currentNodeId, instance);
        if (nextNodeId == null || nextNodeId.isEmpty()) {
            // 无下一节点, 完成实例
            return completeInstance(instance);
        }
        Map<String, Object> nextNode = findNode(flow, nextNodeId);
        String nextNodeType = flowService.toStr(nextNode.get("nodeType"));
        String previousNodeId = currentNodeId;
        // 进入下一节点
        instance.setCurrentNodeId(nextNodeId);
        instance.setCurrentNodeName(flowService.toStr(nextNode.get("nodeName")));
        instance.setCurrentNodeType(nextNodeType);
        // 处理不同节点类型
        switch (nextNodeType) {
            case ScrmApprovalFlowService.NODE_END:
                // 直接到结束节点
                instance.setCurrentApproverIds(null);
                instance.setCurrentTimeoutAt(null);
                instance.setStatus(INSTANCE_APPROVING);
                instance = instanceRepository.save(instance);
                logService.recordLog(instance, nextNodeId, flowService.toStr(nextNode.get("nodeName")),
                        nextNodeType, ACTION_APPROVE, null, null, null, OPERATOR_SYSTEM,
                        "流转到结束节点", null, previousNodeId, nextNodeId, true);
                instance = moveToNextNode(instance.getId());
                break;
            case ScrmApprovalFlowService.NODE_CC:
                // 抄送节点: 通知抄送人后继续流转
                instance.setCurrentApproverIds(flowService.toStr(nextNode.get("ccUserIds")));
                instance = instanceRepository.save(instance);
                logService.recordLog(instance, nextNodeId, flowService.toStr(nextNode.get("nodeName")),
                        nextNodeType, ACTION_CC, null, null, null, OPERATOR_SYSTEM,
                        "抄送节点处理", null, previousNodeId, nextNodeId, true);
                instance = moveToNextNode(instance.getId());
                break;
            case ScrmApprovalFlowService.NODE_CONDITION:
                // 条件节点: 评估条件后选择路由
                instance.setCurrentApproverIds(null);
                instance.setStatus(INSTANCE_APPROVING);
                instance = instanceRepository.save(instance);
                logService.recordLog(instance, nextNodeId, flowService.toStr(nextNode.get("nodeName")),
                        nextNodeType, ACTION_APPROVE, null, null, null, OPERATOR_SYSTEM,
                        "条件节点评估", null, previousNodeId, nextNodeId, true);
                instance = moveToNextNode(instance.getId());
                break;
            case ScrmApprovalFlowService.NODE_START:
                // 起始节点: 直接继续流转
                instance.setCurrentApproverIds(null);
                instance.setStatus(INSTANCE_APPROVING);
                instance = instanceRepository.save(instance);
                logService.recordLog(instance, nextNodeId, flowService.toStr(nextNode.get("nodeName")),
                        nextNodeType, ACTION_APPROVE, null, null, null, OPERATOR_SYSTEM,
                        "起始节点处理", null, previousNodeId, nextNodeId, true);
                instance = moveToNextNode(instance.getId());
                break;
            case ScrmApprovalFlowService.NODE_APPROVE:
            default:
                // 审批节点: 设置当前审批人, 检查自动审批
                String approverIds = flowService.toStr(nextNode.get("approverIds"));
                // 审批人缺失时回退到流程备选审批人
                if (approverIds.isEmpty() && flow.getApproverFallback() != null && !flow.getApproverFallback().isBlank()) {
                    approverIds = flow.getApproverFallback();
                }
                instance.setCurrentApproverIds(approverIds);
                // 设置超时时间
                Object timeoutHoursObj = nextNode.get("timeoutHours");
                if (timeoutHoursObj instanceof Number) {
                    int timeoutHours = ((Number) timeoutHoursObj).intValue();
                    if (timeoutHours > 0) {
                        instance.setCurrentTimeoutAt(LocalDateTime.now().plusHours(timeoutHours));
                    }
                }
                instance.setStatus(INSTANCE_APPROVING);
                instance = instanceRepository.save(instance);
                logService.recordLog(instance, nextNodeId, flowService.toStr(nextNode.get("nodeName")),
                        nextNodeType, ACTION_APPROVE, null, null, null, OPERATOR_SYSTEM,
                        "流转到审批节点", null, previousNodeId, nextNodeId, true);
                // 检查自动审批
                if (checkAutoApprove(nextNode, instance)) {
                    log.info("节点自动审批触发: instanceId={}, nodeId={}", instanceId, nextNodeId);
                    instance = completeInstance(instance);
                }
                break;
        }
        log.info("流转到下一节点: instanceId={}, from={}, to={}", instanceId, previousNodeId, nextNodeId);
        return instance;
    }

    /**
     * 检查自动审批条件 (节点 autoApprove=true 时触发自动通过)。
     *
     * @param node     节点 Map
     * @param instance 实例实体
     * @return 是否触发自动审批
     */
    @Transactional(readOnly = true)
    public boolean checkAutoApprove(Map<String, Object> node, ScrmApprovalInstanceEntity instance) {
        if (node == null || node.isEmpty()) {
            return false;
        }
        Object autoApprove = node.get("autoApprove");
        if (autoApprove instanceof Boolean && (Boolean) autoApprove) {
            log.info("节点配置自动审批: instanceId={}, nodeId={}", instance.getId(), node.get("nodeId"));
            return true;
        }
        if (autoApprove instanceof String && "true".equalsIgnoreCase((String) autoApprove)) {
            log.info("节点配置自动审批: instanceId={}, nodeId={}", instance.getId(), node.get("nodeId"));
            return true;
        }
        return false;
    }

    /**
     * 处理超时 (将实例标记为 TIMEOUT, 并记录日志)。
     *
     * @param instanceId 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @Transactional
    public ScrmApprovalInstanceEntity handleTimeout(Long instanceId) throws ScrmException {
        ScrmApprovalInstanceEntity instance = findInstanceOrThrow(instanceId);
        if (!INSTANCE_PENDING.equals(instance.getStatus()) && !INSTANCE_APPROVING.equals(instance.getStatus())) {
            throw ScrmException.conflict(
                    "仅 PENDING / APPROVING 状态可处理超时: id=" + instanceId + ", status=" + instance.getStatus());
        }
        instance.setStatus(INSTANCE_TIMEOUT);
        instance.setIsOverdue(Boolean.TRUE);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setDurationHours(calcDurationHours(instance.getStartedAt(), instance.getCompletedAt()));
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, instance.getCurrentNodeId(), instance.getCurrentNodeName(),
                instance.getCurrentNodeType(), ACTION_TIMEOUT, null, null, null, OPERATOR_SYSTEM,
                "审批超时", null, instance.getCurrentNodeId(), null, true);
        log.info("处理审批超时: instanceId={}", instanceId);
        return instance;
    }

    /**
     * 生成实例编号 (格式: AP + 年月日 + 6 位随机序号)。
     *
     * @return 实例编号
     */
    public String generateInstanceNo() {
        String datePart = LocalDate.now().format(INSTANCE_NO_DATE_FORMAT);
        int random = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return INSTANCE_NO_PREFIX + datePart + String.format("%06d", random);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 初始化流程: 执行起始节点并流转到第一个审批节点。
     *
     * @param instance 实例实体
     * @param flow     流程实体
     * @throws ScrmException 节点不存在 / 流转失败
     */
    private void initializeFlow(
            ScrmApprovalInstanceEntity instance, ScrmApprovalFlowEntity flow) throws ScrmException {
        List<Map<String, Object>> nodes = flowService.parseJsonList(flow.getNodes(), "nodes");
        if (nodes.isEmpty()) {
            return;
        }
        // 确定起始节点
        String startNodeId = flow.getStartNode();
        if (startNodeId == null || startNodeId.isBlank()) {
            startNodeId = nodes.stream()
                    .filter(n -> ScrmApprovalFlowService.NODE_START.equals(flowService.toStr(n.get("nodeType"))))
                    .map(n -> flowService.toStr(n.get("nodeId")))
                    .findFirst().orElse(null);
        }
        if (startNodeId == null) {
            // 无起始节点, 默认取第一个
            startNodeId = flowService.toStr(nodes.get(0).get("nodeId"));
        }
        // 设置当前节点并流转
        instance.setCurrentNodeId(startNodeId);
        Map<String, Object> startNode = findNode(flow, startNodeId);
        instance.setCurrentNodeName(flowService.toStr(startNode.get("nodeName")));
        instance.setCurrentNodeType(flowService.toStr(startNode.get("nodeType")));
        instance.setStatus(INSTANCE_APPROVING);
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, startNodeId, flowService.toStr(startNode.get("nodeName")),
                flowService.toStr(startNode.get("nodeType")),
                ACTION_APPROVE, null, null, null, OPERATOR_SYSTEM,
                "初始化流程, 进入起始节点", null, null, startNodeId, true);
        // 从起始节点开始流转
        moveToNextNode(instance.getId());
    }

    /**
     * 解析条件路由, 确定下一节点 ID。
     * <p>优先按 conditionRules 中 currentNodeId 对应的路由表评估条件;
     * 若无匹配路由, 取节点定义中的 order 顺序的下一节点。</p>
     *
     * @param flow          流程实体
     * @param currentNodeId 当前节点 ID
     * @param instance      实例实体
     * @return 下一节点 ID, 无则 null
     * @throws ScrmException JSON 解析失败
     */
    private String resolveNextNodeId(ScrmApprovalFlowEntity flow, String currentNodeId,
                                      ScrmApprovalInstanceEntity instance) throws ScrmException {
        if (currentNodeId == null || currentNodeId.isEmpty()) {
            return null;
        }
        // 1. 条件路由规则
        List<Map<String, Object>> rules = flowService.parseJsonList(flow.getConditionRules(), "conditionRules");
        for (Map<String, Object> rule : rules) {
            String ruleNodeId = flowService.toStr(rule.get("nodeId"));
            if (currentNodeId.equals(ruleNodeId)) {
                Object routesObj = rule.get("routes");
                if (routesObj instanceof List) {
                    for (Object r : (List<?>) routesObj) {
                        if (r instanceof Map) {
                            Map<?, ?> route = (Map<?, ?>) r;
                            String toNode = flowService.toStr(route.get("toNode"));
                            String condition = flowService.toStr(route.get("condition"));
                            // 简单条件评估: 空条件视为默认路由, "true" 视为满足
                            if (toNode.isEmpty()) {
                                continue;
                            }
                            if (condition.isEmpty() || "true".equalsIgnoreCase(condition)
                                    || evaluateCondition(condition, instance)) {
                                return toNode;
                            }
                        }
                    }
                }
            }
        }
        // 2. 按 nodes 顺序的下一节点
        List<Map<String, Object>> nodes = flowService.parseJsonList(flow.getNodes(), "nodes");
        int currentIndex = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if (currentNodeId.equals(flowService.toStr(nodes.get(i).get("nodeId")))) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex >= 0 && currentIndex < nodes.size() - 1) {
            return flowService.toStr(nodes.get(currentIndex + 1).get("nodeId"));
        }
        // 3. 检查是否为结束节点
        if (flow.getEndNodes() != null && !flow.getEndNodes().isBlank()) {
            for (String endNode : flow.getEndNodes().split(",")) {
                if (currentNodeId.equals(endNode.trim())) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 简单条件评估 (基于实例变量, 支持 key=value 格式)。
     *
     * @param condition 条件字符串
     * @param instance  实例实体
     * @return 是否满足
     */
    private boolean evaluateCondition(String condition, ScrmApprovalInstanceEntity instance) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        // 简单实现: 支持 key=value 形式, 从实例变量 JSON 中查找
        if (condition.contains("=")) {
            String[] parts = condition.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                Map<String, Object> variables = flowService.parseJsonMap(instance.getVariables(), "variables");
                Object actual = variables.get(key);
                return actual != null && value.equals(String.valueOf(actual));
            }
        }
        // 默认满足
        return true;
    }

    /**
     * 完成实例 (RUNNING / APPROVING → APPROVED)。
     *
     * @param instance 实例实体
     * @return 更新后的实例
     * @throws ScrmException 状态非法
     */
    private ScrmApprovalInstanceEntity completeInstance(ScrmApprovalInstanceEntity instance) throws ScrmException {
        if (INSTANCE_APPROVED.equals(instance.getStatus())
                || INSTANCE_REJECTED.equals(instance.getStatus())
                || INSTANCE_CANCELLED.equals(instance.getStatus())) {
            return instance;
        }
        instance.setStatus(INSTANCE_APPROVED);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setApprovedAt(LocalDateTime.now());
        instance.setDurationHours(calcDurationHours(instance.getStartedAt(), instance.getCompletedAt()));
        instance.setCurrentApproverIds(null);
        instance.setCurrentTimeoutAt(null);
        instance.setLastActivityAt(LocalDateTime.now());
        instance = instanceRepository.save(instance);
        logService.recordLog(instance, instance.getCurrentNodeId(), instance.getCurrentNodeName(),
                instance.getCurrentNodeType(),
                ACTION_AUTO_APPROVE, null, null, null, OPERATOR_SYSTEM,
                "审批流程已完成", null, instance.getCurrentNodeId(), null, true);
        log.info("完成审批实例: id={}, status=APPROVED, durationHours={}",
                instance.getId(), instance.getDurationHours());
        return instance;
    }

    /**
     * 在流程节点图中查找指定节点。
     *
     * @param flow   流程实体
     * @param nodeId 节点 ID
     * @return 节点 Map
     * @throws ScrmException 节点不存在
     */
    private Map<String, Object> findNode(ScrmApprovalFlowEntity flow, String nodeId) throws ScrmException {
        if (nodeId == null || nodeId.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> nodes = flowService.parseJsonList(flow.getNodes(), "nodes");
        return nodes.stream()
                .filter(n -> nodeId.equals(flowService.toStr(n.get("nodeId"))))
                .findFirst()
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "节点不存在: nodeId=" + nodeId));
    }

    /**
     * 按主键查询实例, 不存在则抛异常。
     *
     * @param id 实例 ID
     * @return 实例实体
     * @throws ScrmException 实例不存在
     */
    ScrmApprovalInstanceEntity findInstanceOrThrow(Long id) throws ScrmException {
        ScrmApprovalInstanceEntity entity = instanceRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "审批实例不存在: id=" + id));
        return entity;
    }

    /**
     * 追加节点历史到实例 nodeHistory JSON 数组。
     *
     * @param instance    实例实体
     * @param nodeId      节点 ID
     * @param approverId  审批人 ID
     * @param approverName 审批人名称
     * @param action      操作类型
     * @param comment     审批意见
     */
    @SuppressWarnings("unchecked")
    void appendNodeHistory(ScrmApprovalInstanceEntity instance, String nodeId,
                            String approverId, String approverName, String action, String comment) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (instance.getNodeHistory() != null && !instance.getNodeHistory().isBlank()) {
            try {
                history = objectMapper.readValue(instance.getNodeHistory(),
                        new TypeReference<List<Map<String, Object>>>() {
                        });
            } catch (Exception e) {
                log.warn("节点历史 JSON 解析失败, 重建: instanceId={}, err={}", instance.getId(), e.getMessage());
            }
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("nodeId", nodeId);
        entry.put("nodeName", instance.getCurrentNodeName());
        entry.put("approverId", approverId);
        entry.put("approverName", approverName);
        entry.put("action", action);
        entry.put("comment", comment);
        entry.put("timestamp", LocalDateTime.now().toString());
        history.add(entry);
        instance.setNodeHistory(toJson(history));
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串, 失败返回 null
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("对象序列化为 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析审批人 ID 列表 (逗号分隔字符串)。
     *
     * @param approverIds 审批人 ID 字符串
     * @return 审批人 ID 列表
     */
    List<String> parseApproverIds(String approverIds) {
        if (approverIds == null || approverIds.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(approverIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 拼接审批人 ID 列表为逗号分隔字符串。
     *
     * @param approvers 审批人 ID 列表
     * @return 逗号分隔字符串
     */
    String joinApproverIds(List<String> approvers) {
        if (approvers == null || approvers.isEmpty()) {
            return null;
        }
        return String.join(",", approvers);
    }

    /**
     * 计算实例审批耗时 (小时)。
     *
     * @param startedAt   开始时间
     * @param completedAt 完成时间
     * @return 耗时小时
     */
    int calcDurationHours(LocalDateTime startedAt, LocalDateTime completedAt) {
        if (startedAt == null || completedAt == null) {
            return 0;
        }
        return (int) ChronoUnit.HOURS.between(startedAt, completedAt);
    }
}