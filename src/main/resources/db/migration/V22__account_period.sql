-- ======================================================================
-- V22: 会计期间（结账）+ 结转凭证标记
--
-- 背景: 期末结转损益之后，该期间的账必须"封住"——再补凭证会让已出的报表
--       与账面不符，结转凭证本身也会失真。必须显式记录期间状态，
--       不能靠"结转凭证存不存在"去推断（它可能被手工作废）。
--
-- 约束:
--   1. CLOSED 期间禁止新增/记账凭证（服务层强制）；
--   2. 结账时自动生成结转损益凭证并记账，凭证 id 记录在期间表；
--   3. 反结账作废该结转凭证并恢复 OPEN，保留痕迹便于审计。
--
-- 为什么凭证要加 is_closing 标记:
--   结转凭证本身的分录也落在损益类科目上（借收入/贷费用去清零）。
--   如果不加标记，利润表按本期发生额聚合时会把结转分录一起算进去，
--   结果是"结账后利润变 0"。因此利润表聚合必须排除结转凭证，
--   而这个判断不能靠凭证字（'转'）去猜——企业也可能用'转'字做正常业务。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

CREATE TABLE scrm.scrm_account_period (
    id                  bigint NOT NULL,
    create_time         timestamp without time zone NOT NULL,
    update_time         timestamp without time zone NOT NULL,
    version             bigint DEFAULT 0 NOT NULL,

    company_id          bigint NOT NULL,
    period_year         integer NOT NULL,
    period_month        integer NOT NULL,

    -- OPEN 未结账 / CLOSED 已结账
    status              character varying(20) DEFAULT 'OPEN' NOT NULL,
    -- 结转损益凭证 ID（结账时生成；当期无损益发生额时为空）
    closing_voucher_id  bigint,

    closed_by           character varying(100),
    closed_at           timestamp without time zone,
    reopened_by         character varying(100),
    reopened_at         timestamp without time zone,
    remark              character varying(500),

    CONSTRAINT pk_scrm_account_period PRIMARY KEY (id),
    CONSTRAINT uk_period_company_ym UNIQUE (company_id, period_year, period_month)
);

COMMENT ON TABLE scrm.scrm_account_period IS '会计期间（结账状态 + 结转凭证）';
COMMENT ON COLUMN scrm.scrm_account_period.status IS 'OPEN 未结账 / CLOSED 已结账；CLOSED 期间禁止新增凭证';
COMMENT ON COLUMN scrm.scrm_account_period.closing_voucher_id IS '结转损益凭证 ID，反结账时据此作废';

CREATE INDEX idx_period_company ON scrm.scrm_account_period USING btree (company_id, period_year, period_month);

-- ---------------------------------------------------------------------
-- 凭证增加「结转损益」标记（利润表聚合必须排除，见文件头说明）
-- ---------------------------------------------------------------------
ALTER TABLE scrm.scrm_voucher
    ADD COLUMN is_closing boolean DEFAULT false NOT NULL;

COMMENT ON COLUMN scrm.scrm_voucher.is_closing IS '是否为期末结转损益凭证；利润表聚合排除此类凭证，否则结账后利润会被清零';

CREATE INDEX idx_voucher_closing ON scrm.scrm_voucher USING btree (company_id, is_closing);
