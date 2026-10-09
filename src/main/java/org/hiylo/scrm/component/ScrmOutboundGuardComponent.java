/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOutboundGuardComponent.java
 * Date : 2026/10/09 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmBlacklistCheckDto;
import org.hiylo.scrm.dto.ScrmRiskAssessmentDto;
import org.hiylo.scrm.dto.ScrmRiskEventDto;
import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmOutboundMessageEntity;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmOutboundMessageRepository;
import org.hiylo.scrm.service.ScrmAccountService;
import org.hiylo.scrm.service.ScrmBlacklistRuleService;
import org.hiylo.scrm.service.ScrmBlacklistService;
import org.hiylo.scrm.service.ScrmOutboundMessageService;
import org.hiylo.scrm.service.ScrmRiskEventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 出站消息发送前守卫 (防封体系执行层)。
 * <p>
 * 在 {@code ScrmOutboundMessageService.enqueueFromMessage} 入队前执行, 命中任一规则即拦截:
 * <ul>
 *   <li>账号冻结 (loginState=FROZEN) / 离线 (LOGOUT) → 拦截, 防止向未登录账号发送导致触发平台风控</li>
 *   <li>目标客户已进黑名单 → 拦截</li>
 *   <li>单账号发送频率超限 (近 rateWindowSeconds 秒内出站 PENDING+SENT 数 &gt; maxPerWindow) → 拦截, 降频防封</li>
 * </ul>
 * 拦截不抛异常 (防止故障蔓延): 返回 {@link GuardResult}, 由调用方把出站消息置 BLOCKED 并跳过事件发布。
 * 命中拦截时经 {@link ScrmRiskEventService} 记录 SEND_BLOCKED 风险事件, 供风控看板与审计追踪。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-10-09
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScrmOutboundGuardComponent {

    /** 账号数据访问层 */
    private final ScrmAccountRepository accountRepository;
    /** 出站队列数据访问层 (频率统计) */
    private final ScrmOutboundMessageRepository outboundRepository;
    /** 黑名单服务 */
    private final ScrmBlacklistService blacklistService;

    /** 黑名单规则服务 (内容敏感词/模式规则评估) */
    private final ScrmBlacklistRuleService blacklistRuleService;
    /** 风险事件服务 */
    private final ScrmRiskEventService riskEventService;

    /** 账号服务 (命中违规信号后自动暂停账号) */
    private final ScrmAccountService accountService;

    /** 频率窗口 (秒), 默认 60 */
    @Value("${scrm.outbound.guard.rate-window-seconds:60}")
    private long rateWindowSeconds;

    /** 窗口内最大发送条数, 默认 30 (个人微信建议高频发送易触发风控) */
    @Value("${scrm.outbound.guard.max-per-window:30}")
    private long maxPerWindow;

    /** 是否拦截离线账号发送 (默认 true; 离线账号无法真正送达, 但仍入队会堆积) */
    @Value("${scrm.outbound.guard.block-offline:true}")
    private boolean blockOffline;

    /** 命中账号违规信号后是否自动暂停账号 (置 FROZEN, 默认 true; 频率超限/敏感词命中视为风控信号) */
    @Value("${scrm.outbound.guard.auto-pause-on-block:true}")
    private boolean autoPauseOnBlock;

    /** 风险事件类别 */
    private static final String RISK_CATEGORY = "SEND_BLOCKED";

    /** 触发自动暂停账号的拦截信号 (账号风险升高; ACCOUNT_FROZEN 已冻结 / TARGET_BLACKLISTED 目标黑名单除外) */
    private static final java.util.Set<String> AUTO_PAUSE_SIGNALS =
            java.util.Set.of("SEND_RATE_LIMIT", "SENSITIVE_CONTENT");
    /** 风险等级 */
    private static final String RISK_LEVEL = "MEDIUM";

    /**
     * 守卫结果: 允许发送或携带拦截原因。
     *
     * @param allowed 是否放行
     * @param reason  拦截原因 (allowed=false 时)
     * @param signal  拦截信号类型
     */
    public record GuardResult(boolean allowed, String reason, String signal) {
        /** 放行结果 */
        public static GuardResult allow() {
            return new GuardResult(true, null, null);
        }

        /** 拦截结果 */
        public static GuardResult block(String reason, String signal) {
            return new GuardResult(false, reason, signal);
        }
    }

    /**
     * 出站消息发送前守卫。
     *
     * @param platformType      平台类型
     * @param accountId         发送账号 ID
     * @param targetPlatformId  目标平台 ID (个人微信 wxid 等)
     * @param content           消息内容 (可为 null, 媒体消息无文本)
     * @return 守卫结果
     */
    public GuardResult guard(String platformType, Long accountId,
                             String targetPlatformId, String content) {
        // 1) 账号状态守卫: 冻结 / 离线拦截
        Optional<ScrmAccountEntity> accountOpt =
                accountId != null ? accountRepository.findById(accountId) : Optional.empty();
        if (accountOpt.isPresent()) {
            ScrmAccountEntity account = accountOpt.get();
            if ("FROZEN".equals(account.getLoginState())) {
                return guardBlock(accountId, targetPlatformId,
                        "账号已冻结, 禁止发送: accountId=" + accountId + ", state=" + account.getLoginState(),
                        "ACCOUNT_FROZEN");
            }
            if (blockOffline && "LOGOUT".equals(account.getLoginState())) {
                return guardBlock(accountId, targetPlatformId,
                        "账号离线, 禁止发送: accountId=" + accountId + ", state=" + account.getLoginState(),
                        "ACCOUNT_OFFLINE");
            }
        }

        // 2) 黑名单守卫: 目标客户是否已进黑名单
        if (targetPlatformId != null && !targetPlatformId.isBlank()) {
            ScrmBlacklistCheckDto checkDto = new ScrmBlacklistCheckDto();
            checkDto.setTargetType("CUSTOMER");
            checkDto.setTargetValue(targetPlatformId);
            try {
                Map<String, Object> check = blacklistService.checkBlacklist(checkDto);
                Object inList = check.get("inBlacklist");
                if (Boolean.TRUE.equals(inList)) {
                    return guardBlock(accountId, targetPlatformId,
                            "目标客户在黑名单, 禁止发送: target=" + targetPlatformId,
                            "TARGET_BLACKLISTED");
                }
            } catch (Exception e) {
                // 黑名单服务异常不阻塞发送, 记录日志降级放行 (防故障蔓延)
                log.warn("出站守卫黑名单检查异常, 降级放行: target={}, err={}",
                        targetPlatformId, e.getMessage());
            }
        }

        // 3) 频率守卫: 单账号近 rateWindowSeconds 内出站数超限 → 降频拦截
        if (accountId != null) {
            LocalDateTime since = LocalDateTime.now().minusSeconds(rateWindowSeconds);
            long recent = outboundRepository.countByAccountIdAndStatusInAndCreateTimeAfter(
                    accountId,
                    java.util.List.of(
                            ScrmOutboundMessageService.STATUS_PENDING,
                            ScrmOutboundMessageService.STATUS_IN_PROGRESS,
                            ScrmOutboundMessageService.STATUS_SENT),
                    since);
            if (recent >= maxPerWindow) {
                return guardBlock(accountId, targetPlatformId,
                        "发送频率超限, 触发降频: accountId=" + accountId
                                + ", recent=" + recent + "/" + maxPerWindow
                                + " (window=" + rateWindowSeconds + "s)",
                        "SEND_RATE_LIMIT");
            }
        }

        // 4) 内容守卫: 发送内容命中启用规则 (PATTERN / CONTAINS / REGEX / MATCH) → 敏感词拦截
        if (content != null && !content.isBlank()) {
            ScrmRiskAssessmentDto assessment = new ScrmRiskAssessmentDto();
            assessment.setTargetType("MESSAGE");
            assessment.setTargetValue(targetPlatformId != null ? targetPlatformId
                    : String.valueOf(accountId));
            assessment.setCustomerId(null);
            assessment.setContext(new java.util.LinkedHashMap<>());
            assessment.getContext().put("content", content);
            try {
                Map<String, Object> eval = blacklistRuleService.evaluateAllRules(assessment);
                Object triggered = eval.get("totalTriggered");
                if (triggered instanceof Number n && n.longValue() > 0) {
                    return guardBlock(accountId, targetPlatformId,
                            "发送内容命中敏感词规则: accountId=" + accountId
                                    + ", triggered=" + triggered
                                    + ", contentLen=" + content.length(),
                            "SENSITIVE_CONTENT");
                }
            } catch (Exception e) {
                // 规则评估异常不阻塞发送 (防故障蔓延)
                log.warn("出站守卫内容规则评估异常, 降级放行: err={}", e.getMessage());
            }
        }

        log.debug("出站守卫放行: accountId={}, target={}", accountId, targetPlatformId);
        return GuardResult.allow();
    }

    /**
     * 命中拦截: 记录风险事件并返回拦截结果。
     *
     * @param accountId 发送账号 ID
     * @param target    目标平台 ID
     * @param reason    拦截原因
     * @param signal    拦截信号类型
     * @return 拦截结果
     */
    private GuardResult guardBlock(Long accountId, String target, String reason, String signal) {
        log.warn("出站守卫拦截: {}", reason);
        try {
            ScrmRiskEventDto event = new ScrmRiskEventDto();
            event.setTargetType("CUSTOMER");
            event.setTargetValue(target != null ? target : String.valueOf(accountId));
            event.setRiskCategory(RISK_CATEGORY);
            event.setRiskLevel(RISK_LEVEL);
            event.setTriggerReason(reason);
            riskEventService.createEvent(event);
        } catch (Exception e) {
            // 风险事件记录失败不影响拦截决定
            log.warn("出站守卫记录风险事件失败: err={}", e.getMessage());
        }
        // 自动处置: 账号违规信号 → 自动暂停账号 (置 FROZEN), 防止继续触发平台风控
        if (autoPauseOnBlock && accountId != null && AUTO_PAUSE_SIGNALS.contains(signal)) {
            try {
                accountService.updateLoginState(accountId, "FROZEN",
                        "出站守卫自动暂停: signal=" + signal + ", " + reason);
                log.warn("出站守卫自动暂停账号: accountId={}, signal={}", accountId, signal);
            } catch (Exception e) {
                // 自动暂停失败不影响拦截决定 (账号状态更新异常降级)
                log.warn("出站守卫自动暂停账号失败, 不影响拦截: accountId={}, err={}",
                        accountId, e.getMessage());
            }
        }
        return GuardResult.block(reason, signal);
    }
}
