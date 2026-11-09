/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : AccountingReports.tsx
 * Date : 2026/10/26 00:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Empty,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CheckCircleOutlined,
  LockOutlined,
  ReloadOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import {
  closePeriod,
  getBalanceSheet,
  getDetailLedger,
  getGeneralLedger,
  getIncomeStatement,
  getJournal,
  listAccountPeriods,
  listCompanies,
  listSubjects,
  reopenPeriod,
  type AccountPeriod,
  type AccountSubject,
  type BalanceSheet,
  type BalanceSheetRow,
  type DetailLedger,
  type DetailLedgerItem,
  type GeneralLedger,
  type GeneralLedgerRow,
  type IncomeStatement,
  type IncomeStatementRow,
  type Journal,
  type JournalItem,
} from '../api/finance';

/** 金额格式化 */
const money = (value?: number | null) =>
  value == null
    ? '-'
    : Number(value).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });

/** CSV 单元格转义（含逗号/引号/换行时用双引号包裹，内部引号翻倍） */
const csvEscape = (v: unknown): string => {
  const s = v == null ? '' : String(v);
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
};

/** 生成 CSV 文本（带 UTF-8 BOM，Excel 直接打开不乱码） */
const buildCsv = (
  headers: string[],
  rows: Array<Array<string | number | undefined | null>>,
): string => {
  const lines = [headers, ...rows].map((r) => r.map(csvEscape).join(','));
  return `\uFEFF${lines.join('\r\n')}`;
};

/** 触发浏览器下载 CSV 文件 */
const downloadCsv = (filename: string, content: string): void => {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
};

