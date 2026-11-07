/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TaxNoticeScrapeServiceTest.java
 * Date : 2026/10/24 11:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.config.ScrmInspectionProperties;
import org.hiylo.scrm.component.InspectionRateLimiter;
import org.hiylo.scrm.dto.TaxNoticeFetcher;
import org.hiylo.scrm.dto.TaxNoticeRecord;
import org.hiylo.scrm.entity.ScrmInspectionSnapshotEntity;
import org.hiylo.scrm.entity.ScrmInspectionTaskEntity;
import org.hiylo.scrm.repository.ScrmInspectionTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TaxNoticeScrapeService} 抓取编排单元测试（零网络依赖）。
 * <p>
 * <b>为什么需要这层测试</b>：解析层（{@code TaxNoticeParser}）已有覆盖，但抓取的业务语义
 * ——「按授权企业过滤」「明细唯一键去重」「验证码阻断计数」「单企业失败不中断」「游标更新」
 * ——才是 T03 的合规红线与变化告警基础，且完全可离线验证。
 * </p>
 * <p>
 * 政务站点在本环境不可达（真实可抓性另行验证），但编排逻辑不依赖网络：fetcher 被 mock。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("税务公告抓取编排")
class TaxNoticeScrapeServiceTest {

    /** 任务 ID */
    private static final long TASK_ID = 8001L;

    /** 企业名称 */
    private static final String ENTERPRISE = "示例企业有限公司";

    /** 统一社会信用代码 */
    private static final String CREDIT_CODE = "91330100MA27X00001";

    @Mock
    private ScrmInspectionTaskRepository taskRepository;

    @Mock
    private GuangDongTaxFetcher guangDongFetcher;

    @Mock
    private ZheJiangTaxFetcher zheJiangFetcher;

    @Mock
    private ShaanxiTaxFetcher shaanxiFetcher;

    @Mock
    private ScrmInspectionSnapshotService snapshotService;

    @Mock
    private ScrmInspectionAlertService alertService;

    @Mock
    private InspectionRateLimiter rateLimiter;

    private ScrmInspectionProperties inspectionProperties;

    private TaxNoticeScrapeService service;

    @BeforeEach
    void setUp() {
        inspectionProperties = new ScrmInspectionProperties();
        service = new TaxNoticeScrapeService(taskRepository, guangDongFetcher, zheJiangFetcher,
                shaanxiFetcher, snapshotService, alertService, rateLimiter, inspectionProperties);
        // 默认：限额充足、无快照变化
        when(rateLimiter.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(shaanxiFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(snapshotService.saveSnapshot(anyLong(), anyString(), any(), any(), anyString(),
                any(), anyString(), any())).thenAnswer(inv -> {
                    ScrmInspectionSnapshotEntity snapshot = new ScrmInspectionSnapshotEntity();
                    snapshot.setChanged(false);
                    return snapshot;
                });
        when(taskRepository.save(any(ScrmInspectionTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== 无任务 ====================

    @Test
    @DisplayName("无启用税务任务：直接返回全 0，不抓取")
    void noTasks_returnsEmptySummary() {
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of());

        TaxNoticeScrapeService.ScrapeSummary summary = service.scrapeAll();

        assertThat(summary.taskScanned()).isZero();
        assertThat(summary.recordsMatched()).isZero();
        verify(guangDongFetcher, never()).fetchNoticeList();
    }

    // ==================== 企业过滤（合规红线） ====================

    @Test
    @DisplayName("只落库授权企业的记录：他企业明细一律丢弃")
    void filtersByEnterprise_onlyOwnRecordsStored() {
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task()));
        when(guangDongFetcher.fetchNoticeList()).thenReturn(fetcher(
                record(ENTERPRISE + "（本企业）", CREDIT_CODE),
                record("无关企业甲有限公司", "91330100MA27X99999")));
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(guangDongFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());
        when(zheJiangFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());

        TaxNoticeScrapeService.ScrapeSummary summary = service.scrapeAll();

        assertThat(summary.recordsMatched()).isEqualTo(1);
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(snapshotService, times(1)).saveSnapshot(anyLong(), anyString(), anyString(),
                anyString(), anyString(), any(), detailCaptor.capture(), any());
        assertThat(detailCaptor.getValue()).contains(ENTERPRISE);
        assertThat(detailCaptor.getValue()).doesNotContain("无关企业甲");
    }

    @Test
    @DisplayName("按信用代码命中：企业名不同但代码一致也算本企业")
    void filtersByCreditCode() {
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task()));
        when(guangDongFetcher.fetchNoticeList()).thenReturn(fetcher(
                record("曾用名有限公司", CREDIT_CODE)));
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(guangDongFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());
        when(zheJiangFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());

        assertThat(service.scrapeAll().recordsMatched()).isEqualTo(1);
    }

    // ==================== 去重 ====================

    @Test
    @DisplayName("同一唯一键重复出现只落库一次（变化才告警）")
    void deduplicatesByUniqueKey() {
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task()));
        TaxNoticeRecord dup1 = record(ENTERPRISE, CREDIT_CODE);
        dup1.setUniqueKey("K1");
        TaxNoticeRecord dup2 = record(ENTERPRISE, CREDIT_CODE);
        dup2.setUniqueKey("K1");
        when(guangDongFetcher.fetchNoticeList()).thenReturn(fetcher(dup1, dup2));
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(guangDongFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());
        when(zheJiangFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());

