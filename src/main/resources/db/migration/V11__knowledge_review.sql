-- ======================================================================
-- 财税数字化 - 模块③ 知识库增强（V11）: 版本复核日期列
-- ======================================================================
-- 用途: 按 docs/财税数字化系统立项方案.md §2 模块③「版本与复核」与
--   docs/tasks/T06/knowledge-template.md（附录字段映射: 「系统可另加列
--   last_reviewed_at（属定制项）」）为知识库两个承载域补充「最后人工复核日期」
--   相关字段, 支撑:
--   1) T06 模板批量导入条目（sourceType=IMPORT, ScrmAiKnowledgeDocument）:
--      last_reviewed_at = 最近一次业务人员逐条复核日期;
--      next_review_at = 建议下次复核日期（复核周期默认 180 天, 可配置）;
--      review_cycle_days = 复核周期（默认 180, 政策类信息建议 ≤180 天复核一次,
--      knowledge-template.md 总则第 6 条）。
--   2) 知识库文章域版本复核（ScrmKnowledgeArticle + KnowledgeVersion）:
--      同一组字段, 复核动作（recordReview）更新 last_reviewed_at 并创建
--      KnowledgeVersion 快照（复用 ScrmKnowledgeVersionService.createVersion）,
--      「待复核」检索 = next_review_at < now() OR next_review_at IS NULL。
-- 数据库: PostgreSQL 16+   Schema: scrm
-- 幂等: 仅 ALTER TABLE ADD COLUMN + CREATE INDEX; 重跑由 Flyway 校验和阻止。
-- ======================================================================

-- 1. AI 知识库文档（批量导入条目）: 复核日期字段
ALTER TABLE scrm.scrm_ai_knowledge_document
    ADD COLUMN last_reviewed_at timestamp without time zone,
    ADD COLUMN next_review_at timestamp without time zone,
    ADD COLUMN review_cycle_days integer DEFAULT 180;

COMMENT ON COLUMN scrm.scrm_ai_knowledge_document.last_reviewed_at IS '最后人工复核日期: 最近一次业务人员逐条核对内容的日期 (YYYY-MM-DD)';
COMMENT ON COLUMN scrm.scrm_ai_knowledge_document.next_review_at IS '建议下次复核日期: 复核周期提醒, 默认 last_reviewed_at + 180 天';
COMMENT ON COLUMN scrm.scrm_ai_knowledge_document.review_cycle_days IS '复核周期(天): 政策类信息建议 ≤180 天, 默认 180';

CREATE INDEX idx_ai_doc_next_review ON scrm.scrm_ai_knowledge_document USING btree (next_review_at);

-- 2. 知识库文章（KnowledgeVersion 版本化域）: 复核日期字段
ALTER TABLE scrm.scrm_knowledge_article
    ADD COLUMN last_reviewed_at timestamp without time zone,
    ADD COLUMN next_review_at timestamp without time zone,
    ADD COLUMN review_cycle_days integer DEFAULT 180;

COMMENT ON COLUMN scrm.scrm_knowledge_article.last_reviewed_at IS '最后人工复核日期: 最近一次业务人员逐条核对内容的日期 (YYYY-MM-DD)';
COMMENT ON COLUMN scrm.scrm_knowledge_article.next_review_at IS '建议下次复核日期: 复核周期提醒, 默认 last_reviewed_at + 180 天';
COMMENT ON COLUMN scrm.scrm_knowledge_article.review_cycle_days IS '复核周期(天): 政策类信息建议 ≤180 天, 默认 180';

CREATE INDEX idx_knowledge_article_next_review ON scrm.scrm_knowledge_article USING btree (next_review_at);

-- 3. AI 对话记录: 知识库引用溯源 JSON（模块③ 引用溯源: 答复附知识条目 id/title）
ALTER TABLE scrm.scrm_ai_conversation
    ADD COLUMN knowledge_citations text;

COMMENT ON COLUMN scrm.scrm_ai_conversation.knowledge_citations IS '知识库引用溯源 (JSON 数组): AI 答复来源的知识条目 id/title, 空表示未附引用';