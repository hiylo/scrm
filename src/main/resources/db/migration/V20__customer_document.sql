-- ======================================================================
-- V20: 客户资料归档（资料域）
--
-- 背景: 代账公司接一户就要收一批资料——营业执照、开户许可证、法人身份证、
--       公司章程、租赁合同。过去这些文件散落在微信、邮箱和会计个人电脑里：
--       换会计找不到、年检时补不齐、客户流失后无法交接。
--       归档的关键不是"能传文件"，而是**能回答某户还缺哪些必备资料**。
--
-- 表设计:
--   scrm_customer_document 一户多份，按 doc_type 归口。
--   status ∈ ACTIVE/EXPIRED/ARCHIVED：
--     EXPIRED 表示资料本身过期（如身份证到期、租赁合同到期），
--     不是"文件被删"，删除是物理删行 + 删对象存储。
--
-- 说明:
--   文件本体存对象存储（scrm.storage.*，MinIO/OSS），本表只存 object_key。
--   不在库里存二进制——备份与迁移都靠对象存储，DB 只做元数据与检索。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

CREATE TABLE scrm.scrm_customer_document (
    id              bigint NOT NULL,
    create_time     timestamp without time zone NOT NULL,
    update_time     timestamp without time zone NOT NULL,
    version         bigint DEFAULT 0 NOT NULL,

    company_id      bigint NOT NULL,
    company_name    character varying(200),

    -- BUSINESS_LICENSE 营业执照 / BANK_PERMIT 开户许可证 / LEGAL_ID_CARD 法人身份证
    -- ARTICLES 公司章程 / LEASE_CONTRACT 租赁合同 / SEAL_RECORD 印章备案
    -- BANK_STATEMENT 银行对账单 / FINANCIAL_REPORT 财务报表 / TAX_FORM 涉税表单 / OTHER 其他
    doc_type        character varying(40) NOT NULL,
    doc_name        character varying(200) NOT NULL,

    -- 对象存储定位（不存二进制本体）
    object_key      character varying(500) NOT NULL,
    file_name       character varying(255),
    file_size       bigint,
    content_type    character varying(100),

    -- 资料自身的签发/到期日（如身份证有效期、租赁合同期），用于到期提醒
    issue_date      date,
    expire_date     date,

    -- ACTIVE 有效 / EXPIRED 已过期 / ARCHIVED 已归档封存
    status          character varying(20) DEFAULT 'ACTIVE' NOT NULL,

    remark          character varying(500),
    uploaded_by     character varying(100),
    uploaded_at     timestamp without time zone,

    CONSTRAINT pk_scrm_customer_document PRIMARY KEY (id)
);

COMMENT ON TABLE scrm.scrm_customer_document IS '客户资料归档（文件本体存对象存储，本表只存元数据与 object_key）';
COMMENT ON COLUMN scrm.scrm_customer_document.doc_type IS 'BUSINESS_LICENSE/BANK_PERMIT/LEGAL_ID_CARD/ARTICLES/LEASE_CONTRACT/SEAL_RECORD/BANK_STATEMENT/FINANCIAL_REPORT/TAX_FORM/OTHER';
COMMENT ON COLUMN scrm.scrm_customer_document.status IS 'ACTIVE 有效 / EXPIRED 资料已过期 / ARCHIVED 已归档封存';
COMMENT ON COLUMN scrm.scrm_customer_document.expire_date IS '资料自身到期日（身份证有效期、合同期限），用于到期提醒';

CREATE INDEX idx_document_company_type ON scrm.scrm_customer_document USING btree (company_id, doc_type);
CREATE INDEX idx_document_status_expire ON scrm.scrm_customer_document USING btree (status, expire_date);
CREATE INDEX idx_document_object_key ON scrm.scrm_customer_document USING btree (object_key);
