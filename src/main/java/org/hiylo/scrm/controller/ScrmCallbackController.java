/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCallbackController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.callback.ConversationEventCallbackDto;
import org.hiylo.scrm.dto.callback.RiskSignalCallbackDto;
import org.hiylo.scrm.dto.callback.TaskStatusCallbackDto;

import org.hiylo.scrm.dto.AiGenerateRequestDto;
import org.hiylo.scrm.dto.AiGenerateResponseDto;
import org.hiylo.scrm.entity.ScrmCampaignEntity;
import org.hiylo.scrm.entity.ScrmRiskSignalEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCampaignRepository;
import org.hiylo.scrm.repository.ScrmRiskSignalRepository;
import org.hiylo.scrm.service.ScrmAiReplyService;
import org.hiylo.scrm.service.ScrmCampaignExecutionLogService;
import org.hiylo.scrm.service.ScrmConversationMessageService;
import org.hiylo.scrm.service.ScrmNotificationService;
import org.hiylo.scrm.service.ScrmRiskRuleService;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 回调 Controller, 接收 scrm-server 内部回调。
 * <p>
 * 回调接口不要求网关鉴权, 通过 {@code X-Agent-Secret} 头校验调用方身份 (fail-closed:
 * 未配置 {@code scrm.callback.agent-secret} 时拒绝所有回调)。回调端点:
 * <ul>
 *   <li>{@code POST /scrm/callback/task-status} - 任务状态变更</li>
 *   <li>{@code POST /scrm/callback/risk-signal} - 风控信号</li>
 *   <li>{@code POST /scrm/callback/conversation-event} - 会话事件（消息收发）</li>
 *   <li>{@code POST /scrm/callback/ai-generate} - AI 自动回复生成（行为流调用）</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/callback")
@RequiredArgsConstructor
public class ScrmCallbackController {

    /** 任务状态: 成功 */
    private static final String TASK_STATUS_SUCCESS = "SUCCESS";

    /** 任务状态: 失败 */
    private static final String TASK_STATUS_FAILED = "FAILED";

    /** 任务状态: 运行中 */
    private static final String TASK_STATUS_RUNNING = "RUNNING";

    /** Campaign 终态: 已完成 */
    private static final String CAMPAIGN_COMPLETED = "COMPLETED";

    /** Campaign 终态: 已失败 */
    private static final String CAMPAIGN_FAILED = "FAILED";

    /** 营销任务数据访问层 */
    private final ScrmCampaignRepository campaignRepository;

    /** 风控信号数据访问层（持久化风控回调） */
    private final ScrmRiskSignalRepository riskSignalRepository;

    /** 会话消息服务（保存回调消息） */
    private final ScrmConversationMessageService conversationMessageService;

    /** SCRM 实时通知服务 (将回调事件通过 WebSocket 推送给前端, 异步非阻塞) */
    private final ScrmNotificationService notificationService;

    /** 营销任务执行日志服务 (回调时记录执行日志) */
    private final ScrmCampaignExecutionLogService executionLogService;

    /** AI 自动回复服务 (行为流调用 ai.generate 时经此回调生成回复) */
    private final ScrmAiReplyService scrmAiReplyService;

    /** 风险规则评估服务 (会话事件回调时异步评估风险规则, 命中后写入风控信号) */
    private final ScrmRiskRuleService scrmRiskRuleService;

    /** 回调 X-Agent-Secret 校验密钥（fail-closed: 未配置时拒绝所有回调） */
    @Value("${scrm.callback.agent-secret:}")
    private String agentSecret;

