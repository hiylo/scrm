# 全量 UI 功能验收清单

> 目标：把系统所有功能与功能点列成清单，用 CDP 操作真实页面逐个验证（验证页面功能正常，不是接口正常），并用视觉工具分析各页面布局与功能异常。
> 范围：admin 账号可见的全部 41 个菜单页面（来源 `frontend/src/components/Layout.tsx`）。
> 环境：`http://192.168.1.170:8840/`，账号 `admin / admin123456`（ADMIN，全部 5 个主体可见）。
> 数据口径：调试环境，写操作产生的数据保留，不做删除（用户确认）。

## 一、菜单与页面清单（41 页）

| 批次 | 分组 | 路径 | 名称 |
|---|---|---|---|
| 1 | 概览 | `/` | 财税概览 |
| 1 | 财税业务 | `/workbench` | 会计工作台 |
| 1 | 财税业务 | `/vouchers` | 记账凭证 |
| 1 | 财税业务 | `/accounting-reports` | 账务报表 |
| 1 | 财税业务 | `/companies` | 公司主体 |
| 1 | 财税业务 | `/fixed-assets` | 固定资产 |
| 2 | 客户管理 | `/customers` | 客户列表 |
| 2 | 客户管理 | `/customer-groups` | 客户分组 |
| 2 | 客户管理 | `/customer-tags` | 标签管理 |
| 2 | 客户管理 | `/follow-ups` | 客户跟进 |
| 2 | 客户管理 | `/customer-care` | 客户关怀 |
| 2 | 财税业务 | `/compliance` | 合规到期 |
| 2 | 财税业务 | `/payroll` | 社保个税 |
| 2 | 财税业务 | `/documents` | 资料归档 |
| 3 | 财税业务 | `/license-ocr` | 证照识别 |
| 3 | 财税业务 | `/inspections` | 巡检与公告 |
| 3 | 财税业务 | `/finance-readiness` | 就绪看板 |
| 3 | 财税业务 | `/tax-calendar` | 报税日历 |
| 3 | 财税业务 | `/client-portal` | 客户查账 |
| 3 | 财税业务 | `/tax-risk-profile` | 税负风险 |
| 3 | 财税业务 | `/tax-holidays` | 假期维护 |
| 3 | 财税业务 | `/renewals` | 服务续费 |
| 3 | 财税业务 | `/policy-push` | 政策推送 |
| 4 | 商务闭环 | `/contracts` | 合同管理 |
| 4 | 商务闭环 | `/invoices` | 发票管理 |
| 4 | 系统集成 | `/notifications` | 通知中心 |
| 4 | 系统集成 | `/webhooks` | Webhook |
| 4 | 运营协同 | `/approvals` | 审批中心 |
| 4 | 运营协同 | `/reports` | 报表中心 |
| 4 | 运营协同 | `/work-orders` | 工单管理 |
| 5 | 会话与通讯 | `/conversations` | 会话列表 |
| 5 | 内容与知识 | `/knowledge-base` | 知识库 |
| 5 | 系统配置 | `/message-template-center` | 模板中心 |
| 6 | 系统配置 | `/data-dictionaries` | 数据字典 |
| 6 | 系统配置 | `/data-transfers` | 导入导出 |
| 6 | 系统配置 | `/task-scheduler` | 任务调度 |
| 6 | 系统配置 | `/open-api-apps` | OpenAPI |
| 6 | 数据 | `/dashboard` | 数据看板 |
| 6 | 数据 | `/audit-logs` | 审计日志 |
| 6 | 系统 | `/users` | 用户管理 |
| 6 | 系统 | `/settings` | 平台配置 |

> 说明：`Layout.tsx` 中 admin 可见菜单共 **41 个**（`grep -c "key: '/"` 实测）；`frontend/src/pages/*.tsx` 共 85 个页面文件，
> 差集是从其他页面内嵌引用的子页（如 `CustomerDetail`、`JourneyCanvas`、`AiProvidersPanel`），
> 随主页面一起验证。

## 二、验证方法

每页固定节奏，避免「读得太早」导致误判：

