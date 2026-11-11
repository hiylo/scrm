/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : DataTransfers.tsx
 * Date : 2026/10/08 12:00:00
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
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 数据类型映射（后端按 dataType 过滤，值本身由业务定义，未知值原样展示） */
const dataTypeConfig: Record<string, { color: string; label: string }> = {
  CUSTOMER: { color: 'green', label: '客户' },
  ORDER: { color: 'blue', label: '订单' },
  MESSAGE: { color: 'orange', label: '消息' },
  CHANNEL_CODE: { color: 'cyan', label: '渠道码' },
  COMPANY: { color: 'geekblue', label: '公司主体' },
  EMPLOYEE: { color: 'purple', label: '员工' },
};

/**
 * 导入模板行（与后端 ScrmImportTemplateEntity 对齐）。
 * <p>本页管理的是「导入模板」：定义某类数据怎么导、有哪些列、如何去重，
 * 可反复用于导入；一次性的导入执行记录在 imports 任务里，不在本页。</p>
 */
interface ScrmImportTemplate {
  id: string;
  templateName: string;
  dataType: string;
  description?: string;
  columns: string;
  sampleFileUrl?: string;
  validationRules?: string;
  deduplicationKey?: string;
  updateIfExists?: boolean;
  enabled?: boolean;
  createdBy?: string;
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
 * 数据导入导出页面
 * <p>
 * 导入模板列表 + 新建/编辑/启停/删除。<b>不包含</b>一次性导入任务，
 * 那些由后端 /imports 接口按需触发。
 * </p>
 *
 * @author Hsi Chu
 */
export default function DataTransfers() {
  const { message } = App.useApp();
  /** 列表 */
  const [list, setList] = useState<ScrmImportTemplate[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const pageSize = 10;
  /** 弹窗 */
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ScrmImportTemplate | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** 加载模板列表 */
  const loadList = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(targetPage), size: String(pageSize) });
      const data = await apiClient.get<Page<ScrmImportTemplate>>(
        `/scrm/data-transfer/templates/list?${params.toString()}`,
      );
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

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ dataType: 'CUSTOMER', updateIfExists: false, enabled: true });
    setOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmImportTemplate) => {
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
        await apiClient.put(`/scrm/data-transfer/templates/${editing.id}`, values);
        message.success('模板已更新');
      } else {
        await apiClient.post('/scrm/data-transfer/templates', values);
        message.success('模板已创建');
      }
      setOpen(false);
      setPage(0);
      loadList(0);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 删除模板 */
  const handleDelete = async (record: ScrmImportTemplate) => {
    try {
      await apiClient.delete(`/scrm/data-transfer/templates/${record.id}`);
      message.success('模板已删除');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 启用/停用模板 */
  const handleToggle = async (record: ScrmImportTemplate, next: boolean) => {
    try {
      await apiClient.post(
        `/scrm/data-transfer/templates/${record.id}/${next ? 'enable' : 'disable'}`,
        {},
      );
      message.success(next ? '模板已启用' : '模板已停用');
      loadList();
    } catch {
      // 拦截器已弹出错误
    }
  };

  /** 表格列 */
  const columns: ColumnsType<ScrmImportTemplate> = useMemo(
    () => [
      {
        title: '模板名称',
        dataIndex: 'templateName',
        width: 200,
        render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
      },
      {
        title: '数据类型',
        dataIndex: 'dataType',
        width: 110,
        render: (v: string) => {
          const cfg = dataTypeConfig[v] || { color: 'default', label: v };
          return <Tag color={cfg.color}>{cfg.label}</Tag>;
        },
      },
      { title: '描述', dataIndex: 'description', ellipsis: true, render: (v?: string) => v || '-' },
      {
        title: '列定义',
        dataIndex: 'columns',
        width: 140,
        ellipsis: true,
        render: (v?: string) => (v ? <Text type="secondary">{v}</Text> : '-'),
      },
      {
        title: '去重键',
        dataIndex: 'deduplicationKey',
        width: 130,
        render: (v?: string) => v || '-',
      },
      {
        title: '已存在时更新',
        dataIndex: 'updateIfExists',
        width: 120,
        render: (v?: boolean) => (v ? '是' : '否'),
      },
      {
        title: '状态',
        dataIndex: 'enabled',
        width: 90,
        render: (v: boolean | undefined, r) => (
          <Switch
            size="small"
            checked={!!v}
            checkedChildren="启用"
            unCheckedChildren="停用"
            onChange={(next) => handleToggle(r, next)}
          />
        ),
      },
      {
        title: '创建时间',
        dataIndex: 'createTime',
        width: 140,
        render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
      },
      {
        title: '操作',
        key: 'actions',
        width: 140,
        fixed: 'right',
        render: (_, r) => (
          <Space size={4}>
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
    ],
    [],
  );

  return (
    <div className="data-transfers-page">
      <Card
        title="数据导入模板"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => loadList()} />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新建模板
            </Button>
          </Space>
        }
      >
        <Text type="secondary" style={{ fontSize: 12 }}>
          模板定义某类数据如何导入（列映射、去重键、已存在时是否更新）。启用后可用于批量导入。
        </Text>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={list}
          loading={loading}
          style={{ marginTop: 12 }}
          locale={{ emptyText: <Empty description="还没有导入模板，点右上「新建模板」创建" /> }}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p) => setPage(p - 1),
          }}
          scroll={{ x: 'max-content', y: 'calc(100vh - 340px)' }}
        />
      </Card>

      {/* 模板弹窗 */}
      <Modal
        title={editing ? '编辑导入模板' : '新建导入模板'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={handleSubmit}
        confirmLoading={saving}
        width={620}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="templateName"
            label="模板名称"
            rules={[{ required: true, message: '请输入模板名称' }, { max: 200, message: '不超过 200 字符' }]}
          >
            <Input placeholder="如: 客户批量导入模板" maxLength={200} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="dataType" label="数据类型" rules={[{ required: true, message: '请选择数据类型' }]}>
                <Select
                  options={Object.entries(dataTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="deduplicationKey" label="去重键">
                <Input placeholder="如: phone / externalId" maxLength={200} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="columns"
            label="列定义"
            rules={[{ required: true, message: '请填写列定义' }]}
            extra="后端要求为 TEXT 列定义，可填列名清单或用 JSON 描述映射关系"
          >
            <Input.TextArea rows={3} placeholder='如: [{"source":"姓名","target":"nickname"}]' />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="模板用途说明" maxLength={500} />
          </Form.Item>
          <Form.Item name="sampleFileUrl" label="样例文件地址">
            <Input placeholder="可选，指向样例模板文件" maxLength={500} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="updateIfExists" label="已存在时更新" valuePropName="checked">
                <Switch checkedChildren="更新" unCheckedChildren="跳过" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="enabled" label="启用状态" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="停用" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
}