    /**
     * 任务状态回调: 根据 {@link TaskStatusCallbackDto#getStatus()} 更新 scrm_campaign 状态。
     * <ul>
     *   <li>SUCCESS → campaign.status = COMPLETED</li>
     *   <li>FAILED  → campaign.status = FAILED</li>
     *   <li>RUNNING → 不变, 仅记录日志</li>
     * </ul>
     *
     * @param dto        任务状态回调 DTO
     * @param agentSecretHeader X-Agent-Secret 头
     * @return OperationResponse
     */
    @RateLimit(capacity = 100, refillTokens = 100, refillPeriodSeconds = 60, message = "任务状态回调过于频繁，请稍后重试")
    @PostMapping("/task-status")
    public OperationResponse<Void> onTaskStatus(@RequestBody TaskStatusCallbackDto dto,
                                                  @RequestHeader(value = "X-Agent-Secret",
                                                          required = false) String agentSecretHeader) {
        validateAgentSecret(agentSecretHeader);
        if (dto == null || dto.getFlowId() == null) {
            log.warn("任务状态回调参数非法: dto={}", dto);
            throw ScrmException.badRequest("任务状态回调参数非法");
        }
        log.info("收到任务状态回调: flowId={}, sessionId={}, status={}", dto.getFlowId(), dto.getSessionId(), dto.getStatus());

        List<ScrmCampaignEntity> campaigns = campaignRepository.findByBehaviorFlowId(dto.getFlowId());
        if (campaigns.isEmpty()) {
            log.warn("任务状态回调未匹配到营销任务: flowId={}", dto.getFlowId());
            return OperationResponse.build();
        }

        String targetStatus = mapTaskStatusToCampaignStatus(dto.getStatus());
        if (targetStatus == null) {
            // RUNNING 或未知状态, 不变更 campaign 状态
            log.info("任务状态 {} 无需更新 campaign 状态, flowId={}", dto.getStatus(), dto.getFlowId());
            return OperationResponse.build();
        }

        for (ScrmCampaignEntity campaign : campaigns) {
            campaign.setStatus(targetStatus);
            // 回写 behaviorFlowId 保证一致性（首次回调时可能为空）
            if (campaign.getBehaviorFlowId() == null) {
                campaign.setBehaviorFlowId(dto.getFlowId());
            }
            campaignRepository.save(campaign);
            log.info("更新营销任务状态: campaignId={}, status={}", campaign.getId(), targetStatus);

            // 记录执行日志: 回调 SUCCESS → status=SUCCESS, 回调 FAILED → status=FAILED
            try {
                String logStatus = TASK_STATUS_SUCCESS.equals(dto.getStatus())
                        ? ScrmCampaignExecutionLogService.STATUS_SUCCESS
                        : ScrmCampaignExecutionLogService.STATUS_FAILED;
                executionLogService.log(
                        campaign.getId(),
                        dto.getFlowId(),
                        ScrmCampaignExecutionLogService.ACTION_CALLBACK,
                        logStatus,
                        dto.getErrorCode(),
                        dto.getErrorMessage(),
                        "scrm-callback");
            } catch (Exception e) {
                // 日志写入失败不阻断回调主流程
                log.warn("记录回调执行日志失败 (不影响回调主流程): campaignId={}, err={}",
                        campaign.getId(), e.getMessage());
            }
        }

        // 追加: 通过 WebSocket 异步推送任务状态变更通知给前端 (不阻塞回调返回)
        try {
            notificationService.notifyTaskStatus(
                    campaigns.get(0).getId(),
                    dto.getStatus(),
                    dto.getErrorMessage());
        } catch (Exception e) {
            log.warn("任务状态变更通知推送失败 (不影响回调主流程): flowId={}, err={}",
                    dto.getFlowId(), e.getMessage());
        }
        return OperationResponse.build();
    }

    /**
     * 风控信号回调: 将 scrm-server 命中的风控规则信号持久化到 {@code scrm_risk_signal} 表,
     * 供 SCRM 侧账号风控态势感知与看板聚合使用。
     *
     * @param dto                风控信号回调 DTO
     * @param agentSecretHeader  X-Agent-Secret 头
     * @return OperationResponse
     */
    @RateLimit(capacity = 100, refillTokens = 100, refillPeriodSeconds = 60, message = "风控信号回调过于频繁，请稍后重试")
    @PostMapping("/risk-signal")
    public OperationResponse<Void> onRiskSignal(@RequestBody RiskSignalCallbackDto dto,
                                                  @RequestHeader(value = "X-Agent-Secret",
                                                          required = false) String agentSecretHeader) {
        validateAgentSecret(agentSecretHeader);
        if (dto == null) {
            log.warn("风控信号回调 dto 为空");
            throw ScrmException.badRequest("风控信号回调 dto 为空");
        }
        log.warn("收到风控信号: ruleId={}, personaId={}, accountId={}, signalType={}, riskLevel={}, detail={}",
                dto.getRuleId(),
                dto.getPersonaId(), dto.getAccountId(), dto.getSignalType(), dto.getRiskLevel(), dto.getDetail());

        ScrmRiskSignalEntity entity = new ScrmRiskSignalEntity();
        entity.setRuleId(dto.getRuleId());
        entity.setPersonaId(dto.getPersonaId());
        entity.setAccountId(parseAccountId(dto.getAccountId()));
        entity.setSignalType(dto.getSignalType());
        entity.setRiskLevel(dto.getRiskLevel());
        entity.setDetail(dto.getDetail());
        entity.setTriggeredAt(dto.getTriggeredAt() != null ? dto.getTriggeredAt() : LocalDateTime.now());
        riskSignalRepository.save(entity);
        log.info("风控信号已持久化: id={}, ruleId={}, signalType={}, riskLevel={}",
                entity.getId(), entity.getRuleId(), entity.getSignalType(), entity.getRiskLevel());

        // 追加: 通过 WebSocket 异步推送风控告警通知给前端 (不阻塞回调返回)
        try {
            notificationService.notifyRiskSignal(
                    dto.getRuleId(),
                    dto.getPersonaId(),
                    dto.getRiskLevel(),
                    dto.getDetail());
        } catch (Exception e) {
            log.warn("风控告警通知推送失败 (不影响回调主流程): ruleId={}, err={}",
                    dto.getRuleId(), e.getMessage());
        }
        return OperationResponse.build();
    }

