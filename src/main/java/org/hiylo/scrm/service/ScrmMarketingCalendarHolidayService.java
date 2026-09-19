/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCalendarHolidayService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCalendarHolidayDto;
import org.hiylo.scrm.entity.ScrmCalendarHolidayEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCalendarHolidayRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销日历节假日管理服务 (节假日管理子域)。
 * <p>
 * 承载节日/纪念日库的增删改查 / 启停 / 即将到来与月度查询 / 营销建议, 托管节日类型与
 * 营销机会常量、节日日期解析 {@link #resolveHolidayDate(String, int)}。操作人获取复用
 * {@link ScrmMarketingCalendarEventService} 的包级能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMarketingCalendarHolidayService {

    /** 默认持续天数 */
    private static final int DEFAULT_DURATION_DAYS = 1;

    /** 默认国家 */
    private static final String DEFAULT_COUNTRY = "CN";

    /** 合法的节日类型 */
    private static final List<String> VALID_HOLIDAY_TYPES = List.of(
            "PUBLIC_HOLIDAY", "TRADITIONAL_FESTIVAL", "E_COMMERCE", "SEASONAL", "CUSTOM");

    /** 合法的营销机会 */
    private static final List<String> VALID_MARKETING_OPPORTUNITY = List.of(
            "HIGH", "MEDIUM", "LOW", "NONE");

    /** 节日数据访问层 */
    private final ScrmCalendarHolidayRepository holidayRepository;

    /**
     * 创建节日。
     *
     * @param dto 节日参数
     * @return 创建后的节日
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmCalendarHolidayEntity createHoliday(ScrmCalendarHolidayDto dto) throws ScrmException {
        validateHolidayDto(dto, false);
        ScrmCalendarHolidayEntity entity = new ScrmCalendarHolidayEntity();
        entity.setHolidayName(dto.getHolidayName());
        entity.setHolidayType(dto.getHolidayType());
        entity.setHolidayDate(dto.getHolidayDate());
        entity.setLunarDate(dto.getLunarDate());
        entity.setIsLunar(dto.getIsLunar() != null ? dto.getIsLunar() : Boolean.FALSE);
        entity.setDurationDays(dto.getDurationDays() != null ? dto.getDurationDays() : DEFAULT_DURATION_DAYS);
        entity.setDescription(dto.getDescription());
        entity.setMarketingOpportunity(
                dto.getMarketingOpportunity() != null ? dto.getMarketingOpportunity() : "MEDIUM");
        entity.setSuggestedActions(dto.getSuggestedActions());
        entity.setSuggestedChannels(dto.getSuggestedChannels());
        entity.setCountry(dto.getCountry() != null ? dto.getCountry() : DEFAULT_COUNTRY);
        entity.setRegion(dto.getRegion());
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy()
                : ScrmMarketingCalendarEventService.currentOperator());
        entity = holidayRepository.save(entity);
        log.info("创建节日: id={}, holidayName={}, holidayType={}",
                entity.getId(), entity.getHolidayName(), entity.getHolidayType());
        return entity;
    }

    /**
     * 更新节日 (字段非空才覆盖)。
     *
     * @param id  节日 ID
     * @param dto 节日参数
     * @return 更新后的节日
     * @throws ScrmException 节日不存在 / 参数非法
     */
    @Transactional
    public ScrmCalendarHolidayEntity updateHoliday(Long id, ScrmCalendarHolidayDto dto) throws ScrmException {
        ScrmCalendarHolidayEntity entity = findHolidayOrThrow(id);
        validateHolidayDto(dto, true);
        if (dto.getHolidayName() != null) entity.setHolidayName(dto.getHolidayName());
        if (dto.getHolidayType() != null) entity.setHolidayType(dto.getHolidayType());
        if (dto.getHolidayDate() != null) entity.setHolidayDate(dto.getHolidayDate());
        if (dto.getLunarDate() != null) entity.setLunarDate(dto.getLunarDate());
        if (dto.getIsLunar() != null) entity.setIsLunar(dto.getIsLunar());
        if (dto.getDurationDays() != null) entity.setDurationDays(dto.getDurationDays());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getMarketingOpportunity() != null) entity.setMarketingOpportunity(dto.getMarketingOpportunity());
        if (dto.getSuggestedActions() != null) entity.setSuggestedActions(dto.getSuggestedActions());
        if (dto.getSuggestedChannels() != null) entity.setSuggestedChannels(dto.getSuggestedChannels());
        if (dto.getCountry() != null) entity.setCountry(dto.getCountry());
        if (dto.getRegion() != null) entity.setRegion(dto.getRegion());
        if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = holidayRepository.save(entity);
        log.info("更新节日: id={}, holidayName={}", entity.getId(), entity.getHolidayName());
        return entity;
    }

    /**
     * 删除节日。
     *
     * @param id 节日 ID
     * @throws ScrmException 节日不存在
     */
    @Transactional
    public void deleteHoliday(Long id) throws ScrmException {
        ScrmCalendarHolidayEntity entity = findHolidayOrThrow(id);
        holidayRepository.delete(entity);
        log.info("删除节日: id={}, holidayName={}", id, entity.getHolidayName());
    }

    /**
     * 查询节日详情。
     *
     * @param id 节日 ID
     * @return 节日实体
     * @throws ScrmException 节日不存在
     */
    @Transactional(readOnly = true)
    public ScrmCalendarHolidayEntity getHoliday(Long id) throws ScrmException {
        return findHolidayOrThrow(id);
    }

    /**
     * 分页查询节日, 支持按类型 / 启用状态过滤。
     *
     * @param holidayType 节日类型过滤（可空）
     * @param isActive     启用状态过滤（可空）
     * @param pageable     分页参数
     * @return 节日分页结果 (按 holidayDate 升序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCalendarHolidayEntity> listHolidays(String holidayType, Boolean isActive, Pageable pageable) {
        Specification<ScrmCalendarHolidayEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (holidayType != null && !holidayType.isBlank()) {
                predicates.add(cb.equal(root.get("holidayType"), holidayType));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }
            query.orderBy(cb.asc(root.get("holidayDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return holidayRepository.findAll(spec, pageable);
    }

    /**
     * 启用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    @Transactional
    public ScrmCalendarHolidayEntity activateHoliday(Long id) throws ScrmException {
        ScrmCalendarHolidayEntity entity = findHolidayOrThrow(id);
        entity.setIsActive(Boolean.TRUE);
        entity = holidayRepository.save(entity);
        log.info("启用节日: id={}, holidayName={}", id, entity.getHolidayName());
        return entity;
    }

    /**
     * 停用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    @Transactional
    public ScrmCalendarHolidayEntity deactivateHoliday(Long id) throws ScrmException {
        ScrmCalendarHolidayEntity entity = findHolidayOrThrow(id);
        entity.setIsActive(Boolean.FALSE);
        entity = holidayRepository.save(entity);
        log.info("停用节日: id={}, holidayName={}", id, entity.getHolidayName());
        return entity;
    }

    /**
     * 查询即将到来的节日 (从今天起向后 N 天)。
     * <p>仅返回启用状态的节日, 按日期升序。</p>
     *
     * @param days 未来天数 (≤0 视为 7)
     * @return 节日列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCalendarHolidayEntity> getUpcomingHolidays(int days) {
        if (days <= 0) {
            days = 7;
        }
        LocalDate today = LocalDate.now();
        LocalDate rangeEnd = today.plusDays(days);
        // 获取全部启用的节日, 在内存中过滤出未来 days 天内的
        List<ScrmCalendarHolidayEntity> all = holidayRepository
                .findByIsActiveOrderByHolidayDateAsc(Boolean.TRUE);
        List<ScrmCalendarHolidayEntity> result = new ArrayList<>();
        for (ScrmCalendarHolidayEntity holiday : all) {
            LocalDate holidayDate = resolveHolidayDate(holiday.getHolidayDate(), today.getYear());
            if (holidayDate == null) {
                continue;
            }
            if (!holidayDate.isBefore(today) && !holidayDate.isAfter(rangeEnd)) {
                result.add(holiday);
            }
        }
        return result;
    }

    /**
     * 查询某月的节日。
     *
     * @param year  年份
     * @param month 月份 (1-12)
     * @return 节日列表
     * @throws ScrmException 月份非法
     */
    @Transactional(readOnly = true)
    public List<ScrmCalendarHolidayEntity> getHolidaysByMonth(int year, int month) {
        if (month < 1 || month > 12) {
            throw ScrmException.badRequest("月份非法: " + month);
        }
        List<ScrmCalendarHolidayEntity> all = holidayRepository
                .findByIsActiveOrderByHolidayDateAsc(Boolean.TRUE);
        List<ScrmCalendarHolidayEntity> result = new ArrayList<>();
        for (ScrmCalendarHolidayEntity holiday : all) {
            LocalDate holidayDate = resolveHolidayDate(holiday.getHolidayDate(), year);
            if (holidayDate != null && holidayDate.getMonthValue() == month && holidayDate.getYear() == year) {
                result.add(holiday);
            }
        }
        return result;
    }

    /**
     * 节日营销建议: 返回节日本身的建议动作 / 渠道 + 营销机会分级。
     *
     * @param holidayId 节日 ID
     * @return 营销建议 Map
     * @throws ScrmException 节日不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> suggestMarketingActions(Long holidayId) throws ScrmException {
        ScrmCalendarHolidayEntity holiday = findHolidayOrThrow(holidayId);
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("holidayId", holiday.getId());
        suggestion.put("holidayName", holiday.getHolidayName());
        suggestion.put("holidayType", holiday.getHolidayType());
        suggestion.put("marketingOpportunity", holiday.getMarketingOpportunity());
        suggestion.put("suggestedActions", ScrmMarketingCalendarStatsService.parseCsv(holiday.getSuggestedActions()));
        suggestion.put("suggestedChannels", ScrmMarketingCalendarStatsService.parseCsv(holiday.getSuggestedChannels()));
        suggestion.put("description", holiday.getDescription());
        // 根据营销机会给出建议
        Map<String, String> recommendation = new LinkedHashMap<>();
        switch (holiday.getMarketingOpportunity()) {
            case "HIGH":
                recommendation.put("priority", "P0");
                recommendation.put("advice", "营销机会高, 建议提前 30 天启动多渠道活动, 准备充分预算与素材");
                break;
            case "MEDIUM":
                recommendation.put("priority", "P1");
                recommendation.put("advice", "营销机会中等, 建议提前 14 天启动单渠道或多渠道轻量级活动");
                break;
            case "LOW":
                recommendation.put("priority", "P2");
                recommendation.put("advice", "营销机会较低, 建议仅在社交媒体或推送渠道轻量级露出");
                break;
            default:
                recommendation.put("priority", "P3");
                recommendation.put("advice", "无营销机会, 可不安排专门活动");
                break;
        }
        suggestion.put("recommendation", recommendation);
        return suggestion;
    }

    /**
     * 校验节日参数。
     *
     * @param dto     节日参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateHolidayDto(ScrmCalendarHolidayDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("节日参数不能为空");
        }
        if (dto.getHolidayName() != null) {
            if (dto.getHolidayName().isBlank()) {
                throw ScrmException.badRequest("节日名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("节日名称不能为空");
        }
        if (dto.getHolidayType() != null) {
            if (!VALID_HOLIDAY_TYPES.contains(dto.getHolidayType())) {
                throw ScrmException.badRequest(
                        "节日类型非法: " + dto.getHolidayType() + ", 仅支持 " + VALID_HOLIDAY_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("节日类型不能为空");
        }
        if (dto.getHolidayDate() != null) {
            if (dto.getHolidayDate().isBlank()) {
                throw ScrmException.badRequest("节日日期不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("节日日期不能为空");
        }
        if (dto.getMarketingOpportunity() != null && !VALID_MARKETING_OPPORTUNITY.contains(dto.getMarketingOpportunity())) {
            throw ScrmException.badRequest(
                    "营销机会非法: " + dto.getMarketingOpportunity() + ", 仅支持 " + VALID_MARKETING_OPPORTUNITY);
        }
    }

    /**
     * 解析节日日期为指定年份的 LocalDate。
     * <p>支持 MM-dd (固定每年) 与 yyyy-MM-dd (具体日期) 两种格式。</p>
     *
     * @param holidayDate 节日日期字符串
     * @param year        年份
     * @return LocalDate (无法解析返回 null)
     */
    private LocalDate resolveHolidayDate(String holidayDate, int year) {
        if (holidayDate == null || holidayDate.isBlank()) {
            return null;
        }
        try {
            if (holidayDate.length() == 5 && holidayDate.contains("-")) {
                // MM-dd 格式
                return LocalDate.parse(year + "-" + holidayDate);
            } else if (holidayDate.length() == 10) {
                // yyyy-MM-dd 格式
                return LocalDate.parse(holidayDate);
            }
        } catch (Exception e) {
            log.warn("解析节日日期失败: holidayDate={}, year={}, err={}",
                    holidayDate, year, e.getMessage());
        }
        return null;
    }

    /**
     * 按主键查询节日, 不存在抛异常, 并校验账号归属。
     *
     * @param id 节日 ID
     * @return 节日实体
     * @throws ScrmException 节日不存在
     */
    private ScrmCalendarHolidayEntity findHolidayOrThrow(Long id) throws ScrmException {
        return holidayRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "节日不存在: id=" + id));
    }
}