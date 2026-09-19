/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ThemeContext.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { createContext, useContext, useEffect, useState, ReactNode } from 'react';

/** 主题类型 */
type Theme = 'light' | 'dark';

/** 主题上下文值 */
interface ThemeContextValue {
  theme: Theme;
  toggleTheme: () => void;
}

/** localStorage 存储键, 加 scrm 前缀避免与同域部署的其他应用互相覆盖 */
const STORAGE_KEY = 'scrm-theme';

const ThemeContext = createContext<ThemeContextValue>({
  theme: 'light',
  toggleTheme: () => {},
});

/**
 * 主题 Provider 组件
 * 从 localStorage 读取已保存的主题, 默认亮色
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(() => {
    const saved = localStorage.getItem(STORAGE_KEY) as Theme | null;
    return saved === 'light' || saved === 'dark' ? saved : 'light';
  });

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, theme);
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(prev => (prev === 'light' ? 'dark' : 'light'));
  };

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

/** 获取当前主题和切换方法 */
export function useTheme() {
  return useContext(ThemeContext);
}
