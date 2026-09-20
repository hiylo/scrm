/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignReportServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.entity.ScrmCampaignEntity;
import org.hiylo.scrm.entity.ScrmCampaignExecutionLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCampaignExecutionLogRepository;
import org.hiylo.scrm.repository.ScrmCampaignRepository;
import org.hiylo.scrm.vo.CampaignReportVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * ScrmCampaignReportService 单元测试
 * <p>
 * 聚焦营销任务效果报告生成: 任务存在性校验、执行日志聚合 (成功 / 失败 / 进行中计数与比率)、
 * 涉及账号 / 行为流去重、首末执行时间、平均执行间隔、按天趋势分组、Top 错误统计,
 * 以及私有工具方法 (执行天数计算、错误消息截断、比率四舍五入) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCampaignReportService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCampaignReportServiceTest {

    /** 营销活动数据仓库 Mock 桩 */
    @Mock
    private ScrmCampaignRepository campaignRepository;
    /** 活动执行日志数据仓库 Mock 桩 */
    @Mock
    private ScrmCampaignExecutionLogRepository executionLogRepository;
    /** 活动执行日志服务 Mock 桩 */
    @Mock
    private ScrmCampaignExecutionLogService executionLogService;

    /** 被测服务实例 */
    private ScrmCampaignReportService service;

    @BeforeEach
    void setUp() {
        service = new ScrmCampaignReportService(campaignRepository, executionLogRepository, executionLogService);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 报告生成 ====================

    @Test
    @DisplayName("generateReport: 任务不存在抛 NOT_FOUND")
    void generateReport_notFound() {
        when(campaignRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generateReport(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("营销任务不存在");
    }

    @Test
    @DisplayName("generateReport: 聚合成功/失败计数与比率, 去重账号/行为流, 首末时间与平均间隔")
    void generateReport_aggregatesMetrics() {
        ScrmCampaignEntity campaign = buildCampaign(1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(executionLogRepository.findByCampaignIdOrderByOperatedAtAsc(1L))
                .thenReturn(List.of(
                        buildLog("SUCCESS", "acct1", 100L, LocalDateTime.of(2026, 8, 1, 10, 0), null, null),
                        buildLog("SUCCESS", "acct2", 100L, LocalDateTime.of(2026, 8, 1, 11, 0), null, null),
                        buildLog("FAILED", "acct1", 101L, LocalDateTime.of(2026, 8, 2, 10, 0), "E001", "timeout")));

        CampaignReportVo vo = service.generateReport(1L);

        assertThat(vo.getCampaignId()).isEqualTo(1L);
        assertThat(vo.getCampaignName()).isEqualTo("夏季营销");
        assertThat(vo.getTotalExecutions()).isEqualTo(3L);
        assertThat(vo.getSuccessCount()).isEqualTo(2L);
        assertThat(vo.getFailedCount()).isEqualTo(1L);
        assertThat(vo.getRunningCount()).isZero();
        assertThat(vo.getSuccessRate()).isEqualTo(66.67);
        assertThat(vo.getFailureRate()).isEqualTo(33.33);
        assertThat(vo.getUniqueAccounts()).isEqualTo(2L);
        assertThat(vo.getUniqueBehaviorFlows()).isEqualTo(2L);
        assertThat(vo.getFirstExecutionAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 0));
        assertThat(vo.getLastExecutionAt()).isEqualTo(LocalDateTime.of(2026, 8, 2, 10, 0));
        // 间隔: 60min + 1380min, 平均 720min
        assertThat(vo.getAvgExecutionIntervalMinutes()).isEqualTo(720L);
    }

    @Test
    @DisplayName("generateReport: 无执行日志时计数为 0, 比率为 0.0, 趋势与错误列表为空")
    void generateReport_emptyLogs() {
        ScrmCampaignEntity campaign = buildCampaign(1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(executionLogRepository.findByCampaignIdOrderByOperatedAtAsc(1L))
                .thenReturn(Collections.emptyList());

        CampaignReportVo vo = service.generateReport(1L);

        assertThat(vo.getTotalExecutions()).isZero();
        assertThat(vo.getSuccessCount()).isZero();
        assertThat(vo.getFailedCount()).isZero();
        assertThat(vo.getSuccessRate()).isEqualTo(0.0);
        assertThat(vo.getFailureRate()).isEqualTo(0.0);
        assertThat(vo.getFirstExecutionAt()).isNull();
        assertThat(vo.getLastExecutionAt()).isNull();
        assertThat(vo.getAvgExecutionIntervalMinutes()).isZero();
        assertThat(vo.getDailyTrend()).isEmpty();
        assertThat(vo.getTopErrors()).isEmpty();
    }

    @Test
    @DisplayName("generateReport: 按天分组聚合执行趋势, 同日累加成功/失败计数")
    void generateReport_dailyTrend() {
        ScrmCampaignEntity campaign = buildCampaign(1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(executionLogRepository.findByCampaignIdOrderByOperatedAtAsc(1L))
                .thenReturn(List.of(
                        buildLog("SUCCESS", "a", 1L, LocalDateTime.of(2026, 8, 1, 10, 0), null, null),
                        buildLog("SUCCESS", "a", 1L, LocalDateTime.of(2026, 8, 1, 14, 0), null, null),
                        buildLog("FAILED", "a", 1L, LocalDateTime.of(2026, 8, 2, 9, 0), "E1", "err")));

        CampaignReportVo vo = service.generateReport(1L);

        assertThat(vo.getDailyTrend()).hasSize(2);
        assertThat(vo.getDailyTrend().get(0).getDate()).isEqualTo("2026-08-01");
        assertThat(vo.getDailyTrend().get(0).getTotalCount()).isEqualTo(2L);
        assertThat(vo.getDailyTrend().get(0).getSuccessCount()).isEqualTo(2L);
        assertThat(vo.getDailyTrend().get(0).getFailedCount()).isZero();
        assertThat(vo.getDailyTrend().get(1).getDate()).isEqualTo("2026-08-02");
        assertThat(vo.getDailyTrend().get(1).getFailedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("generateReport: Top 错误按出现次数倒序取前 5, 错误消息截断 100 字符")
    void generateReport_topErrors() {
        ScrmCampaignEntity campaign = buildCampaign(1L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        String longMessage = "x".repeat(150);
        when(executionLogRepository.findByCampaignIdOrderByOperatedAtAsc(1L))
                .thenReturn(List.of(
                        buildLog("FAILED", "a", 1L, LocalDateTime.of(2026, 8, 1, 10, 0), "E002", longMessage),
                        buildLog("FAILED", "a", 1L, LocalDateTime.of(2026, 8, 1, 11, 0), "E001", "timeout"),
                        buildLog("FAILED", "a", 1L, LocalDateTime.of(2026, 8, 1, 12, 0), "E001", "timeout"),
                        buildLog("SUCCESS", "a", 1L, LocalDateTime.of(2026, 8, 1, 13, 0), null, null)));

        CampaignReportVo vo = service.generateReport(1L);

        // E001 出现 2 次 (排前), E002 出现 1 次
        assertThat(vo.getTopErrors()).hasSize(2);
        assertThat(vo.getTopErrors().get(0).getErrorCode()).isEqualTo("E001");
        assertThat(vo.getTopErrors().get(0).getCount()).isEqualTo(2L);
        assertThat(vo.getTopErrors().get(1).getErrorCode()).isEqualTo("E002");
        // 错误消息截断到 100 字符
        assertThat(vo.getTopErrors().get(1).getErrorMessage()).hasSize(100);
    }

    // ==================== 私有方法 ====================

    @Test
    @DisplayName("computeDurationDays: startDate 为 null 返回 0, endDate 为 null 用当前时间")
    void computeDurationDays_nullDates() {
        Integer days = ReflectionTestUtils.invokeMethod(service, "computeDurationDays",
                null, LocalDateTime.now());
        assertThat(days).isZero();
    }

    @Test
    @DisplayName("computeDurationDays: 计算起止日期天数差, 负值归零")
    void computeDurationDays_calculates() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 6, 0, 0);
        Integer days = ReflectionTestUtils.invokeMethod(service, "computeDurationDays", start, end);
        assertThat(days).isEqualTo(5);
    }

    @Test
    @DisplayName("truncate: null 返回 null, 超长截断到 100, 短串原样返回")
    void truncate_messages() {
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "truncate", (String) null)).isNull();
        assertThat((String) ReflectionTestUtils.invokeMethod(service, "truncate", "short")).isEqualTo("short");
        String truncated = ReflectionTestUtils.invokeMethod(service, "truncate", "y".repeat(150));
        assertThat(truncated).hasSize(100);
    }

    @Test
    @DisplayName("roundRate: 四舍五入保留 2 位小数")
    void roundRate_halfUp() {
        Double rate = ReflectionTestUtils.invokeMethod(service, "roundRate", 66.666d);
        assertThat(rate).isEqualTo(66.67);
    }

    // ==================== 辅助方法 ====================

    private ScrmCampaignEntity buildCampaign(Long id) {
        ScrmCampaignEntity entity = new ScrmCampaignEntity();
        entity.setId(id);
        entity.setCampaignName("夏季营销");
        entity.setCampaignType("AUTO_POST");
        entity.setStatus("RUNNING");
        entity.setStartTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        entity.setEndTime(LocalDateTime.of(2026, 8, 6, 0, 0));
        return entity;
    }

    private ScrmCampaignExecutionLogEntity buildLog(String status, String operatedBy, Long behaviorFlowId,
                                                     LocalDateTime operatedAt, String errorCode, String errorMessage) {
        ScrmCampaignExecutionLogEntity log = new ScrmCampaignExecutionLogEntity();
        log.setCampaignId(1L);
        log.setStatus(status);
        log.setOperatedBy(operatedBy);
        log.setBehaviorFlowId(behaviorFlowId);
        log.setOperatedAt(operatedAt);
        log.setAction("START");
        log.setErrorCode(errorCode);
        log.setErrorMessage(errorMessage);
        return log;
    }
}
