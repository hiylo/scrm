/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCalendarController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCalendarEventDto;
import org.hiylo.scrm.dto.ScrmCalendarHolidayDto;
import org.hiylo.scrm.dto.ScrmCalendarMoveDto;
import org.hiylo.scrm.dto.ScrmCalendarRangeDto;
import org.hiylo.scrm.entity.ScrmCalendarConflictEntity;
import org.hiylo.scrm.entity.ScrmCalendarEventEntity;
import org.hiylo.scrm.entity.ScrmCalendarHolidayEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmMarketingCalendarService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销日历控制器。
 * <p>
 * 提供营销日历的事件管理、节日库、冲突检测、统计分析接口。涵盖事件 CRUD
 * 与生命周期管理 (确认/开始/完成/取消/延期/移动/复制), 日期视图 (日/周/月/范围),
 * 节日 CRUD 与营销建议, 冲突检测与解决, 日历统计概览/月度/渠道负载/负责人工作量/繁忙时段。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/marketing-calendar")
@RequiredArgsConstructor
public class ScrmMarketingCalendarController {

    /** 营销日历服务 */
    private final ScrmMarketingCalendarService scrmMarketingCalendarService;

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
    @RequirePermission(resource = "scrm_marketing_calendar", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmCalendarEventEntity> createEvent(@Valid @RequestBody ScrmCalendarEventDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.createEvent(dto));
    }

