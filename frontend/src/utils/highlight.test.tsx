/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : highlight.test.tsx
 * Description : highlightKeyword 纯函数直测 (从 Conversations 页面内联实现提取后的行为固化)
 *
 * 现状说明 (不是断言目标, 只是被如实记录):
 *   - 命中判定走正则 (gi), 但"是否为命中段"的二次判定走 toLowerCase 字符串比较, 两者口径并不完全一致;
 *   - text 或 keyword 任一为空串时直接返回原始 text 字符串, 而不是 React 节点数组。
 */

import { describe, it, expect, vi } from 'vitest';
import type { ReactElement, ReactNode } from 'react';
import { render } from '@testing-library/react';
import { highlightKeyword } from './highlight';

/** 把返回的 ReactNode 摊平成 { 文本, 是否被 <mark> 包裹 } 序列, 便于结构化断言 */
function segments(node: ReactNode): Array<{ text: string; marked: boolean }> {
  if (typeof node === 'string') {
    return [{ text: node, marked: false }];
  }
  return (node as ReactNode[]).map((child) => {
    if (typeof child === 'string') {
      return { text: child, marked: false };
    }
    const el = child as ReactElement<{ children?: string }>;
    return { text: String(el.props.children ?? ''), marked: el.type === 'mark' };
  });
}

/** 只取命中的片段文本 */
function marked(node: ReactNode): string[] {
  return segments(node)
    .filter((s) => s.marked)
    .map((s) => s.text);
}

/** 渲染后按 <mark> 元素断言 DOM */
function renderMarks(node: ReactNode) {
  const { container } = render(<span>{node}</span>);
  return {
    container,
    marks: Array.from(container.querySelectorAll('mark')),
    marksText: Array.from(container.querySelectorAll('mark')).map((m) => m.textContent ?? ''),
    text: container.textContent ?? '',
  };
}

describe('highlightKeyword 空值短路', () => {
  it('关键词为空串时原样返回 text 字符串本身 (不是数组)', () => {
    expect(highlightKeyword('报价单已发送', '')).toBe('报价单已发送');
    expect(segments(highlightKeyword('报价单已发送', ''))).toEqual([
      { text: '报价单已发送', marked: false },
    ]);
  });

  it('文本为空串时返回空串, 即使关键词非空', () => {
    expect(highlightKeyword('', '价格')).toBe('');
  });

  it('两者都为空时返回空串', () => {
    expect(highlightKeyword('', '')).toBe('');
  });
});

describe('highlightKeyword 无命中', () => {
  it('无命中时返回只含原文的数组, 不产生 <mark>', () => {
    const node = highlightKeyword('我们有三档方案', '价格');
    expect(Array.isArray(node)).toBe(true);
    expect(node).toEqual(['我们有三档方案']);
    const { marks, text } = renderMarks(node);
    expect(marks).toHaveLength(0);
    expect(text).toBe('我们有三档方案');
  });

  it('关键词比文本长时无命中, 原文完整保留', () => {
    expect(highlightKeyword('abc', 'abcdef')).toEqual(['abc']);
  });
});

describe('highlightKeyword 命中位置与多次命中', () => {
  it('命中在开头: 数组首元素是空串片段', () => {
    const node = highlightKeyword('价格已确认，请查收', '价格');
    expect(marked(node)).toEqual(['价格']);
    expect(segments(node)[0]).toEqual({ text: '', marked: false });
  });

  it('命中在中间: 前后文本片段完整保留', () => {
    const node = highlightKeyword('请问价格多少', '价格');
    expect(segments(node)).toEqual([
      { text: '请问', marked: false },
      { text: '价格', marked: true },
      { text: '多少', marked: false },
    ]);
  });

  it('命中在结尾: 数组末元素是空串片段', () => {
    const node = highlightKeyword('已发送报价', '报价');
    expect(marked(node)).toEqual(['报价']);
    const parts = segments(node);
    expect(parts[parts.length - 1]).toEqual({ text: '', marked: false });
  });

  it('多次命中按出现顺序全部包裹, 渲染后文本与原文完全一致', () => {
    const node = highlightKeyword('foo bar foo baz foo', 'foo');
    expect(marked(node)).toEqual(['foo', 'foo', 'foo']);
    const { container, marksText, text } = renderMarks(node);
    expect(marksText).toEqual(['foo', 'foo', 'foo']);
    expect(text).toBe('foo bar foo baz foo');
    expect(container.querySelector('mark')).not.toBeNull();
  });

  it('中文文本命中同样生效', () => {
    expect(marked(highlightKeyword('你好, 请问产品怎么收费?', '收费'))).toEqual(['收费']);
  });

  it('怪癖: 匹配不重叠且从左到右, "aa" 在 "aaa" 中只命中一次', () => {
    const node = highlightKeyword('aaa', 'aa');
    expect(marked(node)).toEqual(['aa']);
    expect(segments(node)).toEqual([
      { text: '', marked: false },
      { text: 'aa', marked: true },
      { text: 'a', marked: false },
    ]);
  });
});

