# SCRM 销售外呼域 & 安卓 App —— 实现说明（2026-10-22）

> 本文档说明「销售端 App + 通话记录 + 录音 + 通讯录同步」的设计与实现位置。
> 与对话上下文中的约束对齐：
> - 不限制机型、不强制 MDM；
> - 录音 =「读系统自带录音文件 + 上传」，本机不支持则如实标注，不承诺 100%；
> - 通讯录保存 =「号码规范化 + 复用查重 + 批量原子写入」。

---

## 一、总体架构

```
销售 Android App (scrm-sales)
   ├─ 登录（/scrm/auth/login，JWT）
   ├─ 我的客户 = 分配给我的公海线索（/scrm/sales-app/leads）
   ├─ 点号拨打 → 写 pendingCall → 挂机后上报归属
   ├─ 通话记录同步（CallLog 增量 / TelephonyCallback 近似 / pendingCall）
   ├─ 录音扫描（MediaStore + SAF 双通道）→ 上传
   └─ 通讯录同步（去重建账户写入）

SCRM 后端（scrm-server）
   ├─ V6/V7 迁移：联系方式 / 通话记录 / 录音三表 + owner_type 维度
   ├─ /scrm/sales-app/** 外呼域控制器
   └─ 归属模型 (ownerType, ownerId)：CUSTOMER(正式客户) / PUBLIC_SEA(公海线索)
```

---

## 二、后端实现

### 2.1 数据表（Flyway V6 + V7）

| 表 | 用途 | 关键字段/约束 |
|---|---|---|
| `scrm_customer_contact` | 客户/线索联系方式 | `owner_type`(CUSTOMER/PUBLIC_SEA) + `customer_id` + `phone` 唯一 |
| `scrm_call_record` | 通话记录 | `user_id` + `device_id` + `call_log_id` 去重；`customer_type` 归属 |
| `scrm_recording` | 录音文件元数据 | `file_hash` 唯一幂等；文件本体在对象存储 |

### 2.2 接口（前缀 `/scrm/sales-app`）

| 方法/路径 | 说明 |
|---|---|
| `GET /leads` | 分配给我的公海线索（销售主数据源） |
| `GET /customers` | 按账号归属的正式客户（兼容路径） |
| `GET /owners/{ownerType}/{ownerId}` | 客户/线索详情（含联系方式） |
| `POST /owners/{ownerType}/{ownerId}/contacts` | 保存联系方式 |
| `GET /phone-lookup?phone=` | 号码归属匹配（撞单/来电识别） |
| `POST /calls/batch` | 批量上报通话（幂等 + 号码归属匹配） |
| `GET /calls?ownerType=&ownerId=` | 通话记录分页/按归属过滤 |
| `POST /calls/{id}/bind` | 通话关联归属 |
| `POST /recordings` | 录音上传（multipart，SHA-256 幂等） |
| `GET /recordings` / `/{id}/url` | 录音列表 / 预签名播放地址 |
| `GET /recordings/coverage` | 覆盖率 = 录音数/通话数 |
| `GET /recordings/capability` | 按 ROM 的录音能力指引 |

### 2.3 关键设计

- **归属模型**：`(ownerType, ownerId)`。公海线索是「我的客户」主数据源（`assigned_to = userId`），正式客户按账号归属。
- **通话去重**：`(deviceId, callLogId)` 精确去重；无 callLogId 时按「同设备+同号码哈希+5 分钟窗口」近似去重。
- **号码规范化**：去空格/横线/括号/国码，前后端同一套规则（各自有单测守护）。
- **录音幂等**：文件内容 SHA-256 唯一，重复上传返回既有记录。
- **录音能力指引**：按 ROM（MIUI/COLOROS/EMUI/VIVO/SAMSUNG/AOSP）返回「如何开启自动录音」。

### 2.4 验证结果（2026-10-22，真实 PG + NAS MinIO）

| 环节 | 结果 |
|---|---|
| Flyway V1–V7 迁移 | ✅ 7 条全部 success（V6 三表、V7 owner_type/customer_type） |
| `FlywayPostgresMigrationTest` | ✅ 6/6（断言已更新为 7 迁移 / 279 业务表 / 280 总表） |
| `ScrmSalesAppServiceTest` | ✅ 6/6（号码规范化 / SHA-256 / ROM 识别） |
| 登录 + `/leads` | ✅ roles 为数组；返回 2 条 ASSIGNED 公海线索 + 联系方式 |
| `/calls/batch` 幂等 | ✅ 首次 created=1，重放 duplicated=1 |
| `/phone-lookup` | ✅ `13800138000` → PUBLIC_SEA 线索 |
| 保存联系方式 | ✅ 主号码切换生效 |
| `/recordings` 上传 | ✅ 落 NAS MinIO `scrm-assets`/`scrm-it` 桶，预签名 URL 下载字节一致，重复上传幂等 |
| `/recordings/coverage` | ✅ calls=1 / recs=1 / rate=1.0 |
| `/recordings/capability` | ✅ rom=MIUI，4 步指引 |

> 复跑方式见「五、一键联调脚本」。

---

## 三、Android App（scrm-sales）

### 3.1 工程

