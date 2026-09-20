<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
Project : scrm
File : PULL_REQUEST_TEMPLATE.md
Date : 2026/09/20 00:00:00
Author : Hsi Chu
Contact : hiylo@live.com
-->

## 变更内容

<!-- 一句话说清这次改了什么, 解决什么问题 -->

## 变更类型

- [ ] feat 新功能
- [ ] fix 缺陷修复
- [ ] refactor 重构 (无行为变化)
- [ ] docs 文档
- [ ] test 测试
- [ ] chore 构建/工具/依赖

## 验证

<!-- 列出本次改动跑过的验证命令与结果 -->

- [ ] 后端 `mvn test` 全绿
- [ ] 前端 `npx tsc --noEmit` 通过
- [ ] 前端 `npm test` 全绿
- [ ] 前端 `npm run lint` 0 error
- [ ] 前端 `npm run build` 成功

## 影响面

<!-- 是否改动 API 契约 / 数据库 schema / 配置项? 迁移脚本是否已加 Flyway 版本? -->

## 自检

- [ ] 无硬编码密钥 / 连接串
- [ ] 无未使用的 import / 死代码
- [ ] 新增 public 方法有中文 Javadoc
- [ ] 新增 Flyway 迁移版本号在 V1 之后
- [ ] 若涉及 CI 相关配置, 已确认 `.github/workflows/ci.yml` 与 README 一致
