/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInspectionRunServiceTest.java
 * Date : 2026/10/24 17:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmInspectionRunEntity;
import org.hiylo.scrm.repository.ScrmInspectionRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmInspectionRunService} 单元测试。
 * <p>
 * 锁定执行历史的状态判定口径（最容易各处理解不一致的地方）：
 * <ul>
 *   <li>按开关整体跳过 → {@code SKIPPED}，<b>不是失败</b></li>
 *   <li>有失败但无成功 → {@code FAILED}</li>
 *   <li>有失败也有成功 → {@code PARTIAL}</li>
 *   <li>无失败 → {@code SUCCESS}</li>
 * </ul>
 * 并验证「记录历史失败不影响巡检结果」（写库抛异常时返回 null 而不外抛）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("巡检执行历史")
class ScrmInspectionRunServiceTest {

    @Mock
    private ScrmInspectionRunRepository runRepository;

    private ScrmInspectionRunService service;

    @BeforeEach
    void setUp() {
        service = new ScrmInspectionRunService(runRepository);
        when(runRepository.save(any(ScrmInspectionRunEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        UserContext.setUsername("tester");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("整体跳过记为 SKIPPED，不是 FAILED")
    void businessSkipped_recordedAsSkipped() {
        ScrmInspectionQueryService.BusinessSummary summary =
                new ScrmInspectionQueryService.BusinessSummary(0, 0, 0, 0, "启信宝未启用");

        ScrmInspectionRunEntity run = service.recordBusiness(summary,
                ScrmInspectionRunEntity.TRIGGER_SCHEDULED, LocalDateTime.now(), 12L);

        assertThat(run.getStatus()).isEqualTo(ScrmInspectionRunEntity.STATUS_SKIPPED);
        assertThat(run.getSkipReason()).isEqualTo("启信宝未启用");
        assertThat(run.getRunType()).isEqualTo(ScrmInspectionRunEntity.TYPE_BUSINESS);
        assertThat(run.getTriggerType()).isEqualTo(ScrmInspectionRunEntity.TRIGGER_SCHEDULED);
        assertThat(run.getOperator()).isEqualTo("tester");
    }

    @Test
    @DisplayName("全部成功记为 SUCCESS")
    void businessAllSuccess() {
        ScrmInspectionQueryService.BusinessSummary summary =
                new ScrmInspectionQueryService.BusinessSummary(3, 3, 0, 1);

        ScrmInspectionRunEntity run = service.recordBusiness(summary,
                ScrmInspectionRunEntity.TRIGGER_MANUAL, LocalDateTime.now(), 100L);

        assertThat(run.getStatus()).isEqualTo(ScrmInspectionRunEntity.STATUS_SUCCESS);
        assertThat(run.getTaskScanned()).isEqualTo(3);
        assertThat(run.getSuccessCount()).isEqualTo(3);
        assertThat(run.getChangedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("有失败也有成功记为 PARTIAL")
    void businessPartial() {
        ScrmInspectionQueryService.BusinessSummary summary =
                new ScrmInspectionQueryService.BusinessSummary(3, 2, 1, 0);

        assertThat(service.recordBusiness(summary, ScrmInspectionRunEntity.TRIGGER_SCHEDULED,
                LocalDateTime.now(), 1L).getStatus())
                .isEqualTo(ScrmInspectionRunEntity.STATUS_PARTIAL);
    }

    @Test
    @DisplayName("全部失败记为 FAILED")
    void businessAllFailed() {
        ScrmInspectionQueryService.BusinessSummary summary =
                new ScrmInspectionQueryService.BusinessSummary(2, 0, 2, 0);

        assertThat(service.recordBusiness(summary, ScrmInspectionRunEntity.TRIGGER_SCHEDULED,
                LocalDateTime.now(), 1L).getStatus())
                .isEqualTo(ScrmInspectionRunEntity.STATUS_FAILED);
    }

    @Test
    @DisplayName("税务：命中数记 successCount，验证码阻断记 changedCount")
    void taxSummary_mapping() {
        TaxNoticeScrapeService.ScrapeSummary summary =
                new TaxNoticeScrapeService.ScrapeSummary(5, 60, 2, 1);

        ScrmInspectionRunEntity run = service.recordTax(summary,
                ScrmInspectionRunEntity.TRIGGER_SCHEDULED, LocalDateTime.now(), 500L);

        assertThat(run.getRunType()).isEqualTo(ScrmInspectionRunEntity.TYPE_TAX);
        assertThat(run.getTaskScanned()).isEqualTo(5);
        assertThat(run.getSuccessCount()).isEqualTo(60);
        assertThat(run.getChangedCount()).isEqualTo(2);
        assertThat(run.getFailedCount()).isEqualTo(1);
        assertThat(run.getStatus()).isEqualTo(ScrmInspectionRunEntity.STATUS_PARTIAL);
        assertThat(run.getDetail()).contains("recordsMatched").contains("captchaBlocked");
    }

    @Test
    @DisplayName("写入历史失败不外抛（不影响已完成的巡检）")
    void persistFailure_doesNotThrow() {
        when(runRepository.save(any(ScrmInspectionRunEntity.class)))
                .thenThrow(new RuntimeException("db down"));
        ScrmInspectionQueryService.BusinessSummary summary =
                new ScrmInspectionQueryService.BusinessSummary(1, 1, 0, 0);

        ScrmInspectionRunEntity run = service.recordBusiness(summary,
                ScrmInspectionRunEntity.TRIGGER_SCHEDULED, LocalDateTime.now(), 1L);

        assertThat(run).isNull();
    }

    @Test
    @DisplayName("入参为空：返回 null 不抛")
    void nullSummary_returnsNull() {
        assertThat(service.recordBusiness(null, ScrmInspectionRunEntity.TRIGGER_SCHEDULED,
                LocalDateTime.now(), 1L)).isNull();
        assertThat(service.recordTax(null, ScrmInspectionRunEntity.TRIGGER_SCHEDULED,
                LocalDateTime.now(), 1L)).isNull();
    }

    @Test
    @DisplayName("统计：按类型汇总次数与各项计数")
    void recentStats_aggregates() {
        ScrmInspectionRunEntity a = run(ScrmInspectionRunEntity.TYPE_BUSINESS,
                ScrmInspectionRunEntity.STATUS_SUCCESS, 2, 2, 0, 0);
        ScrmInspectionRunEntity b = run(ScrmInspectionRunEntity.TYPE_BUSINESS,
                ScrmInspectionRunEntity.STATUS_SKIPPED, 0, 0, 0, 0);
        ScrmInspectionRunEntity c = run(ScrmInspectionRunEntity.TYPE_TAX,
                ScrmInspectionRunEntity.STATUS_PARTIAL, 3, 10, 1, 2);
        when(runRepository.findByStartedAtAfter(any())).thenReturn(List.of(a, b, c));

        Map<String, Object> stats = service.recentStats(7);

        assertThat(stats.get("days")).isEqualTo(7);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) stats.get("byType");
        Map<String, Object> business = byType.get(0);
        assertThat(business.get("totalRuns")).isEqualTo(2);
        assertThat(business.get("success")).isEqualTo(1L);
        assertThat(business.get("skipped")).isEqualTo(1L);
        Map<String, Object> tax = byType.get(1);
        assertThat(tax.get("totalRuns")).isEqualTo(1);
        assertThat(tax.get("partial")).isEqualTo(1L);
        assertThat(tax.get("successCount")).isEqualTo(10);
    }

    @Test
    @DisplayName("统计天数：空则 7，越界收敛到 1~90")
    void recentStats_clampsWindow() {
        when(runRepository.findByStartedAtAfter(any())).thenReturn(List.of());

        assertThat(service.recentStats(null).get("days")).isEqualTo(7);
        assertThat(service.recentStats(0).get("days")).isEqualTo(1);
        assertThat(service.recentStats(999).get("days")).isEqualTo(90);
    }

    @Test
    @DisplayName("分页查询：无类型查全部，有类型转大写过滤")
    void list_filtersByType() {
        Page<ScrmInspectionRunEntity> page = new PageImpl<>(List.of());
        when(runRepository.findAllByOrderByStartedAtDesc(any(Pageable.class))).thenReturn(page);
        when(runRepository.findByRunTypeOrderByStartedAtDesc(anyString(), any(Pageable.class)))
                .thenReturn(page);

        assertThat(service.list(null, 0, 20)).isNotNull();
        assertThat(service.list(" business ", 0, 20)).isNotNull();
        assertThat(service.list("tax", 0, 999)).isNotNull();
    }

    // ==================== 测试夹具 ====================

    /**
     * 构造历史记录。
     *
     * @param type    类型
     * @param status  状态
     * @param scanned 扫描数
     * @param success 成功数
     * @param failed  失败数
     * @param changed 变化数
     * @return 实体
     */
    private ScrmInspectionRunEntity run(String type, String status, int scanned,
                                        int success, int failed, int changed) {
        ScrmInspectionRunEntity entity = new ScrmInspectionRunEntity();
        entity.setRunType(type);
        entity.setStatus(status);
        entity.setTaskScanned(scanned);
        entity.setSuccessCount(success);
        entity.setFailedCount(failed);
        entity.setChangedCount(changed);
        entity.setStartedAt(LocalDateTime.now());
        return entity;
    }
}
