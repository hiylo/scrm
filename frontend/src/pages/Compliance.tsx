/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : Compliance.tsx
 * Date : 2026/10/25 11:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
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
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  advanceCompliance,
  createQualification,
  deleteQualification,
  exemptAnnualReport,
  generateAnnualReports,
  getAnnualSummary,
  getQualificationSummary,
  listAnnualReports,
  listCompanies,
  listQualifications,
  remindCompliance,
  renewQualification,
  submitAnnualReport,
  updateQualification,
  type AnnualReport,
  type AnnualSummary,
  type Qualification,
  type QualificationSummary,
} from '../api/finance';

/** 年报状态映射 */
const annualStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'processing', label: '待报送' },
  SUBMITTED: { color: 'success', label: '已报送' },
  OVERDUE: { color: 'error', label: '已逾期' },
  EXEMPT: { color: 'default', label: '免报' },
  CANCELED: { color: 'default', label: '已撤销' },
};

/** 资质状态映射 */
const qualStatusConfig: Record<string, { color: string; label: string }> = {
  VALID: { color: 'green', label: '有效' },
  EXPIRING: { color: 'orange', label: '即将到期' },
  EXPIRED: { color: 'red', label: '已过期' },
  RENEWED: { color: 'cyan', label: '已续期' },
  CANCELED: { color: 'default', label: '已注销' },
};

/** 资质类型映射 */
const qualTypeConfig: Record<string, string> = {
  FOOD: '食品经营',
  HEALTH: '卫生许可',
  LABOR: '劳务派遣',
  FIRE: '消防验收',
  IMPORT_EXPORT: '进出口',
  OTHER: '其他',
};

