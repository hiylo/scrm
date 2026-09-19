/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ThemeContext.test.tsx
 * Description : 主题 Provider 的持久化与切换行为测试
 */

import { describe, it, expect } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, useTheme } from './ThemeContext';

/** 消费主题的探针组件 */
function Probe() {
  const { theme, toggleTheme } = useTheme();
  return (
    <div>
      <span data-testid="theme">{theme}</span>
      <button type="button" onClick={toggleTheme}>
        切换主题
      </button>
    </div>
  );
}

describe('ThemeProvider', () => {
  it('默认为亮色主题并写入 data-theme 属性', () => {
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(screen.getByTestId('theme')).toHaveTextContent('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(localStorage.getItem('scrm-theme')).toBe('light');
  });

  it('启动时恢复已保存的暗色主题', () => {
    localStorage.setItem('scrm-theme', 'dark');
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(screen.getByTestId('theme')).toHaveTextContent('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('存储值非法时回退到亮色', () => {
    localStorage.setItem('scrm-theme', 'neon');
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(screen.getByTestId('theme')).toHaveTextContent('light');
    expect(localStorage.getItem('scrm-theme')).toBe('light');
  });

  it('toggleTheme 在亮/暗之间来回切换并同步存储', async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole('button', { name: '切换主题' }));
    expect(screen.getByTestId('theme')).toHaveTextContent('dark');
    expect(localStorage.getItem('scrm-theme')).toBe('dark');

    await user.click(screen.getByRole('button', { name: '切换主题' }));
    expect(screen.getByTestId('theme')).toHaveTextContent('light');
    expect(localStorage.getItem('scrm-theme')).toBe('light');
  });

  it('Provider 外使用时返回安全的默认值', () => {
    function Bare() {
      const { theme, toggleTheme } = useTheme();
      act(() => toggleTheme());
      return <span data-testid="bare">{theme}</span>;
    }
    render(<Bare />);
    expect(screen.getByTestId('bare')).toHaveTextContent('light');
  });
});