1. `mcp_browser_open(session:"verify", waitUntil:"domcontentloaded")` 打开页面；
2. `sleep 2~3` 等 SPA 渲染完成（React Router 跳转会销毁 evaluate 执行上下文）；
3. `mcp_browser_evaluate` 读取真实结构：Tab / 按钮 / 表头 / 行数 / 空态文案 / 溢出度量 / 失败请求探针；
4. 逐个点击交互点（Tab 切换、弹窗打开、表单填写、提交），每次操作后重新读取；
5. 截图存证 `.tmp/verify/shots/`，必要时用视觉工具分析布局；
6. 功能是否生效的判断必须同时核对**网络状态码 + DOM + 源码**三处，缺一不下结论。

## 三、已验证页面与结论

### 批次 1

| 页面 | 验证要点 | 结果 |
|---|---|---|
| 财税概览 | 4 项统计（在管主体 5、本月待申报 0、资质临期 1、待续费 2）；2 个列表卡片；待续费 2 条数据 | 通过 |
| 会计工作台 | 4 卡片、9 项统计、3 Tab 可切换，空态文案正确 | 通过 |
| 记账凭证 | 4 子 Tab；选企业后 4 条凭证、操作按钮齐全；试算平衡「账平」；会计科目 33 行 | 通过 |
| 账务报表 | 5 Tab（资产负债表 15 行 / 利润表 12 行 / 总账 / 明细账带科目选择器 / 序时账），报表平衡提示正确 | 通过 |
| 公司主体 | 25 行（5 主体多视图），字段完整；指派会计弹窗标题带企业名、下拉已选当前会计 | 通过 |
| 固定资产 | 未选企业提示明确；选企业后 1 资产，累计折旧 4,750.02 与 30,000×95%÷36×6 自洽 | 通过 |

### 批次 2

| 页面 | 验证要点 | 结果 |
|---|---|---|
| 客户列表 | 10 个客户；搜索「明天」过滤到 1 条；企微外部联系人 Tab 需输入 userid 才查询（设计如此） | 通过 |
| 客户分组 | 空态引导 → 选账号 → 无数据提示；新建写入成功；删除二次确认文案完整；**修复后实测新建即刷新**（归属账号与筛选条件相同的触发场景） | 通过（D3 已修复） |
| 标签管理 | 未填客户 ID 时添加按钮禁用；查询后解除；后端对非法 tagCode 返回 400，前端拦截器弹出提示 | 通过（缺陷见 D4） |
| 客户跟进 | 列表与新建功能修复后可用 | 通过（修复 D1） |
| 客户关怀 | 关怀规则 Tab 表头与新建功能修复后可用 | 通过（修复 D2） |
| 合规到期 | 工商年报 5 户统计与逾期天数正确；资质许可 2 证、剩余天数计算正确 | 通过 |
| 社保个税 | 3 Tab；员工档案 7 人、证件号脱敏；社保/个税明细空态正确；未选企业点「生成」提示明确 | 通过（缺陷见 D5） |
| 资料归档 | 5 户齐全度 0%、必备 5 项逐项列出缺失；资料夹抽屉标题带企业名、齐全度 0/5；上传弹窗字段完整（拖拽区/类型/名称/签发到期日期/备注） | 通过 |

### 批次 3

| 页面 | 验证要点 | 结果 |
|---|---|---|
| 报税日历 | 5 统计卡；报税月历 1 条；申报档案 4 条；新建申报档案成功（客户「明天」/增值税/月报/小规模/DAY15）并回显，档案增至 5 条 | 通过 |
| 服务续费 | 9 列 9 条任务、5 统计；录入续费结果成功（续约成功 ¥4,800），待跟进 2→1、已续约 3→4、金额 ¥32,000→¥36,800 全部自洽 | 通过 |
| 证照识别 | 3 卡片（上传/核对结果/任务记录）；上传控件可用；4 条识别任务，状态「识别成功」「需人工复核」，引擎 local-vision:group/sensenova-pool | 通过 |
| 巡检与公告 | 4 卡片、4 统计（近 7 天税务抓取 7 次、工商巡检 7 次、可用公告源 2 个）；执行历史 9 列 9 行；两个手动触发按钮 | 通过 |
| 就绪看板 | 说明 + 外部依赖链路；4 项依赖 2 就绪 2 未就绪，「缺什么/怎么开」列给出环境变量与签约要求 | 通过 |
| 客户查账 | 未选企业提示明确「不会发起请求」；选企业后 5 卡片、11 项统计、19 行数据；固定资产数字与固定资产页完全一致（自查自洽） | 通过 |
| 税负风险 | 未选企业提示明确；选企业后 2 卡片、4 统计、风险项空态正确、税负率趋势 11 个月标「数据不足」，公式写在表头 | 通过 |
| 假期维护 | 2 卡片（法定节假日 / 调休上班日）、13 行数据、年份选择器、编辑与新增补班日 | 通过 |
| 政策推送 | 7 列 1 行、5 统计；两级人工闸门说明清晰；采样名单/审核通过/驳回/撤销按钮齐全 | 通过 |

