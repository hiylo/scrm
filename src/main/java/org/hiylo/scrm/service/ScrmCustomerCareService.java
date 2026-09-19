/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerCareService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmCareExecuteDto;
import org.hiylo.scrm.dto.ScrmCareRecordDto;
import org.hiylo.scrm.dto.ScrmCareRuleDto;
import org.hiylo.scrm.dto.ScrmCareTaskDto;
import org.hiylo.scrm.dto.ScrmFestivalDto;
import org.hiylo.scrm.entity.ScrmCareRecordEntity;
import org.hiylo.scrm.entity.ScrmCareRuleEntity;
import org.hiylo.scrm.entity.ScrmCareTaskEntity;
import org.hiylo.scrm.entity.ScrmFestivalEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户关怀服务 (门面)。
 * <p>
 * 承载客户关怀全流程能力: 关怀规则增删改查与启用禁用, 关怀任务管理与执行 (含批量执行与取消),
 * 关怀任务调度生成 (按规则 / 每日 / 生日 / 节日), 节日配置管理, 关怀记录管理与关怀效果统计。
 * 实际实现按子域拆分至兄弟服务: {@link ScrmCustomerCareRuleService} (关怀规则)、
 * {@link ScrmCustomerCareTaskService} (关怀任务与调度)、{@link ScrmCustomerCareFestivalService}
 * (节日管理)、{@link ScrmCustomerCareRecordService} (关怀记录与统计)。本门面仅做方法委托,
 * 全部 public 方法签名保持不变。
 * </p>
 * <p>
 * 调度生成流程: 加载启用规则 (priority ASC) → 解析触发条件 (daysBefore/time/segment) →
 * 按关怀类型匹配客户 → 去重创建任务 → 增量更新规则执行统计。生日 / 纪念日 / 会员到期类型
 * 因客户实体暂缺对应字段, 当前为模拟实现 (待完善); 活跃度提醒类型基于客户 lastInteractionAt
 * 真实计算; 节日类型由节日兄弟服务按节日配置生成。任务执行 ({@code executeTask}) 为模拟实现:
 * 按动作类型记录执行回执, 写入关怀记录, 不实际触发外部消息 / 优惠券 / 礼品系统。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmCustomerCareService {

    /** 关怀规则兄弟服务 */
    private final ScrmCustomerCareRuleService ruleService;

    /** 关怀任务与调度兄弟服务 */
    private final ScrmCustomerCareTaskService taskService;

    /** 节日管理兄弟服务 */
    private final ScrmCustomerCareFestivalService festivalService;

    /** 关怀记录与统计兄弟服务 */
    private final ScrmCustomerCareRecordService recordService;

    // ============================================================
    // 规则管理
    // ============================================================

    /**
     * 创建关怀规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    public ScrmCareRuleEntity createRule(ScrmCareRuleDto dto) throws ScrmException {
        return ruleService.createRule(dto);
    }

    /**
     * 更新关怀规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    public ScrmCareRuleEntity updateRule(Long id, ScrmCareRuleDto dto) throws ScrmException {
        return ruleService.updateRule(id, dto);
    }

    /**
     * 删除关怀规则。
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
    public ScrmCareRuleEntity getRule(Long id) throws ScrmException {
        return ruleService.getRule(id);
    }

    /**
     * 分页查询规则, 支持按关怀类型、启用状态与关键字过滤。
     *
     * @param careType 关怀类型过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  规则名称关键字模糊匹配（可空）
     * @param pageable 分页参数
     * @return 规则分页结果 (按 priority ASC, createTime DESC)
     */
    public Page<ScrmCareRuleEntity> listRules(String careType, Boolean enabled, String keyword, Pageable pageable) {
        return ruleService.listRules(careType, enabled, keyword, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmCareRuleEntity enableRule(Long id) throws ScrmException {
        return ruleService.enableRule(id);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmCareRuleEntity disableRule(Long id) throws ScrmException {
        return ruleService.disableRule(id);
    }

    // ============================================================
    // 任务管理
    // ============================================================

    /**
     * 创建关怀任务 (手动创建, ruleId 可空)。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 客户不存在
     */
    public ScrmCareTaskEntity createTask(ScrmCareTaskDto dto) throws ScrmException {
        return taskService.createTask(dto);
    }

    /**
     * 更新关怀任务（字段非空才覆盖）。
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    public ScrmCareTaskEntity updateTask(Long id, ScrmCareTaskDto dto) throws ScrmException {
        return taskService.updateTask(id, dto);
    }

    /**
     * 删除关怀任务。
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
    public ScrmCareTaskEntity getTask(Long id) throws ScrmException {
        return taskService.getTask(id);
    }

    /**
     * 分页查询任务, 支持按关怀类型、状态、负责人、客户与时间范围过滤。
     *
     * @param careType   关怀类型过滤（可空）
     * @param status     任务状态过滤（可空）
     * @param assigneeId 负责人 ID 过滤（可空）
     * @param customerId 客户 ID 过滤（可空）
     * @param startDate  关怀日期起始 (含, 可空)
     * @param endDate    关怀日期截止 (含, 可空)
     * @param pageable   分页参数
     * @return 任务分页结果 (按 scheduledAt DESC)
     */
    public Page<ScrmCareTaskEntity> listTasks(String careType, String status, String assigneeId,
                                               Long customerId, LocalDate startDate, LocalDate endDate,
                                               Pageable pageable) {
        return taskService.listTasks(careType, status, assigneeId, customerId, startDate, endDate, pageable);
    }

    /**
     * 执行关怀任务 (模拟实现)。
     *
     * @param executeDto 执行请求 (taskId + result + response)
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法 / 参数非法
     */
    public ScrmCareTaskEntity executeTask(ScrmCareExecuteDto executeDto) throws ScrmException {
        return taskService.executeTask(executeDto);
    }

    /**
     * 取消关怀任务。
     *
     * @param id     任务 ID
     * @param reason 取消原因
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    public ScrmCareTaskEntity cancelTask(Long id, String reason) throws ScrmException {
        return taskService.cancelTask(id, reason);
    }

    /**
     * 批量执行关怀任务。
     *
     * @param taskIds 任务 ID 列表
     * @return 执行结果: {total, success, failed}
     */
    public Map<String, Integer> batchExecuteTasks(List<Long> taskIds) {
        return taskService.batchExecuteTasks(taskIds);
    }

    // ============================================================
    // 调度生成
    // ============================================================

    /**
     * 根据规则生成某日关怀任务。
     *
     * @param ruleId 规则 ID
     * @param date   关怀日期
     * @return 创建的任务数
     * @throws ScrmException 规则不存在
     */
    public int generateTasksFromDate(Long ruleId, LocalDate date) throws ScrmException {
        return taskService.generateTasksFromDate(ruleId, date);
    }

    /**
     * 生成某日所有关怀任务 (模拟实现)。
     *
     * @param date 关怀日期
     * @return 生成结果: {totalCreated, rulesProcessed, failed}
     */
    public Map<String, Integer> generateDailyTasks(LocalDate date) {
        return taskService.generateDailyTasks(date);
    }

    /**
     * 生成生日关怀任务。
     *
     * @param date 关怀日期
     * @return 创建的任务数
     */
    public int generateBirthdayTasks(LocalDate date) {
        return taskService.generateBirthdayTasks(date);
    }

    /**
     * 生成节日关怀任务。
     *
     * @param festivalId 节日 ID
     * @param date       关怀日期
     * @return 创建的任务数
     * @throws ScrmException 节日不存在
     */
    public int generateFestivalTasks(Long festivalId, LocalDate date) throws ScrmException {
        return festivalService.generateFestivalTasks(festivalId, date);
    }

    // ============================================================
    // 节日配置
    // ============================================================

    /**
     * 创建节日配置。
     *
     * @param dto 节日参数
     * @return 创建后的节日
     * @throws ScrmException 参数非法
     */
    public ScrmFestivalEntity createFestival(ScrmFestivalDto dto) throws ScrmException {
        return festivalService.createFestival(dto);
    }

    /**
     * 更新节日配置（字段非空才覆盖）。
     *
     * @param id  节日 ID
     * @param dto 节日参数
     * @return 更新后的节日
     * @throws ScrmException 节日不存在 / 参数非法
     */
    public ScrmFestivalEntity updateFestival(Long id, ScrmFestivalDto dto) throws ScrmException {
        return festivalService.updateFestival(id, dto);
    }

    /**
     * 删除节日配置。
     *
     * @param id 节日 ID
     * @throws ScrmException 节日不存在
     */
    public void deleteFestival(Long id) throws ScrmException {
        festivalService.deleteFestival(id);
    }

    /**
     * 查询节日详情。
     *
     * @param id 节日 ID
     * @return 节日实体
     * @throws ScrmException 节日不存在
     */
    public ScrmFestivalEntity getFestival(Long id) throws ScrmException {
        return festivalService.getFestival(id);
    }

    /**
     * 分页查询节日, 支持按节日类型与启用状态过滤。
     *
     * @param festivalType 节日类型过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param pageable     分页参数
     * @return 节日分页结果 (按 festivalDate ASC)
     */
    public Page<ScrmFestivalEntity> listFestivals(String festivalType, Boolean enabled, Pageable pageable) {
        return festivalService.listFestivals(festivalType, enabled, pageable);
    }

    /**
     * 启用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    public ScrmFestivalEntity enableFestival(Long id) throws ScrmException {
        return festivalService.enableFestival(id);
    }

    /**
     * 禁用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    public ScrmFestivalEntity disableFestival(Long id) throws ScrmException {
        return festivalService.disableFestival(id);
    }

    /**
     * 查询即将到来的节日。
     *
     * @param days 未来天数
     * @return 即将到来的节日列表
     */
    public List<ScrmFestivalEntity> getUpcomingFestivals(int days) {
        return festivalService.getUpcomingFestivals(days);
    }

    // ============================================================
    // 关怀记录
    // ============================================================

    /**
     * 创建关怀记录。
     *
     * @param dto 关怀记录参数
     * @return 创建后的关怀记录
     * @throws ScrmException 参数非法 / 客户不存在
     */
    public ScrmCareRecordEntity createRecord(ScrmCareRecordDto dto) throws ScrmException {
        return recordService.createRecord(dto);
    }

    /**
     * 分页查询关怀记录, 支持按客户、关怀类型、关怀结果与时间范围过滤。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param careType   关怀类型过滤（可空）
     * @param careResult 关怀结果过滤（可空）
     * @param startTime  执行时间起始 (含, 可空)
     * @param endTime    执行时间截止 (含, 可空)
     * @param pageable   分页参数
     * @return 关怀记录分页结果 (按 executedAt DESC)
     */
    public Page<ScrmCareRecordEntity> getRecords(Long customerId, String careType, String careResult,
                                                  LocalDateTime startTime, LocalDateTime endTime,
                                                  Pageable pageable) {
        return recordService.getRecords(customerId, careType, careResult, startTime, endTime, pageable);
    }

    /**
     * 查询关怀记录详情。
     *
     * @param id 记录 ID
     * @return 关怀记录实体
     * @throws ScrmException 记录不存在
     */
    public ScrmCareRecordEntity getRecord(Long id) throws ScrmException {
        return recordService.getRecord(id);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 关怀统计: 关怀数、成功率、客户回应率、各类型分布、各结果分布。
     *
     * @param startTime 执行时间起始 (含, 可空)
     * @param endTime   执行时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getCareStats(LocalDateTime startTime, LocalDateTime endTime) {
        return recordService.getCareStats(startTime, endTime);
    }

    /**
     * 客户关怀历史: 按客户查询全部关怀记录 (按执行时间倒序)。
     *
     * @param customerId 客户 ID
     * @return 关怀记录列表
     */
    public List<ScrmCareRecordEntity> getCustomerCareHistory(Long customerId) {
        return recordService.getCustomerCareHistory(customerId);
    }

    /**
     * 关怀效果分析: 关怀后互动变化。
     *
     * @param startTime 执行时间起始 (含, 可空)
     * @param endTime   执行时间截止 (含, 可空)
     * @return 效果分析结果 Map
     */
    public Map<String, Object> getCareEffectiveness(LocalDateTime startTime, LocalDateTime endTime) {
        return recordService.getCareEffectiveness(startTime, endTime);
    }
}
