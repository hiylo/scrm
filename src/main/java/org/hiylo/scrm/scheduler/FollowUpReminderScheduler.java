/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : FollowUpReminderScheduler.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.service.ScrmNotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 客户跟进提醒调度器
 * <p>
 * 每 10 分钟扫描一次, 查找 {@code nextFollowUpAt} 在未来 30 分钟内 (含已逾期) 的客户,
 * 通过 {@link ScrmNotificationService#sendFollowupReminder} 推送
 * {@code FOLLOWUP_REMINDER} 通知给前端在线用户。
 * </p>
 * <p>
 * 使用 fixedDelay 天然避免任务重叠, 并辅以 {@link AtomicBoolean} 防重入双重保险;
 * 单个客户推送失败仅记录错误日志, 不中断本轮扫描。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpReminderScheduler {

    /** 提醒提前量 (分钟): 命中未来 30 分钟内到期的跟进任务 */
    private static final int REMINDER_LEAD_MINUTES = 30;

    /** 客户数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** SCRM 通知服务 */
    private final ScrmNotificationService notificationService;

    /** 防重入标记: 上一轮扫描未完成时跳过本轮, 避免任务重叠 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 定时扫描待跟进客户并推送提醒通知
     * <p>
     * 每 10 分钟执行一次 (fixedDelay), 上一轮未完成时跳过本轮, 防止任务重叠。
     * 单个客户推送异常不会中断本轮扫描, 仅记录错误日志。
     * </p>
     */
    @Scheduled(fixedDelay = 600000)
    public void remindFollowUp() {
        if (!running.compareAndSet(false, true)) {
            log.debug("[跟进提醒] 上一轮执行未完成, 跳过本轮");
            return;
        }
        try {
            // 阈值时间: 当前时间 + 30 分钟, 命中即将到期与已逾期的跟进
            LocalDateTime threshold = LocalDateTime.now().plusMinutes(REMINDER_LEAD_MINUTES);
            List<ScrmCustomerEntity> customers = customerRepository.findCustomersNeedingFollowUp(threshold);
            if (customers == null || customers.isEmpty()) {
                log.debug("[跟进提醒] 无待跟进客户, 跳过");
                return;
            }
            log.info("[跟进提醒] 扫描命中: count={}, threshold={}", customers.size(), threshold);
            int totalReminded = 0;
            for (ScrmCustomerEntity customer : customers) {
                try {
                    String customerName = customer.getNickname() != null
                            ? customer.getNickname() : String.valueOf(customer.getId());
                    notificationService.sendFollowupReminder(
                            customer.getId(), customerName, customer.getNextFollowUpAt(), null);
                    log.info("[跟进提醒] 推送提醒: customerId={}, customerName={}, nextFollowUpAt={}",
                            customer.getId(), customerName, customer.getNextFollowUpAt());
                    totalReminded++;
                } catch (Exception e) {
                    log.error("[跟进提醒] 客户 {} 推送提醒异常: {}", customer.getId(), e.getMessage(), e);
                }
            }
            log.info("[跟进提醒] 本轮扫描完成: totalReminded={}", totalReminded);
        } finally {
            running.set(false);
        }
    }
}
