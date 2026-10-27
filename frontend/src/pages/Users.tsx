/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Users.tsx
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useEffect, useState, useCallback } from 'react';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  KeyOutlined,
  CheckCircleOutlined,
  StopOutlined,
  LockOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useDebounce } from '../hooks/useDebounce';
import {
  UserView,
  CreateUserPayload,
  UpdateUserPayload,
  listUsers,
  createUser,
  updateUser,
  setUserStatus,
  resetUserPassword,
} from '../api/users';

const { Title, Text, Paragraph } = Typography;

/** 角色中文标签映射 */
const roleConfig: Record<string, { label: string; color: string }> = {
  ADMIN: { label: '系统管理员', color: 'red' },
  MANAGER: { label: '经理（看全部）', color: 'purple' },
  SUPERVISOR: { label: '主管（看全部）', color: 'geekblue' },
  ACCOUNTANT: { label: '会计（只看自己负责的主体）', color: 'green' },
  OPERATOR: { label: '下级用户', color: 'blue' },
  SALES: { label: '业务员', color: 'cyan' },
  VIEWER: { label: '访客（只读）', color: 'default' },
};

/**
 * 角色下拉选项。
 * <p>
 * 直接由 {@link roleConfig} 派生，避免「下拉能选」与「表格能显示」两套定义漂移。
 * 后端 roles 是逗号分隔字符串，因此这里用多选。
 * </p>
 */
const roleOptions = Object.entries(roleConfig).map(([value, cfg]) => ({
  value,
  label: `${cfg.label}（${value}）`,
}));

/**
 * 多选值 → 后端逗号串。
 * <p>后端 {@code roles} 字段是 "ADMIN,MANAGER" 形式，空数组按空串提交，由后端回落默认角色。</p>
 *
 * @param roles 下拉选中的角色数组
 * @return 逗号分隔的角色串
 */
const joinRoles = (roles?: string[]): string => (roles || []).join(',');

/**
 * 权限冲突的角色组合。
 * <p>
 * 一个用户同时拥有管理角色与受限角色时，权限按<b>最高角色</b>生效：
 * 给会计勾上 MANAGER，他就从「只看自己负责的主体」变成「看全部」。
 * 这通常不是本意，因此保存前提示，但不阻断——确实需要兼任时仍可保存。
 * </p>
 */
const MANAGEMENT_ROLES = ['ADMIN', 'MANAGER', 'SUPERVISOR'];
const RESTRICTED_ROLES = ['ACCOUNTANT', 'SALES', 'OPERATOR'];

/**
 * 检测角色组合是否会让受限角色失去数据隔离。
 *
 * @param roles 选中的角色数组
 * @return 命中的受限角色名列表，无冲突返回空数组
 */
const detectRoleConflict = (roles?: string[]): string[] => {
  const picked = roles || [];
  const hasManagement = picked.some(r => MANAGEMENT_ROLES.includes(r));
  if (!hasManagement) return [];
  return picked.filter(r => RESTRICTED_ROLES.includes(r));
};

/**
 * 后端逗号串 → 多选值。
 *
 * @param roles 逗号分隔的角色串
 * @return 角色数组
 */
const splitRoles = (roles?: string): string[] =>
  (roles || '').split(',').map(r => r.trim().toUpperCase()).filter(Boolean);

/** 渲染角色 Tag (按逗号拆分) */
const renderRoles = (roles?: string) => {
  if (!roles) return '-';
  const items = roles.split(',').map(r => r.trim().toUpperCase()).filter(Boolean);
  return (
    <Space size={[0, 4]} wrap>
      {items.map((r, i) => {
        const cfg = roleConfig[r] || { label: r, color: 'default' };
        return <Tag key={i} color={cfg.color}>{cfg.label}</Tag>;
      })}
    </Space>
  );
};

/**
 * 用户管理页面 (仅系统管理员可见)
 * 提供系统用户的创建、分页查询、角色/资料更新、启用/禁用与重置密码功能。
 * 公开注册已关闭, 系统用户只能由管理员在此创建。
 */
