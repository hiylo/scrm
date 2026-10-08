-- ======================================================================
-- SCRM 出站消息队列媒体字段扩展 (scrm_outbound_message)
--
-- 用途: 出站队列从「仅文本」扩展为支持图片/文件。
--   1. 自动回复 / 人工坐席下发的 IMAGE / FILE 消息落队列时,
--      把对象存储 key 与原始文件名冗余进队列表。
--   2. 执行侧 (wx-console) 轮询或 WSS 实时代发时, scrm 生成 1h 预签名
--      URL 随指令下发; 执行侧下载到本地缓存后经 UIA 发送。
--   3. VOICE / VIDEO 暂不支持 (180 侧 UIA 无对应发送能力), 不入队。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

ALTER TABLE scrm.scrm_outbound_message
    ADD COLUMN IF NOT EXISTS media_object_key character varying(500),
    ADD COLUMN IF NOT EXISTS media_file_name character varying(255);
