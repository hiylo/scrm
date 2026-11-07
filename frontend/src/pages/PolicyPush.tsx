/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : PolicyPush.tsx
 * Date : 2026/10/25 01:00:00
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
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  approvePolicyPush,
  cancelPolicyPush,
  createPolicyPushTask,
  getPolicyPushTask,
  listPolicySampling,
  listPolicyPushTasks,
  publishPolicyPush,
  refreshPolicySnapshot,
  rejectPolicyPush,
  searchPolicyArticles,
  submitPolicyPush,
  type KnowledgeArticleOption,
  type PolicyPushPayload,
  type PolicyPushTask,
  type SamplingItem,
} from '../api/finance';

/** 任务状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PENDING_AUDIT: { color: 'processing', label: '待审核' },
  APPROVED: { color: 'cyan', label: '待发布' },
  PUBLISHING: { color: 'processing', label: '发布中' },
  COMPLETED: { color: 'success', label: '已完成' },
  FAILED: { color: 'error', label: '发布失败' },
  REJECTED: { color: 'warning', label: '已驳回' },
  CANCELED: { color: 'default', label: '已撤销' },
};

/** 审核状态映射 */
const auditConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '未提交' },
  SAMPLING_AUDIT: { color: 'warning', label: '抽样待审' },
  APPROVED: { color: 'success', label: '已通过' },
  REJECTED: { color: 'error', label: '已驳回' },
  CANCELED: { color: 'default', label: '已撤销' },
};

/** 税种映射（政策推送可用 ALL） */
const taxTypeConfig: Record<string, string> = {
  VAT: '增值税',
  IIT: '个人所得税',
  CIT: '企业所得税',
  SOCIAL_SECURITY: '社保',
  STAMP: '印花税',
  ALL: '全部税种',
};

/** 规模口径映射 */
const sizeConfig: Record<string, string> = {
  MICRO: '微型',
  SMALL: '小型',
  MEDIUM: '中型',
  LARGE: '大型',
  LISTED: '上市/挂牌',
  ALL: '全部规模',
};

