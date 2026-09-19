/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : friendlyTime.test.ts
 * Description : formatFriendlyTime 纯函数直测 (从 Conversations 页面内联实现提取后的行为固化)
 *
 * 说明: 实现依赖 dayjs() 读当前时间与本地时区, 因此用 vi.useFakeTimers() + vi.setSystemTime()
 * 把"现在"钉死; 输入统一用无偏移量的本地时间字面量, 使断言不随进程 TZ 漂移 (个别用例显式验证时区假设)。
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import dayjs from 'dayjs';
import { formatFriendlyTime } from './friendlyTime';

/** 钉死的"现在": 本地时间 2026-09-12 12:00:00 */
const NOW = '2026-09-12T12:00:00';

/** 相对钉死的"现在"生成一个时间字符串, 便于表达 "N 天前" 这类语义 */
function at(offset: { day?: number; hour?: number; minute?: number; second?: number }): string {
  return dayjs(NOW)
    .subtract(offset.day ?? 0, 'day')
    .subtract(offset.hour ?? 0, 'hour')
    .subtract(offset.minute ?? 0, 'minute')
    .subtract(offset.second ?? 0, 'second')
    .format('YYYY-MM-DDTHH:mm:ss');
}

beforeEach(() => {
  vi.useFakeTimers();
  // dayjs(NOW) 按本地时区解析 → 任意 TZ 下"现在"的本地钟点都是 12:00:00
  vi.setSystemTime(dayjs(NOW).valueOf());
});

describe('formatFriendlyTime 空值与非法输入', () => {
  it('null / undefined / 空串一律返回占位符 "-"', () => {
    expect(formatFriendlyTime(null)).toBe('-');
    expect(formatFriendlyTime(undefined)).toBe('-');
    expect(formatFriendlyTime('')).toBe('-');
  });

  it('无法解析的字符串 (dayjs 判定 Invalid Date) 返回 "-"', () => {
    expect(formatFriendlyTime('not-a-date')).toBe('-');
    expect(formatFriendlyTime('abc')).toBe('-');
    expect(formatFriendlyTime('   ')).toBe('-');
    expect(formatFriendlyTime('-')).toBe('-');
    expect(formatFriendlyTime('null')).toBe('-');
  });

  it('怪癖: 越界日期不报错, 按 JS Date 语义溢出到下一天', () => {
    // 2 月没有 30 号 / 31 号, dayjs 视为合法并溢出为 3 月 2 日
    expect(formatFriendlyTime('2026-02-30T00:00:00')).toBe('03-02 00:00');
    expect(formatFriendlyTime('2024-02-31T00:00:00')).toBe('2024-03-02');
    // 13 月溢出为次年 1 月, 结果是"未来"→ 落入 "刚刚" 分支
    expect(formatFriendlyTime('2026-13-01T00:00:00')).toBe('刚刚');
  });

  it('怪癖: 纯数字字符串不被当作毫秒时间戳, 而按日期片段解析', () => {
    const iso = '1757000000000';
    // 期望值由 dayjs 自身推导, 因此断言与进程 TZ 无关
    expect(formatFriendlyTime(iso)).toBe(dayjs(iso).format('YYYY-MM-DD'));
    // 若被当作 epoch(1757000000000ms) 解析会得到 2025-09-05, 现状并非如此
    expect(formatFriendlyTime(iso)).not.toBe('2025-09-05');
  });
});

describe('formatFriendlyTime 秒级与分钟级', () => {
  it('当前时刻 → "刚刚"', () => {
    expect(formatFriendlyTime(NOW)).toBe('刚刚');
  });

  it('59 秒内 → "刚刚"', () => {
    expect(formatFriendlyTime(at({ second: 1 }))).toBe('刚刚');
    expect(formatFriendlyTime(at({ second: 59 }))).toBe('刚刚');
  });

  it('恰好 60 秒 → "1分钟前" (秒级判定用 < 60, 截断后进入分钟分支)', () => {
    expect(formatFriendlyTime(at({ second: 60 }))).toBe('1分钟前');
  });

  it('1 分钟 ~ 59 分钟 → "N分钟前"', () => {
    expect(formatFriendlyTime(at({ minute: 1, second: 1 }))).toBe('1分钟前');
    expect(formatFriendlyTime(at({ minute: 5 }))).toBe('5分钟前');
    expect(formatFriendlyTime(at({ minute: 59, second: 59 }))).toBe('59分钟前');
  });
});

describe('formatFriendlyTime 小时级', () => {
  it('恰好 1 小时 → "1小时前"', () => {
    expect(formatFriendlyTime(at({ hour: 1 }))).toBe('1小时前');
  });

  it('23 小时 59 分 → "23小时前" (仍按满小时数展示)', () => {
    expect(formatFriendlyTime(at({ hour: 23, minute: 59 }))).toBe('23小时前');
  });

  it('恰好 24 小时 → 不再走 "24小时前", 因当天是昨天而落到 "昨天 HH:mm"', () => {
    expect(formatFriendlyTime(at({ day: 1 }))).toBe('昨天 12:00');
  });
});

