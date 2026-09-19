/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmServerApplicationTest.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm;

import org.hiylo.scrm.service.NoOpPushNotificationService;
import org.hiylo.scrm.service.PushNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCRM 服务启动测试
 * <p>
 * 验证 Spring 上下文能正常加载, Bean 注入无循环依赖。
 * 使用 test profile, 通过 H2 内存数据库替代 PostgreSQL, 不连接任何外部服务。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@SpringBootTest
@ActiveProfiles("test")
class ScrmServerApplicationTest {

    /**
     * 推送服务抽象: test profile 下个推开关 (scrm.push.getui.enabled) 关闭,
     * 上下文必须提供占位实现供通知中心按类型注入
     */
    @Autowired
    private PushNotificationService pushNotificationService;

    /**
     * 验证 Spring 上下文加载, 所有 Bean 能正常注入
     */
    @Test
    void contextLoads() {
        // 验证 Spring 上下文加载
    }

    /**
     * 个推关闭时推送占位 Bean 必须存在 (回归防护):
     * NoOpPushNotificationService 上若挂 {@code @ConditionalOnMissingBean(name = "pushNotificationService")}
     * 会看到自己正在注册的同名定义而被跳过, 导致 PushNotificationService 类型无 Bean,
     * 通知中心 PUSH 渠道注入直接失败。
     */
    @Test
    @DisplayName("个推未启用: PushNotificationService 唯一可用且为 NoOp 占位实现")
    void pushNotificationServiceFallsBackToNoOpWhenGetuiDisabled() {
        assertThat(pushNotificationService).isInstanceOf(NoOpPushNotificationService.class);
    }
}
