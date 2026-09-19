# SCRM Server

企业微信场景下的 SCRM (Social Customer Relationship Management) 单体应用 —— 客户 360 视图、
会话与消息分析、内容生成、营销自动化、风险信号、AI 辅助与工单协作。

- 单进程单体, 无网关 / 无微服务拆分, 无多租户概念
- 后端 Spring Boot 4 单体 JAR, 前端 React SPA, 一键 `docker compose up` 即可运行
- 全量功能自带单元测试, 主分支持续保持 `mvn test` 全绿

## 技术栈

| 层 | 技术 |
| --- | --- |
| 运行时 | Java 21 (虚拟线程), Spring Boot 4.0.7, Spring Framework 7.0.9 |
| 持久层 | Spring Data JPA + Hibernate + Flyway, PostgreSQL 16 (测试用 H2) |
| 限流 | Bucket4j |
| 对象存储 | MinIO (S3 兼容) / 阿里云 OSS, 由 `SCRM_STORAGE_PROVIDER` 切换 |
| 认证 | JWT (jjwt 0.12) + Spring Security, Jasypt 配置加密 |
| 可观测性 | Actuator + Micrometer Prometheus, Logstash 结构化日志, Sentry |
| 集成 | 企业微信开放 API (WebClient), 个推 (可选), AI 服务 (可选) |
| 前端 | React 18 + TypeScript 5 + Vite 5 + Ant Design 5 + ECharts 5 |

规模: 1409 个 Java 源文件 (src/main), 259 实体 / 260 仓储 / 295 服务 / 103 控制器 /
385 DTO, 275 张业务表 (Flyway V1 初始化 + V2 全文检索索引), 1625 个后端测试用例 +
261 个前端测试用例。

## 目录结构

```
├── src/main/java/org/hiylo/scrm/   后端源码 (controller/service/repository/entity/...)
├── src/main/resources/
│   ├── application.yml             主配置 (全部通过环境变量注入, 无硬编码密钥)
│   ├── db/migration/V1__init_schema.sql   Flyway 初始化脚本 (含分区表)
│   ├── api/                        API 路径文档 (Markdown)
│   └── logback-spring.xml
├── src/test/java/                  单元测试 + Spring 上下文启动测试
├── frontend/                       前端 SPA (React + Vite)
├── Dockerfile                      后端多阶段构建镜像
├── docker-compose.yml              postgres + minio + app + web 一键编排
└── LICENSE                         MIT
```

## 快速开始 (Docker)

```bash
git clone https://github.com/hiylo/scrm.git && cd scrm
docker compose up -d --build
```

| 服务 | 地址 |
| --- | --- |
| 前端 (含反向代理) | http://localhost:3002 |
| 后端 API | http://localhost:8840 |
| 健康检查 | http://localhost:8840/actuator/health |
| Prometheus 指标 | http://localhost:8840/actuator/prometheus |
| PostgreSQL | localhost:5432 (scrm / scrm) |
| MinIO 控制台 | http://localhost:9001 |

首次启动由 Flyway 自动建库建表 (schema `scrm`), 无需手工执行 SQL。

> `docker-compose.yml` 默认以 `dev` profile 运行 (企微 mock、AI 关闭、口令均为占位值)。
> 生产部署须传入 `SPRING_PROFILES_ACTIVE=prod` 并修改全部占位口令
> (`POSTGRES_PASSWORD`、`SCRM_JWT_SECRET`、`MINIO_ROOT_PASSWORD`、
> `SCRM_ACCOUNT_ENCRYPTION_KEY`、`SCRM_CALLBACK_AGENT_SECRET`), 建议通过 `.env` 文件注入
> (模板见仓库根 `.env.example`, `cp .env.example .env` 后改值, compose 会自动读取)。
> 仓库内不包含任何真实密钥。注意 prod 下 `SCRM_JWT_SECRET` 仍为开发占位值、或
> `SCRM_CALLBACK_AGENT_SECRET` 为空时, 启动会被 fail-closed 校验直接阻断。
> `SCRM_ACCOUNT_ENCRYPTION_KEY` 必须是合法 Base64 且解码后 16/24/32 字节
> (`openssl rand -base64 32`), 格式不对会在加密字段写入时抛异常。