### 批次 4

| 页面 | 验证要点 | 结果 |
|---|---|---|
| 合同管理 | 10 列 1 条合同（HT202610240001 / 代理记账 / ¥6,000 / 生效中 / 2025-11-20~2026-11-20）；新建合同、回款按钮齐全 | 通过 |
| 发票管理 | 10 列结构完整（发票号/类型/客户/抬头/不含税额/税额/价税合计/状态/创建时间/操作），空态下新建发票入口可见 | 通过 |
| 通知中心 | 2 Tab（通知模板 / 通知消息）；7 列 7 条模板，含工商年报提醒、资质到期提醒，带变量占位符 `{companyName}`，发送/编辑可用 | 通过 |
| 审批中心 | 2 Tab（审批流 / 审批实例）；6 列结构完整，空态下新建审批流入口可见 | 通过 |
| 报表中心 | 5 列结构完整（模板名称/报表类型/数据源/状态/操作），空态下新建模板入口可见 | 通过 |
| 工单管理 | 8 列结构完整；**修复后实测完整状态流转**：新建(`WO202611110002`, OPEN) → 接单 → 处理中 → 解决 → 已解决，每步按钮集合按状态机正确切换（OPEN: 分配/接单/取消 → IN_PROGRESS: 解决/升级 → RESOLVED: 关闭），接口均 200 | 通过（修复 D11/D12/D13） |
| Webhook | 2 Tab（Webhook 配置 / 事件日志）；8 列结构完整（含目标 URL/订阅事件/方法/超时），空态下新建配置入口可见 | 通过 |

### 批次 5

| 页面 | 验证要点 | 结果 |
|---|---|---|
| 会话列表 | 左侧 10 条会话（头像首字/状态/最后消息/时间/消息数）；点开会话右侧加载 19 条消息气泡，工具栏含 AI 总结/查看客户/导出/关闭会话 | 通过 |
| 知识库 | 2 卡片（知识分类 / 知识文章）；9 列 2 篇已发布政策文章；新增分类、新建文章入口可见 | 通过 |
| 模板中心 | 2 Tab（消息模板 / 模板分组）；8 列 9 条模板，多渠道（企微/微信/短信/邮件）、版本号、发布/复制/编辑/删除齐全 | 通过 |

### 批次 6

| 页面 | 验证要点 | 结果 |
|---|---|---|
| 用户管理 | 7 列 2 条用户（kj01 张会计/经理、admin 系统管理员），最后登录时间、重置密码、禁用；**修复后实测 emoji 归零**，编辑按钮改用 edit 图标 | 通过（D7 已修复） |
| 数据字典 | 8 列结构完整（名称/编码/类型/描述/项数/状态），空态下新建字典入口可见 | 通过 |
| 导入导出 | **修复后实测**：按后端「导入模板」模型展示 9 列（模板名称/数据类型/描述/列定义/去重键/已存在时更新/状态/创建时间/操作）；新建模板 200 并回显，启停开关切换成功（`POST /templates/{id}/disable` 200） | 通过（修复 D14） |
| 任务调度 | 2 Tab（定时任务 / 执行记录）；7 列 1 条任务（次年法定节假日 AI 抽取 / cron 0 0 10 1 11 ? / 启用）；执行/编辑/暂停/删除齐全 | 通过 |
| OpenAPI | 8 列结构完整（含每分钟/每日限流），空态下新建应用入口可见 | 通过 |
| 数据看板 | 8 卡片、4 统计（客户 1,269 / 会话 11 / 营销任务 0 / 风控信号 0）、4 组 Tab、7 个 ECharts 图表均渲染 | 通过 |
| 审计日志 | 10 列 20 条记录；同时旁证本轮三次写操作全部入档（13:38 创建 tax_profile、13:41 更新 renewal_task、13:53 会话标记已读） | 通过 |
| 平台配置 | 3 Tab（企业微信 / AI Providers / 通知偏好）；9 个表单字段（CorpID/AgentId/Secret/AESKey/Token/BaseURL/CallbackURL/Mock 模式/启用真实 API） | 通过 |

