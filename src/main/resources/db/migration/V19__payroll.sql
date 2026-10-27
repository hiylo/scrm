-- ======================================================================
-- V19: 社保 / 个税明细（薪酬域）
--
-- 背景: 代账公司每月固定要做两件事——
--       1) 社保: 按人头核定缴费基数、申报增减员、对账缴费明细；
--       2) 个税: 按人头用「累计预扣法」计算扣缴额、申报、回填。
--       此前报税域只有 scrm_tax_declaration_profile/record，粒度是
--       「一户 + 税种 + 所属期」的整体金额，无法回答「张三这个月社保扣了多少、
--       个税为什么是这个数」，会计只能另开 Excel。
--
-- 表设计:
--   1. scrm_employee: 员工档案（增减员是每月固定动作，是社保/个税的唯一数据源）
--      social_status ∈ INSURED/SUSPENDED/NOT_INSURED；status ∈ ACTIVE/LEFT
--   2. scrm_social_security_record: 社保缴费明细（按月按人，uk employee+period）
--      分项记录个人/单位部分，汇总列由应用层计算，便于对账与导出
--   3. scrm_iit_record: 个税扣缴明细（按月按人，uk employee+period）
--      保存「累计」中间量（累计收入/累计减除/累计应纳税所得额/累计已扣），
--      因为累计预扣法每一期的税额都依赖历史累计，只存本期税额无法复算与稽核。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

-- ---------------------------------------------------------------------
-- 1. 员工档案
-- ---------------------------------------------------------------------
CREATE TABLE scrm.scrm_employee (
    id                    bigint NOT NULL,
    create_time           timestamp without time zone NOT NULL,
    update_time           timestamp without time zone NOT NULL,
    version               bigint DEFAULT 0 NOT NULL,

    company_id            bigint NOT NULL,
    company_name          character varying(200),

    name                  character varying(50) NOT NULL,
    -- 证件号：个税/社保申报必需，接口层禁止回显（见实体 @JsonProperty WRITE_ONLY）
    id_card_no            character varying(30),
    phone                 character varying(20),
    position              character varying(100),

    hire_date             date,
    leave_date            date,

    -- INSURED 参保 / SUSPENDED 暂停 / NOT_INSURED 未参保
    social_status         character varying(20) DEFAULT 'INSURED' NOT NULL,
    -- 社保缴费基数（为空时由服务层按配置下限/上限与默认基数推定）
    social_base           numeric(15,2),
    -- 住房公积金缴费基数
    housing_fund_base     numeric(15,2),
    -- 专项附加扣除月额（子女教育/房贷/赡养老人等合计，个税计算用）
    special_deduction     numeric(15,2) DEFAULT 0 NOT NULL,

    -- ACTIVE 在职 / LEFT 离职
    status                character varying(20) DEFAULT 'ACTIVE' NOT NULL,
    remark                character varying(500),
    created_by            character varying(100),

    CONSTRAINT pk_scrm_employee PRIMARY KEY (id)
);

COMMENT ON TABLE scrm.scrm_employee IS '员工档案（社保/个税的唯一数据源，增减员每月固定动作）';
COMMENT ON COLUMN scrm.scrm_employee.social_status IS 'INSURED 参保 / SUSPENDED 暂停 / NOT_INSURED 未参保';
COMMENT ON COLUMN scrm.scrm_employee.special_deduction IS '专项附加扣除月额合计（子女教育/房贷/赡养老人等），个税计算用';

CREATE INDEX idx_employee_company ON scrm.scrm_employee USING btree (company_id);
CREATE INDEX idx_employee_status ON scrm.scrm_employee USING btree (company_id, status, social_status);

-- ---------------------------------------------------------------------
-- 2. 社保缴费明细（按月按人）
-- ---------------------------------------------------------------------
CREATE TABLE scrm.scrm_social_security_record (
    id                        bigint NOT NULL,
    create_time               timestamp without time zone NOT NULL,
    update_time               timestamp without time zone NOT NULL,
    version                   bigint DEFAULT 0 NOT NULL,

    company_id                bigint NOT NULL,
    company_name              character varying(200),
    employee_id               bigint NOT NULL,
    employee_name             character varying(50),

    period_year               integer NOT NULL,
    period_month              integer NOT NULL,

    -- 本月实际缴费基数（已按上下限截断）
    base_amount               numeric(15,2) NOT NULL,

    -- 养老保险
    pension_personal          numeric(15,2) DEFAULT 0 NOT NULL,
    pension_company           numeric(15,2) DEFAULT 0 NOT NULL,
    -- 医疗保险
    medical_personal          numeric(15,2) DEFAULT 0 NOT NULL,
    medical_company           numeric(15,2) DEFAULT 0 NOT NULL,
    -- 失业保险
    unemployment_personal     numeric(15,2) DEFAULT 0 NOT NULL,
    unemployment_company      numeric(15,2) DEFAULT 0 NOT NULL,
    -- 工伤保险（仅单位缴纳）
    injury_company            numeric(15,2) DEFAULT 0 NOT NULL,
    -- 生育保险（仅单位缴纳）
    maternity_company         numeric(15,2) DEFAULT 0 NOT NULL,
    -- 住房公积金
    housing_fund_personal     numeric(15,2) DEFAULT 0 NOT NULL,
    housing_fund_company      numeric(15,2) DEFAULT 0 NOT NULL,

    -- 合计（应用层写入，便于对账与导出口径统一）
    total_personal            numeric(15,2) DEFAULT 0 NOT NULL,
    total_company             numeric(15,2) DEFAULT 0 NOT NULL,
    total_amount              numeric(15,2) DEFAULT 0 NOT NULL,

    -- PENDING 待申报 / DECLARED 已申报 / PAID 已缴费
    status                    character varying(20) DEFAULT 'PENDING' NOT NULL,
    declared_at               timestamp without time zone,
    paid_at                   timestamp without time zone,
    remark                    character varying(500),
    created_by                character varying(100),

    CONSTRAINT pk_scrm_social_security_record PRIMARY KEY (id),
    CONSTRAINT uk_social_employee_period UNIQUE (employee_id, period_year, period_month)
);

