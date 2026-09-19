/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ProtectedRoute.test.tsx
 * Description : 路由守卫的重定向与放行行为测试
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import { expiredToken, makeJwt, validToken } from '../test/fixtures';

/** 展示当前路由信息, 用于断言重定向携带的 state */
function LocationProbe() {
  const location = useLocation();
  return (
    <div data-testid="location">
      {`${location.pathname}|${JSON.stringify(location.state ?? null)}`}
    </div>
  );
}

/** 渲染一段带守卫的路由 */
function renderGuarded(fromPath = '/customers') {
  return render(
    <MemoryRouter initialEntries={[fromPath]}>
      <Routes>
        <Route
          path="/customers"
          element={
            <ProtectedRoute>
              <div>客户页面</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  it('持有未过期令牌时渲染受保护内容', () => {
    localStorage.setItem('scrm_token', validToken());
    renderGuarded();
    expect(screen.getByText('客户页面')).toBeInTheDocument();
    expect(screen.queryByText('客户页面')).not.toHaveTextContent('登录');
  });

  it('无令牌时重定向到 /login', () => {
    renderGuarded();
    expect(screen.queryByText('客户页面')).not.toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
  });

  it('令牌已过期时重定向到 /login', () => {
    localStorage.setItem('scrm_token', expiredToken());
    renderGuarded();
    expect(screen.queryByText('客户页面')).not.toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
  });

  it('重定向时通过 state.from 携带原始位置以便登录后回跳', () => {
    renderGuarded('/customers');
    const probe = screen.getByTestId('location');
    // state.from 为 Location 对象, 断言其中包含被拦截的路径
    expect(probe.textContent).toContain('"pathname":"/customers"');
  });

  it('无法解析的令牌同样视为未登录', () => {
    localStorage.setItem('scrm_token', makeJwt({ sub: 'no-exp' }));
    renderGuarded();
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
  });
});
