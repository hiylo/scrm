/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : JourneyCanvas.tsx
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
  Spin,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  SaveOutlined,
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  BranchesOutlined,
} from '@ant-design/icons';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Handle,
  Position,
  BackgroundVariant,
  useNodesState,
  useEdgesState,
  addEdge,
  type Connection,
  type Edge,
  type Node,
  type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { apiClient } from '../api/client';

const { Text } = Typography;

/** 步骤类型枚举 (与后端 @Pattern 一致) */
enum StepType {
  SEND_MESSAGE = 'SEND_MESSAGE',
  WAIT = 'WAIT',
  CONDITION = 'CONDITION',
  ADD_TAG = 'ADD_TAG',
  SET_LIFECYCLE = 'SET_LIFECYCLE',
  WEBHOOK = 'WEBHOOK',
  END = 'END',
}

/** 旅程实体 (画布头部选择) */
interface ScrmCustomerJourney {
  id: string;
  journeyName: string;
  journeyCode: string;
  status: string; // DRAFT / ACTIVE / PAUSED / ARCHIVED
  entryCondition?: string;
  goal?: string;
  description?: string;
}

/** 旅程步骤 (与 ScrmJourneyStepDto 对齐) */
interface ScrmJourneyStep {
  id: string;
  journeyId: string;
  stepName: string;
  stepType: StepType;
  stepOrder: number;
  config: string;
  nextStepId?: string;
  isEntryPoint?: boolean;
  description?: string;
}

/** Spring Page 分页响应 */
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 步骤类型可视化配置 */
const stepTypeConfig: Record<StepType, { color: string; bg: string; label: string }> = {
  [StepType.SEND_MESSAGE]: { color: '#0ea5e9', bg: '#e0f2fe', label: '发送消息' },
  [StepType.WAIT]: { color: '#8b5cf6', bg: '#ede9fe', label: '等待' },
  [StepType.CONDITION]: { color: '#f59e0b', bg: '#fef3c7', label: '条件分支' },
  [StepType.ADD_TAG]: { color: '#10b981', bg: '#d1fae5', label: '打标签' },
  [StepType.SET_LIFECYCLE]: { color: '#3b82f6', bg: '#dbeafe', label: '改生命周期' },
  [StepType.WEBHOOK]: { color: '#ec4899', bg: '#fce7f3', label: 'Webhook' },
  [StepType.END]: { color: '#6b7280', bg: '#e5e7eb', label: '结束' },
};

/** 旅程状态 */
const journeyStatusConfig: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  ACTIVE: { color: 'green', label: '运行中' },
  PAUSED: { color: 'orange', label: '已暂停' },
  ARCHIVED: { color: 'default', label: '已归档' },
};

/** 画布节点数据类型 */
type StepNode = Node<{ step: ScrmJourneyStep }, 'step'>;

/** 节点默认尺寸 */
const NODE_WIDTH = 200;

