/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowInstanceService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmWorkflowTriggerDto;
import org.hiylo.scrm.entity.ScrmWorkflowEntity;
import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.entity.ScrmWorkflowNodeLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmWorkflowInstanceRepository;
import org.hiylo.scrm.repository.ScrmWorkflowNodeLogRepository;
import org.hiylo.scrm.repository.ScrmWorkflowRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 工作流实例执行服务。
 * <p>
 * 承载执行实例子域: 触发工作流创建实例并从入口流转、实例分页查询与状态操作 (取消 / 暂停 /
 * 恢复 / 重试)、活跃实例与客户实例查询、节点日志与时间线查询、延迟到期实例的抢占恢复。
 * 同时托管实例共享状态常量 (RUNNING / PAUSED / COMPLETED / FAILED / CANCELLED / WAITING),
 * 供节点 / 统计兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmWorkflowInstanceService {

    /** 实例状态: RUNNING 运行中 */
    static final String INSTANCE_RUNNING = "RUNNING";
    /** 实例状态: PAUSED 已暂停 */
    static final String INSTANCE_PAUSED = "PAUSED";
    /** 实例状态: COMPLETED 已完成 */
    static final String INSTANCE_COMPLETED = "COMPLETED";
    /** 实例状态: FAILED 已失败 */
    static final String INSTANCE_FAILED = "FAILED";
    /** 实例状态: CANCELLED 已取消 */
    static final String INSTANCE_CANCELLED = "CANCELLED";
    /** 实例状态: WAITING 等待中 */
    static final String INSTANCE_WAITING = "WAITING";

    /** 工作流数据访问层 (触发时刷新工作流执行统计) */
    private final ScrmWorkflowRepository workflowRepository;

    /** 工作流实例数据访问层 */
    private final ScrmWorkflowInstanceRepository instanceRepository;

    /** 节点日志数据访问层 (日志查询) */
    private final ScrmWorkflowNodeLogRepository nodeLogRepository;

    /** 工作流定义与版本子域服务 (共享常量与按主键查找) */
    private final ScrmWorkflowDefinitionService definitionService;

    /** 工作流节点处理子域服务 (节点执行与流转) */
    private final ScrmWorkflowNodeService nodeService;

    // ============================================================
    // 执行实例
    // ============================================================

    /**
     * 触发工作流 (创建实例 → 执行入口节点 → 按节点流转)。
     * <p>工作流须为 ACTIVE 状态, 且活跃实例数未超 maxConcurrentInstances 上限。</p>
     * <p>
     * 入口节点执行失败时异常向外抛出: 节点级失败已在 {@code executeNode} 内落库为 FAILED,
     * 而此处不能吞掉异常 —— 嵌套事务内的运行时异常已将当前事务标记为 rollback-only,
     * 捕获后继续提交会抛 {@code UnexpectedRollbackException}, 实例与节点日志将全部丢失。
     * </p>
     *
     * @param triggerDto 触发参数
     * @return 创建后的实例
     * @throws ScrmException 工作流不存在 / 状态非法 / 超过并发上限 / 节点执行失败
     */
    @Transactional
    public ScrmWorkflowInstanceEntity triggerWorkflow(ScrmWorkflowTriggerDto triggerDto) throws ScrmException {
        if (triggerDto == null) {
            throw ScrmException.badRequest("触发参数不能为空");
        }
        if (triggerDto.getWorkflowId() == null) {
            throw ScrmException.badRequest("工作流 ID 不能为空");
        }
        if (triggerDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        ScrmWorkflowEntity workflow = definitionService.findWorkflowOrThrow(triggerDto.getWorkflowId());
        if (!ScrmWorkflowDefinitionService.STATUS_ACTIVE.equals(workflow.getStatus())) {
            throw ScrmException.conflict("仅 ACTIVE 状态的工作流可触发: id=" + workflow.getId()
                    + ", status=" + workflow.getStatus());
        }
        long activeCount = instanceRepository.countActiveByWorkflowId(workflow.getId());
        int maxConcurrent = workflow.getMaxConcurrentInstances() != null
                ? workflow.getMaxConcurrentInstances() : ScrmWorkflowDefinitionService.DEFAULT_MAX_CONCURRENT;
        if (activeCount >= maxConcurrent) {
            throw ScrmException.conflict("工作流活跃实例数已达上限: " + maxConcurrent);
        }
        // 确定入口节点
        String entryNode = workflow.getEntryNode();
        if (entryNode == null || entryNode.isBlank()) {
            List<Map<String, Object>> nodes = definitionService.parseJsonList(workflow.getNodes(), "nodes");
            entryNode = nodes.stream()
                    .filter(n -> ScrmWorkflowDefinitionService.NODE_START.equals(definitionService.toStr(n.get("type"))))
                    .map(n -> definitionService.toStr(n.get("id")))
                    .findFirst().orElse(null);
        }
        ScrmWorkflowInstanceEntity instance = new ScrmWorkflowInstanceEntity();
        instance.setWorkflowId(workflow.getId());
        instance.setWorkflowName(workflow.getWorkflowName());
        instance.setCustomerId(triggerDto.getCustomerId());
        instance.setCustomerName(triggerDto.getCustomerName());
        instance.setTriggerType(workflow.getTriggerType());
        instance.setTriggerEvent(triggerDto.getTriggerEvent());
        instance.setTriggerData(triggerDto.getTriggerData());
        instance.setStatus(INSTANCE_RUNNING);
        instance.setStartedAt(LocalDateTime.now());
        instance.setRetryCount(0);
        instance.setPriority(workflow.getPriority() != null ? workflow.getPriority()
                : ScrmWorkflowDefinitionService.DEFAULT_PRIORITY);
        instance.setVariables(triggerDto.getTriggerData());
        instance = instanceRepository.save(instance);
        // 更新工作流执行统计
        workflow.setExecutionCount((workflow.getExecutionCount() != null ? workflow.getExecutionCount() : 0) + 1);
        workflow.setActiveInstanceCount((int) (activeCount + 1));
        workflow.setLastTriggeredAt(LocalDateTime.now());
        workflowRepository.save(workflow);
        log.info("触发工作流: workflowId={}, instanceId={}, customerId={}, entryNode={}",
                workflow.getId(), instance.getId(), triggerDto.getCustomerId(), entryNode);
        // 从入口节点开始同步流转
        if (entryNode != null && !entryNode.isBlank()) {
            nodeService.executeNode(instance.getId(), entryNode);
        }
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
    public ScrmWorkflowInstanceEntity getInstance(Long id) throws ScrmException {
        return nodeService.findInstanceOrThrow(id);
    }

    /**
     * 分页查询实例, 支持按工作流 / 客户 / 状态 / 时间范围过滤。
     *
     * @param workflowId 工作流 ID 过滤（可空）
     * @param customerId 客户 ID 过滤（可空）
     * @param status     状态过滤（可空）
     * @param startTime  开始时间起始 (含, 可空)
     * @param endTime    开始时间截止 (含, 可空)
     * @param pageable   分页参数
     * @return 实例分页结果 (按 startedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmWorkflowInstanceEntity> listInstances(Long workflowId, Long customerId, String status,
                                                           LocalDateTime startTime, LocalDateTime endTime,
                                                           Pageable pageable) {
        Specification<ScrmWorkflowInstanceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (workflowId != null) {
                predicates.add(cb.equal(root.get("workflowId"), workflowId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
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
     * 取消实例 (RUNNING / PAUSED / WAITING → CANCELLED)。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @Transactional
    public ScrmWorkflowInstanceEntity cancelInstance(Long id) throws ScrmException {
        ScrmWorkflowInstanceEntity instance = nodeService.findInstanceOrThrow(id);
        if (INSTANCE_COMPLETED.equals(instance.getStatus())
                || INSTANCE_CANCELLED.equals(instance.getStatus())
                || INSTANCE_FAILED.equals(instance.getStatus())) {
            throw ScrmException.conflict("已完成/已取消/已失败的实例不可取消: id=" + id + ", status=" + instance.getStatus());
        }
        instance.setStatus(INSTANCE_CANCELLED);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setDurationMs(nodeService.calcDurationMs(instance.getStartedAt(), instance.getCompletedAt()));
        instance = instanceRepository.save(instance);
        nodeService.decrementActiveInstanceCount(instance.getWorkflowId());
        log.info("取消工作流实例: id={}, workflowId={}", id, instance.getWorkflowId());
        return instance;
    }

    /**
     * 暂停实例 (RUNNING / WAITING → PAUSED)。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @Transactional
    public ScrmWorkflowInstanceEntity pauseInstance(Long id) throws ScrmException {
        ScrmWorkflowInstanceEntity instance = nodeService.findInstanceOrThrow(id);
        if (!INSTANCE_RUNNING.equals(instance.getStatus()) && !INSTANCE_WAITING.equals(instance.getStatus())) {
            throw ScrmException.conflict("仅 RUNNING / WAITING 状态可暂停: id=" + id
                    + ", status=" + instance.getStatus());
        }
        instance.setStatus(INSTANCE_PAUSED);
        instance = instanceRepository.save(instance);
        log.info("暂停工作流实例: id={}", id);
        return instance;
    }

    /**
     * 恢复实例 (PAUSED → RUNNING, 并从当前节点继续流转)。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 节点执行失败
     */
    @Transactional
    public ScrmWorkflowInstanceEntity resumeInstance(Long id) throws ScrmException {
        ScrmWorkflowInstanceEntity instance = nodeService.findInstanceOrThrow(id);
        if (!INSTANCE_PAUSED.equals(instance.getStatus())) {
            throw ScrmException.conflict("仅 PAUSED 状态可恢复: id=" + id + ", status=" + instance.getStatus());
        }
        instance.setStatus(INSTANCE_RUNNING);
        instance = instanceRepository.save(instance);
        log.info("恢复工作流实例: id={}", id);
        // 从当前节点继续流转 (节点失败异常向外传播, 避免事务 rollback-only 下吞异常)
        if (instance.getCurrentNodeId() != null && !instance.getCurrentNodeId().isBlank()) {
            nodeService.executeNode(instance.getId(), instance.getCurrentNodeId());
        }
        return instanceRepository.findById(instance.getId()).orElse(instance);
    }

    /**
     * 重试失败实例 (FAILED → RUNNING, retryCount 递增, 从当前节点重新执行)。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 节点执行失败
     */
    @Transactional
    public ScrmWorkflowInstanceEntity retryInstance(Long id) throws ScrmException {
        ScrmWorkflowInstanceEntity instance = nodeService.findInstanceOrThrow(id);
        if (!INSTANCE_FAILED.equals(instance.getStatus())) {
            throw ScrmException.conflict("仅 FAILED 状态可重试: id=" + id + ", status=" + instance.getStatus());
        }
        instance.setStatus(INSTANCE_RUNNING);
        instance.setRetryCount((instance.getRetryCount() != null ? instance.getRetryCount() : 0) + 1);
        instance.setErrorMessage(null);
        instance = instanceRepository.save(instance);
        log.info("重试工作流实例: id={}, retryCount={}", id, instance.getRetryCount());
        if (instance.getCurrentNodeId() != null && !instance.getCurrentNodeId().isBlank()) {
            nodeService.executeNode(instance.getId(), instance.getCurrentNodeId());
        }
        return instanceRepository.findById(instance.getId()).orElse(instance);
    }

    /**
     * 获取工作流的活跃实例 (RUNNING / WAITING / PAUSED)。
     *
     * @param workflowId 工作流 ID
     * @return 活跃实例列表
     * @throws ScrmException 工作流不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkflowInstanceEntity> getActiveInstances(Long workflowId) throws ScrmException {
        definitionService.findWorkflowOrThrow(workflowId);
        return instanceRepository.findActiveByWorkflowId(workflowId);
    }

    /**
     * 获取客户的工作流实例。
     *
     * @param customerId 客户 ID
     * @return 实例列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkflowInstanceEntity> getCustomerInstances(Long customerId) {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        return instanceRepository.findByCustomerId(customerId);
    }

    /**
     * 恢复延迟到期的工作流实例 (由 {@code WorkflowDelayResumeScheduler} 调用)。
     * <p>
     * 多实例部署并发安全: 先以 {@code status = WAITING AND version = 期望版本} 的条件更新抢占实例,
     * 只有把状态从 WAITING 改成 RUNNING 的那个节点继续执行流转, 抢占失败 (返回 0 行) 说明已被
     * 其他节点恢复, 直接返回 null 不重复执行。
     * </p>
     *
     * @param instanceId      实例 ID
     * @param expectedVersion 扫描时读到的乐观锁版本号
     * @return 恢复后的实例; 未抢占到 (已被其他节点处理) 时返回 null
     * @throws ScrmException 实例不存在 / 流转执行失败
     */
    @Transactional
    public ScrmWorkflowInstanceEntity resumeDelayedInstance(Long instanceId,
                                                            Long expectedVersion) throws ScrmException {
        int claimed = instanceRepository.claimWaitingInstance(instanceId, expectedVersion, LocalDateTime.now());
        if (claimed == 0) {
            log.info("延迟实例已被其他节点恢复, 跳过: instanceId={}, expectedVersion={}",
                    instanceId, expectedVersion);
            return null;
        }
        ScrmWorkflowInstanceEntity instance = nodeService.findInstanceOrThrow(instanceId);
        log.info("恢复延迟到期的工作流实例: instanceId={}, currentNodeId={}",
                instanceId, instance.getCurrentNodeId());
        nodeService.processNextNode(instanceId);
        return instanceRepository.findById(instanceId).orElse(instance);
    }

    // ============================================================
    // 节点日志
    // ============================================================

    /**
     * 查询实例的节点执行日志 (按执行顺序升序)。
     *
     * @param instanceId 实例 ID
     * @return 节点日志列表
     * @throws ScrmException 实例不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkflowNodeLogEntity> getNodeLogs(Long instanceId) throws ScrmException {
        nodeService.findInstanceOrThrow(instanceId);
        return nodeLogRepository.findByInstanceIdOrderBySequence(
                 instanceId);
    }

    /**
     * 查询节点日志详情。
     *
     * @param id 节点日志 ID
     * @return 节点日志实体
     * @throws ScrmException 节点日志不存在
     */
    @Transactional(readOnly = true)
    public ScrmWorkflowNodeLogEntity getNodeLog(Long id) throws ScrmException {
        ScrmWorkflowNodeLogEntity entity = nodeLogRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "节点日志不存在: id=" + id));
        return entity;
    }

    /**
     * 获取实例执行时间线 (节点日志按执行顺序, 含节点与状态信息)。
     *
     * @param instanceId 实例 ID
     * @return 时间线 Map {instanceId, status, timeline:[{...节点日志}]}
     * @throws ScrmException 实例不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getInstanceTimeline(Long instanceId) throws ScrmException {
        ScrmWorkflowInstanceEntity instance = nodeService.findInstanceOrThrow(instanceId);
        List<ScrmWorkflowNodeLogEntity> logs = nodeLogRepository
                .findByInstanceIdOrderBySequence(instanceId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (ScrmWorkflowNodeLogEntity log : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("logId", log.getId());
            m.put("nodeId", log.getNodeId());
            m.put("nodeName", log.getNodeName());
            m.put("nodeType", log.getNodeType());
            m.put("actionType", log.getActionType());
            m.put("status", log.getStatus());
            m.put("conditionResult", log.getConditionResult());
            m.put("startedAt", log.getStartedAt());
            m.put("completedAt", log.getCompletedAt());
            m.put("durationMs", log.getDurationMs());
            m.put("sequence", log.getSequence());
            m.put("errorMessage", log.getErrorMessage());
            timeline.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instanceId);
        result.put("status", instance.getStatus());
        result.put("startedAt", instance.getStartedAt());
        result.put("completedAt", instance.getCompletedAt());
        result.put("durationMs", instance.getDurationMs());
        result.put("timeline", timeline);
        return result;
    }

    /**
     * 分页查询工作流的失败节点列表。
     *
     * @param workflowId 工作流 ID
     * @param pageable   分页参数
     * @return 失败节点日志分页结果
     * @throws ScrmException 工作流不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmWorkflowNodeLogEntity> getFailedNodes(Long workflowId,
            Pageable pageable) throws ScrmException {
        definitionService.findWorkflowOrThrow(workflowId);
        return nodeLogRepository.findFailedByWorkflowId(workflowId, pageable);
    }
}
