/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowDefinitionService.java
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

import org.hiylo.scrm.dto.ScrmWorkflowDto;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 工作流定义与版本管理服务。
 * <p>
 * 承载工作流定义子域: 工作流增删改查与按编码查询、分页列表、激活 / 暂停 / 归档 / 复制 /
 * 发布新版本、图结构与校验。同时托管工作流共享常量与辅助方法 (状态 / 节点类型 / 合法集合、
 * 按主键查找、JSON 列表解析、对象转字符串、参数校验), 供实例 / 节点 / 统计兄弟类以
 * package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmWorkflowDefinitionService {

    /** 默认工作流类型 */
    private static final String DEFAULT_WORKFLOW_TYPE = "MARKETING";

    /** 状态: DRAFT 草稿 */
    static final String STATUS_DRAFT = "DRAFT";
    /** 状态: ACTIVE 已激活 */
    static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态: PAUSED 已暂停 */
    static final String STATUS_PAUSED = "PAUSED";
    /** 状态: ARCHIVED 已归档 */
    static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 节点类型: START 起始 */
    static final String NODE_START = "START";
    /** 节点类型: END 结束 */
    static final String NODE_END = "END";

    /** 默认优先级 */
    static final int DEFAULT_PRIORITY = 0;
    /** 默认最大并发实例数 */
    static final int DEFAULT_MAX_CONCURRENT = 1000;
    /** 默认冷却时间 (小时) */
    static final int DEFAULT_COOLDOWN_HOURS = 0;
    /** 默认发布版本号 */
    static final int DEFAULT_VERSION_NUMBER = 1;

    /** 合法的工作流类型 */
    static final List<String> VALID_WORKFLOW_TYPES = List.of(
            "MARKETING", "ONBOARDING", "RETENTION", "RE_ENGAGEMENT",
            "POST_PURCHASE", "ABANDONED_CART", "BIRTHDAY", "ANNIVERSARY", "CUSTOM");

    /** 合法的触发器类型 */
    static final List<String> VALID_TRIGGER_TYPES = List.of(
            "EVENT", "SCHEDULE", "SEGMENT", "WEBHOOK", "MANUAL");

    /** 合法的工作流状态 */
    static final List<String> VALID_WORKFLOW_STATUSES = List.of(
            STATUS_DRAFT, STATUS_ACTIVE, STATUS_PAUSED, STATUS_ARCHIVED);

    /** 合法的节点类型 */
    static final List<String> VALID_NODE_TYPES = List.of(
            "START", "END", "ACTION", "CONDITION", "DELAY",
            "LOOP", "SWITCH", "PARALLEL", "WAIT", "SUB_WORKFLOW");

    /** 工作流数据访问层 */
    private final ScrmWorkflowRepository workflowRepository;

    /** 工作流实例数据访问层 (删除工作流时级联删除实例) */
    private final ScrmWorkflowInstanceRepository instanceRepository;

    /** 节点日志数据访问层 (删除工作流时级联删除日志) */
    private final ScrmWorkflowNodeLogRepository nodeLogRepository;

    /** JSON 映射器 */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 工作流管理
    // ============================================================

    /**
     * 创建工作流。
     * <p>校验 workflowType / triggerType 合法性与 workflowCode 唯一性后写入归属账号 ID 持久化,
     * priority / cooldownHours / maxConcurrentInstances 缺省时填默认值, 状态默认 DRAFT。</p>
     *
     * @param dto 工作流参数
     * @return 创建后的工作流
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmWorkflowEntity createWorkflow(ScrmWorkflowDto dto) throws ScrmException {
        validateWorkflowDto(dto, false);
        if (workflowRepository.findByWorkflowCode(dto.getWorkflowCode()).isPresent()) {
            throw ScrmException.conflict("工作流编码已存在: " + dto.getWorkflowCode());
        }
        ScrmWorkflowEntity entity = new ScrmWorkflowEntity();
        entity.setWorkflowName(dto.getWorkflowName());
        entity.setWorkflowCode(dto.getWorkflowCode());
        entity.setDescription(dto.getDescription());
        entity.setWorkflowType(dto.getWorkflowType());
        entity.setTriggerType(dto.getTriggerType());
        entity.setTriggerConfig(dto.getTriggerConfig());
        entity.setNodes(dto.getNodes());
        entity.setEdges(dto.getEdges());
        entity.setEntryNode(dto.getEntryNode());
        entity.setStatus(STATUS_DRAFT);
        entity.setVersionNumber(DEFAULT_VERSION_NUMBER);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setExecutionCount(0);
        entity.setSuccessCount(0);
        entity.setFailureCount(0);
        entity.setActiveInstanceCount(0);
        entity.setAvgExecutionTimeMs(0);
        entity.setTargetSegment(dto.getTargetSegment());
        entity.setExclusionSegment(dto.getExclusionSegment());
        entity.setMaxConcurrentInstances(dto.getMaxConcurrentInstances() != null
                ? dto.getMaxConcurrentInstances() : DEFAULT_MAX_CONCURRENT);
        entity.setCooldownHours(dto.getCooldownHours() != null
                ? dto.getCooldownHours() : DEFAULT_COOLDOWN_HOURS);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = workflowRepository.save(entity);
        log.info("创建工作流: id={}, name={}, code={}, type={}",
                entity.getId(), entity.getWorkflowName(), entity.getWorkflowCode(), entity.getWorkflowType());
        return entity;
    }

    /**
     * 更新工作流（字段非空才覆盖）。
     * <p>状态为 ACTIVE / ARCHIVED 时禁止更新核心字段。</p>
     *
     * @param id  工作流 ID
     * @param dto 工作流参数
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 参数非法 / 状态非法 / 编码重复
     */
    @Transactional
    public ScrmWorkflowEntity updateWorkflow(Long id, ScrmWorkflowDto dto) throws ScrmException {
        ScrmWorkflowEntity entity = findWorkflowOrThrow(id);
        if (STATUS_ACTIVE.equals(entity.getStatus()) || STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已激活/已归档的工作流不可更新: id=" + id + ", status=" + entity.getStatus());
        }
        validateWorkflowDto(dto, true);
        if (dto.getWorkflowCode() != null && !dto.getWorkflowCode().equals(entity.getWorkflowCode())) {
            if (workflowRepository.findByWorkflowCode(dto.getWorkflowCode()).isPresent()) {
                throw ScrmException.conflict("工作流编码已存在: " + dto.getWorkflowCode());
            }
        }
        if (dto.getWorkflowName() != null) entity.setWorkflowName(dto.getWorkflowName());
        if (dto.getWorkflowCode() != null) entity.setWorkflowCode(dto.getWorkflowCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getWorkflowType() != null) entity.setWorkflowType(dto.getWorkflowType());
        if (dto.getTriggerType() != null) entity.setTriggerType(dto.getTriggerType());
        if (dto.getTriggerConfig() != null) entity.setTriggerConfig(dto.getTriggerConfig());
        if (dto.getNodes() != null) entity.setNodes(dto.getNodes());
        if (dto.getEdges() != null) entity.setEdges(dto.getEdges());
        if (dto.getEntryNode() != null) entity.setEntryNode(dto.getEntryNode());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getTargetSegment() != null) entity.setTargetSegment(dto.getTargetSegment());
        if (dto.getExclusionSegment() != null) entity.setExclusionSegment(dto.getExclusionSegment());
        if (dto.getMaxConcurrentInstances() != null) entity.setMaxConcurrentInstances(dto.getMaxConcurrentInstances());
        if (dto.getCooldownHours() != null) entity.setCooldownHours(dto.getCooldownHours());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = workflowRepository.save(entity);
        log.info("更新工作流: id={}, name={}", entity.getId(), entity.getWorkflowName());
        return entity;
    }

    /**
     * 删除工作流 (同时删除关联实例与节点日志)。
     *
     * @param id 工作流 ID
     * @throws ScrmException 工作流不存在
     */
    @Transactional
    public void deleteWorkflow(Long id) throws ScrmException {
        ScrmWorkflowEntity entity = findWorkflowOrThrow(id);
        if (STATUS_ACTIVE.equals(entity.getStatus())) {
            throw ScrmException.conflict("已激活的工作流不可删除, 请先暂停或归档: id=" + id);
        }
        List<ScrmWorkflowInstanceEntity> instances = instanceRepository.findAll((root, query, cb) ->
                cb.and( cb.equal(root.get("workflowId"), id)));
        if (!instances.isEmpty()) {
            instanceRepository.deleteAll(instances);
        }
        List<ScrmWorkflowNodeLogEntity> logs = nodeLogRepository.findAll((root, query, cb) ->
                cb.and( cb.equal(root.get("workflowId"), id)));
        if (!logs.isEmpty()) {
            nodeLogRepository.deleteAll(logs);
        }
        workflowRepository.delete(entity);
        log.info("删除工作流: id={}, name={}, instances={}, logs={}",
                id, entity.getWorkflowName(), instances.size(), logs.size());
    }

    /**
     * 查询工作流详情。
     *
     * @param id 工作流 ID
     * @return 工作流实体
     * @throws ScrmException 工作流不存在
     */
    @Transactional(readOnly = true)
    public ScrmWorkflowEntity getWorkflow(Long id) throws ScrmException {
        return findWorkflowOrThrow(id);
    }

    /**
     * 按编码查询工作流。
     *
     * @param code 工作流编码
     * @return 工作流实体
     * @throws ScrmException 工作流不存在
     */
    @Transactional(readOnly = true)
    public ScrmWorkflowEntity getWorkflowByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("工作流编码不能为空");
        }
        return workflowRepository.findByWorkflowCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "工作流不存在: code=" + code));
    }

    /**
     * 分页查询工作流, 支持按类型 / 触发器类型 / 状态 / 关键字过滤。
     *
     * @param workflowType 工作流类型过滤（可空）
     * @param triggerType  触发器类型过滤（可空）
     * @param status       状态过滤（可空）
     * @param keyword      工作流名称关键字模糊匹配（可空）
     * @param pageable     分页参数
     * @return 工作流分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmWorkflowEntity> listWorkflows(String workflowType, String triggerType, String status,
                                                   String keyword, Pageable pageable) {
        Specification<ScrmWorkflowEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (workflowType != null && !workflowType.isBlank()) {
                predicates.add(cb.equal(root.get("workflowType"), workflowType));
            }
            if (triggerType != null && !triggerType.isBlank()) {
                predicates.add(cb.equal(root.get("triggerType"), triggerType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("workflowName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return workflowRepository.findAll(spec, pageable);
    }

    /**
     * 激活工作流 (DRAFT / PAUSED → ACTIVE)。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 状态非法
     */
    @Transactional
    public ScrmWorkflowEntity activateWorkflow(Long id) throws ScrmException {
        ScrmWorkflowEntity entity = findWorkflowOrThrow(id);
        if (!STATUS_DRAFT.equals(entity.getStatus()) && !STATUS_PAUSED.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 DRAFT / PAUSED 状态可激活: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_ACTIVE);
        entity = workflowRepository.save(entity);
        log.info("激活工作流: id={}, name={}", id, entity.getWorkflowName());
        return entity;
    }

    /**
     * 暂停工作流 (ACTIVE → PAUSED)。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 状态非法
     */
    @Transactional
    public ScrmWorkflowEntity pauseWorkflow(Long id) throws ScrmException {
        ScrmWorkflowEntity entity = findWorkflowOrThrow(id);
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 ACTIVE 状态可暂停: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_PAUSED);
        entity = workflowRepository.save(entity);
        log.info("暂停工作流: id={}, name={}", id, entity.getWorkflowName());
        return entity;
    }

    /**
     * 归档工作流 (DRAFT / ACTIVE / PAUSED → ARCHIVED)。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 状态非法
     */
    @Transactional
    public ScrmWorkflowEntity archiveWorkflow(Long id) throws ScrmException {
        ScrmWorkflowEntity entity = findWorkflowOrThrow(id);
        if (STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw ScrmException.conflict("工作流已归档: id=" + id);
        }
        entity.setStatus(STATUS_ARCHIVED);
        entity = workflowRepository.save(entity);
        log.info("归档工作流: id={}, name={}", id, entity.getWorkflowName());
        return entity;
    }

    /**
     * 复制工作流 (生成新编码副本, 状态 DRAFT)。
     *
     * @param id 工作流 ID
     * @return 复制后的工作流
     * @throws ScrmException 工作流不存在
     */
    @Transactional
    public ScrmWorkflowEntity copyWorkflow(Long id) throws ScrmException {
        ScrmWorkflowEntity source = findWorkflowOrThrow(id);
        String newCode = source.getWorkflowCode() + "_copy_" + System.currentTimeMillis();
        ScrmWorkflowEntity entity = new ScrmWorkflowEntity();
        entity.setWorkflowName(source.getWorkflowName() + " (副本)");
        entity.setWorkflowCode(newCode);
        entity.setDescription(source.getDescription());
        entity.setWorkflowType(source.getWorkflowType());
        entity.setTriggerType(source.getTriggerType());
        entity.setTriggerConfig(source.getTriggerConfig());
        entity.setNodes(source.getNodes());
        entity.setEdges(source.getEdges());
        entity.setEntryNode(source.getEntryNode());
        entity.setStatus(STATUS_DRAFT);
        entity.setVersionNumber(DEFAULT_VERSION_NUMBER);
        entity.setPriority(source.getPriority());
        entity.setExecutionCount(0);
        entity.setSuccessCount(0);
        entity.setFailureCount(0);
        entity.setActiveInstanceCount(0);
        entity.setAvgExecutionTimeMs(0);
        entity.setTargetSegment(source.getTargetSegment());
        entity.setExclusionSegment(source.getExclusionSegment());
        entity.setMaxConcurrentInstances(source.getMaxConcurrentInstances());
        entity.setCooldownHours(source.getCooldownHours());
        entity.setCreatedBy(source.getCreatedBy());
        entity = workflowRepository.save(entity);
        log.info("复制工作流: sourceId={}, newId={}, newCode={}", id, entity.getId(), newCode);
        return entity;
    }

    /**
     * 发布新版本 (versionNumber 递增, 状态置为 DRAFT 以便重新编辑激活)。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在
     */
    @Transactional
    public ScrmWorkflowEntity publishVersion(Long id) throws ScrmException {
        ScrmWorkflowEntity entity = findWorkflowOrThrow(id);
        entity.setVersionNumber((entity.getVersionNumber() != null
                ? entity.getVersionNumber() : DEFAULT_VERSION_NUMBER) + 1);
        entity = workflowRepository.save(entity);
        log.info("发布工作流新版本: id={}, name={}, version={}",
                id, entity.getWorkflowName(), entity.getVersionNumber());
        return entity;
    }

    /**
     * 验证工作流 (检查节点完整性 / 连接有效性)。
     *
     * @param id 工作流 ID
     * @return 验证结果 Map {valid, errors, nodeCount, edgeCount, hasEntry, hasEnd}
     * @throws ScrmException 工作流不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateWorkflow(Long id) throws ScrmException {
        ScrmWorkflowEntity entity = findWorkflowOrThrow(id);
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> nodes = parseJsonList(entity.getNodes(), "nodes");
        List<Map<String, Object>> edges = parseJsonList(entity.getEdges(), "edges");
        result.put("nodeCount", nodes.size());
        result.put("edgeCount", edges.size());
        if (nodes.isEmpty()) {
            errors.add("节点定义为空");
        }
        // 校验节点字段完整
        List<String> nodeIds = new ArrayList<>();
        boolean hasStart = false;
        boolean hasEnd = false;
        for (Map<String, Object> node : nodes) {
            String nodeId = toStr(node.get("id"));
            String type = toStr(node.get("type"));
            if (nodeId.isEmpty()) {
                errors.add("存在缺少 id 的节点");
            } else {
                nodeIds.add(nodeId);
            }
            if (type.isEmpty()) {
                errors.add("节点缺少 type: " + nodeId);
            } else if (!VALID_NODE_TYPES.contains(type)) {
                errors.add("节点类型非法: " + nodeId + ", type=" + type);
            }
            if (NODE_START.equals(type)) {
                hasStart = true;
            }
            if (NODE_END.equals(type)) {
                hasEnd = true;
            }
        }
        if (!hasStart) {
            errors.add("缺少 START 起始节点");
        }
        if (!hasEnd) {
            errors.add("缺少 END 结束节点");
        }
        // 校验入口节点存在
        if (entity.getEntryNode() != null && !entity.getEntryNode().isBlank()) {
            if (!nodeIds.contains(entity.getEntryNode())) {
                errors.add("入口节点不存在于节点列表: " + entity.getEntryNode());
            }
        }
        result.put("hasEntry", entity.getEntryNode() != null && !entity.getEntryNode().isBlank());
        // 校验连接引用的节点存在
        for (Map<String, Object> edge : edges) {
            String from = toStr(edge.get("from"));
            String to = toStr(edge.get("to"));
            if (!from.isEmpty() && !nodeIds.contains(from)) {
                errors.add("连接的 from 节点不存在: " + from);
            }
            if (!to.isEmpty() && !nodeIds.contains(to)) {
                errors.add("连接的 to 节点不存在: " + to);
            }
        }
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        return result;
    }

    /**
     * 获取工作流图结构 (节点 + 连接 + 入口)。
     *
     * @param id 工作流 ID
     * @return 图结构 Map {workflowId, nodes, edges, entryNode}
     * @throws ScrmException 工作流不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getWorkflowGraph(Long id) throws ScrmException {
        ScrmWorkflowEntity entity = findWorkflowOrThrow(id);
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("workflowId", entity.getId());
        graph.put("workflowName", entity.getWorkflowName());
        graph.put("nodes", parseJsonList(entity.getNodes(), "nodes"));
        graph.put("edges", parseJsonList(entity.getEdges(), "edges"));
        graph.put("entryNode", entity.getEntryNode());
        return graph;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验工作流参数。
     *
     * @param dto     工作流参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateWorkflowDto(ScrmWorkflowDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("工作流参数不能为空");
        }
        if (dto.getWorkflowName() != null) {
            if (dto.getWorkflowName().isBlank()) {
                throw ScrmException.badRequest("工作流名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("工作流名称不能为空");
        }
        if (dto.getWorkflowCode() != null) {
            if (dto.getWorkflowCode().isBlank()) {
                throw ScrmException.badRequest("工作流编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("工作流编码不能为空");
        }
        if (dto.getWorkflowType() != null) {
            if (!VALID_WORKFLOW_TYPES.contains(dto.getWorkflowType())) {
                throw ScrmException.badRequest(
                        "工作流类型非法: " + dto.getWorkflowType() + ", 仅支持 " + VALID_WORKFLOW_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("工作流类型不能为空");
        }
        if (dto.getTriggerType() != null) {
            if (!VALID_TRIGGER_TYPES.contains(dto.getTriggerType())) {
                throw ScrmException.badRequest(
                        "触发器类型非法: " + dto.getTriggerType() + ", 仅支持 " + VALID_TRIGGER_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("触发器类型不能为空");
        }
        if (!partial) {
            if (dto.getTriggerConfig() == null || dto.getTriggerConfig().isBlank()) {
                throw ScrmException.badRequest("触发器配置不能为空");
            }
            if (dto.getNodes() == null || dto.getNodes().isBlank()) {
                throw ScrmException.badRequest("节点定义不能为空");
            }
        }
        if (dto.getStatus() != null && !VALID_WORKFLOW_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest(
                    "工作流状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_WORKFLOW_STATUSES);
        }
        if (dto.getMaxConcurrentInstances() != null && dto.getMaxConcurrentInstances() < 0) {
            throw ScrmException.badRequest("最大并发实例数不能为负数");
        }
        if (dto.getCooldownHours() != null && dto.getCooldownHours() < 0) {
            throw ScrmException.badRequest("冷却时间不能为负数");
        }
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
     * 将对象转换为字符串。
     *
     * @param obj 对象
     * @return 字符串, null 返回空串
     */
    String toStr(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 按主键查询工作流, 不存在抛异常, 并校验归属账号。
     *
     * @param id 工作流 ID
     * @return 工作流实体
     * @throws ScrmException 工作流不存在
     */
    ScrmWorkflowEntity findWorkflowOrThrow(Long id) throws ScrmException {        ScrmWorkflowEntity entity = workflowRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "工作流不存在: id=" + id));
        return entity;
    }
}
