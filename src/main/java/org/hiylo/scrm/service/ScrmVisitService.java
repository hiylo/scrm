/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmVisitCompleteDto;
import org.hiylo.scrm.dto.ScrmVisitPlanDto;
import org.hiylo.scrm.dto.ScrmVisitRescheduleDto;
import org.hiylo.scrm.dto.ScrmVisitTaskDto;
import org.hiylo.scrm.dto.ScrmVisitTemplateDto;
import org.hiylo.scrm.entity.ScrmVisitPlanEntity;
import org.hiylo.scrm.entity.ScrmVisitTaskEntity;
import org.hiylo.scrm.entity.ScrmVisitTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户回访服务 (门面)。
 * <p>
 * 承载客户定期回访的核心能力: 回访计划管理 (CRUD + 状态流转 + 任务生成 + 统计),
 * 回访任务管理 (CRUD + 分配 + 开始 + 完成 + 取消 + 改期 + 今日/逾期/按负责人/按客户查询 +
 * 批量分配), 回访模板管理 (CRUD + 启停 + 复制 + 使用计数), 提醒 (单发 + 批发 + 待发查询),
 * 统计 (回访概览 / 计划统计 / 负责人统计 / 客户历史 / 趋势 / 结果分布)。
 * 所有写操作写入当前用户归属账号实现数据隔离。
 * </p>
 * <p>
 * 本类为门面, 所有方法委托给子域兄弟服务:
 * {@link ScrmVisitPlanService} (计划) / {@link ScrmVisitTaskService} (任务) /
 * {@link ScrmVisitTemplateService} (模板) / {@link ScrmVisitStatsService} (统计)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmVisitService {

    /** 回访计划子域服务 */
    private final ScrmVisitPlanService planService;
    /** 回访任务子域服务 */
    private final ScrmVisitTaskService taskService;
    /** 回访模板子域服务 */
    private final ScrmVisitTemplateService templateService;
    /** 回访统计子域服务 */
    private final ScrmVisitStatsService statsService;

    // ============================================================
    // 计划管理
    // ============================================================

    /**
     * 创建回访计划。
     *
     * @param dto 计划参数
     * @return 创建后的计划
     * @throws ScrmException 参数非法
     */
    public ScrmVisitPlanEntity createPlan(ScrmVisitPlanDto dto) throws ScrmException {
        return planService.createPlan(dto);
    }

    /**
     * 更新回访计划 (字段非空才覆盖)。
     *
     * @param id  计划 ID
     * @param dto 计划参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 参数非法
     */
    public ScrmVisitPlanEntity updatePlan(Long id, ScrmVisitPlanDto dto) throws ScrmException {
        return planService.updatePlan(id, dto);
    }

    /**
     * 删除回访计划。
     *
     * @param id 计划 ID
     * @throws ScrmException 计划不存在
     */
    public void deletePlan(Long id) throws ScrmException {
        planService.deletePlan(id);
    }

    /**
     * 查询计划详情。
     *
     * @param id 计划 ID
     * @return 计划实体
     * @throws ScrmException 计划不存在
     */
    public ScrmVisitPlanEntity getPlan(Long id) throws ScrmException {
        return planService.getPlan(id);
    }

    /**
     * 按计划编码查询。
     *
     * @param code 计划编码
     * @return 计划实体
     * @throws ScrmException 计划不存在
     */
    public ScrmVisitPlanEntity getPlanByCode(String code) throws ScrmException {
        return planService.getPlanByCode(code);
    }

    /**
     * 分页查询计划列表。
     *
     * @param planType 计划类型过滤（可空）
     * @param status   状态过滤（可空）
     * @param keyword  计划名称/编码关键字模糊匹配（可空）
     * @param pageable 分页参数
     * @return 计划分页结果 (按 createTime DESC)
     */
    public Page<ScrmVisitPlanEntity> listPlans(String planType, String status, String keyword, Pageable pageable) {
        return planService.listPlans(planType, status, keyword, pageable);
    }

    /**
     * 激活计划 (任意状态 → ACTIVE)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    public ScrmVisitPlanEntity activatePlan(Long id) throws ScrmException {
        return planService.activatePlan(id);
    }

    /**
     * 暂停计划 (ACTIVE → PAUSED)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    public ScrmVisitPlanEntity pausePlan(Long id) throws ScrmException {
        return planService.pausePlan(id);
    }

    /**
     * 完成计划 (非终态 → COMPLETED)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    public ScrmVisitPlanEntity completePlan(Long id) throws ScrmException {
        return planService.completePlan(id);
    }

    /**
     * 根据计划生成回访任务。
     *
     * @param planId 计划 ID
     * @return 生成的任务列表
     * @throws ScrmException 计划不存在 / 状态非法
     */
    public List<ScrmVisitTaskEntity> generateTasks(Long planId) throws ScrmException {
        return planService.generateTasks(planId);
    }

    /**
     * 更新计划统计指标 (任务数/完成率/满意度/成功率)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在
     */
    public ScrmVisitPlanEntity updatePlanStats(Long id) throws ScrmException {
        return planService.updatePlanStats(id);
    }

    /**
     * 查询客户相关的计划 (作为目标 customerId 或通过任务关联)。
     *
     * @param customerId 客户 ID
     * @return 计划列表
     */
    public List<ScrmVisitPlanEntity> getPlansByCustomer(Long customerId) {
        return planService.getPlansByCustomer(customerId);
    }

    // ============================================================
    // 任务管理
    // ============================================================

    /**
     * 创建回访任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    public ScrmVisitTaskEntity createTask(ScrmVisitTaskDto dto) throws ScrmException {
        return taskService.createTask(dto);
    }

    /**
     * 更新回访任务 (字段非空才覆盖)。
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    public ScrmVisitTaskEntity updateTask(Long id, ScrmVisitTaskDto dto) throws ScrmException {
        return taskService.updateTask(id, dto);
    }

    /**
     * 删除回访任务。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    public void deleteTask(Long id) throws ScrmException {
        taskService.deleteTask(id);
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    public ScrmVisitTaskEntity getTask(Long id) throws ScrmException {
        return taskService.getTask(id);
    }

    /**
     * 按任务编号查询。
     *
     * @param taskNo 任务编号
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    public ScrmVisitTaskEntity getTaskByNo(String taskNo) throws ScrmException {
        return taskService.getTaskByNo(taskNo);
    }

    /**
     * 分页查询任务列表, 支持多条件过滤。
     *
     * @param planId      计划 ID 过滤（可空）
     * @param customerId  客户 ID 过滤（可空）
     * @param visitType   回访类型过滤（可空）
     * @param visitMethod 回访方式过滤（可空）
     * @param status      状态过滤（可空）
     * @param assignedTo  负责人过滤（可空）
     * @param startDate   计划日期起始 (含, 可空)
     * @param endDate     计划日期截止 (含, 可空)
     * @param keyword     任务编号/客户名称关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 任务分页结果 (按 createTime DESC)
     */
    public Page<ScrmVisitTaskEntity> listTasks(Long planId, Long customerId, String visitType,
                                                String visitMethod, String status, String assignedTo,
                                                LocalDate startDate, LocalDate endDate, String keyword,
                                                Pageable pageable) {
        return taskService.listTasks(planId, customerId, visitType,
                visitMethod, status, assignedTo, startDate, endDate, keyword, pageable);
    }

    /**
     * 分配任务给指定负责人 (PENDING → ASSIGNED)。
     *
     * @param id         任务 ID
     * @param assigneeId 负责人 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    public ScrmVisitTaskEntity assignTask(Long id, String assigneeId) throws ScrmException {
        return taskService.assignTask(id, assigneeId);
    }

    /**
     * 开始回访 (PENDING/ASSIGNED → IN_PROGRESS)。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    public ScrmVisitTaskEntity startTask(Long id) throws ScrmException {
        return taskService.startTask(id);
    }

    /**
     * 完成回访: 记录结果 → 满意度 → 生成行动项 → 更新统计。
     *
     * @param dto 完成参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    public ScrmVisitTaskEntity completeTask(ScrmVisitCompleteDto dto) throws ScrmException {
        return taskService.completeTask(dto);
    }

    /**
     * 取消任务 (非终态 → CANCELLED)。
     *
     * @param id     任务 ID
     * @param reason 取消原因
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    public ScrmVisitTaskEntity cancelTask(Long id, String reason) throws ScrmException {
        return taskService.cancelTask(id, reason);
    }

    /**
     * 改期任务 (非终态 → RESCHEDULED, 更新计划日期, 累加改期次数)。
     *
     * @param dto 改期参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法 / 日期非法
     */
    public ScrmVisitTaskEntity rescheduleTask(ScrmVisitRescheduleDto dto) throws ScrmException {
        return taskService.rescheduleTask(dto);
    }

    /**
     * 分页查询逾期任务 (scheduledDate < today 且非终态)。
     *
     * @param pageable 分页参数
     * @return 任务分页结果 (按 scheduledDate ASC)
     */
    public Page<ScrmVisitTaskEntity> getOverdueTasks(Pageable pageable) {
        return taskService.getOverdueTasks(pageable);
    }

    /**
     * 查询指定负责人的今日任务。
     *
     * @param assigneeId 负责人 ID (可空, 为空则查询全部今日任务)
     * @param pageable   分页参数
     * @return 任务分页结果 (按 scheduledTime ASC)
     */
    public Page<ScrmVisitTaskEntity> getTodayTasks(String assigneeId, Pageable pageable) {
        return taskService.getTodayTasks(assigneeId, pageable);
    }

    /**
     * 按负责人查询任务。
     *
     * @param assigneeId 负责人 ID
     * @param status     状态过滤（可空）
     * @param pageable   分页参数
     * @return 任务分页结果 (按 createTime DESC)
     */
    public Page<ScrmVisitTaskEntity> getTasksByAssignee(String assigneeId, String status, Pageable pageable) {
        return taskService.getTasksByAssignee(assigneeId, status, pageable);
    }

    /**
     * 按客户查询任务。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 任务分页结果 (按 scheduledDate DESC)
     */
    public Page<ScrmVisitTaskEntity> getTasksByCustomer(Long customerId, Pageable pageable) {
        return taskService.getTasksByCustomer(customerId, pageable);
    }

    /**
     * 批量分配任务给指定负责人。
     *
     * @param taskIds   任务 ID 列表
     * @param assigneeId 负责人 ID
     * @return 已分配的任务列表
     * @throws ScrmException 负责人 ID 非法
     */
    public List<ScrmVisitTaskEntity> batchAssignTasks(List<Long> taskIds, String assigneeId) throws ScrmException {
        return taskService.batchAssignTasks(taskIds, assigneeId);
    }

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建回访模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    public ScrmVisitTemplateEntity createTemplate(ScrmVisitTemplateDto dto) throws ScrmException {
        return templateService.createTemplate(dto);
    }

    /**
     * 更新回访模板 (字段非空才覆盖)。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法
     */
    public ScrmVisitTemplateEntity updateTemplate(Long id, ScrmVisitTemplateDto dto) throws ScrmException {
        return templateService.updateTemplate(id, dto);
    }

    /**
     * 删除回访模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    public void deleteTemplate(Long id) throws ScrmException {
        templateService.deleteTemplate(id);
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    public ScrmVisitTemplateEntity getTemplate(Long id) throws ScrmException {
        return templateService.getTemplate(id);
    }

    /**
     * 按模板编码查询。
     *
     * @param code 模板编码
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    public ScrmVisitTemplateEntity getTemplateByCode(String code) throws ScrmException {
        return templateService.getTemplateByCode(code);
    }

    /**
     * 分页查询模板列表。
     *
     * @param visitType   回访类型过滤（可空）
     * @param visitMethod 回访方式过滤（可空）
     * @param enabled     启用状态过滤（可空）
     * @param pageable    分页参数
     * @return 模板分页结果 (按 createTime DESC)
     */
    public Page<ScrmVisitTemplateEntity> listTemplates(String visitType, String visitMethod,
                                                       Boolean enabled, Pageable pageable) {
        return templateService.listTemplates(visitType, visitMethod, enabled, pageable);
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmVisitTemplateEntity enableTemplate(Long id) throws ScrmException {
        return templateService.enableTemplate(id);
    }

    /**
     * 停用模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmVisitTemplateEntity disableTemplate(Long id) throws ScrmException {
        return templateService.disableTemplate(id);
    }

    /**
     * 复制模板 (创建副本, 编码使用 newCode)。
     *
     * @param id      源模板 ID
     * @param newCode 新模板编码
     * @return 复制后的模板
     * @throws ScrmException 源模板不存在 / 编码已存在
     */
    public ScrmVisitTemplateEntity copyTemplate(Long id, String newCode) throws ScrmException {
        return templateService.copyTemplate(id, newCode);
    }

    /**
     * 增长模板使用计数并更新平均满意度。
     *
     * @param id                模板 ID
     * @param satisfactionScore 本次满意度评分 (可空)
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmVisitTemplateEntity incrementUsage(Long id, Integer satisfactionScore) throws ScrmException {
        return templateService.incrementUsage(id, satisfactionScore);
    }

    // ============================================================
    // 提醒
    // ============================================================

    /**
     * 发送任务提醒 (模拟)。
     *
     * @param taskId 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在
     */
    public ScrmVisitTaskEntity sendReminder(Long taskId) throws ScrmException {
        return taskService.sendReminder(taskId);
    }

    /**
     * 批量发送提醒 (针对今日及未来 N 天内的未提醒任务)。
     *
     * @return 已发送提醒的任务列表
     */
    public List<ScrmVisitTaskEntity> batchSendReminders() {
        return taskService.batchSendReminders();
    }

    /**
     * 查询待发送提醒的任务 (今日及未来 1 天, 未提醒, 非终态)。
     *
     * @return 任务列表
     */
    public List<ScrmVisitTaskEntity> getPendingReminders() {
        return taskService.getPendingReminders();
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 回访统计概览: 总数 / 完成率 / 满意度 / 成功率 / 各类型。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getVisitStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getVisitStats(startTime, endTime);
    }

    /**
     * 计划统计: 任务数 / 完成率 / 满意度 / 成功率。
     *
     * @param planId 计划 ID
     * @return 统计结果 Map
     * @throws ScrmException 计划不存在
     */
    public Map<String, Object> getPlanStats(Long planId) throws ScrmException {
        return statsService.getPlanStats(planId);
    }

    /**
     * 负责人统计: 任务数 / 各状态 / 满意度。
     *
     * @param assigneeId 负责人 ID
     * @param startTime  开始时间 (含, 可空)
     * @param endTime    结束时间 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getAssigneeStats(String assigneeId, LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getAssigneeStats(assigneeId, startTime, endTime);
    }

    /**
     * 客户回访历史。
     *
     * @param customerId 客户 ID
     * @return 任务列表 (按 scheduledDate DESC)
     */
    public List<ScrmVisitTaskEntity> getCustomerVisitHistory(Long customerId) {
        return statsService.getCustomerVisitHistory(customerId);
    }

    /**
     * 回访趋势: 按日聚合任务数。
     *
     * @param days 天数 (从今天向前推算)
     * @return 趋势列表 [{date, count}]
     */
    public List<Map<String, Object>> getVisitTrend(int days) {
        return statsService.getVisitTrend(days);
    }

    /**
     * 满意度趋势: 按日聚合已完成任务的平均满意度。
     *
     * @param days 天数 (从今天向前推算)
     * @return 趋势列表 [{date, avgScore}]
     */
    public List<Map<String, Object>> getSatisfactionTrend(int days) {
        return statsService.getSatisfactionTrend(days);
    }

    /**
     * 回访结果分布: 按结果聚合任务数。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 分布列表 [{outcome, count}]
     */
    public List<Map<String, Object>> getOutcomeDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getOutcomeDistribution(startTime, endTime);
    }
}