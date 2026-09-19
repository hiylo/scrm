/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Campaigns.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState, useCallback, useMemo, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  App,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  PauseCircleOutlined,
  StopOutlined,
  CaretRightOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  FileTextOutlined,
  ThunderboltOutlined,
  TeamOutlined,
  SyncOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';
import { useDebounce } from '../hooks/useDebounce';
import { useRole } from '../hooks/useRole';
import { usePlatforms } from '../hooks/usePlatforms';

const { RangePicker } = DatePicker;

/** 营销任务实体 (与后端 ScrmCampaignDto 对齐) */
interface ScrmCampaign {
  id: string;
  campaignName: string;
  campaignType: string; // AUTO_ADD_FRIEND / AUTO_POST / AUTO_CHAT / AUTO_NURTURE / AUTO_REPLY
  platformType?: string;
  fleetId?: number;
  status: string; // DRAFT / RUNNING / PAUSED / COMPLETED
  cronExpression?: string;
  startTime?: string;
  endTime?: string;
  createTime?: string;
  updateTime?: string;
}

/** 任务效果分析报告 */
interface CampaignReport {
  campaignId: string;
  campaignName: string;
  campaignType: string;
  status: string;
  startDate?: string;
  endDate?: string;
  durationDays: number;
  totalExecutions: number;
  successCount: number;
  failedCount: number;
  runningCount: number;
  successRate: number;
  failureRate: number;
  uniqueAccounts: number;
  firstExecutionAt?: string;
  lastExecutionAt?: string;
  avgExecutionIntervalMinutes: number;
  dailyTrend: Array<{ date: string; totalCount: number; successCount: number; failedCount: number }>;
  topErrors: Array<{ errorCode: string; errorMessage: string; count: number }>;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** SOP 模板 (与后端 ScrmCampaignTemplateDto 对齐) */
interface CampaignTemplate {
  id?: string;
  templateName: string;
  campaignType: string; // AUTO_ADD_FRIEND / AUTO_POST / AUTO_CHAT / AUTO_NURTURE / AUTO_REPLY
  platformType: string; // wework
  templateContent: string; // JSON 字符串, 行为流模板
  description?: string;
  createTime?: string;
  updateTime?: string;
  version?: number;
}

/** 营销任务执行日志实体 (与后端 ScrmCampaignExecutionLogEntity 对齐) */
interface CampaignExecutionLog {
  id: string; // Long 序列化为字符串
  campaignId: string;
  action: string; // START / PAUSE / RESUME / STOP / CALLBACK
  status: string; // SUCCESS / FAILED / RUNNING
  errorCode?: string | null; // 可为空
  errorMessage?: string | null; // 可为空
  operatedBy?: string | null; // 可为空
  operatedAt: string; // ISO 时间字符串
}

/** 任务类型映射 (与后端 campaignType 字段对齐: AUTO_ADD_FRIEND / AUTO_POST / AUTO_CHAT / AUTO_NURTURE / AUTO_REPLY) */
const typeConfig: Record<string, { label: string; color: string }> = {
  AUTO_ADD_FRIEND: { label: '自动加好友', color: 'blue' },
  AUTO_POST: { label: '自动发圈', color: 'green' },
  AUTO_CHAT: { label: '自动聊天', color: 'orange' },
  AUTO_NURTURE: { label: '自动养号', color: 'purple' },
  AUTO_REPLY: { label: '自动回复', color: 'cyan' },
};

/** 任务状态映射 (含图标) */
const statusConfig: Record<string, { label: string; color: string; icon: ReactNode }> = {
  DRAFT: { label: '草稿', color: 'default', icon: <FileTextOutlined /> },
  RUNNING: { label: '运行中', color: 'processing', icon: <SyncOutlined spin /> },
  PAUSED: { label: '已暂停', color: 'warning', icon: <PauseCircleOutlined /> },
  COMPLETED: { label: '已完成', color: 'success', icon: <CheckCircleOutlined /> },
  FAILED: { label: '失败', color: 'error', icon: <CloseCircleOutlined /> },
  STOPPED: { label: '已停止', color: 'default', icon: <StopOutlined /> },
};

/** 类型下拉选项 */
const typeOptions = Object.entries(typeConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 状态下拉选项 */
const statusOptions = Object.entries(statusConfig).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** SOP 模板任务类型下拉选项 */
const campaignTypeOptions = [
  { value: 'AUTO_ADD_FRIEND', label: '自动加好友' },
  { value: 'AUTO_POST', label: '自动发布' },
  { value: 'AUTO_CHAT', label: '自动聊天' },
  { value: 'AUTO_NURTURE', label: '自动养号' },
  { value: 'AUTO_REPLY', label: '自动回复' },
];

/** SOP 模板任务类型颜色映射 (AUTO_ADD_FRIEND=blue, AUTO_POST=green, AUTO_CHAT=cyan, AUTO_NURTURE=orange, AUTO_REPLY=purple) */
const templateTypeColorMap: Record<string, string> = {
  AUTO_ADD_FRIEND: 'blue',
  AUTO_POST: 'green',
  AUTO_CHAT: 'cyan',
  AUTO_NURTURE: 'orange',
  AUTO_REPLY: 'purple',
};

/** 表单值类型 */
interface CampaignFormValues {
  campaignName: string;
  campaignType: string;
  platformType?: string;
  timeRange?: [dayjs.Dayjs, dayjs.Dayjs];
}

/**
 * 营销任务管理页面
 * 支持创建 / 编辑 / 删除 / 启动 / 暂停 / 恢复 / 停止 / 效果分析
 */
export default function Campaigns() {
  const { message, modal } = App.useApp();
  const { canEdit, canDelete } = useRole();
  // 平台列表 (从后端 API 动态获取, 失败时使用回退列表)
  const { platforms } = usePlatforms();
  // 平台下拉选项 (规范化为小写以匹配现有数据格式)
  const platformOptions = platforms.map(p => ({
    value: p.platformType.toLowerCase(),
    label: p.displayName,
  }));
  const [campaigns, setCampaigns] = useState<ScrmCampaign[]>([]);
  const [loading, setLoading] = useState(false);
  // 从 URL 查询参数读取筛选状态 (实现筛选状态持久化, 用户导航返回后恢复筛选条件)
  const [searchParams, setSearchParams] = useSearchParams();
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
  // 搜索与筛选 (初始值从 URL 查询参数读取)
  const [keyword, setKeyword] = useState(searchParams.get('q') || '');
  const debouncedKeyword = useDebounce(keyword, 300);
  const [typeFilter, setTypeFilter] = useState<string | undefined>(
    searchParams.get('type') || undefined,
  );
  const [statusFilter, setStatusFilter] = useState<string | undefined>(
    searchParams.get('status') || undefined,
  );
  // 新建/编辑弹窗
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ScrmCampaign | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<CampaignFormValues>();
  // 效果分析弹窗
  const [reportOpen, setReportOpen] = useState(false);
  const [report, setReport] = useState<CampaignReport | null>(null);
  const [reportLoading, setReportLoading] = useState(false);
  // 执行日志 (效果分析弹窗内展示)
  const [execLogs, setExecLogs] = useState<CampaignExecutionLog[]>([]);
  const [execLogsLoading, setExecLogsLoading] = useState(false);
  // 状态操作中按钮 (用于按钮 loading)
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);
  // 当前激活的 Tab (campaigns / templates), 从 URL 查询参数读取实现持久化
  const [activeTab, setActiveTab] = useState(searchParams.get('tab') || 'campaigns');
  // SOP 模板列表
  const [templates, setTemplates] = useState<CampaignTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templatesLoaded, setTemplatesLoaded] = useState(false);
  // SOP 模板新建/编辑弹窗
  const [templateModalOpen, setTemplateModalOpen] = useState(false);
  const [templateModalMode, setTemplateModalMode] = useState<'create' | 'edit'>('create');
  const [templateForm] = Form.useForm();
  const [templateSubmitting, setTemplateSubmitting] = useState(false);
  const [editingTemplateId, setEditingTemplateId] = useState<string | null>(null);