## 四、缺陷清单

### 已修复

| 编号 | 级别 | 页面 | 问题 | 状态 |
|---|---|---|---|---|
| D1 | P1 | 客户跟进 | 前端字段名 `taskName`/`followType` 与后端 `title`/`taskType` 不匹配：列表列空白、新建提交 400 完全不可用；类型枚举不一致；缺必填「负责人」 | 已修复 |
| D2 | P1 | 客户关怀 | 表单字段与后端 DTO 大面积错配，缺 `actionType`/`actionContent`，新建必然 400；类型枚举不一致；`actionContent` 实为 JSON 但提示文案误导 | 已修复 |
| D3 | P2 | 客户分组 | 新建分组后列表不自动刷新（删除却会），根因是刷新依赖 `ownerAccountId` 变化，选同一账号时不触发 effect | 已修复 |
| D7 | P3 | 用户管理 | 操作列「编辑」同时渲染 emoji ✏️ 与文案，图标重复 | 已修复 |
| D9 | P1 | 数据字典 | 前端用 `GET /scrm/data-dictionaries/list`，后端是 `POST /list`。GET 落到 `@GetMapping("/{id}")`，`id` 收到字符串 "list"，返回 400「参数 'id' 类型错误」，字典列表完全加载不出来 | 已修复 |
| D10 | P1 | 审批中心 | 前端请求 `/scrm/approvals/approvals/list`（多了一层 approvals），后端是 `/scrm/approvals/list`。请求返回 SPA 首页 HTML，审批实例 Tab 一条都不显示（实际有 7 条） | 已修复 |
| D4 | P3 | 标签管理 | 提交失败后弹窗保持打开但不展示原因，用户不知道错在哪 | 已修复（弹窗内展示后端原因） |
| D5 | P3 | 社保个税 | 空态文案让人直接点「生成」，但还需先选企业，前置条件未说明 | 已修复（按是否选企业给不同文案） |
| D6 | P3 | 客户列表 | 企微外部联系人空态未提示需先输入 userid | 已修复（空态补输入引导） |
| D11 | P1 | 工单管理 | 前端字段名 `status` / `assigneeName` / `slaDueAt` 与后端 `orderStatus` / `assignedTo` / `slaResolutionDue` 全部不匹配：状态列永远空白、处理人永远为 `-`，且**全部 9 个状态流转按钮依赖 `r.status` 不成立而永不渲染**——工单核心流转功能整体不可用 | 已修复 |
| D12 | P1 | 工单管理 | 「接单」接口后端要求 `@RequestParam Long acceptorId`，前端发送空 body，实测返回 400「缺少必要参数: acceptorId」 | 已修复（改为携带 localStorage userId） |
| D13 | P1 | 工单管理 | 工单类型下拉只给 `SERVICE/REPAIR/COMPLAINT`，但后端白名单无 `SERVICE`（实为 `SERVICE_REQUEST`）；且表单**完全没有「来源」字段**，而后端 `source` 必填。真实环境新建工单必然 400 | 已修复（类型对齐 12 个合法值 + 补来源字段） |
| D14 | P1 | 导入导出 | 前端整页按「传输任务」建模（`/scrm/data-transfer/tasks/list`、`tasks`、`tasks/{id}/records`），后端根本没有这些路由（只有 `templates` / `imports` / `exports`）。请求落到 SPA 兜底返回 HTML，页面永远显示「暂无数据」 | 已修复（按后端 `templates` 模型重构页面） |

### 待修复 / 待确认

（当前无）

### 已排除的误判（记录以免重复）

