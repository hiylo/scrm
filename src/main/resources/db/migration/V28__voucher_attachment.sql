-- ======================================================================
-- V28: 凭证原始单据（电子附件）
--
-- 背景:
--   V21 的 scrm_voucher 只有一个 attachment_count 整数，意思是「附件几张」，
--   是会计手工填的数字——没有文件可看。可原始单据（发票、银行回单、合同、
--   工资表）是记账凭证的法定留存物：审计、税务检查、客户对账时都要能翻出来。
--   只记张数不存文件，等于"知道有几张，但一张都拿不出来"。
--
-- 表设计:
--   scrm_voucher_attachment 一张凭证挂多份单据，一份单据一行。
--   文件本体存对象存储（scrm.storage.*，MinIO/OSS），本表只存 object_key，
--   与 V20 资料归档同口径——备份/迁移靠对象存储，DB 只做元数据与检索。
--
--   与 attachment_count 的关系:
--     两者并存，语义不同、不互相覆盖。
--       attachment_count → 「纸质附件张数」，会计手工声明（可能未扫描）；
--       attachment 表   → 「电子单据」，真实可下载的文件。
--     前端把两者分列展示（附件 3 张 / 单据 2 份），不改 V21 字段，
--     避免历史数据被误解或旧报表口径漂移。
--
--   删除语义:
--     凭证物理删除（仅草稿）时级联删除本表行；对象存储对象由服务层尽力删除，
--     失败只记日志不阻断事务（孤儿对象优于坏数据），与 V20 一致。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

CREATE TABLE scrm.scrm_voucher_attachment (
    id              bigint NOT NULL,
    create_time     timestamp without time zone NOT NULL,
    update_time     timestamp without time zone NOT NULL,
    version         bigint DEFAULT 0 NOT NULL,

    -- 归属：凭证 + 冗余主体（列表按主体过滤时免 join 凭证表）
    voucher_id      bigint NOT NULL,
    company_id      bigint NOT NULL,

    -- 对象存储定位（不存二进制本体）
    object_key      character varying(500) NOT NULL,
    file_name       character varying(255),
    file_size       bigint,
    content_type    character varying(100),

    -- 单据类型: INVOICE 发票 / BANK_RECEIPT 银行回单 / CONTRACT 合同
    --           PAYROLL 工资表 / RECEIPT 收据 / OTHER 其他
    doc_type        character varying(40) DEFAULT 'OTHER' NOT NULL,
    remark          character varying(500),

    uploaded_by     character varying(100),
    uploaded_at     timestamp without time zone,

    CONSTRAINT pk_scrm_voucher_attachment PRIMARY KEY (id)
);

COMMENT ON TABLE scrm.scrm_voucher_attachment IS
    '凭证原始单据（电子附件）: 文件本体存对象存储, 本表只存元数据与 object_key (V28)';
COMMENT ON COLUMN scrm.scrm_voucher_attachment.voucher_id IS
    '所属凭证 ID, 凭证物理删除(仅草稿)时级联清理';
COMMENT ON COLUMN scrm.scrm_voucher_attachment.company_id IS
    '冗余主体 ID: 列表按主体过滤免 join, 越权校验直接可用';
COMMENT ON COLUMN scrm.scrm_voucher_attachment.doc_type IS
    'INVOICE 发票 / BANK_RECEIPT 银行回单 / CONTRACT 合同 / PAYROLL 工资表 / RECEIPT 收据 / OTHER 其他';

-- 按凭证取单据列表（详情抽屉主查询）
CREATE INDEX idx_voucher_attachment_voucher
    ON scrm.scrm_voucher_attachment USING btree (voucher_id, uploaded_at DESC);

-- 列表页统计每张凭证的单据数（一次 group by 出全页）
CREATE INDEX idx_voucher_attachment_company
    ON scrm.scrm_voucher_attachment USING btree (company_id, voucher_id);

-- 对象存储 key 唯一: 同一对象不允许被两条记录引用, 避免删除时互相打脸
CREATE UNIQUE INDEX uk_voucher_attachment_object_key
    ON scrm.scrm_voucher_attachment USING btree (object_key);