describe('highlightKeyword 大小写敏感性 (现状: 完全不敏感)', () => {
  it('关键词小写命中文本中不同大小写的全部出现, 且 <mark> 内保留原始大小写', () => {
    const node = highlightKeyword('PRICE Price price', 'price');
    expect(marked(node)).toEqual(['PRICE', 'Price', 'price']);
    expect(renderMarks(node).text).toBe('PRICE Price price');
  });

  it('关键词大写也能命中小写文本', () => {
    expect(marked(highlightKeyword('price', 'PRICE'))).toEqual(['price']);
  });

  it('中英混排时字母大小写不影响命中', () => {
    expect(marked(highlightKeyword('价格 Price 已发', 'pRiCe'))).toEqual(['Price']);
  });

  it('怪癖: 正则折叠命中但 toLowerCase 不相等时, 该片段不会被标黄 (宁可漏标)', () => {
    // U+017F LATIN SMALL LETTER LONG S: 是否被 /s/gi 折叠由引擎决定,
    // 但无论哪种结果, 该字符的 toLowerCase 都不等于 "s", 因此最终不产生 <mark>。
    const node = highlightKeyword('ſ', 's');
    expect(marked(node)).toEqual([]);
    expect(renderMarks(node).text).toBe('ſ');
  });
});

describe('highlightKeyword 正则元字符按字面量处理', () => {
  it('"." 只匹配真正的点号, 不会通配任意字符', () => {
    expect(highlightKeyword('aXb', '.')).toEqual(['aXb']);
    expect(marked(highlightKeyword('a.b', '.'))).toEqual(['.']);
  });

  it('"a+b" 不会被当作量词, 只命中字面量', () => {
    const node = highlightKeyword('axb a+b', 'a+b');
    expect(marked(node)).toEqual(['a+b']);
  });

  it('"(x)" 括号按字面量, 不产生捕获组语义', () => {
    const node = highlightKeyword('x (x)', '(x)');
    expect(marked(node)).toEqual(['(x)']);
  });

  it('量词字面量 "a{2}" 不展开为 "aa"', () => {
    expect(highlightKeyword('aaa', 'a{2}')).toEqual(['aaa']);
  });

  it.each(['.', '*', '+', '?', '^', '$', '{', '}', '(', ')', '|', '[', ']', '\\'])(
    '元字符 %s 作为关键词时按字面量命中且不抛错',
    (ch) => {
      const text = `前${ch}后`;
      const run = () => highlightKeyword(text, ch);
      expect(run).not.toThrow();
      expect(marked(run())).toEqual([ch]);
      expect(renderMarks(run()).text).toBe(text);
    },
  );

  it('组合元字符关键词 "(a|b+)" 也按字面量命中', () => {
    expect(marked(highlightKeyword('x (a|b+) y', '(a|b+)'))).toEqual(['(a|b+)']);
  });

  it('未闭合方括号 "]" 等危险片段不会让 RegExp 构造失败', () => {
    expect(() => highlightKeyword('a]b[', ']')).not.toThrow();
    expect(marked(highlightKeyword('a]b[', ']'))).toEqual([']']);
  });
});

describe('highlightKeyword 渲染产物', () => {
  it('命中片段渲染为带内联样式的 <mark>', () => {
    const { marks } = renderMarks(highlightKeyword('价格已确认', '价格'));
    expect(marks).toHaveLength(1);
    expect(marks[0]).toHaveTextContent('价格');
    expect(marks[0].getAttribute('style')).toContain('background-color: rgb(255, 229, 143)');
    expect(marks[0].getAttribute('style')).toContain('padding: 0px');
  });

  it('怪癖: 纯空白关键词也是"真关键词" (只做 !keyword 判定), 空格会被标黄', () => {
    const node = highlightKeyword('a b', ' ');
    expect(marked(node)).toEqual([' ']);
    expect(renderMarks(node).text).toBe('a b');
  });

  it('返回的节点数组可直接被 React 渲染且不报 key 缺失警告', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    renderMarks(highlightKeyword('foo bar foo', 'foo'));
    expect(spy).not.toHaveBeenCalled();
    spy.mockRestore();
  });
});
