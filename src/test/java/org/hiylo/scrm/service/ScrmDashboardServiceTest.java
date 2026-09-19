/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDashboardServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountHealthRepository;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmCampaignRepository;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmRiskSignalRepository;
import org.hiylo.scrm.vo.AccountOverviewVo;
import org.hiylo.scrm.vo.CampaignOverviewVo;
import org.hiylo.scrm.vo.ConversationOverviewVo;
import org.hiylo.scrm.vo.CustomerOverviewVo;
import org.hiylo.scrm.vo.DailyCountVo;
import org.hiylo.scrm.vo.DashboardOverviewVo;
import org.hiylo.scrm.vo.DashboardTrendVo;
import org.hiylo.scrm.vo.RiskOverviewVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmDashboardService 单元测试
 * <p>
 * 聚焦账号 / 任务 / 客户 / 会话 / 风控五维度概览聚合与多指标趋势统计
 * (含日期补齐 / 天数规范化 / 不支持指标校验) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmDashboardService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmDashboardServiceTest {

    /** 日期格式化器, 统一 yyyy-MM-dd 格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 账号仓库 Mock */
    @Mock
    private ScrmAccountRepository accountRepository;
    /** 账号健康度仓库 Mock */
    @Mock
    private ScrmAccountHealthRepository accountHealthRepository;
    /** 营销活动仓库 Mock */
    @Mock
    private ScrmCampaignRepository campaignRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 会话仓库 Mock */
    @Mock
    private ScrmConversationRepository conversationRepository;
    /** 会话消息仓库 Mock */
    @Mock
    private ScrmConversationMessageRepository messageRepository;
    /** 风险信号仓库 Mock */
    @Mock
    private ScrmRiskSignalRepository riskSignalRepository;

    /** 被测服务实例 */
    private ScrmDashboardService service;

    @BeforeEach
    void setUp() {
        service = new ScrmDashboardService(accountRepository, accountHealthRepository,
                campaignRepository, customerRepository, conversationRepository,
                messageRepository, riskSignalRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造聚合查询结果行 Object[]{key, count}
     */
    private Object[] row(Object key, Object count) {
        return new Object[]{key, count};
    }

    /**
     * 构造按日聚合结果行 Object[]{date(yyyy-MM-dd), count}, 使用偏移天方便构造
     */
    private Object[] dailyRow(int daysAgo, long count) {
        String date = LocalDate.now().minusDays(daysAgo).format(DATE_FORMATTER);
        return new Object[]{date, count};
    }

    @Test
    @DisplayName("getAccountOverview: 聚合账号总数/平台分布/登录态分布/在线率/不健康数/检测记录数")
    void getAccountOverview_success() {
        when(accountRepository.count()).thenReturn(10L);
        when(accountRepository.countByPlatformType())
                .thenReturn(List.of(row("WECHAT", 7L), row("WORKWX", 3L)));
        when(accountRepository.countByLoginState())
                .thenReturn(List.of(row("LOGIN", 6L), row("LOGOUT", 3L), row("FROZEN", 1L)));
        when(accountRepository.countByLoginState("LOGIN")).thenReturn(6L);
        when(accountRepository.countByLoginState("LOGOUT")).thenReturn(3L);
        when(accountRepository.countByLoginState("FROZEN")).thenReturn(1L);
        when(accountHealthRepository.count()).thenReturn(25L);

        AccountOverviewVo result = service.getAccountOverview();

        assertThat(result.getTotalAccounts()).isEqualTo(10L);
        assertThat(result.getPlatformDistribution())
                .containsEntry("WECHAT", 7L)
                .containsEntry("WORKWX", 3L)
                .hasSize(2);
        assertThat(result.getLoginStateDistribution())
                .containsEntry("LOGIN", 6L)
                .containsEntry("LOGOUT", 3L)
                .containsEntry("FROZEN", 1L);
        // onlineRate = 6 / 10 = 0.6
        assertThat(result.getOnlineRate()).isEqualTo(0.6d);
        // unhealthyCount = LOGOUT(3) + FROZEN(1) = 4
        assertThat(result.getUnhealthyCount()).isEqualTo(4L);
        assertThat(result.getTotalHealthChecks()).isEqualTo(25L);
    }

    @Test
    @DisplayName("getAccountOverview: 账号总数为 0 时在线率为 0.0 (避免除零)")
    void getAccountOverview_zeroAccounts() {
        when(accountRepository.count()).thenReturn(0L);
        when(accountRepository.countByPlatformType()).thenReturn(Collections.emptyList());
        when(accountRepository.countByLoginState()).thenReturn(Collections.emptyList());
        when(accountRepository.countByLoginState(any(String.class))).thenReturn(0L);
        when(accountHealthRepository.count()).thenReturn(0L);

        AccountOverviewVo result = service.getAccountOverview();

        assertThat(result.getTotalAccounts()).isZero();
        assertThat(result.getOnlineRate()).isEqualTo(0.0d);
        assertThat(result.getUnhealthyCount()).isZero();
        assertThat(result.getTotalHealthChecks()).isZero();
        assertThat(result.getPlatformDistribution()).isEmpty();
        assertThat(result.getLoginStateDistribution()).isEmpty();
    }

    @Test
    @DisplayName("getCampaignOverview: 聚合任务总数/状态分布/近7天创建趋势 (含缺失日期补零)")
    void getCampaignOverview_success() {
        when(campaignRepository.count()).thenReturn(20L);
        when(campaignRepository.countByStatus())
                .thenReturn(List.of(row("RUNNING", 5L), row("COMPLETED", 15L)));
        // 仅返回今天与 3 天前有数据, 其余 5 天应补零
        when(campaignRepository.dailyCountByCreateTime(any(LocalDateTime.class)))
                .thenReturn(List.of(dailyRow(0, 2L), dailyRow(3, 4L)));

        CampaignOverviewVo result = service.getCampaignOverview();

        assertThat(result.getTotalCampaigns()).isEqualTo(20L);
        assertThat(result.getStatusDistribution())
                .containsEntry("RUNNING", 5L)
                .containsEntry("COMPLETED", 15L)
                .hasSize(2);
        // 近 7 天趋势应包含 7 个日期 (含今天), 按升序
        List<DailyCountVo> trend = result.getRecentTrend();
        assertThat(trend).hasSize(7);
        assertThat(trend.get(0).getDate()).isEqualTo(LocalDate.now().minusDays(6).format(DATE_FORMATTER));
        assertThat(trend.get(6).getDate()).isEqualTo(LocalDate.now().format(DATE_FORMATTER));
        // 3 天前计数 = 4
        String threeDaysAgo = LocalDate.now().minusDays(3).format(DATE_FORMATTER);
        DailyCountVo threeDaysAgoVo = trend.stream()
                .filter(v -> threeDaysAgo.equals(v.getDate()))
                .findFirst().orElseThrow();
        assertThat(threeDaysAgoVo.getCount()).isEqualTo(4L);
        // 今天计数 = 2
        assertThat(trend.get(6).getCount()).isEqualTo(2L);
        // 缺失日期补零 (例如 6 天前)
        assertThat(trend.get(0).getCount()).isZero();
    }

    @Test
    @DisplayName("getCustomerOverview: 聚合客户总数/生命周期分布/近7天新增")
    void getCustomerOverview_success() {
        when(customerRepository.count()).thenReturn(150L);
        when(customerRepository.countByLifecycle())
                .thenReturn(List.of(row("NEW", 30L), row("ACTIVE", 100L), row("DORMANT", 20L)));
        when(customerRepository.dailyCountByCreateTime(any(LocalDateTime.class)))
                .thenReturn(List.of(dailyRow(1, 5L), dailyRow(0, 8L)));

        CustomerOverviewVo result = service.getCustomerOverview();

        assertThat(result.getTotalCustomers()).isEqualTo(150L);
        assertThat(result.getLifecycleDistribution())
                .containsEntry("NEW", 30L)
                .containsEntry("ACTIVE", 100L)
                .containsEntry("DORMANT", 20L)
                .hasSize(3);
        List<DailyCountVo> recent = result.getRecentNewCustomers();
        assertThat(recent).hasSize(7);
        assertThat(recent.get(6).getDate()).isEqualTo(LocalDate.now().format(DATE_FORMATTER));
        assertThat(recent.get(6).getCount()).isEqualTo(8L);
        assertThat(recent.get(5).getCount()).isEqualTo(5L);
        // 缺失日期补零
        assertThat(recent.get(0).getCount()).isZero();
    }

    @Test
    @DisplayName("getConversationOverview: 聚合会话总数/消息总量/近7天消息趋势/活跃会话数")
    void getConversationOverview_success() {
        when(conversationRepository.count()).thenReturn(80L);
        when(messageRepository.count()).thenReturn(1200L);
        when(messageRepository.dailyCountBySentAt(any(LocalDateTime.class)))
                .thenReturn(List.of(dailyRow(0, 100L), dailyRow(2, 50L)));
        when(conversationRepository.countByLastMessageAtAfter(any(LocalDateTime.class)))
                .thenReturn(45L);

        ConversationOverviewVo result = service.getConversationOverview();

        assertThat(result.getTotalConversations()).isEqualTo(80L);
        assertThat(result.getTotalMessages()).isEqualTo(1200L);
        assertThat(result.getActiveConversations()).isEqualTo(45L);
        List<DailyCountVo> recent = result.getRecentMessages();
        assertThat(recent).hasSize(7);
        assertThat(recent.get(6).getCount()).isEqualTo(100L);
        // 2 天前计数 = 50
        String twoDaysAgo = LocalDate.now().minusDays(2).format(DATE_FORMATTER);
        assertThat(recent.stream().filter(v -> twoDaysAgo.equals(v.getDate()))
                .findFirst().orElseThrow().getCount()).isEqualTo(50L);
        // 缺失日期补零
        assertThat(recent.get(0).getCount()).isZero();
    }

    @Test
    @DisplayName("getRiskOverview: 聚合风控信号总数/风险等级分布/信号类型分布/近7天触发趋势")
    void getRiskOverview_success() {
        when(riskSignalRepository.count()).thenReturn(60L);
        when(riskSignalRepository.countGroupByRiskLevel())
                .thenReturn(List.of(row("LOW", 40L), row("MEDIUM", 15L), row("HIGH", 5L)));
        when(riskSignalRepository.countGroupBySignalType())
                .thenReturn(List.of(row("captcha_detected", 20L), row("frequent_operation", 40L)));
        when(riskSignalRepository.dailyCountByTriggeredAt(any(LocalDateTime.class)))
                .thenReturn(List.of(dailyRow(0, 3L), dailyRow(1, 7L)));

        RiskOverviewVo result = service.getRiskOverview();

        assertThat(result.getTotalRiskSignals()).isEqualTo(60L);
        assertThat(result.getRiskLevelDistribution())
                .containsEntry("LOW", 40L)
                .containsEntry("MEDIUM", 15L)
                .containsEntry("HIGH", 5L)
                .hasSize(3);
        assertThat(result.getSignalTypeDistribution())
                .containsEntry("captcha_detected", 20L)
                .containsEntry("frequent_operation", 40L)
                .hasSize(2);
        List<DailyCountVo> trend = result.getRecentTrend();
        assertThat(trend).hasSize(7);
        assertThat(trend.get(6).getCount()).isEqualTo(3L);
        assertThat(trend.get(5).getCount()).isEqualTo(7L);
        assertThat(trend.get(0).getCount()).isZero();
    }

    @Test
    @DisplayName("getOverview: 综合概览聚合五维度数据")
    void getOverview_success() {
        // 账号维度
        when(accountRepository.count()).thenReturn(10L);
        when(accountRepository.countByPlatformType()).thenReturn(Collections.singletonList(row("WECHAT", 10L)));
        when(accountRepository.countByLoginState()).thenReturn(Collections.singletonList(row("LOGIN", 10L)));
        when(accountRepository.countByLoginState(any(String.class))).thenReturn(0L);
        when(accountHealthRepository.count()).thenReturn(5L);
        // 任务维度
        when(campaignRepository.count()).thenReturn(3L);
        when(campaignRepository.countByStatus()).thenReturn(Collections.singletonList(row("RUNNING", 3L)));
        when(campaignRepository.dailyCountByCreateTime(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        // 客户维度
        when(customerRepository.count()).thenReturn(50L);
        when(customerRepository.countByLifecycle()).thenReturn(Collections.singletonList(row("ACTIVE", 50L)));
        when(customerRepository.dailyCountByCreateTime(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        // 会话维度
        when(conversationRepository.count()).thenReturn(20L);
        when(messageRepository.count()).thenReturn(100L);
        when(messageRepository.dailyCountBySentAt(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(conversationRepository.countByLastMessageAtAfter(any(LocalDateTime.class)))
                .thenReturn(15L);
        // 风控维度
        when(riskSignalRepository.count()).thenReturn(8L);
        when(riskSignalRepository.countGroupByRiskLevel()).thenReturn(Collections.singletonList(row("LOW", 8L)));
        when(riskSignalRepository.countGroupBySignalType()).thenReturn(Collections.emptyList());
        when(riskSignalRepository.dailyCountByTriggeredAt(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        DashboardOverviewVo result = service.getOverview();

        assertThat(result.getAccountOverview()).isNotNull();
        assertThat(result.getAccountOverview().getTotalAccounts()).isEqualTo(10L);
        assertThat(result.getCampaignOverview()).isNotNull();
        assertThat(result.getCampaignOverview().getTotalCampaigns()).isEqualTo(3L);
        assertThat(result.getCustomerOverview()).isNotNull();
        assertThat(result.getCustomerOverview().getTotalCustomers()).isEqualTo(50L);
        assertThat(result.getConversationOverview()).isNotNull();
        assertThat(result.getConversationOverview().getTotalConversations()).isEqualTo(20L);
        assertThat(result.getRiskOverview()).isNotNull();
        assertThat(result.getRiskOverview().getTotalRiskSignals()).isEqualTo(8L);
    }

    @Test
    @DisplayName("getTrend: customers 指标按 3 天聚合, 计算总量/日均/峰值")
    void getTrend_customers_success() {
        // 3 天窗口, 仅今天与昨天有数据
        when(customerRepository.dailyCountByCreateTime(any(LocalDateTime.class)))
                .thenReturn(List.of(dailyRow(0, 10L), dailyRow(1, 20L)));

        DashboardTrendVo result = service.getTrend("customers", 3);

        assertThat(result.getMetric()).isEqualTo("customers");
        assertThat(result.getDays()).isEqualTo(3);
        assertThat(result.getStartDate()).isEqualTo(LocalDate.now().minusDays(2).format(DATE_FORMATTER));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.now().format(DATE_FORMATTER));
        // 趋势长度 = 3
        assertThat(result.getTrend()).hasSize(3);
        // 总量 = 10 + 20 = 30
        assertThat(result.getTotalCount()).isEqualTo(30L);
        // 日均 = 30 / 3 = 10
        assertThat(result.getDailyAverage()).isEqualTo(10.0d);
        // 峰值 = 20, 峰值日期 = 昨天
        assertThat(result.getPeakValue()).isEqualTo(20L);
        assertThat(result.getPeakDate()).isEqualTo(LocalDate.now().minusDays(1).format(DATE_FORMATTER));
    }

    @Test
    @DisplayName("getTrend: 不支持的指标抛 BAD_REQUEST")
    void getTrend_invalidMetric_throwsBadRequest() {
        assertThatThrownBy(() -> service.getTrend("unknown-metric", 7))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不支持的趋势指标");
        // 验证未触发任何聚合查询
        verify(customerRepository, times(0)).dailyCountByCreateTime(any());
    }

    @Test
    @DisplayName("getTrend: days 小于 1 时回退为默认 7, 大于 90 时夹紧为 90")
    void getTrend_daysClamping() {
        // 默认 7 天场景
        when(customerRepository.dailyCountByCreateTime(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        DashboardTrendVo resultNegative = service.getTrend("customers", -1);
        assertThat(resultNegative.getDays()).isEqualTo(7);
        // 7 天趋势长度
        assertThat(resultNegative.getTrend()).hasSize(7);

        // 上限 90 天场景
        DashboardTrendVo resultHuge = service.getTrend("customers", 200);
        assertThat(resultHuge.getDays()).isEqualTo(90);
        assertThat(resultHuge.getTrend()).hasSize(90);
    }

    @Test
    @DisplayName("getTrend: metric 为 null 时抛 BAD_REQUEST (避免 NPE)")
    void getTrend_nullMetric_throwsBadRequest() {
        assertThatThrownBy(() -> service.getTrend(null, 7))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不支持的趋势指标");
    }

    @Test
    @DisplayName("getTrend: messages 指标按指定天数聚合, 缺失日期补零")
    void getTrend_messages_success() {
        when(messageRepository.dailyCountBySentAt(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(dailyRow(0, 5L)));

        DashboardTrendVo result = service.getTrend("messages", 2);

        assertThat(result.getMetric()).isEqualTo("messages");
        assertThat(result.getDays()).isEqualTo(2);
        // 趋势长度 = 2
        assertThat(result.getTrend()).hasSize(2);
        // 总量 = 5
        assertThat(result.getTotalCount()).isEqualTo(5L);
        // 峰值日期为今天
        assertThat(result.getPeakDate()).isEqualTo(LocalDate.now().format(DATE_FORMATTER));
        assertThat(result.getPeakValue()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getTrend: risk-signals 指标大小写不敏感 (传入大写也可识别)")
    void getTrend_riskSignals_caseInsensitive() {
        when(riskSignalRepository.dailyCountByTriggeredAt(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(dailyRow(0, 2L)));

        DashboardTrendVo result = service.getTrend("RISK-SIGNALS", 1);

        // metric 字段为小写形式
        assertThat(result.getMetric()).isEqualTo("risk-signals");
        assertThat(result.getDays()).isEqualTo(1);
        assertThat(result.getTotalCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getTrend: 空数据时 totalCount=0, peakValue=0, peakDate=null")
    void getTrend_emptyData() {
        when(campaignRepository.dailyCountByCreateTime(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        DashboardTrendVo result = service.getTrend("campaigns", 5);

        assertThat(result.getTotalCount()).isZero();
        assertThat(result.getDailyAverage()).isEqualTo(0.0d);
        assertThat(result.getPeakValue()).isZero();
        // 仍补齐 5 天空数据
        assertThat(result.getTrend()).hasSize(5);
    }
}
