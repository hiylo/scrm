-- ======================================================================
-- V18: 工商年报 + 资质许可到期管理（合规域）
--
-- 背景: 这两项是代账公司的法定强制业务，也是直接收费项——
--       1) 工商年报: 《企业信息公示暂行条例》要求每年 1 月 1 日至 6 月 30 日
--          报送上一年度年报，逾期进入经营异常名录，影响招投标与贷款；
--       2) 资质许可: 食品经营许可、劳务派遣许可等到期未续，客户被罚是代账公司的责任。
--
--       此前系统只有「服务到期」(scrm_company.service_end_date) 一种到期概念，
--       无法表达「这家公司今年年报报没报」「这个许可证几月到期」。
--
-- 表设计:
--   1. scrm_annual_report: 一户一年一条（uk_annual_company_year），
--      status ∈ PENDING/SUBMITTED/OVERDUE/EXEMPT/CANCELED；
--      EXEMPT 表示当年注销/免征，不计入逾期。
--   2. scrm_qualification: 一户多证（食品/卫生/劳务/消防…），
--      status ∈ VALID/EXPIRING/EXPIRED/RENEWED/CANCELED，由调度按 expire_date 推进。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

-- ---------------------------------------------------------------------
-- 1. 工商年报
-- ---------------------------------------------------------------------
CREATE TABLE scrm.scrm_annual_report (
    id                  bigint NOT NULL,
    create_time         timestamp without time zone NOT NULL,
    update_time         timestamp without time zone NOT NULL,
    version             bigint DEFAULT 0 NOT NULL,

    company_id          bigint NOT NULL,
    company_name        character varying(200),

    -- 报送年度（如 2025 表示 2025 年度报告，截止 2026-06-30）
    report_year         integer NOT NULL,
    due_date            date NOT NULL,

    -- PENDING 待报送 / SUBMITTED 已报送 / OVERDUE 已逾期
    -- EXEMPT 免报（当年注销等） / CANCELED 已撤销
    status              character varying(20) DEFAULT 'PENDING' NOT NULL,

    submitted_at        timestamp without time zone,
    submitted_by        character varying(100),
    receipt_no          character varying(100),
    remark              character varying(500),

    remind_count        integer DEFAULT 0 NOT NULL,
    last_reminded_at    timestamp without time zone,

    created_by          character varying(100),
    CONSTRAINT pk_scrm_annual_report PRIMARY KEY (id),
    CONSTRAINT uk_annual_company_year UNIQUE (company_id, report_year)
);

COMMENT ON TABLE scrm.scrm_annual_report IS '工商年报（一户一年一条，每年 1/1-6/30 报送上一年度报告）';
COMMENT ON COLUMN scrm.scrm_annual_report.report_year IS '报送年度；2025 表示 2025 年度报告，截止 2026-06-30';
COMMENT ON COLUMN scrm.scrm_annual_report.status IS 'PENDING/SUBMITTED/OVERDUE/EXEMPT/CANCELED；EXEMPT 不计入逾期';

CREATE INDEX idx_annual_company ON scrm.scrm_annual_report USING btree (company_id);
CREATE INDEX idx_annual_status_due ON scrm.scrm_annual_report USING btree (status, due_date);

-- ---------------------------------------------------------------------
-- 2. 资质许可（一户多证）
-- ---------------------------------------------------------------------
CREATE TABLE scrm.scrm_qualification (
    id                    bigint NOT NULL,
    create_time           timestamp without time zone NOT NULL,
    update_time           timestamp without time zone NOT NULL,
    version               bigint DEFAULT 0 NOT NULL,

    company_id            bigint NOT NULL,
    company_name          character varying(200),

    qualification_name    character varying(200) NOT NULL,
    -- FOOD 食品经营 / HEALTH 卫生 / LABOR 劳务派遣 / FIRE 消防 / IMPORT_EXPORT 进出口 / OTHER
    qualification_type    character varying(50) DEFAULT 'OTHER' NOT NULL,
    cert_no               character varying(100),
    issuer                character varying(200),

    issue_date            date,
    expire_date           date,

    -- VALID 有效 / EXPIRING 即将到期 / EXPIRED 已过期 / RENEWED 已续期 / CANCELED 已注销
    status                character varying(20) DEFAULT 'VALID' NOT NULL,

    -- 到期前多少天开始提醒负责会计
    remind_days_before    integer DEFAULT 60 NOT NULL,
    remind_count          integer DEFAULT 0 NOT NULL,
    last_reminded_at      timestamp without time zone,

    remark                character varying(500),
    created_by            character varying(100),
    CONSTRAINT pk_scrm_qualification PRIMARY KEY (id)
);

