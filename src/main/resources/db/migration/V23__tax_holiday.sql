-- ======================================================================
-- V23: 税务假期表（法定节假日顺延专用）
--
-- 背景: 申报截止日遇法定节假日必须顺延，此前只跳周末（见
--       ScrmTaxDeclarationService#nextWorkday），跨国庆/春节时截止日会算错。
--
-- 为什么不用 scrm_calendar_holiday:
--   那张表是"营销节日/纪念日库"，语义是给运营做节日营销规划用的
--   （含 marketing_opportunity、suggested_channels 等营销字段，
--   holiday_date 还支持固定的 MM-dd 农历/公历混合写法）。
--   把法定节假日塞进去会有两个问题:
--     1. 营销日历上会混入"国庆""春节"这类非营销节点；
--     2. 税务顺延需要的是"确切年份的具体日期区间"（法定节假日每年由国务院
--        公告，日期逐年变化），与营销表的固定 MM-dd 语义冲突。
--   因此单独建表，只服务税务截止日计算。
--
-- 设计要点:
--   - start_date / end_date 用 date 类型（不是字符串），跨天假期直接给定区间，
--     避免"起始日 + 天数"在调休场景下算错；
--   - year 单独冗余，便于按年导入与核对"该年数据是否齐备"；
--   - 调休上班日（周末补班）单独用 workday_override 表记录，
--     否则国庆调休后的周六会被误判为休息日而把截止日往后推。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

CREATE TABLE scrm.scrm_tax_holiday (
    id                  bigint NOT NULL,
    create_time         timestamp without time zone NOT NULL,
    update_time         timestamp without time zone NOT NULL,
    version             bigint DEFAULT 0 NOT NULL,

    -- 节假日名称（如 国庆节、春节）
    holiday_name        character varying(100) NOT NULL,
    -- 所属年份，便于按年核对数据完整性
    holiday_year        integer NOT NULL,
    -- 假期起止（含首尾）。跨天假期必须给完整区间，不能只给起始日
    start_date          date NOT NULL,
    end_date            date NOT NULL,

    -- 法定 / 调休（调休日为法定假期的调休安排，同样不可申报）
    holiday_kind        character varying(20) DEFAULT 'STATUTORY' NOT NULL,
    -- 说明（如"国务院办公厅通知"编号）
    remark              character varying(500),
    is_active           boolean DEFAULT true NOT NULL,

    CONSTRAINT pk_scrm_tax_holiday PRIMARY KEY (id),
    CONSTRAINT ck_tax_holiday_range CHECK (end_date >= start_date)
);

COMMENT ON TABLE scrm.scrm_tax_holiday IS '税务假期（法定节假日区间，用于申报截止日顺延）';
COMMENT ON COLUMN scrm.scrm_tax_holiday.start_date IS '假期起始日（含）';
COMMENT ON COLUMN scrm.scrm_tax_holiday.end_date IS '假期结束日（含）；跨天假期必须给完整区间';
COMMENT ON COLUMN scrm.scrm_tax_holiday.holiday_kind IS 'STATUTORY 法定节假日 / ADJUSTED 调休放假';

CREATE INDEX idx_tax_holiday_year ON scrm.scrm_tax_holiday USING btree (holiday_year, start_date);
CREATE INDEX idx_tax_holiday_range ON scrm.scrm_tax_holiday USING btree (start_date, end_date)
    WHERE is_active = true;

-- ---------------------------------------------------------------------
-- 调休上班日：周末但需正常上班（如国庆前后的补班周六）
--
-- 没有这张表时，10-10（周六补班）会被 nextWorkday 误判为休息日，
-- 把本应落在当天的截止日推后，导致申报逾期判定错误。
-- ---------------------------------------------------------------------
CREATE TABLE scrm.scrm_tax_workday_override (
    id                  bigint NOT NULL,
    create_time         timestamp without time zone NOT NULL,
    update_time         timestamp without time zone NOT NULL,
    version             bigint DEFAULT 0 NOT NULL,

    -- 需上班的日期（通常是周末）
    work_date           date NOT NULL,
    remark              character varying(500),
    is_active           boolean DEFAULT true NOT NULL,

    CONSTRAINT pk_scrm_tax_workday_override PRIMARY KEY (id),
    CONSTRAINT uk_tax_workday_override UNIQUE (work_date)
);

COMMENT ON TABLE scrm.scrm_tax_workday_override IS '调休上班日（周末补班，申报截止日计算时视为工作日）';

-- ---------------------------------------------------------------------
-- 2026 年法定节假日种子数据
--
-- 依据国务院办公厅 2026 年节假日安排。数据每年更新，此处仅提供首年基线，
-- 保证顺延逻辑上线即可用；后续年份由运维按年补录（新增迁移或后台维护）。
-- ---------------------------------------------------------------------
INSERT INTO scrm.scrm_tax_holiday
    (id, create_time, update_time, version, holiday_name, holiday_year, start_date, end_date, holiday_kind, remark)
VALUES
    (202601010000000001, now(), now(), 0, '元旦',   2026, DATE '2026-01-01', DATE '2026-01-03', 'STATUTORY', '国务院办公厅 2026 年节假日安排'),
    (202601010000000002, now(), now(), 0, '春节',   2026, DATE '2026-02-15', DATE '2026-02-22', 'STATUTORY', '国务院办公厅 2026 年节假日安排'),
    (202601010000000003, now(), now(), 0, '清明节', 2026, DATE '2026-04-04', DATE '2026-04-06', 'STATUTORY', '国务院办公厅 2026 年节假日安排'),
    (202601010000000004, now(), now(), 0, '劳动节', 2026, DATE '2026-05-01', DATE '2026-05-05', 'STATUTORY', '国务院办公厅 2026 年节假日安排'),
    (202601010000000005, now(), now(), 0, '端午节', 2026, DATE '2026-06-19', DATE '2026-06-21', 'STATUTORY', '国务院办公厅 2026 年节假日安排'),
    (202601010000000006, now(), now(), 0, '中秋节', 2026, DATE '2026-09-25', DATE '2026-09-27', 'STATUTORY', '国务院办公厅 2026 年节假日安排'),
    (202601010000000007, now(), now(), 0, '国庆节', 2026, DATE '2026-10-01', DATE '2026-10-08', 'STATUTORY', '国务院办公厅 2026 年节假日安排')
ON CONFLICT DO NOTHING;

-- 2026 年调休上班日（周末补班）
INSERT INTO scrm.scrm_tax_workday_override
    (id, create_time, update_time, version, work_date, remark)
VALUES
    (202602010000000001, now(), now(), 0, DATE '2026-02-14', '春节调休上班'),
    (202602010000000002, now(), now(), 0, DATE '2026-02-28', '春节调休上班'),
    (202602010000000003, now(), now(), 0, DATE '2026-05-09', '劳动节调休上班'),
    (202602010000000004, now(), now(), 0, DATE '2026-09-19', '中秋/国庆调休上班'),
    (202602010000000005, now(), now(), 0, DATE '2026-10-10', '国庆调休上班')
ON CONFLICT DO NOTHING;
