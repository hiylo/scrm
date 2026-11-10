-- ======================================================================
-- V29: 期初余额（账套启用时的结转数）
--
-- 背景:
--   代账公司中途接手客户是常态，但客户的账在此之前是别家做的——
--   历史凭证在上一家的系统里，不可能逐笔补录进本系统。
--   而本系统所有期初数都是「按已记账凭证累计」倒推出来的：
--     试算平衡的期初 = 本期之前的分录累计
--     资产负债表年初余额 = 截至上一年 12 月的分录累计
--   新接手的客户在本系统里没有任何历史凭证，于是：
--     期初余额全是 0 → 账接不上；
--     年报「年初余额」列全是 0 → 报表直接不能用。
--   这不是"锦上添花"，是接户第一天的硬阻塞。
--
-- 设计:
--   把期初余额表达为「启用期间之前的虚拟分录」：
--     opening_period = 启用期间（形如 202601），表示账套从这个月开始记；
--     本表的每条记录 = 该科目在启用期间前一刻的余额。
--   查询时，凡 yyyymm >= 启用期间的前一期，就把这些"虚拟分录"并进聚合结果，
--   因此试算平衡 / 总账 / 明细账 / 资产负债表 / 结转损益全部自动带上期初，
--   不需要在五个地方各写一遍取数逻辑。
--
--   opening_period 冗余在每一行上（而不是单独建一张"账套设置表"）：
--   一行一科目，天然唯一（company_id, subject_code），读写都是单表操作，
--   不需要 join；重新设置期初时整户替换，语义是"重设"而不是"增量修改"。
--
-- 约束:
--   期初必须借贷平衡——不平的期初等于账一开始就是错的，
--   而且会让后续所有报表都带着这个错误，服务层强制校验。
--   启用期间之前不允许存在已记账凭证（否则期初 + 历史会重复计算）。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

CREATE TABLE scrm.scrm_account_opening_balance (
    id              bigint NOT NULL,
    create_time     timestamp without time zone NOT NULL,
    update_time     timestamp without time zone NOT NULL,
    version         bigint DEFAULT 0 NOT NULL,

    company_id      bigint NOT NULL,

    -- 账套启用期间（形如 202601）：本行是该科目在启用期间前一刻的余额
    opening_period  integer NOT NULL,

    -- 科目（冗余名称做快照：科目日后改名不影响历史期初的可读性）
    subject_code    character varying(20) NOT NULL,
    subject_name    character varying(100) NOT NULL,

    -- 余额分借贷两列，不用"正负号 + 方向"：负数在会计上无意义，
    -- 混用会让人误读，也无法直接求和校验
    debit_amount    numeric(15,2) DEFAULT 0 NOT NULL,
    credit_amount   numeric(15,2) DEFAULT 0 NOT NULL,

    remark          character varying(500),
    created_by      character varying(100),
    updated_by      character varying(100),

    CONSTRAINT pk_scrm_account_opening_balance PRIMARY KEY (id)
);

COMMENT ON TABLE scrm.scrm_account_opening_balance IS
    '期初余额: 账套启用时从旧系统结转的科目余额, 查询时作为"启用期间前的虚拟分录"并入聚合 (V29)';
COMMENT ON COLUMN scrm.scrm_account_opening_balance.opening_period IS
    '账套启用期间(形如 202601): 本行表示该科目在启用期间前一刻的余额';
COMMENT ON COLUMN scrm.scrm_account_opening_balance.debit_amount IS
    '期初借方余额; 与 credit_amount 至少一个为 0 (同一科目不可能同时有借贷余额)';

-- 一户一科目一行: 重设期初时整户替换, 天然幂等
CREATE UNIQUE INDEX uk_opening_balance_company_subject
    ON scrm.scrm_account_opening_balance USING btree (company_id, subject_code);

-- 按户取全量期初（报表聚合入口）
CREATE INDEX idx_opening_balance_company
    ON scrm.scrm_account_opening_balance USING btree (company_id, opening_period);