COMMENT ON TABLE scrm.scrm_social_security_record IS '社保缴费明细（按月按人，分项存个人/单位部分）';
COMMENT ON COLUMN scrm.scrm_social_security_record.base_amount IS '本月实际缴费基数（已按当地上下限截断）';
COMMENT ON COLUMN scrm.scrm_social_security_record.status IS 'PENDING 待申报 / DECLARED 已申报 / PAID 已缴费';

CREATE INDEX idx_social_company_period ON scrm.scrm_social_security_record USING btree (company_id, period_year, period_month);
CREATE INDEX idx_social_status ON scrm.scrm_social_security_record USING btree (status, period_year, period_month);

-- ---------------------------------------------------------------------
-- 3. 个税扣缴明细（按月按人，累计预扣法）
-- ---------------------------------------------------------------------
CREATE TABLE scrm.scrm_iit_record (
    id                        bigint NOT NULL,
    create_time               timestamp without time zone NOT NULL,
    update_time               timestamp without time zone NOT NULL,
    version                   bigint DEFAULT 0 NOT NULL,

    company_id                bigint NOT NULL,
    company_name              character varying(200),
    employee_id               bigint NOT NULL,
    employee_name             character varying(50),

    period_year               integer NOT NULL,
    period_month              integer NOT NULL,

    -- 本期收入（税前工资）
    income_amount             numeric(15,2) DEFAULT 0 NOT NULL,
    -- 本期减除费用（5000 元/月）
    basic_deduction           numeric(15,2) DEFAULT 0 NOT NULL,
    -- 本期专项扣除（社保 + 公积金个人部分）
    social_deduction          numeric(15,2) DEFAULT 0 NOT NULL,
    -- 本期专项附加扣除
    special_deduction         numeric(15,2) DEFAULT 0 NOT NULL,

    -- 累计口径（累计预扣法必需，缺了无法复算历史）
    cumulative_income         numeric(15,2) DEFAULT 0 NOT NULL,
    cumulative_deduction      numeric(15,2) DEFAULT 0 NOT NULL,
    cumulative_taxable        numeric(15,2) DEFAULT 0 NOT NULL,
    -- 适用税率（小数，如 0.03）
    tax_rate                  numeric(8,4) DEFAULT 0 NOT NULL,
    quick_deduction           numeric(15,2) DEFAULT 0 NOT NULL,
    -- 累计应纳税额 / 累计已预扣税额 / 本期应预扣
    cumulative_tax            numeric(15,2) DEFAULT 0 NOT NULL,
    paid_tax                  numeric(15,2) DEFAULT 0 NOT NULL,
    current_tax               numeric(15,2) DEFAULT 0 NOT NULL,

    -- PENDING 待申报 / DECLARED 已申报 / PAID 已缴款
    status                    character varying(20) DEFAULT 'PENDING' NOT NULL,
    declared_at               timestamp without time zone,
    paid_at                   timestamp without time zone,
    remark                    character varying(500),
    created_by                character varying(100),

    CONSTRAINT pk_scrm_iit_record PRIMARY KEY (id),
    CONSTRAINT uk_iit_employee_period UNIQUE (employee_id, period_year, period_month)
);

COMMENT ON TABLE scrm.scrm_iit_record IS '个税扣缴明细（按月按人，累计预扣法）';
COMMENT ON COLUMN scrm.scrm_iit_record.cumulative_taxable IS '累计应纳税所得额 = 累计收入 - 累计减除费用 - 累计专项扣除 - 累计专项附加扣除';
COMMENT ON COLUMN scrm.scrm_iit_record.current_tax IS '本期应预扣 = 累计应纳税额 - 累计已预扣税额（可为负，负数留抵下期）';
COMMENT ON COLUMN scrm.scrm_iit_record.status IS 'PENDING 待申报 / DECLARED 已申报 / PAID 已缴款';

CREATE INDEX idx_iit_company_period ON scrm.scrm_iit_record USING btree (company_id, period_year, period_month);
CREATE INDEX idx_iit_status ON scrm.scrm_iit_record USING btree (status, period_year, period_month);