    /**
     * 更新日历事件。
     *
     * @param id  事件 ID
     * @param dto 事件参数
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmCalendarEventEntity> updateEvent(@PathVariable Long id,
                                                                    @RequestBody ScrmCalendarEventDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.updateEvent(id, dto));
    }

    /**
     * 删除日历事件 (同时清理关联冲突记录)。
     *
     * @param id 事件 ID
     * @return 空响应
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteEvent(@PathVariable Long id) throws ScrmException {
        scrmMarketingCalendarService.deleteEvent(id);
        return OperationResponse.build();
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件详情
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCalendarEventEntity> getEvent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.getEvent(id));
    }

    /**
     * 分页查询事件列表。
     *
     * @param eventType 事件类型过滤（可空）
     * @param status    状态过滤（可空）
     * @param ownerId   负责人 ID 过滤（可空）
     * @param startDate 开始日期过滤 (事件开始日期 ≥ 此值, 可空, ISO 格式: yyyy-MM-dd)
     * @param endDate   结束日期过滤 (事件结束日期 ≤ 此值, 可空, ISO 格式: yyyy-MM-dd)
     * @param keyword   事件标题关键字模糊匹配（可空）
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 事件分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmCalendarEventEntity>> listEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmMarketingCalendarService.listEvents(
                eventType, status, ownerId, startDate, endDate, keyword, pageable));
    }

    /**
     * 按日期范围查询事件 (日历视图数据)。
     *
     * @param rangeDto 日期范围参数
     * @return 事件列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @PostMapping("/range")
    public OperationResponse<List<ScrmCalendarEventEntity>> getEventsByDateRange(
            @Valid @RequestBody ScrmCalendarRangeDto rangeDto) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.getEventsByDateRange(rangeDto));
    }

    /**
     * 查询某日的事件。
     *
     * @param date 日期 (ISO 格式: yyyy-MM-dd)
     * @return 事件列表
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/by-date/{date}")
    public OperationResponse<List<ScrmCalendarEventEntity>> getEventsByDate(@PathVariable LocalDate date) {
        return OperationResponse.build(scrmMarketingCalendarService.getEventsByDate(date));
    }

    /**
     * 月视图: 查询某月的事件。
     *
     * @param year  年份 (如 2026)
     * @param month 月份 (1-12)
     * @return 事件列表
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/month/{year}/{month}")
    public OperationResponse<List<ScrmCalendarEventEntity>> getEventsByMonth(@PathVariable int year,
                                                                             @PathVariable int month) {
        return OperationResponse.build(scrmMarketingCalendarService.getEventsByMonth(year, month));
    }

    /**
     * 周视图: 查询某周的事件 (周一开始)。
     *
     * @param weekStart 周开始日期 (ISO 格式: yyyy-MM-dd)
     * @return 事件列表
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/week/{weekStart}")
    public OperationResponse<List<ScrmCalendarEventEntity>> getEventsByWeek(@PathVariable LocalDate weekStart) {
        return OperationResponse.build(scrmMarketingCalendarService.getEventsByWeek(weekStart));
    }

    /**
     * 确认事件。
     *
     * @param id 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @PostMapping("/{id}/confirm")
    public OperationResponse<ScrmCalendarEventEntity> confirmEvent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.confirmEvent(id));
    }

    /**
     * 开始事件。
     *
     * @param id 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @PostMapping("/{id}/start")
    public OperationResponse<ScrmCalendarEventEntity> startEvent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.startEvent(id));
    }

    /**
     * 完成事件。
     *
     * @param id 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @PostMapping("/{id}/complete")
    public OperationResponse<ScrmCalendarEventEntity> completeEvent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.completeEvent(id));
    }

    /**
     * 取消事件。
     *
     * @param id    事件 ID
     * @param body  请求体, 可包含 reason 字段 (取消原因)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @PostMapping("/{id}/cancel")
    public OperationResponse<ScrmCalendarEventEntity> cancelEvent(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, Object> body) throws ScrmException {
        String reason = body != null && body.get("reason") != null
                ? body.get("reason").toString() : null;
        return OperationResponse.build(scrmMarketingCalendarService.cancelEvent(id, reason));
    }

    /**
     * 延期事件。
     *
     * @param id        事件 ID
     * @param newDates  新的日期范围 (startDate + endDate)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法 / 日期非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @PostMapping("/{id}/postpone")
    public OperationResponse<ScrmCalendarEventEntity> postponeEvent(
            @PathVariable Long id,
            @RequestBody ScrmCalendarEventDto newDates) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.postponeEvent(id, newDates));
    }

    /**
     * 移动事件 (拖拽日历场景, 仅修改日期)。
     *
     * @param moveDto 移动参数
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 日期非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/move")
    public OperationResponse<ScrmCalendarEventEntity> moveEvent(@Valid @RequestBody ScrmCalendarMoveDto moveDto)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.moveEvent(moveDto));
    }

    /**
     * 复制事件 (创建副本, 状态置为 PLANNED)。
     *
     * @param id        源事件 ID
     * @param newDates  新的日期范围 (可空则沿用源事件日期)
     * @return 复制后的事件
     * @throws ScrmException 源事件不存在 / 日期非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/{id}/duplicate")
    public OperationResponse<ScrmCalendarEventEntity> duplicateEvent(
            @PathVariable Long id,
            @RequestBody(required = false) ScrmCalendarEventDto newDates) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.duplicateEvent(id, newDates));
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
    @RequirePermission(resource = "scrm_marketing_calendar", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/holidays")
    public OperationResponse<ScrmCalendarHolidayEntity> createHoliday(@Valid @RequestBody ScrmCalendarHolidayDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.createHoliday(dto));
    }

    /**
     * 更新节日。
     *
     * @param id  节日 ID
     * @param dto 节日参数
     * @return 更新后的节日
     * @throws ScrmException 节日不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @PutMapping("/holidays/{id}")
    public OperationResponse<ScrmCalendarHolidayEntity> updateHoliday(@PathVariable Long id,
                                                                       @RequestBody ScrmCalendarHolidayDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.updateHoliday(id, dto));
    }

    /**
     * 删除节日。
     *
     * @param id 节日 ID
     * @return 空响应
     * @throws ScrmException 节日不存在
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "delete")
    @DeleteMapping("/holidays/{id}")
    public OperationResponse<Void> deleteHoliday(@PathVariable Long id) throws ScrmException {
        scrmMarketingCalendarService.deleteHoliday(id);
        return OperationResponse.build();
    }

    /**
     * 查询节日详情。
     *
     * @param id 节日 ID
     * @return 节日详情
     * @throws ScrmException 节日不存在
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/holidays/{id}")
    public OperationResponse<ScrmCalendarHolidayEntity> getHoliday(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.getHoliday(id));
    }

    /**
     * 分页查询节日列表。
     *
     * @param holidayType 节日类型过滤（可空）
     * @param isActive    启用状态过滤（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 节日分页结果 (按 holidayDate 升序)
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/holidays/list")
    public OperationResponse<Page<ScrmCalendarHolidayEntity>> listHolidays(
            @RequestParam(required = false) String holidayType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "holidayDate"));
        return OperationResponse.build(scrmMarketingCalendarService.listHolidays(holidayType, isActive, pageable));
    }

    /**
     * 启用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @PostMapping("/holidays/{id}/activate")
    public OperationResponse<ScrmCalendarHolidayEntity> activateHoliday(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.activateHoliday(id));
    }

    /**
     * 停用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @PostMapping("/holidays/{id}/deactivate")
    public OperationResponse<ScrmCalendarHolidayEntity> deactivateHoliday(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.deactivateHoliday(id));
    }

    /**
     * 查询即将到来的节日 (从今天起向后 N 天)。
     *
     * @param days 未来天数 (默认 7)
     * @return 节日列表
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/holidays/upcoming")
    public OperationResponse<List<ScrmCalendarHolidayEntity>> getUpcomingHolidays(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmMarketingCalendarService.getUpcomingHolidays(days));
    }

    /**
     * 查询某月的节日。
     *
     * @param year  年份
     * @param month 月份 (1-12)
     * @return 节日列表
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/holidays/by-month/{year}/{month}")
    public OperationResponse<List<ScrmCalendarHolidayEntity>> getHolidaysByMonth(@PathVariable int year,
                                                                                  @PathVariable int month) {
        return OperationResponse.build(scrmMarketingCalendarService.getHolidaysByMonth(year, month));
    }

    /**
     * 节日营销建议。
     *
     * @param id 节日 ID
     * @return 营销建议 Map
     * @throws ScrmException 节日不存在
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/holidays/{id}/suggestions")
    public OperationResponse<Map<String, Object>> suggestMarketingActions(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.suggestMarketingActions(id));
    }

    // ============================================================
    // 冲突检测
    // ============================================================

    /**
     * 检测指定事件的冲突。
     *
     * @param eventId 事件 ID
     * @return 检测到的冲突列表
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/conflicts/detect/{eventId}")
    public OperationResponse<List<ScrmCalendarConflictEntity>> detectConflicts(@PathVariable Long eventId)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.detectConflicts(eventId));
    }

    /**
     * 批量检测全部事件的冲突。
     *
     * @return 检测到的冲突列表
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @RateLimit(capacity = 1, refillTokens = 1, refillPeriodSeconds = 60)
    @PostMapping("/conflicts/batch-detect")
    public OperationResponse<List<ScrmCalendarConflictEntity>> batchDetectConflicts() {
        return OperationResponse.build(scrmMarketingCalendarService.batchDetectConflicts());
    }

    /**
     * 查询冲突详情。
     *
     * @param id 冲突 ID
     * @return 冲突详情
     * @throws ScrmException 冲突不存在
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/conflicts/{id}")
    public OperationResponse<ScrmCalendarConflictEntity> getConflict(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMarketingCalendarService.getConflict(id));
    }

    /**
     * 分页查询冲突列表。
     *
     * @param resolvedStatus 解决状态过滤（可空）: UNRESOLVED/RESOLVED/IGNORED
     * @param severity       严重程度过滤（可空）: WARNING/ERROR/INFO
     * @param page           页码（从 0 开始, 默认 0）
     * @param size           每页大小（默认 20）
     * @return 冲突分页结果 (按 detectedAt DESC)
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/conflicts/list")
    public OperationResponse<Page<ScrmCalendarConflictEntity>> listConflicts(
            @RequestParam(required = false) String resolvedStatus,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "detectedAt"));
        return OperationResponse.build(scrmMarketingCalendarService.listConflicts(resolvedStatus, severity, pageable));
    }

    /**
     * 解决冲突。
     *
     * @param id   冲突 ID
     * @param body  请求体, 可包含 note 字段 (解决备注)
     * @return 更新后的冲突
     * @throws ScrmException 冲突不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @PostMapping("/conflicts/{id}/resolve")
    public OperationResponse<ScrmCalendarConflictEntity> resolveConflict(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, Object> body) throws ScrmException {
        String note = body != null && body.get("note") != null
                ? body.get("note").toString() : null;
        return OperationResponse.build(scrmMarketingCalendarService.resolveConflict(id, note));
    }

    /**
     * 忽略冲突。
     *
     * @param id   冲突 ID
     * @param body  请求体, 可包含 note 字段 (忽略备注)
     * @return 更新后的冲突
     * @throws ScrmException 冲突不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "update")
    @PostMapping("/conflicts/{id}/ignore")
    public OperationResponse<ScrmCalendarConflictEntity> ignoreConflict(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, Object> body) throws ScrmException {
        String note = body != null && body.get("note") != null
                ? body.get("note").toString() : null;
        return OperationResponse.build(scrmMarketingCalendarService.ignoreConflict(id, note));
    }

    /**
     * 查询事件相关冲突。
     *
     * @param eventId 事件 ID
     * @return 冲突列表
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/conflicts/event/{eventId}")
    public OperationResponse<List<ScrmCalendarConflictEntity>> getEventConflicts(@PathVariable Long eventId) {
        return OperationResponse.build(scrmMarketingCalendarService.getEventConflicts(eventId));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 日历统计概览: 事件数 / 各类型 / 各状态 / 完成率。
     *
     * @param startDate 开始日期 (可空, ISO 格式: yyyy-MM-dd)
     * @param endDate   结束日期 (可空, ISO 格式: yyyy-MM-dd)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getCalendarStats(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return OperationResponse.build(scrmMarketingCalendarService.getCalendarStats(startDate, endDate));
    }

    /**
     * 月度统计。
     *
     * @param year  年份
     * @param month 月份 (1-12)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/stats/monthly/{year}/{month}")
    public OperationResponse<Map<String, Object>> getMonthlyStats(@PathVariable int year,
                                                                   @PathVariable int month) {
        return OperationResponse.build(scrmMarketingCalendarService.getMonthlyStats(year, month));
    }

    /**
     * 渠道负载: 各渠道事件分布。
     *
     * @param startDate 开始日期 (可空, ISO 格式: yyyy-MM-dd)
     * @param endDate   结束日期 (可空, ISO 格式: yyyy-MM-dd)
     * @return 渠道负载列表
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/stats/channel-load")
    public OperationResponse<List<Map<String, Object>>> getChannelLoad(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return OperationResponse.build(scrmMarketingCalendarService.getChannelLoad(startDate, endDate));
    }

    /**
     * 负责人工作量。
     *
     * @param ownerId   负责人 ID
     * @param startDate 开始日期 (可空, ISO 格式: yyyy-MM-dd)
     * @param endDate   结束日期 (可空, ISO 格式: yyyy-MM-dd)
     * @return 工作量 Map
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/stats/owner-workload/{ownerId}")
    public OperationResponse<Map<String, Object>> getOwnerWorkload(
            @PathVariable String ownerId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return OperationResponse.build(scrmMarketingCalendarService.getOwnerWorkload(ownerId, startDate, endDate));
    }

    /**
     * 繁忙时段分析。
     *
     * @param startDate 开始日期 (可空, ISO 格式: yyyy-MM-dd)
     * @param endDate   结束日期 (可空, ISO 格式: yyyy-MM-dd)
     * @return 繁忙时段列表 (按事件数降序, 取前 20)
     */
    @RequirePermission(resource = "scrm_marketing_calendar", action = "read")
    @GetMapping("/stats/busy-periods")
    public OperationResponse<List<Map<String, Object>>> getBusyPeriods(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return OperationResponse.build(scrmMarketingCalendarService.getBusyPeriods(startDate, endDate));
    }
}
