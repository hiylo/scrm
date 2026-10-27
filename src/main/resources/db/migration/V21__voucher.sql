-- ======================================================================
-- V21: 记账（凭证域）
--
-- 背景: 代账公司最核心的交付物是「账」。此前系统管得住客户、申报、社保、
--       资料，但会计做完账之后，凭证和科目余额没有地方落，只能留在 Excel / 用友里。
--
-- 三条不可妥协的业务约束（由服务层强制，不依赖前端）:
--   1. 借贷平衡: 每张凭证 sum(借方) 必须等于 sum(贷方)，且不为 0；
--   2. 科目受控: 分录必须挂在本企业已启用的科目上，不允许自由文本科目；
--   3. 记账后不可改: 已记账(POSTED)凭证不允许修改分录，只能作废(VOID)后重开，
--      否则历史账目可被悄悄改掉，无法审计。
--
-- 表设计:
--   1. scrm_account_subject: 会计科目（按企业独立建账，支持父子级）
--      category ∈ ASSET/LIABILITY/EQUITY/COST/INCOME/EXPENSE
--      direction ∈ DEBIT/CREDIT（余额方向，决定期末余额的算法）
--   2. scrm_voucher: 凭证头（一户一期一字一号，uk 保证凭证号不重复）
--      status ∈ DRAFT/POSTED/VOID
--   3. scrm_voucher_entry: 凭证分录（借/贷两列各自记，不用正负数表达方向）
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

-- ---------------------------------------------------------------------
-- 1. 会计科目
-- ---------------------------------------------------------------------
CREATE TABLE scrm.scrm_account_subject (
    id              bigint NOT NULL,
    create_time     timestamp without time zone NOT NULL,
    update_time     timestamp without time zone NOT NULL,
    version         bigint DEFAULT 0 NOT NULL,

    company_id      bigint NOT NULL,
    company_name    character varying(200),

    -- 科目编码（如 1002 / 100201），按企业唯一
    subject_code    character varying(20) NOT NULL,
    subject_name    character varying(100) NOT NULL,

    -- ASSET 资产 / LIABILITY 负债 / EQUITY 所有者权益
    -- COST 成本 / INCOME 损益收入类 / EXPENSE 损益支出类
    category        character varying(20) NOT NULL,
    -- DEBIT 借方科目（资产、成本、费用） / CREDIT 贷方科目（负债、权益、收入）
    direction       character varying(10) NOT NULL,

    -- 上级科目编码（空表示一级科目），用于总账/明细账层级
    parent_code     character varying(20),
    subject_level   integer DEFAULT 1 NOT NULL,

    -- ACTIVE 启用 / DISABLED 停用（停用后不可新增分录，历史分录保留）
    status          character varying(20) DEFAULT 'ACTIVE' NOT NULL,
    remark          character varying(500),
    created_by      character varying(100),

    CONSTRAINT pk_scrm_account_subject PRIMARY KEY (id),
    CONSTRAINT uk_subject_company_code UNIQUE (company_id, subject_code)
);

COMMENT ON TABLE scrm.scrm_account_subject IS '会计科目（按企业独立建账）';
COMMENT ON COLUMN scrm.scrm_account_subject.category IS 'ASSET/LIABILITY/EQUITY/COST/INCOME/EXPENSE';
COMMENT ON COLUMN scrm.scrm_account_subject.direction IS 'DEBIT 借方科目 / CREDIT 贷方科目，决定期末余额算法';
COMMENT ON COLUMN scrm.scrm_account_subject.status IS 'ACTIVE 启用 / DISABLED 停用';

CREATE INDEX idx_subject_company_category ON scrm.scrm_account_subject USING btree (company_id, category);
CREATE INDEX idx_subject_parent ON scrm.scrm_account_subject USING btree (company_id, parent_code);

