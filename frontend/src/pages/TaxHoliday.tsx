/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : TaxHoliday.tsx
 * Date : 2026/10/27 11:50:00
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
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import {
  CalendarOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  PlusOutlined,
  ReloadOutlined,
  RobotOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { apiClient } from '../api/client';

/** 税务假期 */
interface TaxHoliday {
  id: string;
  holidayName: string;
  holidayYear: number;
  startDate: string;
  endDate: string;
  holidayKind?: string;
  remark?: string;
}

/** 调休上班日 */
interface TaxWorkdayOverride {
  id: string;
  workDate: string;
  remark?: string;
}

/** AI 抽取草稿 */
interface HolidayDraft {
  id: string;
  holidayYear: number;
  sourceTitle?: string;
  sourceUrl?: string;
  holidaysJson?: string;
  workdaysJson?: string;
  draftStatus: string;
  modelName?: string;
  createTime?: string;
}

/** 数据完整性检查结果 */
interface Coverage {
  year: number;
  holidayCount: number;
  workdayOverrideCount: number;
  complete: boolean;
  warnings: string[];
}

/** 假期类型映射 */
const kindConfig: Record<string, { color: string; label: string }> = {
  STATUTORY: { color: 'red', label: '法定节假日' },
  ADJUSTED: { color: 'orange', label: '调休放假' },
};

/**
 * 安全解析草稿里的 JSON 数组。
 *
 * @param json JSON 字符串
 * @return 元素列表，非法时返回空数组
 */
function safeParse(json?: string): Array<Record<string, unknown>> {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/** 年份下拉选项：当前年份前后各两年 */
const yearOptions = (() => {
  const current = dayjs().year();
  const options = [];
  for (let y = current - 1; y <= current + 2; y += 1) {
    options.push({ value: y, label: `${y} 年` });
  }
  return options;
})();

/**
 * 税务假期维护页。
 * <p>
 * 申报截止日遇法定节假日要顺延，而假期数据每年由国务院公告、必须人工维护。
 * 本页提供两件事：
 * </p>
 * <ul>
 *   <li><b>数据维护</b>：维护法定假期区间与调休补班日；</li>
 *   <li><b>完整性自检</b>：假期缺失时顺延会<b>静默失效</b>（只跳周末、不报错），
 *       因此提供按年检查，让运维在年底能确认下一年数据是否已录入。</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
export default function TaxHoliday() {
  const { message } = App.useApp();

  const [year, setYear] = useState<number>(dayjs().year());
  const [holidays, setHolidays] = useState<TaxHoliday[]>([]);
  const [workdays, setWorkdays] = useState<TaxWorkdayOverride[]>([]);
  const [coverage, setCoverage] = useState<Coverage | null>(null);
  const [loading, setLoading] = useState(false);

  const [holidayOpen, setHolidayOpen] = useState(false);
  const [editingHoliday, setEditingHoliday] = useState<TaxHoliday | null>(null);
  const [holidaySaving, setHolidaySaving] = useState(false);
  const [holidayForm] = Form.useForm();

  const [drafts, setDrafts] = useState<HolidayDraft[]>([]);
  const [aiRunning, setAiRunning] = useState(false);

  const [workdayOpen, setWorkdayOpen] = useState(false);
  const [workdaySaving, setWorkdaySaving] = useState(false);
  const [workdayForm] = Form.useForm();

  /** 加载全部数据（假期 / 补班日 / 自检结果） */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [holidayList, workdayList, coverageResult, draftList] = await Promise.all([
        apiClient.get<TaxHoliday[]>('/scrm/tax-holidays', { params: { year } }),
        apiClient.get<TaxWorkdayOverride[]>('/scrm/tax-holidays/workdays', { params: { year } }),
        apiClient.get<Coverage>('/scrm/tax-holidays/coverage', { params: { year } }),
        apiClient.get<HolidayDraft[]>('/scrm/tax-holidays/drafts', { params: { year } }),
      ]);
      setHolidays(holidayList || []);
      setWorkdays(workdayList || []);
      setCoverage(coverageResult);
      setDrafts(draftList || []);
    } catch {
      // client.ts 已统一提示
    } finally {
      setLoading(false);
    }
  }, [year]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 打开新建/编辑假期弹窗 */
  const openHoliday = (record?: TaxHoliday) => {
    setEditingHoliday(record || null);
    holidayForm.resetFields();
    if (record) {
      holidayForm.setFieldsValue({
        holidayName: record.holidayName,
        // DatePicker 需要 dayjs 对象，直接塞字符串会导致回填为空
        range: [dayjs(record.startDate), dayjs(record.endDate)],
        holidayKind: record.holidayKind || 'STATUTORY',
        remark: record.remark,
      });
    } else {
      holidayForm.setFieldsValue({ holidayKind: 'STATUTORY' });
    }
    setHolidayOpen(true);
  };

  /** 保存假期 */
  const submitHoliday = async () => {
    try {
      const values = await holidayForm.validateFields();
      setHolidaySaving(true);
      const payload = {
        holidayName: values.holidayName,
        startDate: values.range[0].format('YYYY-MM-DD'),
        endDate: values.range[1].format('YYYY-MM-DD'),
        holidayKind: values.holidayKind,
        remark: values.remark,
      };
      if (editingHoliday) {
        await apiClient.put(`/scrm/tax-holidays/${editingHoliday.id}`, payload);
        message.success('假期已更新');
      } else {
        await apiClient.post('/scrm/tax-holidays', payload);
        message.success('假期已创建');
      }
      setHolidayOpen(false);
      await load();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'message' in err) {
        message.error(String((err as { message: unknown }).message) || '保存失败');
      }
    } finally {
      setHolidaySaving(false);
    }
  };

  /** 删除假期 */
  const removeHoliday = async (record: TaxHoliday) => {
    try {
      await apiClient.delete(`/scrm/tax-holidays/${record.id}`);
      message.success('已删除');
      await load();
    } catch {
      // 已统一提示
    }
  };

  /** 保存调休上班日 */
  const submitWorkday = async () => {
    try {
      const values = await workdayForm.validateFields();
      setWorkdaySaving(true);
      await apiClient.post('/scrm/tax-holidays/workdays', {
        workDate: values.workDate.format('YYYY-MM-DD'),
        remark: values.remark,
      });
      message.success('调休上班日已添加');
      setWorkdayOpen(false);
      await load();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'message' in err) {
        message.error(String((err as { message: unknown }).message) || '保存失败');
      }
    } finally {
      setWorkdaySaving(false);
    }
  };

  /** 删除调休上班日 */
  const removeWorkday = async (record: TaxWorkdayOverride) => {
    try {
      await apiClient.delete(`/scrm/tax-holidays/workdays/${record.id}`);
      message.success('已删除');
      await load();
    } catch {
      // 已统一提示
    }
  };

  /**
   * 触发 AI 抽取。
   * <p>
   * 抓取公告 + 模型结构化都在后端完成；产出只落草稿，
   * 必须人工核对后再点「采纳」，AI 结果不会直接生效。
   * </p>
   */
  const runAiExtract = async () => {
    setAiRunning(true);
    // 后端遇到模型临时故障会自动重试（最多 3 次，间隔递增），耗时可能十几秒；
    // 先给出提示，避免用户以为卡死而重复点击
    const hint = message.loading('正在抽取，若模型临时故障会自动重试（最多 3 次）…', 0);
    try {
      const result = await apiClient.post<{ year: number; generated: boolean; message: string }>(
        '/scrm/tax-holidays/ai/extract',
        undefined,
        { params: { year } },
      );
      if (result?.generated) {
        message.success(result.message || '草稿已生成');
      } else {
        message.warning(result?.message || '未生成草稿');
      }
      await load();
    } catch {
      // 已统一提示（后端重试耗尽后的错误信息会指明「已重试 3 次」）
    } finally {
      hint();
      setAiRunning(false);
    }
  };

  /** 采纳草稿：写入生效表（会覆盖该年份现有假期） */
  const applyDraft = async (draft: HolidayDraft) => {
    try {
      await apiClient.post(`/scrm/tax-holidays/drafts/${draft.id}/apply`, undefined);
      message.success('已采纳并写入生效数据');
      await load();
    } catch {
      // 已统一提示
    }
  };

  /** 丢弃草稿 */
  const rejectDraft = async (draft: HolidayDraft) => {
    try {
      await apiClient.post(`/scrm/tax-holidays/drafts/${draft.id}/reject`, undefined);
      message.success('草稿已丢弃');
      await load();
    } catch {
      // 已统一提示
    }
  };

  /** 假期列表列定义 */
  const holidayColumns: ColumnsType<TaxHoliday> = [
    {
      title: '假期名称',
      dataIndex: 'holidayName',
      width: 160,
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    {
      title: '起止日期',
      key: 'range',
      width: 220,
      render: (_, r) => `${r.startDate} ~ ${r.endDate}`,
    },
    {
      title: '天数',
      key: 'days',
      width: 90,
      render: (_, r) => dayjs(r.endDate).diff(dayjs(r.startDate), 'day') + 1,
    },
    {
      title: '类型',
      dataIndex: 'holidayKind',
      width: 120,
      render: (v?: string) => {
        const cfg = kindConfig[v || 'STATUTORY'] || { color: 'default', label: v };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    { title: '说明', dataIndex: 'remark', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size="small">
          <Button size="small" onClick={() => openHoliday(record)}>编辑</Button>
          <Popconfirm
            title="删除后该期间不再顺延"
            description="申报截止日会退化为只跳过周末，确认删除？"
            onConfirm={() => void removeHoliday(record)}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  /** 调休上班日列定义 */
  const workdayColumns: ColumnsType<TaxWorkdayOverride> = [
    {
      title: '上班日期',
      dataIndex: 'workDate',
      width: 150,
      render: (v: string) => {
        const d = dayjs(v);
        return `${v}（${['日', '一', '二', '三', '四', '五', '六'][d.day()]}）`;
      },
    },
    { title: '说明', dataIndex: 'remark', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, record) => (
        <Popconfirm
          title="确认删除该调休上班日？"
          description="删除后该日期会被当作休息日，截止日可能被往后推。"
          onConfirm={() => void removeWorkday(record)}
        >
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      {/* 数据完整性提示：假期缺失会让顺延静默失效，必须显性化 */}
      {coverage && !coverage.complete && (
        <Alert
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          style={{ marginBottom: 16 }}
          message={`${coverage.year} 年假期数据不完整，申报截止日可能未按法定节假日顺延`}
          description={
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {coverage.warnings.map((w, i) => <li key={i}>{w}</li>)}
            </ul>
          }
        />
      )}

      {/* 顶部统计 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="假期数"
              value={coverage?.holidayCount ?? 0}
              suffix="个"
              prefix={<CalendarOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="调休上班日"
              value={coverage?.workdayOverrideCount ?? 0}
              suffix="天"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="数据状态"
              value={coverage?.complete ? '齐备' : '待补'}
              valueStyle={{ color: coverage?.complete ? '#52c41a' : '#fa8c16' }}
              prefix={coverage?.complete ? <CheckCircleOutlined /> : <WarningOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Space direction="vertical" size={4} style={{ width: '100%' }}>
              <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>年份</span>
              <Select
                style={{ width: '100%' }}
                value={year}
                onChange={setYear}
                options={yearOptions}
              />
            </Space>
          </Card>
        </Col>
      </Row>

      {/* 法定假期 */}
      <Card
        title="法定节假日"
        extra={
          <Space>
            <Tooltip title="刷新">
              <Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading} />
            </Tooltip>
            <Tooltip title="抓取国务院办公厅公告，交 AI 结构化为草稿（需人工确认后才生效）">
              <Button
                icon={<RobotOutlined />}
                loading={aiRunning}
                onClick={() => void runAiExtract()}
              >
                AI 抽取
              </Button>
            </Tooltip>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openHoliday()}>
              新增假期
            </Button>
          </Space>
        }
        style={{ marginBottom: 16 }}
      >
        <Table<TaxHoliday>
          rowKey="id"
          size="small"
          columns={holidayColumns}
          dataSource={holidays}
          loading={loading}
          pagination={false}
          locale={{ emptyText: '该年暂无假期数据，申报截止日将只按周末顺延' }}
        />
      </Card>

      {/* AI 抽取草稿：人工确认后才生效 */}
      {drafts.length > 0 && (
        <Card
          title={
            <Space>
              <span>AI 抽取草稿</span>
              <Tag color="orange">{drafts.length} 条待确认</Tag>
            </Space>
          }
          style={{ marginBottom: 16, borderColor: '#fa8c16' }}
        >
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="AI 抽取结果不会直接生效"
            description="假期数据直接决定申报截止日，请先核对公告原文再采纳。采纳会覆盖该年份现有的假期数据。"
          />
          {drafts.map(draft => {
            const parsedHolidays = safeParse(draft.holidaysJson);
            const parsedWorkdays = safeParse(draft.workdaysJson);
            return (
              <Card
                key={draft.id}
                size="small"
                style={{ marginBottom: 8 }}
                title={
                  <Space size={8}>
                    <span>{draft.holidayYear} 年</span>
                    {draft.modelName && <Tag>{draft.modelName}</Tag>}
                    <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                      {draft.sourceTitle || '手工录入'}
                    </span>
                  </Space>
                }
                extra={
                  <Space>
                    <Popconfirm
                      title="采纳会覆盖该年份现有假期"
                      description="确认已将草稿与公告原文核对一致？"
                      onConfirm={() => void applyDraft(draft)}
                    >
                      <Button type="primary" size="small">核对无误，采纳</Button>
                    </Popconfirm>
                    <Popconfirm title="确认丢弃该草稿？" onConfirm={() => void rejectDraft(draft)}>
                      <Button size="small" danger>丢弃</Button>
                    </Popconfirm>
                  </Space>
                }
              >
                <div style={{ marginBottom: 8 }}>
                  <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>假期：</span>
                  {parsedHolidays.length === 0 ? (
                    <Tag color="default">无</Tag>
                  ) : (
                    parsedHolidays.map((h, i) => (
                      <Tag key={i} color="blue" style={{ marginBottom: 4 }}>
                        {String(h.name)} {String(h.startDate)} ~ {String(h.endDate)}
                      </Tag>
                    ))
                  )}
                </div>
                <div>
                  <span style={{ color: 'var(--color-text-tertiary)', fontSize: 12 }}>调休上班：</span>
                  {parsedWorkdays.length === 0 ? (
                    <Tag color="default">无</Tag>
                  ) : (
                    parsedWorkdays.map((w, i) => (
                      <Tag key={i} color="orange" style={{ marginBottom: 4 }}>
                        {String(w.workDate)}
                      </Tag>
                    ))
                  )}
                </div>
              </Card>
            );
          })}
        </Card>
      )}

      {/* 调休上班日 */}
      <Card
        title="调休上班日"
        extra={
          <Button icon={<PlusOutlined />} onClick={() => { workdayForm.resetFields(); setWorkdayOpen(true); }}>
            新增补班日
          </Button>
        }
        style={{ marginBottom: 8 }}
      >
        <div style={{ marginBottom: 12, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
          法定节假日常伴随调休：周末补班的日期需登记在这里，否则会被当作休息日，
          导致截止日被错误往后顺延。
        </div>
        <Table<TaxWorkdayOverride>
          rowKey="id"
          size="small"
          columns={workdayColumns}
          dataSource={workdays}
          loading={loading}
          pagination={false}
          locale={{ emptyText: '该年暂无调休上班日' }}
        />
      </Card>

      {/* 假期弹窗 */}
      <Modal
        title={editingHoliday ? '编辑假期' : '新增假期'}
        open={holidayOpen}
        onOk={() => void submitHoliday()}
        confirmLoading={holidaySaving}
        onCancel={() => setHolidayOpen(false)}
        width={520}
        destroyOnClose
      >
        <Form form={holidayForm} layout="vertical">
          <Form.Item
            name="holidayName"
            label="假期名称"
            rules={[{ required: true, message: '请输入假期名称' }]}
          >
            <Input placeholder="如：国庆节" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="range"
            label="起止日期"
            rules={[{ required: true, message: '请选择起止日期' }]}
            extra="包含首尾两天。跨天假期请给完整区间（如国庆 10-01 至 10-08）"
          >
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="holidayKind" label="类型" initialValue="STATUTORY">
            <Select
              options={[
                { value: 'STATUTORY', label: '法定节假日（STATUTORY）' },
                { value: 'ADJUSTED', label: '调休放假（ADJUSTED）' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="说明">
            <Input placeholder="如：国务院办公厅 2026 年节假日安排" maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 调休上班日弹窗 */}
      <Modal
        title="新增调休上班日"
        open={workdayOpen}
        onOk={() => void submitWorkday()}
        confirmLoading={workdaySaving}
        onCancel={() => setWorkdayOpen(false)}
        destroyOnClose
      >
        <Form form={workdayForm} layout="vertical">
          <Form.Item
            name="workDate"
            label="上班日期"
            rules={[{ required: true, message: '请选择上班日期' }]}
            extra="填需要正常上班的日期，通常是调休后的周末"
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="说明">
            <Input placeholder="如：国庆调休上班" maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
