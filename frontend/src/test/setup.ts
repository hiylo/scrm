/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : setup.ts
 * Description : 测试全局初始化: jest-dom 匹配器、antd 所需的浏览器 API 垫片、用例间清理
 */

import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

/** antd 5 组件依赖 matchMedia, jsdom 未实现, 提供一个恒不匹配的桩 */
if (!window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList;
}

/** antd Table / List 等组件使用 ResizeObserver, jsdom 未实现 */
if (!('ResizeObserver' in globalThis)) {
  class ResizeObserverStub {
    observe(): void {
      /* noop */
    }
    unobserve(): void {
      /* noop */
    }
    disconnect(): void {
      /* noop */
    }
  }
  // 直接赋值而非 vi.stubGlobal: 防止 afterEach 的 unstubAllGlobals() 在测试间隙撤销,
  // 导致 ReactFlow 异步挂载的回调 (commit 阶段) 因 ResizeObserver undefined 抛错。
  globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;
}

/**
 * ReactFlow(@xyflow/react) 依赖 getComputedStyle(elt, pseudoElt), jsdom 未实现带
 * 伪元素参数的重载 → 抛 "Not implemented"。垫片忽略伪元素参数返回单参结果。
 */
const originalGetComputedStyle = window.getComputedStyle.bind(window);
window.getComputedStyle = ((elt: Element, pseudoElt?: string | null): CSSStyleDeclaration => {
  void pseudoElt;
  return originalGetComputedStyle(elt);
}) as typeof window.getComputedStyle;

/** jsdom 未实现 scrollIntoView / createObjectURL */
Element.prototype.scrollIntoView = Element.prototype.scrollIntoView || (() => undefined);
if (!window.URL || !window.URL.createObjectURL) {
  vi.stubGlobal('URL', Object.assign(globalThis.URL, {
    createObjectURL: vi.fn(() => 'blob:mock-url'),
    revokeObjectURL: vi.fn(),
  }));
}

/** 每个用例从干净的 localStorage 开始, 避免鉴权状态互相污染 */
beforeEach(() => {
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
