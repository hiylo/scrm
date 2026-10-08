/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : GroupBroadcast.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { apiClient } from '../api/client';

/** 社群平台映射 */
const platformConfig: Record<string, { color: string; label: string }> = {
  WECHAT: { color: 'green', label: '微信' },
  WEWORK: { color: 'blue', label: '企业微信' },
  DINGTALK: { color: 'cyan', label: '钉钉' },
};

/** 社群类型映射 */
const communityTypeConfig: Record<string, { color: string; label: string }> = {
  CUSTOMER: { color: 'purple', label: '客户群' },
  INTERNAL: { color: 'orange', label: '内部群' },
  SALES: { color: 'blue', label: '销售群' },
  SERVICE: { color: 'cyan', label: '服务群' },
};

/** 群状态映射 */
const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '活跃' },
  INACTIVE: { color: 'default', label: '不活跃' },
  DISBANDED: { color: 'red', label: '已解散' },
};

/** 消息类型映射 (广播时可选的消息类型) */
const messageTypeConfig: Record<string, string> = {
  TEXT: '文本',
  IMAGE: '图片',
  LINK: '链接',
  FILE: '文件',
};

/** 社群行 */
interface ScrmCommunity {
  id: string;
  communityName: string;
  communityCode?: string;
  platformType: string;
  communityType: string;
  memberCount?: number;
  activeMembers?: number;
  activityScore?: number;
  status: string;
  createTime?: string;
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
 * 群群发页面
 * <p>
 * 社群列表查询 + 勾选目标群 + 群发消息 (广播) 全流程。
 * </p>
 *
 * @author Hsi Chu
 */
export default function GroupBroadcast() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmCommunity[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 批量勾选 */
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  /** 广播弹窗 */
  const [open, setOpen] = useState(false);
  const [sending, setSending] = useState(false);
  const [form] = Form.useForm();

  /** 加载列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmCommunity>>(`/scrm/communities/list?${params.toString()}`);
      setList(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    loadList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  /** 打开群发弹窗 (未勾选时提示) */
  const openBroadcast = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先勾选要广播的群');
      return;
    }
    form.resetFields();
    form.setFieldsValue({ messageType: 'TEXT' });
    setOpen(true);
  };

  /** 提交群发 */
  const handleBroadcast = async () => {
    const values = await form.validateFields();
    setSending(true);
    try {
      await apiClient.post('/scrm/communities/messages/broadcast', {
        communityIds: selectedRowKeys.map(Number),
        messageType: values.messageType,
        content: values.content,
      });
      message.success('群发消息已发送');
      setOpen(false);
      setSelectedRowKeys([]);
      loadList();
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSending(false);
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmCommunity> = useMemo(() => [
    { title: '群名称', dataIndex: 'communityName', width: 180, render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span> },
    {
      title: '平台',
      dataIndex: 'platformType',
      width: 100,
      render: (v: string) => {
        const cfg = platformConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '群类型',
      dataIndex: 'communityType',
      width: 100,
      render: (v: string) => {
        const cfg = communityTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '成员数', dataIndex: 'memberCount', width: 80, render: (v?: number) => v ?? '-' },
    { title: '活跃成员', dataIndex: 'activeMembers', width: 90, render: (v?: number) => v ?? '-' },
    { title: '活跃度', dataIndex: 'activityScore', width: 80, render: (v?: number) => (v != null ? `${v}%` : '-') },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="group-broadcast-page">
      <Card
        title="群群发"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<SendOutlined />} onClick={openBroadcast}>
              群发消息{selectedRowKeys.length > 0 ? ` (${selectedRowKeys.length})` : ''}
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          rowSelection={{
            selectedRowKeys,
            onChange: keys => setSelectedRowKeys(keys),
          }}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showTotal: t => `共 ${t} 条`,
            onChange: p => setPage(p - 1),
          }}
          scroll={{ y: 'calc(100vh - 320px)' }}
        />
      </Card>

      {/* 广播弹窗 */}
      <Modal
        title="群群发消息"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleBroadcast}
        confirmLoading={sending}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="messageType" label="消息类型" rules={[{ required: true, message: '请选择消息类型' }]}>
            <Select options={Object.entries(messageTypeConfig).map(([value, label]) => ({ value, label }))} />
          </Form.Item>
          <Form.Item name="content" label="消息内容" rules={[{ required: true, message: '请输入消息内容' }]}>
            <Input.TextArea rows={4} placeholder="群发消息内容" maxLength={5000} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
