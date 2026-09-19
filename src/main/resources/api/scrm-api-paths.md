# SCRM Server API 路径

> 本文档汇总 scrm-server 所有 Controller 暴露的 HTTP 端点, 供前端对接与权限配置参考。
>
> - **统一响应格式**: `OperationResponse<T>` (status / code / message / data)
> - **鉴权方式**: 由 gateway-server 统一鉴权, `@RequirePermission` 仅作为端点权限元数据声明
> - **用户透传**: 请求头 `X-User-Id` (String 类型)
> - **回调接口**: `/scrm/callback/**` 为 scrm-server 内部调用, 不走网关鉴权, 可选校验 `X-Agent-Secret` 头

---

## 账号管理 (/scrm/accounts)

多平台社媒账号的增删改查、设备绑定、登录态维护与人设绑定。

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /scrm/accounts | 创建账号 | scrm_account:create |
| PUT | /scrm/accounts/{id} | 更新账号 | scrm_account:update |
| GET | /scrm/accounts/{id} | 查询账号详情 | scrm_account:read |
| GET | /scrm/accounts/by-platform | 按平台类型 + 平台账号 UID 查询账号 | scrm_account:read |
| GET | /scrm/accounts/by-device/{deviceId} | 按设备 ID 查询关联账号列表 | scrm_account:read |
| POST | /scrm/accounts/{accountId}/bind-device | 绑定设备 | scrm_account:update |
| POST | /scrm/accounts/{accountId}/unbind-device | 解绑设备 | scrm_account:update |
| PUT | /scrm/accounts/{accountId}/login-state | 更新账号登录态 (LOGIN/LOGOUT/FROZEN/UNKNOWN) | scrm_account:update |
| POST | /scrm/accounts/{accountId}/bind-persona | 绑定人设 (同步创建执行侧 Persona) | scrm_account:update |
| GET | /scrm/accounts/list | 分页查询账号 (支持 platformType / loginState 过滤) | scrm_account:read |
| GET | /scrm/accounts/{id}/login-logs | 分页查询账号登录日志 | scrm_account:read |

---

## 人设管理 (/scrm/personas)

人设业务字段的增删改查, 创建/绑定人设时同步通过 Feign 调 scrm-server 创建执行侧 Persona。

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /scrm/personas | 创建人设 | scrm_persona:create |
| PUT | /scrm/personas/{personaId} | 更新人设业务字段 | scrm_persona:update |
| GET | /scrm/personas/{personaId} | 查询人设详情 | scrm_persona:read |
| GET | /scrm/personas/by-account/{accountId} | 按账号 ID 查询人设列表 | scrm_persona:read |
| DELETE | /scrm/personas/{personaId} | 删除人设 | scrm_persona:delete |
| GET | /scrm/personas/list | 分页查询人设 | scrm_persona:read |

---

## 客户管理 (/scrm/customers)

客户档案维护、标签管理、分组管理与生命周期阶段切换。

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /scrm/customers | 创建客户 | scrm_customer:create |
| PUT | /scrm/customers/{id} | 更新客户 | scrm_customer:update |
| GET | /scrm/customers/{id} | 查询客户详情 | scrm_customer:read |
| GET | /scrm/customers/by-platform | 按平台类型 + 平台客户 UID + 归属账号 ID 查询客户 | scrm_customer:read |
| GET | /scrm/customers/by-owner/{ownerAccountId} | 按归属账号分页查询客户 | scrm_customer:read |
| POST | /scrm/customers/{id}/tags | 为客户打标签 (相同 tagKey 覆盖) | scrm_customer:update |
| DELETE | /scrm/customers/{id}/tags/{tagKey} | 删除客户标签 | scrm_customer:update |
| GET | /scrm/customers/{id}/tags | 查询客户所有标签 | scrm_customer:read |
| POST | /scrm/customers/groups | 创建客户分组 | scrm_customer_group:create |
| GET | /scrm/customers/groups | 查询分组列表 (按归属账号过滤) | scrm_customer_group:read |
| GET | /scrm/customers/groups/{groupId}/members | 查询分组成员列表 | scrm_customer_group:read |
| POST | /scrm/customers/groups/{groupId}/members/{customerId} | 将客户加入分组 | scrm_customer_group:update |
| DELETE | /scrm/customers/groups/{groupId}/members/{customerId} | 将客户移出分组 | scrm_customer_group:update |
| DELETE | /scrm/customers/groups/{groupId} | 删除分组 (同时清理成员关联) | scrm_customer_group:delete |
| POST | /scrm/customers/groups/{groupId}/customers/{customerId} | 将客户加入分组 (兼容路径) | scrm_customer:update |
| PUT | /scrm/customers/{id}/lifecycle | 更新客户生命周期阶段 (NEW/ACTIVE/DORMANT/LOST) | scrm_customer:update |
| GET | /scrm/customers/list | 分页查询客户列表 (支持平台类型 + 生命周期过滤) | scrm_customer:read |

---

## 营销任务 (/scrm/campaigns)

