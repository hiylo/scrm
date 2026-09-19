/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CallbackSecretStartupValidator.java
 * Date : 2026/09/14 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;

/**
 * 回调共享密钥启动校验器。
 * <p>
 * 校验平台回调 / Webhook 端点 ({@code /scrm/callback/**}、{@code /scrm/webhooks/**}) 使用的
 * {@code X-Agent-Secret} 共享密钥是否已配置。该密钥用于校验自动化执行侧 (scrm-server)
 * 发起的服务间回调请求身份, 与用户登录认证 (JWT) 无关。
 * </p>
 * <p>
 * fail-closed 策略: 非 dev/local/test 环境且 {@code scrm.callback.agent-secret} 为空时,
 * 启动即抛出 {@link IllegalStateException} 阻断上线, 防止零鉴权回调端点暴露;
 * dev/local/test 环境仅记录警告 (回调不可用但服务可启动, 便于本地调试)。
 * </p>
 *
 * @author Hsi Chu
 */
@Configuration
@Slf4j
public class CallbackSecretStartupValidator {

    /** 允许缺失回调密钥的开发类环境 */
    private static final Set<String> DEV_LIKE_PROFILES = Set.of("dev", "local", "test");

    /** 回调共享密钥, 由 scrm-server 通过 X-Agent-Secret 头携带 */
    @Value("${scrm.callback.agent-secret:}")
    private String agentSecret;

    /** 当前激活的 Spring profile (可为空) */
    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    /**
     * 启动时校验回调共享密钥。
     * <p>
     * 非 dev/local/test 环境未配置密钥时抛出 {@link IllegalStateException} 阻断启动,
     * 防止零鉴权回调端点上线。
     * </p>
     */
    @PostConstruct
    public void validateAgentSecretConfigured() {
        if (agentSecret != null && !agentSecret.isEmpty()) {
            return;
        }
        if (isDevLikeProfile()) {
            log.warn("回调 X-Agent-Secret 未配置, 回调接口 (/scrm/callback/**, /scrm/webhooks/**)"
                    + "将拒绝所有请求; 开发环境如需启用回调请设置环境变量 SCRM_CALLBACK_AGENT_SECRET");
            return;
        }
        throw new IllegalStateException(
                "环境变量 SCRM_CALLBACK_AGENT_SECRET 未配置 (config key: scrm.callback.agent-secret), "
                        + "回调接口 (/scrm/callback/**, /scrm/webhooks/**) 将拒绝所有请求, "
                        + "生产环境必须显式配置回调共享密钥");
    }

    /**
     * 判断当前激活 profile 是否属于开发类环境。
     *
     * @return true=dev/local/test 之一 (或未激活任何 profile)
     */
    private boolean isDevLikeProfile() {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return true;
        }
        return Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .anyMatch(DEV_LIKE_PROFILES::contains);
    }
}
