/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : chart.ts
 * Description : ECharts 图表数据整形工具 (纯函数, 与页面解耦便于直接单测)
 */

/** 饼图 / 环形图 series.data 单项 */
export interface PieSlice {
  value: number;
  name: string;
}

/**
 * 把后端返回的枚举分布 (Record<string, number>) 转成 ECharts 饼图 data 数组。
 *
 * 行为约定:
 * 1. labelMap 命中时把枚举键翻译为中文, 未命中 (或省略 labelMap) 时用键名本身作 label,
 *    因此永远不会产出 undefined;
 * 2. 条目顺序与 Object.entries 一致 —— 非数字键按插入顺序, 整数样式的键 (如 '2'、'10')
 *    由 JS 引擎按数值升序前置, 传入同一个对象多次结果稳定;
 * 3. 值为 0、负数或非数字的条目原样保留, 由调用方/echarts 决定如何呈现 (不做静默丢弃);
 * 4. record 为 null / undefined 时返回空数组, 调用方无需再写 `|| {}` 兜底;
 * 5. 不修改入参, 每次返回全新数组与全新条目对象。
 *
 * @param record 枚举 → 数量的分布, 允许 nullish
 * @param labelMap 枚举 → 中文标签映射, 可省略
 * @returns echarts series.data 所需的 { value, name } 数组
 */
export function recordToPieData(
  record: Record<string, number> | null | undefined,
  labelMap: Record<string, string> = {},
): PieSlice[] {
  if (!record) return [];
  return Object.entries(record).map(([key, value]) => ({
    value,
    name: labelMap[key] || key,
  }));
}
