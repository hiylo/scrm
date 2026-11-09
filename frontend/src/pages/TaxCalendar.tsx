/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TaxCalendar.tsx
 * Date : 2026/10/24 22:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Badge,
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
  Tag,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import {
  archiveRecord,
  cancelRecord,
  createTaxProfile,
  deleteTaxProfile,
  batchMarkFiled,
  listRecordsByMonth,
  listTaxProfiles,
  markRecordBackFilled,
  markRecordFiled,
  markRecordPaid,
  suggestCalendarMonth,
  type TaxProfile,
  type TaxProfilePayload,
  type TaxRecord,
} from '../api/finance';
import { searchCustomers, type CustomerOption } from '../api/finance';

/** 税种映射 */
const taxTypeConfig: Record<string, { color: string; label: string }> = {
  VAT: { color: 'blue', label: '增值税' },
  IIT: { color: 'green', label: '个人所得税' },
  CIT: { color: 'orange', label: '企业所得税' },
  SOCIAL_SECURITY: { color: 'purple', label: '社保' },
};

/** 申报状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  NOT_STARTED: { color: 'default', label: '未开始' },
  PENDING: { color: 'processing', label: '待申报' },
  FILED: { color: 'cyan', label: '已申报' },
  PAID: { color: 'success', label: '已缴款' },
  OVERDUE: { color: 'error', label: '已逾期' },
  ARCHIVED: { color: 'default', label: '已归档' },
  CANCELED: { color: 'default', label: '已取消' },
};

/** 申报周期映射 */
const cycleConfig: Record<string, string> = {
  MONTHLY: '月报',
  QUARTERLY: '季报',
  YEARLY: '年报',
  ANNUAL_SETTLEMENT: '汇算清缴',
};

/** 纳税人类型映射 */
const taxpayerConfig: Record<string, string> = {
  SMALL: '小规模',
  GENERAL: '一般纳税人',
  INDIVIDUAL: '个体',
};

