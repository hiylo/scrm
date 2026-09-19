-- ======================================================================
-- SCRM 文本检索索引 - pg_trgm
-- ======================================================================
-- 用途: 为 LIKE '%xx%' / ILIKE '%xx%' 通配查询提供 GIN trigram 索引加速
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- 说明:
--   1. 先安装 pg_trgm 扩展
--   2. lower() 函数索引: 业务查询用 cb.like(cb.lower(col), '%x%'),
--      普通 gin_trgm_ops 列索引无法被 lower() 包裹的表达式命中,
--      必须对 lower(col) 建函数索引
--   3. scrm_conversation_message 是 RANGE 分区表(按 sent_at 月分区),
--      父索引必须 ON ONLY 创建, 再对每个已存在分区逐个建同定义索引并
--      ATTACH PARTITION, 单个 CREATE INDEX(不带 ONLY)无法覆盖全部已存在分区
--   4. PostgreSQL 索引名在 schema 级唯一, 各分区索引沿用 V1 的
--      <表名>_<列>_idx 命名模式, 不能与父索引重名
--   5. GIN 索引不支持 fillfactor(仅 btree/hash 支持), 故不加 WITH 子句
-- ======================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------- 1. scrm_customer 客户关键词搜索 (ScrmCustomerService) ----------
-- 查询形如 cb.like(cb.lower(c.nickname), '%x%') /
--          cb.like(cb.lower(c.platformCustomerUid), '%x%'),
-- 必须对 lower() 表达式建函数索引才能被 PostgreSQL 利用
CREATE INDEX IF NOT EXISTS idx_customer_nickname_trgm ON scrm.scrm_customer USING gin (lower(nickname) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_customer_platform_uid_trgm ON scrm.scrm_customer USING gin (lower(platform_customer_uid) gin_trgm_ops);

-- ---------- 2. scrm_conversation_message 消息检索 (ScrmConversationMessageService) ----------
-- content 查询为 ContainingIgnoreCase -> ILIKE '%x%', ILIKE 可被 gin_trgm_ops 直接利用,
-- 不需要 lower() 包裹, 用普通 gin_trgm_ops 即可
-- 分区表处理: 先 ON ONLY 建父索引, 再对 V1 已存在的 13 个分区
-- (202607~202706 + default) 逐个建同定义索引并 ATTACH PARTITION;
-- 索引名 schema 级唯一, 各分区索引名不可重复, 沿用 V1 <表名>_<列>_idx 模式
CREATE INDEX IF NOT EXISTS idx_conversation_message_content_trgm ON ONLY scrm.scrm_conversation_message USING gin (content gin_trgm_ops);

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202607_content_trgm_idx ON scrm.scrm_conversation_message_202607 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202607_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202608_content_trgm_idx ON scrm.scrm_conversation_message_202608 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202608_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202609_content_trgm_idx ON scrm.scrm_conversation_message_202609 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202609_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202610_content_trgm_idx ON scrm.scrm_conversation_message_202610 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202610_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202611_content_trgm_idx ON scrm.scrm_conversation_message_202611 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202611_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202612_content_trgm_idx ON scrm.scrm_conversation_message_202612 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202612_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202701_content_trgm_idx ON scrm.scrm_conversation_message_202701 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202701_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202702_content_trgm_idx ON scrm.scrm_conversation_message_202702 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202702_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202703_content_trgm_idx ON scrm.scrm_conversation_message_202703 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202703_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202704_content_trgm_idx ON scrm.scrm_conversation_message_202704 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202704_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202705_content_trgm_idx ON scrm.scrm_conversation_message_202705 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202705_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_202706_content_trgm_idx ON scrm.scrm_conversation_message_202706 USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_202706_content_trgm_idx;

CREATE INDEX IF NOT EXISTS scrm_conversation_message_default_content_trgm_idx ON scrm.scrm_conversation_message_default USING gin (content gin_trgm_ops);
ALTER INDEX scrm.idx_conversation_message_content_trgm ATTACH PARTITION scrm.scrm_conversation_message_default_content_trgm_idx;

-- ---------- 3. scrm_wework_archive_message 存档检索 (ScrmWeWorkArchiveService) ----------
-- 普通表(非分区), content 为解密后的消息内容 JSON, 检索用 ILIKE, 直接建 trgm 索引
CREATE INDEX IF NOT EXISTS idx_wework_archive_message_content_trgm ON scrm.scrm_wework_archive_message USING gin (content gin_trgm_ops);

-- ---------- 4. scrm_webhook_log 日志时间范围过滤 (ScrmWebhookService) ----------
-- 按 create_time 时间范围过滤, 普通 btree 即可
CREATE INDEX IF NOT EXISTS idx_webhook_log_create_time ON scrm.scrm_webhook_log (create_time);

-- ======================================================================
-- 索引服务位置与设计说明
-- ======================================================================
-- 1. 服务位置:
--    - ScrmCustomerService: 客户关键词搜索, 对 nickname / platform_customer_uid
--      执行 cb.like(cb.lower(col), '%x%')
--    - ScrmConversationMessageService: 消息检索, content 用 ContainingIgnoreCase -> ILIKE
--    - ScrmWeWorkArchiveService: 企微会话存档检索, content 用 ILIKE
--    - ScrmWebhookService: webhook 日志按时间范围过滤 (create_time)
-- 2. 为什么用 lower() 函数索引:
--    普通 gin_trgm_ops 索引只覆盖列本身, 无法匹配 lower(col) LIKE '%x%' 这类
--    表达式; 必须对 lower(col) 建函数索引, 查询才能走 Bitmap Index Scan。
--    content 场景查询是 ILIKE(不区分大小写), 无需 lower() 包裹, 直接
--    content gin_trgm_ops 即可命中。
-- 3. 为什么分区表逐区建索引:
--    PostgreSQL 对已存在分区不会因父表 CREATE INDEX(不带 ONLY) 自动补索引,
--    必须先 ON ONLY 建父索引, 再逐分区建同定义索引并 ATTACH PARTITION。
--    注意: 后续新增分区(如 202707...)时, 建分区 DDL 必须同步携带
--    scrm_conversation_message_<后缀>_content_trgm_idx 索引, 否则新分区
--    的 content 检索退化为全表扫描。
-- 4. 写放大提示:
--    GIN trgm 索引会显著放大写入量(每个 token 需更新索引), 尤其 content 是
--    大文本; 仅对热点检索列建索引, 避免对低频字段滥用。GIN 不支持
--    fillfactor 参数(仅 btree/hash 支持), 故不设置存储参数。
-- ======================================================================
