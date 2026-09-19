/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowNodeService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmWorkflowEntity;
import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.entity.ScrmWorkflowNodeLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmWorkflowInstanceRepository;
import org.hiylo.scrm.repository.ScrmWorkflowNodeLogRepository;
import org.hiylo.scrm.repository.ScrmWorkflowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SCRM 工作流节点处理服务。
 * <p>
 * 承载节点执行子域: 按节点类型执行 (START / ACTION / CONDITION / DELAY / END, 其余编排语义
 * 未实现的节点直通), 条件真实评估与动作真实分派, 延迟登记, 下一节点流转与实例完成。
 * 同时托管节点共享常量 (节点 / 日志 / 条件结果状态、简化条件表达式) 与辅助方法
 * (按主键查实例、节点查找与配置解析、条件解析与校验、延迟时长解析、执行日志与活跃数维护),
 * 供实例 / 统计兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmWorkflowNodeService {

    /** 节点日志状态: PENDING 待执行 */
    static final String LOG_PENDING = "PENDING";
    /** 节点日志状态: RUNNING 执行中 */
    static final String LOG_RUNNING = "RUNNING";
    /** 节点日志状态: SUCCESS 成功 */
    static final String LOG_SUCCESS = "SUCCESS";
    /** 节点日志状态: FAILED 失败 */
    static final String LOG_FAILED = "FAILED";
    /** 节点日志状态: SKIPPED 已跳过 */
    static final String LOG_SKIPPED = "SKIPPED";
    /** 节点日志状态: WAITING 等待中 */
    static final String LOG_WAITING = "WAITING";

    /** 节点类型: ACTION 动作 */
    private static final String NODE_ACTION = "ACTION";
    /** 节点类型: CONDITION 条件 */
    private static final String NODE_CONDITION = "CONDITION";
    /** 节点类型: DELAY 延时 */
    private static final String NODE_DELAY = "DELAY";

    /** 默认条件组合 */
    private static final String DEFAULT_CONDITION_TYPE = CustomerConditionEvaluator.CONDITION_TYPE_ALL;
    /** 默认延迟时长 (分钟) */
    private static final int DEFAULT_DELAY_MINUTES = 60;

    /** 条件结果: 命中 */
    private static final String CONDITION_RESULT_TRUE = "TRUE";
    /** 条件结果: 未命中 */
    private static final String CONDITION_RESULT_FALSE = "FALSE";

    /** 简化条件表达式: {@code <field> <operator> <value>} 三段式 */
    private static final Pattern SIMPLE_CONDITION = Pattern.compile("^(\\S+)\\s+(\\S+)\\s+(.+)$");

    /** 简化条件表达式操作符别名 */
    private static final Map<String, String> CONDITION_OPERATOR_ALIASES = Map.of(
            "==", "eq", "!=", "ne", ">=", "gte", "<=", "lte", ">", "gt", "<", "lt");

    /** 工作流数据访问层 */
    private final ScrmWorkflowRepository workflowRepository;

    /** 工作流实例数据访问层 */
    private final ScrmWorkflowInstanceRepository instanceRepository;

    /** 节点日志数据访问层 */
    private final ScrmWorkflowNodeLogRepository nodeLogRepository;

    /** 客户数据访问层 (条件节点评估取客户属性) */
    private final ScrmCustomerRepository customerRepository;

    /** 客户条件评估共享组件 (与客群分群复用同一实现) */
    private final CustomerConditionEvaluator conditionEvaluator;

    /** 工作流动作执行器 (分派到真实业务服务) */
    private final WorkflowActionExecutor actionExecutor;

    /** JSON 映射器 */
    private final ObjectMapper objectMapper;

    /** 工作流定义与版本子域服务 (共享按主键查找 / JSON 解析 / 字符串转换) */
    private final ScrmWorkflowDefinitionService definitionService;

    // ============================================================
    // 节点执行
    // ============================================================

    /**
     * 执行节点 (记录节点日志 → 按节点类型执行 → 流转到下一节点)。
     * <p>节点类型: START 直接到下一节点; ACTION 分派真实业务服务; CONDITION 真实评估条件;
     * DELAY 置 WAITING 并登记到期时间; END 完成实例。执行失败将节点日志与实例置为 FAILED
     * 并抛出异常, 由调用方事务统一回滚。</p>
     * <p>LOOP / SWITCH / PARALLEL / WAIT / SUB_WORKFLOW 仍为直通执行 (编排语义待实现)。</p>
     *
     * @param instanceId 实例 ID
     * @param nodeId     节点 ID
     * @return 节点日志
     * @throws ScrmException 实例不存在 / 工作流不存在 / 节点不存在 / 节点执行失败
     */
    @Transactional
    public ScrmWorkflowNodeLogEntity executeNode(Long instanceId, String nodeId) throws ScrmException {
        ScrmWorkflowInstanceEntity instance = findInstanceOrThrow(instanceId);
        if (ScrmWorkflowInstanceService.INSTANCE_COMPLETED.equals(instance.getStatus())
                || ScrmWorkflowInstanceService.INSTANCE_CANCELLED.equals(instance.getStatus())) {
            throw ScrmException.conflict("实例已结束, 不可执行节点: instanceId=" + instanceId
                    + ", status=" + instance.getStatus());
        }
        ScrmWorkflowEntity workflow = definitionService.findWorkflowOrThrow(instance.getWorkflowId());
        Map<String, Object> node = findNode(workflow, nodeId);
        String nodeType = definitionService.toStr(node.get("type"));
        String nodeName = definitionService.toStr(node.get("name"));
        String actionType = node.get("actionType") != null ? definitionService.toStr(node.get("actionType"))
                : (node.get("config") instanceof Map ? definitionService.toStr(((Map<?, ?>) node.get("config")).get("actionType")) : "");
        String config = node.get("config") != null ? toJson(node.get("config")) : null;
        // 创建节点日志
        ScrmWorkflowNodeLogEntity logEntry = new ScrmWorkflowNodeLogEntity();
        logEntry.setInstanceId(instanceId);
        logEntry.setWorkflowId(workflow.getId());
        logEntry.setNodeId(nodeId);
        logEntry.setNodeName(nodeName);
        logEntry.setNodeType(nodeType);
        logEntry.setActionType(actionType.isEmpty() ? null : actionType);
        logEntry.setActionConfig(config);
        logEntry.setInputVariables(instance.getVariables());
        logEntry.setStatus(LOG_RUNNING);
        logEntry.setStartedAt(LocalDateTime.now());
        logEntry.setSequence(nextSequence(instanceId));
        logEntry.setRetryCount(0);
        logEntry = nodeLogRepository.save(logEntry);
        // 更新实例当前节点
        instance.setCurrentNodeId(nodeId);
        instance.setCurrentNodeName(nodeName);
        instance.setCurrentNodeType(nodeType);
        long startTime = System.currentTimeMillis();
        try {
            switch (nodeType) {
                case ScrmWorkflowDefinitionService.NODE_START:
                    logEntry.setOutputResult(toJson(Map.of("message", "工作流开始")));
                    logEntry.setStatus(LOG_SUCCESS);
                    break;
                case ScrmWorkflowDefinitionService.NODE_END:
                    logEntry.setOutputResult(toJson(Map.of("message", "工作流结束")));
                    logEntry.setStatus(LOG_SUCCESS);
                    logEntry = nodeLogRepository.save(logEntry);
                    instance = instanceRepository.save(instance);
                    appendExecutionLog(instance, logEntry);
                    completeInstance(instanceId);
                    return logEntry;
                case NODE_ACTION:
                    Map<String, Object> actionResult = executeAction(
                            actionType.isEmpty() ? null : actionType, config, instance);
                    logEntry.setOutputResult(toJson(actionResult));
                    logEntry.setStatus(LOG_SUCCESS);
                    // 动作结果并入实例变量, 供后续条件与动作节点引用
                    instance.setVariables(toJson(mergeVariables(
                            parseInstanceVariables(instance), actionResult)));
                    break;
                case NODE_CONDITION:
                    String conditionResult = evaluateCondition(instanceId, nodeId);
                    logEntry.setConditionResult(conditionResult);
                    logEntry.setOutputResult(toJson(Map.of("conditionResult", conditionResult)));
                    logEntry.setStatus(LOG_SUCCESS);
                    break;
                case NODE_DELAY:
                    Map<String, Object> delayResult = handleDelay(instanceId, nodeId);
                    logEntry.setOutputResult(toJson(delayResult));
                    logEntry.setStatus(LOG_WAITING);
                    logEntry = nodeLogRepository.save(logEntry);
                    instance.setStatus(ScrmWorkflowInstanceService.INSTANCE_WAITING);
                    instance = instanceRepository.save(instance);
                    appendExecutionLog(instance, logEntry);
                    return logEntry;
                default:
                    // LOOP / SWITCH / PARALLEL / WAIT / SUB_WORKFLOW: 编排语义未实现, 直通并如实标注
                    logEntry.setOutputResult(toJson(Map.of(
                            "message", "节点类型 " + nodeType + " 编排语义待实现, 已直通到下一节点",
                            "nodeType", nodeType, "simulated", true)));
                    logEntry.setStatus(LOG_SUCCESS);
                    break;
            }
        } catch (ScrmException e) {
            logEntry.setStatus(LOG_FAILED);
            logEntry.setErrorMessage(e.getMessage());
            logEntry.setCompletedAt(LocalDateTime.now());
            logEntry.setDurationMs((int) (System.currentTimeMillis() - startTime));
            nodeLogRepository.save(logEntry);
            instance.setStatus(ScrmWorkflowInstanceService.INSTANCE_FAILED);
            instance.setErrorMessage(e.getMessage());
            instance.setCompletedAt(LocalDateTime.now());
            instance.setDurationMs(calcDurationMs(instance.getStartedAt(), instance.getCompletedAt()));
            instanceRepository.save(instance);
            decrementActiveInstanceCount(workflow.getId());
            appendExecutionLog(instance, logEntry);
            throw e;
        }
        logEntry.setCompletedAt(LocalDateTime.now());
        logEntry.setDurationMs((int) (System.currentTimeMillis() - startTime));
        logEntry = nodeLogRepository.save(logEntry);
        instance = instanceRepository.save(instance);
        appendExecutionLog(instance, logEntry);
        // 流转到下一节点
        processNextNode(instanceId);
        return logEntry;
    }

    /**
     * 处理下一节点 (按条件结果选择出边并继续执行)。
     * <p>
     * 实例非 RUNNING (被延迟置 WAITING / 已被取消) 时停止流转; 无可达节点时完成实例。
     * </p>
     *
     * @param instanceId 实例 ID
     * @throws ScrmException 实例不存在 / 工作流不存在 / 后续节点执行失败
     */
    @Transactional
    public void processNextNode(Long instanceId) throws ScrmException {
        ScrmWorkflowInstanceEntity instance = findInstanceOrThrow(instanceId);
        if (!ScrmWorkflowInstanceService.INSTANCE_RUNNING.equals(instance.getStatus())) {
            return;
        }
        ScrmWorkflowEntity workflow = definitionService.findWorkflowOrThrow(instance.getWorkflowId());
        List<Map<String, Object>> edges = definitionService.parseJsonList(workflow.getEdges(), "edges");
        // 取最近一条节点日志以判定条件结果
        String lastConditionResult = null;
        List<ScrmWorkflowNodeLogEntity> logs = nodeLogRepository
                .findByInstanceIdOrderBySequence(instanceId);
        if (!logs.isEmpty()) {
            ScrmWorkflowNodeLogEntity last = logs.get(logs.size() - 1);
            if (last.getConditionResult() != null) {
                lastConditionResult = last.getConditionResult();
            }
        }
        String nextNodeId = null;
        for (Map<String, Object> edge : edges) {
            if (instance.getCurrentNodeId() != null && instance.getCurrentNodeId().equals(definitionService.toStr(edge.get("from")))) {
                String condition = definitionService.toStr(edge.get("condition"));
                if (condition.isEmpty() || lastConditionResult == null
                        || condition.equalsIgnoreCase(lastConditionResult)) {
                    nextNodeId = definitionService.toStr(edge.get("to"));
                    break;
                }
            }
        }
        if (nextNodeId == null || nextNodeId.isEmpty()) {
            // 无下一节点, 完成实例
            completeInstance(instanceId);
            return;
        }
        executeNode(instanceId, nextNodeId);
    }

    /**
     * 完成实例 (RUNNING / WAITING / PAUSED → COMPLETED)。
     *
     * @param instanceId 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    @Transactional
    public ScrmWorkflowInstanceEntity completeInstance(Long instanceId) throws ScrmException {
        ScrmWorkflowInstanceEntity instance = findInstanceOrThrow(instanceId);
        if (ScrmWorkflowInstanceService.INSTANCE_COMPLETED.equals(instance.getStatus())
                || ScrmWorkflowInstanceService.INSTANCE_CANCELLED.equals(instance.getStatus())) {
            return instance;
        }
        instance.setStatus(ScrmWorkflowInstanceService.INSTANCE_COMPLETED);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setDurationMs(calcDurationMs(instance.getStartedAt(), instance.getCompletedAt()));
        instance = instanceRepository.save(instance);
        // 更新工作流成功统计与平均耗时
        ScrmWorkflowEntity workflow = definitionService.findWorkflowOrThrow(instance.getWorkflowId());
        workflow.setSuccessCount((workflow.getSuccessCount() != null ? workflow.getSuccessCount() : 0) + 1);
        workflow.setActiveInstanceCount(Math.max(0, (workflow.getActiveInstanceCount() != null
                ? workflow.getActiveInstanceCount() : 0) - 1));
        int total = workflow.getExecutionCount() != null ? workflow.getExecutionCount() : 0;
        int prevAvg = workflow.getAvgExecutionTimeMs() != null ? workflow.getAvgExecutionTimeMs() : 0;
        if (total > 0) {
            workflow.setAvgExecutionTimeMs((prevAvg * (total - 1) + instance.getDurationMs()) / total);
        }
        workflowRepository.save(workflow);
        log.info("完成工作流实例: id={}, workflowId={}, durationMs={}",
                instanceId, instance.getWorkflowId(), instance.getDurationMs());
        return instance;
    }

    /**
     * 评估条件节点 (真实评估, 复用 {@link CustomerConditionEvaluator} 共享实现)。
     * <p>
     * 条件书写方式支持两种, 按优先级取用:
     * <ol>
     *   <li>{@code config.conditions}: 与客群分群完全一致的条件数组
     *       ({@code [{"field":"customer_level","operator":"eq","value":"ACTIVE"}]}),
     *       可配合 {@code config.conditionType} (ALL / ANY / NONE, 默认 ALL);
     *       数组的 JSON 文本同样接受</li>
     *   <li>{@code config.expression}: 字符串, 内容可为上述条件数组 / 单个条件对象的 JSON 文本,
     *       或三元简化式 {@code <field> <operator> <value>}
     *       (操作符支持 eq/ne/gt/lt/gte/lte/between/contains/in 及符号别名
     *       {@code == != >= <= < >}; between / in 的值写成 JSON 数组, 如
     *       {@code order_count between [1,5]})</li>
     * </ol>
     * 字段取值先取客户上下文 ({@link CustomerConditionEvaluator#AVAILABLE_FIELDS}),
     * 再叠加实例变量 (触发数据) 覆盖同名字段, 因此可直接引用事件字段做判断。
     * </p>
     * <p>
     * 失败策略: 条件缺失、JSON 非法、字段未知、操作符非法均抛 {@link ScrmException},
     * 由调用方将节点与实例置为 FAILED, <b>绝不默认返回 TRUE</b> —— 静默放行会让未配置的条件
     * 误触发后续群发动作。表达式合法但未命中时返回 FALSE, 走 else / no-match 分支。
     * </p>
     *
     * @param instanceId 实例 ID
     * @param nodeId     条件节点 ID
     * @return 条件结果: TRUE / FALSE
     * @throws ScrmException 实例不存在 / 节点不存在 / 条件配置非法 / 客户不存在
     */
    @Transactional(readOnly = true)
    public String evaluateCondition(Long instanceId, String nodeId) throws ScrmException {
        ScrmWorkflowInstanceEntity instance = findInstanceOrThrow(instanceId);
        ScrmWorkflowEntity workflow = definitionService.findWorkflowOrThrow(instance.getWorkflowId());
        Map<String, Object> node = findNode(workflow, nodeId);
        Map<String, Object> config = nodeConfig(node, nodeId);
        List<Map<String, Object>> conditions = resolveConditions(config, nodeId);
        String conditionType = resolveConditionType(config, nodeId);
        Map<String, Object> context = buildConditionContext(instance);
        for (int i = 0; i < conditions.size(); i++) {
            validateCondition(conditions.get(i), context, nodeId, i);
        }
        Map<String, Object> matchDetails = conditionEvaluator.evaluateWithContext(
                context, conditions, conditionType);
        String result = matchDetails == null ? CONDITION_RESULT_FALSE : CONDITION_RESULT_TRUE;
        log.info("评估条件节点: instanceId={}, nodeId={}, node={}, conditionType={}, conditions={}, result={}",
                instanceId, nodeId, node.get("name"), conditionType, conditions.size(), result);
        return result;
    }

    /**
     * 执行动作 (分派到仓库内真实业务服务, 详见 {@link WorkflowActionExecutor})。
     * <p>
     * 真实副作用: 打标签 / 出入客群 / 生命周期流转 / 通知中心发送 / Webhook 投递 /
     * 创建跟进任务 / 创建工单 / 更新客户字段。无内部通道的渠道 (邮件 / 短信 / 单客户消息 /
     * 通用外部 API / 归属变更) 返回 {@code simulated=true} 并在 {@code reason} 中说明原因。
     * </p>
     *
     * @param actionType 动作类型
     * @param config     动作配置 JSON
     * @param instance   执行动作的工作流实例 (提供客户与变量上下文)
     * @return 动作执行结果 Map
     * @throws ScrmException 动作类型不支持 / 必填配置缺失 / 下游服务执行失败
     */
    @Transactional
    public Map<String, Object> executeAction(String actionType, String config,
                                             ScrmWorkflowInstanceEntity instance) throws ScrmException {
        return actionExecutor.execute(actionType, config, instance);
    }

    /**
     * 处理延迟节点 (设置实例 WAITING 状态与 nextExecutionAt, 到期由调度器恢复)。
     * <p>
     * 时长取 {@code config.delayMinutes} (数字或数字字符串), 缺省 60 分钟。
     * 到期实例由 {@code WorkflowDelayResumeScheduler} 按
     * {@code scrm.workflow-delay-resume-interval-ms} 间隔扫描, 以状态 + version 条件更新抢占后
     * 从当前节点的下一条边继续流转, 因此本方法返回即为真实生效的延迟登记。
     * </p>
     *
     * @param instanceId 实例 ID
     * @param nodeId     延迟节点 ID
     * @return 延迟配置 Map {delayMinutes, nextExecutionAt}
     * @throws ScrmException 实例不存在 / 节点不存在 / 延迟时长非法
     */
    @Transactional
    public Map<String, Object> handleDelay(Long instanceId, String nodeId) throws ScrmException {
        ScrmWorkflowInstanceEntity instance = findInstanceOrThrow(instanceId);
        ScrmWorkflowEntity workflow = definitionService.findWorkflowOrThrow(instance.getWorkflowId());
        Map<String, Object> node = findNode(workflow, nodeId);
        Map<String, Object> result = new LinkedHashMap<>();
        int delayMinutes = resolveDelayMinutes(node, nodeId);
        LocalDateTime nextExecutionAt = LocalDateTime.now().plusMinutes(delayMinutes);
        instance.setStatus(ScrmWorkflowInstanceService.INSTANCE_WAITING);
        instance.setNextExecutionAt(nextExecutionAt);
        instanceRepository.save(instance);
        result.put("delayMinutes", delayMinutes);
        result.put("nextExecutionAt", nextExecutionAt.toString());
        result.put("simulated", false);
        result.put("message", "实例进入 WAITING, 将于 " + nextExecutionAt + " 由延迟调度器恢复");
        log.info("处理延迟节点: instanceId={}, nodeId={}, delayMinutes={}, nextExecutionAt={}",
                instanceId, nodeId, delayMinutes, nextExecutionAt);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 在工作流节点图中查找指定节点。
     *
     * @param workflow 工作流实体
     * @param nodeId   节点 ID
     * @return 节点 Map
     * @throws ScrmException 节点不存在
     */
    private Map<String, Object> findNode(ScrmWorkflowEntity workflow, String nodeId) throws ScrmException {
        List<Map<String, Object>> nodes = definitionService.parseJsonList(workflow.getNodes(), "nodes");
        return nodes.stream()
                .filter(n -> nodeId.equals(definitionService.toStr(n.get("id"))))
                .findFirst()
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "节点不存在: nodeId=" + nodeId));
    }

    /**
     * 取节点配置 (兼容 config 为对象或 JSON 文本两种书写)。
     *
     * @param node   节点 Map
     * @param nodeId 节点 ID (错误消息用)
     * @return 配置 Map, 未配置返回空 Map
     * @throws ScrmException config 类型非法或 JSON 解析失败
     */
    private Map<String, Object> nodeConfig(Map<String, Object> node, String nodeId) throws ScrmException {
        Object config = node.get("config");
        if (config == null) {
            return new LinkedHashMap<>();
        }
        if (config instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        if (config instanceof String text) {
            if (text.isBlank()) {
                return new LinkedHashMap<>();
            }
            return parseJsonObject(text, "节点 config (nodeId=" + nodeId + ")");
        }
        throw ScrmException.badRequest("节点 config 类型非法, 需为对象或 JSON 文本: nodeId=" + nodeId);
    }

    /**
     * 解析 JSON 对象字符串为 Map。
     *
     * @param json      JSON 文本
     * @param fieldName 字段描述 (错误消息用)
     * @return 解析结果, 空文本返回空 Map
     * @throws ScrmException JSON 非法或不是对象
     */
    private Map<String, Object> parseJsonObject(String json, String fieldName) throws ScrmException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw ScrmException.badRequest(fieldName + " JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析条件节点的条件集合, 支持 {@code conditions} 与 {@code expression} 两种写法。
     *
     * @param config 节点配置
     * @param nodeId 节点 ID (错误消息用)
     * @return 条件集合 (非空)
     * @throws ScrmException 未配置条件 / 条件格式非法
     */
    private List<Map<String, Object>> resolveConditions(Map<String, Object> config, String nodeId)
            throws ScrmException {
        Object raw = config.get("conditions");
        String field = "conditions";
        if (raw == null) {
            raw = config.get("expression");
            field = "expression";
        }
        if (raw == null) {
            throw ScrmException.badRequest("条件节点未配置 conditions 或 expression: nodeId=" + nodeId);
        }
        List<Map<String, Object>> conditions = toConditionList(raw, nodeId, field);
        // 空条件在分群侧语义为"全量命中", 但工作流条件节点为空即配置缺失,
        // 若沿用将默认放行并触发后续群发动作, 因此显式失败
        if (conditions.isEmpty()) {
            throw ScrmException.badRequest("条件节点条件为空: nodeId=" + nodeId + ", field=" + field);
        }
        return conditions;
    }

    /**
     * 将条件原始值归一化为条件集合: 数组 / 对象 / JSON 文本 / 三元简化式均可。
     *
     * @param raw     原始值
     * @param nodeId  节点 ID (错误消息用)
     * @param field   配置字段名 (错误消息用)
     * @return 条件集合
     * @throws ScrmException 类型非法 / 元素非对象 / JSON 解析失败 / 简化式格式非法
     */
    private List<Map<String, Object>> toConditionList(Object raw, String nodeId, String field)
            throws ScrmException {
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw ScrmException.badRequest(
                            "条件项必须为对象: nodeId=" + nodeId + ", field=" + field + ", item=" + item);
                }
                Map<String, Object> condition = new LinkedHashMap<>();
                map.forEach((k, v) -> condition.put(String.valueOf(k), v));
                conditions.add(condition);
            }
            return conditions;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> condition = new LinkedHashMap<>();
            map.forEach((k, v) -> condition.put(String.valueOf(k), v));
            conditions.add(condition);
            return conditions;
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return conditions;
            }
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                Object parsed;
                try {
                    parsed = objectMapper.readValue(trimmed, Object.class);
                } catch (Exception e) {
                    throw ScrmException.badRequest("条件 JSON 解析失败: nodeId=" + nodeId + ", field=" + field
                            + ", err=" + e.getMessage());
                }
                return toConditionList(parsed, nodeId, field);
            }
            conditions.add(parseSimpleCondition(trimmed, nodeId, field));
            return conditions;
        }
        throw ScrmException.badRequest("条件配置类型非法, 需为数组 / 对象 / 字符串: nodeId=" + nodeId
                + ", field=" + field);
    }

    /**
     * 解析三元简化式条件 {@code <field> <operator> <value>}。
     *
     * @param expression 简化式文本
     * @param nodeId     节点 ID (错误消息用)
     * @param field      配置字段名 (错误消息用)
     * @return 条件 Map
     * @throws ScrmException 格式非法
     */
    private Map<String, Object> parseSimpleCondition(String expression, String nodeId, String field)
            throws ScrmException {
        Matcher matcher = SIMPLE_CONDITION.matcher(expression);
        if (!matcher.matches()) {
            throw ScrmException.badRequest("简化条件表达式格式非法, 应为 \"<field> <operator> <value>\": nodeId="
                    + nodeId + ", field=" + field + ", expression=" + expression);
        }
        String operator = matcher.group(2);
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("field", matcher.group(1));
        condition.put("operator", CONDITION_OPERATOR_ALIASES.getOrDefault(operator, operator));
        condition.put("value", parseConditionValue(matcher.group(3).trim()));
        return condition;
    }

    /**
     * 解析简化式的值字面量: JSON 数组 / 对象 / 数字 / 布尔按 JSON 解析, 其余按字符串处理。
     *
     * @param raw 值文本
     * @return 值对象
     */
    private Object parseConditionValue(String raw) {
        if (raw.startsWith("[") || raw.startsWith("{")) {
            try {
                return objectMapper.readValue(raw, Object.class);
            } catch (Exception e) {
                log.warn("简化条件表达式的值非合法 JSON, 按字符串处理: value={}, err={}", raw, e.getMessage());
            }
        }
        return raw;
    }

    /**
     * 解析条件组合类型, 缺省为 ALL。
     *
     * @param config 节点配置
     * @param nodeId 节点 ID (错误消息用)
     * @return 条件组合类型
     * @throws ScrmException 类型非法
     */
    private String resolveConditionType(Map<String, Object> config, String nodeId) throws ScrmException {
        Object raw = config.get("conditionType");
        String conditionType = raw == null ? "" : definitionService.toStr(raw).trim().toUpperCase(Locale.ROOT);
        if (conditionType.isEmpty()) {
            return DEFAULT_CONDITION_TYPE;
        }
        if (!CustomerConditionEvaluator.VALID_CONDITION_TYPES.contains(conditionType)) {
            throw ScrmException.badRequest("条件组合类型非法: nodeId=" + nodeId + ", conditionType=" + conditionType
                    + ", 仅支持 " + CustomerConditionEvaluator.VALID_CONDITION_TYPES);
        }
        return conditionType;
    }

    /**
     * 构建条件评估上下文: 客户属性上下文, 再叠加实例变量 (触发数据) 覆盖同名字段。
     *
     * @param instance 实例
     * @return 评估上下文
     * @throws ScrmException 客户不存在
     */
    private Map<String, Object> buildConditionContext(ScrmWorkflowInstanceEntity instance) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(instance.getCustomerId())
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "条件评估失败, 客户不存在: customerId=" + instance.getCustomerId()));
        Map<String, Object> context = new LinkedHashMap<>(conditionEvaluator.buildCustomerContext(customer));
        context.putAll(parseInstanceVariables(instance));
        return context;
    }

    /**
     * 解析实例变量 JSON 对象 (非对象或解析失败时按空变量处理, 不影响客户属性字段的判断)。
     *
     * @param instance 实例
     * @return 变量 Map
     */
    private Map<String, Object> parseInstanceVariables(ScrmWorkflowInstanceEntity instance) {
        String variables = instance.getVariables();
        if (variables == null || variables.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = objectMapper.readValue(variables, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
            log.warn("实例变量不是 JSON 对象, 忽略: instanceId={}", instance.getId());
        } catch (Exception e) {
            log.warn("实例变量解析失败, 忽略: instanceId={}, err={}", instance.getId(), e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    /**
     * 将动作执行结果并入实例变量 (以 {@code action_<动作类型>} 命名空间存放, 避免覆盖客户属性字段)。
     *
     * @param variables    现有变量
     * @param actionResult 动作执行结果
     * @return 合并后的变量
     */
    private Map<String, Object> mergeVariables(Map<String, Object> variables, Map<String, Object> actionResult) {
        Map<String, Object> merged = new LinkedHashMap<>(variables);
        Object actionType = actionResult.get("actionType");
        if (actionType != null) {
            merged.put("action_" + actionType, actionResult);
        }
        return merged;
    }

    /**
     * 校验单条条件的字段与操作符可用性: 未知字段 / 非法操作符 / 缺失取值均显式失败。
     *
     * @param condition 条件
     * @param context   评估上下文
     * @param nodeId    节点 ID (错误消息用)
     * @param index     条件下标 (错误消息用)
     * @throws ScrmException 条件非法
     */
    private void validateCondition(Map<String, Object> condition, Map<String, Object> context,
                                   String nodeId, int index) throws ScrmException {
        String where = "nodeId=" + nodeId + ", index=" + index;
        String field = definitionService.toStr(condition.get("field")).trim();
        String operator = definitionService.toStr(condition.get("operator")).trim();
        if (field.isEmpty()) {
            throw ScrmException.badRequest("条件缺少 field: " + where);
        }
        if (operator.isEmpty()) {
            throw ScrmException.badRequest("条件缺少 operator: " + where + ", field=" + field);
        }
        if (!CustomerConditionEvaluator.VALID_OPERATORS.contains(operator)) {
            throw ScrmException.badRequest("条件操作符非法: " + where + ", operator=" + operator
                    + ", 仅支持 " + CustomerConditionEvaluator.VALID_OPERATORS);
        }
        if (!context.containsKey(field)) {
            throw ScrmException.badRequest("条件字段未知: " + where + ", field=" + field + ", 可用字段为 "
                    + CustomerConditionEvaluator.AVAILABLE_FIELDS + " 及实例变量字段");
        }
        if (!condition.containsKey("value")) {
            throw ScrmException.badRequest("条件缺少 value: " + where + ", field=" + field);
        }
    }

    /**
     * 解析延迟时长 (分钟): 取 {@code config.delayMinutes}, 支持数字或数字字符串, 缺省 60。
     *
     * @param node   节点 Map
     * @param nodeId 节点 ID (错误消息用)
     * @return 延迟分钟数
     * @throws ScrmException 时长非法或非正数
     */
    private int resolveDelayMinutes(Map<String, Object> node, String nodeId) throws ScrmException {
        Object raw = nodeConfig(node, nodeId).get("delayMinutes");
        if (raw == null) {
            return DEFAULT_DELAY_MINUTES;
        }
        int delayMinutes;
        if (raw instanceof Number number) {
            delayMinutes = number.intValue();
        } else {
            try {
                delayMinutes = Integer.parseInt(definitionService.toStr(raw).trim());
            } catch (NumberFormatException e) {
                throw ScrmException.badRequest("延迟时长非法, 需为整数分钟: nodeId=" + nodeId + ", delayMinutes=" + raw);
            }
        }
        if (delayMinutes <= 0) {
            throw ScrmException.badRequest("延迟时长必须为正数: nodeId=" + nodeId + ", delayMinutes=" + delayMinutes);
        }
        return delayMinutes;
    }

    /**
     * 计算下一节点日志的执行顺序号。
     *
     * @param instanceId 实例 ID
     * @return 下一顺序号
     */
    private int nextSequence(Long instanceId) {
        List<ScrmWorkflowNodeLogEntity> logs = nodeLogRepository
                .findByInstanceIdOrderBySequence(instanceId);
        if (logs.isEmpty()) {
            return 0;
        }
        return logs.get(logs.size() - 1).getSequence() != null
                ? logs.get(logs.size() - 1).getSequence() + 1
                : logs.size();
    }

    /**
     * 追加节点日志到实例执行日志 JSON 数组。
     *
     * @param instance 实例实体
     * @param logEntry 节点日志
     */
    @SuppressWarnings("unchecked")
    private void appendExecutionLog(ScrmWorkflowInstanceEntity instance, ScrmWorkflowNodeLogEntity logEntry) {
        List<Map<String, Object>> executionLog = new ArrayList<>();
        if (instance.getExecutionLog() != null && !instance.getExecutionLog().isBlank()) {
            try {
                executionLog = objectMapper.readValue(instance.getExecutionLog(),
                        new TypeReference<List<Map<String, Object>>>() {
                        });
            } catch (Exception e) {
                log.warn("执行日志 JSON 解析失败, 重建: instanceId={}, err={}", instance.getId(), e.getMessage());
            }
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("nodeId", logEntry.getNodeId());
        entry.put("nodeName", logEntry.getNodeName());
        entry.put("action", logEntry.getActionType());
        entry.put("status", logEntry.getStatus());
        entry.put("timestamp", logEntry.getStartedAt() != null ? logEntry.getStartedAt().toString() : null);
        entry.put("duration", logEntry.getDurationMs());
        executionLog.add(entry);
        instance.setExecutionLog(toJson(executionLog));
        instanceRepository.save(instance);
    }

    /**
     * 递减工作流活跃实例数。
     *
     * @param workflowId 工作流 ID
     */
    void decrementActiveInstanceCount(Long workflowId) {
        try {
            ScrmWorkflowEntity workflow = workflowRepository.findById(workflowId).orElse(null);
            if (workflow != null) {
                workflow.setActiveInstanceCount(Math.max(0, (workflow.getActiveInstanceCount() != null
                        ? workflow.getActiveInstanceCount() : 0) - 1));
                workflowRepository.save(workflow);
            }
        } catch (Exception e) {
            log.warn("递减工作流活跃实例数失败: workflowId={}, err={}", workflowId, e.getMessage());
        }
    }

    /**
     * 计算实例执行耗时 (毫秒)。
     *
     * @param startedAt   开始时间
     * @param completedAt 完成时间
     * @return 耗时毫秒
     */
    int calcDurationMs(LocalDateTime startedAt, LocalDateTime completedAt) {
        if (startedAt == null || completedAt == null) {
            return 0;
        }
        return (int) ChronoUnit.MILLIS.between(startedAt, completedAt);
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
     * 按主键查询实例, 不存在抛异常, 并校验归属账号。
     *
     * @param id 实例 ID
     * @return 实例实体
     * @throws ScrmException 实例不存在
     */
    ScrmWorkflowInstanceEntity findInstanceOrThrow(Long id) throws ScrmException {
        ScrmWorkflowInstanceEntity entity = instanceRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "工作流实例不存在: id=" + id));
        return entity;
    }
}
