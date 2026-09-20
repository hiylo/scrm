/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : LocalTaskExecutionService.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.execution;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.SequenceGeneratorHolder;
import org.hiylo.scrm.entity.ScrmCampaignEntity;

/**
 * 营销任务执行引擎的默认实现 (仅登记不派发)。
 * <p>
 * 这是 {@link TaskExecutionService} 扩展点的兜底实现, 当容器内未提供真实执行引擎的
 * {@code TaskExecutionService} Bean 时自动生效:
 * <ul>
 *   <li>{@link #submitCampaign} 使用雪花序列生成一个登记 ID, 便于后续对账与补偿,
 *       但不会向任何外部服务派发; 若雪花序列不可用则回退到 {@link System#nanoTime()}。</li>
 *   <li>{@link #executeCampaign} 直接返回 {@code false}, 表示无外部执行引擎, 无需触发。</li>
 *   <li>{@link #ensureExecutionPersona} 直接返回 {@code null}, 表示无执行侧人设。</li>
 *   <li>{@link #getSessionStatus} 直接返回 {@code null}, 表示无会话信息。</li>
 * </ul>
 * 接入真实执行引擎时, 在容器中声明自己的 {@code TaskExecutionService} Bean
 * 即可覆盖本实现, 见 {@link TaskExecutionConfig}。
 * 本类不标注 {@code @Service}, 由 {@link TaskExecutionConfig} 通过 {@code @Bean} 方式
 * 装配, 以保证 {@code @ConditionalOnMissingBean} 条件能被正确求值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
public class LocalTaskExecutionService implements TaskExecutionService {

    /**
     * 提交营销任务执行, 生成并返回本地登记 ID。
     * <p>
     * 使用项目统一的雪花序列生成器生成 ID; 若序列生成器未初始化 (如测试环境) 则
     * 回退到 {@link System#nanoTime()}, 仅作占位, 保证调用方不抛异常。
     * </p>
     *
     * @param campaign 营销任务实体
     * @return 本地登记 ID, 不为 null
     */
    @Override
    public Long submitCampaign(ScrmCampaignEntity campaign) {
        Long registrationId = generateRegistrationId();
        log.info("已登记营销任务执行, 无外部执行引擎将不做实际派发: campaignId={}, campaignType={}, registrationId={}",
                campaign.getId(), campaign.getCampaignType(), registrationId);
        return registrationId;
    }

    /**
     * 触发已登记任务的执行, 无外部执行引擎直接返回 false。
     *
     * @param executionTaskId 执行任务 ID
     * @param fleetId         设备编排 ID (可空)
     * @return false, 表示未派发
     */
    @Override
    public boolean executeCampaign(Long executionTaskId, Long fleetId) {
        log.info("无外部执行引擎, 跳过执行派发: executionTaskId={}, fleetId={}", executionTaskId, fleetId);
        return false;
    }

    /**
     * 创建/确认执行侧人设记录, 无外部执行引擎直接返回 null。
     *
     * @param personaId    人设 ID
     * @param platformType 平台类型
     * @param name         人设昵称
     * @param avatarUrl    人设头像 URL
     * @return null
     */
    @Override
    public Long ensureExecutionPersona(String personaId, String platformType, String name, String avatarUrl) {
        log.debug("无外部执行引擎, 跳过执行侧人设创建/确认: personaId={}, platformType={}",
                personaId, platformType);
        return null;
    }

    /**
     * 查询执行会话状态, 无外部执行引擎直接返回 null。
     *
     * @param sessionId 会话 ID
     * @return null
     */
    @Override
    public ExecutionSessionStatus getSessionStatus(String sessionId) {
        return null;
    }

    /**
     * 生成本地登记 ID, 优先使用雪花序列, 失败时回退到 {@link System#nanoTime()}。
     *
     * @return 登记 ID
     */
    private Long generateRegistrationId() {
        try {
            return SequenceGeneratorHolder.getSequenceGenerator().nextId();
        } catch (Exception e) {
            // 序列生成器尚未初始化时 (如脱离 Spring 容器的测试场景), 回退到 nanoTime 保证不抛异常
            log.warn("雪花序列生成器不可用, 回退到 System.nanoTime() 生成登记 ID: {}", e.getMessage());
            return System.nanoTime();
        }
    }
}