    /**
     * 会话事件回调: 调用 {@link ScrmConversationMessageService#saveMessageFromCallback} 保存消息,
     * 并异步触发风险规则评估 ({@link ScrmRiskRuleService#evaluateMessage})。
     * <p>
     * 风险评估在虚拟线程中执行, 避免阻塞回调返回; 命中规则时由服务层持久化风控信号并推送告警。
     * 评估异常仅记录日志, 不影响回调主流程。
     * </p>
     *
     * @param dto                会话事件回调 DTO
     * @param agentSecretHeader  X-Agent-Secret 头
     * @return OperationResponse
     */
    @RateLimit(capacity = 100, refillTokens = 100, refillPeriodSeconds = 60, message = "会话事件回调过于频繁，请稍后重试")
    @PostMapping("/conversation-event")
    public OperationResponse<Void> onConversationEvent(@RequestBody ConversationEventCallbackDto dto,
                                                         @RequestHeader(value = "X-Agent-Secret",
                                                                   required = false) String agentSecretHeader) {
        validateAgentSecret(agentSecretHeader);
        if (dto == null) {
            log.warn("会话事件回调 dto 为空");
            throw ScrmException.badRequest("会话事件回调 dto 为空");
        }
        log.info("收到会话事件回调: platformType={}, accountId={}, customerId={}, messageType={}, direction={}",
                dto.getPlatformType(),
                dto.getAccountId(), dto.getCustomerId(), dto.getMessageType(), dto.getDirection());
        conversationMessageService.saveMessageFromCallback(dto);

        // 异步触发风险规则评估: 在虚拟线程中执行, 捕获调用线程的当前用户归属账号并透传到异步线程,
        // 避免 ThreadLocal 在异步线程丢失; 评估异常不阻断回调主流程
        asyncEvaluateRiskRules(dto);

        // 通过 WebSocket 异步推送新会话消息通知给前端 (不阻塞回调返回)
        try {
            Long convId = dto.getConversationId() != null && !dto.getConversationId().isBlank()
                    ? Long.valueOf(dto.getConversationId()) : null;
            notificationService.notifyNewMessage(
                    convId,
                    dto.getDirection(),
                    dto.getMessageType(),
                    dto.getContent());
        } catch (Exception e) {
            log.warn("会话事件通知推送失败 (不影响回调主流程): platformType={}, err={}",
                    dto.getPlatformType(), e.getMessage());
        }
        return OperationResponse.build();
    }

