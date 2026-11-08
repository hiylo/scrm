-- ======================================================================
-- V27: 固定资产折旧模块（竞品差距 P1）
--
-- 背景: 好会计/精斗云等代账 SaaS 标配「资产卡片 + 自动折旧」。
--      记账域 V21 已把 1601 固定资产 / 1602 累计折旧 纳入标准科目，
--      财务报表资产负债表也已有「固定资产原价 / 减：累计折旧」映射，
--      但系统缺少资产卡片与折旧计提能力（先前仅有素材库 AssetLibrary，
--      非固定资产）。本迁移补齐。
--
-- 算法: 直线法。月折旧 = 原值 × (1 − 残值率) / 寿命月数，保留 2 位；
--      最后一个折旧月补差，使累计 = 原值 × (1 − 残值率)。
--      计提结果生成一张「借：费用科目 贷：1602 累计折旧」的凭证草稿
--      （调用 ScrmVoucherService.createVoucher，status=DRAFT），人工确认后记账。
--
-- 幂等: 同资产同期间 (asset_id + period_year + period_month) 唯一，
--      已计提过的期间重复调用直接跳过。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

CREATE TABLE scrm.scrm_fixed_asset (
    id                        bigint NOT NULL,
    create_time               timestamp without time zone NOT NULL,
    update_time               timestamp without time zone NOT NULL,
    version                   bigint DEFAULT 0 NOT NULL,

    -- 归属主体
    company_id                bigint NOT NULL,
    company_name              character varying(200),

    -- 卡片信息
    asset_name                character varying(200) NOT NULL,
    -- 资产类别: MACHINERY(机器设备) / ELECTRONIC(电子设备) / FURNITURE(办公家具)
    --           VEHICLE(车辆) / OTHER(其他)
    asset_category            character varying(30) DEFAULT 'OTHER' NOT NULL,
    -- 原值
    original_value            numeric(16,2) NOT NULL,
    -- 残值率（默认 5%）
    residual_rate             numeric(6,4) DEFAULT 0.0500 NOT NULL,
    -- 使用寿命（月）
    useful_life_months        integer NOT NULL,
    -- 启用日期（开始计提月）
    start_date                date,
    -- 购买日期
    bought_date               date,

    -- 折旧进度
    -- 累计已提折旧
    accumulated_depreciation  numeric(16,2) DEFAULT 0 NOT NULL,
    -- 净值 = 原值 − 累计折旧
    net_value                 numeric(16,2) NOT NULL,

    -- 状态: ACTIVE / SCRAPPED
    status                    character varying(20) DEFAULT 'ACTIVE' NOT NULL,
    remark                    character varying(500),

    created_by                character varying(100)
);

ALTER TABLE ONLY scrm.scrm_fixed_asset
    ADD CONSTRAINT scrm_fixed_asset_pkey PRIMARY KEY (id);

-- 查询: 按企业列表 / 状态
CREATE INDEX idx_fixed_asset_company ON scrm.scrm_fixed_asset USING btree (company_id);
CREATE INDEX idx_fixed_asset_company_status ON scrm.scrm_fixed_asset USING btree (company_id, status);

CREATE TABLE scrm.scrm_fixed_asset_depreciation (
    id                        bigint NOT NULL,
    create_time               timestamp without time zone NOT NULL,
    update_time               timestamp without time zone NOT NULL,
    version                   bigint DEFAULT 0 NOT NULL,

    asset_id                  bigint NOT NULL,
    period_year               integer NOT NULL,
    period_month              integer NOT NULL,
    -- 当月折旧额
    depreciation_amount       numeric(16,2) NOT NULL,
    -- 提完当月后的累计折旧
    accumulated_after         numeric(16,2) NOT NULL,
    -- 摘要（如「计提 2026年11月折旧」）
    remark                    character varying(500)
);

ALTER TABLE ONLY scrm.scrm_fixed_asset_depreciation
    ADD CONSTRAINT scrm_fixed_asset_depreciation_pkey PRIMARY KEY (id);

-- 幂等: 同资产同期间最多一条
CREATE UNIQUE INDEX uk_fixed_asset_depr_asset_period
    ON scrm.scrm_fixed_asset_depreciation USING btree (asset_id, period_year, period_month);

COMMENT ON TABLE scrm.scrm_fixed_asset IS
    '固定资产卡片: 直线法折旧, 计提生成凭证草稿 (V27)';
COMMENT ON COLUMN scrm.scrm_fixed_asset.original_value IS
    '原值: 固定资产入账金额';
COMMENT ON COLUMN scrm.scrm_fixed_asset.residual_rate IS
    '残值率(默认5%): 折旧只提原值×(1-残值率)部分';
COMMENT ON COLUMN scrm.scrm_fixed_asset.accumulated_depreciation IS
    '累计已提折旧';
COMMENT ON COLUMN scrm.scrm_fixed_asset.net_value IS
    '净值 = 原值 - 累计折旧';

COMMENT ON TABLE scrm.scrm_fixed_asset_depreciation IS
    '固定资产折旧记录: 同资产同期间唯一(幂等), 计提后联动生成凭证草稿 (V27)';