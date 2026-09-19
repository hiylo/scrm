/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionCalendarManageService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCalendarViewDto;
import org.hiylo.scrm.dto.ScrmInteractionCalendarDto;
import org.hiylo.scrm.entity.ScrmInteractionCalendarEntity;
import org.hiylo.scrm.entity.ScrmInteractionPlanEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmInteractionCalendarRepository;
import org.hiylo.scrm.repository.ScrmInteractionPlanRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 客户互动日历管理服务。
 * <p>
 * 承载互动日历管理能力: 日历 CRUD / 启停 / 共享 / 日周月议程视图聚合 /
 * 日历统计与摘要 / 计划查询 (客户/负责人/日期范围/状态/即将到来/逾期/今日,
 * 供日历视图与工作台使用)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmInteractionCalendarManageService {

    /** 默认时区 */
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /** 默认统计初值 */
    private static final int DEFAULT_COUNT = 0;

    /** 默认完成率 */
    private static final double DEFAULT_RATE = 0.0;

    /** 默认日历类型 */
    private static final String DEFAULT_CALENDAR_TYPE = "PERSONAL";

    /** 默认工作时间开始 */
    private static final String DEFAULT_WORKING_HOURS_START = "09:00";

    /** 默认工作时间结束 */
    private static final String DEFAULT_WORKING_HOURS_END = "18:00";

    /** 默认工作日 */
    private static final String DEFAULT_WORKING_DAYS = "1,2,3,4,5";

    /** 默认日历提醒分钟 */
    private static final int DEFAULT_CALENDAR_REMINDER_MINUTES = 15;

    /** 默认日历时长分钟 */
    private static final int DEFAULT_CALENDAR_DURATION_MINUTES = 60;

    /** 计划状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 计划状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";
    /** 计划状态: 未到 */
    private static final String STATUS_NO_SHOW = "NO_SHOW";

    /** 非终态状态集合 (可流转的状态) */
    private static final List<String> ACTIVE_STATUSES = List.of(
            "PLANNED", "CONFIRMED", "IN_PROGRESS", "RESCHEDULED");

    /** 合法的日历类型 */
    private static final List<String> VALID_CALENDAR_TYPES = List.of(
            "PERSONAL", "TEAM", "DEPARTMENT", "COMPANY", "CUSTOMER");

    /** 互动日历数据访问层 */
    private final ScrmInteractionCalendarRepository calendarRepository;

    /** 互动计划数据访问层 */
    private final ScrmInteractionPlanRepository planRepository;

    /** 互动计划管理服务 (日历视图聚合复用计划查询) */
    private final ScrmInteractionCalendarPlanService planService;

    /**
     * 创建互动日历。
     *
     * @param dto 日历参数
     * @return 创建后的日历
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmInteractionCalendarEntity createCalendar(ScrmInteractionCalendarDto dto) throws ScrmException {
        validateCalendarDto(dto, false);
        if (calendarRepository.findByCalendarCode(dto.getCalendarCode()).isPresent()) {
            throw ScrmException.conflict("日历编码已存在: " + dto.getCalendarCode());
        }
        ScrmInteractionCalendarEntity entity = new ScrmInteractionCalendarEntity();
        entity.setCalendarName(dto.getCalendarName());
        entity.setCalendarCode(dto.getCalendarCode());
        entity.setDescription(dto.getDescription());
        entity.setCalendarType(dto.getCalendarType() != null && !dto.getCalendarType().isBlank()
                ? dto.getCalendarType() : DEFAULT_CALENDAR_TYPE);
        entity.setOwnerId(dto.getOwnerId());
        entity.setOwnerName(dto.getOwnerName());
        entity.setSharedWith(dto.getSharedWith());
        entity.setIsPublic(dto.getIsPublic() != null ? dto.getIsPublic() : Boolean.FALSE);
        entity.setColor(dto.getColor());
        entity.setIcon(dto.getIcon());
        entity.setWorkingHoursStart(dto.getWorkingHoursStart() != null && !dto.getWorkingHoursStart().isBlank()
                ? dto.getWorkingHoursStart() : DEFAULT_WORKING_HOURS_START);
        entity.setWorkingHoursEnd(dto.getWorkingHoursEnd() != null && !dto.getWorkingHoursEnd().isBlank()
                ? dto.getWorkingHoursEnd() : DEFAULT_WORKING_HOURS_END);
        entity.setWorkingDays(dto.getWorkingDays() != null && !dto.getWorkingDays().isBlank()
                ? dto.getWorkingDays() : DEFAULT_WORKING_DAYS);
        entity.setTimezone(DEFAULT_TIMEZONE);
        entity.setDefaultReminderMinutes(dto.getDefaultReminderMinutes() != null
                ? dto.getDefaultReminderMinutes() : DEFAULT_CALENDAR_REMINDER_MINUTES);
        entity.setDefaultDurationMinutes(dto.getDefaultDurationMinutes() != null
                ? dto.getDefaultDurationMinutes() : DEFAULT_CALENDAR_DURATION_MINUTES);
        entity.setPlanCount(DEFAULT_COUNT);
        entity.setCompletedCount(DEFAULT_COUNT);
        entity.setCancelledCount(DEFAULT_COUNT);
        entity.setCompletionRate(DEFAULT_RATE);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : planService.currentOperator());
        entity = calendarRepository.save(entity);
        log.info("创建互动日历: id={}, calendarName={}, calendarCode={}",
                entity.getId(), entity.getCalendarName(), entity.getCalendarCode());
        return entity;
    }

    /**
     * 更新互动日历 (字段非空才覆盖)。
     *
     * @param id  日历 ID
     * @param dto 日历参数
     * @return 更新后的日历
     * @throws ScrmException 日历不存在 / 参数非法
     */
    @Transactional
    public ScrmInteractionCalendarEntity updateCalendar(
            Long id, ScrmInteractionCalendarDto dto) throws ScrmException {
        ScrmInteractionCalendarEntity entity = findCalendarOrThrow(id);
        validateCalendarDto(dto, true);
        if (dto.getCalendarName() != null) entity.setCalendarName(dto.getCalendarName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getCalendarType() != null) entity.setCalendarType(dto.getCalendarType());
        if (dto.getOwnerId() != null) entity.setOwnerId(dto.getOwnerId());
        if (dto.getOwnerName() != null) entity.setOwnerName(dto.getOwnerName());
        if (dto.getSharedWith() != null) entity.setSharedWith(dto.getSharedWith());
        if (dto.getIsPublic() != null) entity.setIsPublic(dto.getIsPublic());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getWorkingHoursStart() != null) entity.setWorkingHoursStart(dto.getWorkingHoursStart());
        if (dto.getWorkingHoursEnd() != null) entity.setWorkingHoursEnd(dto.getWorkingHoursEnd());
        if (dto.getWorkingDays() != null) entity.setWorkingDays(dto.getWorkingDays());
        if (dto.getDefaultReminderMinutes() != null) entity.setDefaultReminderMinutes(dto.getDefaultReminderMinutes());
        if (dto.getDefaultDurationMinutes() != null) entity.setDefaultDurationMinutes(dto.getDefaultDurationMinutes());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = calendarRepository.save(entity);
        log.info("更新互动日历: id={}, calendarName={}", entity.getId(), entity.getCalendarName());
        return entity;
    }

    /**
     * 删除互动日历。
     *
     * @param id 日历 ID
     * @throws ScrmException 日历不存在
     */
    @Transactional
    public void deleteCalendar(Long id) throws ScrmException {
        ScrmInteractionCalendarEntity entity = findCalendarOrThrow(id);
        calendarRepository.delete(entity);
        log.info("删除互动日历: id={}, calendarName={}", id, entity.getCalendarName());
    }

    /**
     * 查询日历详情。
     *
     * @param id 日历 ID
     * @return 日历实体
     * @throws ScrmException 日历不存在
     */
    @Transactional(readOnly = true)
    public ScrmInteractionCalendarEntity getCalendar(Long id) throws ScrmException {
        return findCalendarOrThrow(id);
    }

    /**
     * 按日历编码查询。
     *
     * @param code 日历编码
     * @return 日历实体
     * @throws ScrmException 日历不存在
     */
    @Transactional(readOnly = true)
    public ScrmInteractionCalendarEntity getCalendarByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("日历编码不能为空");
        }
        return calendarRepository.findByCalendarCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "互动日历不存在: code=" + code));
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
    @Transactional(readOnly = true)
    public Page<ScrmInteractionCalendarEntity> listCalendars(String ownerId, String calendarType,
                                                             Boolean enabled, Pageable pageable) {
        Specification<ScrmInteractionCalendarEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ownerId != null && !ownerId.isBlank()) {
                predicates.add(cb.equal(root.get("ownerId"), ownerId));
            }
            if (calendarType != null && !calendarType.isBlank()) {
                predicates.add(cb.equal(root.get("calendarType"), calendarType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return calendarRepository.findAll(spec, pageable);
    }

    /**
     * 启用日历。
     *
     * @param id 日历 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在
     */
    @Transactional
    public ScrmInteractionCalendarEntity enableCalendar(Long id) throws ScrmException {
        ScrmInteractionCalendarEntity entity = findCalendarOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = calendarRepository.save(entity);
        log.info("启用互动日历: id={}", id);
        return entity;
    }

    /**
     * 停用日历。
     *
     * @param id 日历 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在
     */
    @Transactional
    public ScrmInteractionCalendarEntity disableCalendar(Long id) throws ScrmException {
        ScrmInteractionCalendarEntity entity = findCalendarOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = calendarRepository.save(entity);
        log.info("停用互动日历: id={}", id);
        return entity;
    }

    /**
     * 共享日历给指定用户 (追加到 sharedWith)。
     *
     * @param id     日历 ID
     * @param userId 用户 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在 / 用户 ID 非法
     */
    @Transactional
    public ScrmInteractionCalendarEntity shareCalendar(Long id, String userId) throws ScrmException {
        if (userId == null || userId.isBlank()) {
            throw ScrmException.badRequest("用户 ID 不能为空");
        }
        ScrmInteractionCalendarEntity entity = findCalendarOrThrow(id);
        String sharedWith = entity.getSharedWith();
        List<String> users = sharedWith == null || sharedWith.isBlank()
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(sharedWith.split(",")));
        if (!users.contains(userId)) {
            users.add(userId);
            entity.setSharedWith(String.join(",", users));
            entity = calendarRepository.save(entity);
        }
        log.info("共享互动日历: id={}, userId={}", id, userId);
        return entity;
    }

    /**
     * 查询共享给指定用户的日历。
     *
     * @param userId 用户 ID
     * @return 日历列表
     */
    @Transactional(readOnly = true)
    public List<ScrmInteractionCalendarEntity> getSharedCalendars(String userId) {
        if (userId == null || userId.isBlank()) {
            throw ScrmException.badRequest("用户 ID 不能为空");
        }
        Specification<ScrmInteractionCalendarEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // sharedWith 包含 userId (LIKE '%userId%' 简单匹配)
            predicates.add(cb.like(root.get("sharedWith"), "%" + userId + "%"));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return calendarRepository.findAll(spec);
    }

    /**
     * 日历视图: 按日期范围聚合计划, 返回日视图结构。
     *
     * @param viewDto 视图查询参数
     * @return 日历视图 Map {startDate, endDate, ownerId, days:[{date, plans:[...]}]}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCalendarView(ScrmCalendarViewDto viewDto) {
        if (viewDto == null || viewDto.getStartDate() == null || viewDto.getEndDate() == null) {
            throw ScrmException.badRequest("视图查询参数不能为空");
        }
        List<ScrmInteractionPlanEntity> plans = this.getPlansByDateRange(
                viewDto.getStartDate(), viewDto.getEndDate(), viewDto.getOwnerId());
        // 客户过滤
        if (viewDto.getCustomerId() != null) {
            plans = plans.stream()
                    .filter(p -> Objects.equals(p.getCustomerId(), viewDto.getCustomerId()))
                    .collect(Collectors.toList());
        }
        // 按日期分组
        Map<LocalDate, List<ScrmInteractionPlanEntity>> byDate = new LinkedHashMap<>();
        for (ScrmInteractionPlanEntity plan : plans) {
            LocalDate date = plan.getScheduledStart().toLocalDate();
            byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(plan);
        }
        List<Map<String, Object>> days = new ArrayList<>();
        LocalDate cursor = viewDto.getStartDate().toLocalDate();
        LocalDate end = viewDto.getEndDate().toLocalDate();
        while (!cursor.isAfter(end)) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", cursor);
            day.put("plans", byDate.getOrDefault(cursor, List.of()));
            days.add(day);
            cursor = cursor.plusDays(1);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startDate", viewDto.getStartDate());
        result.put("endDate", viewDto.getEndDate());
        result.put("ownerId", viewDto.getOwnerId());
        result.put("days", days);
        return result;
    }

    /**
     * 月视图: 返回指定年月每天的互动计划。
     *
     * @param year    年
     * @param month   月 (1-12)
     * @param ownerId 负责人 (可空)
     * @return 月视图 Map {year, month, days:[{date, plans:[...]}]}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMonthView(int year, int month, String ownerId) {
        if (month < 1 || month > 12) {
            throw ScrmException.badRequest("月份必须在 1-12 之间");
        }
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);
        List<ScrmInteractionPlanEntity> plans = this.getPlansByDateRange(start, end, ownerId);
        Map<LocalDate, List<ScrmInteractionPlanEntity>> byDate = plans.stream()
                .collect(Collectors.groupingBy(p -> p.getScheduledStart().toLocalDate(), LinkedHashMap::new,
                        Collectors.toList()));
        List<Map<String, Object>> days = new ArrayList<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date);
            day.put("plans", byDate.getOrDefault(date, List.of()));
            days.add(day);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("month", month);
        result.put("days", days);
        return result;
    }

    /**
     * 周视图: 返回指定周开始的 7 天互动计划。
     *
     * @param weekStart 周开始日期 (周一)
     * @param ownerId   负责人 (可空)
     * @return 周视图 Map {weekStart, days:[{date, plans:[...]}]}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getWeekView(LocalDate weekStart, String ownerId) {
        if (weekStart == null) {
            throw ScrmException.badRequest("周开始日期不能为空");
        }
        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime end = weekStart.plusDays(7).atStartOfDay();
        List<ScrmInteractionPlanEntity> plans = this.getPlansByDateRange(start, end, ownerId);
        Map<LocalDate, List<ScrmInteractionPlanEntity>> byDate = plans.stream()
                .collect(Collectors.groupingBy(p -> p.getScheduledStart().toLocalDate(), LinkedHashMap::new,
                        Collectors.toList()));
        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date);
            day.put("plans", byDate.getOrDefault(date, List.of()));
            days.add(day);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weekStart", weekStart);
        result.put("days", days);
        return result;
    }

    /**
     * 日视图: 返回指定日期的互动计划。
     *
     * @param date    日期
     * @param ownerId 负责人 (可空)
     * @return 日视图 Map {date, plans:[...]}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDayView(LocalDate date, String ownerId) {
        if (date == null) {
            throw ScrmException.badRequest("日期不能为空");
        }
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<ScrmInteractionPlanEntity> plans = this.getPlansByDateRange(start, end, ownerId);
        plans.sort((a, b) -> a.getScheduledStart().compareTo(b.getScheduledStart()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("plans", plans);
        return result;
    }

    /**
     * 议程视图: 返回未来 N 天的互动计划列表。
     *
     * @param ownerId 负责人 (可空)
     * @param days    天数
     * @return 议程视图 Map {days, plans:[...]}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAgendaView(String ownerId, int days) {
        if (days <= 0) {
            throw ScrmException.badRequest("天数必须为正数");
        }
        List<ScrmInteractionPlanEntity> plans = this.getUpcomingPlans(ownerId, days);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("plans", plans);
        return result;
    }

    /**
     * 按客户查询计划。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 计划分页结果 (按 scheduledStart DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmInteractionPlanEntity> getPlansByCustomer(Long customerId, Pageable pageable) {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            query.orderBy(cb.desc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec, pageable);
    }

    /**
     * 按负责人查询计划。
     *
     * @param ownerId   负责人 ID
     * @param pageable  分页参数
     * @return 计划分页结果 (按 scheduledStart DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmInteractionPlanEntity> getPlansByOwner(String ownerId, Pageable pageable) {
        if (ownerId == null || ownerId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerId"), ownerId));
            query.orderBy(cb.desc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec, pageable);
    }

    /**
     * 按日期范围查询计划。
     *
     * @param startDate 开始时间 (含)
     * @param endDate   结束时间 (含)
     * @param ownerId   负责人过滤（可空）
     * @return 计划列表 (按 scheduledStart ASC)
     */
    @Transactional(readOnly = true)
    public List<ScrmInteractionPlanEntity> getPlansByDateRange(LocalDateTime startDate, LocalDateTime endDate,
                                                               String ownerId) {
        if (startDate == null || endDate == null) {
            throw ScrmException.badRequest("日期范围不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw ScrmException.badRequest("开始时间不能晚于结束时间");
        }
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 计划时间区间与查询区间有交集: scheduledStart <= endDate AND scheduledEnd >= startDate
            predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStart"), endDate));
            predicates.add(cb.or(
                    cb.isNull(root.get("scheduledEnd")),
                    cb.greaterThanOrEqualTo(root.get("scheduledEnd"), startDate)));
            if (ownerId != null && !ownerId.isBlank()) {
                predicates.add(cb.equal(root.get("ownerId"), ownerId));
            }
            query.orderBy(cb.asc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec);
    }

    /**
     * 查询即将到来的计划 (未来 N 天内, 非终态)。
     *
     * @param ownerId 负责人 ID (可空, 为空则查询全部)
     * @param days    天数 (从现在起)
     * @return 计划列表 (按 scheduledStart ASC)
     */
    @Transactional(readOnly = true)
    public List<ScrmInteractionPlanEntity> getUpcomingPlans(String ownerId, int days) {
        if (days <= 0) {
            throw ScrmException.badRequest("天数必须为正数");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime horizon = now.plusDays(days);
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), now));
            predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStart"), horizon));
            predicates.add(root.get("status").in(ACTIVE_STATUSES));
            if (ownerId != null && !ownerId.isBlank()) {
                predicates.add(cb.equal(root.get("ownerId"), ownerId));
            }
            query.orderBy(cb.asc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec);
    }

    /**
     * 查询逾期计划 (计划开始时间已过且仍为非终态)。
     *
     * @param ownerId 负责人 ID (可空, 为空则查询全部)
     * @return 计划列表 (按 scheduledStart ASC)
     */
    @Transactional(readOnly = true)
    public List<ScrmInteractionPlanEntity> getOverduePlans(String ownerId) {
        LocalDateTime now = LocalDateTime.now();
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.lessThan(root.get("scheduledStart"), now));
            predicates.add(root.get("status").in(ACTIVE_STATUSES));
            if (ownerId != null && !ownerId.isBlank()) {
                predicates.add(cb.equal(root.get("ownerId"), ownerId));
            }
            query.orderBy(cb.asc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec);
    }

    /**
     * 查询今日计划。
     *
     * @param ownerId 负责人 ID (可空, 为空则查询全部)
     * @return 计划列表 (按 scheduledStart ASC)
     */
    @Transactional(readOnly = true)
    public List<ScrmInteractionPlanEntity> getTodayPlans(String ownerId) {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), dayStart));
            predicates.add(cb.lessThan(root.get("scheduledStart"), dayEnd));
            if (ownerId != null && !ownerId.isBlank()) {
                predicates.add(cb.equal(root.get("ownerId"), ownerId));
            }
            query.orderBy(cb.asc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec);
    }

    /**
     * 按状态分页查询计划。
     *
     * @param status   状态
     * @param pageable 分页参数
     * @return 计划分页结果 (按 scheduledStart DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmInteractionPlanEntity> getPlansByStatus(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            throw ScrmException.badRequest("状态不能为空");
        }
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), status));
            query.orderBy(cb.desc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec, pageable);
    }

    /**
     * 更新日历统计指标 (计划数/完成数/取消数/完成率/最近活动)。
     *
     * @param id 日历 ID
     * @return 更新后的日历
     * @throws ScrmException 日历不存在
     */
    @Transactional
    public ScrmInteractionCalendarEntity updateCalendarStats(Long id) throws ScrmException {
        ScrmInteractionCalendarEntity calendar = findCalendarOrThrow(id);
        // 提取为 final 变量, 避免 lambda 引用被重新赋值的 calendar
        final String ownerId = calendar.getOwnerId();
        // 日历没有直接关联计划, 这里以所有者 + 非终态进行统计
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("ownerId"), ownerId));
        List<ScrmInteractionPlanEntity> plans = planRepository.findAll(spec);
        int total = plans.size();
        int completed = (int) plans.stream().filter(p -> STATUS_COMPLETED.equals(p.getStatus())).count();
        int cancelled = (int) plans.stream().filter(p -> STATUS_CANCELLED.equals(p.getStatus())).count();
        LocalDate lastActivity = plans.stream()
                .map(p -> p.getScheduledStart().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(null);
        calendar.setPlanCount(total);
        calendar.setCompletedCount(completed);
        calendar.setCancelledCount(cancelled);
        calendar.setCompletionRate(total == 0 ? 0.0 : (double) completed / total);
        calendar.setLastActivityDate(lastActivity);
        calendar = calendarRepository.save(calendar);
        log.info("更新互动日历统计: id={}, total={}, completed={}", id, total, completed);
        return calendar;
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
    @Transactional(readOnly = true)
    public Map<String, Object> getCalendarSummary(Long id, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        ScrmInteractionCalendarEntity calendar = findCalendarOrThrow(id);
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerId"), calendar.getOwnerId()));
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStart"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmInteractionPlanEntity> plans = planRepository.findAll(spec);
        int total = plans.size();
        int completed = (int) plans.stream().filter(p -> STATUS_COMPLETED.equals(p.getStatus())).count();
        int cancelled = (int) plans.stream().filter(p -> STATUS_CANCELLED.equals(p.getStatus())).count();
        int noShow = (int) plans.stream().filter(p -> STATUS_NO_SHOW.equals(p.getStatus())).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("calendarId", calendar.getId());
        summary.put("calendarName", calendar.getCalendarName());
        summary.put("total", total);
        summary.put("completed", completed);
        summary.put("cancelled", cancelled);
        summary.put("noShow", noShow);
        summary.put("completionRate", total == 0 ? 0.0 : (double) completed / total);
        return summary;
    }

    /**
     * 校验日历参数。
     *
     * @param dto     日历参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateCalendarDto(ScrmInteractionCalendarDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("日历参数不能为空");
        }
        if (dto.getCalendarName() != null) {
            if (dto.getCalendarName().isBlank()) {
                throw ScrmException.badRequest("日历名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("日历名称不能为空");
        }
        if (dto.getCalendarCode() != null) {
            if (dto.getCalendarCode().isBlank()) {
                throw ScrmException.badRequest("日历编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("日历编码不能为空");
        }
        if (!partial && dto.getOwnerId() == null) {
            throw ScrmException.badRequest("所有者 ID 不能为空");
        }
        if (dto.getCalendarType() != null && !VALID_CALENDAR_TYPES.contains(dto.getCalendarType())) {
            throw ScrmException.badRequest(
                    "日历类型非法: " + dto.getCalendarType() + ", 仅支持 " + VALID_CALENDAR_TYPES);
        }
    }

    /**
     * 按主键查询日历, 不存在抛异常, 并校验账号归属。
     *
     * @param id 日历 ID
     * @return 日历实体
     * @throws ScrmException 日历不存在
     */
    private ScrmInteractionCalendarEntity findCalendarOrThrow(Long id) throws ScrmException {
        ScrmInteractionCalendarEntity entity = calendarRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "互动日历不存在: id=" + id));
        return entity;
    }

}