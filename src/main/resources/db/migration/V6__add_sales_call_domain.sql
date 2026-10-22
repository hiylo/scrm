-- ======================================================================
-- SCRM 销售外呼域（V6）：客户联系方式 / 通话记录 / 录音文件
-- ======================================================================
-- 用途: 支持销售手机端 App 的外呼闭环。
--   1. scrm_customer_contact: 客户手机号（一个客户可多个号码, 主号码为默认）。
--      App 端据此做「保存到系统通讯录 + 去重 + 点号拨打」。
--   2. scrm_call_record: 通话记录, source 区分来源:
--      APP_CALL(App 内拨号) / CALL_LOG(系统通话记录) / SCREENING(来电识别角色) / MANUAL(手工补录)。
--      peer_number 落库前应做脱敏(可存哈希 peer_hash + 明文仅存必要), 由服务层决定。
--   3. scrm_recording: 录音文件元数据, source 区分:
--      OEM_RECORD(系统自带录音文件上传) / LINE(线路录音) / MANUAL(手动上传)。
--      文件本体存对象存储(MinIO/OSS), 本表仅存 object_key。
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

-- 1. 客户联系方式
CREATE TABLE scrm.scrm_customer_contact (
    id           bigint NOT NULL,
    create_time  timestamp without time zone NOT NULL,
    update_time  timestamp without time zone NOT NULL,
    version      bigint DEFAULT 0 NOT NULL,
    customer_id  bigint NOT NULL,
    phone        character varying(32) NOT NULL,
    phone_type   character varying(20) DEFAULT 'MOBILE' NOT NULL,
    is_primary   boolean DEFAULT true NOT NULL,
    remark       character varying(200)
);
COMMENT ON TABLE scrm.scrm_customer_contact IS '客户联系方式: 一个客户可拥有多个号码, is_primary 标记默认拨打号码';
COMMENT ON COLUMN scrm.scrm_customer_contact.phone_type IS '号码类型: MOBILE(手机) / LANDLINE(固话) / OTHER(其他)';
COMMENT ON COLUMN scrm.scrm_customer_contact.is_primary IS '是否主号码: 同一客户仅允许一个 true (服务层保证)';

ALTER TABLE scrm.scrm_customer_contact
    ADD CONSTRAINT pk_customer_contact PRIMARY KEY (id);
CREATE INDEX idx_customer_contact_customer ON scrm.scrm_customer_contact USING btree (customer_id);
CREATE INDEX idx_customer_contact_phone   ON scrm.scrm_customer_contact USING btree (phone);
-- 同一客户号码不重复
CREATE UNIQUE INDEX uk_customer_contact_phone ON scrm.scrm_customer_contact USING btree (customer_id, phone);

-- 2. 通话记录
CREATE TABLE scrm.scrm_call_record (
    id            bigint NOT NULL,
    create_time   timestamp without time zone NOT NULL,
    update_time   timestamp without time zone NOT NULL,
    version       bigint DEFAULT 0 NOT NULL,
    user_id       character varying(64) NOT NULL,
    device_id     character varying(128),
    customer_id   bigint,
    direction     character varying(16) NOT NULL,
    peer_number   character varying(32),
    peer_hash     character varying(64),
    start_at      timestamp without time zone NOT NULL,
    duration_sec  integer DEFAULT 0 NOT NULL,
    connected     boolean DEFAULT false NOT NULL,
    source        character varying(16) NOT NULL,
    call_log_id   bigint,
    remark        character varying(500)
);
COMMENT ON TABLE scrm.scrm_call_record IS '销售通话记录: 外呼/呼入/未接, 来源区分 App 拨号/系统话单/来电识别/手工补录';
COMMENT ON COLUMN scrm.scrm_call_record.direction IS '方向: OUT(呼出) / IN(呼入) / MISSED(未接) / REJECTED(拒接)';
COMMENT ON COLUMN scrm.scrm_call_record.connected IS '是否接通(仅接通后才计入通话时长)';
COMMENT ON COLUMN scrm.scrm_call_record.source IS '来源: APP_CALL / CALL_LOG / SCREENING / MANUAL';
COMMENT ON COLUMN scrm.scrm_call_record.call_log_id IS '系统 CallLog._ID, 与 device_id 构成客户端去重唯一键';

ALTER TABLE scrm.scrm_call_record
    ADD CONSTRAINT pk_call_record PRIMARY KEY (id);
CREATE INDEX idx_call_record_user_time ON scrm.scrm_call_record USING btree (user_id, start_at DESC);
CREATE INDEX idx_call_record_customer   ON scrm.scrm_call_record USING btree (customer_id);
-- 同一设备同一条系统话单只入一次
CREATE UNIQUE INDEX uk_call_record_dev_log ON scrm.scrm_call_record USING btree (device_id, call_log_id);

-- 3. 录音文件
CREATE TABLE scrm.scrm_recording (
    id            bigint NOT NULL,
    create_time   timestamp without time zone NOT NULL,
    update_time   timestamp without time zone NOT NULL,
    version       bigint DEFAULT 0 NOT NULL,
    user_id       character varying(64) NOT NULL,
    device_id     character varying(128),
    call_record_id bigint,
    customer_id   bigint,
    object_key    character varying(500) NOT NULL,
    file_name     character varying(255),
    file_size     bigint,
    duration_sec  integer,
    source        character varying(16) NOT NULL,
    file_hash     character varying(64) NOT NULL,
    status        character varying(20) DEFAULT 'UPLOADED' NOT NULL
);
COMMENT ON TABLE scrm.scrm_recording IS '通话录音文件元数据: 文件本体存对象存储, 本表记录 object_key 与归属';
COMMENT ON COLUMN scrm.scrm_recording.source IS '来源: OEM_RECORD(系统自带录音) / LINE(线路录音) / MANUAL(手动上传)';
COMMENT ON COLUMN scrm.scrm_recording.file_hash IS '文件内容 SHA-256, 用于服务端去重(同一文件重复上传只保留一份)';
COMMENT ON COLUMN scrm.scrm_recording.status IS '状态: UPLOADED(已上传) / DELETED(已删除)';

ALTER TABLE scrm.scrm_recording
    ADD CONSTRAINT pk_recording PRIMARY KEY (id);
CREATE INDEX idx_recording_call    ON scrm.scrm_recording USING btree (call_record_id);
CREATE INDEX idx_recording_user    ON scrm.scrm_recording USING btree (user_id, create_time DESC);
CREATE INDEX idx_recording_customer ON scrm.scrm_recording USING btree (customer_id);
-- 同一文件哈希只保留一份（重复上传幂等）
CREATE UNIQUE INDEX uk_recording_hash ON scrm.scrm_recording USING btree (file_hash);