- 位置：`/workspaces/scrm-sales`（独立工程，结构对齐 wechat-agent/android 模板）
- 包名：`com.cloudsstudio.sales`，compileSdk 34 / minSdk 26 / Kotlin 2.0.21 / AGP 8.7.3
- 构建：`./gradlew assembleDebug`（已产出 apk，~7MB）；单测 `testDebugUnitTest`（7 条全绿）

### 3.2 功能模块

| 模块 | 说明 |
|---|---|
| `net/ApiClient.kt` | JWT 登录 + 接口封装（含录音 multipart 上传） |
| `net/SalesSettings.kt` | 后端地址/令牌/设备 ID/游标/待归属通话/录音目录 URI |
| `contacts/ContactSyncManager.kt` | 通讯录同步：规范化 → 查重 → applyBatch 原子写入，结果回执 |
| `service/CallStateService.kt` | 通话状态监听（TelephonyCallback，普通权限），挂机触发录音扫描 |
| `service/CallLogSyncManager.kt` | CallLog 增量同步 + 无权限时上报 pendingCall |
| `service/PendingCallStore.kt` | App 内拨号的待归属记录（TTL 10 分钟） |
| `workers/RecordingScanWorker.kt` | MediaStore+SAF 双通道扫描，文件名解析号码，上传去重 |
| `ui/` | 登录页 / 我的线索 3 Tab / 客户详情（拨号+最近通话）/ 设置页（录音能力+目录授权+同步通讯录） |

### 3.3 权限（全部普通权限，无需 MDM）

`READ_PHONE_STATE`、`CALL_PHONE`、`READ/WRITE_CONTACTS`、`AUTHENTICATE_ACCOUNTS`、
`READ_MEDIA_AUDIO`(13+)/`READ_EXTERNAL_STORAGE`(≤12)、前台服务、`POST_NOTIFICATIONS`、开机自启。

`READ_CALL_LOG` 在 Manifest 声明但**不参与运行时申请**（hardRestricted，需安装器 allowlist）；
拿不到时自动降级：App 内拨号用 pendingCall 归属，通话时长走 TelephonyCallback 近似，如实展示。

### 3.4 验证

- `assembleDebug` ✅（编译 0 error，仅 2 个 unchecked cast 警告）
- `testDebugUnitTest` ✅ 10 条（`PhoneNormalizerTest` 5 + `PendingCallStoreTest` 5）

---

## 四、已知边界（已在设计上接受）

1. **录音覆盖率不承诺 100%**：取决于机型系统拨号器是否支持自动录音，不支持的如实标注 + 手动上传。
2. **精确话单（接通/未接/时长）依赖 READ_CALL_LOG**：BYOD 建议 adb 安装或 MDM 以 allowlist；否则降级近似。
3. **鸿蒙 NEXT 无法安装 Android APK**：需鸿蒙原生版（另一套 MDM Kit）。
4. **国产 ROM 后台保活**：前台服务 + 引导白名单，实测各机型。

---

## 五、一键联调脚本

脚本：`scripts/sales-app-smoke.sh`（后端 HTTP 全链路，无需真机）

```bash
# 完整跑一遍（自动构建 jar → 起独立 PG → 起独立后端 → 12 项断言 → 清理）
bash scripts/sales-app-smoke.sh

# 保留 PG 容器与后端进程，便于人工复查
bash scripts/sales-app-smoke.sh --keep
```

**环境隔离**：独立 PG 容器 `scrm-it-pg`（端口 54329）+ 独立后端端口 8841 + NAS MinIO 独立桶 `scrm-it`，
不触碰开发库（5432）与开发服务（8840）。

**对象存储凭据**：优先读环境变量 `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`；
缺省时自动从 `../framework/vision-service/.env` 读取（不回显、不写入仓库）。缺失则以退出码 2 报错。

**可覆盖的环境变量**：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SCRM_IT_PG_PORT` | 54329 | 测试 PG 宿主机端口 |
| `SCRM_IT_SRV_PORT` | 8841 | 测试后端端口 |
| `SCRM_ART_BASE` | `/opt/test-artifacts/scrm` | 产物与日志根目录 |
| `JAVA_BIN` | `/usr/lib/jvm/dragonwell-21/bin/java` | Java 可执行文件 |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | 见上 | 对象存储 |

**断言覆盖（12 项）**：健康检查 → 登录 → `/leads` 数量 → `/calls/batch` 新增 → 重放幂等 →
`/phone-lookup` → 保存联系方式 → 录音上传 → 预签名下载字节一致 → 重复上传幂等 → 覆盖率 → 能力指引。

**产物**：`/opt/test-artifacts/scrm/smoke/sales-app/<时间戳>/`（各接口 JSON、seed.sql、上传/下载的 wav）
与 `/opt/test-artifacts/scrm/logs/sales-app-smoke-<时间戳>.log`。

退出码：`0` 全绿 / `1` 断言失败 / `2` 环境缺失（缺 docker、凭据等）。

---

## 六、待真机验证（已登记 todo）

| 项目 | 阻塞 |
|---|---|
| 各 ROM 录音目录实测 + 文件名解析适配 | 目标机型真机 |
| `READ_CALL_LOG` 授权行为（adb allowlist） | 目标机型真机 |
| SAF 目录授权长期有效性 | 真机系统升级后 |
| 国产 ROM 后台存活 | 真机长时间 |
| App 端到端录音上传 | 真机 + 后端联调 |

> 后端联调已于 2026-10-22 在本机闭环（真实 PG + NAS MinIO），不再列为待验项。