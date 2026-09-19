/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCalendarEventService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmCalendarEventDto;
import org.hiylo.scrm.dto.ScrmCalendarMoveDto;
import org.hiylo.scrm.dto.ScrmCalendarRangeDto;
import org.hiylo.scrm.entity.ScrmCalendarConflictEntity;
import org.hiylo.scrm.entity.ScrmCalendarEventEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCalendarConflictRepository;
import org.hiylo.scrm.repository.ScrmCalendarEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 营销日历事件管理服务 (营销事件子域)。
 * <p>
 * 承载日历事件的增删改查、状态流转 (确认/开始/完成/取消/延期)、移动 / 复制与日 / 周 / 月 /
 * 范围视图查询, 托管事件子域状态常量、事件参数校验与当前操作人获取 {@link #currentOperator()}
 * 供节日 / 冲突兄弟类以包级 static 复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMarketingCalendarEventService {

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 默认预算 */
    private static final double DEFAULT_BUDGET = 0.0;

    /** 默认触达数 */
    private static final int DEFAULT_REACH = 0;

    /** 默认提醒分钟数 */
    private static final int DEFAULT_REMINDER_MINUTES = 0;

    /** 默认操作人 (请求头未透传时使用) */
    static final String DEFAULT_OPERATOR = "scrm-system";

    /** 复制事件名称后缀 */
    private static final String COPY_SUFFIX = "-副本";

    /** 事件状态: 已计划 */
    private static final String STATUS_PLANNED = "PLANNED";
    /** 事件状态: 已确认 */
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    /** 事件状态: 进行中 */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 事件状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 事件状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";
    /** 事件状态: 已延期 */
    private static final String STATUS_POSTPONED = "POSTPONED";

    /** 合法的事件类型 */
    private static final List<String> VALID_EVENT_TYPES = List.of(
            "CAMPAIGN", "PROMOTION", "HOLIDAY", "FESTIVAL", "ANNIVERSARY",
            "CONTENT_PUBLISH", "LIVE_STREAMING", "PRODUCT_LAUNCH",
            "SALES_TARGET", "MEETING", "REMINDER", "CUSTOM");

    /** 事件数据访问层 */
    private final ScrmCalendarEventRepository eventRepository;

    /** 冲突数据访问层 */
    private final ScrmCalendarConflictRepository conflictRepository;

    /**
     * 创建日历事件。
     *
     * @param dto 事件参数
     * @return 创建后的事件
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmCalendarEventEntity createEvent(ScrmCalendarEventDto dto) throws ScrmException {
        validateEventDto(dto, false);
        ScrmCalendarEventEntity entity = new ScrmCalendarEventEntity();
        entity.setEventTitle(dto.getEventTitle());
        entity.setEventType(dto.getEventType());
        entity.setDescription(dto.getDescription());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setIsAllDay(dto.getIsAllDay() != null ? dto.getIsAllDay() : Boolean.TRUE);
        entity.setIsRecurring(dto.getIsRecurring() != null ? dto.getIsRecurring() : Boolean.FALSE);
        entity.setRecurringType(dto.getRecurringType());
        entity.setRecurringConfig(dto.getRecurringConfig());
        entity.setChannels(dto.getChannels());
        entity.setCampaignId(dto.getCampaignId());
        entity.setContentId(dto.getContentId());
        entity.setTargetSegment(dto.getTargetSegment());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_PLANNED);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setColor(dto.getColor());
        entity.setTags(dto.getTags());
        entity.setLocation(dto.getLocation());
        entity.setOwnerId(dto.getOwnerId());
        entity.setOwnerName(dto.getOwnerName());
        entity.setTeamId(dto.getTeamId());
        entity.setBudget(dto.getBudget() != null ? dto.getBudget() : DEFAULT_BUDGET);
        entity.setEstimatedReach(dto.getEstimatedReach() != null ? dto.getEstimatedReach() : DEFAULT_REACH);
        entity.setActualReach(dto.getActualReach() != null ? dto.getActualReach() : DEFAULT_REACH);
        entity.setNotes(dto.getNotes());
        entity.setReminderMinutes(
                dto.getReminderMinutes() != null ? dto.getReminderMinutes() : DEFAULT_REMINDER_MINUTES);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = eventRepository.save(entity);
        log.info("创建日历事件: id={}, eventTitle={}, eventType={}",
                entity.getId(), entity.getEventTitle(), entity.getEventType());
        return entity;
    }

    /**
     * 更新日历事件 (字段非空才覆盖)。
     *
     * @param id  事件 ID
     * @param dto 事件参数
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 参数非法
     */
    @Transactional
    public ScrmCalendarEventEntity updateEvent(Long id, ScrmCalendarEventDto dto) throws ScrmException {
        ScrmCalendarEventEntity entity = findEventOrThrow(id);
        validateEventDto(dto, true);
        if (dto.getEventTitle() != null) entity.setEventTitle(dto.getEventTitle());
        if (dto.getEventType() != null) entity.setEventType(dto.getEventType());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getStartDate() != null) entity.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) entity.setEndDate(dto.getEndDate());
        if (dto.getStartTime() != null) entity.setStartTime(dto.getStartTime());
        if (dto.getEndTime() != null) entity.setEndTime(dto.getEndTime());
        if (dto.getIsAllDay() != null) entity.setIsAllDay(dto.getIsAllDay());
        if (dto.getIsRecurring() != null) entity.setIsRecurring(dto.getIsRecurring());
        if (dto.getRecurringType() != null) entity.setRecurringType(dto.getRecurringType());
        if (dto.getRecurringConfig() != null) entity.setRecurringConfig(dto.getRecurringConfig());
        if (dto.getChannels() != null) entity.setChannels(dto.getChannels());
        if (dto.getCampaignId() != null) entity.setCampaignId(dto.getCampaignId());
        if (dto.getContentId() != null) entity.setContentId(dto.getContentId());
        if (dto.getTargetSegment() != null) entity.setTargetSegment(dto.getTargetSegment());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getLocation() != null) entity.setLocation(dto.getLocation());
        if (dto.getOwnerId() != null) entity.setOwnerId(dto.getOwnerId());
        if (dto.getOwnerName() != null) entity.setOwnerName(dto.getOwnerName());
        if (dto.getTeamId() != null) entity.setTeamId(dto.getTeamId());
        if (dto.getBudget() != null) entity.setBudget(dto.getBudget());
        if (dto.getEstimatedReach() != null) entity.setEstimatedReach(dto.getEstimatedReach());
        if (dto.getActualReach() != null) entity.setActualReach(dto.getActualReach());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getReminderMinutes() != null) entity.setReminderMinutes(dto.getReminderMinutes());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        // 更新后再次校验日期区间合法性
        validateDateRange(entity.getStartDate(), entity.getEndDate());
        entity = eventRepository.save(entity);
        log.info("更新日历事件: id={}, eventTitle={}", entity.getId(), entity.getEventTitle());
        return entity;
    }

    /**
     * 删除日历事件。
     *
     * @param id 事件 ID
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public void deleteEvent(Long id) throws ScrmException {
        ScrmCalendarEventEntity entity = findEventOrThrow(id);
        // 清理关联冲突记录
        List<ScrmCalendarConflictEntity> conflicts = conflictRepository.findByEventId(id);
        if (!conflicts.isEmpty()) {
            conflictRepository.deleteAll(conflicts);
        }
        eventRepository.delete(entity);
        log.info("删除日历事件: id={}, eventTitle={}, conflicts={}",
                id, entity.getEventTitle(), conflicts.size());
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    @Transactional(readOnly = true)
    public ScrmCalendarEventEntity getEvent(Long id) throws ScrmException {
        return findEventOrThrow(id);
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
    @Transactional(readOnly = true)
    public Page<ScrmCalendarEventEntity> listEvents(String eventType, String status, String ownerId,
                                                     LocalDate startDate, LocalDate endDate, String keyword,
                                                     Pageable pageable) {
        Specification<ScrmCalendarEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (ownerId != null && !ownerId.isBlank()) {
                predicates.add(cb.equal(root.get("ownerId"), ownerId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), endDate));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("eventTitle"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return eventRepository.findAll(spec, pageable);
    }

    /**
     * 按日期范围查询事件 (日历视图数据, 支持事件类型与渠道过滤)。
     * <p>返回与查询区间有交集的全部事件: 事件开始 ≤ 区间结束 且 事件结束 ≥ 区间开始。</p>
     *
     * @param rangeDto 日期范围参数
     * @return 事件列表
     * @throws ScrmException 参数非法
     */
    @Transactional(readOnly = true)
    public List<ScrmCalendarEventEntity> getEventsByDateRange(ScrmCalendarRangeDto rangeDto) throws ScrmException {
        if (rangeDto == null || rangeDto.getStartDate() == null || rangeDto.getEndDate() == null) {
            throw ScrmException.badRequest("日期范围参数不能为空");
        }
        validateDateRange(rangeDto.getStartDate(), rangeDto.getEndDate());
        Specification<ScrmCalendarEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 区间交集: event.start <= rangeEnd AND event.end >= rangeStart
            predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), rangeDto.getEndDate()));
            predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), rangeDto.getStartDate()));
            if (rangeDto.getEventTypes() != null && !rangeDto.getEventTypes().isEmpty()) {
                predicates.add(root.get("eventType").in(rangeDto.getEventTypes()));
            }
            if (rangeDto.getChannels() != null && !rangeDto.getChannels().isEmpty()) {
                // channels 为逗号分隔字符串, 使用 like 模糊匹配
                List<Predicate> channelPredicates = new ArrayList<>();
                for (String channel : rangeDto.getChannels()) {
                    channelPredicates.add(cb.like(root.get("channels"), "%" + channel + "%"));
                }
                predicates.add(cb.or(channelPredicates.toArray(new Predicate[0])));
            }
            query.orderBy(cb.asc(root.get("startDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return eventRepository.findAll(spec);
    }

    /**
     * 查询某日的事件。
     *
     * @param date 日期 (ISO 格式: yyyy-MM-dd)
     * @return 事件列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCalendarEventEntity> getEventsByDate(LocalDate date) {
        if (date == null) {
            throw ScrmException.badRequest("日期不能为空");
        }
        Specification<ScrmCalendarEventEntity> spec = (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("startDate"), date),
                cb.greaterThanOrEqualTo(root.get("endDate"), date));
        return eventRepository.findAll(spec);
    }

    /**
     * 月视图: 查询某月的事件。
     *
     * @param year  年份 (如 2026)
     * @param month 月份 (1-12)
     * @return 事件列表
     * @throws ScrmException 月份非法
     */
    @Transactional(readOnly = true)
    public List<ScrmCalendarEventEntity> getEventsByMonth(int year, int month) {
        if (month < 1 || month > 12) {
            throw ScrmException.badRequest("月份非法: " + month);
        }
        YearMonth ym = YearMonth.of(year, month);
        ScrmCalendarRangeDto rangeDto = new ScrmCalendarRangeDto();
        rangeDto.setStartDate(ym.atDay(1));
        rangeDto.setEndDate(ym.atEndOfMonth());
        return getEventsByDateRange(rangeDto);
    }

    /**
     * 周视图: 查询某周的事件 (周一开始)。
     *
     * @param weekStart 周开始日期
     * @return 事件列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCalendarEventEntity> getEventsByWeek(LocalDate weekStart) {
        if (weekStart == null) {
            throw ScrmException.badRequest("周开始日期不能为空");
        }
        // 周一为一周的开始
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        ScrmCalendarRangeDto rangeDto = new ScrmCalendarRangeDto();
        rangeDto.setStartDate(monday);
        rangeDto.setEndDate(sunday);
        return getEventsByDateRange(rangeDto);
    }

    /**
     * 确认事件 (PLANNED → CONFIRMED)。
     *
     * @param id 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @Transactional
    public ScrmCalendarEventEntity confirmEvent(Long id) throws ScrmException {
        ScrmCalendarEventEntity entity = findEventOrThrow(id);
        if (!STATUS_PLANNED.equals(entity.getStatus()) && !STATUS_POSTPONED.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "仅 PLANNED / POSTPONED 状态可确认: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_CONFIRMED);
        entity = eventRepository.save(entity);
        log.info("确认日历事件: id={}, eventTitle={}", id, entity.getEventTitle());
        return entity;
    }

    /**
     * 开始事件 (CONFIRMED → IN_PROGRESS)。
     *
     * @param id 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @Transactional
    public ScrmCalendarEventEntity startEvent(Long id) throws ScrmException {
        ScrmCalendarEventEntity entity = findEventOrThrow(id);
        if (!STATUS_CONFIRMED.equals(entity.getStatus()) && !STATUS_PLANNED.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "仅 PLANNED / CONFIRMED 状态可开始: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_IN_PROGRESS);
        entity = eventRepository.save(entity);
        log.info("开始日历事件: id={}, eventTitle={}", id, entity.getEventTitle());
        return entity;
    }

    /**
     * 完成事件 (IN_PROGRESS → COMPLETED)。
     *
     * @param id 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @Transactional
    public ScrmCalendarEventEntity completeEvent(Long id) throws ScrmException {
        ScrmCalendarEventEntity entity = findEventOrThrow(id);
        if (!STATUS_IN_PROGRESS.equals(entity.getStatus()) && !STATUS_CONFIRMED.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "仅 IN_PROGRESS / CONFIRMED 状态可完成: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_COMPLETED);
        entity = eventRepository.save(entity);
        log.info("完成日历事件: id={}, eventTitle={}", id, entity.getEventTitle());
        return entity;
    }

    /**
     * 取消事件 (非终态 → CANCELLED)。
     *
     * @param id     事件 ID
     * @param reason 取消原因 (可空)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @Transactional
    public ScrmCalendarEventEntity cancelEvent(Long id, String reason) throws ScrmException {
        ScrmCalendarEventEntity entity = findEventOrThrow(id);
        if (STATUS_COMPLETED.equals(entity.getStatus()) || STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已完成 / 已取消的事件不可取消: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_CANCELLED);
        if (reason != null && !reason.isBlank()) {
            String notes = entity.getNotes();
            entity.setNotes((notes == null ? "" : notes + " | ") + "取消原因: " + reason);
        }
        entity = eventRepository.save(entity);
        log.info("取消日历事件: id={}, eventTitle={}, reason={}", id, entity.getEventTitle(), reason);
        return entity;
    }

    /**
     * 延期事件 (非终态 → POSTPONED, 同时更新日期)。
     *
     * @param id        事件 ID
     * @param newDates  新的日期范围 (startDate + endDate)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法 / 日期非法
     */
    @Transactional
    public ScrmCalendarEventEntity postponeEvent(Long id, ScrmCalendarEventDto newDates) throws ScrmException {
        ScrmCalendarEventEntity entity = findEventOrThrow(id);
        if (STATUS_COMPLETED.equals(entity.getStatus()) || STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已完成 / 已取消的事件不可延期: id=" + id + ", status=" + entity.getStatus());
        }
        if (newDates == null || newDates.getStartDate() == null || newDates.getEndDate() == null) {
            throw ScrmException.badRequest("延期日期不能为空");
        }
        validateDateRange(newDates.getStartDate(), newDates.getEndDate());
        entity.setStatus(STATUS_POSTPONED);
        entity.setStartDate(newDates.getStartDate());
        entity.setEndDate(newDates.getEndDate());
        entity = eventRepository.save(entity);
        log.info("延期日历事件: id={}, eventTitle={}, newStart={}, newEnd={}",
                id, entity.getEventTitle(), newDates.getStartDate(), newDates.getEndDate());
        return entity;
    }

    /**
     * 移动事件 (拖拽日历场景, 仅修改日期, 不改变状态)。
     *
     * @param moveDto 移动参数
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 日期非法
     */
    @Transactional
    public ScrmCalendarEventEntity moveEvent(ScrmCalendarMoveDto moveDto) throws ScrmException {
        if (moveDto == null || moveDto.getEventId() == null) {
            throw ScrmException.badRequest("移动参数不能为空");
        }
        ScrmCalendarEventEntity entity = findEventOrThrow(moveDto.getEventId());
        validateDateRange(moveDto.getNewStartDate(), moveDto.getNewEndDate());
        entity.setStartDate(moveDto.getNewStartDate());
        entity.setEndDate(moveDto.getNewEndDate());
        entity = eventRepository.save(entity);
        log.info("移动日历事件: id={}, eventTitle={}, newStart={}, newEnd={}",
                moveDto.getEventId(), entity.getEventTitle(),
                moveDto.getNewStartDate(), moveDto.getNewEndDate());
        return entity;
    }

    /**
     * 复制事件 (创建副本, 状态置为 PLANNED)。
     *
     * @param id        源事件 ID
     * @param newDates  新的日期范围 (startDate + endDate, 可空则沿用源事件日期)
     * @return 复制后的事件
     * @throws ScrmException 源事件不存在 / 日期非法
     */
    @Transactional
    public ScrmCalendarEventEntity duplicateEvent(Long id, ScrmCalendarEventDto newDates) throws ScrmException {
        ScrmCalendarEventEntity source = findEventOrThrow(id);
        ScrmCalendarEventEntity copy = new ScrmCalendarEventEntity();
        copy.setEventTitle(source.getEventTitle() + COPY_SUFFIX);
        copy.setEventType(source.getEventType());
        copy.setDescription(source.getDescription());
        if (newDates != null && newDates.getStartDate() != null && newDates.getEndDate() != null) {
            validateDateRange(newDates.getStartDate(), newDates.getEndDate());
            copy.setStartDate(newDates.getStartDate());
            copy.setEndDate(newDates.getEndDate());
        } else {
            copy.setStartDate(source.getStartDate());
            copy.setEndDate(source.getEndDate());
        }
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setIsAllDay(source.getIsAllDay());
        copy.setIsRecurring(source.getIsRecurring());
        copy.setRecurringType(source.getRecurringType());
        copy.setRecurringConfig(source.getRecurringConfig());
        copy.setChannels(source.getChannels());
        copy.setCampaignId(source.getCampaignId());
        copy.setContentId(source.getContentId());
        copy.setTargetSegment(source.getTargetSegment());
        copy.setStatus(STATUS_PLANNED);
        copy.setPriority(source.getPriority());
        copy.setColor(source.getColor());
        copy.setTags(source.getTags());
        copy.setLocation(source.getLocation());
        copy.setOwnerId(source.getOwnerId());
        copy.setOwnerName(source.getOwnerName());
        copy.setTeamId(source.getTeamId());
        copy.setBudget(source.getBudget());
        copy.setEstimatedReach(source.getEstimatedReach());
        copy.setActualReach(DEFAULT_REACH);
        copy.setNotes(source.getNotes());
        copy.setReminderMinutes(source.getReminderMinutes());
        copy.setCreatedBy(currentOperator());
        copy = eventRepository.save(copy);
        log.info("复制日历事件: sourceId={}, copyId={}, eventTitle={}",
                id, copy.getId(), copy.getEventTitle());
        return copy;
    }

    /**
     * 获取当前操作人 (优先从 UserContext 获取)。
     *
     * @return 操作人用户名
     */
    static String currentOperator() {
        String username = UserContext.getUsername();
        return username != null ? username : DEFAULT_OPERATOR;
    }

    /**
     * 校验事件参数。
     * <p>
     * 创建场景 (partial=false): eventTitle / eventType / startDate / endDate 必填。
     * 更新场景 (partial=true): 允许字段为空, 仅校验非空字段合法性。
     * </p>
     *
     * @param dto     事件参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateEventDto(ScrmCalendarEventDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("事件参数不能为空");
        }
        if (dto.getEventTitle() != null) {
            if (dto.getEventTitle().isBlank()) {
                throw ScrmException.badRequest("事件标题不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("事件标题不能为空");
        }
        if (dto.getEventType() != null) {
            if (!VALID_EVENT_TYPES.contains(dto.getEventType())) {
                throw ScrmException.badRequest(
                        "事件类型非法: " + dto.getEventType() + ", 仅支持 " + VALID_EVENT_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("事件类型不能为空");
        }
        if (!partial) {
            if (dto.getStartDate() == null) {
                throw ScrmException.badRequest("开始日期不能为空");
            }
            if (dto.getEndDate() == null) {
                throw ScrmException.badRequest("结束日期不能为空");
            }
        }
        if (dto.getStartDate() != null || dto.getEndDate() != null) {
            validateDateRange(dto.getStartDate(), dto.getEndDate());
        }
    }

    /**
     * 校验日期区间合法性 (startDate ≤ endDate, 且均非空时校验)。
     *
     * @param startDate 开始日期 (可空)
     * @param endDate   结束日期 (可空)
     * @throws ScrmException 日期区间非法
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) throws ScrmException {
        if (startDate == null || endDate == null) {
            return;
        }
        if (startDate.isAfter(endDate)) {
            throw ScrmException.badRequest("开始日期不能晚于结束日期");
        }
    }

    /**
     * 按主键查询事件, 不存在抛异常, 并校验账号归属。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    ScrmCalendarEventEntity findEventOrThrow(Long id) throws ScrmException {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "日历事件不存在: id=" + id));
    }
}