COMMENT ON TABLE scrm.scrm_qualification IS '资质许可台账（一户多证：食品经营/卫生/劳务派遣/消防等）';
COMMENT ON COLUMN scrm.scrm_qualification.status IS 'VALID/EXPIRING/EXPIRED/RENEWED/CANCELED，由调度按 expire_date 推进';
COMMENT ON COLUMN scrm.scrm_qualification.remind_days_before IS '到期前提醒天数（默认 60，留足续办时间）';

CREATE INDEX idx_qualification_company ON scrm.scrm_qualification USING btree (company_id);
CREATE INDEX idx_qualification_status_expire ON scrm.scrm_qualification USING btree (status, expire_date);

-- ---------------------------------------------------------------------
-- 3. 通知模板（年报 / 资质到期提醒）
--    通知中心按 template_code 加载模板渲染，缺模板会直接发送失败，
--    因此模板必须随迁移一起落库，不能只靠人工在页面上建。
-- ---------------------------------------------------------------------
INSERT INTO scrm.scrm_notification_template (
    id, template_name, template_code, category, channel, title, content, variables,
    sender_name, is_html, enabled, usage_count, create_time, update_time, version
) VALUES (
    88006, '工商年报提醒', 'annual-report-remind', 'ALERT', 'IN_APP',
    '工商年报提醒: {companyName} {reportYear}年度',
    '【工商年报】企业: {companyName}　年度: {reportYear}　截止: {dueDate}　状态: {statusLabel}
距截止还有 {daysLeft} 天。
① 逾期后果: 未按期报送将进入经营异常名录, 影响招投标、贷款、政府采购;
② 报送入口: 国家企业信用信息公示系统 https://www.gsxt.gov.cn ;
③ 报送完成后请在系统内回填回执编号, 状态将自动转为「已报送」。
（内部提醒，勿直接转发客户）',
    'companyName,reportYear,dueDate,statusLabel,daysLeft',
    '合规系统', false, true, 0,
    timestamp '2026-10-25 10:00:00', timestamp '2026-10-25 10:00:00', 0
) ON CONFLICT (template_code) DO NOTHING;

INSERT INTO scrm.scrm_notification_template (
    id, template_name, template_code, category, channel, title, content, variables,
    sender_name, is_html, enabled, usage_count, create_time, update_time, version
) VALUES (
    88007, '资质到期提醒', 'qualification-expire-remind', 'ALERT', 'IN_APP',
    '资质到期提醒: {companyName} {qualificationName}',
    '【资质到期】企业: {companyName}　证照: {qualificationName}　证号: {certNo}
到期日: {expireDate}　当前状态: {statusLabel}　距到期还有 {daysLeft} 天。
① 到期未续办, 客户将无法继续经营相关业务, 代账方需承担提醒责任;
② 请提前联系客户准备续办材料（场地证明、人员资质、体检报告等）;
③ 续办完成后请更新证照到期日并置为「已续期」。
（内部提醒，勿直接转发客户）',
    'companyName,qualificationName,certNo,expireDate,statusLabel,daysLeft',
    '合规系统', false, true, 0,
    timestamp '2026-10-25 10:00:00', timestamp '2026-10-25 10:00:00', 0
) ON CONFLICT (template_code) DO NOTHING;
