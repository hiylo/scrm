<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
Project : scrm
File : SECURITY.md
Date : 2026/09/20 00:00:00
Author : Hsi Chu
Contact : hiylo@live.com
-->

# 安全策略

本项目重视安全。若发现漏洞, 请优先使用「私密披露」渠道, 不要在公开 Issue 中暴露细节。

## 披露渠道

**推荐**: 通过 GitHub 的 [Private Vulnerability Reporting](https://docs.github.com/en/code-security/security-advisories/working-with-repository-security-advisories) 提交
(仓库设置中开启即可用: Settings → Security → Secret scanning → Private vulnerability reporting)。

**备用**: 邮件 `hiylo@live.com`, 主题加 `[SCRM] security:` 前缀。

## 支持版本

| 版本 | 是否接收安全修复 |
| --- | --- |
| 1.0.x (当前) | 是 |
| < 1.0.0 | 否 |

## 响应时间

- 初步确认: 3 个工作日内
- 修复版本: 视严重性, 高严重性目标 7 天内, 中等 30 天内

## 项目已实现的安全基线

以下能力是仓库内已编码实现的, 提漏洞前请先确认绕过路径:

- **启动期 fail-closed 校验** (三个校验点, 生产 profile 命中即拒绝启动):
  - `JwtTokenProvider.init()`: `SCRM_JWT_SECRET` UTF-8 字节数 < 32 抛 `WeakKeyException`;
    命中内置 `scrm-dev-only-` 前缀且非 dev/local/test profile 抛 `IllegalStateException`。
  - `EncryptionKeyStartupValidator`: `SCRM_ACCOUNT_ENCRYPTION_KEY` 为空或非合法 Base64
    / 解码后长度不是 16/24/32 字节。
  - `CallbackSecretStartupValidator`: `SCRM_CALLBACK_AGENT_SECRET` 为空。
- **回调接口共享密钥**: `/scrm/callback/**`、`/scrm/webhooks/**` 需要 `X-Agent-Secret`
  头匹配 `SCRM_CALLBACK_AGENT_SECRET`; 未配置时 dev profile 告警, prod 拒绝启动。
- **敏感字段加密**: `AesGcmUtils` 用 AES-GCM 对账号凭据落库加密, 由
  `SCRM_ACCOUNT_ENCRYPTION_KEY` 派生; 该密钥为空时 dev profile 明文落库并告警。
- **对象存储路径穿越防护**: `ObjectStorage` 抽象层对 `objectKey` 做规范化校验,
  拒绝 `..` 与绝对路径 (已修 objectKey 路径穿越)。
- **SSRF 防护**: `UrlSecurityUtils.validatePublicHttpUrl` 对通知中心 webhook 的
  目标 URL 做白名单/公共地址校验。
- **企业微信回调 IP 白名单**: `WEWORK_CALLBACK_ALLOWED_IPS` 非空时校验来源 IP。
- **限流**: Bucket4j 对敏感接口限流。
- **无硬编码密钥**: 全部配置项通过环境变量注入, 仓库内不含任何真实密钥;
  `.env.example` 只放「故意不可用于生产」的占位符。

## 报告范围

- 认证/授权绕过、JWT 伪造、越权访问
- 反射/反序列化/RCE/SQL 注入
- 敏感信息泄露 (日志、错误响应、堆栈)
- 路径穿越、SSRF、开放重定向
- 依赖组件已知漏洞 (CVE)
- 加密/密钥管理缺陷

## 不受理

- 需要已获得管理员权限的攻击链
- 理论上的、无实际可利用性的配置建议
- 第三方平台 (企业微信、MinIO、PostgreSQL 等) 自身的漏洞 —— 请向其官方渠道报告
