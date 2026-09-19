/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : usePlatforms.test.ts
 * Description : 平台列表 Hook 测试
 */

import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { usePlatforms } from './usePlatforms';

describe('usePlatforms', () => {
  it('返回企业微信静态平台列表且不处于加载态', () => {
    const { result } = renderHook(() => usePlatforms());
    expect(result.current.loading).toBe(false);
    expect(result.current.platforms).toEqual([
      { platformType: 'WEWORK', displayName: '企业微信', available: true },
    ]);
  });

  it('多次渲染返回稳定的数组引用 (避免下游 useEffect 抖动)', () => {
    const { result, rerender } = renderHook(() => usePlatforms());
    const first = result.current.platforms;
    rerender();
    expect(result.current.platforms).toBe(first);
  });
});
