/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Wework.tsx
 * Date : 2026/08/01
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useState, useCallback } from 'react';
import {
  Card, Table, Button, Input, Tabs, Space, message, Tag, Modal, Form, List, Avatar, Typography,
} from 'antd';
import {
  ReloadOutlined, UserOutlined, TeamOutlined, SendOutlined, ApartmentOutlined,
} from '@ant-design/icons';
import { apiClient } from '../api/client';

const { TextArea } = Input;
const { Text } = Typography;

/** 外部联系人 */
interface ExternalContact {
  externalUserId?: string;
  name?: string;
  corpName?: string;
  type?: number;
}

/** 客户群 */
interface GroupChat {
  chatId?: string;
  name?: string;
  owner?: string;
  memberCount?: number;
}

/** 部门 */
interface Department {
  id: number;
  name: string;
  parentid?: number;
}

/** 部门成员 */
interface DepartmentUser {
  userid?: string;
  name?: string;
  department?: number[];
  position?: string;
}

/**
 * 企业微信管理页面
 * <p>
 * 通过 SCRM 后端调用企微开放 API, 支持:
 * <ul>
 *   <li>外部联系人 (客户) 列表查询</li>
 *   <li>客户群列表查询</li>
 *   <li>部门列表 / 部门成员查询</li>
 *   <li>发送私聊消息 / 客户群消息</li>
 * </ul>
 * </p>
 */
/** 企业微信页面入参 (支持指定默认 Tab, singleView 模式下只渲染对应 Tab 内容) */
interface WeworkProps {
  defaultTab?: string;
  singleView?: boolean;
}