/**
 * 合规到期页（工商年报 + 资质许可）。
 * <p>
 * 两项同属代账公司的<b>法定提醒责任</b>，放在一个页面是因为会计的实际问题是
 * 「这个月有哪些到期事项要处理」，而不是「年报归年报、证照归证照」。
 * </p>
 * <ul>
 *   <li><b>工商年报</b>：每年 1/1–6/30 报送，逾期进经营异常名录；</li>
 *   <li><b>资质许可</b>：食品经营、劳务派遣等证照到期前须提醒续办。</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function Compliance() {
  const { message, modal } = App.useApp();

  const [activeTab, setActiveTab] = useState('annual');

  // ============ 工商年报 ============
  const [reports, setReports] = useState<AnnualReport[]>([]);
  const [reportTotal, setReportTotal] = useState(0);
  const [reportLoading, setReportLoading] = useState(false);
  const [reportPage, setReportPage] = useState(0);
  const [reportStatus, setReportStatus] = useState<string | undefined>();
  const [reportYear, setReportYear] = useState<number | undefined>(dayjs().year() - 1);
  const [annualSummary, setAnnualSummary] = useState<AnnualSummary | null>(null);

  // ============ 资质许可 ============
  const [quals, setQuals] = useState<Qualification[]>([]);
  const [qualTotal, setQualTotal] = useState(0);
  const [qualLoading, setQualLoading] = useState(false);
  const [qualPage, setQualPage] = useState(0);
  const [qualStatus, setQualStatus] = useState<string | undefined>();
  const [qualKeyword, setQualKeyword] = useState('');
  const [qualSummary, setQualSummary] = useState<QualificationSummary | null>(null);

  // 证照弹窗
  const [qualModalOpen, setQualModalOpen] = useState(false);
  const [editingQual, setEditingQual] = useState<Qualification | null>(null);
  const [qualSubmitting, setQualSubmitting] = useState(false);
  const [qualForm] = Form.useForm<Qualification>();
  const [companyOptions, setCompanyOptions] = useState<Array<{ value: string; label: string }>>([]);
  const [companyLoading, setCompanyLoading] = useState(false);

  // 报送弹窗
  const [submitTarget, setSubmitTarget] = useState<AnnualReport | null>(null);
  const [submitForm] = Form.useForm<{ receiptNo?: string; remark?: string }>();
  const [submitSubmitting, setSubmitSubmitting] = useState(false);

  /** 加载年报 */
  const loadReports = useCallback(async () => {
    setReportLoading(true);
    try {
      const result = await listAnnualReports({
        status: reportStatus,
        year: reportYear,
        page: reportPage,
        size: 10,
      });
      setReports(result.content || []);
      setReportTotal(result.totalElements || 0);
    } catch {
      // client.ts 已统一提示
    } finally {
      setReportLoading(false);
    }
  }, [reportStatus, reportYear, reportPage]);

  /** 加载年报统计 */
  const loadAnnualSummary = useCallback(async () => {
    try {
      setAnnualSummary(await getAnnualSummary(reportYear));
    } catch {
      // 已统一提示
    }
  }, [reportYear]);

  /** 加载资质 */
  const loadQuals = useCallback(async () => {
    setQualLoading(true);
    try {
      const result = await listQualifications({
        status: qualStatus,
        keyword: qualKeyword || undefined,
        page: qualPage,
        size: 10,
      });
      setQuals(result.content || []);
      setQualTotal(result.totalElements || 0);
    } catch {
      // 已统一提示
    } finally {
      setQualLoading(false);
    }
  }, [qualStatus, qualKeyword, qualPage]);

  /** 加载资质统计 */
  const loadQualSummary = useCallback(async () => {
    try {
      setQualSummary(await getQualificationSummary());
    } catch {
      // 已统一提示
    }
  }, []);

  useEffect(() => {
    void loadReports();
    void loadAnnualSummary();
  }, [loadReports, loadAnnualSummary]);

  useEffect(() => {
    void loadQuals();
    void loadQualSummary();
  }, [loadQuals, loadQualSummary]);

  /** 企业远程搜索（证照挂主体，走公司列表接口而非用户列表） */
  const handleCompanySearch = async (keyword: string) => {
    setCompanyLoading(true);
    try {
      const result = await listCompanies({ keyword: keyword || undefined, page: 0, size: 20 });
      setCompanyOptions(
        (result.content || []).map((c) => ({
          value: c.id,
          label: c.creditCode ? `${c.companyName}（${c.creditCode}）` : c.companyName,
        })),
      );
    } catch {
      // 已统一提示
    } finally {
      setCompanyLoading(false);
    }
  };

  /** 立即推进合规状态（年报逾期 + 证照到期） */
  const handleAdvance = async () => {
    try {
      const result = await advanceCompliance();
      message.success(
        `已推进：年报转逾期 ${result.annualOverdue} 条，证照状态变更 ${result.qualificationChanged} 张`,
      );
      await Promise.all([
        loadReports(),
        loadAnnualSummary(),
        loadQuals(),
        loadQualSummary(),
      ]);
    } catch {
      // client.ts 已统一提示
    }
  };

  /** 立即推送到期提醒 */
  const handleRemind = async () => {
    try {
      const result = await remindCompliance();
      message.success(
        `已推送：年报提醒 ${result.annualPushed} 条，资质提醒 ${result.qualificationPushed} 条`,
      );
      await Promise.all([loadReports(), loadQuals()]);
    } catch {
      // 已统一提示
    }
  };

  /** 生成年报（手动补历史） */
  const handleGenerate = () => {
    modal.confirm({
      title: `为所有在服企业生成 ${reportYear ?? dayjs().year() - 1} 年度年报任务？`,
      content: '已存在的（公司 + 年度）不会重复生成，可安全重复执行。',
      onOk: async () => {
        try {
          const count = await generateAnnualReports(reportYear);
          message.success(`已生成 ${count} 条年报任务`);
          await Promise.all([loadReports(), loadAnnualSummary()]);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 提交报送 */
  const submitReport = async () => {
    if (!submitTarget) return;
    const values = await submitForm.validateFields();
    setSubmitSubmitting(true);
    try {
      await submitAnnualReport(submitTarget.id, values.receiptNo, values.remark);
      message.success('已标记为已报送');
      setSubmitTarget(null);
      await Promise.all([loadReports(), loadAnnualSummary()]);
    } catch {
      // 已统一提示
    } finally {
      setSubmitSubmitting(false);
    }
  };

  /** 免报 */
  const handleExempt = (record: AnnualReport) => {
    let remark = '';
    modal.confirm({
      title: '标记为免报？',
      content: (
        <Input.TextArea
          rows={2}
          placeholder="免报原因（必填，如：当年已注销 / 未开展经营）"
          onChange={(e) => {
            remark = e.target.value;
          }}
        />
      ),
      onOk: async () => {
        if (!remark.trim()) {
          message.warning('请填写免报原因');
          return Promise.reject(new Error('reason required'));
        }
        try {
          await exemptAnnualReport(record.id, remark.trim());
          message.success('已标记为免报');
          await Promise.all([loadReports(), loadAnnualSummary()]);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 打开证照弹窗 */
  const openQualModal = (record?: Qualification) => {
    setEditingQual(record ?? null);
    qualForm.resetFields();
    if (record) {
      qualForm.setFieldsValue({
        ...record,
        issueDate: record.issueDate as unknown as string,
        expireDate: record.expireDate as unknown as string,
      });
    } else {
      qualForm.setFieldsValue({
        qualificationType: 'FOOD',
        status: 'VALID',
        remindDaysBefore: 60,
      } as unknown as Qualification);
    }
    setQualModalOpen(true);
    if (!record) {
      void handleCompanySearch('');
    }
  };

  /** 保存证照 */
  const submitQual = async () => {
    const values = await qualForm.validateFields();
    setQualSubmitting(true);
    try {
      const payload: Partial<Qualification> = {
        ...values,
        issueDate: values.issueDate
          ? dayjs(values.issueDate as unknown as dayjs.Dayjs).format('YYYY-MM-DD')
          : undefined,
        expireDate: values.expireDate
          ? dayjs(values.expireDate as unknown as dayjs.Dayjs).format('YYYY-MM-DD')
          : undefined,
      };
      if (editingQual) {
        await updateQualification(editingQual.id, payload);
        message.success('证照已更新');
      } else {
        await createQualification(payload);
        message.success('证照已登记');
      }
      setQualModalOpen(false);
      await Promise.all([loadQuals(), loadQualSummary()]);
    } catch {
      // 已统一提示
    } finally {
      setQualSubmitting(false);
    }
  };

  /** 续期 */
  const handleRenew = (record: Qualification) => {
    let newExpire: string | null = null;
    modal.confirm({
      title: `续期：${record.qualificationName}`,
      content: (
        <div>
          <div style={{ marginBottom: 8, color: '#666' }}>
            当前到期日：{record.expireDate || '长期有效'}
          </div>
          <DatePicker
            style={{ width: '100%' }}
            placeholder="选择新的到期日"
            onChange={(value) => {
              newExpire = value ? value.format('YYYY-MM-DD') : null;
            }}
          />
        </div>
      ),
      onOk: async () => {
        if (!newExpire) {
          message.warning('请选择新的到期日');
          return Promise.reject(new Error('date required'));
        }
        try {
          await renewQualification(record.id, newExpire);
          message.success('已标记续期');
          await Promise.all([loadQuals(), loadQualSummary()]);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 删除证照 */
  const handleDeleteQual = (record: Qualification) => {
    modal.confirm({
      title: '确认删除该证照记录？',
      content: `${record.companyName || ''} ${record.qualificationName}`,
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteQualification(record.id);
          message.success('已删除');
          await Promise.all([loadQuals(), loadQualSummary()]);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 到期天数渲染 */
  const renderDaysLeft = (dateStr?: string) => {
    if (!dateStr) return <span style={{ color: '#999' }}>长期</span>;
    const days = dayjs(dateStr).diff(dayjs(), 'day');
    if (days < 0) {
      return <Tag color="red">已过期 {-days} 天</Tag>;
    }
    if (days <= 30) {
      return <Tag color="orange">剩 {days} 天</Tag>;
    }
    if (days <= 90) {
      return <Tag color="gold">剩 {days} 天</Tag>;
    }
    return <span>{days} 天</span>;
  };

  /** 年报列定义 */
  const reportColumns: ColumnsType<AnnualReport> = [
    { title: '企业名称', dataIndex: 'companyName', ellipsis: true },
    {
      title: '年度',
      dataIndex: 'reportYear',
      width: 90,
      render: (value: number) => `${value} 年度`,
    },
    {
      title: '截止日',
      dataIndex: 'dueDate',
      width: 160,
      render: (value: string) => (
        <Space size={4}>
          <span>{dayjs(value).format('YYYY-MM-DD')}</span>
          {renderDaysLeft(value)}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => {
        const cfg = annualStatusConfig[value] || { color: 'default', label: value };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '回执号',
      dataIndex: 'receiptNo',
      width: 160,
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: '提醒',
      dataIndex: 'remindCount',
      width: 70,
      align: 'right',
      render: (value: number) => value ?? 0,
    },
    {
      title: '操作',
      key: 'action',
      width: 190,
      render: (_, record) => {
        const actionable = ['PENDING', 'OVERDUE'].includes(record.status);
        return (
          <Space size="small" wrap>
            {actionable && (
              <Button
                size="small"
                type="primary"
                onClick={() => {
                  setSubmitTarget(record);
                  submitForm.resetFields();
                }}
              >
                标记已报送
              </Button>
            )}
            {actionable && (
              <Button size="small" onClick={() => handleExempt(record)}>免报</Button>
            )}
            {!actionable && <span style={{ color: '#bbb' }}>—</span>}
          </Space>
        );
      },
    },
  ];

  /** 资质列定义 */
  const qualColumns: ColumnsType<Qualification> = [
    { title: '企业名称', dataIndex: 'companyName', ellipsis: true },
    { title: '证照名称', dataIndex: 'qualificationName', width: 170, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'qualificationType',
      width: 110,
      render: (value: string) => qualTypeConfig[value] || value || '-',
    },
    { title: '证号', dataIndex: 'certNo', width: 150, ellipsis: true, render: (v: string) => v || '-' },
    {
      title: '到期日',
      dataIndex: 'expireDate',
      width: 160,
      render: (value: string) => (
        <Space size={4}>
          <span>{value ? dayjs(value).format('YYYY-MM-DD') : '长期'}</span>
          {renderDaysLeft(value)}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => {
        const cfg = qualStatusConfig[value] || { color: 'default', label: value };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="small" wrap>
          <Button size="small" onClick={() => openQualModal(record)}>编辑</Button>
          <Button size="small" type="primary" onClick={() => handleRenew(record)}>续期</Button>
          <Button size="small" danger onClick={() => handleDeleteQual(record)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="合规提醒由调度每日自动执行（01:00 推进状态 / 09:30 推送提醒）"
        description="年报截止 6 月 30 日，逾期进经营异常名录；证照到期前按各自设定天数提醒负责会计。下面是手动触发入口，用于即时纠偏与验证。"
        action={
          <Space>
            <Button size="small" icon={<ThunderboltOutlined />} onClick={() => void handleAdvance()}>
              立即推进状态
            </Button>
            <Button size="small" onClick={() => void handleRemind()}>立即推送提醒</Button>
          </Space>
        }
      />
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'annual',
            label: '工商年报',
            children: (
              <>
                <Row gutter={16} style={{ marginBottom: 16 }}>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic title="待报送" value={annualSummary?.pending ?? 0} suffix="户" />
                    </Card>
                  </Col>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic
                        title="已逾期"
                        value={annualSummary?.overdue ?? 0}
                        suffix="户"
                        valueStyle={{ color: (annualSummary?.overdue ?? 0) > 0 ? '#ff4d4f' : undefined }}
                      />
                    </Card>
                  </Col>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic
                        title="已报送"
                        value={annualSummary?.submitted ?? 0}
                        suffix="户"
                        valueStyle={{ color: '#52c41a' }}
                      />
                    </Card>
                  </Col>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic title="免报" value={annualSummary?.exempt ?? 0} suffix="户" />
                    </Card>
                  </Col>
                  <Col span={4}>
                    <Card size="small">
                      <Statistic title="合计" value={annualSummary?.total ?? 0} suffix="户" />
                    </Card>
                  </Col>
                </Row>

                {(annualSummary?.overdue ?? 0) > 0 && (
                  <Alert
                    type="error"
                    showIcon
                    style={{ marginBottom: 16 }}
                    message={`有 ${annualSummary?.overdue} 户工商年报已逾期`}
                    description="逾期会进入经营异常名录，影响招投标、贷款与政府采购。请尽快补报并移出异常。"
                  />
                )}

                <Card
                  title={
                    <Space>
                      <span>工商年报台账</span>
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                        每年 1 月 1 日至 6 月 30 日 报送上一年度报告
                      </Typography.Text>
                    </Space>
                  }
                  extra={
                    <Space>
                      <Select
                        allowClear
                        placeholder="年度"
                        style={{ width: 110 }}
                        value={reportYear}
                        onChange={(value) => {
                          setReportYear(value);
                          setReportPage(0);
                        }}
                        options={Array.from({ length: 6 }, (_, i) => {
                          const y = dayjs().year() - 1 - i;
                          return { value: y, label: `${y} 年度` };
                        })}
                      />
                      <Select
                        allowClear
                        placeholder="状态"
                        style={{ width: 120 }}
                        value={reportStatus}
                        onChange={(value) => {
                          setReportStatus(value);
                          setReportPage(0);
                        }}
                        options={Object.entries(annualStatusConfig).map(([value, cfg]) => ({
                          value,
                          label: cfg.label,
                        }))}
                      />
                      <Button icon={<ReloadOutlined />} onClick={() => void loadReports()}>刷新</Button>
                      <Tooltip title="为在服企业生成指定年度的年报任务（幂等，可重复执行）">
                        <Button icon={<ThunderboltOutlined />} onClick={handleGenerate}>
                          生成年报任务
                        </Button>
                      </Tooltip>
                    </Space>
                  }
                >
                  <Table<AnnualReport>
                    rowKey="id"
                    size="small"
                    loading={reportLoading}
                    columns={reportColumns}
                    dataSource={reports}
                    locale={{
                      emptyText: (
                        <Empty description="没有年报记录。点「生成年报任务」为在服企业批量创建" />
                      ),
                    }}
                    pagination={{
                      current: reportPage + 1,
                      pageSize: 10,
                      total: reportTotal,
                      showSizeChanger: false,
                      onChange: (p) => setReportPage(p - 1),
                      showTotal: (t) => `共 ${t} 户`,
                    }}
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'qualification',
            label: '资质许可',
            children: (
              <>
                <Row gutter={16} style={{ marginBottom: 16 }}>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic
                        title="90 天内到期"
                        value={qualSummary?.dueSoon90d ?? 0}
                        suffix="证"
                        valueStyle={{ color: (qualSummary?.dueSoon90d ?? 0) > 0 ? '#fa8c16' : undefined }}
                      />
                    </Card>
                  </Col>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic
                        title="已过期"
                        value={qualSummary?.expired ?? 0}
                        suffix="证"
                        valueStyle={{ color: (qualSummary?.expired ?? 0) > 0 ? '#ff4d4f' : undefined }}
                      />
                    </Card>
                  </Col>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic
                        title="即将到期"
                        value={qualSummary?.expiring ?? 0}
                        suffix="证"
                        valueStyle={{ color: (qualSummary?.expiring ?? 0) > 0 ? '#fa8c16' : undefined }}
                      />
                    </Card>
                  </Col>
                  <Col span={5}>
                    <Card size="small">
                      <Statistic title="有效" value={qualSummary?.valid ?? 0} suffix="证" valueStyle={{ color: '#52c41a' }} />
                    </Card>
                  </Col>
                  <Col span={4}>
                    <Card size="small">
                      <Statistic title="合计" value={qualSummary?.total ?? 0} suffix="证" />
                    </Card>
                  </Col>
                </Row>

                {(qualSummary?.expired ?? 0) > 0 && (
                  <Alert
                    type="error"
                    showIcon
                    style={{ marginBottom: 16 }}
                    message={`有 ${qualSummary?.expired} 张证照已过期`}
                    description="证照过期客户无法继续经营相关业务，代账方需承担提醒责任。请立即联系客户续办。"
                  />
                )}

                <Card
                  title={
                    <Space>
                      <span>资质许可台账</span>
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                        食品经营 / 劳务派遣等证照到期前 60 天自动提醒负责会计
                      </Typography.Text>
                    </Space>
                  }
                  extra={
                    <Space>
                      <Input.Search
                        allowClear
                        placeholder="证照名 / 证号 / 企业名"
                        style={{ width: 220 }}
                        onSearch={(value) => {
                          setQualKeyword(value);
                          setQualPage(0);
                        }}
                      />
                      <Select
                        allowClear
                        placeholder="状态"
                        style={{ width: 130 }}
                        value={qualStatus}
                        onChange={(value) => {
                          setQualStatus(value);
                          setQualPage(0);
                        }}
                        options={Object.entries(qualStatusConfig).map(([value, cfg]) => ({
                          value,
                          label: cfg.label,
                        }))}
                      />
                      <Button icon={<ReloadOutlined />} onClick={() => void loadQuals()}>刷新</Button>
                      <Button type="primary" icon={<PlusOutlined />} onClick={() => openQualModal()}>
                        登记证照
                      </Button>
                    </Space>
                  }
                >
                  <Table<Qualification>
                    rowKey="id"
                    size="small"
                    loading={qualLoading}
                    columns={qualColumns}
                    dataSource={quals}
                    locale={{
                      emptyText: (
                        <Empty description="还没有登记证照。客户持有的许可证需人工录入一次，之后系统自动提醒到期" />
                      ),
                    }}
                    pagination={{
                      current: qualPage + 1,
                      pageSize: 10,
                      total: qualTotal,
                      showSizeChanger: false,
                      onChange: (p) => setQualPage(p - 1),
                      showTotal: (t) => `共 ${t} 张`,
                    }}
                  />
                </Card>
              </>
            ),
          },
        ]}
      />

      {/* 报送弹窗 */}
      <Modal
        title={submitTarget ? `标记已报送 · ${submitTarget.companyName}` : '标记已报送'}
        open={!!submitTarget}
        confirmLoading={submitSubmitting}
        onOk={() => void submitReport()}
        onCancel={() => setSubmitTarget(null)}
        destroyOnClose
      >
        {submitTarget && (
          <div style={{ marginBottom: 16, color: '#666' }}>
            <div>
              年度：{submitTarget.reportYear} 年度　截止：{submitTarget.dueDate}
            </div>
          </div>
        )}
        <Form form={submitForm} layout="vertical">
          <Form.Item name="receiptNo" label="回执编号" extra="国家企业信用信息公示系统返回的回执号（可空）">
            <Input placeholder="选填，便于日后核对" maxLength={100} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 证照弹窗 */}
      <Modal
        title={editingQual ? '编辑证照' : '登记证照'}
        open={qualModalOpen}
        confirmLoading={qualSubmitting}
        onOk={() => void submitQual()}
        onCancel={() => setQualModalOpen(false)}
        width={640}
        destroyOnClose
      >
        <Form form={qualForm} layout="vertical">
          <Form.Item
            name="companyId"
            label="所属企业"
            rules={[{ required: true, message: '请选择企业' }]}
            extra="证照挂在公司主体上；企业须在「公司主体」页已建档"
          >
            <Select
              showSearch
              placeholder="输入企业名称搜索"
              filterOption={false}
              loading={companyLoading}
              onSearch={(value) => void handleCompanySearch(value)}
              options={companyOptions}
              disabled={!!editingQual}
              notFoundContent="请到「公司主体」页复制主体 ID"
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={14}>
              <Form.Item
                name="qualificationName"
                label="证照名称"
                rules={[{ required: true, message: '请填写证照名称' }]}
              >
                <Input placeholder="如 食品经营许可证" maxLength={200} />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item name="qualificationType" label="类型">
                <Select
                  options={Object.entries(qualTypeConfig).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="certNo" label="证照编号">
                <Input placeholder="许可证编号" maxLength={100} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="issuer" label="发证机关">
                <Input placeholder="如 西安市雁塔区市场监督管理局" maxLength={200} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="issueDate" label="发证日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="expireDate" label="到期日期" extra="留空表示长期有效">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="remindDaysBefore"
                label="提前提醒天数"
                extra="默认 60 天，留足续办材料准备时间"
              >
                <InputNumber style={{ width: '100%' }} min={1} max={365} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="状态">
                <Select
                  disabled={!editingQual}
                  options={Object.entries(qualStatusConfig).map(([value, cfg]) => ({
                    value,
                    label: cfg.label,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="经营范围、特殊要求等" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
