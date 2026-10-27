-- ======================================================================
-- V25: 修正 2026 年国庆调休补班日（9/19 → 9/20）
--
-- 背景: V23 手工种入的 2026 年补班日有笔误，写成了 2026-09-19。
--       国务院办公厅原文（国办发明电〔2025〕7号）明确:
--         「七、国庆节：10月1日（周四）至7日（周三）放假调休，共7天。
--           9月20日（周日）、10月10日（周六）上班。」
--       9/19 是周六，9/20 才是真正需要上班的周日。
--
-- 影响: 截止日顺延逻辑（ScrmTaxDeclarationService#nextWorkday）把补班日
--       视为「即便落在周末也算工作日」。这条错数据会:
--         1. 把 2026-09-19（周六）误判为工作日；
--         2. 漏掉 2026-09-20（周日）这个真正的补班日，
--            让它被当成休息日继续顺延到 9/21。
--       落在该周附近的申报截止日会算错 1 天。
--
-- 为什么新建迁移而不改 V23:
--       V23 已在各环境执行，改动会破坏 Flyway 校验和导致启动失败。
--       数据修正必须用新迁移。
--
-- 该笔误同时印证了假期 AI 抽取的价值: 人工录入逐年变化的调休日容易出错，
--       AI 抽取 + 人工核对能发现这类偏差。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

-- 表上 work_date 有唯一约束（uk_tax_workday_override），主键 id 也不能重复。
-- 分三步走，任一步骤在特定数据状态下都不会触发约束冲突:
--   1) 若 9/20 已存在（人工已改对），删掉多余的 9/19，保留已有 9/20；
--   2) 否则把 9/19 那条原地改成 9/20（id 不变，最小改动）；
--   3) 兜底：9/19 不存在且 9/20 也不存在时补一条。

-- 1) 9/20 已存在 → 删掉错误的 9/19
DELETE FROM scrm.scrm_tax_workday_override
WHERE work_date = DATE '2026-09-19'
  AND EXISTS (SELECT 1 FROM scrm.scrm_tax_workday_override WHERE work_date = DATE '2026-09-20');

-- 2) 只有 9/19 没有 9/20 → 原地改正日期
UPDATE scrm.scrm_tax_workday_override
SET work_date   = DATE '2026-09-20',
    remark      = '国庆调休上班',
    update_time = now(),
    version     = version + 1
WHERE work_date = DATE '2026-09-19';

-- 3) 两者都不存在（新环境数据被清过）→ 补入正确日期
INSERT INTO scrm.scrm_tax_workday_override
    (id, create_time, update_time, version, work_date, remark)
SELECT 202602010000000004, now(), now(), 0, DATE '2026-09-20', '国庆调休上班'
WHERE NOT EXISTS (SELECT 1 FROM scrm.scrm_tax_workday_override WHERE work_date = DATE '2026-09-20')
  AND NOT EXISTS (SELECT 1 FROM scrm.scrm_tax_workday_override WHERE id = 202602010000000004);
