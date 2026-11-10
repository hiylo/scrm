/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : Voucher.tsx
 * Date : 2026/10/25 20:00:00
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
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CameraOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  SettingOutlined,
  StopOutlined,
} from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import {
  aiRecognizeVoucher,
  createVoucher,
  deleteVoucher,
  getTrialBalance,
  getVoucher,
  getVoucherPeriodSummary,
  initSubjects,
  listCompanies,
  listSubjects,
  listVouchers,
  postVoucher,
  updateVoucher,
  voidVoucher,
  batchPostVouchers,
  batchVoidVouchers,
  type AccountSubject,
  type AiVoucherSuggestion,
  type TrialBalance,
  type TrialBalanceRow,
  type Voucher,
  type VoucherDetail,
  type VoucherEntry,
  type VoucherPeriodSummary,
} from '../api/finance';
import VoucherAttachmentDrawer from '../components/VoucherAttachmentDrawer';
import OpeningBalancePanel from '../components/OpeningBalancePanel';

/** 科目类别映射 */
const categoryConfig: Record<string, { label: string; color: string }> = {
  ASSET: { label: '资产', color: 'blue' },
  LIABILITY: { label: '负债', color: 'volcano' },
  EQUITY: { label: '权益', color: 'purple' },
  COST: { label: '成本', color: 'gold' },
  INCOME: { label: '收入', color: 'green' },
  EXPENSE: { label: '费用', color: 'orange' },
};

/** 凭证状态映射 */
const voucherStatusConfig: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  POSTED: { label: '已记账', color: 'success' },
  VOID: { label: '已作废', color: 'error' },
};

/** 金额格式化 */
const money = (value?: number | null) =>
  value == null
    ? '-'
    : Number(value).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });

/** 空分录行 */
const emptyEntry = (lineNo: number): VoucherEntry => ({
  lineNo,
  subjectCode: '',
  summary: '',
  debitAmount: 0,
  creditAmount: 0,
});

