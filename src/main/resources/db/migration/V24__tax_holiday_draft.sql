-- ======================================================================
-- V24: 假期 AI 抽取草稿（人工确认为准）
--
-- 背景: 法定节假日每年由国务院办公厅发布，日期逐年变化且常伴调休。
--       手工维护容易遗漏，而一旦遗漏，申报截止日顺延会「静默失效」
--       （只跳周末、不报错），导致截止日算错、逾期判定错误。
--
-- 为什么 AI 结果不直接写 scrm_tax_holiday:
--   1. 假期数据直接决定截止日与逾期，是国家级的硬事实，绝不能由模型
--      自动写入并立即生效；
--   2. 模型可能幻觉出不存在的假期、算错区间、漏掉调休补班日；
--   3. 必须保留「来源公告纯文本 + 模型原始输出」以便人工核对。
--   因此 AI 产出只落草稿表，由人工确认后调用既有接口写入 scrm_tax_holiday。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

CREATE TABLE scrm.scrm_tax_holiday_draft (
    id                  bigint NOT NULL,
    create_time         timestamp without time zone NOT NULL,
    update_time         timestamp without time zone NOT NULL,
    version             bigint DEFAULT 0 NOT NULL,

    -- 目标年份
    holiday_year        integer NOT NULL,
    -- 来源公告标题与 URL（人工核对时可回溯）
    source_title        character varying(500),
    source_url          character varying(1000),
    -- 公告原文（截断保存，用于核对模型是否读错）
    source_text         text,
    -- 模型原始输出（JSON 字符串，不解析，便于排查幻觉）
    raw_model_output    text,

    -- 抽取出的假期明细（JSON 数组: [{name,startDate,endDate}]）
    holidays_json       text,
    -- 抽取出的调休上班日（JSON 数组: [{workDate,remark}]）
    workdays_json       text,

    -- 抽取方式: AI / MANUAL
    extract_mode        character varying(20) DEFAULT 'AI' NOT NULL,
    -- 状态: PENDING 待确认 / APPLIED 已采纳 / REJECTED 已丢弃
    draft_status        character varying(20) DEFAULT 'PENDING' NOT NULL,
    -- 模型标识（如 gpt-4o-mini / sensenova-6.8-flash-lite）
    model_name          character varying(100),
    -- 人工处理人/时间/备注
    reviewed_by         character varying(100),
    reviewed_at         timestamp without time zone,
    review_remark       character varying(500),

    CONSTRAINT pk_scrm_tax_holiday_draft PRIMARY KEY (id)
);

COMMENT ON TABLE scrm.scrm_tax_holiday_draft IS '假期 AI 抽取草稿；必须人工确认后才写入 scrm_tax_holiday，AI 结果绝不直接生效';
COMMENT ON COLUMN scrm.scrm_tax_holiday_draft.source_text IS '公告原文（核对模型是否误读的依据）';
COMMENT ON COLUMN scrm.scrm_tax_holiday_draft.raw_model_output IS '模型原始输出，保留用于排查幻觉';
COMMENT ON COLUMN scrm.scrm_tax_holiday_draft.draft_status IS 'PENDING 待确认 / APPLIED 已采纳 / REJECTED 已丢弃';

CREATE INDEX idx_th_draft_year_status ON scrm.scrm_tax_holiday_draft USING btree (holiday_year, draft_status);

-- ---------------------------------------------------------------------
-- 次年假期数据提醒模板
--
-- 假期缺失会让申报截止日顺延「静默失效」：不报错、但截止日算错。
-- 国务院办公厅通常在前一年 11 月发布次年安排，因此按年提醒运维补录。
-- ---------------------------------------------------------------------
INSERT INTO scrm.scrm_notification_template
    (id, template_name, template_code, category, channel, title, content, variables,
     enabled, usage_count, create_time, update_time, version)
VALUES
    (202611010000000001, '次年假期数据提醒', 'tax-holiday-year-remind', 'SYSTEM', 'IN_APP',
     '{year} 年法定节假日数据待维护',
     '{year} 年的法定节假日未录入/未确认(AI 自动抽取已重试仍失败时也会发本条)。假期缺失会导致申报截止日顺延退化为只跳周末，截止日可能算错。请前往「假期维护」页重新抽取或手工补录。',
     'year', true, 0, now(), now(), 0)
ON CONFLICT DO NOTHING;
