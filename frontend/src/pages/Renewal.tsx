/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Renewal.tsx
 * Date : 2026/10/24 22:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
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
  Tag,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import {
  approveRenewal,
  createRenewalTask,
  getRenewalTask,
  listContracts,
  listRenewalTasks,
  recordRenewalResult,
  type ContractOption,
  type RenewalTask,
  type RenewalTaskPayload,
} from '../api/finance';

/** 续费状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待跟进' },
  APPROVAL_PENDING: { color: 'orange', label: '审批中' },
  APPROVED: { color: 'cyan', label: '已批准' },
  IN_PROGRESS: { color: 'processing', label: '跟进中' },
  RENEWED: { color: 'success', label: '已续约' },
  LOST: { color: 'error', label: '已流失' },
  OVERDUE: { color: 'warning', label: '已过期未决' },
  CANCELLED: { color: 'default', label: '已取消' },
};

/** 续费方案映射 */
const optionConfig: Record<string, string> = {
  STANDARD: '标准续约',
  DISCOUNT: '折扣续约',
  GRACE: '赠期续约',
  CANCEL: '不续约',
};

/**
 * 服务续费页。
 * <p>
 * 代账公司的收入命脉是续费率。本页围绕「哪些客户快到期了」「谁在跟进」「续没续成」：
 * </p>
 * <ul>
 *   <li>顶部统计：待跟进、已续约、已流失、待续金额</li>
 *   <li>任务列表：按状态/公司筛选，临近到期日高亮</li>
 *   <li>录入续费结果：续约（回写合同）/ 流失 / 取消</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function Renewal() {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();

  const [tasks, setTasks] = useState<RenewalTask[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();

  // 建单弹窗
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createForm] = Form.useForm<RenewalTaskPayload>();
  const [contractOptions, setContractOptions] = useState<ContractOption[]>([]);
  const [contractLoading, setContractLoading] = useState(false);

  // 结果录入弹窗
  const [resultTask, setResultTask] = useState<RenewalTask | null>(null);
  const [resultSubmitting, setResultSubmitting] = useState(false);
  const [resultForm] = Form.useForm<{ result: string; amount?: number }>();

  /** 加载任务列表 */
  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listRenewalTasks({ status: statusFilter, page, size: 10 });
      setTasks(result.content || []);
      setTotal(result.totalElements || 0);
    } catch {
      // client.ts 已统一提示
    } finally {
      setLoading(false);
    }
  }, [statusFilter, page]);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  /** 合同远程搜索（建单用） */
  const handleContractSearch = useCallback(async (keyword: string) => {
    setContractLoading(true);
    try {
      const result = await listContracts({ keyword: keyword || undefined, page: 0, size: 20 });
      setContractOptions(result.content || []);
    } catch {
      // 已统一提示
    } finally {
      setContractLoading(false);
    }
  }, []);

  /** 统计（基于当前页，全量统计需后端支持，这里明确标注口径） */
  const stats = useMemo(() => {
    const counter = (...statuses: string[]) =>
      tasks.filter((t) => statuses.includes(t.status)).length;
    return {
      pending: counter('PENDING', 'IN_PROGRESS'),
      approval: counter('APPROVAL_PENDING', 'APPROVED'),
      renewed: counter('RENEWED'),
      lost: counter('LOST'),
      overdue: counter('OVERDUE'),
      renewedAmount: tasks
        .filter((t) => t.status === 'RENEWED')
        .reduce((sum, t) => sum + (t.renewalAmount || 0), 0),
    };
  }, [tasks]);

  /** 创建任务 */
  const submitCreate = async () => {
    const values = await createForm.validateFields();
    setCreating(true);
    try {
      await createRenewalTask({
        ...values,
        plannedRenewalDate: values.plannedRenewalDate
          ? dayjs(values.plannedRenewalDate).format('YYYY-MM-DD')
          : undefined,
      });
      message.success('续费任务已创建');
      setCreateOpen(false);
      createForm.resetFields();
      setPage(0);
      await loadTasks();
    } catch {
      // 已统一提示
    } finally {
      setCreating(false);
    }
  };

  /** 打开结果录入 */
  const openResult = (task: RenewalTask) => {
    setResultTask(task);
    resultForm.resetFields();
    resultForm.setFieldsValue({ result: 'RENEWED', amount: task.renewalAmount });
  };

  /** 提交续费结果 */
  const submitResult = async () => {
    if (!resultTask) return;
    const values = await resultForm.validateFields();
    setResultSubmitting(true);
    try {
      await recordRenewalResult(resultTask.id, values.result, values.amount);
      message.success(values.result === 'RENEWED' ? '已标记为续约成功' : '续费结果已录入');
      setResultTask(null);
      await loadTasks();
    } catch {
      // 已统一提示
    } finally {
      setResultSubmitting(false);
    }
  };

  /**
   * 审批通过（折扣/赠期）。
   * <p>
   * 有审批实例时<b>必须去审批中心</b>通过：直接调续费自身的 approve 接口会绕过
   * 审批引擎，导致实例永远停在「审批中」，与任务状态不一致。
   * 只有历史遗留（无实例）的任务才允许在此直接通过。
   * </p>
   */
  const handleApprove = (task: RenewalTask) => {
    if (task.approvalInstanceId) {
      modal.info({
        title: '该续费已接入审批流',
        content: '请在「审批中心」处理对应的审批单，通过或驳回后续费状态会自动同步。',
        okText: '去审批中心',
        onOk: () => navigate('/approvals'),
      });
      return;
    }
    modal.confirm({
      title: '确认审批通过该续费？',
      content: '该任务无关联审批单（历史数据），通过后回填折扣与赠期。',
      onOk: async () => {
        try {
          await approveRenewal(task.id, { amount: task.renewalAmount });
          message.success('已审批通过');
          await loadTasks();
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 查看详情 */
  const handleDetail = async (task: RenewalTask) => {
    try {
      const detail = await getRenewalTask(task.id);
      modal.info({
        title: `续费任务 ${detail.taskNo}`,
        width: 560,
        content: (
          <div style={{ lineHeight: 1.9, marginTop: 12 }}>
            <div>合同：{detail.contractName || detail.contractNo || '-'}</div>
            <div>客户：{detail.customerName || '-'}</div>
            <div>计划续费日：{dayjs(detail.plannedRenewalDate).format('YYYY-MM-DD')}</div>
            <div>提前提醒：{detail.remindDaysBefore ?? 0} 天</div>
            <div>续费方案：{optionConfig[detail.renewalOption || ''] || detail.renewalOption || '-'}</div>
            <div>
              续约金额：{detail.renewalAmount == null ? '-' : `¥ ${detail.renewalAmount.toLocaleString()}`}
            </div>
            <div>状态：{(statusConfig[detail.status] || { label: detail.status }).label}</div>
            <div>来源：{detail.source === 'SYSTEM' ? '系统扫描' : '手动创建'}</div>
            <div>备注：{detail.notes || '-'}</div>
          </div>
        ),
      });
    } catch {
      // 已统一提示
    }
  };

  /** 列表列定义 */
  const columns: ColumnsType<RenewalTask> = [
    {
      title: '任务号',
      dataIndex: 'taskNo',
      width: 170,
      render: (value: string, record) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => void handleDetail(record)}>
          {value}
        </Button>
      ),
    },
    {
      title: '合同',
      dataIndex: 'contractName',
      ellipsis: true,
      render: (value: string, record) => value || record.contractNo || '-',
    },
    {
      title: '客户',
      dataIndex: 'customerName',
      width: 160,
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: '计划续费日',
      dataIndex: 'plannedRenewalDate',
      width: 130,
      render: (value: string) => {
        const date = dayjs(value);
        const daysLeft = date.diff(dayjs(), 'day');
        const urgent = daysLeft <= 7;
        return (
          <Tooltip title={daysLeft >= 0 ? `还有 ${daysLeft} 天` : `已过期 ${-daysLeft} 天`}>
            <span style={{ color: urgent ? '#ff4d4f' : undefined, fontWeight: urgent ? 600 : undefined }}>
              {date.format('YYYY-MM-DD')}
            </span>
          </Tooltip>
        );
      },
    },
    {
      title: '续费方案',
      dataIndex: 'renewalOption',
      width: 110,
      render: (value: string) => optionConfig[value] || value || '-',
    },
    {
      title: '续约金额',
      dataIndex: 'renewalAmount',
      width: 120,
      align: 'right',
      render: (value: number) => (value == null ? '-' : `¥ ${value.toLocaleString()}`),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value: string) => {
        const cfg = statusConfig[value] || { color: 'default', label: value };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '提醒次数',
      dataIndex: 'remindCount',
      width: 90,
      align: 'right',
      render: (value: number) => value ?? 0,
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => {
        const status = record.status;
        const actionable = ['PENDING', 'IN_PROGRESS', 'OVERDUE', 'APPROVED'].includes(status);
        return (
          <Space size="small" wrap>
            {actionable && (
              <Button size="small" type="primary" onClick={() => openResult(record)}>
                录入结果
              </Button>
            )}
            {status === 'APPROVAL_PENDING' && (
              <Button size="small" onClick={() => handleApprove(record)}>审批通过</Button>
            )}
            <Button size="small" onClick={() => void handleDetail(record)}>详情</Button>
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      {/* 统计 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={5}>
          <Card size="small">
            <Statistic title="待跟进" value={stats.pending} suffix="单" />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="审批中/已批准"
              value={stats.approval}
              suffix="单"
              valueStyle={{ color: stats.approval > 0 ? '#fa8c16' : undefined }}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic title="已续约" value={stats.renewed} suffix="单" valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="已流失/已过期"
              value={stats.lost + stats.overdue}
              suffix="单"
              valueStyle={{ color: stats.lost + stats.overdue > 0 ? '#ff4d4f' : undefined }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card size="small">
            <Statistic
              title="本页续约金额"
              value={stats.renewedAmount}
              precision={2}
              prefix="¥"
            />
          </Card>
        </Col>
      </Row>

      <Card
        title="续费任务"
        extra={
          <Space>
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 150 }}
              value={statusFilter}
              onChange={(value) => {
                setStatusFilter(value);
                setPage(0);
              }}
              options={Object.entries(statusConfig).map(([value, cfg]) => ({
                value,
                label: cfg.label,
              }))}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void loadTasks()}>刷新</Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                createForm.resetFields();
                setCreateOpen(true);
                void handleContractSearch('');
              }}
            >
              新建续费任务
            </Button>
          </Space>
        }
      >
        <Table<RenewalTask>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={tasks}
          locale={{
            emptyText: <Empty description="没有续费任务。系统每日 08:00 会扫描即将到期的合同自动生成" />,
          }}
          pagination={{
            current: page + 1,
            pageSize: 10,
            total,
            showSizeChanger: false,
            onChange: (p) => setPage(p - 1),
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      </Card>

      {/* 建单弹窗 */}
      <Modal
        title="新建续费任务"
        open={createOpen}
        confirmLoading={creating}
        onOk={() => void submitCreate()}
        onCancel={() => setCreateOpen(false)}
        width={560}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" initialValues={{ remindDaysBefore: 30, renewalOption: 'STANDARD' }}>
          <Form.Item
            name="contractId"
            label="合同"
            rules={[{ required: true, message: '请选择合同' }]}
            extra="续费任务必须关联合同；客户与主体信息会从合同自动带出"
          >
            <Select
              showSearch
              placeholder="输入合同编号或名称搜索"
              filterOption={false}
              loading={contractLoading}
              onSearch={(value) => void handleContractSearch(value)}
              onDropdownVisibleChange={(open) => {
                if (open && contractOptions.length === 0) void handleContractSearch('');
              }}
              options={contractOptions.map((c) => ({
                value: c.id,
                label: `${c.contractName || c.contractNo || c.id}${
                  c.customerName ? ` · ${c.customerName}` : ''
                }`,
              }))}
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="plannedRenewalDate" label="计划续费日" extra="留空则取合同到期日">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="remindDaysBefore" label="提前提醒天数">
                <InputNumber style={{ width: '100%' }} min={0} max={180} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="renewalOption" label="续费方案">
            <Select
              options={[
                { value: 'STANDARD', label: '标准续约' },
                { value: 'DISCOUNT', label: '折扣续约' },
                { value: 'GRACE', label: '赠期续约' },
                { value: 'CANCEL', label: '不续约' },
              ]}
            />
          </Form.Item>

          <Form.Item name="notes" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 结果录入弹窗 */}
      <Modal
        title="录入续费结果"
        open={!!resultTask}
        confirmLoading={resultSubmitting}
        onOk={() => void submitResult()}
        onCancel={() => setResultTask(null)}
        destroyOnClose
      >
        {resultTask && (
          <div style={{ marginBottom: 16, color: '#666' }}>
            <div>任务：{resultTask.taskNo}</div>
            <div>合同：{resultTask.contractName || resultTask.contractNo || '-'}</div>
            <div>计划续费日：{dayjs(resultTask.plannedRenewalDate).format('YYYY-MM-DD')}</div>
          </div>
        )}
        <Form form={resultForm} layout="vertical">
          <Form.Item
            name="result"
            label="续费结果"
            rules={[{ required: true, message: '请选择结果' }]}
            extra="续约方案（标准/折扣/赠期）在建单时已定，这里只记最终结果"
          >
            <Select
              options={[
                { value: 'RENEWED', label: '续约成功' },
                { value: 'LOST', label: '流失（客户不续）' },
                { value: 'CANCELLED', label: '取消（客户关停/主动终止）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="amount" label="续约金额（元）" extra="续约成功时回填，其余可留空">
            <InputNumber style={{ width: '100%' }} min={0} precision={2} placeholder="0.00" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
