/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Customers.test.tsx
 * Description : 客户列表页面的加载 / 成功 / 空数据 / 报错降级与关键交互测试
 *
 * 选型说明: 采用 vi.mock 模块级替换 apiClient, 而非引入 MSW。
 * 理由: 页面只通过 apiClient 抽象层访问后端, 模块 mock 零新增依赖、无网络层副作用与全局 fetch 劫持,
 * 而 axios 拦截器 (鉴权头注入 / 401 跳转 / 错误提示) 已在 client.test.ts 中用真实请求管线单独覆盖。
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, within, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import Customers from './Customers';
import { apiClient, apiClientInstance } from '../api/client';
import { cn, customer, page } from '../test/fixtures';
import { PathProbe, renderWithProviders } from '../test/render';

vi.mock('../api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  apiClientInstance: {
    get: vi.fn(),
    post: vi.fn(),
  },
  getApiBaseUrl: () => '',
  DEFAULT_TIMEOUT: 30000,
  LONG_TIMEOUT: 120000,
}));

/** 渲染客户页面, 并为导航断言挂载探针路由 */
function renderCustomers(initialPath = '/customers') {
  return renderWithProviders(
    <Routes>
      <Route path="/customers" element={<Customers />} />
      <Route path="/customers/:id" element={<PathProbe />} />
      <Route path="/conversations" element={<PathProbe />} />
    </Routes>,
    initialPath,
  );
}

const get = vi.mocked(apiClient.get);
const post = vi.mocked(apiClient.post);
const put = vi.mocked(apiClient.put);
const del = vi.mocked(apiClient.delete);
const rawGet = vi.mocked(apiClientInstance.get);

/** 最近一次列表请求的 URL */
const lastListUrl = (): string =>
  String(get.mock.calls[get.mock.calls.length - 1]?.[0] ?? '');

/** 读取 Blob 原始字节 (jsdom 未提供 Blob.arrayBuffer, 使用 FileReader) */
function readBlobBytes(blob: Blob): Promise<Uint8Array> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(blob);
  });
}

/** 挂起中的列表请求, 用于观察加载态; 返回值为结束挂起的函数 */
function pendingListRequest() {
  let resolve: ((value: unknown) => void) | null = null;
  get.mockImplementation(
    () =>
      new Promise((r) => {
        resolve = r;
      }) as never,
  );
  return (rows: ReturnType<typeof customer>[] = []) => resolve?.(page(rows));
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
  put.mockReset();
  del.mockReset();
  rawGet.mockReset();
  // 未显式配置时所有请求默认返回空分页, 避免用例间相互污染
  get.mockResolvedValue(page([]));
  post.mockResolvedValue(null);
  put.mockResolvedValue(null);
  del.mockResolvedValue(null);
  rawGet.mockResolvedValue({ data: new Blob(['x']) } as never);
});

describe('Customers 加载态', () => {
  it('请求未返回时展示表格 loading 遮罩', async () => {
    const settle = pendingListRequest();
    const { container } = renderCustomers();

    expect(screen.getByRole('tab', { name: '客户列表' })).toBeInTheDocument();
    await waitFor(() =>
      expect(container.querySelector('.ant-spin-spinning')).toBeTruthy(),
    );
    // 加载中列表内容被置灰
    expect(container.querySelector('.ant-spin-container.ant-spin-blur')).toBeTruthy();

    settle([customer({ nickname: '张三' })]);
    await screen.findByText('张三');
    await waitFor(() =>
      expect(container.querySelector('.ant-spin-spinning')).toBeNull(),
    );
    expect(container.querySelector('.ant-spin-container.ant-spin-blur')).toBeNull();
  });

  it('首次请求使用第 0 页与默认分页大小', async () => {
    renderCustomers();
    await waitFor(() => expect(get).toHaveBeenCalled());
    expect(lastListUrl()).toContain('/scrm/customers?');
    expect(lastListUrl()).toContain('page=0');
    expect(lastListUrl()).toContain('size=10');
  });
});

