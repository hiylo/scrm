-- ======================================================================
-- V17: 公司主体负责会计（代账公司多会计协作的基础字段）
--
-- 背景: 财税代账公司一个会计通常负责数十到数百户，报税/续费/政策提醒
--       必须落到「这家公司由哪个会计负责」而不是全公司一个 admin。
--       V14 已建 scrm_company 但只有 owner_account_id（归属销售账号），
--       没有会计维度，导致提醒只能固定发给配置里的单个人。
--
-- 变更:
--   1. scrm_company 增加 accountant_id / accountant_name（会计 ID + 冗余姓名，
--      避免每次列表都回查用户表）；
--   2. 按 accountant_id 建索引，支撑「我的客户」这类高频筛选；
--   3. 不做存量回填——历史公司由管理员在页面上逐一指派，
--      未指派时提醒自动回落到 scrm.*.notify-recipient 配置，行为与改动前一致。
--
-- 幂等: 仅 ALTER TABLE ADD COLUMN + CREATE INDEX; 重跑由 Flyway 校验和阻止。
-- ======================================================================

ALTER TABLE scrm.scrm_company ADD COLUMN accountant_id bigint;
ALTER TABLE scrm.scrm_company ADD COLUMN accountant_name character varying(100);

COMMENT ON COLUMN scrm.scrm_company.accountant_id IS
    '负责会计的用户 ID；为空表示未指派，相关提醒回落到配置的默认收件人';
COMMENT ON COLUMN scrm.scrm_company.accountant_name IS
    '负责会计姓名（冗余，避免列表页回查用户表）';

CREATE INDEX idx_company_accountant ON scrm.scrm_company USING btree (accountant_id);
