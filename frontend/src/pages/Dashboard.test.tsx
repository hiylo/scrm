/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Dashboard.test.tsx
 * Description : 运营看板页面的骨架加载 / 统计卡片 / ECharts 配置 / 跟进提醒 / 空数据与报错降级测试
 *
 * 选型说明: 沿用 Customers、Conversations 的 vi.mock 方案 —— 页面只通过 apiClient 访问后端;
 * echarts-for-react 在 jsdom 中没有 canvas 无法初始化, 因此替换为记录 props 的桩组件,
 * 断言图表 option (而非像素), 这样既覆盖饼图数据接线 / 指标色值 / 平均值参考线等真实逻辑
 * 又不依赖渲染。recordToPieData 本身已下沉到 src/utils/chart.ts, 由 chart.test.ts 直接单测。
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, within, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import dayjs from 'dayjs';
import { Route, Routes } from 'react-router-dom';
import Dashboard from './Dashboard';
import { apiClient } from '../api/client';
import { useScrmWebSocket } from '../hooks/useScrmWebSocket';
import { dashboardOverview, dashboardTrend, deferred, validToken } from '../test/fixtures';
import { PathProbe, renderWithProviders } from '../test/render';

vi.mock('../api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  apiClientInstance: { get: vi.fn(), post: vi.fn() },
  getApiBaseUrl: () => '',
  DEFAULT_TIMEOUT: 30000,
  LONG_TIMEOUT: 120000,
}));

vi.mock('../hooks/useScrmWebSocket', () => ({
  useScrmWebSocket: vi.fn(() => ({ connected: false, lastNotification: null })),
}));

/** 图表桩组件记录的 props */
interface ChartProps {
  option?: Record<string, unknown>;
  showLoading?: boolean;
  notMerge?: boolean;
  style?: Record<string, unknown>;
}
type ChartElement = HTMLElement & { __chartProps?: ChartProps };

vi.mock('echarts-for-react', () => ({
  default: (props: ChartProps) => (
    <div
      data-testid="echarts"
      ref={(el: HTMLElement | null) => {
        if (el) (el as ChartElement).__chartProps = props;
      }}
    />
  ),
}));

/** 接口报错哨兵: 传入 route() 表示该接口 reject */
class ApiFailure extends Error {}

/** 按 URL 分发看板三个接口 */
function route(overrides: { overview?: unknown; trend?: unknown; followUps?: unknown } = {}) {
  const routes = {
    overview: dashboardOverview(),
    trend: dashboardTrend(),
    followUps: [] as unknown[],
    ...overrides,
  };
  const respond = (value: unknown) =>
    value instanceof ApiFailure ? Promise.reject(value) : Promise.resolve(value);

  get.mockImplementation(((url: string) => {
    const u = String(url);
    if (u.startsWith('/scrm/dashboard/overview')) return respond(routes.overview);
    if (u.startsWith('/scrm/dashboard/trend')) return respond(routes.trend);
    if (u.startsWith('/scrm/dashboard/follow-ups')) return respond(routes.followUps);
    return Promise.resolve(null);
  }) as never);
  return routes;
}

function renderDashboard() {
  return renderWithProviders(
    <Routes>
      <Route path="/dashboard" element={<Dashboard />} />
      <Route path="/customers" element={<PathProbe />} />
      <Route path="/customers/:id" element={<PathProbe />} />
      <Route path="/conversations" element={<PathProbe />} />
      <Route path="/campaigns" element={<PathProbe />} />
      <Route path="/risk-rules" element={<PathProbe />} />
    </Routes>,
    '/dashboard',
  );
}

/** 首屏加载完成后进入内容态 */
async function renderLoaded() {
  const result = renderDashboard();
  await screen.findByText('客户总数');
  return result;
}

const OVERVIEW_URL = '/scrm/dashboard/overview';
const trendUrl = (metric: string, days: number) =>
  `/scrm/dashboard/trend?metric=${metric}&days=${days}`;
const FOLLOW_UPS_URL = '/scrm/dashboard/follow-ups?limit=10';

/** 某个接口的调用次数 */
function callCount(prefix: string) {
  return get.mock.calls.filter((call) => String(call[0]).startsWith(prefix)).length;
}