-- ---------------------------------------------------------------------
-- 2. 记账凭证（头）
-- ---------------------------------------------------------------------
CREATE TABLE scrm.scrm_voucher (
    id                bigint NOT NULL,
    create_time       timestamp without time zone NOT NULL,
    update_time       timestamp without time zone NOT NULL,
    version           bigint DEFAULT 0 NOT NULL,

    company_id        bigint NOT NULL,
    company_name      character varying(200),

    period_year       integer NOT NULL,
    period_month      integer NOT NULL,
    -- 凭证字: 记 / 收 / 付 / 转
    voucher_word      character varying(10) DEFAULT '记' NOT NULL,
    voucher_no        integer NOT NULL,
    voucher_date      date NOT NULL,

    -- 借贷合计（保存时由服务层按分录汇总写入，便于列表页与试算平衡快速取数）
    total_debit       numeric(15,2) DEFAULT 0 NOT NULL,
    total_credit      numeric(15,2) DEFAULT 0 NOT NULL,
    entry_count       integer DEFAULT 0 NOT NULL,

    -- DRAFT 草稿 / POSTED 已记账 / VOID 已作废
    status            character varying(20) DEFAULT 'DRAFT' NOT NULL,
    attachment_count  integer DEFAULT 0 NOT NULL,
    remark            character varying(500),

    created_by        character varying(100),
    posted_by         character varying(100),
    posted_at         timestamp without time zone,
    voided_by         character varying(100),
    voided_at         timestamp without time zone,

    CONSTRAINT pk_scrm_voucher PRIMARY KEY (id),
    CONSTRAINT uk_voucher_company_period_no UNIQUE
        (company_id, period_year, period_month, voucher_word, voucher_no)
);

COMMENT ON TABLE scrm.scrm_voucher IS '记账凭证头（一户一期一字一号）';
COMMENT ON COLUMN scrm.scrm_voucher.status IS 'DRAFT 草稿 / POSTED 已记账 / VOID 已作废';
COMMENT ON COLUMN scrm.scrm_voucher.total_debit IS '借贷合计由服务层按分录汇总写入，不信任前端传值';

CREATE INDEX idx_voucher_company_period ON scrm.scrm_voucher USING btree (company_id, period_year, period_month);
CREATE INDEX idx_voucher_status ON scrm.scrm_voucher USING btree (company_id, status);
CREATE INDEX idx_voucher_date ON scrm.scrm_voucher USING btree (company_id, voucher_date);

-- ---------------------------------------------------------------------
-- 3. 凭证分录
-- ---------------------------------------------------------------------
CREATE TABLE scrm.scrm_voucher_entry (
    id              bigint NOT NULL,
    create_time     timestamp without time zone NOT NULL,
    update_time     timestamp without time zone NOT NULL,
    version         bigint DEFAULT 0 NOT NULL,

    voucher_id      bigint NOT NULL,
    company_id      bigint NOT NULL,
    line_no         integer NOT NULL,

    subject_code    character varying(20) NOT NULL,
    subject_name    character varying(100) NOT NULL,

    summary         character varying(200),
    -- 借方与贷方分列存放，不用「正负号 + 方向」表达，
    -- 因为负数在会计上没有意义，混用会让人误读且无法直接求和校验
    debit_amount    numeric(15,2) DEFAULT 0 NOT NULL,
    credit_amount   numeric(15,2) DEFAULT 0 NOT NULL,

    CONSTRAINT pk_scrm_voucher_entry PRIMARY KEY (id)
);

COMMENT ON TABLE scrm.scrm_voucher_entry IS '凭证分录（借/贷分列，不用正负号表达方向）';
COMMENT ON COLUMN scrm.scrm_voucher_entry.debit_amount IS '借方金额，与 credit_amount 至多一个非零';

CREATE INDEX idx_entry_voucher ON scrm.scrm_voucher_entry USING btree (voucher_id);
CREATE INDEX idx_entry_company_subject ON scrm.scrm_voucher_entry USING btree (company_id, subject_code);
