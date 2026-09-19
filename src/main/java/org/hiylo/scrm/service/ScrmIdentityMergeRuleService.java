/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeRuleService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmIdentityMergeRuleDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeTaskDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerIdentityEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeRuleEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerIdentityRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmIdentityMergeRuleRepository;
import org.hiylo.scrm.repository.ScrmIdentityMergeTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 客户身份合并服务 - 规则执行与重复检测子域。
 * <p>
 * 承载合并规则的增删改查 / 启停 / 执行 / 批量执行, 以及重复检测 (单/批量检测、
 * 匹配分数计算、潜在重复查找、重复报告)。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmIdentityMergeRuleService {

    /** 默认模糊匹配阈值 */
    private static final double DEFAULT_FUZZY_MATCH_THRESHOLD = 0.9;

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 合并规则数据访问层 */
    private final ScrmIdentityMergeRuleRepository ruleRepository;

    /** 客户数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** 客户身份数据访问层 */
    private final ScrmCustomerIdentityRepository identityRepository;

    /** 合并任务数据访问层 */
    private final ScrmIdentityMergeTaskRepository taskRepository;

    /** 合并任务与执行子域服务 (共享创建任务 / 匹配分数计算 / 客户查询) */
    private final ScrmIdentityMergeTaskService taskService;

    // ============================================================
    // 合并规则管理
    // ============================================================

    /**
     * 创建合并规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmIdentityMergeRuleEntity createRule(ScrmIdentityMergeRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        ScrmIdentityMergeRuleEntity entity = new ScrmIdentityMergeRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setDescription(dto.getDescription());
        entity.setMatchFields(dto.getMatchFields());
        entity.setMatchThreshold(dto.getMatchThreshold());
        entity.setFuzzyMatchFields(dto.getFuzzyMatchFields());
        entity.setFuzzyMatchThreshold(dto.getFuzzyMatchThreshold() != null
                ? dto.getFuzzyMatchThreshold() : DEFAULT_FUZZY_MATCH_THRESHOLD);
        entity.setAutoMerge(dto.getAutoMerge() != null ? dto.getAutoMerge() : Boolean.FALSE);
        entity.setFieldStrategy(dto.getFieldStrategy());
        entity.setExcludeFields(dto.getExcludeFields());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setMatchCount(0);
        entity.setMergeCount(0);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : UserContext.getUsername());
        entity = ruleRepository.save(entity);
        log.info("创建合并规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 更新合并规则 (字段非空才覆盖)。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @Transactional
    public ScrmIdentityMergeRuleEntity updateRule(Long id, ScrmIdentityMergeRuleDto dto) throws ScrmException {
        ScrmIdentityMergeRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getMatchFields() != null) entity.setMatchFields(dto.getMatchFields());
        if (dto.getMatchThreshold() != null) entity.setMatchThreshold(dto.getMatchThreshold());
        if (dto.getFuzzyMatchFields() != null) entity.setFuzzyMatchFields(dto.getFuzzyMatchFields());
        if (dto.getFuzzyMatchThreshold() != null) entity.setFuzzyMatchThreshold(dto.getFuzzyMatchThreshold());
        if (dto.getAutoMerge() != null) entity.setAutoMerge(dto.getAutoMerge());
        if (dto.getFieldStrategy() != null) entity.setFieldStrategy(dto.getFieldStrategy());
        if (dto.getExcludeFields() != null) entity.setExcludeFields(dto.getExcludeFields());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新合并规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除合并规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmIdentityMergeRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.delete(entity);
        log.info("删除合并规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmIdentityMergeRuleEntity getRule(Long id) throws ScrmException {
        return findRuleOrThrow(id);
    }

    /**
     * 分页查询规则, 支持按启用状态 / 关键词过滤。
     *
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  关键词过滤 (可空, 匹配规则名称)
     * @param pageable 分页参数
     * @return 规则分页结果 (按 priority DESC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmIdentityMergeRuleEntity> listRules(Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmIdentityMergeRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("ruleName")), "%" + keyword.toLowerCase() + "%"));
            }
            query.orderBy(cb.desc(root.get("priority")), cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return ruleRepository.findAll(spec, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmIdentityMergeRuleEntity enableRule(Long id) throws ScrmException {
        ScrmIdentityMergeRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = ruleRepository.save(entity);
        log.info("启用合并规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmIdentityMergeRuleEntity disableRule(Long id) throws ScrmException {
        ScrmIdentityMergeRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = ruleRepository.save(entity);
        log.info("禁用合并规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    /**
     * 执行规则 (扫描所有客户 → 匹配 → 创建合并任务)。
     *
     * @param id 规则 ID
     * @return 创建的合并任务列表
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public List<ScrmIdentityMergeTaskEntity> executeRule(Long id) throws ScrmException {
        ScrmIdentityMergeRuleEntity rule = findRuleOrThrow(id);
        if (Boolean.FALSE.equals(rule.getEnabled())) {
            throw ScrmException.badRequest("规则未启用, 无法执行: ruleId=" + id);
        }
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        List<String> matchFields = Arrays.stream(rule.getMatchFields().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        List<ScrmIdentityMergeTaskEntity> createdTasks = new ArrayList<>();
        // 预加载全部客户身份与源客户合并任务, 避免两两比对时逐对查库 (N+1)
        List<Long> allCustomerIds = customers.stream()
                .map(ScrmCustomerEntity::getId).collect(Collectors.toList());
        Map<Long, List<ScrmCustomerIdentityEntity>> identityMap = new LinkedHashMap<>();
        Map<Long, List<ScrmIdentityMergeTaskEntity>> taskMap = new LinkedHashMap<>();
        if (!allCustomerIds.isEmpty()) {
            identityMap = buildIdentityMap(identityRepository
                    .findByCustomerIdInAndIsActive(allCustomerIds, Boolean.TRUE));
            taskMap = buildTaskMap(taskRepository
                    .findBySourceCustomerIdIn(allCustomerIds));
        }
        // 两两比对客户
        for (int i = 0; i < customers.size(); i++) {
            for (int j = i + 1; j < customers.size(); j++) {
                ScrmCustomerEntity c1 = customers.get(i);
                ScrmCustomerEntity c2 = customers.get(j);
                double score = taskService.calculateMatchScore(c1, c2, identityMap);
                if (score >= rule.getMatchThreshold()) {
                    // 确定源 / 目标 (id 较小者为源, 被合并)
                    ScrmCustomerEntity source = c1.getId() < c2.getId() ? c1 : c2;
                    ScrmCustomerEntity target = c1.getId() < c2.getId() ? c2 : c1;
                    // 检查是否已存在未完成任务 (内存判断, 避免逐源客户查库)
                    if (hasPendingTask(source.getId(), taskMap)) {
                        continue;
                    }
                    ScrmIdentityMergeTaskDto taskDto = new ScrmIdentityMergeTaskDto();
                    taskDto.setTaskName("规则[" + rule.getRuleName() + "]合并: " + source.getId() + "→" + target.getId());
                    taskDto.setSourceCustomerId(source.getId());
                    taskDto.setSourceCustomerName(source.getNickname());
                    taskDto.setTargetCustomerId(target.getId());
                    taskDto.setTargetCustomerName(target.getNickname());
                    taskDto.setMergeType(rule.getAutoMerge()
                            ? ScrmIdentityMergeTaskService.MERGE_TYPE_AUTO
                            : ScrmIdentityMergeTaskService.MERGE_TYPE_SUGGESTED);
                    taskDto.setMatchScore(score);
                    taskDto.setMatchReasons(String.join(",", matchFields));
                    taskDto.setMatchedFields(rule.getMatchFields());
                    taskDto.setMergeConfig(rule.getFieldStrategy());
                    createdTasks.add(taskService.doCreateMergeTask(taskDto, source, target));
                }
            }
        }
        // 更新规则统计
        rule.setMatchCount((rule.getMatchCount() != null ? rule.getMatchCount() : 0) + createdTasks.size());
        rule.setLastExecutedAt(LocalDateTime.now());
        ruleRepository.save(rule);
        log.info("执行合并规则: ruleId={}, matchCount={}", id, createdTasks.size());
        return createdTasks;
    }

    /**
     * 批量执行所有启用的规则。
     *
     * @return 各规则创建的任务数统计
     */
    @Transactional
    public List<Map<String, Object>> batchExecuteRules() {
        List<ScrmIdentityMergeRuleEntity> rules = ruleRepository
                .findByEnabledOrderByPriorityDesc(Boolean.TRUE);
        List<Map<String, Object>> results = new ArrayList<>();
        for (ScrmIdentityMergeRuleEntity rule : rules) {
            try {
                List<ScrmIdentityMergeTaskEntity> tasks = executeRule(rule.getId());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ruleId", rule.getId());
                result.put("ruleName", rule.getRuleName());
                result.put("createdTasks", tasks.size());
                results.add(result);
            } catch (ScrmException e) {
                log.warn("批量执行规则失败: ruleId={}, err={}", rule.getId(), e.getMessage());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ruleId", rule.getId());
                result.put("ruleName", rule.getRuleName());
                result.put("createdTasks", 0);
                result.put("error", e.getMessage());
                results.add(result);
            }
        }
        return results;
    }

    // ============================================================
    // 重复检测
    // ============================================================

    /**
     * 检测指定客户的重复 (基于身份标识 + 字段匹配)。
     *
     * @param customerId 客户 ID
     * @return 重复列表 (含匹配分数与匹配原因)
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> detectDuplicates(Long customerId) throws ScrmException {
        ScrmCustomerEntity source = taskService.findCustomerOrThrow(customerId);
        // 1. 基于身份标识查找
        List<ScrmCustomerIdentityEntity> identities = identityRepository
                .findByCustomerIdAndIsActive(customerId, Boolean.TRUE);
        Map<Long, List<String>> candidateReasons = new LinkedHashMap<>();
        // 按身份类型分组后 In 一次查询, 避免逐身份查库
        Map<String, List<String>> identityValuesByType = identities.stream()
                .collect(Collectors.groupingBy(ScrmCustomerIdentityEntity::getIdentityType,
                        LinkedHashMap::new,
                        Collectors.mapping(ScrmCustomerIdentityEntity::getIdentityValue,
                                Collectors.toList())));
        for (Map.Entry<String, List<String>> typeEntry : identityValuesByType.entrySet()) {
            List<ScrmCustomerIdentityEntity> matches = identityRepository
                    .findByIdentityTypeAndIdentityValueIn(typeEntry.getKey(), typeEntry.getValue());
            for (ScrmCustomerIdentityEntity match : matches) {
                if (!Objects.equals(match.getCustomerId(), customerId)) {
                    candidateReasons.computeIfAbsent(match.getCustomerId(), k -> new ArrayList<>())
                            .add(match.getIdentityType() + ":" + match.getIdentityValue());
                }
            }
        }
        // 候选客户一次性加载
        Map<Long, ScrmCustomerEntity> candidateMap = customerRepository
                .findAllById(new ArrayList<>(candidateReasons.keySet())).stream()
                .collect(Collectors.toMap(ScrmCustomerEntity::getId, c -> c, (a, b) -> a,
                        LinkedHashMap::new));
        // 2. 计算匹配分数
        List<Map<String, Object>> duplicates = new ArrayList<>();
        for (Map.Entry<Long, List<String>> entry : candidateReasons.entrySet()) {
            ScrmCustomerEntity candidate = candidateMap.get(entry.getKey());
            if (candidate == null) {
                continue;
            }

            double score = taskService.calculateMatchScore(source, candidate);
            Map<String, Object> duplicate = new LinkedHashMap<>();
            duplicate.put("customerId", candidate.getId());
            duplicate.put("customerName", candidate.getNickname());
            duplicate.put("platformType", candidate.getPlatformType());
            duplicate.put("matchScore", score);
            duplicate.put("matchReasons", String.join(",", entry.getValue()));
            duplicate.put("identityMatches", entry.getValue().size());
            duplicates.add(duplicate);
        }
        duplicates.sort((a, b) -> Double.compare((Double) b.get("matchScore"), (Double) a.get("matchScore")));
        return duplicates;
    }

    /**
     * 批量检测重复 (扫描全部客户, 返回重复对统计)。
     *
     * @return 重复检测结果列表 (每项含两客户与匹配分数)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> batchDetectDuplicates() {
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        // 预加载全部客户身份, 避免两两比对时逐对查库 (N+1)
        List<Long> allCustomerIds = customers.stream()
                .map(ScrmCustomerEntity::getId).collect(Collectors.toList());
        Map<Long, List<ScrmCustomerIdentityEntity>> identityMap = new LinkedHashMap<>();
        if (!allCustomerIds.isEmpty()) {
            identityMap = buildIdentityMap(identityRepository
                    .findByCustomerIdInAndIsActive(allCustomerIds, Boolean.TRUE));
        }
        List<Map<String, Object>> duplicates = new ArrayList<>();
        for (int i = 0; i < customers.size(); i++) {
            for (int j = i + 1; j < customers.size(); j++) {
                ScrmCustomerEntity c1 = customers.get(i);
                ScrmCustomerEntity c2 = customers.get(j);
                double score = taskService.calculateMatchScore(c1, c2, identityMap);
                if (score >= ScrmIdentityMergeTaskService.DEFAULT_MATCH_THRESHOLD) {
                    Map<String, Object> duplicate = new LinkedHashMap<>();
                    duplicate.put("customer1Id", c1.getId());
                    duplicate.put("customer1Name", c1.getNickname());
                    duplicate.put("customer2Id", c2.getId());
                    duplicate.put("customer2Name", c2.getNickname());
                    duplicate.put("matchScore", score);
                    duplicates.add(duplicate);
                }
            }
        }
        duplicates.sort((a, b) -> Double.compare((Double) b.get("matchScore"), (Double) a.get("matchScore")));
        return duplicates;
    }

    /**
     * 查找潜在重复客户 (按关键词模糊匹配客户名称)。
     *
     * @param keyword 关键词
     * @param limit   返回数量上限
     * @return 潜在重复客户列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findPotentialDuplicates(String keyword, int limit) {
        Specification<ScrmCustomerEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("nickname")), "%" + keyword.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCustomerEntity> customers = customerRepository.findAll(spec);
        if (limit > 0 && customers.size() > limit) {
            customers = customers.subList(0, limit);
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (ScrmCustomerEntity customer : customers) {
            List<Map<String, Object>> dups = detectDuplicates(customer.getId());
            if (!dups.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("customerId", customer.getId());
                result.put("customerName", customer.getNickname());
                result.put("potentialDuplicates", dups);
                results.add(result);
            }
        }
        return results;
    }

    /**
     * 重复报告 (按匹配类型统计)。
     *
     * @return 报告 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDuplicateReport() {
        Map<String, Integer> typeStats = new LinkedHashMap<>();
        // 按身份类型统计重复值
        List<Object[]> typeCounts = identityRepository.countByIdentityType();
        for (Object[] row : typeCounts) {
            typeStats.put((String) row[0], ((Number) row[1]).intValue());
        }
        // 检测总重复对数
        List<Map<String, Object>> duplicates = batchDetectDuplicates();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalDuplicatePairs", duplicates.size());
        report.put("identityTypeStats", typeStats);
        report.put("highScoreDuplicates", duplicates.stream()
                .filter(d -> (Double) d.get("matchScore") >= 0.9)
                .count());
        report.put("mediumScoreDuplicates", duplicates.stream()
                .filter(d -> (Double) d.get("matchScore") >= ScrmIdentityMergeTaskService.DEFAULT_MATCH_THRESHOLD
                        && (Double) d.get("matchScore") < 0.9)
                .count());
        return report;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验规则参数。
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmIdentityMergeRuleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        if (dto.getRuleName() == null || dto.getRuleName().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("规则名称不能为空");
            }
        }
        if (dto.getMatchFields() == null || dto.getMatchFields().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("匹配字段不能为空");
            }
        }
        if (dto.getMatchThreshold() == null) {
            if (!partial) {
                throw ScrmException.badRequest("匹配阈值不能为空");
            }
        }
    }

    /**
     * 按客户 ID 构建客户身份 Map (customerId → 身份列表), 供批量匹配内存查询使用, 避免逐客户查库。
     *
     * @param identities 身份列表
     * @return 客户身份 Map
     */
    private Map<Long, List<ScrmCustomerIdentityEntity>> buildIdentityMap(
            List<ScrmCustomerIdentityEntity> identities) {
        Map<Long, List<ScrmCustomerIdentityEntity>> identityMap = new LinkedHashMap<>();
        for (ScrmCustomerIdentityEntity identity : identities) {
            identityMap.computeIfAbsent(identity.getCustomerId(), k -> new ArrayList<>()).add(identity);
        }
        return identityMap;
    }

    /**
     * 按源客户 ID 构建合并任务 Map (sourceCustomerId → 任务列表), 供批量规则执行内存判断使用。
     *
     * @param tasks 任务列表
     * @return 源客户合并任务 Map
     */
    private Map<Long, List<ScrmIdentityMergeTaskEntity>> buildTaskMap(
            List<ScrmIdentityMergeTaskEntity> tasks) {
        Map<Long, List<ScrmIdentityMergeTaskEntity>> taskMap = new LinkedHashMap<>();
        for (ScrmIdentityMergeTaskEntity task : tasks) {
            taskMap.computeIfAbsent(task.getSourceCustomerId(), k -> new ArrayList<>()).add(task);
        }
        return taskMap;
    }

    /**
     * 判断源客户是否已存在未完成的合并任务 (内存判断, 避免逐源客户查库)。
     *
     * @param sourceCustomerId 源客户 ID
     * @param taskMap          预加载的源客户合并任务 Map
     * @return 存在未完成任务返回 true
     */
    private boolean hasPendingTask(Long sourceCustomerId,
            Map<Long, List<ScrmIdentityMergeTaskEntity>> taskMap) {
        List<ScrmIdentityMergeTaskEntity> tasks = taskMap.getOrDefault(sourceCustomerId, List.of());
        return tasks.stream().anyMatch(t -> ScrmIdentityMergeTaskService.STATUS_PENDING.equals(t.getStatus())
                || ScrmIdentityMergeTaskService.STATUS_REVIEWING.equals(t.getStatus())
                || ScrmIdentityMergeTaskService.STATUS_APPROVED.equals(t.getStatus())
                || ScrmIdentityMergeTaskService.STATUS_IN_PROGRESS.equals(t.getStatus()));
    }

    /**
     * 主键查询规则, 不存在抛异常。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmIdentityMergeRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmIdentityMergeRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合并规则不存在: id=" + id));
        return entity;
    }
}