function Wework({ defaultTab = 'contacts', singleView = false }: WeworkProps) {
  const [activeTab, setActiveTab] = useState(defaultTab);
  const [contacts, setContacts] = useState<ExternalContact[]>([]);
  const [groupChats, setGroupChats] = useState<GroupChat[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [departmentUsers, setDepartmentUsers] = useState<DepartmentUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [contactsUserId, setContactsUserId] = useState('');
  const [selectedDeptId, setSelectedDeptId] = useState<number | null>(null);
  const [sendMsgModalVisible, setSendMsgModalVisible] = useState(false);
  const [sendMsgTarget, setSendMsgTarget] = useState<{ type: 'private' | 'group'; id: string } | null>(null);
  const [msgForm] = Form.useForm();

  /** 加载外部联系人 */
  const loadContacts = useCallback(async () => {
    if (!contactsUserId) {
      message.warning('请输入成员 userid');
      return;
    }
    setLoading(true);
    try {
      const data = await apiClient.get<ExternalContact[]>('/scrm/wework/external-contacts', {
        params: { userId: contactsUserId },
      });
      setContacts(data || []);
      if (!data || data.length === 0) message.info('未找到外部联系人');
    } catch (err: any) {
      message.error(err?.message || '获取外部联系人失败');
      setContacts([]);
    } finally {
      setLoading(false);
    }
  }, [contactsUserId]);

  /** 加载客户群列表 */
  const loadGroupChats = useCallback(async () => {
    setLoading(true);
    try {
      const data = await apiClient.get<GroupChat[]>('/scrm/wework/group-chats', {
        params: { pageIndex: 0, pageSize: 100 },
      });
      setGroupChats(data || []);
      if (!data || data.length === 0) message.info('未找到客户群');
    } catch (err: any) {
      message.error(err?.message || '获取客户群列表失败');
      setGroupChats([]);
    } finally {
      setLoading(false);
    }
  }, []);

  /** 加载部门列表 */
  const loadDepartments = useCallback(async () => {
    setLoading(true);
    try {
      const data = await apiClient.get<Department[]>('/scrm/wework/departments');
      setDepartments(data || []);
      if (!data || data.length === 0) message.info('未找到部门');
    } catch (err: any) {
      message.error(err?.message || '获取部门列表失败');
      setDepartments([]);
    } finally {
      setLoading(false);
    }
  }, []);

  /** 加载部门成员 */
  const loadDepartmentUsers = useCallback(async (deptId: number) => {
    setLoading(true);
    setSelectedDeptId(deptId);
    try {
      const data = await apiClient.get<DepartmentUser[]>(`/scrm/wework/departments/${deptId}/users`);
      setDepartmentUsers(data || []);
      if (!data || data.length === 0) message.info('该部门暂无成员');
    } catch (err: any) {
      message.error(err?.message || '获取部门成员失败');
      setDepartmentUsers([]);
    } finally {
      setLoading(false);
    }
  }, []);

  /** 打开发送消息弹窗 */
  const openSendMsgModal = (type: 'private' | 'group', id: string) => {
    setSendMsgTarget({ type, id });
    msgForm.resetFields();
    setSendMsgModalVisible(true);
  };

  /** 发送消息 */
  const handleSendMsg = async () => {
    try {
      const values = await msgForm.validateFields();
      if (!sendMsgTarget) return;
      const url = sendMsgTarget.type === 'private'
        ? '/scrm/wework/messages/send'
        : '/scrm/wework/group-messages/send';
      const params = sendMsgTarget.type === 'private'
        ? { userId: sendMsgTarget.id, content: values.content }
        : { chatId: sendMsgTarget.id, content: values.content };
      await apiClient.post(url, null, { params });
      message.success('消息发送成功');
      setSendMsgModalVisible(false);
      msgForm.resetFields();
    } catch (err: any) {
      if (err?.errorFields) return;
      message.error(err?.message || '发送失败');
    }
  };

  /** Tab 切换时自动加载 */
  const handleTabChange = (key: string) => {
    setActiveTab(key);
    if (key === 'group-chats' && groupChats.length === 0) loadGroupChats();
    if (key === 'departments' && departments.length === 0) loadDepartments();
  };

  /** 联系人类型标签 */
  const contactTypeLabel = (type?: number) => {
    if (type === 1) return <Tag>微信客户</Tag>;
    if (type === 2) return <Tag color="blue">企业微信</Tag>;
    return <Tag>未知</Tag>;
  };

  const contactColumns = [
    {
      title: '头像',
      key: 'avatar',
      width: 60,
      render: () => <Avatar icon={<UserOutlined />} />,
    },
    {
      title: '名称',
      key: 'name',
      render: (_: any, record: ExternalContact) => record.name || record.externalUserId || '-',
    },
    {
      title: '企业名称',
      dataIndex: 'corpName',
      key: 'corpName',
      render: (corp: string) => corp || <Text type="secondary">无</Text>,
    },
    {
      title: '类型',
      key: 'type',
      width: 120,
      render: (_: any, record: ExternalContact) => contactTypeLabel(record.type),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: any, record: ExternalContact) => (
        <Button
          size="small"
          type="link"
          icon={<SendOutlined />}
          onClick={() => record.externalUserId && openSendMsgModal('private', record.externalUserId)}
        >
          发消息
        </Button>
      ),
    },
  ];

  const groupChatColumns = [
    {
      title: '群名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => name || '未命名群聊',
    },
    {
      title: '群ID',
      dataIndex: 'chatId',
      key: 'chatId',
      width: 200,
      ellipsis: true,
      render: (id: string) => id ? <Text copyable style={{ fontSize: 12 }}>{id}</Text> : '-',
    },
    {
      title: '群主',
      dataIndex: 'owner',
      key: 'owner',
      width: 150,
      render: (owner: string) => owner || '-',
    },
    {
      title: '成员数',
      dataIndex: 'memberCount',
      key: 'memberCount',
      width: 80,
      render: (count: number) => count || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_: any, record: GroupChat) => (
        <Button
          size="small"
          type="link"
          icon={<SendOutlined />}
          onClick={() => record.chatId && openSendMsgModal('group', record.chatId)}
        >
          群发消息
        </Button>
      ),
    },
  ];

  /** 渲染外部联系人 Tab 内容 */
  const renderContacts = () => (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="成员 userid (如: ZhangSan)"
          style={{ width: 240 }}
          value={contactsUserId}
          onChange={(e) => setContactsUserId(e.target.value)}
        />
        <Button icon={<ReloadOutlined />} loading={loading} onClick={loadContacts}>
          查询
        </Button>
        <Text type="secondary" style={{ fontSize: 12 }}>
          查询指定成员添加的外部联系人 (客户)
        </Text>
      </Space>
      <Table
        columns={contactColumns}
        dataSource={contacts}
        rowKey={(_, idx) => String(idx)}
        loading={loading}
        pagination={{ pageSize: 20 }}
        size="middle"
        locale={{ emptyText: '暂无外部联系人数据' }}
      />
    </>
  );

  /** 渲染客户群 Tab 内容 */
  const renderGroupChats = () => (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={loadGroupChats}>
          刷新客户群列表
        </Button>
        <Text type="secondary" style={{ fontSize: 12 }}>
          查询企业所有客户群
        </Text>
      </Space>
      <Table
        columns={groupChatColumns}
        dataSource={groupChats}
        rowKey={(_, idx) => String(idx)}
        loading={loading}
        pagination={{ pageSize: 20 }}
        size="middle"
        locale={{ emptyText: '暂无客户群数据' }}
      />
    </>
  );

  /** 渲染部门通讯录 Tab 内容 */
  const renderDepartments = () => (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={loadDepartments}>
          刷新部门列表
        </Button>
        <Text type="secondary" style={{ fontSize: 12 }}>
          查询企微通讯录部门结构
        </Text>
      </Space>
      <div style={{ display: 'flex', gap: 16 }}>
        <div style={{ flex: '0 0 280px' }}>
          <List
            loading={loading}
            dataSource={departments}
            locale={{ emptyText: '暂无部门数据' }}
            renderItem={(item) => (
              <List.Item
                key={item.id}
                actions={[
                  <Button
                    size="small"
                    type="link"
                    onClick={() => loadDepartmentUsers(item.id)}
                  >
                    查看成员
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  avatar={<Avatar icon={<ApartmentOutlined />} />}
                  title={item.name}
                  description={`ID: ${item.id}${item.parentid ? ` | 上级: ${item.parentid}` : ''}`}
                />
              </List.Item>
            )}
          />
        </div>
        <div style={{ flex: 1 }}>
          {selectedDeptId !== null ? (
            <Table
              columns={[
                { title: '姓名', dataIndex: 'name', key: 'name' },
                { title: 'User ID', dataIndex: 'userid', key: 'userid', render: (id: string) => <Text copyable style={{ fontSize: 12 }}>{id}</Text> },
                { title: '职位', dataIndex: 'position', key: 'position', render: (p: string) => p || '-' },
                {
                  title: '操作',
                  key: 'action',
                  width: 120,
                  render: (_: any, record: DepartmentUser) => (
                    <Button
                      size="small"
                      type="link"
                      icon={<SendOutlined />}
                      onClick={() => record.userid && openSendMsgModal('private', record.userid)}
                    >
                      发消息
                    </Button>
                  ),
                },
              ]}
              dataSource={departmentUsers}
              rowKey={(_, idx) => String(idx)}
              loading={loading}
              pagination={{ pageSize: 20 }}
              size="middle"
              locale={{ emptyText: '该部门暂无成员' }}
            />
          ) : (
            <div style={{ textAlign: 'center', padding: '60px 0', color: '#999' }}>
              请从左侧选择部门查看成员
            </div>
          )}
        </div>
      </div>
    </>
  );

  /** 单视图模式: 只渲染 defaultTab 对应的内容, 不包含 Card/Tabs 外层, 但保留发送消息弹窗 */
  if (singleView) {
    let content;
    if (defaultTab === 'contacts') content = renderContacts();
    else if (defaultTab === 'group-chats') content = renderGroupChats();
    else if (defaultTab === 'departments') content = renderDepartments();
    return (
      <>
        {content}
        {/* 发送消息弹窗 */}
        <Modal
          title={sendMsgTarget?.type === 'private' ? '发送私聊消息' : '发送客户群消息'}
          open={sendMsgModalVisible}
          onOk={handleSendMsg}
          onCancel={() => { setSendMsgModalVisible(false); msgForm.resetFields(); }}
          okText="发送"
          cancelText="取消"
          okButtonProps={{ icon: <SendOutlined /> }}
        >
          <Form form={msgForm} layout="vertical" preserve={false}>
            <Form.Item name="content" label="消息内容" rules={[{ required: true, message: '请输入消息内容' }]}>
              <TextArea rows={4} placeholder="请输入消息内容" maxLength={1000} showCount />
            </Form.Item>
          </Form>
        </Modal>
      </>
    );
  }

  return (
    <div style={{ padding: '0 0 24px' }}>
      <Card title="企业微信管理">
        <Tabs
          activeKey={activeTab}
          onChange={handleTabChange}
          items={[
            {
              key: 'contacts',
              label: <span><UserOutlined /> 外部联系人</span>,
              children: renderContacts(),
            },
            {
              key: 'group-chats',
              label: <span><TeamOutlined /> 客户群</span>,
              children: renderGroupChats(),
            },
            {
              key: 'departments',
              label: <span><ApartmentOutlined /> 部门通讯录</span>,
              children: renderDepartments(),
            },
          ]}
        />
      </Card>

      {/* 发送消息弹窗 */}
      <Modal
        title={sendMsgTarget?.type === 'private' ? '发送私聊消息' : '发送客户群消息'}
        open={sendMsgModalVisible}
        onOk={handleSendMsg}
        onCancel={() => { setSendMsgModalVisible(false); msgForm.resetFields(); }}
        okText="发送"
        cancelText="取消"
        okButtonProps={{ icon: <SendOutlined /> }}
      >
        <Form form={msgForm} layout="vertical" preserve={false}>
          <Form.Item name="content" label="消息内容" rules={[{ required: true, message: '请输入消息内容' }]}>
            <TextArea rows={4} placeholder="请输入消息内容" maxLength={1000} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default Wework;