/** 按 DOM 顺序取第 n 个图表桩: 0 趋势 1 风险 2 近期消息 3 平台 4 登录态 5 生命周期 6 信号类型 */
function chartProps(index: number): ChartProps {
  const elements = screen.getAllByTestId('echarts');
  return (elements[index] as ChartElement).__chartProps as ChartProps;
}

/** 折线 / 条形图 option 的可断言子集 */
function seriesOf(index: number) {
  const option = chartProps(index).option as {
    series: Array<{
      name: string;
      type: string;
      data: unknown[];
      smooth?: boolean;
      itemStyle?: { color?: string };
      markLine?: { data?: unknown[]; label?: { formatter?: string } };
    }>;
  };
  return option.series[0];
}

function axisDataOf(index: number, axis: 'xAxis' | 'yAxis' = 'xAxis') {
  const option = chartProps(index).option as Record<string, { data?: unknown[] }>;
  return option[axis]?.data ?? [];
}

/** 饼图 series[0].data */
function pieDataOf(index: number) {
  const option = chartProps(index).option as {
    series: Array<{ data: Array<{ value: number; name: string; itemStyle?: { color?: string } }> }>;
  };
  return option.series[0].data;
}

/** 触发一次 WebSocket 看板统计推送 */
function pushStat(statType: string) {
  const calls = ws.mock.calls;
  const options = calls[calls.length - 1][0];
  act(() => options.onNotification?.({ type: 'DASHBOARD_STAT_UPDATE', data: { statType } }));
}

/** 四张统计卡片展示的数值 */
function statValues(container: HTMLElement) {
  return [...container.querySelectorAll('.ant-statistic-content-value')].map(
    (element) => element.textContent,
  );
}

/** 高亮动画时长为 1500ms, 断言其自动撤销时需要放宽超时 */
const HIGHLIGHT_SETTLE = { timeout: 4000 };

const get = vi.mocked(apiClient.get);
const ws = vi.mocked(useScrmWebSocket);

beforeEach(() => {
  get.mockReset();
  ws.mockReset();
  ws.mockReturnValue({ connected: false, lastNotification: null });
  route();
});

describe('Dashboard 加载态', () => {
  it('概览接口未返回时展示骨架屏, 返回后渲染真实内容', async () => {
    const pendingOverview = deferred<unknown>();
    get.mockImplementation(((url: string) => {
      const u = String(url);
      if (u.startsWith(OVERVIEW_URL)) return pendingOverview.promise;
      if (u.startsWith('/scrm/dashboard/trend')) return Promise.resolve(dashboardTrend());
      return Promise.resolve([]);
    }) as never);

    const { container } = renderDashboard();
    await waitFor(() => expect(container.querySelectorAll('.ant-skeleton')).toHaveLength(6));
    // 骨架阶段不渲染图表, 也不渲染统计卡片
    expect(screen.queryAllByTestId('echarts')).toHaveLength(0);
    expect(screen.queryByText('客户总数')).not.toBeInTheDocument();

    act(() => pendingOverview.resolve(dashboardOverview()));
    await screen.findByText('客户总数');
    expect(container.querySelectorAll('.ant-skeleton')).toHaveLength(0);
    expect(screen.getAllByTestId('echarts')).toHaveLength(7);
  });

  it('首屏并行请求概览、趋势与跟进提醒, 默认指标 customers / 7 天', async () => {
    await renderLoaded();
    const urls = get.mock.calls.map((call) => String(call[0]));
    expect(urls).toContain(OVERVIEW_URL);
    expect(urls).toContain(trendUrl('customers', 7));
    expect(urls).toContain(FOLLOW_UPS_URL);
  });
});

