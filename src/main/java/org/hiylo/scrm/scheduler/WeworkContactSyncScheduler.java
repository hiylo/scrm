/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkContactSyncScheduler.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.PlatformConfigChangedEvent;

import org.hiylo.scrm.entity.ScrmPlatformConfigEntity;
import org.hiylo.scrm.repository.ScrmPlatformConfigRepository;
import org.hiylo.scrm.wework.WeworkContactSyncService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 企业微信外部联系人定时同步调度器
 * <p>
 * 按 {@code scrm.wework.sync-interval-ms} 配置的间隔（默认 30 分钟）自动同步
 * 企微外部联系人和客户群到 SCRM 本地数据表。
 * 仅在企微平台配置存在且 realApiEnabled=true 时才执行同步。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeworkContactSyncScheduler {

    /** 企微平台类型标识 */
    private static final String PLATFORM_TYPE_WEWORK = "wework";

    /** 企业微信联系人同步服务 */
    private final WeworkContactSyncService syncService;
    /** 平台配置数据仓库 */
    private final ScrmPlatformConfigRepository platformConfigRepository;

    /**
     * 定时同步企微外部联系人和客户群
     * <p>
     * 默认每 30 分钟执行一次, 可通过 {@code scrm.wework.sync-interval-ms} 配置调整。
     * 同步前检查企微平台配置是否存在且 realApiEnabled=true, 不满足则跳过。
     * 异常不会导致调度器崩溃, 仅记录错误日志。
     * </p>
     */
    @Scheduled(fixedDelayString = "${scrm.wework.sync-interval-ms:1800000}")
    public void syncContacts() {
        try {
            Optional<ScrmPlatformConfigEntity> config =
                    platformConfigRepository.findByPlatformType(PLATFORM_TYPE_WEWORK);

            if (config.isEmpty()) {
                log.debug("[企微定时同步] 无企微平台配置, 跳过同步");
                return;
            }

            ScrmPlatformConfigEntity platformConfig = config.get();
            if (!platformConfig.isRealApiEnabled()) {
                log.debug("[企微定时同步] 企微 realApiEnabled=false, 跳过同步");
                return;
            }

            log.info("[企微定时同步] 开始同步:");

            WeworkContactSyncService.SyncResult contactResult = syncService.syncExternalContacts();
            log.info("[企微定时同步] 外部联系人同步完成: total={}, new={}, updated={}, failed={}",
                    contactResult.getTotal(), contactResult.getNewCount(), contactResult.getUpdatedCount(),
                    contactResult.getFailedCount());
            if (!contactResult.getErrors().isEmpty()) {
                log.warn("[企微定时同步] 外部联系人同步错误: {}", contactResult.getErrors());
            }

            WeworkContactSyncService.SyncResult groupResult = syncService.syncCustomerGroups();
            log.info("[企微定时同步] 客户群同步完成: total={}, new={}, updated={}, failed={}",
                    groupResult.getTotal(), groupResult.getNewCount(), groupResult.getUpdatedCount(),
                    groupResult.getFailedCount());
            if (!groupResult.getErrors().isEmpty()) {
                log.warn("[企微定时同步] 客户群同步错误: {}", groupResult.getErrors());
            }

        } catch (Exception e) {
            log.error("[企微定时同步] 同步异常:, error={}", e.getMessage(), e);
        }
    }

    /**
     * 监听平台配置变更事件，企微配置变更时立即触发同步
     * <p>
     * 当用户在 Settings 页面保存企微配置时，WeworkConfigProvider 发布
     * PlatformConfigChangedEvent，此方法在独立异步线程中延迟 2 秒后触发同步，
     * 以等待 WeworkConfigProvider 刷新完成。
     * </p>
     *
     * @param event 平台配置变更事件
     */
    @Async
    @EventListener
    public void onPlatformConfigChanged(PlatformConfigChangedEvent event) {
        if (!PLATFORM_TYPE_WEWORK.equalsIgnoreCase(event.getPlatformType())) {
            return;
        }
        log.info("[企微配置变更同步] 收到企微配置变更事件:, source={}",
                event.getConfigSource());
        try {
            // 延迟 2 秒等待 WeworkConfigProvider 刷新完成
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[企微配置变更同步] 延迟等待被中断, 仍将尝试同步");
        }
        syncContacts();
    }
}