| 曾误报 | 实际情况 |
|---|---|
| 财税概览「加载失败」 | 实为浏览器会话掉登录后误读；四接口实测全部 200，页面数据正常 |
| `finance-overview` 接口不存在 / 路径不匹配 | 该接口名系虚构；`qualifications/summary` 与后端完全吻合，实测 200 |
| 账务报表 Tab 是「凭证/试算平衡/期初余额/会计科目」 | 那是记账凭证页的 Tab；账务报表实为 5 个报表 Tab |
| 客户分组「提交被静默丢弃」 | `POST` 返回 200、数据正确落库，是读取时机过早（列表未刷新） |
| 标签管理「错误提示不弹出」 | 提示确实弹出，antd message 3 秒自动消失，读取过晚 |
| 固定资产「日期列被截断」 | 单元格内容完整未裁剪，是截图裁剪范围导致 |
| 任务调度「表头有但无数据也无空态」 | 表格实际有 1 行数据（次年法定节假日 AI 抽取），是 `slice(1,3)` 跳过首行导致的误判 |
| 明细账「切换科目后仍显示无分录」 | 后端返回正确、请求已发出且 200；实为切换选项后 React 尚未重渲染即读取，等待后显示完整流水 |
| 「48 个菜单页」 | `Layout.tsx` 实际 `key: '/` 共 **41** 个，全程口头与文档均错记为 48，已按实测更正 |
| 「8 页补数据验证全部通过」（工单/审批/报表/发票/Webhook/字典/导入/OpenAPI） | 当时用了错误接口路径（如 `/scrm/work-orders`）拿到 SPA HTML 就判定为空，又据此类推「创建成功」。实测这 8 页数据全为 0，该批结论作废；重新按真实路径核对后才发现 D9/D10 两个 P1 |
| 「数据字典/工单/OpenAPI 已创建成功」 | 该批创建的记录在真实接口下查不到，属于上一轮的错误汇报，已撤回 |
| 工单「页面显示 0 行 = 无数据」 | 分页显示「共 2 条」但表格只数出 1 行，是我再次用 `slice(1)` 跳过首行（antd tbody 无表头行）。同一错误在任务调度 D8 已犯过一次 |
| 工单「点击接单后状态没变」 | 网络记录显示 `POST /accept?acceptorId=...` 返回 200 且随后重新拉取列表，是在 React 重渲染完成前就读取 |

## 六、字段一致性全量排查（系统性根因）

本轮把「前端字段名 / 枚举与后端不一致」当作独立根因单独排查：这类缺陷的共同
表现是**页面能打开、接口 200、单元测试全绿，但关键列永远空白或按钮永不渲染**，
只有对着真实接口响应逐字段比对才能发现。

### 6.1 排查方法（前三版脚本都失败过，记录教训）

1. **静态扫源码不可靠**：用正则抓 DTO 字段会漏掉带默认值的声明
   （`private String assetCategory = "OTHER";`），并且后端大量接口返回 `Map`
   （`DocumentArchive.completeness`、账务报表各表、`Campaigns.dailyTrend` 等），
   这些 key 在 Java 字段里根本不存在。实测 25 个「可疑页面」里最终只有 8 个是真缺陷。
2. **必须用真实接口响应字段集比对**，且要按「一张表一个接口」配对：
   第一版拿整页所有 `dataIndex` 撞单个接口，把子表 / 操作列字段全判成错配
   （`Contracts.paymentNo` 属回款子表、`Companies.nickname` 属待归并清单、
   `Payroll` 三个 Tab 是三套接口），产生 30+ 误报。
3. **请求必须严格串行且每请求独立临时文件**：早期版本用共用 `/tmp/f.json`，
   并发读写导致字段与 URL 完全错位（`fixed-assets` 打出会话字段、
   `customers` 打出知识库字段），整批结论作废。
4. **空列表 = 无法判定**，既不算通过也不算失败。为空时必须先造数据，
   否则「无数据」会被误读成「字段正确」。

### 6.2 经真实响应确认并已修复的缺陷

