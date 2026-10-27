-- ======================================================================
-- 财税数字化 - 公司主体域（V14）
-- ======================================================================
-- 背景: 现有模型「一个微信好友 = 一条 scrm_customer」，没有公司层，导致
--   同一公司的老板/财务是互不相干的客户、业务数据（合同/报税/证照/巡检/续约）
--   挂在「某个人的微信号」上、财务离职换号即数据断裂、电话/线下客户无处建档。
--   财税业务的天然主体是「企业」，故本迁移引入公司主体层。
--
-- 设计原则（2026-10-23 用户确认）:
--   1. 公司 = 财税业务主键（报税/证照/巡检/续约/合同均挂 company_id）；
--   2. 一个联系人只属一家公司（单 company_id，不做多公司关联）；
--   3. 电话/线下联系人复用 scrm_customer，不另建表；
--   4. 三个维度严格分离、互不排斥：
--      a) 获客渠道 source_channel / source_channel_code_id（客户「从哪来」，
--         与 scrm_public_sea_customer、scrm_channel_code 对齐）；
--      b) 平台账号 platform_type + platform_customer_uid（微信/企微/抖音等
--         平台身份，值为 wxid / userid / openid，**不可填手机号**）；
--      c) 手机号 phone（独立字段，可与平台账号并存）。
--
--   一个联系人可以：只有平台账号（微信好友，未留手机）/ 只有手机号
--   （电话、线下沟通，未加微信）/ 两者都有（微信好友并留了手机号）。
--   后期补录任一侧都属于正常业务动作，故两列都不互斥、都可空。
--
-- 兼容性: 全部为新增表/新增列 + 放开两列 NOT NULL，不改变任何既有语义。
--   存量 1269 条客户 company_id 留空（待关联），phone 留空（待补录），
--   platform_type/platform_customer_uid 原本全部有值，放开 NOT NULL 不产生副作用。
-- 数据库: PostgreSQL 16+   Schema: scrm   Flyway 版本: V14
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 公司主体表 scrm_company
-- ----------------------------------------------------------------------
CREATE TABLE scrm.scrm_company (
    id                          bigint NOT NULL,
    create_time                 timestamp without time zone NOT NULL,
    update_time                 timestamp without time zone NOT NULL,
    version                     bigint DEFAULT 0 NOT NULL,

    -- 归属（数据隔离维度，与 scrm_customer.owner_account_id 一致）
    owner_account_id            bigint NOT NULL,

    -- 主体基本信息
    company_name                character varying(200) NOT NULL,
    credit_code                 character varying(32),
    company_type                character varying(30) DEFAULT 'ENTERPRISE' NOT NULL,
    legal_representative        character varying(100),
    registered_capital          character varying(100),
    establish_date              date,
    registered_address          character varying(500),
    industry                    character varying(100),
    employee_scale              character varying(30),

    -- 经营与税务状态（工商巡检/税务巡检的落地字段）
    business_status             character varying(30) DEFAULT 'ACTIVE' NOT NULL,
    tax_status                  character varying(30) DEFAULT 'NORMAL' NOT NULL,
    business_status_changed_at  timestamp without time zone,

    -- 服务关系（代账业务关键字段）
    service_type                character varying(50),
    service_status              character varying(30) DEFAULT 'PENDING' NOT NULL,
    service_start_date          date,
    service_end_date            date,

    -- 来源与核验
    source                      character varying(30) DEFAULT 'MANUAL' NOT NULL,
    source_ref_id               bigint,
    verified                    boolean DEFAULT false NOT NULL,
    verified_at                 timestamp without time zone,
    remark                      text,

    created_by                  character varying(100),
    CONSTRAINT pk_scrm_company PRIMARY KEY (id)
);

COMMENT ON TABLE scrm.scrm_company IS '财税主体（公司/个体户），财税业务的业务主键';
COMMENT ON COLUMN scrm.scrm_company.owner_account_id IS '归属账号 ID（数据隔离）';
COMMENT ON COLUMN scrm.scrm_company.company_name IS '企业名称（营业执照登记名称）';
COMMENT ON COLUMN scrm.scrm_company.credit_code IS '统一社会信用代码（GB32100，唯一，可空：个体户/未核验）';
COMMENT ON COLUMN scrm.scrm_company.company_type IS '主体类型: ENTERPRISE(企业)/INDIVIDUAL_BUSINESS(个体户)/SOLE_PROPRIETORSHIP(个人独资)/INDIVIDUAL(个人)';
COMMENT ON COLUMN scrm.scrm_company.business_status IS '工商状态: ACTIVE(在营)/ABNORMAL(经营异常)/CANCELLED(已注销)/REVOKED(吊销)/MOVED(迁出)/UNKNOWN';
COMMENT ON COLUMN scrm.scrm_company.tax_status IS '税务状态: NORMAL(正常)/ABNORMAL(非正常户)/ARREARS(欠税)/CANCELLED(已注销)/UNKNOWN';
COMMENT ON COLUMN scrm.scrm_company.service_type IS '服务类型（逗号分隔多值）: BOOKKEEPING(代账)/REGISTRATION(注册)/DEREGISTRATION(注销)/QUALIFICATION(资质)/TAX_CONSULTING(税务咨询)';
COMMENT ON COLUMN scrm.scrm_company.service_status IS '服务状态: PENDING(待启动)/SERVING(服务中)/SUSPENDED(暂停)/TERMINATED(已终止)';
COMMENT ON COLUMN scrm.scrm_company.source IS '来源: MANUAL(手工录入)/OCR(证照识别)/IMPORT(批量导入)/INSPECTION(巡检发现)/CUSTOMER_CONVERT(客户转化)';
COMMENT ON COLUMN scrm.scrm_company.source_ref_id IS '来源单据 ID（如证照识别任务 ID）';
COMMENT ON COLUMN scrm.scrm_company.verified IS '是否已核验（营业执照 OCR 通过或人工确认）';

