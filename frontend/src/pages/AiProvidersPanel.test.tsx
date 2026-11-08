/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : AiProvidersPanel.test.tsx
 * Description : AI Provider 管理面板的列表渲染 / 新增 / 编辑回填 / 删除 / 连通测试 / 拉模型 / 设默认 / 启停测试
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent, within } from '@testing-library/react';
import AiProvidersPanel from './AiProvidersPanel';
import { apiClient } from '../api/client';
import { renderWithProviders } from '../test/render';
import { cn } from '../test/fixtures';

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

const get = vi.mocked(apiClient.get);
const post = vi.mocked(apiClient.post);
const put = vi.mocked(apiClient.put);
const del = vi.mocked(apiClient.delete);

/** Provider 行 */
function provider(overrides: Record<string, unknown> = {}) {
  return {
    providerName: '未命名',
    providerType: 'OPENAI_LLM',
    apiBaseUrl: 'http://127.0.0.1:8000',
    chatPath: '/v1/chat/completions',
    healthPath: '/v1/models',
    hasApiKey: true,
    capability: 'text',
    temperature: 0.7,
    maxTokens: 500,
    priority: 100,
    enabled: true,
    defaultText: false,
    defaultImage: false,
    connectionStatus: 'UNKNOWN',
    ...overrides,
  } as never;
}

/** 主文本 + 多模态网关（已连接，同时是默认文本与默认多模态） */
const MAGPIE = provider({
  id: '1',
  providerName: 'Magpie 网关',
  providerType: 'OPENAI_LLM',
  apiBaseUrl: 'http://192.168.1.150:3425',
  defaultModel: 'group/sensenova-pool',
  capability: 'text,image',
  connectionStatus: 'CONNECTED',
  enabled: true,
  defaultText: true,
  defaultImage: true,
});

/** 本地小模型（未测试，仅 text，无默认模型但有可用模型清单） */
const LOCAL = provider({
  id: '2',
  providerName: '内网小模型',
  providerType: 'LOCAL_LLM',
  apiBaseUrl: 'http://127.0.0.1:8080',
  defaultModel: undefined,
  capability: 'text',
  connectionStatus: 'UNKNOWN',
  enabled: false,
  defaultText: false,
  defaultImage: false,
  models: [
    { name: 'qwen2-vl', text: true, image: true, embedding: false },
    { name: 'qwen2', text: true, image: false, embedding: false },
  ],
});

/** 行的 Tag 文本集合（用于区分「默认文本」Tag 与同名的操作按钮） */
function tagTexts(row: HTMLElement): string[] {
  return Array.from(row.querySelectorAll('.ant-tag')).map(el => (el.textContent || '').trim());
}

/** 取某名称所在的表格行 */
function rowOf(name: string): HTMLElement {
  const cell = screen.getByText(name);
  return cell.closest('tr') as HTMLElement;
}

/** 取当前打开的弹窗 */
async function dialog(): Promise<HTMLElement> {
  await waitFor(() => expect(document.querySelector('.ant-modal-content')).toBeTruthy());
  return document.querySelector('.ant-modal-content') as HTMLElement;
}

function renderPage(list: unknown[] = [MAGPIE, LOCAL]) {
  get.mockResolvedValue(list as never);
  return renderWithProviders(<AiProvidersPanel />);
}

beforeEach(() => {
  vi.clearAllMocks();
  get.mockResolvedValue([MAGPIE, LOCAL] as never);
});

