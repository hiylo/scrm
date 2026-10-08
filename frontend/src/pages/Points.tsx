/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Points.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  EditOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 规则类型映射 */
const ruleTypeConfig: Record<string, { color: string; label: string }> = {
  EARN: { color: 'green', label: '获取' },
  REDEEM: { color: 'orange', label: '消耗' },
  EXPIRE: { color: 'default', label: '过期' },
  ADJUST: { color: 'purple', label: '调整' },
};

/** 触发事件映射 */
const triggerEventConfig: Record<string, string> = {
  ORDER_PAID: '订单支付',
  ORDER_COMPLETED: '订单完成',
  SIGN_IN: '每日签到',
  REGISTER: '注册',
  SHARE: '分享',
  REVIEW: '评价',
  BIRTHDAY: '生日',
};

/** 积分类型映射 */
const pointsTypeConfig: Record<string, string> = {
  FIXED: '固定值',
  PERCENTAGE: '百分比',
  RATIO: '比例',
};

/** 流水类型映射 */
const transactionTypeConfig: Record<string, { color: string; label: string }> = {
  EARN: { color: 'green', label: '获取' },
  REDEEM: { color: 'orange', label: '消耗' },
  EXPIRE: { color: 'default', label: '过期' },
  ADJUST: { color: 'purple', label: '调整' },
};

/** 积分账户实体 */
interface ScrmPointsAccount {
  id: string;
  customerId: string;
  customerName?: string;
  currentPoints?: number;
  frozenPoints?: number;
  totalEarned?: number;
  totalRedeemed?: number;
  level?: string;
  updateTime?: string;
}

/** 积分规则实体 */
interface ScrmPointsRule {
  id: string;
  ruleName: string;
  ruleType: string;
  triggerEvent: string;
  pointsType: string;
  pointsValue?: number;
  basisField?: string;
  dailyLimit?: number;
  monthlyLimit?: number;
  enabled?: boolean;
  createTime?: string;
}

/** 积分流水实体 */
interface ScrmPointsTransaction {
  id: string;
  customerId?: string;
  customerName?: string;
  transactionType: string;
  points?: number;
  balance?: number;
  reason?: string;
  createTime?: string;
}

