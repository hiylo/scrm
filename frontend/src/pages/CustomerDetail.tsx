/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerDetail.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  App,
  Avatar,
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Result,
  Select,
  Skeleton,
  Space,
  Spin,
  Steps,
  Tag,
  Timeline,
  Typography,
  Alert,
  DatePicker,
} from 'antd';
import {
  ArrowLeftOutlined,
  ClockCircleOutlined,
  EditOutlined,
  MessageOutlined,
  PlusOutlined,
  SaveOutlined,
  SwapOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';
import { isImeComposing } from '../utils/imeHelpers';

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
  /** 下次跟进时间 (ISO 字符串, 由后端 nextFollowUpAt 映射) */
  nextFollowUpAt?: string;
  /** 客户备注 (由后端 remark 字段映射) */
  remark?: string;
  /** 更新时间 (用于备注"上次更新"展示) */
  updateTime?: string;
  createTime?: string;
}

/** 客户标签 (与后端 ScrmCustomerTagEntity 对齐) */
interface CustomerTag {
  id: string;
  customerId: string;
  tagKey: string;
  tagValue?: string;
  createTime?: string;
}

/** 客户分组 (与后端 ScrmCustomerGroupEntity 对齐) */
interface CustomerGroup {
  id: string;
  groupName: string;
  description?: string;
}

/** 平台类型映射 (用于 Tag 颜色与显示文案) */
const platformConfig: Record<string, { label: string; color: string }> = {
  wework: { label: '企业微信', color: 'blue' },
};

/** 生命周期阶段标签与颜色映射 (中文文案) */
/** 生命周期标签映射 (与后端 ScrmCustomerDto @Pattern 保持一致) */
const LIFECYCLE_LABELS: Record<string, { label: string; color: string }> = {
  NEW: { label: '新客户', color: 'blue' },
  PROSPECT: { label: '意向客户', color: 'cyan' },
  ACTIVE: { label: '活跃客户', color: 'green' },
  DORMANT: { label: '沉睡客户', color: 'orange' },
  CHURNED: { label: '流失客户', color: 'red' },
  CONVERTED: { label: '已转化', color: 'purple' },
};

/** 生命周期阶段顺序 (用于进度条与时间线展示) */
const LIFECYCLE_STAGES = ['NEW', 'PROSPECT', 'ACTIVE', 'DORMANT', 'CHURNED', 'CONVERTED'] as const;

/** 生命周期下拉选项 */
const lifecycleOptions = Object.entries(LIFECYCLE_LABELS).map(([value, cfg]) => ({
  value,
  label: cfg.label,
}));

/** 标签颜色选项 */
const tagColorOptions = [
  { value: 'blue', label: '蓝色' },
  { value: 'green', label: '绿色' },
  { value: 'orange', label: '橙色' },
  { value: 'red', label: '红色' },
  { value: 'purple', label: '紫色' },
  { value: 'cyan', label: '青色' },
  { value: 'gold', label: '金色' },
  { value: 'magenta', label: '洋红' },
];

/** 编辑表单值 */
interface EditFormValues {
  nickname: string;
  avatarUrl?: string;
  ownerAccountId?: string;
  lifecycle: string;
}

/** 生命周期变更记录 (对齐后端 ScrmCustomerLifecycleHistoryEntity) */
interface LifecycleHistoryEntry {
  id: string;
  previousLifecycle: string;
  newLifecycle: string;
  remark?: string;
  operatorId?: string;
  operatorName?: string;
  changedAt: string;
}

/** 最近会话消息 (用于客户详情页内联展示) */
interface RecentMessage {
  id: string;
  content: string;
  messageType: string;
  direction: string;
  sentAt: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
}

/**
 * 客户详情页面
 * 展示客户基本信息 / 标签 / 所属分组, 支持编辑与标签管理
 */
