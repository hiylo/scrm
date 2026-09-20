<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
Project : scrm
File : CONTRIBUTING.md
Date : 2026/09/20 00:00:00
Author : Hsi Chu
Contact : hiylo@live.com
-->

# 贡献指南

感谢贡献。请先阅读本指南再提交 Issue 或 PR。

## 提 Issue

- **Bug 报告**: 请包含最小复现步骤、期望行为、实际行为、环境 (OS / JDK / PostgreSQL 版本)、
  相关日志片段。
- **功能建议**: 请说明使用场景与替代方案。
- **安全问题**: 不要开公开 Issue, 走 [SECURITY.md](SECURITY.md) 的私密披露渠道。

## 提 PR

### 环境准备

- JDK 21+ (Spring Boot 4.0.7, `maven.compiler.release=21`)
- Maven 3.9+
- Node.js 20+ (前端)
- Docker (仅 `FlywayPostgresMigrationTest` 需要, 通过 Testcontainers 起 PostgreSQL 16)

### 分支与提交

- 从 `main` 切分支, 分支名建议 `feat/xxx`、`fix/xxx`、`docs/xxx`。
- 提交信息遵循 `<type>(<scope>): <subject>` 格式, 例如:
  `feat(scrm): 新增客户分群导出`, `fix(auth): 修复 JWT 过期刷新`。
- 一个 PR 聚焦一件事, 不混入无关改动; 避免 `git add -A`。

### 编码约定

- 包名: `org.hiylo.scrm.{module}`。
- 分层: `controller / service / impl / repository / entity / dto / vo / config /
  exception / constants / converter`。
- 实体统一含 `id` (雪花 ID, 应用层生成)、`createdAt`、`updatedAt`、`version` (乐观锁),
  由 `@PrePersist` / `@PreUpdate` 维护时间戳。
- 统一响应: 所有接口返回 `OperationResponse<T>`, 用 `build(data)` / `fail(code, msg)` 构造。
- 异常: 业务异常继承 `RuntimeException`, 错误码集中在 `ScrmExceptionConstants`。
- **禁止** Swagger / SpringDoc / OpenAPI / Knife4j 注解 (`@Tag` / `@Operation` / `@Schema`
  等); 只用 Javadoc。
- **禁止** SpotBugs 排除规则 / `@SuppressFBWarnings`; 每个告警都要修。
- 关键数值 (金额、库存、定价) 由 Java 代码精确计算, 不由 LLM 直接产出。
- 单文件目标 ≤ 1000 行, 硬上限 1500 行; 超长 service 已按门面 + 兄弟类拆分模式组织
  (门面保留全部 public 签名 + 一行委托, 兄弟类 `ScrmXxx{子域}Service`)。
- UTF-8 无 BOM, 行尾 LF, 4 空格缩进, 行宽 ≤ 120 字符, K&R 大括号。

### Java 文件头

新建或修改后的 Java 文件必须包含以下 Copyright 头 (逐文件手改, 不用脚本批量改):

```java
/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : {文件名}.java
 * Date : yyyy/MM/dd HH:mm:ss
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
```

- `Author` 统一 `Hsi Chu`, 不是 `hiylo`。
- `Date` 优先取文件首次提交时间; 新建未提交文件取当前时间。

### Javadoc 与注释

- public 类必须有中文 Javadoc: 核心职责 + `@author Hsi Chu` + `@since`。
- public / protected 方法必须有中文 Javadoc: 描述 + `@param` + `@return` + 必要时 `@throws`。
- 静态常量、关键字段、配置字段必须有注释, 说明含义、单位、范围或默认值。
- 工具类私有构造: `/** 工具类, 私有构造防止实例化 */`。
- Logger 字段: `/** 日志记录器 */`。

### 数据库迁移

- 使用 Flyway, 脚本位于 `src/main/resources/db/migration/`。
- 已有 V1 (初始化 275 张表 + 分区表) 与 V2 (pg_trgm 全文检索索引)。
- 新迁移必须用新版本号 (V3, V4, ...), **不要修改已发布的迁移脚本**。
- PostgreSQL 专有语法 (分区表、pg_trgm) 需在 `FlywayPostgresMigrationTest` 中回归。

### 测试

- 后端: JUnit 5 + Mockito + `@ExtendWith(MockitoExtension.class)` +
  `@MockitoSettings(strictness = Strictness.LENIENT)`。
- Spring 上下文测试 (`ScrmServerApplicationTest`) 用 H2 `MODE=PostgreSQL` +
  `ddl-auto: create-drop` 校验所有 JPA 派生查询名与 `@Query` 语句。
- PostgreSQL 专有迁移由 `FlywayPostgresMigrationTest` 通过 Testcontainers 回归,
  标注 `@Testcontainers(disabledWithoutDocker=true)`, 无 Docker 时静默跳过。
- 前端: Vitest + Testing Library, `npx tsc --noEmit` 做类型检查。

### 提交前自检

```bash
# 后端
export MAVEN_OPTS="-Xmx10g -Xms2g"
mvn test

# 前端
cd frontend
npx tsc --noEmit
npm test
npm run lint
npm run build
```

CI 会自动跑同样的命令; 本地跑通再推 PR 可以省一轮往返。

## License

本项目采用 MIT License。按惯例, 通过提交贡献即表示你同意你的贡献以 MIT License 发布。
