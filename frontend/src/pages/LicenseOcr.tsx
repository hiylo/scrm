/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : LicenseOcr.tsx
 * Date : 2026/10/24 18:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Empty,
  Row,
  Select,
  Space,
  Steps,
  Table,
  Tag,
  Upload,
} from 'antd';
import type { UploadFile } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  createCompanyFromLicense,
  getLicenseTask,
  listLicenseTasks,
  reviewLicenseTask,
  searchCustomers,
  uploadLicense,
  type CustomerOption,
  type LicenseOcrTask,
} from '../api/finance';

/** 任务状态映射 */
const taskStatusConfig: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '待识别' },
  SUCCEEDED: { color: 'green', label: '识别成功' },
  PARTIAL: { color: 'orange', label: '需人工复核' },
  FAILED: { color: 'red', label: '识别失败' },
};

/** 复核状态映射 */
const reviewStatusConfig: Record<string, { color: string; label: string }> = {
  NONE: { color: 'default', label: '无需复核' },
  REQUIRED: { color: 'orange', label: '待复核' },
  REVIEWED: { color: 'green', label: '已复核' },
};

/**
 * 营业执照上传闭环页。
 * <p>
 * 三步闭环：上传照片 → 视觉模型识别字段 → 人工核对后一键建企业主体。
 * 识别结果是<b>脱敏</b>展示的（企业名/法人打码），建主体时后端用库内原文，
 * 所以创建出的公司名称是完整的。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function LicenseOcr() {
  const { message } = App.useApp();

  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [customerId, setCustomerId] = useState<string | undefined>(undefined);
  const [customerOptions, setCustomerOptions] = useState<CustomerOption[]>([]);
  const [customerLoading, setCustomerLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [current, setCurrent] = useState<LicenseOcrTask | null>(null);

  const [tasks, setTasks] = useState<LicenseOcrTask[]>([]);
  const [taskTotal, setTaskTotal] = useState(0);
  const [taskLoading, setTaskLoading] = useState(false);

  /** 加载识别任务列表 */
  const loadTasks = useCallback(async () => {
    setTaskLoading(true);
    try {
      const result = await listLicenseTasks({ page: 0, size: 10 });
      setTasks(result.content || []);
      setTaskTotal(result.totalElements || 0);
    } catch {
      // client.ts 已统一提示
    } finally {
      setTaskLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  /**
   * 远程搜索客户。
   * <p>
   * 输入关键字时才查后端（1269 条不做全量预加载）；首次展开先取默认一页，
   * 让用户不输入也能直接选。限定 20 条，避免下拉过长。
   * </p>
   *
   * @param keyword 昵称或手机号关键字
   */
  const handleCustomerSearch = useCallback(async (keyword: string) => {
    setCustomerLoading(true);
    try {
      const result = await searchCustomers(keyword || undefined, 20);
      setCustomerOptions(result.content || []);
    } catch {
      // client.ts 已统一提示
    } finally {
      setCustomerLoading(false);
    }
  }, []);

  /** 执行上传 + 识别 */
  const handleUpload = async () => {
    const raw = fileList[0]?.originFileObj as File | undefined;
    if (!raw) {
      message.warning('请先选择一张营业执照图片');
      return;
    }
    if (!customerId) {
      message.warning('请选择关联客户（建主体时用它确定归属账号）');
      return;
    }
    setUploading(true);
    try {
      const task = await uploadLicense(raw, {
        licenseType: 'license',
        customerId,
      });
      setCurrent(task);
      message.success(
        task.taskStatus === 'SUCCEEDED' ? '识别完成' : '识别完成，但需要人工复核',
      );
      setFileList([]);
      await loadTasks();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '识别失败');
    } finally {
      setUploading(false);
    }
  };

  /** 复核 */
  const handleReview = async (passed: boolean) => {
    if (!current) return;
    try {
      const updated = await reviewLicenseTask(current.id, passed);
      setCurrent(updated);
      message.success(passed ? '已标记为复核通过' : '已标记为复核不通过');
      await loadTasks();
    } catch {
      // 已统一提示
    }
  };

  /** 一键建主体 */
  const handleCreateCompany = async () => {
    if (!current) return;
    try {
      const company = await createCompanyFromLicense(current.id);
      message.success(`已创建企业主体：${company.companyName}`);
      // 建完后刷新任务（companyId 已回写）
      const refreshed = await getLicenseTask(current.id);
      setCurrent(refreshed);
      await loadTasks();
    } catch {
      // 已统一提示
    }
  };

  /** 任务列表列定义 */
  const columns: ColumnsType<LicenseOcrTask> = [
    {
      title: '文件',
      dataIndex: 'fileName',
      ellipsis: true,
      render: (value: string, record) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => setCurrent(record)}>
          {value || record.id}
        </Button>
      ),
    },
    {
      title: '状态',
      dataIndex: 'taskStatus',
      width: 110,
      render: (value: string) => {
        const cfg = taskStatusConfig[value] || { color: 'default', label: value || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '复核',
      dataIndex: 'reviewStatus',
      width: 100,
      render: (value: string) => {
        const cfg = reviewStatusConfig[value] || { color: 'default', label: value || '-' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '引擎',
      dataIndex: 'engine',
      width: 200,
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: '已建主体',
      dataIndex: 'companyId',
      width: 100,
      render: (value: string) => (value ? <Tag color="green">已建</Tag> : <Tag>未建</Tag>),
    },
    {
      title: '识别时间',
      dataIndex: 'createTime',
      width: 160,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
  ];

  /** 当前步骤 */
  const currentStep = current
    ? (current.companyId ? 2 : 1)
    : 0;

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Steps
          current={currentStep}
          items={[
            { title: '上传营业执照', description: '选择照片并识别' },
            { title: '人工核对', description: '确认字段无误' },
            { title: '添加企业', description: '生成公司主体' },
          ]}
        />
      </Card>

      <Row gutter={16}>
        <Col span={10}>
          <Card title="① 上传营业执照">
            <Upload.Dragger
              accept="image/*"
              maxCount={1}
              fileList={fileList}
              beforeUpload={() => false}
              onChange={({ fileList: list }) => setFileList(list.slice(-1))}
              disabled={uploading}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽营业执照图片到此处</p>
              <p className="ant-upload-hint">支持 JPG / PNG，识别约需 5–10 秒</p>
            </Upload.Dragger>

            <Divider />

            <Space direction="vertical" style={{ width: '100%' }}>
              <div>
                <div style={{ marginBottom: 4, color: '#666' }}>
                  关联客户 <span style={{ color: '#ff4d4f' }}>*</span>
                  <span style={{ marginLeft: 8, fontSize: 12, color: '#999' }}>
                    建主体时用它确定归属账号
                  </span>
                </div>
                <Select
                  showSearch
                  allowClear
                  style={{ width: '100%' }}
                  placeholder="输入昵称或手机号搜索客户"
                  value={customerId}
                  filterOption={false}
                  loading={customerLoading}
                  onSearch={(value) => void handleCustomerSearch(value)}
                  onDropdownVisibleChange={(open) => {
                    if (open && customerOptions.length === 0) void handleCustomerSearch('');
                  }}
                  onChange={(value) => setCustomerId(value)}
                  notFoundContent={customerLoading ? '搜索中…' : '没有匹配的客户'}
                  options={customerOptions.map((c) => ({
                    value: c.id,
                    label: `${c.nickname || '(无昵称)'}${c.phone ? ` · ${c.phone}` : ''}`,
                  }))}
                />
              </div>
              <Button
                type="primary"
                block
                loading={uploading}
                onClick={() => void handleUpload()}
              >
                {uploading ? '识别中…' : '开始识别'}
              </Button>
            </Space>
          </Card>
        </Col>

        <Col span={14}>
          <Card
            title="② 核对识别结果"
            extra={
              current && (
                <Space>
                  {current.reviewStatus === 'REQUIRED' && (
                    <>
                      <Button size="small" onClick={() => void handleReview(false)}>复核不通过</Button>
                      <Button size="small" type="primary" onClick={() => void handleReview(true)}>
                        复核通过
                      </Button>
                    </>
                  )}
                  {current.companyId ? (
                    <Tag color="green">已建主体</Tag>
                  ) : (
                    <Button
                      type="primary"
                      size="small"
                      disabled={current.taskStatus === 'FAILED'}
                      onClick={() => void handleCreateCompany()}
                    >
                      ③ 添加企业
                    </Button>
                  )}
                </Space>
              )
            }
          >
            {!current ? (
              <Empty description="先上传一张营业执照" />
            ) : (
              <>
                <Descriptions column={2} size="small" style={{ marginBottom: 12 }}>
                  <Descriptions.Item label="识别状态">
                    <Tag color={taskStatusConfig[current.taskStatus || '']?.color || 'default'}>
                      {taskStatusConfig[current.taskStatus || '']?.label || current.taskStatus}
                    </Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="校验结果">
                    {current.overallOk ? <Tag color="green">通过</Tag> : <Tag color="orange">有问题</Tag>}
                  </Descriptions.Item>
                  <Descriptions.Item label="引擎" span={2}>
                    <span style={{ fontSize: 12, color: '#888' }}>{current.engine || '-'}</span>
                  </Descriptions.Item>
                </Descriptions>

                {(current.warnings || []).length > 0 && (
                  <Alert
                    type="warning"
                    showIcon
                    style={{ marginBottom: 12 }}
                    message="识别告警"
                    description={
                      <ul style={{ paddingLeft: 18, marginBottom: 0 }}>
                        {(current.warnings || []).map((w, i) => <li key={i}>{w}</li>)}
                      </ul>
                    }
                  />
                )}

                <Table<{ key: string; name: string; value: string }>
                  rowKey="key"
                  size="small"
                  pagination={false}
                  dataSource={(current.fields || []).map((f) => ({
                    key: f.name,
                    name: f.name,
                    value: f.value || '',
                  }))}
                  columns={[
                    { title: '字段', dataIndex: 'name', width: 180 },
                    {
                      title: '识别值',
                      dataIndex: 'value',
                      render: (v: string) =>
                        v ? v : <span style={{ color: '#bbb' }}>（未识别到）</span>,
                    },
                  ]}
                />

                <Alert
                  style={{ marginTop: 12 }}
                  type="info"
                  showIcon
                  message="识别结果是脱敏展示的（企业名/法人打码）；点「添加企业」时后端使用库内原文，建出的主体名称是完整的。"
                />
              </>
            )}
          </Card>
        </Col>
      </Row>

      <Card title="识别任务记录" style={{ marginTop: 16 }} extra={<span style={{ color: '#888' }}>共 {taskTotal} 条</span>}>
        <Table<LicenseOcrTask>
          rowKey="id"
          size="small"
          loading={taskLoading}
          columns={columns}
          dataSource={tasks}
          pagination={false}
        />
      </Card>
    </div>
  );
}
