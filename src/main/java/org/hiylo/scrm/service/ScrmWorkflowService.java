/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkflowService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmWorkflowDto;
import org.hiylo.scrm.dto.ScrmWorkflowTriggerDto;
import org.hiylo.scrm.entity.ScrmWorkflowEntity;
import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.entity.ScrmWorkflowNodeLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销自动化工作流服务 (门面)。
 * <p>
 * 作为工作流模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmWorkflowDefinitionService} (工作流定义与版本)、{@link ScrmWorkflowInstanceService}
 * (实例执行与节点日志查询)、{@link ScrmWorkflowNodeService} (节点执行与条件/动作/延迟处理) 与
 * {@link ScrmWorkflowStatsService} (统计分析)。
 * </p>
 * <p>
 * 条件节点复用 {@link CustomerConditionEvaluator} (与客群分群同一份评估逻辑);
 * 动作节点经 {@link WorkflowActionExecutor} 分派到真实业务服务, 无内部通道的渠道显式降级为
 * {@code simulated=true} 并说明原因; 延迟节点置 WAITING 后由
 * {@code WorkflowDelayResumeScheduler} 扫描到期实例并恢复流转。
 * LOOP / SWITCH / PARALLEL / WAIT / SUB_WORKFLOW 节点类型仍为直通执行 (待实现编排语义)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmWorkflowService {

    /** 工作流定义与版本子域服务 */
    private final ScrmWorkflowDefinitionService definitionService;
    /** 实例执行与节点日志查询子域服务 */
    private final ScrmWorkflowInstanceService instanceService;
    /** 节点执行与条件/动作/延迟处理子域服务 */
    private final ScrmWorkflowNodeService nodeService;
    /** 统计分析子域服务 */
    private final ScrmWorkflowStatsService statsService;

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
    public ScrmWorkflowEntity createWorkflow(ScrmWorkflowDto dto) throws ScrmException {
        return definitionService.createWorkflow(dto);
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
    public ScrmWorkflowEntity updateWorkflow(Long id, ScrmWorkflowDto dto) throws ScrmException {
        return definitionService.updateWorkflow(id, dto);
    }

    /**
     * 删除工作流 (同时删除关联实例与节点日志)。
     *
     * @param id 工作流 ID
     * @throws ScrmException 工作流不存在
     */
    public void deleteWorkflow(Long id) throws ScrmException {
        definitionService.deleteWorkflow(id);
    }

    /**
     * 查询工作流详情。
     *
     * @param id 工作流 ID
     * @return 工作流实体
     * @throws ScrmException 工作流不存在
     */
    public ScrmWorkflowEntity getWorkflow(Long id) throws ScrmException {
        return definitionService.getWorkflow(id);
    }

    /**
     * 按编码查询工作流。
     *
     * @param code 工作流编码
     * @return 工作流实体
     * @throws ScrmException 工作流不存在
     */
    public ScrmWorkflowEntity getWorkflowByCode(String code) throws ScrmException {
        return definitionService.getWorkflowByCode(code);
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
    public Page<ScrmWorkflowEntity> listWorkflows(String workflowType, String triggerType, String status,
                                                   String keyword, Pageable pageable) {
        return definitionService.listWorkflows(workflowType, triggerType, status, keyword, pageable);
    }

    /**
     * 激活工作流 (DRAFT / PAUSED → ACTIVE)。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 状态非法
     */
    public ScrmWorkflowEntity activateWorkflow(Long id) throws ScrmException {
        return definitionService.activateWorkflow(id);
    }

    /**
     * 暂停工作流 (ACTIVE → PAUSED)。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 状态非法
     */
    public ScrmWorkflowEntity pauseWorkflow(Long id) throws ScrmException {
        return definitionService.pauseWorkflow(id);
    }

    /**
     * 归档工作流 (DRAFT / ACTIVE / PAUSED → ARCHIVED)。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在 / 状态非法
     */
    public ScrmWorkflowEntity archiveWorkflow(Long id) throws ScrmException {
        return definitionService.archiveWorkflow(id);
    }

    /**
     * 复制工作流 (生成新编码副本, 状态 DRAFT)。
     *
     * @param id 工作流 ID
     * @return 复制后的工作流
     * @throws ScrmException 工作流不存在
     */
    public ScrmWorkflowEntity copyWorkflow(Long id) throws ScrmException {
        return definitionService.copyWorkflow(id);
    }

    /**
     * 发布新版本 (versionNumber 递增, 状态置为 DRAFT 以便重新编辑激活)。
     *
     * @param id 工作流 ID
     * @return 更新后的工作流
     * @throws ScrmException 工作流不存在
     */
    public ScrmWorkflowEntity publishVersion(Long id) throws ScrmException {
        return definitionService.publishVersion(id);
    }

    /**
     * 验证工作流 (检查节点完整性 / 连接有效性)。
     *
     * @param id 工作流 ID
     * @return 验证结果 Map {valid, errors, nodeCount, edgeCount, hasEntry, hasEnd}
     * @throws ScrmException 工作流不存在
     */
    public Map<String, Object> validateWorkflow(Long id) throws ScrmException {
        return definitionService.validateWorkflow(id);
    }

    /**
     * 获取工作流图结构 (节点 + 连接 + 入口)。
     *
     * @param id 工作流 ID
     * @return 图结构 Map {workflowId, nodes, edges, entryNode}
     * @throws ScrmException 工作流不存在
     */
    public Map<String, Object> getWorkflowGraph(Long id) throws ScrmException {
        return definitionService.getWorkflowGraph(id);
    }

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
    public ScrmWorkflowInstanceEntity triggerWorkflow(ScrmWorkflowTriggerDto triggerDto) throws ScrmException {
        return instanceService.triggerWorkflow(triggerDto);
    }

    /**
     * 查询实例详情。
     *
     * @param id 实例 ID
     * @return 实例实体
     * @throws ScrmException 实例不存在
     */
    public ScrmWorkflowInstanceEntity getInstance(Long id) throws ScrmException {
        return instanceService.getInstance(id);
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
    public Page<ScrmWorkflowInstanceEntity> listInstances(Long workflowId, Long customerId, String status,
                                                           LocalDateTime startTime, LocalDateTime endTime,
                                                           Pageable pageable) {
        return instanceService.listInstances(workflowId, customerId, status, startTime, endTime, pageable);
    }

    /**
     * 取消实例 (RUNNING / PAUSED / WAITING → CANCELLED)。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    public ScrmWorkflowInstanceEntity cancelInstance(Long id) throws ScrmException {
        return instanceService.cancelInstance(id);
    }

    /**
     * 暂停实例 (RUNNING / WAITING → PAUSED)。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    public ScrmWorkflowInstanceEntity pauseInstance(Long id) throws ScrmException {
        return instanceService.pauseInstance(id);
    }

    /**
     * 恢复实例 (PAUSED → RUNNING, 并从当前节点继续流转)。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 节点执行失败
     */
    public ScrmWorkflowInstanceEntity resumeInstance(Long id) throws ScrmException {
        return instanceService.resumeInstance(id);
    }

    /**
     * 重试失败实例 (FAILED → RUNNING, retryCount 递增, 从当前节点重新执行)。
     *
     * @param id 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法 / 节点执行失败
     */
    public ScrmWorkflowInstanceEntity retryInstance(Long id) throws ScrmException {
        return instanceService.retryInstance(id);
    }

    /**
     * 获取工作流的活跃实例 (RUNNING / WAITING / PAUSED)。
     *
     * @param workflowId 工作流 ID
     * @return 活跃实例列表
     * @throws ScrmException 工作流不存在
     */
    public List<ScrmWorkflowInstanceEntity> getActiveInstances(Long workflowId) throws ScrmException {
        return instanceService.getActiveInstances(workflowId);
    }

    /**
     * 获取客户的工作流实例。
     *
     * @param customerId 客户 ID
     * @return 实例列表
     */
    public List<ScrmWorkflowInstanceEntity> getCustomerInstances(Long customerId) {
        return instanceService.getCustomerInstances(customerId);
    }

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
    public ScrmWorkflowNodeLogEntity executeNode(Long instanceId, String nodeId) throws ScrmException {
        return nodeService.executeNode(instanceId, nodeId);
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
    public void processNextNode(Long instanceId) throws ScrmException {
        nodeService.processNextNode(instanceId);
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
    public String evaluateCondition(Long instanceId, String nodeId) throws ScrmException {
        return nodeService.evaluateCondition(instanceId, nodeId);
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
    public Map<String, Object> executeAction(String actionType, String config,
                                             ScrmWorkflowInstanceEntity instance) throws ScrmException {
        return nodeService.executeAction(actionType, config, instance);
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
    public Map<String, Object> handleDelay(Long instanceId, String nodeId) throws ScrmException {
        return nodeService.handleDelay(instanceId, nodeId);
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
    public ScrmWorkflowInstanceEntity resumeDelayedInstance(Long instanceId,
                                                            Long expectedVersion) throws ScrmException {
        return instanceService.resumeDelayedInstance(instanceId, expectedVersion);
    }

    /**
     * 完成实例 (RUNNING / WAITING / PAUSED → COMPLETED)。
     *
     * @param instanceId 实例 ID
     * @return 更新后的实例
     * @throws ScrmException 实例不存在 / 状态非法
     */
    public ScrmWorkflowInstanceEntity completeInstance(Long instanceId) throws ScrmException {
        return nodeService.completeInstance(instanceId);
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
    public List<ScrmWorkflowNodeLogEntity> getNodeLogs(Long instanceId) throws ScrmException {
        return instanceService.getNodeLogs(instanceId);
    }

    /**
     * 查询节点日志详情。
     *
     * @param id 节点日志 ID
     * @return 节点日志实体
     * @throws ScrmException 节点日志不存在
     */
    public ScrmWorkflowNodeLogEntity getNodeLog(Long id) throws ScrmException {
        return instanceService.getNodeLog(id);
    }

    /**
     * 获取实例执行时间线 (节点日志按执行顺序, 含节点与状态信息)。
     *
     * @param instanceId 实例 ID
     * @return 时间线 Map {instanceId, status, timeline:[{...节点日志}]}
     * @throws ScrmException 实例不存在
     */
    public Map<String, Object> getInstanceTimeline(Long instanceId) throws ScrmException {
        return instanceService.getInstanceTimeline(instanceId);
    }

    /**
     * 分页查询工作流的失败节点列表。
     *
     * @param workflowId 工作流 ID
     * @param pageable   分页参数
     * @return 失败节点日志分页结果
     * @throws ScrmException 工作流不存在
     */
    public Page<ScrmWorkflowNodeLogEntity> getFailedNodes(Long workflowId,
            Pageable pageable) throws ScrmException {
        return instanceService.getFailedNodes(workflowId, pageable);
    }

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 工作流统计: 总数 / 活跃数 / 执行次数 / 成功率 / 平均时长。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getWorkflowStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getWorkflowStats(startTime, endTime);
    }

    /**
     * 实例统计: 各状态数 / 平均时长 / 转化率 (完成率)。
     *
     * @param workflowId 工作流 ID
     * @return 统计结果 Map
     * @throws ScrmException 工作流不存在
     */
    public Map<String, Object> getInstanceStats(Long workflowId) throws ScrmException {
        return statsService.getInstanceStats(workflowId);
    }

    /**
     * 节点性能: 各节点成功率 / 平均耗时。
     *
     * @param workflowId 工作流 ID
     * @return 节点性能列表
     * @throws ScrmException 工作流不存在
     */
    public List<Map<String, Object>> getNodePerformance(Long workflowId) throws ScrmException {
        return statsService.getNodePerformance(workflowId);
    }

    /**
     * 工作流趋势: 最近 N 天每日执行实例数。
     *
     * @param days 天数
     * @return 趋势列表 [{date, count}]
     */
    public List<Map<String, Object>> getWorkflowTrend(int days) {
        return statsService.getWorkflowTrend(days);
    }

    /**
     * 转化漏斗: 各节点通过率 (基于节点日志)。
     *
     * @param workflowId 工作流 ID
     * @return 漏斗 Map {workflowId, funnel:[{nodeId, nodeName, nodeType, totalCount, successCount, passRate}]}
     * @throws ScrmException 工作流不存在
     */
    public Map<String, Object> getConversionFunnel(Long workflowId) throws ScrmException {
        return statsService.getConversionFunnel(workflowId);
    }
}