describe('Dashboard 统计卡片', () => {
  it('按后端概览字段渲染四项核心指标', async () => {
    const { container } = await renderLoaded();
    expect(statValues(container)).toEqual(['128', '64', '5', '9']);
  });

  it('卡片副标题取最近一日新增、活跃会话、运行中任务与高风险数', async () => {
    await renderLoaded();
    // recentNewCustomers 取最后一项 (09-12 → 7)
    expect(screen.getByText('今日新增 7 人')).toBeInTheDocument();
    expect(screen.getByText('活跃会话 21 个')).toBeInTheDocument();
    expect(screen.getByText('运行中 2 个')).toBeInTheDocument();
    expect(screen.getByText('高风险 2 个')).toBeInTheDocument();
  });

  it('缺失分布字段时副标题回退为 0', async () => {
    route({
      overview: {
        accountOverview: {},
        campaignOverview: { totalCampaigns: 1 },
        customerOverview: { totalCustomers: 2 },
        conversationOverview: { totalConversations: 3 },
        riskOverview: { totalRiskSignals: 4 },
      },
    });
    const { container } = await renderLoaded();
    expect(screen.getByText('今日新增 0 人')).toBeInTheDocument();
    expect(screen.getByText('活跃会话 0 个')).toBeInTheDocument();
    expect(screen.getByText('运行中 0 个')).toBeInTheDocument();
    expect(screen.getByText('高风险 0 个')).toBeInTheDocument();
    expect(statValues(container)).toEqual(['2', '3', '1', '4']);
  });

  const cardRoutes: Array<[string, string]> = [
    ['客户总数', '/customers'],
    ['会话总数', '/conversations'],
    ['营销任务', '/campaigns'],
    ['风控信号', '/risk-rules'],
  ];

  it.each(cardRoutes)('点击「%s」卡片跳转 %s', async (title, path) => {
    const user = userEvent.setup();
    await renderLoaded();
    const card = screen
      .getByText(title, { selector: '.ant-statistic-title' })
      .closest('.stat-card') as HTMLElement;

    await user.click(within(card).getByText(title));
    expect(screen.getByTestId('path')).toHaveTextContent(path);
  });
});

describe('Dashboard 图表配置', () => {
  it('趋势折线图使用当前指标名、色值、日期轴与平均值参考线', async () => {
    await renderLoaded();
    const series = seriesOf(0);
    expect(series.name).toBe('客户增长');
    expect(series.type).toBe('line');
    expect(series.smooth).toBe(true);
    expect(series.data).toEqual([3, 4, 5]);
    expect(series.itemStyle?.color).toBe('#6366f1');
    expect(axisDataOf(0)).toEqual(['09-06', '09-07', '09-08']);
    // 平均值参考线由前端声明统计类型, 具体数值交给 echarts
    expect(series.markLine?.data).toEqual([{ type: 'average', name: '平均值' }]);
    expect(series.markLine?.label?.formatter).toBe('平均: {c}');
    // 切换指标需要整份 option 重建, 因此必须 notMerge
    expect(chartProps(0).notMerge).toBe(true);
  });

  it('趋势接口返回空数组时图表数据为空而非报错', async () => {
    route({ trend: dashboardTrend({ trend: [] }) });
    await renderLoaded();
    expect(seriesOf(0).data).toEqual([]);
    expect(axisDataOf(0)).toEqual([]);
  });

  it('风险等级分布固定四档并补齐缺失等级为 0', async () => {
    await renderLoaded();
    expect(pieDataOf(1)).toEqual([
      expect.objectContaining({ value: 0, name: '严重' }),
      expect.objectContaining({ value: 2, name: '高风险' }),
      expect.objectContaining({ value: 4, name: '中风险' }),
      expect.objectContaining({ value: 3, name: '低风险' }),
    ]);
    expect(pieDataOf(1)[1].itemStyle?.color).toBe('#ef4444');
  });

  it('平台与登录态分布将枚举键翻译为中文, 未知键原样展示', async () => {
    route({
      overview: dashboardOverview({
        accountOverview: {
          platformDistribution: { wework: 12, dingtalk: 2 },
          loginStateDistribution: { LOGIN: 9, BAN: 1 },
        },
      }),
    });
    await renderLoaded();
    expect(pieDataOf(3).map((d) => [d.name, d.value])).toEqual([
      ['企业微信', 12],
      ['dingtalk', 2],
    ]);
    expect(pieDataOf(4).map((d) => [d.name, d.value])).toEqual([
      ['已登录', 9],
      ['BAN', 1],
    ]);
  });

  it('生命周期条形图翻译坐标名并保持键序对应', async () => {
    await renderLoaded();
    const series = seriesOf(5);
    expect(series.name).toBe('客户数');
    expect(series.type).toBe('bar');
    expect(series.data).toEqual([30, 80, 18]);
    expect(axisDataOf(5, 'yAxis')).toEqual(['新客户', '活跃', '流失']);
  });

  it('近期消息趋势取 recentMessages 并截断日期年份', async () => {
    await renderLoaded();
    expect(axisDataOf(2)).toEqual(['09-11', '09-12']);
    expect(seriesOf(2).data).toEqual([40, 55]);
  });

  it('风控信号类型饼图翻译类型枚举', async () => {
    await renderLoaded();
    expect(pieDataOf(6).map((d) => [d.name, d.value])).toEqual([
      ['频率异常', 5],
      ['内容违规', 4],
    ]);
  });

  it('分布数据为空时各图表渲染空数组', async () => {
    route({
      overview: {
        accountOverview: { platformDistribution: {}, loginStateDistribution: {} },
        campaignOverview: { totalCampaigns: 0, statusDistribution: {} },
        customerOverview: { totalCustomers: 0, lifecycleDistribution: {}, recentNewCustomers: [] },
        conversationOverview: { totalConversations: 0, recentMessages: [] },
        riskOverview: {
          totalRiskSignals: 0,
          riskLevelDistribution: {},
          signalTypeDistribution: {},
        },
      },
    });
    await renderLoaded();
    expect(pieDataOf(3)).toEqual([]);
    expect(pieDataOf(4)).toEqual([]);
    expect(pieDataOf(6)).toEqual([]);
    expect(seriesOf(2).data).toEqual([]);
    expect(seriesOf(5).data).toEqual([]);
    // 风险分布是固定四档, 空数据仍保留 0 值条目
    expect(pieDataOf(1).map((d) => d.value)).toEqual([0, 0, 0, 0]);
  });
});