/**
 * 账务报表页（资产负债表 / 利润表 / 总账 / 明细账 / 序时账 + 结账）。
 * <p>
 * 与「记账凭证」页分开的原因：凭证页是录入视角，
 * 这里是<b>出数视角</b>——会计给客户交的就是这几张表，页面要能直接读给别人听。
 * </p>
 * <p>
 * 每个表都带自校验提示：
 * </p>
 * <ul>
 *   <li>资产负债表校验会计恒等式（资产 = 负债 + 所有者权益）；</li>
 *   <li>总账/序时账校验借贷合计相等；</li>
 *   <li>不平时高亮提示，避免会计把错表发给客户。</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function AccountingReports() {
  const { message, modal } = App.useApp();

  const [activeTab, setActiveTab] = useState('balanceSheet');

  // ============ 共享上下文 ============
  const [companyId, setCompanyId] = useState<string | undefined>();
  const [period, setPeriod] = useState<Dayjs>(dayjs());
  const [companyOptions, setCompanyOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [companyLoading, setCompanyLoading] = useState(false);
  const [periods, setPeriods] = useState<AccountPeriod[]>([]);

  // ============ 各表数据 ============
  const [balanceSheet, setBalanceSheet] = useState<BalanceSheet | null>(null);
  const [incomeStatement, setIncomeStatement] = useState<IncomeStatement | null>(null);
  const [generalLedger, setGeneralLedger] = useState<GeneralLedger | null>(null);
  const [detailLedger, setDetailLedger] = useState<DetailLedger | null>(null);
  const [journal, setJournal] = useState<Journal | null>(null);
  const [loading, setLoading] = useState(false);
  const [subjectCode, setSubjectCode] = useState<string | undefined>();
  const [subjects, setSubjects] = useState<AccountSubject[]>([]);

  const year = period.year();
  const month = period.month() + 1;

  /** 当前期间是否已结账 */
  const currentPeriod = useMemo(
    () => periods.find((p) => p.periodYear === year && p.periodMonth === month),
    [periods, year, month],
  );
  const closed = currentPeriod?.status === 'CLOSED';

  /** 加载企业 */
  const loadCompanies = useCallback(async (keyword?: string) => {
    setCompanyLoading(true);
    try {
      const result = await listCompanies({ keyword: keyword || undefined, page: 0, size: 30 });
      setCompanyOptions(
        (result.content || []).map((c) => ({
          value: c.id,
          label: c.creditCode ? `${c.companyName}（${c.creditCode}）` : c.companyName,
        })),
      );
    } catch {
      // client.ts 已统一提示
    } finally {
      setCompanyLoading(false);
    }
  }, []);

  /** 加载期间状态 + 科目 */
  const loadContext = useCallback(async () => {
    if (!companyId) {
      setPeriods([]);
      setSubjects([]);
      return;
    }
    try {
      const [ps, ss] = await Promise.all([listAccountPeriods(companyId), listSubjects(companyId)]);
      setPeriods(ps || []);
      setSubjects(ss || []);
      if (!subjectCode && ss && ss.length > 0) {
        setSubjectCode(ss[0].subjectCode);
      }
    } catch {
      // 已统一提示
    }
  }, [companyId, subjectCode]);

  /** 按当前 Tab 加载数据 */
  const loadReport = useCallback(async () => {
    if (!companyId) {
      setBalanceSheet(null);
      setIncomeStatement(null);
      setGeneralLedger(null);
      setDetailLedger(null);
      setJournal(null);
      return;
    }
    setLoading(true);
    try {
      if (activeTab === 'balanceSheet') {
        setBalanceSheet(await getBalanceSheet(companyId, year, month));
      } else if (activeTab === 'incomeStatement') {
        setIncomeStatement(await getIncomeStatement(companyId, year, month));
      } else if (activeTab === 'generalLedger') {
        setGeneralLedger(await getGeneralLedger(companyId, year, month));
      } else if (activeTab === 'detailLedger') {
        if (subjectCode) {
          setDetailLedger(await getDetailLedger(companyId, subjectCode, year, month));
        } else {
          setDetailLedger(null);
        }
      } else if (activeTab === 'journal') {
        setJournal(await getJournal(companyId, year, month));
      }
    } catch {
      // 已统一提示
    } finally {
      setLoading(false);
    }
  }, [companyId, activeTab, year, month, subjectCode]);

  useEffect(() => {
    void loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    void loadContext();
  }, [loadContext]);

  useEffect(() => {
    void loadReport();
  }, [loadReport]);

  /** 结账 */
  const handleClose = () => {
    if (!companyId) return;
    modal.confirm({
      title: `结账 ${year} 年 ${month} 月？`,
      content:
        '将自动生成「结转损益」凭证（把收入/费用结转到本年利润）并记账，随后本期封账。封账后不可再新增或修改凭证，如需补录须先反结账。',
      onOk: async () => {
        try {
          const result = await closePeriod(companyId, year, month);
          message.success(
            `${result.message}${result.closingVoucherLabel ? `（凭证 ${result.closingVoucherLabel}，结转利润 ${money(result.profit)}）` : ''}`,
          );
          await Promise.all([loadContext(), loadReport()]);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 反结账 */
  const handleReopen = () => {
    if (!companyId) return;
    modal.confirm({
      title: `反结账 ${year} 年 ${month} 月？`,
      content: '将作废该期的结转损益凭证并恢复未结账状态。结转凭证不会被删除，保留痕迹便于审计。',
      onOk: async () => {
        try {
          const result = await reopenPeriod(companyId, year, month);
          message.success(result.message);
          await Promise.all([loadContext(), loadReport()]);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 资产负债表行（左右并排两栏：资产 | 负债及权益） */
  const balanceRows = useMemo(() => {
    if (!balanceSheet) return [];
    const left = balanceSheet.assets;
    const right = [...balanceSheet.liabilities, ...balanceSheet.equities];
    const max = Math.max(left.length, right.length);
    const rows: Array<{
      key: string;
      leftName?: string;
      leftClosing?: number;
      leftOpening?: number;
      rightName?: string;
      rightClosing?: number;
      rightOpening?: number;
    }> = [];
    for (let i = 0; i < max; i += 1) {
      rows.push({
        key: String(i),
        leftName: left[i]?.name,
        leftClosing: left[i]?.closing,
        leftOpening: left[i]?.opening,
        rightName: right[i]?.name,
        rightClosing: right[i]?.closing,
        rightOpening: right[i]?.opening,
      });
    }
    return rows;
  }, [balanceSheet]);

  /** 导出资产负债表 CSV（左右两栏并排 + 合计行） */
  const exportBalanceSheet = () => {
    if (!balanceSheet) {
      message.warning('本期还没有报表数据，无法导出');
      return;
    }
    const rows: Array<Array<string | number | undefined | null>> = balanceRows.map((r) => [
      r.leftName,
      r.leftClosing,
      r.leftOpening,
      r.rightName,
      r.rightClosing,
      r.rightOpening,
    ]);
    rows.push([
      '资产合计',
      balanceSheet.totalAssetClosing,
      balanceSheet.totalAssetOpening,
      '负债和所有者权益合计',
      balanceSheet.totalLiabilityAndEquityClosing,
      balanceSheet.totalLiabilityAndEquityOpening,
    ]);
    downloadCsv(
      `资产负债表_${period.format('YYYYMM')}.csv`,
      buildCsv(['资产', '期末余额', '年初余额', '负债和所有者权益', '期末余额', '年初余额'], rows),
    );
    message.success('资产负债表已导出');
  };

  /** 导出利润表 CSV（项目 / 本期金额 / 本年累计） */
  const exportIncomeStatement = () => {
    const src = incomeStatement?.rows ?? [];
    if (src.length === 0) {
      message.warning('本期没有损益发生额，无法导出');
      return;
    }
    const rows: Array<Array<string | number | undefined | null>> = src.map((r) => [
      r.name,
      r.current,
      r.ytd,
    ]);
    downloadCsv(
      `利润表_${period.format('YYYYMM')}.csv`,
      buildCsv(['项目', '本期金额', '本年累计'], rows),
    );
    message.success('利润表已导出');
  };

  const balanceColumns: ColumnsType<(typeof balanceRows)[number]> = [
    { title: '资产', dataIndex: 'leftName', width: 180, render: (v: string) => v || '' },
    {
      title: '期末余额',
      dataIndex: 'leftClosing',
      width: 140,
      align: 'right',
      render: (v: number) => (v == null ? '' : money(v)),
    },
    {
      title: '年初余额',
      dataIndex: 'leftOpening',
      width: 140,
      align: 'right',
      render: (v: number) => (v == null ? '' : money(v)),
    },
    { title: '负债和所有者权益', dataIndex: 'rightName', width: 180, render: (v: string) => v || '' },
    {
      title: '期末余额',
      dataIndex: 'rightClosing',
      width: 140,
      align: 'right',
      render: (v: number) => (v == null ? '' : money(v)),
    },
    {
      title: '年初余额',
      dataIndex: 'rightOpening',
      width: 140,
      align: 'right',
      render: (v: number) => (v == null ? '' : money(v)),
    },
  ];

  const incomeColumns: ColumnsType<IncomeStatementRow> = [
    { title: '项目', dataIndex: 'name', ellipsis: true },
    {
      title: '本期金额',
      dataIndex: 'current',
      width: 160,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '本年累计',
      dataIndex: 'ytd',
      width: 160,
      align: 'right',
      render: (v: number) => money(v),
    },
  ];

  const generalColumns: ColumnsType<GeneralLedgerRow> = [
    { title: '科目编码', dataIndex: 'subjectCode', width: 110, fixed: 'left' },
    { title: '科目名称', dataIndex: 'subjectName', width: 150, fixed: 'left' },
    {
      title: '期初借方',
      dataIndex: 'openingDebit',
      width: 120,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '期初贷方',
      dataIndex: 'openingCredit',
      width: 120,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '本期借方',
      dataIndex: 'periodDebit',
      width: 120,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '本期贷方',
      dataIndex: 'periodCredit',
      width: 120,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '期末借方',
      dataIndex: 'closingDebit',
      width: 120,
      align: 'right',
      render: (v: number) => <strong>{money(v)}</strong>,
    },
    {
      title: '期末贷方',
      dataIndex: 'closingCredit',
      width: 120,
      align: 'right',
      render: (v: number) => <strong>{money(v)}</strong>,
    },
  ];

  const detailColumns: ColumnsType<DetailLedgerItem> = [
    { title: '日期', dataIndex: 'voucherDate', width: 110 },
    { title: '凭证号', dataIndex: 'voucherLabel', width: 100 },
    { title: '摘要', dataIndex: 'summary', ellipsis: true },
    { title: '科目', dataIndex: 'subjectName', width: 150, ellipsis: true },
    {
      title: '借方',
      dataIndex: 'debitAmount',
      width: 120,
      align: 'right',
      render: (v: number) => (v ? money(v) : ''),
    },
    {
      title: '贷方',
      dataIndex: 'creditAmount',
      width: 120,
      align: 'right',
      render: (v: number) => (v ? money(v) : ''),
    },
    {
      title: '余额',
      dataIndex: 'balance',
      width: 130,
      align: 'right',
      render: (v: number) => <strong>{money(v)}</strong>,
    },
    { title: '方向', dataIndex: 'balanceDirection', width: 70, align: 'center' },
  ];

  const journalColumns: ColumnsType<JournalItem> = [
    { title: '日期', dataIndex: 'voucherDate', width: 110 },
    { title: '凭证号', dataIndex: 'voucherLabel', width: 100 },
    { title: '摘要', dataIndex: 'summary', ellipsis: true },
    { title: '科目', dataIndex: 'subjectName', width: 160, ellipsis: true },
    {
      title: '借方',
      dataIndex: 'debitAmount',
      width: 130,
      align: 'right',
      render: (v: number) => (v ? money(v) : ''),
    },
    {
      title: '贷方',
      dataIndex: 'creditAmount',
      width: 130,
      align: 'right',
      render: (v: number) => (v ? money(v) : ''),
    },
  ];

  return (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space wrap size="large">
          <Space>
            <span style={{ color: '#666' }}>企业</span>
            <Select
              showSearch
              allowClear
              style={{ width: 340 }}
              placeholder="搜索企业名称"
              filterOption={false}
              loading={companyLoading}
              onSearch={(value) => void loadCompanies(value)}
              onChange={(value) => setCompanyId(value)}
              value={companyId}
              options={companyOptions}
            />
          </Space>
          <Space>
            <span style={{ color: '#666' }}>会计期间</span>
            <DatePicker
              picker="month"
              allowClear={false}
              value={period}
              onChange={(value) => value && setPeriod(value)}
            />
          </Space>
          {closed ? (
            <Tag icon={<LockOutlined />} color="success">
              已结账
            </Tag>
          ) : (
            <Tag icon={<UnlockOutlined />} color="default">
              未结账
            </Tag>
          )}
          <Button icon={<ReloadOutlined />} onClick={() => void loadReport()}>
            刷新
          </Button>
          <Tooltip title="生成结转损益凭证并封账">
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              disabled={!companyId || closed}
              onClick={handleClose}
            >
              结账
            </Button>
          </Tooltip>
          <Button disabled={!companyId || !closed} onClick={handleReopen}>
            反结账
          </Button>
        </Space>
      </Card>

      {!companyId && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="请先选择企业。报表按「企业 + 会计期间」独立出数。"
        />
      )}

      {closed && (
        <Alert
          type="success"
          showIcon
          icon={<LockOutlined />}
          style={{ marginBottom: 16 }}
          message={`${year} 年 ${month} 月已结账`}
          description={
            currentPeriod?.closingVoucherId
              ? '结转损益凭证已生成并记账，本期凭证已锁定。如需补录凭证，请先反结账。'
              : '本期无损益发生额，已直接封账。如需补录凭证，请先反结账。'
          }
        />
      )}

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'balanceSheet',
            label: '资产负债表',
            children: (
              <>
                {balanceSheet && (
                  <Alert
                    type={balanceSheet.balanced ? 'success' : 'error'}
                    showIcon
                    style={{ marginBottom: 16 }}
                    message={
                      balanceSheet.balanced
                        ? `报表平衡：资产 ${money(balanceSheet.totalAssetClosing)} = 负债和所有者权益 ${money(balanceSheet.totalLiabilityAndEquityClosing)}`
                        : `报表不平衡！资产 ${money(balanceSheet.totalAssetClosing)} ≠ 负债和所有者权益 ${money(balanceSheet.totalLiabilityAndEquityClosing)}`
                    }
                    description={
                      balanceSheet.balanced
                        ? '会计恒等式成立。未结转的当期损益已并入「未分配利润」，因此任何时点出表都平衡。'
                        : '请检查是否有凭证借贷不等，或科目归类是否遗漏。'
                    }
                  />
                )}
                <Card
                  title={
                    <Space>
                      <span>资产负债表</span>
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                        {period.format('YYYY 年 M 月')} 期末
                      </Typography.Text>
                    </Space>
                  }
                  extra={
                    <Button size="small" onClick={exportBalanceSheet}>
                      导出 CSV
                    </Button>
                  }
                >
                  <Table
                    rowKey="key"
                    size="small"
                    loading={loading}
                    columns={balanceColumns}
                    dataSource={balanceRows}
                    pagination={false}
                    scroll={{ x: 920 }}
                    locale={{ emptyText: <Empty description="本期还没有已记账凭证" /> }}
                    summary={() =>
                      balanceSheet ? (
                        <Table.Summary.Row>
                          <Table.Summary.Cell index={0}>
                            <strong>资产合计</strong>
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={1} align="right">
                            <strong>{money(balanceSheet.totalAssetClosing)}</strong>
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={2} align="right">
                            {money(balanceSheet.totalAssetOpening)}
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={3}>
                            <strong>负债和所有者权益合计</strong>
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={4} align="right">
                            <strong
                              style={{ color: balanceSheet.balanced ? '#52c41a' : '#ff4d4f' }}
                            >
                              {money(balanceSheet.totalLiabilityAndEquityClosing)}
                            </strong>
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={5} align="right">
                            {money(balanceSheet.totalLiabilityAndEquityOpening)}
                          </Table.Summary.Cell>
                        </Table.Summary.Row>
                      ) : null
                    }
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'incomeStatement',
            label: '利润表',
            children: (
              <>
                {incomeStatement && (
                  <Row gutter={16} style={{ marginBottom: 16 }}>
                    <Col span={6}>
                      <Card size="small">
                        <Statistic
                          title="本期营业收入"
                          value={incomeStatement.revenueCurrent}
                          precision={2}
                          prefix="¥"
                        />
                      </Card>
                    </Col>
                    <Col span={6}>
                      <Card size="small">
                        <Statistic
                          title="本期净利润"
                          value={incomeStatement.netProfitCurrent}
                          precision={2}
                          prefix="¥"
                          valueStyle={{
                            color: incomeStatement.netProfitCurrent >= 0 ? '#52c41a' : '#ff4d4f',
                          }}
                        />
                      </Card>
                    </Col>
                    <Col span={6}>
                      <Card size="small">
                        <Statistic
                          title="本年累计收入"
                          value={incomeStatement.revenueYtd}
                          precision={2}
                          prefix="¥"
                        />
                      </Card>
                    </Col>
                    <Col span={6}>
                      <Card size="small">
                        <Statistic
                          title="本年累计净利润"
                          value={incomeStatement.netProfitYtd}
                          precision={2}
                          prefix="¥"
                          valueStyle={{
                            color: incomeStatement.netProfitYtd >= 0 ? '#52c41a' : '#ff4d4f',
                          }}
                        />
                      </Card>
                    </Col>
                  </Row>
                )}
                <Card
                  title={
                    <Space>
                      <span>利润表</span>
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                        {period.format('YYYY 年 M 月')} · 已排除结转损益凭证
                      </Typography.Text>
                    </Space>
                  }
                  extra={
                    <Button size="small" onClick={exportIncomeStatement}>
                      导出 CSV
                    </Button>
                  }
                >
                  <Table<IncomeStatementRow>
                    rowKey="name"
                    size="small"
                    loading={loading}
                    columns={incomeColumns}
                    dataSource={incomeStatement?.rows ?? []}
                    pagination={false}
                    locale={{ emptyText: <Empty description="本期没有损益发生额" /> }}
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'generalLedger',
            label: '总账',
            children: (
              <>
                {generalLedger && (
                  <Alert
                    type={generalLedger.balanced ? 'success' : 'error'}
                    showIcon
                    style={{ marginBottom: 16 }}
                    message={
                      generalLedger.balanced
                        ? `借贷平衡：本期借方 ${money(generalLedger.totalPeriodDebit)} = 贷方 ${money(generalLedger.totalPeriodCredit)}`
                        : `借贷不平！本期借方 ${money(generalLedger.totalPeriodDebit)} ≠ 贷方 ${money(generalLedger.totalPeriodCredit)}`
                    }
                  />
                )}
                <Card
                  title={
                    <Space>
                      <span>总账</span>
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                        按一级科目汇总，明细科目已归并
                      </Typography.Text>
                    </Space>
                  }
                >
                  <Table<GeneralLedgerRow>
                    rowKey="subjectCode"
                    size="small"
                    loading={loading}
                    columns={generalColumns}
                    dataSource={generalLedger?.rows ?? []}
                    pagination={false}
                    scroll={{ x: 1000 }}
                    locale={{ emptyText: <Empty description="本期还没有已记账凭证" /> }}
                    summary={() =>
                      generalLedger ? (
                        <Table.Summary.Row>
                          <Table.Summary.Cell index={0} colSpan={2}>
                            <strong>合计</strong>
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={2} align="right">
                            {money(generalLedger.totalOpeningDebit)}
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={3} align="right">
                            {money(generalLedger.totalOpeningCredit)}
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={4} align="right">
                            {money(generalLedger.totalPeriodDebit)}
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={5} align="right">
                            {money(generalLedger.totalPeriodCredit)}
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={6} align="right">
                            <strong>{money(generalLedger.totalClosingDebit)}</strong>
                          </Table.Summary.Cell>
                          <Table.Summary.Cell index={7} align="right">
                            <strong>{money(generalLedger.totalClosingCredit)}</strong>
                          </Table.Summary.Cell>
                        </Table.Summary.Row>
                      ) : null
                    }
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'detailLedger',
            label: '明细账',
            children: (
              <>
                <Card size="small" style={{ marginBottom: 16 }}>
                  <Space>
                    <span style={{ color: '#666' }}>选择科目</span>
                    <Select
                      showSearch
                      style={{ width: 320 }}
                      optionFilterProp="label"
                      value={subjectCode}
                      onChange={setSubjectCode}
                      options={subjects.map((s) => ({
                        value: s.subjectCode,
                        label: `${s.subjectCode} ${s.subjectName}`,
                      }))}
                    />
                    <span style={{ color: '#999', fontSize: 12 }}>
                      可输入一级编码（如 1002）查看含下级明细的全部流水
                    </span>
                  </Space>
                </Card>

                {detailLedger && (
                  <Descriptions size="small" column={4} style={{ marginBottom: 16 }} bordered>
                    <Descriptions.Item label="期初余额">
                      {money(detailLedger.openingBalance)}（{detailLedger.openingDirection}）
                    </Descriptions.Item>
                    <Descriptions.Item label="本期借方">
                      {money(detailLedger.periodDebit)}
                    </Descriptions.Item>
                    <Descriptions.Item label="本期贷方">
                      {money(detailLedger.periodCredit)}
                    </Descriptions.Item>
                    <Descriptions.Item label="期末余额">
                      <strong>
                        {money(detailLedger.closingBalance)}（{detailLedger.closingDirection}）
                      </strong>
                    </Descriptions.Item>
                  </Descriptions>
                )}

                <Card title="明细账流水">
                  <Table<DetailLedgerItem>
                    rowKey={(_, index) => String(index)}
                    size="small"
                    loading={loading}
                    columns={detailColumns}
                    dataSource={detailLedger?.items ?? []}
                    pagination={{ pageSize: 50, showSizeChanger: false }}
                    scroll={{ x: 1000 }}
                    locale={{
                      emptyText: (
                        <Empty description="该科目本期没有已记账分录（期初余额仍会显示在上方）" />
                      ),
                    }}
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'journal',
            label: '序时账',
            children: (
              <>
                {journal && (
                  <Alert
                    type={journal.balanced ? 'success' : 'error'}
                    showIcon
                    style={{ marginBottom: 16 }}
                    message={
                      journal.balanced
                        ? `借贷平衡：借方 ${money(journal.totalDebit)} = 贷方 ${money(journal.totalCredit)}`
                        : `借贷不平！借方 ${money(journal.totalDebit)} ≠ 贷方 ${money(journal.totalCredit)}`
                    }
                  />
                )}
                <Card
                  title={
                    <Space>
                      <span>序时账</span>
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                        按凭证日期顺序列出本期全部分录
                      </Typography.Text>
                    </Space>
                  }
                >
                  <Table<JournalItem>
                    rowKey={(_, index) => String(index)}
                    size="small"
                    loading={loading}
                    columns={journalColumns}
                    dataSource={journal?.items ?? []}
                    pagination={{ pageSize: 50, showSizeChanger: false }}
                    scroll={{ x: 900 }}
                    locale={{ emptyText: <Empty description="本期还没有已记账凭证" /> }}
                  />
                </Card>
              </>
            ),
          },
        ]}
      />
    </div>
  );
}
