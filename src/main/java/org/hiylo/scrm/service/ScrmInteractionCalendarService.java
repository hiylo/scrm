/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionCalendarService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmCalendarViewDto;
import org.hiylo.scrm.dto.ScrmInteractionCalendarDto;
import org.hiylo.scrm.dto.ScrmInteractionPlanCompleteDto;
import org.hiylo.scrm.dto.ScrmInteractionPlanDto;
import org.hiylo.scrm.dto.ScrmInteractionRescheduleDto;
import org.hiylo.scrm.entity.ScrmInteractionCalendarEntity;
import org.hiylo.scrm.entity.ScrmInteractionPlanEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户互动日历服务 (门面)。
 * <p>
 * 统一对外暴露客户互动计划与日历的全部能力, 具体实现按子域下沉到兄弟服务:
 * <ul>
 *   <li>{@link ScrmInteractionCalendarPlanService} 互动计划管理</li>
 *   <li>{@link ScrmInteractionCalendarManageService} 日历管理 (含日历视图与计划查询)</li>
 *   <li>{@link ScrmInteractionCalendarStatsService} 冲突检测与统计</li>
 * </ul>
 * </p>
 * <p>
 * 计划状态流转: PLANNED (已计划) → CONFIRMED (已确认) → IN_PROGRESS (进行中) →
 * COMPLETED (已完成) / CANCELLED (已取消) / RESCHEDULED (已改期) / NO_SHOW (未到)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmInteractionCalendarService {

    /** 互动计划管理服务 */
    private final ScrmInteractionCalendarPlanService planService;

    /** 日历管理服务 */
    private final ScrmInteractionCalendarManageService calendarManageService;

    /** 冲突检测与统计服务 */
    private final ScrmInteractionCalendarStatsService statsService;

    /**
     * 创建互动计划。
     *
     * @param dto 计划参数
     * @return 创建后的计划
     * @throws ScrmException 参数非法
     */
    public ScrmInteractionPlanEntity createPlan(ScrmInteractionPlanDto dto) throws ScrmException {
        return planService.createPlan(dto);
    }

    /**
     * 更新互动计划 (字段非空才覆盖)。
     *
     * @param id  计划 ID
     * @param dto 计划参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 参数非法
     */
    public ScrmInteractionPlanEntity updatePlan(Long id, ScrmInteractionPlanDto dto) throws ScrmException {
        return planService.updatePlan(id, dto);
    }

    /**
     * 删除互动计划。
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
    public ScrmInteractionPlanEntity getPlan(Long id) throws ScrmException {
        return planService.getPlan(id);
    }

    /**
     * 按计划编码查询。
     *
     * @param code 计划编码
     * @return 计划实体
     * @throws ScrmException 计划不存在
     */
    public ScrmInteractionPlanEntity getPlanByCode(String code) throws ScrmException {
        return planService.getPlanByCode(code);
    }

    /**
     * 分页查询计划列表, 支持多条件过滤。
     *
     * @param customerId       客户 ID 过滤（可空）
     * @param ownerId          负责人过滤（可空）
     * @param interactionType  互动类型过滤（可空）
     * @param status           状态过滤（可空）
     * @param priority         优先级过滤（可空）
     * @param startTime        计划开始时间起始 (含, 可空)
     * @param endTime          计划开始时间截止 (含, 可空)
     * @param keyword          计划名称/编码/主题关键字模糊匹配（可空）
     * @param pageable         分页参数
     * @return 计划分页结果 (按 scheduledStart DESC)
     */
    public Page<ScrmInteractionPlanEntity> listPlans(Long customerId, String ownerId, String interactionType,
                                                     String status, String priority, LocalDateTime startTime,
                                                     LocalDateTime endTime, String keyword, Pageable pageable) {
        return planService.listPlans(customerId, ownerId, interactionType, status, priority,
                startTime, endTime, keyword, pageable);
    }

    /**
     * 按客户查询计划。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 计划分页结果 (按 scheduledStart DESC)
     */
    public Page<ScrmInteractionPlanEntity> getPlansByCustomer(Long customerId, Pageable pageable) {
        return calendarManageService.getPlansByCustomer(customerId, pageable);
    }

    /**
     * 按负责人查询计划。
     *
     * @param ownerId   负责人 ID
     * @param pageable  分页参数
     * @return 计划分页结果 (按 scheduledStart DESC)
     */
    public Page<ScrmInteractionPlanEntity> getPlansByOwner(String ownerId, Pageable pageable) {
        return calendarManageService.getPlansByOwner(ownerId, pageable);
    }

    /**
     * 按日期范围查询计划。
     *
     * @param startDate 开始时间 (含)
     * @param endDate   结束时间 (含)
     * @param ownerId   负责人过滤（可空）
     * @return 计划列表 (按 scheduledStart ASC)
     */
    public List<ScrmInteractionPlanEntity> getPlansByDateRange(LocalDateTime startDate, LocalDateTime endDate,
                                                               String ownerId) {
        return calendarManageService.getPlansByDateRange(startDate, endDate, ownerId);
    }

    /**
     * 完成互动计划: 设置实际时间 → 记录结果 → 生成后续计划。
     *
     * @param dto 完成参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    public ScrmInteractionPlanEntity completePlan(ScrmInteractionPlanCompleteDto dto) throws ScrmException {
        return planService.completePlan(dto);
    }

    /**
     * 取消互动计划 (非终态 → CANCELLED)。
     *
     * @param id     计划 ID
     * @param reason 取消原因（可空）
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    public ScrmInteractionPlanEntity cancelPlan(Long id, String reason) throws ScrmException {
        return planService.cancelPlan(id, reason);
    }

    /**
     * 改期计划: 记录原时间 → 更新新时间 → 标记 RESCHEDULED。
     *
     * @param dto 改期参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法 / 日期非法
     */
    public ScrmInteractionPlanEntity reschedulePlan(ScrmInteractionRescheduleDto dto) throws ScrmException {
        return planService.reschedulePlan(dto);
    }

    /**
     * 确认计划 (任意非终态 → CONFIRMED)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    public ScrmInteractionPlanEntity confirmPlan(Long id) throws ScrmException {
        return planService.confirmPlan(id);
    }

    /**
     * 开始计划 (非终态 → IN_PROGRESS, 记录实际开始时间)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    public ScrmInteractionPlanEntity startPlan(Long id) throws ScrmException {
        return planService.startPlan(id);
    }

    /**
     * 标记未到 (非终态 → NO_SHOW)。
     *
     * @param id     计划 ID
     * @param reason 原因（可空）
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    public ScrmInteractionPlanEntity markNoShow(Long id, String reason) throws ScrmException {
        return planService.markNoShow(id, reason);
    }

    /**
     * 发送提醒 (模拟, 标记已发送)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    public ScrmInteractionPlanEntity sendReminder(Long id) throws ScrmException {
        return planService.sendReminder(id);
    }

    /**
     * 检查待提醒计划 (批量扫描): 计划开始时间在未来 N 分钟内、未提醒、非终态。
     *
     * @return 已发送提醒的计划列表
     */
    public List<ScrmInteractionPlanEntity> checkReminders() {
        return planService.checkReminders();
    }

    /**
     * 处理重复计划: 为重复类型非 NONE 的计划生成下一周期实例。
     *
     * @return 生成的下一周期计划列表
     */
    public List<ScrmInteractionPlanEntity> processRecurringPlans() {
        return planService.processRecurringPlans();
    }

    /**
     * 查询即将到来的计划 (未来 N 天内, 非终态)。
     *
     * @param ownerId 负责人 ID (可空, 为空则查询全部)
     * @param days    天数 (从现在起)
     * @return 计划列表 (按 scheduledStart ASC)
     */
    public List<ScrmInteractionPlanEntity> getUpcomingPlans(String ownerId, int days) {
        return calendarManageService.getUpcomingPlans(ownerId, days);
    }

    /**
     * 查询逾期计划 (计划开始时间已过且仍为非终态)。
     *
     * @param ownerId 负责人 ID (可空, 为空则查询全部)
     * @return 计划列表 (按 scheduledStart ASC)
     */
    public List<ScrmInteractionPlanEntity> getOverduePlans(String ownerId) {
        return calendarManageService.getOverduePlans(ownerId);
    }

    /**
     * 查询今日计划。
     *
     * @param ownerId 负责人 ID (可空, 为空则查询全部)
     * @return 计划列表 (按 scheduledStart ASC)
     */
    public List<ScrmInteractionPlanEntity> getTodayPlans(String ownerId) {
        return calendarManageService.getTodayPlans(ownerId);
    }

    /**
     * 按状态分页查询计划。
     *
     * @param status   状态
     * @param pageable 分页参数
     * @return 计划分页结果 (按 scheduledStart DESC)
     */
    public Page<ScrmInteractionPlanEntity> getPlansByStatus(String status, Pageable pageable) {
        return calendarManageService.getPlansByStatus(status, pageable);
    }

    /**
     * 复制计划 (创建副本, 使用新开始时间)。
     *
     * @param id       源计划 ID
     * @param newStart 新开始时间
     * @return 复制后的计划
     * @throws ScrmException 源计划不存在
     */
    public ScrmInteractionPlanEntity duplicatePlan(Long id, LocalDateTime newStart) throws ScrmException {
        return planService.duplicatePlan(id, newStart);
    }

    /**
     * 批量创建计划。
     *
     * @param dtos 计划参数列表
     * @return 创建后的计划列表
     * @throws ScrmException 参数非法
     */
    public List<ScrmInteractionPlanEntity> batchCreatePlans(
            List<ScrmInteractionPlanDto> dtos) throws ScrmException {
        return planService.batchCreatePlans(dtos);
    }

    /**
     * 批量取消计划。
     *
     * @param planIds 计划 ID 列表
     * @param reason  取消原因
     * @return 已取消的计划列表
     * @throws ScrmException 计划 ID 列表非法
     */
    public List<ScrmInteractionPlanEntity> batchCancel(List<Long> planIds, String reason) throws ScrmException {
        return planService.batchCancel(planIds, reason);
    }

    /**
     * 客户互动时间线 (按月聚合最近 N 个月的互动记录)。
     *
     * @param customerId 客户 ID
     * @param months     月数
     * @return 时间线列表 [{yearMonth, plans:[...]}]
     */
    public List<Map<String, Object>> getPlanTimeline(Long customerId, int months) {
        return statsService.getPlanTimeline(customerId, months);
    }

    /**
     * 客户互动频率 (按月聚合最近 N 个月的互动次数)。
     *
     * @param customerId 客户 ID
     * @param months     月数
     * @return 频率列表 [{yearMonth, count}]
     */
    public List<Map<String, Object>> getInteractionFrequency(Long customerId, int months) {
        return statsService.getInteractionFrequency(customerId, months);
    }

    /**
     * 创建互动日历。
     *
     * @param dto 日历参数
     * @return 创建后的日历
     * @throws ScrmException 参数非法
     */
    public ScrmInteractionCalendarEntity createCalendar(ScrmInteractionCalendarDto dto) throws ScrmException {
        return calendarManageService.createCalendar(dto);
    }

    /**
     * 更新互动日历 (字段非空才覆盖)。
     *
     * @param id  日历 ID
     * @param dto 日历参数
     * @return 更新后的日历
     * @throws ScrmException 日历不存在 / 参数非法
     */
    public ScrmInteractionCalendarEntity updateCalendar(
            Long id, ScrmInteractionCalendarDto dto) throws ScrmException {
        return calendarManageService.updateCalendar(id, dto);
    }

    /**
     * 删除互动日历。
     *
     * @param id 日历 ID
     * @throws ScrmException 日历不存在
     */
    public void deleteCalendar(Long id) throws ScrmException {
        calendarManageService.deleteCalendar(id);
    }

    /**
     * 查询日历详情。
     *
     * @param id 日历 ID
     * @return 日历实体
     * @throws ScrmException 日历不存在
     */
    public ScrmInteractionCalendarEntity getCalendar(Long id) throws ScrmException {
        return calendarManageService.getCalendar(id);
    }

    /**
     * 按日历编码查询。
     *
     * @param code 日历编码
     * @return 日历实体
     * @throws ScrmException 日历不存在
     */
    public ScrmInteractionCalendarEntity getCalendarByCode(String code) throws ScrmException {
        return calendarManageService.getCalendarByCode(code);
    }

    /**
     * 分页查询日历列表。
     *
     * @param ownerId      所有者过滤（可空）
     * @param calendarType 日历类型过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param pageable     分页参数
     * @return 日历分页结果 (按 createTime DESC)
     */
    public Page<ScrmInteractionCalendarEntity> listCalendars(String ownerId, String calendarType,
                                                             Boolean enabled, Pageable pageable) {
        return calendarManageService.listCalendars(ownerId, calendarType, enabled, pageable);
    }

    /**
     * 启用日历。
     *
     * @param id 日历 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在
     */
    public ScrmInteractionCalendarEntity enableCalendar(Long id) throws ScrmException {
        return calendarManageService.enableCalendar(id);
    }

    /**
     * 停用日历。
     *
     * @param id 日历 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在
     */
    public ScrmInteractionCalendarEntity disableCalendar(Long id) throws ScrmException {
        return calendarManageService.disableCalendar(id);
    }

    /**
     * 共享日历给指定用户 (追加到 sharedWith)。
     *
     * @param id     日历 ID
     * @param userId 用户 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在 / 用户 ID 非法
     */
    public ScrmInteractionCalendarEntity shareCalendar(Long id, String userId) throws ScrmException {
        return calendarManageService.shareCalendar(id, userId);
    }

    /**
     * 查询共享给指定用户的日历。
     *
     * @param userId 用户 ID
     * @return 日历列表
     */
    public List<ScrmInteractionCalendarEntity> getSharedCalendars(String userId) {
        return calendarManageService.getSharedCalendars(userId);
    }

    /**
     * 日历视图: 按日期范围聚合计划, 返回日视图结构。
     *
     * @param viewDto 视图查询参数
     * @return 日历视图 Map {startDate, endDate, ownerId, days:[{date, plans:[...]}]}
     */
    public Map<String, Object> getCalendarView(ScrmCalendarViewDto viewDto) {
        return calendarManageService.getCalendarView(viewDto);
    }

    /**
     * 月视图: 返回指定年月每天的互动计划。
     *
     * @param year    年
     * @param month   月 (1-12)
     * @param ownerId 负责人 (可空)
     * @return 月视图 Map {year, month, days:[{date, plans:[...]}]}
     */
    public Map<String, Object> getMonthView(int year, int month, String ownerId) {
        return calendarManageService.getMonthView(year, month, ownerId);
    }

    /**
     * 周视图: 返回指定周开始的 7 天互动计划。
     *
     * @param weekStart 周开始日期 (周一)
     * @param ownerId   负责人 (可空)
     * @return 周视图 Map {weekStart, days:[{date, plans:[...]}]}
     */
    public Map<String, Object> getWeekView(LocalDate weekStart, String ownerId) {
        return calendarManageService.getWeekView(weekStart, ownerId);
    }

    /**
     * 日视图: 返回指定日期的互动计划。
     *
     * @param date    日期
     * @param ownerId 负责人 (可空)
     * @return 日视图 Map {date, plans:[...]}
     */
    public Map<String, Object> getDayView(LocalDate date, String ownerId) {
        return calendarManageService.getDayView(date, ownerId);
    }

    /**
     * 议程视图: 返回未来 N 天的互动计划列表。
     *
     * @param ownerId 负责人 (可空)
     * @param days    天数
     * @return 议程视图 Map {days, plans:[...]}
     */
    public Map<String, Object> getAgendaView(String ownerId, int days) {
        return calendarManageService.getAgendaView(ownerId, days);
    }

    /**
     * 更新日历统计指标 (计划数/完成数/取消数/完成率/最近活动)。
     *
     * @param id 日历 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在
     */
    public ScrmInteractionCalendarEntity updateCalendarStats(Long id) throws ScrmException {
        return calendarManageService.updateCalendarStats(id);
    }

    /**
     * 日历摘要: 指定时间范围内的计划统计。
     *
     * @param id        日历 ID
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 摘要 Map
     * @throws ScrmException 日历不存在
     */
    public Map<String, Object> getCalendarSummary(Long id, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        return calendarManageService.getCalendarSummary(id, startTime, endTime);
    }

    /**
     * 查找空闲时间段: 基于负责人当天已有计划与工作时间, 返回可用的空闲时段。
     *
     * @param ownerId         负责人 ID
     * @param date            日期
     * @param durationMinutes 所需时长分钟
     * @return 空闲时段列表 [{start, end}]
     */
    public List<Map<String, Object>> findFreeSlots(String ownerId, LocalDate date, int durationMinutes) {
        return statsService.findFreeSlots(ownerId, date, durationMinutes);
    }

    /**
     * 检测时间冲突: 返回与指定时间区间重叠的计划列表。
     *
     * @param ownerId 负责人 ID
     * @param start   开始时间
     * @param end     结束时间
     * @return 冲突计划列表
     */
    public List<ScrmInteractionPlanEntity> detectConflicts(String ownerId, LocalDateTime start, LocalDateTime end) {
        return statsService.detectConflicts(ownerId, start, end);
    }

    /**
     * 互动统计概览: 各类型/各方式/完成率/平均时长。
     *
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getInteractionStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getInteractionStats(startTime, endTime);
    }

    /**
     * 客户互动统计: 各状态计数与互动类型分布。
     *
     * @param customerId 客户 ID
     * @return 统计结果 Map
     */
    public Map<String, Object> getCustomerInteractionStats(Long customerId) {
        return statsService.getCustomerInteractionStats(customerId);
    }

    /**
     * 负责人统计: 计划数 / 各状态 / 完成率。
     *
     * @param ownerId   负责人 ID
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getOwnerStats(String ownerId, LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getOwnerStats(ownerId, startTime, endTime);
    }

    /**
     * 互动趋势: 按月聚合最近 N 个月的计划数。
     *
     * @param months 月数
     * @return 趋势列表 [{yearMonth, count}]
     */
    public List<Map<String, Object>> getInteractionTrend(int months) {
        return statsService.getInteractionTrend(months);
    }

    /**
     * 完成率: 指定负责人在指定时间区间的已完成计划占比。
     *
     * @param ownerId   负责人 ID
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 完成率
     */
    public double getCompletionRate(String ownerId, LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getCompletionRate(ownerId, startTime, endTime);
    }

    /**
     * 未到率: 指定负责人在指定时间区间的 NO_SHOW 计划占比。
     *
     * @param ownerId   负责人 ID
     * @param startTime 开始时间 (含, 可空)
     * @param endTime   结束时间 (含, 可空)
     * @return 未到率
     */
    public double getNoShowRate(String ownerId, LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getNoShowRate(ownerId, startTime, endTime);
    }

    /**
     * 最佳互动时间段: 按小时聚合计划数, 返回互动最频繁的时段。
     *
     * @param ownerId 负责人 ID (可空, 为空则查询全部)
     * @return 时段列表 [{hour, count}] (按 count DESC)
     */
    public List<Map<String, Object>> getBestTimeSlots(String ownerId) {
        return statsService.getBestTimeSlots(ownerId);
    }

    /**
     * 互动热力图: 按日聚合最近 N 个月的计划数。
     *
     * @param ownerId 负责人 ID (可空, 为空则查询全部)
     * @param months  月数
     * @return 热力图列表 [{date, count}]
     */
    public List<Map<String, Object>> getInteractionHeatmap(String ownerId, int months) {
        return statsService.getInteractionHeatmap(ownerId, months);
    }

}