describe('Dashboard 指标与时间范围切换', () => {
  it('切换趋势指标只重新请求趋势接口', async () => {
    const user = userEvent.setup();
    await renderLoaded();
    const overviewCalls = callCount(OVERVIEW_URL);

    await user.click(screen.getByRole('tab', { name: '营销任务' }));

    await waitFor(() => expect(get).toHaveBeenCalledWith(trendUrl('campaigns', 7)));
    expect(seriesOf(0).name).toBe('营销任务');
    expect(seriesOf(0).itemStyle?.color).toBe('#f59e0b');
    // 指标切换不触发概览重复拉取
    expect(callCount(OVERVIEW_URL)).toBe(overviewCalls);
  });

  it('切换天数后按新天数请求趋势并展示图表 loading 状态', async () => {
    const user = userEvent.setup();
    await renderLoaded();

    // 让趋势请求挂起, 观察图表自身 showLoading 开关
    const pendingTrend = deferred<unknown>();
    get.mockImplementation(((url: string) => {
      if (String(url).startsWith('/scrm/dashboard/trend')) return pendingTrend.promise;
      return Promise.resolve(dashboardOverview());
    }) as never);

    await user.click(screen.getByRole('radio', { name: '14天' }));

    await waitFor(() => expect(chartProps(0).showLoading).toBe(true));
    expect(String(get.mock.calls[get.mock.calls.length - 1][0])).toBe(trendUrl('customers', 14));

    act(() => pendingTrend.resolve(dashboardTrend({ trend: [{ date: '2026-09-09', count: 12 }] })));
    await waitFor(() => expect(chartProps(0).showLoading).toBe(false));
    expect(seriesOf(0).data).toEqual([12]);
  });

  it('手动刷新重新拉取概览并更新最后刷新时间', async () => {
    const user = userEvent.setup();
    const { container } = await renderLoaded();
    expect(container.querySelector('.last-refreshed')).toBeInTheDocument();
    const before = callCount(OVERVIEW_URL);

    await user.click(container.querySelector('.anticon-reload') as HTMLElement);

    await waitFor(() => expect(callCount(OVERVIEW_URL)).toBeGreaterThan(before));
    expect(container.querySelector('.last-refreshed')?.textContent).toMatch(
      /^最后刷新: \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/,
    );
  });

  it('自动刷新开关启用后注册 60 秒轮询, 卸载时清理定时器', async () => {
    const user = userEvent.setup();
    const setIntervalSpy = vi.spyOn(window, 'setInterval');
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval');
    const { container, unmount } = await renderLoaded();

    const switchElement = container.querySelector('.ant-switch') as HTMLElement;
    expect(switchElement).toHaveAttribute('aria-checked', 'false');
    await user.click(switchElement);
    expect(switchElement).toHaveAttribute('aria-checked', 'true');
    expect(setIntervalSpy).toHaveBeenCalledWith(expect.any(Function), 60_000);

    const timer = setIntervalSpy.mock.results[setIntervalSpy.mock.results.length - 1]
      .value as unknown as number;
    unmount();
    expect(clearIntervalSpy).toHaveBeenCalledWith(timer);
  });
});