/** 积分兑换商品实体 */
interface ScrmPointsExchange {
  id: string;
  productName: string;
  productType?: string;
  pointsCost?: number;
  stockQuantity?: number;
  exchangedQuantity?: number;
  status?: string;
  description?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * 积分管理页
 * <p>
 * 四 Tab 大页面: 积分账户 / 积分规则 / 积分流水 / 兑换商城;
 * 支持积分规则配置与手动调账。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Points() {
  const { message } = App.useApp();
  /** 积分账户 */
  const [accounts, setAccounts] = useState<ScrmPointsAccount[]>([]);
  const [accountsLoading, setAccountsLoading] = useState(false);
  const [accountPage, setAccountPage] = useState(0);
  const accountPageSize = 10;
  const [accountTotal, setAccountTotal] = useState(0);
  /** 积分规则 */
  const [rules, setRules] = useState<ScrmPointsRule[]>([]);
  const [rulesLoading, setRulesLoading] = useState(false);
  const [rulePage, setRulePage] = useState(0);
  const rulePageSize = 10;
  const [ruleTotal, setRuleTotal] = useState(0);
  const [ruleKeyword, setRuleKeyword] = useState('');
  /** 积分流水 */
  const [transactions, setTransactions] = useState<ScrmPointsTransaction[]>([]);
  const [transLoading, setTransLoading] = useState(false);
  const [transPage, setTransPage] = useState(0);
  const transPageSize = 10;
  const [transTotal, setTransTotal] = useState(0);
  /** 兑换商城 */
  const [exchanges, setExchanges] = useState<ScrmPointsExchange[]>([]);
  const [exchangeLoading, setExchangeLoading] = useState(false);
  const [exchangePage, setExchangePage] = useState(0);
  const exchangePageSize = 10;
  const [exchangeTotal, setExchangeTotal] = useState(0);
  /** 规则弹窗 */
  const [editing, setEditing] = useState<ScrmPointsRule | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 调账弹窗 */
  const [adjustOpen, setAdjustOpen] = useState(false);
  const [adjusting, setAdjusting] = useState(false);
  const [adjustForm] = Form.useForm();

  /** 加载积分账户 */
  const loadAccounts = useCallback(async (targetPage = accountPage) => {
    setAccountsLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(accountPageSize) });
      const data = await apiClient.get<Page<ScrmPointsAccount>>(`/scrm/points/accounts/list?${params.toString()}`);
      setAccounts(data.content || []);
      setAccountTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setAccountsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountPage]);

  /** 加载积分规则 */
  const loadRules = useCallback(async (targetPage = rulePage, keyword = ruleKeyword) => {
    setRulesLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(rulePageSize) });
      if (keyword) params.set('keyword', keyword);
      const data = await apiClient.get<Page<ScrmPointsRule>>(`/scrm/points/rules/list?${params.toString()}`);
      setRules(data.content || []);
      setRuleTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setRulesLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rulePage]);

  /** 加载积分流水 */
  const loadTransactions = useCallback(async (targetPage = transPage) => {
    setTransLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(transPageSize) });
      const data = await apiClient.get<Page<ScrmPointsTransaction>>(`/scrm/points/transactions/list?${params.toString()}`);
      setTransactions(data.content || []);
      setTransTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTransLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [transPage]);

  /** 加载兑换商品 */
  const loadExchanges = useCallback(async (targetPage = exchangePage) => {
    setExchangeLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(exchangePageSize) });
      const data = await apiClient.get<Page<ScrmPointsExchange>>(`/scrm/points/exchanges/list?${params.toString()}`);
      setExchanges(data.content || []);
      setExchangeTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setExchangeLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exchangePage]);

  useEffect(() => {
    loadAccounts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountPage]);

  useEffect(() => {
    loadRules();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rulePage]);

  useEffect(() => {
    loadTransactions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [transPage]);

  useEffect(() => {
    loadExchanges();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exchangePage]);

  /** 打开新建规则 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ ruleType: 'EARN', triggerEvent: 'ORDER_PAID', pointsType: 'FIXED' });
    setOpen(true);
  };

  /** 打开编辑规则 */
  const openEdit = (record: ScrmPointsRule) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交规则 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/points/rules/${editing.id}`, values);
        message.success('积分规则已更新');
      } else {
        await apiClient.post('/scrm/points/rules', values);
        message.success('积分规则已创建');
      }
      setOpen(false);
      setRulePage(0);
      loadRules(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 打开手动调账弹窗 */
  const openAdjust = () => {
    adjustForm.resetFields();
    adjustForm.setFieldsValue({ type: 'EARN' });
    setAdjustOpen(true);
  };

  /** 提交手动调账 */
  const handleAdjust = async () => {
    const values = await adjustForm.validateFields();
    setAdjusting(true);
    try {
      await apiClient.post('/scrm/points/accounts/adjust', values);
      message.success('积分已调整');
      setAdjustOpen(false);
      setAccountPage(0);
      loadAccounts(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setAdjusting(false);
    }
  };

  /** 积分账户列 */
  const accountColumns: ColumnsType<ScrmPointsAccount> = useMemo(() => [
    { title: '客户', dataIndex: 'customerName', width: 130, render: (v: string | undefined, r) => v || r.customerId || '-' },
    { title: '当前积分', dataIndex: 'currentPoints', width: 110, render: (v?: number) => <Text strong>{v ?? 0}</Text> },
    { title: '冻结积分', dataIndex: 'frozenPoints', width: 100, render: (v?: number) => v ?? 0 },
    { title: '累计获取', dataIndex: 'totalEarned', width: 110, render: (v?: number) => v ?? 0 },
    { title: '累计消耗', dataIndex: 'totalRedeemed', width: 110, render: (v?: number) => v ?? 0 },
    { title: '等级', dataIndex: 'level', width: 90, render: (v?: string) => (v ? <Tag color="blue">{v}</Tag> : '-') },
    { title: '更新时间', dataIndex: 'updateTime', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
  ], []);

  /** 积分规则列 */
  const ruleColumns: ColumnsType<ScrmPointsRule> = useMemo(() => [
    { title: '规则名称', dataIndex: 'ruleName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '规则类型',
      dataIndex: 'ruleType',
      width: 100,
      render: (v: string) => {
        const cfg = ruleTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '触发事件', dataIndex: 'triggerEvent', width: 120, render: (v?: string) => (v ? triggerEventConfig[v] || v : '-') },
    { title: '积分类型', dataIndex: 'pointsType', width: 100, render: (v?: string) => (v ? pointsTypeConfig[v] || v : '-') },
    { title: '积分值', dataIndex: 'pointsValue', width: 90, render: (v?: number) => v ?? '-' },
    { title: '每日上限', dataIndex: 'dailyLimit', width: 90, render: (v?: number) => v ?? '不限' },
    { title: '每月上限', dataIndex: 'monthlyLimit', width: 90, render: (v?: number) => v ?? '不限' },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (v?: boolean) => (v === false ? <Tag>停用</Tag> : <Tag color="green">启用</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      fixed: 'right',
      render: (_, r) => (
        <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
          编辑
        </Button>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  /** 积分流水列 */
  const transColumns: ColumnsType<ScrmPointsTransaction> = useMemo(() => [
    { title: '客户', dataIndex: 'customerName', width: 120, render: (v: string | undefined, r) => v || r.customerId || '-' },
    {
      title: '类型',
      dataIndex: 'transactionType',
      width: 90,
      render: (v: string) => {
        const cfg = transactionTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '积分变动',
      dataIndex: 'points',
      width: 100,
      render: (v?: number) => {
        if (v == null) return '-';
        const color = v >= 0 ? '#52c41a' : '#ff4d4f';
        return <span style={{ color, fontWeight: 600 }}>{v > 0 ? `+${v}` : v}</span>;
      },
    },
    { title: '变动后余额', dataIndex: 'balance', width: 110, render: (v?: number) => v ?? '-' },
    { title: '原因', dataIndex: 'reason', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '时间', dataIndex: 'createTime', width: 150, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
  ], []);

  /** 兑换商品列 */
  const exchangeColumns: ColumnsType<ScrmPointsExchange> = useMemo(() => [
    { title: '商品名称', dataIndex: 'productName', render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    { title: '商品类型', dataIndex: 'productType', width: 110, render: (v?: string) => v || '-' },
    { title: '所需积分', dataIndex: 'pointsCost', width: 100, render: (v?: number) => (v != null ? <Text strong>{v}</Text> : '-') },
    { title: '库存', dataIndex: 'stockQuantity', width: 90, render: (v?: number) => v ?? '-' },
    { title: '已兑换', dataIndex: 'exchangedQuantity', width: 90, render: (v?: number) => v ?? 0 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => (v === 'INACTIVE' ? <Tag>下架</Tag> : <Tag color="green">上架</Tag>),
    },
    { title: '说明', dataIndex: 'description', ellipsis: true, render: (v?: string) => v || '-' },
  ], []);

  return (
    <div className="points-page">
      <Tabs
        defaultActiveKey="accounts"
        items={[
          {
            key: 'accounts',
            label: '积分账户',
            children: (
              <Card
                title="积分账户"
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={() => loadAccounts()} />
                    <Button type="primary" icon={<SwapOutlined />} onClick={openAdjust}>
                      手动调账
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={accountColumns}
                  dataSource={accounts}
                  loading={accountsLoading}
                  pagination={{
                    current: accountPage + 1,
                    pageSize: accountPageSize,
                    total: accountTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setAccountPage(p - 1),
                  }}
                  scroll={{ y: 'calc(100vh - 320px)' }}
                />
              </Card>
            ),
          },
          {
            key: 'rules',
            label: '积分规则',
            children: (
              <Card
                title="积分规则"
                extra={
                  <Space>
                    <Input
                      placeholder="搜索规则名称"
                      prefix={<SearchOutlined />}
                      allowClear
                      style={{ width: 200 }}
                      onChange={e => {
                        setRuleKeyword(e.target.value);
                        setRulePage(0);
                      }}
                    />
                    <Button icon={<ReloadOutlined />} onClick={() => loadRules()} />
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建规则
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  columns={ruleColumns}
                  dataSource={rules}
                  loading={rulesLoading}
                  pagination={{
                    current: rulePage + 1,
                    pageSize: rulePageSize,
                    total: ruleTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setRulePage(p - 1),
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'transactions',
            label: '积分流水',
            children: (
              <Card
                title="积分流水"
                extra={<Button icon={<ReloadOutlined />} onClick={() => loadTransactions()} />}
              >
                <Table
                  rowKey="id"
                  columns={transColumns}
                  dataSource={transactions}
                  loading={transLoading}
                  pagination={{
                    current: transPage + 1,
                    pageSize: transPageSize,
                    total: transTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setTransPage(p - 1),
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'exchanges',
            label: '兑换商城',
            children: (
              <Card
                title="兑换商品"
                extra={<Button icon={<ReloadOutlined />} onClick={() => loadExchanges()} />}
              >
                <Table
                  rowKey="id"
                  columns={exchangeColumns}
                  dataSource={exchanges}
                  loading={exchangeLoading}
                  pagination={{
                    current: exchangePage + 1,
                    pageSize: exchangePageSize,
                    total: exchangeTotal,
                    showTotal: t => `共 ${t} 条`,
                    onChange: p => setExchangePage(p - 1),
                  }}
                  locale={{ emptyText: <Empty description="暂无兑换商品" /> }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* 规则弹窗 */}
      <Modal
        title={editing ? '编辑积分规则' : '新建积分规则'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="ruleName" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
            <Input placeholder="如: 首单送 100 积分" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="ruleType" label="规则类型" rules={[{ required: true }]}>
              <Select style={{ width: 130 }} options={Object.entries(ruleTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="triggerEvent" label="触发事件" rules={[{ required: true }]}>
              <Select style={{ width: 160 }} options={Object.entries(triggerEventConfig).map(([v, l]) => ({ value: v, label: l }))} />
            </Form.Item>
          </Space>
          <Space size={16} wrap>
            <Form.Item name="pointsType" label="积分类型" rules={[{ required: true }]}>
              <Select style={{ width: 130 }} options={Object.entries(pointsTypeConfig).map(([v, l]) => ({ value: v, label: l }))} />
            </Form.Item>
            <Form.Item name="pointsValue" label="积分值" rules={[{ required: true }]}>
              <InputNumber min={0} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="basisField" label="计算字段 (百分比时)">
              <Input placeholder="如 amount" style={{ width: 150 }} />
            </Form.Item>
          </Space>
          <Space size={16} wrap>
            <Form.Item name="dailyLimit" label="每日上限">
              <InputNumber min={0} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="monthlyLimit" label="每月上限">
              <InputNumber min={0} style={{ width: 120 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 调账弹窗 */}
      <Modal
        title="手动调整积分"
        open={adjustOpen}
        onCancel={() => setAdjustOpen(false)}
        onOk={handleAdjust}
        confirmLoading={adjusting}
      >
        <Form form={adjustForm} layout="vertical">
          <Form.Item name="customerId" label="客户 ID" rules={[{ required: true, message: '请输入客户 ID' }]}>
            <Input placeholder="客户 ID" />
          </Form.Item>
          <Form.Item name="points" label="积分变动" rules={[{ required: true, message: '请输入积分变动 (正/负)' }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="type" label="类型" initialValue="EARN">
            <Select options={[{ value: 'EARN', label: '增加' }, { value: 'REDEEM', label: '扣减' }, { value: 'ADJUST', label: '调整' }]} />
          </Form.Item>
          <Form.Item name="reason" label="原因">
            <Input placeholder="调账原因" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}