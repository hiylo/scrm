# SCRM 竞品差距补齐路线图（2026-10）

> 制定日期：2026-10-08　状态：进行中（P0-1 已完成）
> 坐标系：企业微信 SCRM（微伴/尘锋/微盛）+ 个人微信私域工具（Wetool 类）双线。
> 基线事实：后端 103 controller / 260 实体 / 275 表几乎全套就绪；缺口大头在**前端**（19 页 vs 100+ 域）与**真实通道**（企微 mock、AI 对话助手占位、无侧边栏/移动端）。

## 现状缺口速览

| 差距 | 状态 |
|---|---|
| AI 对话助手用占位生成器（`DefaultAiResponseGenerator`），未接 `AiChatClient` | ✅ **P0-1 已完成**（真实 LLM 冒烟通过） |
| `AiChatClient` 路径写死 ai-server 专有端点（真实环境 404）+ 无鉴权头 | ✅ P0-1 顺带修复（`SCRM_AI_CHAT_PATH` / `SCRM_AI_API_KEY` 可配置） |
| `AiChatRequest.maxTokens` 序列化为 camelCase（LiteLLM 拒绝 500） | ✅ P0-1 顺带修复（`@JsonProperty("max_tokens")`） |
| 企微通道 mock（`WEWORK_MOCK_MODE=true`），会话存档解密 JNI 未接 | P0-2 |
| 无企微侧边栏 / H5 应用（SCRM 主入口） | P0-3 |
| 渠道活码/欢迎语/自动打标有后端无前端（拉新闭环） | ✅ **P1-1 已完成**（前端 3 页 + 扫码记录分页端点补齐） |
| 群发仅客户维度，无群群发 | ✅ **P1-2 已完成**（群发任务管理+群群发 2 页） |
| 快捷回复/话术库/素材库有后端无前端 | ✅ **P1-3 已完成**（前端 3 页） |
| 客户旅程可视化编排有后端无前端 | P2-1 |
| 30+ 域有 API 无 UI（工单/审批/合同/线索/公海/会员/积分/NPS/知识库/质检/竞品/黑名单/行为追踪/报表等） | ✅ **P2-2 已完成**（12 批、约 61 个后端域 UI 承接，前端页面 19 → 66） |
| 封号风控/养号体系 | ✅ **P2-3 执行层已落地**（出站发送前守卫：账号冻结/离线/黑名单/频率拦截）；剩余回声环真机验证待外部 |
| 无多租户（README 明确单租户单体） | 架构决策，不列为缺口 |

## 批次计划

### P0 —— 决定产品"能否用起来"

- **P0-1 真实 AI 对话助手落地**（✅ 已完成 + 真实 LLM 冒烟通过）
  - 后端：`RealAiResponseGenerator implements AiResponseGenerator`（`@Component @Primary`），
    复用既有 `AiChatClient`；systemPrompt 取 `ScrmAiAssistantConfigEntity.systemPrompt`；
    模型/温度/maxTokens 实体配置优先、全局 `scrm.ai.*` 兜底；降级回退占位生成器。
  - 冒烟附加修复（真实调用 LiteLLM 暴露）：
    - `AiChatClient` 路径写死 ai-server 专有端点（真实 404）→ 新增 `scrm.ai.chat-path` /
      `scrm.ai.api-key` 配置，可对接 OpenAI 兼容服务（冒烟用 NAS LiteLLM 4000）。
    - `AiChatRequest.maxTokens` 序列化 camelCase（LiteLLM 上游 500）→
      `@JsonProperty("max_tokens")`。
  - 前端：`AiAssistant.tsx` 对话页（选客户 + 对话 + 意图/情感标签 + 推荐回复 + 历史记录）。
  - 冒烟：`RealAiResponseGeneratorSmokeTest`（`@EnabledIfEnvironmentVariable` 保护，
    无 `SCRM_SMOKE_AI_*` 自动跳过），连 NAS LiteLLM 返回真实回复。
  - 验证：后端 40 用例 + 前端 270 用例 + `tsc --noEmit` + `lint 0 error` 全绿。
  - 待办：测试环境 8840 需重启加载新 jar + `SCRM_AI_ENABLED=true` +
    `SCRM_AI_CHAT_PATH=/v1/chat/completions` + `SCRM_AI_API_KEY` 才真正启用（登记 todo）。