describe('Dashboard 待跟进客户', () => {
  /** 逾期 + 两小时内紧急 + 无昵称无跟进时间 三种行 */
  function followUpRows() {
    const overdueAt = dayjs().subtract(2, 'day');
    const urgentAt = dayjs().add(1, 'hour');
    return {
      overdueAt,
      urgentAt,
      rows: [
        {
          id: '1001',
          nickname: '张三',
          platformType: 'wework',
          platformCustomerUid: 'wm_1',
          lifecycle: 'ACTIVE',
          nextFollowUpAt: overdueAt.format('YYYY-MM-DDTHH:mm:ss'),
          remark: 'r'.repeat(25),
        },
        {
          id: '1002',
          nickname: 'vip user',
          platformType: 'wework',
          platformCustomerUid: 'wm_2',
          nextFollowUpAt: urgentAt.format('YYYY-MM-DDTHH:mm:ss'),
          remark: '简短备注',
        },
        { id: '1003', platformType: 'wework', platformCustomerUid: 'wm_3' },
      ],
    };
  }

  it('跟进列表为空时展示空态且不计角标', async () => {
    const { container } = await renderLoaded();
    expect(container.querySelector('.ant-badge-count')).toBeNull();
    expect(screen.getByText('暂无待跟进客户')).toBeInTheDocument();
  });

  it('渲染跟进列表: 角标计数、逾期/紧急文案、头像首字母与备注截断', async () => {
    const { overdueAt, urgentAt, rows } = followUpRows();
    route({ followUps: rows });
    const { container } = await renderLoaded();

    expect(container.querySelector('.ant-badge-count')).toHaveTextContent('3');
    const text = container.textContent as string;
    expect(text).toContain(`逾期: ${overdueAt.format('MM-DD HH:mm')}`);
    expect(text).toContain(`跟进: ${urgentAt.format('MM-DD HH:mm')}`);
    // 无昵称回退客户编号, 头像取昵称首字母大写
    expect(screen.getByText('客户 1003')).toBeInTheDocument();
    expect(screen.getByText('V')).toBeInTheDocument();
    // 枚举复用页面顶部中文映射 (lifecycleLabels / platformLabels), 三行都是 wework 客户
    expect(screen.getByText('活跃')).toBeInTheDocument();
    expect(screen.getAllByText('企业微信')).toHaveLength(3);
    // 超过 20 字的备注截断为 20 字 + 省略号
    expect(text).toContain(`${'r'.repeat(20)}...`);
    expect(text).not.toContain(`${'r'.repeat(21)}`);
  });

  it('跟进列表枚举未命中中文映射时兜底展示原始值而不是 undefined', async () => {
    route({
      followUps: [
        {
          id: '2001',
          nickname: '李四',
          platformType: 'dingtalk',
          platformCustomerUid: 'dt_1',
          lifecycle: 'RECALL',
        },
      ],
    });
    const { container } = await renderLoaded();

    expect(within(container).getByText('RECALL')).toBeInTheDocument();
    expect(within(container).getByText('dingtalk')).toBeInTheDocument();
    expect(container.textContent).not.toContain('undefined');
  });

  it('点击「详情」跳转对应客户详情', async () => {
    const user = userEvent.setup();
    route({ followUps: followUpRows().rows });
    await renderLoaded();

    await user.click(screen.getAllByRole('button', { name: /详情/ })[0]);
    expect(screen.getByTestId('path')).toHaveTextContent('/customers/1001');
  });

  it('点击「查看全部」跳转客户列表', async () => {
    const user = userEvent.setup();
    route({ followUps: followUpRows().rows });
    await renderLoaded();

    await user.click(screen.getByRole('button', { name: /查看全部/ }));
    expect(screen.getByTestId('path')).toHaveTextContent('/customers');
  });

  it('跟进提醒接口报错时只局部提示, 不影响看板主体', async () => {
    route({ followUps: new ApiFailure('follow ups down') });
    const { container } = await renderLoaded();

    expect(statValues(container)).toEqual(['128', '64', '5', '9']);
    expect(screen.getByText('待跟进客户加载失败')).toBeInTheDocument();
    expect(screen.getByText('follow ups down')).toBeInTheDocument();
    // 失败时不再谎称 "暂无待跟进客户", 也不计入角标
    expect(screen.queryByText('暂无待跟进客户')).not.toBeInTheDocument();
    expect(container.querySelector('.ant-badge-count')).toBeNull();
  });

  it('接口恢复后手动刷新会清掉跟进失败提示', async () => {
    const user = userEvent.setup();
    route({ followUps: new ApiFailure('follow ups down') });
    const { container } = await renderLoaded();
    expect(screen.getByText('待跟进客户加载失败')).toBeInTheDocument();

    // 跟进接口恢复, 返回一行数据
    get.mockImplementation(((url: string) => {
      const u = String(url);
      if (u.startsWith('/scrm/dashboard/follow-ups')) {
        return Promise.resolve([
          { id: '3001', nickname: '王五', platformType: 'wework', platformCustomerUid: 'wm_9' },
        ]);
      }
      if (u.startsWith('/scrm/dashboard/trend')) return Promise.resolve(dashboardTrend());
      return Promise.resolve(dashboardOverview());
    }) as never);

    await user.click(container.querySelector('.anticon-reload') as HTMLElement);

    await waitFor(() => expect(screen.queryByText('待跟进客户加载失败')).not.toBeInTheDocument());
    expect(screen.getByText('王五')).toBeInTheDocument();
    expect(container.querySelector('.ant-badge-count')).toHaveTextContent('1');
  });
});

