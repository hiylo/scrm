/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : imeHelpers.test.ts
 * Description : IME 组合态判定纯函数测试
 */

import { describe, it, expect } from 'vitest';
import type { KeyboardEvent } from 'react';
import { isImeComposing } from './imeHelpers';

/** 构造最小 React KeyboardEvent 桩 */
function keyEvent(init: {
  isComposing?: boolean;
  keyCode?: number;
  key?: string;
}): KeyboardEvent {
  return {
    key: init.key ?? 'Enter',
    keyCode: init.keyCode ?? 13,
    nativeEvent: { isComposing: init.isComposing } as unknown as Event,
  } as unknown as KeyboardEvent;
}

describe('isImeComposing', () => {
  it('nativeEvent.isComposing 为 true 时判定为组合态', () => {
    expect(isImeComposing(keyEvent({ isComposing: true }))).toBe(true);
  });

  it('keyCode 229 (老浏览器组合态) 时判定为组合态', () => {
    expect(isImeComposing(keyEvent({ keyCode: 229 }))).toBe(true);
  });

  it('两个条件同时满足仍返回 true', () => {
    expect(isImeComposing(keyEvent({ isComposing: true, keyCode: 229 }))).toBe(true);
  });

  it('普通 Enter 事件返回 false', () => {
    expect(isImeComposing(keyEvent({ isComposing: false, keyCode: 13 }))).toBe(false);
  });

  it('isComposing 为 undefined 时返回 false (非严格 true)', () => {
    expect(isImeComposing(keyEvent({}))).toBe(false);
  });

  it('isComposing 为真值但非布尔 true 时返回 false (实现使用严格相等)', () => {
    expect(isImeComposing(keyEvent({ isComposing: 1 as unknown as boolean }))).toBe(false);
  });

  it('Backspace 等其它按键在组合态下同样被识别', () => {
    expect(isImeComposing(keyEvent({ key: 'Backspace', keyCode: 8, isComposing: true }))).toBe(
      true,
    );
    expect(isImeComposing(keyEvent({ key: 'Backspace', keyCode: 8 }))).toBe(false);
  });

  it('nativeEvent 缺失时抛出类型安全 (实现依赖可选链等价判断)', () => {
    const broken = { keyCode: 13 } as unknown as KeyboardEvent;
    expect(() => isImeComposing(broken)).toThrow();
  });
});