  /** 拉取任务分页列表 */
  const fetchCampaigns = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', String(page));
      params.set('size', String(size));
      if (debouncedKeyword) params.set('keyword', debouncedKeyword);
      if (typeFilter) params.set('campaignType', typeFilter);
      if (statusFilter) params.set('status', statusFilter);
      const data = await apiClient.get<Page<ScrmCampaign>>(`/scrm/campaigns?${params.toString()}`);
      setCampaigns(data.content || []);
      setTotal(data.totalElements || 0);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setCampaigns([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, size, debouncedKeyword, typeFilter, statusFilter]);

  useEffect(() => {
    fetchCampaigns();
  }, [fetchCampaigns]);

  // 同步筛选状态到 URL 查询参数 (使用 replace 避免污染浏览器历史; keyword 使用防抖值; tab 一并持久化)
  useEffect(() => {
    const params: Record<string, string> = {};
    if (debouncedKeyword) params.q = debouncedKeyword;
    if (typeFilter) params.type = typeFilter;
    if (statusFilter) params.status = statusFilter;
    if (page !== 0) params.page = String(page + 1);
    if (size !== 10) params.size = String(size);
    if (activeTab !== 'campaigns') params.tab = activeTab;
    setSearchParams(params, { replace: true });
  }, [debouncedKeyword, typeFilter, statusFilter, page, size, activeTab, setSearchParams]);

