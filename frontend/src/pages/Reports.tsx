/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Reports.tsx
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Drawer,
  Empty,
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
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
  PlayCircleOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 报表模板 */
interface ScrmReportTemplate {
  id: string;
  templateName: string;
  reportType: string; // CUSTOMER / SALES / MARKETING / CONTRACT / INTEGRATED
  dataSource: string;
  dimensions?: string;
  metrics?: string;
  filters?: string;
  status: string; // ACTIVE / INACTIVE
  createTime?: string;
}

/** 报表结果 */
interface ScrmReportResult {
  id: string;
  templateId: string;
  runBy?: string;
  runAt?: string;
  resultData?: string;
  rowCount?: number;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 类型映射 */
const reportTypeConfig: Record<string, { color: string; label: string }> = {
  CUSTOMER: { color: 'blue', label: '客户' },
  SALES: { color: 'cyan', label: '销售' },
  MARKETING: { color: 'purple', label: '营销' },
  CONTRACT: { color: 'geekblue', label: '合同' },
  INTEGRATED: { color: 'magenta', label: '综合' },
};

const statusConfig: Record<string, { color: string; label: string }> = {
  ACTIVE: { color: 'green', label: '启用' },
  INACTIVE: { color: 'default', label: '停用' },
};

/**
 * 报表中心页
 * <p>
 * 业务报表模板与执行: 报表模板 (客户/销售/营销/合同/综合) + 执行报表 +
 * 结果列表 (JSON 结果展示, 行数统计)。
 * </p>
 *
 * @author Hsi Chu
 */
export default function Reports() {
  const { message } = App.useApp();
  /** 模板 */
  const [templates, setTemplates] = useState<ScrmReportTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templateTotal, setTemplateTotal] = useState(0);
  const [templatePage, setTemplatePage] = useState(0);
  const templatePageSize = 10;
  const [keyword, setKeyword] = useState('');
  /** 弹窗 */
  const [editing, setEditing] = useState<ScrmReportTemplate | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  /** 结果抽屉 */
  const [resultTemplate, setResultTemplate] = useState<ScrmReportTemplate | null>(null);
  const [results, setResults] = useState<ScrmReportResult[]>([]);
  const [resultLoading, setResultLoading] = useState(false);

  /** 加载模板 */
  const loadTemplates = useCallback(async (targetPage = templatePage, k = keyword) => {
    setTemplatesLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(templatePageSize) });
      if (k) params.set('keyword', k);
      const data = await apiClient.get<Page<ScrmReportTemplate>>(`/scrm/reports/templates/list?${params.toString()}`);
      setTemplates(data.content || []);
      setTemplateTotal(data.totalElements || 0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setTemplatesLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templatePage]);

  useEffect(() => {
    loadTemplates();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [templatePage]);

  /** 打开创建 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ reportType: 'CUSTOMER', dataSource: 'customer', status: 'ACTIVE' });
    setOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (record: ScrmReportTemplate) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  /** 提交模板 */
  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await apiClient.put(`/scrm/reports/templates/${editing.id}`, values);
        message.success('模板已更新');
      } else {
        await apiClient.post('/scrm/reports/templates', values);
        message.success('模板已创建');
      }
      setOpen(false);
      setTemplatePage(0);
      loadTemplates(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 删除模板 */
  const handleDelete = async (record: ScrmReportTemplate) => {
    try {
      await apiClient.delete(`/scrm/reports/templates/${record.id}`);
      message.success('模板已删除');
      loadTemplates();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 执行报表 */
  const handleExecute = async (record: ScrmReportTemplate) => {
    try {
      const data = await apiClient.post<ScrmReportResult>(`/scrm/reports/templates/${record.id}/execute`, {});
      message.success(`报表已执行, ${data?.rowCount ?? 0} 行`);
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 查看结果 */
  const openResults = async (record: ScrmReportTemplate) => {
    setResultTemplate(record);
    setResultLoading(true);
    try {
      const data = await apiClient.get<Page<ScrmReportResult>>(`/scrm/reports/templates/${record.id}/results?page=0&size=20`);
      setResults(data.content || []);
    } catch {
      setResults([]);
    } finally {
      setResultLoading(false);
    }
  };

  /** 模板列 */
  const columns: ColumnsType<ScrmReportTemplate> = useMemo(() => [
    {
      title: '模板名称',
      dataIndex: 'templateName',
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    {
      title: '报表类型',
      dataIndex: 'reportType',
      width: 100,
      render: (v: string) => {
        const cfg = reportTypeConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '数据源', dataIndex: 'dataSource', width: 100, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => {
        const cfg = statusConfig[v] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<PlayCircleOutlined />} onClick={() => handleExecute(r)}>
            执行
          </Button>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => openResults(r)}>
            结果
          </Button>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该模板?" onConfirm={() => handleDelete(r)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], []);

  return (
    <div className="reports-page">
      <Card
        title="报表中心"
        extra={
          <Space>
            <Input
              placeholder="搜索模板名称"
              prefix={<SearchOutlined />}
              allowClear
              style={{ width: 200 }}
              onChange={e => {
                setKeyword(e.target.value);
                setTemplatePage(0);
              }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => loadTemplates()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建模板
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={templates}
          loading={templatesLoading}
          pagination={{
            current: templatePage + 1,
            pageSize: templatePageSize,
            total: templateTotal,
            showTotal: t => `共 ${t} 条`,
            onChange: p => setTemplatePage(p - 1),
          }}
        />
      </Card>

      {/* 模板弹窗 */}
      <Modal
        title={editing ? '编辑报表模板' : '新建报表模板'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="templateName" label="模板名称" rules={[{ required: true, message: '请输入模板名称' }]}>
            <Input placeholder="如: 客户月度报表" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="reportType" label="报表类型" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={Object.entries(reportTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="dataSource" label="数据源">
              <Input placeholder="如: customer" style={{ width: 140 }} />
            </Form.Item>
          </Space>
          <Form.Item name="dimensions" label="维度 JSON">
            <Input.TextArea rows={2} placeholder='["lifecycle","region"]' />
          </Form.Item>
          <Form.Item name="metrics" label="指标 JSON">
            <Input.TextArea rows={2} placeholder='[{"name":"count","type":"COUNT"}]' />
          </Form.Item>
          <Form.Item name="filters" label="过滤器 JSON">
            <Input.TextArea rows={2} placeholder='{"status":"ACTIVE"}' />
          </Form.Item>
        </Form>
      </Modal>

      {/* 结果抽屉 */}
      <Drawer
        title={resultTemplate ? `报表结果 - ${resultTemplate.templateName}` : '报表结果'}
        open={!!resultTemplate}
        onClose={() => setResultTemplate(null)}
        width={680}
      >
        <Table
          rowKey="id"
          size="small"
          dataSource={results}
          loading={resultLoading}
          pagination={false}
          locale={{ emptyText: <Empty description="暂无结果, 请先执行报表" /> }}
          columns={[
            { title: '执行人', dataIndex: 'runBy', width: 100, render: (v?: string) => v || '-' },
            { title: '行数', dataIndex: 'rowCount', width: 70, render: (v?: number) => v ?? '-' },
            {
              title: '结果预览',
              dataIndex: 'resultData',
              ellipsis: true,
              render: (v?: string) => (v ? <Text type="secondary">{v.slice(0, 120)}...</Text> : '-'),
            },
            { title: '执行时间', dataIndex: 'runAt', width: 140, render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-') },
          ]}
        />
      </Drawer>
    </div>
  );
}