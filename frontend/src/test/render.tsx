/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : render.tsx
 * Description : 页面测试的最小 provider 包壳
 *
 * 说明: StyleProvider 将 antd cssinjs 生成的样式注入到游离节点, 而不是 document.head。
 * jsdom 因此不会把约 460KB 的样式表解析进 CSSOM, 单次渲染与可访问性查询耗时下降一个数量级
 * (实测 getByRole 从 ~2800ms 降到 ~50ms), 且不改变组件树的任何行为。
 */

import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { App as AntApp, ConfigProvider } from 'antd';
import { StyleProvider } from '@ant-design/cssinjs';
import { MemoryRouter, useLocation } from 'react-router-dom';
import zhCN from 'antd/locale/zh_CN';

/** 展示当前落点路径, 用于断言页面内导航 */
export function PathProbe() {
  const location = useLocation();
  return <div data-testid="path">{`${location.pathname}${location.search}`}</div>;
}

/**
 * 以生产环境等价的最小 provider 组合渲染页面
 * @param ui 页面元素 (可包含 Routes/Route)
 * @param initialPath MemoryRouter 初始地址
 */
export function renderWithProviders(ui: ReactElement, initialPath = '/') {
  const styleContainer = document.createElement('div');
  return render(
    <StyleProvider container={styleContainer}>
      <ConfigProvider locale={zhCN}>
        <AntApp>
          <MemoryRouter initialEntries={[initialPath]}>{ui}</MemoryRouter>
        </AntApp>
      </ConfigProvider>
    </StyleProvider>,
  );
}
