-- ======================================================================
-- 财税数字化 - 模块① 工商税务巡检域（V8）
-- ======================================================================
-- 用途: 对存量/在服务客户企业自动巡检工商与税务状态, 异常主动告警。
--   1. scrm_inspection_task: 企业 + 周期巡检任务配置。
--      - enterprise_name / credit_code(统一社会信用代码) 是第三方工商 API 查询主键;
--      - customer_id 可空: 与 scrm_customer 关联 (自有/委托客户), 为空表示未建档但已授权巡检;
--      - cycle_type 决定调度频率: DAILY / WEEKLY / QUARTERLY / MANUAL (默认 QUARTERLY,
--        对应立项方案 500 家 × 每季度 1 次全量巡检口径);
--      - inspect_business=API(工商第三方 API) / inspect_tax=SCRAPE(公开公告抓取);
--      - 调度本身复用 scrm_scheduled_task (TaskScheduler), 本表登记巡检维度的
--        企业/周期/开关/上次执行游标。
--   2. scrm_inspection_snapshot: 巡检结果快照。
--      - 同一企业同一维度 (business / tax) 只保留最新一条 (is_latest=true, 服务层维护);
--      - state_hash = SHA-256(结构化状态 JSON), 状态哈希比对「变化才告警」;
--      - 首次入库 (无旧快照) 视为基线, 不告警; 之后哈希不一致才告警 (服务层保证);
--      - detail_json 保留字段明细供告警与人工复核 (含欠税金额/所属期/公告机关/日期等,
--        证件号码按项目红线脱敏后落库)。
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

-- 1. 巡检任务配置
CREATE TABLE scrm.scrm_inspection_task (
    id                  bigint NOT NULL,
    create_time         timestamp without time zone NOT NULL,
    update_time         timestamp without time zone NOT NULL,
    version             bigint DEFAULT 0 NOT NULL,
    customer_id         bigint,
    enterprise_name     character varying(200) NOT NULL,
    credit_code         character varying(32),
    legal_person        character varying(100),
    owner_account_id    bigint,
    cycle_type          character varying(20) DEFAULT 'QUARTERLY' NOT NULL,
    inspect_business    boolean DEFAULT true NOT NULL,
    inspect_tax         boolean DEFAULT true NOT NULL,
    is_enabled          boolean DEFAULT true NOT NULL,
    last_business_run_at timestamp without time zone,
    last_tax_run_at     timestamp without time zone,
    last_run_at         timestamp without time zone,
    last_result         character varying(20),
    last_error_message  character varying(1000),
    run_count           integer DEFAULT 0 NOT NULL,
    alert_count         integer DEFAULT 0 NOT NULL,
    remark              character varying(500)
);
COMMENT ON TABLE scrm.scrm_inspection_task IS '工商税务巡检任务: 企业 + 周期任务配置, 调度复用 scrm_scheduled_task, 本表登记巡检维度参数';
COMMENT ON COLUMN scrm.scrm_inspection_task.credit_code IS '统一社会信用代码, 第三方工商 API 查询主键 (可空, 名称兜底)';
COMMENT ON COLUMN scrm.scrm_inspection_task.cycle_type IS '巡检周期: DAILY / WEEKLY / QUARTERLY / MANUAL';
COMMENT ON COLUMN scrm.scrm_inspection_task.inspect_business IS '是否启用工商状态 API 巡检';
COMMENT ON COLUMN scrm.scrm_inspection_task.inspect_tax IS '是否启用税务公开公告抓取';
COMMENT ON COLUMN scrm.scrm_inspection_task.last_result IS '上次执行结果: SUCCESS / PARTIAL / FAILED';

ALTER TABLE scrm.scrm_inspection_task
    ADD CONSTRAINT pk_inspection_task PRIMARY KEY (id);
