/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerJourneyDefinitionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerJourneyDto;
import org.hiylo.scrm.dto.ScrmJourneyStepDto;
import org.hiylo.scrm.entity.ScrmCustomerJourneyEntity;
import org.hiylo.scrm.entity.ScrmJourneyStepEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerJourneyRepository;
import org.hiylo.scrm.repository.ScrmJourneyEnrollmentRepository;
import org.hiylo.scrm.repository.ScrmJourneyProgressLogRepository;
import org.hiylo.scrm.repository.ScrmJourneyStepRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 客户旅程定义与步骤编排子域服务
 * <p>
 * 负责旅程的全生命周期管理 (创建 / 更新 / 删除 / 发布 / 暂停 / 归档 / 复制) 与
 * 步骤编排 (新增 / 更新 / 删除 / 查询 / 重排)。入营 / 步骤执行 / 统计见
 * {@link ScrmJourneyEnrollmentService} / {@link ScrmJourneyStepExecutionService} /
 * {@link ScrmJourneyStatsService}。本服务为 {@link ScrmCustomerJourneyService}
 * 门面的子域拆分, 不反向依赖门面。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerJourneyDefinitionService {

    /** 旅程状态: 草稿 */
    private static final String STATUS_DRAFT = "DRAFT";
    /** 旅程状态: 已发布 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 旅程状态: 已暂停 */
    private static final String STATUS_PAUSED = "PAUSED";
    /** 旅程状态: 已归档 */
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 步骤类型: 条件分支 */
    private static final String STEP_TYPE_CONDITION = "CONDITION";

    /** 客户旅程数据仓库 */
    private final ScrmCustomerJourneyRepository journeyRepository;
    /** 旅程步骤数据仓库 */
    private final ScrmJourneyStepRepository stepRepository;
    /** 旅程加入数据仓库 */
    private final ScrmJourneyEnrollmentRepository enrollmentRepository;
    /** 旅程进度日志数据仓库 */
    private final ScrmJourneyProgressLogRepository progressLogRepository;
    /** JSON 序列化/反序列化器 (步骤 config / 入旅程条件解析) */
    private final ObjectMapper objectMapper;
    /** 步骤执行兄弟服务 (提供共享查询与 JSON 解析工具) */
    private final ScrmJourneyStepExecutionService stepExecutionService;

    // ============================================================
    // 旅程管理
    // ============================================================

    /**
     * 创建客户旅程
     * <p>
     * 默认状态为 DRAFT, 业务版本号初始化为 1, 入旅程/完成/退出计数初始化为 0。
     * </p>
     *
     * @param dto 旅程参数
     * @return 创建后的旅程
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmCustomerJourneyDto createJourney(ScrmCustomerJourneyDto dto) throws ScrmException {
        if (dto.getJourneyName() == null || dto.getJourneyName().isBlank()) {
            throw ScrmException.badRequest("旅程名称不能为空");
        }
        if (dto.getEntryCondition() == null || dto.getEntryCondition().isBlank()) {
            throw ScrmException.badRequest("入旅程条件不能为空");
        }
        if (dto.getEntryType() == null || dto.getEntryType().isBlank()) {
            throw ScrmException.badRequest("入旅程方式不能为空");
        }
        ScrmCustomerJourneyEntity entity = new ScrmCustomerJourneyEntity();
        entity.setJourneyName(dto.getJourneyName());
        entity.setDescription(dto.getDescription());
        entity.setGoal(dto.getGoal());
        entity.setEntryCondition(dto.getEntryCondition());
        entity.setEntryType(dto.getEntryType());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_DRAFT);
        entity.setEnrolledCount(0);
        entity.setCompletedCount(0);
        entity.setExitedCount(0);
        entity.setConversionRate(0d);
        entity.setJourneyVersion(1);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = journeyRepository.save(entity);
        log.info("创建客户旅程: id={}, journeyName={}, entryType={}",
                entity.getId(), entity.getJourneyName(), entity.getEntryType());
        return toJourneyDto(entity);
    }

    /**
     * 更新客户旅程
     * <p>
     * 已发布或已归档的旅程不允许修改关键参数 (entryCondition / entryType), 避免影响在途入营。
     * </p>
     *
     * @param id  旅程 ID
     * @param dto 旅程参数
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    @Transactional
    public ScrmCustomerJourneyDto updateJourney(Long id, ScrmCustomerJourneyDto dto) throws ScrmException {
        ScrmCustomerJourneyEntity entity = stepExecutionService.findJourneyOrThrow(id);
        boolean published = STATUS_PUBLISHED.equals(entity.getStatus())
                || STATUS_ARCHIVED.equals(entity.getStatus());
        if (published) {
            if (dto.getEntryCondition() != null && !dto.getEntryCondition().equals(entity.getEntryCondition())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "已发布/归档旅程不允许修改入旅程条件: id=" + id);
            }
            if (dto.getEntryType() != null && !dto.getEntryType().equals(entity.getEntryType())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "已发布/归档旅程不允许修改入旅程方式: id=" + id);
            }
        }
        if (dto.getJourneyName() != null) {
            if (dto.getJourneyName().isBlank()) {
                throw ScrmException.badRequest("旅程名称不能为空");
            }
            entity.setJourneyName(dto.getJourneyName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getGoal() != null) {
            entity.setGoal(dto.getGoal());
        }
        if (dto.getEntryCondition() != null) {
            entity.setEntryCondition(dto.getEntryCondition());
        }
        if (dto.getEntryType() != null) {
            entity.setEntryType(dto.getEntryType());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getCreatedBy() != null) {
            entity.setCreatedBy(dto.getCreatedBy());
        }
        entity = journeyRepository.save(entity);
        return toJourneyDto(entity);
    }

    /**
     * 删除客户旅程
     * <p>
     * 已发布或暂停状态的旅程不允许直接删除 (需先归档), 避免影响在途入营。
     * 删除时级联清理步骤定义 / 入营记录 / 进度日志。
     * </p>
     *
     * @param id 旅程 ID
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    @Transactional
    public void deleteJourney(Long id) throws ScrmException {
        ScrmCustomerJourneyEntity entity = stepExecutionService.findJourneyOrThrow(id);
        if (STATUS_PUBLISHED.equals(entity.getStatus())
                || STATUS_PAUSED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "旅程已发布或暂停, 请先归档后再删除: id=" + id);
        }
        // 级联清理: 进度日志 → 入营记录 → 步骤 → 旅程
        progressLogRepository.deleteByJourneyId(id);
        enrollmentRepository.deleteByJourneyId(id);
        stepRepository.deleteByJourneyId(id);
        journeyRepository.delete(entity);
        log.info("删除客户旅程: id={}, name={}", id, entity.getJourneyName());
    }

    /**
     * 查询客户旅程详情
     *
     * @param id 旅程 ID
     * @return 旅程 DTO
     * @throws ScrmException 旅程不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerJourneyDto getJourney(Long id) throws ScrmException {
        return toJourneyDto(stepExecutionService.findJourneyOrThrow(id));
    }

    /**
     * 分页查询客户旅程, 支持按状态与关键词过滤
     *
     * @param status  状态过滤 (可空)
     * @param keyword 关键词过滤, 匹配旅程名称 (可空)
     * @param pageable 分页参数
     * @return 旅程分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerJourneyDto> listJourneys(String status, String keyword, Pageable pageable) {
        Specification<ScrmCustomerJourneyEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("journeyName")), kw));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return journeyRepository.findAll(spec, sorted).map(this::toJourneyDto);
    }

    /**
     * 发布客户旅程 (状态置 PUBLISHED, 业务版本号递增)
     *
     * @param id 旅程 ID
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    @Transactional
    public ScrmCustomerJourneyDto publishJourney(Long id) throws ScrmException {
        ScrmCustomerJourneyEntity entity = stepExecutionService.findJourneyOrThrow(id);
        if (!STATUS_DRAFT.equals(entity.getStatus()) && !STATUS_PAUSED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "旅程状态非法, 仅 DRAFT / PAUSED 可发布: currentStatus=" + entity.getStatus());
        }
        // 校验旅程至少配置一个步骤
        long stepCount = stepRepository.findByJourneyId(id).size();
        if (stepCount == 0) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "旅程未配置任何步骤, 不允许发布: id=" + id);
        }
        entity.setStatus(STATUS_PUBLISHED);
        entity.setJourneyVersion(entity.getJourneyVersion() + 1);
        entity = journeyRepository.save(entity);
        log.info("发布客户旅程: id={}, version={}", id, entity.getJourneyVersion());
        return toJourneyDto(entity);
    }

    /**
     * 暂停客户旅程 (状态置 PAUSED, 在途入营不中断但新客户不可入营)
     *
     * @param id 旅程 ID
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    @Transactional
    public ScrmCustomerJourneyDto pauseJourney(Long id) throws ScrmException {
        ScrmCustomerJourneyEntity entity = stepExecutionService.findJourneyOrThrow(id);
        if (!STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "旅程状态非法, 仅 PUBLISHED 可暂停: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(STATUS_PAUSED);
        entity = journeyRepository.save(entity);
        log.info("暂停客户旅程: id={}", id);
        return toJourneyDto(entity);
    }

    /**
     * 归档客户旅程 (状态置 ARCHIVED, 不可再发布或入营)
     *
     * @param id 旅程 ID
     * @return 更新后的旅程
     * @throws ScrmException 旅程不存在 / 状态非法
     */
    @Transactional
    public ScrmCustomerJourneyDto archiveJourney(Long id) throws ScrmException {
        ScrmCustomerJourneyEntity entity = stepExecutionService.findJourneyOrThrow(id);
        if (STATUS_ARCHIVED.equals(entity.getStatus())) {
            return toJourneyDto(entity);
        }
        if (!STATUS_PUBLISHED.equals(entity.getStatus()) && !STATUS_PAUSED.equals(entity.getStatus()) && !STATUS_DRAFT.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "旅程状态非法, 不允许归档: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(STATUS_ARCHIVED);
        entity = journeyRepository.save(entity);
        log.info("归档客户旅程: id={}", id);
        return toJourneyDto(entity);
    }

    /**
     * 复制客户旅程
     * <p>
     * 创建一个新的 DRAFT 旅程, 名称后缀 "(副本)", 并复制原旅程的全部步骤
     * (含 config / stepOrder / stepType), nextStepId 与 CONDITION config 中的
     * trueNextStep / falseNextStep 引用按旧→新 ID 映射重写。
     * </p>
     *
     * @param id 源旅程 ID
     * @return 复制后的新旅程
     * @throws ScrmException 源旅程不存在
     */
    @Transactional
    public ScrmCustomerJourneyDto copyJourney(Long id) throws ScrmException {
        ScrmCustomerJourneyEntity source = stepExecutionService.findJourneyOrThrow(id);
        // 复制旅程主记录
        ScrmCustomerJourneyEntity copy = new ScrmCustomerJourneyEntity();
        copy.setJourneyName(source.getJourneyName() + " (副本)");
        copy.setDescription(source.getDescription());
        copy.setGoal(source.getGoal());
        copy.setEntryCondition(source.getEntryCondition());
        copy.setEntryType(source.getEntryType());
        copy.setStatus(STATUS_DRAFT);
        copy.setEnrolledCount(0);
        copy.setCompletedCount(0);
        copy.setExitedCount(0);
        copy.setConversionRate(0d);
        copy.setJourneyVersion(1);
        copy.setCreatedBy(source.getCreatedBy());
        copy = journeyRepository.save(copy);
        // 复制步骤并建立旧→新 ID 映射
        List<ScrmJourneyStepEntity> sourceSteps = stepRepository
                .findByJourneyIdOrderByStepOrderAsc(id);
        Map<Long, Long> stepIdMap = new HashMap<>();
        List<ScrmJourneyStepEntity> copySteps = new ArrayList<>();
        for (ScrmJourneyStepEntity src : sourceSteps) {
            ScrmJourneyStepEntity dst = new ScrmJourneyStepEntity();
            dst.setJourneyId(copy.getId());
            dst.setStepName(src.getStepName());
            dst.setStepType(src.getStepType());
            dst.setStepOrder(src.getStepOrder());
            dst.setConfig(src.getConfig());
            dst.setNextStepId(src.getNextStepId());
            dst.setIsEntryPoint(src.getIsEntryPoint());
            dst.setDescription(src.getDescription());
            dst = stepRepository.save(dst);
            stepIdMap.put(src.getId(), dst.getId());
            copySteps.add(dst);
        }
        // 重写 nextStepId 与 CONDITION config 中的 trueNextStep / falseNextStep 引用
        for (ScrmJourneyStepEntity dst : copySteps) {
            if (dst.getNextStepId() != null) {
                dst.setNextStepId(stepIdMap.get(dst.getNextStepId()));
            }
            if (STEP_TYPE_CONDITION.equals(dst.getStepType())) {
                dst.setConfig(remapConditionConfig(dst.getConfig(), stepIdMap));
            }
            stepRepository.save(dst);
        }
        log.info("复制客户旅程: sourceId={}, copyId={}, stepCount={}", id, copy.getId(), copySteps.size());
        return toJourneyDto(copy);
    }

    // ============================================================
    // 步骤管理
    // ============================================================

    /**
     * 新增旅程步骤
     *
     * @param journeyId 旅程 ID
     * @param dto       步骤参数
     * @return 创建后的步骤
     * @throws ScrmException 旅程不存在 / 参数非法
     */
    @Transactional
    public ScrmJourneyStepDto addStep(Long journeyId, ScrmJourneyStepDto dto) throws ScrmException {
        stepExecutionService.findJourneyOrThrow(journeyId);
        if (dto.getStepName() == null || dto.getStepName().isBlank()) {
            throw ScrmException.badRequest("步骤名称不能为空");
        }
        if (dto.getStepType() == null || dto.getStepType().isBlank()) {
            throw ScrmException.badRequest("步骤类型不能为空");
        }
        if (dto.getConfig() == null || dto.getConfig().isBlank()) {
            throw ScrmException.badRequest("步骤配置不能为空");
        }
        if (dto.getStepOrder() == null) {
            throw ScrmException.badRequest("步骤顺序不能为空");
        }
        ScrmJourneyStepEntity entity = new ScrmJourneyStepEntity();
        entity.setJourneyId(journeyId);
        entity.setStepName(dto.getStepName());
        entity.setStepType(dto.getStepType());
        entity.setStepOrder(dto.getStepOrder());
        entity.setConfig(dto.getConfig());
        entity.setNextStepId(dto.getNextStepId());
        entity.setIsEntryPoint(Boolean.TRUE.equals(dto.getIsEntryPoint()));
        entity.setDescription(dto.getDescription());
        entity = stepRepository.save(entity);
        log.info("新增旅程步骤: journeyId={}, stepId={}, order={}, type={}",
                journeyId, entity.getId(), entity.getStepOrder(), entity.getStepType());
        return toStepDto(entity);
    }

    /**
     * 更新旅程步骤
     *
     * @param id  步骤 ID
     * @param dto 步骤参数
     * @return 更新后的步骤
     * @throws ScrmException 步骤不存在
     */
    @Transactional
    public ScrmJourneyStepDto updateStep(Long id, ScrmJourneyStepDto dto) throws ScrmException {
        ScrmJourneyStepEntity entity = stepExecutionService.findStepOrThrow(id);
        if (dto.getStepName() != null) {
            if (dto.getStepName().isBlank()) {
                throw ScrmException.badRequest("步骤名称不能为空");
            }
            entity.setStepName(dto.getStepName());
        }
        if (dto.getStepType() != null) {
            entity.setStepType(dto.getStepType());
        }
        if (dto.getStepOrder() != null) {
            entity.setStepOrder(dto.getStepOrder());
        }
        if (dto.getConfig() != null) {
            entity.setConfig(dto.getConfig());
        }
        if (dto.getNextStepId() != null) {
            entity.setNextStepId(dto.getNextStepId());
        }
        if (dto.getIsEntryPoint() != null) {
            entity.setIsEntryPoint(dto.getIsEntryPoint());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        entity = stepRepository.save(entity);
        return toStepDto(entity);
    }

    /**
     * 删除旅程步骤
     * <p>
     * 被其他步骤通过 nextStepId 引用的步骤不允许删除, 需先解除引用。
     * </p>
     *
     * @param id 步骤 ID
     * @throws ScrmException 步骤不存在 / 仍被引用
     */
    @Transactional
    public void deleteStep(Long id) throws ScrmException {
        ScrmJourneyStepEntity entity = stepExecutionService.findStepOrThrow(id);
        // 校验是否被其他步骤通过 nextStepId 引用
        List<ScrmJourneyStepEntity> siblings = stepRepository
                .findByJourneyId(entity.getJourneyId());
        for (ScrmJourneyStepEntity s : siblings) {
            if (Objects.equals(s.getId(), id)) {
                continue;
            }
            if (Objects.equals(s.getNextStepId(), id)) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "步骤被 stepId=" + s.getId() + " 的 nextStepId 引用, 请先解除引用");
            }
        }
        stepRepository.delete(entity);
        log.info("删除旅程步骤: id={}, name={}", id, entity.getStepName());
    }

    /**
     * 查询旅程的全部步骤 (按 stepOrder 升序)
     *
     * @param journeyId 旅程 ID
     * @return 步骤列表
     * @throws ScrmException 旅程不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmJourneyStepDto> listSteps(Long journeyId) throws ScrmException {
        stepExecutionService.findJourneyOrThrow(journeyId);
        return stepRepository.findByJourneyIdOrderByStepOrderAsc(
                 journeyId).stream()
                .map(this::toStepDto)
                .collect(Collectors.toList());
    }

    /**
     * 批量重排旅程步骤顺序
     *
     * @param journeyId 旅程 ID
     * @param stepIds   步骤 ID 列表 (按新顺序排列)
     * @return 重排后的步骤列表
     * @throws ScrmException 旅程不存在 / 步骤不属于此旅程
     */
    @Transactional
    public List<ScrmJourneyStepDto> reorderSteps(Long journeyId, List<Long> stepIds) throws ScrmException {
        stepExecutionService.findJourneyOrThrow(journeyId);
        if (stepIds == null || stepIds.isEmpty()) {
            throw ScrmException.badRequest("步骤 ID 列表不能为空");
        }
        List<ScrmJourneyStepEntity> steps = stepRepository
                .findByJourneyId(journeyId);
        Map<Long, ScrmJourneyStepEntity> stepMap = steps.stream()
                .collect(Collectors.toMap(ScrmJourneyStepEntity::getId, s -> s));
        for (int i = 0; i < stepIds.size(); i++) {
            Long stepId = stepIds.get(i);
            ScrmJourneyStepEntity step = stepMap.get(stepId);
            if (step == null) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "步骤不属于此旅程: stepId=" + stepId);
            }
            step.setStepOrder(i + 1);
        }
        stepRepository.saveAll(steps);
        return steps.stream()
                .sorted((a, b) -> Integer.compare(a.getStepOrder(), b.getStepOrder()))
                .map(this::toStepDto)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 重写 CONDITION config 中的 trueNextStep / falseNextStep 引用 (旅程复制用)
     *
     * @param configJson 原 config JSON
     * @param stepIdMap   旧→新步骤 ID 映射
     * @return 重写后的 config JSON
     */
    private String remapConditionConfig(String configJson, Map<Long, Long> stepIdMap) {
        Map<String, Object> config = stepExecutionService.parseConfig(configJson);
        if (config.isEmpty()) {
            return configJson;
        }
        boolean changed = false;
        Long oldTrue = stepExecutionService.longVal(config.get("trueNextStep"));
        if (oldTrue != null && stepIdMap.containsKey(oldTrue)) {
            config.put("trueNextStep", String.valueOf(stepIdMap.get(oldTrue)));
            changed = true;
        }
        Long oldFalse = stepExecutionService.longVal(config.get("falseNextStep"));
        if (oldFalse != null && stepIdMap.containsKey(oldFalse)) {
            config.put("falseNextStep", String.valueOf(stepIdMap.get(oldFalse)));
            changed = true;
        }
        if (!changed) {
            return configJson;
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.warn("CONDITION config 重写失败, 返回原值: json={}", configJson, e);
            return configJson;
        }
    }

    // ============================================================
    // 实体转 DTO
    // ============================================================

    /**
     * 旅程实体转 DTO
     */
    private ScrmCustomerJourneyDto toJourneyDto(ScrmCustomerJourneyEntity entity) {
        ScrmCustomerJourneyDto dto = new ScrmCustomerJourneyDto();
        dto.setId(entity.getId());
        dto.setJourneyName(entity.getJourneyName());
        dto.setDescription(entity.getDescription());
        dto.setGoal(entity.getGoal());
        dto.setEntryCondition(entity.getEntryCondition());
        dto.setEntryType(entity.getEntryType());
        dto.setStatus(entity.getStatus());
        dto.setEnrolledCount(entity.getEnrolledCount());
        dto.setCompletedCount(entity.getCompletedCount());
        dto.setExitedCount(entity.getExitedCount());
        dto.setConversionRate(entity.getConversionRate());
        dto.setJourneyVersion(entity.getJourneyVersion());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 步骤实体转 DTO
     */
    private ScrmJourneyStepDto toStepDto(ScrmJourneyStepEntity entity) {
        ScrmJourneyStepDto dto = new ScrmJourneyStepDto();
        dto.setId(entity.getId());
        dto.setJourneyId(entity.getJourneyId());
        dto.setStepName(entity.getStepName());
        dto.setStepType(entity.getStepType());
        dto.setStepOrder(entity.getStepOrder());
        dto.setConfig(entity.getConfig());
        dto.setNextStepId(entity.getNextStepId());
        dto.setIsEntryPoint(entity.getIsEntryPoint());
        dto.setDescription(entity.getDescription());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
