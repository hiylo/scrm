/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : chart.test.ts
 * Description : recordToPieData 纯函数单测 (空值 / 翻译兜底 / 键序稳定 / 异常值)
 *
 * 说明: 此前该函数内联在 Dashboard.tsx 中且未导出, 只能通过整页渲染间接覆盖;
 * 下沉到 utils 后直接断言返回值, 不再依赖 antd / echarts 渲染。
 */

import { describe, it, expect } from 'vitest';
import { recordToPieData } from './chart';

describe('recordToPieData 空值与入参兜底', () => {
  it('空对象返回空数组', () => {
    expect(recordToPieData({})).toEqual([]);
    expect(recordToPieData({}, { LOGIN: '已登录' })).toEqual([]);
  });

  it('record 为 null / undefined 时返回空数组而不是抛错', () => {
    expect(recordToPieData(null)).toEqual([]);
    expect(recordToPieData(undefined)).toEqual([]);
    expect(recordToPieData(null, { wework: '企业微信' })).toEqual([]);
  });

  it('省略 labelMap 时键名本身作为 label', () => {
    expect(recordToPieData({ HIGH: 2 })).toEqual([{ value: 2, name: 'HIGH' }]);
  });

  it('不修改入参且每次返回全新对象', () => {
    const record = { LOGIN: 9 };
    const labelMap = { LOGIN: '已登录' };
    const first = recordToPieData(record, labelMap);
    const second = recordToPieData(record, labelMap);

    expect(record).toEqual({ LOGIN: 9 });
    expect(labelMap).toEqual({ LOGIN: '已登录' });
    expect(second).toEqual(first);
    expect(second).not.toBe(first);
    expect(second[0]).not.toBe(first[0]);
  });
});

describe('recordToPieData 标签翻译', () => {
  it('命中映射翻译为中文, 未命中的键原样展示而不是 undefined', () => {
    const result = recordToPieData(
      { wework: 12, dingtalk: 2, LOGIN: 1 },
      { wework: '企业微信', LOGIN: '已登录' },
    );
    expect(result).toEqual([
      { value: 12, name: '企业微信' },
      { value: 2, name: 'dingtalk' },
      { value: 1, name: '已登录' },
    ]);
    expect(result.every((slice) => slice.name !== 'undefined')).toBe(true);
  });

  it('映射值为空串时回退键名 (|| 兜底而非 ??)', () => {
    expect(recordToPieData({ NEW: 3 }, { NEW: '' })).toEqual([{ value: 3, name: 'NEW' }]);
  });

  it('每条只产出 value / name 两个字段, 不夹带 itemStyle 等副作用', () => {
    expect(Object.keys(recordToPieData({ LOW: 1 })[0])).toEqual(['value', 'name']);
  });
});

describe('recordToPieData 顺序与稳定性', () => {
  it('非数字键保持对象插入顺序', () => {
    const result = recordToPieData({ FREQUENCY: 5, CONTENT: 4, BEHAVIOR: 1 });
    expect(result.map((slice) => slice.name)).toEqual(['FREQUENCY', 'CONTENT', 'BEHAVIOR']);
  });

  it('重复调用同一对象得到相同的键序 (图例与坐标轴依赖该稳定性)', () => {
    const record = { ACTIVE: 80, NEW: 30, CHURNED: 18 };
    const names = recordToPieData(record).map((slice) => slice.name);
    expect(recordToPieData(record).map((slice) => slice.name)).toEqual(names);
    expect(names).toEqual(['ACTIVE', 'NEW', 'CHURNED']);
  });

  it('整数样式的键会被引擎按数值升序前置 (已知 JS 行为, 与 Object.entries 一致)', () => {
    expect(recordToPieData({ 10: 1, 2: 2 }).map((slice) => slice.name)).toEqual(['2', '10']);
  });
});

describe('recordToPieData 异常值', () => {
  it('值为 0 的条目保留 (饼图不画扇区但图例与合计仍需要它)', () => {
    expect(recordToPieData({ CRITICAL: 0, HIGH: 2 })).toEqual([
      { value: 0, name: 'CRITICAL' },
      { value: 2, name: 'HIGH' },
    ]);
  });

  it('负值与非数字值原样透传, 不做静默过滤', () => {
    const record = { NEG: -3, NAN: NaN, UNSET: undefined } as unknown as Record<string, number>;
    expect(recordToPieData(record)).toEqual([
      { value: -3, name: 'NEG' },
      { value: NaN, name: 'NAN' },
      { value: undefined, name: 'UNSET' },
    ]);
  });

  it('键名含特殊字符时仍原样作为 label, 不做转义', () => {
    expect(recordToPieData({ 'a<b > c': 1 })).toEqual([{ value: 1, name: 'a<b > c' }]);
  });
});
