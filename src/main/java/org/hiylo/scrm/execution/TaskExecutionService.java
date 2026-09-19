/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : TaskExecutionService.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.execution;

import org.hiylo.scrm.entity.ScrmCampaignEntity;

/**
 * 营销任务执行引擎扩展点。
 * <p>
 * 项目已剥离对外部自动化执行引擎的 Feign 客户端, 保留该接口作为扩展点:
 * 未接入真实执行引擎时使用 {@link LocalTaskExecutionService} 默认实现 (仅登记不派发),
 * 接入真实执行引擎时通过提供同类型 {@code @Service} Bean 覆盖默认实现即可,
 * 无需再改动业务层 {@code ScrmCampaignService} / {@code ScrmPersonaService} 等调用方。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface TaskExecutionService {

    /**
     * 提交营销任务执行, 返回执行任务 ID。
     * <p>
     * 无外部执行引擎时返回 {@code null} (仅登记不派发), 调用方应记录告警日志并继续将
     * 任务状态置为运行中, 后续由调度或人工手动触发。
     * </p>
     *
     * @param campaign 营销任务实体
     * @return 执行任务 ID, 无外部执行引擎时返回 null
     */
    Long submitCampaign(ScrmCampaignEntity campaign);

    /**
     * 触发已登记任务的执行。
     * <p>
     * fleetId 为 {@code null} 表示无设备编排, 直接返回 {@code false};
     * 返回 {@code true} 表示已成功派发执行, {@code false} 表示未派发或不支持。
     * 调用方应保证「失败不阻断启动」, 将结果仅记录日志。
     * </p>
     *
     * @param executionTaskId 执行任务 ID
     * @param fleetId         设备编排 ID (可空)
     * @return 是否成功触发执行
     */
    boolean executeCampaign(Long executionTaskId, Long fleetId);

    /**
     * 创建/确认执行侧人设记录 (幂等)。
     * <p>
     * 失败或无执行引擎时返回 {@code null}, 不得抛异常。调用方应保证「失败仅记日志不阻断」
     * 语义, 业务侧人设仍以本地记录为准。
     * </p>
     *
     * @param personaId    人设 ID (业务侧共享)
     * @param platformType 平台类型 (可空)
     * @param name         人设昵称
     * @param avatarUrl    人设头像 URL (可空)
     * @return 执行侧人设 ID, 失败或无执行引擎时返回 null
     */
    Long ensureExecutionPersona(String personaId, String platformType, String name, String avatarUrl);

    /**
     * 查询执行会话状态。
     * <p>
     * 无执行引擎或会话不存在时返回 {@code null}, 调用方应将「无会话信息」视为非离线状态,
     * 避免误报健康度下降。
     * </p>
     *
     * @param sessionId 会话 ID (当前实现传入设备 ID)
     * @return 会话状态快照, 无执行引擎或会话不存在时返回 null
     */
    ExecutionSessionStatus getSessionStatus(String sessionId);
}
