/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : Companies.tsx
 * Date : 2026/10/24 18:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
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
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  assignAccountant,
  bulkBindContacts,
  getBackfillStatus,
  getCompanyOverview,
  listCompanies,
  listUnlinkedContacts,
  searchUsers,
  unbindContact,
  type BackfillStatus,
  type Company,
  type CompanyOverview,
  type UnlinkedContact,
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

/** 联系人在公司内的角色 */
const contactRoleConfig: Record<string, { color: string; label: string }> = {
  BOSS: { color: 'gold', label: '老板' },
  FINANCE: { color: 'blue', label: '财务' },
  CASHIER: { color: 'cyan', label: '出纳' },
  ADMIN: { color: 'purple', label: '行政' },
  AGENT: { color: 'geekblue', label: '经办' },
  OTHER: { color: 'default', label: '其他' },
  UNKNOWN: { color: 'default', label: '未指定' },
};

/** 财税业务域计数标签 */
const businessLabels: Array<{ key: keyof CompanyOverview['business']; label: string }> = [
  { key: 'contracts', label: '合同' },
  { key: 'taxProfiles', label: '报税档案' },
  { key: 'licenseOcrTasks', label: '证照识别' },
  { key: 'inspectionTasks', label: '巡检任务' },
  { key: 'renewalTasks', label: '续费任务' },
];

