/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : useDebounce.test.ts
 * Description : 防抖 Hook 的延时更新与清理行为测试
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useDebounce } from './useDebounce';

beforeEach(() => {
  vi.useFakeTimers();
});

describe('useDebounce', () => {
  it('初始值立即返回, 未被防抖', () => {
    const { result } = renderHook(() => useDebounce('a', 300));
    expect(result.current).toBe('a');
  });

  it('到达延时后更新为最新值', () => {
    const { result, rerender } = renderHook(({ value }) => useDebounce(value, 300), {
      initialProps: { value: 'a' },
    });

    rerender({ value: 'b' });
    expect(result.current).toBe('a');

    act(() => {
      vi.advanceTimersByTime(299);
    });
    expect(result.current).toBe('a');

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current).toBe('b');
  });

  it('延时内的连续变更只保留最后一次值', () => {
    const { result, rerender } = renderHook(({ value }) => useDebounce(value, 500), {
      initialProps: { value: '' },
    });

    ['x', 'xy', 'xyz'].forEach((value) => rerender({ value }));

    act(() => {
      vi.advanceTimersByTime(500);
    });
    expect(result.current).toBe('xyz');
  });

  it('卸载后定时器被清理, 不再触发状态更新', () => {
    const { rerender, unmount } = renderHook(({ value }) => useDebounce(value, 300), {
      initialProps: { value: 'a' },
    });
    rerender({ value: 'b' });
    unmount();

    expect(() => {
      act(() => {
        vi.advanceTimersByTime(1000);
      });
    }).not.toThrow();
  });

  it('delay 缺省为 300ms', () => {
    const { result, rerender } = renderHook(({ value }) => useDebounce(value), {
      initialProps: { value: 1 },
    });
    rerender({ value: 2 });

    act(() => {
      vi.advanceTimersByTime(300);
    });
    expect(result.current).toBe(2);
  });

  it('delay 变化会按新延时重新计时', () => {
    const { result, rerender } = renderHook(
      ({ value, delay }) => useDebounce(value, delay),
      { initialProps: { value: 'a', delay: 100 } },
    );
    rerender({ value: 'b', delay: 800 });

    act(() => {
      vi.advanceTimersByTime(100);
    });
    expect(result.current).toBe('a');

    act(() => {
      vi.advanceTimersByTime(700);
    });
    expect(result.current).toBe('b');
  });
});