describe('Dashboard 报错与实时推送', () => {
  it('概览接口报错时整页降级为失败结果并透出原因', async () => {
    route({ overview: new ApiFailure('后端服务不可用') });
    renderDashboard();

    expect(await screen.findByText('数据加载失败')).toBeInTheDocument();
    expect(screen.getByText('后端服务不可用')).toBeInTheDocument();
    expect(screen.queryByText('客户总数')).not.toBeInTheDocument();
  });

  it('趋势接口报错时保留已加载内容, 不整页降级', async () => {
    const user = userEvent.setup();
    const { container } = await renderLoaded();
    get.mockImplementation(((url: string) => {
      if (String(url).startsWith('/scrm/dashboard/trend')) {
        return Promise.reject(new ApiFailure('trend down'));
      }
      return Promise.resolve(dashboardOverview());
    }) as never);

    await user.click(screen.getByRole('radio', { name: '30天' }));
    await waitFor(() => expect(chartProps(0).showLoading).toBe(false));
    expect(statValues(container)).toEqual(['128', '64', '5', '9']);
    expect(screen.queryByText('数据加载失败')).not.toBeInTheDocument();
  });

  it('概览接口成功但返回 null 时展示空态而不是错误页', async () => {
    route({ overview: null });
    renderDashboard();

    expect(await screen.findByText('暂无看板数据')).toBeInTheDocument();
    expect(screen.queryByText('数据加载失败')).not.toBeInTheDocument();
    expect(screen.queryByText('请稍后重试')).not.toBeInTheDocument();
  });

  it('概览接口成功但返回空对象时同样按空态处理', async () => {
    route({ overview: {} });
    renderDashboard();

    expect(await screen.findByText('暂无看板数据')).toBeInTheDocument();
    expect(screen.queryByText('数据加载失败')).not.toBeInTheDocument();
  });

  it('空态提供刷新入口, 概览恢复后渲染正常内容', async () => {
    const user = userEvent.setup();
    route({ overview: null });
    renderDashboard();

    const refresh = await screen.findByRole('button', { name: /刷\s*新/ });
    // 概览接口恢复后点刷新
    route();
    await user.click(refresh);

    await screen.findByText('客户总数');
    expect(screen.queryByText('暂无看板数据')).not.toBeInTheDocument();
  });

  it('错误页提供重试入口, 接口恢复后重新渲染看板', async () => {
    const user = userEvent.setup();
    route({ overview: new ApiFailure('后端服务不可用') });
    renderDashboard();

    const retry = await screen.findByRole('button', { name: /重\s*试/ });
    // 接口恢复后点重试, 错误态应被清空
    route();
    await user.click(retry);

    await screen.findByText('客户总数');
    expect(screen.queryByText('数据加载失败')).not.toBeInTheDocument();
  });

  it('透传 localStorage 中的 token 给 WebSocket 并展示实时连接状态', async () => {
    localStorage.setItem('scrm_token', validToken());
    ws.mockReturnValue({ connected: true, lastNotification: null });
    await renderLoaded();

    const calls = ws.mock.calls;
    expect(calls[calls.length - 1][0]).toMatchObject({
      token: expect.stringMatching(/^[\w-]+\.[\w-]+\./),
    });
    expect(screen.getByText('实时')).toBeInTheDocument();
    expect(screen.queryByText('实时 (已断开)')).not.toBeInTheDocument();
  });

  it('WebSocket 断开时展示断开文案', async () => {
    await renderLoaded();
    expect(screen.getByText('实时 (已断开)')).toBeInTheDocument();
  });

  it('收到 customers 推送时客户总数自增并高亮对应卡片, 动画结束后取消高亮', async () => {
    const { container } = await renderLoaded();
    expect(statValues(container)).toEqual(['128', '64', '5', '9']);

    pushStat('customers');

    await waitFor(() => expect(statValues(container)).toEqual(['129', '64', '5', '9']));
    expect(container.querySelectorAll('.stat-card')[0].className).toContain(
      'stat-card-highlight',
    );

    // HIGHLIGHT_DURATION = 1500ms 后自动清除高亮类名
    await waitFor(() => expect(container.querySelectorAll('.stat-card-highlight')).toHaveLength(0), HIGHLIGHT_SETTLE);
    expect(container.querySelectorAll('.stat-card')).toHaveLength(4);
  });

  it('消息与会话推送都高亮会话卡片, 会话数与消息数分别展示', async () => {
    const { container } = await renderLoaded();
    const footers = () =>
      [...container.querySelectorAll('.stat-card-footer')].map((el) => el.textContent);
    expect(footers()[1]).toContain('累计消息 890 条');

    pushStat('messages');
    await waitFor(() => expect(footers()[1]).toContain('累计消息 891 条'));
    expect(container.querySelectorAll('.stat-card')[1].className).toContain(
      'stat-card-highlight',
    );
    // totalMessages 自增落在卡片副标题, 主指标仍是 totalConversations
    expect(statValues(container)).toEqual(['128', '64', '5', '9']);

    pushStat('conversations');
    await waitFor(() => expect(statValues(container)).toEqual(['128', '65', '5', '9']));
    expect(footers()[1]).toContain('累计消息 891 条');
  });

  it('风控推送高亮风控卡片且动画自动结束, 不改动任何计数', async () => {
    const { container } = await renderLoaded();

    pushStat('risk');
    await waitFor(() =>
      expect(container.querySelectorAll('.stat-card')[3].className).toContain(
        'stat-card-highlight',
      ),
    );
    expect(statValues(container)).toEqual(['128', '64', '5', '9']);

    await waitFor(() => expect(container.querySelectorAll('.stat-card-highlight')).toHaveLength(0), HIGHLIGHT_SETTLE);
  });

  it('营销推送 (marketing 与 campaigns 两种 statType) 都高亮营销卡片', async () => {
    const { container } = await renderLoaded();

    pushStat('marketing');
    await waitFor(() =>
      expect(container.querySelectorAll('.stat-card')[2].className).toContain(
        'stat-card-highlight',
      ),
    );
    expect(statValues(container)).toEqual(['128', '64', '5', '9']);

    await waitFor(() => expect(container.querySelectorAll('.stat-card-highlight')).toHaveLength(0), HIGHLIGHT_SETTLE);

    pushStat('campaigns');
    await waitFor(() =>
      expect(container.querySelectorAll('.stat-card')[2].className).toContain(
        'stat-card-highlight',
      ),
    );
  });

  it('未接线的 statType 推送不改计数, 也不产生高亮', async () => {
    const { container } = await renderLoaded();

    pushStat('accounts');
    await act(async () => {
      await new Promise((r) => setTimeout(r, 50));
    });
    expect(container.querySelectorAll('.stat-card-highlight')).toHaveLength(0);
    expect(statValues(container)).toEqual(['128', '64', '5', '9']);
  });
});