/**
 * 公司主体管理页。
 * <p>
 * 三块能力：主体列表（含 360 概览抽屉）、存量归并工作台（待归并清单 + 批量挂靠）、
 * 补录完成度。归并的原则是「系统给候选 + 人工确认」——不自动猜测归属。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function Companies() {
  const { message, modal } = App.useApp();

  // 主体列表
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyTotal, setCompanyTotal] = useState(0);
  const [companyLoading, setCompanyLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [size] = useState(10);

  // 360 抽屉
  const [overview, setOverview] = useState<CompanyOverview | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [overviewOpen, setOverviewOpen] = useState(false);

  // 待归并清单
  const [unlinked, setUnlinked] = useState<UnlinkedContact[]>([]);
  const [unlinkedTotal, setUnlinkedTotal] = useState(0);
  const [unlinkedLoading, setUnlinkedLoading] = useState(false);
  const [selectedCustomers, setSelectedCustomers] = useState<string[]>([]);

  // 补录完成度
  const [status, setStatus] = useState<BackfillStatus | null>(null);

  // 负责会计指派
  const [assignTarget, setAssignTarget] = useState<Company | null>(null);
  const [assignSubmitting, setAssignSubmitting] = useState(false);
  const [userOptions, setUserOptions] = useState<UserOption[]>([]);
  const [userLoading, setUserLoading] = useState(false);
  const [assignedId, setAssignedId] = useState<string | null>(null);

  /** 加载主体列表 */
  const loadCompanies = useCallback(async () => {
    setCompanyLoading(true);
    try {
      const result = await listCompanies({ keyword: keyword || undefined, page, size });
      setCompanies(result.content || []);
      setCompanyTotal(result.totalElements || 0);
    } catch {
      // client.ts 已统一弹错
    } finally {
      setCompanyLoading(false);
    }
  }, [keyword, page, size]);

  /** 加载待归并清单（首屏只取一页，够看信号分布即可） */
  const loadUnlinked = useCallback(async () => {
    setUnlinkedLoading(true);
    try {
      const result = await listUnlinkedContacts({ page: 0, size: 20 });
      setUnlinked(result.content || []);
      setUnlinkedTotal(result.totalElements || 0);
    } catch {
      // 已统一提示
    } finally {
      setUnlinkedLoading(false);
    }
  }, []);

  /** 加载补录完成度 */
  const loadStatus = useCallback(async () => {
    try {
      setStatus(await getBackfillStatus());
    } catch {
      // 已统一提示
    }
  }, []);

  useEffect(() => {
    void loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    void loadUnlinked();
    void loadStatus();
  }, [loadUnlinked, loadStatus]);

  /** 打开 360 概览 */
  const openOverview = async (company: Company) => {
    setOverviewOpen(true);
    setOverviewLoading(true);
    setOverview(null);
    try {
      setOverview(await getCompanyOverview(company.id));
    } catch {
      setOverviewOpen(false);
    } finally {
      setOverviewLoading(false);
    }
  };

  /** 把勾选的待归并联系人挂到指定公司 */
  const handleBulkBind = (company: Company) => {
    if (selectedCustomers.length === 0) {
      message.warning('请先在「待归并清单」里勾选联系人');
      return;
    }
    modal.confirm({
      title: `确认挂靠到「${company.companyName}」？`,
      content: `本次将 ${selectedCustomers.length} 个联系人挂到该主体下。挂靠后可在公司详情里调整角色。`,
      okText: '确认挂靠',
      onOk: async () => {
        try {
          const result = await bulkBindContacts(company.id, selectedCustomers);
          message.success(`已挂靠 ${result.bound} 个，跳过 ${result.skipped?.length ?? 0} 个`);
          if (result.skipped?.length) {
            // 跳过原因逐条呈现，避免"静默少挂"
            modal.info({
              title: '部分联系人被跳过',
              content: (
                <ul style={{ paddingLeft: 18, marginBottom: 0 }}>
                  {result.skipped.map((s, i) => (
                    <li key={i}>{`${s.customerId ?? '-'}：${s.reason}`}</li>
                  ))}
                </ul>
              ),
            });
          }
          setSelectedCustomers([]);
          await Promise.all([loadCompanies(), loadUnlinked(), loadStatus()]);
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 远程搜索可指派的会计 */
  const handleUserSearch = async (keyword: string) => {
    setUserLoading(true);
    try {
      const result = await searchUsers(keyword || undefined, 20);
      setUserOptions(result.content || []);
    } catch {
      // 已统一提示
    } finally {
      setUserLoading(false);
    }
  };

  /** 打开指派弹窗 */
  const openAssign = (company: Company) => {
    setAssignTarget(company);
    setAssignedId(company.accountantId ?? null);
    void handleUserSearch('');
  };

  /** 提交负责会计指派 */
  const submitAssign = async () => {
    if (!assignTarget) return;
    setAssignSubmitting(true);
    try {
      const picked = userOptions.find((u) => u.id === assignedId);
      await assignAccountant(
        assignTarget.id,
        assignedId,
        picked ? picked.displayName || picked.username : undefined,
      );
      message.success(assignedId ? '已指派负责会计' : '已取消指派');
      setAssignTarget(null);
      await loadCompanies();
    } catch {
      // 已统一提示
    } finally {
      setAssignSubmitting(false);
    }
  };

  /** 解除单个联系人的归属 */
  const handleUnbind = (customerId: string) => {
    modal.confirm({
      title: '确认解除该联系人的公司归属？',
      okText: '解除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await unbindContact(customerId);
          message.success('已解除归属');
          await Promise.all([loadCompanies(), loadUnlinked(), loadStatus()]);
          if (overview) {
            setOverview(await getCompanyOverview(overview.companyId));
          }
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 主体列表列定义 */
  const companyColumns: ColumnsType<Company> = [
    {
      title: '企业名称',
      dataIndex: 'companyName',
      render: (value: string, record) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => void openOverview(record)}>
          {value}
        </Button>
      ),
    },
    {
      title: '类型',
      dataIndex: 'companyType',
      width: 120,
      render: (value: string) => {
        const cfg = companyTypeConfig[value] || { color: 'default', label: value || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '统一社会信用代码',
      dataIndex: 'creditCode',
      width: 200,
      render: (value: string) => value || '-',
    },
    {
      title: '法人',
      dataIndex: 'legalRepresentative',
      width: 100,
      render: (value: string) => value || '-',
    },
    {
      title: '服务状态',
      dataIndex: 'serviceStatus',
      width: 110,
      render: (value: string) => {
        const cfg = serviceStatusConfig[value] || { color: 'default', label: value || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '联系人',
      dataIndex: 'contactCount',
      width: 90,
      align: 'right',
      render: (value: number) => value ?? 0,
    },
    {
      title: '核验',
      dataIndex: 'verified',
      width: 80,
      render: (value: boolean) =>
        value ? <Tag color="green">已核验</Tag> : <Tag color="orange">待核验</Tag>,
    },
    {
      title: '负责会计',
      dataIndex: 'accountantName',
      width: 110,
      render: (value: string, record) =>
        value ? <Tag color="blue">{value}</Tag> : <Tag>未指派</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space size="small">
          <Button size="small" onClick={() => void openOverview(record)}>360</Button>
          <Tooltip title="把「待归并清单」中勾选的联系人挂到本公司">
            <Button size="small" type="primary" onClick={() => handleBulkBind(record)}>
              挂靠选中
            </Button>
          </Tooltip>
          <Button size="small" onClick={() => openAssign(record)}>指派会计</Button>
        </Space>
      ),
    },
  ];

  /** 待归并清单列定义 */
  const unlinkedColumns: ColumnsType<UnlinkedContact> = [
    {
      title: '昵称',
      dataIndex: 'nickname',
      width: 200,
      render: (value: string) => value || '(空)',
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      width: 130,
      render: (value: string) => value || '-',
    },
    {
      title: '可用信号',
      dataIndex: 'signals',
      render: (signals: string[]) => (
        <Space size={[4, 4]} wrap>
          {(signals || []).map((s, i) => (
            <Tag key={i} color={s.includes('无可用于判断') ? 'default' : 'blue'}>
              {s}
            </Tag>
          ))}
        </Space>
      ),
    },
  ];

  return (
    <div>
      {/* 补录完成度 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic title="客户总数" value={status?.total ?? 0} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="已归属公司" value={status?.linked ?? 0} suffix={`/ ${status?.total ?? 0}`} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="归并覆盖率" value={status?.linkCoverage ?? 0} precision={2} suffix="%" />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="有手机号" value={status?.withPhone ?? 0} suffix={`/ ${status?.total ?? 0}`} />
          </Card>
        </Col>
      </Row>

      {/* 主体列表 */}
      <Card
        title="公司主体"
        style={{ marginBottom: 16 }}
        extra={
          <Space>
            <Input
              allowClear
              placeholder="企业名称 / 信用代码"
              prefix={<SearchOutlined />}
              style={{ width: 240 }}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={() => {
                setPage(0);
                void loadCompanies();
              }}
            />
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                setPage(0);
                void loadCompanies();
              }}
            >
              刷新
            </Button>
          </Space>
        }
      >
        <Table<Company>
          rowKey="id"
          size="small"
          loading={companyLoading}
          columns={companyColumns}
          dataSource={companies}
          locale={{ emptyText: <Empty description="暂无公司主体" /> }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: companyTotal,
            showSizeChanger: false,
            onChange: (p) => setPage(p - 1),
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      </Card>

      {/* 待归并清单 */}
      <Card
        title="待归并清单"
        extra={
          <Space>
            <span style={{ color: '#888' }}>
              共 {unlinkedTotal} 条未归属；勾选后在主体列表点「挂靠选中」
            </span>
            <Button icon={<ReloadOutlined />} onClick={() => void loadUnlinked()}>
              刷新
            </Button>
          </Space>
        }
      >
        <Table<UnlinkedContact>
          rowKey="customerId"
          size="small"
          loading={unlinkedLoading}
          columns={unlinkedColumns}
          dataSource={unlinked}
          locale={{ emptyText: <Empty description="没有待归并的联系人" /> }}
          rowSelection={{
            selectedRowKeys: selectedCustomers,
            onChange: (keys) => setSelectedCustomers(keys as string[]),
          }}
          pagination={false}
        />
      </Card>

      {/* 360 概览抽屉 */}
      <Drawer
        title={overview ? `${overview.companyName} · 360 概览` : '公司 360 概览'}
        width={720}
        open={overviewOpen}
        loading={overviewLoading}
        onClose={() => setOverviewOpen(false)}
      >
        {overview && (
          <>
            <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="企业名称" span={2}>
                {overview.companyName}
              </Descriptions.Item>
              <Descriptions.Item label="信用代码">{overview.creditCode || '-'}</Descriptions.Item>
              <Descriptions.Item label="主体类型">
                {(companyTypeConfig[overview.companyType || ''] || { label: '-' }).label}
              </Descriptions.Item>
              <Descriptions.Item label="法定代表人">
                {overview.legalRepresentative || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="工商状态">{overview.businessStatus || '-'}</Descriptions.Item>
              <Descriptions.Item label="税务状态">{overview.taxStatus || '-'}</Descriptions.Item>
              <Descriptions.Item label="服务状态">
                {(serviceStatusConfig[overview.serviceStatus || ''] || { label: '-' }).label}
              </Descriptions.Item>
              <Descriptions.Item label="服务到期">
                {overview.serviceEndDate || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="主体核验">
                {overview.verified ? <Tag color="green">已核验</Tag> : <Tag color="orange">待核验</Tag>}
              </Descriptions.Item>
            </Descriptions>

            <Card size="small" title={`联系人（${overview.contacts?.total ?? 0}）`} style={{ marginBottom: 16 }}>
              <Space size={[8, 8]} wrap>
                {Object.entries(overview.contacts?.byRole || {}).map(([role, count]) => {
                  const cfg = contactRoleConfig[role] || { color: 'default', label: role };
                  return (
                    <Tag key={role} color={cfg.color}>
                      {`${cfg.label} ${count}`}
                    </Tag>
                  );
                })}
                {(overview.contacts?.total ?? 0) === 0 && <span style={{ color: '#888' }}>暂无联系人</span>}
              </Space>
            </Card>

            <Card size="small" title="业务数据" style={{ marginBottom: 16 }}>
              <Row gutter={16}>
                {businessLabels.map(({ key, label }) => (
                  <Col span={4} key={key}>
                    <Statistic title={label} value={overview.business?.[key] ?? 0} />
                  </Col>
                ))}
              </Row>
            </Card>

            <Card size="small" title="风险提示">
              {(overview.risks || []).length === 0 ? (
                <span style={{ color: '#52c41a' }}>无风险提示</span>
              ) : (
                <Space direction="vertical" size={4}>
                  {overview.risks.map((r, i) => (
                    <Tag key={i} color="red">{r}</Tag>
                  ))}
                </Space>
              )}
            </Card>
          </>
        )}
      </Drawer>

      {/* 负责会计指派弹窗 */}
      <Modal
        title={assignTarget ? `指派负责会计 · ${assignTarget.companyName}` : '指派负责会计'}
        open={!!assignTarget}
        confirmLoading={assignSubmitting}
        onOk={() => void submitAssign()}
        onCancel={() => setAssignTarget(null)}
        destroyOnClose
      >
        <div style={{ marginBottom: 12, color: '#666' }}>
          报税到期、续费到期等提醒会优先发给该会计；留空则回落到系统默认收件人。
        </div>
        <Select
          showSearch
          allowClear
          style={{ width: '100%' }}
          placeholder="搜索并选择会计"
          value={assignedId ?? undefined}
          filterOption={false}
          loading={userLoading}
          onSearch={(value) => void handleUserSearch(value)}
          onDropdownVisibleChange={(open) => {
            if (open && userOptions.length === 0) void handleUserSearch('');
          }}
          onChange={(value) => setAssignedId(value ?? null)}
          options={userOptions.map((u) => ({
            value: u.id,
            label: `${u.displayName || u.username || u.id}${
              u.username ? `（${u.username}）` : ''
            }`,
          }))}
        />
      </Modal>
    </div>
  );
}