- **P0-2 企微通道真实化**
  - 关 mock：`WeworkServiceImpl` mock 分支改为真 API（通讯录/部门/消息发送骨架已在）。
  - 会话存档解密拍板：引入 `libWeWorkFinanceSdk_C.so(JNI)` 或显式 `DECRYPT_SDK_REQUIRED` 失败（诚实降级）。
  - 阻塞：真实企微企业凭据（`WEWORK_CORP_ID/AGENT_ID/SECRET`）。
- **P0-3 企微侧边栏 / H5 应用**
  - H5 版"客户资料 + 快捷回复 + 打标"（企微自建应用侧边栏 iframe 嵌入）。
  - 后端：企微 OAuth 网页授权登录（userid → JWT）。
  - 依赖：P0-2。

### P1 —— 品类核心体验（多数"后端已有只差前端"，性价比最高）

- **P1-1 拉新闭环：渠道活码 + 欢迎语 + 自动打标**（✅ 已完成）
  - 后端三模块 API 本就完备（CRUD/启停/扫码/匹配/触发/评估/统计/元数据），缺口在**前端 0 页面**。
  - 补齐：`GET /scrm/channel-codes/{id}/scans` 扫码记录分页端点
    （repository `findByChannelCodeIdOrderByScannedAtDesc` + service `listScans` + controller `/scans`）。
  - 前端 3 页：`ChannelCodes.tsx`（列表/创建编辑/启停/统计抽屉+扫码明细/扫码添加统计）、
    `WelcomeMessages.tsx`（规则 CRUD/启停/绑定账号活码/触发预览/触发统计）、
    `AutoTags.tsx`（规则 CRUD/启停/**手动评估**/执行日志/字段与操作符元数据加载）。
  - 验证：前端 283 用例（新增 13）+ `tsc --noEmit` + `lint 0 error`；后端 12 用例含上下文装配全绿。
  - 链路说明：`recordScan` 目前只做「分配账号 + 记扫码」；打标（`/auto-tags/evaluate`）与
    欢迎语（`/welcome-messages/trigger`）为独立端点，执行侧可按「扫码→评估→触发」编排，未在
    `recordScan` 内硬编码串联（保持 REST 语义，避免后端过度耦合）。
- **P1-2 群发增强：群发任务管理 + 群群发**（✅ 已完成）
  - 现状调研：群发任务后端（`/scrm/mass-send*` CRUD/发布/暂停/恢复/取消/目标明细/报告）
    与群广播后端（`/scrm/communities/messages/broadcast`，`ScrmCommunityService.broadcast` 已实现
    多群写入 + 今日消息数更新）**均已完备**，缺口在**前端 0 页面**。
  - 前端 2 页：`MassSends.tsx`（任务列表/创建（全量/分群/标签/指定客户）/发布/暂停/恢复/
    取消/目标明细抽屉+发送报告）、`GroupBroadcast.tsx`（群列表勾选 + 多群广播弹窗）。
  - 验证：前端 290 用例（新增 7）+ `tsc --noEmit` + lint（12 个新文件 0 warning）；后端 27 用例全绿。- **P1-3 沟通效率：快捷回复 + 话术库 + 素材库**（✅ 已完成）
  - 后端 `QuickReply/Speech*/AssetLibrary` 全套现成（CRUD/分类/搜索/使用计数/点赞/发布归档/审核），
    缺口在**前端 0 页面**。
  - 前端 3 页：`QuickReplies.tsx`（分类侧栏 + 回复列表 + 快捷码 + 使用次数）、
    `Speeches.tsx`（分类 + 话术 CRUD + 点赞/上下架）、`Assets.tsx`（分类树 + 素材 CRUD +
    上传/发布/归档 + 审核状态 + 文件大小格式化）。
  - 素材真实文件上传（MinIO，2026-10-09 补充）：新增 `POST /scrm/assets/upload-file`（multipart），
    文件写入对象存储素材命名空间 `scrm/asset/{yyyyMM}/{uuid}_{name}`，素材落
    `fileUrl=objectKey` + `storagePath`/`storageBucket`/`storageType=OSS`；复用
    `ObjectStorage`（MinIO/OSS 双实现），路径穿越净化 + MIME 白名单降级防 XSS，
    未配置 endpoint 抛 400（不退化）。前端上传弹窗支持选文件（multipart）或填 URL（JSON）二选一。
  - 预签名预览/下载闭环（2026-10-09 补充）：新增 `GET /scrm/assets/{id}/presigned-url`，OSS 素材
    实时生成预签名 URL（默认 60 分钟），LOCAL/外部 URL 素材直接返回 fileUrl；前端列表加「预览」操作。
  - 验证：后端 1685 用例（+10：上传 5 + 预签名 5）+ 前端 452 用例（+2：文件上传/预览）+ `tsc 0 错误` + lint 0 error 全绿。

