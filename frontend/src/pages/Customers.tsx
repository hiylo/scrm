/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Customers.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useRef, useState, useCallback } from 'react';
import type { Key, ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  App,
  Avatar,
  Button,
  Card,
  Col,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Upload,
} from 'antd';
import type { UploadProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  EyeOutlined,
  DownloadOutlined,
  ImportOutlined,
  FileExcelOutlined,
  ExportOutlined,
  UserAddOutlined,
  FireOutlined,
  ClockCircleOutlined,
  StopOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  MessageOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { apiClient, apiClientInstance } from '../api/client';
import { useDebounce } from '../hooks/useDebounce';
import { useRole } from '../hooks/useRole';
import { usePlatforms } from '../hooks/usePlatforms';
import { parseCsv, type ParsedRow } from '../utils/csv';
import Wework from './Wework';

/** 客户实体 */
interface ScrmCustomer {
  id: string;
  platformType: string;
  platformCustomerUid: string;
  nickname: string;
  avatarUrl?: string;
  ownerAccountId?: string;
  personaId?: string;
  lifecycle: string; // NEW / PROSPECT / ACTIVE / DORMANT / CHURNED / CONVERTED
  lastInteractionAt?: string;
  nextFollowUpAt?: string;
  remark?: string;
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

/** 导入失败的行 */
interface ImportFailedRow {
  row: number;
  reason: string;
  rawData?: string;
}

/** 导入结果 */
interface CustomerImportResultVo {
  successCount: number;
  failedCount: number;
  skippedCount: number;
  failedRows?: ImportFailedRow[];
}

/** 平台类型映射 (用于 Tag 颜色与显示文案) */
const platformConfig: Record<string, { label: string; color: string }> = {
  wework: { label: '企业微信', color: 'blue' },
};

/** 生命周期映射 (含图标, 与后端 ScrmCustomerDto @Pattern 保持一致) */
const lifecycleConfig: Record<string, { label: string; color: string; icon: ReactNode }> = {
  NEW: { label: '新客户', color: 'blue', icon: <UserAddOutlined /> },
  PROSPECT: { label: '意向', color: 'cyan', icon: <ExclamationCircleOutlined /> },
  ACTIVE: { label: '活跃', color: 'green', icon: <FireOutlined /> },
  DORMANT: { label: '休眠', color: 'orange', icon: <ClockCircleOutlined /> },
  CHURNED: { label: '流失', color: 'red', icon: <StopOutlined /> },
  CONVERTED: { label: '已转化', color: 'purple', icon: <CheckCircleOutlined /> },
};

/** 生命周期下拉选项 */
const lifecycleOptions = Object.entries(lifecycleConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 生命周期筛选选项 (含"全部") */
const lifecycleFilterOptions = [
  { value: '', label: '全部' },
  ...lifecycleOptions,
];

/** 表单值类型 */
interface CustomerFormValues {
  platformType: string;
  platformCustomerUid: string;
  nickname: string;
  avatarUrl?: string;
  ownerAccountId?: string;
  lifecycle: string;
}

/**
 * 客户列表管理页面
 * 支持搜索 / 筛选 / 创建 / 编辑 / 删除 / 导出 / 导入 / 下载模板 / 批量删除
 */
export default function Customers() {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const { canEdit, canDelete } = useRole();
  // 平台列表 (从后端 API 动态获取, 失败时使用回退列表)
  const { platforms } = usePlatforms();
  // 平台下拉选项 (规范化为小写以匹配现有 platformConfig 与数据格式)
  const platformOptions = platforms.map(p => ({
    value: p.platformType.toLowerCase(),
    label: p.displayName,
  }));
  // 从 URL 查询参数读取筛选状态 (实现筛选状态持久化, 用户导航返回后恢复筛选条件)
  const [searchParams, setSearchParams] = useSearchParams();
  const [customers, setCustomers] = useState<ScrmCustomer[]>([]);
  const [loading, setLoading] = useState(false);
  // page 为 0 索引, URL 中存储 1 索引 (page=1 对应第一页)
  const [page, setPage] = useState(() => {
    const p = Number(searchParams.get('page'));
    return Number.isFinite(p) && p > 0 ? p - 1 : 0;
  });
  const [size, setSize] = useState(() => {
    const s = Number(searchParams.get('size'));
    return Number.isFinite(s) && s > 0 ? s : 10;
  });
  const [total, setTotal] = useState(0);
  // 搜索与筛选条件 (keyword 为输入框值, debouncedKeyword 为防抖后用于请求的值)
  // 初始值从 URL 查询参数读取, 实现筛选状态持久化
  const [keyword, setKeyword] = useState(searchParams.get('q') || '');
  const debouncedKeyword = useDebounce(keyword, 300);
  const [platformFilter, setPlatformFilter] = useState<string | undefined>(
    searchParams.get('platform') || undefined,
  );
  const [lifecycleFilter, setLifecycleFilter] = useState<string | undefined>(
    searchParams.get('lifecycle') || undefined,
  );
  // 导入中状态
  const [importing, setImporting] = useState(false);
  // 导出中状态
  const [exporting, setExporting] = useState(false);
  // 新建/编辑弹窗
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ScrmCustomer | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<CustomerFormValues>();
  // 行选择 (用于批量操作)
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  // 导入结果弹窗
  const [importResult, setImportResult] = useState<CustomerImportResultVo | null>(null);
  // 批量打标签弹窗
  const [batchTagModalOpen, setBatchTagModalOpen] = useState(false);
  const [batchTagSubmitting, setBatchTagSubmitting] = useState(false);
  const [batchTagForm] = Form.useForm<{ tagKey: string; tagValue?: string }>();
  // CSV 客户端导入弹窗状态: 弹窗开关 / 解析后的全部行 / 解析中 / 导入中 / 文件名 / 归属账号 ID
  const [csvImportModalOpen, setCsvImportModalOpen] = useState(false);
  const [csvRows, setCsvRows] = useState<ParsedRow[]>([]);
  const [csvParsing, setCsvParsing] = useState(false);
  const [csvImporting, setCsvImporting] = useState(false);
  const [csvFileName, setCsvFileName] = useState<string>('');
  const [csvOwnerAccountId, setCsvOwnerAccountId] = useState<string>('');

  /** 拉取客户分页列表 */
  const fetchCustomers = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(size));
      if (debouncedKeyword) params.set('keyword', debouncedKeyword);
      if (platformFilter) params.set('platformType', platformFilter);
      if (lifecycleFilter) params.set('lifecycle', lifecycleFilter);
      const data = await apiClient.get<Page<ScrmCustomer>>(`/scrm/customers?${params.toString()}`);
      setCustomers(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setCustomers([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, size, debouncedKeyword, platformFilter, lifecycleFilter]);

  useEffect(() => {
    fetchCustomers();
  }, [fetchCustomers]);

  // 防抖关键词变化时, 重置到第一页 (避免在高页码搜索时返回空列表)
  // 首次挂载不发信号: 否则会把手工输入的 ?page=3 深链静默打回第一页, 还多一次列表请求
  const lastKeywordRef = useRef(debouncedKeyword);
  useEffect(() => {
    if (lastKeywordRef.current === debouncedKeyword) return;
    lastKeywordRef.current = debouncedKeyword;
    setPage(0);
  }, [debouncedKeyword]);

  // 同步筛选状态到 URL 查询参数 (使用 replace 避免污染浏览器历史; keyword 使用防抖值避免频繁更新)
  useEffect(() => {
    const params: Record<string, string> = {};
    if (debouncedKeyword) params.q = debouncedKeyword;
    if (platformFilter) params.platform = platformFilter;
    if (lifecycleFilter) params.lifecycle = lifecycleFilter;
    if (page !== 0) params.page = String(page + 1);
    if (size !== 10) params.size = String(size);
    setSearchParams(params, { replace: true });
  }, [debouncedKeyword, platformFilter, lifecycleFilter, page, size, setSearchParams]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmCustomer) => {
    setEditing(record);
    form.setFieldsValue({
      platformType: record.platformType,
      platformCustomerUid: record.platformCustomerUid,
      nickname: record.nickname,
      avatarUrl: record.avatarUrl,
      ownerAccountId: record.ownerAccountId,
      lifecycle: record.lifecycle,
    });
    setModalOpen(true);
  };

  /** 提交新建/编辑表单 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editing) {
        await apiClient.put(`/scrm/customers/${editing.id}`, values);
        message.success('客户已更新');
      } else {
        await apiClient.post('/scrm/customers', values);
        message.success('客户已创建');
      }
      setModalOpen(false);
      fetchCustomers();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除客户 (带二次确认) */
  const handleDelete = (record: ScrmCustomer) => {
    modal.confirm({
      title: '删除客户',
      content: `确认删除客户 "${record.nickname}" 吗? 此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiClient.delete(`/scrm/customers/${record.id}`);
          message.success('客户已删除');
          // 清理已删除项的选中状态
          setSelectedRowKeys((keys) => keys.filter((k) => k !== record.id));
          fetchCustomers();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 批量删除选中客户 */
  const handleBatchDelete = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择要删除的客户');
      return;
    }
    modal.confirm({
      title: '批量删除客户',
      content: `确认删除选中的 ${selectedRowKeys.length} 个客户吗? 此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          // 串行删除以避免后端并发压力
          const failures: Key[] = [];
          for (const key of selectedRowKeys) {
            try {
              await apiClient.delete(`/scrm/customers/${key}`);
            } catch {
              failures.push(key);
            }
          }
          const successCount = selectedRowKeys.length - failures.length;
          if (failures.length === 0) {
            message.success(`成功删除 ${successCount} 个客户`);
          } else {
            message.warning(`成功 ${successCount} 个, 失败 ${failures.length} 个`);
          }
          setSelectedRowKeys([]);
          fetchCustomers();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 批量打标签 */
  const handleBatchTag = async () => {
    try {
      const values = await batchTagForm.validateFields();
      setBatchTagSubmitting(true);
      await apiClient.post('/scrm/customers/batch-tags', {
        customerIds: selectedRowKeys.map(k => Number(k)),
        tagKey: values.tagKey,
        tagValue: values.tagValue || null,
      });
      message.success(`已为 ${selectedRowKeys.length} 个客户打上标签 "${values.tagKey}"`);
      setBatchTagModalOpen(false);
      batchTagForm.resetFields();
      setSelectedRowKeys([]);
      fetchCustomers();
    } catch {
      // 表单校验失败或请求失败
    } finally {
      setBatchTagSubmitting(false);
    }
  };

  /** 跳转到客户详情页 */
  const goToDetail = (id: string) => {
    navigate(`/customers/${id}`);
  };

  /** 导出客户 (xlsx / csv), 返回 blob 需使用 axios 实例 */
  const handleExport = async (format: 'xlsx' | 'csv') => {
    setExporting(true);
    try {
      const response = await apiClientInstance.get(`/scrm/customers/export?format=${format}`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.download = `customers_${new Date().toISOString().slice(0, 10)}.${format === 'xlsx' ? 'xlsx' : 'csv'}`;
      link.click();
      window.URL.revokeObjectURL(url);
      message.success('导出成功');
    } catch {
      // 错误已由拦截器提示
    } finally {
      setExporting(false);
    }
  };

  /** 下载导入模板 */
  const handleDownloadTemplate = async () => {
    try {
      const response = await apiClientInstance.get('/scrm/customers/import-template?format=xlsx', {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.download = `customers_template_${new Date().toISOString().slice(0, 10)}.xlsx`;
      link.click();
      window.URL.revokeObjectURL(url);
      message.success('模板已下载');
    } catch {
      // 错误已由拦截器提示
    }
  };

  /** 导入客户 (multipart 上传) */
  const handleImport = async (file: File) => {
    setImporting(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('defaultOwnerAccountId', '0');
      const response = await apiClientInstance.post('/scrm/customers/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 60000,
      });
      // response.data 是 OperationResponse<CustomerImportResultVo>
      const result = response.data?.data as CustomerImportResultVo | undefined;
      if (result) {
        // 弹窗展示详细导入结果
        setImportResult(result);
        message.success(
          `导入完成: 成功 ${result.successCount} 条, 失败 ${result.failedCount} 条, 跳过 ${result.skippedCount} 条`,
        );
      } else {
        message.success('导入完成');
      }
      fetchCustomers();
    } catch {
      // 错误已由拦截器提示
    } finally {
      setImporting(false);
    }
  };

  /** Upload 组件配置 (手动上传, 不走 antd 内置 xhr) */
  const uploadProps: UploadProps = {
    accept: '.xlsx,.csv',
    showUploadList: false,
    beforeUpload: (file) => {
      handleImport(file);
      // 返回 false 阻止 antd 自动上传
      return false;
    },
  };

  /** 导出当前列表为客户 CSV (前端直接生成, 不依赖后端) */
  const handleExportCsv = () => {
    if (customers.length === 0) {
      message.warning('当前列表无数据, 无法导出');
      return;
    }
    // CSV 表头 (中文)
    const headers = [
      'ID',
      '平台类型',
      '平台客户UID',
      '昵称',
      '生命周期',
      '所属账号ID',
      '人设ID',
      '最后互动时间',
      '下次跟进时间',
      '备注',
      '创建时间',
    ];
    const rows = customers.map((c) => [
      c.id,
      c.platformType,
      c.platformCustomerUid,
      c.nickname,
      c.lifecycle,
      c.ownerAccountId ?? '',
      c.personaId ?? '',
      c.lastInteractionAt ? dayjs(c.lastInteractionAt).format('YYYY-MM-DD HH:mm:ss') : '',
      c.nextFollowUpAt ? dayjs(c.nextFollowUpAt).format('YYYY-MM-DD HH:mm:ss') : '',
      c.remark ?? '',
      c.createTime ? dayjs(c.createTime).format('YYYY-MM-DD HH:mm:ss') : '',
    ]);
    // 转义 CSV 单元格 (含逗号/换行/引号需用双引号包裹并转义内部引号)
    const escapeCell = (val: string): string => {
      if (/[",\n\r]/.test(val)) {
        return `"${val.replace(/"/g, '""')}"`;
      }
      return val;
    };
    const csvContent = [headers, ...rows]
      .map((row) => row.map(escapeCell).join(','))
      .join('\r\n');
    // 添加 BOM 头确保 Excel 正确识别 UTF-8 编码
    const blob = new Blob([`\uFEFF${csvContent}`], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `客户列表_${dayjs().format('YYYYMMDD_HHmmss')}.csv`;
    link.click();
    URL.revokeObjectURL(url);
    message.success(`已导出 ${customers.length} 条客户数据`);
  };

  // CSV 解析 (引号转义 / 表头映射) 已下沉到 utils/csv.ts 的 parseCsv, 原先的 BOM 剥离写作 text.replace(/^\uFEFF/, '');

  /** 下载 CSV 导入模板 (前端直接生成, 含 BOM 头确保 Excel 识别 UTF-8) */
  const handleDownloadCsvTemplate = () => {
    const template = '\uFEFF昵称,平台,平台客户ID,备注\n张三,wework,wwid_12345,VIP客户\n';
    const blob = new Blob([template], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = 'customer_import_template.csv';
    link.click();
    URL.revokeObjectURL(link.href);
  };

  /** 选择 CSV 文件后解析并生成预览数据 */
  const handleCsvFileSelect = async (file: File) => {
    setCsvFileName(file.name);
    setCsvParsing(true);
    setCsvRows([]);
    try {
      const rows = await parseCsv(file);
      setCsvRows(rows);
      message.success(`已解析 ${rows.length} 行数据`);
    } catch (err) {
      message.error(err instanceof Error ? err.message : 'CSV 解析失败');
      setCsvRows([]);
    } finally {
      setCsvParsing(false);
    }
  };

  /** 确认导入: 映射 CSV 列到客户字段并批量调用 POST /scrm/customers */
  const handleCsvImport = async () => {
    if (csvRows.length === 0) {
      message.warning('请先选择 CSV 文件');
      return;
    }
    if (!csvOwnerAccountId.trim()) {
      message.warning('请填写归属账号 ID');
      return;
    }
    setCsvImporting(true);
    try {
      // 列映射: 昵称→nickname, 平台→platformType, 平台客户ID→platformCustomerUid, 备注→remark
      // ownerAccountId 为必填字段, 由弹窗中的输入框统一指定 (所有导入客户归属同一账号)
      const ownerAccountId = Number(csvOwnerAccountId.trim());
      const results = await Promise.allSettled(
        csvRows.map((row) => {
          const payload = {
            nickname: row['昵称'] || '',
            platformType: row['平台'] || '',
            platformCustomerUid: row['平台客户ID'] || '',
            ownerAccountId,
            remark: row['备注'] || '',
          };
          return apiClient.post('/scrm/customers', payload);
        }),
      );
      const successCount = results.filter((r) => r.status === 'fulfilled').length;
      const failedCount = results.filter((r) => r.status === 'rejected').length;
      if (failedCount === 0) {
        message.success(`导入完成: 成功 ${successCount} 条`);
      } else {
        message.warning(`导入完成: 成功 ${successCount} 条, 失败 ${failedCount} 条`);
      }
      // 关闭弹窗并重置状态
      setCsvImportModalOpen(false);
      setCsvRows([]);
      setCsvFileName('');
      setCsvOwnerAccountId('');
      // 刷新客户列表
      fetchCustomers();
    } catch {
      // 错误已由 axios 拦截器统一提示
    } finally {
      setCsvImporting(false);
    }
  };

  /** 导出下拉菜单项 */
  const exportMenuItems = [
    { key: 'xlsx', label: '导出 Excel', icon: <FileExcelOutlined /> },
    { key: 'csv', label: '导出 CSV', icon: <FileExcelOutlined /> },
  ];

  /** 行选择配置 */
  const rowSelection = {
    selectedRowKeys,
    onChange: (keys: Key[]) => setSelectedRowKeys(keys),
    preserveSelectedRowKeys: true,
  };

  /** 表格列定义 */
  const columns: ColumnsType<ScrmCustomer> = [
    {
      title: '客户',
      dataIndex: 'nickname',
      key: 'nickname',
      width: 220,
      // 支持按昵称排序 (客户端排序)
      sorter: (a, b) => (a.nickname || '').localeCompare(b.nickname || ''),
      render: (_, record) => (
        <Space>
          {/* 头像加载失败时回退显示昵称首字母 */}
          <Avatar src={record.avatarUrl} size="small">
            {record.nickname?.[0]?.toUpperCase() || '?'}
          </Avatar>
          <span>{record.nickname}</span>
        </Space>
      ),
    },
    {
      title: '平台类型',
      dataIndex: 'platformType',
      key: 'platformType',
      width: 120,
      render: (value: string) => {
        const cfg = platformConfig[value] || { label: value, color: 'default' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '平台 UID',
      dataIndex: 'platformCustomerUid',
      key: 'platformCustomerUid',
      width: 180,
      ellipsis: true,
    },
    {
      title: '生命周期',
      dataIndex: 'lifecycle',
      key: 'lifecycle',
      width: 120,
      render: (value: string) => {
        const cfg = lifecycleConfig[value] || { label: value, color: 'default', icon: null };
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        );
      },
    },
    {
      title: '所属账号',
      dataIndex: 'ownerAccountId',
      key: 'ownerAccountId',
      width: 110,
      render: (value?: string) => (value ? String(value) : '-'),
    },
    {
      title: '最后交互',
      dataIndex: 'lastInteractionAt',
      key: 'lastInteractionAt',
      width: 160,
      // 支持按最后交互时间排序 (客户端排序, 缺失值视为 0)
      sorter: (a, b) =>
        new Date(a.lastInteractionAt || 0).getTime() - new Date(b.lastInteractionAt || 0).getTime(),
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '下次跟进',
      dataIndex: 'nextFollowUpAt',
      key: 'nextFollowUpAt',
      width: 160,
      // 支持按下次跟进时间排序 (缺失值排最后)
      sorter: (a, b) =>
        new Date(a.nextFollowUpAt || 0).getTime() - new Date(b.nextFollowUpAt || 0).getTime(),
      render: (value?: string) => {
        if (!value) return <span style={{ color: 'var(--ant-color-text-tertiary)' }}>-</span>;
        const followUpTime = dayjs(value);
        const now = dayjs();
        // 逾期: 红色加粗; 即将到期(24h内): 橙色; 正常: 默认色
        if (followUpTime.isBefore(now)) {
          return (
            <span style={{ color: 'var(--ant-color-error)', fontWeight: 500 }}>
              {followUpTime.format('YYYY-MM-DD HH:mm')}
            </span>
          );
        }
        if (followUpTime.isBefore(now.add(24, 'hour'))) {
          return (
            <span style={{ color: 'var(--ant-color-warning)' }}>
              {followUpTime.format('YYYY-MM-DD HH:mm')}
            </span>
          );
        }
        return <span>{followUpTime.format('YYYY-MM-DD HH:mm')}</span>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      // 支持按创建时间排序 (客户端排序, 缺失值视为 0)
      sorter: (a, b) => new Date(a.createTime || 0).getTime() - new Date(b.createTime || 0).getTime(),
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 280,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={(e) => {
              e.stopPropagation();
              goToDetail(record.id);
            }}
          >
            查看
          </Button>
          <Button
            type="link"
            size="small"
            icon={<MessageOutlined />}
            onClick={(e) => {
              e.stopPropagation();
              navigate(`/conversations?customerId=${record.id}`);
            }}
          >
            发消息
          </Button>
          {canEdit && (
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={(e) => {
                e.stopPropagation();
                openEdit(record);
              }}
            >
              编辑
            </Button>
          )}
          {canDelete && (
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={(e) => {
                e.stopPropagation();
                handleDelete(record);
              }}
            >
              删除
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Tabs
      defaultActiveKey="scrm"
      items={[
        {
          key: 'scrm',
          label: '客户列表',
          children: (
            <>
      {/* 顶部工具栏 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={8} md={6}>
            <Input
              placeholder="搜索昵称 / 平台 UID"
              allowClear
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              suffix={
                keyword && keyword !== debouncedKeyword ? <LoadingOutlined /> : null
              }
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              placeholder="平台类型"
              allowClear
              style={{ width: '100%' }}
              options={platformOptions}
              value={platformFilter}
              onChange={(v) => {
                setPlatformFilter(v);
                setPage(0);
              }}
            />
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Select
              placeholder="生命周期"
              style={{ width: '100%' }}
              options={lifecycleFilterOptions}
              value={lifecycleFilter ?? ''}
              onChange={(v) => {
                setLifecycleFilter(v || undefined);
                setPage(0);
              }}
            />
          </Col>
          <Col flex="auto">
            <Space style={{ float: 'right' }} wrap>
              {selectedRowKeys.length > 0 && canEdit && (
                <Button
                  icon={<TagsOutlined />}
                  onClick={() => setBatchTagModalOpen(true)}
                >
                  批量打标签 ({selectedRowKeys.length})
                </Button>
              )}
              {selectedRowKeys.length > 0 && canDelete && (
                <Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete}>
                  批量删除 ({selectedRowKeys.length})
                </Button>
              )}
              <Button icon={<ReloadOutlined />} onClick={fetchCustomers}>
                刷新
              </Button>
              <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>
                下载模板
              </Button>
              <Button icon={<DownloadOutlined />} onClick={handleExportCsv}>
                导出 CSV
              </Button>
              {canEdit && (
                <Button icon={<ImportOutlined />} onClick={() => setCsvImportModalOpen(true)}>
                  导入 CSV
                </Button>
              )}
              {canEdit && (
                <Upload {...uploadProps}>
                  <Button icon={<ImportOutlined />} loading={importing}>
                    导入
                  </Button>
                </Upload>
              )}
              <Dropdown
                menu={{
                  items: exportMenuItems,
                  onClick: ({ key }) => handleExport(key as 'xlsx' | 'csv'),
                }}
              >
                <Button icon={<ExportOutlined />} loading={exporting}>
                  导出
                </Button>
              </Dropdown>
              {canEdit && (
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  新建客户
                </Button>
              )}
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 客户列表 */}
      <Card>
        <Table<ScrmCustomer>
          rowKey="id"
          columns={columns}
          dataSource={customers}
          loading={loading}
          rowSelection={rowSelection}
          scroll={{ x: 'max-content' }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无客户数据"
                style={{ padding: 32 }}
              >
                <Space>
                  {canEdit && (
                    <>
                      <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                        新建客户
                      </Button>
                      <Button
                        icon={<ImportOutlined />}
                        onClick={() =>
                          message.info('请点击上方"导入"按钮上传 Excel/CSV 文件')
                        }
                      >
                        导入客户
                      </Button>
                    </>
                  )}
                </Space>
              </Empty>
            ),
          }}
          onRow={(record) => ({
            onClick: () => goToDetail(record.id),
            style: { cursor: 'pointer' },
          })}
          pagination={{
            current: page + 1,
            pageSize: size,
            total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p - 1);
              setSize(s);
            },
          }}
        />
      </Card>

      {/* 新建/编辑弹窗 */}
      <Modal
        title={editing ? '编辑客户' : '新建客户'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          preserve={false}
        >
          <Form.Item
            name="platformType"
            label="平台类型"
            rules={[{ required: true, message: '请选择平台类型' }]}
          >
            <Select placeholder="请选择平台" options={platformOptions} />
          </Form.Item>
          <Form.Item
            name="platformCustomerUid"
            label="平台客户 UID"
            rules={[{ required: true, message: '请输入平台客户 UID' }]}
          >
            <Input placeholder="请输入平台客户 UID" />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[{ required: true, message: '请输入昵称' }]}
          >
            <Input placeholder="请输入昵称" />
          </Form.Item>
          <Form.Item name="avatarUrl" label="头像 URL">
            <Input placeholder="请输入头像 URL" />
          </Form.Item>
          <Form.Item name="ownerAccountId" label="所属账号 ID">
            <Input placeholder="请输入所属账号 ID" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="lifecycle" label="生命周期">
            <Select placeholder="请选择生命周期" options={lifecycleOptions} allowClear />
          </Form.Item>
        </Form>
      </Modal>

      {/* 导入结果弹窗 */}
      <Modal
        title="导入结果"
        open={importResult !== null}
        onCancel={() => setImportResult(null)}
        footer={[
          <Button key="close" type="primary" onClick={() => setImportResult(null)}>
            关闭
          </Button>,
        ]}
      >
        {importResult && (
          <div>
            <Space style={{ marginBottom: 16 }} size="large">
              <Tag color="green" icon={<CheckCircleOutlined />}>
                成功 {importResult.successCount} 条
              </Tag>
              <Tag color="red" icon={<ExclamationCircleOutlined />}>
                失败 {importResult.failedCount} 条
              </Tag>
              <Tag color="orange" icon={<ClockCircleOutlined />}>
                跳过 {importResult.skippedCount} 条
              </Tag>
            </Space>
            {importResult.failedRows && importResult.failedRows.length > 0 && (
              <div>
                <div style={{ marginBottom: 8, fontWeight: 500 }}>失败明细:</div>
                <Table<ImportFailedRow>
                  rowKey="row"
                  dataSource={importResult.failedRows}
                  columns={[
                    { title: '行号', dataIndex: 'row', key: 'row', width: 80 },
                    { title: '原因', dataIndex: 'reason', key: 'reason' },
                  ]}
                  size="small"
                  pagination={{ pageSize: 5, showSizeChanger: false }}
                />
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* 批量打标签弹窗 */}
      <Modal
        title={`批量打标签 (${selectedRowKeys.length} 个客户)`}
        open={batchTagModalOpen}
        onOk={handleBatchTag}
        onCancel={() => { setBatchTagModalOpen(false); batchTagForm.resetFields(); }}
        confirmLoading={batchTagSubmitting}
        destroyOnHidden
      >
        <Form form={batchTagForm} layout="vertical" preserve={false}>
          <Form.Item
            name="tagKey"
            label="标签键"
            rules={[
              { required: true, message: '请输入标签键' },
              { max: 50, message: '标签键不超过 50 个字符' },
            ]}
          >
            <Input placeholder="请输入标签键, 如 VIP / 意向等级" maxLength={50} />
          </Form.Item>
          <Form.Item
            name="tagValue"
            label="标签值 (可选)"
            rules={[{ max: 200, message: '标签值不超过 200 个字符' }]}
          >
            <Input placeholder="请输入标签值, 如 黄金 / 白银" maxLength={200} />
          </Form.Item>
          <div style={{ color: '#94a3b8', fontSize: 12 }}>
            将对选中的 {selectedRowKeys.length} 个客户批量打上指定标签, 已存在相同键的标签将被覆盖。
          </div>
        </Form>
      </Modal>

      {/* CSV 客户端导入弹窗: 选择文件 / 填写归属账号 / 预览前 5 行 / 下载模板 / 确认导入 */}
      <Modal
        title="导入 CSV"
        open={csvImportModalOpen}
        onOk={handleCsvImport}
        onCancel={() => { setCsvImportModalOpen(false); setCsvRows([]); setCsvFileName(''); setCsvOwnerAccountId(''); }}
        confirmLoading={csvImporting}
        okText="确认导入"
        okButtonProps={{ disabled: csvRows.length === 0 }}
        cancelText="取消"
        destroyOnHidden
        width={720}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          {/* 归属账号 ID (必填, 所有导入客户将归属此账号) */}
          <div>
            <div style={{ marginBottom: 4, fontWeight: 500 }}>
              归属账号 ID <span style={{ color: 'var(--ant-color-error)' }}>*</span>
            </div>
            <Input
              placeholder="请输入归属账号 ID (数字)"
              value={csvOwnerAccountId}
              onChange={(e) => setCsvOwnerAccountId(e.target.value)}
              style={{ width: '100%' }}
            />
            <div style={{ color: '#94a3b8', fontSize: 12, marginTop: 4 }}>
              所有导入的客户将归属此账号, 请在导入前确认账号 ID 正确
            </div>
          </div>
          <div>
            <Upload accept=".csv" showUploadList={false} beforeUpload={(file) => { handleCsvFileSelect(file); return false; }}>
              <Button icon={<ImportOutlined />} loading={csvParsing}>选择 CSV 文件</Button>
            </Upload>
            <Button type="link" onClick={handleDownloadCsvTemplate}>下载导入模板</Button>
          </div>
          {csvFileName && (
            <div style={{ color: 'var(--color-text-secondary)' }}>已选择文件: {csvFileName}</div>
          )}
          {csvRows.length > 0 && (
            <div>
              <div style={{ marginBottom: 8, fontWeight: 500 }}>
                预览 (前 5 行, 共 {csvRows.length} 行):
              </div>
              <Table<ParsedRow>
                size="small"
                rowKey={(_, index) => String(index ?? 0)}
                dataSource={csvRows.slice(0, 5)}
                columns={Object.keys(csvRows[0]).map((col) => ({
                  title: col,
                  dataIndex: col,
                  key: col,
                  ellipsis: true,
                }))}
                pagination={false}
                scroll={{ x: 'max-content' }}
              />
            </div>
          )}
        </Space>
      </Modal>
    </>
          ),
        },
        {
          key: 'wework-contacts',
          label: '企微外部联系人',
          children: <Wework singleView defaultTab="contacts" />,
        },
      ]}
    />
  );
}