        service.scrapeAll();

        verify(snapshotService, times(1)).saveSnapshot(anyLong(), anyString(), anyString(),
                anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("唯一键缺失时按「信用代码+类型+所属期起」自动补全")
    void fillsMissingUniqueKey() {
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task()));
        TaxNoticeRecord record = record(ENTERPRISE, CREDIT_CODE);
        record.setUniqueKey(null);
        record.setTaxPeriodStart("2026-07");
        when(guangDongFetcher.fetchNoticeList()).thenReturn(fetcher(record));
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(guangDongFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());
        when(zheJiangFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());

        service.scrapeAll();

        assertThat(record.getUniqueKey()).isEqualTo(CREDIT_CODE + "|通知公告|2026-07");
    }

    @Test
    @DisplayName("快照判定为变化时才触发告警")
    void alertsOnlyWhenChanged() {
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task()));
        when(guangDongFetcher.fetchNoticeList()).thenReturn(fetcher(record(ENTERPRISE, CREDIT_CODE)));
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(guangDongFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());
        when(zheJiangFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());
        when(snapshotService.saveSnapshot(anyLong(), anyString(), any(), any(), anyString(),
                any(), anyString(), any())).thenAnswer(inv -> {
                    ScrmInspectionSnapshotEntity snapshot = new ScrmInspectionSnapshotEntity();
                    snapshot.setChanged(true);
                    return snapshot;
                });

        service.scrapeAll();

        verify(alertService, times(1)).notifyTaxChange(any(), any());
    }

    // ==================== 验证码 / 失败计数 ====================

    @Test
    @DisplayName("明细命中验证码：captchaBlocked=1，不算失败")
    void captchaBlocked_countedSeparately() {
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task()));
        when(guangDongFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(guangDongFetcher.fetchQsggDetail(anyString(), anyString()))
                .thenReturn(new TaxNoticeFetcher(List.of(), true, "需验证码"));
        when(zheJiangFetcher.fetchQsggDetail(anyString(), anyString()))
                .thenReturn(new TaxNoticeFetcher(List.of(), false, null));

        TaxNoticeScrapeService.ScrapeSummary summary = service.scrapeAll();

        assertThat(summary.captchaBlocked()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
    }

    @Test
    @DisplayName("明细抓取报错：failed=1")
    void fetchError_countedAsFailed() {
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task()));
        when(guangDongFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(guangDongFetcher.fetchQsggDetail(anyString(), anyString()))
                .thenReturn(new TaxNoticeFetcher(List.of(), false, "连接超时"));
        when(zheJiangFetcher.fetchQsggDetail(anyString(), anyString()))
                .thenReturn(new TaxNoticeFetcher(List.of(), false, null));

        assertThat(service.scrapeAll().failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("单企业抓取异常不中断整轮：其余企业继续，failed 累加")
    void oneTaskThrows_doesNotAbortRound() {
        ScrmInspectionTaskEntity bad = task();
        bad.setId(8002L);
        bad.setEnterpriseName("异常企业有限公司");
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(bad, task()));
        when(guangDongFetcher.fetchNoticeList())
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(fetcher(record(ENTERPRISE, CREDIT_CODE)));
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(guangDongFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());
        when(zheJiangFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());

        TaxNoticeScrapeService.ScrapeSummary summary = service.scrapeAll();

        assertThat(summary.taskScanned()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.recordsMatched()).isEqualTo(1);
    }

    // ==================== 限额与游标 ====================

    @Test
    @DisplayName("明细日预算用尽：跳过查询但不算失败")
    void detailBudgetExhausted_skipsWithoutFailure() {
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task()));
        when(guangDongFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(rateLimiter.tryAcquire(eq("GD_SCRAPE_DETAIL"), anyInt())).thenReturn(false);
        when(rateLimiter.tryAcquire(eq("ZJ_SCRAPE_DETAIL"), anyInt())).thenReturn(false);

        TaxNoticeScrapeService.ScrapeSummary summary = service.scrapeAll();

        assertThat(summary.failed()).isZero();
        verify(guangDongFetcher, never()).fetchQsggDetail(anyString(), anyString());
        verify(zheJiangFetcher, never()).fetchQsggDetail(anyString(), anyString());
    }

    @Test
    @DisplayName("抓取后更新任务润标：lastTaxRunAt/lastRunAt/lastResult")
    void updatesTaskCursor() {
        ScrmInspectionTaskEntity task = task();
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task));
        when(guangDongFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(guangDongFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());
        when(zheJiangFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());

        service.scrapeAll();

        assertThat(task.getLastTaxRunAt()).isNotNull();
        assertThat(task.getLastRunAt()).isNotNull();
        assertThat(task.getLastResult()).isEqualTo("SUCCESS");
        verify(taskRepository, times(1)).save(task);
    }

    @Test
    @DisplayName("有失败时任务结果标记 PARTIAL")
    void partialResultWhenFailed() {
        ScrmInspectionTaskEntity task = task();
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task));
        when(guangDongFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchNoticeList()).thenReturn(fetcher());
        when(zheJiangFetcher.fetchMajorViolationList()).thenReturn(fetcher());
        when(guangDongFetcher.fetchQsggDetail(anyString(), anyString()))
                .thenReturn(new TaxNoticeFetcher(List.of(), false, "超时"));
        when(zheJiangFetcher.fetchQsggDetail(anyString(), anyString())).thenReturn(fetcher());

        service.scrapeAll();

        assertThat(task.getLastResult()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("关闭税务巡检的任务不抓取公告列表（inspectTax=false）")
    void inspectTaxDisabled_skipsListFetch() {
        ScrmInspectionTaskEntity task = task();
        task.setInspectTax(false);
        when(taskRepository.findEnabledTaxTasks()).thenReturn(List.of(task));

        service.scrapeAll();

        verify(guangDongFetcher, never()).fetchNoticeList();
        verify(zheJiangFetcher, never()).fetchNoticeList();
    }

    // ==================== 测试夹具 ====================

    /**
     * 构造一个启用税务巡检的任务。
     *
     * @return 任务实体
     */
    private ScrmInspectionTaskEntity task() {
        ScrmInspectionTaskEntity task = new ScrmInspectionTaskEntity();
        task.setId(TASK_ID);
        task.setEnterpriseName(ENTERPRISE);
        task.setCreditCode(CREDIT_CODE);
        task.setInspectTax(true);
        task.setInspectBusiness(true);
        return task;
    }

    /**
     * 构造一条公告记录。
     *
     * @param name       企业名称
     * @param creditCode 信用代码
     * @return 公告记录
     */
    private TaxNoticeRecord record(String name, String creditCode) {
        TaxNoticeRecord record = new TaxNoticeRecord();
        record.setEnterpriseName(name);
        record.setCreditCode(creditCode);
        record.setNoticeType("通知公告");
        record.setProvince("GD");
        return record;
    }

    /**
     * 把记录包装成抓取结果。
     *
     * @param records 记录
     * @return 抓取结果
     */
    private TaxNoticeFetcher fetcher(TaxNoticeRecord... records) {
        return new TaxNoticeFetcher(new ArrayList<>(List.of(records)), false, null);
    }
}