营销任务的创建、更新、查询、生命周期管理 (启动/暂停/恢复/停止)、账号分配与 SOP 模板。

### 任务管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /scrm/campaigns | 创建营销任务 | scrm_campaign:create |
| PUT | /scrm/campaigns/{id} | 更新营销任务 | scrm_campaign:update |
| GET | /scrm/campaigns/{id} | 查询营销任务详情 | scrm_campaign:read |
| POST | /scrm/campaigns/{id}/start | 启动营销任务 (创建行为流并执行) | scrm_campaign:execute |
| POST | /scrm/campaigns/{id}/pause | 暂停营销任务 (状态置 PAUSED) | scrm_campaign:execute |
| POST | /scrm/campaigns/{id}/resume | 恢复营销任务 (状态置 RUNNING) | scrm_campaign:execute |
| POST | /scrm/campaigns/{id}/stop | 停止营销任务 (状态置 COMPLETED) | scrm_campaign:execute |
| POST | /scrm/campaigns/{id}/accounts | 批量分配账号到营销任务 | scrm_campaign:update |
| GET | /scrm/campaigns/list | 分页查询营销任务 (支持 status / platformType 过滤) | scrm_campaign:read |

### SOP 模板

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /scrm/campaigns/templates | 创建 SOP 模板 | scrm_campaign_template:create |
| GET | /scrm/campaigns/templates/list | 分页查询 SOP 模板 (支持 campaignType / platformType 过滤) | scrm_campaign_template:read |
| GET | /scrm/campaigns/templates/{id} | 查询 SOP 模板详情 | scrm_campaign_template:read |
| PUT | /scrm/campaigns/templates/{id} | 更新 SOP 模板 | scrm_campaign_template:update |
| DELETE | /scrm/campaigns/templates/{id} | 删除 SOP 模板 | scrm_campaign_template:delete |

---

## 会话存档 (/scrm/conversations)

会话与消息的增删查接口, 媒体上传通过 ConversationMediaService 单独提供。

### 会话管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /scrm/conversations | 创建会话 | conversation:write |
| GET | /scrm/conversations/{id} | 查询会话详情 | conversation:read |
| GET | /scrm/conversations/by-platform | 按平台会话 ID 查询会话 | conversation:read |
| GET | /scrm/conversations/by-account/{accountId} | 按账号分页查询会话列表 | conversation:read |
| GET | /scrm/conversations/by-customer/{customerId} | 按客户分页查询会话列表 | conversation:read |
| GET | /scrm/conversations/list | 按时间范围分页查询会话列表 | conversation:read |
| GET | /scrm/conversations/{id}/export | 导出会话消息 (支持时间范围过滤) | scrm_conversation:read |

### 消息管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /scrm/conversations/{id}/messages | 分页查询会话消息 (按发送时间倒序) | message:read |
| GET | /scrm/conversations/{id}/messages/search | 搜索会话消息 (LIKE 关键字) | message:read |
| GET | /scrm/conversations/messages/{messageId}/media | 获取消息媒体预签名 URL | message:read |
| POST | /scrm/conversations/messages | 手动保存消息 | message:write |

---

## 数据看板 (/scrm/dashboard)

账号 / 任务 / 客户 / 会话 / 风控 / 综合 6 个看板聚合接口。

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /scrm/dashboard/accounts | 账号概览 | scrm_dashboard:read |
| GET | /scrm/dashboard/campaigns | 任务概览 | scrm_dashboard:read |
| GET | /scrm/dashboard/customers | 客户概览 | scrm_dashboard:read |
| GET | /scrm/dashboard/conversations | 会话概览 | scrm_dashboard:read |
| GET | /scrm/dashboard/risk | 风控概览 | scrm_dashboard:read |
| GET | /scrm/dashboard/overview | 综合概览 (聚合全部维度) | scrm_dashboard:read |

---

## 平台管理 (/scrm/platforms)

平台可用性查询、平台配置获取与账号信息同步。

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /scrm/platforms/available | 获取可用平台列表 | scrm_platform:read |
| GET | /scrm/platforms/{platformType}/config | 获取平台配置 | scrm_platform:read |
| POST | /scrm/platforms/accounts/sync | 同步平台账号信息 (拉取昵称/头像更新到本地) | scrm_platform:update |

---

## 回调接口 (/scrm/callback)

scrm-server 内部回调, **不要求网关鉴权**, 通过 `X-Agent-Secret` 头校验调用方身份 (fail-closed: 密钥配置项 `scrm.callback.agent-secret` / 环境变量 `SCRM_CALLBACK_AGENT_SECRET` 为空时一律拒绝回调并返回 401)。

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /scrm/callback/task-status | 任务状态变更回调 (SUCCESS→COMPLETED / FAILED→FAILED) | 内部调用,无需权限 |
| POST | /scrm/callback/risk-signal | 风控信号回调 (持久化到 scrm_risk_signal 表) | 内部调用,无需权限 |
| POST | /scrm/callback/conversation-event | 会话事件回调 (消息收发, 按 platformMessageId 去重) | 内部调用,无需权限 |
