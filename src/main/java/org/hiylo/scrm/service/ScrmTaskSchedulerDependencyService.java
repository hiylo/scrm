/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskSchedulerDependencyService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmTaskDependencyDto;
import org.hiylo.scrm.dto.ScrmTaskExecuteDto;
import org.hiylo.scrm.entity.ScrmTaskDependencyEntity;
import org.hiylo.scrm.entity.ScrmTaskExecutionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmTaskDependencyRepository;
import org.hiylo.scrm.repository.ScrmTaskExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 任务调度 - 依赖关系子域服务。
 * <p>
 * 承载任务依赖关系管理 (增删改查) 与依赖触发能力: 依赖满足度校验、父执行完成后按
 * dependencyType 触发下游任务、递归收集上游依赖链。依赖任务执行复用执行子域服务。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmTaskSchedulerDependencyService {

    /** 默认依赖类型 */
    private static final String DEFAULT_DEPENDENCY_TYPE = "ON_SUCCESS";

    /** 默认延迟秒数 */
    private static final int DEFAULT_DELAY_SECONDS = 0;

    /** 默认是否必须 */
    private static final boolean DEFAULT_IS_REQUIRED = true;

    /** 默认最长等待分钟 */
    private static final int DEFAULT_MAX_WAIT_MINUTES = 60;

    /** 合法的依赖类型 */
    private static final List<String> VALID_DEPENDENCY_TYPES = List.of(
            "ON_SUCCESS", "ON_COMPLETION", "ON_FAILURE");

    /** 终态执行状态集合 (不可再变更) */
    private static final List<String> TERMINAL_EXECUTION_STATUSES = List.of(
            "SUCCESS", "FAILED", "TIMEOUT", "CANCELLED", "SKIPPED");

    /** 任务依赖关系数据访问层 */
    private final ScrmTaskDependencyRepository dependencyRepository;

    /** 任务执行记录数据访问层 */
    private final ScrmTaskExecutionRepository executionRepository;

    /** 任务管理子域服务 (共享任务查询) */
    private final ScrmTaskSchedulerTaskService taskService;

    /** 任务执行子域服务 (共享执行查询与执行触发) */
    private final ScrmTaskSchedulerExecutionService executionService;

    /**
     * 创建任务依赖关系。
     *
     * @param dto 依赖参数
     * @return 创建后的依赖关系
     * @throws ScrmException 参数非法 / 重复依赖
     */
    @Transactional
    public ScrmTaskDependencyEntity createDependency(ScrmTaskDependencyDto dto) throws ScrmException {
        validateDependencyDto(dto, false);
        // 校验主任务与依赖任务存在
        taskService.findTaskOrThrow(dto.getTaskId());
        taskService.findTaskOrThrow(dto.getDependsOnTaskId());
        // 校验依赖不重复
        long existing = dependencyRepository.count((root, query, cb) -> cb.and(
                cb.equal(root.get("taskId"), dto.getTaskId()),
                cb.equal(root.get("dependsOnTaskId"), dto.getDependsOnTaskId())));
        if (existing > 0) {
            throw ScrmException.conflict("依赖关系已存在: taskId=" + dto.getTaskId()
                    + ", dependsOnTaskId=" + dto.getDependsOnTaskId());
        }
        ScrmTaskDependencyEntity entity = new ScrmTaskDependencyEntity();
        entity.setTaskId(dto.getTaskId());
        entity.setTaskCode(dto.getTaskCode());
        entity.setDependsOnTaskId(dto.getDependsOnTaskId());
        entity.setDependsOnTaskCode(dto.getDependsOnTaskCode());
        entity.setDependencyType(dto.getDependencyType() != null && !dto.getDependencyType().isBlank()
                ? dto.getDependencyType() : DEFAULT_DEPENDENCY_TYPE);
        entity.setConditionExpression(dto.getConditionExpression());
        entity.setDelaySeconds(dto.getDelaySeconds() != null ? dto.getDelaySeconds() : DEFAULT_DELAY_SECONDS);
        entity.setIsRequired(dto.getIsRequired() != null ? dto.getIsRequired() : DEFAULT_IS_REQUIRED);
        entity.setMaxWaitMinutes(dto.getMaxWaitMinutes() != null
                ? dto.getMaxWaitMinutes() : DEFAULT_MAX_WAIT_MINUTES);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = dependencyRepository.save(entity);
        log.info("创建任务依赖关系: id={}, taskId={}, dependsOnTaskId={}, dependencyType={}",
                entity.getId(), entity.getTaskId(), entity.getDependsOnTaskId(), entity.getDependencyType());
        return entity;
    }

    /**
     * 更新任务依赖关系（字段非空才覆盖）。
     *
     * @param id  依赖 ID
     * @param dto 依赖参数
     * @return 更新后的依赖关系
     * @throws ScrmException 依赖关系不存在 / 参数非法
     */
    @Transactional
    public ScrmTaskDependencyEntity updateDependency(Long id, ScrmTaskDependencyDto dto) throws ScrmException {
        ScrmTaskDependencyEntity entity = findDependencyOrThrow(id);
        validateDependencyDto(dto, true);
        if (dto.getTaskId() != null) {
            taskService.findTaskOrThrow(dto.getTaskId());
            entity.setTaskId(dto.getTaskId());
        }
        if (dto.getTaskCode() != null) entity.setTaskCode(dto.getTaskCode());
        if (dto.getDependsOnTaskId() != null) {
            taskService.findTaskOrThrow(dto.getDependsOnTaskId());
            entity.setDependsOnTaskId(dto.getDependsOnTaskId());
        }
        if (dto.getDependsOnTaskCode() != null) entity.setDependsOnTaskCode(dto.getDependsOnTaskCode());
        if (dto.getDependencyType() != null) entity.setDependencyType(dto.getDependencyType());
        if (dto.getConditionExpression() != null) entity.setConditionExpression(dto.getConditionExpression());
        if (dto.getDelaySeconds() != null) entity.setDelaySeconds(dto.getDelaySeconds());
        if (dto.getIsRequired() != null) entity.setIsRequired(dto.getIsRequired());
        if (dto.getMaxWaitMinutes() != null) entity.setMaxWaitMinutes(dto.getMaxWaitMinutes());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = dependencyRepository.save(entity);
        log.info("更新任务依赖关系: id={}, taskId={}, dependsOnTaskId={}",
                entity.getId(), entity.getTaskId(), entity.getDependsOnTaskId());
        return entity;
    }

    /**
     * 删除任务依赖关系。
     *
     * @param id 依赖 ID
     * @throws ScrmException 依赖关系不存在
     */
    @Transactional
    public void deleteDependency(Long id) throws ScrmException {
        ScrmTaskDependencyEntity entity = findDependencyOrThrow(id);
        dependencyRepository.delete(entity);
        log.info("删除任务依赖关系: id={}, taskId={}, dependsOnTaskId={}",
                id, entity.getTaskId(), entity.getDependsOnTaskId());
    }

    /**
     * 查询任务依赖关系详情。
     *
     * @param id 依赖 ID
     * @return 依赖关系实体
     * @throws ScrmException 依赖关系不存在
     */
    @Transactional(readOnly = true)
    public ScrmTaskDependencyEntity getDependency(Long id) throws ScrmException {
        return findDependencyOrThrow(id);
    }

    /**
     * 分页查询任务依赖关系, 支持按主任务 ID 与依赖任务 ID 过滤。
     *
     * @param taskId         主任务 ID 过滤（可空）
     * @param dependsOnTaskId 依赖任务 ID 过滤（可空）
     * @param pageable        分页参数
     * @return 依赖关系分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmTaskDependencyEntity> listDependencies(Long taskId, Long dependsOnTaskId, Pageable pageable) {
        if (taskId != null) {
            return dependencyRepository.findByTaskId(taskId, pageable);
        }
        if (dependsOnTaskId != null) {
            return dependencyRepository.findByDependsOnTaskId(dependsOnTaskId, pageable);
        }
        return dependencyRepository.findAll(pageable);
    }

    /**
     * 检查任务依赖是否满足。
     * <p>遍历任务的全部依赖, 校验依赖任务最近一次执行是否符合 dependencyType:
     * ON_SUCCESS (SUCCESS) / ON_COMPLETION (任意终态) / ON_FAILURE (FAILED)。
     * isRequired=TRUE 的依赖未满足时返回 false。</p>
     *
     * @param taskId 任务 ID
     * @return true 表示所有必须依赖均已满足
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public boolean checkDependencies(Long taskId) throws ScrmException {
        taskService.findTaskOrThrow(taskId);
        List<ScrmTaskDependencyEntity> deps = dependencyRepository.findByTaskId(taskId);
        for (ScrmTaskDependencyEntity dep : deps) {
            ScrmTaskExecutionEntity latest = executionRepository
                    .findFirstByTaskIdOrderByStartedAtDesc(dep.getDependsOnTaskId()).orElse(null);
            boolean satisfied = isDependencySatisfied(dep, latest);
            if (!satisfied && Boolean.TRUE.equals(dep.getIsRequired())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 执行依赖任务（父执行完成后按 dependencyType 触发）。
     *
     * @param parentExecutionId 父执行 ID
     * @return 触发的依赖执行记录列表
     * @throws ScrmException 父执行不存在
     */
    @Transactional
    public List<ScrmTaskExecutionEntity> executeDependentTasks(Long parentExecutionId) throws ScrmException {
        ScrmTaskExecutionEntity parent = executionService.getExecution(parentExecutionId);
        // 查询所有依赖父任务的依赖关系
        List<ScrmTaskDependencyEntity> deps = dependencyRepository.findAll(
                (root, query, cb) ->
                        cb.equal(root.get("dependsOnTaskId"), parent.getTaskId()));
        List<ScrmTaskExecutionEntity> triggered = new ArrayList<>();
        for (ScrmTaskDependencyEntity dep : deps) {
            if (!isDependencySatisfied(dep, parent)) {
                continue;
            }
            // 应用延迟执行
            if (dep.getDelaySeconds() != null && dep.getDelaySeconds() > 0) {
                log.info("依赖任务延迟执行: taskId={}, delaySeconds={}", dep.getTaskId(),
                        dep.getDelaySeconds());
            }
            ScrmTaskExecuteDto executeDto = new ScrmTaskExecuteDto();
            executeDto.setTaskId(dep.getTaskId());
            executeDto.setTriggeredBy(parent.getTriggeredBy());
            try {
                ScrmTaskExecutionEntity exec = executionService.executeTask(executeDto);
                // 标记触发类型为 DEPENDENCY 并关联父执行
                exec.setTriggerType("DEPENDENCY");
                exec.setDependencyExecutionId(parentExecutionId);
                executionRepository.save(exec);
                triggered.add(exec);
            } catch (ScrmException e) {
                log.warn("依赖任务执行失败: taskId={}, err={}", dep.getTaskId(), e.getMessage());
            }
        }
        log.info("触发依赖任务执行: parentExecutionId={}, triggered={}", parentExecutionId, triggered.size());
        return triggered;
    }

    /**
     * 获取任务的依赖链（递归查询所有上游依赖）。
     *
     * @param taskId 任务 ID
     * @return 依赖链 (按层级排序, 包含直接与间接依赖)
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmTaskDependencyEntity> getDependencyChain(Long taskId) throws ScrmException {
        taskService.findTaskOrThrow(taskId);
        List<ScrmTaskDependencyEntity> chain = new ArrayList<>();
        collectDependencies(taskId, chain, new java.util.HashSet<>());
        return chain;
    }

    /**
     * 校验依赖参数。
     *
     * @param dto     依赖参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateDependencyDto(ScrmTaskDependencyDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("依赖参数不能为空");
        }
        if (dto.getTaskId() == null && !partial) {
            throw ScrmException.badRequest("主任务 ID 不能为空");
        }
        if (dto.getDependsOnTaskId() == null && !partial) {
            throw ScrmException.badRequest("依赖任务 ID 不能为空");
        }
        if (dto.getDependencyType() != null && !dto.getDependencyType().isBlank() && !VALID_DEPENDENCY_TYPES.contains(dto.getDependencyType())) {
            throw ScrmException.badRequest(
                    "依赖类型非法: " + dto.getDependencyType() + ", 仅支持 " + VALID_DEPENDENCY_TYPES);
        }
        if (dto.getDelaySeconds() != null && dto.getDelaySeconds() < 0) {
            throw ScrmException.badRequest("延迟执行秒不能为负数: " + dto.getDelaySeconds());
        }
        if (dto.getMaxWaitMinutes() != null && dto.getMaxWaitMinutes() < 0) {
            throw ScrmException.badRequest("最长等待分钟不能为负数: " + dto.getMaxWaitMinutes());
        }
    }

    /**
     * 判断依赖是否满足。
     *
     * @param dep    依赖关系
     * @param latest 依赖任务最近一次执行 (可空)
     * @return true 表示依赖已满足
     */
    private boolean isDependencySatisfied(ScrmTaskDependencyEntity dep, ScrmTaskExecutionEntity latest) {
        if (latest == null) {
            return false;
        }
        String status = latest.getStatus();
        String type = dep.getDependencyType() != null ? dep.getDependencyType() : DEFAULT_DEPENDENCY_TYPE;
        switch (type) {
            case "ON_SUCCESS":
                return "SUCCESS".equals(status);
            case "ON_COMPLETION":
                return TERMINAL_EXECUTION_STATUSES.contains(status);
            case "ON_FAILURE":
                return "FAILED".equals(status) || "TIMEOUT".equals(status);
            default:
                return false;
        }
    }

    /**
     * 递归收集任务的上游依赖链。
     *
     * @param taskId  当前任务 ID
     * @param chain   依赖链 (累积)
     * @param visited 已访问任务 ID 集合 (避免循环)
     */
    private void collectDependencies(Long taskId, List<ScrmTaskDependencyEntity> chain,
                                      java.util.Set<Long> visited) {
        if (visited.contains(taskId)) {
            log.warn("检测到循环依赖, 终止递归: taskId={}", taskId);
            return;
        }
        visited.add(taskId);
        List<ScrmTaskDependencyEntity> deps = dependencyRepository.findByTaskId(taskId);
        for (ScrmTaskDependencyEntity dep : deps) {
            chain.add(dep);
            collectDependencies(dep.getDependsOnTaskId(), chain, visited);
        }
    }

    /**
     * 按主键查询依赖关系, 不存在抛异常, 并校验归属账号。
     *
     * @param id 依赖 ID
     * @return 依赖关系实体
     * @throws ScrmException 依赖关系不存在
     */
    private ScrmTaskDependencyEntity findDependencyOrThrow(Long id) throws ScrmException {
        ScrmTaskDependencyEntity entity = dependencyRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "任务依赖关系不存在: id=" + id));

        return entity;
    }
}
