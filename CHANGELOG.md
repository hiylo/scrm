<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
Project : scrm
File : CHANGELOG.md
Date : 2026/09/20 00:00:00
Author : Hsi Chu
Contact : hiylo@live.com
-->

# Changelog

本文件记录 SCRM Server 的正式版本变更。遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)
与 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

格式约定:
- `Added` 新增能力
- `Changed` 行为或接口变化
- `Fixed` 缺陷修复
- `Security` 安全相关变更

## [1.0.0] - 2026-09-20

首个正式发布版本。企业微信场景下的 SCRM 单体应用, 包含全部业务模块、前后端完整实现、
CI 与容器化部署能力。

### Added

- **后端单体**: Spring Boot 4.0.7 (Java 21 虚拟线程), 1409 个 Java 源文件, 259 实体 /
  260 仓储 / 296 服务 / 103 控制器 / 392 DTO, 275 张业务表
  (Flyway V1 初始化 + V2 pg_trgm 全文检索索引, 含 RANGE 分区表)。
- **业务模块** (~30 个子域, 每个以门面 + 兄弟类拆分组织):
  - 客户 360 视图、身份识别与合并
  - 会话与消息分析、互动日历、参与度评分
  - 内容生成、AI 辅助、话术推荐、知识库
  - 营销自动化、活动分析、归因分析、线索打分
  - 工单协作、工作流、审批、合同、佣金、产品订单
  - NPS 调研、关怀任务、积分、营销日历、任务调度
  - 消息模板中心、通知中心、资产库、推荐、自动回复
  - 风险信号、报表、系统配置
- **前端 SPA**: React 18 + TypeScript 5 + Vite 5 + Ant Design 5 + ECharts 5,
  261 个测试用例 (15 个测试文件)。
- **对象存储抽象** `ObjectStorage`: 由 `SCRM_STORAGE_PROVIDER` 在 MinIO (S3 兼容) /
  阿里云 OSS 间切换; 端点为空时 fail-closed, 相关接口快速失败而非静默降级;
  含 `/scrm/media` 上传 / 下载 / 预签名 / 删除接口, objectKey 路径穿越防护。
- **认证与安全**: JWT (jjwt 0.12) + Spring Security, Jasypt 配置加密,
  AES-GCM 敏感字段加密, Bucket4j 限流, SSRF 校验
  (`UrlSecurityUtils.validatePublicHttpUrl`), 企业微信回调 IP 白名单。
- **启动期 fail-closed 校验**: `JwtTokenProvider` (密钥长度 + dev 前缀)、
  `EncryptionKeyStartupValidator` (AES 密钥 Base64 与长度)、
  `CallbackSecretStartupValidator` (回调共享密钥), 生产 profile 命中即拒绝启动。
- **可观测性**: Actuator + Micrometer Prometheus (`/actuator/prometheus`),
  Logstash 结构化 JSON 日志, Sentry 异常上报 (可选)。
- **集成**: 企业微信开放 API (WebClient, 支持 mock 模式)、个推 (可选, NoOp 实现)、
  AI 服务 (可选)。
- **容器化**: 后端多阶段构建 `Dockerfile` + `docker-compose.yml`
  (postgres + minio + app + web 一键编排), 前端 Nginx 反向代理部署。
- **CI**: GitHub Actions 双 job (后端 `mvn test` + 前端 tsc/test/lint/build),
  Testcontainers 起真实 PostgreSQL 16 回归 Flyway 迁移。
- **文档**: README (技术栈 / 快速开始 / 本地开发 / 测试 / 配置项 / 设计约定)、
  SECURITY (安全策略与已实现基线)、CONTRIBUTING (编码与提交流程)、CHANGELOG (本文件)、
  `.github/` (CI 工作流 / CODEOWNERS / PR 模板)、`.env.example` (配置模板)。

### Changed

- 单体架构: 无网关 / 无微服务拆分 / 无多租户概念, 单进程部署。
- 全量配置通过环境变量注入, 仓库内不含任何真实密钥; `.env.example` 只放「故意
  不可用于生产」的占位符, prod profile 由启动校验阻断错误配置。

### Fixed

- Flyway V1 语法错误与 27 条重复语句 (在真实 PostgreSQL 16 集成阶段修复)。
- `scrm_user` 表整个缺失、实体-表漂移 8 处。
- 动态 SQL 空 WHERE / 关键字粘连、报表空条件。
- 失败记录写 NULL 吞真实错误。
- objectKey 路径穿越 (对象存储上传/下载)。

### Security

- 启动期三个 fail-closed 校验 (见上方 Added)。
- JWT 密钥强度: UTF-8 字节数 ≥ 32, 命中 `scrm-dev-only-` 前缀在非 dev profile 阻断启动。
- 对象存储路径穿越防护 (objectKey 规范化校验, 拒绝 `..` 与绝对路径)。
- 通知中心 webhook SSRF 校验。
- 企业微信回调 IP 白名单 (`WEWORK_CALLBACK_ALLOWED_IPS`)。
- 敏感字段 AES-GCM 加密 (`SCRM_ACCOUNT_ENCRYPTION_KEY`)。
- 回调接口共享密钥 (`SCRM_CALLBACK_AGENT_SECRET`, `X-Agent-Secret` 头)。
- 全量配置外部化, 零硬编码密钥。

---

## 未发布 (Unreleased)

后续版本在此追加。