describe('formatFriendlyTime 昨天分支', () => {
  it('昨天且已满 24 小时的时间点展示为 "昨天 HH:mm"', () => {
    expect(formatFriendlyTime('2026-09-11T00:00:00')).toBe('昨天 00:00');
    expect(formatFriendlyTime('2026-09-11T11:59:00')).toBe('昨天 11:59');
    expect(formatFriendlyTime('2026-09-11T12:00:00')).toBe('昨天 12:00');
  });

  it('怪癖: 昨天但不足 24 小时 (12:00 之后) 不会显示 "昨天", 而是 "N小时前"', () => {
    expect(formatFriendlyTime('2026-09-11T12:00:01')).toBe('23小时前');
    expect(formatFriendlyTime('2026-09-11T23:59:00')).toBe('12小时前');
  });

  it('跨零点: 距"现在"超过 24 小时的昨天用 "昨天", 更早的昨天用 "N天前"', () => {
    vi.setSystemTime(dayjs('2026-09-12T00:30:00').valueOf());
    // 24h30m 前, 且日历上是昨天 → 昨天分支
    expect(formatFriendlyTime('2026-09-11T00:00:00')).toBe('昨天 00:00');
    // 23h30m 前 → 小时分支
    expect(formatFriendlyTime('2026-09-11T01:00:00')).toBe('23小时前');
    // 25h30m 前, 日历上是前天 → "N天前" (diffDay 为 1)
    expect(formatFriendlyTime('2026-09-10T23:00:00')).toBe('1天前');
  });
});

describe('formatFriendlyTime 一周内分支', () => {
  it('非昨天的 2~6 天 → "N天前"', () => {
    expect(formatFriendlyTime(at({ day: 2 }))).toBe('2天前');
    expect(formatFriendlyTime(at({ day: 3 }))).toBe('3天前');
    expect(formatFriendlyTime(at({ day: 5 }))).toBe('5天前');
    expect(formatFriendlyTime(at({ day: 6 }))).toBe('6天前');
  });

  it('边界: 正好 7 天 (不含) 起退出 "N天前", 改为 "MM-DD HH:mm"', () => {
    expect(formatFriendlyTime(at({ day: 7, second: 1 }))).toBe('09-05 11:59');
    expect(formatFriendlyTime(at({ day: 7 }))).toBe('09-05 12:00');
  });
});

describe('formatFriendlyTime 年内与跨年分支', () => {
  it('更早但未满一年 (按周年差) → "MM-DD HH:mm"', () => {
    expect(formatFriendlyTime('2026-09-01T08:30:00')).toBe('09-01 08:30');
    expect(formatFriendlyTime('2026-01-05T10:00:00')).toBe('01-05 10:00');
  });

  it('怪癖: 日历年已跨年但周年差仍不足一年 → 输出不带年份', () => {
    // 注释写的是 "当年 → MM-DD HH:mm", 实际判定是 diffYear === 0 (按周年而非日历年)
    expect(formatFriendlyTime('2025-12-20T09:00:00')).toBe('12-20 09:00');
  });

  it('满一年起 → "YYYY-MM-DD"; 恰好 1 年即触发', () => {
    expect(formatFriendlyTime('2025-09-12T12:00:00')).toBe('2025-09-12');
    expect(formatFriendlyTime('2025-01-01T00:00:00')).toBe('2025-01-01');
    expect(formatFriendlyTime('2020-03-04T05:06:00')).toBe('2020-03-04');
  });
});

describe('formatFriendlyTime 未来时间', () => {
  it('怪癖: 任何晚于"现在"的时间都落入 diffSec < 60 分支, 展示 "刚刚"', () => {
    expect(formatFriendlyTime('2026-09-12T12:00:30')).toBe('刚刚');
    expect(formatFriendlyTime('2026-09-13T12:00:00')).toBe('刚刚');
    expect(formatFriendlyTime('2099-01-01T00:00:00')).toBe('刚刚');
  });
});

describe('formatFriendlyTime 本地时区假设', () => {
  it('无偏移量的字符串按本地时间解析并原样格式化 (不走 UTC)', () => {
    expect(formatFriendlyTime('2025-06-15T08:30:00')).toBe('2025-06-15');
  });

  it('带 Z 的 ISO 先换算到本地时区再格式化, 结果随进程 TZ 变化 (故期望值由 dayjs 本地格式化推导)', () => {
    const iso = '2026-03-01T00:00:00Z';
    // UTC 下为 "03-01 00:00", UTC+8 下为 "03-01 08:00" —— 实现没有 utc 插件, 始终按本地展示
    expect(formatFriendlyTime(iso)).toBe(dayjs(iso).format('MM-DD HH:mm'));
  });

  it('周年差按"日"对齐, 不是精确时长: 差 1 秒满周年即已算作 1 年', () => {
    // 2025-09-13 距"现在"约 364 天 → diffYear 0 → 仍走 MM-DD
    expect(formatFriendlyTime('2025-09-13T00:00:00')).toBe('09-13 00:00');
    // 2025-09-12 11:59:59 距"现在"不足整一年 (差 1 秒) → diffYear 已为 1 → YYYY-MM-DD
    expect(formatFriendlyTime('2025-09-12T11:59:59')).toBe('2025-09-12');
    expect(formatFriendlyTime('2025-09-12T12:00:00')).toBe('2025-09-12');
  });
});