-- ======================================================================
-- SCRM 出站消息队列 (scrm_outbound_message)
--
-- 用途:
--   1. scrm 决策的回复/触达内容, 当目标平台无官方开放 API (个人微信 wechat_personal 等)
--      时, 回复内容写入本表形成待发送队列。
--   2. 执行侧 (wx-console / wechat-agent) 轮询拉取 PENDING 消息, 调用本地
--      UIA/协议库发送后, 通过 /scrm/callback/outbound/ack 回写 SENT/FAILED。
--   3. 状态机: PENDING -> IN_PROGRESS -> SENT | FAILED
--      IN_PROGRESS 超时 (in_progress_at + timeout) 由扫描任务退回 PENDING 重试。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- 说明:
--   - 普通表 (不分区): 出站消息量远小于会话消息 (个人微信场景日均百级), 普通表即可。
--   - retry_count / max_retries: 执行侧 ack FAILED 时累加, 超上限进入 FAILED 终态。
--   - 乐观锁 version: 并发取走 / ack 时由 Hibernate @Version 保证不互相覆盖。
-- ======================================================================

CREATE TABLE scrm.scrm_outbound_message (
    id                 bigint NOT NULL,
    message_id         bigint,
    business_message_id character varying(100),
    conversation_id    bigint NOT NULL,
    account_id         bigint NOT NULL,
    platform_type      character varying(30) NOT NULL,
    target_platform_id character varying(200) NOT NULL,
    message_type       character varying(20) NOT NULL,
    content            text,
    source             character varying(20) NOT NULL,
    status             character varying(20) NOT NULL,
    retry_count        integer DEFAULT 0 NOT NULL,
    max_retries        integer DEFAULT 3 NOT NULL,
    in_progress_at     timestamp without time zone,
    completed_at       timestamp without time zone,
    error_message      character varying(500),
    next_retry_at      timestamp without time zone,
    create_time        timestamp without time zone NOT NULL,
    update_time        timestamp without time zone NOT NULL,
    version            bigint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_outbound_message PRIMARY KEY (id)
);

-- 查询索引: 按平台 + 状态拉取待发送 (执行侧轮询主路径)
CREATE INDEX idx_outbound_platform_status
    ON scrm.scrm_outbound_message USING btree (platform_type, status);

-- 超时回退查询: IN_PROGRESS 超时扫描 (in_progress_at 早于阈值)
CREATE INDEX idx_outbound_in_progress_time
    ON scrm.scrm_outbound_message USING btree (status, in_progress_at);

-- 会话维度查询 (人工坐席查看某会话的出站状态)
CREATE INDEX idx_outbound_conversation
    ON scrm.scrm_outbound_message USING btree (conversation_id);

-- 业务消息 ID 防重复入队
CREATE INDEX idx_outbound_business_message
    ON scrm.scrm_outbound_message USING btree (business_message_id);

-- 账号维度查询
CREATE INDEX idx_outbound_account
    ON scrm.scrm_outbound_message USING btree (account_id);
