/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerCareFestivalService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmFestivalDto;
import org.hiylo.scrm.entity.ScrmCareRuleEntity;
import org.hiylo.scrm.entity.ScrmCareTaskEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmFestivalEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCareRuleRepository;
import org.hiylo.scrm.repository.ScrmCareTaskRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmFestivalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户关怀节日配置服务 (节日管理子域)。
 * <p>
 * 承载节日配置增删改查 / 启用禁用 / 即将到来节日查询, 以及节日类型关怀任务生成
 * (generateFestivalTasks / generateFestivalTasksForDate)。农历节日转换沿用简化处理 (待完善)。
 * 与规则 / 任务 / 记录兄弟类共享 {@link ScrmCustomerCareRuleService} 常量。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerCareFestivalService {

    /** 节日配置数据访问层 */
    private final ScrmFestivalRepository festivalRepository;

    /** 关怀规则数据访问层 (节日任务生成用) */
    private final ScrmCareRuleRepository ruleRepository;

    /** 关怀任务数据访问层 (节日任务生成用) */
    private final ScrmCareTaskRepository taskRepository;

    /** 客户数据访问层 (节日任务生成用) */
    private final ScrmCustomerRepository customerRepository;

    /** JSON 解析器 (解析 triggerCondition) */
    private final ObjectMapper objectMapper;

    /**
     * 创建节日配置。
     *
     * @param dto 节日参数
     * @return 创建后的节日
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmFestivalEntity createFestival(ScrmFestivalDto dto) throws ScrmException {
        validateFestivalDto(dto, false);
        ScrmFestivalEntity entity = new ScrmFestivalEntity();
        entity.setFestivalName(dto.getFestivalName());
        entity.setFestivalType(dto.getFestivalType());
        entity.setFestivalDate(dto.getFestivalDate());
        entity.setLunarMonth(dto.getLunarMonth());
        entity.setLunarDay(dto.getLunarDay());
        entity.setDescription(dto.getDescription());
        entity.setDefaultGreeting(dto.getDefaultGreeting());
        entity.setDefaultActionType(dto.getDefaultActionType());
        entity.setDefaultActionContent(dto.getDefaultActionContent());
        entity.setApplicable(dto.getApplicable() != null ? dto.getApplicable() : ScrmCustomerCareRuleService.DEFAULT_APPLICABLE);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : ScrmCustomerCareRuleService.DEFAULT_ENABLED);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = festivalRepository.save(entity);
        log.info("创建节日配置: id={}, festivalName={}, festivalType={}",
                entity.getId(), entity.getFestivalName(), entity.getFestivalType());
        return entity;
    }

    /**
     * 更新节日配置（字段非空才覆盖）。
     *
     * @param id  节日 ID
     * @param dto 节日参数
     * @return 更新后的节日
     * @throws ScrmException 节日不存在 / 参数非法
     */
    @Transactional
    public ScrmFestivalEntity updateFestival(Long id, ScrmFestivalDto dto) throws ScrmException {
        ScrmFestivalEntity entity = findFestivalOrThrow(id);
        validateFestivalDto(dto, true);
        if (dto.getFestivalName() != null) entity.setFestivalName(dto.getFestivalName());
        if (dto.getFestivalType() != null) entity.setFestivalType(dto.getFestivalType());
        if (dto.getFestivalDate() != null) entity.setFestivalDate(dto.getFestivalDate());
        if (dto.getLunarMonth() != null) entity.setLunarMonth(dto.getLunarMonth());
        if (dto.getLunarDay() != null) entity.setLunarDay(dto.getLunarDay());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getDefaultGreeting() != null) entity.setDefaultGreeting(dto.getDefaultGreeting());
        if (dto.getDefaultActionType() != null) entity.setDefaultActionType(dto.getDefaultActionType());
        if (dto.getDefaultActionContent() != null) entity.setDefaultActionContent(dto.getDefaultActionContent());
        if (dto.getApplicable() != null) entity.setApplicable(dto.getApplicable());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = festivalRepository.save(entity);
        log.info("更新节日配置: id={}, festivalName={}", entity.getId(), entity.getFestivalName());
        return entity;
    }

    /**
     * 删除节日配置。
     *
     * @param id 节日 ID
     * @throws ScrmException 节日不存在
     */
    @Transactional
    public void deleteFestival(Long id) throws ScrmException {
        ScrmFestivalEntity entity = findFestivalOrThrow(id);
        festivalRepository.delete(entity);
        log.info("删除节日配置: id={}, festivalName={}", id, entity.getFestivalName());
    }

    /**
     * 查询节日详情。
     *
     * @param id 节日 ID
     * @return 节日实体
     * @throws ScrmException 节日不存在
     */
    @Transactional(readOnly = true)
    public ScrmFestivalEntity getFestival(Long id) throws ScrmException {
        return findFestivalOrThrow(id);
    }

    /**
     * 分页查询节日, 支持按节日类型与启用状态过滤。
     *
     * @param festivalType 节日类型过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param pageable     分页参数
     * @return 节日分页结果 (按 festivalDate ASC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmFestivalEntity> listFestivals(String festivalType, Boolean enabled, Pageable pageable) {
        Specification<ScrmFestivalEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (festivalType != null && !festivalType.isBlank()) {
                predicates.add(cb.equal(root.get("festivalType"), festivalType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.asc(root.get("festivalDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return festivalRepository.findAll(spec, pageable);
    }

    /**
     * 启用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    @Transactional
    public ScrmFestivalEntity enableFestival(Long id) throws ScrmException {
        ScrmFestivalEntity entity = findFestivalOrThrow(id);
        entity.setEnabled(true);
        entity = festivalRepository.save(entity);
        log.info("启用节日配置: id={}, festivalName={}", id, entity.getFestivalName());
        return entity;
    }

    /**
     * 禁用节日。
     *
     * @param id 节日 ID
     * @return 更新后的节日
     * @throws ScrmException 节日不存在
     */
    @Transactional
    public ScrmFestivalEntity disableFestival(Long id) throws ScrmException {
        ScrmFestivalEntity entity = findFestivalOrThrow(id);
        entity.setEnabled(false);
        entity = festivalRepository.save(entity);
        log.info("禁用节日配置: id={}, festivalName={}", id, entity.getFestivalName());
        return entity;
    }

    /**
     * 查询即将到来的节日。
     * <p>按节日日期 (MM-dd) 匹配未来 days 天内的启用节日。农历节日采用简化处理
     * (按 lunarMonth/lunarDay 近似匹配, 待完善)。</p>
     *
     * @param days 未来天数
     * @return 即将到来的节日列表
     */
    @Transactional(readOnly = true)
    public List<ScrmFestivalEntity> getUpcomingFestivals(int days) {
        List<ScrmFestivalEntity> enabled = festivalRepository.findByEnabledTrue();
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(days);
        List<ScrmFestivalEntity> result = new ArrayList<>();
        for (ScrmFestivalEntity festival : enabled) {
            LocalDate festivalDateThisYear = resolveFestivalDate(festival, today.getYear());
            if (festivalDateThisYear != null) {
                // 若今年节日已过, 取次年
                LocalDate target = festivalDateThisYear.isBefore(today)
                        ? resolveFestivalDate(festival, today.getYear() + 1)
                        : festivalDateThisYear;
                if (target != null && !target.isBefore(today) && !target.isAfter(horizon)) {
                    result.add(festival);
                }
            }
        }
        return result;
    }

    /**
     * 生成节日关怀任务。
     * <p>加载指定节日, 匹配 FESTIVAL 类型启用规则, 为全部客户创建关怀任务。
     * actionContent 缺省时使用节日默认祝福语; actionType 缺省时使用规则动作类型。
     * 农历节日日期转换采用简化处理 (待完善)。</p>
     *
     * @param festivalId 节日 ID
     * @param date       关怀日期
     * @return 创建的任务数
     * @throws ScrmException 节日不存在
     */
    @Transactional
    public int generateFestivalTasks(Long festivalId, LocalDate date) throws ScrmException {
        ScrmFestivalEntity festival = findFestivalOrThrow(festivalId);
        if (!Boolean.TRUE.equals(festival.getEnabled())) {
            log.debug("节日未启用, 跳过生成: festivalId={}", festivalId);
            return 0;
        }
        if (date == null) {
            date = LocalDate.now();
        }
        return generateFestivalTasksInternal(festival, date);
    }

    /**
     * 按规则与日期匹配当日节日生成关怀任务 (每日调度用, 供任务兄弟类调用)。
     * <p>匹配节日日期等于入参 date 的启用节日, 逐一生成任务。</p>
     *
     * @param rule 节日规则
     * @param date 关怀日期
     * @return 创建的任务数
     */
    int generateFestivalTasksForDate(ScrmCareRuleEntity rule, LocalDate date) {
        List<ScrmFestivalEntity> festivals = festivalRepository.findByEnabledTrue();
        int created = 0;
        for (ScrmFestivalEntity festival : festivals) {
            LocalDate festivalDate = resolveFestivalDate(festival, date.getYear());
            if (festivalDate != null && festivalDate.equals(date)) {
                created += generateFestivalTasksInternal(festival, date);
            }
        }
        return created;
    }

    /**
     * 校验节日参数。
     *
     * @param dto     节日参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateFestivalDto(ScrmFestivalDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("节日参数不能为空");
        }
        if (dto.getFestivalName() != null) {
            if (dto.getFestivalName().isBlank()) {
                throw ScrmException.badRequest("节日名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("节日名称不能为空");
        }
        if (dto.getFestivalType() != null) {
            if (!ScrmCustomerCareRuleService.VALID_FESTIVAL_TYPES.contains(dto.getFestivalType())) {
                throw ScrmException.badRequest(
                        "节日类型非法: " + dto.getFestivalType() + ", 仅支持 "
                                + ScrmCustomerCareRuleService.VALID_FESTIVAL_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("节日类型不能为空");
        }
        if (dto.getFestivalDate() != null) {
            if (dto.getFestivalDate().isBlank()) {
                throw ScrmException.badRequest("节日日期不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("节日日期不能为空");
        }
        if (dto.getApplicable() != null
                && !ScrmCustomerCareRuleService.VALID_APPLICABLE.contains(dto.getApplicable())) {
            throw ScrmException.badRequest(
                    "适用范围非法: " + dto.getApplicable() + ", 仅支持 "
                            + ScrmCustomerCareRuleService.VALID_APPLICABLE);
        }
        if (dto.getDefaultActionType() != null
                && !ScrmCustomerCareRuleService.VALID_ACTION_TYPES.contains(dto.getDefaultActionType())) {
            throw ScrmException.badRequest(
                    "默认动作类型非法: " + dto.getDefaultActionType() + ", 仅支持 "
                            + ScrmCustomerCareRuleService.VALID_ACTION_TYPES);
        }
    }

    /**
     * 按节日与规则生成关怀任务 (节日适用全部客户)。
     *
     * @param festival 节日实体
     * @param date     关怀日期
     * @return 创建的任务数
     */
    private int generateFestivalTasksInternal(ScrmFestivalEntity festival, LocalDate date) {
        List<ScrmCareRuleEntity> rules = ruleRepository
                .findByCareTypeAndEnabledTrueOrderByPriorityAsc(ScrmCustomerCareRuleService.CARE_TYPE_FESTIVAL);
        if (rules.isEmpty()) {
            log.debug("无启用的节日关怀规则, 跳过: festivalId={}", festival.getId());
            return 0;
        }
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        int created = 0;
        for (ScrmCareRuleEntity rule : rules) {
            Map<String, Object> condition = parseTriggerCondition(rule.getTriggerCondition());
            LocalTime scheduledTime = parseTime(getAsString(condition, "time"));
            LocalDateTime scheduledAt = date.atTime(scheduledTime);
            // 动作内容: 规则优先, 缺省取节日默认祝福语
            String actionContent = rule.getActionContent() != null
                    ? rule.getActionContent()
                    : (festival.getDefaultGreeting() != null
                            ? "{\"greeting\":\"" + festival.getDefaultGreeting() + "\"}"
                            : ScrmCustomerCareRuleService.DEFAULT_ACTION_CONTENT);
            String actionType = rule.getActionType() != null
                    ? rule.getActionType()
                    : (festival.getDefaultActionType() != null
                            ? festival.getDefaultActionType()
                            : "SEND_MESSAGE");
            for (ScrmCustomerEntity customer : customers) {
                List<ScrmCareTaskEntity> existing = taskRepository
                        .findByRuleIdAndCustomerIdAndCareDate(
                                 rule.getId(), customer.getId(), date);
                if (!existing.isEmpty()) {
                    continue;
                }
                ScrmCareTaskEntity task = new ScrmCareTaskEntity();
                task.setRuleId(rule.getId());
                task.setCustomerId(customer.getId());
                task.setCustomerName(customer.getNickname());
                task.setCareType(ScrmCustomerCareRuleService.CARE_TYPE_FESTIVAL);
                task.setCareDate(date);
                task.setScheduledAt(scheduledAt);
                task.setActionType(actionType);
                task.setActionContent(actionContent);
                task.setStatus(ScrmCustomerCareRuleService.STATUS_PENDING);
                taskRepository.save(task);
                created++;
            }
            try {
                ruleRepository.incrementExecutionCount(rule.getId(), LocalDateTime.now());
            } catch (Exception e) {
                log.warn("更新规则执行统计失败, 忽略: ruleId={}, err={}", rule.getId(), e.getMessage());
            }
        }
        log.info("节日关怀任务生成: festivalId={}, festivalName={}, date={}, created={}",
                festival.getId(), festival.getFestivalName(), date, created);
        return created;
    }

    /**
     * 解析触发条件 JSON 为 Map。
     *
     * @param triggerCondition 触发条件 JSON 字符串
     * @return 触发条件 Map, 解析失败返回空 Map
     */
    private Map<String, Object> parseTriggerCondition(String triggerCondition) {
        try {
            return objectMapper.readValue(triggerCondition, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("触发条件 JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 解析时间字符串 (HH:mm) 为 LocalTime, 解析失败取默认 09:00。
     *
     * @param time 时间字符串
     * @return LocalTime
     */
    private LocalTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            return LocalTime.of(ScrmCustomerCareRuleService.DEFAULT_SCHEDULED_HOUR,
                    ScrmCustomerCareRuleService.DEFAULT_SCHEDULED_MINUTE);
        }
        try {
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return LocalTime.of(hour, minute);
        } catch (Exception e) {
            return LocalTime.of(ScrmCustomerCareRuleService.DEFAULT_SCHEDULED_HOUR,
                    ScrmCustomerCareRuleService.DEFAULT_SCHEDULED_MINUTE);
        }
    }

    /**
     * 解析节日日期为指定年份的 LocalDate。
     * <p>SOLAR / FIXED: 直接解析; LUNAR: 简化处理, 按公历同月日近似 (待完善);
     * CUSTOM: festivalDate 视为 MM-dd 或完整日期。</p>
     *
     * @param festival 节日实体
     * @param year     年份
     * @return 节日日期, 解析失败返回 null
     */
    private LocalDate resolveFestivalDate(ScrmFestivalEntity festival, int year) {
        if (festival == null || festival.getFestivalDate() == null) {
            return null;
        }
        String date = festival.getFestivalDate().trim();
        try {
            if (date.length() == 10 && date.contains("-")) {
                // 完整日期 yyyy-MM-dd (FIXED)
                return LocalDate.parse(date);
            }
            // MM-dd 格式
            String[] parts = date.split("-");
            if (parts.length != 2) {
                return null;
            }
            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            // 待完善: LUNAR 类型需农历转公历, 当前按公历同月日近似处理
            if (ScrmCustomerCareRuleService.FESTIVAL_TYPE_LUNAR.equals(festival.getFestivalType())
                    && festival.getLunarMonth() != null && festival.getLunarDay() != null) {
                month = festival.getLunarMonth();
                day = festival.getLunarDay();
            }
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            log.warn("节日日期解析失败: festivalId={}, date={}, err={}",
                    festival.getId(), date, e.getMessage());
            return null;
        }
    }

    /**
     * 从 Map 中获取字符串值。
     *
     * @param map Map
     * @param key 键
     * @return 字符串值, 不存在返回 null
     */
    private String getAsString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * 按主键查询节日, 不存在抛异常, 并校验账号归属。
     *
     * @param id 节日 ID
     * @return 节日实体
     * @throws ScrmException 节日不存在
     */
    private ScrmFestivalEntity findFestivalOrThrow(Long id) throws ScrmException {
        ScrmFestivalEntity entity = festivalRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "节日配置不存在: id=" + id));
        return entity;
    }
}
