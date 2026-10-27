-- ======================================================================
-- 财税数字化 - 证照 OCR 引擎标识列宽放宽（V16）
-- ======================================================================
-- 背景: V10 建表时 engine 为 character varying(30), 当时假设引擎标识是
--       "rapidocr" / "qwen3.5" 这类短名。实际运行中发现:
--         1. 本地视觉通道写入 "local-vision:<model>"，模型 id 走网关时形如
--            "group/sensenova-pool"，拼上前缀共 33 字符 → 超长直接 500;
--         2. 外部 OCR 服务返回的 engine 同样不受本服务控制。
--       引擎标识是审计与排障的关键字段，不能静默截断（截断后不同模型可能
--       撞成同一标签，反而失去区分能力），因此放宽列宽而非截断值。
-- 影响: 仅加宽可空列, 无数据迁移, 可安全回滚（回滚需先清理超长值）。
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

ALTER TABLE scrm.scrm_license_ocr_task
    ALTER COLUMN engine TYPE character varying(100);

COMMENT ON COLUMN scrm.scrm_license_ocr_task.engine IS
    '识别引擎标识: 本地视觉为 local-vision:<model>, 外部 OCR 为服务端返回的引擎名 (列宽 100, 允许网关模型 id)';