/**
 * 记账页（会计科目 + 记账凭证 + 试算平衡）。
 * <p>
 * 设计上有两个刻意的选择：
 * </p>
 * <ul>
 *   <li><b>借贷差额实时显示</b>：会计录入时立刻看到「差 XXX 元」，
 *       而不是提交后被服务端拒绝再来回找。服务端仍是权威校验，前端只是提前发现。</li>
 *   <li><b>试算平衡单独成一个 Tab</b>：它是判断「账做对没有」的唯一手段，
 *       不该藏在报表深处。表尾直接给出两侧合计与平衡结论。</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function Voucher() {
  const { message, modal } = App.useApp();

  const [activeTab, setActiveTab] = useState('voucher');

  // ============ 共享上下文 ============
  const [companyId, setCompanyId] = useState<string | undefined>();
  const [period, setPeriod] = useState<Dayjs>(dayjs());
  const [companyOptions, setCompanyOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [companyLoading, setCompanyLoading] = useState(false);

  // ============ 科目 ============
  const [subjects, setSubjects] = useState<AccountSubject[]>([]);
  const [subjectLoading, setSubjectLoading] = useState(false);

  // ============ 凭证 ============
  const [vouchers, setVouchers] = useState<Voucher[]>([]);
  const [voucherTotal, setVoucherTotal] = useState(0);
  const [voucherLoading, setVoucherLoading] = useState(false);
  const [voucherPage, setVoucherPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  /** 关键词筛选（摘要/凭证号模糊搜索） */
  const [keywordFilter, setKeywordFilter] = useState<string>('');
  const [summary, setSummary] = useState<VoucherPeriodSummary | null>(null);
  /** 批量记账/作废选中的凭证 ID */
  const [selectedVoucherIds, setSelectedVoucherIds] = useState<string[]>([]);
  const [batchRunning, setBatchRunning] = useState(false);
  /** 原始单据抽屉：当前凭证（null 表示未打开） */
  const [attachmentVoucher, setAttachmentVoucher] = useState<Voucher | null>(null);

  // ============ 试算平衡 ============
  const [trial, setTrial] = useState<TrialBalance | null>(null);
  const [trialLoading, setTrialLoading] = useState(false);

  // ============ 凭证编辑弹窗 ============
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingStatus, setEditingStatus] = useState<string>('DRAFT');
  const [entries, setEntries] = useState<VoucherEntry[]>([]);
  const [saving, setSaving] = useState(false);
  const [aiRecognizing, setAiRecognizing] = useState(false);
  const [aiWarnings, setAiWarnings] = useState<string[]>([]);
  const [editorForm] = Form.useForm<{
    voucherWord: string;
    voucherDate: Dayjs;
    attachmentCount?: number;
    remark?: string;
  }>();

  const year = period.year();
  const month = period.month() + 1;

  /** 借贷差额（正数表示借方多，负数表示贷方多） */
  const balanceDiff = useMemo(() => {
    const debit = entries.reduce((s, e) => s + (Number(e.debitAmount) || 0), 0);
    const credit = entries.reduce((s, e) => s + (Number(e.creditAmount) || 0), 0);
    return Math.round((debit - credit) * 100) / 100;
  }, [entries]);

  /** 借方合计 */
  const totalDebit = useMemo(
    () => entries.reduce((s, e) => s + (Number(e.debitAmount) || 0), 0),
    [entries],
  );

  /** 贷方合计 */
  const totalCredit = useMemo(
    () => entries.reduce((s, e) => s + (Number(e.creditAmount) || 0), 0),
    [entries],
  );

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

  /** 加载科目 */
  const loadSubjects = useCallback(async () => {
    if (!companyId) {
      setSubjects([]);
      return;
    }
    setSubjectLoading(true);
    try {
      setSubjects((await listSubjects(companyId)) || []);
    } catch {
      // 已统一提示
    } finally {
      setSubjectLoading(false);
    }
  }, [companyId]);

  /** 加载凭证 */
  const loadVouchers = useCallback(async () => {
    if (!companyId) {
      setVouchers([]);
      setVoucherTotal(0);
      setSummary(null);
      return;
    }
    setVoucherLoading(true);
    try {
      const [page, sum] = await Promise.all([
        listVouchers({
          companyId,
          periodYear: year,
          periodMonth: month,
          status: statusFilter,
          keyword: keywordFilter || undefined,
          page: voucherPage,
          size: 10,
        }),
        getVoucherPeriodSummary(companyId, year, month),
      ]);
      setVouchers(page.content || []);
      setVoucherTotal(page.totalElements || 0);
      setSummary(sum);
    } catch {
      // 已统一提示
    } finally {
      setVoucherLoading(false);
    }
  }, [companyId, year, month, statusFilter, keywordFilter, voucherPage]);

  /** 加载试算平衡 */
  const loadTrial = useCallback(async () => {
    if (!companyId) {
      setTrial(null);
      return;
    }
    setTrialLoading(true);
    try {
      setTrial(await getTrialBalance(companyId, year, month));
    } catch {
      // 已统一提示
    } finally {
      setTrialLoading(false);
    }
  }, [companyId, year, month]);

  useEffect(() => {
    void loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    void loadSubjects();
    void loadVouchers();
    void loadTrial();
  }, [loadSubjects, loadVouchers, loadTrial]);

  /** 初始化标准科目 */
  const handleInitSubjects = () => {
    if (!companyId) {
      message.warning('请先选择企业');
      return;
    }
    modal.confirm({
      title: '初始化标准科目表？',
      content:
        '将按小企业会计准则常用科目建账（32 个高频科目）。幂等：已存在的科目不会覆盖，可重复执行。',
      onOk: async () => {
        try {
          const count = await initSubjects(companyId);
          message.success(`已新增 ${count} 个科目`);
          await loadSubjects();
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 打开新建凭证 */
  const openCreate = () => {
    if (!companyId) {
      message.warning('请先选择企业');
      return;
    }
    if (subjects.length === 0) {
      message.warning('该企业还没有科目表，请先点「初始化标准科目」');
      return;
    }
    setEditingId(null);
    setEditingStatus('DRAFT');
    setEntries([emptyEntry(1), emptyEntry(2)]);
    editorForm.resetFields();
    editorForm.setFieldsValue({
      voucherWord: '记',
      voucherDate: period.endOf('month'),
      attachmentCount: 0,
    } as never);
    setEditorOpen(true);
  };

  /**
   * 以某张凭证为模板新建（复制）。
   * <p>
   * 代账场景高频：房租/工资/折旧等固定分录每月重复发生，会计不必重录。
   * 复制只取「凭证字 + 摘要 + 分录」，日期落在当前期间月末，新凭证为草稿，
   * 原凭证状态（草稿/已记账/已作废）不影响复制。附件数不继承（需重新挂）。
   * </p>
   *
   * @param record 作为模板的凭证
   */
  const handleCopy = async (record: Voucher) => {
    if (!companyId) {
      message.warning('请先选择企业');
      return;
    }
    if (subjects.length === 0) {
      message.warning('该企业还没有科目表，请先点「初始化标准科目」');
      return;
    }
    try {
      const detail: VoucherDetail = await getVoucher(record.id);
      setEditingId(null);
      setEditingStatus('DRAFT');
      setEntries(
        (detail.entries || []).map((e, index) => ({
          ...e,
          lineNo: index + 1,
          debitAmount: Number(e.debitAmount) || 0,
          creditAmount: Number(e.creditAmount) || 0,
        })),
      );
      editorForm.resetFields();
      editorForm.setFieldsValue({
        voucherWord: detail.voucher.voucherWord,
        voucherDate: period.endOf('month'),
        attachmentCount: 0,
        remark: detail.voucher.remark,
      } as never);
      setEditorOpen(true);
      message.success('已复制为新建凭证草稿，请确认期间与金额后保存');
    } catch {
      // 已统一提示
    }
  };

  /** 打开编辑（仅草稿可改） */
  const openEdit = async (record: Voucher) => {
    if (record.status !== 'DRAFT') {
      message.warning('已记账或已作废的凭证不可修改；请先作废后重开');
      return;
    }
    try {
      const detail: VoucherDetail = await getVoucher(record.id);
      setEditingId(record.id);
      setEditingStatus(detail.voucher.status);
      setEntries(
        (detail.entries || []).map((e, index) => ({
          ...e,
          lineNo: index + 1,
          debitAmount: Number(e.debitAmount) || 0,
          creditAmount: Number(e.creditAmount) || 0,
        })),
      );
      editorForm.resetFields();
      editorForm.setFieldsValue({
        voucherWord: detail.voucher.voucherWord,
        voucherDate: dayjs(detail.voucher.voucherDate),
        attachmentCount: detail.voucher.attachmentCount ?? 0,
        remark: detail.voucher.remark,
      } as never);
      setEditorOpen(true);
    } catch {
      // 已统一提示
    }
  };

  /** 更新分录字段 */
  const updateEntry = (index: number, patch: Partial<VoucherEntry>) => {
    setEntries((prev) =>
      prev.map((entry, i) => {
        if (i !== index) return entry;
        const next = { ...entry, ...patch };
        // 借贷互斥：填了一边就把另一边清零，避免同时非零被服务端拒绝
        if (patch.debitAmount != null && Number(patch.debitAmount) > 0) {
          next.creditAmount = 0;
        }
        if (patch.creditAmount != null && Number(patch.creditAmount) > 0) {
          next.debitAmount = 0;
        }
        return next;
      }),
    );
  };

  /** 新增分录行 */
  const addEntry = () => {
    setEntries((prev) => [...prev, emptyEntry(prev.length + 1)]);
  };

  /** 删除分录行 */
  const removeEntry = (index: number) => {
    setEntries((prev) =>
      prev.length <= 2
        ? prev
        : prev.filter((_, i) => i !== index).map((e, i) => ({ ...e, lineNo: i + 1 })),
    );
  };

  /** AI 智能凭证：上传票据图片 → 识别 → 回填分录草稿 */
  const handleAiRecognize = async (file: File) => {
    if (!companyId) {
      message.warning('请先选择企业');
      return;
    }
    setAiRecognizing(true);
    setAiWarnings([]);
    try {
      const suggestion: AiVoucherSuggestion = await aiRecognizeVoucher(companyId, file);
      if (!suggestion.valid) {
        setAiWarnings(suggestion.warnings || ['AI 未能生成有效凭证草稿，请手工录入']);
        message.warning(suggestion.warnings?.[0] || '识别结果不完整，请核对后手工录入');
        return;
      }
      const mapped: VoucherEntry[] = (suggestion.entries || []).map((e, index) => ({
        lineNo: index + 1,
        subjectCode: e.subjectCode,
        subjectName: e.subjectName,
        summary: e.summary || suggestion.summary || '',
        debitAmount: e.direction === 'DEBIT' ? Number(e.amount) || 0 : 0,
        creditAmount: e.direction === 'CREDIT' ? Number(e.amount) || 0 : 0,
      }));
      if (mapped.length < 2) {
        setAiWarnings(suggestion.warnings || ['识别结果分录不足，请核对']);
        message.warning('识别结果分录不足，请核对后手工补齐');
        return;
      }
      setEntries(mapped);
      editorForm.setFieldsValue({
        voucherDate: suggestion.billDate ? dayjs(suggestion.billDate) : period.endOf('month'),
        remark: suggestion.billNo ? `AI 识别票据 ${suggestion.billType || ''} ${suggestion.billNo}` : undefined,
      } as never);
      if (suggestion.amount !== undefined) {
        message.success(
          `识别成功：${suggestion.billType || '票据'} 金额 ${money(Number(suggestion.amount))}，分录已回填，请核对后保存`,
        );
      } else {
        message.success('识别成功，分录已回填，请核对后保存');
      }
      setAiWarnings(suggestion.warnings || []);
    } catch {
      // 已统一提示
    } finally {
      setAiRecognizing(false);
    }
  };

  /** 保存凭证 */
  const saveVoucher = async () => {
    if (!companyId) return;
    const values = await editorForm.validateFields();
    const filled = entries.filter(
      (e) => e.subjectCode && (Number(e.debitAmount) > 0 || Number(e.creditAmount) > 0),
    );
    if (filled.length < 2) {
      message.warning('至少需要 2 条有科目和金额的分录');
      return;
    }
    if (Math.abs(balanceDiff) > 0.001) {
      message.error(`借贷不平衡，差额 ${money(Math.abs(balanceDiff))} 元`);
      return;
    }
    setSaving(true);
    try {
      const payload = {
        periodYear: year,
        periodMonth: month,
        voucherWord: values.voucherWord,
        voucherDate: values.voucherDate ? values.voucherDate.format('YYYY-MM-DD') : undefined,
        attachmentCount: values.attachmentCount ?? 0,
        remark: values.remark,
        entries: filled,
      };
      if (editingId) {
        await updateVoucher(editingId, payload);
        message.success('凭证已更新');
      } else {
        await createVoucher(companyId, payload);
        message.success('凭证已保存为草稿');
      }
      setEditorOpen(false);
      await Promise.all([loadVouchers(), loadTrial()]);
    } catch {
      // 已统一提示
    } finally {
      setSaving(false);
    }
  };

  /** 记账 */
  const handlePost = (record: Voucher) => {
    modal.confirm({
      title: `确认记账：${record.voucherLabel ?? record.voucherWord}？`,
      content: '记账后分录锁定，不可再修改，只能作废重开。记账后该凭证计入科目余额。',
      onOk: async () => {
        try {
          await postVoucher(record.id);
          message.success('已记账');
          await Promise.all([loadVouchers(), loadTrial()]);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 作废 */
  const handleVoid = (record: Voucher) => {
    let reason = '';
    modal.confirm({
      title: `作废 ${record.voucherLabel ?? ''}？`,
      content: (
        <div>
          <div style={{ marginBottom: 8, color: '#666' }}>
            作废后该凭证退出余额，但记录保留可查。
          </div>
          <Input.TextArea
            rows={2}
            placeholder="作废原因（建议填写，便于日后追溯）"
            onChange={(e) => {
              reason = e.target.value;
            }}
          />
        </div>
      ),
      onOk: async () => {
        try {
          await voidVoucher(record.id, reason || undefined);
          message.success('已作废');
          await Promise.all([loadVouchers(), loadTrial()]);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 删除草稿 */
  const handleDelete = (record: Voucher) => {
    modal.confirm({
      title: `删除草稿 ${record.voucherLabel ?? ''}？`,
      content: '只有草稿可以删除；已记账凭证请用作废以保留痕迹。',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteVoucher(record.id);
          message.success('已删除');
          await Promise.all([loadVouchers(), loadTrial()]);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 批量结果提示：全成功用 success，部分失败用 warning 并列出失败明细 */
  const notifyBatchResult = (action: string, result: { successCount: number; failCount: number; failures: Array<{ id: string; label?: string | null; message?: string | null }> }) => {
    if (result.failCount === 0) {
      message.success(`${action}完成：共 ${result.successCount} 张`);
      return;
    }
    const detail = result.failures
      .slice(0, 5)
      .map((f) => `${f.label ?? f.id}：${f.message ?? '失败'}`)
      .join('；');
    const more = result.failCount > 5 ? ` 等 ${result.failCount} 张` : '';
    if (result.successCount === 0) {
      message.error(`${action}全部失败：${detail}${more}`);
    } else {
      message.warning(`${action}完成 ${result.successCount} 张，失败 ${result.failCount} 张：${detail}${more}`);
    }
  };

  /** 批量记账（草稿 → 已记账） */
  const handleBatchPost = () => {
    const drafts = vouchers.filter((v) => selectedVoucherIds.includes(v.id) && v.status === 'DRAFT');
    if (drafts.length === 0) {
      message.warning('选中的凭证里没有草稿，无可记账凭证');
      return;
    }
    modal.confirm({
      title: `批量记账 ${drafts.length} 张凭证？`,
      content: '记账后分录锁定、计入科目余额，不可再修改（只能作废重开）。已结账期间、已作废或借贷不平的凭证会被跳过并列在结果里。',
      onOk: async () => {
        setBatchRunning(true);
        try {
          const res = await batchPostVouchers(drafts.map((v) => v.id));
          notifyBatchResult('批量记账', res);
          setSelectedVoucherIds([]);
          await Promise.all([loadVouchers(), loadTrial()]);
        } catch {
          // 已统一提示
        } finally {
          setBatchRunning(false);
        }
      },
    });
  };

  /** 批量作废 */
  const handleBatchVoid = () => {
    const targets = vouchers.filter((v) => selectedVoucherIds.includes(v.id) && v.status !== 'VOID');
    if (targets.length === 0) {
      message.warning('选中的凭证都已是作废状态，无可作废凭证');
      return;
    }
    let reason = '';
    modal.confirm({
      title: `批量作废 ${targets.length} 张凭证？`,
      content: (
        <div>
          <div style={{ marginBottom: 8, color: '#666' }}>
            作废后这些凭证退出余额，但记录保留可查（已作废的会自动跳过）。
          </div>
          <Input.TextArea
            rows={2}
            placeholder="作废原因（建议填写，便于日后追溯）"
            onChange={(e) => {
              reason = e.target.value;
            }}
          />
        </div>
      ),
      okButtonProps: { danger: true },
      onOk: async () => {
        setBatchRunning(true);
        try {
          const res = await batchVoidVouchers(targets.map((v) => v.id), reason || undefined);
          notifyBatchResult('批量作废', res);
          setSelectedVoucherIds([]);
          await Promise.all([loadVouchers(), loadTrial()]);
        } catch {
          // 已统一提示
        } finally {
          setBatchRunning(false);
        }
      },
    });
  };

  /** 凭证列 */
  const voucherColumns: ColumnsType<Voucher> = [
    {
      title: '凭证号',
      key: 'label',
      width: 110,
      render: (_, record) => (
        <Typography.Text strong>{record.voucherLabel ?? `${record.voucherWord}-${record.voucherNo}`}</Typography.Text>
      ),
    },
    {
      title: '日期',
      dataIndex: 'voucherDate',
      width: 120,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: '摘要',
      dataIndex: 'remark',
      ellipsis: true,
      render: (v: string) => v || <span style={{ color: '#bbb' }}>—</span>,
    },
    {
      title: '借方合计',
      dataIndex: 'totalDebit',
      width: 130,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '贷方合计',
      dataIndex: 'totalCredit',
      width: 130,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '分录',
      dataIndex: 'entryCount',
      width: 70,
      align: 'right',
    },
    {
      title: (
        <Tooltip title="纸质张数 = 会计手工声明；单据 = 已挂载的电子原始单据份数">
          <span>附件/单据</span>
        </Tooltip>
      ),
      key: 'attachments',
      width: 110,
      align: 'right',
      render: (_, record) => {
        const paper = record.attachmentCount ?? 0;
        const files = record.attachmentFileCount ?? 0;
        return (
          <Tooltip title="点击管理原始单据（发票/回单/合同）">
            <Button
              type="link"
              size="small"
              style={{ padding: 0, height: 'auto' }}
              onClick={() => setAttachmentVoucher(record)}
            >
              {paper} 张 / {files} 份
            </Button>
          </Tooltip>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const cfg = voucherStatusConfig[v] || { label: v, color: 'default' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 280,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {record.status === 'DRAFT' && (
            <>
              <Button size="small" onClick={() => void openEdit(record)}>编辑</Button>
              <Button size="small" type="primary" onClick={() => handlePost(record)}>记账</Button>
              <Button size="small" danger onClick={() => handleDelete(record)}>删除</Button>
            </>
          )}
          {record.status === 'POSTED' && (
            <Button size="small" icon={<StopOutlined />} onClick={() => handleVoid(record)}>
              作废
            </Button>
          )}
          {record.status === 'VOID' && <span style={{ color: '#bbb' }}>已作废</span>}
          <Tooltip title="以本凭证为模板新建一张草稿（固定分录免重录）">
            <Button size="small" onClick={() => void handleCopy(record)}>复制</Button>
          </Tooltip>
        </Space>
      ),
    },
  ];

  /** 科目列 */
  const subjectColumns: ColumnsType<AccountSubject> = [
    { title: '科目编码', dataIndex: 'subjectCode', width: 130 },
    { title: '科目名称', dataIndex: 'subjectName', width: 200 },
    {
      title: '类别',
      dataIndex: 'category',
      width: 100,
      render: (v: string) => {
        const cfg = categoryConfig[v] || { label: v, color: 'default' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '余额方向',
      dataIndex: 'direction',
      width: 100,
      render: (v: string) => (v === 'DEBIT' ? '借方' : '贷方'),
    },
    {
      title: '级次',
      dataIndex: 'subjectLevel',
      width: 80,
      align: 'center',
      render: (v: number) => (v ?? 1) === 1 ? '一级' : '明细',
    },
    {
      title: '上级',
      dataIndex: 'parentCode',
      width: 100,
      render: (v: string) => v || '—',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) =>
        v === 'ACTIVE' ? <Tag color="success">启用</Tag> : <Tag color="default">停用</Tag>,
    },
  ];

  /** 试算平衡列 */
  const trialColumns: ColumnsType<TrialBalanceRow> = [
    { title: '科目编码', dataIndex: 'subjectCode', width: 110, fixed: 'left' },
    { title: '科目名称', dataIndex: 'subjectName', width: 160, fixed: 'left' },
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

  const contextHint = useMemo(
    () => (companyId ? null : '请先选择企业。记账按「企业 + 会计期间」独立建账，不同企业科目表互不影响。'),
    [companyId],
  );

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
              onChange={(value) => {
                setCompanyId(value);
                setVoucherPage(0);
              }}
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
              onChange={(value) => {
                if (!value) return;
                setPeriod(value);
                setVoucherPage(0);
              }}
            />
          </Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => void Promise.all([loadSubjects(), loadVouchers(), loadTrial()])}
          >
            刷新
          </Button>
          <Tooltip title="按小企业会计准则常用科目建账（幂等，不覆盖已有科目）">
            <Button icon={<SettingOutlined />} onClick={handleInitSubjects}>
              初始化标准科目
            </Button>
          </Tooltip>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建凭证
          </Button>
        </Space>
      </Card>

      {contextHint && <Alert type="info" showIcon style={{ marginBottom: 16 }} message={contextHint} />}

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'voucher',
            label: '凭证',
            children: (
              <>
                <Row gutter={16} style={{ marginBottom: 16 }}>
                  <Col span={6}>
                    <Card size="small">
                      <Statistic title="本期凭证" value={summary?.total ?? 0} suffix="张" />
                    </Card>
                  </Col>
                  <Col span={6}>
                    <Card size="small">
                      <Statistic
                        title="草稿"
                        value={summary?.draft ?? 0}
                        suffix="张"
                        valueStyle={{ color: (summary?.draft ?? 0) > 0 ? '#fa8c16' : undefined }}
                      />
                    </Card>
                  </Col>
                  <Col span={6}>
                    <Card size="small">
                      <Statistic
                        title="已记账"
                        value={summary?.posted ?? 0}
                        suffix="张"
                        valueStyle={{ color: '#52c41a' }}
                      />
                    </Card>
                  </Col>
                  <Col span={6}>
                    <Card size="small">
                      <Statistic
                        title="已记账借方合计"
                        value={summary?.postedDebitTotal ?? 0}
                        precision={2}
                        prefix="¥"
                      />
                    </Card>
                  </Col>
                </Row>

                <Card
                  title={
                    <Space>
                      <span>记账凭证</span>
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                        {period.format('YYYY 年 M 月')} · 草稿不参与余额，记账后锁定
                      </Typography.Text>
                    </Space>
                  }
                  extra={
                    <Space wrap>
                      <Button
                        size="small"
                        type="primary"
                        disabled={selectedVoucherIds.length === 0 || batchRunning}
                        loading={batchRunning}
                        onClick={handleBatchPost}
                      >
                        批量记账（{selectedVoucherIds.length}）
                      </Button>
                      <Button
                        size="small"
                        danger
                        disabled={selectedVoucherIds.length === 0 || batchRunning}
                        onClick={handleBatchVoid}
                      >
                        批量作废（{selectedVoucherIds.length}）
                      </Button>
                      <Input.Search
                        allowClear
                        placeholder="搜索摘要/凭证号"
                        style={{ width: 200 }}
                        onSearch={(value) => {
                          setKeywordFilter(value);
                          setVoucherPage(0);
                        }}
                      />
                      <Select
                        allowClear
                        placeholder="状态"
                        style={{ width: 130 }}
                        value={statusFilter}
                        onChange={(value) => {
                          setStatusFilter(value);
                          setVoucherPage(0);
                        }}
                        options={Object.entries(voucherStatusConfig).map(([value, cfg]) => ({
                          value,
                          label: cfg.label,
                        }))}
                      />
                    </Space>
                  }
                >
                  <Table<Voucher>
                    rowKey="id"
                    size="small"
                    loading={voucherLoading}
                    columns={voucherColumns}
                    dataSource={vouchers}
                    scroll={{ x: 1200 }}
                    rowSelection={{
                      selectedRowKeys: selectedVoucherIds,
                      onChange: (keys) => setSelectedVoucherIds(keys as string[]),
                      getCheckboxProps: (record) => ({
                        // 已作废的凭证既不能记账也不能再作废，直接禁用勾选减少误操作
                        disabled: record.status === 'VOID',
                      }),
                    }}
                    locale={{
                      emptyText: (
                        <Empty
                          description={
                            subjects.length === 0
                              ? '还没有科目表。先点「初始化标准科目」，再新建凭证'
                              : '本期还没有凭证。点「新建凭证」录入'
                          }
                        />
                      ),
                    }}
                    pagination={{
                      current: voucherPage + 1,
                      pageSize: 10,
                      total: voucherTotal,
                      showSizeChanger: false,
                      onChange: (p) => setVoucherPage(p - 1),
                      showTotal: (t) => `共 ${t} 张`,
                    }}
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'trial',
            label: '试算平衡',
            children: (
              <>
                {trial && (
                  <Alert
                    type={trial.balanced ? 'success' : 'error'}
                    showIcon
                    style={{ marginBottom: 16 }}
                    message={
                      trial.balanced
                        ? `账平：期末借贷双方各 ${money(trial.totalClosingDebit)} 元`
                        : `账不平！期末借方合计 ${money(trial.totalClosingDebit)}，贷方合计 ${money(trial.totalClosingCredit)}`
                    }
                    description={
                      trial.balanced
                        ? '所有科目期末借方余额合计等于贷方余额合计。只统计已记账凭证，草稿与已作废凭证不参与。'
                        : '账不平说明有凭证借贷不等或余额计算异常。请逐张检查本期凭证。'
                    }
                  />
                )}
                <Card
                  title={
                    <Space>
                      <span>试算平衡表</span>
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                        {period.format('YYYY 年 M 月')} · 只统计已记账凭证
                      </Typography.Text>
                    </Space>
                  }
                >
                  <Table<TrialBalanceRow>
                    rowKey="subjectCode"
                    size="small"
                    loading={trialLoading}
                    columns={trialColumns}
                    dataSource={trial?.rows ?? []}
                    scroll={{ x: 1100 }}
                    locale={{ emptyText: <Empty description="本期还没有已记账凭证，无余额数据" /> }}
                    pagination={false}
                    summary={() =>
                      trial ? (
                        <Table.Summary fixed>
                          <Table.Summary.Row>
                            <Table.Summary.Cell index={0} colSpan={2}>
                              <strong>合计</strong>
                            </Table.Summary.Cell>
                            <Table.Summary.Cell index={2} align="right">
                              {money(trial.totalOpeningDebit)}
                            </Table.Summary.Cell>
                            <Table.Summary.Cell index={3} align="right">
                              {money(trial.totalOpeningCredit)}
                            </Table.Summary.Cell>
                            <Table.Summary.Cell index={4} align="right">
                              {money(trial.totalPeriodDebit)}
                            </Table.Summary.Cell>
                            <Table.Summary.Cell index={5} align="right">
                              {money(trial.totalPeriodCredit)}
                            </Table.Summary.Cell>
                            <Table.Summary.Cell index={6} align="right">
                              <strong style={{ color: trial.balanced ? '#52c41a' : '#ff4d4f' }}>
                                {money(trial.totalClosingDebit)}
                              </strong>
                            </Table.Summary.Cell>
                            <Table.Summary.Cell index={7} align="right">
                              <strong style={{ color: trial.balanced ? '#52c41a' : '#ff4d4f' }}>
                                {money(trial.totalClosingCredit)}
                              </strong>
                            </Table.Summary.Cell>
                          </Table.Summary.Row>
                        </Table.Summary>
                      ) : null
                    }
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'opening',
            label: '期初余额',
            children: (
              <OpeningBalancePanel
                companyId={companyId}
                onSaved={() => void Promise.all([loadTrial(), loadVouchers()])}
                onSwitchCompany={(id) => setCompanyId(id)}
              />
            ),
          },
          {
            key: 'subject',
            label: '会计科目',
            children: (
              <Card
                title={
                  <Space>
                    <span>会计科目</span>
                    <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                      按企业独立建账；停用科目不可新增分录，历史分录保留
                    </Typography.Text>
                  </Space>
                }
                extra={
                  <Button icon={<ReloadOutlined />} onClick={() => void loadSubjects()}>
                    刷新
                  </Button>
                }
              >
                <Table<AccountSubject>
                  rowKey="id"
                  size="small"
                  loading={subjectLoading}
                  columns={subjectColumns}
                  dataSource={subjects}
                  scroll={{ x: 900 }}
                  locale={{
                    emptyText: (
                      <Empty description="还没有科目表。点上方「初始化标准科目」按常用科目一键建账" />
                    ),
                  }}
                  pagination={{ pageSize: 50, showSizeChanger: false, showTotal: (t) => `共 ${t} 个科目` }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 凭证编辑弹窗 */}
      <Modal
        title={
          editingId
            ? '编辑凭证草稿'
            : `新建凭证 · ${period.format('YYYY 年 M 月')}`
        }
        open={editorOpen}
        confirmLoading={saving}
        onCancel={() => setEditorOpen(false)}
        width={1100}
        destroyOnClose
        footer={[
          <Button key="cancel" onClick={() => setEditorOpen(false)}>取消</Button>,
          <Button
            key="save"
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            onClick={() => void saveVoucher()}
          >
            保存草稿
          </Button>,
        ]}
      >
        <Form form={editorForm} layout="inline" style={{ marginBottom: 16 }}>
          <Form.Item label="AI 识别" style={{ marginBottom: 0 }}>
            <Upload
              accept="image/png,image/jpeg,image/webp"
              maxCount={1}
              showUploadList={false}
              beforeUpload={() => false}
              onChange={({ file: f }) => {
                if (f.originFileObj) {
                  void handleAiRecognize(f.originFileObj);
                }
              }}
            >
              <Button icon={<CameraOutlined />} loading={aiRecognizing} disabled={!!editingId}>
                {aiRecognizing ? '识别中…' : '扫票据识别'}
              </Button>
            </Upload>
          </Form.Item>
          <Form.Item name="voucherWord" label="凭证字">
            <Select
              style={{ width: 90 }}
              options={['记', '收', '付', '转'].map((w) => ({ value: w, label: w }))}
            />
          </Form.Item>
          <Form.Item name="voucherDate" label="凭证日期">
            <DatePicker />
          </Form.Item>
          <Form.Item name="attachmentCount" label="附件张数">
            <InputNumber min={0} style={{ width: 90 }} />
          </Form.Item>
          <Form.Item name="remark" label="备注" style={{ minWidth: 260 }}>
            <Input placeholder="整张凭证的说明" maxLength={500} />
          </Form.Item>
        </Form>

        {aiWarnings.length > 0 && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="AI 识别提示（请人工核对）"
            description={
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                {aiWarnings.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            }
          />
        )}

        <Alert
          type={Math.abs(balanceDiff) < 0.001 && totalDebit > 0 ? 'success' : 'warning'}
          showIcon
          style={{ marginBottom: 12 }}
          message={
            Math.abs(balanceDiff) < 0.001 && totalDebit > 0
              ? `借贷平衡：借方 = 贷方 = ${money(totalDebit)} 元`
              : totalDebit === 0
                ? '请录入分录金额（有借必有贷，借贷必相等）'
                : `借贷不平衡：借方 ${money(totalDebit)} − 贷方 ${money(totalCredit)} = 差额 ${money(balanceDiff)} 元`
          }
        />

        <Table<VoucherEntry>
          size="small"
          rowKey={(_, index) => String(index)}
          dataSource={entries}
          pagination={false}
          scroll={{ y: 340 }}
          columns={[
            {
              title: '行',
              width: 50,
              align: 'center',
              render: (_, __, index) => index + 1,
            },
            {
              title: '科目',
              dataIndex: 'subjectCode',
              width: 260,
              render: (value: string, _record, index) => (
                <Select
                  showSearch
                  style={{ width: '100%' }}
                  placeholder="选择科目"
                  value={value || undefined}
                  optionFilterProp="label"
                  onChange={(code) => {
                    const subject = subjects.find((s) => s.subjectCode === code);
                    updateEntry(index, {
                      subjectCode: code,
                      subjectName: subject?.subjectName,
                    });
                  }}
                  options={subjects
                    .filter((s) => s.status === 'ACTIVE')
                    .map((s) => ({
                      value: s.subjectCode,
                      label: `${s.subjectCode} ${s.subjectName}`,
                    }))}
                />
              ),
            },
            {
              title: '摘要',
              dataIndex: 'summary',
              render: (value: string, _record, index) => (
                <Input
                  value={value}
                  placeholder="业务说明"
                  maxLength={200}
                  onChange={(e) => updateEntry(index, { summary: e.target.value })}
                />
              ),
            },
            {
              title: '借方金额',
              dataIndex: 'debitAmount',
              width: 150,
              render: (value: number, _record, index) => (
                <InputNumber
                  style={{ width: '100%' }}
                  min={0}
                  precision={2}
                  value={value as number}
                  onChange={(v) => updateEntry(index, { debitAmount: Number(v) || 0 })}
                />
              ),
            },
            {
              title: '贷方金额',
              dataIndex: 'creditAmount',
              width: 150,
              render: (value: number, _record, index) => (
                <InputNumber
                  style={{ width: '100%' }}
                  min={0}
                  precision={2}
                  value={value as number}
                  onChange={(v) => updateEntry(index, { creditAmount: Number(v) || 0 })}
                />
              ),
            },
            {
              title: '',
              width: 50,
              render: (_, __, index) => (
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  disabled={entries.length <= 2}
                  onClick={() => removeEntry(index)}
                />
              ),
            },
          ]}
          summary={() => (
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={3}>
                <Button type="dashed" size="small" icon={<PlusOutlined />} onClick={addEntry}>
                  增加分录
                </Button>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={3} align="right">
                <strong>{money(totalDebit)}</strong>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={4} align="right">
                <strong>{money(totalCredit)}</strong>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={5} />
            </Table.Summary.Row>
          )}
        />
      </Modal>

      {/* 原始单据抽屉：上传/下载/删除凭证的发票、回单、合同 */}
      <VoucherAttachmentDrawer
        open={!!attachmentVoucher}
        voucherId={attachmentVoucher?.id}
        voucherLabel={
          attachmentVoucher
            ? (attachmentVoucher.voucherLabel ??
              `${attachmentVoucher.voucherWord}-${attachmentVoucher.voucherNo}`)
            : undefined
        }
        voucherStatus={attachmentVoucher?.status}
        onClose={() => setAttachmentVoucher(null)}
        onChanged={() => void loadVouchers()}
      />
    </div>
  );
}
