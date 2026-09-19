/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCalendarServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmCalendarEventDto;
import org.hiylo.scrm.dto.ScrmCalendarHolidayDto;
import org.hiylo.scrm.dto.ScrmCalendarMoveDto;
import org.hiylo.scrm.entity.ScrmCalendarConflictEntity;
import org.hiylo.scrm.entity.ScrmCalendarEventEntity;
import org.hiylo.scrm.entity.ScrmCalendarHolidayEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCalendarConflictRepository;
import org.hiylo.scrm.repository.ScrmCalendarEventRepository;
import org.hiylo.scrm.repository.ScrmCalendarHolidayRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmMarketingCalendarService 单元测试
 * <p>
 * 聚焦营销日历事件管理 (创建校验 / 默认值填充 / 状态流转 / 取消 / 复制 / 移动)、
 * 节日管理 (创建 / 默认值填充 / 营销建议分级)、冲突检测 (时间重叠 / 渠道冲突 /
 * 客群重叠 / 预算超支 / 事件对去重)、冲突解决 (RESOLVED / IGNORED 状态保护)、
 * 日历统计 (状态 / 类型聚合 / 完成率 / 总预算) 与越权隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmMarketingCalendarService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmMarketingCalendarServiceTest {

    /** 营销日历事件仓库 Mock */
    @Mock
    private ScrmCalendarEventRepository eventRepository;
    /** 营销日历节日仓库 Mock */
    @Mock
    private ScrmCalendarHolidayRepository holidayRepository;
    /** 营销日历冲突仓库 Mock */
    @Mock
    private ScrmCalendarConflictRepository conflictRepository;

    /** 被测服务实例 */
    private ScrmMarketingCalendarService service;
    /** 营销事件兄弟服务 (持有 validateDateRange 私有方法) */
    private ScrmMarketingCalendarEventService eventService;
    /** 节假日兄弟服务 (持有 resolveHolidayDate 私有方法) */
    private ScrmMarketingCalendarHolidayService holidayService;
    /** 统计兄弟服务 (持有 parseCsv 私有方法) */
    private ScrmMarketingCalendarStatsService statsService;

    @BeforeEach
    void setUp() {
        eventService = new ScrmMarketingCalendarEventService(eventRepository,
                conflictRepository);
        holidayService = new ScrmMarketingCalendarHolidayService(holidayRepository);
        ScrmMarketingCalendarConflictService conflictService = new ScrmMarketingCalendarConflictService(eventRepository,
                conflictRepository);
        statsService = new ScrmMarketingCalendarStatsService(eventRepository);
        service = new ScrmMarketingCalendarService(eventService, holidayService, conflictService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 事件管理 ====================

    @Test
    @DisplayName("createEvent: 成功创建, status/priority/isAllDay/isRecurring 缺省填充默认值")
    void createEvent_success_defaultsFilled() throws Exception {
        ScrmCalendarEventDto dto = buildEventDto();
        dto.setStatus(null);
        dto.setPriority(null);
        dto.setIsAllDay(null);
        dto.setIsRecurring(null);
        when(eventRepository.save(any(ScrmCalendarEventEntity.class)))
                .thenAnswer(inv -> assignEventId(inv.getArgument(0), 100L));

        ScrmCalendarEventEntity result = service.createEvent(dto);

        assertThat(result.getId()).isEqualTo(100L);
        ArgumentCaptor<ScrmCalendarEventEntity> captor = ArgumentCaptor.forClass(ScrmCalendarEventEntity.class);
        verify(eventRepository).save(captor.capture());
        ScrmCalendarEventEntity saved = captor.getValue();
        assertThat(saved.getEventTitle()).isEqualTo("夏季大促");
        assertThat(saved.getEventType()).isEqualTo("PROMOTION");
        assertThat(saved.getStatus()).isEqualTo("PLANNED");
        assertThat(saved.getPriority()).isZero();
        assertThat(saved.getIsAllDay()).isTrue();
        assertThat(saved.getIsRecurring()).isFalse();
        assertThat(saved.getBudget()).isEqualTo(0.0);
        assertThat(saved.getEstimatedReach()).isZero();
    }

    @Test
    @DisplayName("createEvent: eventType 非法时抛 BAD_REQUEST, 不写入仓库")
    void createEvent_invalidEventType_badRequest() {
        ScrmCalendarEventDto dto = buildEventDto();
        dto.setEventType("INVALID_TYPE");

        assertThatThrownBy(() -> service.createEvent(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("事件类型非法");
        verify(eventRepository, never()).save(any(ScrmCalendarEventEntity.class));
    }

    @Test
    @DisplayName("createEvent: startDate 晚于 endDate 时抛 BAD_REQUEST")
    void createEvent_invalidDateRange_badRequest() {
        ScrmCalendarEventDto dto = buildEventDto();
        dto.setStartDate(LocalDate.of(2026, 8, 10));
        dto.setEndDate(LocalDate.of(2026, 8, 5));

        assertThatThrownBy(() -> service.createEvent(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");
    }

    
    @Test
    @DisplayName("confirmEvent: PLANNED → CONFIRMED")
    void confirmEvent_success() throws Exception {
        ScrmCalendarEventEntity entity = buildEventEntity(100L);
        entity.setStatus("PLANNED");
        when(eventRepository.findById(100L)).thenReturn(Optional.of(entity));
        when(eventRepository.save(any(ScrmCalendarEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCalendarEventEntity result = service.confirmEvent(100L);

        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("confirmEvent: COMPLETED 状态抛 CONFLICT, 状态非法")
    void confirmEvent_invalidState_conflict() {
        ScrmCalendarEventEntity entity = buildEventEntity(100L);
        entity.setStatus("COMPLETED");
        when(eventRepository.findById(100L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.confirmEvent(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PLANNED / POSTPONED 状态可确认");
        verify(eventRepository, never()).save(any(ScrmCalendarEventEntity.class));
    }

    @Test
    @DisplayName("cancelEvent: 非终态 → CANCELLED, reason 追加写入 notes")
    void cancelEvent_success_appendsNotes() throws Exception {
        ScrmCalendarEventEntity entity = buildEventEntity(100L);
        entity.setStatus("CONFIRMED");
        entity.setNotes("已有备注");
        when(eventRepository.findById(100L)).thenReturn(Optional.of(entity));
        when(eventRepository.save(any(ScrmCalendarEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCalendarEventEntity result = service.cancelEvent(100L, "预算不足");

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getNotes()).contains("已有备注");
        assertThat(result.getNotes()).contains("取消原因: 预算不足");
    }

    @Test
    @DisplayName("cancelEvent: 已完成状态抛 CONFLICT, 终态保护")
    void cancelEvent_terminalState_conflict() {
        ScrmCalendarEventEntity entity = buildEventEntity(100L);
        entity.setStatus("COMPLETED");
        when(eventRepository.findById(100L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.cancelEvent(100L, "测试"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已完成 / 已取消的事件不可取消");
    }

    @Test
    @DisplayName("deleteEvent: 删除事件时级联清理关联冲突记录")
    void deleteEvent_cascadesConflicts() throws Exception {
        ScrmCalendarEventEntity entity = buildEventEntity(100L);
        when(eventRepository.findById(100L)).thenReturn(Optional.of(entity));
        List<ScrmCalendarConflictEntity> conflicts = List.of(
                buildConflict(200L, 100L, 101L));
        when(conflictRepository.findByEventId(100L)).thenReturn(conflicts);

        service.deleteEvent(100L);

        verify(conflictRepository).deleteAll(conflicts);
        verify(eventRepository).delete(entity);
    }

    @Test
    @DisplayName("duplicateEvent: 复制事件, 标题加 -副本, 状态置为 PLANNED, actualReach 重置 0")
    void duplicateEvent_success() throws Exception {
        ScrmCalendarEventEntity source = buildEventEntity(100L);
        source.setEventTitle("夏季大促");
        source.setStatus("COMPLETED");
        source.setActualReach(5000);
        when(eventRepository.findById(100L)).thenReturn(Optional.of(source));
        when(eventRepository.save(any(ScrmCalendarEventEntity.class)))
                .thenAnswer(inv -> assignEventId(inv.getArgument(0), 200L));

        ScrmCalendarEventEntity copy = service.duplicateEvent(100L, null);

        assertThat(copy.getId()).isEqualTo(200L);
        assertThat(copy.getEventTitle()).isEqualTo("夏季大促-副本");
        assertThat(copy.getStatus()).isEqualTo("PLANNED");
        assertThat(copy.getActualReach()).isZero();
        assertThat(copy.getStartDate()).isEqualTo(source.getStartDate());
        ArgumentCaptor<ScrmCalendarEventEntity> captor = ArgumentCaptor.forClass(ScrmCalendarEventEntity.class);
        verify(eventRepository).save(captor.capture());
    }

    @Test
    @DisplayName("moveEvent: 移动事件仅修改日期, 不改变状态")
    void moveEvent_success_updatesDatesOnly() throws Exception {
        ScrmCalendarEventEntity entity = buildEventEntity(100L);
        entity.setStatus("CONFIRMED");
        when(eventRepository.findById(100L)).thenReturn(Optional.of(entity));
        when(eventRepository.save(any(ScrmCalendarEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCalendarMoveDto moveDto = new ScrmCalendarMoveDto();
        moveDto.setEventId(100L);
        moveDto.setNewStartDate(LocalDate.of(2026, 9, 1));
        moveDto.setNewEndDate(LocalDate.of(2026, 9, 3));

        ScrmCalendarEventEntity result = service.moveEvent(moveDto);

        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("getEventsByMonth: 月份非法 (13) 抛 BAD_REQUEST")
    void getEventsByMonth_invalidMonth_badRequest() {
        assertThatThrownBy(() -> service.getEventsByMonth(2026, 13))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("月份非法");
    }

    // ==================== 节日管理 ====================

    @Test
    @DisplayName("createHoliday: 成功创建, isActive/country/marketingOpportunity/durationDays 缺省填充")
    void createHoliday_success_defaultsFilled() throws Exception {
        ScrmCalendarHolidayDto dto = buildHolidayDto();
        dto.setIsActive(null);
        dto.setCountry(null);
        dto.setMarketingOpportunity(null);
        dto.setDurationDays(null);
        dto.setIsLunar(null);
        when(holidayRepository.save(any(ScrmCalendarHolidayEntity.class)))
                .thenAnswer(inv -> assignHolidayId(inv.getArgument(0), 300L));

        ScrmCalendarHolidayEntity result = service.createHoliday(dto);

        assertThat(result.getId()).isEqualTo(300L);
        ArgumentCaptor<ScrmCalendarHolidayEntity> captor =
                ArgumentCaptor.forClass(ScrmCalendarHolidayEntity.class);
        verify(holidayRepository).save(captor.capture());
        ScrmCalendarHolidayEntity saved = captor.getValue();
        assertThat(saved.getHolidayName()).isEqualTo("国庆节");
        assertThat(saved.getHolidayType()).isEqualTo("PUBLIC_HOLIDAY");
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getCountry()).isEqualTo("CN");
        assertThat(saved.getMarketingOpportunity()).isEqualTo("MEDIUM");
        assertThat(saved.getDurationDays()).isEqualTo(1);
        assertThat(saved.getIsLunar()).isFalse();
    }

    @Test
    @DisplayName("createHoliday: holidayType 非法时抛 BAD_REQUEST")
    void createHoliday_invalidType_badRequest() {
        ScrmCalendarHolidayDto dto = buildHolidayDto();
        dto.setHolidayType("INVALID");

        assertThatThrownBy(() -> service.createHoliday(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("节日类型非法");
        verify(holidayRepository, never()).save(any(ScrmCalendarHolidayEntity.class));
    }

    @Test
    @DisplayName("suggestMarketingActions: HIGH 营销机会返回 P0 优先级建议")
    void suggestMarketingActions_highOpportunity() throws Exception {
        ScrmCalendarHolidayEntity holiday = buildHolidayEntity(300L);
        holiday.setMarketingOpportunity("HIGH");
        holiday.setSuggestedActions("多渠道促销,限时折扣");
        holiday.setSuggestedChannels("微信,短信,推送");
        when(holidayRepository.findById(300L)).thenReturn(Optional.of(holiday));

        Map<String, Object> suggestion = service.suggestMarketingActions(300L);

        assertThat(suggestion.get("holidayId")).isEqualTo(300L);
        assertThat(suggestion.get("marketingOpportunity")).isEqualTo("HIGH");
        @SuppressWarnings("unchecked")
        List<String> actions = (List<String>) suggestion.get("suggestedActions");
        assertThat(actions).containsExactly("多渠道促销", "限时折扣");
        @SuppressWarnings("unchecked")
        List<String> channels = (List<String>) suggestion.get("suggestedChannels");
        assertThat(channels).containsExactly("微信", "短信", "推送");
        @SuppressWarnings("unchecked")
        Map<String, String> recommendation = (Map<String, String>) suggestion.get("recommendation");
        assertThat(recommendation.get("priority")).isEqualTo("P0");
        assertThat(recommendation.get("advice")).contains("营销机会高");
    }

    // ==================== 冲突检测 ====================

    @Test
    @DisplayName("detectConflicts: 时间重叠 + 渠道冲突时生成冲突记录, 已存在的冲突对跳过")
    void detectConflicts_timeAndChannelOverlap() throws Exception {
        ScrmCalendarEventEntity target = buildEventEntity(100L);
        target.setStartDate(LocalDate.of(2026, 8, 1));
        target.setEndDate(LocalDate.of(2026, 8, 5));
        target.setChannels("微信,短信");
        target.setBudget(1000d);
        ScrmCalendarEventEntity other = buildEventEntity(101L);
        other.setStartDate(LocalDate.of(2026, 8, 3));
        other.setEndDate(LocalDate.of(2026, 8, 7));
        other.setChannels("微信,推送");
        other.setBudget(500d);
        when(eventRepository.findById(100L)).thenReturn(Optional.of(target));
        when(eventRepository.findAll(any(Specification.class))).thenReturn(List.of(other));
        // 事件对未检测过
        when(conflictRepository.findByEventPair(100L, 101L)).thenReturn(Collections.emptyList());

        List<ScrmCalendarConflictEntity> conflicts = service.detectConflicts(100L);

        // 时间重叠 + 渠道冲突 (微信) = 2 条冲突记录
        assertThat(conflicts).hasSize(2);
        ArgumentCaptor<List<ScrmCalendarConflictEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(conflictRepository).saveAll(captor.capture());
        List<ScrmCalendarConflictEntity> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getConflictType()).isEqualTo("TIME_OVERLAP");
        assertThat(saved.get(0).getSeverity()).isEqualTo("WARNING");
        assertThat(saved.get(0).getResolvedStatus()).isEqualTo("UNRESOLVED");
        assertThat(saved.get(1).getConflictType()).isEqualTo("CHANNEL_CONFLICT");
        assertThat(saved.get(1).getOverlappingChannels()).isEqualTo("微信");
    }

    @Test
    @DisplayName("detectConflicts: 已存在冲突记录的事件对跳过, 不重复检测")
    void detectConflicts_existingPair_skipped() throws Exception {
        ScrmCalendarEventEntity target = buildEventEntity(100L);
        target.setStartDate(LocalDate.of(2026, 8, 1));
        target.setEndDate(LocalDate.of(2026, 8, 5));
        target.setChannels("微信");
        ScrmCalendarEventEntity other = buildEventEntity(101L);
        other.setStartDate(LocalDate.of(2026, 8, 3));
        other.setEndDate(LocalDate.of(2026, 8, 7));
        other.setChannels("微信");
        when(eventRepository.findById(100L)).thenReturn(Optional.of(target));
        when(eventRepository.findAll(any(Specification.class))).thenReturn(List.of(other));
        // 事件对已检测过
        when(conflictRepository.findByEventPair(100L, 101L))
                .thenReturn(List.of(buildConflict(200L, 100L, 101L)));

        List<ScrmCalendarConflictEntity> conflicts = service.detectConflicts(100L);

        assertThat(conflicts).isEmpty();
        verify(conflictRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("resolveConflict: UNRESOLVED → RESOLVED, 写入 resolutionNote")
    void resolveConflict_success() throws Exception {
        ScrmCalendarConflictEntity conflict = buildConflict(200L, 100L, 101L);
        conflict.setResolvedStatus("UNRESOLVED");
        when(conflictRepository.findById(200L)).thenReturn(Optional.of(conflict));
        when(conflictRepository.save(any(ScrmCalendarConflictEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCalendarConflictEntity result = service.resolveConflict(200L, "已协商调整时间");

        assertThat(result.getResolvedStatus()).isEqualTo("RESOLVED");
        assertThat(result.getResolutionNote()).isEqualTo("已协商调整时间");
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("resolveConflict: 已 RESOLVED 状态抛 CONFLICT, 不可重复解决")
    void resolveConflict_alreadyResolved_conflict() {
        ScrmCalendarConflictEntity conflict = buildConflict(200L, 100L, 101L);
        conflict.setResolvedStatus("RESOLVED");
        when(conflictRepository.findById(200L)).thenReturn(Optional.of(conflict));

        assertThatThrownBy(() -> service.resolveConflict(200L, "再次解决"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("冲突已解决, 不可重复解决");
        verify(conflictRepository, never()).save(any(ScrmCalendarConflictEntity.class));
    }

    @Test
    @DisplayName("ignoreConflict: 已 RESOLVED 状态抛 CONFLICT, 不可忽略")
    void ignoreConflict_alreadyResolved_conflict() {
        ScrmCalendarConflictEntity conflict = buildConflict(200L, 100L, 101L);
        conflict.setResolvedStatus("RESOLVED");
        when(conflictRepository.findById(200L)).thenReturn(Optional.of(conflict));

        assertThatThrownBy(() -> service.ignoreConflict(200L, "忽略"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("冲突已解决, 不可忽略");
    }

    // ==================== 统计 ====================

    @Test
    @DisplayName("getCalendarStats: 聚合状态/类型计数, 计算完成率与总预算")
    void getCalendarStats_aggregates() {
        when(eventRepository.countByStatus(any(), any()))
                .thenReturn(List.of(
                        new Object[]{"PLANNED", 2L},
                        new Object[]{"COMPLETED", 3L},
                        new Object[]{"CANCELLED", 1L}));
        when(eventRepository.countByEventType(any(), any()))
                .thenReturn(List.of(
                        new Object[]{"PROMOTION", 4L},
                        new Object[]{"CAMPAIGN", 2L}));
        when(eventRepository.sumBudget(any(), any())).thenReturn(50000d);

        Map<String, Object> stats = service.getCalendarStats(null, null);

        assertThat(stats.get("total")).isEqualTo(6L);
        @SuppressWarnings("unchecked")
        Map<String, Long> statusCount = (Map<String, Long>) stats.get("statusCount");
        assertThat(statusCount.get("PLANNED")).isEqualTo(2L);
        assertThat(statusCount.get("COMPLETED")).isEqualTo(3L);
        assertThat(statusCount.get("CANCELLED")).isEqualTo(1L);
        assertThat(statusCount.get("CONFIRMED")).isZero();
        assertThat(stats.get("completed")).isEqualTo(3L);
        assertThat((Double) stats.get("completionRate")).isEqualTo(3.0 / 6.0);
        assertThat((Double) stats.get("totalBudget")).isEqualTo(50000d);
    }

    @Test
    @DisplayName("getOwnerWorkload: ownerId 为空抛 BAD_REQUEST")
    void getOwnerWorkload_blankOwnerId_badRequest() {
        assertThatThrownBy(() -> service.getOwnerWorkload("", null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("负责人 ID 不能为空");
    }

    @Test
    @DisplayName("getOwnerWorkload: 聚合负责人事件数与预算")
    void getOwnerWorkload_aggregates() {
        when(eventRepository.aggregateByOwner(eq("u001"), any(), any()))
                .thenReturn(Collections.singletonList(new Object[]{"u001", "张三", 5L, 20000d, 10000L}));

        Map<String, Object> workload = service.getOwnerWorkload("u001", null, null);

        assertThat(workload.get("ownerId")).isEqualTo("u001");
        assertThat(workload.get("ownerName")).isEqualTo("张三");
        assertThat(workload.get("eventCount")).isEqualTo(5L);
        assertThat((Double) workload.get("totalBudget")).isEqualTo(20000d);
        assertThat(workload.get("totalEstimatedReach")).isEqualTo(10000L);
    }

    // ==================== 私有方法 ====================

    @Test
    @DisplayName("parseCsv: null/空串返回空列表, 逗号分隔去空白")
    void parseCsv_parses() {
        List<String> empty1 = ReflectionTestUtils.invokeMethod(statsService, "parseCsv", (String) null);
        assertThat(empty1).isEmpty();
        List<String> empty2 = ReflectionTestUtils.invokeMethod(statsService, "parseCsv", "  ");
        assertThat(empty2).isEmpty();
        List<String> list = ReflectionTestUtils.invokeMethod(statsService, "parseCsv", "微信, 短信 ,推送");
        assertThat(list).containsExactly("微信", "短信", "推送");
    }

    @Test
    @DisplayName("resolveHolidayDate: MM-dd 格式按年份解析, yyyy-MM-dd 直接解析, 非法返回 null")
    void resolveHolidayDate_parses() {
        LocalDate md = ReflectionTestUtils.invokeMethod(holidayService, "resolveHolidayDate", "10-01", 2026);
        assertThat(md).isEqualTo(LocalDate.of(2026, 10, 1));
        LocalDate ymd = ReflectionTestUtils.invokeMethod(holidayService, "resolveHolidayDate", "2026-08-05", 2026);
        assertThat(ymd).isEqualTo(LocalDate.of(2026, 8, 5));
        LocalDate invalid = ReflectionTestUtils.invokeMethod(holidayService, "resolveHolidayDate", "invalid", 2026);
        assertThat(invalid).isNull();
    }

    @Test
    @DisplayName("validateDateRange: 开始晚于结束抛 BAD_REQUEST")
    void validateDateRange_invalid() {
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(eventService, "validateDateRange",
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 5)))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");
    }

    // ==================== 辅助构建方法 ====================

    private ScrmCalendarEventDto buildEventDto() {
        ScrmCalendarEventDto dto = new ScrmCalendarEventDto();
        dto.setEventTitle("夏季大促");
        dto.setEventType("PROMOTION");
        dto.setStartDate(LocalDate.of(2026, 8, 1));
        dto.setEndDate(LocalDate.of(2026, 8, 5));
        dto.setChannels("微信,短信");
        dto.setOwnerId("u001");
        dto.setOwnerName("张三");
        return dto;
    }

    private ScrmCalendarHolidayDto buildHolidayDto() {
        ScrmCalendarHolidayDto dto = new ScrmCalendarHolidayDto();
        dto.setHolidayName("国庆节");
        dto.setHolidayType("PUBLIC_HOLIDAY");
        dto.setHolidayDate("10-01");
        dto.setDescription("国庆长假");
        return dto;
    }

    private ScrmCalendarEventEntity buildEventEntity(Long id) {
        ScrmCalendarEventEntity entity = new ScrmCalendarEventEntity();
        entity.setId(id);
        entity.setEventTitle("夏季大促");
        entity.setEventType("PROMOTION");
        entity.setStartDate(LocalDate.of(2026, 8, 1));
        entity.setEndDate(LocalDate.of(2026, 8, 5));
        entity.setStatus("PLANNED");
        entity.setBudget(1000d);
        entity.setEstimatedReach(1000);
        entity.setActualReach(0);
        return entity;
    }

    private ScrmCalendarHolidayEntity buildHolidayEntity(Long id) {
        ScrmCalendarHolidayEntity entity = new ScrmCalendarHolidayEntity();
        entity.setId(id);
        entity.setHolidayName("国庆节");
        entity.setHolidayType("PUBLIC_HOLIDAY");
        entity.setHolidayDate("10-01");
        entity.setMarketingOpportunity("HIGH");
        entity.setIsActive(true);
        entity.setCountry("CN");
        return entity;
    }

    private ScrmCalendarConflictEntity buildConflict(Long id, Long event1Id, Long event2Id) {
        ScrmCalendarConflictEntity entity = new ScrmCalendarConflictEntity();
        entity.setId(id);
        entity.setEvent1Id(event1Id);
        entity.setEvent2Id(event2Id);
        entity.setConflictType("TIME_OVERLAP");
        entity.setSeverity("WARNING");
        entity.setResolvedStatus("UNRESOLVED");
        return entity;
    }

    private ScrmCalendarEventEntity assignEventId(ScrmCalendarEventEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }

    private ScrmCalendarHolidayEntity assignHolidayId(ScrmCalendarHolidayEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