describe('Customers 成功渲染', () => {
  it('渲染客户行、平台标签、生命周期标签与分页总数', async () => {
    get.mockResolvedValue(
      page(
        [
          customer({ id: '1001', nickname: '张三', lifecycle: 'ACTIVE' }),
          customer({
            id: '1002',
            nickname: '李四',
            lifecycle: 'CHURNED',
            platformCustomerUid: 'wm_002',
          }),
        ],
        { totalElements: 42 },
      ),
    );

    renderCustomers();

    expect(await screen.findByText('张三')).toBeInTheDocument();
    expect(screen.getByText('李四')).toBeInTheDocument();
    expect(screen.getAllByText('企业微信').length).toBe(2);
    expect(screen.getByText('活跃')).toBeInTheDocument();
    expect(screen.getByText('流失')).toBeInTheDocument();
    expect(screen.getByText('wm_userid_001')).toBeInTheDocument();
    expect(screen.getByText('共 42 条')).toBeInTheDocument();
  });

  it('未知生命周期值原样展示, 缺失字段回退为占位符', async () => {
    get.mockResolvedValue(
      page([
        customer({
          id: '1003',
          nickname: '王五',
          lifecycle: 'WEIRD',
          ownerAccountId: undefined,
          lastInteractionAt: undefined,
          nextFollowUpAt: undefined,
        }),
      ]),
    );

    renderCustomers();

    expect(await screen.findByText('WEIRD')).toBeInTheDocument();
    expect(screen.getAllByText('-').length).toBeGreaterThanOrEqual(3);
  });

  it('从 URL 查询参数恢复关键词 / 平台 / 分页大小并带入请求', async () => {
    get.mockResolvedValue(page([customer()]));
    renderCustomers('/customers?q=%E5%BC%A0&lifecycle=NEW&size=20');
    await screen.findByText('张三');

    await waitFor(() => {
      expect(lastListUrl()).toContain('keyword=%E5%BC%A0');
      expect(lastListUrl()).toContain('lifecycle=NEW');
      expect(lastListUrl()).toContain('size=20');
    });
    // 搜索框回填历史关键词
    expect(screen.getByPlaceholderText('搜索昵称 / 平台 UID')).toHaveValue('张');
  });

  it('深链 ?page=3 直接请求第 3 页 (0 索引 page=2), 挂载时不重置页码', async () => {
    get.mockResolvedValue(page([customer()]));
    renderCustomers('/customers?page=3');
    await screen.findByText('张三');

    // 页码副作用只在关键词真正变化时触发, 首次挂载不再把 page 打回 0
    expect(lastListUrl()).toContain('page=2');
    expect(lastListUrl()).not.toContain('page=0');
    // 也少了一次由页码重置引发的多余请求
    expect(get).toHaveBeenCalledTimes(1);
  });

  it('关键词变化后回到第一页, 深链页码不残留', async () => {
    const user = userEvent.setup();
    get.mockResolvedValue(page([customer()]));
    renderCustomers('/customers?page=3');
    await screen.findByText('张三');
    expect(lastListUrl()).toContain('page=2');

    await user.type(screen.getByPlaceholderText('搜索昵称 / 平台 UID'), '李四');

    // 关键词变化后最终落到第 1 页 (注: 重置页码的副作用声明在取数之后, 中间会先发一次旧页码的请求)
    await waitFor(
      () => {
        expect(lastListUrl()).toContain('keyword=%E6%9D%8E%E5%9B%9B');
        expect(lastListUrl()).toContain('page=0');
      },
      { timeout: 3000 },
    );
  });
});

describe('Customers 空数据与报错降级', () => {
  it('后端返回空分页时展示空状态引导', async () => {
    localStorage.setItem('userRole', 'admin');
    get.mockResolvedValue(page([]));

    renderCustomers();

    expect(await screen.findByText('暂无客户数据')).toBeInTheDocument();
    const emptyArea = screen.getByText('暂无客户数据').closest('.ant-empty') as HTMLElement;
    expect(within(emptyArea).getByRole('button', { name: /新建客户/ })).toBeInTheDocument();
  });

  it('接口报错时清空列表并展示空态, 不崩溃', async () => {
    get.mockRejectedValue(new Error('boom'));

    renderCustomers();

    expect(await screen.findByText('暂无客户数据')).toBeInTheDocument();
    expect(screen.queryByText('张三')).not.toBeInTheDocument();
    expect(screen.queryByText(/共 \d+ 条/)).not.toBeInTheDocument();
  });
});