export default function CustomerDetail() {
  const { message } = App.useApp();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [customer, setCustomer] = useState<ScrmCustomer | null>(null);
  const [tags, setTags] = useState<CustomerTag[]>([]);
  const [groups, setGroups] = useState<CustomerGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  // 编辑弹窗
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [editForm] = Form.useForm<EditFormValues>();
  // 标签弹窗
  const [tagModalOpen, setTagModalOpen] = useState(false);
  const [newTagName, setNewTagName] = useState('');
  const [newTagColor, setNewTagColor] = useState('blue');
  const [tagSubmitting, setTagSubmitting] = useState(false);
  // 账号列表（编辑弹窗下拉选择用）
  const [accounts, setAccounts] = useState<Array<{ id: string; accountName: string; platformType: string }>>([]);
  // 生命周期管理: 变更弹窗 / 变更备注 / 会话内变更历史
  const [lifecycleModalOpen, setLifecycleModalOpen] = useState(false);
  const [lifecycleSubmitting, setLifecycleSubmitting] = useState(false);
  const [pendingLifecycle, setPendingLifecycle] = useState<string>('');
  const [lifecycleRemark, setLifecycleRemark] = useState('');
  const [lifecycleHistory, setLifecycleHistory] = useState<LifecycleHistoryEntry[]>([]);
  // 跟进提醒: 安排弹窗 / 选择的跟进时间 / 跟进备注 / 提交中
  const [followUpModalOpen, setFollowUpModalOpen] = useState(false);
  const [followUpTime, setFollowUpTime] = useState<dayjs.Dayjs | null>(null);
  const [followUpRemark, setFollowUpRemark] = useState('');
  const [followUpSubmitting, setFollowUpSubmitting] = useState(false);
  // 最近会话消息 (内联展示, 无需跳转)
  const [recentMessages, setRecentMessages] = useState<RecentMessage[]>([]);
  const [recentMessagesLoading, setRecentMessagesLoading] = useState(false);
  const [recentConversationId, setRecentConversationId] = useState<string | null>(null);
  // 客户备注: 编辑内容 / 保存中 / 上次更新时间
  const [notes, setNotes] = useState('');
  const [notesSaving, setNotesSaving] = useState(false);
  const [notesUpdatedAt, setNotesUpdatedAt] = useState<string | null>(null);

  /** 拉取客户详情 / 标签 / 分组 */
  useEffect(() => {
    if (!id) {
      setError(new Error('缺少客户 ID'));
      setLoading(false);
      return;
    }
    const customerId = id;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        // 并行加载详情、标签、分组、生命周期历史
        const [customerData, tagsData, groupsData, historyData] = await Promise.all([
          apiClient.get<ScrmCustomer>(`/scrm/customers/${customerId}`),
          apiClient.get<CustomerTag[]>(`/scrm/customers/${customerId}/tags`).catch(() => [] as CustomerTag[]),
          apiClient.get<CustomerGroup[]>(`/scrm/customers/${customerId}/groups`).catch(() => [] as CustomerGroup[]),
          apiClient.get<LifecycleHistoryEntry[]>(`/scrm/customers/${customerId}/lifecycle-history`).catch(() => [] as LifecycleHistoryEntry[]),
        ]);
        setCustomer(customerData);
        setTags(tagsData || []);
        setGroups(groupsData || []);
        setLifecycleHistory(historyData || []);
      } catch (err) {
        setError(err instanceof Error ? err : new Error('加载失败'));
        // 错误已由 axios 拦截器统一提示
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [id]);

  /** 客户数据加载后, 初始化备注内容与上次更新时间 */
  useEffect(() => {
    if (customer) {
      setNotes(customer.remark || '');
      setNotesUpdatedAt(customer.updateTime || null);
    }
  }, [customer]);

  /** 加载账号列表用于下拉选择 */
  useEffect(() => {
    apiClient
      .get<{ content: Array<{ id: string; accountName: string; platformType: string }> }>(
        '/scrm/accounts?page=0&size=100',
      )
      .then((data) => setAccounts(data?.content || []))
      .catch(() => setAccounts([]));
  }, []);

  /**
   * 拉取客户最近会话消息
   * 先通过 by-customer 端点查找会话, 再取最近 5 条消息内联展示
   */
  useEffect(() => {
    if (!id) return;
    const fetchRecentMessages = async () => {
      setRecentMessagesLoading(true);
      try {
        // 查找该客户的会话 (取第一条)
        const convData = await apiClient.get<Page<{ id: string }>>(
          `/scrm/conversations/by-customer/${id}`, { params: { page: 0, size: 1 } },
        );
        const conversations = convData?.content || [];
        if (conversations.length === 0) {
          setRecentMessages([]);
          setRecentConversationId(null);
          return;
        }
        const convId = conversations[0].id;
        setRecentConversationId(convId);
        // 获取最近 5 条消息 (倒序, 前端再反转为正序展示)
        const msgData = await apiClient.get<Page<RecentMessage>>(
          `/scrm/conversations/${convId}/messages`, { params: { page: 0, size: 5 } },
        );
        const msgs = (msgData?.content || []).reverse();
        setRecentMessages(msgs);
      } catch {
        // 会话或消息获取失败时静默处理 (不影响页面其他功能)
        setRecentMessages([]);
        setRecentConversationId(null);
      } finally {
        setRecentMessagesLoading(false);
      }
    };
    fetchRecentMessages();
  }, [id]);

  /** 返回客户列表 */
  const handleBack = () => {
    navigate('/customers');
  };

  /** 打开编辑弹窗 */
  const openEdit = () => {
    if (!customer) return;
    editForm.setFieldsValue({
      nickname: customer.nickname,
      avatarUrl: customer.avatarUrl,
      ownerAccountId: customer.ownerAccountId,
      lifecycle: customer.lifecycle,
    });
    setEditModalOpen(true);
  };

  /** 提交编辑表单 */
  const handleEditSubmit = async () => {
    if (!customer) return;
    try {
      const values = await editForm.validateFields();
      setEditSubmitting(true);
      await apiClient.put(`/scrm/customers/${customer.id}`, values);
      // 更新本地客户状态
      setCustomer((prev) => (prev ? { ...prev, ...values } : prev));
      message.success('客户信息已更新');
      setEditModalOpen(false);
    } catch {
      // 表单校验失败或请求失败; 请求失败已由 axios 拦截器统一提示
    } finally {
      setEditSubmitting(false);
    }
  };

  /** 打开添加标签弹窗 */
  const openAddTag = () => {
    setNewTagName('');
    setNewTagColor('blue');
    setTagModalOpen(true);
  };

  /** 打开安排跟进弹窗, 默认预填当前已存在的跟进时间 */
  const openFollowUp = () => {
    if (!customer) return;
    setFollowUpTime(customer.nextFollowUpAt ? dayjs(customer.nextFollowUpAt) : null);
    setFollowUpRemark('');
    setFollowUpModalOpen(true);
  };

  /** 提交安排跟进, 调用 PUT /scrm/customers/{id}/follow-up-schedule */
  const handleFollowUpSubmit = async () => {
    if (!customer) return;
    if (!followUpTime) {
      message.warning('请选择跟进时间');
      return;
    }
    setFollowUpSubmitting(true);
    try {
      // 时间格式与后端 @DateTimeFormat(yyyy-MM-dd HH:mm:ss) 对齐
      const formatted = followUpTime.format('YYYY-MM-DD HH:mm:ss');
      const params: Record<string, string> = { nextFollowUpAt: formatted };
      if (followUpRemark.trim()) {
        params.remark = followUpRemark.trim();
      }
      await apiClient.put(`/scrm/customers/${customer.id}/follow-up-schedule`, undefined, { params });
      // 更新本地客户状态, 立即反映提醒徽标
      setCustomer((prev) =>
        prev ? { ...prev, nextFollowUpAt: followUpTime.toISOString() } : prev,
      );
      message.success('跟进安排已保存');
      setFollowUpModalOpen(false);
    } catch {
      // 错误已由 axios 拦截器统一提示
    } finally {
      setFollowUpSubmitting(false);
    }
  };

  /** 添加标签 */
  const handleAddTag = async () => {
    if (!customer || !newTagName.trim()) {
      message.warning('请输入标签名称');
      return;
    }
    const trimmedName = newTagName.trim();
    // 前端唯一性校验: 检查是否已存在相同 tagKey 的标签
    if (tags.some((t) => t.tagKey === trimmedName)) {
      message.warning(`标签 "${trimmedName}" 已存在`);
      return;
    }
    setTagSubmitting(true);
    try {
      // 使用后端标签接口: tagKey + tagValue
      await apiClient.post(
        `/scrm/customers/${customer.id}/tags?tagKey=${encodeURIComponent(trimmedName)}&tagValue=${encodeURIComponent(newTagColor)}`,
      );
      // 重新加载标签列表
      const tagsData = await apiClient.get<CustomerTag[]>(`/scrm/customers/${customer.id}/tags`).catch(() => [] as CustomerTag[]);
      setTags(tagsData || []);
      message.success('标签已添加');
      setTagModalOpen(false);
    } catch {
      // 错误已由拦截器统一提示
    } finally {
      setTagSubmitting(false);
    }
  };

  /** 删除标签 */
  const handleRemoveTag = async (tag: CustomerTag) => {
    if (!customer) return;
    try {
      // 使用后端标签删除接口: DELETE /{id}/tags/{tagKey}
      await apiClient.delete(`/scrm/customers/${customer.id}/tags/${encodeURIComponent(tag.tagKey)}`);
      setTags((prev) => prev.filter((t) => t.tagKey !== tag.tagKey));
      message.success('标签已删除');
    } catch {
      // 错误已由拦截器统一提示
    }
  };

  /** 打开生命周期变更弹窗 (预填目标阶段) */
  const openLifecycleModal = (target: string) => {
    setPendingLifecycle(target);
    setLifecycleRemark('');
    setLifecycleModalOpen(true);
  };

  /** 提交生命周期变更 */
  const handleLifecycleSubmit = async () => {
    if (!customer || !pendingLifecycle) return;
    // 与当前阶段一致则直接关闭
    if (pendingLifecycle === customer.lifecycle) {
      setLifecycleModalOpen(false);
      return;
    }
      setLifecycleSubmitting(true);
      try {
        // 调用生命周期更新接口: PUT /{id}/lifecycle?lifecycle=&remark=
      await apiClient.put(
        `/scrm/customers/${customer.id}/lifecycle?lifecycle=${encodeURIComponent(pendingLifecycle)}&remark=${encodeURIComponent(lifecycleRemark)}`,
      );
      // 更新本地客户状态
      setCustomer((prev) => (prev ? { ...prev, lifecycle: pendingLifecycle } : prev));
      // 从后端重新加载变更历史 (确保持久化记录与操作人信息一致)
      const historyData = await apiClient
        .get<LifecycleHistoryEntry[]>(`/scrm/customers/${customer.id}/lifecycle-history`)
        .catch(() => [] as LifecycleHistoryEntry[]);
      setLifecycleHistory(historyData || []);
      message.success('生命周期已更新');
      setLifecycleModalOpen(false);
    } catch {
      // 错误已由拦截器统一提示
    } finally {
      setLifecycleSubmitting(false);
    }
  };

  /** 保存客户备注, 调用 PUT /scrm/customers/{id}/notes */
  const handleSaveNotes = async () => {
    if (!customer) return;
    setNotesSaving(true);
    try {
      await apiClient.put(`/scrm/customers/${customer.id}/notes`, { notes });
      // 更新上次更新时间为当前时间
      const now = new Date().toISOString();
      setNotesUpdatedAt(now);
      // 同步更新本地客户状态中的备注字段与更新时间
      setCustomer((prev) => (prev ? { ...prev, remark: notes, updateTime: now } : prev));
      message.success('备注已保存');
    } catch {
      // 错误已由 axios 拦截器统一提示
    } finally {
      setNotesSaving(false);
    }
  };

  // 加载中 (骨架屏)
  if (loading) {
    return (
      <div>
        <Button icon={<ArrowLeftOutlined />} onClick={handleBack} style={{ marginBottom: 16 }}>
          返回列表
        </Button>
        <Card style={{ marginBottom: 16 }}>
          <Skeleton avatar paragraph={{ rows: 4 }} active />
        </Card>
        <Card title="客户标签" style={{ marginBottom: 16 }}>
          <Skeleton paragraph={{ rows: 2 }} active />
        </Card>
        <Card title="所属分组">
          <Skeleton paragraph={{ rows: 2 }} active />
        </Card>
      </div>
    );
  }

  // 加载失败
  if (error || !customer) {
    return (
      <div>
        <Button icon={<ArrowLeftOutlined />} onClick={handleBack} style={{ marginBottom: 16 }}>
          返回列表
        </Button>
        <Result
          status="error"
          title="客户加载失败"
          subTitle={error?.message || '请稍后重试'}
          extra={
            <Button type="primary" onClick={handleBack}>
              返回列表
            </Button>
          }
        />
      </div>
    );
  }

  // 平台 / 生命周期展示配置 (兼容未知值)
  const platformCfg = platformConfig[customer.platformType] || { label: customer.platformType, color: 'default' };
  const lifecycleCfg = LIFECYCLE_LABELS[customer.lifecycle] || { label: customer.lifecycle, color: 'default' };

  return (
    <div>
      {/* 返回按钮 */}
      <Button icon={<ArrowLeftOutlined />} onClick={handleBack} style={{ marginBottom: 16 }}>
        返回列表
      </Button>

      {/* 跟进提醒横幅: 逾期红色告警 / 即将到期蓝色提醒 */}
      {customer.nextFollowUpAt && (() => {
        const followUpTime = dayjs(customer.nextFollowUpAt);
        const now = dayjs();
        // 逾期: 跟进时间早于当前时间
        if (followUpTime.isBefore(now)) {
          return (
            <Alert
              type="error"
              showIcon
              banner
              style={{ marginBottom: 16 }}
              message={`该客户有逾期的跟进任务: ${followUpTime.format('YYYY-MM-DD HH:mm')}`}
              description="请尽快跟进该客户或重新安排跟进时间"
              action={<Button size="small" onClick={openFollowUp}>重新安排</Button>}
            />
          );
        }
        // 即将到期: 30 分钟内
        if (followUpTime.isBefore(now.add(30, 'minute'))) {
          return (
            <Alert
              type="info"
              showIcon
              banner
              style={{ marginBottom: 16 }}
              message={`即将到跟进时间: ${followUpTime.format('YYYY-MM-DD HH:mm')}`}
              action={<Button size="small" onClick={openFollowUp}>查看</Button>}
            />
          );
        }
        // 未来跟进: 展示徽标提醒
        return (
          <Alert
            type="success"
            showIcon
            banner
            style={{ marginBottom: 16 }}
            message={`已安排跟进: ${followUpTime.format('YYYY-MM-DD HH:mm')}`}
            action={<Button size="small" onClick={openFollowUp}>修改</Button>}
          />
        );
      })()}

      {/* 客户基本信息卡片 (extra 显示操作按钮) */}
      <Card
        style={{ marginBottom: 16 }}
        extra={
          <Space>
            <Button
              type="primary"
              icon={<MessageOutlined />}
              onClick={() => navigate(`/conversations?customerId=${customer.id}`)}
            >
              发消息
            </Button>
            <Button icon={<EditOutlined />} onClick={openEdit}>
              编辑
            </Button>
            <Button icon={<TagsOutlined />} onClick={openAddTag}>
              添加标签
            </Button>
            <Button icon={<ClockCircleOutlined />} onClick={openFollowUp}>
              安排跟进
            </Button>
          </Space>
        }
      >
        <Space size="middle" align="center" style={{ marginBottom: 16 }}>
          <Avatar src={customer.avatarUrl} size={64}>
            {customer.nickname?.[0]?.toUpperCase() || '?'}
          </Avatar>
          <div>
            <div style={{ fontSize: 18, fontWeight: 500, marginBottom: 8 }}>
              {customer.nickname}
            </div>
            <Space>
              <Tag color={platformCfg.color}>{platformCfg.label}</Tag>
              <Tag color={lifecycleCfg.color}>{lifecycleCfg.label}</Tag>
            </Space>
          </div>
        </Space>
        <Descriptions column={{ xs: 1, sm: 2 }} bordered size="small">
          <Descriptions.Item label="平台 UID">{customer.platformCustomerUid}</Descriptions.Item>
          <Descriptions.Item label="平台类型">{platformCfg.label}</Descriptions.Item>
          <Descriptions.Item label="所属账号">
            {customer.ownerAccountId ? String(customer.ownerAccountId) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="人设 ID">
            {customer.personaId ? String(customer.personaId) : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="生命周期">{lifecycleCfg.label}</Descriptions.Item>
          <Descriptions.Item label="客户 ID">{customer.id}</Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {customer.createTime ? dayjs(customer.createTime).format('YYYY-MM-DD HH:mm') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="最后交互时间">
            {customer.lastInteractionAt ? dayjs(customer.lastInteractionAt).format('YYYY-MM-DD HH:mm') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="下次跟进">
            {customer.nextFollowUpAt ? dayjs(customer.nextFollowUpAt).format('YYYY-MM-DD HH:mm') : '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 客户备注卡片: 编辑备注 / 保存 / 上次更新时间 */}
      <Card title="客户备注" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Input.TextArea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="请输入客户备注..."
            autoSize={{ minRows: 3 }}
            maxLength={2000}
            showCount
          />
          <Space>
            <Button type="primary" icon={<SaveOutlined />} loading={notesSaving} onClick={handleSaveNotes}>
              保存备注
            </Button>
            <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
              上次更新: {notesUpdatedAt ? dayjs(notesUpdatedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
            </span>
          </Space>
        </Space>
      </Card>

      {/* 生命周期管理: 进度条 / 当前阶段 / 变更入口 / 变更历史 */}
      <Card title="生命周期管理" style={{ marginBottom: 16 }}>
        {/* 进度条: 5 个阶段按顺序展示, 当前阶段高亮 (未知阶段回退到 0) */}
        <Steps
          size="small"
          current={Math.max(
            0,
            LIFECYCLE_STAGES.indexOf(customer.lifecycle as typeof LIFECYCLE_STAGES[number]),
          )}
          items={LIFECYCLE_STAGES.map((stage) => ({
            title: LIFECYCLE_LABELS[stage].label,
          }))}
          style={{ marginBottom: 24 }}
        />

        {/* 当前阶段展示 + 变更入口 */}
        <Space size="middle" align="center" style={{ marginBottom: 16 }}>
          <span>当前阶段:</span>
          <Tag color={lifecycleCfg.color} style={{ marginInlineEnd: 0 }}>
            {lifecycleCfg.label}
          </Tag>
          <Button icon={<SwapOutlined />} onClick={() => openLifecycleModal(customer.lifecycle)}>
            变更生命周期
          </Button>
        </Space>

        {/* 变更历史时间线 (从后端加载持久化记录) */}
        <div style={{ fontWeight: 500, marginBottom: 8 }}>变更历史</div>
        {lifecycleHistory.length > 0 ? (
          <Timeline
            items={lifecycleHistory.map((entry) => ({
              key: entry.id,
              color: LIFECYCLE_LABELS[entry.newLifecycle]?.color || 'gray',
              children: (
                <div>
                  <Space size={4} wrap>
                    <Tag>{LIFECYCLE_LABELS[entry.previousLifecycle]?.label || entry.previousLifecycle}</Tag>
                    <span>→</span>
                    <Tag color={LIFECYCLE_LABELS[entry.newLifecycle]?.color || 'default'}>
                      {LIFECYCLE_LABELS[entry.newLifecycle]?.label || entry.newLifecycle}
                    </Tag>
                    <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                      {entry.changedAt ? dayjs(entry.changedAt).format('YYYY-MM-DD HH:mm') : ''}
                    </span>
                    {entry.operatorName && (
                      <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>by {entry.operatorName}</span>
                    )}
                  </Space>
                  {entry.remark ? (
                    <div style={{ color: 'var(--color-text-secondary)', marginTop: 4 }}>备注: {entry.remark}</div>
                  ) : null}
                </div>
              ),
            }))}
          />
        ) : (
          <span style={{ color: 'var(--color-text-tertiary)' }}>暂无变更记录</span>
        )}
      </Card>

      {/* 客户标签 (extra 显示添加按钮, 每个标签可删除) */}
      <Card
        title="客户标签"
        style={{ marginBottom: 16 }}
        extra={
          <Button size="small" icon={<PlusOutlined />} onClick={openAddTag}>
            添加标签
          </Button>
        }
      >
        {tags.length > 0 ? (
          <Space size={[8, 8]} wrap>
            {tags.map((tag) => (
              <Tag
                key={tag.tagKey}
                color={tag.tagValue || 'blue'}
                closable
                onClose={() => handleRemoveTag(tag)}
              >
                {tag.tagKey}{tag.tagValue ? `=${tag.tagValue}` : ''}
              </Tag>
            ))}
          </Space>
        ) : (
          <span style={{ color: 'var(--color-text-tertiary)' }}>暂无标签</span>
        )}
      </Card>

      {/* 客户所属分组 */}
      <Card title="所属分组">
        {groups.length > 0 ? (
          <Space size={[8, 8]} wrap>
            {groups.map((group) => (
              <Tag key={group.id} color="geekblue">
                {group.groupName}
              </Tag>
            ))}
          </Space>
        ) : (
          <span style={{ color: 'var(--color-text-tertiary)' }}>暂未加入任何分组</span>
        )}
      </Card>

      {/* 最近会话消息 (内联展示最近 5 条, 点击查看全部跳转会话详情) */}
      <Card
        title="最近会话"
        style={{ marginTop: 16 }}
        extra={
          recentConversationId && (
            <Button
              type="link"
              size="small"
              icon={<MessageOutlined />}
              onClick={() => navigate(`/conversations?customerId=${recentConversationId}`)}
            >
              查看全部
            </Button>
          )
        }
      >
        {recentMessagesLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}>
            <Spin size="small" />
          </div>
        ) : recentMessages.length > 0 ? (
          <Timeline
            items={recentMessages.map((msg) => ({
              key: msg.id,
              color: msg.direction === 'IN' ? 'blue' : 'green',
              children: (
                <div>
                  <Space size={4} style={{ marginBottom: 4 }}>
                    <Tag style={{ fontSize: 11, margin: 0 }}>
                      {msg.direction === 'IN' ? '客户' : '客服'}
                    </Tag>
                    {msg.messageType !== 'TEXT' && (
                      <Tag style={{ fontSize: 11, margin: 0 }} color="orange">
                        {msg.messageType}
                      </Tag>
                    )}
                    <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>
                      {msg.sentAt ? dayjs(msg.sentAt).format('MM-DD HH:mm') : ''}
                    </span>
                  </Space>
                  <Typography.Text
                    style={{ display: 'block', fontSize: 13 }}
                    ellipsis
                  >
                    {msg.content || `[${msg.messageType}消息]`}
                  </Typography.Text>
                </div>
              ),
            }))}
          />
        ) : (
          <div style={{ textAlign: 'center', padding: '24px 0' }}>
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无会话记录" />
            <Button
              type="primary"
              size="small"
              icon={<MessageOutlined />}
              style={{ marginTop: 12 }}
              onClick={() => navigate(`/conversations?customerId=${customer.id}`)}
            >
              发起会话
            </Button>
          </div>
        )}
      </Card>

      {/* 编辑客户弹窗 */}
      <Modal
        title="编辑客户"
        open={editModalOpen}
        onOk={handleEditSubmit}
        onCancel={() => setEditModalOpen(false)}
        confirmLoading={editSubmitting}
        destroyOnHidden
      >
        <Form form={editForm} layout="vertical" preserve={false}>
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
          <Form.Item name="ownerAccountId" label="所属账号">
            <Select
              showSearch
              placeholder="选择归属账号"
              allowClear
              filterOption={(input, option) =>
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={accounts.map((a) => ({
                value: String(a.id),
                label: `${a.accountName} (${a.platformType})`,
              }))}
            />
          </Form.Item>
          <Form.Item name="lifecycle" label="生命周期">
            <Select placeholder="请选择生命周期" options={lifecycleOptions} allowClear />
          </Form.Item>
        </Form>
      </Modal>

      {/* 添加标签弹窗 */}
      <Modal
        title="添加标签"
        open={tagModalOpen}
        onOk={handleAddTag}
        onCancel={() => setTagModalOpen(false)}
        confirmLoading={tagSubmitting}
        destroyOnHidden
      >
        <Form layout="vertical">
          <Form.Item label="标签名称" required>
            <Input
              placeholder="请输入标签名称"
              value={newTagName}
              onChange={(e) => setNewTagName(e.target.value)}
              onPressEnter={(e) => { if (!isImeComposing(e)) handleAddTag(); }}
            />
          </Form.Item>
          <Form.Item label="标签颜色">
            <Select
              value={newTagColor}
              onChange={setNewTagColor}
              options={tagColorOptions}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 安排跟进弹窗 (选择下次跟进时间 + 备注) */}
      <Modal
        title="安排跟进"
        open={followUpModalOpen}
        onOk={handleFollowUpSubmit}
        onCancel={() => setFollowUpModalOpen(false)}
        confirmLoading={followUpSubmitting}
        destroyOnHidden
      >
        <Form layout="vertical">
          <Form.Item label="跟进时间" required>
            <DatePicker
              showTime
              format="YYYY-MM-DD HH:mm:ss"
              placeholder="请选择跟进时间"
              value={followUpTime}
              onChange={setFollowUpTime}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item label="跟进备注 (可选)">
            <Input.TextArea
              placeholder="可填写跟进事项备注"
              value={followUpRemark}
              onChange={(e) => setFollowUpRemark(e.target.value)}
              rows={3}
              maxLength={200}
              showCount
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 生命周期变更确认弹窗 (选择目标阶段 + 备注) */}
      <Modal
        title="变更生命周期"
        open={lifecycleModalOpen}
        onOk={handleLifecycleSubmit}
        onCancel={() => setLifecycleModalOpen(false)}
        confirmLoading={lifecycleSubmitting}
        destroyOnHidden
      >
        <Form layout="vertical">
          <Form.Item label="变更为">
            <Select
              value={pendingLifecycle}
              onChange={setPendingLifecycle}
              options={lifecycleOptions}
              placeholder="请选择生命周期阶段"
            />
          </Form.Item>
          <Form.Item label="变更备注">
            <Input.TextArea
              value={lifecycleRemark}
              onChange={(e) => setLifecycleRemark(e.target.value)}
              placeholder="请输入变更备注 (可选)"
              rows={3}
              maxLength={200}
              showCount
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
