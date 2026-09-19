/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionTaskService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmInspectionExecuteDto;
import org.hiylo.scrm.dto.ScrmQualityInspectionResultDto;
import org.hiylo.scrm.dto.ScrmQualityInspectionTaskDto;
import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmQualityInspectionResultEntity;
import org.hiylo.scrm.entity.ScrmQualityInspectionRuleEntity;
import org.hiylo.scrm.entity.ScrmQualityInspectionTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmQualityInspectionResultRepository;
import org.hiylo.scrm.repository.ScrmQualityInspectionRuleRepository;
import org.hiylo.scrm.repository.ScrmQualityInspectionTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 质检任务与执行服务。
 * <p>
 * 承载质检任务的创建/查询/进度查询，以及质检任务的批量执行 (状态流转
 * PENDING → RUNNING → COMPLETED/FAILED)。执行时按任务范围批量加载会话消息与
 * 客户/账号名称 (避免逐会话 N+1 查询) 后逐会话调用评估引擎生成并持久化质检结果，
 * 支持单会话即时质检与按被质检人批量质检。
 * </p>
 * <p>
 * 依赖 {@link ScrmQualityInspectionResultService} 提供规则评估、总分计算、通过判定与
 * 结果 DTO 转换能力。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmQualityInspectionTaskService {

    /** 默认通过条件 (总分及格线 80) */
    private static final String DEFAULT_PASS_CONDITION = "GTE:80";

    /** 质检人类型: AI */
    private static final String INSPECTOR_TYPE_AI = "AI";

    /** 任务状态: PENDING 待执行 */
    private static final String TASK_STATUS_PENDING = "PENDING";
    /** 任务状态: RUNNING 执行中 */
    private static final String TASK_STATUS_RUNNING = "RUNNING";
    /** 任务状态: COMPLETED 已完成 */
    private static final String TASK_STATUS_COMPLETED = "COMPLETED";
    /** 任务状态: FAILED 执行失败 */
    private static final String TASK_STATUS_FAILED = "FAILED";

    /** 质检范围: ALL 全部会话 */
    private static final String SCOPE_ALL = "ALL";
    /** 质检范围: ASSIGNEE 指定坐席 */
    private static final String SCOPE_ASSIGNEE = "ASSIGNEE";
    /** 质检范围: ACCOUNT 指定账号 */
    private static final String SCOPE_ACCOUNT = "ACCOUNT";
    /** 质检范围: CUSTOMER 指定客户 */
    private static final String SCOPE_CUSTOMER = "CUSTOMER";

    /** 合法质检范围 */
    private static final List<String> VALID_SCOPES = List.of(
            "ALL", "ASSIGNEE", "ACCOUNT", "CUSTOMER");

    /** 质检任务数据访问层 */
    private final ScrmQualityInspectionTaskRepository taskRepository;

    /** 质检规则数据访问层 (加载任务关联规则) */
    private final ScrmQualityInspectionRuleRepository ruleRepository;

    /** 质检结果数据访问层 (持久化质检结果) */
    private final ScrmQualityInspectionResultRepository resultRepository;

    /** 会话数据访问层 (加载质检范围内的会话) */
    private final ScrmConversationRepository conversationRepository;

    /** 会话消息数据访问层 (加载会话消息用于评估) */
    private final ScrmConversationMessageRepository messageRepository;

    /** 客户数据访问层 (补全客户名称) */
    private final ScrmCustomerRepository customerRepository;

    /** 账号数据访问层 (补全被质检人名称) */
    private final ScrmAccountRepository accountRepository;

    /** JSON 序列化/反序列化 */
    private final ObjectMapper objectMapper;

    /** 质检结果服务 (评估引擎/总分/通过判定/结果转换) */
    private final ScrmQualityInspectionResultService resultService;

    // ============================================================
    // 任务管理
    // ============================================================

    /**
     * 创建质检任务。
     * <p>仅创建任务记录 (状态 PENDING), 不立即执行, 由 {@code executeTask} 触发执行。</p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmQualityInspectionTaskEntity createTask(ScrmQualityInspectionTaskDto dto) throws ScrmException {
        validateTaskDto(dto);
        ScrmQualityInspectionTaskEntity entity = new ScrmQualityInspectionTaskEntity();
        entity.setTaskName(dto.getTaskName());
        entity.setInspectionScope(dto.getInspectionScope());
        entity.setScopeValue(dto.getScopeValue());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setRuleIds(dto.getRuleIds());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = taskRepository.save(entity);
        log.info("创建质检任务: id={}, taskName={}, scope={}",
                entity.getId(), entity.getTaskName(), entity.getInspectionScope());
        return entity;
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmQualityInspectionTaskEntity getTask(Long id) throws ScrmException {
        return findTaskOrThrow(id);
    }

    /**
     * 分页查询任务, 支持按状态过滤。
     *
     * @param status   任务状态过滤（可空）
     * @param pageable 分页参数
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmQualityInspectionTaskEntity> listTasks(String status, Pageable pageable) {
        Specification<ScrmQualityInspectionTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 执行质检任务: 遍历范围内会话 → 逐个质检 → 汇总统计。
     * <p>任务状态流转 PENDING → RUNNING → COMPLETED/FAILED, 单会话质检异常跳过不阻断整体。</p>
     *
     * @param taskId 任务 ID
     * @return 执行后的任务
     * @throws ScrmException 任务不存在 / 任务状态非法
     */
    @Transactional
    public ScrmQualityInspectionTaskEntity executeTask(Long taskId) throws ScrmException {
        ScrmQualityInspectionTaskEntity task = findTaskOrThrow(taskId);
        if (TASK_STATUS_RUNNING.equals(task.getStatus())) {
            throw ScrmException.conflict("质检任务正在执行中, 请勿重复触发: taskId=" + taskId);
        }
        if (TASK_STATUS_COMPLETED.equals(task.getStatus())) {
            throw ScrmException.conflict("质检任务已完成, 不允许重复执行: taskId=" + taskId);
        }
        List<ScrmQualityInspectionRuleEntity> rules = loadRulesFromJson(task.getRuleIds());
        if (rules.isEmpty()) {
            throw ScrmException.badRequest("质检任务未关联任何启用规则: taskId=" + taskId);
        }
        List<ScrmConversationEntity> conversations = loadConversationsForTask(task);
        task.setStatus(TASK_STATUS_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setTotalConversations(conversations.size());
        task.setInspectedCount(0);
        task.setPassedCount(0);
        task.setFailedCount(0);
        task.setAverageScore(0.0);
        task = taskRepository.save(task);

        int inspected = 0;
        int passed = 0;
        int failed = 0;
        double scoreSum = 0.0;
        try {
            // 批量预加载会话消息与客户/账号名称, 避免 doInspect 内逐会话 N+1 查询
            Map<Long, List<ScrmConversationMessageEntity>> messagesMap =
                    loadMessagesByConversation(conversations, task.getStartTime(), task.getEndTime());
            Map<Long, ScrmCustomerEntity> customerMap = loadCustomers(conversations);
            Map<Long, ScrmAccountEntity> accountMap = loadAccounts(conversations);
            for (ScrmConversationEntity conversation : conversations) {
                try {
                    ScrmQualityInspectionResultEntity result = doInspect(conversation, rules,
                            task.getStartTime(), task.getEndTime(), taskId,
                            messagesMap, customerMap, accountMap);
                    inspected++;
                    scoreSum += result.getTotalScore();
                    if (Boolean.TRUE.equals(result.getPassed())) {
                        passed++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    log.warn("任务内单会话质检异常, 跳过: taskId={}, conversationId={}, err={}",
                            taskId, conversation.getId(), e.getMessage());
                }
            }
            task.setInspectedCount(inspected);
            task.setPassedCount(passed);
            task.setFailedCount(failed);
            task.setAverageScore(inspected > 0 ? round2(scoreSum / inspected) : 0.0);
            task.setStatus(TASK_STATUS_COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task = taskRepository.save(task);
            log.info("质检任务执行完成: taskId={}, total={}, inspected={}, passed={}, failed={}, avgScore={}",
                    taskId, conversations.size(), inspected, passed, failed, task.getAverageScore());
        } catch (Exception e) {
            task.setStatus(TASK_STATUS_FAILED);
            task.setCompletedAt(LocalDateTime.now());
            task = taskRepository.save(task);
            log.error("质检任务执行失败: taskId={}", taskId, e);
            throw new ScrmException(ScrmExceptionConstants.INTERNAL_ERROR,
                    "质检任务执行失败: " + e.getMessage(), e);
        }
        return task;
    }

    /**
     * 查询任务进度。
     *
     * @param taskId 任务 ID
     * @return 进度信息 (total/inspected/passed/failed/averageScore/status)
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTaskProgress(Long taskId) throws ScrmException {
        ScrmQualityInspectionTaskEntity task = findTaskOrThrow(taskId);
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("taskId", task.getId());
        progress.put("taskName", task.getTaskName());
        progress.put("status", task.getStatus());
        progress.put("totalConversations", task.getTotalConversations());
        progress.put("inspectedCount", task.getInspectedCount());
        progress.put("passedCount", task.getPassedCount());
        progress.put("failedCount", task.getFailedCount());
        progress.put("averageScore", task.getAverageScore());
        progress.put("startedAt", task.getStartedAt());
        progress.put("completedAt", task.getCompletedAt());
        return progress;
    }

    // ============================================================
    // 会话质检
    // ============================================================

    /**
     * 质检单个会话: 加载会话消息 → 逐规则评估 → 计算总分 → 生成结果。
     *
     * @param executeDto 质检执行参数 (conversationId + ruleIds)
     * @return 质检结果
     * @throws ScrmException 会话不存在 / 规则不存在
     */
    @Transactional
    public ScrmQualityInspectionResultDto inspectConversation(
            ScrmInspectionExecuteDto executeDto) throws ScrmException {
        if (executeDto == null) {
            throw ScrmException.badRequest("质检参数不能为空");
        }
        ScrmConversationEntity conversation = findConversationOrThrow(executeDto.getConversationId());
        List<ScrmQualityInspectionRuleEntity> rules = loadRulesFromJson(executeDto.getRuleIds());
        if (rules.isEmpty()) {
            throw ScrmException.badRequest("未指定有效的质检规则");
        }
        ScrmQualityInspectionResultEntity result = doInspect(conversation, rules, null, null, null);
        return resultService.toResultDto(result);
    }

    /**
     * 质检某销售的所有会话 (按时间范围过滤)。
     *
     * @param assigneeId 被质检人 ID (账号 ID 字符串)
     * @param startTime  起始时间（含）
     * @param endTime    截止时间（含）
     * @param ruleIds    规则 ID 列表 JSON 数组
     * @return 质检结果列表
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmQualityInspectionResultDto> inspectByAssignee(String assigneeId, LocalDateTime startTime,
                                                                   LocalDateTime endTime,
                                                                   String ruleIds) throws ScrmException {
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("被质检人 ID 不能为空");
        }
        if (startTime == null || endTime == null) {
            throw ScrmException.badRequest("质检时间范围不能为空");
        }
        if (startTime.isAfter(endTime)) {
            throw ScrmException.badRequest("起始时间不能晚于截止时间");
        }
        Long accountId = parseLong(assigneeId);
        if (accountId == null) {
            throw ScrmException.badRequest("被质检人 ID 非法: " + assigneeId);
        }
        List<ScrmQualityInspectionRuleEntity> rules = loadRulesFromJson(ruleIds);
        if (rules.isEmpty()) {
            throw ScrmException.badRequest("未指定有效的质检规则");
        }
        // 按账号 + 时间范围在数据库层过滤会话 (避免 Integer.MAX_VALUE 伪分页 + 内存过滤)
        List<ScrmConversationEntity> scoped = conversationRepository
                .findByAccountIdAndLastMessageAtBetweenOrderByLastMessageAtDesc(
                        accountId, startTime, endTime);
        // 批量加载会话消息与客户/账号名称, 避免 doInspect 内逐会话 N+1 查询
        Map<Long, List<ScrmConversationMessageEntity>> messagesMap =
                loadMessagesByConversation(scoped, startTime, endTime);
        Map<Long, ScrmCustomerEntity> customerMap = loadCustomers(scoped);
        Map<Long, ScrmAccountEntity> accountMap = loadAccounts(scoped);
        List<ScrmQualityInspectionResultDto> results = new ArrayList<>();
        for (ScrmConversationEntity conversation : scoped) {
            try {
                ScrmQualityInspectionResultEntity result = doInspect(conversation, rules, startTime, endTime,
                        null, messagesMap, customerMap, accountMap);
                results.add(resultService.toResultDto(result));
            } catch (Exception e) {
                log.warn("按被质检人质检单会话异常, 跳过: assigneeId={}, conversationId={}, err={}",
                        assigneeId, conversation.getId(), e.getMessage());
            }
        }
        log.info("按被质检人质检完成: assigneeId={}, conversations={}, results={}",
                assigneeId, scoped.size(), results.size());
        return results;
    }

    // ============================================================
    // 单会话质检核心逻辑
    // ============================================================

    /**
     * 单会话质检核心逻辑: 加载消息 → 逐规则评估 → 计算总分 → 持久化结果。
     *
     * @param conversation 会话实体
     * @param rules        质检规则列表
     * @param startTime    消息起始时间 (可空, 单会话即时质检时为空表示加载全部消息)
     * @param endTime      消息截止时间 (可空)
     * @param taskId       关联任务 ID (可空, 单会话质检时为空)
     * @return 持久化后的质检结果
     */
    private ScrmQualityInspectionResultEntity doInspect(ScrmConversationEntity conversation,
                                                         List<ScrmQualityInspectionRuleEntity> rules,
                                                         LocalDateTime startTime, LocalDateTime endTime,
                                                         Long taskId) {
        return doInspect(conversation, rules, startTime, endTime, taskId,
                loadMessagesByConversation(List.of(conversation), startTime, endTime),
                loadCustomers(List.of(conversation)),
                loadAccounts(List.of(conversation)));
    }

    /**
     * 单会话质检核心逻辑 (批量场景): 消息与客户/账号名称由调用方预加载后传入, 避免逐会话 N+1 查询。
     *
     * @param conversation               会话实体
     * @param rules                      质检规则列表
     * @param startTime                  消息起始时间 (可空)
     * @param endTime                    消息截止时间 (可空)
     * @param taskId                     关联任务 ID (可空)
     * @param messagesByConversationId   会话 ID → 消息列表 (key=conversationId, 可空)
     * @param customerMap                客户 ID → 客户实体 (可空)
     * @param accountMap                 账号 ID → 账号实体 (可空)
     * @return 持久化后的质检结果
     */
    private ScrmQualityInspectionResultEntity doInspect(ScrmConversationEntity conversation,
                                                         List<ScrmQualityInspectionRuleEntity> rules,
                                                         LocalDateTime startTime, LocalDateTime endTime,
                                                         Long taskId,
                                                         Map<Long, List<ScrmConversationMessageEntity>> messagesByConversationId,
                                                         Map<Long, ScrmCustomerEntity> customerMap,
                                                         Map<Long, ScrmAccountEntity> accountMap) {
        List<ScrmConversationMessageEntity> messages = messagesByConversationId != null
                ? messagesByConversationId.getOrDefault(conversation.getId(), List.of())
                : loadMessagesByConversation(List.of(conversation), startTime, endTime)
                        .getOrDefault(conversation.getId(), List.of());
        // 逐规则评估
        List<Map<String, Object>> ruleResults = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        for (ScrmQualityInspectionRuleEntity rule : rules) {
            try {
                Map<String, Object> rr = resultService.evaluateRule(rule, messages);
                ruleResults.add(rr);
                if (!Boolean.TRUE.equals(rr.get("passed"))) {
                    issues.add("规则[" + rule.getRuleName() + "]未通过: " + rr.get("detail"));
                }
            } catch (Exception e) {
                log.warn("规则评估异常, 跳过: ruleId={}, conversationId={}, err={}",
                        rule.getId(), conversation.getId(), e.getMessage());
            }
        }
        double totalScore = resultService.calculateTotalScore(ruleResults);
        boolean passed = resultService.checkPassed(totalScore, DEFAULT_PASS_CONDITION);

        // 补全客户与被质检人信息
        String customerName = resolveCustomerName(conversation.getCustomerId(), customerMap);
        String assigneeId = conversation.getAccountId() != null ? String.valueOf(conversation.getAccountId()) : null;
        String assigneeName = resolveAssigneeName(conversation.getAccountId(), accountMap);

        ScrmQualityInspectionResultEntity result = new ScrmQualityInspectionResultEntity();
        result.setTaskId(taskId);
        result.setConversationId(conversation.getId());
        result.setCustomerId(conversation.getCustomerId());
        result.setCustomerName(customerName);
        result.setAssigneeId(assigneeId);
        result.setAssigneeName(assigneeName);
        result.setTotalScore(totalScore);
        result.setPassed(passed);
        result.setRuleResults(toJson(ruleResults));
        result.setIssuesFound(issues.isEmpty() ? null : toJson(issues));
        result.setSuggestions(buildSuggestions(ruleResults));
        result.setInspectedAt(LocalDateTime.now());
        result.setInspectorType(INSPECTOR_TYPE_AI);
        result = resultRepository.save(result);

        // 更新规则累计匹配/通过次数
        updateRuleCounters(rules, ruleResults);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 根据失败的规则类别生成改进建议。
     */
    private String buildSuggestions(List<Map<String, Object>> ruleResults) {
        List<String> failedCategories = ruleResults.stream()
                .filter(rr -> !Boolean.TRUE.equals(rr.get("passed")))
                .map(rr -> String.valueOf(rr.get("category")))
                .distinct()
                .toList();
        if (failedCategories.isEmpty()) {
            return "质检通过, 保持良好服务";
        }
        List<String> suggestions = new ArrayList<>();
        for (String category : failedCategories) {
            switch (category) {
                case "SENSITIVE_WORD" -> suggestions.add("注意避免使用敏感词汇");
                case "RESPONSE_TIME" -> suggestions.add("提升响应速度, 控制平均响应时长");
                case "SCRIPT_COMPLIANCE" -> suggestions.add("严格按话术规范接待客户");
                case "SERVICE_ATTITUDE" -> suggestions.add("改善服务态度, 保持礼貌用语");
                case "PROFESSIONALISM" -> suggestions.add("提升专业度, 准确解答客户问题");
                case "COMPLIANCE" -> suggestions.add("遵守合规要求, 规范业务操作");
                default -> suggestions.add("针对 " + category + " 维度改进");
            }
        }
        return String.join("; ", suggestions);
    }

    /**
     * 更新规则累计匹配/通过次数。
     */
    private void updateRuleCounters(List<ScrmQualityInspectionRuleEntity> rules,
            List<Map<String, Object>> ruleResults) {
        Map<String, Map<String, Object>> rrById = ruleResults.stream()
                .collect(Collectors.toMap(rr -> String.valueOf(rr.get("ruleId")), rr -> rr, (a, b) -> a));
        List<ScrmQualityInspectionRuleEntity> changed = new ArrayList<>();
        for (ScrmQualityInspectionRuleEntity rule : rules) {
            Map<String, Object> rr = rrById.get(String.valueOf(rule.getId()));
            if (rr == null) {
                continue;
            }
            int matchCount = rule.getMatchCount() != null ? rule.getMatchCount() : 0;
            int passCount = rule.getPassCount() != null ? rule.getPassCount() : 0;
            rule.setMatchCount(matchCount + 1);
            if (Boolean.TRUE.equals(rr.get("passed"))) {
                rule.setPassCount(passCount + 1);
            }
            changed.add(rule);
        }
        if (changed.isEmpty()) {
            return;
        }
        try {
            ruleRepository.saveAll(changed);
        } catch (Exception e) {
            log.warn("批量更新规则计数失败, 忽略: count={}, err={}", changed.size(), e.getMessage());
        }
    }

    /**
     * 加载质检任务范围内的会话。
     */
    private List<ScrmConversationEntity> loadConversationsForTask(ScrmQualityInspectionTaskEntity task) {
        switch (task.getInspectionScope()) {
            case SCOPE_ALL:
                return conversationRepository.findByLastMessageAtBetween(
                         task.getStartTime(), task.getEndTime());
            case SCOPE_ASSIGNEE:
            case SCOPE_ACCOUNT: {
                Long accountId = parseLong(task.getScopeValue());
                if (accountId == null) {
                    return List.of();
                }
                if (task.getStartTime() != null && task.getEndTime() != null) {
                    return conversationRepository.findByAccountIdAndLastMessageAtBetweenOrderByLastMessageAtDesc(
                            accountId, task.getStartTime(), task.getEndTime());
                }
                return conversationRepository.findByAccountIdOrderByLastMessageAtDesc(accountId,
                        Pageable.unpaged()).getContent();
            }
            case SCOPE_CUSTOMER: {
                Long customerId = parseLong(task.getScopeValue());
                if (customerId == null) {
                    return List.of();
                }
                if (task.getStartTime() != null && task.getEndTime() != null) {
                    return conversationRepository.findByCustomerIdAndLastMessageAtBetweenOrderByLastMessageAtDesc(
                            customerId, task.getStartTime(), task.getEndTime());
                }
                return conversationRepository.findByCustomerIdOrderByLastMessageAtDesc(customerId,
                        Pageable.unpaged()).getContent();
            }
            default:
                return List.of();
        }
    }

    /**
     * 从规则 ID 列表 JSON 加载启用规则 (数据隔离)。
     */
    private List<ScrmQualityInspectionRuleEntity> loadRulesFromJson(String ruleIdsJson) {
        List<Long> ids = parseLongList(ruleIdsJson);
        if (ids.isEmpty()) {
            return List.of();
        }
        return ruleRepository.findAllById(ids).stream()
                .filter(r -> true)
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                .toList();
    }

    /**
     * 批量加载多个会话的消息, 按会话 ID 分组 (避免逐会话查询的 N+1)。
     *
     * @param conversations 会话列表
     * @param startTime     消息起始时间 (可空)
     * @param endTime       消息截止时间 (可空)
     * @return 会话 ID → 消息列表
     */
    private Map<Long, List<ScrmConversationMessageEntity>> loadMessagesByConversation(
            List<ScrmConversationEntity> conversations, LocalDateTime startTime, LocalDateTime endTime) {
        if (conversations == null || conversations.isEmpty()) {
            return Map.of();
        }
        List<Long> conversationIds = conversations.stream()
                .map(ScrmConversationEntity::getId)
                .distinct()
                .toList();
        List<ScrmConversationMessageEntity> messages;
        if (startTime != null && endTime != null) {
            messages = messageRepository.findByConversationIdInAndSentAtBetweenOrderBySentAtDesc(
                    conversationIds, startTime, endTime);
        } else {
            messages = messageRepository.findByConversationIdInOrderBySentAtDesc(conversationIds);
        }
        return messages.stream().collect(Collectors.groupingBy(
                ScrmConversationMessageEntity::getConversationId, Collectors.toList()));
    }

    /**
     * 批量加载会话涉及的客户, 按客户 ID 分组 (避免逐会话 findById 的 N+1)。
     *
     * @param conversations 会话列表
     * @return 客户 ID → 客户实体
     */
    private Map<Long, ScrmCustomerEntity> loadCustomers(List<ScrmConversationEntity> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return Map.of();
        }
        List<Long> customerIds = conversations.stream()
                .map(ScrmConversationEntity::getCustomerId)
                .filter(c -> c != null)
                .distinct()
                .toList();
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(ScrmCustomerEntity::getId, c -> c, (a, b) -> a));
    }

    /**
     * 批量加载会话涉及的账号, 按账号 ID 分组 (避免逐会话 findById 的 N+1)。
     *
     * @param conversations 会话列表
     * @return 账号 ID → 账号实体
     */
    private Map<Long, ScrmAccountEntity> loadAccounts(List<ScrmConversationEntity> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return Map.of();
        }
        List<Long> accountIds = conversations.stream()
                .map(ScrmConversationEntity::getAccountId)
                .filter(a -> a != null)
                .distinct()
                .toList();
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(ScrmAccountEntity::getId, a -> a, (a, b) -> a));
    }

    /**
     * 解析客户名称 (nickname), 优先从预加载 Map 取, 未命中时回退按主键查询。
     *
     * @param customerId  客户 ID
     * @param customerMap 客户 ID → 客户实体 (可空)
     * @return 客户昵称, 客户不存在或 ID 为空返回 null
     */
    private String resolveCustomerName(Long customerId, Map<Long, ScrmCustomerEntity> customerMap) {
        if (customerId == null) {
            return null;
        }
        ScrmCustomerEntity customer = customerMap != null ? customerMap.get(customerId) : null;
        if (customer != null) {
            return customer.getNickname();
        }
        return customerRepository.findById(customerId)
                .map(ScrmCustomerEntity::getNickname)
                .orElse(null);
    }

    /**
     * 解析被质检人名称 (账号展示名), 优先从预加载 Map 取, 未命中时回退按主键查询。
     *
     * @param accountId  账号 ID
     * @param accountMap 账号 ID → 账号实体 (可空)
     * @return 展示名, 空时取账号名; 账号不存在或 ID 为空返回 null
     */
    private String resolveAssigneeName(Long accountId, Map<Long, ScrmAccountEntity> accountMap) {
        if (accountId == null) {
            return null;
        }
        ScrmAccountEntity acc = accountMap != null ? accountMap.get(accountId) : null;
        if (acc == null) {
            acc = accountRepository.findById(accountId).orElse(null);
        }
        if (acc == null) {
            return null;
        }
        return acc.getDisplayName() != null && !acc.getDisplayName().isBlank()
                ? acc.getDisplayName() : acc.getAccountName();
    }

    /**
     * 校验质检任务参数。
     *
     * @param dto 任务参数
     * @throws ScrmException 参数非法
     */
    private void validateTaskDto(ScrmQualityInspectionTaskDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("任务参数不能为空");
        }
        if (dto.getTaskName() == null || dto.getTaskName().isBlank()) {
            throw ScrmException.badRequest("任务名称不能为空");
        }
        if (dto.getInspectionScope() == null || !VALID_SCOPES.contains(dto.getInspectionScope())) {
            throw ScrmException.badRequest(
                    "质检范围非法: " + dto.getInspectionScope() + ", 仅支持 " + VALID_SCOPES);
        }
        if (!SCOPE_ALL.equals(dto.getInspectionScope()) && (dto.getScopeValue() == null || dto.getScopeValue().isBlank())) {
            throw ScrmException.badRequest("非 ALL 范围必须指定 scopeValue");
        }
        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            throw ScrmException.badRequest("质检时间范围不能为空");
        }
        if (dto.getStartTime().isAfter(dto.getEndTime())) {
            throw ScrmException.badRequest("起始时间不能晚于截止时间");
        }
        if (dto.getRuleIds() == null || dto.getRuleIds().isBlank()) {
            throw ScrmException.badRequest("质检规则 ID 列表不能为空");
        }
        try {
            objectMapper.readTree(dto.getRuleIds());
        } catch (Exception e) {
            throw ScrmException.badRequest("规则 ID 列表不是合法 JSON: " + e.getMessage());
        }
    }

    /**
     * 按主键查询任务, 不存在抛 404, 并校验归属账号。
     */
    private ScrmQualityInspectionTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        ScrmQualityInspectionTaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "质检任务不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询会话, 不存在抛 404, 并校验归属账号。
     */
    private ScrmConversationEntity findConversationOrThrow(Long id) throws ScrmException {
        ScrmConversationEntity entity = conversationRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.SCRM_CONVERSATION_NOT_FOUND,
                        "会话不存在: id=" + id));

        return entity;
    }

    /**
     * 序列化为 JSON 字符串, 失败返回 "[]".
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 解析 JSON 数组为 Long 列表。
     */
    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("Long 列表 JSON 解析失败: json={}, err={}", json, e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析字符串为 Long, 不可解析返回 null。
     */
    private Long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 保留两位小数。
     */
    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
