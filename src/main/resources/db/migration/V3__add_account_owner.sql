-- ======================================================================
-- SCRM 账号归属用户 + 默认管理员引导
-- ======================================================================
-- 用途:
--   1. scrm_account 增加 owner_user_id: 数据隔离由「账号归属用户」承担,
--      ADMIN 可看全部账号, 下级用户 (OPERATOR) 只能看到/操作自己归属的账号。
--   2. owner_user_id 关联 scrm_user.id (雪花 ID), 为可空列 ——
--      存量账号无归属时视为「系统账号/历史账号」, ADMIN 仍可见。
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- 说明:
--   - 默认管理员 (admin) 不在 SQL 中种入 —— 口令需 BCrypt 加密, 由启动期
--     AdminUserInitializer 基于配置 (SCRM_ADMIN_INITIAL_USERNAME / PASSWORD)
--     幂等创建, 密码可控且不落明文 SQL。
-- ======================================================================

-- 1. 账号归属用户列 (可空, 雪花 ID, 关联 scrm_user.id)
ALTER TABLE scrm.scrm_account
    ADD COLUMN owner_user_id bigint;

-- 2. 归属用户查询索引 (数据隔离按 owner_user_id 过滤)
CREATE INDEX idx_account_owner_user_id ON scrm.scrm_account USING btree (owner_user_id);