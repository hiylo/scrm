/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : friendlyTime.ts
 * Description : 会话列表用的"友好时间"格式化 (从 pages/Conversations.tsx 原样提取, 行为未做任何修改)
 */

import dayjs from 'dayjs';

/**
 * 格式化为友好时间显示 (会话列表用)
 * 1分钟内 → "刚刚"
 * 1小时内 → "X分钟前"
 * 24小时内 → "X小时前"
 * 昨天 → "昨天 HH:mm"
 * 7天内 → "X天前"
 * 当年 → "MM-DD HH:mm"
 * 跨年 → "YYYY-MM-DD"
 *
 * @param time ISO 时间字符串或 dayjs 对象
 * @returns 友好时间文本
 */
export const formatFriendlyTime = (time: string | undefined | null): string => {
  if (!time) return '-';
  const t = dayjs(time);
  if (!t.isValid()) return '-';
  const now = dayjs();
  const diffSec = now.diff(t, 'second');
  const diffMin = now.diff(t, 'minute');
  const diffHour = now.diff(t, 'hour');
  const diffDay = now.diff(t, 'day');
  const diffYear = now.diff(t, 'year');
  if (diffSec < 60) return '刚刚';
  if (diffMin < 60) return `${diffMin}分钟前`;
  if (diffHour < 24) return `${diffHour}小时前`;
  // 判断是否是昨天 (比较日期部分)
  if (t.format('YYYY-MM-DD') === now.subtract(1, 'day').format('YYYY-MM-DD')) {
    return `昨天 ${t.format('HH:mm')}`;
  }
  if (diffDay < 7) return `${diffDay}天前`;
  if (diffYear === 0) return t.format('MM-DD HH:mm');
  return t.format('YYYY-MM-DD');
};
