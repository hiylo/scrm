/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxHolidayAiTaskHandler.java
 * Date : 2026/10/27 15:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.tax;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.service.ScheduledTaskHandler;
import org.springframework.stereotype.Component;

/**
 * 假期 AI 抽取调度处理器。
 * <p>
 * 建议每年 11 月执行：国务院办公厅通常在前一年 11 月发布次年的节假日安排，
 * 此时抓取并抽取次年数据，运维在年底前确认即可。
 * </p>
 * <p>
 * <b>只生成草稿，不写生效表</b>：抽取结果落 {@code scrm_tax_holiday_draft}，
 * 必须人工在「假期维护」页核对后才计入申报顺延。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScrmTaxHolidayAiTaskHandler implements ScheduledTaskHandler {

    /** 假期 AI 抽取服务 */
    private final ScrmTaxHolidayAiService aiService;

    /**
     * 执行一轮次年假期抽取。
     * <p>
     * 返回执行摘要供调度中心留痕；任何失败都已在 service 层降级为「不生成草稿」，
     * 这里不抛异常，避免调度框架按重试策略反复打外部站点。
     * </p>
     *
     * @param task 调度任务实体
     * @return 执行摘要
     */
    @Override
    public String handle(ScrmScheduledTaskEntity task) {
        int targetYear = java.time.LocalDate.now().getYear() + 1;
        log.info("[假期-AI调度] 开始抽取次年假期: year={}, taskCode={}",
                targetYear, task == null ? null : task.getTaskCode());
        // 先做数据齐备性提醒：即便抽取失败，也必须让运维知道该补数据了
        boolean reminded = aiService.remindIfIncomplete(targetYear);
        var draft = aiService.autoExtract(targetYear);
        if (draft == null) {
            log.warn("[假期-AI调度] 未生成草稿（可能抓取失败或 AI 未启用）: year={}", targetYear);
            return "未生成草稿: year=" + targetYear + "（抓取失败或 AI 未启用）"
                    + (reminded ? "；已发送补录提醒" : "");
        }
        log.info("[假期-AI调度] 草稿已生成, 等待人工确认: draftId={}, year={}",
                draft.getId(), targetYear);
        return "草稿已生成: draftId=" + draft.getId() + ", year=" + targetYear + "，待人工确认";
    }
}
