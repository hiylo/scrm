-- ======================================================================
-- SCRM 销售外呼域补充（V7）：
--   1. 客户联系方式支持「公海线索」归属（owner_type 维度）
--   2. 公海线索是销售 App「我的客户」的主要数据源（assigned_to = 当前用户）
-- ======================================================================
-- 背景:
--   销售 App 的「我的客户」= 分配给当前销售的公海线索
--   (scrm_public_sea_customer.assigned_to = userId, status = ASSIGNED)。
--   V6 的联系方式表只支持正式客户(customer_id)，公海线索无法挂号码，
--   导致「保存到系统通讯录 + 点号拨打」在公海线索上不可用。
--   本迁移为联系方式表增加 owner_type 维度：
--     owner_type = CUSTOMER    → customer_id 引用 scrm_customer.id（正式客户）
--     owner_type = PUBLIC_SEA  → customer_id 引用 scrm_public_sea_customer.id（公海线索）
-- ======================================================================

-- 1. 增加归属类型列（存量数据视为正式客户）
ALTER TABLE scrm.scrm_customer_contact
    ADD COLUMN owner_type character varying(20) DEFAULT 'CUSTOMER' NOT NULL;
COMMENT ON COLUMN scrm.scrm_customer_contact.owner_type IS '归属类型: CUSTOMER(正式客户) / PUBLIC_SEA(公海线索)';

-- 2. 重建唯一约束：同一归属(类型+ID)下号码不重复
DROP INDEX IF EXISTS scrm.uk_customer_contact_phone;
CREATE UNIQUE INDEX uk_customer_contact_owner_phone
    ON scrm.scrm_customer_contact USING btree (owner_type, customer_id, phone);

-- 3. 号码查询索引保持不变（撞单检测按号码查全部归属）

-- 4. 通话记录 / 录音增加关联对象类型，区分「正式客户」与「公海线索」
ALTER TABLE scrm.scrm_call_record
    ADD COLUMN customer_type character varying(20);
COMMENT ON COLUMN scrm.scrm_call_record.customer_type IS '关联对象类型: CUSTOMER(正式客户) / PUBLIC_SEA(公海线索)，空表示未关联';

ALTER TABLE scrm.scrm_recording
    ADD COLUMN customer_type character varying(20);
COMMENT ON COLUMN scrm.scrm_recording.customer_type IS '关联对象类型: CUSTOMER(正式客户) / PUBLIC_SEA(公海线索)，空表示未关联';