describe('AiProvidersPanel', () => {
  it('加载后表格展示 provider 列表（名称/类型/地址/模型/能力/默认标记/状态/启用开关）', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText('Magpie 网关')).toBeInTheDocument());
    expect(get).toHaveBeenCalledWith('/scrm/ai-providers');
    expect(screen.getByText('内网小模型')).toBeInTheDocument();

    const magpie = rowOf('Magpie 网关');
    // 类型枚举 → 中文标签
    expect(within(magpie).getByText('OpenAI 兼容 LLM')).toBeInTheDocument();
    expect(within(magpie).getByText('http://192.168.1.150:3425')).toBeInTheDocument();
    // 默认模型直出
    expect(within(magpie).getByText('group/sensenova-pool')).toBeInTheDocument();
    // 能力标签 + 默认标记 + 连接状态
    expect(tagTexts(magpie)).toEqual(
      expect.arrayContaining(['默认文本', '默认多模态', '文本', '多模态', '已连接']),
    );

    const local = rowOf('内网小模型');
    expect(within(local).getByText('本地 llama-server')).toBeInTheDocument();
    expect(within(local).getByText('2 个可用')).toBeInTheDocument();
    expect(tagTexts(local)).toEqual(expect.arrayContaining(['文本', '未测试']));
    // 该行不是默认 provider，只有同名操作按钮、没有同名 Tag
    expect(tagTexts(local)).not.toContain('默认文本');
    expect(tagTexts(local)).not.toContain('默认多模态');

    // 启用开关：Magpie 开启、内网小模型关闭
    expect(within(magpie).getByRole('switch')).toHaveAttribute('aria-checked', 'true');
    expect(within(local).getByRole('switch')).toHaveAttribute('aria-checked', 'false');
  });

  it('点击「新增 Provider」打开弹窗且表单默认值正确', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Magpie 网关')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: cn('新增 Provider') }));

    const box = await dialog();
    await waitFor(() => expect(within(box).getByLabelText('名称')).toBeInTheDocument());
    expect(within(box).getByText('新增 AI Provider')).toBeInTheDocument();

    // 名称/地址默认空
    expect((within(box).getByLabelText('名称') as HTMLInputElement).value).toBe('');
    expect((within(box).getByLabelText('API 基础地址') as HTMLInputElement).value).toBe('');
    // 类型默认 OpenAI 兼容 LLM
    expect(within(box).getByText('OpenAI 兼容 LLM')).toBeInTheDocument();
    // 路径默认值
    expect((within(box).getByLabelText('对话补全路径') as HTMLInputElement).value).toBe(
      '/v1/chat/completions',
    );
    expect((within(box).getByLabelText('健康/模型探测路径') as HTMLInputElement).value).toBe(
      '/v1/models',
    );
    // 能力默认 text
    expect(within(box).getByText('文本（对话/客服/文案）')).toBeInTheDocument();
    // 数值默认值
    expect((within(box).getByLabelText('温度') as HTMLInputElement).value).toBe('0.7');
    expect((within(box).getByLabelText('最大 token') as HTMLInputElement).value).toBe('500');
    expect((within(box).getByLabelText('优先级（小优先）') as HTMLInputElement).value).toBe('100');
    // 三个开关：启用=开，默认文本=关，默认多模态=关
    const switches = within(box).getAllByRole('switch');
    expect(switches).toHaveLength(3);
    expect(switches[0]).toHaveAttribute('aria-checked', 'true');
    expect(switches[1]).toHaveAttribute('aria-checked', 'false');
    expect(switches[2]).toHaveAttribute('aria-checked', 'false');
  });

  it('保存新增：POST /scrm/ai-providers，payload 不含密钥掩码', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Magpie 网关')).toBeInTheDocument());
    post.mockResolvedValue(provider({ id: '9' }));

    fireEvent.click(screen.getByRole('button', { name: cn('新增 Provider') }));
    const box = await dialog();
    await waitFor(() => expect(within(box).getByLabelText('名称')).toBeInTheDocument());

    fireEvent.change(within(box).getByLabelText('名称'), { target: { value: '内网网关' } });
    fireEvent.change(within(box).getByLabelText('API 基础地址'), {
      target: { value: 'http://10.0.0.9:8000' },
    });
    fireEvent.change(within(box).getByLabelText('API 密钥'), { target: { value: 'sk-abc' } });

    fireEvent.click(within(box).getByRole('button', { name: cn('确定') }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/scrm/ai-providers',
        expect.objectContaining({
          providerName: '内网网关',
          providerType: 'OPENAI_LLM',
          apiBaseUrl: 'http://10.0.0.9:8000',
          chatPath: '/v1/chat/completions',
          healthPath: '/v1/models',
          apiKey: 'sk-abc',
          capability: 'text',
          temperature: 0.7,
          maxTokens: 500,
          priority: 100,
          enabled: true,
          defaultText: false,
          defaultImage: false,
        }),
      ),
    );

    const payload = post.mock.calls[0][1] as Record<string, unknown>;
    expect(JSON.stringify(payload)).not.toContain('********');
    // 保存后重新拉取列表
    await waitFor(() => expect(get.mock.calls.length).toBeGreaterThan(1));
  });

  it('编辑回填：apiKey 显示掩码，保存时 payload.apiKey 为 undefined（不回传掩码）', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Magpie 网关')).toBeInTheDocument());
    put.mockResolvedValue(MAGPIE);

    fireEvent.click(within(rowOf('Magpie 网关')).getByRole('button', { name: 'edit' }));

    const box = await dialog();
    await waitFor(() => expect(within(box).getByLabelText('名称')).toBeInTheDocument());
    expect(within(box).getByText('编辑 AI Provider')).toBeInTheDocument();

    // 回填断言
    expect((within(box).getByLabelText('名称') as HTMLInputElement).value).toBe('Magpie 网关');
    expect((within(box).getByLabelText('API 基础地址') as HTMLInputElement).value).toBe(
      'http://192.168.1.150:3425',
    );
    expect((within(box).getByLabelText('默认模型') as HTMLInputElement).value).toBe(
      'group/sensenova-pool',
    );
    expect((within(box).getByLabelText('API 密钥') as HTMLInputElement).value).toBe('********');
    // 能力 'text,image' → 两个选项标签
    expect(within(box).getByText('文本（对话/客服/文案）')).toBeInTheDocument();
    expect(within(box).getByText('图像（证照 OCR 多模态）')).toBeInTheDocument();
    // 默认标记开关按数据回填
    const switches = within(box).getAllByRole('switch');
    expect(switches[0]).toHaveAttribute('aria-checked', 'true');
    expect(switches[1]).toHaveAttribute('aria-checked', 'true');
    expect(switches[2]).toHaveAttribute('aria-checked', 'true');

    fireEvent.click(within(box).getByRole('button', { name: cn('确定') }));

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith(
        '/scrm/ai-providers/1',
        expect.objectContaining({
          providerName: 'Magpie 网关',
          capability: 'text,image',
          defaultText: true,
          defaultImage: true,
        }),
      ),
    );
    const payload = put.mock.calls[0][1] as Record<string, unknown>;
    expect(payload.apiKey).toBeUndefined();
    expect(JSON.stringify(payload)).not.toContain('********');
  });

  it('删除：Popconfirm 二次确认（含危险操作确认框）后 DELETE', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Magpie 网关')).toBeInTheDocument());
    del.mockResolvedValue({} as never);

    fireEvent.click(within(rowOf('Magpie 网关')).getByRole('button', { name: 'delete' }));

    // Popconfirm 气泡确认
    await waitFor(() => expect(screen.getByText('删除该 Provider？')).toBeInTheDocument());
    let confirms = screen.getAllByRole('button', { name: cn('删除') });
    fireEvent.click(confirms[confirms.length - 1]);

    // 组件随后用 modal.confirm 再确认一次（不可逆操作）
    await waitFor(() => expect(document.querySelector('.ant-modal-confirm')).toBeTruthy());
    const confirmBox = document.querySelector('.ant-modal-confirm') as HTMLElement;
    // 标题在 header 与 body 各渲染一次（antd 的结构），用 textContent 断言即可
    expect(confirmBox.textContent).toContain('删除 Provider「Magpie 网关」？');
    expect(confirmBox.textContent).toContain('删除后该模型接入点将不可用');
    fireEvent.click(within(confirmBox).getByRole('button', { name: cn('删除') }));

    await waitFor(() => expect(del).toHaveBeenCalledWith('/scrm/ai-providers/1'));
  });

  it('连通测试：POST /scrm/ai-providers/{id}/test', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Magpie 网关')).toBeInTheDocument());
    post.mockResolvedValue({ id: '1', connectionStatus: 'CONNECTED' } as never);

    fireEvent.click(within(rowOf('Magpie 网关')).getByRole('button', { name: cn('测试') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/ai-providers/1/test'));
    await waitFor(() => expect(get.mock.calls.length).toBeGreaterThan(1));
  });

  it('拉取模型：POST /scrm/ai-providers/{id}/models', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('内网小模型')).toBeInTheDocument());
    post.mockResolvedValue([{ name: 'qwen2-vl', text: true, image: true, embedding: false }] as never);

    fireEvent.click(within(rowOf('内网小模型')).getByRole('button', { name: cn('模型') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/ai-providers/2/models'));
  });

  it('设默认文本：POST /scrm/ai-providers/{id}/default-text（已是默认的行不展示该按钮）', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('内网小模型')).toBeInTheDocument());
    post.mockResolvedValue({} as never);

    // 已是默认文本的 Magpie 行不提供该按钮
    expect(
      within(rowOf('Magpie 网关')).queryByRole('button', { name: cn('默认文本') }),
    ).toBeNull();

    fireEvent.click(within(rowOf('内网小模型')).getByRole('button', { name: cn('默认文本') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/ai-providers/2/default-text'));
  });

  it('设默认多模态：POST /scrm/ai-providers/{id}/default-image', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('内网小模型')).toBeInTheDocument());
    post.mockResolvedValue({} as never);

    fireEvent.click(within(rowOf('内网小模型')).getByRole('button', { name: cn('默认多模态') }));

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scrm/ai-providers/2/default-image'));
  });

  it('默认模型：拉取过清单的 provider 编辑时出现候选（多模态/文本标注），且保留当前值回填', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('内网小模型')).toBeInTheDocument());

    // 打开「内网小模型」编辑弹窗（该行无 defaultModel，但有 models 清单 → 自动补全有候选）
    fireEvent.click(within(rowOf('内网小模型')).getByRole('button', { name: 'edit' }));

    const dlg = await dialog();
    const input = within(dlg).getByLabelText('默认模型') as HTMLInputElement;
    expect(input).toBeTruthy();
    expect(input.value).toBe('');
    // 点开下拉出现候选：qwen2-vl（多模态）、qwen2（文本）
    fireEvent.mouseDown(input);
    await waitFor(() =>
      expect(screen.getByText('qwen2-vl（多模态）')).toBeInTheDocument(),
    );
    expect(screen.getByText('qwen2（文本）')).toBeInTheDocument();

    // 关闭弹窗，改开 Magpie（defaultModel 已有值 → 回填 group/sensenova-pool）
    fireEvent.keyDown(input, { key: 'Escape' });
    fireEvent.click(document.querySelector('.ant-modal-close') as HTMLElement);
    fireEvent.click(within(rowOf('Magpie 网关')).getByRole('button', { name: 'edit' }));

    const dlg2 = await dialog();
    const magpieInput = within(dlg2).getByLabelText('默认模型') as HTMLInputElement;
    await waitFor(() => expect(magpieInput.value).toBe('group/sensenova-pool'));
    // Magpie 也拉取过清单（models 有值）→ extra 显示可用数量
    expect(within(dlg2).getByText(/可用/)).toBeTruthy();
  });

  it('启停开关：POST /scrm/ai-providers/{id}/enabled?enabled=false', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('Magpie 网关')).toBeInTheDocument());
    post.mockResolvedValue({} as never);

    const sw = within(rowOf('Magpie 网关')).getByRole('switch');
    expect(sw).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(sw);

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/scrm/ai-providers/1/enabled?enabled=false'),
    );
  });

  it('加载失败时表格为空且不抛错（apiClient 已提示错误）', async () => {
    get.mockRejectedValue(new Error('boom') as never);
    renderWithProviders(<AiProvidersPanel />);

    await waitFor(() => expect(screen.getByText('AI Provider 配置')).toBeInTheDocument());
    // 空态：无数据行
    await waitFor(() => expect(document.querySelectorAll('tbody tr.ant-table-row').length).toBe(0));
  });
});
