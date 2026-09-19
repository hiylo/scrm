/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : csv.ts
 * Date : 2026-09-19
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

/**
 * 客户 CSV 导入的解析逻辑 (从 pages/Customers.tsx 下沉而来)
 * <p>
 * 页面组件只负责交互与状态, 纯解析函数放在这里以便脱离 React 直接单测。
 * 解析行为与原实现完全一致: 支持 RFC4180 风格的引号包裹与 "" 转义、剔除 UTF-8 BOM、
 * 跳过空行、以首行为表头把每行映射成 { 列名 → 值 }。
 */

/** CSV 解析后的行数据 (列名 → 值) */
export type ParsedRow = Record<string, string>;

/**
 * 解析单行 CSV (处理引号包裹与双引号转义)
 * @param line 一行原始文本
 * @returns 该行的单元格数组 (保留引号内的逗号)
 */
export function parseCsvLine(line: string): string[] {
  const result: string[] = [];
  let current = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (char === '"') {
      // 双引号转义: 引号内连续两个双引号表示一个字面双引号
      if (inQuotes && line[i + 1] === '"') {
        current += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (char === ',' && !inQuotes) {
      result.push(current);
      current = '';
    } else {
      current += char;
    }
  }
  result.push(current);
  return result;
}

/** UTF-8 BOM (U+FEFF), Excel 导出的 CSV 常带该前缀 */
const BOM = '\uFEFF';

/**
 * 解析 CSV 文本, 返回行数据 (首行作为表头, 映射为列名 → 值)
 * @param text CSV 原始文本 (可含 BOM, 行尾可为 \n 或 \r\n)
 * @returns 每行一个 ParsedRow
 * @throws 当缺少表头或数据行时抛出 'CSV 文件至少需要表头和一行数据'
 */
export function parseCsvText(text: string): ParsedRow[] {
  // 移除 BOM 头, 避免 Excel 保存的 UTF-8 文件首个列名带不可见字符
  const content = text.startsWith(BOM) ? text.slice(BOM.length) : text;
  const lines = content.split('\n').filter((l) => l.trim());
  if (lines.length < 2) {
    throw new Error('CSV 文件至少需要表头和一行数据');
  }
  const headers = parseCsvLine(lines[0]).map((h) => h.trim());
  return lines.slice(1).map((line) => {
    const values = parseCsvLine(line);
    const row: ParsedRow = {};
    // 缺列时补空串, 保证下游按列名取值时不会拿到 undefined
    headers.forEach((h, i) => {
      row[h] = (values[i] || '').trim();
    });
    return row;
  });
}

/**
 * 读取并解析 CSV 文件 (浏览器 File API 包装 parseCsvText)
 * @param file 用户选择的 CSV 文件
 * @returns 每行一个 ParsedRow
 */
export function parseCsv(file: File): Promise<ParsedRow[]> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        resolve(parseCsvText(e.target?.result as string));
      } catch (err) {
        reject(err);
      }
    };
    reader.onerror = () => reject(new Error('文件读取失败'));
    reader.readAsText(file, 'UTF-8');
  });
}
