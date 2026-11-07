/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : DocumentArchive.tsx
 * Date : 2026/10/25 17:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Divider,
  Drawer,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload/interface';
import {
  CloudUploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EyeOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  deleteDocument,
  getDocumentChecklist,
  getDocumentDownloadUrl,
  getDocumentOverview,
  listDocuments,
  updateDocument,
  uploadDocument,
  type ChecklistItem,
  type CustomerDocument,
  type DocumentChecklist,
  type DocumentOverview,
  type DocumentOverviewRow,
} from '../api/finance';

/** 资料类型映射 */
const docTypeConfig: Record<string, { label: string; color: string }> = {
  BUSINESS_LICENSE: { label: '营业执照', color: 'blue' },
  BANK_PERMIT: { label: '开户许可证', color: 'cyan' },
  LEGAL_ID_CARD: { label: '法人身份证', color: 'purple' },
  ARTICLES: { label: '公司章程', color: 'geekblue' },
  LEASE_CONTRACT: { label: '租赁合同', color: 'gold' },
  SEAL_RECORD: { label: '印章备案', color: 'magenta' },
  BANK_STATEMENT: { label: '银行对账单', color: 'lime' },
  FINANCIAL_REPORT: { label: '财务报表', color: 'green' },
  TAX_FORM: { label: '涉税表单', color: 'orange' },
  OTHER: { label: '其他', color: 'default' },
};

/** 必备资料类型（与后端 REQUIRED_TYPES 一致，用于本地展示兜底） */
const REQUIRED_TYPES = [
  'BUSINESS_LICENSE',
  'BANK_PERMIT',
  'LEGAL_ID_CARD',
  'ARTICLES',
  'LEASE_CONTRACT',
];

/** 资料状态映射 */
const docStatusConfig: Record<string, { label: string; color: string }> = {
  ACTIVE: { label: '有效', color: 'green' },
  EXPIRED: { label: '已过期', color: 'red' },
  ARCHIVED: { label: '已封存', color: 'default' },
};

/** 文件大小格式化 */
const formatSize = (bytes?: number) => {
  if (bytes == null) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
};