/** 画布节点组件: 展示步骤名 + 类型徽标, 带 Source/Target Handle */
function StepNodeComponent({ data }: NodeProps<StepNode>) {
  const { step } = data;
  const cfg = stepTypeConfig[step.stepType] || stepTypeConfig[StepType.END];
  return (
    <div
      style={{
        width: NODE_WIDTH,
        border: `2px solid ${cfg.color}`,
        borderRadius: 12,
        background: '#fff',
        padding: '6px 10px',
        boxShadow: '0 1px 3px rgba(0,0,0,0.12)',
      }}
    >
      <Handle type="target" position={Position.Left} style={{ background: cfg.color }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 4 }}>
        <span style={{ fontWeight: 600, fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {step.stepName}
        </span>
        <span
          style={{
            background: cfg.bg,
            color: cfg.color,
            borderRadius: 8,
            padding: '0 6px',
            fontSize: 11,
            whiteSpace: 'nowrap',
          }}
        >
          {cfg.label}
        </span>
      </div>
      <Handle type="source" position={Position.Right} style={{ background: cfg.color }} />
    </div>
  );
}

/**
 * 客户旅程画布编辑器
 * <p>
 * 基于 @xyflow/react 的可视化旅程编排: 加载后端步骤 (GET /{journeyId}/steps) →
 * 画布节点/连线 (edge = nextStepId) → 拖拽定位 + 手动连线 / 属性面板编辑 →
 * 保存 (PUT /steps/{id} 更新 nextStepId/名称/配置 + POST /steps/reorder 重排)。
 * CONDITION 分支通过节点 config.trueNextStep/falseNextStep 表达, 画布上以
 * 两段标注的连边 (true/false) 呈现。
 * </p>
 *
 * @author Hsi Chu
 */
export default function JourneyCanvas() {
  const { message } = App.useApp();
  /** 旅程列表 (顶部选择) */
  const [journeys, setJourneys] = useState<ScrmCustomerJourney[]>([]);
  const [journeysLoading, setJourneysLoading] = useState(false);
  const [selectedJourneyId, setSelectedJourneyId] = useState<string>('');
  const [selectedJourney, setSelectedJourney] = useState<ScrmCustomerJourney | null>(null);
  /** 步骤 */
  const [steps, setSteps] = useState<ScrmJourneyStep[]>([]);
  const [stepsLoading, setStepsLoading] = useState(false);
  /** ReactFlow 状态 */
  const [nodes, setNodes, onNodesChange] = useNodesState<StepNode>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  /** 选中节点属性面板 */
  const [selectedStep, setSelectedStep] = useState<ScrmJourneyStep | null>(null);
  const [propForm] = Form.useForm();
  const [propSaving, setPropSaving] = useState(false);
  /** 新增步骤弹窗 */
  const [addOpen, setAddOpen] = useState(false);
  const [addSaving, setAddSaving] = useState(false);
  const [addForm] = Form.useForm();
  /** 保存中 */
  const [saving, setSaving] = useState(false);

  /** 加载旅程列表 */
  const loadJourneys = useCallback(async () => {
    setJourneysLoading(true);
    try {
      const params = new URLSearchParams({ page: '0', size: '50' });
      const data = await apiClient.get<Page<ScrmCustomerJourney>>(`/scrm/customer-journeys/list?${params.toString()}`);
      setJourneys(data.content || []);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setJourneysLoading(false);
    }
  }, []);

  useEffect(() => {
    loadJourneys();
  }, [loadJourneys]);

  /** 从 CONFIG JSON 取 CONDITION 的 trueNextStep */
  const stepNextOf = useCallback((configJson?: string): string | undefined => {
    if (!configJson) return undefined;
    try {
      const c = JSON.parse(configJson);
      return c?.trueNextStep ? String(c.trueNextStep) : undefined;
    } catch {
      return undefined;
    }
  }, []);

  /**
   * 拓扑分层布局: BFS 按入度分层, 每层从上向下均分; 孤立节点放最左侧独立列。
   */
  const buildLayout = useCallback((stepsList: ScrmJourneyStep[]): Map<string, { row: number; col: number }> => {
    const byId = new Map(stepsList.map(s => [s.id, s]));
    const childrenOf = new Map<string, string[]>();
    const indegree = new Map<string, number>();
    stepsList.forEach(s => { indegree.set(s.id, 0); childrenOf.set(s.id, []); });
    stepsList.forEach(s => {
      const next = s.nextStepId || (s.stepType === StepType.CONDITION ? stepNextOf(s.config) : undefined);
      if (next && byId.has(next)) {
        childrenOf.get(s.id)!.push(next);
        indegree.set(next, (indegree.get(next) || 0) + 1);
      }
    });
    const pos = new Map<string, { row: number; col: number }>();
    const queue: string[] = [];
    stepsList.forEach(s => { if ((indegree.get(s.id) || 0) === 0) queue.push(s.id); });
    // 无条件下一跳的 END / 孤立节点需要兜底
    if (queue.length === 0 && stepsList.length > 0) {
      const entry = stepsList.find(s => s.isEntryPoint) || stepsList[0];
      queue.push(entry.id);
    }
    let row = 0;
    while (queue.length > 0) {
      const levelSize = queue.length;
      const levelCols = new Set<string>();
      for (let i = 0; i < levelSize; i++) {
        const id = queue.shift()!;
        const step = byId.get(id)!;
        const siblings = (() => {
          const parent = stepsList.find(p => (p.nextStepId === id) || (p.stepType === StepType.CONDITION && stepNextOf(p.config) === id));
          if (parent) return childrenOf.get(parent.id) || [id];
          return [id];
        })();
        const idx = siblings.indexOf(id) === -1 ? 0 : siblings.indexOf(id);
        const total = siblings.length || 1;
        pos.set(id, { row, col: total === 1 ? 0 : (idx - (total - 1) / 2) });
        levelCols.add(String(pos.get(id)!.col));
        byId.get(id)!;
        if (step.stepType === StepType.CONDITION) {
          const n = stepNextOf(step.config);
          if (n && byId.has(n) && !queue.includes(n)) {
            indegree.set(n, (indegree.get(n) || 0) - 1);
            if ((indegree.get(n) || 0) <= 0) queue.push(n);
          }
        } else if (step.nextStepId && byId.has(step.nextStepId) && !queue.includes(step.nextStepId)) {
          indegree.set(step.nextStepId, (indegree.get(step.nextStepId) || 0) - 1);
          if ((indegree.get(step.nextStepId) || 0) <= 0) queue.push(step.nextStepId);
        }
      }
      row += 1;
      // 排除孤立节点入队
      if (row > stepsList.length * 2) break;
    }
    return pos;
  }, [stepNextOf]);

  /** 后端步骤 → 画布 nodes/edges */
  const stepsToCanvas = useCallback((stepsList: ScrmJourneyStep[]) => {
    const pos = buildLayout(stepsList);
    const nodeList: StepNode[] = stepsList.map((s, idx) => {
      const p = pos.get(s.id) || { row: idx, col: 0 };
      return {
        id: s.id,
        type: 'step',
        position: { x: 100 + (p.col || 0) * 260, y: 60 + (p.row || 0) * 130 },
        data: { step: s },
      } as StepNode;
    });
    const edgeList: Edge[] = [];
    stepsList.forEach(s => {
      const next = s.nextStepId || (s.stepType === StepType.CONDITION ? stepNextOf(s.config) : undefined);
      if (next && stepsList.some(t => t.id === next)) {
        edgeList.push({
          id: `${s.id}->${next}`,
          source: s.id,
          target: next,
          label: s.stepType === StepType.CONDITION ? 'true' : undefined,
          style: { stroke: '#6366f1', strokeWidth: 2 },
        });
      }
    });
    return { nodeList, edgeList };
  }, [buildLayout, stepNextOf]);

  /** 加载选中旅程的步骤 */
  const loadSteps = useCallback(async (journeyId: string) => {
    setStepsLoading(true);
    try {
      const data = await apiClient.get<ScrmJourneyStep[]>(`/scrm/customer-journeys/${journeyId}/steps`);
      const sorted = [...(data || [])].sort((a, b) => a.stepOrder - b.stepOrder);
      setSteps(sorted);
      const { nodeList, edgeList } = stepsToCanvas(sorted);
      setNodes(nodeList);
      setEdges(edgeList);
    } catch {
      // 拦截器已弹出错误
    } finally {
      setStepsLoading(false);
    }
  }, [setNodes, setEdges, stepsToCanvas]);

  /** 选择旅程 */
  const handleJourneySelect = useCallback((journeyId: string) => {
    setSelectedJourneyId(journeyId);
    const j = journeys.find(x => x.id === journeyId) || null;
    setSelectedJourney(j);
    setSelectedStep(null);
    if (journeyId) loadSteps(journeyId);
  }, [journeys, loadSteps]);

  useEffect(() => {
    if (journeys.length > 0 && !selectedJourneyId) {
      handleJourneySelect(journeys[0].id);
    }
  }, [journeys, selectedJourneyId, handleJourneySelect]);

  /** 手动连线 (step.nextStepId) */
  const onConnect = useCallback((connection: Connection) => {
    setEdges(eds => addEdge({ ...connection, style: { stroke: '#6366f1', strokeWidth: 2 } }, eds));
    if (connection.source && connection.target) {
      setSteps(prev => prev.map(s => s.id === connection.source ? { ...s, nextStepId: String(connection.target) } : s));
    }
  }, [setEdges, setSteps]);

  /** 删除节点 */
  const handleNodeDelete = useCallback(async (nodeId: string) => {
    try {
      await apiClient.delete(`/scrm/customer-journeys/steps/${nodeId}`);
      message.success('步骤已删除');
      const after = steps.filter(s => s.id !== nodeId);
      setSteps(after);
      setNodes(ns => ns.filter(n => n.id !== nodeId));
      setEdges(es => es.filter(e => e.source !== nodeId && e.target !== nodeId));
      if (selectedStep?.id === nodeId) setSelectedStep(null);
    } catch {
      // 拦截器已弹出错误
    }
  }, [steps, setSteps, setNodes, setEdges, selectedStep, message]);

  /** 打开新增步骤弹窗 */
  const openAdd = () => {
    addForm.resetFields();
    addForm.setFieldsValue({ stepType: StepType.SEND_MESSAGE, config: '{}', stepOrder: steps.length + 1 });
    setAddOpen(true);
  };

  /** 新增步骤 */
  const handleAdd = async () => {
    if (!selectedJourneyId) { message.warning('请先选择旅程'); return; }
    const values = await addForm.validateFields();
    setAddSaving(true);
    try {
      const dto = {
        stepName: values.stepName,
        stepType: values.stepType,
        stepOrder: values.stepOrder,
        config: values.config || '{}',
        isEntryPoint: steps.length === 0,
      };
      const created = await apiClient.post<ScrmJourneyStep>(`/scrm/customer-journeys/${selectedJourneyId}/steps`, dto);
      setAddOpen(false);
      setSteps(prev => [...prev, created]);
      setSelectedStep(created);
      await loadSteps(selectedJourneyId);
      message.success('步骤已新增');
    } catch {
      // 拦截器已弹出错误
    } finally {
      setAddSaving(false);
    }
  };

  /** 保存节点属性 (名称/类型/配置/nextStepId) */
  const handlePropSave = async () => {
    if (!selectedStep) return;
    const values = await propForm.validateFields();
    setPropSaving(true);
    try {
      const updated = await apiClient.put<ScrmJourneyStep>(`/scrm/customer-journeys/steps/${selectedStep.id}`, {
        stepName: values.stepName,
        stepType: values.stepType,
        stepOrder: selectedStep.stepOrder,
        config: values.config,
        nextStepId: selectedStep.nextStepId,
        isEntryPoint: selectedStep.isEntryPoint,
        description: values.description,
      });
      setSteps(prev => prev.map(s => s.id === updated.id ? updated : s));
      setSelectedStep(updated);
      setNodes(ns => ns.map(n => n.id === updated.id ? { ...n, data: { step: updated } } : n));
      message.success('步骤已保存');
    } catch {
      // 拦截器已弹出错误
    } finally {
      setPropSaving(false);
    }
  };

  /** 全量保存 (节点位置/连线 → reorder + 更新 nextStepId) */
  const handleSaveAll = async () => {
    if (!selectedJourneyId) return;
    setSaving(true);
    try {
      // 1) 更新连线引起的 nextStepId（CONDITION trueNextStep 由属性面板负责）
      const updateTasks = steps.filter(s => s.nextStepId).map(s =>
        apiClient.put(`/scrm/customer-journeys/steps/${s.id}`, {
          stepName: s.stepName,
          stepType: s.stepType,
          stepOrder: s.stepOrder,
          config: s.config,
          nextStepId: s.nextStepId,
          isEntryPoint: s.isEntryPoint,
          description: s.description,
        }),
      );
      await Promise.all(updateTasks);
      // 2) 按画布拓扑重排 (source 入度顺序)
      const topo: string[] = [];
      const visited = new Set<string>();
      const byId = new Map(steps.map(s => [s.id, s]));
      const visit = (id: string) => {
        if (visited.has(id) || !byId.has(id)) return;
        visited.add(id);
        const s = byId.get(id)!;
        const next = s.nextStepId || (s.stepType === StepType.CONDITION ? stepNextOf(s.config) : undefined);
        if (next) visit(next);
        topo.push(id);
      };
      steps.filter(s => s.isEntryPoint).forEach(s => visit(s.id));
      steps.forEach(s => visit(s.id));
      await apiClient.post(`/scrm/customer-journeys/${selectedJourneyId}/steps/reorder`, topo);
      message.success('画布已保存 (步骤顺序与连线已同步)');
    } catch {
      // 拦截器已弹出错误
    } finally {
      setSaving(false);
    }
  };

  /** 启停旅程 */
  const handleToggleJourney = async (toActive: boolean) => {
    if (!selectedJourney) return;
    try {
      await apiClient.post(`/scrm/customer-journeys/${selectedJourney.id}/${toActive ? 'activate' : 'pause'}`);
      message.success(toActive ? '旅程已启动' : '旅程已暂停');
      setSelectedJourney(prev => prev ? { ...prev, status: toActive ? 'ACTIVE' : 'PAUSED' } : prev);
    } catch {
      // 拦截器已弹出错误
    }
  };

  const nodeTypes = useMemo(() => ({ step: StepNodeComponent }), []);

  return (
    <div className="journey-canvas-page">
      <Card
        title="客户旅程画布"
        extra={
          <Space>
            <Select
              placeholder="选择旅程"
              style={{ width: 240 }}
              loading={journeysLoading}
              value={selectedJourneyId || undefined}
              onChange={handleJourneySelect}
              options={journeys.map(j => ({ value: j.id, label: j.journeyName }))}
            />
            {selectedJourney && (
              <Tag color={journeyStatusConfig[selectedJourney.status]?.color || 'default'}>
                {journeyStatusConfig[selectedJourney.status]?.label || selectedJourney.status}
              </Tag>
            )}
            <Button icon={<ReloadOutlined />} onClick={() => selectedJourneyId && loadSteps(selectedJourneyId)} />
            <Button
              icon={<PlusOutlined />}
              onClick={openAdd}
              disabled={!selectedJourneyId}
              loading={addSaving}
            >
              新增步骤
            </Button>
            {selectedJourney?.status === 'ACTIVE' ? (
              <Button icon={<PauseCircleOutlined />} onClick={() => handleToggleJourney(false)}>
                暂停
              </Button>
            ) : (
              <Button
                icon={<PlayCircleOutlined />}
                onClick={() => handleToggleJourney(true)}
                disabled={!selectedJourney || selectedJourney.status === 'ARCHIVED'}
              >
                启动
              </Button>
            )}
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={handleSaveAll}
              loading={saving}
              disabled={!selectedJourneyId}
            >
              保存画布
            </Button>
          </Space>
        }
      >
        {!selectedJourneyId ? (
          <Empty description="请选择上方旅程以开始编排" style={{ padding: 80 }} />
        ) : (
          <Row gutter={12}>
            <Col span={17}>
              <div
                style={{
                  height: 560,
                  border: '1px solid #e5e7eb',
                  borderRadius: 12,
                  background: '#fafafa',
                }}
              >
                {stepsLoading ? (
                  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
                    <Spin tip="加载步骤..." />
                  </div>
                ) : (
                  <ReactFlow<StepNode, Edge>
                    nodes={nodes}
                    edges={edges}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    onConnect={onConnect}
                    nodeTypes={nodeTypes}
                    fitView
                    proOptions={{ hideAttribution: true }}
                    defaultEdgeOptions={{ style: { stroke: '#6366f1', strokeWidth: 2 } }}
                    minZoom={0.3}
                    maxZoom={1.6}
                    onNodeClick={(_, node) => {
                      const step = steps.find(s => s.id === node.id);
                      if (step) {
                        setSelectedStep(step);
                        propForm.setFieldsValue({ ...step, config: step.config === '{}' ? '{}' : step.config });
                      }
                    }}
                  >
                    <Background variant={BackgroundVariant.Dots} gap={20} size={1} />
                    <Controls />
                    <MiniMap
                      nodeColor={(n) => stepTypeConfig[(n as StepNode).data?.step?.stepType as StepType]?.color || '#a855f7'}
                      maskColor="rgba(0,0,0,0.06)"
                    />
                  </ReactFlow>
                )}
              </div>
            </Col>
            <Col span={7}>
              <Card
                size="small"
                title="步骤属性"
                extra={
                  selectedStep ? (
                    <Popconfirm title="删除该步骤?" onConfirm={() => handleNodeDelete(selectedStep.id)}>
                      <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                    </Popconfirm>
                  ) : null
                }
              >
                {!selectedStep ? (
                  <Empty description="点击画布节点编辑属性" style={{ padding: 24 }} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  <Form form={propForm} layout="vertical">
                    <Form.Item name="stepName" label="步骤名称" rules={[{ required: true, message: '请输入步骤名称' }]}>
                      <Input maxLength={200} />
                    </Form.Item>
                    <Form.Item name="stepType" label="步骤类型" rules={[{ required: true }]}>
                      <Select
                        options={Object.entries(stepTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))}
                      />
                    </Form.Item>
                    <Form.Item name="config" label="配置 JSON">
                      <Input.TextArea rows={5} placeholder='{"templateId":"1"}' />
                    </Form.Item>
                    <Form.Item
                      name="description"
                      label="描述"
                    >
                      <Input.TextArea rows={2} placeholder="步骤说明" />
                    </Form.Item>
                    {selectedStep.isEntryPoint && (
                      <Form.Item>
                        <Tag color="geekblue">入口步骤 (isEntryPoint)</Tag>
                      </Form.Item>
                    )}
                    <Space>
                      <Button type="primary" size="small" icon={<SaveOutlined />} onClick={handlePropSave} loading={propSaving}>
                        保存属性
                      </Button>
                      <Tooltip title="连接线 = nextStepId; CONDITION 分支在 config.trueNextStep / config.falseNextStep">
                        <BranchesOutlined style={{ color: '#8b5cf6' }} />
                      </Tooltip>
                    </Space>
                    {selectedStep.stepType === StepType.CONDITION && (
                      <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
                        条件分支: 请在 config 中设置 trueNextStep / falseNextStep (步骤 ID), 连边默认标 true
                      </Text>
                    )}
                  </Form>
                )}
              </Card>
            </Col>
          </Row>
        )}
      </Card>

      {/* 新增步骤弹窗 */}
      <Modal
        title="新增步骤"
        open={addOpen}
        onCancel={() => setAddOpen(false)}
        onOk={handleAdd}
        confirmLoading={addSaving}
        width={520}
      >
        <Form form={addForm} layout="vertical">
          <Form.Item name="stepName" label="步骤名称" rules={[{ required: true, message: '请输入步骤名称' }]}>
            <Input placeholder="如: 发送欢迎消息" maxLength={200} />
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item name="stepType" label="步骤类型" rules={[{ required: true }]}>
              <Select style={{ width: 180 }} options={Object.entries(stepTypeConfig).map(([v, c]) => ({ value: v, label: c.label }))} />
            </Form.Item>
            <Form.Item name="stepOrder" label="顺序">
              <Input type="number" style={{ width: 100 }} />
            </Form.Item>
          </Space>
          <Form.Item name="config" label="配置 JSON" initialValue="{}">
            <Input.TextArea rows={3} placeholder='{"templateId":"1"} / {"days":7} / {"trueNextStep":"2","falseNextStep":"3"}' />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