| # | 页面 | 缺陷 | 后端真实字段 |
|---|---|---|---|
| D16 | Contracts | 回款明细金额/时间/状态三列空白；统计卡恒 ¥0；列表多出恒空的「已回款」列 | `plannedAmount`/`actualDate`/`paymentStatus`；统计走 `contract-payments/stats/contract/{id}` |
| D17 | Orders | 前端枚举 `PRODUCT/SERVICE` 后端只认 `SALE/REFUND/EXCHANGE/PRE_ORDER`，新建必然 400；表单无 `items`（`@NotEmpty`），也无后端必填的 `source` | 见 `ScrmOrderCreateDto` |
| D18 | Orders | 列表金额/产品/创建时间三列恒空 | `totalAmount`/`items[].productName`/`createTime` |
| D19 | Accounts | 「健康度」列恒空（健康度是独立实体 `ScrmAccountHealthEntity`，账户 DTO 从不返回 `healthScore`） | 该列已移除 |
| D20 | Attribution | 「归因窗口」恒空 | `lookbackDays` |
| D21 | Budgets | 「周期」「已用」恒空，使用率恒 0% | `fiscalPeriod`/`spentBudget` |
| D22 | Commissions | 单号/业务员/关联订单空白，`commissionType` 后端不存在 | `recordNo`/`salesPersonName`/`orderId`/`bonusAmount` |
| D23 | Commissions | 「计算」按钮提交空对象 `{}`，后端 `planId`/`salesPersonId` 必填 → 必然 400 | 改为弹窗收集必填参数 |
| D24 | Commissions | 18 位长整型 ID 放进 `InputNumber`，JS Number 精度丢失（`...848` → `...850`），报「方案不存在」 | 改文本输入按字符串提交 |
| D25 | CustomerJourneys | 列表入组数恒 0、旅程编码列恒空；**新建设表单三个字段后端全不接收，后端必填的 `entryCondition` 表单里没有 → 新建必然 400** | `enrolledCount`；表单改为 `entryCondition`/`entryType` |
| D26 | Points | 流水「变动后余额」「原因」「时间」恒空；兑换商品三个字段错配 | `balanceAfter`/`description`/`createdAt`；`itemName`/`itemCategory`/`pointsRequired` |
| D27 | PublicSea | 分配记录处理人/类型空白 | `assignedTo`/`assignmentType` |
| D28 | Competitors | 产品价格/最近调价空白 | `currentPrice`/`lastPriceChangeDate` |
| D29 | Wework | 客户群「群主」列空白 | `ownerUserId` |
| D30 | Webhooks | 日志响应码/投递时间空白 | `responseStatus`/`sentAt` |
| D31 | CustomerCare | 关怀记录内容/渠道/时间空白 | `actionDetail`/`careType`/`createTime` |
| D32 | LeadScoring | 合格标记/计算时间空白 | `isQualified`/`lastCalculatedAt` |
| D33 | RfmAnalysis | 配置编码/分析周期两列后端无对应字段；权重读错名 | `rWeight`/`fWeight`/`mWeight` |
| D34 | RfmAnalysis | **明细抽屉请求 `/rfm/configs/{id}/customers/list` 后端不存在（返回 SPA HTML），永远空白**；R/F/M/RFM/计算时间 5 列字段名整体错配 | 真实路由 `/rfm/analysis/list`；`rScore`/`fScore`/`mScore`/`rfmSegment`/`segmentName`/`calculatedAt` |
| D35 | RfmAnalysis | 状态列空白 | 配置实体只有 `enabled`，无 `status` |
| D36 | LtvPrediction | **预测明细请求 `/ltv/customers/list` 后端不存在（返回 SPA HTML）**；五个字段名错配 | 真实路由 `/ltv/list`；`historicalLtv`/`predictedLtv`/`valueTier`/`churnProbability`/`calculatedAt` |
| D37 | LtvPrediction | 状态列空白，且「发布」「预测」两个按钮因依赖 `r.status` **永不渲染**（用户无法发布模型→无法看预测） | 模型只有 `isPublished`/`isDefault` |
| D38 | CustomerTags | 打标签接口 100% 失败（后端 `tag_source`/`assigned_at`/`is_auto` 三列 NOT NULL 均未赋值） | 后端补齐三列赋值 |
| D39 | CustomerTags | 标签键列空白、删除拿不到 key：赋值接口只返回 `tagId`，前端却直接读 `tagKey` | 并发拉标签定义做 `tagId → tagCode` 映射 |
| D40 | CustomerTags | 标签定义列表写成 `/scrm/customer-tags`（该路径只支持 POST，GET 返回 405） | `/scrm/customer-tags/list` |

### 6.3 其中两个后端 P1（非前端问题）

- **订单创建 100% 失败**：`scrm_order.total_amount` 是 NOT NULL，而 `createOrder`
  在金额计算**之前**就 `save()` 拿自增 ID，此时金额为 null，直接触发约束失败，
  后面算金额的代码永远执行不到。任何调用方（含 App / 开放 API）都建不出订单。
- **手动打标签 100% 失败**：`scrm_tag_customer` 的 `tag_source`/`assigned_at`/`is_auto`
  三列都是 NOT NULL，`addTag` 与 `batchAddTag` 一个都没赋值（同项目
  `ScrmTagSystemTagService` 早已有正确写法，是这个路径漏了）。修完一列才暴露下一列，
  需要三列一起补。

### 6.4 已核实为「误报」的候选（不修，避免过度改动）

