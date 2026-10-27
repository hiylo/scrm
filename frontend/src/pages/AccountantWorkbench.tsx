/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AccountantWorkbench.tsx
 * Date : 2026/10/25 09:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Empty,
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
import { ReloadOutlined, TeamOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useRole } from '../hooks/useRole';
import {
  batchAssignAccountant,
  getAccountantWorkload,
  listMyCompanies,
  searchUsers,
  type AccountantWorkload,
  type Company,
  type UserOption,
} from '../api/finance';

/** 主体类型映射 */
const companyTypeConfig: Record<string, { color: string; label: string }> = {
  ENTERPRISE: { color: 'blue', label: '企业' },
  INDIVIDUAL_BUSINESS: { color: 'cyan', label: '个体工商户' },
  SOLE_PROPRIETORSHIP: { color: 'geekblue', label: '个人独资' },
  INDIVIDUAL: { color: 'default', label: '个人' },
};

/** 服务状态映射 */
const serviceStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待服务' },
  SERVING: { color: 'green', label: '服务中' },
  SUSPENDED: { color: 'orange', label: '暂停' },
  TERMINATED: { color: 'red', label: '已终止' },
};

/**
 * 会计工作台。
 * <p>
 * 代账公司多会计协作的落地页。两个视角：
 * </p>
 * <ul>
 *   <li><b>我的客户</b>：当前登录会计负责的主体，可批量勾选后移交/回收；</li>
 *   <li><b>分派总览</b>：每个会计负责多少户，以及还有多少户未指派
 *       —— 未指派数直接反映多会计落地进度。</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function AccountantWorkbench() {
  const { message, modal } = App.useApp();
  /**
   * 分派总览是主管视角：会列出每个会计负责多少户（含他人负载）。
   * 会计只能看自己的，因此该区块仅对管理类角色渲染。
   */
  const { isAccountant } = useRole();

  const [companies, setCompanies] = useState<Company[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<string[]>([]);

  const [workload, setWorkload] = useState<AccountantWorkload[]>([]);
  const [workloadLoading, setWorkloadLoading] = useState(false);

  // 批量指派弹窗
  const [assignOpen, setAssignOpen] = useState(false);
  const [assigning, setAssigning] = useState(false);
  const [targetAccountant, setTargetAccountant] = useState<string | null>(null);
  const [userOptions, setUserOptions] = useState<UserOption[]>([]);
  const [userLoading, setUserLoading] = useState(false);

  /** 加载我的客户 */
  const loadMine = useCallback(async () => {
    setLoading(true);
    try {
      const list = await listMyCompanies(keyword || undefined);
      setCompanies(list || []);
    } catch {
      // client.ts 已统一提示
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  /** 加载分派总览 */
  const loadWorkload = useCallback(async () => {
    setWorkloadLoading(true);
    try {
      setWorkload(await getAccountantWorkload());
    } catch {
      // 已统一提示
    } finally {
      setWorkloadLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadMine();
  }, [loadMine]);

  useEffect(() => {
    // 分派总览是主管视角，会计不发起该请求（避免无意义的越权调用）
    if (!isAccountant) void loadWorkload();
  }, [loadWorkload, isAccountant]);

  /** 远程搜索用户 */
  const handleUserSearch = async (value: string) => {
    setUserLoading(true);
    try {
      const result = await searchUsers(value || undefined, 20);
      setUserOptions(result.content || []);
    } catch {
      // 已统一提示
    } finally {
      setUserLoading(false);
    }
  };

  /** 统计 */
  const stats = useMemo(() => {
    const serving = companies.filter((c) => c.serviceStatus === 'SERVING').length;
    const pending = companies.filter((c) => c.serviceStatus === 'PENDING').length;
    const unassigned = workload.find((w) => !w.accountantId)?.companyCount ?? 0;
    const total = workload
      .filter((w) => w.accountantId)
      .reduce((sum, w) => sum + w.companyCount, 0);
    return { mine: companies.length, serving, pending, unassigned, total };
  }, [companies, workload]);

  /** 批量移交 */
  const submitAssign = async () => {
    if (selected.length === 0) return;
    setAssigning(true);
    try {
      const picked = userOptions.find((u) => u.id === targetAccountant);
      const result = await batchAssignAccountant(
        selected,
        targetAccountant,
        picked ? picked.displayName || picked.username : undefined,
      );
      message.success(
        targetAccountant
          ? `已把 ${result.assigned} 户移交给 ${picked?.displayName || picked?.username || '该会计'}`
          : `已收回 ${result.assigned} 户的指派`,
      );
      if (result.failed?.length) {
        modal.warning({
          title: '部分主体未能移交',
          content: (
            <ul style={{ paddingLeft: 18, marginBottom: 0 }}>
              {result.failed.map((f, i) => (
                <li key={i}>{`${f.companyId}：${f.reason}`}</li>
              ))}
            </ul>
          ),
        });
      }
      setAssignOpen(false);
      setSelected([]);
      setTargetAccountant(null);
      await Promise.all([loadMine(), loadWorkload()]);
    } catch {
      // 已统一提示
    } finally {
      setAssigning(false);
    }
  };

  /** 我的客户列定义 */
  const columns: ColumnsType<Company> = [
    { title: '企业名称', dataIndex: 'companyName', ellipsis: true },
    {
      title: '类型',
      dataIndex: 'companyType',
      width: 110,
      render: (value: string) => {
        const cfg = companyTypeConfig[value] || { color: 'default', label: value || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '统一社会信用代码',
      dataIndex: 'creditCode',
      width: 190,
      render: (value: string) => value || <span style={{ color: '#bbb' }}>未填写</span>,
    },
    {
      title: '服务状态',
      dataIndex: 'serviceStatus',
      width: 100,
      render: (value: string) => {
        const cfg = serviceStatusConfig[value] || { color: 'default', label: value || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '服务到期',
      dataIndex: 'serviceEndDate',
      width: 120,
      render: (value: string) => {
        if (!value) return '-';
        const days = dayjs(value).diff(dayjs(), 'day');
        return (
          <Tooltip title={days >= 0 ? `还有 ${days} 天` : `已过期 ${-days} 天`}>
            <span style={{ color: days <= 30 ? '#ff4d4f' : undefined }}>
              {dayjs(value).format('YYYY-MM-DD')}
            </span>
          </Tooltip>
        );
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 150,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
  ];

  /** 工作量列定义 */
  const workloadColumns: ColumnsType<AccountantWorkload> = [
    {
      title: '会计',
      dataIndex: 'accountantName',
      render: (value: string, record) =>
        record.accountantId ? <span>{value}</span> : <Tag color="orange">未指派</Tag>,
    },
    {
      title: '负责户数',
      dataIndex: 'companyCount',
      width: 120,
      align: 'right',
      render: (value: number, record) =>
        record.accountantId ? (
          <strong>{value}</strong>
        ) : (
          <strong style={{ color: value > 0 ? '#fa8c16' : undefined }}>{value}</strong>
        ),
    },
    {
      title: '占比',
      key: 'ratio',
      width: 120,
      render: (_, record) => {
        const base = stats.total + stats.unassigned;
        if (base === 0) return '-';
        return `${((record.companyCount / base) * 100).toFixed(1)}%`;
      },
    },
  ];

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="我负责的户数"
              value={stats.mine}
              suffix="户"
              prefix={<TeamOutlined />}
            />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic title="服务中" value={stats.serving} suffix="户" valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic title="待服务" value={stats.pending} suffix="户" />
          </Card>
        </Col>
        <Col span={5}>
          <Card size="small">
            <Statistic
              title="全公司未指派"
              value={stats.unassigned}
              suffix="户"
              valueStyle={{ color: stats.unassigned > 0 ? '#fa8c16' : undefined }}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card size="small">
            <Statistic title="总户数" value={stats.total + stats.unassigned} suffix="户" />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={15}>
          <Card
            title={
              <Space>
                <span>我的客户</span>
                <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                  数据范围由登录账号解析，只能看到指派给自己的主体
                </Typography.Text>
              </Space>
            }
            extra={
              <Space>
                <Input.Search
                  allowClear
                  placeholder="企业名称"
                  style={{ width: 180 }}
                  onSearch={(value) => setKeyword(value)}
                />
                <Button icon={<ReloadOutlined />} onClick={() => void loadMine()}>刷新</Button>
                <Button
                  type="primary"
                  disabled={selected.length === 0}
                  onClick={() => {
                    setTargetAccountant(null);
                    setAssignOpen(true);
                    void handleUserSearch('');
                  }}
                >
                  移交选中（{selected.length}）
                </Button>
              </Space>
            }
          >
            <Table<Company>
              rowKey="id"
              size="small"
              loading={loading}
              columns={columns}
              dataSource={companies}
              rowSelection={{
                selectedRowKeys: selected,
                onChange: (keys) => setSelected(keys as string[]),
              }}
              locale={{
                emptyText: (
                  <Empty description="还没有指派给你的客户。请联系管理员在「公司主体」页指派负责会计" />
                ),
              }}
              pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 户` }}
            />
          </Card>
        </Col>

        <Col span={isAccountant ? 0 : 9}>
          {!isAccountant && (
          <Card
            title="分派总览"
            extra={<Button size="small" icon={<ReloadOutlined />} onClick={() => void loadWorkload()}>刷新</Button>}
          >
            <Table<AccountantWorkload>
              rowKey={(r) => r.accountantId ?? 'unassigned'}
              size="small"
              loading={workloadLoading}
              columns={workloadColumns}
              dataSource={workload}
              pagination={false}
              locale={{ emptyText: <Empty description="暂无指派数据" /> }}
            />
            {stats.unassigned > 0 && (
              <div style={{ marginTop: 12, color: '#fa8c16', fontSize: 12 }}>
                还有 {stats.unassigned} 户未指派会计，这些户的报税/续费提醒会发给系统默认收件人。
                建议在「公司主体」页批量指派。
              </div>
            )}
          </Card>
          )}
        </Col>
      </Row>

      {/* 移交弹窗 */}
      <Modal
        title={`移交 ${selected.length} 户`}
        open={assignOpen}
        confirmLoading={assigning}
        onOk={() => void submitAssign()}
        onCancel={() => setAssignOpen(false)}
        destroyOnClose
      >
        <div style={{ marginBottom: 12, color: '#666' }}>
          选择接收这批客户的会计。留空表示<b>收回指派</b>（这些户的提醒回到系统默认收件人）。
        </div>
        <Select
          showSearch
          allowClear
          style={{ width: '100%' }}
          placeholder="搜索并选择会计"
          value={targetAccountant ?? undefined}
          filterOption={false}
          loading={userLoading}
          onSearch={(value) => void handleUserSearch(value)}
          onDropdownVisibleChange={(open) => {
            if (open && userOptions.length === 0) void handleUserSearch('');
          }}
          onChange={(value) => setTargetAccountant(value ?? null)}
          options={userOptions.map((u) => ({
            value: u.id,
            label: `${u.displayName || u.username || u.id}${u.username ? `（${u.username}）` : ''}`,
          }))}
        />
      </Modal>
    </div>
  );
}
