/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmInspectionExecuteDto;
import org.hiylo.scrm.dto.ScrmQualityInspectionResultDto;
import org.hiylo.scrm.dto.ScrmQualityInspectionRuleDto;
import org.hiylo.scrm.dto.ScrmQualityInspectionTaskDto;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmQualityInspectionRuleEntity;
import org.hiylo.scrm.entity.ScrmQualityInspectionTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 智能质检/会话质检服务门面。
 * <p>
 * 承载质检规则的增删改查、质检任务的批量执行、单会话即时质检与质检统计排名能力的统一入口。
 * 具体实现按子域拆分并委托给 {@link ScrmQualityInspectionRuleService} (规则管理)、
 * {@link ScrmQualityInspectionTaskService} (任务与执行) 与 {@link ScrmQualityInspectionResultService}
 * (结果与统计)。所有写操作写入当前用户归属账号实现数据隔离。
 * </p>
 * <p>
 * 质检评估流程: 加载会话消息 → 逐规则评估 (关键词/正则/会话时长/响应时长/AI 评估) →
 * 加权计算总分 → 判断是否通过 → 持久化质检结果。AI_EVALUATE 通过可插拔
 * {@link org.hiylo.scrm.service.evaluator.QualityAiEvaluator} 接口实现, 默认返回 80 分,
 * 对接大模型时新建 @Primary 实现即可替换。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmQualityInspectionService {

    /** 质检规则管理服务 */
    private final ScrmQualityInspectionRuleService ruleService;

    /** 质检任务与执行服务 */
    private final ScrmQualityInspectionTaskService taskService;

    /** 质检结果与统计服务 */
    private final ScrmQualityInspectionResultService resultService;

    // ============================================================
    // 规则管理
    // ============================================================

    /**
     * 创建质检规则。
     * <p>校验参数合法性后写入归属账号 ID 持久化, enabled/passCondition/scoreWeight 缺省时填默认值。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    public ScrmQualityInspectionRuleEntity createRule(ScrmQualityInspectionRuleDto dto) throws ScrmException {
        return ruleService.createRule(dto);
    }

    /**
     * 更新质检规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    public ScrmQualityInspectionRuleEntity updateRule(Long id,
            ScrmQualityInspectionRuleDto dto) throws ScrmException {
        return ruleService.updateRule(id, dto);
    }

    /**
     * 删除质检规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    public void deleteRule(Long id) throws ScrmException {
        ruleService.deleteRule(id);
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    public ScrmQualityInspectionRuleEntity getRule(Long id) throws ScrmException {
        return ruleService.getRule(id);
    }

    /**
     * 分页查询规则, 支持按类别/规则类型/启用状态/关键字过滤。
     *
     * @param category 质检类别过滤（可空）
     * @param ruleType 规则类型过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  关键字过滤（按规则名称模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    public Page<ScrmQualityInspectionRuleEntity> listRules(String category, String ruleType,
                                                            Boolean enabled, String keyword, Pageable pageable) {
        return ruleService.listRules(category, ruleType, enabled, keyword, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    public void enableRule(Long id) throws ScrmException {
        ruleService.enableRule(id);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    public void disableRule(Long id) throws ScrmException {
        ruleService.disableRule(id);
    }

    // ============================================================
    // 任务管理
    // ============================================================

    /**
     * 创建质检任务。
     * <p>仅创建任务记录 (状态 PENDING), 不立即执行, 由 {@link #executeTask(Long)} 触发执行。</p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    public ScrmQualityInspectionTaskEntity createTask(ScrmQualityInspectionTaskDto dto) throws ScrmException {
        return taskService.createTask(dto);
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    public ScrmQualityInspectionTaskEntity getTask(Long id) throws ScrmException {
        return taskService.getTask(id);
    }

    /**
     * 分页查询任务, 支持按状态过滤。
     *
     * @param status   任务状态过滤（可空）
     * @param pageable 分页参数
     * @return 任务分页结果
     */
    public Page<ScrmQualityInspectionTaskEntity> listTasks(String status, Pageable pageable) {
        return taskService.listTasks(status, pageable);
    }

    /**
     * 执行质检任务: 遍历范围内会话 → 逐个质检 → 汇总统计。
     * <p>任务状态流转 PENDING → RUNNING → COMPLETED/FAILED, 单会话质检异常跳过不阻断整体。</p>
     *
     * @param taskId 任务 ID
     * @return 执行后的任务
     * @throws ScrmException 任务不存在 / 任务状态非法
     */
    public ScrmQualityInspectionTaskEntity executeTask(Long taskId) throws ScrmException {
        return taskService.executeTask(taskId);
    }

    /**
     * 查询任务进度。
     *
     * @param taskId 任务 ID
     * @return 进度信息 (total/inspected/passed/failed/averageScore/status)
     * @throws ScrmException 任务不存在
     */
    public Map<String, Object> getTaskProgress(Long taskId) throws ScrmException {
        return taskService.getTaskProgress(taskId);
    }

    // ============================================================
    // 会话质检
    // ============================================================

    /**
     * 质检单个会话: 加载会话消息 → 逐规则评估 → 计算总分 → 生成结果。
     *
     * @param executeDto 质检执行参数 (conversationId + ruleIds)
     * @return 质检结果
     * @throws ScrmException 会话不存在 / 规则不存在
     */
    public ScrmQualityInspectionResultDto inspectConversation(
            ScrmInspectionExecuteDto executeDto) throws ScrmException {
        return taskService.inspectConversation(executeDto);
    }

    /**
     * 质检某销售的所有会话 (按时间范围过滤)。
     *
     * @param assigneeId 被质检人 ID (账号 ID 字符串)
     * @param startTime  起始时间（含）
     * @param endTime    截止时间（含）
     * @param ruleIds    规则 ID 列表 JSON 数组
     * @return 质检结果列表
     * @throws ScrmException 参数非法
     */
    public List<ScrmQualityInspectionResultDto> inspectByAssignee(String assigneeId, LocalDateTime startTime,
                                                                   LocalDateTime endTime,
                                                                   String ruleIds) throws ScrmException {
        return taskService.inspectByAssignee(assigneeId, startTime, endTime, ruleIds);
    }

    // ============================================================
    // 评估引擎
    // ============================================================

    /**
     * 评估单条规则, 返回该规则的质检结果明细。
     * <p>按规则类型分发: KEYWORD_MATCH→关键词匹配 / REGEX→正则 / DURATION→会话时长 /
     * RESPONSE_TIME→平均响应时长 / AI_EVALUATE→AI 评估 (可插拔 QualityAiEvaluator, 默认 80 分)。</p>
     *
     * @param rule     质检规则
     * @param messages 会话消息列表 (按时间升序)
     * @return 规则质检结果明细 Map (ruleId/ruleName/category/ruleType/score/passed/detail/scoreWeight)
     */
    public Map<String, Object> evaluateRule(ScrmQualityInspectionRuleEntity rule,
            List<ScrmConversationMessageEntity> messages) {
        return resultService.evaluateRule(rule, messages);
    }

    /**
     * 加权计算总分 (0-100)。
     * <p>totalScore = Σ(score_i × weight_i) / Σ(weight_i), 无规则时返回 0。</p>
     *
     * @param ruleResults 各规则质检结果明细列表
     * @return 加权总分
     */
    public double calculateTotalScore(List<Map<String, Object>> ruleResults) {
        return resultService.calculateTotalScore(ruleResults);
    }

    /**
     * 判断是否通过。
     * <p>passCondition 格式: GTE:N (总分≥N 通过) / LTE:N (总分≤N 通过) / CONTAINS/NOT_CONTAINS (默认按 80 分及格)。</p>
     *
     * @param totalScore    总分
     * @param passCondition 通过条件
     * @return 是否通过
     */
    public boolean checkPassed(double totalScore, String passCondition) {
        return resultService.checkPassed(totalScore, passCondition);
    }

    // ============================================================
    // 结果查询
    // ============================================================

    /**
     * 查询质检结果详情。
     *
     * @param id 结果 ID
     * @return 质检结果
     * @throws ScrmException 结果不存在
     */
    public ScrmQualityInspectionResultDto getResult(Long id) throws ScrmException {
        return resultService.getResult(id);
    }

    /**
     * 分页查询质检结果, 支持多条件过滤。
     *
     * @param taskId         任务 ID 过滤（可空）
     * @param conversationId 会话 ID 过滤（可空）
     * @param assigneeId     被质检人 ID 过滤（可空）
     * @param passed         是否通过过滤（可空）
     * @param minValueScore  总分下限（可空）
     * @param maxValueScore  总分上限（可空）
     * @param startTime       质检时间下限（可空）
     * @param endTime         质检时间上限（可空）
     * @param pageable        分页参数
     * @return 质检结果分页
     */
    public Page<ScrmQualityInspectionResultDto> listResults(Long taskId, Long conversationId, String assigneeId,
                                                             Boolean passed, Double minValueScore, Double maxValueScore,
                                                              LocalDateTime startTime,
                                                              LocalDateTime endTime, Pageable pageable) {
        return resultService.listResults(taskId, conversationId, assigneeId, passed,
                minValueScore, maxValueScore, startTime, endTime, pageable);
    }

    /**
     * 查询某销售时间范围内的质检结果。
     *
     * @param assigneeId 被质检人 ID
     * @param startTime  起始时间（含）
     * @param endTime    截止时间（含）
     * @return 质检结果列表
     */
    public List<ScrmQualityInspectionResultDto> getResultsByAssignee(String assigneeId,
            LocalDateTime startTime, LocalDateTime endTime) {
        return resultService.getResultsByAssignee(assigneeId, startTime, endTime);
    }

    // ============================================================
    // 统计与排名
    // ============================================================

    /**
     * 质检统计: 总质检数 / 通过率 / 平均分 / 各类别得分。
     *
     * @param startTime 起始时间（含）
     * @param endTime   截止时间（含）
     * @return 统计信息
     */
    public Map<String, Object> getInspectionStats(LocalDateTime startTime, LocalDateTime endTime) {
        return resultService.getInspectionStats(startTime, endTime);
    }

    /**
     * 销售质检排名 (按平均分降序)。
     *
     * @param startTime 起始时间（含）
     * @param endTime   截止时间（含）
     * @param pageable  分页参数
     * @return 排名分页结果
     */
    public Page<Map<String, Object>> getAssigneeRanking(LocalDateTime startTime,
            LocalDateTime endTime, Pageable pageable) {
        return resultService.getAssigneeRanking(startTime, endTime, pageable);
    }
}
