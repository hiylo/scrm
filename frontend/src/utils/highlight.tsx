/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : highlight.tsx
 * Description : 搜索结果关键词高亮渲染 (从 pages/Conversations.tsx 原样提取, 行为未做任何修改)
 */

import type { ReactNode } from 'react';

/**
 * 高亮文本中的关键词 (大小写不敏感)
 * 用于消息搜索结果展示, 将匹配的关键词用 <mark> 包裹
 *
 * @param text     原始文本
 * @param keyword  搜索关键词
 * @returns React 节点, 关键词部分高亮显示
 */
export const highlightKeyword = (text: string, keyword: string): ReactNode => {
  if (!text || !keyword) return text;
  // 转义正则特殊字符, 防止注入
  const escaped = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const parts = text.split(new RegExp(`(${escaped})`, 'gi'));
  return parts.map((part, i) =>
    part.toLowerCase() === keyword.toLowerCase()
      ? <mark key={i} style={{ padding: 0, backgroundColor: '#ffe58f' }}>{part}</mark>
      : part,
  );
};