describe('Customers 交互', () => {
  it('搜索框输入后按防抖值重新请求', async () => {
    const user = userEvent.setup();
    get.mockResolvedValue(page([customer()]));
    renderCustomers();
    await screen.findByText('张三');

    await user.type(screen.getByPlaceholderText('搜索昵称 / 平台 UID'), '李四');

    await waitFor(() => expect(lastListUrl()).toContain('keyword=%E6%9D%8E%E5%9B%9B'), {
      timeout: 3000,
    });
  });

  it('只读角色不展示新建 / 导入 / 编辑 / 删除入口', async () => {
    localStorage.setItem('userRole', 'viewer');
    get.mockResolvedValue(page([customer()]));

    renderCustomers();
    await screen.findByText('张三');

    expect(screen.queryByRole('button', { name: /新建客户/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^导入/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: cn('编辑') })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: cn('删除') })).not.toBeInTheDocument();
    // 只读角色仍可刷新与导出
    expect(screen.getByRole('button', { name: /刷新/ })).toBeInTheDocument();
  });

  it('新建客户: 必填校验拦截空表单, 补齐后提交 POST', async () => {
    const user = userEvent.setup();
    localStorage.setItem('userRole', 'sales');
    get.mockResolvedValue(page([customer()]));

    renderCustomers();
    await screen.findByText('张三');

    await user.click(screen.getByRole('button', { name: /新建客户/ }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('平台类型')).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: cn('确定') }));
    expect(await within(dialog).findByText('请选择平台类型')).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();

    // antd Select 的占位符渲染在 .ant-select-selection-placeholder 上, 内部 input 无 placeholder
    await user.click(within(dialog).getByText('请选择平台'));
    const option = await screen.findByText('企业微信', {
      selector: '.ant-select-item-option-content',
    });
    await user.click(option);
    await user.type(within(dialog).getByPlaceholderText('请输入平台客户 UID'), 'wm_new_01');
    await user.type(within(dialog).getByPlaceholderText('请输入昵称'), '新客户');

    await user.click(within(dialog).getByRole('button', { name: cn('确定') }));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][0]).toBe('/scrm/customers');
    expect(post.mock.calls[0][1]).toMatchObject({
      platformType: 'wework',
      platformCustomerUid: 'wm_new_01',
      nickname: '新客户',
    });
    // 提交成功后刷新列表
    await waitFor(() => expect(get.mock.calls.length).toBeGreaterThan(1));
  });

  it('编辑客户回填表单并提交 PUT', async () => {
    const user = userEvent.setup();
    localStorage.setItem('userRole', 'manager');
    get.mockResolvedValue(page([customer({ id: '1001', nickname: '张三' })]));

    renderCustomers();
    await screen.findByText('张三');

    await user.click(screen.getByRole('button', { name: cn('编辑') }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByDisplayValue('张三')).toBeInTheDocument();

    const nickname = within(dialog).getByDisplayValue('张三');
    await user.clear(nickname);
    await user.type(nickname, '张三丰');
    await user.click(within(dialog).getByRole('button', { name: cn('确定') }));

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith(
        '/scrm/customers/1001',
        expect.objectContaining({ nickname: '张三丰' }),
      ),
    );
    expect(post).not.toHaveBeenCalled();
  });

  it('删除客户走二次确认, 取消时不发请求', async () => {
    const user = userEvent.setup();
    localStorage.setItem('userRole', 'admin');
    get.mockResolvedValue(page([customer()]));

    renderCustomers();
    await screen.findByText('张三');

    await user.click(screen.getByRole('button', { name: cn('删除') }));
    const confirmBox = await waitFor(() => {
      const el = document.querySelector('.ant-modal-confirm-body-wrapper');
      if (!el) throw new Error('确认弹窗未出现');
      return el as HTMLElement;
    });
    expect(within(confirmBox).getByText('删除客户')).toBeInTheDocument();
    expect(del).not.toHaveBeenCalled();

    await user.click(within(confirmBox).getByRole('button', { name: cn('取消') }));
    await waitFor(() => expect(screen.queryByText('删除客户')).not.toBeInTheDocument());
    expect(del).not.toHaveBeenCalled();
  });

  it('确认删除后调用 DELETE 并刷新列表', async () => {
    const user = userEvent.setup();
    localStorage.setItem('userRole', 'admin');
    get.mockResolvedValue(page([customer()]));

    renderCustomers();
    await screen.findByText('张三');
    const initialCalls = get.mock.calls.length;

    await user.click(screen.getByRole('button', { name: cn('删除') }));
    // 确认弹窗的 "删除" 按钮与行内按钮同名, 需限定在确认框容器内查找
    const confirmBox = await waitFor(() => {
      const el = document.querySelector('.ant-modal-confirm-body-wrapper');
      if (!el) throw new Error('确认弹窗未出现');
      return el as HTMLElement;
    });
    await user.click(within(confirmBox).getByRole('button', { name: cn('删除') }));

    await waitFor(() => expect(del).toHaveBeenCalledWith('/scrm/customers/1001'));
    await waitFor(() => expect(get.mock.calls.length).toBeGreaterThan(initialCalls));
  });

  it('点击行内查看按钮跳转到客户详情页', async () => {
    get.mockResolvedValue(page([customer({ id: '2468', nickname: '赵六' })]));

    renderCustomers();
    await screen.findByText('赵六');

    await userEvent.click(screen.getByRole('button', { name: /查看/ }));
    expect(await screen.findByTestId('path')).toHaveTextContent('/customers/2468');
  });

  it('切换生命周期筛选后带参数重新请求', async () => {
    const user = userEvent.setup();
    get.mockResolvedValue(page([customer()]));
    renderCustomers();
    await screen.findByText('张三');

    await user.click(screen.getByText('全部'));
    const option = await screen.findByText('新客户', {
      selector: '.ant-select-item-option-content',
    });
    await user.click(option);

    await waitFor(() => expect(lastListUrl()).toContain('lifecycle=NEW'));
  });

  it('导出 CSV 在无数据时不生成文件', async () => {
    const user = userEvent.setup();
    const createObjectURL = vi.fn(() => 'blob:csv');
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }));
    get.mockResolvedValue(page([]));
    renderCustomers();
    await screen.findByText('暂无客户数据');

    await user.click(screen.getByRole('button', { name: /导出 CSV/ }));
    expect(createObjectURL).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('有数据时导出 CSV 生成 Blob 并转义含逗号字段', async () => {
    const user = userEvent.setup();
    get.mockResolvedValue(page([customer({ nickname: '张三', remark: 'VIP,重点客户' })]));
    const captured: { blob?: Blob } = {};
    const createObjectURL = vi.fn((b: Blob) => {
      captured.blob = b;
      return 'blob:csv';
    });
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }));

    renderCustomers();
    await screen.findByText('张三');
    await user.click(screen.getByRole('button', { name: /导出 CSV/ }));

    await waitFor(() => expect(createObjectURL).toHaveBeenCalledTimes(1));
    const bytes = await readBlobBytes(captured.blob as Blob);
    // BOM 头 (EF BB BF) 保证 Excel 以 UTF-8 打开不乱码
    expect(Array.from(bytes.slice(0, 3))).toEqual([0xef, 0xbb, 0xbf]);
    const csv = new TextDecoder('utf-8').decode(bytes);
    expect(csv).toContain('"VIP,重点客户"');
    expect(csv).toContain('ID,平台类型,平台客户UID,昵称,生命周期');
    vi.unstubAllGlobals();
  });

  it('下载模板通过未解包的 axios 实例请求 blob', async () => {
    const user = userEvent.setup();
    vi.stubGlobal('URL', Object.assign(URL, {
      createObjectURL: vi.fn(() => 'blob:x'),
      revokeObjectURL: vi.fn(),
    }));
    get.mockResolvedValue(page([customer()]));
    renderCustomers();
    await screen.findByText('张三');

    await user.click(screen.getByRole('button', { name: /下载模板/ }));
    await waitFor(() =>
      expect(rawGet).toHaveBeenCalledWith('/scrm/customers/import-template?format=xlsx', {
        responseType: 'blob',
      }),
    );
    vi.unstubAllGlobals();
  });

  it('CSV 导入: 解析文件生成预览并按行提交 POST', async () => {
    const user = userEvent.setup();
    localStorage.setItem('userRole', 'admin');
    get.mockResolvedValue(page([customer()]));

    renderCustomers();
    await screen.findByText('张三');

    await user.click(screen.getByRole('button', { name: /导入 CSV/ }));
    const dialog = await screen.findByRole('dialog');

    const file = new File(
      ['\uFEFF昵称,平台,平台客户ID,备注\n张三,wework,wwid_1,VIP\n李四,wework,wwid_2,意向客户'],
      'customers.csv',
      { type: 'text/csv' },
    );
    const fileInput = dialog.querySelector('input[type="file"]') as HTMLInputElement;
    expect(fileInput).toBeTruthy();
    fireEvent.change(fileInput, { target: { files: [file] } });

    expect(await within(dialog).findByText(/共 2 行/)).toBeInTheDocument();

    // 未填归属账号 ID 时阻止提交
    await user.click(within(dialog).getByRole('button', { name: cn('确认导入') }));
    expect(await screen.findByText('请填写归属账号 ID')).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();

    await user.type(within(dialog).getByPlaceholderText('请输入归属账号 ID (数字)'), '3001');
    await user.click(within(dialog).getByRole('button', { name: cn('确认导入') }));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
    expect(post.mock.calls[0][1]).toEqual({
      nickname: '张三',
      platformType: 'wework',
      platformCustomerUid: 'wwid_1',
      ownerAccountId: 3001,
      remark: 'VIP',
    });
    expect(post.mock.calls[1][1]).toMatchObject({ nickname: '李四' });
  });
});
