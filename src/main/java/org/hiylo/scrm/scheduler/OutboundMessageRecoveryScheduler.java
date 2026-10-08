/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OutboundMessageRecoveryScheduler.java
 * Date : 2026/10/07 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.service.ScrmOutboundMessageService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 出站消息超时回收调度器。
 * <p>
 * 执行侧（wx-console / wechat-agent）取走出站消息后若崩溃 / 断网未回执，
 * IN_PROGRESS 记录会卡死。本调度器按配置周期（默认 60s）扫描超时消息，
 * 退回 PENDING 重新入队（自动回复场景可被同一执行侧或其他实例重试）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundMessageRecoveryScheduler {

    /** 出站队列平台范围：个人微信（UIA 执行侧轮询）+ 企业微信（开放 API 直发，供兜底重试） */
    private static final List<String> PLATFORM_TYPES = List.of("wechat_personal", "wework");

    /** 发送中超时秒数（IN_PROGRESS 超过该时长未回执视为执行侧丢失） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    /** 出站消息队列服务 */
    private final ScrmOutboundMessageService outboundMessageService;

    /**
     * 周期扫描并回收超时未回执的出站消息（覆盖全部出站队列平台）。
     * <p>
     * 默认每 60s 执行一次，可通过 {@code scrm.outbound.recover-interval-ms} 配置调整。
     * 异常不影响调度器运行，仅记录日志。
     * </p>
     */
    @Scheduled(fixedDelayString = "${scrm.outbound.recover-interval-ms:60000}")
    public void recoverTimedOut() {
        for (String platformType : PLATFORM_TYPES) {
            try {
                int recovered = outboundMessageService.recoverTimedOut(
                        platformType, DEFAULT_TIMEOUT_SECONDS);
                if (recovered > 0) {
                    log.info("出站消息超时回收: platform={}, 回收 {} 条", platformType, recovered);
                }
            } catch (Exception e) {
                log.warn("出站消息超时回收失败 (不影响调度器): platform={}, err={}",
                        platformType, e.getMessage());
            }
        }
    }
}