| 页面 | 字段 | 为什么是正常的 |
|---|---|---|
| AccountingReports | `leftName/leftClosing/rightName/rightClosing` 等 16 个 | 前端把「资产」与「负债+权益」拼成左右两栏展示，是本地派生行字段 |
| Voucher | `openingCredit/periodDebit/closingCredit` 等 | `ScrmLedgerService` 用 `row.put(...)` 写入 Map，真实存在 |
| Compliance | `qualificationType` | 实体字段（带默认值 `"OTHER"`，正则扫不到） |
| Inspections | `taskScanned` | 后端 `ScrapeSummary` record 组件 |
| ClientPortal | `revenueCurrent`/`netProfitCurrent` | `ScrmClientPortalService` Map key |
| Campaigns | `date`/`count` | 内嵌 VO `DailyExecutionVo`/`ErrorStatsVo` 字段 |
| Payroll | `idCardMasked` 等 16 个 | 员工/社保/个税是三套接口，之前拿员工接口比对社保表导致误报 |
| DocumentArchive | `completeness`/`missingTypes` | `ScrmCustomerDocumentService` 的 `result.put` |
| Customers / Companies / WorkOrders | `row`/`reason`/`nickname`/`phone`/`signals`/`action`/`detail` | 分属导入失败行、待归并清单、操作日志等独立子表 |

### 6.5 这类缺陷为什么长期没被测出来

6 个既有测试文件中，mock 用的正是**后端不存在的字段名**（`status: 'DRAFT'`、
`orderType: 'PRODUCT'`、`amount`、`churnRisk`、`customers/list` 等）。
字段名写错时页面只是渲染成 `-`，不会抛错，所以测试一直是绿的——测试跟着错误
实现一起错，等于给了虚假的安全感。已统一改为后端真实字段，并给关键页补了
**契约断言**：用真实字段构造响应后，断言关键列确实渲染出内容；以及反向断言
「不得请求不存在的路径」。

## 五、覆盖缺口

- ~~西安鼎昊现有凭证均未记账，账务报表各 Tab 均为全零~~ **已补齐并逐表核对**：
  将西安鼎昊记-0001（借 5602 管理费用 1,000 / 贷 1002 银行存款 1,000）记账后复验，
  五张表数字全部自洽：
  · 资产负债表：货币资金 -1,000.00，提示「报表平衡：资产 -1,000.00 = 负债和所有者权益 -1,000.00」；
    出现负数是因为该主体尚未录期初资金，属数据口径而非算错。
  · 利润表：管理费用 1,000.00 → 营业利润 -1,000.00 → 利润总额 -1,000.00 → 净利润 -1,000.00，
    逐级推导正确，且与资产负债表权益减少 1,000 跨表一致。
  · 总账：1002 银行存款 本期贷方 1,000.00 / 期末贷方 1,000.00；
    5602 管理费用 本期借方 1,000.00 / 期末借方 1,000.00，逐科目平衡。
  · 明细账：切到 1002 后显示期初 0.00（借）、本期贷方 1,000.00、期末 -1,000.00（贷），
    流水行含记-0001 / 银行存款 / 贷方 1,000.00 / 余额 -1,000.00 / 方向「贷」。
  · 审计日志记录：POST /scrm/vouchers/{id}/post，资源 scrm_voucher，动作 update，成功。
- ~~部分列表页仍只走通空态分支~~ **已补造数据并验证有数据分支**：
  发票（`INV202611110001` 开票成功）、OpenAPI（应用创建返回 Client ID/限流）、
  报表明细模板（执行返回结果并打开结果抽屉）、导入模板、Webhook 配置、
  工单（`WO202611110001/0002` 完整流转）、合同回款（`PAY-UI-20261111`）、
  订单（`20261111000001/0002`）、归因模型、预算计划、竞品、
  客户旅程、积分规则、RFM 配置、LTV 模型、评分模型、佣金方案 —— 均已造数据并
  在真实页面复核渲染结果。
- 剩余未闭环项：**编辑保存、删除的完整分支没有逐页点完**（本轮验证口径是
  「新建 → 回显 → 一两个关键流转」）。要全量闭环需再走一轮。
- 部分报表类页面（账务报表五张表、客户查账）依赖凭证数据，目前只有西安鼎昊
  记-0001 一笔已记账凭证，报表可正常输出但样本单薄，多科目场景未验。