/**
 * 客户资料归档页。
 * <p>
 * 页面设计围绕「知道还缺什么」而不是「能上传什么」：
 * </p>
 * <ul>
 *   <li>顶部总览：在服企业里有多少户资料不全，直接给出待办量；</li>
 *   <li>企业清单：按齐全度升序，资料最不全的排在前面，先处理最急的；</li>
 *   <li>逐户抽屉：五项必备资料逐条勾选状态 + 上传入口 + 全部文件。</li>
 * </ul>
 * <p>
 * 文件下载走对象存储预签名 URL 直连，不经应用进程中转，避免大文件占满线程。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function DocumentArchive() {
  const { message, modal } = App.useApp();

  // ============ 总览 ============
  const [overview, setOverview] = useState<DocumentOverview | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [overviewPage, setOverviewPage] = useState(0);
  const [onlyIncomplete, setOnlyIncomplete] = useState(true);

  // ============ 逐户抽屉 ============
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [activeRow, setActiveRow] = useState<DocumentOverviewRow | null>(null);
  const [checklist, setChecklist] = useState<DocumentChecklist | null>(null);
  const [checklistLoading, setChecklistLoading] = useState(false);

  // ============ 上传 ============
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [uploadForm] = Form.useForm<{
    docType: string;
    docName?: string;
    issueDate?: dayjs.Dayjs;
    expireDate?: dayjs.Dayjs;
    remark?: string;
  }>();

  /** 加载总览 */
  const loadOverview = useCallback(async () => {
    setOverviewLoading(true);
    try {
      setOverview(await getDocumentOverview());
    } catch {
      // client.ts 已统一提示
    } finally {
      setOverviewLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  /** 加载某户齐全度 */
  const loadChecklist = useCallback(async (companyId: string) => {
    setChecklistLoading(true);
    try {
      setChecklist(await getDocumentChecklist(companyId));
    } catch {
      // 已统一提示
    } finally {
      setChecklistLoading(false);
    }
  }, []);

  /** 打开逐户抽屉 */
  const openDrawer = (row: DocumentOverviewRow) => {
    setActiveRow(row);
    setChecklist(null);
    setDrawerOpen(true);
    void loadChecklist(row.companyId);
  };

  /** 打开上传弹窗（预填缺失的第一项） */
  const openUpload = (missingTypes: string[]) => {
    setFileList([]);
    uploadForm.resetFields();
    uploadForm.setFieldsValue({
      docType: missingTypes[0] ?? 'OTHER',
    } as never);
    setUploadOpen(true);
  };

  /** 执行上传 */
  const submitUpload = async () => {
    const values = await uploadForm.validateFields();
    const file = fileList[0]?.originFileObj;
    if (!file) {
      message.warning('请选择要上传的文件');
      return;
    }
    if (!activeRow) return;
    setUploading(true);
    try {
      await uploadDocument(file as File, {
        companyId: activeRow.companyId,
        docType: values.docType,
        docName: values.docName,
        issueDate: values.issueDate ? values.issueDate.format('YYYY-MM-DD') : undefined,
        expireDate: values.expireDate ? values.expireDate.format('YYYY-MM-DD') : undefined,
        remark: values.remark,
      });
      message.success('资料已归档');
      setUploadOpen(false);
      setFileList([]);
      await Promise.all([loadChecklist(activeRow.companyId), loadOverview()]);
    } catch {
      // 已统一提示
    } finally {
      setUploading(false);
    }
  };

  /** 下载（预签名直连） */
  const handleDownload = async (record: CustomerDocument) => {
    try {
      const result = await getDocumentDownloadUrl(record.id);
      if (!result?.url) {
        message.error('未取到下载地址');
        return;
      }
      window.open(result.url, '_blank', 'noopener,noreferrer');
    } catch {
      // 已统一提示
    }
  };

  /** 删除 */
  const handleDelete = (record: CustomerDocument) => {
    modal.confirm({
      title: '确认删除该资料？',
      content: `${record.docName}（删除后文件一并从存储中移除，不可恢复）`,
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteDocument(record.id);
          message.success('已删除');
          if (activeRow) {
            await Promise.all([loadChecklist(activeRow.companyId), loadOverview()]);
          }
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 标记封存 */
  const handleArchive = (record: CustomerDocument) => {
    modal.confirm({
      title: '封存该资料？',
      content: '封存后仍可下载，但不再计入齐全度判定。',
      onOk: async () => {
        try {
          await updateDocument(record.id, { status: 'ARCHIVED' });
          message.success('已封存');
          if (activeRow) {
            await Promise.all([loadChecklist(activeRow.companyId), loadOverview()]);
          }
        } catch {
          // 已统一提示
        }
      },
    });
  };

  /** 总览行数据（默认只看资料不全的） */
  const rowData = useMemo(() => {
    const rows = overview?.rows ?? [];
    const filtered = onlyIncomplete ? rows.filter((r) => !r.complete) : rows;
    // 齐全度低的排前面：先处理最急的
    return [...filtered].sort((a, b) => a.completeness - b.completeness);
  }, [overview, onlyIncomplete]);

  /** 总览列 */
  const overviewColumns: ColumnsType<DocumentOverviewRow> = [
    { title: '企业名称', dataIndex: 'companyName', ellipsis: true },
    {
      title: '资料齐全度',
      dataIndex: 'completeness',
      width: 220,
      render: (value: number) => (
        <Progress
          percent={value}
          size="small"
          status={value === 100 ? 'success' : value >= 60 ? 'normal' : 'exception'}
          format={(p) => `${p}%`}
        />
      ),
    },
    {
      title: '已收/必备',
      key: 'count',
      width: 110,
      align: 'center',
      render: (_, record) => `${record.requiredPresent}/${record.requiredTotal}`,
    },
    {
      title: '缺失资料',
      dataIndex: 'missingTypes',
      render: (types: string[]) =>
        types.length === 0 ? (
          <Tag color="success">齐全</Tag>
        ) : (
          <Space size={4} wrap>
            {types.map((t) => (
              <Tag key={t} color={docTypeConfig[t]?.color ?? 'default'}>
                {docTypeConfig[t]?.label ?? t}
              </Tag>
            ))}
          </Space>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size="small">
          <Button size="small" type="primary" icon={<EyeOutlined />} onClick={() => openDrawer(record)}>
            资料夹
          </Button>
          <Button size="small" onClick={() => {
            setActiveRow(record);
            openUpload(record.missingTypes);
          }}>
            上传
          </Button>
        </Space>
      ),
    },
  ];

  /** 齐全度清单渲染（必备项） */
  const renderChecklistItems = (items: ChecklistItem[]) => (
    <List
      size="small"
      dataSource={items}
      renderItem={(item) => {
        const cfg = docTypeConfig[item.docType] ?? { label: item.docType, color: 'default' };
        return (
          <List.Item
            actions={[
              item.present ? (
                <Tag color="success" key="ok">已收 {item.count} 份</Tag>
              ) : (
                <Tag color="error" key="missing">缺失</Tag>
              ),
            ]}
          >
            <Space>
              <Tag color={cfg.color}>{cfg.label}</Tag>
              {item.nearestExpireDate && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  最早到期 {item.nearestExpireDate}
                </Typography.Text>
              )}
            </Space>
          </List.Item>
        );
      }}
    />
  );

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic title="在服企业" value={overview?.companyTotal ?? 0} suffix="户" />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="资料齐全"
              value={overview?.complete ?? 0}
              suffix="户"
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="资料不全"
              value={overview?.incomplete ?? 0}
              suffix="户"
              valueStyle={{ color: (overview?.incomplete ?? 0) > 0 ? '#fa8c16' : undefined }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="必备项 / 户"
              value={overview?.rows?.[0]?.requiredTotal ?? REQUIRED_TYPES.length}
              suffix="项"
            />
          </Card>
        </Col>
      </Row>

      {(overview?.incomplete ?? 0) > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message={`有 ${overview?.incomplete} 户必备资料不全`}
          description="营业执照、开户许可证、法人身份证、公司章程、租赁合同是接户与年检的硬性要求。缺项会导致开户受阻、工商年检被退回、客户流失时无法交接。"
        />
      )}

      <Card
        title={
          <Space>
            <span>企业资料齐全度</span>
            <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
              按齐全度升序排列，资料最不全的排在最前
            </Typography.Text>
          </Space>
        }
        extra={
          <Space>
            <Select
              style={{ width: 160 }}
              value={onlyIncomplete ? 'incomplete' : 'all'}
              onChange={(v) => {
                setOnlyIncomplete(v === 'incomplete');
                setOverviewPage(0);
              }}
              options={[
                { value: 'incomplete', label: '只看资料不全' },
                { value: 'all', label: '全部企业' },
              ]}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void loadOverview()}>
              刷新
            </Button>
          </Space>
        }
      >
        <Table<DocumentOverviewRow>
          rowKey="companyId"
          size="small"
          loading={overviewLoading}
          columns={overviewColumns}
          dataSource={rowData}
          locale={{
            emptyText: (
              <Empty
                description={
                  onlyIncomplete
                    ? '所有在服企业必备资料都已齐全'
                    : '还没有企业档案。请先在「公司主体」页建档'
                }
              />
            ),
          }}
          pagination={{
            current: overviewPage + 1,
            pageSize: 10,
            showSizeChanger: false,
            onChange: (p) => setOverviewPage(p - 1),
            showTotal: (t) => `共 ${t} 户`,
          }}
        />
      </Card>

      {/* 逐户资料夹 */}
      <Drawer
        title={
          activeRow ? `${activeRow.companyName ?? ''} · 资料夹` : '资料夹'
        }
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={860}
        loading={checklistLoading}
        extra={
          <Space>
            <Button
              type="primary"
              icon={<CloudUploadOutlined />}
              onClick={() => openUpload(checklist?.missingTypes ?? [])}
            >
              上传资料
            </Button>
          </Space>
        }
      >
        {checklist && (
          <>
            <Descriptions size="small" column={3} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="齐全度">
                <Progress
                  percent={checklist.completeness}
                  size="small"
                  status={checklist.completeness === 100 ? 'success' : 'exception'}
                  style={{ width: 120 }}
                />
              </Descriptions.Item>
              <Descriptions.Item label="已收/必备">
                {checklist.requiredPresent}/{checklist.requiredTotal}
              </Descriptions.Item>
              <Descriptions.Item label="资料总数">
                {checklist.documents?.length ?? 0} 份
              </Descriptions.Item>
            </Descriptions>

            <Divider orientation="left" style={{ marginTop: 0 }}>必备资料清单</Divider>
            {checklist.missingTypes.length > 0 ? (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 12 }}
                message={`还缺 ${checklist.missingTypes.length} 项：${checklist.missingTypes
                  .map((t) => docTypeConfig[t]?.label ?? t)
                  .join('、')}`}
              />
            ) : (
              <Alert type="success" showIcon style={{ marginBottom: 12 }} message="五项必备资料已齐全" />
            )}
            {renderChecklistItems(checklist.items ?? [])}

            <Divider orientation="left">已归档文件</Divider>
            <List
              size="small"
              dataSource={checklist.documents ?? []}
              locale={{ emptyText: <Empty description="还没有上传任何资料" /> }}
              renderItem={(doc) => {
                const typeCfg = docTypeConfig[doc.docType] ?? { label: doc.docType, color: 'default' };
                const statusCfg = docStatusConfig[doc.status] ?? { label: doc.status, color: 'default' };
                return (
                  <List.Item
                    actions={[
                      <Tooltip title="生成下载链接（直连对象存储）" key="dl">
                        <Button
                          type="link"
                          size="small"
                          icon={<DownloadOutlined />}
                          onClick={() => void handleDownload(doc)}
                        >
                          下载
                        </Button>
                      </Tooltip>,
                      doc.status === 'ACTIVE' ? (
                        <Button
                          type="link"
                          size="small"
                          key="archive"
                          onClick={() => handleArchive(doc)}
                        >
                          封存
                        </Button>
                      ) : null,
                      <Button
                        type="link"
                        size="small"
                        danger
                        key="del"
                        icon={<DeleteOutlined />}
                        onClick={() => handleDelete(doc)}
                      >
                        删除
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      title={
                        <Space size={6} wrap>
                          <span>{doc.docName}</span>
                          <Tag color={typeCfg.color}>{typeCfg.label}</Tag>
                          <Tag color={statusCfg.color}>{statusCfg.label}</Tag>
                          {doc.expireDate && (
                            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                              到期 {doc.expireDate}
                            </Typography.Text>
                          )}
                        </Space>
                      }
                      description={
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          {doc.fileName} · {formatSize(doc.fileSize)} · 上传于{' '}
                          {doc.uploadedAt ? dayjs(doc.uploadedAt).format('YYYY-MM-DD HH:mm') : '-'}
                        </Typography.Text>
                      }
                    />
                  </List.Item>
                );
              }}
            />
          </>
        )}
      </Drawer>

      {/* 上传弹窗 */}
      <Modal
        title={
          activeRow ? `上传资料 · ${activeRow.companyName ?? ''}` : '上传资料'
        }
        open={uploadOpen}
        confirmLoading={uploading}
        onOk={() => void submitUpload()}
        onCancel={() => {
          setUploadOpen(false);
          setFileList([]);
        }}
        okText="上传归档"
        width={620}
        destroyOnClose
      >
        <Upload.Dragger
          fileList={fileList}
          maxCount={1}
          beforeUpload={() => false}
          onChange={({ fileList: list }) => setFileList(list.slice(-1))}
          style={{ marginBottom: 16 }}
        >
          <p className="ant-upload-drag-icon">
            <CloudUploadOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽文件到此处</p>
          <p className="ant-upload-hint">支持图片、PDF、Word、Excel，单份最大 100MB</p>
        </Upload.Dragger>

        <Form form={uploadForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="docType"
                label="资料类型"
                rules={[{ required: true, message: '请选择资料类型' }]}
              >
                <Select
                  options={Object.entries(docTypeConfig).map(([value, cfg]) => ({
                    value,
                    label: cfg.label,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="docName" label="资料名称" extra="留空则使用文件名">
                <Input maxLength={200} placeholder="如 营业执照（正本）" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="issueDate" label="签发日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="expireDate"
                label="到期日期"
                extra="身份证有效期、合同期限等，留空表示长期有效"
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
