/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCalendarService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmCalendarEventDto;
import org.hiylo.scrm.dto.ScrmCalendarHolidayDto;
import org.hiylo.scrm.dto.ScrmCalendarMoveDto;
import org.hiylo.scrm.dto.ScrmCalendarRangeDto;
import org.hiylo.scrm.entity.ScrmCalendarConflictEntity;
import org.hiylo.scrm.entity.ScrmCalendarEventEntity;
import org.hiylo.scrm.entity.ScrmCalendarHolidayEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销日历服务 (门面)。
 * <p>
 * 作为营销日历模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmMarketingCalendarEventService} (营销事件)、{@link ScrmMarketingCalendarHolidayService}
 * (节假日管理)、{@link ScrmMarketingCalendarConflictService} (冲突检测) 与
 * {@link ScrmMarketingCalendarStatsService} (统计)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmMarketingCalendarService {

    /** 营销事件管理子域服务 */
    private final ScrmMarketingCalendarEventService eventService;

    /** 节假日管理子域服务 */
    private final ScrmMarketingCalendarHolidayService holidayService;

    /** 冲突检测子域服务 */
    private final ScrmMarketingCalendarConflictService conflictService;

    /** 统计子域服务 */
    private final ScrmMarketingCalendarStatsService statsService;

    // ============================================================
    // 事件管理
    // ============================================================

    /**
     * 创建日历事件。
     *
     * @param dto 事件参数
     * @return 创建后的事件
     * @throws ScrmException 参数非法
     */
    public ScrmCalendarEventEntity createEvent(ScrmCalendarEventDto dto) throws ScrmException {
        return eventService.createEvent(dto);
    }

    /**
     * 更新日历事件 (字段非空才覆盖)。
     *
     * @param id  事件 ID
     * @param dto 事件参数
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 参数非法
     */
    public ScrmCalendarEventEntity updateEvent(Long id, ScrmCalendarEventDto dto) throws ScrmException {
        return eventService.updateEvent(id, dto);
    }

    /**
     * 删除日历事件。
     *
     * @param id 事件 ID
     * @throws ScrmException 事件不存在
     */
    public void deleteEvent(Long id) throws ScrmException {
        eventService.deleteEvent(id);
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    public ScrmCalendarEventEntity getEvent(Long id) throws ScrmException {
        return eventService.getEvent(id);
    }

    /**
     * 分页查询事件, 支持按类型 / 状态 / 负责人 / 时间范围 / 关键字过滤。
     *
     * @param eventType 事件类型过滤（可空）
     * @param status    状态过滤（可空）
     * @param ownerId   负责人 ID 过滤（可空）
     * @param startDate 开始日期过滤 (事件开始日期 ≥ 此值, 可空)
     * @param endDate   结束日期过滤 (事件结束日期 ≤ 此值, 可空)
     * @param keyword   事件标题关键字模糊匹配（可空）
     * @param pageable  分页参数
     * @return 事件分页结果 (按 createTime DESC)
     */
    public Page<ScrmCalendarEventEntity> listEvents(String eventType, String status, String ownerId,
                                                     LocalDate startDate, LocalDate endDate, String keyword,
                                                     Pageable pageable) {
        return eventService.listEvents(eventType, status, ownerId, startDate, endDate, keyword, pageable);
    }

    /**
     * 按日期范围查询事件 (日历视图数据, 支持事件类型与渠道过滤)。
     * <p>返回与查询区间有交集的全部事件: 事件开始 ≤ 区间结束 且 事件结束 ≥ 区间开始。</p>
     *
     * @param rangeDto 日期范围参数
     * @return 事件列表
     * @throws ScrmException 参数非法
     */
    public List<ScrmCalendarEventEntity> getEventsByDateRange(ScrmCalendarRangeDto rangeDto) throws ScrmException {
        return eventService.getEventsByDateRange(rangeDto);
    }

    /**
     * 查询某日的事件。
     *
     * @param date 日期 (ISO 格式: yyyy-MM-dd)
     * @return 事件列表
     */
    public List<ScrmCalendarEventEntity> getEventsByDate(LocalDate date) {
        return eventService.getEventsByDate(date);
    }

    /**
     * 月视图: 查询某月的事件。
     *
     * @param year  年份 (如 2026)
     * @param month 月份 (1-12)
     * @return 事件列表
     * @throws ScrmException 月份非法
     */
    public List<ScrmCalendarEventEntity> getEventsByMonth(int year, int month) {
        return eventService.getEventsByMonth(year, month);
    }

    /**
     * 周视图: 查询某周的事件 (周一开始)。
     *
     * @param weekStart 周开始日期
     * @return 事件列表
     */
    public List<ScrmCalendarEventEntity> getEventsByWeek(LocalDate weekStart) {
        return eventService.getEventsByWeek(weekStart);
    }

    /**
     * 确认事件 (PLANNED → CONFIRMED)。
     *
     * @param id 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    public ScrmCalendarEventEntity confirmEvent(Long id) throws ScrmException {
        return eventService.confirmEvent(id);
    }

    /**
     * 开始事件 (CONFIRMED → IN_PROGRESS)。
     *
     * @param id 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    public ScrmCalendarEventEntity startEvent(Long id) throws ScrmException {
        return eventService.startEvent(id);
    }

    /**
     * 完成事件 (IN_PROGRESS → COMPLETED)。
     *
     * @param id 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    public ScrmCalendarEventEntity completeEvent(Long id) throws ScrmException {
        return eventService.completeEvent(id);
    }

    /**
     * 取消事件 (非终态 → CANCELLED)。
     *
     * @param id     事件 ID
     * @param reason 取消原因 (可空)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    public ScrmCalendarEventEntity cancelEvent(Long id, String reason) throws ScrmException {
        return eventService.cancelEvent(id, reason);
    }

    /**
     * 延期事件 (非终态 → POSTPONED, 同时更新日期)。
     *
     * @param id        事件 ID
     * @param newDates  新的日期范围 (startDate + endDate)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法 / 日期非法
     */
    public ScrmCalendarEventEntity postponeEvent(Long id, ScrmCalendarEventDto newDates) throws ScrmException {
        return eventService.postponeEvent(id, newDates);
    }

    /**
     * 移动事件 (拖拽日历场景, 仅修改日期, 不改变状态)。
     *
     * @param moveDto 移动参数
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 日期非法
     */
    public ScrmCalendarEventEntity moveEvent(ScrmCalendarMoveDto moveDto) throws ScrmException {
        return eventService.moveEvent(moveDto);
    }

    /**
     * 复制事件 (创建副本, 状态置为 PLANNED)。
     *
     * @param id        源事件 ID
     * @param newDates  新的日期范围 (startDate + endDate, 可空则沿用源事件日期)
     * @return 复制后的事件
     * @throws ScrmException 源事件不存在 / 日期非法
     */
    public ScrmCalendarEventEntity duplicateEvent(Long id, ScrmCalendarEventDto newDates) throws ScrmException {
        return eventService.duplicateEvent(id, newDates);
    }

    // ============================================================
    // 节日管理
    // ============================================================

    /**
     * 创建节日。
     *
     * @param dto 节日参数
     * @return 创建后的节日
     * @throws ScrmException 参数非法
     */
    public ScrmCalendarHolidayEntity createHoliday(ScrmCalendarHolidayDto dto) throws ScrmException {
        return holidayService.createHoliday(dto);
    }

    /**
     * 更新节日 (字段非空才覆盖)。
     *
     * @param id  节日 ID
     * @param dto 节日参数
     * @return 更新后的节日
     * @throws ScrmException 节日不存在 / 参数非法
     */
    public ScrmCalendarHolidayEntity updateHoliday(Long id, ScrmCalendarHolidayDto dto) throws ScrmException {
        return holidayService.updateHoliday(id, dto);
    }

    /**
     * 删除节日。
     *
     * @param id 节日 ID
     * @throws ScrmException 节日不存在
     */
    public void deleteHoliday(Long id) throws ScrmException {
        holidayService.deleteHoliday(id);
    }

    /**
     * 查询节日详情。
     *
     * @param id 节日 ID
     * @return 节日实体
     * @throws ScrmException 节日不存在
     */
    public ScrmCalendarHolidayEntity getHoliday(Long id) throws ScrmException {
        return holidayService.getHoliday(id);
    }

    /**
     * 分页查询节日, 支持按类型 / 启用状态过滤。
     *
     * @param holidayType 节日类型过滤（可空）
     * @param isActive     启用状态过滤（可空）
     * @param pageable     分页参数
     * @return 节日分页结果 (按 holidayDate 升序)
     */
    public Page<ScrmCalendarHolidayEntity> listHolidays(String holidayType, Boolean isActive, Pageable pageable) {
        return holidayService.listHolidays(holidayType, isActive, pageable);
    }

    /**
     * 启用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    public ScrmCalendarHolidayEntity activateHoliday(Long id) throws ScrmException {
        return holidayService.activateHoliday(id);
    }

    /**
     * 停用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    public ScrmCalendarHolidayEntity deactivateHoliday(Long id) throws ScrmException {
        return holidayService.deactivateHoliday(id);
    }

    /**
     * 查询即将到来的节日 (从今天起向后 N 天)。
     * <p>仅返回启用状态的节日, 按日期升序。</p>
     *
     * @param days 未来天数 (≤0 视为 7)
     * @return 节日列表
     */
    public List<ScrmCalendarHolidayEntity> getUpcomingHolidays(int days) {
        return holidayService.getUpcomingHolidays(days);
    }

    /**
     * 查询某月的节日。
     *
     * @param year  年份
     * @param month 月份 (1-12)
     * @return 节日列表
     * @throws ScrmException 月份非法
     */
    public List<ScrmCalendarHolidayEntity> getHolidaysByMonth(int year, int month) {
        return holidayService.getHolidaysByMonth(year, month);
    }

    /**
     * 节日营销建议: 返回节日本身的建议动作 / 渠道 + 营销机会分级。
     *
     * @param holidayId 节日 ID
     * @return 营销建议 Map
     * @throws ScrmException 节日不存在
     */
    public Map<String, Object> suggestMarketingActions(Long holidayId) throws ScrmException {
        return holidayService.suggestMarketingActions(holidayId);
    }

    // ============================================================
    // 冲突检测
    // ============================================================

    /**
     * 检测指定事件的冲突。
     * <p>检测维度: 时间重叠 / 渠道冲突 / 客群重叠 / 预算超支。</p>
     *
     * @param eventId 事件 ID
     * @return 检测到的冲突列表
     * @throws ScrmException 事件不存在
     */
    public List<ScrmCalendarConflictEntity> detectConflicts(Long eventId) throws ScrmException {
        return conflictService.detectConflicts(eventId);
    }

    /**
     * 批量检测全部未取消事件的冲突。
     *
     * @return 检测到的冲突列表
     */
    public List<ScrmCalendarConflictEntity> batchDetectConflicts() {
        return conflictService.batchDetectConflicts();
    }

    /**
     * 查询冲突详情。
     *
     * @param id 冲突 ID
     * @return 冲突实体
     * @throws ScrmException 冲突不存在
     */
    public ScrmCalendarConflictEntity getConflict(Long id) throws ScrmException {
        return conflictService.getConflict(id);
    }

    /**
     * 分页查询冲突, 支持按解决状态 / 严重程度过滤。
     *
     * @param resolvedStatus 解决状态过滤（可空）
     * @param severity       严重程度过滤（可空）
     * @param pageable       分页参数
     * @return 冲突分页结果 (按 detectedAt DESC)
     */
    public Page<ScrmCalendarConflictEntity> listConflicts(String resolvedStatus, String severity, Pageable pageable) {
        return conflictService.listConflicts(resolvedStatus, severity, pageable);
    }

    /**
     * 解决冲突 (UNRESOLVED → RESOLVED)。
     *
     * @param id   冲突 ID
     * @param note 解决备注
     * @return 更新后的冲突
     * @throws ScrmException 冲突不存在 / 状态非法
     */
    public ScrmCalendarConflictEntity resolveConflict(Long id, String note) throws ScrmException {
        return conflictService.resolveConflict(id, note);
    }

    /**
     * 忽略冲突 (UNRESOLVED → IGNORED)。
     *
     * @param id   冲突 ID
     * @param note 忽略备注
     * @return 更新后的冲突
     * @throws ScrmException 冲突不存在 / 状态非法
     */
    public ScrmCalendarConflictEntity ignoreConflict(Long id, String note) throws ScrmException {
        return conflictService.ignoreConflict(id, note);
    }

    /**
     * 查询事件相关冲突。
     *
     * @param eventId 事件 ID
     * @return 冲突列表
     */
    public List<ScrmCalendarConflictEntity> getEventConflicts(Long eventId) {
        return conflictService.getEventConflicts(eventId);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 日历统计概览: 事件数 / 各类型 / 各状态 / 完成率。
     *
     * @param startDate 开始日期 (含, 可空)
     * @param endDate   结束日期 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getCalendarStats(LocalDate startDate, LocalDate endDate) {
        return statsService.getCalendarStats(startDate, endDate);
    }

    /**
     * 月度统计: 指定月份的事件数 / 各类型 / 各状态 / 完成率。
     *
     * @param year  年份
     * @param month 月份 (1-12)
     * @return 统计结果 Map
     * @throws ScrmException 月份非法
     */
    public Map<String, Object> getMonthlyStats(int year, int month) {
        return statsService.getMonthlyStats(year, month);
    }

    /**
     * 渠道负载: 各渠道事件分布。
     * <p>遍历查询区间内的事件, 按渠道聚合事件数与预算。</p>
     *
     * @param startDate 开始日期 (含, 可空)
     * @param endDate   结束日期 (含, 可空)
     * @return 渠道负载列表 [{channel, eventCount, totalBudget}]
     */
    public List<Map<String, Object>> getChannelLoad(LocalDate startDate, LocalDate endDate) {
        return statsService.getChannelLoad(startDate, endDate);
    }

    /**
     * 负责人工作量: 指定负责人在区间内的事件数与预算。
     *
     * @param ownerId   负责人 ID
     * @param startDate 开始日期 (含, 可空)
     * @param endDate   结束日期 (含, 可空)
     * @return 工作量 Map
     */
    public Map<String, Object> getOwnerWorkload(String ownerId, LocalDate startDate, LocalDate endDate) {
        return statsService.getOwnerWorkload(ownerId, startDate, endDate);
    }

    /**
     * 繁忙时段分析: 按日聚合事件数, 找出事件数排名前列的繁忙日期。
     *
     * @param startDate 开始日期 (含, 可空)
     * @param endDate   结束日期 (含, 可空)
     * @return 繁忙时段列表 [{date, eventCount, totalBudget}]
     */
    public List<Map<String, Object>> getBusyPeriods(LocalDate startDate, LocalDate endDate) {
        return statsService.getBusyPeriods(startDate, endDate);
    }
}