  /** 打开新建弹窗 */
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  /** 打开编辑弹窗 */
  const openEdit = (record: ScrmCampaign) => {
    setEditing(record);
    form.setFieldsValue({
      campaignName: record.campaignName,
      campaignType: record.campaignType,
      platformType: record.platformType,
      timeRange:
        record.startTime && record.endTime
          ? [dayjs(record.startTime), dayjs(record.endTime)]
          : undefined,
    });
    setModalOpen(true);
  };

  /** 提交新建/编辑表单 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      // 校验时间范围: 开始时间必须早于结束时间
      if (values.timeRange?.[0] && values.timeRange?.[1]) {
        if (!values.timeRange[0].isBefore(values.timeRange[1])) {
          message.error('开始时间必须早于结束时间');
          return;
        }
      }
      setSubmitting(true);
      // 时间范围拆分为开始/结束 ISO 字符串
      const payload = {
        campaignName: values.campaignName,
        campaignType: values.campaignType,
        platformType: values.platformType,
        startTime: values.timeRange?.[0] ? values.timeRange[0].toISOString() : undefined,
        endTime: values.timeRange?.[1] ? values.timeRange[1].toISOString() : undefined,
      };
      if (editing) {
        await apiClient.put(`/scrm/campaigns/${editing.id}`, payload);
        message.success('任务已更新');
      } else {
        await apiClient.post('/scrm/campaigns', payload);
        message.success('任务已创建');
      }
      setModalOpen(false);
      fetchCampaigns();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除任务 (带二次确认) */
  const handleDelete = (record: ScrmCampaign) => {
    modal.confirm({
      title: '删除任务',
      content: `确认删除任务 "${record.campaignName}" 吗? 此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiClient.delete(`/scrm/campaigns/${record.id}`);
          message.success('任务已删除');
          fetchCampaigns();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 执行状态变更操作 (启动/暂停/恢复/停止) */
  const handleStatusAction = async (
    record: ScrmCampaign,
    action: 'start' | 'pause' | 'resume' | 'stop',
    label: string,
  ) => {
    setActionLoadingId(record.id);
    try {
      await apiClient.post(`/scrm/campaigns/${record.id}/${action}`);
      message.success(`${label}成功`);
      fetchCampaigns();
    } catch {
      // 错误已由拦截器提示
    } finally {
      setActionLoadingId(null);
    }
  };

  /** 拉取任务执行日志 (效果分析弹窗内展示, 默认拉取前 10 条) */
  const fetchExecLogs = async (campaignId: string) => {
    setExecLogsLoading(true);
    try {
      const data = await apiClient.get<Page<CampaignExecutionLog>>(
        `/scrm/campaigns/${campaignId}/execution-logs`,
        { params: { page: 0, size: 10 } },
      );
      setExecLogs(data.content || []);
    } catch {
      // 错误已由拦截器提示
      setExecLogs([]);
    } finally {
      setExecLogsLoading(false);
    }
  };

  /** 打开效果分析弹窗 */
  const openReport = async (record: ScrmCampaign) => {
    setReportOpen(true);
    setReport(null);
    setExecLogs([]); // 重置执行日志, 避免显示上一个任务的数据
    setReportLoading(true);
    try {
      const data = await apiClient.get<CampaignReport>(`/scrm/campaigns/${record.id}/report`);
      setReport(data);
    } catch {
      // 错误已由拦截器提示
    } finally {
      setReportLoading(false);
    }
    // 并行拉取执行日志 (不阻塞报告加载)
    fetchExecLogs(record.id);
  };

  /** 拉取 SOP 模板列表 (默认拉取前 20 条) */
  const fetchTemplates = useCallback(async () => {
    setTemplatesLoading(true);
    try {
      const data = await apiClient.get<Page<CampaignTemplate>>(
        '/scrm/campaigns/templates/list',
        { params: { page: 0, size: 20 } },
      );
      setTemplates(data.content || []);
      setTemplatesLoaded(true);
    } catch {
      // 错误已由 axios 拦截器统一提示
      setTemplates([]);
    } finally {
      setTemplatesLoading(false);
    }
  }, []);

  /** 打开新建模板弹窗 */
  const openCreateTemplate = () => {
    setTemplateModalMode('create');
    setEditingTemplateId(null);
    templateForm.resetFields();
    setTemplateModalOpen(true);
  };

  /** 打开编辑模板弹窗 (预填表单) */
  const openEditTemplate = (record: CampaignTemplate) => {
    setTemplateModalMode('edit');
    setEditingTemplateId(record.id ?? null);
    templateForm.setFieldsValue({
      templateName: record.templateName,
      campaignType: record.campaignType,
      platformType: record.platformType,
      templateContent: record.templateContent,
      description: record.description,
    });
    setTemplateModalOpen(true);
  };

  /** 提交新建/编辑模板表单 (POST 创建 / PUT 更新) */
  const handleTemplateSubmit = async () => {
    try {
      const values = await templateForm.validateFields();
      setTemplateSubmitting(true);
      const payload = {
        templateName: values.templateName,
        campaignType: values.campaignType,
        platformType: values.platformType,
        templateContent: values.templateContent,
        description: values.description,
      };
      if (templateModalMode === 'edit' && editingTemplateId) {
        await apiClient.put(`/scrm/campaigns/templates/${editingTemplateId}`, payload);
        message.success('模板已更新');
      } else {
        await apiClient.post('/scrm/campaigns/templates', payload);
        message.success('模板已创建');
      }
      setTemplateModalOpen(false);
      fetchTemplates();
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setTemplateSubmitting(false);
    }
  };

  /** 删除模板 (带二次确认) */
  const handleDeleteTemplate = (record: CampaignTemplate) => {
    modal.confirm({
      title: '删除模板',
      content: `确认删除模板 "${record.templateName}" 吗? 此操作不可恢复。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          if (!record.id) return;
          await apiClient.delete(`/scrm/campaigns/templates/${record.id}`);
          message.success('模板已删除');
          fetchTemplates();
        } catch {
          // 错误已由拦截器提示
        }
      },
    });
  };

  /** 切换 Tab (模板列表的按需加载由 useEffect 处理, 支持从 URL 恢复 tab 参数) */
  const handleTabChange = (key: string) => {
    setActiveTab(key);
  };

  /** 按天趋势 ECharts 柱状图配置 */
  const trendChartOption: EChartsOption = useMemo(() => {
    const trend = report?.dailyTrend || [];
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['总数', '成功', '失败'] },
      grid: { left: 40, right: 20, top: 40, bottom: 40 },
      xAxis: {
        type: 'category',
        data: trend.map((item) => dayjs(item.date).format('MM-DD')),
        axisLine: { lineStyle: { color: '#94a3b8' } },
      },
      yAxis: {
        type: 'value',
        axisLine: { lineStyle: { color: '#94a3b8' } },
        // 分割线使用半透明颜色, 亮色/暗色模式下均可见
        splitLine: { lineStyle: { color: 'rgba(128, 128, 128, 0.15)' } },
      },
      series: [
        {
          name: '总数',
          type: 'bar',
          data: trend.map((item) => item.totalCount),
          itemStyle: { color: '#1677ff' },
        },
        {
          name: '成功',
          type: 'bar',
          data: trend.map((item) => item.successCount),
          itemStyle: { color: '#52c41a' },
        },
        {
          name: '失败',
          type: 'bar',
          data: trend.map((item) => item.failedCount),
          itemStyle: { color: '#ff4d4f' },
        },
      ],
    };
  }, [report]);

  /** 表格列定义 */
  const columns: ColumnsType<ScrmCampaign> = [
    {
      title: '任务名称',
      dataIndex: 'campaignName',
      key: 'campaignName',
      ellipsis: true,
      // 支持按任务名称排序 (客户端排序)
      sorter: (a, b) => (a.campaignName || '').localeCompare(b.campaignName || ''),
    },
    {
      title: '类型',
      dataIndex: 'campaignType',
      key: 'campaignType',
      width: 110,
      render: (value: string) => {
        const cfg = typeConfig[value] || { label: value, color: 'default' };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (value: string) => {
        const cfg = statusConfig[value] || { label: value, color: 'default', icon: null };
        return (
          <Tag color={cfg.color} icon={cfg.icon}>
            {cfg.label}
          </Tag>
        );
      },
    },
    {
      title: '平台',
      dataIndex: 'platformType',
      key: 'platformType',
      width: 100,
      render: (value?: string) => value || '-',
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      key: 'startTime',
      width: 150,
      // 支持按开始时间排序 (客户端排序, 缺失值视为 0)
      sorter: (a, b) => new Date(a.startTime || 0).getTime() - new Date(b.startTime || 0).getTime(),
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
      key: 'endTime',
      width: 150,
      // 支持按结束时间排序 (客户端排序, 缺失值视为 0)
      sorter: (a, b) => new Date(a.endTime || 0).getTime() - new Date(b.endTime || 0).getTime(),
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      render: (_, record) => {
        const isLoading = actionLoadingId === record.id;
        return (
          <Space size="small" wrap>
            {record.status === 'DRAFT' && (
              <>
                <Tooltip title="启动任务">
                  <Button
                    type="link"
                    size="small"
                    icon={<CaretRightOutlined />}
                    loading={isLoading}
                    onClick={() => handleStatusAction(record, 'start', '启动')}
                  >
                    启动
                  </Button>
                </Tooltip>
                {canEdit && (
                  <Tooltip title="编辑任务">
                    <Button
                      type="link"
                      size="small"
                      icon={<EditOutlined />}
                      onClick={() => openEdit(record)}
                    >
                      编辑
                    </Button>
                  </Tooltip>
                )}
                {canDelete && (
                  <Tooltip title="删除任务">
                    <Button
                      type="link"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => handleDelete(record)}
                    >
                      删除
                    </Button>
                  </Tooltip>
                )}
              </>
            )}
            {record.status === 'RUNNING' && (
              <>
                <Tooltip title="暂停任务">
                  <Button
                    type="link"
                    size="small"
                    icon={<PauseCircleOutlined />}
                    loading={isLoading}
                    onClick={() => handleStatusAction(record, 'pause', '暂停')}
                  >
                    暂停
                  </Button>
                </Tooltip>
                <Tooltip title="停止任务">
                  <Button
                    type="link"
                    size="small"
                    danger
                    icon={<StopOutlined />}
                    loading={isLoading}
                    onClick={() => handleStatusAction(record, 'stop', '停止')}
                  >
                    停止
                  </Button>
                </Tooltip>
                <Tooltip title="查看效果分析">
                  <Button
                    type="link"
                    size="small"
                    icon={<BarChartOutlined />}
                    onClick={() => openReport(record)}
                  >
                    效果分析
                  </Button>
                </Tooltip>
              </>
            )}
            {record.status === 'PAUSED' && (
              <>
                <Tooltip title="恢复任务">
                  <Button
                    type="link"
                    size="small"
                    icon={<CaretRightOutlined />}
                    loading={isLoading}
                    onClick={() => handleStatusAction(record, 'resume', '恢复')}
                  >
                    恢复
                  </Button>
                </Tooltip>
                <Tooltip title="停止任务">
                  <Button
                    type="link"
                    size="small"
                    danger
                    icon={<StopOutlined />}
                    loading={isLoading}
                    onClick={() => handleStatusAction(record, 'stop', '停止')}
                  >
                    停止
                  </Button>
                </Tooltip>
                <Tooltip title="查看效果分析">
                  <Button
                    type="link"
                    size="small"
                    icon={<BarChartOutlined />}
                    onClick={() => openReport(record)}
                  >
                    效果分析
                  </Button>
                </Tooltip>
              </>
            )}
            {(record.status === 'COMPLETED' ||
              record.status === 'FAILED' ||
              record.status === 'STOPPED') && (
              <>
                <Tooltip title="查看效果分析">
                  <Button
                    type="link"
                    size="small"
                    icon={<BarChartOutlined />}
                    onClick={() => openReport(record)}
                  >
                    效果分析
                  </Button>
                </Tooltip>
                {canDelete && (
                  <Tooltip title="删除任务">
                    <Button
                      type="link"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => handleDelete(record)}
                    >
                      删除
                    </Button>
                  </Tooltip>
                )}
              </>
            )}
          </Space>
        );
      },
    },
  ];

  /** 趋势表格列定义 */
  const trendColumns = [
    { title: '日期', dataIndex: 'date', key: 'date' },
    { title: '总数', dataIndex: 'totalCount', key: 'totalCount', width: 100 },
    { title: '成功', dataIndex: 'successCount', key: 'successCount', width: 100 },
    { title: '失败', dataIndex: 'failedCount', key: 'failedCount', width: 100 },
  ];

  /** 错误列表列定义 */
  const errorColumns = [
    { title: '错误码', dataIndex: 'errorCode', key: 'errorCode', width: 120 },
    { title: '错误信息', dataIndex: 'errorMessage', key: 'errorMessage' },
    { title: '次数', dataIndex: 'count', key: 'count', width: 80 },
  ];

  /** 执行日志动作颜色映射 (START=blue, PAUSE=orange, RESUME=cyan, STOP=red, CALLBACK=purple) */
  const execLogActionColorMap: Record<string, string> = {
    START: 'blue',
    PAUSE: 'orange',
    RESUME: 'cyan',
    STOP: 'red',
    CALLBACK: 'purple',
  };

  /** 执行日志状态颜色映射 (SUCCESS=green, FAILED=red, RUNNING=blue) */
  const execLogStatusColorMap: Record<string, string> = {
    SUCCESS: 'green',
    FAILED: 'red',
    RUNNING: 'blue',
  };

  /** 执行日志表格列定义 */
  const execLogColumns: ColumnsType<CampaignExecutionLog> = [
    {
      title: '动作',
      dataIndex: 'action',
      key: 'action',
      width: 100,
      render: (value: string) => (
        <Tag color={execLogActionColorMap[value] || 'default'}>{value}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={execLogStatusColorMap[value] || 'default'}>{value}</Tag>
      ),
    },
    {
      title: '错误信息',
      key: 'error',
      ellipsis: true,
      render: (_, record) => record.errorMessage || record.errorCode || '-',
    },
    {
      title: '操作人',
      dataIndex: 'operatedBy',
      key: 'operatedBy',
      width: 120,
      render: (value?: string | null) => value || '-',
    },
    {
      title: '操作时间',
      dataIndex: 'operatedAt',
      key: 'operatedAt',
      width: 170,
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
  ];

  /** SOP 模板表格列定义 */
  const templateColumns: ColumnsType<CampaignTemplate> = [
    {
      title: '模板名称',
      dataIndex: 'templateName',
      key: 'templateName',
      ellipsis: true,
    },
    {
      title: '任务类型',
      dataIndex: 'campaignType',
      key: 'campaignType',
      width: 120,
      render: (value: string) => {
        const opt = campaignTypeOptions.find((o) => o.value === value);
        return <Tag color={templateTypeColorMap[value] || 'default'}>{opt?.label || value}</Tag>;
      },
    },
    {
      title: '平台类型',
      dataIndex: 'platformType',
      key: 'platformType',
      width: 120,
      render: (value?: string) => {
        if (!value) return '-';
        const opt = platformOptions.find((o) => o.value === value);
        return <Tag>{opt?.label || value}</Tag>;
      },
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 170,
      render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_, record) => (
        <Space size="small">
          {canEdit && (
            <Tooltip title="编辑模板">
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={() => openEditTemplate(record)}
              >
                编辑
              </Button>
            </Tooltip>
          )}
          {canDelete && (
            <Tooltip title="删除模板">
              <Button
                type="link"
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={() => handleDeleteTemplate(record)}
              >
                删除
              </Button>
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Tabs
        activeKey={activeTab}
        onChange={handleTabChange}
        items={[
          {
            key: 'campaigns',
            label: '营销任务',
            children: (
              <>
                {/* 顶部工具栏 */}
                <Card style={{ marginBottom: 16 }}>
                  <Row gutter={[16, 16]} align="middle">
                    <Col xs={24} sm={8} md={6}>
                      <Input.Search
                        placeholder="搜索任务名称"
                        allowClear
                        value={keyword}
                        onSearch={(v) => {
                          setKeyword(v);
                          setPage(0);
                        }}
                        onChange={(e) => setKeyword(e.target.value)}
                      />
                    </Col>
                    <Col xs={12} sm={6} md={4}>
                      <Select
                        placeholder="任务类型"
                        allowClear
                        style={{ width: '100%' }}
                        options={typeOptions}
                        value={typeFilter}
                        onChange={(v) => {
                          setTypeFilter(v);
                          setPage(0);
                        }}
                      />
                    </Col>
                    <Col xs={12} sm={6} md={4}>
                      <Select
                        placeholder="状态"
                        allowClear
                        style={{ width: '100%' }}
                        options={statusOptions}
                        value={statusFilter}
                        onChange={(v) => {
                          setStatusFilter(v);
                          setPage(0);
                        }}
                      />
                    </Col>
                    <Col flex="auto">
                      <Space style={{ float: 'right' }}>
                        <Button icon={<ReloadOutlined />} onClick={fetchCampaigns}>
                          刷新
                        </Button>
                        {canEdit && (
                          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                            新建任务
                          </Button>
                        )}
                      </Space>
                    </Col>
                  </Row>
                </Card>

                {/* 任务列表 */}
                <Card>
                  <Table<ScrmCampaign>
                    rowKey="id"
                    columns={columns}
                    dataSource={campaigns}
                    loading={loading}
                    scroll={{ x: 1000 }}
                    locale={{
                      // 空状态展示新建任务 CTA 按钮, 复用工具栏的 openCreate 处理函数
                      emptyText: (
                        <Empty
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                          description="暂无任务数据"
                          style={{ padding: 32 }}
                        >
                          {canEdit && (
                            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                              新建任务
                            </Button>
                          )}
                        </Empty>
                      ),
                    }}
                    pagination={{
                      current: page + 1,
                      pageSize: size,
                      total,
                      showSizeChanger: true,
                      showTotal: (t) => `共 ${t} 条`,
                      showQuickJumper: true,
                      onChange: (p, s) => {
                        setPage(p - 1);
                        setSize(s);
                      },
                    }}
                  />
                </Card>
              </>
            ),
          },
          {
            key: 'templates',
            label: 'SOP 模板',
            children: (
              <>
                {/* 模板工具栏 */}
                <Card style={{ marginBottom: 16 }}>
                  <Row gutter={[16, 16]} align="middle">
                    <Col flex="auto">
                      <Space style={{ float: 'right' }}>
                        <Button icon={<ReloadOutlined />} onClick={fetchTemplates}>
                          刷新
                        </Button>
                        {canEdit && (
                          <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={openCreateTemplate}
                          >
                            新建模板
                          </Button>
                        )}
                      </Space>
                    </Col>
                  </Row>
                </Card>

                {/* 模板列表 */}
                <Card>
                  <Table<CampaignTemplate>
                    rowKey="id"
                    columns={templateColumns}
                    dataSource={templates}
                    loading={templatesLoading}
                    scroll={{ x: 1000 }}
                    locale={{
                      // 空状态展示新建模板 CTA 按钮, 复用工具栏的 openCreateTemplate 处理函数
                      emptyText: (
                        <Empty
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                          description="暂无模板数据"
                          style={{ padding: 32 }}
                        >
                          {canEdit && (
                            <Button
                              type="primary"
                              icon={<PlusOutlined />}
                              onClick={openCreateTemplate}
                            >
                              新建模板
                            </Button>
                          )}
                        </Empty>
                      ),
                    }}
                    pagination={false}
                  />
                </Card>
              </>
            ),
          },
        ]}
      />

      {/* 新建/编辑弹窗 */}
      <Modal
        title={editing ? '编辑任务' : '新建任务'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnHidden
        width={560}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="campaignName"
            label="任务名称"
            rules={[{ required: true, message: '请输入任务名称' }]}
            extra="请输入有意义的任务名称便于后续检索"
          >
            <Input placeholder="请输入任务名称" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="campaignType"
            label="任务类型"
            rules={[{ required: true, message: '请选择任务类型' }]}
          >
            <Select placeholder="请选择任务类型" options={typeOptions} />
          </Form.Item>
          <Form.Item name="platformType" label="平台类型">
            <Select
              placeholder="请选择平台类型"
              allowClear
              options={platformOptions}
            />
          </Form.Item>
          <Form.Item
            name="timeRange"
            label="执行时间范围"
            extra="可选, 设置任务的开始与结束时间"
          >
            <RangePicker
              showTime
              style={{ width: '100%' }}
              placeholder={['开始时间', '结束时间']}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 效果分析弹窗 */}
      <Modal
        title="效果分析"
        open={reportOpen}
        onCancel={() => setReportOpen(false)}
        footer={<Button onClick={() => setReportOpen(false)}>关闭</Button>}
        width={860}
        destroyOnHidden
      >
        {reportLoading ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin />
          </div>
        ) : report ? (
          <div>
            {/* 顶部统计卡片 (带图标) */}
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="总执行次数"
                    value={report.totalExecutions}
                    prefix={<ThunderboltOutlined style={{ color: '#1677ff' }} />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="成功率"
                    value={report.successRate}
                    precision={2}
                    suffix="%"
                    prefix={<CheckCircleOutlined style={{ color: '#3f8600' }} />}
                    valueStyle={{ color: '#3f8600' }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="失败率"
                    value={report.failureRate}
                    precision={2}
                    suffix="%"
                    prefix={<CloseCircleOutlined style={{ color: '#cf1322' }} />}
                    valueStyle={{ color: '#cf1322' }}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="涉及账号数"
                    value={report.uniqueAccounts}
                    prefix={<TeamOutlined style={{ color: '#722ed1' }} />}
                  />
                </Card>
              </Col>
            </Row>
            {/* 次级统计 */}
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="成功次数"
                    value={report.successCount}
                    prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="失败次数"
                    value={report.failedCount}
                    prefix={<CloseCircleOutlined style={{ color: '#ff4d4f' }} />}
                  />
                </Card>
              </Col>
              <Col span={6}>
                <Card size="small">
                  <Statistic
                    title="运行中"
                    value={report.runningCount}
                    prefix={<SyncOutlined spin style={{ color: '#1677ff' }} />}
                  />
                </Card>
              </Col>
            </Row>
            {/* 按天趋势 ECharts 柱状图 */}
            <Card
              title={
                <Space>
                  <BarChartOutlined />
                  <span>按天趋势</span>
                </Space>
              }
              size="small"
              style={{ marginBottom: 16 }}
            >
              {(report.dailyTrend || []).length > 0 ? (
                <ReactECharts option={trendChartOption} style={{ height: 280 }} />
              ) : (
                <Empty description="暂无趋势数据" />
              )}
            </Card>
            {/* 趋势明细表格 */}
            <Card title="趋势明细" size="small" style={{ marginBottom: 16 }}>
              <Table
                rowKey="date"
                size="small"
                columns={trendColumns}
                dataSource={report.dailyTrend || []}
                pagination={false}
                scroll={{ y: 240 }}
              />
            </Card>
            {/* Top 错误 */}
            <Card
              title={
                <Space>
                  <InfoCircleOutlined />
                  <span>Top 错误</span>
                </Space>
              }
              size="small"
              style={{ marginBottom: 16 }}
            >
              <Table
                rowKey={(r) => r.errorCode}
                size="small"
                columns={errorColumns}
                dataSource={report.topErrors || []}
                pagination={false}
                scroll={{ y: 200 }}
              />
            </Card>
            {/* 执行日志 */}
            <Card
              title={
                <Space>
                  <FileTextOutlined />
                  <span>执行日志</span>
                </Space>
              }
              size="small"
            >
              <Table<CampaignExecutionLog>
                rowKey="id"
                size="small"
                columns={execLogColumns}
                dataSource={execLogs}
                loading={execLogsLoading}
                pagination={false}
                scroll={{ y: 240 }}
                locale={{ emptyText: <Empty description="暂无执行日志" /> }}
              />
            </Card>
          </div>
        ) : (
          <Empty description="暂无数据" />
        )}
      </Modal>

      {/* SOP 模板新建/编辑弹窗 */}
      <Modal
        title={templateModalMode === 'edit' ? '编辑 SOP 模板' : '创建 SOP 模板'}
        open={templateModalOpen}
        onOk={handleTemplateSubmit}
        onCancel={() => setTemplateModalOpen(false)}
        confirmLoading={templateSubmitting}
        destroyOnHidden
        width={640}
      >
        <Form form={templateForm} layout="vertical" preserve={false}>
          <Form.Item
            name="templateName"
            label="模板名称"
            rules={[
              { required: true, message: '请输入模板名称' },
              { max: 200, message: '模板名称最长 200 字符' },
            ]}
          >
            <Input placeholder="请输入模板名称" maxLength={200} />
          </Form.Item>
          <Form.Item
            name="campaignType"
            label="任务类型"
            rules={[{ required: true, message: '请选择任务类型' }]}
          >
            <Select placeholder="请选择任务类型" options={campaignTypeOptions} />
          </Form.Item>
          <Form.Item
            name="platformType"
            label="平台类型"
            rules={[{ required: true, message: '请选择平台类型' }]}
          >
            <Select placeholder="请选择平台类型" options={platformOptions} />
          </Form.Item>
          <Form.Item
            name="templateContent"
            label="模板内容"
            rules={[{ required: true, message: '请输入模板内容' }]}
            extra="行为流模板内容, JSON 格式"
          >
            <Input.TextArea
              placeholder="请输入 JSON 格式的行为流模板内容"
              autoSize={{ minRows: 6, maxRows: 16 }}
            />
          </Form.Item>
          <Form.Item
            name="description"
            label="描述"
            rules={[{ max: 500, message: '描述最长 500 字符' }]}
          >
            <Input.TextArea
              placeholder="请输入描述 (可选)"
              autoSize={{ minRows: 2, maxRows: 6 }}
              maxLength={500}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
