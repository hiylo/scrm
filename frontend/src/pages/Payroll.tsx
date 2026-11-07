/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : Payroll.tsx
 * Date : 2026/10/25 14:40:00
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
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import {
  createEmployee,
  deleteEmployee,
  generatePayroll,
  getIitSummary,
  getSocialSummary,
  listCompanies,
  listEmployees,
  listIitRecords,
  listSocialRecords,
  markIitDeclared,
  markIitPaid,
  markSocialDeclared,
  markSocialPaid,
  updateEmployee,
  type Employee,
  type IitRecord,
  type IitSummary,
  type SocialRecord,
  type SocialSummary,
} from '../api/finance';

/** 社保/个税明细状态映射 */
const recordStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待申报' },
  DECLARED: { color: 'processing', label: '已申报' },
  PAID: { color: 'success', label: '已缴费' },
};

/** 参保状态映射 */
const socialStatusConfig: Record<string, { color: string; label: string }> = {
  INSURED: { color: 'green', label: '参保' },
  SUSPENDED: { color: 'orange', label: '暂停' },
  NOT_INSURED: { color: 'default', label: '未参保' },
};

/** 在职状态映射 */
const employeeStatusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '在职' },
  LEFT: { color: 'default', label: '离职' },
};

/** 金额格式化（千分位 + 两位小数） */
const money = (value?: number | null) =>
  value == null ? '-' : value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * 薪酬页（员工档案 + 社保明细 + 个税明细）。
 * <p>
 * 三块是串行的月度动作：先维护员工档案，再按月生成社保分项，
 * 最后按累计预扣法算个税。因此放在一个页面用 Tabs 串联，
 * 会计按顺序做完即可，不必在多个页面之间来回跳。
 * </p>
 * <p>
 * 页面顶部的「所属期」与「企业」是三个 Tab 的共享上下文——
 * 因为社保和个税都必须按「同一公司 + 同一个月」来看，分开选容易对错月份。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function Payroll() {
  const { message, modal } = App.useApp();

  const [activeTab, setActiveTab] = useState('employee');

  // ============ 共享上下文：企业 + 所属期 ============
  const [companyId, setCompanyId] = useState<string | undefined>();
  const [period, setPeriod] = useState<Dayjs>(dayjs());
  const [companyOptions, setCompanyOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [companyLoading, setCompanyLoading] = useState(false);

  // ============ 员工 ============
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [employeeTotal, setEmployeeTotal] = useState(0);
  const [employeeLoading, setEmployeeLoading] = useState(false);
  const [employeePage, setEmployeePage] = useState(0);
  const [employeeKeyword, setEmployeeKeyword] = useState('');
  const [employeeStatus, setEmployeeStatus] = useState<string | undefined>('ACTIVE');
  const [employeeModalOpen, setEmployeeModalOpen] = useState(false);
  const [editingEmployee, setEditingEmployee] = useState<Employee | null>(null);
  const [employeeSubmitting, setEmployeeSubmitting] = useState(false);
  const [employeeForm] = Form.useForm<Employee>();

  // ============ 社保 ============
  const [socialRecords, setSocialRecords] = useState<SocialRecord[]>([]);
  const [socialSummary, setSocialSummary] = useState<SocialSummary | null>(null);
  const [socialLoading, setSocialLoading] = useState(false);

  // ============ 个税 ============
  const [iitRecords, setIitRecords] = useState<IitRecord[]>([]);
  const [iitSummary, setIitSummary] = useState<IitSummary | null>(null);
  const [iitLoading, setIitLoading] = useState(false);

  // ============ 批量生成弹窗 ============
  const [generateOpen, setGenerateOpen] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [incomeForm] = Form.useForm<Record<string, number>>();

  const year = period.year();
  const month = period.month() + 1;

  /** 加载企业选项 */
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

  /** 加载员工 */
  const loadEmployees = useCallback(async () => {
    setEmployeeLoading(true);
    try {
      const result = await listEmployees({
        companyId,
        status: employeeStatus,
        keyword: employeeKeyword || undefined,
        page: employeePage,
        size: 10,
      });
      setEmployees(result.content || []);
      setEmployeeTotal(result.totalElements || 0);
    } catch {
      // 已统一提示
    } finally {
      setEmployeeLoading(false);
    }
  }, [companyId, employeeStatus, employeeKeyword, employeePage]);

  /** 加载社保明细与汇总 */
  const loadSocial = useCallback(async () => {
    if (!companyId) {
      setSocialRecords([]);
      setSocialSummary(null);
      return;
    }
    setSocialLoading(true);
    try {
      const [records, summary] = await Promise.all([
        listSocialRecords(companyId, year, month),
        getSocialSummary(year, month, companyId),
      ]);
      setSocialRecords(records || []);
      setSocialSummary(summary);
    } catch {
      // 已统一提示
    } finally {
      setSocialLoading(false);
    }
  }, [companyId, year, month]);

  /** 加载个税明细与汇总 */
  const loadIit = useCallback(async () => {
    if (!companyId) {
      setIitRecords([]);
      setIitSummary(null);
      return;
    }
    setIitLoading(true);
    try {
      const [records, summary] = await Promise.all([
        listIitRecords(companyId, year, month),
        getIitSummary(year, month, companyId),
      ]);
      setIitRecords(records || []);
      setIitSummary(summary);
    } catch {
      // 已统一提示
    } finally {
      setIitLoading(false);
    }
  }, [companyId, year, month]);

  useEffect(() => {
    void loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    void loadEmployees();
  }, [loadEmployees]);

  useEffect(() => {
    if (activeTab === 'social') void loadSocial();
    if (activeTab === 'iit') void loadIit();
  }, [activeTab, loadSocial, loadIit]);

  /** 打开员工弹窗 */
  const openEmployeeModal = (record?: Employee) => {
    setEditingEmployee(record ?? null);
    employeeForm.resetFields();
    if (record) {
      employeeForm.setFieldsValue({
        ...record,
        hireDate: record.hireDate as unknown as string,
      });
    } else {
      employeeForm.setFieldsValue({
        socialStatus: 'INSURED',
        status: 'ACTIVE',
        specialDeduction: 0,
      } as unknown as Employee);
    }
    setEmployeeModalOpen(true);
  };

  /** 保存员工 */
  const submitEmployee = async () => {
    const values = await employeeForm.validateFields();
    setEmployeeSubmitting(true);
    try {
      const payload: Partial<Employee> = {
        ...values,
        hireDate: values.hireDate
          ? dayjs(values.hireDate as unknown as dayjs.Dayjs).format('YYYY-MM-DD')
          : undefined,
      };
      if (editingEmployee) {
        await updateEmployee(editingEmployee.id, payload);
        message.success('员工信息已更新');
      } else {
        if (!companyId) {
          message.warning('请先在页面顶部选择企业');
          return;
        }
        await createEmployee({ ...payload, companyId });
        message.success('员工已添加');
      }
      setEmployeeModalOpen(false);
      await loadEmployees();
    } catch {
      // 已统一提示
    } finally {
      setEmployeeSubmitting(false);
    }
  };

  /** 办理离职 */
  const handleLeave = (record: Employee) => {
    modal.confirm({
      title: `为「${record.name}」办理离职？`,
      content: '离职后该员工不再参与社保与个税计提，历史明细保留。',
      onOk: async () => {
        try {
          await updateEmployee(record.id, {
            status: 'LEFT',
            leaveDate: dayjs().format('YYYY-MM-DD'),
          });
          message.success('已办理离职');
          await loadEmployees();
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 删除员工 */
  const handleDeleteEmployee = (record: Employee) => {
    modal.confirm({
      title: `确认删除「${record.name}」？`,
      content: '仅从未产生社保/个税明细的员工可删除；已有历史账请改用「离职」。',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteEmployee(record.id);
          message.success('已删除');
          await loadEmployees();
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 打开批量生成弹窗（预填在职员工） */
  const openGenerate = async () => {
    if (!companyId) {
      message.warning('请先在页面顶部选择企业');
      return;
    }
    try {
      const result = await listEmployees({ companyId, status: 'ACTIVE', page: 0, size: 200 });
      const active = result.content || [];
      if (active.length === 0) {
        message.warning('该企业没有在职员工，请先添加员工档案');
        return;
      }
      // 预填社保基数作为收入占位，会计按实际工资调整
      const initial: Record<string, number> = {};
      active.forEach((e) => {
        initial[e.id] = Number(e.socialBase || 0);
      });
      incomeForm.setFieldsValue(initial);
      setGenerateOpen(true);
    } catch {
      // 已统一提示
    }
  };

  /** 执行批量生成 */
  const submitGenerate = async () => {
    if (!companyId) return;
    const values = await incomeForm.validateFields();
    const incomes = Object.entries(values)
      .filter(([, v]) => v != null && Number(v) > 0)
      .map(([k, v]) => `${k}:${v}`)
      .join(',');
    setGenerating(true);
    try {
      const result = await generatePayroll(companyId, year, month, incomes || undefined);
      const recalcNote = Number(result.recalculated) > 0
        ? `；已联动重算后续月份 ${result.recalculated} 条（补齐历史月时累计税额需重算）`
        : '';
      const lockedNote = Number(result.skippedLocked) > 0
        ? `；${result.skippedLocked} 条已申报月份因已报送未改动，如需更正请走更正申报`
        : '';
      message.success(
        `生成完成：社保新增 ${result.socialCreated} 条、个税新增 ${result.iitCreated} 条` +
          `（已存在跳过 社保 ${result.socialSkipped} / 个税 ${result.iitSkipped}）` +
          recalcNote + lockedNote,
      );
      setGenerateOpen(false);
      await Promise.all([loadSocial(), loadIit()]);
    } catch {
      // 已统一提示
    } finally {
      setGenerating(false);
    }
  };

  /** 员工列 */
  const employeeColumns: ColumnsType<Employee> = [
    { title: '姓名', dataIndex: 'name', width: 110 },
    {
      title: '证件号',
      dataIndex: 'idCardMasked',
      width: 180,
      render: (v: string) => v || <span style={{ color: '#bbb' }}>未录入</span>,
    },
    { title: '岗位', dataIndex: 'position', width: 130, render: (v: string) => v || '-' },
    {
      title: '入职日期',
      dataIndex: 'hireDate',
      width: 120,
      render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD') : '-'),
    },
    {
      title: '参保状态',
      dataIndex: 'socialStatus',
      width: 100,
      render: (v: string) => {
        const cfg = socialStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '社保基数',
      dataIndex: 'socialBase',
      width: 110,
      align: 'right',
      render: (v: number) => (v == null ? <span style={{ color: '#bbb' }}>按下限</span> : money(v)),
    },
    {
      title: '专项附加扣除',
      dataIndex: 'specialDeduction',
      width: 120,
      align: 'right',
      render: (v: number) => money(v ?? 0),
    },
    {
      title: '在职状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const cfg = employeeStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_, record) => (
        <Space size="small">
          <Button size="small" onClick={() => openEmployeeModal(record)}>编辑</Button>
          {record.status === 'ACTIVE' && (
            <Button size="small" onClick={() => handleLeave(record)}>离职</Button>
          )}
          <Button size="small" danger onClick={() => handleDeleteEmployee(record)}>删除</Button>
        </Space>
      ),
    },
  ];

  /** 社保列 */
  const socialColumns: ColumnsType<SocialRecord> = [
    { title: '姓名', dataIndex: 'employeeName', width: 100, fixed: 'left' },
    {
      title: '基数',
      dataIndex: 'baseAmount',
      width: 90,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '养老(个人)',
      dataIndex: 'pensionPersonal',
      width: 100,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '医疗(个人)',
      dataIndex: 'medicalPersonal',
      width: 100,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '失业(个人)',
      dataIndex: 'unemploymentPersonal',
      width: 100,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '公积金(个人)',
      dataIndex: 'housingFundPersonal',
      width: 110,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '个人合计',
      dataIndex: 'totalPersonal',
      width: 100,
      align: 'right',
      render: (v: number) => <strong>{money(v)}</strong>,
    },
    {
      title: '单位合计',
      dataIndex: 'totalCompany',
      width: 100,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '总计',
      dataIndex: 'totalAmount',
      width: 110,
      align: 'right',
      render: (v: number) => <strong style={{ color: '#1677ff' }}>{money(v)}</strong>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = recordStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {record.status === 'PENDING' && (
            <Button
              size="small"
              onClick={async () => {
                try {
                  await markSocialDeclared(record.id);
                  message.success('已标记申报');
                  await loadSocial();
                } catch {
                  // 已统一提示
                }
              }}
            >
              已申报
            </Button>
          )}
          {record.status !== 'PAID' && (
            <Button
              size="small"
              type="primary"
              onClick={async () => {
                try {
                  await markSocialPaid(record.id);
                  message.success('已标记缴费');
                  await loadSocial();
                } catch {
                  // 已统一提示
                }
              }}
            >
              已缴费
            </Button>
          )}
          {record.status === 'PAID' && <span style={{ color: '#bbb' }}>已完成</span>}
        </Space>
      ),
    },
  ];

  /** 个税列 */
  const iitColumns: ColumnsType<IitRecord> = [
    { title: '姓名', dataIndex: 'employeeName', width: 100, fixed: 'left' },
    {
      title: '本期收入',
      dataIndex: 'incomeAmount',
      width: 110,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '减除费用',
      dataIndex: 'basicDeduction',
      width: 100,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '专项扣除',
      dataIndex: 'socialDeduction',
      width: 100,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '专项附加',
      dataIndex: 'specialDeduction',
      width: 100,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '累计应纳税所得额',
      dataIndex: 'cumulativeTaxable',
      width: 150,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '税率',
      dataIndex: 'taxRate',
      width: 80,
      align: 'right',
      render: (v: number) => `${(Number(v || 0) * 100).toFixed(0)}%`,
    },
    {
      title: '累计已扣',
      dataIndex: 'paidTax',
      width: 100,
      align: 'right',
      render: (v: number) => money(v),
    },
    {
      title: '本期应扣',
      dataIndex: 'currentTax',
      width: 110,
      align: 'right',
      render: (v: number) => <strong style={{ color: Number(v) > 0 ? '#fa8c16' : undefined }}>{money(v)}</strong>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = recordStatusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          {record.status === 'PENDING' && (
            <Button
              size="small"
              onClick={async () => {
                try {
                  await markIitDeclared(record.id);
                  message.success('已标记申报');
                  await loadIit();
                } catch {
                  // 已统一提示
                }
              }}
            >
              已申报
            </Button>
          )}
          {record.status !== 'PAID' && (
            <Button
              size="small"
              type="primary"
              onClick={async () => {
                try {
                  await markIitPaid(record.id);
                  message.success('已标记缴款');
                  await loadIit();
                } catch {
                  // 已统一提示
                }
              }}
            >
              已缴款
            </Button>
          )}
          {record.status === 'PAID' && <span style={{ color: '#bbb' }}>已完成</span>}
        </Space>
      ),
    },
  ];

  /** 本期上下文提示 */
  const contextHint = useMemo(() => {
    if (!companyId) {
      return '请先在下方选择企业 —— 社保与个税必须按「同一公司 + 同一个月」查看，否则容易对错月份。';
    }
    return null;
  }, [companyId]);

  return (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space wrap size="large">
          <Space>
            <span style={{ color: '#666' }}>企业</span>
            <Select
              showSearch
              allowClear
              style={{ width: 360 }}
              placeholder="搜索企业名称"
              filterOption={false}
              loading={companyLoading}
              onSearch={(value) => void loadCompanies(value)}
              onChange={(value) => {
                setCompanyId(value);
                setEmployeePage(0);
              }}
              value={companyId}
              options={companyOptions}
            />
          </Space>
          <Space>
            <span style={{ color: '#666' }}>所属期</span>
            <DatePicker
              picker="month"
              allowClear={false}
              value={period}
              onChange={(value) => value && setPeriod(value)}
            />
          </Space>
          <Button icon={<ReloadOutlined />} onClick={() => void Promise.all([loadEmployees(), loadSocial(), loadIit()])}>
            刷新
          </Button>
          <Tooltip title="按当前所属期为该企业在职员工批量生成社保分项与个税（幂等，已存在的跳过）">
            <Button type="primary" icon={<ThunderboltOutlined />} onClick={() => void openGenerate()}>
              生成本月社保/个税
            </Button>
          </Tooltip>
        </Space>
      </Card>

      {contextHint && (
        <Alert type="info" showIcon style={{ marginBottom: 16 }} message={contextHint} />
      )}

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'employee',
            label: '员工档案',
            children: (
              <Card
                title={
                  <Space>
                    <span>员工档案</span>
                    <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                      社保与个税的唯一数据源；增减员是每月固定动作
                    </Typography.Text>
                  </Space>
                }
                extra={
                  <Space>
                    <Input.Search
                      allowClear
                      placeholder="姓名 / 岗位 / 电话"
                      style={{ width: 200 }}
                      onSearch={(value) => {
                        setEmployeeKeyword(value);
                        setEmployeePage(0);
                      }}
                    />
                    <Select
                      allowClear
                      placeholder="在职状态"
                      style={{ width: 120 }}
                      value={employeeStatus}
                      onChange={(value) => {
                        setEmployeeStatus(value);
                        setEmployeePage(0);
                      }}
                      options={Object.entries(employeeStatusConfig).map(([value, cfg]) => ({
                        value,
                        label: cfg.label,
                      }))}
                    />
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => openEmployeeModal()}>
                      新增员工
                    </Button>
                  </Space>
                }
              >
                <Table<Employee>
                  rowKey="id"
                  size="small"
                  loading={employeeLoading}
                  columns={employeeColumns}
                  dataSource={employees}
                  scroll={{ x: 1200 }}
                  locale={{ emptyText: <Empty description="还没有员工档案。先添加员工，才能生成社保和个税" /> }}
                  pagination={{
                    current: employeePage + 1,
                    pageSize: 10,
                    total: employeeTotal,
                    showSizeChanger: false,
                    onChange: (p) => setEmployeePage(p - 1),
                    showTotal: (t) => `共 ${t} 人`,
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'social',
            label: '社保明细',
            children: (
              <>
                <Row gutter={16} style={{ marginBottom: 16 }}>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic title="参保人数" value={socialSummary?.headcount ?? 0} suffix="人" />
                    </Card>
                  </Col>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic
                        title="个人合计"
                        value={socialSummary?.personalTotal ?? 0}
                        precision={2}
                        prefix="¥"
                      />
                    </Card>
                  </Col>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic
                        title="单位合计"
                        value={socialSummary?.companyTotal ?? 0}
                        precision={2}
                        prefix="¥"
                      />
                    </Card>
                  </Col>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic
                        title="缴费总额"
                        value={socialSummary?.grandTotal ?? 0}
                        precision={2}
                        prefix="¥"
                        valueStyle={{ color: '#1677ff' }}
                      />
                    </Card>
                  </Col>
                  <Col span={4}>
                    <Card size="small">
                      <Statistic
                        title="待申报"
                        value={socialSummary?.pending ?? 0}
                        suffix="人"
                        valueStyle={{ color: (socialSummary?.pending ?? 0) > 0 ? '#fa8c16' : undefined }}
                      />
                    </Card>
                  </Col>
                </Row>

                {(!companyId || socialRecords.length === 0) && (
                  <Alert
                    type="warning"
                    showIcon
                    style={{ marginBottom: 16 }}
                    message="当前所属期还没有社保明细"
                    description="点右上「生成本月社保/个税」，按在职员工批量计算分项；已存在的记录会跳过，不会覆盖人工调整过的数据。"
                  />
                )}

                <Card
                  title={
                    <Space>
                      <span>社保缴费明细</span>
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                        {period.format('YYYY 年 M 月')} · 基数按当地上下限自动截断
                      </Typography.Text>
                    </Space>
                  }
                >
                  <Table<SocialRecord>
                    rowKey="id"
                    size="small"
                    loading={socialLoading}
                    columns={socialColumns}
                    dataSource={socialRecords}
                    scroll={{ x: 1400 }}
                    locale={{ emptyText: <Empty description="暂无社保明细" /> }}
                    pagination={false}
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'iit',
            label: '个税明细',
            children: (
              <>
                <Row gutter={16} style={{ marginBottom: 16 }}>
                  <Col span={6}>
                    <Card size="small">
                      <Statistic title="申报人数" value={iitSummary?.headcount ?? 0} suffix="人" />
                    </Card>
                  </Col>
                  <Col span={6}>
                    <Card size="small">
                      <Statistic
                        title="收入合计"
                        value={iitSummary?.incomeTotal ?? 0}
                        precision={2}
                        prefix="¥"
                      />
                    </Card>
                  </Col>
                  <Col span={6}>
                    <Card size="small">
                      <Statistic
                        title="本期税额合计"
                        value={iitSummary?.taxTotal ?? 0}
                        precision={2}
                        prefix="¥"
                        valueStyle={{ color: '#fa8c16' }}
                      />
                    </Card>
                  </Col>
                  <Col span={6}>
                    <Card size="small">
                      <Statistic
                        title="待申报"
                        value={iitSummary?.pending ?? 0}
                        suffix="人"
                        valueStyle={{ color: (iitSummary?.pending ?? 0) > 0 ? '#fa8c16' : undefined }}
                      />
                    </Card>
                  </Col>
                </Row>

                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message="个税按「累计预扣法」计算，不是按月单独计税"
                  description="本期应扣 = 累计应纳税额 − 累计已预扣。因此越到年底税率可能越高，这是正常的跨档结果，不是算错。表中列出了累计收入与累计应纳税所得额，可直接与个税申报表逐项核对。"
                />

                <Card
                  title={
                    <Space>
                      <span>个税扣缴明细</span>
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                        {period.format('YYYY 年 M 月')}
                      </Typography.Text>
                    </Space>
                  }
                >
                  <Table<IitRecord>
                    rowKey="id"
                    size="small"
                    loading={iitLoading}
                    columns={iitColumns}
                    dataSource={iitRecords}
                    scroll={{ x: 1400 }}
                    locale={{ emptyText: <Empty description="暂无个税明细" /> }}
                    pagination={false}
                  />
                </Card>
              </>
            ),
          },
        ]}
      />

      {/* 员工弹窗 */}
      <Modal
        title={editingEmployee ? `编辑员工 · ${editingEmployee.name}` : '新增员工'}
        open={employeeModalOpen}
        confirmLoading={employeeSubmitting}
        onOk={() => void submitEmployee()}
        onCancel={() => setEmployeeModalOpen(false)}
        width={680}
        destroyOnClose
      >
        <Form form={employeeForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请填写姓名' }]}>
                <Input maxLength={50} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label="联系电话">
                <Input maxLength={20} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="idCardNo"
                label="证件号"
                extra="个税/社保申报必需，接口不回显明文，列表显示脱敏值"
              >
                <Input maxLength={30} placeholder="18 位身份证号" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="position" label="岗位">
                <Input maxLength={100} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="hireDate" label="入职日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="socialStatus" label="参保状态">
                <Select
                  options={Object.entries(socialStatusConfig).map(([value, cfg]) => ({
                    value,
                    label: cfg.label,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="在职状态">
                <Select
                  options={Object.entries(employeeStatusConfig).map(([value, cfg]) => ({
                    value,
                    label: cfg.label,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="socialBase" label="社保缴费基数" extra="留空按下限计缴">
                <InputNumber style={{ width: '100%' }} min={0} precision={2} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="housingFundBase" label="公积金基数" extra="留空沿用社保基数">
                <InputNumber style={{ width: '100%' }} min={0} precision={2} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="specialDeduction"
                label="专项附加扣除/月"
                extra="子女教育/房贷/赡养老人等合计"
              >
                <InputNumber style={{ width: '100%' }} min={0} precision={2} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 批量生成弹窗 */}
      <Modal
        title={`生成 ${period.format('YYYY 年 M 月')} 社保/个税`}
        open={generateOpen}
        confirmLoading={generating}
        onOk={() => void submitGenerate()}
        onCancel={() => setGenerateOpen(false)}
        width={680}
        okText="生成"
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="填写各员工本月税前收入，社保按档案基数计算"
          description="社保金额由员工档案的缴费基数自动算出，无需在此填写；个税以社保个人部分作为专项扣除，按累计预扣法计算。填 0 或留空的员工个税为 0。"
        />
        <Form form={incomeForm} layout="vertical">
          {employees
            .filter((e) => e.status === 'ACTIVE')
            .map((e) => (
              <Form.Item key={e.id} name={e.id} label={`${e.name}${e.position ? `（${e.position}）` : ''} 本月税前收入`}>
                <InputNumber
                  style={{ width: '100%' }}
                  min={0}
                  precision={2}
                  prefix="¥"
                  placeholder="0.00"
                />
              </Form.Item>
            ))}
        </Form>
      </Modal>
    </div>
  );
}
