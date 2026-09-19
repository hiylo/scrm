/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : csv.test.ts
 * Description : CSV 解析纯函数测试 (单行切分 / 引号转义 / BOM / 表头映射 / 文件读取)
 */

import { describe, it, expect, afterEach, vi } from 'vitest';
import { parseCsv, parseCsvLine, parseCsvText, type ParsedRow } from './csv';

/** UTF-8 BOM (U+FEFF) — 用 charCode 构造, 避免测试源码里出现不可见字符 */
const BOM = String.fromCharCode(0xfeff);

/** 客户导入模板的表头 (与页面下载模板保持一致) */
const TEMPLATE_HEADER = '昵称,平台,平台客户ID,备注';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('parseCsvLine', () => {
  it('按逗号切分普通字段', () => {
    expect(parseCsvLine('张三,wework,wwid_1')).toEqual(['张三', 'wework', 'wwid_1']);
  });

  it('保留引号内的逗号', () => {
    expect(parseCsvLine('"VIP,重点客户",张三')).toEqual(['VIP,重点客户', '张三']);
  });

  it('把连续两个双引号还原为一个字面双引号', () => {
    expect(parseCsvLine('"他说""你好""",备注')).toEqual(['他说"你好"', '备注']);
  });

  it('引号内的逗号按字面量保留, 不作为分隔符', () => {
    expect(parseCsvLine('"第一行, 第二行"')).toEqual(['第一行, 第二行']);
  });

  it('空字段与尾部空字段', () => {
    expect(parseCsvLine('a,,b,')).toEqual(['a', '', 'b', '']);
    expect(parseCsvLine('')).toEqual(['']);
  });

  it('未闭合的引号会吞掉后续分隔符 (按当前实现: 整行剩余内容并成一个字段)', () => {
    expect(parseCsvLine('"未闭合,x,y')).toEqual(['未闭合,x,y']);
  });

  it('字段首尾的引号本身不会进入值', () => {
    expect(parseCsvLine('"带引号"')).toEqual(['带引号']);
  });
});

describe('parseCsvText', () => {
  it('以首行为表头把数据行映射为 { 列名 → 值 }', () => {
    const rows = parseCsvText(`${TEMPLATE_HEADER}\n张三,wework,wwid_1,VIP\n李四,wework,wwid_2,意向`);

    expect(rows).toEqual([
      { 昵称: '张三', 平台: 'wework', 平台客户ID: 'wwid_1', 备注: 'VIP' },
      { 昵称: '李四', 平台: 'wework', 平台客户ID: 'wwid_2', 备注: '意向' },
    ]);
  });

  it('去除 UTF-8 BOM, 首个列名不带不可见字符', () => {
    const rows = parseCsvText(`${BOM}${TEMPLATE_HEADER}\n张三,wework,wwid_1,VIP`);

    expect(Object.keys(rows[0])).toEqual(['昵称', '平台', '平台客户ID', '备注']);
    expect(rows[0].昵称).toBe('张三');
  });

  it('跳过空行并容忍 CRLF 行尾与末尾换行', () => {
    const rows = parseCsvText(`${TEMPLATE_HEADER}\r\n\r\n张三,wework,wwid_1,VIP\r\n   \r\n`);

    expect(rows).toHaveLength(1);
    expect(rows[0]).toEqual({ 昵称: '张三', 平台: 'wework', 平台客户ID: 'wwid_1', 备注: 'VIP' });
  });

  it('缺失的列补为空字符串, 多余的列被丢弃', () => {
    const rows = parseCsvText(`${TEMPLATE_HEADER}\n张三,wework,wwid_1\n李四,wework,wwid_2,备注,额外列`);

    expect(rows[0]).toEqual({ 昵称: '张三', 平台: 'wework', 平台客户ID: 'wwid_1', 备注: '' });
    expect(rows[1]).toEqual({ 昵称: '李四', 平台: 'wework', 平台客户ID: 'wwid_2', 备注: '备注' });
    expect(rows[1]).not.toHaveProperty('额外列');
  });

  it('单元格值两端空白被 trim', () => {
    const rows = parseCsvText('昵称,平台\n  张三  ,\twework\t');

    expect(rows[0]).toEqual({ 昵称: '张三', 平台: 'wework' });
  });

  it('表头两端空白被 trim', () => {
    const rows = parseCsvText(' 昵称 , 平台 \n张三,wework');

    expect(rows[0]).toEqual({ 昵称: '张三', 平台: 'wework' });
  });

  it('引号内的逗号不影响列数', () => {
    const rows = parseCsvText(`${TEMPLATE_HEADER}\n"张三, 外号",wework,wwid_1,"VIP,重点客户"`);

    expect(rows[0]).toEqual({
      昵称: '张三, 外号',
      平台: 'wework',
      平台客户ID: 'wwid_1',
      备注: 'VIP,重点客户',
    });
  });

  it('只有表头或空内容时抛出提示 (页面 toast 文案来源)', () => {
    expect(() => parseCsvText(TEMPLATE_HEADER)).toThrow('CSV 文件至少需要表头和一行数据');
    expect(() => parseCsvText('')).toThrow('CSV 文件至少需要表头和一行数据');
    expect(() => parseCsvText(`${BOM}${TEMPLATE_HEADER}`)).toThrow(
      'CSV 文件至少需要表头和一行数据',
    );
  });
});

describe('parseCsv (File 读取)', () => {
  /** 构造一个 CSV File */
  function csvFile(content: string[]): File {
    return new File(content, 'customers.csv', { type: 'text/csv' });
  }

  it('读取文件文本并解析为行', async () => {
    const file = csvFile([`${BOM}${TEMPLATE_HEADER}\n张三,wework,wwid_1,VIP\n李四,wework,wwid_2,`]);

    const rows: ParsedRow[] = await parseCsv(file);

    expect(rows).toEqual([
      { 昵称: '张三', 平台: 'wework', 平台客户ID: 'wwid_1', 备注: 'VIP' },
      { 昵称: '李四', 平台: 'wework', 平台客户ID: 'wwid_2', 备注: '' },
    ]);
  });

  it('数据行缺失时以相同文案 reject', async () => {
    await expect(parseCsv(csvFile([TEMPLATE_HEADER]))).rejects.toThrow(
      'CSV 文件至少需要表头和一行数据',
    );
  });

  it('FileReader 报错时 reject 读取失败文案', async () => {
    class FailingFileReader {
      onload: ((e: ProgressEvent) => void) | null = null;
      onerror: (() => void) | null = null;

      readAsText(): void {
        this.onerror?.();
      }
    }
    vi.stubGlobal('FileReader', FailingFileReader);

    await expect(parseCsv(csvFile([`${TEMPLATE_HEADER}\n张三`]))).rejects.toThrow('文件读取失败');
  });
});