### P2 —— 完整度

- **P2-1 客户旅程可视化编排**（✅ 已完成）
  - `JourneyCanvas.tsx` 画布编辑器（引入 `@xyflow/react@12.12.0`，用户已拍板）：旅程选择 → 步骤节点画布
    （拖拽定位/连线=nextStepId/CONDITION 分支 true 标注）→ 属性面板编辑（名称/类型/配置 JSON/入口标记）
    → 新增步骤 / 删除步骤 / 保存画布（PUT steps/{id} 同步连线 + POST steps/reorder 拓扑重排）/ 启停旅程。
  - 后端零改动：steps CRUD + reorder + nextStepId 持久化早已完备（`ScrmCustomerJourneyController`）。
  - 验证：前端 450 用例（新增 4）+ `tsc 0 错误` + lint 0 error；配套 `src/test/setup.ts` 补
    `getComputedStyle` 伪元素垫片 + `ResizeObserver` 改直接赋值（避免 `unstubAllGlobals` 间隙撤销致
    ReactFlow 异步回调崩溃，这是本批测试基础设施的关键修复）。
- **P2-2 30+ 域 UI 批量承接**（每批 3-5 页）
  - 批次 1（销售转化域，✅ 已完成）：
    - `PublicSea.tsx` 公海客户（线索管理：领取/分配/转移/回收/转正/批量分配/分配记录详情）
    - `Opportunities.tsx` 商机管理（漏斗阶段/金额/概率/阶段推进+历史/销售预测抽屉）
    - `Tickets.tsx` 客户工单（编号/类别/优先级/状态机流转/分配/升级/详情抽屉含评论+流转历史）
    - 验证：前端 311 用例（新增 12）+ `tsc --noEmit` + lint（新文件 0 warning）；后端 45 用例含上下文装配全绿。
  - 批次 2（客户运营域，✅ 已完成）：
    - `Membership.tsx` 会员体系（等级 CRUD/升级门槛/启停 + 会员入会/升降级/冻结/注销/续期 + 会员列表）
    - `Points.tsx` 积分管理（账户余额/冻结/累计 + 积分规则 EARN/REDEEM + 手动调账 + 积分流水 + 兑换商城 4 Tab）
    - `Coupons.tsx` 优惠券（券模板 代金/折扣/免邮 + 批量发放 + 券列表/领用状态 + 模板统计抽屉）
    - `Surveys.tsx` NPS 问卷（问卷 CRUD + 状态流转 草稿→进行中→暂停→完成→归档 + 邀请码批量生成 + 复制）
    - 验证：前端 324 用例（新增 13）+ `tsc --noEmit` + lint（新文件 0 warning）；后端上下文装配全绿。
  - 批次 3（内容与知识域，✅ 已完成）：
    - `KnowledgeBase.tsx` 知识库（分类树 + 文章 CRUD/发布/归档/精选/置顶 + 浏览/有帮助计数）
    - `ContentMarketing.tsx` 内容营销（内容 CRUD + 提交审核/审核通过驳回/发布/归档 + 互动指标）
    - `QualityInspections.tsx` 会话质检（规则 SENSITIVE_WORD/态度/响应速度/合规 + 任务创建/执行/进度 + 结果抽屉含总分/通过判定）
    - 验证：前端 335 用例（新增 11）+ `tsc --noEmit` + lint（新文件 0 warning）；后端 107 用例全绿（KB 48 + Content 48 + Quality 9 + 装配 2）。
  - 批次 4（数据分析与风控域，✅ 已完成）：
    - `RfmAnalysis.tsx` RFM 客户价值分析（配置管理 R/F/M 权重/周期 + 触发计算 + 分群策略 + 客户价值明细抽屉）
    - `LtvPrediction.tsx` LTV 客户终身价值预测（模型管理 历史/回归/BG-NBD/CLV + 发布/默认 + 同期群 Cohort 留存 + 客户预测明细含流失风险 2 Tab）
    - `Attribution.tsx` 营销归因分析（归因模型 首次/末次/线性/时间衰减/位置/数据驱动 + 发布 + 触点明细 2 Tab）
    - `Blacklist.tsx` 风险黑名单（客户/手机号/设备/IP/OpenID 黑白名单 CRUD + 单条校验 + 移除）
    - 验证：前端 352 用例（新增 17）+ `tsc --noEmit` + lint（新文件 0 warning）；后端 54 用例全绿（LTV 27 + RFM 15 + Blacklist 10 + 装配 2）。
  - 批次 5（商务闭环域，✅ 已完成）：
    - `Contracts.tsx` 合同管理（CRUD + 提交审批/审批/签署/生效/终止状态流 + 回款记录抽屉）
    - `Orders.tsx` 产品订单（CRUD + 状态机 待确认→确认→收款→发货→送达→完成 + 明细抽屉）
    - `Commissions.tsx` 佣金结算（记录列表 + records/calculate 计算 + payouts mark-paid 支付 + clawback 冲正 + 汇总抽屉）
    - `Invoices.tsx` 发票管理（apply 申请 + issue 开具 + void 作废 + PDF）
    - 验证：前端 368 用例（新增 16）+ `tsc --noEmit` + lint（新文件 0 warning）；后端 76 用例全绿（Order 33 + Invoice 25 + Commission 10 + Contract 6 + 装配 2）。
  - 批次 6（系统集成域，✅ 已完成）：
    - `LeadScoring.tsx` 线索打分（模型 CRUD/发布/默认 + 打分维度 JSON + 全量计算 + 分数明细 2 Tab）
    - `Notifications.tsx` 通知中心（模板 CRUD/启停 + 发送/标记已读 + 模板/消息 2 Tab）
    - `Webhooks.tsx` Webhook（配置 CRUD/启停 + 测试回调 + 事件日志 2 Tab）
    - 验证：前端 380 用例（新增 12）+ `tsc --noEmit` + lint（新文件 0 warning）；后端上下文装配全绿。
  - 后续批次（每批 3-5 页）：数据字典/导入导出、客户旅程、报表中心等。
    每页遵循 `Table + Modal + apiClient` 模式 + `*.test.tsx`。
  - 批次 8（运营协同域，✅ 已完成）：
    - `Approvals.tsx` 审批中心（审批流 CRUD/默认 + 实例提交/通过·驳回 body 型 action + 日志时间线 2 Tab）
    - `Reports.tsx` 报表中心（报表模板 CRUD/执行 + 结果列表 JSON 预览）
    - `MarketingCalendar.tsx` 营销日历（事件 CRUD + 状态流转 待确认→已确认→进行中→完成/取消 + 假日管理 2 Tab）
    - 验证：前端 403 用例（新增 12）+ `tsc --noEmit` + lint（新文件 0 warning）；后端 58 用例全绿（MarketingCalendar 25 + Report 26 + Approval 5 + 装配 2）。
  - 批次 9（客户运营执行域，✅ 已完成）：
    - `CustomerCare.tsx` 客户关怀（关怀规则 生日/纪念日/节日/事件/手动 + 启停 + 关怀任务执行 + 关怀记录抽屉 2 Tab）
    - `Visits.tsx` 客户拜访（拜访计划 CRUD/启停 + 拜访任务完成/取消 + 频次/模板 2 Tab）
    - `FollowUps.tsx` 客户跟进（跟进任务 CRUD/完成/取消 + 跟进记录 2 Tab）
    - 验证：前端 414 用例（新增 11）+ `tsc --noEmit` + lint（新文件 0 warning）；后端 21 用例全绿（FollowUp 10 + Care 9 + 装配 2）。
  - 批次 10（工单/互动/竞品/预算，✅ 已完成）：
    - `WorkOrders.tsx` 工单管理（CRUD + 状态机 分配→接单→开始→解决→关闭 + 重开/取消/升级 + SLA + 明细抽屉）
    - `InteractionCalendars.tsx` 互动日历（计划 CRUD + 状态流转 确认→开始→完成/取消/未赴约 + 今日/近期/逾期视图）
    - `Competitors.tsx` 竞品监测（竞品 CRUD + 威胁等级/行业过滤 + 监控启停 + 产品价格监测抽屉）
    - `Budgets.tsx` 预算管理（计划 CRUD + 审批/启停/预警 + 渠道分配 + 预算转移）
    - 验证：前端 429 用例（新增 15）+ `tsc --noEmit` + lint（新文件 0 warning）；后端 4 模块相关测试全绿。
  - P2-2 覆盖度：已累计 10 批，约 57 个后端域获得 UI 承接（前端页面 19 → 62）。
  - 批次 11（系统基础·收尾批，✅ 已完成）：
    - `Segments.tsx` 客户分群（动态/静态/混合 + 条件 JSON + 计算/预览 + 成员管理 + 启停）
    - `TaskScheduler.tsx` 任务调度（定时任务 CRUD + cron/固定频率 + 启停/暂停/恢复 + 手动执行 + 执行记录 2 Tab）
    - `OpenApiApps.tsx` OpenAPI 管理（应用 CRUD + 暂停/吊销/续签 + 密钥管理 + 权限范围）
    - 验证：前端 439 用例（新增 10）+ `tsc --noEmit` + lint（新文件 0 warning）；后端相关测试全绿。
  - P2-2 覆盖度：已累计 11 批，约 60 个后端域获得 UI 承接（前端页面 19 → 65）。
  - 批次 12（消息模板中心，✅ 已完成）：
    - `MessageTemplateCenter.tsx` 消息模板中心（模板 CRUD 营销/通知/服务/系统 + 多渠道 + 发布/归档/复制 + 版本 + 模板分组 CRUD/启停 + 分组树 2 Tab）
    - 验证：前端 441 用例 + `tsc --noEmit` + lint（新文件 0 warning）；后端 MessageTemplateCenter 相关测试全绿。
  - P2-2 覆盖度：**已累计 12 批，约 61 个后端域获得 UI 承接（前端页面 19 → 66）**，与既有页面重叠的剩余域不再重复建页，P2-2 收口。
