/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyStepExecutionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.util.UrlSecurityUtils;
import org.hiylo.scrm.dto.ScrmJourneyEnrollmentDto;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerJourneyEntity;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmJourneyEnrollmentEntity;
import org.hiylo.scrm.entity.ScrmJourneyProgressLogEntity;
import org.hiylo.scrm.entity.ScrmJourneyStepEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmCustomerJourneyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmJourneyEnrollmentRepository;
import org.hiylo.scrm.repository.ScrmJourneyProgressLogRepository;
import org.hiylo.scrm.repository.ScrmJourneyStepRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SCRM 客户旅程步骤执行子域服务
 * <p>
 * 负责按 stepType 分派执行旅程步骤 (SEND_MESSAGE / WAIT / CONDITION / ADD_TAG /
 * SET_LIFECYCLE / WEBHOOK / END), 写入进度日志并推进入营记录, 以及处理所有
 * 待执行的 WAIT 到期步骤。同时承载入营 / 旅程定义 / 统计子域共用的查询与
 * JSON 解析工具方法 (package-private)。本服务为 {@link ScrmCustomerJourneyService}
 * 门面的子域拆分, 不反向依赖门面。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmJourneyStepExecutionService {

    /** 入营状态: 进行中 */
    private static final String ENROLLMENT_STATUS_ACTIVE = "ACTIVE";
    /** 入营状态: 已完成 */
    private static final String ENROLLMENT_STATUS_COMPLETED = "COMPLETED";

    /** 步骤类型: 发送消息 */
    private static final String STEP_TYPE_SEND_MESSAGE = "SEND_MESSAGE";
    /** 步骤类型: 等待 */
    private static final String STEP_TYPE_WAIT = "WAIT";
    /** 步骤类型: 条件分支 */
    private static final String STEP_TYPE_CONDITION = "CONDITION";
    /** 步骤类型: 打标签 */
    private static final String STEP_TYPE_ADD_TAG = "ADD_TAG";
    /** 步骤类型: 改生命周期 */
    private static final String STEP_TYPE_SET_LIFECYCLE = "SET_LIFECYCLE";
    /** 步骤类型: Webhook 回调 */
    private static final String STEP_TYPE_WEBHOOK = "WEBHOOK";
    /** 步骤类型: 结束 */
    private static final String STEP_TYPE_END = "END";

    /** 执行结果: 成功 */
    private static final String RESULT_SUCCESS = "SUCCESS";
    /** 执行结果: 失败 */
    private static final String RESULT_FAILED = "FAILED";
    /** 执行结果: 跳过 */
    private static final String RESULT_SKIPPED = "SKIPPED";
    /** 执行结果: 等待中 */
    private static final String RESULT_WAITING = "WAITING";

    /** 默认操作人 (请求头未透传 X-User-Id 时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** Webhook HTTP 连接超时 (毫秒) */
    private static final int WEBHOOK_CONNECT_TIMEOUT_MS = 5000;
    /** Webhook HTTP 读取超时 (毫秒) */
    private static final int WEBHOOK_READ_TIMEOUT_MS = 10000;
    /** 标签来源: 旅程自动打标 */
    private static final String TAG_SOURCE_JOURNEY = "JOURNEY";

    /** 客户旅程数据仓库 */
    private final ScrmCustomerJourneyRepository journeyRepository;
    /** 旅程步骤数据仓库 */
    private final ScrmJourneyStepRepository stepRepository;
    /** 旅程加入数据仓库 */
    private final ScrmJourneyEnrollmentRepository enrollmentRepository;
    /** 旅程进度日志数据仓库 */
    private final ScrmJourneyProgressLogRepository progressLogRepository;
    /** 客户数据仓库 */
    private final ScrmCustomerRepository customerRepository;
    /** 客户-标签赋值关系数据访问层 (ADD_TAG 动作 + 标签字段查询) */
    private final ScrmTagCustomerRepository tagCustomerRepository;
    /** 标签定义数据访问层 (tagKey → tagId 解析, 用于标签字段查询) */
    private final ScrmCustomerTagRepository customerTagRepository;
    /** 会话消息数据访问层 (SEND_MESSAGE 动作持久化) */
    private final ScrmConversationMessageRepository conversationMessageRepository;
    /** JSON 序列化/反序列化器 (步骤 config / 入旅程条件解析) */
    private final ObjectMapper objectMapper;

    /**
     * 执行入营记录的当前步骤
     * <p>
     * 按 stepType 分派执行:
     * <ul>
     *   <li>SEND_MESSAGE → 发送消息 (持久化出站会话消息)</li>
     *   <li>WAIT → 等待, 推进至下一步并设置 nextStepAt = now + durationHours</li>
     *   <li>CONDITION → 解析 config 中的 field/operator/value, 从客户上下文取值比较,
     *       走 trueNextStep 或 falseNextStep</li>
     *   <li>ADD_TAG → 打标签 (创建标签赋值记录, 幂等)</li>
     *   <li>SET_LIFECYCLE → 改生命周期 (更新客户 lifecycle 字段)</li>
     *   <li>WEBHOOK → 回调通知 (真实 HTTP POST, 5s/10s 超时)</li>
     *   <li>END → 标记入营完成</li>
     * </ul>
     * 执行后写入进度日志, 更新入营记录的 currentStepId / lastStepAt / nextStepAt / progress。
     * </p>
     *
     * @param enrollmentId 入营记录 ID
     * @return 更新后的入营记录
     * @throws ScrmException 入营记录不存在 / 步骤不存在
     */
    @Transactional
    public ScrmJourneyEnrollmentDto processStep(Long enrollmentId) throws ScrmException {
        ScrmJourneyEnrollmentEntity enrollment = findEnrollmentOrThrow(enrollmentId);
        if (!ENROLLMENT_STATUS_ACTIVE.equals(enrollment.getStatus())) {
            log.debug("入营记录非 ACTIVE 状态, 跳过执行: enrollmentId={}, status={}",
                    enrollmentId, enrollment.getStatus());
            return toEnrollmentDto(enrollment);
        }
        Long currentStepId = enrollment.getCurrentStepId();
        // 当前步骤为空 → 旅程完成
        if (currentStepId == null) {
            completeEnrollment(enrollment);
            return toEnrollmentDto(enrollment);
        }
        ScrmJourneyStepEntity step = findStepOrThrow(currentStepId);
        LocalDateTime now = LocalDateTime.now();
        String actionResult;
        String actionDetail = null;
        Long nextStepId = step.getNextStepId();
        boolean completed = false;
        boolean waiting = false;
        try {
            switch (step.getStepType()) {
                case STEP_TYPE_SEND_MESSAGE:
                    executeSendMessageAction(enrollment, step);
                    actionResult = RESULT_SUCCESS;
                    actionDetail = "消息已发送";
                    break;
                case STEP_TYPE_WAIT:
                    Integer durationHours = parseWaitDuration(step.getConfig());
                    enrollment.setNextStepAt(now.plusHours(durationHours == null ? 0 : durationHours));
                    actionResult = RESULT_WAITING;
                    actionDetail = "等待 " + (durationHours == null ? 0 : durationHours) + " 小时后继续";
                    waiting = true;
                    break;
                case STEP_TYPE_CONDITION:
                    ConditionEvalResult condResult = evaluateCondition(enrollment, step);
                    nextStepId = condResult.branchNextStepId;
                    actionResult = RESULT_SUCCESS;
                    actionDetail = condResult.detail;
                    break;
                case STEP_TYPE_ADD_TAG:
                    executeAddTagAction(enrollment, step);
                    actionResult = RESULT_SUCCESS;
                    actionDetail = "标签已添加";
                    break;
                case STEP_TYPE_SET_LIFECYCLE:
                    executeSetLifecycleAction(enrollment, step);
                    actionResult = RESULT_SUCCESS;
                    actionDetail = "生命周期已更新";
                    break;
                case STEP_TYPE_WEBHOOK:
                    executeWebhookAction(enrollment, step);
                    actionResult = RESULT_SUCCESS;
                    actionDetail = "Webhook 已回调";
                    break;
                case STEP_TYPE_END:
                    actionResult = RESULT_SUCCESS;
                    actionDetail = "旅程结束";
                    completed = true;
                    break;
                default:
                    actionResult = RESULT_SKIPPED;
                    actionDetail = "未知步骤类型, 已跳过: " + step.getStepType();
            }
        } catch (Exception e) {
            log.error("步骤执行异常: enrollmentId={}, stepId={}, stepType={}",
                    enrollmentId, step.getId(), step.getStepType(), e);
            actionResult = RESULT_FAILED;
            actionDetail = "执行异常: " + e.getMessage();
        }
        // 写入进度日志
        writeProgressLog(enrollment, step, actionResult, actionDetail, now);
        // 更新入营记录
        enrollment.setLastStepAt(now);
        if (completed) {
            enrollment.setCurrentStepId(null);
            enrollment.setNextStepAt(null);
            enrollment.setStatus(ENROLLMENT_STATUS_COMPLETED);
            enrollment.setCompletedAt(now);
            enrollment.setProgress(100);
            // 旅程完成计数 +1
            ScrmCustomerJourneyEntity journey = findJourneyOrThrow(enrollment.getJourneyId());
            journey.setCompletedCount((journey.getCompletedCount() == null ? 0 : journey.getCompletedCount()) + 1);
            recalcConversionRate(journey);
            journeyRepository.save(journey);
        } else if (waiting) {
            // WAIT: 推进至下一步, nextStepAt 已设置, 等待定时任务触发
            enrollment.setCurrentStepId(nextStepId);
        } else {
            // 其他: 推进至下一步, 清除 nextStepAt
            enrollment.setCurrentStepId(nextStepId);
            enrollment.setNextStepAt(null);
            // 若无下一步且非 END/WAIT, 标记完成
            if (nextStepId == null) {
                enrollment.setStatus(ENROLLMENT_STATUS_COMPLETED);
                enrollment.setCompletedAt(now);
                enrollment.setProgress(100);
                ScrmCustomerJourneyEntity journey = findJourneyOrThrow(enrollment.getJourneyId());
                journey.setCompletedCount((journey.getCompletedCount() == null ? 0 : journey.getCompletedCount()) + 1);
                recalcConversionRate(journey);
                journeyRepository.save(journey);
            } else {
                enrollment.setProgress(calcProgress(enrollment.getJourneyId(), nextStepId));
            }
        }
        enrollment = enrollmentRepository.save(enrollment);
        log.info("步骤执行完成: enrollmentId={}, stepId={}, stepType={}, result={}",
                enrollmentId, step.getId(), step.getStepType(), actionResult);
        return toEnrollmentDto(enrollment);
    }

    /**
     * 处理所有待执行的 WAIT 到期步骤 (定时任务用)
     * <p>
     * 查询当前账号下所有 nextStepAt 早于等于当前时间的 ACTIVE 入营记录,
     * 逐一调用 {@link #processStep(Long)} 推进。
     * </p>
     *
     * @return 处理的入营记录数
     */
    @Transactional
    public int processPendingSteps() {
        List<ScrmJourneyEnrollmentEntity> pending = enrollmentRepository
                .findPendingWaitEnrollments(LocalDateTime.now());
        if (pending.isEmpty()) {
            return 0;
        }
        int processed = 0;
        for (ScrmJourneyEnrollmentEntity e : pending) {
            try {
                processStep(e.getId());
                processed++;
            } catch (Exception ex) {
                log.warn("处理待执行步骤失败, 跳过: enrollmentId={}", e.getId(), ex);
            }
        }
        log.info("处理待执行 WAIT 步骤完成:, total={}, processed={}", pending.size(), processed);
        return processed;
    }

    // ============================================================
    // 步骤动作执行 (对接真实服务)
    // ============================================================

    /**
     * 执行 SEND_MESSAGE 动作: 向客户发送消息。
     * <p>从 config 解析 conversationId / content / messageType, 持久化一条出站会话消息。
     * 若未配置 conversationId 则记录警告并跳过 (需先创建会话)。</p>
     *
     * @param enrollment 入营记录
     * @param step       步骤实体
     */
    private void executeSendMessageAction(ScrmJourneyEnrollmentEntity enrollment,
                                          ScrmJourneyStepEntity step) {
        Map<String, Object> config = parseConfig(step.getConfig());
        Object conversationIdObj = config.get("conversationId");
        Object content = config.get("content");
        Object messageType = config.get("messageType");
        if (conversationIdObj == null) {
            log.warn("SEND_MESSAGE 动作未配置 conversationId, 跳过: enrollmentId={}, customerId={}",
                    enrollment.getId(), enrollment.getCustomerId());
            return;
        }
        Long conversationId = toLong(conversationIdObj);
        ScrmConversationMessageEntity message = new ScrmConversationMessageEntity();
        message.setConversationId(conversationId);
        message.setMessageType(messageType != null ? messageType.toString() : "TEXT");
        message.setDirection("OUTBOUND");
        message.setContent(content != null ? content.toString() : null);
        message.setSentAt(LocalDateTime.now());
        conversationMessageRepository.save(message);
        log.info("发送消息: enrollmentId={}, customerId={}, conversationId={}, messageType={}",
                enrollment.getId(), enrollment.getCustomerId(), conversationId, message.getMessageType());
    }

    /**
     * 执行 ADD_TAG 动作: 为客户打标签。
     * <p>从 config 解析 tagIds (逗号分隔或列表), 逐个检查是否已存在, 不存在则创建
     * ScrmTagCustomerEntity (tagSource=JOURNEY, isAuto=true)。</p>
     *
     * @param enrollment 入营记录
     * @param step       步骤实体
     */
    private void executeAddTagAction(ScrmJourneyEnrollmentEntity enrollment,
                                      ScrmJourneyStepEntity step) {
        Map<String, Object> config = parseConfig(step.getConfig());
        Object tagIdsObj = config.get("tagIds");
        if (tagIdsObj == null) {
            log.warn("ADD_TAG 动作未配置 tagIds, 跳过: enrollmentId={}", enrollment.getId());
            return;
        }
        Long customerId = enrollment.getCustomerId();
        List<Long> tagIds = parseTagIds(tagIdsObj);
        int assigned = 0;
        for (Long tagId : tagIds) {
            // 检查是否已存在该标签赋值
            Optional<ScrmTagCustomerEntity> existing = tagCustomerRepository
                    .findByCustomerIdAndTagId(customerId, tagId);
            if (existing.isPresent()) {
                continue;
            }
            ScrmTagCustomerEntity tagCustomer = new ScrmTagCustomerEntity();
            tagCustomer.setCustomerId(customerId);
            tagCustomer.setTagId(tagId);
            tagCustomer.setTagSource(TAG_SOURCE_JOURNEY);
            tagCustomer.setAssignedBy(DEFAULT_OPERATOR);
            tagCustomer.setAssignedAt(LocalDateTime.now());
            tagCustomer.setIsAuto(Boolean.TRUE);
            tagCustomerRepository.save(tagCustomer);
            assigned++;
        }
        log.info("打标签完成: enrollmentId={}, customerId={}, tagIds={}, assigned={}",
                enrollment.getId(), customerId, tagIds, assigned);
    }

    /**
     * 执行 SET_LIFECYCLE 动作: 更新客户生命周期。
     * <p>从 config 解析 lifecycle, 查找客户并更新 lifecycle 字段后持久化。</p>
     *
     * @param enrollment 入营记录
     * @param step       步骤实体
     */
    private void executeSetLifecycleAction(ScrmJourneyEnrollmentEntity enrollment,
                                            ScrmJourneyStepEntity step) {
        Map<String, Object> config = parseConfig(step.getConfig());
        Object lifecycle = config.get("lifecycle");
        if (lifecycle == null) {
            log.warn("SET_LIFECYCLE 动作未配置 lifecycle, 跳过: enrollmentId={}", enrollment.getId());
            return;
        }
        Long customerId = enrollment.getCustomerId();
        ScrmCustomerEntity customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.warn("SET_LIFECYCLE 客户不存在, 跳过: customerId={}", customerId);
            return;
        }
        String oldLifecycle = customer.getLifecycle();
        customer.setLifecycle(lifecycle.toString());
        customerRepository.save(customer);
        log.info("更新生命周期: enrollmentId={}, customerId={}, old={}, new={}",
                enrollment.getId(), customerId, oldLifecycle, lifecycle);
    }

    /**
     * 执行 WEBHOOK 动作: 回调通知外部服务。
     * <p>从 config 解析 url / method / body, 发起真实 HTTP 请求 (5s/10s 超时)。
     * 默认 POST + JSON body。异常仅记录日志不中断旅程。</p>
     *
     * @param enrollment 入营记录
     * @param step       步骤实体
     */
    private void executeWebhookAction(ScrmJourneyEnrollmentEntity enrollment,
                                       ScrmJourneyStepEntity step) {
        Map<String, Object> config = parseConfig(step.getConfig());
        Object url = config.get("url");
        if (url == null || url.toString().isBlank()) {
            log.warn("WEBHOOK 动作未配置 url, 跳过: enrollmentId={}", enrollment.getId());
            return;
        }
        // SSRF 防护: 仅允许公网 http/https 目标, 拒绝内网/回环/云 metadata 地址
        UrlSecurityUtils.validatePublicHttpUrl(url.toString());
        String method = config.get("method") != null ? config.get("method").toString().toUpperCase() : "POST";
        Object body = config.get("body");
        String bodyStr = body != null ? body.toString() : "{}";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url.toString()).toURL().openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(WEBHOOK_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(WEBHOOK_READ_TIMEOUT_MS);
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyStr.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                log.info("Webhook 回调成功: enrollmentId={}, customerId={}, url={}, method={}, code={}",
                        enrollment.getId(), enrollment.getCustomerId(), url, method, code);
            } else {
                log.warn("Webhook 回调非 2xx: enrollmentId={}, url={}, method={}, code={}",
                        enrollment.getId(), url, method, code);
            }
        } catch (IOException e) {
            log.warn("Webhook 回调异常: enrollmentId={}, url={}, error={}",
                    enrollment.getId(), url, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 解析 tagIds 配置 (支持逗号分隔字符串或 List)。
     *
     * @param tagIdsObj 原始配置值
     * @return 标签 ID 列表
     */
    @SuppressWarnings("unchecked")
    private List<Long> parseTagIds(Object tagIdsObj) {
        if (tagIdsObj instanceof List) {
            return ((List<Object>) tagIdsObj).stream()
                    .map(this::toLong)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        String str = tagIdsObj.toString();
        List<Long> result = new ArrayList<>();
        for (String part : str.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                Long id = toLong(trimmed);
                if (id != null) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    /**
     * 安全转换 Long (null / 非数字返回 null)。
     *
     * @param obj 原始值
     * @return Long 值
     */
    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.parseLong(obj.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 条件分支评估结果
     * @since V1.0
     * @author Hsi Chu
     */
    private static class ConditionEvalResult {
        /** 命中分支的下一步 ID */
        final Long branchNextStepId;
        /** 评估详情 (用于日志) */
        final String detail;

        ConditionEvalResult(Long branchNextStepId, String detail) {
            this.branchNextStepId = branchNextStepId;
            this.detail = detail;
        }
    }

    /**
     * 评估 CONDITION 步骤的条件分支
     * <p>
     * 解析 config 中的 field / operator / value, 从客户上下文取值比较,
     * 命中走 trueNextStep, 否则走 falseNextStep。
     * 支持 field:
     * <ul>
     *   <li>lifecycle / nickname / platformType → 客户实体字段</li>
     *   <li>tag.{tagKey} → 客户标签值 (从 scrm_customer_tag 查询)</li>
     * </ul>
     * 支持 operator: EQ / NE / CONTAINS / GT / LT
     * </p>
     *
     * @param enrollment 入营记录
     * @param step       条件步骤实体
     * @return 评估结果 (含分支下一步 ID 与详情)
     */
    private ConditionEvalResult evaluateCondition(ScrmJourneyEnrollmentEntity enrollment,
                                                   ScrmJourneyStepEntity step) {
        Map<String, Object> config = parseConfig(step.getConfig());
        String field = str(config.get("field"));
        String operator = str(config.get("operator"));
        String expectedValue = str(config.get("value"));
        Long trueNextStep = longVal(config.get("trueNextStep"));
        Long falseNextStep = longVal(config.get("falseNextStep"));
        // 从客户上下文取实际值
        ScrmCustomerEntity customer = customerRepository.findById(enrollment.getCustomerId()).orElse(null);
        String actualValue = extractCustomerFieldValue(customer, field);
        boolean matched = compareValues(actualValue, operator, expectedValue);
        Long branch = matched ? trueNextStep : falseNextStep;
        String detail = "field=" + field + ", operator=" + operator + ", expected=" + expectedValue
                + ", actual=" + actualValue + ", branch=" + (matched ? "true" : "false")
                + ", nextStepId=" + branch;
        return new ConditionEvalResult(branch, detail);
    }

    /**
     * 从客户实体或标签中提取字段值
     *
     * @param customer 客户实体 (可能为空)
     * @param field    字段名 (lifecycle / nickname / platformType / tag.{tagKey})
     * @return 字段值 (未命中返回 null)
     */
    private String extractCustomerFieldValue(ScrmCustomerEntity customer, String field) {
        if (customer == null || field == null) {
            return null;
        }
        if (field.startsWith("tag.")) {
            // 标签查询: tagKey → tagId (标签定义) → tagValue (客户-标签赋值)
            String tagCode = field.substring(4);
            Optional<ScrmCustomerTagEntity> tagDef = customerTagRepository
                    .findByTagCode(tagCode);
            if (tagDef.isEmpty()) {
                log.debug("标签定义不存在: tagCode={}", tagCode);
                return null;
            }
            return tagCustomerRepository
                    .findByCustomerIdAndTagId(customer.getId(), tagDef.get().getId())
                    .map(ScrmTagCustomerEntity::getTagValue)
                    .orElse(null);
        }
        switch (field) {
            case "lifecycle":
                return customer.getLifecycle();
            case "nickname":
                return customer.getNickname();
            case "platformType":
                return customer.getPlatformType();
            default:
                return null;
        }
    }

    /**
     * 按运算符比较实际值与期望值
     *
     * @param actual   实际值
     * @param operator 运算符: EQ / NE / CONTAINS / GT / LT
     * @param expected 期望值
     * @return 比较结果
     */
    private boolean compareValues(String actual, String operator, String expected) {
        if (operator == null) {
            return Objects.equals(actual, expected);
        }
        switch (operator.toUpperCase()) {
            case "EQ":
                return Objects.equals(actual, expected);
            case "NE":
                return !Objects.equals(actual, expected);
            case "CONTAINS":
                return actual != null && expected != null && actual.contains(expected);
            case "GT":
                return compareNumeric(actual, expected) > 0;
            case "LT":
                return compareNumeric(actual, expected) < 0;
            default:
                return Objects.equals(actual, expected);
        }
    }

    /**
     * 数值比较 (无法解析为数字时按字符串比较)
     */
    private int compareNumeric(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return String.CASE_INSENSITIVE_ORDER.compare(a, b);
        }
    }

    /**
     * 解析 WAIT 步骤的等待时长 (小时)
     *
     * @param configJson 步骤配置 JSON
     * @return 等待小时数 (解析失败返回 null)
     */
    private Integer parseWaitDuration(String configJson) {
        Map<String, Object> config = parseConfig(configJson);
        Object val = config.get("durationHours");
        return longVal(val) == null ? null : longVal(val).intValue();
    }

    /**
     * 解析步骤配置 JSON 为 Map
     *
     * @param json 配置 JSON
     * @return Map (解析失败返回空 Map)
     */
    Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("步骤配置 JSON 解析失败, 返回空 Map: json={}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * 写入进度日志
     *
     * @param enrollment   入营记录
     * @param step         步骤实体
     * @param actionResult 执行结果
     * @param actionDetail 执行详情
     * @param executedAt   执行时间
     */
    private void writeProgressLog(ScrmJourneyEnrollmentEntity enrollment, ScrmJourneyStepEntity step,
                                  String actionResult, String actionDetail, LocalDateTime executedAt) {
        ScrmJourneyProgressLogEntity logEntity = new ScrmJourneyProgressLogEntity();
        logEntity.setEnrollmentId(enrollment.getId());
        logEntity.setJourneyId(enrollment.getJourneyId());
        logEntity.setCustomerId(enrollment.getCustomerId());
        logEntity.setStepId(step.getId());
        logEntity.setStepName(step.getStepName());
        logEntity.setStepType(step.getStepType());
        logEntity.setActionResult(actionResult);
        logEntity.setActionDetail(actionDetail);
        logEntity.setExecutedAt(executedAt);
        progressLogRepository.save(logEntity);
    }

    /**
     * 标记入营记录完成 (当前步骤为空时调用)
     *
     * @param enrollment 入营记录
     */
    private void completeEnrollment(ScrmJourneyEnrollmentEntity enrollment) throws ScrmException {
        enrollment.setStatus(ENROLLMENT_STATUS_COMPLETED);
        enrollment.setCompletedAt(LocalDateTime.now());
        enrollment.setProgress(100);
        enrollment.setNextStepAt(null);
        enrollmentRepository.save(enrollment);
        // 旅程完成计数 +1
        ScrmCustomerJourneyEntity journey = findJourneyOrThrow(enrollment.getJourneyId());
        journey.setCompletedCount((journey.getCompletedCount() == null ? 0 : journey.getCompletedCount()) + 1);
        recalcConversionRate(journey);
        journeyRepository.save(journey);
    }

    /**
     * 计算入营进度百分比 (当前步骤序号 / 总步骤数 * 100)
     *
     * @param journeyId    旅程 ID
     * @param currentStepId 当前步骤 ID
     * @return 进度百分比 (0-100)
     */
    private int calcProgress(Long journeyId, Long currentStepId) {
        List<ScrmJourneyStepEntity> steps = stepRepository
                .findByJourneyIdOrderByStepOrderAsc(journeyId);
        if (steps.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < steps.size(); i++) {
            if (Objects.equals(steps.get(i).getId(), currentStepId)) {
                return (int) Math.round(i * 100d / steps.size());
            }
        }
        return 0;
    }

    /**
     * 重算旅程转化率 (完成数 / 入旅程数 * 100)
     *
     * @param journey 旅程实体
     */
    void recalcConversionRate(ScrmCustomerJourneyEntity journey) {
        int enrolled = journey.getEnrolledCount() == null ? 0 : journey.getEnrolledCount();
        int completed = journey.getCompletedCount() == null ? 0 : journey.getCompletedCount();
        journey.setConversionRate(enrolled > 0 ? Math.round(completed * 100d / enrolled * 100d) / 100d : 0d);
    }

    /**
     * 按主键查询旅程, 不存在或越权抛异常
     */
    ScrmCustomerJourneyEntity findJourneyOrThrow(Long id) throws ScrmException {
        ScrmCustomerJourneyEntity entity = journeyRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户旅程不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询步骤, 不存在或越权抛异常
     */
    ScrmJourneyStepEntity findStepOrThrow(Long id) throws ScrmException {
        ScrmJourneyStepEntity entity = stepRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "旅程步骤不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询入营记录, 不存在或越权抛异常
     */
    ScrmJourneyEnrollmentEntity findEnrollmentOrThrow(Long id) throws ScrmException {
        ScrmJourneyEnrollmentEntity entity = enrollmentRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "入营记录不存在: id=" + id));
        return entity;
    }

    /**
     * Object → String
     */
    String str(Object obj) {
        return obj == null ? null : obj.toString();
    }

    /**
     * Object → Long (兼容 Number / String)
     */
    Long longVal(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ============================================================
    // 实体转 DTO
    // ============================================================

    /**
     * 入营记录实体转 DTO
     */
    ScrmJourneyEnrollmentDto toEnrollmentDto(ScrmJourneyEnrollmentEntity entity) {
        ScrmJourneyEnrollmentDto dto = new ScrmJourneyEnrollmentDto();
        dto.setId(entity.getId());
        dto.setJourneyId(entity.getJourneyId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerNickname(entity.getCustomerNickname());
        dto.setCurrentStepId(entity.getCurrentStepId());
        dto.setEntrySource(entity.getEntrySource());
        dto.setStatus(entity.getStatus());
        dto.setEnteredAt(entity.getEnteredAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setExitedAt(entity.getExitedAt());
        dto.setExitReason(entity.getExitReason());
        dto.setLastStepAt(entity.getLastStepAt());
        dto.setNextStepAt(entity.getNextStepAt());
        dto.setProgress(entity.getProgress());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
