/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : pageAssertions.test.ts
 * Description : 走查断言工具测试 - 确保「接口失败 / 接口返回HTML / 未渲染完」能被自动标出
 */

import { describe, it, expect } from 'vitest';

/**
 * 走查断言：从 scripts/ui-verify-probe.js 复制核心逻辑做独立验证。
 *
 * 之所以不直接 import 脚本文件：该脚本是浏览器上下文注入用的 IIFE 风格模块，
 * 只导出给 node require；这里验证的是判据本身，与 harness 解耦。
 */
interface ProbeResult {
  fails: Array<{ url: string; status: number }>;
  errors: Array<{ msg: string }>;
  htmlResponses: string[];
}

interface Snapshot {
  tables: number[];
  empty: string[];
  hasLoadFailText?: boolean;
}

interface Issue {
  level: string;
  code: string;
  message: string;
}

/**
 * 把走查结论转成可判定的问题列表。
 *
 * @param snap  页面结构快照
 * @param probe 探针采集到的失败
 * @returns 问题列表与是否通过
 */
function assertPage(snap: Snapshot, probe: ProbeResult): { ok: boolean; issues: Issue[] } {
  const issues: Issue[] = [];

  (probe.fails || []).forEach((f) => {
    issues.push({ level: 'P1', code: 'API_FAIL', message: `接口失败 ${f.status} ${f.url}` });
  });

  (probe.htmlResponses || []).forEach((u) => {
    issues.push({ level: 'P1', code: 'API_HTML', message: `接口返回 HTML，疑似路径不存在：${u}` });
  });

  (probe.errors || []).forEach((e) => {
    issues.push({ level: 'P2', code: 'JS_ERROR', message: `JS 错误：${e.msg}` });
  });

  if (snap.hasLoadFailText) {
    issues.push({ level: 'P1', code: 'LOAD_FAIL_TEXT', message: '页面出现「加载失败」文案' });
  }

  if (Array.isArray(snap.tables) && snap.tables.length > 0) {
    const noRows = snap.tables.every((n) => n <= 1);
    const noEmptyCopy = !(snap.empty && snap.empty.length > 0);
    if (noRows && noEmptyCopy) {
      issues.push({
        level: 'P2',
        code: 'AMBIGUOUS_LOADING',
        message: '表格无数据行且无空态文案：可能仍在渲染，需等待后重读',
      });
    }
  }

  return { ok: issues.length === 0, issues };
}

/** 空探针 */
const cleanProbe: ProbeResult = { fails: [], errors: [], htmlResponses: [] };

describe('走查断言 assertPage', () => {
  it('接口 4xx/5xx 必须报 P1，不能当作页面为空', () => {
    const r = assertPage(
      { tables: [2], empty: [] },
      { ...cleanProbe, fails: [{ url: '/scrm/data-dictionaries/list', status: 400 }] },
    );
    expect(r.ok).toBe(false);
    expect(r.issues.map((i) => i.code)).toContain('API_FAIL');
  });

  it('接口返回 HTML（路径不存在落到 SPA 兜底）必须报 P1', () => {
    const r = assertPage(
      { tables: [2], empty: [] },
      { ...cleanProbe, htmlResponses: ['/scrm/approvals/approvals/list'] },
    );
    expect(r.ok).toBe(false);
    expect(r.issues.map((i) => i.code)).toContain('API_HTML');
  });

  it('表格无数据行也无空态文案时标记为「可能未渲染完」，避免误判为缺陷', () => {
    const r = assertPage({ tables: [1], empty: [] }, cleanProbe);
    expect(r.ok).toBe(false);
    expect(r.issues.map((i) => i.code)).toContain('AMBIGUOUS_LOADING');
  });

  it('有数据行、无异常时应通过，不产生误报', () => {
    const r = assertPage({ tables: [5], empty: [] }, cleanProbe);
    expect(r.ok).toBe(true);
    expect(r.issues).toHaveLength(0);
  });

  it('空态文案齐全时应通过（有数据或明确空态都不算问题）', () => {
    const r = assertPage({ tables: [1], empty: ['暂无数据'] }, cleanProbe);
    expect(r.ok).toBe(true);
  });

  it('页面出现「加载失败」文案必须报 P1', () => {
    const r = assertPage({ tables: [3], empty: [], hasLoadFailText: true }, cleanProbe);
    expect(r.ok).toBe(false);
    expect(r.issues.map((i) => i.code)).toContain('LOAD_FAIL_TEXT');
  });
});