    /**
     * AI 自动回复生成回调: 行为流脚本执行 {@code ai.generate(lastMsg, personaId, replyRules)} 时回调,
     * 由 scrm-server 调用 ai-server 生成符合人设风格的回复内容。
     * <p>校验 {@code X-Agent-Secret} 头与 {@code incomingMessage} 非空, 记录请求与响应日志,
     * 返回包含回复内容、生效人设 / 模型 / 耗时的 {@link AiGenerateResponseDto}。</p>
     *
     * @param dto               AI 生成请求 DTO
     * @param agentSecretHeader X-Agent-Secret 头
     * @return OperationResponse 包含 AI 生成的回复内容
     */
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "AI 生成回调过于频繁，请稍后重试")
    @PostMapping("/ai-generate")
    public OperationResponse<AiGenerateResponseDto> onAiGenerate(@Valid @RequestBody AiGenerateRequestDto dto,
                                                                  @RequestHeader(value = "X-Agent-Secret",
                                                                          required = false) String agentSecretHeader) {
        validateAgentSecret(agentSecretHeader);
        if (dto == null || dto.getIncomingMessage() == null || dto.getIncomingMessage().isBlank()) {
            log.warn("AI 生成回调参数非法: incomingMessage 为空, sessionId={}", dto == null ? null : dto.getSessionId());
            throw ScrmException.badRequest("入站消息不能为空");
        }
        log.info("收到 AI 生成回调: personaId={}, sessionId={}, incomingMessage={}",
                dto.getPersonaId(), dto.getSessionId(), truncate(dto.getIncomingMessage(), 80));

        long start = System.currentTimeMillis();
        String reply = scrmAiReplyService.generateReply(dto.getIncomingMessage(),
                dto.getPersonaId(), dto.getReplyRules());
        long latencyMs = System.currentTimeMillis() - start;
        String effectivePersonaId = scrmAiReplyService.resolvePersonaId(dto.getPersonaId());
        String model = scrmAiReplyService.getModel();

        AiGenerateResponseDto respDto = AiGenerateResponseDto.builder()
                .reply(reply)
                .personaId(effectivePersonaId)
                .model(model)
                .latencyMs(latencyMs)
                .build();
        log.info("AI 生成回调完成: personaId={}, model={}, latencyMs={}, reply={}",
                effectivePersonaId, model, latencyMs, truncate(reply, 80));
        return OperationResponse.build(respDto);
    }

    // ==================== 内部方法 ====================

    /**
     * 异步触发风险规则评估。
     * <p>
     * 在 Java 21 虚拟线程中执行评估, 避免阻塞回调返回。调用线程先捕获当前用户归属账号并透传到
     * 异步线程 (重新写入请求上下文), 避免 ThreadLocal 在异步线程丢失导致
     * 风控信号归属字段错乱。命中规则时由服务层记录 warn 日志并推送告警, 评估异常仅记录
     * 日志, 不影响回调主流程。
     * </p>
     *
     * @param dto 会话事件回调 DTO
     */
    private void asyncEvaluateRiskRules(ConversationEventCallbackDto dto) {
        // 在调用线程捕获  异步线程无法访问调用线程的 ThreadLocal
        Thread.startVirtualThread(() -> {
            try {
                List<ScrmRiskSignalEntity> signals = scrmRiskRuleService.evaluateMessage(dto);
                if (signals != null && !signals.isEmpty()) {
                    log.warn("会话事件触发风控规则: platformType={}, accountId={}, hitCount={}",
                            dto.getPlatformType(), dto.getAccountId(), signals.size());
                }
            } catch (Exception e) {
                log.warn("会话事件风控规则评估失败 (不影响回调主流程): platformType={}, err={}",
                        dto.getPlatformType(), e.getMessage());
            }
        });
    }

    /**
     * 校验 X-Agent-Secret 头 (fail-closed)。
     * <p>
     * 未配置 {@code scrm.callback.agent-secret} 或请求头不匹配时一律拒绝 (返回 401),
     * 避免默认零鉴权导致回调接口被任意调用方伪造。
     * </p>
     *
     * @param agentSecretHeader 请求头值
     */
    private void validateAgentSecret(String agentSecretHeader) {
        if (agentSecret == null || agentSecret.isEmpty()) {
            log.warn("回调 X-Agent-Secret 未配置 (agent-secret 为空), 拒绝回调");
            throw ScrmException.unauthorized("回调鉴权失败: agent-secret 未配置");
        }
        // 恒定时间比较, 避免通过请求耗时差异推断密钥内容 (时序侧信道)
        if (agentSecretHeader == null
                || !MessageDigest.isEqual(agentSecret.getBytes(StandardCharsets.UTF_8),
                agentSecretHeader.getBytes(StandardCharsets.UTF_8))) {
            log.warn("回调 X-Agent-Secret 校验失败");
            throw ScrmException.unauthorized("回调 X-Agent-Secret 校验失败");
        }
    }

    /**
     * 将任务状态映射为 Campaign 状态。
     *
     * @param taskStatus 任务状态（SUCCESS / FAILED / RUNNING）
     * @return Campaign 状态, RUNNING / 未知返回 null（表示不变更）
     */
    private String mapTaskStatusToCampaignStatus(String taskStatus) {
        if (taskStatus == null) {
            return null;
        }
        switch (taskStatus.toUpperCase()) {
            case TASK_STATUS_SUCCESS:
                return CAMPAIGN_COMPLETED;
            case TASK_STATUS_FAILED:
                return CAMPAIGN_FAILED;
            case TASK_STATUS_RUNNING:
            default:
                return null;
        }
    }

    /**
     * 将回调 DTO 中的 accountId（String）解析为 Long。
     * <p>
     * {@code RiskSignalCallbackDto.accountId} 为 String 类型, 而 {@code ScrmRiskSignalEntity.accountId}
     * 为 Long。空串或非数字时返回 null, 避免阻断风控信号持久化。
     * </p>
     *
     * @param accountId 回调中的账号 ID 字符串
     * @return 解析后的 Long, 不可解析时为 null
     */
    private Long parseAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(accountId.trim());
        } catch (NumberFormatException e) {
            log.warn("风控信号 accountId 非数字, 置 null: accountId={}", accountId);
            return null;
        }
    }

    /**
     * 截断字符串用于日志输出, 避免日志过长刷屏。
     *
     * @param text   原始文本
     * @param maxLen 最大长度
     * @return 截断后的文本 (超长时追加 "..."), 输入为 null 时返回 null
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
