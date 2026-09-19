/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : PushNotificationService.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import java.util.List;
import java.util.Map;

/**
 * 个推推送服务抽象。
 * <p>
 * 定义向业务员 APP (Android / iOS) 推送通知的能力, 底层对接个推 (GeTui) SDK。
 * 目前项目尚未提供个推 AppKey / MasterSecret, 默认使用 {@link NoOpPushNotificationService}
 * 占位实现 (仅记录日志, 不实际发送); 后续凭证就绪后提供
 * {@code GeTuiPushNotificationService} 实现并通过 {@code @Primary} 或
 * {@code @ConditionalOnProperty} 替换占位实现即可, 调用方无需改动。
 * </p>
 * <p>
 * 推送目标通过 {@code ScrmUserDeviceRepository} 按 userId 查询其注册的 client_id 列表,
 * 由实现类负责解析设备列表并调用个推 API。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface PushNotificationService {

    /**
     * 向单个用户的所有活跃设备推送通知。
     *
     * @param userId  目标用户 ID
     * @param title   通知标题
     * @param content 通知正文
     * @param type    通知类型 (如 TASK_STATUS / RISK_SIGNAL / SYSTEM)
     * @param extra   附加透传数据 (可空)
     */
    void pushToUser(String userId, String title, String content, String type, Map<String, Object> extra);

    /**
     * 向多个用户的所有活跃设备推送通知 (批量推送)。
     *
     * @param userIds 目标用户 ID 列表
     * @param title   通知标题
     * @param content 通知正文
     * @param type    通知类型
     * @param extra   附加透传数据 (可空)
     */
    void pushToUsers(List<String> userIds, String title, String content, String type, Map<String, Object> extra);
}
