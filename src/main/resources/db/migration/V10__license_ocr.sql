-- ======================================================================
-- 财税数字化 - 模块② 证照 OCR 识别任务域（V10）
-- ======================================================================
-- 用途: 按 docs/财税数字化系统立项方案.md §2 模块②A（证件/资料 OCR 识别, P1）
--   与 docs/tasks/T07/ocr-api.md（REST 契约）/ license-fields.md（证照字段模板）,
--   承接「证照上传 → OCR 识别 → 结果落库 → 低置信度字段人工复核」全链路。
--   1. scrm_license_ocr_task: 证照识别任务（一次上传 = 一条任务）。
--      - license_type: license(营业执照) / idcard(身份证) / account_license(开户许可证)
--        / general(通用), 对齐契约 §2.2;
--      - task_status: PENDING(已建/待识别) / SUCCEEDED(识别成功且无需复核)
--        / PARTIAL(识别成功但存在待核对项) / FAILED(调用失败或引擎侧 ok=false);
--      - review_status: NONE(不要求复核) / REQUIRED(必填字段 status!=ok 或
--        verification.ok=false → 转人工复核) / REVIEWED(已复核通过);
--      - fields_json / verification_json / lines_json / raw_text: 识别结果与校验
--        明细。身份证号 / 银行账号等敏感字段含在 JSON 中, 实体层用
--        EncryptedStringConverter 整列 AES-GCM 加密落库, 日志与错误信息一律脱敏;
--      - object_key: 证照原图/PDF 存 MinIO（ConversationMedia/对象存储影像域）,
--        OCR 服务不留副本（deploy.md §7 红线）;
--      - customer_id / business_id 可空: 关联客户与目标档案域业务单号（合同/发票等,
--        一期不做强外键）, owner_account_id 数据隔离。
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

CREATE TABLE scrm.scrm_license_ocr_task (
    id                  bigint NOT NULL,
    create_time         timestamp without time zone NOT NULL,
    update_time         timestamp without time zone NOT NULL,
    version             bigint DEFAULT 0 NOT NULL,
    customer_id         bigint,
    business_id         bigint,
    owner_account_id    bigint,
    license_type        character varying(30) NOT NULL,
    side                character varying(10),
    file_name           character varying(255),
    object_key          character varying(500),
    request_id          character varying(100),
    mode                character varying(20),
    engine              character varying(30),
    doc_type            character varying(30),
    task_status         character varying(20) NOT NULL,
    overall_ok          boolean,
    fields_json         text,
    verification_json   text,
    lines_json          text,
    raw_text            text,
    warnings_json       text,
    review_status       character varying(20) NOT NULL,
    review_fields_json  text,
    review_remark       character varying(1000),
    reviewer            character varying(100),
    reviewed_at         timestamp without time zone,
    error_message       character varying(1000),
    created_by          character varying(100)
);
COMMENT ON TABLE scrm.scrm_license_ocr_task IS '证照 OCR 识别任务: 一次上传=一条任务, 承接识别结果落库与低置信度字段人工复核';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.license_type IS '识别类型: license(营业执照) / idcard(身份证) / account_license(开户许可证) / general(通用)';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.side IS '身份证翻面: front(人像面) / back(国徽面), 其他类型为空';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.task_status IS '任务状态: PENDING / SUCCEEDED(无需复核) / PARTIAL(需复核) / FAILED';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.overall_ok IS 'OCR 顶层 ok: false 表示引擎缺件/图片损坏 (契约 ok=false + error)';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.fields_json IS '识别字段列表 JSON (含 confidence/verified/status, 敏感字段整列加密)';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.verification_json IS '交叉校验结果 JSON {ok,checked,problems}';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.lines_json IS '原始文本行 JSON (坐标+置信度, 人工复核阅读原文用)';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.raw_text IS 'OCR 全文 (人工复核参考, 加密落库)';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.review_status IS '复核状态: NONE(无需复核) / REQUIRED(低置信必填/校验不过→转人工) / REVIEWED(已复核)';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.review_fields_json IS '待复核字段清单 JSON [{key,name,status,problems}]';
COMMENT ON COLUMN scrm.scrm_license_ocr_task.object_key IS '证照原图/PDF 对象存储 key (ConversationMedia/影像域, 不留 OCR 服务端)';

ALTER TABLE scrm.scrm_license_ocr_task
    ADD CONSTRAINT pk_license_ocr_task PRIMARY KEY (id);
CREATE INDEX idx_license_ocr_customer ON scrm.scrm_license_ocr_task USING btree (customer_id);
CREATE INDEX idx_license_ocr_business ON scrm.scrm_license_ocr_task USING btree (business_id);
CREATE INDEX idx_license_ocr_owner    ON scrm.scrm_license_ocr_task USING btree (owner_account_id);
CREATE INDEX idx_license_ocr_status   ON scrm.scrm_license_ocr_task USING btree (task_status);
CREATE INDEX idx_license_ocr_review   ON scrm.scrm_license_ocr_task USING btree (review_status);
CREATE INDEX idx_license_ocr_type     ON scrm.scrm_license_ocr_task USING btree (license_type);