/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerGroups.tsx
 * Date : 2026/07/27
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  TeamOutlined,
  UserAddOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 客户分组实体 */
interface ScrmCustomerGroup {
  id: string;
  groupName: string;
  description?: string;
  ownerAccountId: string;
  createTime?: string;
}

/** 分组成员关联实体 (中间表) */
interface ScrmCustomerGroupMember {
  id: string;
  groupId: string;
  customerId: string;
  createTime?: string;
}

/** 创建分组表单值 */
interface GroupFormValues {
  groupName: string;
  description?: string;
  ownerAccountId: string;
}

/**
 * 客户分组管理页面
 * 支持按归属账号查询分组 / 创建 / 删除 / 查看成员 / 添加 / 移除成员
 */
export default function CustomerGroups() {
  const { message, modal } = App.useApp();
  const [groups, setGroups] = useState<ScrmCustomerGroup[]>([]);
  const [loading, setLoading] = useState(false);
  // 归属账号 ID 过滤 (后端要求必传, 否则返回空列表)
  const [ownerAccountId, setOwnerAccountId] = useState<string | undefined>(undefined);
  const [accounts, setAccounts] = useState<Array<{ id: string; accountName: string; platformType: string }>>([]);
  // 新建分组弹窗
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<GroupFormValues>();
  // 成员抽屉
  const [memberDrawerOpen, setMemberDrawerOpen] = useState(false);
  const [currentGroup, setCurrentGroup] = useState<ScrmCustomerGroup | null>(null);
  const [members, setMembers] = useState<ScrmCustomerGroupMember[]>([]);
  const [membersLoading, setMembersLoading] = useState(false);
  // 添加成员弹窗
  const [addMemberOpen, setAddMemberOpen] = useState(false);
  const [addMemberSubmitting, setAddMemberSubmitting] = useState(false);
  const [addMemberForm] = Form.useForm<{ customerIds: number[] }>();
  // 客户搜索 (添加成员弹窗)
  const [customerOptions, setCustomerOptions] = useState<{ label: string; value: number }[]>([]);
  const [customerSearchLoading, setCustomerSearchLoading] = useState(false);

  /** 拉取分组列表 */
  const fetchGroups = useCallback(async () => {
    if (ownerAccountId == null) {
      setGroups([]);
      return;
    }
    setLoading(true);
    try {
      const data = await apiClient.get<ScrmCustomerGroup[]>(
        `/scrm/customers/groups?ownerAccountId=${ownerAccountId}`,
      );
      setGroups(data || []);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setGroups([]);
    } finally {
      setLoading(false);
    }
  }, [ownerAccountId]);

  useEffect(() => {
    fetchGroups();
  }, [fetchGroups]);

  // 加载账号列表用于下拉选择
  useEffect(() => {
    apiClient
      .get<{ content: Array<{ id: string; accountName: string; platformType: string }> }>(
        '/scrm/accounts?page=0&size=100',
      )
      .then((data) => setAccounts(data?.content || []))
      .catch(() => setAccounts([]));
  }, []);

  /** 打开新建弹窗 */
  const openCreate = () => {
    form.resetFields();
    // 回填当前过滤的归属账号, 便于连续为同一账号建组
    if (ownerAccountId != null) {
      form.setFieldsValue({ ownerAccountId });
    }
    setModalOpen(true);
  };

  /** 提交新建分组 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await apiClient.post('/scrm/customers/groups', values);
      message.success('分组已创建');
      setModalOpen(false);
      // 创建后将归属账号回填到过滤条件, 便于看到新建的分组
      setOwnerAccountId(values.ownerAccountId);
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除分组 (带二次确认) */
  const handleDelete = (record: ScrmCustomerGroup) => {
    modal.confirm({
      title: '删除分组',
      content: `确认删除分组 "${record.groupName}" 吗? 此操作不可恢复, 同时会清空分组成员。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiClient.delete(`/scrm/customers/groups/${record.id}`);
          message.success('分组已删除');
          fetchGroups();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 打开成员抽屉并加载成员列表 */
  const openMembers = async (record: ScrmCustomerGroup) => {
    setCurrentGroup(record);
    setMemberDrawerOpen(true);
    setMembersLoading(true);
    try {
      const data = await apiClient.get<ScrmCustomerGroupMember[]>(
        `/scrm/customers/groups/${record.id}/members`,
      );
      setMembers(data || []);
    } catch {
      setMembers([]);
    } finally {
      setMembersLoading(false);
    }
  };

  /** 刷新当前分组成员列表 */
  const refreshMembers = async () => {
    if (!currentGroup) return;
    try {
      const data = await apiClient.get<ScrmCustomerGroupMember[]>(
        `/scrm/customers/groups/${currentGroup.id}/members`,
      );
      setMembers(data || []);
    } catch {
      // 错误已由拦截器提示
    }
  };

  /** 移除成员 (带二次确认) */
  const handleRemoveMember = (member: ScrmCustomerGroupMember) => {
    modal.confirm({
      title: '移除成员',
      content: `确认将客户 ${member.customerId} 移出该分组吗?`,
      okText: '移除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiClient.delete(
            `/scrm/customers/groups/${member.groupId}/members/${member.customerId}`,
          );
          message.success('成员已移除');
          refreshMembers();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 搜索客户 (用于添加成员弹窗的选择器) */
  const searchCustomers = useCallback(async (keyword: string) => {
    if (!keyword.trim()) {
      setCustomerOptions([]);
      return;
    }
    setCustomerSearchLoading(true);
    try {
      const data = await apiClient.get<{ content: { id: string; nickname: string; platformType: string }[] }>(
        `/scrm/customers?keyword=${encodeURIComponent(keyword)}&size=20`,
      );
      setCustomerOptions(
        (data.content || []).map(c => ({
          label: `${c.nickname} (${c.platformType}) #${c.id}`,
          value: Number(c.id),
        })),
      );
    } catch {
      setCustomerOptions([]);
    } finally {
      setCustomerSearchLoading(false);
    }
  }, []);

  /** 提交添加成员 (批量) */
  const handleAddMember = async () => {
    if (!currentGroup) return;
    try {
      const values = await addMemberForm.validateFields();
      setAddMemberSubmitting(true);
      const added = await apiClient.post<number>(
        `/scrm/customers/groups/${currentGroup.id}/members/batch`,
        values.customerIds,
      );
      message.success(`成功添加 ${added} 个成员`);
      setAddMemberOpen(false);
      addMemberForm.resetFields();
      setCustomerOptions([]);
      refreshMembers();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setAddMemberSubmitting(false);
    }
  };

  /** 表格列定义 */
  const columns: ColumnsType<ScrmCustomerGroup> = [
    {
      title: '分组名称',
      dataIndex: 'groupName',
      key: 'groupName',
      width: 200,
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '归属账号 ID',
      dataIndex: 'ownerAccountId',
      key: 'ownerAccountId',
      width: 140,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<TeamOutlined />}
            onClick={() => openMembers(record)}
          >
            成员
          </Button>
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  /** 成员表格列定义 */
  const memberColumns: ColumnsType<ScrmCustomerGroupMember> = [
    {
      title: '客户 ID',
      dataIndex: 'customerId',
      key: 'customerId',
      width: 160,
    },
    {
      title: '加入时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleRemoveMember(record)}
        >
          移除
        </Button>
      ),
    },
  ];

  return (
    <div>
      {/* 顶部工具栏 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={10} md={7}>
            <Select
              showSearch
              placeholder="选择归属账号查询分组"
              allowClear
              style={{ width: '100%' }}
              value={ownerAccountId || undefined}
              onChange={(value) => {
                setOwnerAccountId(value);
              }}
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={accounts.map((a) => ({
                value: String(a.id),
                label: `${a.accountName} (${a.platformType})`,
              }))}
            />
          </Col>
          <Col flex="auto">
            <Space style={{ float: 'right' }} wrap>
              <Button icon={<ReloadOutlined />} onClick={fetchGroups}>
                刷新
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                新建分组
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 分组列表 */}
      <Card>
        <Table<ScrmCustomerGroup>
          rowKey="id"
          columns={columns}
          dataSource={groups}
          loading={loading}
          scroll={{ x: 'max-content' }}
          locale={{
            // 空状态: 未选择归属账号时提示选择; 已选择账号且无数据时展示新建分组 CTA
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  ownerAccountId == null ? '请选择归属账号查询分组' : '暂无分组数据'
                }
                style={{ padding: 32 }}
              >
                {ownerAccountId != null && (
                  <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                    新建分组
                  </Button>
                )}
              </Empty>
            ),
          }}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      </Card>

      {/* 新建分组弹窗 */}
      <Modal
        title="新建分组"
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="groupName"
            label="分组名称"
            rules={[
              { required: true, message: '请输入分组名称' },
              { max: 200, message: '分组名称不超过 200 个字符' },
            ]}
          >
            <Input placeholder="请输入分组名称" maxLength={200} />
          </Form.Item>
          <Form.Item
            name="description"
            label="分组描述"
            rules={[{ max: 500, message: '描述不超过 500 个字符' }]}
          >
            <Input.TextArea placeholder="请输入分组描述" maxLength={500} rows={3} />
          </Form.Item>
          <Form.Item
            name="ownerAccountId"
            label="归属账号 ID"
            rules={[{ required: true, message: '请选择归属账号' }]}
          >
            <Select
              showSearch
              placeholder="请选择归属账号"
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={accounts.map((a) => ({
                value: String(a.id),
                label: `${a.accountName} (${a.platformType})`,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 成员抽屉 */}
      <Drawer
        title={currentGroup ? `分组成员 - ${currentGroup.groupName}` : '分组成员'}
        open={memberDrawerOpen}
        onClose={() => setMemberDrawerOpen(false)}
        width={560}
        destroyOnHidden
      >
        <Space style={{ marginBottom: 16 }}>
          <Button
            type="primary"
            icon={<UserAddOutlined />}
            onClick={() => {
              addMemberForm.resetFields();
              setCustomerOptions([]);
              setAddMemberOpen(true);
            }}
          >
            添加成员
          </Button>
          <Button icon={<ReloadOutlined />} onClick={refreshMembers}>
            刷新
          </Button>
        </Space>
        <Table<ScrmCustomerGroupMember>
          rowKey="id"
          columns={memberColumns}
          dataSource={members}
          loading={membersLoading}
          size="small"
          locale={{ emptyText: <Empty description="暂无成员" /> }}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      </Drawer>

      {/* 添加成员弹窗 */}
      <Modal
        title="批量添加成员"
        open={addMemberOpen}
        onOk={handleAddMember}
        onCancel={() => { setAddMemberOpen(false); setCustomerOptions([]); }}
        confirmLoading={addMemberSubmitting}
        destroyOnHidden
      >
        <Form form={addMemberForm} layout="vertical" preserve={false}>
          <Form.Item
            name="customerIds"
            label="选择客户"
            rules={[{ required: true, message: '请搜索并选择客户' }]}
            extra="输入昵称关键词搜索, 支持多选批量添加"
          >
            <Select
              mode="multiple"
              showSearch
              filterOption={false}
              onSearch={searchCustomers}
              loading={customerSearchLoading}
              options={customerOptions}
              placeholder="输入客户昵称搜索..."
              style={{ width: '100%' }}
              maxTagCount="responsive"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