## 本地开发

### 后端

```bash
# 依赖: JDK 21, Maven 3.9+, PostgreSQL 16
export SPRING_DATASOURCE_PASSWORD='your-password'
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

数据库连接 / 各集成的默认值见 `src/main/resources/application.yml`,
均可通过环境变量覆盖 (命名规则: 配置项大写 + `_` 分隔)。

### 前端

```bash
cd frontend
npm ci
npm run dev        # http://localhost:3001
```

开发模式下前端默认同源调用 API; 需要直连后端时, 新建 `frontend/.env` 并设置
`VITE_API_BASE_URL=http://localhost:8840` 与 `VITE_SCRM_WS_HOST=localhost:8840`。
`frontend/.env` 不入库 (`.gitignore`), 且 `frontend/.dockerignore` 刻意不把它打进镜像 ——
容器内始终回落到 `window.location.host`, 所以反代部署无需重新构建前端。

### 构建产物

```bash
mvn clean package -DskipTests            # 后端 jar -> target/scrm-server-1.0.0.jar
cd frontend && npm run build             # 前端静态资源 -> frontend/dist
```

镜像构建上下文由根 `/.dockerignore` 与 `frontend/.dockerignore` 收窄: 后端只送 `pom.xml` + `src/`
(约 17 MB, 不排除的话 `target/` 与 `frontend/node_modules/` 会把上下文顶到 484 MB), 前端只送
`src/` + 配置 (约 750 KB)。前端那份尤其关键 —— `frontend/Dockerfile` 先 `npm ci` 再 `COPY . .`,
不排除就会用宿主机的 glibc `node_modules` 覆盖容器里 alpine/musl 版本的那份。

## 测试

```bash
export MAVEN_OPTS="-Xmx10g -Xms2g"
mvn test                                   # 全量 1625 用例 (需 Docker, 见下)
mvn test -Dtest=ScrmServerApplicationTest # Spring 上下文装配 + 全部 @Query 解析
mvn test -Dtest='!FlywayPostgresMigrationTest'  # 无 Docker 时跳过容器化迁移测试

cd frontend
npx tsc --noEmit                          # 前端类型检查
npm test                                  # vitest 全量 261 用例 (15 个测试文件)
npm run lint                              # eslint 扁平配置, 退出码可作增量门禁
```

`npm run lint` 目前以 0 error / 20 warning 通过: `no-explicit-any` (11 处) 与 `no-unused-vars`
(5 处) 按 warning 放行, 其中 3 处 warning 是真实死代码而非风格问题 —— `Layout.tsx` 算了
`btnWithBadge` 却始终 `return btn` (侧边栏未读红点不会出现), `Accounts.tsx` 的账号详情弹窗
既无 `setDetailOpen(true)` 也无 `setDetailAccount(...)`, 弹窗内容永远渲染不出来。

`ScrmServerApplicationTest.contextLoads` 会完整装配 Spring 上下文 (H2 `MODE=PostgreSQL` +
Hibernate `ddl-auto: create-drop`), 因此所有 JPA 派生查询名与 `@Query` 语句在测试阶段即可被校验。

上下文测试本身 `spring.flyway.enabled=false`, 不执行 `db/migration` 下的脚本; V1 的分区表与
V2 的 pg_trgm 索引属 PostgreSQL 专有语法, 由 `FlywayPostgresMigrationTest` 通过 Testcontainers
起真实 PostgreSQL 16 来回归 (迁移成功、275 张表、父索引有效、13 个叶子分区全部 ATTACH、
昵称模糊查询可命中 trigram 索引)。该类标注 `@Testcontainers(disabledWithoutDocker=true)`:
**没有 Docker 的执行机会静默跳过它而不是失败**, 因此跑迁移回归的节点需要可用的 Docker daemon。

