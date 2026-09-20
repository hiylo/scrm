/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : NoOpPushNotificationService.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 个推推送服务占位实现。
 * <p>
 * 在个推 AppKey / MasterSecret 凭证未提供前 (即 {@code scrm.push.getui.enabled} 为 false 或缺失),
 * 作为 {@link PushNotificationService} 的默认实现注入, 所有推送方法仅通过日志记录推送意图,
 * 不实际调用个推 SDK 发送。
 * </p>
 * <p>
 * 与 {@link GeTuiPushNotificationService} 通过同一属性 {@code scrm.push.getui.enabled} 互斥:
 * <ul>
 *   <li>{@code enabled=false} 或缺失 (默认) → 本占位实现生效</li>
 *   <li>{@code enabled=true} → {@link GeTuiPushNotificationService} 注册为
 *       {@code pushNotificationService} Bean, 本占位实现因属性条件不满足而不创建</li>
 * </ul>
 * 两个条件的 havingValue 互斥且覆盖全部取值, 任一时刻只会有一个实现注册, 无需额外的
 * {@code @ConditionalOnMissingBean} 兜底 —— 该条件写在本类上时会看到自己正在注册的
 * {@code pushNotificationService} 定义, 反而导致占位实现被跳过 (个推关闭时类型无 Bean 可注入)。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Service("pushNotificationService")
@Primary
@ConditionalOnProperty(prefix = "scrm.push.getui", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpPushNotificationService implements PushNotificationService {

    /**
     * 向单个用户推送 (占位实现: 仅记录日志)。
     *
     * @param userId  目标用户 ID
     * @param title   通知标题
     * @param content 通知正文
     * @param type    通知类型
     * @param extra   附加透传数据 (可空)
     */
    @Override
    public void pushToUser(String userId, String title, String content, String type, Map<String, Object> extra) {
        log.info("[NoOp Push] 推送意图: userId={}, type={}, title={}, content={}, extra={}",
                userId, type, title, content, extra);
    }

    /**
     * 向多个用户推送 (占位实现: 仅记录日志)。
     *
     * @param userIds 目标用户 ID 列表
     * @param title   通知标题
     * @param content 通知正文
     * @param type    通知类型
     * @param extra   附加透传数据 (可空)
     */
    @Override
    public void pushToUsers(List<String> userIds, String title, String content, String type, Map<String, Object>
            extra) {
        log.info("[NoOp Push] 批量推送意图: userIds={}, type={}, title={}, content={}, extra={}",
                userIds, type, title, content, extra);
    }
}
