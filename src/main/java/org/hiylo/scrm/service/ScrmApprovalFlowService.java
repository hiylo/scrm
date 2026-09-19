/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApprovalFlowService.java
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

import org.hiylo.scrm.dto.ScrmApprovalFlowDto;
import org.hiylo.scrm.entity.ScrmApprovalFlowEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmApprovalFlowRepository;
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
 * SCRM 审批流程定义服务。
 * <p>
 * 承载审批流定义子域: 流程增删改查与生命周期 (激活 / 停用 / 默认 / 复制 / 校验 / 按业务类型查找
 * / 使用次数递增)。同时托管流程共享常量 (流程 / 节点 / 审批人类型)、JSON 解析工具、按主键查找流程
 * 与流程参数校验等辅助能力, 供实例 / 操作 / 统计兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmApprovalFlowService {

    // ==================== 流程状态 (共享) ====================

    /** 流程状态: DRAFT 草稿 */
    private static final String FLOW_STATUS_DRAFT = "DRAFT";
    /** 流程状态: ACTIVE 已激活 */
    static final String FLOW_STATUS_ACTIVE = "ACTIVE";
    /** 流程状态: INACTIVE 已停用 */
    private static final String FLOW_STATUS_INACTIVE = "INACTIVE";

    // ==================== 节点类型 (共享) ====================

    /** 节点类型: START 起始 */
    static final String NODE_START = "START";
    /** 节点类型: APPROVE 审批 */
    static final String NODE_APPROVE = "APPROVE";
    /** 节点类型: CC 抄送 */
    static final String NODE_CC = "CC";
    /** 节点类型: CONDITION 条件 */
    static final String NODE_CONDITION = "CONDITION";
    /** 节点类型: END 结束 */
    static final String NODE_END = "END";

    /** 审批人类型: USER 指定用户 */
    private static final String APPROVER_TYPE_USER = "USER";
    /** 审批人类型: ROLE 指定角色 */
    private static final String APPROVER_TYPE_ROLE = "ROLE";
    /** 审批人类型: DEPT 部门 */
    private static final String APPROVER_TYPE_DEPT = "DEPT";
    /** 审批人类型: MANAGER 直属主管 */
    private static final String APPROVER_TYPE_MANAGER = "MANAGER";
    /** 审批人类型: SUPERIOR 上级 */
    private static final String APPROVER_TYPE_SUPERIOR = "SUPERIOR";

    /** 默认版本号: 新建流程从 1 开始 */
    private static final int DEFAULT_VERSION_NUMBER = 1;
    /** 默认最长处理时长: 30 天 */
    private static final int DEFAULT_MAX_DURATION_DAYS = 30;
    /** 默认使用次数: 0 次 */
    private static final int DEFAULT_USAGE_COUNT = 0;

    /** 合法的流程类型 */
    private static final List<String> VALID_FLOW_TYPES = List.of(
            "CONTRACT", "EXPENSE", "LEAVE", "REFUND", "DISCOUNT",
            "PRICE_CHANGE", "CUSTOMER_MERGE", "CONTENT", "PURCHASE", "OTHER", "CUSTOM");

    /** 合法的业务类型 (共享) */
    static final List<String> VALID_BUSINESS_TYPES = List.of(
            "CONTRACT", "EXPENSE", "LEAVE", "REFUND", "DISCOUNT", "OTHER");

    /** 合法的流程状态 */
    private static final List<String> VALID_FLOW_STATUSES = List.of(
            FLOW_STATUS_DRAFT, FLOW_STATUS_ACTIVE, FLOW_STATUS_INACTIVE);

    /** 合法的节点类型 */
    private static final List<String> VALID_NODE_TYPES = List.of(
            NODE_START, NODE_APPROVE, NODE_CC, NODE_CONDITION, NODE_END);

    /** 合法的审批人类型 */
    private static final List<String> VALID_APPROVER_TYPES = List.of(
            APPROVER_TYPE_USER, APPROVER_TYPE_ROLE, APPROVER_TYPE_DEPT,
            APPROVER_TYPE_MANAGER, APPROVER_TYPE_SUPERIOR);

    /** JSON 映射器 */
    private final ObjectMapper objectMapper;

    /** 流程定义数据访问层 */
    private final ScrmApprovalFlowRepository flowRepository;

    // ============================================================
    // 流程管理
    // ============================================================

    /**
     * 创建审批流程定义。
     * <p>校验 flowType / nodes 合法性与 flowCode 唯一性后写入归属账号 ID 持久化,
     * 各开关与版本号缺省时填默认值, 状态默认 ACTIVE。</p>
     *
     * @param dto 流程参数
     * @return 创建后的流程
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmApprovalFlowEntity createFlow(ScrmApprovalFlowDto dto) throws ScrmException {
        validateFlowDto(dto, false);
        if (flowRepository.findByFlowCode(dto.getFlowCode()).isPresent()) {
            throw ScrmException.conflict("流程编码已存在: " + dto.getFlowCode());
        }
        ScrmApprovalFlowEntity entity = new ScrmApprovalFlowEntity();
        entity.setFlowName(dto.getFlowName());
        entity.setFlowCode(dto.getFlowCode());
        entity.setDescription(dto.getDescription());
        entity.setFlowType(dto.getFlowType());
        entity.setApplicableModule(dto.getApplicableModule());
        entity.setNodes(dto.getNodes());
        entity.setConditionRules(dto.getConditionRules());
        entity.setStartNode(dto.getStartNode());
        entity.setEndNodes(dto.getEndNodes());
        entity.setVersionNumber(DEFAULT_VERSION_NUMBER);
        entity.setStatus(FLOW_STATUS_ACTIVE);
        entity.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : Boolean.FALSE);
        entity.setUsageCount(DEFAULT_USAGE_COUNT);
        entity.setApproverFallback(dto.getApproverFallback());
        entity.setAllowDelegation(dto.getAllowDelegation() != null ? dto.getAllowDelegation() : Boolean.TRUE);
        entity.setAllowCountersign(dto.getAllowCountersign() != null ? dto.getAllowCountersign() : Boolean.FALSE);
        entity.setAllowUrgent(dto.getAllowUrgent() != null ? dto.getAllowUrgent() : Boolean.TRUE);
        entity.setMaxDurationDays(dto.getMaxDurationDays() != null ?
                dto.getMaxDurationDays() : DEFAULT_MAX_DURATION_DAYS);
        entity.setCreatedBy(dto.getCreatedBy());
        // 若设为默认, 清除同一账号下其他流程的默认标记
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            flowRepository.clearDefaultFlag(-1L);
        }
        entity = flowRepository.save(entity);
        log.info("创建审批流程: id={}, name={}, code={}, type={}",
                entity.getId(), entity.getFlowName(), entity.getFlowCode(), entity.getFlowType());
        return entity;
    }

    /**
     * 更新审批流程（字段非空才覆盖）。
     * <p>状态为 ACTIVE 时禁止更新核心节点定义, 请先停用或复制新版本。</p>
     *
     * @param id  流程 ID
     * @param dto 流程参数
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 参数非法 / 状态非法 / 编码重复
     */
    @Transactional
    public ScrmApprovalFlowEntity updateFlow(Long id, ScrmApprovalFlowDto dto) throws ScrmException {
        ScrmApprovalFlowEntity entity = findFlowOrThrow(id);
        if (FLOW_STATUS_ACTIVE.equals(entity.getStatus())) {
            throw ScrmException.conflict("已激活的流程不可更新, 请先停用: id=" + id);
        }
        validateFlowDto(dto, true);
        if (dto.getFlowCode() != null && !dto.getFlowCode().equals(entity.getFlowCode())) {
            if (flowRepository.findByFlowCode(dto.getFlowCode()).isPresent()) {
                throw ScrmException.conflict("流程编码已存在: " + dto.getFlowCode());
            }
        }
        if (dto.getFlowName() != null) entity.setFlowName(dto.getFlowName());
        if (dto.getFlowCode() != null) entity.setFlowCode(dto.getFlowCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getFlowType() != null) entity.setFlowType(dto.getFlowType());
        if (dto.getApplicableModule() != null) entity.setApplicableModule(dto.getApplicableModule());
        if (dto.getNodes() != null) entity.setNodes(dto.getNodes());
        if (dto.getConditionRules() != null) entity.setConditionRules(dto.getConditionRules());
        if (dto.getStartNode() != null) entity.setStartNode(dto.getStartNode());
        if (dto.getEndNodes() != null) entity.setEndNodes(dto.getEndNodes());
        if (dto.getApproverFallback() != null) entity.setApproverFallback(dto.getApproverFallback());
        if (dto.getAllowDelegation() != null) entity.setAllowDelegation(dto.getAllowDelegation());
        if (dto.getAllowCountersign() != null) entity.setAllowCountersign(dto.getAllowCountersign());
        if (dto.getAllowUrgent() != null) entity.setAllowUrgent(dto.getAllowUrgent());
        if (dto.getMaxDurationDays() != null) entity.setMaxDurationDays(dto.getMaxDurationDays());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = flowRepository.save(entity);
        log.info("更新审批流程: id={}, name={}", entity.getId(), entity.getFlowName());
        return entity;
    }

    /**
     * 删除审批流程 (须为 INACTIVE / DRAFT 状态)。
     *
     * @param id 流程 ID
     * @throws ScrmException 流程不存在 / 状态非法
     */
    @Transactional
    public void deleteFlow(Long id) throws ScrmException {
        ScrmApprovalFlowEntity entity = findFlowOrThrow(id);
        if (FLOW_STATUS_ACTIVE.equals(entity.getStatus())) {
            throw ScrmException.conflict("已激活的流程不可删除, 请先停用: id=" + id);
        }
        flowRepository.delete(entity);
        log.info("删除审批流程: id={}, name={}", id, entity.getFlowName());
    }

    /**
     * 查询流程详情。
     *
     * @param id 流程 ID
     * @return 流程实体
     * @throws ScrmException 流程不存在
     */
    @Transactional(readOnly = true)
    public ScrmApprovalFlowEntity getFlow(Long id) throws ScrmException {
        return findFlowOrThrow(id);
    }

    /**
     * 按编码查询流程。
     *
     * @param code 流程编码
     * @return 流程实体
     * @throws ScrmException 流程不存在
     */
    @Transactional(readOnly = true)
    public ScrmApprovalFlowEntity getFlowByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("流程编码不能为空");
        }
        return flowRepository.findByFlowCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "审批流程不存在: code=" + code));
    }

    /**
     * 分页查询流程, 支持按类型 / 状态 / 关键字过滤。
     *
     * @param flowType 流程类型过滤（可空）
     * @param status   状态过滤（可空）
     * @param keyword  流程名称关键字模糊匹配（可空）
     * @param pageable 分页参数
     * @return 流程分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmApprovalFlowEntity> listFlows(String flowType, String status, String keyword, Pageable pageable) {
        Specification<ScrmApprovalFlowEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (flowType != null && !flowType.isBlank()) {
                predicates.add(cb.equal(root.get("flowType"), flowType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("flowName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return flowRepository.findAll(spec, pageable);
    }

    /**
     * 激活流程 (DRAFT / INACTIVE → ACTIVE)。
     *
     * @param id 流程 ID
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 状态非法
     */
    @Transactional
    public ScrmApprovalFlowEntity activateFlow(Long id) throws ScrmException {
        ScrmApprovalFlowEntity entity = findFlowOrThrow(id);
        if (!FLOW_STATUS_DRAFT.equals(entity.getStatus()) && !FLOW_STATUS_INACTIVE.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 DRAFT / INACTIVE 状态可激活: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(FLOW_STATUS_ACTIVE);
        entity = flowRepository.save(entity);
        log.info("激活审批流程: id={}, name={}", id, entity.getFlowName());
        return entity;
    }

    /**
     * 停用流程 (ACTIVE → INACTIVE)。
     *
     * @param id 流程 ID
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 状态非法
     */
    @Transactional
    public ScrmApprovalFlowEntity deactivateFlow(Long id) throws ScrmException {
        ScrmApprovalFlowEntity entity = findFlowOrThrow(id);
        if (!FLOW_STATUS_ACTIVE.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 ACTIVE 状态可停用: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(FLOW_STATUS_INACTIVE);
        entity = flowRepository.save(entity);
        log.info("停用审批流程: id={}, name={}", id, entity.getFlowName());
        return entity;
    }

    /**
     * 设置为默认流程 (清除同一账号下其他流程的默认标记)。
     *
     * @param id 流程 ID
     * @return 更新后的流程
     * @throws ScrmException 流程不存在 / 状态非法
     */
    @Transactional
    public ScrmApprovalFlowEntity setDefault(Long id) throws ScrmException {
        ScrmApprovalFlowEntity entity = findFlowOrThrow(id);
        if (!FLOW_STATUS_ACTIVE.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 ACTIVE 状态可设为默认: id=" + id + ", status=" + entity.getStatus());
        }
        flowRepository.clearDefaultFlag(id);
        entity.setIsDefault(Boolean.TRUE);
        entity = flowRepository.save(entity);
        log.info("设置默认审批流程: id={}, name={}", id, entity.getFlowName());
        return entity;
    }

    /**
     * 复制流程 (生成新编码副本, 状态 DRAFT)。
     *
     * @param id      流程 ID
     * @param newCode 新流程编码
     * @return 复制后的流程
     * @throws ScrmException 流程不存在 / 编码重复
     */
    @Transactional
    public ScrmApprovalFlowEntity copyFlow(Long id, String newCode) throws ScrmException {
        ScrmApprovalFlowEntity source = findFlowOrThrow(id);
        if (newCode == null || newCode.isBlank()) {
            throw ScrmException.badRequest("新流程编码不能为空");
        }
        if (flowRepository.findByFlowCode(newCode).isPresent()) {
            throw ScrmException.conflict("流程编码已存在: " + newCode);
        }
        ScrmApprovalFlowEntity entity = new ScrmApprovalFlowEntity();
        entity.setFlowName(source.getFlowName() + " (副本)");
        entity.setFlowCode(newCode);
        entity.setDescription(source.getDescription());
        entity.setFlowType(source.getFlowType());
        entity.setApplicableModule(source.getApplicableModule());
        entity.setNodes(source.getNodes());
        entity.setConditionRules(source.getConditionRules());
        entity.setStartNode(source.getStartNode());
        entity.setEndNodes(source.getEndNodes());
        entity.setVersionNumber(DEFAULT_VERSION_NUMBER);
        entity.setStatus(FLOW_STATUS_DRAFT);
        entity.setIsDefault(Boolean.FALSE);
        entity.setUsageCount(DEFAULT_USAGE_COUNT);
        entity.setApproverFallback(source.getApproverFallback());
        entity.setAllowDelegation(source.getAllowDelegation());
        entity.setAllowCountersign(source.getAllowCountersign());
        entity.setAllowUrgent(source.getAllowUrgent());
        entity.setMaxDurationDays(source.getMaxDurationDays());
        entity.setCreatedBy(source.getCreatedBy());
        entity = flowRepository.save(entity);
        log.info("复制审批流程: sourceId={}, newId={}, newCode={}", id, entity.getId(), newCode);
        return entity;
    }

    /**
     * 验证流程 (检查节点完整性 / 连接有效性)。
     *
     * @param id 流程 ID
     * @return 验证结果 Map {valid, errors, nodeCount, hasStart, hasEnd}
     * @throws ScrmException 流程不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateFlow(Long id) throws ScrmException {
        ScrmApprovalFlowEntity entity = findFlowOrThrow(id);
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> nodes = parseJsonList(entity.getNodes(), "nodes");
        result.put("nodeCount", nodes.size());
        if (nodes.isEmpty()) {
            errors.add("节点定义为空");
        }
        List<String> nodeIds = new ArrayList<>();
        boolean hasStart = false;
        boolean hasEnd = false;
        for (Map<String, Object> node : nodes) {
            String nodeId = toStr(node.get("nodeId"));
            String nodeType = toStr(node.get("nodeType"));
            if (nodeId.isEmpty()) {
                errors.add("存在缺少 nodeId 的节点");
            } else {
                nodeIds.add(nodeId);
            }
            if (nodeType.isEmpty()) {
                errors.add("节点缺少 nodeType: " + nodeId);
            } else if (!VALID_NODE_TYPES.contains(nodeType)) {
                errors.add("节点类型非法: " + nodeId + ", nodeType=" + nodeType);
            }
            if (NODE_START.equals(nodeType)) {
                hasStart = true;
            }
            if (NODE_END.equals(nodeType)) {
                hasEnd = true;
            }
            // 审批节点校验审批人类型
            if (NODE_APPROVE.equals(nodeType)) {
                String approverType = toStr(node.get("approverType"));
                if (!approverType.isEmpty() && !VALID_APPROVER_TYPES.contains(approverType)) {
                    errors.add("审批人类型非法: " + nodeId + ", approverType=" + approverType);
                }
                String approverIds = toStr(node.get("approverIds"));
                if (approverType.isEmpty() || approverIds.isEmpty()) {
                    errors.add("审批节点缺少审批人配置: " + nodeId);
                }
            }
        }
        if (!hasStart) {
            errors.add("缺少 START 起始节点");
        }
        if (!hasEnd) {
            errors.add("缺少 END 结束节点");
        }
        // 校验起始节点存在
        if (entity.getStartNode() != null && !entity.getStartNode().isBlank()) {
            if (!nodeIds.contains(entity.getStartNode())) {
                errors.add("起始节点不存在于节点列表: " + entity.getStartNode());
            }
        }
        // 校验结束节点存在
        if (entity.getEndNodes() != null && !entity.getEndNodes().isBlank()) {
            for (String endNode : entity.getEndNodes().split(",")) {
                String trimmed = endNode.trim();
                if (!trimmed.isEmpty() && !nodeIds.contains(trimmed)) {
                    errors.add("结束节点不存在于节点列表: " + trimmed);
                }
            }
        }
        // 校验条件路由规则引用的节点存在
        List<Map<String, Object>> rules = parseJsonList(entity.getConditionRules(), "conditionRules");
        for (Map<String, Object> rule : rules) {
            String ruleNodeId = toStr(rule.get("nodeId"));
            if (!ruleNodeId.isEmpty() && !nodeIds.contains(ruleNodeId)) {
                errors.add("条件路由规则引用的节点不存在: " + ruleNodeId);
            }
            Object routesObj = rule.get("routes");
            if (routesObj instanceof List) {
                for (Object r : (List<?>) routesObj) {
                    if (r instanceof Map) {
                        String toNode = toStr(((Map<?, ?>) r).get("toNode"));
                        if (!toNode.isEmpty() && !nodeIds.contains(toNode)) {
                            errors.add("条件路由目标节点不存在: " + toNode);
                        }
                    }
                }
            }
        }
        result.put("hasStart", hasStart);
        result.put("hasEnd", hasEnd);
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        return result;
    }

    /**
     * 按业务类型获取默认流程 (优先 isDefault=TRUE 的已激活流程, 否则取使用次数最多的)。
     *
     * @param businessType 业务类型
     * @return 流程实体
     * @throws ScrmException 业务类型非法 / 流程不存在
     */
    @Transactional(readOnly = true)
    public ScrmApprovalFlowEntity getFlowByBusinessType(String businessType) throws ScrmException {
        if (businessType == null || businessType.isBlank()) {
            throw ScrmException.badRequest("业务类型不能为空");
        }
        if (!VALID_BUSINESS_TYPES.contains(businessType)) {
            throw ScrmException.badRequest("业务类型非法: " + businessType + ", 仅支持 " + VALID_BUSINESS_TYPES);
        }
        // 业务类型与流程类型对齐: 默认查找 flowType=businessType 的流程
        List<ScrmApprovalFlowEntity> flows = flowRepository.findActiveByFlowType(businessType);
        if (flows.isEmpty()) {
            // 回退到默认流程
            flows = flowRepository.findDefaultFlows();
        }
        if (flows.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                    "未找到匹配业务类型 [" + businessType + "] 的已激活审批流程");
        }
        return flows.get(0);
    }

    /**
     * 递增流程使用次数并刷新最近使用时间。
     *
     * @param id 流程 ID
     */
    @Transactional
    public void incrementUsage(Long id) {
        try {
            flowRepository.incrementUsage(id, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("递增审批流程使用次数失败: flowId={}, err={}", id, e.getMessage());
        }
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验流程参数。
     *
     * @param dto     流程参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateFlowDto(ScrmApprovalFlowDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("流程参数不能为空");
        }
        if (dto.getFlowName() != null) {
            if (dto.getFlowName().isBlank()) {
                throw ScrmException.badRequest("流程名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("流程名称不能为空");
        }
        if (dto.getFlowCode() != null) {
            if (dto.getFlowCode().isBlank()) {
                throw ScrmException.badRequest("流程编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("流程编码不能为空");
        }
        if (dto.getFlowType() != null) {
            if (!VALID_FLOW_TYPES.contains(dto.getFlowType())) {
                throw ScrmException.badRequest(
                        "流程类型非法: " + dto.getFlowType() + ", 仅支持 " + VALID_FLOW_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("流程类型不能为空");
        }
        if (!partial) {
            if (dto.getNodes() == null || dto.getNodes().isBlank()) {
                throw ScrmException.badRequest("节点定义不能为空");
            }
        }
        if (dto.getStatus() != null && !VALID_FLOW_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest(
                    "流程状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_FLOW_STATUSES);
        }
        if (dto.getMaxDurationDays() != null && dto.getMaxDurationDays() < 0) {
            throw ScrmException.badRequest("最大审批时长不能为负数");
        }
    }

    /**
     * 按主键查询流程, 不存在则抛异常。
     *
     * @param id 流程 ID
     * @return 流程实体
     * @throws ScrmException 流程不存在
     */
    ScrmApprovalFlowEntity findFlowOrThrow(Long id) throws ScrmException {
        ScrmApprovalFlowEntity entity = flowRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "审批流程不存在: id=" + id));
        return entity;
    }

    /**
     * 解析 JSON 数组字符串为 List<Map>。
     *
     * @param json      JSON 字符串
     * @param fieldName 字段名 (错误消息用)
     * @return 解析结果, 空字符串返回空列表
     * @throws ScrmException JSON 解析失败
     */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> parseJsonList(String json, String fieldName) throws ScrmException {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw ScrmException.internal(fieldName + " JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 JSON 对象字符串为 Map。
     *
     * @param json      JSON 字符串
     * @param fieldName 字段名 (错误消息用)
     * @return 解析结果, 空字符串返回空 Map
     * @throws ScrmException JSON 解析失败
     */
    @SuppressWarnings("unchecked")
    Map<String, Object> parseJsonMap(String json, String fieldName) throws ScrmException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw ScrmException.internal(fieldName + " JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 将对象转换为字符串。
     *
     * @param obj 对象
     * @return 字符串, null 返回空串
     */
    String toStr(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}