export default function Users() {
  const { message, modal } = App.useApp();
  const [users, setUsers] = useState<UserView[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState('');
  const debouncedKeyword = useDebounce(keyword, 250);

  // 创建用户弹窗
  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [createForm] = Form.useForm<CreateUserPayload & { role: string[] }>();

  // 编辑/重置密码
  const [editingUser, setEditingUser] = useState<UserView | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [editForm] = Form.useForm<UpdateUserPayload & { role: string[] }>();
  const [resetOpen, setResetOpen] = useState(false);
  const [resetForm] = Form.useForm<{ newPassword: string }>();

  /** 拉取用户分页列表 */
  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listUsers({
        keyword: debouncedKeyword.trim() || undefined,
        page,
        size,
      });
      setUsers(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      setUsers([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, size, debouncedKeyword]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  /** 创建用户 */
  const handleCreate = async () => {
    try {
      const values = await createForm.validateFields();
      if (!(await confirmRoleConflict(values.role))) return;
      setSubmitting(true);
      await createUser({
        username: values.username,
        password: values.password,
        displayName: values.displayName,
        email: values.email || undefined,
        roles: joinRoles(values.role),
      });
      message.success('用户创建成功');
      setCreateOpen(false);
      createForm.resetFields();
      fetchUsers();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'message' in err) {
        message.error(String((err as { message: unknown }).message) || '创建失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * 角色组合风险确认。
   * <p>
   * 管理角色 + 受限角色并存时，受限角色的数据隔离会失效（按最高权限走）。
   * 提示但不阻断，避免误配到生产而不自知。
   * </p>
   *
   * @param roles 选中的角色
   * @return 用户是否确认继续
   */
  const confirmRoleConflict = (roles?: string[]): Promise<boolean> => {
    const conflicts = detectRoleConflict(roles);
    if (conflicts.length === 0) return Promise.resolve(true);
    return new Promise(resolve => {
      modal.confirm({
        title: '角色组合会让数据隔离失效',
        content: (
          <div>
            同时拥有管理角色（ADMIN / MANAGER / SUPERVISOR）与
            <b>{conflicts.join('、')}</b> 时，权限按<b>最高角色</b>生效：
            该账号将能看到<b>全部</b>公司主体，而不是只看自己负责的。
            <br />
            如果这是有意的（例如主管兼任会计），可以继续保存。
          </div>
        ),
        okText: '仍然保存',
        cancelText: '返回修改',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  };

  /** 打开编辑弹窗 */
  const openEdit = (user: UserView) => {
    setEditingUser(user);
    editForm.setFieldsValue({
      displayName: user.displayName || '',
      email: user.email || '',
      // 后端存的是逗号串，多选组件需要数组，否则回填成一项 "ADMIN,MANAGER"
      role: splitRoles(user.roles),
    });
    setEditOpen(true);
  };

  /** 保存编辑 */
  const handleUpdate = async () => {
    if (!editingUser) return;
    try {
      const values = await editForm.validateFields();
      if (!(await confirmRoleConflict(values.role))) return;
      setSubmitting(true);
      await updateUser(editingUser.id, {
        displayName: values.displayName || undefined,
        email: values.email || undefined,
        roles: joinRoles(values.role),
      });
      message.success('用户更新成功');
      setEditOpen(false);
      fetchUsers();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'message' in err) {
        message.error(String((err as { message: unknown }).message) || '更新失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  /** 启用 / 禁用 */
  const handleToggleStatus = async (user: UserView) => {
    try {
      await setUserStatus(user.id, user.status === 1 ? 0 : 1);
      message.success(user.status === 1 ? '已禁用该用户' : '已启用该用户');
      fetchUsers();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'message' in err) {
        message.error(String((err as { message: unknown }).message) || '操作失败');
      }
    }
  };

  /** 重置密码 */
  const handleResetPassword = async () => {
    if (!editingUser) return;
    try {
      const values = await resetForm.validateFields();
      setSubmitting(true);
      await resetUserPassword(editingUser.id, values.newPassword);
      message.success('密码已重置');
      setResetOpen(false);
      resetForm.resetFields();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'message' in err) {
        message.error(String((err as { message: unknown }).message) || '重置失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  /** 表格列配置 */
  const columns: ColumnsType<UserView> = [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      render: (v: string, record) => (
        <Space>
          <Text strong>{v}</Text>
          {record.status === 0 && <Tag color="red">已禁用</Tag>}
        </Space>
      ),
    },
    {
      title: '昵称',
      dataIndex: 'displayName',
      key: 'displayName',
      render: (v?: string) => v || '-',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      render: (v?: string) => v || '-',
    },
    {
      title: '角色',
      dataIndex: 'roles',
      key: 'roles',
      render: (v?: string) => renderRoles(v),
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      render: (v?: string) => v || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      render: (v?: string) => v || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      render: (_, record) => (
        <Space size={0}>
          <Button type="link" size="small" icon={<span aria-hidden>✏️</span>} onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            icon={<KeyOutlined />}
            onClick={() => {
              setEditingUser(record);
              setResetOpen(true);
            }}
          >
            重置密码
          </Button>
          <Popconfirm
            title={record.status === 1 ? '确定禁用该用户?' : '确定启用该用户?'}
            description={record.status === 1 ? '禁用后该用户将无法登录' : undefined}
            onConfirm={() => handleToggleStatus(record)}
          >
            <Button
              type="link"
              size="small"
              icon={record.status === 1 ? <StopOutlined /> : <CheckCircleOutlined />}
              danger={record.status === 1}
            >
              {record.status === 1 ? '禁用' : '启用'}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div>
          <Title level={4} style={{ marginBottom: 4 }}>用户管理</Title>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            仅系统管理员可管理用户。公开注册已关闭, 系统用户只能由管理员在此创建。
          </Paragraph>
        </div>
        <Space>
          <Input.Search
            allowClear
            placeholder="搜索用户名 / 昵称"
            value={keyword}
            onChange={e => setKeyword(e.target.value)}
            style={{ width: 240 }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => { setPage(0); fetchUsers(); }}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => {
            createForm.resetFields();
            setCreateOpen(true);
          }}>
            创建用户
          </Button>
        </Space>
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={users}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: size,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          onChange: (p, ps) => {
            setPage(p - 1);
            setSize(ps);
          },
        }}
      />

      {/* 创建用户弹窗 */}
      <Modal
        title="创建用户"
        open={createOpen}
        onOk={handleCreate}
        confirmLoading={submitting}
        onCancel={() => setCreateOpen(false)}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: '请输入用户名' },
              { pattern: /^[a-zA-Z0-9_]{3,64}$/, message: '3-64 位字母、数字或下划线' },
            ]}
          >
            <Input placeholder="登录用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, message: '请输入初始密码' },
              { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/, message: '8-72 位且同时包含字母与数字' },
            ]}
          >
            <Input.Password placeholder="至少 8 位并含字母与数字" />
          </Form.Item>
          <Form.Item name="displayName" label="昵称">
            <Input placeholder="展示昵称(可选)" />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '邮箱格式不合法' }]}>
            <Input placeholder="邮箱(可选)" />
          </Form.Item>
          <Form.Item
            name="role"
            label="角色"
            initialValue={['OPERATOR']}
            extra="会计只能看到自己负责的公司主体；经理/主管可查看全部。多个角色取权限最高者。"
          >
            <Select
              mode="multiple"
              placeholder="请选择角色"
              options={roleOptions}
              optionFilterProp="label"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑用户弹窗 */}
      <Modal
        title={`编辑用户: ${editingUser?.username || ''}`}
        open={editOpen}
        onOk={handleUpdate}
        confirmLoading={submitting}
        onCancel={() => setEditOpen(false)}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="displayName" label="昵称">
            <Input placeholder="展示昵称(可选)" />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '邮箱格式不合法' }]}>
            <Input placeholder="邮箱(可选)" />
          </Form.Item>
          <Form.Item
            name="role"
            label="角色"
            extra="会计只能看到自己负责的公司主体；经理/主管可查看全部。"
          >
            <Select
              mode="multiple"
              placeholder="请选择角色"
              options={roleOptions}
              optionFilterProp="label"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 重置密码弹窗 */}
      <Modal
        title={`重置密码: ${editingUser?.username || ''}`}
        open={resetOpen}
        onOk={handleResetPassword}
        confirmLoading={submitting}
        onCancel={() => setResetOpen(false)}
        destroyOnClose
      >
        <Form form={resetForm} layout="vertical">
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/, message: '8-72 位且同时包含字母与数字' },
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="至少 8 位并含字母与数字" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}