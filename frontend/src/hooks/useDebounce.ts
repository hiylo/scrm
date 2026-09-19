/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : useDebounce.ts
 * Date : 2026/07/26
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
import { useEffect, useState } from 'react';

/**
 * 防抖 hook, 延迟更新值
 * @param value 原始值
 * @param delay 延迟毫秒 (默认 300ms)
 * @returns 防抖后的值
 */
export function useDebounce<T>(value: T, delay: number = 300): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);
  useEffect(() => {
    const handler = setTimeout(() => setDebouncedValue(value), delay);
    return () => clearTimeout(handler);
  }, [value, delay]);
  return debouncedValue;
}