/**
 * 政策推送页。
 * <p>
 * 财税政策变动的批量触达通道。流程是<b>两级人工闸门</b>，不是一键群发：
 * </p>
 * <ol>
 *   <li>草稿期先算命中客户快照，可反复重算；</li>
 *   <li>提交审核 → 抽样审核（采样名单人工过目）→ 通过 / 驳回；</li>
 *   <li>通过后才可发布，发布是<b>真实下发</b>，会写入站内信。</li>
 * </ol>
 * <p>
 * 页面按钮严格按后端状态机可见性实现，不做「看起来能点但必然被拒」的按钮。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function PolicyPush() {
  const { message, modal } = App.useApp();

  const [tasks, setTasks] = useState<PolicyPushTask[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [keyword, setKeyword] = useState('');

  // 建单弹窗
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createForm] = Form.useForm<PolicyPushPayload>();
  const [articleOptions, setArticleOptions] = useState<KnowledgeArticleOption[]>([]);
  const [articleLoading, setArticleLoading] = useState(false);

  // 采样名单弹窗
  const [samplingOpen, setSamplingOpen] = useState(false);
  const [samplingList, setSamplingList] = useState<SamplingItem[]>([]);
  const [samplingLoading, setSamplingLoading] = useState(false);
  const [samplingTask, setSamplingTask] = useState<PolicyPushTask | null>(null);

  /** 加载任务列表 */
  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listPolicyPushTasks({
        status: statusFilter,
        keyword: keyword || undefined,
        page,
        size: 10,
      });
      setTasks(result.content || []);
      setTotal(result.totalElements || 0);
    } catch {
      // client.ts 已统一提示
    } finally {
      setLoading(false);
    }
  }, [statusFilter, keyword, page]);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  /** 政策条目远程搜索 */
  const handleArticleSearch = useCallback(async (value: string) => {
    setArticleLoading(true);
    try {
      const result = await searchPolicyArticles(value || undefined, 20);
      setArticleOptions(result.content || []);
    } catch {
      // 已统一提示
    } finally {
      setArticleLoading(false);
    }
  }, []);

  /** 统计（基于当前页，口径明确标注） */
  const stats = useMemo(() => {
    const counter = (...statuses: string[]) =>
      tasks.filter((t) => statuses.includes(t.status)).length;
    return {
      pendingAudit: counter('PENDING_AUDIT'),
      pendingPublish: counter('APPROVED'),
      completed: counter('COMPLETED'),
      failed: counter('FAILED'),
      reach: tasks
        .filter((t) => t.status === 'COMPLETED')
        .reduce((sum, t) => sum + (t.expectedRecipientCount || 0), 0),
    };
  }, [tasks]);

  /** 通用动作执行 */
  const runAction = async (
    label: string,
    action: () => Promise<unknown>,
    successText: string,
  ) => {
    try {
      await action();
      message.success(successText);
      await loadTasks();
    } catch (e) {
      // client.ts 已统一提示，这里只兜底非 axios 异常
      if (e instanceof Error && !(e as { response?: unknown }).response) {
        message.error(`${label}失败：${e.message}`);
      }
    }
  };

  /** 重算快照 */
  const handleRefresh = async (task: PolicyPushTask) => {
    try {
      const updated = await refreshPolicySnapshot(task.id);
      message.success(`快照已重算，预计触达 ${updated.expectedRecipientCount ?? 0} 人`);
      await loadTasks();
    } catch {
      // 已统一提示
    }
  };

  /** 查看采样名单（二级人工闸门） */
  const handleSampling = async (task: PolicyPushTask) => {
    setSamplingTask(task);
    setSamplingOpen(true);
    setSamplingLoading(true);
    setSamplingList([]);
    try {
      const list = await listPolicySampling(task.id);
      setSamplingList(list || []);
    } catch {
      setSamplingOpen(false);
    } finally {
      setSamplingLoading(false);
    }
  };

  /** 驳回 */
  const handleReject = (task: PolicyPushTask) => {
    let reason = '';
    modal.confirm({
      title: '驳回该政策推送？',
      content: (
        <Input.TextArea
          rows={3}
          placeholder="驳回原因（必填，会计/负责人据此修正）"
          onChange={(e) => {
            reason = e.target.value;
          }}
        />
      ),
      okText: '确认驳回',
      okButtonProps: { danger: true },
      onOk: async () => {
        if (!reason.trim()) {
          message.warning('请填写驳回原因');
          return Promise.reject(new Error('reason required'));
        }
        await runAction('驳回', () => rejectPolicyPush(task.id, reason.trim()), '已驳回');
      },
    });
  };

  /** 发布（真实下发，需二次确认） */
  const handlePublish = (task: PolicyPushTask) => {
    const count = task.expectedRecipientCount ?? 0;
    modal.confirm({
      title: '确认发布并真实下发？',
      width: 520,
      content: (
        <div style={{ lineHeight: 1.9 }}>
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="发布会立即写入站内信，无法撤回"
          />
          <div>政策：{task.policyTitle}</div>
          <div>
            预计触达：<strong style={{ color: '#cf1322' }}>{count}</strong> 人
          </div>
          <div>审核状态：{(auditConfig[task.auditStatus || ''] || { label: '-' }).label}</div>
        </div>
      ),
      okText: `确认向 ${count} 人推送`,
      okButtonProps: { danger: true },
      onOk: () => runAction('发布', () => publishPolicyPush(task.id), '推送已完成'),
    });
  };

  /** 创建任务 */
  const submitCreate = async () => {
    const values = await createForm.validateFields();
    setCreating(true);
    try {
      const created = await createPolicyPushTask({
        ...values,
        effectiveFrom: values.effectiveFrom
          ? dayjs(values.effectiveFrom).format('YYYY-MM-DD')
          : undefined,
        effectiveUntil: values.effectiveUntil
          ? dayjs(values.effectiveUntil).format('YYYY-MM-DD')
          : undefined,
      });
      message.success(`任务已创建，命中 ${created.expectedRecipientCount ?? 0} 人`);
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

  /** 列表列定义 */
  const columns: ColumnsType<PolicyPushTask> = [
    {
      title: '任务号',
      dataIndex: 'taskNo',
      width: 170,
      render: (value: string, record) => (
        <Tooltip title={record.policyCode || ''}>
          <span style={{ fontFamily: 'monospace' }}>{value}</span>
        </Tooltip>
      ),
    },
    {
      title: '政策标题',
      dataIndex: 'policyTitle',
      ellipsis: true,
      render: (value: string, record) => (
        <Space direction="vertical" size={0}>
          <span>{value || '-'}</span>
          <span style={{ fontSize: 12, color: '#999' }}>
            {taxTypeConfig[record.taxType || ''] || record.taxType || '-'} ·{' '}
            {sizeConfig[record.sizeRange || ''] || record.sizeRange || '-'}
          </span>
        </Space>
      ),
    },
    {
      title: '预计触达',
      dataIndex: 'expectedRecipientCount',
      width: 100,
      align: 'right',
      render: (value: number) => (value == null ? '-' : `${value} 人`),
    },
    {
      title: '审核状态',
      dataIndex: 'auditStatus',
      width: 110,
      render: (value: string) => {
        const cfg = auditConfig[value] || { color: 'default', label: value || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '任务状态',
      dataIndex: 'status',
      width: 110,
      render: (value: string) => {
        const cfg = statusConfig[value] || { color: 'default', label: value };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '完成时间',
      dataIndex: 'completedAt',
      width: 150,
      render: (value: string) => (value ? dayjs(value).format('MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 300,
      render: (_, record) => {
        const status = record.status;
        const canRefresh = status === 'DRAFT' || status === 'REJECTED';
        const canSubmit = canRefresh;
        const canAudit = status === 'PENDING_AUDIT';
        const canPublish = status === 'APPROVED' || status === 'FAILED';
        const canCancel = !['PUBLISHING', 'COMPLETED', 'CANCELED'].includes(status);
        const canSample = status === 'PENDING_AUDIT' || status === 'APPROVED';
        return (
          <Space size="small" wrap>
            {canRefresh && (
              <Button size="small" onClick={() => void handleRefresh(record)}>重算快照</Button>
            )}
            {canSubmit && (
              <Button
                size="small"
                type="primary"
                onClick={() => void runAction('提交审核', () => submitPolicyPush(record.id), '已提交审核')}
              >
                提交审核
              </Button>
            )}
            {canSample && (
              <Button size="small" onClick={() => void handleSampling(record)}>采样名单</Button>
            )}
            {canAudit && (
              <>
                <Button
                  size="small"
                  type="primary"
                  onClick={() =>
                    void runAction('审核通过', () => approvePolicyPush(record.id), '已审核通过')
                  }
                >
                  审核通过
                </Button>
                <Button size="small" danger onClick={() => handleReject(record)}>驳回</Button>
              </>
            )}
            {canPublish && (
              <Button size="small" danger onClick={() => handlePublish(record)}>
                {status === 'FAILED' ? '重试发布' : '发布'}
              </Button>
            )}
            {canCancel && (
              <Button
                size="small"
                onClick={() => {
                  modal.confirm({
                    title: '确认撤销该推送任务？',
                    onOk: () =>
                      runAction('撤销', () => cancelPolicyPush(record.id), '已撤销'),
                  });
                }}
              >
                撤销
              </Button>
            )}
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
            <Statistic
              title="待审核"
              value={stats.pendingAudit}
              suffix="单"
              valueStyle={{ color: stats.pendingAudit > 0 ? '#1677ff' : undefined }}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="待发布"
              value={stats.pendingPublish}
              suffix="单"
              valueStyle={{ color: stats.pendingPublish > 0 ? '#fa8c16' : undefined }}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic title="已完成" value={stats.completed} suffix="单" valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="发布失败"
              value={stats.failed}
              suffix="单"
              valueStyle={{ color: stats.failed > 0 ? '#ff4d4f' : undefined }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card size="small">
            <Statistic title="本页触达人次" value={stats.reach} suffix="人" />
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <Space>
            <span>政策推送任务</span>
            <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
              两级人工闸门：提交审核 → 抽样核对 → 通过后才可发布
            </Typography.Text>
          </Space>
        }
        extra={
          <Space>
            <Input.Search
              allowClear
              placeholder="政策标题 / 任务号"
              style={{ width: 220 }}
              onSearch={(value) => {
                setKeyword(value);
                setPage(0);
              }}
            />
            <Select
              allowClear
              placeholder="任务状态"
              style={{ width: 130 }}
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
                void handleArticleSearch('');
              }}
            >
              新建推送
            </Button>
          </Space>
        }
      >
        <Table<PolicyPushTask>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={tasks}
          locale={{
            emptyText: (
              <Empty description="没有推送任务。新建时只能选「已发布且已审核」的 POLICY 类型知识条目" />
            ),
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
        title="新建政策推送"
        open={createOpen}
        confirmLoading={creating}
        onOk={() => void submitCreate()}
        onCancel={() => setCreateOpen(false)}
        width={680}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="只能选择 POLICY 类型、已发布且审核通过的知识条目"
          description="任务创建后是草稿态，先重算命中快照，再提交审核；两级审核通过后才会真实下发。"
        />
        <Form form={createForm} layout="vertical" initialValues={{ auditMode: 'SAMPLING' }}>
          <Form.Item
            name="policyArticleId"
            label="政策条目"
            rules={[{ required: true, message: '请选择政策条目' }]}
          >
            <Select
              showSearch
              placeholder="输入政策标题搜索"
              filterOption={false}
              loading={articleLoading}
              onSearch={(value) => void handleArticleSearch(value)}
              onDropdownVisibleChange={(open) => {
                if (open && articleOptions.length === 0) void handleArticleSearch('');
              }}
              notFoundContent={articleLoading ? '搜索中…' : '没有可用的政策条目（需先发布）'}
              options={articleOptions.map((a) => ({
                value: a.id,
                label: `${a.title}${a.articleCode ? `（${a.articleCode}）` : ''}`,
                disabled: a.reviewStatus != null && a.reviewStatus !== 'APPROVED',
              }))}
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="taxType" label="税种" extra="留空=全部税种">
                <Select
                  allowClear
                  options={Object.entries(taxTypeConfig).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="sizeRange" label="规模口径" extra="留空=全部规模">
                <Select
                  allowClear
                  options={Object.entries(sizeConfig).map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="auditMode" label="审核模式">
                <Select
                  options={[
                    { value: 'SAMPLING', label: '抽样审核（默认）' },
                    { value: 'FULL', label: '全量审核' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="region" label="属地" extra="如 陕西省西安市，留空=不限">
                <Input placeholder="行政区域" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="tagCodes" label="标签编码" extra="多个用英文逗号分隔，留空=不限">
                <Input placeholder="POLICY_VAT,POLICY_SMALL" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="effectiveFrom" label="政策生效起">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="effectiveUntil" label="政策生效止">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="notes" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 采样名单弹窗 */}
      <Modal
        title={`采样核对名单${samplingTask ? `（${samplingTask.taskNo}）` : ''}`}
        open={samplingOpen}
        footer={null}
        onCancel={() => setSamplingOpen(false)}
        width={560}
        destroyOnClose
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="二级人工闸门"
          description="从命中客户中抽样，人工确认这批人确实适用该政策；确认无误再点「审核通过」。"
        />
        <Table<SamplingItem>
          rowKey="customerId"
          size="small"
          loading={samplingLoading}
          dataSource={samplingList}
          pagination={false}
          columns={[
            { title: '客户', dataIndex: 'customerName', ellipsis: true },
            {
              title: '客户 ID',
              dataIndex: 'customerId',
              width: 200,
              render: (value: string) => (
                <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{value}</span>
              ),
            },
          ]}
          locale={{ emptyText: <Empty description="暂无采样数据" /> }}
        />
        {samplingTask && (
          <div style={{ marginTop: 12, color: '#888', fontSize: 12 }}>
            全量命中 {samplingTask.expectedRecipientCount ?? 0} 人，此处仅抽样展示。
          </div>
        )}
      </Modal>
    </div>
  );
}
