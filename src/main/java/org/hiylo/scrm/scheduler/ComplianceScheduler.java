/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ComplianceScheduler.java
 * Date : 2026/10/25 10:50:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.service.finance.compliance.ScrmComplianceService;
import org.hiylo.scrm.service.finance.document.ScrmCustomerDocumentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 合规域调度器（工商年报 + 资质许可）。
 * <p>
 * 与 {@link FinanceTaxScheduler} 分开的原因：合规事项是<b>年度/半年度节奏</b>
 * （年报每年一次、证照到期按证计算），和报税的月度节奏不同，
 * 混在一个类里会让「哪个 job 什么时候跑」难以一眼看清。
 * </p>
 * <p>
 * 每个 job 用 {@link AtomicBoolean} 做单机互斥：慢任务（如首次批量生成年报）
 * 还没跑完时，下一轮直接跳过，避免同一批数据被并发处理。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceScheduler {

    /** 合规域服务 */
    private final ScrmComplianceService complianceService;

    /** 资料归档服务（到期推进） */
    private final ScrmCustomerDocumentService documentService;

    /** 年报生成任务互斥标记 */
    private final AtomicBoolean generateRunning = new AtomicBoolean(false);

    /** 状态推进任务互斥标记 */
    private final AtomicBoolean advanceRunning = new AtomicBoolean(false);

    /** 提醒推送任务互斥标记 */
    private final AtomicBoolean remindRunning = new AtomicBoolean(false);

    /**
     * 生成工商年报任务（默认每年 1 月 1 日 01:30）。
     * <p>
     * 为在服企业生成「上一年度」的年报记录，截止日固定为当年 6 月 30 日。
     * 幂等：已存在的（公司 + 年度）不会重复生成。
     * </p>
     */
    @Scheduled(cron = "${scrm.compliance.generate-cron:0 30 1 1 1 ?}")
    public void generateAnnualReports() {
        if (!generateRunning.compareAndSet(false, true)) {
            log.debug("[调度-年报生成] 上一轮仍在执行, 跳过");
            return;
        }
        try {
            int year = LocalDate.now().getYear() - 1;
            int created = complianceService.generateAnnualReports(year);
            log.info("[调度-年报生成] 完成: 年度={}, created={}", year, created);
        } catch (Exception e) {
            log.error("[调度-年报生成] 异常: {}", e.getMessage(), e);
        } finally {
            generateRunning.set(false);
        }
    }

    /**
     * 状态推进（默认每日 01:00）：年报逾期判定 + 证照到期状态推进。
     */
    @Scheduled(cron = "${scrm.compliance.advance-cron:0 0 1 * * ?}")
    public void advanceStates() {
        if (!advanceRunning.compareAndSet(false, true)) {
            log.debug("[调度-合规推进] 上一轮仍在执行, 跳过");
            return;
        }
        try {
            int overdue = complianceService.advanceAnnualReports();
            int qualChanged = complianceService.advanceQualifications();
            // 资料自身的到期（身份证/合同过期）与合规事项同属「到期」语义，一并推进
            int docExpired = documentService.advanceExpired();
            log.info("[调度-合规推进] 完成: 年报逾期={}, 证照状态变更={}, 资料过期={}",
                    overdue, qualChanged, docExpired);
        } catch (Exception e) {
            log.error("[调度-合规推进] 异常: {}", e.getMessage(), e);
        } finally {
            advanceRunning.set(false);
        }
    }

    /**
     * 到期提醒（默认每日 09:30）：年报临近截止 + 证照临近到期。
     */
    @Scheduled(cron = "${scrm.compliance.remind-cron:0 30 9 * * ?}")
    public void pushReminders() {
        if (!remindRunning.compareAndSet(false, true)) {
            log.debug("[调度-合规提醒] 上一轮仍在执行, 跳过");
            return;
        }
        try {
            int annual = complianceService.pushAnnualReminders();
            int qual = complianceService.pushQualificationReminders();
            log.info("[调度-合规提醒] 完成: 年报={}, 资质={}", annual, qual);
        } catch (Exception e) {
            log.error("[调度-合规提醒] 异常: {}", e.getMessage(), e);
        } finally {
            remindRunning.set(false);
        }
    }
}
