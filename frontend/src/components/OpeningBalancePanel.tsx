/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : OpeningBalancePanel.tsx
 * Date : 2026/11/10 18:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Empty,
  Input,
  InputNumber,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import {
  clearOpeningBalance,
  getOpeningBalance,
  getOpeningBalanceSummary,
  saveOpeningBalance,
  type OpeningBalanceSummaryRow,
  type OpeningBalanceItem,
  type OpeningBalanceView,
} from '../api/finance';

/** 金额格式化（两位小数 + 千分位） */
const money = (value?: number | null) =>
  value == null
    ? '0.00'
    : Number(value).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });

/** 科目类别 → 中文与颜色 */
const categoryConfig: Record<string, { label: string; color: string }> = {
  ASSET: { label: '资产', color: 'blue' },
  LIABILITY: { label: '负债', color: 'orange' },
  EQUITY: { label: '权益', color: 'purple' },
  COST: { label: '成本', color: 'cyan' },
  INCOME: { label: '收入', color: 'green' },
  EXPENSE: { label: '费用', color: 'red' },
};

/** 表格行的可编辑形态 */
interface EditableRow {
  subjectCode: string;
  subjectName: string;
  category: string;
  active: boolean;
  debitAmount: number | null;
  creditAmount: number | null;
  remark: string;
}

/** 组件入参 */
export interface OpeningBalancePanelProps {
  /** 目标公司主体 ID */
  companyId?: string;
  /** 录入/保存后回调（页面据此刷新试算平衡等下游视图） */
  onSaved?: () => void;
  /** 切换到其他主体（总览条里点某户时用） */
  onSwitchCompany?: (companyId: string) => void;
}

/**
 * 期初余额录入面板。
 * <p>
 * <b>为什么需要它</b>：代账公司中途接手客户是常态，但客户的账在此之前是别家做的——
 * 历史凭证在上一家的系统里，不可能逐笔补录。而本系统所有期初数都是「按已记账凭证
 * 累计」倒推的，新客户没有历史凭证，于是试算平衡期初全 0、资产负债表年初余额全 0，
 * 账接不上、年报不能用。这里就是把上家余额表搬进来的地方。
 * </p>
 * <p>
 * 交互上做了三个决定：
 * </p>
 * <ul>
 *   <li><b>列出全部在用科目</b>，而不是让会计逐个新增行——照着上家报表往下填最快；</li>
 *   <li><b>实时显示借贷合计与差额</b>，不平衡就禁用保存。不平的期初会被后面每一张报表
 *       继承，事后极难发现，所以宁可在门口拦住；</li>
 *   <li><b>默认只显示有余额的科目</b>，但可以一键展开全部科目来补录漏项。</li>
 * </ul>
 *
 * @param props 组件入参
 */