-- 唯一约束: 同一归属下信用代码唯一（信用代码为空不参与唯一性判定）
CREATE UNIQUE INDEX uk_company_owner_credit_code
    ON scrm.scrm_company USING btree (owner_account_id, credit_code)
    WHERE credit_code IS NOT NULL AND credit_code <> '';

CREATE INDEX idx_company_owner ON scrm.scrm_company USING btree (owner_account_id);
CREATE INDEX idx_company_name ON scrm.scrm_company USING btree (company_name);
CREATE INDEX idx_company_service_status ON scrm.scrm_company USING btree (service_status);
CREATE INDEX idx_company_business_status ON scrm.scrm_company USING btree (business_status);
CREATE INDEX idx_company_service_end_date ON scrm.scrm_company USING btree (service_end_date);

-- ----------------------------------------------------------------------
-- 2. 联系人（scrm_customer）接入公司主体 + 补齐「手机号」与「获客渠道」
-- ----------------------------------------------------------------------
ALTER TABLE scrm.scrm_customer
    ADD COLUMN company_id bigint,
    ADD COLUMN contact_role character varying(30),
    ADD COLUMN phone character varying(32),
    ADD COLUMN source_channel character varying(100),
    ADD COLUMN source_channel_code_id bigint;

-- 平台账号与手机号不互斥：纯电话/线下联系人没有平台账号，故放开 NOT NULL
ALTER TABLE scrm.scrm_customer ALTER COLUMN platform_type DROP NOT NULL;
ALTER TABLE scrm.scrm_customer ALTER COLUMN platform_customer_uid DROP NOT NULL;

COMMENT ON COLUMN scrm.scrm_customer.company_id IS '所属公司主体 ID（引用 scrm_company.id，可空=未关联待认领）';
COMMENT ON COLUMN scrm.scrm_customer.contact_role IS '在公司内的角色: BOSS(老板/法人)/FINANCE(财务)/CASHIER(出纳)/ADMIN(行政)/AGENT(经办/中介)/OTHER';
COMMENT ON COLUMN scrm.scrm_customer.phone IS '手机号（独立字段，与平台账号并存不互斥；撞单检测与检索依据）';
COMMENT ON COLUMN scrm.scrm_customer.source_channel IS '获客渠道（客户从哪来）: 抖音/朋友介绍/地推/官网/渠道活码等；与联系方式(平台账号/手机号)是不同维度';
COMMENT ON COLUMN scrm.scrm_customer.source_channel_code_id IS '来源渠道活码 ID（引用 scrm_channel_code.id）';
COMMENT ON COLUMN scrm.scrm_customer.platform_type IS '平台类型（联系方式之一）: wework/wechat_personal/douyin/kuaishou/xiaohongshu/bilibili；纯电话/线下客户为空';
COMMENT ON COLUMN scrm.scrm_customer.platform_customer_uid IS '平台账号唯一标识（wxid/企微 userid/openid 等，不可填手机号）；纯电话/线下客户为空';

-- 平台账号唯一性：仅当平台类型与账号标识都有值时生效（部分唯一索引）
CREATE UNIQUE INDEX uk_customer_owner_platform
    ON scrm.scrm_customer USING btree (owner_account_id, platform_type, platform_customer_uid)
    WHERE platform_type IS NOT NULL AND platform_customer_uid IS NOT NULL;

-- 手机号：非唯一索引用于查重/撞单检测（同一号码可能既是线索又是客户，业务层判定）
CREATE INDEX idx_customer_phone ON scrm.scrm_customer USING btree (phone);
CREATE INDEX idx_customer_company ON scrm.scrm_customer USING btree (company_id);
CREATE INDEX idx_customer_source_channel ON scrm.scrm_customer USING btree (source_channel);

-- ----------------------------------------------------------------------
-- 3. 业务数据接入公司主体（保留 customer_id 作为「经办联系人」）
-- ----------------------------------------------------------------------
ALTER TABLE scrm.scrm_contract                ADD COLUMN company_id bigint;
ALTER TABLE scrm.scrm_tax_declaration_profile ADD COLUMN company_id bigint;
ALTER TABLE scrm.scrm_license_ocr_task        ADD COLUMN company_id bigint;
ALTER TABLE scrm.scrm_inspection_task         ADD COLUMN company_id bigint;
ALTER TABLE scrm.scrm_renewal_task            ADD COLUMN company_id bigint;

COMMENT ON COLUMN scrm.scrm_contract.company_id IS '服务主体（公司）ID；customer_id 保留为签约/经办联系人';
COMMENT ON COLUMN scrm.scrm_tax_declaration_profile.company_id IS '报税主体（公司）ID；customer_id 保留为经办联系人';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.company_id IS '证照所属主体（公司）ID；识别成功后可直接回填公司档案';
COMMENT ON COLUMN scrm.scrm_inspection_task.company_id IS '巡检主体（公司）ID；未关联时沿用任务内 enterprise_name/credit_code 文本';
COMMENT ON COLUMN scrm.scrm_renewal_task.company_id IS '续费主体（公司）ID；customer_id 保留为对接联系人';

CREATE INDEX idx_contract_company ON scrm.scrm_contract USING btree (company_id);
CREATE INDEX idx_tax_profile_company ON scrm.scrm_tax_declaration_profile USING btree (company_id);
CREATE INDEX idx_license_ocr_company ON scrm.scrm_license_ocr_task USING btree (company_id);
CREATE INDEX idx_inspection_company ON scrm.scrm_inspection_task USING btree (company_id);
CREATE INDEX idx_renewal_company ON scrm.scrm_renewal_task USING btree (company_id);