CREATE INDEX idx_inspection_task_customer  ON scrm.scrm_inspection_task USING btree (customer_id);
CREATE INDEX idx_inspection_task_owner     ON scrm.scrm_inspection_task USING btree (owner_account_id);
CREATE INDEX idx_inspection_task_enabled   ON scrm.scrm_inspection_task USING btree (is_enabled, cycle_type);
CREATE INDEX idx_inspection_task_credit    ON scrm.scrm_inspection_task USING btree (credit_code);

-- 2. 巡检结果快照
CREATE TABLE scrm.scrm_inspection_snapshot (
    id              bigint NOT NULL,
    create_time     timestamp without time zone NOT NULL,
    update_time     timestamp without time zone NOT NULL,
    version         bigint DEFAULT 0 NOT NULL,
    task_id         bigint NOT NULL,
    dimension       character varying(20) NOT NULL,
    credit_code     character varying(32),
    enterprise_name character varying(200),
    state_hash      character varying(64) NOT NULL,
    is_latest       boolean DEFAULT true NOT NULL,
    inspect_at      timestamp without time zone NOT NULL,
    source          character varying(30) NOT NULL,
    status          character varying(30),
    detail_json     text,
    changed         boolean DEFAULT false NOT NULL,
    error_message   character varying(1000)
);
COMMENT ON TABLE scrm.scrm_inspection_snapshot IS '工商税务巡检结果快照: 按企业+维度只留最新一条, 状态哈希比对变化才告警';
COMMENT ON COLUMN scrm.scrm_inspection_snapshot.dimension IS '巡检维度: BUSINESS(工商状态 API) / TAX(税务公开公告)';
COMMENT ON COLUMN scrm.scrm_inspection_snapshot.state_hash IS '结构化状态 JSON 的 SHA-256 哈希, 用于变化比对';
COMMENT ON COLUMN scrm.scrm_inspection_snapshot.is_latest IS '是否该企业该维度最新快照 (服务层维护唯一最新)';
COMMENT ON COLUMN scrm.scrm_inspection_snapshot.source IS '数据来源: QIXIN(启信宝) / QCC(企查查) / GD_TAX(广东税务) / ZJ_TAX(浙江税务) / MANUAL';
COMMENT ON COLUMN scrm.scrm_inspection_snapshot.changed IS '相对上一快照是否发生变化 (基线快照为 false)';

ALTER TABLE scrm.scrm_inspection_snapshot
    ADD CONSTRAINT pk_inspection_snapshot PRIMARY KEY (id);
CREATE INDEX idx_inspection_snapshot_task ON scrm.scrm_inspection_snapshot USING btree (task_id);
CREATE INDEX idx_inspection_snapshot_latest ON scrm.scrm_inspection_snapshot USING btree (task_id, dimension, is_latest);
CREATE INDEX idx_inspection_snapshot_hash ON scrm.scrm_inspection_snapshot USING btree (state_hash);
-- 同一企业同一维度同一来源同一时刻不重复
CREATE UNIQUE INDEX uk_inspection_snapshot_uniq
    ON scrm.scrm_inspection_snapshot USING btree (task_id, dimension, inspect_at);

-- 3. 巡检告警站内信模板（复用 NotificationCenter 模板渲染链路, 变化告警开箱即用）
INSERT INTO scrm.scrm_notification_template (
    id, template_name, template_code, category, channel, title, content,
    variables, sender_name, is_html, enabled, usage_count,
    create_time, update_time, version
) VALUES (
    88001, '工商税务巡检告警', 'inspection-alert', 'ALERT', 'IN_APP',
    '巡检告警: {signalType}', '企业状态变化告警\n信号类型: {signalType}\n风险等级: {riskLevel}\n详情: {detail}',
    'signalType,riskLevel,detail,title', '巡检告警', false, true, 0,
    now(), now(), 0
);
COMMENT ON TABLE scrm.scrm_notification_template IS '通知模板表（含巡检域 V8 预置的 inspection-alert 告警模板）';
