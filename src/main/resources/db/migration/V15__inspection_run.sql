-- ======================================================================
-- 财税数字化 - 巡检/抓取执行历史（V15）
-- ======================================================================
-- 用途: 记录每一轮工商巡检与税务公告抓取的执行结果, 解决「过去 N 天跑了几次、
--       成功几次、命中几条、为什么跳过」无法回溯的问题。
--       此前只能看到任务上的 last_result 单点值, 出问题只能翻日志。
--   1. run_type: BUSINESS(工商 API 巡检) / TAX(税务公告抓取)
--   2. trigger_type: SCHEDULED(定时调度) / MANUAL(手动触发)
--   3. status: SUCCESS / PARTIAL / FAILED / SKIPPED
--      - SKIPPED 表示按开关整体跳过（如启信宝未启用）, 不是失败
--      - PARTIAL 表示部分任务失败或部分命中
--   4. detail: 各类型的补充指标 JSON（税务: recordsMatched/captchaBlocked）
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

CREATE TABLE scrm.scrm_inspection_run (
    id              bigint NOT NULL,
    create_time     timestamp without time zone NOT NULL,
    update_time     timestamp without time zone NOT NULL,
    version         bigint DEFAULT 0 NOT NULL,
    run_type        character varying(20) NOT NULL,
    trigger_type    character varying(20) NOT NULL,
    status          character varying(20) NOT NULL,
    task_scanned    integer DEFAULT 0 NOT NULL,
    success_count   integer DEFAULT 0 NOT NULL,
    failed_count    integer DEFAULT 0 NOT NULL,
    changed_count   integer DEFAULT 0 NOT NULL,
    skip_reason     character varying(255),
    error_message   character varying(1000),
    detail          character varying(2000),
    operator        character varying(100),
    started_at      timestamp without time zone NOT NULL,
    finished_at     timestamp without time zone,
    cost_ms         bigint,
    CONSTRAINT pk_scrm_inspection_run PRIMARY KEY (id)
);

COMMENT ON TABLE scrm.scrm_inspection_run IS '巡检/抓取执行历史: 每轮工商巡检与税务抓取一条, 供运营回溯与统计';
COMMENT ON COLUMN scrm.scrm_inspection_run.run_type IS '执行类型: BUSINESS(工商 API 巡检) / TAX(税务公告抓取)';
COMMENT ON COLUMN scrm.scrm_inspection_run.trigger_type IS '触发方式: SCHEDULED(定时) / MANUAL(手动)';
COMMENT ON COLUMN scrm.scrm_inspection_run.status IS '结果: SUCCESS / PARTIAL / FAILED / SKIPPED(按开关整体跳过, 非失败)';
COMMENT ON COLUMN scrm.scrm_inspection_run.task_scanned IS '扫描的任务数';
COMMENT ON COLUMN scrm.scrm_inspection_run.success_count IS '工商: 成功任务数; 税务: 命中记录数';
COMMENT ON COLUMN scrm.scrm_inspection_run.failed_count IS '失败任务数';
COMMENT ON COLUMN scrm.scrm_inspection_run.changed_count IS '工商: 状态变化数; 税务: 验证码阻断数';
COMMENT ON COLUMN scrm.scrm_inspection_run.skip_reason IS '整体跳过原因（如启信宝未启用）';
COMMENT ON COLUMN scrm.scrm_inspection_run.detail IS '补充指标 JSON（税务: recordsMatched/captchaBlocked）';
COMMENT ON COLUMN scrm.scrm_inspection_run.operator IS '手动触发时的操作人';
COMMENT ON COLUMN scrm.scrm_inspection_run.cost_ms IS '本轮耗时（毫秒）';

CREATE INDEX idx_inspection_run_type_time ON scrm.scrm_inspection_run USING btree (run_type, started_at DESC);
CREATE INDEX idx_inspection_run_status ON scrm.scrm_inspection_run USING btree (status);