- **P2-3 封号风控/养号体系**（✅ 执行层已落地；回声环真机验证待 180 侧配合）
  - 核心缺口定位：出站发送唯一入队口 `ScrmOutboundMessageService.enqueueFromMessage` **此前无防封拦截**
    （账号冻结/离线照发、目标黑名单不查、单账号发送不降频）。
  - 实现：`ScrmOutboundGuardComponent`（发送前守卫，注入入队点）——账号冻结/离线拦截、
    目标客户黑名单拦截、单账号近 60s 发送频率超限拦截（`scrm.outbound.guard.*` 可配）；
    命中拦截 → 出站消息置 `BLOCKED`（新增状态, 不入队不发事件）+ 记 `SEND_BLOCKED` 风险事件；
    黑名单服务异常降级放行（防故障蔓延）。
  - 内容守卫（敏感词拦截，2026-10-09 补充）：第 4 守卫对发送内容构造评估 context 调用
    `ScrmBlacklistRuleService.evaluateAllRules`（复用现有 PATTERN/CONTAINS/REGEX/MATCH 规则引擎，
    零新增敏感词表），`totalTriggered>0` → 拦截（BLOCKED + `SENSITIVE_CONTENT` 信号）；规则引擎
    异常降级放行。
  - 自动处置闭环（2026-10-09 补充）：`guardBlock` 命中账号违规信号（`SEND_RATE_LIMIT` /
    `SENSITIVE_CONTENT`）时自动调用 `ScrmAccountService.updateLoginState(accountId, "FROZEN", ...)`
    暂停账号，防止继续触发平台风控（开关 `scrm.outbound.guard.auto-pause-on-block`，默认 true）；
    `ACCOUNT_FROZEN`（已冻结）与 `TARGET_BLACKLISTED`（目标客户黑名单，非账号风险）不触发；
    暂停失败（异常）只记日志，不影响拦截决定。
  - 验证：后端 1675 用例（守卫 9 路径：冻结/离线/黑名单/频率/内容命中/放行/黑名单异常降级/
    规则异常降级/自动暂停异常降级 + 既有 Outbound 16 兼容）全绿。
  - 剩余：回声环 is_self 真机验证（待 180 侧配合, todo `91ca0a5a259e`）。

## 决策点

1. 企微会话存档 JNI SDK 是否引入（P0-2）。
2. 客户旅程画布是否引入 `react-flow` 依赖（P2-1）。✅ 已拍板：引入 `@xyflow/react@12.12.0`（2026-10-09 完成）。
3. 多租户是否需要（当前明确不做，仅记录）。

## 横切纪律（每批强制）

- 后端 `mvn test`（基线 1625 绿）、前端 `npx tsc --noEmit` + `npm test` + `npm run lint` 零新增告警。
- 按功能拆分提交（禁 `git add -A`）；`@stub` 出现即需真实现或显式降级。
- 每批验收判据写进 todo（登记制度，完成即删）。