/**
 * 报税日历页。
 * <p>
 * 代账公司的核心工作台。两个视图：
 * </p>
 * <ul>
 *   <li><b>月历</b>：按截止日铺满整月，含全部状态（待申报/已申报/已缴款/逾期），
 *       逾期红色高亮。会计据此判断「这个月还有哪些没报」。</li>
 *   <li><b>档案</b>：客户 × 税种 × 周期 的申报配置，支撑月历自动生成记录。</li>
 * </ul>
 * <p>
 * 状态机按后端约束实现：PENDING/OVERDUE → 已申报 → 已缴款 → 已归档；
 * 逾期记录可走「补报」一步到位。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function TaxCalendar() {
  const { message, modal } = App.useApp();

  /**
   * 当前月份。
   * <p>
   * 不能固定默认本月：申报截止日通常落在<b>次月</b>（10 月所属期在 11 月截止），
   * 月初打开页面往往是空月，会计得自己翻。改为首屏用后端建议的
   * 「本月或下一个有记录的月」，用户手动切月后不再被覆盖。
   * </p>
   */
  const [month, setMonth] = useState<Dayjs>(dayjs());
  /** 用户是否已手动选过月份（选过后不再自动跳转） */
  const [monthTouched, setMonthTouched] = useState(false);

  // 月历记录
  const [records, setRecords] = useState<TaxRecord[]>([]);
  const [recordsLoading, setRecordsLoading] = useState(false);
  /** 批量申报选中的记录 ID（仅 NOT_STARTED/PENDING/OVERDUE 可勾选） */
  const [selectedRecordIds, setSelectedRecordIds] = useState<string[]>([]);

  // 申报档案
  const [profiles, setProfiles] = useState<TaxProfile[]>([]);
  const [profileTotal, setProfileTotal] = useState(0);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profilePage, setProfilePage] = useState(0);
  const [taxTypeFilter, setTaxTypeFilter] = useState<string | undefined>();

  // 建档弹窗
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm<TaxProfilePayload>();
  const [customerOptions, setCustomerOptions] = useState<CustomerOption[]>([]);
  const [customerLoading, setCustomerLoading] = useState(false);

  // 申报动作弹窗
  const [actionRecord, setActionRecord] = useState<TaxRecord | null>(null);
  const [actionType, setActionType] = useState<'filed' | 'paid' | 'backfill' | null>(null);
  const [actionSubmitting, setActionSubmitting] = useState(false);
  const [actionForm] = Form.useForm<{
    taxAmount?: number;
    paidAmount?: number;
    receiptNo?: string;
  }>();

  /** 加载月历 */
  const loadRecords = useCallback(async (m: Dayjs) => {
    setRecordsLoading(true);
    try {
      const list = await listRecordsByMonth(m.year(), m.month() + 1);
      setRecords(list || []);
    } catch {
      // client.ts 已统一提示
    } finally {
      setRecordsLoading(false);
    }
  }, []);

  /** 加载档案 */
  const loadProfiles = useCallback(async () => {
    setProfileLoading(true);
    try {
      const result = await listTaxProfiles({
        taxType: taxTypeFilter,
        page: profilePage,
        size: 10,
      });
      setProfiles(result.content || []);
      setProfileTotal(result.totalElements || 0);
    } catch {
      // 已统一提示
    } finally {
      setProfileLoading(false);
    }
  }, [taxTypeFilter, profilePage]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const suggestion = await suggestCalendarMonth();
        if (cancelled || !suggestion) return;
        const suggested = dayjs(`${suggestion.year}-${String(suggestion.month).padStart(2, '0')}-01`);
        // 用户已经手动切过月就尊重用户选择
        if (!monthTouched && suggested.isValid() && !suggested.isSame(dayjs(), 'month')) {
          setMonth(suggested);
        }
      } catch {
        // 建议失败时留在本月，不影响主流程
      }
    })();
    return () => {
      cancelled = true;
    };
    // 仅首屏执行一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void loadRecords(month);
  }, [loadRecords, month]);

  useEffect(() => {
    void loadProfiles();
  }, [loadProfiles]);

  /** 客户远程搜索 */
  const handleCustomerSearch = useCallback(async (keyword: string) => {
    setCustomerLoading(true);
    try {
      const result = await searchCustomers(keyword || undefined, 20);
      setCustomerOptions(result.content || []);
    } catch {
      // 已统一提示
    } finally {
      setCustomerLoading(false);
    }
  }, []);

  /** 月历统计 */
  const stats = useMemo(() => {
    const counter = (status: string) => records.filter((r) => r.declarationStatus === status).length;
    return {
      total: records.length,
      pending: counter('PENDING') + counter('NOT_STARTED'),
      filed: counter('FILED'),
      paid: counter('PAID'),
      overdue: counter('OVERDUE'),
      overdueAmount: records
        .filter((r) => r.declarationStatus === 'OVERDUE')
        .reduce((sum, r) => sum + (r.taxAmount || 0), 0),
    };
  }, [records]);

  /** 打开动作弹窗 */
  /**
   * 批量申报（征期收尾高频动作）。
   * <p>逐条由后端校验状态：仅 NOT_STARTED/PENDING/OVERDUE 可申报，其余跳过并在
   * 结果里列出，不阻塞整批。申报 ≠ 缴款，缴款需逐条填实缴金额。</p>
   */
  const handleBatchFiled = () => {
    if (selectedRecordIds.length === 0) return;
    modal.confirm({
      title: `批量申报 ${selectedRecordIds.length} 条记录？`,
      content:
        '逐条标记为「已申报」；状态不允许的记录会被跳过并在结果中列出。申报不等于缴款，缴款仍需逐条填写实缴金额。',
      onOk: async () => {
        try {
          const result = await batchMarkFiled(selectedRecordIds);
          setSelectedRecordIds([]);
          if (result.failCount > 0) {
            message.warning(
              `成功 ${result.successCount} 条，失败 ${result.failCount} 条：` +
                result.failures
                  .slice(0, 3)
                  .map((f) => f.message)
                  .join('；'),
            );
          } else {
            message.success(`已批量申报 ${result.successCount} 条`);
          }
          void loadRecords(month);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  const openAction = (record: TaxRecord, type: 'filed' | 'paid' | 'backfill') => {
    setActionRecord(record);
    setActionType(type);
    actionForm.resetFields();
    actionForm.setFieldsValue({
      taxAmount: record.taxAmount,
      paidAmount: record.paidAmount,
      receiptNo: record.taxReceiptNo,
    });
  };

  /** 提交动作 */
  const submitAction = async () => {
    if (!actionRecord || !actionType) return;
    const values = await actionForm.validateFields();
    setActionSubmitting(true);
    try {
      if (actionType === 'filed') {
        await markRecordFiled(actionRecord.id, values.taxAmount, values.receiptNo);
        message.success('已标记为已申报');
      } else if (actionType === 'paid') {
        await markRecordPaid(actionRecord.id, values.paidAmount, values.receiptNo);
        message.success('已标记为已缴款');
      } else {
        await markRecordBackFilled(actionRecord.id, {
          taxAmount: values.taxAmount,
          paidAmount: values.paidAmount,
          receiptNo: values.receiptNo,
        });
        message.success('补报完成');
      }
      setActionRecord(null);
      setActionType(null);
      await loadRecords(month);
    } catch {
      // 已统一提示
    } finally {
      setActionSubmitting(false);
    }
  };

  /** 归档 */
  const handleArchive = (record: TaxRecord) => {
    modal.confirm({
      title: '确认归档该申报记录？',
      content: '归档后记录进入历史，不再出现在月历的待办统计中。',
      onOk: async () => {
        try {
          await archiveRecord(record.id);
          message.success('已归档');
          await loadRecords(month);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 取消 */
  const handleCancel = (record: TaxRecord) => {
    let remark = '';
    modal.confirm({
      title: '确认取消该申报记录？',
      content: (
        <Input
          placeholder="取消原因（减免/政策豁免等，可空）"
          onChange={(e) => {
            remark = e.target.value;
          }}
        />
      ),
      onOk: async () => {
        try {
          await cancelRecord(record.id, remark || undefined);
          message.success('已取消');
          await loadRecords(month);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 创建档案 */
  const submitCreate = async () => {
    const values = await form.validateFields();
    setCreating(true);
    try {
      await createTaxProfile({
        ...values,
        startDate: values.startDate,
        endDate: values.endDate || undefined,
      });
      message.success('申报档案已创建，调度将在次日 01:00 生成当期申报记录');
      setCreateOpen(false);
      form.resetFields();
      setProfilePage(0);
      await loadProfiles();
    } catch {
      // 已统一提示
    } finally {
      setCreating(false);
    }
  };

  /** 删除档案 */
  const handleDeleteProfile = (profile: TaxProfile) => {
    modal.confirm({
      title: '确认删除该申报档案？',
      content: '删除后该客户不再自动生成申报记录，历史记录保留。',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteTaxProfile(profile.id);
          message.success('已删除');
          await loadProfiles();
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 月历列定义 */
  const recordColumns: ColumnsType<TaxRecord> = [
    {
      title: '截止日',
      dataIndex: 'dueDate',
      width: 130,
      render: (value: string) => {
        const due = dayjs(value);
        const isPast = due.isBefore(dayjs(), 'day');
        return (
          <span style={{ color: isPast ? '#ff4d4f' : undefined, fontWeight: isPast ? 600 : undefined }}>
            {due.format('MM-DD')}
            <span style={{ marginLeft: 6, fontSize: 12, color: '#999' }}>
              {due.format('dd')}
            </span>
          </span>
        );
      },
    },
    {
      title: '税种',
      dataIndex: 'taxType',
      width: 110,
      render: (value: string) => {
        const cfg = taxTypeConfig[value] || { color: 'default', label: value };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '所属期',
      key: 'period',
      width: 180,
      render: (_, record) =>
        `${dayjs(record.periodStart).format('YYYY-MM-DD')} ~ ${dayjs(record.periodEnd).format('MM-DD')}`,
    },
    {
      title: '状态',
      dataIndex: 'declarationStatus',
      width: 100,
      render: (value: string, record) => {
        const cfg = statusConfig[value] || { color: 'default', label: value };
        if (value === 'OVERDUE') {
          return (
            <Tooltip title={`逾期 ${record.overdueDays ?? 0} 天`}>
              <Badge status="error" text={<strong style={{ color: '#ff4d4f' }}>{cfg.label}</strong>} />
            </Tooltip>
          );
        }
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '申报税额',
      dataIndex: 'taxAmount',
      width: 120,
      align: 'right',
      render: (value: number) => (value == null ? '-' : `¥ ${value.toLocaleString()}`),
    },
    {
      title: '实缴税额',
      dataIndex: 'paidAmount',
      width: 120,
      align: 'right',
      render: (value: number) => (value == null ? '-' : `¥ ${value.toLocaleString()}`),
    },
    {
      title: '完税凭证',
      dataIndex: 'taxReceiptNo',
      width: 160,
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_, record) => {
        const status = record.declarationStatus;
        return (
          <Space size="small" wrap>
            {(status === 'PENDING' || status === 'NOT_STARTED') && (
              <Button size="small" type="primary" onClick={() => openAction(record, 'filed')}>
                申报完成
              </Button>
            )}
            {status === 'FILED' && (
              <Button size="small" type="primary" onClick={() => openAction(record, 'paid')}>
                缴款完成
              </Button>
            )}
            {status === 'OVERDUE' && (
              <Button size="small" danger onClick={() => openAction(record, 'backfill')}>
                补报
              </Button>
            )}
            {status === 'PAID' && (
              <Button size="small" onClick={() => handleArchive(record)}>归档</Button>
            )}
            {(status === 'PENDING' || status === 'NOT_STARTED') && (
              <Button size="small" onClick={() => handleCancel(record)}>取消</Button>
            )}
          </Space>
        );
      },
    },
  ];

  /** 档案列定义 */
  const profileColumns: ColumnsType<TaxProfile> = [
    { title: '客户', dataIndex: 'customerName', width: 200, ellipsis: true },
    {
      title: '税种',
      dataIndex: 'taxType',
      width: 110,
      render: (value: string) => {
        const cfg = taxTypeConfig[value] || { color: 'default', label: value };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '周期',
      dataIndex: 'declarationCycle',
      width: 100,
      render: (value: string) => cycleConfig[value] || value || '-',
    },
    {
      title: '纳税人',
      dataIndex: 'taxpayerType',
      width: 110,
      render: (value: string) => taxpayerConfig[value] || value || '-',
    },
    { title: '征期规则', dataIndex: 'deadlineRule', width: 110 },
    {
      title: '生效起',
      dataIndex: 'startDate',
      width: 110,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD') : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>{value === 'ACTIVE' ? '生效中' : value || '-'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) => (
        <Button size="small" danger onClick={() => handleDeleteProfile(record)}>删除</Button>
      ),
    },
  ];

  return (
    <div>
      {/* 月度概览 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={5}>
          <Card size="small">
            <Statistic title="本月申报任务" value={stats.total} suffix="条" />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="待申报"
              value={stats.pending}
              suffix="条"
              valueStyle={{ color: stats.pending > 0 ? '#1677ff' : undefined }}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic title="已申报/已缴款" value={stats.filed + stats.paid} suffix="条" valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="已逾期"
              value={stats.overdue}
              suffix="条"
              valueStyle={{ color: stats.overdue > 0 ? '#ff4d4f' : undefined }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card size="small">
            <Statistic
              title="逾期涉及税额"
              value={stats.overdueAmount}
              precision={2}
              prefix="¥"
              valueStyle={{ color: stats.overdue > 0 ? '#ff4d4f' : undefined }}
            />
          </Card>
        </Col>
      </Row>

      {/* 月历 */}
      <Card
        title="报税月历"
        style={{ marginBottom: 16 }}
        extra={
          <Space>
            <DatePicker
              picker="month"
              allowClear={false}
              value={month}
              onChange={(value) => {
                if (!value) return;
                setMonthTouched(true);
                setMonth(value);
              }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void loadRecords(month)}>
              刷新
            </Button>
            <Button
              type="primary"
              disabled={selectedRecordIds.length === 0}
              onClick={handleBatchFiled}
            >
              批量申报（{selectedRecordIds.length}）
            </Button>
          </Space>
        }
      >
        <Table<TaxRecord>
          rowKey="id"
          size="small"
          loading={recordsLoading}
          columns={recordColumns}
          dataSource={records}
          rowSelection={{
            selectedRowKeys: selectedRecordIds,
            onChange: (keys) => setSelectedRecordIds(keys as string[]),
            getCheckboxProps: (record) => ({
              disabled: !['NOT_STARTED', 'PENDING', 'OVERDUE'].includes(
                record.declarationStatus,
              ),
            }),
          }}
          locale={{ emptyText: <Empty description="本月没有申报任务（先在下方建申报档案，调度会自动生成记录）" /> }}
          pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
        />
      </Card>

      {/* 申报档案 */}
      <Card
        title="申报档案"
        extra={
          <Space>
            <Select
              allowClear
              placeholder="税种"
              style={{ width: 140 }}
              value={taxTypeFilter}
              onChange={(value) => {
                setTaxTypeFilter(value);
                setProfilePage(0);
              }}
              options={Object.entries(taxTypeConfig).map(([value, cfg]) => ({
                value,
                label: cfg.label,
              }))}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void loadProfiles()}>刷新</Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                form.resetFields();
                setCreateOpen(true);
                void handleCustomerSearch('');
              }}
            >
              新建档案
            </Button>
          </Space>
        }
      >
        <Table<TaxProfile>
          rowKey="id"
          size="small"
          loading={profileLoading}
          columns={profileColumns}
          dataSource={profiles}
          locale={{
            emptyText: <Empty description="还没有申报档案。新建后，调度每日 01:00 会按周期自动生成申报记录" />,
          }}
          pagination={{
            current: profilePage + 1,
            pageSize: 10,
            total: profileTotal,
            showSizeChanger: false,
            onChange: (p) => setProfilePage(p - 1),
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      </Card>

      {/* 建档弹窗 */}
      <Modal
        title="新建申报档案"
        open={createOpen}
        confirmLoading={creating}
        onOk={() => void submitCreate()}
        onCancel={() => setCreateOpen(false)}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ taxpayerType: 'SMALL' }}>
          <Form.Item
            name="customerId"
            label="客户"
            rules={[{ required: true, message: '请选择客户' }]}
            extra="档案挂在客户上，后端会自动解析其所属公司主体"
          >
            <Select
              showSearch
              placeholder="输入昵称或手机号搜索"
              filterOption={false}
              loading={customerLoading}
              onSearch={(value) => void handleCustomerSearch(value)}
              onDropdownVisibleChange={(open) => {
                if (open && customerOptions.length === 0) void handleCustomerSearch('');
              }}
              options={customerOptions.map((c) => ({
                value: c.id,
                label: `${c.nickname || '(无昵称)'}${c.phone ? ` · ${c.phone}` : ''}`,
              }))}
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="taxType" label="税种" rules={[{ required: true, message: '请选择税种' }]}>
                <Select
                  options={Object.entries(taxTypeConfig).map(([value, cfg]) => ({
                    value,
                    label: `${cfg.label}（${value}）`,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="taxpayerType" label="纳税人类型">
                <Select
                  options={Object.entries(taxpayerConfig).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="declarationCycle"
                label="申报周期"
                extra="留空按税种给默认值（增值税/企业所得税季报，个税/社保月报）"
              >
                <Select allowClear options={Object.entries(cycleConfig).map(([value, label]) => ({ value, label }))} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="deadlineRule"
                label="征期规则"
                extra="留空按税种给默认值"
              >
                <Select
                  allowClear
                  options={[
                    { value: 'DAY15', label: '次月 15 日' },
                    { value: 'DAY31', label: '月末' },
                    { value: 'DAY05_31', label: '5 月 31 日（汇算清缴）' },
                    { value: 'LOCAL', label: '属地规则' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="startDate"
                label="生效开始日期"
                rules={[{ required: true, message: '请选择生效开始日期' }]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="endDate" label="生效结束日期" extra="留空表示长期有效">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="region" label="属地">
            <Input placeholder="如 陕西省西安市" />
          </Form.Item>
          <Form.Item name="declarationOrg" label="申报机关">
            <Input placeholder="如 国家税务总局西安市雁塔区税务局" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 申报动作弹窗 */}
      <Modal
        title={
          actionType === 'filed' ? '标记申报完成' : actionType === 'paid' ? '标记缴款完成' : '补报完成'
        }
        open={!!actionRecord && !!actionType}
        confirmLoading={actionSubmitting}
        onOk={() => void submitAction()}
        onCancel={() => {
          setActionRecord(null);
          setActionType(null);
        }}
        destroyOnClose
      >
        {actionRecord && (
          <div style={{ marginBottom: 16, color: '#666' }}>
            <div>
              税种：{(taxTypeConfig[actionRecord.taxType] || { label: actionRecord.taxType }).label}
            </div>
            <div>
              所属期：{dayjs(actionRecord.periodStart).format('YYYY-MM-DD')} ~{' '}
              {dayjs(actionRecord.periodEnd).format('YYYY-MM-DD')}
            </div>
            <div>截止日：{dayjs(actionRecord.dueDate).format('YYYY-MM-DD')}</div>
          </div>
        )}
        <Form form={actionForm} layout="vertical">
          {(actionType === 'filed' || actionType === 'backfill') && (
            <Form.Item name="taxAmount" label="申报税额（元）">
              <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder="0.00" />
            </Form.Item>
          )}
          {(actionType === 'paid' || actionType === 'backfill') && (
            <Form.Item name="paidAmount" label="实缴税额（元）">
              <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder="0.00" />
            </Form.Item>
          )}
          <Form.Item name="receiptNo" label="完税凭证号">
            <Input placeholder="选填，用于日后核对" maxLength={100} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