## 关键配置项

| 环境变量 | 说明 | 默认 |
| --- | --- | --- |
| `SERVER_PORT` | 服务端口 | 8840 |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | 数据源 | localhost:5432/scrm |
| `SCRM_JWT_SECRET` | JWT 签名密钥 | 开发占位值, 生产必填 |
| `SCRM_CORS_ALLOWED_ORIGINS` | CORS 白名单 (逗号分隔) | http://localhost:3002 |
| `SCRM_ACCOUNT_ENCRYPTION_KEY` | 平台账号凭据加密密钥 | 空 |
| `SCRM_CALLBACK_AGENT_SECRET` | 回调接口共享密钥, 空则拒绝所有回调 | 空 |
| `SCRM_STORAGE_PROVIDER` | 对象存储实现: `minio` 或 `oss` | minio |
| `SCRM_STORAGE_ENDPOINT` | 对象存储端点 (MinIO: `http://host:9000`; OSS: `oss-cn-xxx.aliyuncs.com`) | localhost:9000 |
| `SCRM_STORAGE_ACCESS_KEY` / `SCRM_STORAGE_SECRET_KEY` | 对象存储访问凭证 | 空 |
| `SCRM_STORAGE_BUCKET` | 默认 bucket | scrm-assets |
| `SCRM_STORAGE_SECURE` | MinIO 端点未带 scheme 时是否补 `https://` | false |
| `SCRM_STORAGE_PRESIGN_EXPIRY_SECONDS` | 预签名 URL 默认有效期 (秒) | 3600 |
| `SCRM_AI_ENABLED` / `SCRM_AI_BASE_URL` / `SCRM_AI_MODEL` | AI 辅助 | false |
| `WEWORK_CORP_ID` / `WEWORK_AGENT_ID` / `WEWORK_SECRET` | 企业微信凭证 | mock 值 |
| `WEWORK_MOCK_MODE` / `WEWORK_REAL_API_ENABLED` | 企微 mock 开关 | true / false |
| `WEWORK_CALLBACK_ALLOWED_IPS` | 企微回调 IP 白名单 (空则跳过校验) | 空 |
| `GETUI_ENABLED` / `GETUI_APP_KEY` / `GETUI_MASTER_SECRET` | 个推推送 | false (NoOp 实现) |
| `SENTRY_DSN` | 异常上报 | 空 (不上报) |
| `JASYPT_ENCRYPTOR_PASSWORD` | 配置加密 master 密钥 | 空 |
| `SEQUENCE_WORKER_ID` / `SEQUENCE_DATACENTER_ID` | 雪花 ID 分片 | 1 / 1 |

## 设计约定

- **统一响应**: 所有接口返回 `OperationResponse<T>`, 通过 `build(data)` / `fail(code, msg)` 构造。
- **异常体系**: 业务异常继承 `RuntimeException`, 错误码集中在 `ScrmExceptionConstants`。
- **实体**: 统一含 `id` (雪花 ID, 应用层生成) / `createdAt` / `updatedAt` / `version` (乐观锁),
  由 `@PrePersist` / `@PreUpdate` 维护时间戳。
- **关键数值**: 金额、库存、定价等由 Java 代码精确计算, 不由 LLM 直接产出。
- **接口文档**: 不使用 Swagger / OpenAPI 注解, 仅使用 Javadoc。
- **对象存储**: 会话媒体通过 `ObjectStorage` 抽象访问, 由 `SCRM_STORAGE_PROVIDER` 在
  MinIO / 阿里云 OSS 间切换; 端点为空时存储不可用, 相关接口快速失败而非静默降级。

## License

MIT — 详见 [LICENSE](LICENSE)。