export default function OpeningBalancePanel({
  companyId,
  onSaved,
  onSwitchCompany,
}: OpeningBalancePanelProps) {
  const { message, modal } = App.useApp();

  const [view, setView] = useState<OpeningBalanceView | null>(null);
  const [rows, setRows] = useState<EditableRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [openingPeriod, setOpeningPeriod] = useState<Dayjs | null>(null);
  const [showAll, setShowAll] = useState(false);
  /** 全主体期初录入状态（用于提示「还有几户没录」） */
  const [summaryRows, setSummaryRows] = useState<OpeningBalanceSummaryRow[]>([]);

  /** 从后端视图生成可编辑行 */
  const toEditable = useCallback(
    (data: OpeningBalanceView): EditableRow[] =>
      (data.items || []).map((item: OpeningBalanceItem) => ({
        subjectCode: item.subjectCode,
        subjectName: item.subjectName,
        category: item.category,
        active: item.active,
        debitAmount: Number(item.debitAmount) || null,
        creditAmount: Number(item.creditAmount) || null,
        remark: item.remark || '',
      })),
    [],
  );

  /** 加载期初余额 */
  const load = useCallback(async () => {
    if (!companyId) {
      setView(null);
      setRows([]);
      return;
    }
    setLoading(true);
    try {
      const data = await getOpeningBalance(companyId);
      setView(data);
      setRows(toEditable(data));
      setOpeningPeriod(
        data.openingPeriod
          ? dayjs(`${String(data.openingPeriod).slice(0, 4)}-${String(data.openingPeriod).slice(4)}-01`)
          : null,
      );
    } catch {
      // 拦截器已统一提示
    } finally {
      setLoading(false);
    }
  }, [companyId, toEditable]);

  useEffect(() => {
    void load();
  }, [load]);

  /**
   * 加载全主体期初录入状态。
   * <p>
   * 未选企业时不查：总览是「这个户之外还有谁没录」的旁路信息，没选中主体时
   * 页面本身就在提示先选企业，此时再发一次请求纯属浪费。
   * 非关键路径，失败静默，不影响录入主流程。
   * </p>
   */
  const loadSummary = useCallback(async () => {
    if (!companyId) {
      setSummaryRows([]);
      return;
    }
    try {
      const data = await getOpeningBalanceSummary();
      setSummaryRows(data?.items || []);
    } catch {
      setSummaryRows([]);
    }
  }, [companyId]);

  useEffect(() => {
    void loadSummary();
  }, [loadSummary]);

  /** 借贷合计与差额（实时算，不信任后端返回值） */
  const totals = useMemo(() => {
    let debit = 0;
    let credit = 0;
    for (const row of rows) {
      debit += Number(row.debitAmount) || 0;
      credit += Number(row.creditAmount) || 0;
    }
    debit = Math.round(debit * 100) / 100;
    credit = Math.round(credit * 100) / 100;
    return { debit, credit, diff: Math.round((debit - credit) * 100) / 100 };
  }, [rows]);

  /** 已录非零余额的行数 */
  const filledCount = useMemo(
    () =>
      rows.filter(
        (r) => (Number(r.debitAmount) || 0) !== 0 || (Number(r.creditAmount) || 0) !== 0,
      ).length,
    [rows],
  );

  /** 只显示有余额的科目（展开后显示全部） */
  const visibleRows = useMemo(
    () =>
      showAll
        ? rows
        : rows.filter(
            (r) => (Number(r.debitAmount) || 0) !== 0 || (Number(r.creditAmount) || 0) !== 0,
          ),
    [rows, showAll],
  );

  /**
   * 还需要录期初的**其他**主体（未录期初 + 已建科目，且不是当前这户）。
   * <p>
   * 排除当前户是必要的：会计正站在这户的录入页上填余额，提示条再说「这户没录」
   * 只会让人困惑「我正在录，为什么说我没录」。所以这里的语义是「除当前户以外」。
   * </p>
   */
  const otherPendingRows = useMemo(
    () => summaryRows.filter((row) => row.suggestOpening && row.companyId !== companyId),
    [summaryRows, companyId],
  );

  /** 更新某行 */
  const updateRow = (subjectCode: string, patch: Partial<EditableRow>) => {
    setRows((prev) =>
      prev.map((row) => (row.subjectCode === subjectCode ? { ...row, ...patch } : row)),
    );
  };

  /** 保存 */
  const handleSave = async () => {
    if (!companyId) {
      message.warning('请先选择企业');
      return;
    }
    if (!openingPeriod) {
      message.warning('请选择账套启用期间');
      return;
    }
    if (filledCount === 0) {
      message.warning('请至少录入一行有余额的科目');
      return;
    }
    if (totals.diff !== 0) {
      message.error(`期初借贷不平，差额 ${money(totals.diff)} 元，不能保存`);
      return;
    }
    const payload = rows
      .filter(
        (r) => (Number(r.debitAmount) || 0) !== 0 || (Number(r.creditAmount) || 0) !== 0,
      )
      .map((r) => ({
        subjectCode: r.subjectCode,
        debitAmount: Number(r.debitAmount) || 0,
        creditAmount: Number(r.creditAmount) || 0,
        remark: r.remark || undefined,
      }));
    setSaving(true);
    try {
      const data = await saveOpeningBalance(
        companyId,
        openingPeriod.year() * 100 + openingPeriod.month() + 1,
        payload,
      );
      setView(data);
      setRows(toEditable(data));
      message.success('期初余额已保存，试算平衡与报表已带上该期初');
      onSaved?.();
    } catch {
      // 拦截器已统一提示
    } finally {
      setSaving(false);
    }
  };

  /** 清空 */
  const handleClear = () => {
    if (!companyId) return;
    modal.confirm({
      title: '确认清空该企业的期初余额？',
      content:
        '清空后，试算平衡期初与资产负债表年初余额将回到「只按本系统已记账凭证累计」的口径。此操作不可撤销。',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          const result = await clearOpeningBalance(companyId);
          message.success(`已清空 ${result?.deleted ?? 0} 行期初余额`);
          await load();
          onSaved?.();
        } catch {
          // 拦截器已统一提示
        }
      },
    });
  };

  /** 表格列 */
  const columns: ColumnsType<EditableRow> = [
    {
      title: '科目编码',
      dataIndex: 'subjectCode',
      width: 110,
      render: (value: string, record) => (
        <Space size={4}>
          <span>{value}</span>
          {!record.active && <Tag color="default">停用</Tag>}
        </Space>
      ),
    },
    {
      title: '科目名称',
      dataIndex: 'subjectName',
      ellipsis: true,
    },
    {
      title: '类别',
      dataIndex: 'category',
      width: 80,
      render: (value: string) => {
        const cfg = categoryConfig[value] ?? { label: value || '-', color: 'default' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '期初借方',
      dataIndex: 'debitAmount',
      width: 170,
      align: 'right',
      render: (_: unknown, record) => (
        <InputNumber
          aria-label={`${record.subjectCode} 期初借方`}
          min={0}
          precision={2}
          style={{ width: '100%' }}
          placeholder="0.00"
          value={record.debitAmount ?? undefined}
          onChange={(value) =>
            updateRow(record.subjectCode, {
              debitAmount: value == null ? null : Number(value),
              // 借贷互斥：填了借方就清空贷方，避免同一行两边都有数被后端拒绝
              creditAmount: value ? null : record.creditAmount,
            })
          }
        />
      ),
    },
    {
      title: '期初贷方',
      dataIndex: 'creditAmount',
      width: 170,
      align: 'right',
      render: (_: unknown, record) => (
        <InputNumber
          aria-label={`${record.subjectCode} 期初贷方`}
          min={0}
          precision={2}
          style={{ width: '100%' }}
          placeholder="0.00"
          value={record.creditAmount ?? undefined}
          onChange={(value) =>
            updateRow(record.subjectCode, {
              creditAmount: value == null ? null : Number(value),
              debitAmount: value ? null : record.debitAmount,
            })
          }
        />
      ),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      width: 200,
      render: (_: unknown, record) => (
        <Input
          aria-label={`${record.subjectCode} 备注`}
          placeholder="可空"
          maxLength={500}
          value={record.remark}
          onChange={(e) => updateRow(record.subjectCode, { remark: e.target.value })}
        />
      ),
    },
  ];

  if (!companyId) {
    return (
      <Card>
        <Empty description="请先选择企业，再录入期初余额" />
      </Card>
    );
  }

  return (
    <Card
      title={
        <Space wrap>
          <span>期初余额</span>
          <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
            接户结转数 · 只影响期初与年初口径，不计入本期发生额
          </Typography.Text>
        </Space>
      }
      extra={
        <Space wrap>
          <Tooltip title="账套从哪个月开始在本系统记账；期初余额 = 该月前一刻的科目余额">
            <span style={{ color: '#8c8c8c' }}>启用期间</span>
          </Tooltip>
          <DatePicker
            picker="month"
            value={openingPeriod}
            onChange={(value) => setOpeningPeriod(value)}
            placeholder="选择启用月份"
            style={{ width: 150 }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={handleClear} disabled={!view?.configured}>
            清空
          </Button>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            disabled={totals.diff !== 0 || filledCount === 0}
            onClick={() => void handleSave()}
          >
            保存期初
          </Button>
        </Space>
      }
    >
      {otherPendingRows.length > 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={`其他还有 ${otherPendingRows.length} 户尚未录入期初余额`}
          description={
            <Space wrap size={[8, 8]} style={{ marginTop: 4 }}>
              {otherPendingRows.map((row) => (
                <Tag
                  key={row.companyId}
                  style={{
                    cursor: onSwitchCompany ? 'pointer' : 'default',
                    marginInlineEnd: 0,
                  }}
                  onClick={() => onSwitchCompany?.(row.companyId)}
                >
                  {row.companyName}
                </Tag>
              ))}
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                点击可切换；结账时未录期初会被拦截，建议提前补齐
              </Typography.Text>
            </Space>
          }
        />
      )}

      <Alert
        type={totals.diff === 0 && filledCount > 0 ? 'success' : 'warning'}
        showIcon
        style={{ marginBottom: 16 }}
        message={
          totals.diff === 0 && filledCount > 0
            ? `期初借贷平衡：借方 = 贷方 = ${money(totals.debit)} 元（已录 ${filledCount} 个科目）`
            : filledCount === 0
              ? '尚未录入期初余额。照着上家账套的余额表，把每个科目的余额填进来即可'
              : `期初借贷不平衡：借方 ${money(totals.debit)} − 贷方 ${money(totals.credit)} = 差额 ${money(totals.diff)} 元`
        }
        description={
          totals.diff === 0 && filledCount > 0
            ? '保存后，试算平衡的「期初」列、资产负债表「年初余额」、总账与明细账的期初、以及期末结转损益都会自动带上这批余额。'
            : '期初不平会导致后续每一张报表都不平，且事后极难定位，因此必须先对上上家余额表再保存。'
        }
      />

      <Space style={{ marginBottom: 12 }}>
        <Switch
          checked={showAll}
          onChange={setShowAll}
          checkedChildren="全部科目"
          unCheckedChildren="仅有余额"
        />
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          共 {rows.length} 个科目，其中 {filledCount} 个已录余额
          {!showAll && filledCount < rows.length ? `（隐藏了 ${rows.length - filledCount} 个零余额科目）` : ''}
        </Typography.Text>
      </Space>

      <Table<EditableRow>
        rowKey="subjectCode"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={visibleRows}
        pagination={{ pageSize: 30, showSizeChanger: false, showTotal: (t) => `共 ${t} 个科目` }}
        scroll={{ x: 900 }}
        locale={{
          emptyText: (
            <Empty
              description={
                showAll
                  ? '该企业还没有科目表。请先到「凭证」页初始化标准科目'
                  : '还没有录入任何期初余额。切换到「全部科目」即可开始填'
              }
            />
          ),
        }}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={3}>
              <strong>合计</strong>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={3} align="right">
              <strong>{money(totals.debit)}</strong>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={4} align="right">
              <strong>{money(totals.credit)}</strong>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={5}>
              <strong style={{ color: totals.diff === 0 ? '#52c41a' : '#ff4d4f' }}>
                {totals.diff === 0 ? '平衡' : `差额 ${money(totals.diff)}`}
              </strong>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
    </Card>
  );
}
