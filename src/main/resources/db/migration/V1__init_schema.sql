-- ======================================================================
-- SCRM 自动化运营引擎 - 数据库初始化脚本
-- ======================================================================
-- 用途: 企业微信 SCRM 系统完整的数据库初始化脚本
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- 说明:
--   1. 本脚本由 101 个 Flyway 增量迁移脚本合并而成, 消除中间态
--   2. 主键 BIGINT 不自增, 由应用层雪花 ID 生成
--   3. 所有业务表均含 create_time / update_time / version
--   4. scrm_conversation_message 按 sent_at 月分区, 预建 12 个月分区 + default
--   5. 无种子数据 INSERT
-- ======================================================================

-- ---------- 0. 创建 schema ----------
CREATE SCHEMA IF NOT EXISTS scrm;
SET search_path TO scrm, public;

-- A/B 测试表
CREATE TABLE scrm.scrm_ab_test (
    id bigint NOT NULL,
    test_name character varying(200) NOT NULL,
    test_code character varying(50) NOT NULL,
    description character varying(500),
    test_type character varying(30) NOT NULL,
    test_objective character varying(50) NOT NULL,
    metric_definition text,
    target_segment character varying(500),
    sample_size integer,
    confidence_level double precision DEFAULT 0.95 NOT NULL,
    significance_threshold double precision DEFAULT 0.05 NOT NULL,
    traffic_allocation integer DEFAULT 100 NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    start_date date NOT NULL,
    end_date date,
    actual_start_date date,
    actual_end_date date,
    winner_variant_id bigint,
    winner_variant_name character varying(100),
    total_participants integer DEFAULT 0,
    total_conversions integer DEFAULT 0,
    conclusion character varying(2000),
    is_significant boolean DEFAULT false NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    campaign_id bigint,
    campaign_name character varying(200),
    hypothesis character varying(1000),
    metric character varying(50) DEFAULT 'CONVERSION_RATE'::character varying NOT NULL,
    secondary_metrics character varying(500),
    variants text DEFAULT '[]'::text NOT NULL,
    variant_count integer DEFAULT 2 NOT NULL,
    traffic_split character varying(200) DEFAULT '50,50'::character varying NOT NULL,
    target_sample_size integer,
    min_sample_size integer,
    current_sample_size integer,
    winning_variant character varying(100),
    winner_confidence double precision,
    winner_improvement double precision,
    significance_level double precision,
    statistical_method character varying(50) DEFAULT 'CHI_SQUARE'::character varying NOT NULL,
    p_value double precision,
    confidence_interval character varying(200),
    effect_size double precision,
    power double precision,
    recommendation character varying(2000),
    duration_days integer,
    stopping_rule character varying(200),
    auto_stop boolean DEFAULT false NOT NULL,
    stopped_at date,
    stopped_reason character varying(500),
    analysis_result text,
    last_analyzed_at timestamp without time zone,
    tags character varying(500)
);
-- A/B 测试分配
CREATE TABLE scrm.scrm_ab_test_assignment (
    id bigint NOT NULL,
    test_id bigint NOT NULL,
    variant_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    assigned_at timestamp without time zone NOT NULL,
    assignment_method character varying(20) DEFAULT 'RANDOM'::character varying NOT NULL,
    converted boolean DEFAULT false NOT NULL,
    converted_at timestamp without time zone,
    conversion_value double precision DEFAULT 0,
    engagement_data text,
    session_id character varying(200),
    metadata text,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- A/B 测试变体
CREATE TABLE scrm.scrm_ab_test_variant (
    id bigint NOT NULL,
    test_id bigint NOT NULL,
    variant_name character varying(100) NOT NULL,
    variant_code character varying(50) NOT NULL,
    variant_type character varying(20) DEFAULT 'VARIANT'::character varying NOT NULL,
    description character varying(500),
    content_config text,
    traffic_percent integer DEFAULT 50 NOT NULL,
    is_control boolean DEFAULT false NOT NULL,
    participants integer DEFAULT 0,
    conversions integer DEFAULT 0,
    conversion_rate double precision DEFAULT 0,
    revenue double precision DEFAULT 0,
    avg_order_value double precision DEFAULT 0,
    engagement_score double precision DEFAULT 0,
    is_winner boolean DEFAULT false NOT NULL,
    color character varying(20),
    sort_order integer DEFAULT 0,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 平台账号表
CREATE TABLE scrm.scrm_account (
    id bigint NOT NULL,
    platform_type character varying(30) NOT NULL,
    platform_account_uid character varying(200) NOT NULL,
    display_name character varying(200),
    avatar_url character varying(500),
    device_id character varying(100),
    persona_id character varying(100),
    login_state character varying(20) DEFAULT 'UNKNOWN'::character varying NOT NULL,
    last_login_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    account_name character varying(200) DEFAULT NULL::character varying
);
-- 账号健康度检测记录表
CREATE TABLE scrm.scrm_account_health (
    id bigint NOT NULL,
    account_id bigint NOT NULL,
    check_result character varying(20) NOT NULL,
    previous_state character varying(20),
    current_state character varying(20),
    detail text,
    checked_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 登录态日志表
CREATE TABLE scrm.scrm_account_login_log (
    id bigint NOT NULL,
    account_id bigint NOT NULL,
    from_state character varying(20),
    to_state character varying(20) NOT NULL,
    reason character varying(500),
    operate_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- AI 助手配置
CREATE TABLE scrm.scrm_ai_assistant_config (
    id bigint NOT NULL,
    config_name character varying(200) NOT NULL,
    provider character varying(50) NOT NULL,
    model character varying(100) DEFAULT 'gpt-4o-mini'::character varying NOT NULL,
    api_key character varying(500),
    api_endpoint character varying(500),
    system_prompt text,
    temperature double precision DEFAULT 0.7,
    max_tokens integer DEFAULT 1000,
    knowledge_base_id bigint,
    enabled boolean DEFAULT true NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    request_count integer DEFAULT 0,
    last_used_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- AI 对话记录
CREATE TABLE scrm.scrm_ai_conversation (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    conversation_id bigint,
    user_message text NOT NULL,
    detected_intent character varying(100),
    intent_confidence double precision DEFAULT 0,
    sentiment character varying(20),
    sentiment_score double precision DEFAULT 0,
    recommended_replies text,
    used_reply character varying(2000),
    ai_response character varying(2000),
    response_time_ms integer,
    config_id bigint,
    feedback character varying(20),
    created_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- AI 意图识别
CREATE TABLE scrm.scrm_ai_intent (
    id bigint NOT NULL,
    intent_name character varying(200) NOT NULL,
    intent_category character varying(50) NOT NULL,
    keywords character varying(1000),
    examples text,
    response_template character varying(2000),
    suggested_action character varying(50),
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    match_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- AI 知识库
CREATE TABLE scrm.scrm_ai_knowledge_base (
    id bigint NOT NULL,
    kb_name character varying(200) NOT NULL,
    description character varying(500),
    category character varying(50),
    document_count integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- AI 知识文档
CREATE TABLE scrm.scrm_ai_knowledge_document (
    id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    content_type character varying(20) DEFAULT 'TEXT'::character varying NOT NULL,
    tags character varying(500),
    source_type character varying(30) DEFAULT 'MANUAL'::character varying NOT NULL,
    source_url character varying(500),
    qa_pairs text,
    enabled boolean DEFAULT true NOT NULL,
    view_count integer DEFAULT 0,
    last_used_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 告警事件
CREATE TABLE scrm.scrm_alert_event (
    id bigint NOT NULL,
    event_no character varying(100) NOT NULL,
    rule_id bigint,
    rule_name character varying(200),
    rule_code character varying(50),
    metric_id bigint,
    metric_name character varying(200),
    metric_code character varying(50),
    severity character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'FIRING'::character varying NOT NULL,
    trigger_value double precision DEFAULT 0,
    threshold_value double precision DEFAULT 0,
    condition character varying(20),
    trigger_time timestamp without time zone NOT NULL,
    resolved_time timestamp without time zone,
    duration_seconds integer DEFAULT 0,
    fire_count integer DEFAULT 1,
    title character varying(500) NOT NULL,
    message character varying(2000),
    description text,
    root_cause_analysis character varying(1000),
    impact_analysis character varying(1000),
    affected_services character varying(500),
    affected_users integer DEFAULT 0,
    acknowledged_by character varying(100),
    acknowledged_at timestamp without time zone,
    acknowledge_note character varying(500),
    resolved_by character varying(100),
    resolved_note character varying(500),
    resolution_type character varying(20),
    notifications_sent integer DEFAULT 0,
    notification_failures integer DEFAULT 0,
    last_notification_at timestamp without time zone,
    escalated boolean DEFAULT false NOT NULL,
    escalated_at timestamp without time zone,
    escalated_to character varying(500),
    action_items character varying(1000),
    metadata text,
    related_event_ids character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_alert_event IS 'SCRM 告警事件表';
COMMENT ON COLUMN scrm.scrm_alert_event.event_no IS '事件编号 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_alert_event.rule_id IS '规则 ID (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.rule_name IS '规则名称 (可空, 快照)';
COMMENT ON COLUMN scrm.scrm_alert_event.rule_code IS '规则编码 (可空, 快照)';
COMMENT ON COLUMN scrm.scrm_alert_event.metric_id IS '指标 ID (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.metric_name IS '指标名称 (可空, 快照)';
COMMENT ON COLUMN scrm.scrm_alert_event.metric_code IS '指标编码 (可空, 快照)';
COMMENT ON COLUMN scrm.scrm_alert_event.severity IS '严重程度: INFO/WARNING/CRITICAL/FATAL';
COMMENT ON COLUMN scrm.scrm_alert_event.status IS '状态: FIRING/PENDING/RESOLVED/ACKNOWLEDGED/SUPPRESSED/EXPIRED (默认 FIRING)';
COMMENT ON COLUMN scrm.scrm_alert_event.trigger_value IS '触发值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_alert_event.threshold_value IS '阈值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_alert_event.condition IS '条件操作符 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.trigger_time IS '触发时间';
COMMENT ON COLUMN scrm.scrm_alert_event.resolved_time IS '恢复时间 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.duration_seconds IS '持续秒 (默认 0)';
COMMENT ON COLUMN scrm.scrm_alert_event.fire_count IS '触发次数 (默认 1)';
COMMENT ON COLUMN scrm.scrm_alert_event.title IS '告警标题';
COMMENT ON COLUMN scrm.scrm_alert_event.message IS '告警消息 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.description IS '详细描述 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.root_cause_analysis IS '根因分析 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.impact_analysis IS '影响分析 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.affected_services IS '受影响服务 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.affected_users IS '受影响用户数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_alert_event.acknowledged_by IS '确认人 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.acknowledged_at IS '确认时间 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.acknowledge_note IS '确认备注 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.resolved_by IS '恢复人 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.resolved_note IS '恢复备注 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.resolution_type IS '恢复方式: AUTO/MANUAL/MAINTENANCE/SUPPRESSED (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.notifications_sent IS '通知发送数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_alert_event.notification_failures IS '通知失败数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_alert_event.last_notification_at IS '最近通知时间 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.escalated IS '是否已升级 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_alert_event.escalated_at IS '升级时间 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.escalated_to IS '升级接收人 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.action_items IS '行动项 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.metadata IS '附加数据 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.related_event_ids IS '相关联事件 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_event.created_by IS '创建人 (可空)';
-- 告警规则
CREATE TABLE scrm.scrm_alert_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    rule_code character varying(50) NOT NULL,
    description character varying(500),
    metric_id bigint NOT NULL,
    metric_name character varying(200),
    metric_code character varying(50),
    condition character varying(20) NOT NULL,
    threshold_value double precision NOT NULL,
    threshold_value2 double precision DEFAULT 0,
    severity character varying(20) DEFAULT 'WARNING'::character varying NOT NULL,
    duration_seconds integer DEFAULT 0,
    evaluation_periods integer DEFAULT 1,
    cooldown_minutes integer DEFAULT 30,
    notification_channels character varying(500) NOT NULL,
    notification_template_id bigint,
    recipients character varying(1000),
    escalation_recipients character varying(1000),
    escalation_after_minutes integer DEFAULT 60,
    auto_resolve boolean DEFAULT true NOT NULL,
    auto_resolve_message character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    trigger_count integer DEFAULT 0,
    last_triggered_at timestamp without time zone,
    last_resolved_at timestamp without time zone,
    tags character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_alert_rule IS 'SCRM 告警规则表';
COMMENT ON COLUMN scrm.scrm_alert_rule.rule_name IS '规则名称';
COMMENT ON COLUMN scrm.scrm_alert_rule.rule_code IS '规则编码 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_alert_rule.description IS '描述 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_rule.metric_id IS '关联指标 ID';
COMMENT ON COLUMN scrm.scrm_alert_rule.metric_name IS '指标名称 (可空, 快照)';
COMMENT ON COLUMN scrm.scrm_alert_rule.metric_code IS '指标编码 (可空, 快照)';
COMMENT ON COLUMN scrm.scrm_alert_rule.condition IS '条件操作符: GT/GTE/LT/LTE/EQ/NE/CONTAINS/NOT_CONTAINS';
COMMENT ON COLUMN scrm.scrm_alert_rule.threshold_value IS '阈值';
COMMENT ON COLUMN scrm.scrm_alert_rule.threshold_value2 IS '第二阈值 (范围用, 默认 0)';
COMMENT ON COLUMN scrm.scrm_alert_rule.severity IS '严重程度: INFO/WARNING/CRITICAL/FATAL (默认 WARNING)';
COMMENT ON COLUMN scrm.scrm_alert_rule.duration_seconds IS '持续时间秒 (0=立即, 默认 0)';
COMMENT ON COLUMN scrm.scrm_alert_rule.evaluation_periods IS '连续触发次数 (默认 1)';
COMMENT ON COLUMN scrm.scrm_alert_rule.cooldown_minutes IS '冷却分钟 (默认 30)';
COMMENT ON COLUMN scrm.scrm_alert_rule.notification_channels IS '通知渠道 (逗号分隔): EMAIL/SMS/WECHAT/WEBHOOK/APP_PUSH/PHONE';
COMMENT ON COLUMN scrm.scrm_alert_rule.notification_template_id IS '通知模板 ID (可空)';
COMMENT ON COLUMN scrm.scrm_alert_rule.recipients IS '接收人 (可空, 逗号分隔)';
COMMENT ON COLUMN scrm.scrm_alert_rule.escalation_recipients IS '升级接收人 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_rule.escalation_after_minutes IS '升级时间分钟 (默认 60)';
COMMENT ON COLUMN scrm.scrm_alert_rule.auto_resolve IS '自动恢复 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_alert_rule.auto_resolve_message IS '自动恢复消息 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_rule.enabled IS '是否启用 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_alert_rule.trigger_count IS '触发次数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_alert_rule.last_triggered_at IS '最近触发时间 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_rule.last_resolved_at IS '最近恢复时间 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_rule.tags IS '标签 (可空)';
COMMENT ON COLUMN scrm.scrm_alert_rule.created_by IS '创建人 (可空)';
-- API 访问日志
CREATE TABLE scrm.scrm_api_access_log (
    id bigint NOT NULL,
    app_id bigint,
    api_key_id bigint,
    client_id character varying(200),
    endpoint character varying(500) NOT NULL,
    method character varying(10) NOT NULL,
    request_ip character varying(100) NOT NULL,
    user_agent character varying(500),
    request_params text,
    request_body text,
    response_status integer NOT NULL,
    response_time_ms integer,
    error_code character varying(50),
    error_message character varying(500),
    request_id character varying(100),
    accessed_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- API 应用
CREATE TABLE scrm.scrm_api_app (
    id bigint NOT NULL,
    app_name character varying(200) NOT NULL,
    app_code character varying(100) NOT NULL,
    description character varying(500),
    app_type character varying(20) DEFAULT 'THIRD_PARTY'::character varying NOT NULL,
    client_id character varying(200) NOT NULL,
    client_secret character varying(500) NOT NULL,
    redirect_uris character varying(1000),
    scopes character varying(500),
    rate_limit_per_minute integer DEFAULT 60,
    rate_limit_per_day integer DEFAULT 10000,
    ip_whitelist character varying(500),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    expires_at timestamp without time zone,
    last_access_at timestamp without time zone,
    total_request_count integer DEFAULT 0,
    today_request_count integer DEFAULT 0,
    owner_name character varying(100),
    contact_email character varying(200),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- API 密钥
CREATE TABLE scrm.scrm_api_key (
    id bigint NOT NULL,
    app_id bigint NOT NULL,
    key_name character varying(200) NOT NULL,
    api_key character varying(500) NOT NULL,
    key_secret character varying(500),
    key_type character varying(20) DEFAULT 'PERMANENT'::character varying NOT NULL,
    scopes character varying(500),
    allowed_ips character varying(500),
    rate_limit_per_minute integer DEFAULT 60,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    expires_at timestamp without time zone,
    last_used_at timestamp without time zone,
    last_used_ip character varying(100),
    usage_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- API 权限范围
CREATE TABLE scrm.scrm_api_scope (
    id bigint NOT NULL,
    scope_name character varying(100) NOT NULL,
    display_name character varying(200) NOT NULL,
    description character varying(500),
    resource character varying(100) NOT NULL,
    actions character varying(200) NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 审批流程
CREATE TABLE scrm.scrm_approval_flow (
    id bigint NOT NULL,
    flow_name character varying(200) NOT NULL,
    flow_code character varying(50) NOT NULL,
    description character varying(500),
    flow_type character varying(30) NOT NULL,
    applicable_module character varying(100),
    nodes text NOT NULL,
    condition_rules text,
    start_node character varying(100),
    end_nodes character varying(500),
    version_number integer DEFAULT 1 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    usage_count integer DEFAULT 0,
    last_used_at timestamp without time zone,
    approver_fallback character varying(200),
    allow_delegation boolean DEFAULT true NOT NULL,
    allow_countersign boolean DEFAULT false NOT NULL,
    allow_urgent boolean DEFAULT true NOT NULL,
    max_duration_days integer DEFAULT 30,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_approval_flow IS 'SCRM 审批流程定义表';
COMMENT ON COLUMN scrm.scrm_approval_flow.flow_name IS '流程名称';
COMMENT ON COLUMN scrm.scrm_approval_flow.flow_code IS '流程编码 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_approval_flow.description IS '描述 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_flow.flow_type IS '流程类型: CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/PRICE_CHANGE/CUSTOMER_MERGE/CONTENT/PURCHASE/OTHER/CUSTOM';
COMMENT ON COLUMN scrm.scrm_approval_flow.applicable_module IS '适用模块 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_flow.nodes IS '节点定义 JSON: [{nodeId,nodeName,nodeType,approverType,approverIds,ccUserIds,condition,actions,autoApprove,timeoutHours,order}]';
COMMENT ON COLUMN scrm.scrm_approval_flow.condition_rules IS '条件路由规则 JSON: [{nodeId,conditions,routes:[{toNode,condition}]}] (可空)';
COMMENT ON COLUMN scrm.scrm_approval_flow.start_node IS '起始节点 ID (可空)';
COMMENT ON COLUMN scrm.scrm_approval_flow.end_nodes IS '结束节点 ID 列表 (可空, 逗号分隔)';
COMMENT ON COLUMN scrm.scrm_approval_flow.version_number IS '流程版本号 (默认 1)';
COMMENT ON COLUMN scrm.scrm_approval_flow.status IS '状态: ACTIVE/INACTIVE/DRAFT (默认 ACTIVE)';
COMMENT ON COLUMN scrm.scrm_approval_flow.is_default IS '是否为默认流程 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_approval_flow.usage_count IS '使用次数';
COMMENT ON COLUMN scrm.scrm_approval_flow.last_used_at IS '最近使用时间 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_flow.approver_fallback IS '审批人缺失时的备选 (可空, 逗号分隔用户 ID)';
COMMENT ON COLUMN scrm.scrm_approval_flow.allow_delegation IS '允许转交 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_approval_flow.allow_countersign IS '允许加签 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_approval_flow.allow_urgent IS '允许加急 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_approval_flow.max_duration_days IS '最大审批时长 (天, 默认 30)';
COMMENT ON COLUMN scrm.scrm_approval_flow.created_by IS '创建人 (可空)';
-- 审批实例
CREATE TABLE scrm.scrm_approval_instance (
    id bigint NOT NULL,
    instance_no character varying(100) NOT NULL,
    flow_id bigint NOT NULL,
    flow_name character varying(200),
    flow_type character varying(30),
    business_type character varying(50) NOT NULL,
    business_id character varying(200),
    business_title character varying(500),
    business_data text,
    applicant_id character varying(100) NOT NULL,
    applicant_name character varying(100),
    applicant_dept character varying(200),
    applicant_role character varying(100),
    current_node_id character varying(100),
    current_node_name character varying(200),
    current_node_type character varying(50),
    current_approver_ids character varying(500),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    priority integer DEFAULT 0,
    is_urgent boolean DEFAULT false NOT NULL,
    urgent_reason character varying(500),
    started_at timestamp without time zone NOT NULL,
    completed_at timestamp without time zone,
    duration_hours integer,
    approved_at timestamp without time zone,
    approved_by character varying(100),
    rejected_by character varying(100),
    rejected_reason character varying(500),
    withdrawn_at timestamp without time zone,
    node_history text,
    variables text,
    attachment_urls character varying(1000),
    cc_users character varying(500),
    notified_at timestamp without time zone,
    last_activity_at timestamp without time zone,
    current_timeout_at timestamp without time zone,
    is_overdue boolean DEFAULT false NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_approval_instance IS 'SCRM 审批实例表';
COMMENT ON COLUMN scrm.scrm_approval_instance.instance_no IS '实例编号 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_approval_instance.flow_id IS '流程 ID';
COMMENT ON COLUMN scrm.scrm_approval_instance.flow_name IS '流程名称 (提交时快照, 可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.flow_type IS '流程类型 (提交时快照, 可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.business_type IS '业务类型: CONTRACT/EXPENSE/LEAVE/REFUND/DISCOUNT/OTHER';
COMMENT ON COLUMN scrm.scrm_approval_instance.business_id IS '业务 ID (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.business_title IS '业务标题 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.business_data IS '业务数据 JSON 快照 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.applicant_id IS '申请人 ID';
COMMENT ON COLUMN scrm.scrm_approval_instance.applicant_name IS '申请人名称 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.applicant_dept IS '申请人部门 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.applicant_role IS '申请人角色 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.current_node_id IS '当前节点 ID (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.current_node_name IS '当前节点名称 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.current_node_type IS '当前节点类型 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.current_approver_ids IS '当前审批人 ID 列表 (可空, 逗号分隔)';
COMMENT ON COLUMN scrm.scrm_approval_instance.status IS '状态: PENDING/APPROVING/APPROVED/REJECTED/CANCELLED/TRANSFERRED/TIMEOUT/WITHDRAWN (默认 PENDING)';
COMMENT ON COLUMN scrm.scrm_approval_instance.priority IS '优先级 (默认 0)';
COMMENT ON COLUMN scrm.scrm_approval_instance.is_urgent IS '是否加急 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_approval_instance.urgent_reason IS '加急原因 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.started_at IS '开始时间';
COMMENT ON COLUMN scrm.scrm_approval_instance.completed_at IS '完成时间 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.duration_hours IS '审批时长 (小时, 可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.approved_at IS '最终审批时间 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.approved_by IS '最终审批人 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.rejected_by IS '驳回人 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.rejected_reason IS '驳回原因 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.withdrawn_at IS '撤回时间 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.node_history IS '节点历史 JSON: [{nodeId,nodeName,approverId,approverName,action,comment,timestamp}] (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.variables IS '流程变量 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.attachment_urls IS '附件 URL (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.cc_users IS '抄送人 (可空, 逗号分隔)';
COMMENT ON COLUMN scrm.scrm_approval_instance.notified_at IS '通知时间 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.last_activity_at IS '最后活动时间 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.current_timeout_at IS '当前节点超时时间 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_instance.is_overdue IS '是否逾期 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_approval_instance.created_by IS '创建人 (可空)';
-- 审批日志
CREATE TABLE scrm.scrm_approval_log (
    id bigint NOT NULL,
    instance_id bigint NOT NULL,
    node_id character varying(100) NOT NULL,
    node_name character varying(200),
    node_type character varying(50),
    action_type character varying(20) NOT NULL,
    operator_id character varying(100) NOT NULL,
    operator_name character varying(100),
    operator_role character varying(100),
    operator_type character varying(20) DEFAULT 'APPROVER'::character varying NOT NULL,
    comment character varying(2000),
    action_data text,
    previous_node_id character varying(100),
    next_node_id character varying(100),
    attachments character varying(1000),
    acted_at timestamp without time zone NOT NULL,
    is_auto_action boolean DEFAULT false NOT NULL,
    sequence integer DEFAULT 0 NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_approval_log IS 'SCRM 审批操作日志表';
COMMENT ON COLUMN scrm.scrm_approval_log.instance_id IS '审批实例 ID';
COMMENT ON COLUMN scrm.scrm_approval_log.node_id IS '节点 ID';
COMMENT ON COLUMN scrm.scrm_approval_log.node_name IS '节点名称 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_log.node_type IS '节点类型 (可空): START/APPROVE/CC/CONDITION/END';
COMMENT ON COLUMN scrm.scrm_approval_log.action_type IS '操作类型: SUBMIT/APPROVE/REJECT/TRANSFER/COUNTERSIGN/CC/WITHDRAW/RESUBMIT/TIMEOUT/URGE/COMMENT/AUTO_APPROVE';
COMMENT ON COLUMN scrm.scrm_approval_log.operator_id IS '操作人 ID';
COMMENT ON COLUMN scrm.scrm_approval_log.operator_name IS '操作人名称 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_log.operator_role IS '操作人角色 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_log.operator_type IS '操作人类型: APPLICANT/APPROVER/CC/SYSTEM (默认 APPROVER)';
COMMENT ON COLUMN scrm.scrm_approval_log.comment IS '审批意见 (可空)';
COMMENT ON COLUMN scrm.scrm_approval_log.action_data IS '操作数据 JSON: {transferredTo,countersignUsers,ccUsers} (可空)';
COMMENT ON COLUMN scrm.scrm_approval_log.previous_node_id IS '上一节点 ID (可空)';
COMMENT ON COLUMN scrm.scrm_approval_log.next_node_id IS '下一节点 ID (可空)';
COMMENT ON COLUMN scrm.scrm_approval_log.attachments IS '附件 (可空, 逗号分隔 URL)';
COMMENT ON COLUMN scrm.scrm_approval_log.acted_at IS '操作时间';
COMMENT ON COLUMN scrm.scrm_approval_log.is_auto_action IS '是否自动操作 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_approval_log.sequence IS '操作顺序 (默认 0, 同实例内递增)';
-- 归档规则
CREATE TABLE scrm.scrm_archive_rule (
    id bigint NOT NULL,
    rule_name character varying(100) NOT NULL,
    platform_type character varying(30),
    account_id bigint,
    direction character varying(10),
    message_types character varying(200),
    keywords text,
    risk_level_filter character varying(20),
    enabled boolean DEFAULT true NOT NULL,
    priority integer DEFAULT 0 NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_archive_rule IS '归档规则: 定义自动归档的触发条件与过滤规则';
COMMENT ON COLUMN scrm.scrm_archive_rule.direction IS '消息方向过滤: INBOUND / OUTBOUND / NULL(全部)';
COMMENT ON COLUMN scrm.scrm_archive_rule.message_types IS '消息类型过滤(逗号分隔): TEXT,IMAGE,VIDEO';
COMMENT ON COLUMN scrm.scrm_archive_rule.keywords IS '关键词过滤(逗号分隔), 命中任一即归档';
COMMENT ON COLUMN scrm.scrm_archive_rule.risk_level_filter IS '风险等级过滤: LOW / MEDIUM / HIGH / NULL(全部)';
-- 素材表
CREATE TABLE scrm.scrm_asset (
    id bigint NOT NULL,
    asset_name character varying(500) NOT NULL,
    asset_code character varying(50) NOT NULL,
    category_id bigint,
    category_name character varying(200),
    asset_type character varying(30) NOT NULL,
    mime_type character varying(100),
    file_extension character varying(20),
    file_size_bytes bigint,
    file_url character varying(1000),
    thumbnail_url character varying(1000),
    preview_url character varying(1000),
    download_url character varying(1000),
    storage_type character varying(20) DEFAULT 'LOCAL'::character varying NOT NULL,
    storage_path character varying(1000),
    storage_bucket character varying(200),
    checksum character varying(200),
    description character varying(1000),
    tags character varying(500),
    keywords character varying(500),
    width integer,
    height integer,
    duration_seconds integer,
    page_count integer,
    resolution character varying(50),
    bitrate integer,
    format character varying(50),
    metadata text,
    applicable_scenarios character varying(500),
    applicable_products character varying(500),
    applicable_channels character varying(500),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    review_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    reviewed_by character varying(100),
    reviewed_at timestamp without time zone,
    review_comment character varying(500),
    version_no integer DEFAULT 1,
    is_public boolean DEFAULT false NOT NULL,
    is_template boolean DEFAULT false NOT NULL,
    download_count integer DEFAULT 0,
    view_count integer DEFAULT 0,
    use_count integer DEFAULT 0,
    like_count integer DEFAULT 0,
    share_count integer DEFAULT 0,
    favorite_count integer DEFAULT 0,
    last_used_at timestamp without time zone,
    uploaded_by character varying(100),
    uploaded_at timestamp without time zone,
    expiry_date date,
    is_expired boolean DEFAULT false NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 素材分类
CREATE TABLE scrm.scrm_asset_category (
    id bigint NOT NULL,
    category_name character varying(200) NOT NULL,
    category_code character varying(50) NOT NULL,
    description character varying(500),
    parent_id bigint,
    category_level integer DEFAULT 1,
    category_path character varying(1000),
    sort_order integer DEFAULT 0,
    icon character varying(100),
    color character varying(20),
    asset_count integer DEFAULT 0,
    total_size_bytes bigint DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    visible_to_roles character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 素材使用
CREATE TABLE scrm.scrm_asset_usage (
    id bigint NOT NULL,
    asset_id bigint NOT NULL,
    usage_type character varying(20) NOT NULL,
    usage_module character varying(100),
    usage_entity character varying(200),
    usage_entity_name character varying(200),
    usage_scenario character varying(200),
    user_id character varying(100) NOT NULL,
    user_name character varying(100),
    user_role character varying(50),
    usage_count integer DEFAULT 1,
    metadata text,
    used_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 归因转化
CREATE TABLE scrm.scrm_attribution_conversion (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    conversion_type character varying(50) NOT NULL,
    conversion_time timestamp without time zone NOT NULL,
    conversion_value double precision DEFAULT 0 NOT NULL,
    conversion_count integer DEFAULT 1,
    order_id character varying(100),
    total_touchpoints integer DEFAULT 0,
    attributed_touchpoints integer DEFAULT 0,
    model_id bigint,
    model_name character varying(200),
    attribution_details text,
    first_touch_type character varying(50),
    first_touch_channel character varying(50),
    last_touch_type character varying(50),
    last_touch_channel character varying(50),
    conversion_window_days integer DEFAULT 7,
    time_to_conversion_hours integer,
    metadata text,
    attributed_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 归因模型
CREATE TABLE scrm.scrm_attribution_model (
    id bigint NOT NULL,
    model_name character varying(200) NOT NULL,
    model_code character varying(50) NOT NULL,
    description character varying(500),
    model_type character varying(30) NOT NULL,
    lookback_days integer DEFAULT 30 NOT NULL,
    position_weights text,
    time_decay_half_life integer DEFAULT 7,
    custom_weights text,
    conversion_window_days integer DEFAULT 7,
    is_default boolean DEFAULT false NOT NULL,
    is_published boolean DEFAULT false NOT NULL,
    applied_count integer DEFAULT 0,
    last_applied_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 归因触点
CREATE TABLE scrm.scrm_attribution_touchpoint (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    touchpoint_order integer NOT NULL,
    touchpoint_type character varying(50) NOT NULL,
    channel character varying(50) NOT NULL,
    campaign_id bigint,
    campaign_name character varying(200),
    content_id bigint,
    touchpoint_time timestamp without time zone NOT NULL,
    touchpoint_value double precision DEFAULT 0,
    utm_source character varying(100),
    utm_medium character varying(100),
    utm_campaign character varying(200),
    utm_content character varying(200),
    utm_term character varying(200),
    landing_page character varying(500),
    referrer character varying(500),
    device_type character varying(30),
    session_id character varying(200),
    metadata text,
    is_attributed boolean DEFAULT false NOT NULL,
    attribution_weight double precision DEFAULT 0,
    attribution_value double precision DEFAULT 0,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 审计日志表
CREATE TABLE scrm.scrm_audit_log (
    id bigint NOT NULL,
    user_id character varying(100),
    username character varying(100),
    resource character varying(50) NOT NULL,
    action character varying(20) NOT NULL,
    method character varying(10) NOT NULL,
    request_uri character varying(500) NOT NULL,
    request_params text,
    response_body text,
    result character varying(20) NOT NULL,
    error_message text,
    execution_time bigint,
    client_ip character varying(50),
    operated_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 自动回复日志
CREATE TABLE scrm.scrm_auto_reply_log (
    id bigint NOT NULL,
    rule_id bigint,
    rule_name character varying(200),
    rule_type character varying(30) NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    account_id bigint,
    channel character varying(30),
    incoming_message text,
    matched_keyword character varying(200),
    match_score double precision DEFAULT 0,
    reply_type character varying(20) NOT NULL,
    reply_content text,
    sent_at timestamp without time zone NOT NULL,
    response_time_ms integer,
    status character varying(20) DEFAULT 'SENT'::character varying NOT NULL,
    error_message character varying(500),
    is_fallback boolean DEFAULT false NOT NULL,
    session_id character varying(200),
    metadata text,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 自动回复规则
CREATE TABLE scrm.scrm_auto_reply_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    rule_type character varying(30) NOT NULL,
    description character varying(500),
    match_type character varying(20) DEFAULT 'EXACT'::character varying NOT NULL,
    keywords character varying(1000),
    match_scope character varying(20) DEFAULT 'MESSAGE'::character varying NOT NULL,
    reply_type character varying(20) DEFAULT 'TEXT'::character varying NOT NULL,
    reply_content text NOT NULL,
    reply_template_id bigint,
    media_url character varying(500),
    link_url character varying(500),
    link_title character varying(200),
    link_description character varying(500),
    link_thumbnail character varying(500),
    applicable_channels character varying(500),
    applicable_accounts character varying(500),
    work_time_only boolean DEFAULT false NOT NULL,
    work_time_start character varying(10),
    work_time_end character varying(10),
    work_days character varying(50),
    timeout_seconds integer,
    priority integer DEFAULT 0,
    max_trigger_per_customer integer DEFAULT 0,
    cooldown_minutes integer DEFAULT 0,
    fallback_rule boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    trigger_count integer DEFAULT 0,
    last_triggered_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 自动回复模板
CREATE TABLE scrm.scrm_auto_reply_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    template_type character varying(20) DEFAULT 'TEXT'::character varying NOT NULL,
    category character varying(100),
    content text NOT NULL,
    variables character varying(500),
    applicable_scenes character varying(500),
    thumbnail_url character varying(500),
    usage_count integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 自动打标规则
CREATE TABLE scrm.scrm_auto_tag_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    description character varying(500),
    trigger_event character varying(50) NOT NULL,
    condition_type character varying(20) NOT NULL,
    conditions text NOT NULL,
    action_type character varying(20) NOT NULL,
    action_params text NOT NULL,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    match_count integer DEFAULT 0,
    last_match_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 自动打标规则日志
CREATE TABLE scrm.scrm_auto_tag_rule_log (
    id bigint NOT NULL,
    rule_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_nickname character varying(200),
    trigger_event character varying(50) NOT NULL,
    matched_conditions text,
    action_type character varying(20) NOT NULL,
    action_result character varying(20) NOT NULL,
    action_detail character varying(500),
    executed_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 行为路径
CREATE TABLE scrm.scrm_behavior_path (
    id bigint NOT NULL,
    path_name character varying(200),
    customer_id bigint NOT NULL,
    session_start_time timestamp without time zone NOT NULL,
    session_end_time timestamp without time zone,
    touchpoints character varying(1000),
    behavior_sequence text NOT NULL,
    total_behaviors integer DEFAULT 0,
    total_duration_seconds integer DEFAULT 0,
    touchpoint_count integer DEFAULT 0,
    has_conversion boolean DEFAULT false NOT NULL,
    conversion_point character varying(500),
    entry_touchpoint character varying(50),
    exit_touchpoint character varying(50),
    device_type character varying(30),
    session_id character varying(200),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 行为轨迹
CREATE TABLE scrm.scrm_behavior_track (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    account_id bigint,
    behavior_type character varying(50) NOT NULL,
    touchpoint character varying(50) NOT NULL,
    page_url character varying(500),
    page_title character varying(200),
    referrer character varying(500),
    behavior_time timestamp without time zone NOT NULL,
    duration_seconds integer,
    device_type character varying(30),
    os character varying(50),
    browser character varying(100),
    app_version character varying(50),
    ip character varying(100),
    location character varying(200),
    session_id character varying(200),
    metadata text,
    utm_source character varying(100),
    utm_medium character varying(100),
    utm_campaign character varying(200),
    utm_content character varying(200),
    utm_term character varying(200),
    conversion_value double precision DEFAULT 0,
    is_conversion boolean DEFAULT false NOT NULL,
    funnel_stage character varying(30),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 黑名单
CREATE TABLE scrm.scrm_blacklist (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    list_type character varying(20) NOT NULL,
    target_type character varying(30) NOT NULL,
    target_value character varying(500) NOT NULL,
    target_name character varying(200),
    customer_id bigint,
    reason character varying(1000) NOT NULL,
    risk_level character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    risk_score double precision DEFAULT 0,
    risk_tags character varying(500),
    source character varying(100) NOT NULL,
    source_detail character varying(500),
    evidence character varying(2000),
    related_event_id bigint,
    effective_date date NOT NULL,
    expiry_date date,
    is_permanent boolean DEFAULT false NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    added_by character varying(100) NOT NULL,
    added_at timestamp without time zone NOT NULL,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    removed_by character varying(100),
    removed_at timestamp without time zone,
    remove_reason character varying(500),
    appeal_status character varying(20),
    appeal_reason character varying(1000),
    appealed_at timestamp without time zone,
    appeal_reviewed_by character varying(100),
    appeal_reviewed_at timestamp without time zone,
    appeal_result character varying(500),
    review_count integer DEFAULT 0,
    last_reviewed_at timestamp without time zone,
    next_review_date date,
    alert_count integer DEFAULT 0,
    last_alert_at timestamp without time zone,
    metadata text,
    notes character varying(1000),
    created_by character varying(100)
);
-- 黑名单规则
CREATE TABLE scrm.scrm_blacklist_rule (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    rule_name character varying(200) NOT NULL,
    rule_code character varying(50) NOT NULL,
    description character varying(500),
    rule_type character varying(30) NOT NULL,
    risk_category character varying(50) NOT NULL,
    condition_field character varying(100) NOT NULL,
    condition_operator character varying(20) NOT NULL,
    condition_value character varying(1000) NOT NULL,
    condition_value2 character varying(500),
    time_window_minutes integer,
    threshold_count integer,
    threshold_amount double precision,
    severity character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    action character varying(30) DEFAULT 'ALERT'::character varying NOT NULL,
    action_params character varying(1000),
    applicable_modules character varying(500),
    applicable_scenarios character varying(500),
    target_list_type character varying(20),
    notification_channels character varying(500),
    notification_recipients character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    priority integer DEFAULT 0,
    trigger_count integer DEFAULT 0,
    last_triggered_at timestamp without time zone,
    false_positive_count integer DEFAULT 0,
    false_positive_rate double precision DEFAULT 0,
    accuracy_rate double precision DEFAULT 0,
    last_evaluated_at timestamp without time zone,
    evaluation_count integer DEFAULT 0,
    tags character varying(500),
    created_by character varying(100)
);
-- 预算分配
CREATE TABLE scrm.scrm_budget_allocation (
    id bigint NOT NULL,
    plan_id bigint NOT NULL,
    plan_name character varying(200),
    allocation_name character varying(200) NOT NULL,
    allocation_type character varying(30) NOT NULL,
    target_type character varying(100) NOT NULL,
    target_name character varying(200),
    allocated_amount double precision DEFAULT 0 NOT NULL,
    spent_amount double precision DEFAULT 0,
    remaining_amount double precision DEFAULT 0,
    spend_rate double precision DEFAULT 0,
    period_start date NOT NULL,
    period_end date NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    alert_threshold double precision DEFAULT 0.8,
    is_alert_triggered boolean DEFAULT false NOT NULL,
    last_alert_at timestamp without time zone,
    notes character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 预算支出
CREATE TABLE scrm.scrm_budget_expense (
    id bigint NOT NULL,
    expense_no character varying(100) NOT NULL,
    plan_id bigint,
    plan_name character varying(200),
    allocation_id bigint,
    allocation_name character varying(200),
    expense_type character varying(30) NOT NULL,
    campaign_id bigint,
    campaign_name character varying(200),
    expense_date date NOT NULL,
    amount double precision DEFAULT 0 NOT NULL,
    currency character varying(10) DEFAULT 'CNY'::character varying NOT NULL,
    description character varying(500),
    vendor character varying(200),
    invoice_no character varying(100),
    payment_method character varying(30),
    payment_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    paid_at timestamp without time zone,
    receipt_url character varying(500),
    attachments character varying(1000),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    approver_comment character varying(500),
    department_id character varying(100),
    department_name character varying(200),
    requester_id character varying(100),
    requester_name character varying(100),
    tags character varying(500),
    notes character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 预算计划
CREATE TABLE scrm.scrm_budget_plan (
    id bigint NOT NULL,
    plan_name character varying(200) NOT NULL,
    plan_code character varying(50) NOT NULL,
    description character varying(500),
    fiscal_year integer NOT NULL,
    fiscal_period character varying(20) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    total_budget double precision DEFAULT 0 NOT NULL,
    allocated_budget double precision DEFAULT 0,
    spent_budget double precision DEFAULT 0,
    remaining_budget double precision DEFAULT 0,
    allocation_rate double precision DEFAULT 0,
    spend_rate double precision DEFAULT 0,
    currency character varying(10) DEFAULT 'CNY'::character varying NOT NULL,
    budget_type character varying(30) NOT NULL,
    departments character varying(500),
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    approved_amount double precision DEFAULT 0,
    alert_threshold double precision DEFAULT 0.8,
    is_alert_triggered boolean DEFAULT false NOT NULL,
    last_alert_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 预算 ROI
CREATE TABLE scrm.scrm_budget_roi (
    id bigint NOT NULL,
    plan_id bigint,
    allocation_id bigint,
    campaign_id bigint,
    campaign_name character varying(200),
    period character varying(20),
    total_spend double precision DEFAULT 0 NOT NULL,
    total_revenue double precision DEFAULT 0,
    total_profit double precision DEFAULT 0,
    revenue_roi double precision DEFAULT 0,
    profit_roi double precision DEFAULT 0,
    roas double precision DEFAULT 0,
    cpa double precision DEFAULT 0,
    cpc double precision DEFAULT 0,
    cpm double precision DEFAULT 0,
    cac double precision DEFAULT 0,
    ltv double precision DEFAULT 0,
    ltv_cac_ratio double precision DEFAULT 0,
    conversions integer DEFAULT 0,
    clicks integer DEFAULT 0,
    impressions integer DEFAULT 0,
    conversion_rate double precision DEFAULT 0,
    click_rate double precision DEFAULT 0,
    payback_period_months integer,
    breakdown text,
    calculated_at timestamp without time zone NOT NULL,
    notes character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 日历冲突
CREATE TABLE scrm.scrm_calendar_conflict (
    id bigint NOT NULL,
    event1_id bigint NOT NULL,
    event2_id bigint NOT NULL,
    conflict_type character varying(30) NOT NULL,
    severity character varying(20) DEFAULT 'WARNING'::character varying NOT NULL,
    description character varying(500),
    overlapping_channels character varying(500),
    overlapping_audience character varying(500),
    resolved_status character varying(20) DEFAULT 'UNRESOLVED'::character varying NOT NULL,
    resolved_by character varying(100),
    resolved_at timestamp without time zone,
    resolution_note character varying(500),
    detected_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 日历事件
CREATE TABLE scrm.scrm_calendar_event (
    id bigint NOT NULL,
    event_title character varying(200) NOT NULL,
    event_type character varying(30) NOT NULL,
    description character varying(500),
    start_date date NOT NULL,
    end_date date NOT NULL,
    start_time time without time zone,
    end_time time without time zone,
    is_all_day boolean DEFAULT true NOT NULL,
    is_recurring boolean DEFAULT false NOT NULL,
    recurring_type character varying(20),
    recurring_config text,
    channels character varying(500),
    campaign_id bigint,
    content_id bigint,
    target_segment character varying(500),
    status character varying(20) DEFAULT 'PLANNED'::character varying NOT NULL,
    priority integer DEFAULT 0,
    color character varying(20),
    tags character varying(500),
    location character varying(200),
    owner_id character varying(100),
    owner_name character varying(100),
    team_id character varying(100),
    budget double precision DEFAULT 0,
    estimated_reach integer DEFAULT 0,
    actual_reach integer DEFAULT 0,
    notes character varying(500),
    reminder_minutes integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 日历节假日
CREATE TABLE scrm.scrm_calendar_holiday (
    id bigint NOT NULL,
    holiday_name character varying(100) NOT NULL,
    holiday_type character varying(30) NOT NULL,
    holiday_date character varying(20) NOT NULL,
    lunar_date character varying(20),
    is_lunar boolean DEFAULT false NOT NULL,
    duration_days integer DEFAULT 1,
    description character varying(500),
    marketing_opportunity character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    suggested_actions character varying(500),
    suggested_channels character varying(500),
    country character varying(50) DEFAULT 'CN'::character varying NOT NULL,
    region character varying(100),
    is_active boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 营销任务表
CREATE TABLE scrm.scrm_campaign (
    id bigint NOT NULL,
    campaign_name character varying(200) NOT NULL,
    campaign_type character varying(30) NOT NULL,
    platform_type character varying(30) NOT NULL,
    fleet_id bigint,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    cron_expression character varying(100),
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    behavior_flow_id bigint,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 任务-账号关联表
CREATE TABLE scrm.scrm_campaign_account (
    id bigint NOT NULL,
    campaign_id bigint NOT NULL,
    account_id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 营销活动分析
CREATE TABLE scrm.scrm_campaign_analysis (
    id bigint NOT NULL,
    campaign_id bigint NOT NULL,
    campaign_name character varying(200) NOT NULL,
    campaign_code character varying(100),
    campaign_type character varying(50) NOT NULL,
    objective character varying(200),
    start_date date NOT NULL,
    end_date date NOT NULL,
    duration_days integer DEFAULT 0,
    status character varying(20) DEFAULT 'PLANNED'::character varying NOT NULL,
    budget double precision DEFAULT 0,
    actual_cost double precision DEFAULT 0,
    channels character varying(500),
    segments character varying(500),
    products character varying(500),
    reach_count integer DEFAULT 0,
    impression_count integer DEFAULT 0,
    click_count integer DEFAULT 0,
    click_through_rate double precision DEFAULT 0,
    registration_count integer DEFAULT 0,
    participation_count integer DEFAULT 0,
    conversion_count integer DEFAULT 0,
    conversion_rate double precision DEFAULT 0,
    revenue double precision DEFAULT 0,
    profit double precision DEFAULT 0,
    roi double precision DEFAULT 0,
    roas double precision DEFAULT 0,
    cpc double precision DEFAULT 0,
    cpa double precision DEFAULT 0,
    cpm double precision DEFAULT 0,
    cac double precision DEFAULT 0,
    ltv double precision DEFAULT 0,
    ltv_cac_ratio double precision DEFAULT 0,
    payback_period double precision DEFAULT 0,
    average_order_value double precision DEFAULT 0,
    orders_per_customer double precision DEFAULT 0,
    new_customer_count integer DEFAULT 0,
    repeat_customer_count integer DEFAULT 0,
    new_customer_rate double precision DEFAULT 0,
    retention_rate double precision DEFAULT 0,
    nps_score integer DEFAULT 0,
    csat_score double precision DEFAULT 0,
    highlights character varying(2000),
    issues character varying(2000),
    recommendations character varying(2000),
    analyzed_by character varying(100),
    analyzed_at timestamp without time zone,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    is_approved boolean DEFAULT false NOT NULL,
    tags character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 营销活动渠道
CREATE TABLE scrm.scrm_campaign_channel (
    id bigint NOT NULL,
    analysis_id bigint NOT NULL,
    campaign_id bigint,
    channel_name character varying(100) NOT NULL,
    channel_type character varying(50) NOT NULL,
    channel_cost double precision DEFAULT 0,
    reach_count integer DEFAULT 0,
    impression_count integer DEFAULT 0,
    click_count integer DEFAULT 0,
    click_through_rate double precision DEFAULT 0,
    registration_count integer DEFAULT 0,
    participation_count integer DEFAULT 0,
    conversion_count integer DEFAULT 0,
    conversion_rate double precision DEFAULT 0,
    revenue double precision DEFAULT 0,
    profit double precision DEFAULT 0,
    roi double precision DEFAULT 0,
    cpc double precision DEFAULT 0,
    cpa double precision DEFAULT 0,
    cpm double precision DEFAULT 0,
    new_customer_count integer DEFAULT 0,
    repeat_customer_count integer DEFAULT 0,
    average_order_value double precision DEFAULT 0,
    engagement_rate double precision DEFAULT 0,
    bounce_rate double precision DEFAULT 0,
    share_rate double precision DEFAULT 0,
    cost_weight double precision DEFAULT 0,
    revenue_weight double precision DEFAULT 0,
    efficiency_score double precision DEFAULT 0,
    is_best_performer boolean DEFAULT false NOT NULL,
    is_underperforming boolean DEFAULT false NOT NULL,
    notes character varying(1000),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 营销活动效果表
CREATE TABLE scrm.scrm_campaign_effect (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    campaign_id bigint NOT NULL,
    campaign_name character varying(200),
    campaign_code character varying(100),
    campaign_type character varying(50) NOT NULL,
    objective character varying(100) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status character varying(20) NOT NULL,
    target_audience_count integer,
    reached_count integer,
    engaged_count integer,
    responded_count integer,
    clicked_count integer,
    opened_count integer,
    converted_count integer,
    bounced_count integer,
    unsubscribed_count integer,
    complained_count integer,
    shared_count integer,
    forwarded_count integer,
    downloaded_count integer,
    registered_count integer,
    purchased_count integer,
    revenue double precision,
    cost double precision,
    profit double precision,
    roi double precision,
    roas double precision,
    cpa double precision,
    cpc double precision,
    cpm double precision,
    cpl double precision,
    cps double precision,
    cvr double precision,
    ctr double precision,
    open_rate double precision,
    bounce_rate double precision,
    unsubscribe_rate double precision,
    engagement_rate double precision,
    reach_rate double precision,
    response_rate double precision,
    conversion_value double precision,
    avg_order_value double precision,
    avg_conversion_time double precision,
    customer_acquisition_cost double precision,
    ltv_acquired double precision,
    payback_period double precision,
    segments text,
    channels text,
    funnel text,
    attribution_model character varying(50) NOT NULL,
    attributions text,
    control_group_size integer,
    control_conversion_count integer,
    control_revenue double precision,
    uplift double precision,
    incremental_revenue double precision,
    statistical_significance double precision,
    confidence_level double precision,
    is_significant boolean NOT NULL,
    benchmarks text,
    last_calculated_at timestamp without time zone,
    calculation_status character varying(20) NOT NULL,
    notes character varying(2000),
    created_by character varying(100)
);
-- 营销任务执行日志
CREATE TABLE scrm.scrm_campaign_execution_log (
    id bigint NOT NULL,
    campaign_id bigint NOT NULL,
    behavior_flow_id bigint,
    action character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    error_code character varying(100),
    error_message text,
    operated_by character varying(100),
    operated_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 营销活动漏斗
CREATE TABLE scrm.scrm_campaign_funnel (
    id bigint NOT NULL,
    analysis_id bigint NOT NULL,
    campaign_id bigint,
    funnel_name character varying(200) NOT NULL,
    funnel_type character varying(50) DEFAULT 'PURCHASE'::character varying NOT NULL,
    stage_name character varying(200) NOT NULL,
    stage_order integer NOT NULL,
    stage_type character varying(50) NOT NULL,
    entry_count integer DEFAULT 0,
    exit_count integer DEFAULT 0,
    conversion_count integer DEFAULT 0,
    dropoff_count integer DEFAULT 0,
    conversion_rate double precision DEFAULT 0,
    dropoff_rate double precision DEFAULT 0,
    avg_time_spent double precision DEFAULT 0,
    revenue double precision DEFAULT 0,
    cost double precision DEFAULT 0,
    is_bottleneck boolean DEFAULT false NOT NULL,
    optimization_notes character varying(1000),
    metadata text,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- SOP 模板表
CREATE TABLE scrm.scrm_campaign_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    campaign_type character varying(30) NOT NULL,
    platform_type character varying(30) NOT NULL,
    template_content text NOT NULL,
    description character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 关怀记录表
CREATE TABLE scrm.scrm_care_record (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    care_type character varying(30) NOT NULL,
    care_date date NOT NULL,
    action_type character varying(30) NOT NULL,
    action_detail character varying(500),
    care_result character varying(20) NOT NULL,
    customer_response character varying(500),
    response_time_hours integer,
    sentiment character varying(20),
    assignee_id character varying(100),
    executed_at timestamp without time zone NOT NULL,
    notes character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 关怀规则表
CREATE TABLE scrm.scrm_care_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    care_type character varying(30) NOT NULL,
    description character varying(500),
    trigger_condition text NOT NULL,
    action_type character varying(30) NOT NULL,
    action_content text NOT NULL,
    priority integer DEFAULT 0,
    applicable_segments character varying(500),
    applicable_levels character varying(200),
    enabled boolean DEFAULT true NOT NULL,
    execution_count integer DEFAULT 0,
    last_executed_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 关怀任务表
CREATE TABLE scrm.scrm_care_task (
    id bigint NOT NULL,
    rule_id bigint,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    care_type character varying(30) NOT NULL,
    care_date date NOT NULL,
    scheduled_at timestamp without time zone NOT NULL,
    action_type character varying(30) NOT NULL,
    action_content text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    executed_at timestamp without time zone,
    action_result character varying(500),
    error_message character varying(500),
    assignee_id character varying(100),
    assignee_name character varying(100),
    customer_response character varying(500),
    response_at timestamp without time zone,
    notes character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 渠道码表
CREATE TABLE scrm.scrm_channel_code (
    id bigint NOT NULL,
    code_name character varying(200) NOT NULL,
    code_type character varying(20) NOT NULL,
    platform_type character varying(30) NOT NULL,
    qr_code_url character varying(500),
    redirect_account_id bigint,
    assign_rule text,
    welcome_message text,
    tags character varying(500),
    status character varying(20) NOT NULL,
    scan_count integer DEFAULT 0,
    add_count integer DEFAULT 0,
    expire_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_channel_code IS 'SCRM 渠道活码表, 生成渠道二维码/活码引流';
COMMENT ON COLUMN scrm.scrm_channel_code.code_name IS '活码名称';
COMMENT ON COLUMN scrm.scrm_channel_code.code_type IS '活码类型: SINGLE/MULTI/ROUND_ROBIN';
COMMENT ON COLUMN scrm.scrm_channel_code.platform_type IS '平台类型';
COMMENT ON COLUMN scrm.scrm_channel_code.qr_code_url IS '二维码图片 URL';
COMMENT ON COLUMN scrm.scrm_channel_code.redirect_account_id IS 'SINGLE 类型重定向账号 ID (可空)';
COMMENT ON COLUMN scrm.scrm_channel_code.assign_rule IS 'MULTI 类型分配规则 JSON (账号 ID 列表与权重)';
COMMENT ON COLUMN scrm.scrm_channel_code.welcome_message IS '欢迎语 (可空)';
COMMENT ON COLUMN scrm.scrm_channel_code.tags IS '标签 (逗号分隔)';
COMMENT ON COLUMN scrm.scrm_channel_code.status IS '状态: ACTIVE/INACTIVE';
COMMENT ON COLUMN scrm.scrm_channel_code.scan_count IS '累计扫码数';
COMMENT ON COLUMN scrm.scrm_channel_code.add_count IS '累计添加数';
COMMENT ON COLUMN scrm.scrm_channel_code.expire_at IS '过期时间 (可空, 空表示永久有效)';
COMMENT ON COLUMN scrm.scrm_channel_code.created_by IS '创建人';
-- 渠道码扫码记录
CREATE TABLE scrm.scrm_channel_code_scan (
    id bigint NOT NULL,
    channel_code_id bigint NOT NULL,
    scanner_uid character varying(200),
    scanner_nickname character varying(200),
    assigned_account_id bigint,
    ip character varying(50),
    user_agent character varying(500),
    scanned_at timestamp without time zone NOT NULL,
    added character varying(20) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_channel_code_scan IS 'SCRM 渠道活码扫码记录表, 记录扫码与分配账号';
COMMENT ON COLUMN scrm.scrm_channel_code_scan.channel_code_id IS '渠道活码 ID';
COMMENT ON COLUMN scrm.scrm_channel_code_scan.scanner_uid IS '扫码者唯一标识';
COMMENT ON COLUMN scrm.scrm_channel_code_scan.scanner_nickname IS '扫码者昵称';
COMMENT ON COLUMN scrm.scrm_channel_code_scan.assigned_account_id IS '分配到的账号 ID';
COMMENT ON COLUMN scrm.scrm_channel_code_scan.ip IS '扫码者 IP';
COMMENT ON COLUMN scrm.scrm_channel_code_scan.user_agent IS '扫码者 User-Agent';
COMMENT ON COLUMN scrm.scrm_channel_code_scan.scanned_at IS '扫码时间';
COMMENT ON COLUMN scrm.scrm_channel_code_scan.added IS '添加状态: PENDING/ADDED/REJECTED';
-- 会话归档表
CREATE TABLE scrm.scrm_chat_archive (
    id bigint NOT NULL,
    account_id bigint NOT NULL,
    customer_id bigint,
    conversation_id bigint,
    platform_type character varying(30) NOT NULL,
    direction character varying(10) NOT NULL,
    message_type character varying(20) NOT NULL,
    content text,
    raw_content text,
    media_url character varying(500),
    sent_at timestamp without time zone NOT NULL,
    archived_at timestamp without time zone NOT NULL,
    quality_flag character varying(20),
    risk_level character varying(20),
    archive_source character varying(20) DEFAULT 'AUTO'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_chat_archive IS '会话存档: 全量消息归档记录, 用于合规审计与质量分析';
COMMENT ON COLUMN scrm.scrm_chat_archive.direction IS '消息方向: INBOUND(接收) / OUTBOUND(发送)';
COMMENT ON COLUMN scrm.scrm_chat_archive.message_type IS '消息类型: TEXT / IMAGE / VIDEO / VOICE / FILE / LINK / SYSTEM';
COMMENT ON COLUMN scrm.scrm_chat_archive.quality_flag IS '质量标记: NORMAL(正常) / SENSITIVE(敏感) / VIOLATION(违规)';
COMMENT ON COLUMN scrm.scrm_chat_archive.risk_level IS '风险等级: LOW / MEDIUM / HIGH';
COMMENT ON COLUMN scrm.scrm_chat_archive.archive_source IS '归档来源: AUTO(自动) / MANUAL(手动)';
-- 流失挽回
CREATE TABLE scrm.scrm_churn_recovery (
    id bigint NOT NULL,
    warning_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    recovery_action character varying(30) NOT NULL,
    action_detail character varying(500),
    action_executed_at timestamp without time zone NOT NULL,
    executed_by character varying(100) NOT NULL,
    result character varying(20) NOT NULL,
    customer_responded boolean DEFAULT false NOT NULL,
    response_at timestamp without time zone,
    reactivated boolean DEFAULT false NOT NULL,
    notes character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 流失规则
CREATE TABLE scrm.scrm_churn_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    description character varying(500),
    risk_level character varying(20) NOT NULL,
    condition_type character varying(20) DEFAULT 'ALL'::character varying NOT NULL,
    conditions text NOT NULL,
    action_type character varying(30) NOT NULL,
    action_params text NOT NULL,
    cooldown_days integer DEFAULT 7,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    match_count integer DEFAULT 0,
    last_match_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 流失预警
CREATE TABLE scrm.scrm_churn_warning (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    rule_id bigint NOT NULL,
    rule_name character varying(200),
    risk_level character varying(20) NOT NULL,
    risk_score double precision NOT NULL,
    risk_factors text NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    action_type character varying(30) NOT NULL,
    action_result character varying(500),
    action_executed_at timestamp without time zone,
    assignee_id character varying(100),
    assignee_name character varying(100),
    resolution_note character varying(500),
    resolved_at timestamp without time zone,
    resolved_by character varying(100),
    detected_at timestamp without time zone NOT NULL,
    last_interaction_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 佣金计划
CREATE TABLE scrm.scrm_commission_plan (
    id bigint NOT NULL,
    plan_name character varying(200) NOT NULL,
    plan_code character varying(50) NOT NULL,
    description character varying(500),
    plan_type character varying(30) NOT NULL,
    calculation_basis character varying(30) NOT NULL,
    start_date date NOT NULL,
    end_date date,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    target_amount double precision DEFAULT 0,
    cap_amount double precision DEFAULT 0,
    min_amount double precision DEFAULT 0,
    clawback_days integer DEFAULT 0,
    payout_frequency character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    payout_day integer DEFAULT 15,
    applicable_products character varying(500),
    applicable_teams character varying(500),
    is_default boolean DEFAULT false NOT NULL,
    total_commission_paid double precision DEFAULT 0,
    total_sales_amount double precision DEFAULT 0,
    total_orders integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 佣金记录
CREATE TABLE scrm.scrm_commission_record (
    id bigint NOT NULL,
    record_no character varying(100) NOT NULL,
    plan_id bigint NOT NULL,
    plan_name character varying(200),
    rule_id bigint,
    rule_name character varying(200),
    sales_person_id character varying(100) NOT NULL,
    sales_person_name character varying(200),
    team_id character varying(100),
    team_name character varying(200),
    order_id character varying(100),
    order_amount double precision DEFAULT 0,
    order_profit double precision DEFAULT 0,
    order_date date,
    product_category character varying(100),
    customer_type character varying(50),
    commission_basis double precision DEFAULT 0,
    commission_rate double precision DEFAULT 0,
    commission_amount double precision DEFAULT 0 NOT NULL,
    bonus_amount double precision DEFAULT 0,
    deduction_amount double precision DEFAULT 0,
    final_commission double precision DEFAULT 0 NOT NULL,
    calculation_details text,
    status character varying(20) DEFAULT 'CALCULATED'::character varying NOT NULL,
    period character varying(20),
    payout_date date,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    approval_note character varying(500),
    paid_at timestamp without time zone,
    paid_amount double precision DEFAULT 0,
    tax_amount double precision DEFAULT 0,
    deduction_note character varying(500),
    clawback_amount double precision DEFAULT 0,
    clawback_reason character varying(500),
    clawback_at timestamp without time zone,
    notes character varying(500),
    calculated_at timestamp without time zone NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 佣金规则
CREATE TABLE scrm.scrm_commission_rule (
    id bigint NOT NULL,
    plan_id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    rule_type character varying(30) NOT NULL,
    conditions text,
    commission_rate double precision DEFAULT 0,
    commission_amount double precision DEFAULT 0,
    tier_config text,
    bonus_amount double precision DEFAULT 0,
    multiplier double precision DEFAULT 1.0,
    deduction_amount double precision DEFAULT 0,
    min_order_amount double precision DEFAULT 0,
    max_commission_per_order double precision DEFAULT 0,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    match_count integer DEFAULT 0,
    total_commission_calculated double precision DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 社群表
CREATE TABLE scrm.scrm_community (
    id bigint NOT NULL,
    community_name character varying(200) NOT NULL,
    platform_type character varying(30) DEFAULT 'WECHAT'::character varying NOT NULL,
    community_type character varying(30) NOT NULL,
    room_id character varying(200),
    qr_code character varying(500),
    description character varying(500),
    owner_id character varying(100) NOT NULL,
    owner_name character varying(100),
    manager_id character varying(100),
    manager_name character varying(100),
    member_count integer DEFAULT 0,
    max_members integer DEFAULT 500,
    active_members integer DEFAULT 0,
    today_new_members integer DEFAULT 0,
    today_messages integer DEFAULT 0,
    activity_score double precision DEFAULT 0,
    tags character varying(500),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 社群成员
CREATE TABLE scrm.scrm_community_member (
    id bigint NOT NULL,
    community_id bigint NOT NULL,
    customer_id bigint,
    member_name character varying(200) NOT NULL,
    member_alias character varying(200),
    platform_uid character varying(200),
    role character varying(20) DEFAULT 'MEMBER'::character varying NOT NULL,
    join_type character varying(20) DEFAULT 'INVITED'::character varying NOT NULL,
    join_at timestamp without time zone NOT NULL,
    last_active_at timestamp without time zone,
    message_count integer DEFAULT 0,
    is_active boolean DEFAULT true NOT NULL,
    invited_by character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    left_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 社群消息
CREATE TABLE scrm.scrm_community_message (
    id bigint NOT NULL,
    community_id bigint NOT NULL,
    sender_id character varying(200),
    sender_name character varying(200),
    sender_type character varying(20) DEFAULT 'MEMBER'::character varying NOT NULL,
    message_type character varying(20) DEFAULT 'TEXT'::character varying NOT NULL,
    content text,
    media_url character varying(500),
    sent_at timestamp without time zone NOT NULL,
    is_reply boolean DEFAULT false NOT NULL,
    reply_to_message_id bigint,
    sentiment character varying(20),
    archived boolean DEFAULT false NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 社群 SOP
CREATE TABLE scrm.scrm_community_sop (
    id bigint NOT NULL,
    sop_name character varying(200) NOT NULL,
    community_id bigint,
    trigger_type character varying(30) NOT NULL,
    trigger_config text,
    action_type character varying(30) NOT NULL,
    action_content text NOT NULL,
    delay_minutes integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    execution_count integer DEFAULT 0,
    last_executed_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 竞争对手表
CREATE TABLE scrm.scrm_competitor (
    id bigint NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    competitor_name character varying(200) NOT NULL,
    competitor_code character varying(50) NOT NULL,
    short_name character varying(100),
    description character varying(1000),
    website character varying(500),
    logo_url character varying(500),
    industry character varying(100),
    founded_year integer,
    company_size character varying(50),
    headquarters character varying(200),
    market_position character varying(50),
    market_share double precision DEFAULT 0 NOT NULL,
    strengths character varying(1000),
    weaknesses character varying(1000),
    threat_level character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    competitive_products character varying(500),
    target_market character varying(500),
    pricing_strategy character varying(200),
    business_model character varying(200),
    funding_stage character varying(50),
    total_funding double precision DEFAULT 0 NOT NULL,
    key_personnel character varying(500),
    social_media character varying(1000),
    monitoring_enabled boolean DEFAULT true NOT NULL,
    monitoring_frequency character varying(20) DEFAULT 'DAILY'::character varying NOT NULL,
    last_monitored_at timestamp without time zone,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    tags character varying(500),
    notes character varying(1000),
    created_by character varying(100)
);
-- 竞争对手活动
CREATE TABLE scrm.scrm_competitor_activity (
    id bigint NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    competitor_id bigint NOT NULL,
    competitor_name character varying(200),
    activity_type character varying(30) NOT NULL,
    title character varying(500) NOT NULL,
    summary character varying(2000),
    description text,
    activity_date date NOT NULL,
    source character varying(200),
    source_url character varying(500),
    impact_level character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    impact_analysis character varying(1000),
    affected_products character varying(500),
    affected_segments character varying(500),
    our_response character varying(1000),
    response_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    response_owner character varying(100),
    response_due_date date,
    detected_by character varying(100),
    detection_method character varying(50),
    importance_score integer DEFAULT 50 NOT NULL,
    is_verified boolean DEFAULT false NOT NULL,
    verified_by character varying(100),
    verified_at timestamp without time zone,
    tags character varying(500),
    attachments character varying(1000),
    related_activity_ids character varying(500),
    created_by character varying(100)
);
-- 竞争对手产品
CREATE TABLE scrm.scrm_competitor_product (
    id bigint NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    competitor_id bigint NOT NULL,
    competitor_name character varying(200),
    product_name character varying(200) NOT NULL,
    product_code character varying(50),
    product_category character varying(100),
    description character varying(1000),
    current_price double precision DEFAULT 0 NOT NULL,
    original_price double precision DEFAULT 0 NOT NULL,
    discount_rate double precision DEFAULT 0 NOT NULL,
    currency character varying(10) DEFAULT 'CNY'::character varying NOT NULL,
    price_unit character varying(50),
    product_url character varying(500),
    image_url character varying(500),
    features character varying(1000),
    specifications character varying(1000),
    target_segment character varying(200),
    positioning character varying(200),
    launch_date date,
    last_price_change_date date,
    last_price_change_percent double precision DEFAULT 0 NOT NULL,
    price_change_count integer DEFAULT 0 NOT NULL,
    lowest_price double precision DEFAULT 0 NOT NULL,
    highest_price double precision DEFAULT 0 NOT NULL,
    avg_price double precision DEFAULT 0 NOT NULL,
    price_history text,
    our_product_id character varying(100),
    our_product_name character varying(200),
    our_price double precision DEFAULT 0 NOT NULL,
    price_comparison character varying(20),
    advantage_score integer DEFAULT 0 NOT NULL,
    monitoring_enabled boolean DEFAULT true NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    last_monitored_at timestamp without time zone,
    notes character varying(500),
    created_by character varying(100)
);
-- 配置分组
CREATE TABLE scrm.scrm_config_group (
    id bigint NOT NULL,
    group_name character varying(200) NOT NULL,
    group_code character varying(100) NOT NULL,
    description character varying(500),
    parent_group_code character varying(100),
    group_level integer DEFAULT 1,
    group_icon character varying(200),
    display_order integer DEFAULT 0,
    config_count integer DEFAULT 0,
    last_modified_at timestamp without time zone,
    is_visible boolean DEFAULT true NOT NULL,
    is_expanded boolean DEFAULT true NOT NULL,
    applicable_roles character varying(500),
    applicable_modules character varying(500),
    environment character varying(50) DEFAULT 'ALL'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_config_group IS 'SCRM 配置分组表';
COMMENT ON COLUMN scrm.scrm_config_group.group_name IS '分组名称';
COMMENT ON COLUMN scrm.scrm_config_group.group_code IS '分组编码 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_config_group.description IS '描述 (可空)';
COMMENT ON COLUMN scrm.scrm_config_group.parent_group_code IS '父分组编码 (可空)';
COMMENT ON COLUMN scrm.scrm_config_group.group_level IS '分组层级 (默认 1)';
COMMENT ON COLUMN scrm.scrm_config_group.group_icon IS '分组图标 (可空)';
COMMENT ON COLUMN scrm.scrm_config_group.display_order IS '显示顺序 (默认 0)';
COMMENT ON COLUMN scrm.scrm_config_group.config_count IS '配置数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_config_group.last_modified_at IS '最近修改时间 (可空)';
COMMENT ON COLUMN scrm.scrm_config_group.is_visible IS '是否可见 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_config_group.is_expanded IS '默认展开 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_config_group.applicable_roles IS '可见角色 (可空)';
COMMENT ON COLUMN scrm.scrm_config_group.applicable_modules IS '适用模块 (可空)';
COMMENT ON COLUMN scrm.scrm_config_group.environment IS '环境限定: ALL/DEV/STAGING/PRODUCTION (默认 ALL)';
COMMENT ON COLUMN scrm.scrm_config_group.enabled IS '是否启用 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_config_group.created_by IS '创建人 (可空)';
-- 配置历史
CREATE TABLE scrm.scrm_config_history (
    id bigint NOT NULL,
    config_id bigint,
    config_key character varying(200) NOT NULL,
    config_name character varying(200),
    config_group character varying(100),
    old_value text,
    new_value text,
    old_display_value character varying(2000),
    new_display_value character varying(2000),
    change_type character varying(30) NOT NULL,
    change_reason character varying(500),
    changed_by character varying(100) NOT NULL,
    changed_at timestamp without time zone NOT NULL,
    ip_address character varying(50),
    user_agent character varying(500),
    session_id character varying(200),
    rollback_possible boolean DEFAULT true NOT NULL,
    rollback_by_id bigint,
    is_rolled_back boolean DEFAULT false NOT NULL,
    rolled_back_at timestamp without time zone,
    rolled_back_by character varying(100),
    review_status character varying(20),
    reviewed_by character varying(100),
    reviewed_at timestamp without time zone,
    metadata text,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_config_history IS 'SCRM 配置变更历史表';
COMMENT ON COLUMN scrm.scrm_config_history.config_id IS '配置 ID (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.config_key IS '配置键';
COMMENT ON COLUMN scrm.scrm_config_history.config_name IS '配置名称 (可空, 快照)';
COMMENT ON COLUMN scrm.scrm_config_history.config_group IS '配置分组 (可空, 快照)';
COMMENT ON COLUMN scrm.scrm_config_history.old_value IS '旧值 (可空, TEXT)';
COMMENT ON COLUMN scrm.scrm_config_history.new_value IS '新值 (可空, TEXT)';
COMMENT ON COLUMN scrm.scrm_config_history.old_display_value IS '旧显示值 (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.new_display_value IS '新显示值 (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.change_type IS '变更类型: CREATE/UPDATE/DELETE/ENABLE/DISABLE/IMPORT/EXPORT/RESET';
COMMENT ON COLUMN scrm.scrm_config_history.change_reason IS '变更原因 (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.changed_by IS '变更人';
COMMENT ON COLUMN scrm.scrm_config_history.changed_at IS '变更时间';
COMMENT ON COLUMN scrm.scrm_config_history.ip_address IS 'IP 地址 (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.user_agent IS 'User-Agent (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.session_id IS '会话 ID (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.rollback_possible IS '可回滚 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_config_history.rollback_by_id IS '回滚关联历史 ID (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.is_rolled_back IS '已回滚 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_config_history.rolled_back_at IS '回滚时间 (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.rolled_back_by IS '回滚人 (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.review_status IS '审核状态: PENDING/APPROVED/REJECTED (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.reviewed_by IS '审核人 (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.reviewed_at IS '审核时间 (可空)';
COMMENT ON COLUMN scrm.scrm_config_history.metadata IS '附加数据 JSON (可空, TEXT)';
-- 内容表
CREATE TABLE scrm.scrm_content (
    id bigint NOT NULL,
    title character varying(200) NOT NULL,
    content_type character varying(30) NOT NULL,
    category character varying(100),
    summary character varying(500),
    body_content text,
    cover_image character varying(500),
    media_url character varying(500),
    media_duration integer,
    tags character varying(500),
    target_audience character varying(500),
    author_id character varying(100),
    author_name character varying(100),
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    review_status character varying(20),
    reviewer_id character varying(100),
    reviewer_name character varying(100),
    reviewed_at timestamp without time zone,
    review_comment character varying(500),
    published_at timestamp without time zone,
    scheduled_at timestamp without time zone,
    view_count integer DEFAULT 0,
    like_count integer DEFAULT 0,
    share_count integer DEFAULT 0,
    comment_count integer DEFAULT 0,
    collect_count integer DEFAULT 0,
    conversion_count integer DEFAULT 0,
    seo_title character varying(200),
    seo_description character varying(500),
    seo_keywords character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 内容资产
CREATE TABLE scrm.scrm_content_asset (
    id bigint NOT NULL,
    asset_name character varying(200) NOT NULL,
    asset_type character varying(30) NOT NULL,
    file_url character varying(500) NOT NULL,
    file_path character varying(500),
    file_size integer,
    file_type character varying(20),
    width integer,
    height integer,
    duration integer,
    thumbnail_url character varying(500),
    description character varying(500),
    tags character varying(500),
    category character varying(100),
    source_type character varying(20) DEFAULT 'UPLOAD'::character varying NOT NULL,
    source_url character varying(500),
    usage_count integer DEFAULT 0,
    is_public boolean DEFAULT false NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 内容渠道
CREATE TABLE scrm.scrm_content_channel (
    id bigint NOT NULL,
    content_id bigint NOT NULL,
    channel character varying(30) NOT NULL,
    channel_post_id character varying(200),
    channel_post_url character varying(500),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    published_at timestamp without time zone,
    view_count integer DEFAULT 0,
    like_count integer DEFAULT 0,
    share_count integer DEFAULT 0,
    comment_count integer DEFAULT 0,
    conversion_count integer DEFAULT 0,
    error_message character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 内容排期
CREATE TABLE scrm.scrm_content_schedule (
    id bigint NOT NULL,
    content_id bigint NOT NULL,
    schedule_name character varying(200) NOT NULL,
    channels character varying(500) NOT NULL,
    scheduled_at timestamp without time zone NOT NULL,
    timezone character varying(50) DEFAULT 'Asia/Shanghai'::character varying NOT NULL,
    repeat_type character varying(20) DEFAULT 'NONE'::character varying NOT NULL,
    repeat_config text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    executed_at timestamp without time zone,
    error_message character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 合同表
CREATE TABLE scrm.scrm_contract (
    id bigint NOT NULL,
    contract_no character varying(100) NOT NULL,
    contract_name character varying(500) NOT NULL,
    contract_type character varying(30) NOT NULL,
    template_id bigint,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    customer_contact character varying(200),
    customer_address character varying(500),
    title character varying(500),
    description character varying(1000),
    content text NOT NULL,
    variables text,
    contract_amount double precision DEFAULT 0 NOT NULL,
    currency character varying(10) DEFAULT 'CNY'::character varying NOT NULL,
    payment_terms character varying(500),
    start_date date NOT NULL,
    end_date date NOT NULL,
    duration_months integer,
    auto_renew boolean DEFAULT false NOT NULL,
    auto_renew_months integer DEFAULT 0 NOT NULL,
    signed_date date,
    effective_date date,
    expired_date date,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    priority integer DEFAULT 0 NOT NULL,
    sales_person_id character varying(100),
    sales_person_name character varying(100),
    department_id character varying(100),
    department_name character varying(200),
    approver_id character varying(100),
    approver_name character varying(100),
    approved_at timestamp without time zone,
    approval_comment character varying(500),
    signer_id character varying(100),
    signer_name character varying(100),
    signature_method character varying(30),
    signature_url character varying(500),
    attachments character varying(1000),
    tags character varying(500),
    related_contracts character varying(500),
    renewal_of_id bigint,
    renewed_to_id bigint,
    reminders_enabled boolean DEFAULT true NOT NULL,
    reminder_days_before integer DEFAULT 30 NOT NULL,
    last_reminder_sent_at timestamp without time zone,
    terms text,
    custom_fields text,
    notes character varying(1000),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 合同变更
CREATE TABLE scrm.scrm_contract_change (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    contract_id bigint NOT NULL,
    contract_no character varying(100),
    change_no character varying(100) NOT NULL,
    change_type character varying(30) NOT NULL,
    change_reason character varying(500) NOT NULL,
    change_description character varying(2000),
    change_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    change_date date,
    effective_date date,
    old_value character varying(2000),
    new_value character varying(2000),
    affected_fields character varying(500),
    value_change double precision DEFAULT 0,
    value_before double precision DEFAULT 0,
    value_after double precision DEFAULT 0,
    impact_assessment character varying(1000),
    risk_assessment character varying(1000),
    approver_id bigint,
    approver_name character varying(100),
    approved_at timestamp without time zone,
    approval_notes character varying(1000),
    attachments character varying(1000),
    new_contract_id bigint,
    notes character varying(1000),
    created_by character varying(100)
);
-- 合同付款
CREATE TABLE scrm.scrm_contract_payment (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    contract_id bigint NOT NULL,
    contract_no character varying(100),
    payment_no character varying(100) NOT NULL,
    payment_name character varying(200),
    payment_type character varying(30) NOT NULL,
    payment_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    planned_amount double precision DEFAULT 0 NOT NULL,
    paid_amount double precision DEFAULT 0,
    unpaid_amount double precision DEFAULT 0,
    currency character varying(10) DEFAULT 'CNY'::character varying NOT NULL,
    tax_rate double precision DEFAULT 0,
    tax_amount double precision DEFAULT 0,
    planned_date date NOT NULL,
    actual_date date,
    due_date date,
    overdue_days integer DEFAULT 0,
    payment_method character varying(50),
    bank_account character varying(200),
    transaction_no character varying(200),
    invoice_no character varying(100),
    invoice_issued boolean DEFAULT false NOT NULL,
    invoice_date date,
    milestone character varying(200),
    milestone_description character varying(500),
    completion_rate double precision DEFAULT 0,
    reminder_sent boolean DEFAULT false NOT NULL,
    reminder_date date,
    reminder_count integer DEFAULT 0,
    notes character varying(1000),
    attachments character varying(1000),
    confirmed_by character varying(100),
    confirmed_at timestamp without time zone,
    created_by character varying(100)
);
-- 合同提醒
CREATE TABLE scrm.scrm_contract_reminder (
    id bigint NOT NULL,
    contract_id bigint NOT NULL,
    reminder_type character varying(30) NOT NULL,
    reminder_date date NOT NULL,
    reminder_time time without time zone,
    title character varying(200) NOT NULL,
    message character varying(1000),
    recipients character varying(500),
    channels character varying(200),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    sent_at timestamp without time zone,
    sent_count integer DEFAULT 0 NOT NULL,
    failed_count integer DEFAULT 0 NOT NULL,
    response_count integer DEFAULT 0 NOT NULL,
    is_recurring boolean DEFAULT false NOT NULL,
    recurring_config text,
    action_required character varying(20) DEFAULT 'NOTIFY'::character varying NOT NULL,
    action_url character varying(500),
    action_taken boolean DEFAULT false NOT NULL,
    action_taken_at timestamp without time zone,
    action_taken_by character varying(100),
    notes character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 合同模板
CREATE TABLE scrm.scrm_contract_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    template_code character varying(50) NOT NULL,
    description character varying(500),
    contract_type character varying(30) NOT NULL,
    template_content text NOT NULL,
    variables text,
    applicable_products character varying(500),
    clauses text,
    template_version integer DEFAULT 1 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    usage_count integer DEFAULT 0 NOT NULL,
    last_used_at timestamp without time zone,
    reviewed_by character varying(100),
    approved_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 会话表
CREATE TABLE scrm.scrm_conversation (
    id bigint NOT NULL,
    platform_type character varying(30) NOT NULL,
    account_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    conversation_type character varying(20) NOT NULL,
    platform_conversation_id character varying(200),
    last_message_at timestamp without time zone,
    last_message_summary character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    unread_count bigint DEFAULT 0 NOT NULL
);
-- 消息表(按月分区)
CREATE TABLE scrm.scrm_conversation_message (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
)
PARTITION BY RANGE (sent_at);
CREATE TABLE scrm.scrm_conversation_message_202607 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202608 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202609 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202610 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202611 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202612 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202701 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202702 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202703 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202704 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202705 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_202706 (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE scrm.scrm_conversation_message_default (
    id bigint NOT NULL,
    message_id character varying(100) NOT NULL,
    conversation_id bigint NOT NULL,
    message_type character varying(20) NOT NULL,
    direction character varying(10) NOT NULL,
    content text,
    media_object_key character varying(500),
    media_size bigint,
    platform_message_id character varying(200),
    sent_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 优惠券表
CREATE TABLE scrm.scrm_coupon (
    id bigint NOT NULL,
    template_id bigint NOT NULL,
    coupon_code character varying(100) NOT NULL,
    customer_id bigint,
    customer_name character varying(200),
    claim_source character varying(30) NOT NULL,
    claimed_at timestamp without time zone,
    expires_at timestamp without time zone NOT NULL,
    status character varying(20) DEFAULT 'UNUSED'::character varying NOT NULL,
    used_at timestamp without time zone,
    used_order character varying(100),
    used_amount double precision,
    issued_by character varying(100),
    notes character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 优惠券模板
CREATE TABLE scrm.scrm_coupon_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    coupon_type character varying(20) NOT NULL,
    face_value double precision NOT NULL,
    threshold_amount double precision DEFAULT 0 NOT NULL,
    discount_limit double precision,
    valid_type character varying(20) NOT NULL,
    valid_start date,
    valid_end date,
    valid_days integer,
    total_quantity integer NOT NULL,
    issued_quantity integer DEFAULT 0 NOT NULL,
    used_quantity integer DEFAULT 0 NOT NULL,
    claimed_quantity integer DEFAULT 0 NOT NULL,
    per_user_limit integer DEFAULT 1 NOT NULL,
    applicable_products character varying(1000),
    applicable_scenes character varying(500),
    description character varying(500),
    rules character varying(2000),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 优惠券使用日志
CREATE TABLE scrm.scrm_coupon_usage_log (
    id bigint NOT NULL,
    coupon_id bigint NOT NULL,
    template_id bigint NOT NULL,
    customer_id bigint,
    customer_name character varying(200),
    action_type character varying(20) NOT NULL,
    action_time timestamp without time zone NOT NULL,
    operator_id character varying(100),
    operator_name character varying(100),
    order_amount double precision,
    discount_amount double precision,
    detail character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户表
CREATE TABLE scrm.scrm_customer (
    id bigint NOT NULL,
    platform_type character varying(30) NOT NULL,
    platform_customer_uid character varying(200) NOT NULL,
    nickname character varying(200),
    avatar_url character varying(500),
    owner_account_id bigint NOT NULL,
    persona_id character varying(100),
    lifecycle character varying(20) DEFAULT 'NEW'::character varying NOT NULL,
    last_interaction_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    next_follow_up_at timestamp without time zone,
    remark text
);
COMMENT ON COLUMN scrm.scrm_customer.next_follow_up_at IS '下次跟进时间';
COMMENT ON COLUMN scrm.scrm_customer.remark IS '客户备注';
-- 客户重复记录
CREATE TABLE scrm.scrm_customer_duplicate (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    duplicate_customer_id bigint NOT NULL,
    match_type character varying(20) NOT NULL,
    match_score numeric(5,2),
    match_criteria character varying(200),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    detected_at timestamp without time zone NOT NULL,
    resolved_at timestamp without time zone,
    resolved_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_customer_duplicate IS '客户去重: 重复客户检测结果, 记录匹配类型/分数与处理状态';
COMMENT ON COLUMN scrm.scrm_customer_duplicate.match_type IS '匹配类型: EXACT(精确) / FUZZY(模糊)';
COMMENT ON COLUMN scrm.scrm_customer_duplicate.match_score IS '匹配分数(0~100), 越高越相似';
COMMENT ON COLUMN scrm.scrm_customer_duplicate.status IS '处理状态: PENDING(待处理) / CONFIRMED(已确认) / IGNORED(已忽略) / MERGED(已合并)';
-- 客户分组表
CREATE TABLE scrm.scrm_customer_group (
    id bigint NOT NULL,
    group_name character varying(200) NOT NULL,
    description character varying(500),
    owner_account_id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    platform_group_uid character varying(200),
    platform_type character varying(50),
    member_count integer,
    owner_user_id character varying(200)
);
-- 客户-分组中间表
CREATE TABLE scrm.scrm_customer_group_member (
    id bigint NOT NULL,
    group_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户健康分
CREATE TABLE scrm.scrm_customer_health_score (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    model_id bigint NOT NULL,
    total_score double precision DEFAULT 0 NOT NULL,
    max_score double precision DEFAULT 100,
    health_level character varying(20),
    health_label character varying(50),
    score_percent double precision DEFAULT 0,
    metric_scores text,
    engagement_score double precision DEFAULT 0,
    usage_score double precision DEFAULT 0,
    satisfaction_score double precision DEFAULT 0,
    payment_score double precision DEFAULT 0,
    growth_score double precision DEFAULT 0,
    support_score double precision DEFAULT 0,
    score_trend character varying(20),
    trend_change double precision DEFAULT 0,
    previous_score double precision DEFAULT 0,
    risk_level character varying(20),
    risk_factors character varying(1000),
    recommended_actions character varying(1000),
    is_at_risk boolean DEFAULT false NOT NULL,
    is_churn_risk boolean DEFAULT false NOT NULL,
    last_interaction_days integer DEFAULT 0,
    days_since_last_order integer DEFAULT 0,
    open_tickets integer DEFAULT 0,
    nps_score integer,
    calculated_at timestamp without time zone NOT NULL,
    next_calculation_at timestamp without time zone,
    notes character varying(500),
    assigned_to character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户身份关联
CREATE TABLE scrm.scrm_customer_identity (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    identity_type character varying(30) NOT NULL,
    identity_value character varying(500) NOT NULL,
    platform character varying(30),
    is_primary boolean DEFAULT false NOT NULL,
    is_verified boolean DEFAULT false NOT NULL,
    verified_at timestamp without time zone,
    source character varying(30) DEFAULT 'REGISTRATION'::character varying NOT NULL,
    last_used_at timestamp without time zone,
    metadata text,
    is_active boolean DEFAULT true NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户旅程表
CREATE TABLE scrm.scrm_customer_journey (
    id bigint NOT NULL,
    journey_name character varying(200) NOT NULL,
    description character varying(500),
    goal character varying(200),
    entry_condition text NOT NULL,
    entry_type character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    enrolled_count integer DEFAULT 0 NOT NULL,
    completed_count integer DEFAULT 0 NOT NULL,
    exited_count integer DEFAULT 0 NOT NULL,
    conversion_rate double precision DEFAULT 0,
    journey_version integer DEFAULT 1 NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户等级表
CREATE TABLE scrm.scrm_customer_level (
    id bigint NOT NULL,
    level_name character varying(100) NOT NULL,
    level_code character varying(50) NOT NULL,
    level_order integer NOT NULL,
    description character varying(500),
    color character varying(20),
    icon character varying(100),
    benefits text,
    upgrade_threshold double precision,
    downgrade_threshold double precision,
    validity_days integer,
    is_default boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户等级变更历史
CREATE TABLE scrm.scrm_customer_level_history (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    from_level_id bigint,
    from_level_name character varying(100),
    to_level_id bigint NOT NULL,
    to_level_name character varying(100) NOT NULL,
    change_type character varying(20) NOT NULL,
    change_reason character varying(500),
    changed_by character varying(100),
    changed_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户等级规则
CREATE TABLE scrm.scrm_customer_level_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    target_level_id bigint NOT NULL,
    rule_type character varying(30) NOT NULL,
    condition_type character varying(20) DEFAULT 'ALL'::character varying NOT NULL,
    conditions text NOT NULL,
    action_type character varying(20) DEFAULT 'SET_LEVEL'::character varying NOT NULL,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    match_count integer DEFAULT 0,
    last_match_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户生命周期表
CREATE TABLE scrm.scrm_customer_lifecycle (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    current_stage_id bigint NOT NULL,
    current_stage_code character varying(50) NOT NULL,
    current_stage_name character varying(100),
    entered_current_stage_at timestamp without time zone NOT NULL,
    duration_in_stage_days integer DEFAULT 0,
    previous_stage_id bigint,
    previous_stage_code character varying(50),
    stage_history_count integer DEFAULT 0,
    is_overdue boolean DEFAULT false NOT NULL,
    overdue_days integer DEFAULT 0,
    next_stage_id bigint,
    expected_transition_at timestamp without time zone,
    assigned_to character varying(100),
    notes character varying(500),
    last_updated_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    current_stage character varying(50),
    previous_stage character varying(50),
    stage_entered_at timestamp without time zone,
    stage_duration_days integer DEFAULT 0,
    total_lifecycle_days integer DEFAULT 0,
    stage_history text,
    transition_count integer DEFAULT 0,
    last_transition_at timestamp without time zone,
    last_transition_trigger character varying(200),
    last_transition_by character varying(100),
    acquisition_channel character varying(100),
    acquisition_date date,
    first_purchase_date date,
    last_purchase_date date,
    last_activity_date date,
    last_interaction_date date,
    total_orders integer DEFAULT 0,
    total_spent double precision DEFAULT 0,
    avg_order_value double precision DEFAULT 0,
    purchase_frequency double precision DEFAULT 0,
    days_since_last_purchase integer DEFAULT 0,
    days_since_last_activity integer DEFAULT 0,
    engagement_score double precision DEFAULT 0,
    satisfaction_score double precision DEFAULT 0,
    churn_risk_score double precision DEFAULT 0,
    churn_probability double precision DEFAULT 0,
    ltv_value double precision DEFAULT 0,
    predicted_ltv double precision DEFAULT 0,
    next_best_action character varying(500),
    next_best_offer character varying(500),
    tags character varying(500),
    risk_level character varying(20) DEFAULT 'LOW'::character varying NOT NULL,
    value_segment character varying(30) DEFAULT 'STANDARD'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    is_churned boolean DEFAULT false NOT NULL,
    churned_at timestamp without time zone,
    churn_reason character varying(500),
    reactivation_date date,
    reactivation_campaign character varying(200)
);
-- 客户生命周期历史
CREATE TABLE scrm.scrm_customer_lifecycle_history (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    previous_lifecycle character varying(30),
    new_lifecycle character varying(30) NOT NULL,
    remark character varying(500),
    operator_id character varying(100),
    operator_name character varying(100),
    changed_at timestamp without time zone NOT NULL
);
COMMENT ON TABLE scrm.scrm_customer_lifecycle_history IS '客户生命周期变更历史';
COMMENT ON COLUMN scrm.scrm_customer_lifecycle_history.id IS '主键 ID (Snowflake)';
COMMENT ON COLUMN scrm.scrm_customer_lifecycle_history.customer_id IS '客户 ID';
COMMENT ON COLUMN scrm.scrm_customer_lifecycle_history.previous_lifecycle IS '变更前生命周期阶段';
COMMENT ON COLUMN scrm.scrm_customer_lifecycle_history.new_lifecycle IS '变更后生命周期阶段';
COMMENT ON COLUMN scrm.scrm_customer_lifecycle_history.remark IS '变更备注';
COMMENT ON COLUMN scrm.scrm_customer_lifecycle_history.operator_id IS '操作人用户 ID';
COMMENT ON COLUMN scrm.scrm_customer_lifecycle_history.operator_name IS '操作人用户名';
COMMENT ON COLUMN scrm.scrm_customer_lifecycle_history.changed_at IS '变更时间';
-- 客户生命周期价值表
CREATE TABLE scrm.scrm_customer_ltv (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    model_id bigint NOT NULL,
    historical_ltv double precision DEFAULT 0 NOT NULL,
    predicted_ltv double precision DEFAULT 0 NOT NULL,
    total_revenue double precision DEFAULT 0,
    total_orders integer DEFAULT 0,
    avg_order_value double precision DEFAULT 0,
    avg_purchase_frequency double precision DEFAULT 0,
    avg_purchase_interval_days double precision DEFAULT 0,
    customer_age_days integer DEFAULT 0,
    last_purchase_date date,
    days_since_last_purchase integer DEFAULT 0,
    acquisition_cost double precision DEFAULT 0,
    projected_revenue double precision DEFAULT 0,
    projected_orders integer DEFAULT 0,
    customer_profitability double precision DEFAULT 0,
    roi double precision DEFAULT 0,
    value_tier character varying(20),
    churn_probability double precision DEFAULT 0,
    predicted_churn_date date,
    growth_potential character varying(20),
    confidence_score double precision DEFAULT 0,
    ltv_trend character varying(20),
    trend_change_percent double precision DEFAULT 0,
    calculated_at timestamp without time zone NOT NULL,
    previous_ltv double precision DEFAULT 0,
    metadata text,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户会员信息
CREATE TABLE scrm.scrm_customer_membership (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    tier_id bigint NOT NULL,
    tier_name character varying(200),
    tier_level integer NOT NULL,
    tier_code character varying(50),
    member_card_no character varying(100) NOT NULL,
    member_card_type character varying(30) DEFAULT 'STANDARD'::character varying NOT NULL,
    membership_status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    join_date date NOT NULL,
    current_tier_date date NOT NULL,
    tier_expiry_date date,
    total_spend double precision DEFAULT 0,
    total_orders integer DEFAULT 0,
    total_points integer DEFAULT 0,
    available_points integer DEFAULT 0,
    spend_in_period double precision DEFAULT 0,
    orders_in_period integer DEFAULT 0,
    period_start_date date,
    period_end_date date,
    next_tier_spend_needed double precision DEFAULT 0,
    next_tier_id bigint,
    next_tier_name character varying(200),
    upgrade_progress double precision DEFAULT 0,
    downgrade_risk boolean DEFAULT false NOT NULL,
    points_to_expire integer DEFAULT 0,
    points_expiry_date date,
    benefits_used_count integer DEFAULT 0,
    benefits_saved_amount double precision DEFAULT 0,
    last_activity_date date,
    last_upgrade_date date,
    last_downgrade_date date,
    upgrade_history text,
    referral_code character varying(100),
    referred_by character varying(100),
    notes character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_customer_membership IS 'SCRM 客户会员表';
COMMENT ON COLUMN scrm.scrm_customer_membership.customer_id IS '客户 ID';
COMMENT ON COLUMN scrm.scrm_customer_membership.customer_name IS '客户名称 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.tier_id IS '等级 ID';
COMMENT ON COLUMN scrm.scrm_customer_membership.tier_name IS '等级名称快照 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.tier_level IS '当前等级序号';
COMMENT ON COLUMN scrm.scrm_customer_membership.tier_code IS '等级编码快照 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.member_card_no IS '会员卡号 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_customer_membership.member_card_type IS '会员卡类型: STANDARD/VIP/BLACK_GOLD/DIAMOND/CUSTOM (默认 STANDARD)';
COMMENT ON COLUMN scrm.scrm_customer_membership.membership_status IS '会员状态: ACTIVE/FROZEN/EXPIRED/CANCELLED/PENDING (默认 ACTIVE)';
COMMENT ON COLUMN scrm.scrm_customer_membership.join_date IS '加入日期';
COMMENT ON COLUMN scrm.scrm_customer_membership.current_tier_date IS '当前等级日期';
COMMENT ON COLUMN scrm.scrm_customer_membership.tier_expiry_date IS '等级到期日 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.total_spend IS '累计消费 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.total_orders IS '累计订单 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.total_points IS '累计积分 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.available_points IS '可用积分 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.spend_in_period IS '周期内消费 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.orders_in_period IS '周期内订单 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.period_start_date IS '周期开始 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.period_end_date IS '周期结束 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.next_tier_spend_needed IS '距下一等级还需消费 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.next_tier_id IS '下一等级 ID (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.next_tier_name IS '下一等级名称 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.upgrade_progress IS '升级进度 0-1 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.downgrade_risk IS '降级风险 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_customer_membership.points_to_expire IS '即将过期积分 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.points_expiry_date IS '积分过期日 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.benefits_used_count IS '权益使用次数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.benefits_saved_amount IS '权益节省金额 (默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_membership.last_activity_date IS '最后活动日 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.last_upgrade_date IS '最后升级日 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.last_downgrade_date IS '最后降级日 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.upgrade_history IS 'JSON 升降级历史: [{date,fromTier,toTier,reason,type,fromTierId,toTierId,fromLevel,toLevel}] (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.referral_code IS '会员推荐码 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.referred_by IS '推荐人 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.notes IS '备注 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_membership.created_by IS '创建人 (可空)';
-- 客户合并记录
CREATE TABLE scrm.scrm_customer_merge_record (
    id bigint NOT NULL,
    primary_customer_id bigint NOT NULL,
    merged_customer_ids character varying(500) NOT NULL,
    merge_strategy character varying(20) NOT NULL,
    match_criteria character varying(200) NOT NULL,
    status character varying(20) DEFAULT 'COMPLETED'::character varying NOT NULL,
    merged_at timestamp without time zone,
    reverted_at timestamp without time zone,
    merged_by character varying(100),
    reverted_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_customer_merge_record IS '客户合并: 合并操作记录, 支持回滚';
COMMENT ON COLUMN scrm.scrm_customer_merge_record.merge_strategy IS '合并策略: MANUAL(手动) / AUTO_MERGE(自动)';
COMMENT ON COLUMN scrm.scrm_customer_merge_record.status IS '合并状态: COMPLETED(已完成) / REVERTED(已回滚)';
-- 客户画像表
CREATE TABLE scrm.scrm_customer_profile (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    profile_version integer DEFAULT 1 NOT NULL,
    profile_status character varying(20) DEFAULT 'GENERATED'::character varying NOT NULL,
    demographic_info text,
    behavioral_traits text,
    psychographic_info text,
    purchase_behavior text,
    communication_preference text,
    social_profile text,
    risk_profile text,
    value_profile text,
    summary text,
    tags character varying(1000),
    confidence_score double precision DEFAULT 0,
    data_sources character varying(500),
    last_data_update timestamp without time zone,
    generated_at timestamp without time zone NOT NULL,
    verified_by character varying(100),
    verified_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_customer_profile IS 'SCRM 客户画像表';
COMMENT ON COLUMN scrm.scrm_customer_profile.customer_id IS '客户 ID';
COMMENT ON COLUMN scrm.scrm_customer_profile.customer_name IS '客户名称 (生成时快照, 可空)';
COMMENT ON COLUMN scrm.scrm_customer_profile.profile_version IS '画像版本号 (每次重新生成递增, 默认 1)';
COMMENT ON COLUMN scrm.scrm_customer_profile.profile_status IS '画像状态: DRAFT/GENERATED/VERIFIED/OUTDATED/ARCHIVED (默认 GENERATED)';
COMMENT ON COLUMN scrm.scrm_customer_profile.demographic_info IS '人口统计 JSON: {age,gender,location,occupation,income,education}';
COMMENT ON COLUMN scrm.scrm_customer_profile.behavioral_traits IS '行为特征 JSON: {activityLevel,preferredChannels,activeTimeSlots,browsingPattern,purchaseFrequency}';
COMMENT ON COLUMN scrm.scrm_customer_profile.psychographic_info IS '心理特征 JSON: {interests,values,lifestyle,personality,attitudes}';
COMMENT ON COLUMN scrm.scrm_customer_profile.purchase_behavior IS '消费行为 JSON: {avgOrderValue,preferredCategories,paymentPreference,priceSensitivity,brandLoyalty,discountSensitivity}';
COMMENT ON COLUMN scrm.scrm_customer_profile.communication_preference IS '沟通偏好 JSON: {preferredChannel,preferredTime,language,tone,responseRate}';
COMMENT ON COLUMN scrm.scrm_customer_profile.social_profile IS '社交画像 JSON: {influence,socialActiveness,referralTendency,communityRole}';
COMMENT ON COLUMN scrm.scrm_customer_profile.risk_profile IS '风险画像 JSON: {churnRisk,fraudRisk,creditRisk,complianceRisk}';
COMMENT ON COLUMN scrm.scrm_customer_profile.value_profile IS '价值画像 JSON: {ltv,ltvSegment,rfmSegment,valueTier,growthPotential}';
COMMENT ON COLUMN scrm.scrm_customer_profile.summary IS '画像摘要文本';
COMMENT ON COLUMN scrm.scrm_customer_profile.tags IS '推荐标签 (逗号分隔, 可空)';
COMMENT ON COLUMN scrm.scrm_customer_profile.confidence_score IS '置信度 (0-1, 默认 0)';
COMMENT ON COLUMN scrm.scrm_customer_profile.data_sources IS '数据来源: ORDER/BEHAVIOR/SURVEY/SOCIAL/TRANSACTION (逗号分隔, 可空)';
COMMENT ON COLUMN scrm.scrm_customer_profile.last_data_update IS '最后数据更新时间 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_profile.generated_at IS '画像生成时间';
COMMENT ON COLUMN scrm.scrm_customer_profile.verified_by IS '验证人 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_profile.verified_at IS '验证时间 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_profile.created_by IS '创建人 (可空)';
-- 客户标签表
CREATE TABLE scrm.scrm_customer_tag (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    tag_key character varying(100) NOT NULL,
    tag_value character varying(200),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户标签定义表
CREATE TABLE scrm.scrm_customer_tag_def (
    id bigint NOT NULL,
    tag_name character varying(200) NOT NULL,
    tag_code character varying(50) NOT NULL,
    description character varying(500),
    group_id bigint,
    group_name character varying(200),
    tag_type character varying(30) NOT NULL,
    value_type character varying(20) DEFAULT 'BOOLEAN'::character varying NOT NULL,
    enum_options character varying(1000),
    default_value character varying(200),
    category character varying(100),
    sub_category character varying(100),
    is_system boolean DEFAULT false NOT NULL,
    is_required boolean DEFAULT false NOT NULL,
    is_visible boolean DEFAULT true NOT NULL,
    is_searchable boolean DEFAULT true NOT NULL,
    is_multiple boolean DEFAULT false NOT NULL,
    color character varying(20),
    icon character varying(200),
    display_order integer DEFAULT 0,
    help_text character varying(500),
    applicable_segments character varying(500),
    rule_expression character varying(2000),
    rule_conditions text,
    rule_logic character varying(20) DEFAULT 'AND'::character varying NOT NULL,
    auto_apply boolean DEFAULT false NOT NULL,
    evaluation_frequency character varying(20) DEFAULT 'DAILY'::character varying NOT NULL,
    last_evaluated_at timestamp without time zone,
    customer_count integer DEFAULT 0,
    coverage_rate double precision DEFAULT 0,
    positive_count integer DEFAULT 0,
    negative_count integer DEFAULT 0,
    neutral_count integer DEFAULT 0,
    true_count integer DEFAULT 0,
    false_count integer DEFAULT 0,
    avg_numeric_value double precision DEFAULT 0,
    top_values character varying(1000),
    trend character varying(20) DEFAULT 'STABLE'::character varying NOT NULL,
    trend_percent double precision DEFAULT 0,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    tags character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 客户标签规则表
CREATE TABLE scrm.scrm_customer_tag_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    rule_code character varying(50) NOT NULL,
    description character varying(500),
    tag_id bigint NOT NULL,
    tag_name character varying(200),
    tag_code character varying(50),
    rule_type character varying(30) NOT NULL,
    condition_logic character varying(20) DEFAULT 'AND'::character varying NOT NULL,
    conditions text NOT NULL,
    target_value character varying(200),
    action_type character varying(30) DEFAULT 'ASSIGN'::character varying NOT NULL,
    priority integer DEFAULT 0,
    applicable_scope character varying(30) DEFAULT 'ALL'::character varying NOT NULL,
    scope_params character varying(1000),
    evaluation_type character varying(20) DEFAULT 'BATCH'::character varying NOT NULL,
    schedule_expression character varying(100),
    last_evaluated_at timestamp without time zone,
    evaluation_count integer DEFAULT 0,
    match_count integer DEFAULT 0,
    applied_count integer DEFAULT 0,
    removed_count integer DEFAULT 0,
    match_rate double precision DEFAULT 0,
    false_positive_count integer DEFAULT 0,
    accuracy_rate double precision DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    tags character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    condition_type character varying(20) DEFAULT ''::character varying NOT NULL,
    target_fields character varying(500),
    execution_frequency character varying(20) DEFAULT ''::character varying NOT NULL,
    last_executed_at timestamp without time zone,
    matched_count integer,
    status character varying(20) DEFAULT ''::character varying NOT NULL
);
-- 客户时间线
CREATE TABLE scrm.scrm_customer_timeline (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    event_type character varying(50) NOT NULL,
    event_title character varying(200) NOT NULL,
    event_detail text,
    event_time timestamp without time zone NOT NULL,
    operator_id character varying(100),
    operator_name character varying(100),
    platform_type character varying(30),
    importance character varying(10) DEFAULT 'NORMAL'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_customer_timeline IS 'SCRM 客户时间线事件表, 记录客户全生命周期关键事件';
COMMENT ON COLUMN scrm.scrm_customer_timeline.customer_id IS '客户 ID (引用 scrm_customer.id)';
COMMENT ON COLUMN scrm.scrm_customer_timeline.event_type IS '事件类型: CUSTOMER_CREATED/TAG_ADDED/TAG_REMOVED/LIFECYCLE_CHANGED/FOLLOW_UP_COMPLETED/MESSAGE_SENT/MESSAGE_RECEIVED/OPPORTUNITY_CREATED/OPPORTUNITY_STAGE_CHANGED/JOURNEY_ENROLLED/JOURNEY_STEP_COMPLETED/MASS_SEND_RECEIVED/CAMPAIGN_TRIGGERED/NOTE_ADDED/FILE_SHARED';
COMMENT ON COLUMN scrm.scrm_customer_timeline.event_title IS '事件标题';
COMMENT ON COLUMN scrm.scrm_customer_timeline.event_detail IS '事件详情 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_customer_timeline.event_time IS '事件发生时间';
COMMENT ON COLUMN scrm.scrm_customer_timeline.operator_id IS '操作人 ID (可空, 系统事件无操作人)';
COMMENT ON COLUMN scrm.scrm_customer_timeline.operator_name IS '操作人姓名 (可空)';
COMMENT ON COLUMN scrm.scrm_customer_timeline.platform_type IS '平台类型 (可空, 标识事件来源平台)';
COMMENT ON COLUMN scrm.scrm_customer_timeline.importance IS '重要级别: HIGH/NORMAL/LOW (默认 NORMAL)';
-- 数据字典
CREATE TABLE scrm.scrm_data_dictionary (
    id bigint NOT NULL,
    dict_name character varying(200) NOT NULL,
    dict_code character varying(50) NOT NULL,
    description character varying(500),
    dict_type character varying(30) DEFAULT 'LIST'::character varying NOT NULL,
    category character varying(100),
    parent_id bigint,
    module character varying(100),
    applicable_scenarios character varying(500),
    item_count integer DEFAULT 0,
    is_system boolean DEFAULT false NOT NULL,
    is_cacheable boolean DEFAULT true NOT NULL,
    cache_ttl_seconds integer DEFAULT 3600,
    sort_order integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    usage_count integer DEFAULT 0,
    last_used_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 数据字典项
CREATE TABLE scrm.scrm_data_dictionary_item (
    id bigint NOT NULL,
    dict_id bigint NOT NULL,
    dict_code character varying(50) NOT NULL,
    item_label character varying(200) NOT NULL,
    item_value character varying(500) NOT NULL,
    item_code character varying(100),
    parent_id bigint,
    item_level integer DEFAULT 1,
    item_path character varying(1000),
    sort_order integer DEFAULT 0,
    item_style character varying(50),
    color character varying(20),
    icon character varying(100),
    description character varying(500),
    extra_data text,
    tags character varying(500),
    is_default boolean DEFAULT false NOT NULL,
    is_disabled boolean DEFAULT false NOT NULL,
    is_visible boolean DEFAULT true NOT NULL,
    usage_count integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 数据字典使用
CREATE TABLE scrm.scrm_data_dictionary_usage (
    id bigint NOT NULL,
    dict_id bigint NOT NULL,
    dict_code character varying(50) NOT NULL,
    item_id bigint,
    item_value character varying(500),
    usage_module character varying(100) NOT NULL,
    usage_entity character varying(200),
    usage_field character varying(200),
    usage_scenario character varying(200),
    usage_count integer DEFAULT 0,
    last_used_at timestamp without time zone,
    first_used_at timestamp without time zone NOT NULL,
    notes character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 数据传输日志
CREATE TABLE scrm.scrm_data_transfer_log (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    task_type character varying(10) NOT NULL,
    row_index integer,
    record_key character varying(200),
    operation character varying(20) NOT NULL,
    field_errors text,
    message character varying(500),
    status character varying(20) NOT NULL,
    processed_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_data_transfer_log IS 'SCRM 数据导入导出日志表, 单条记录处理轨迹';
COMMENT ON COLUMN scrm.scrm_data_transfer_log.task_id IS '关联任务 ID';
COMMENT ON COLUMN scrm.scrm_data_transfer_log.task_type IS '任务类型: IMPORT/EXPORT';
COMMENT ON COLUMN scrm.scrm_data_transfer_log.row_index IS '行号 (可空)';
COMMENT ON COLUMN scrm.scrm_data_transfer_log.record_key IS '记录键 (可空, 去重键或主业务键)';
COMMENT ON COLUMN scrm.scrm_data_transfer_log.operation IS '操作: CREATE/UPDATE/SKIP/FAIL';
COMMENT ON COLUMN scrm.scrm_data_transfer_log.field_errors IS '字段错误 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_data_transfer_log.message IS '消息 (可空)';
COMMENT ON COLUMN scrm.scrm_data_transfer_log.status IS '状态: SUCCESS/WARNING/ERROR';
COMMENT ON COLUMN scrm.scrm_data_transfer_log.processed_at IS '处理时间';
-- 互动事件
CREATE TABLE scrm.scrm_engagement_event (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    behavior_type character varying(50) NOT NULL,
    channel character varying(30),
    event_time timestamp without time zone NOT NULL,
    points integer DEFAULT 0 NOT NULL,
    rule_id bigint,
    session_id character varying(200),
    page_url character varying(500),
    referrer character varying(500),
    user_agent character varying(500),
    device_type character varying(30),
    location character varying(200),
    metadata text,
    ip character varying(100),
    processed boolean DEFAULT true NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 互动等级
CREATE TABLE scrm.scrm_engagement_level (
    id bigint NOT NULL,
    level_name character varying(100) NOT NULL,
    level_code character varying(50) NOT NULL,
    min_score double precision NOT NULL,
    max_score double precision,
    color character varying(20),
    description character varying(500),
    recommended_action character varying(500),
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 互动规则
CREATE TABLE scrm.scrm_engagement_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    behavior_type character varying(50) NOT NULL,
    channel character varying(30),
    points integer DEFAULT 1 NOT NULL,
    daily_limit integer DEFAULT 0,
    weekly_limit integer DEFAULT 0,
    monthly_limit integer DEFAULT 0,
    decay_days integer DEFAULT 30,
    decay_type character varying(20) DEFAULT 'LINEAR'::character varying NOT NULL,
    weight double precision DEFAULT 1.0,
    description character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    match_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 互动评分
CREATE TABLE scrm.scrm_engagement_score (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    total_score double precision DEFAULT 0 NOT NULL,
    current_score double precision DEFAULT 0 NOT NULL,
    engagement_level character varying(20) DEFAULT 'INACTIVE'::character varying NOT NULL,
    score_trend character varying(20),
    trend_change_percent double precision DEFAULT 0,
    last_event_at timestamp without time zone,
    last_calculated_at timestamp without time zone NOT NULL,
    streak_days integer DEFAULT 0,
    total_events integer DEFAULT 0,
    weekly_score double precision DEFAULT 0,
    monthly_score double precision DEFAULT 0,
    quarterly_score double precision DEFAULT 0,
    yearly_score double precision DEFAULT 0,
    level_updated_at timestamp without time zone,
    metadata text,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 导出任务
CREATE TABLE scrm.scrm_export_task (
    id bigint NOT NULL,
    task_name character varying(200) NOT NULL,
    data_type character varying(30) NOT NULL,
    query_condition text,
    selected_fields character varying(1000),
    filters text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    file_path character varying(500),
    file_name character varying(200),
    file_type character varying(20) DEFAULT 'EXCEL'::character varying NOT NULL,
    total_records integer DEFAULT 0,
    exported_records integer DEFAULT 0,
    file_size integer,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    duration_ms integer,
    error_message character varying(1000),
    triggered_by character varying(100) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_export_task IS 'SCRM 数据导出任务表, 批量数据导出任务';
COMMENT ON COLUMN scrm.scrm_export_task.task_name IS '任务名称';
COMMENT ON COLUMN scrm.scrm_export_task.data_type IS '数据类型: CUSTOMER/CONTACT/FOLLOW_RECORD/TAG/PRODUCT/ORDER/OTHER';
COMMENT ON COLUMN scrm.scrm_export_task.query_condition IS '查询条件 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_export_task.selected_fields IS '逗号分隔选中字段 (可空)';
COMMENT ON COLUMN scrm.scrm_export_task.filters IS '过滤条件 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_export_task.status IS '状态: PENDING/EXPORTING/SUCCESS/FAILED/CANCELLED (默认 PENDING)';
COMMENT ON COLUMN scrm.scrm_export_task.file_path IS '导出文件路径 (可空)';
COMMENT ON COLUMN scrm.scrm_export_task.file_name IS '导出文件名称 (可空)';
COMMENT ON COLUMN scrm.scrm_export_task.file_type IS '文件类型: CSV/EXCEL/JSON (默认 EXCEL)';
COMMENT ON COLUMN scrm.scrm_export_task.total_records IS '总记录数';
COMMENT ON COLUMN scrm.scrm_export_task.exported_records IS '已导出记录数';
COMMENT ON COLUMN scrm.scrm_export_task.file_size IS '文件大小 KB (可空)';
COMMENT ON COLUMN scrm.scrm_export_task.start_time IS '开始执行时间 (可空)';
COMMENT ON COLUMN scrm.scrm_export_task.end_time IS '结束执行时间 (可空)';
COMMENT ON COLUMN scrm.scrm_export_task.duration_ms IS '执行耗时毫秒 (可空)';
COMMENT ON COLUMN scrm.scrm_export_task.error_message IS '错误信息 (可空)';
COMMENT ON COLUMN scrm.scrm_export_task.triggered_by IS '触发人';
-- 外部联系人映射
CREATE TABLE scrm.scrm_external_contact_mapping (
    id bigint NOT NULL,
    platform character varying(30) NOT NULL,
    external_contact_id character varying(200) NOT NULL,
    external_user_id character varying(200),
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    external_name character varying(200),
    external_avatar character varying(500),
    external_corp_id character varying(200),
    union_id character varying(200),
    open_id character varying(200),
    follow_user_id character varying(100),
    follow_status character varying(20),
    last_sync_at timestamp without time zone NOT NULL,
    sync_status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 外部联系人同步配置
CREATE TABLE scrm.scrm_external_contact_sync_config (
    id bigint NOT NULL,
    config_name character varying(200) NOT NULL,
    platform character varying(30) DEFAULT 'WORK_WECHAT'::character varying NOT NULL,
    corp_id character varying(200),
    agent_id character varying(100),
    secret character varying(500),
    sync_mode character varying(20) DEFAULT 'INCREMENTAL'::character varying NOT NULL,
    sync_direction character varying(20) DEFAULT 'BIDIRECTIONAL'::character varying NOT NULL,
    sync_frequency character varying(20) DEFAULT 'HOURLY'::character varying NOT NULL,
    last_sync_at timestamp without time zone,
    last_sync_status character varying(20),
    last_sync_count integer DEFAULT 0,
    auto_create_customer boolean DEFAULT true NOT NULL,
    auto_merge_duplicate boolean DEFAULT false NOT NULL,
    field_mapping text,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 外部联系人同步日志
CREATE TABLE scrm.scrm_external_contact_sync_log (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    external_contact_id character varying(200) NOT NULL,
    external_name character varying(200),
    external_avatar character varying(500),
    operation_type character varying(20) NOT NULL,
    customer_id bigint,
    customer_name character varying(200),
    field_changes text,
    status character varying(20) DEFAULT 'SUCCESS'::character varying NOT NULL,
    error_message character varying(500),
    processed_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 外部联系人同步任务
CREATE TABLE scrm.scrm_external_contact_sync_task (
    id bigint NOT NULL,
    config_id bigint NOT NULL,
    task_name character varying(200) NOT NULL,
    sync_mode character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    duration_ms integer,
    total_records integer DEFAULT 0,
    success_count integer DEFAULT 0,
    failed_count integer DEFAULT 0,
    new_count integer DEFAULT 0,
    update_count integer DEFAULT 0,
    skip_count integer DEFAULT 0,
    error_message character varying(1000),
    triggered_by character varying(100) NOT NULL,
    triggered_by_type character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 反馈表
CREATE TABLE scrm.scrm_feedback (
    id bigint NOT NULL,
    feedback_no character varying(50) NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    feedback_type character varying(30) NOT NULL,
    category character varying(100),
    priority character varying(10) DEFAULT 'MEDIUM'::character varying NOT NULL,
    status character varying(20) DEFAULT 'NEW'::character varying NOT NULL,
    source character varying(30) DEFAULT 'CUSTOMER'::character varying NOT NULL,
    customer_id bigint,
    customer_name character varying(200),
    customer_phone character varying(50),
    customer_email character varying(200),
    customer_level character varying(50),
    order_id character varying(100),
    product_id character varying(100),
    ticket_id bigint,
    sentiment character varying(20),
    sentiment_score double precision DEFAULT 0,
    rating integer,
    tags character varying(500),
    attachments character varying(1000),
    assignee_id character varying(100),
    assignee_name character varying(100),
    team_id character varying(100),
    assigned_at timestamp without time zone,
    first_response_at timestamp without time zone,
    resolved_at timestamp without time zone,
    closed_at timestamp without time zone,
    response_time_hours integer,
    resolution_time_hours integer,
    resolution character varying(1000),
    satisfaction_score integer,
    is_public boolean DEFAULT false NOT NULL,
    is_anonymous boolean DEFAULT false NOT NULL,
    view_count integer DEFAULT 0,
    upvote_count integer DEFAULT 0,
    comment_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 反馈分类
CREATE TABLE scrm.scrm_feedback_category (
    id bigint NOT NULL,
    category_name character varying(100) NOT NULL,
    category_code character varying(50) NOT NULL,
    description character varying(500),
    applicable_types character varying(500),
    default_priority character varying(10) DEFAULT 'MEDIUM'::character varying NOT NULL,
    default_assignee_id character varying(100),
    default_team_id character varying(100),
    sla_hours integer DEFAULT 48,
    auto_tag character varying(200),
    sort_order integer DEFAULT 0,
    feedback_count integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 反馈评论
CREATE TABLE scrm.scrm_feedback_comment (
    id bigint NOT NULL,
    feedback_id bigint NOT NULL,
    comment_type character varying(20) NOT NULL,
    author_id character varying(100) NOT NULL,
    author_name character varying(100),
    author_role character varying(50),
    content text NOT NULL,
    attachments character varying(1000),
    is_internal boolean DEFAULT false NOT NULL,
    upvote_count integer DEFAULT 0,
    parent_comment_id bigint,
    created_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 节日表
CREATE TABLE scrm.scrm_festival (
    id bigint NOT NULL,
    festival_name character varying(100) NOT NULL,
    festival_type character varying(30) NOT NULL,
    festival_date character varying(20) NOT NULL,
    lunar_month integer,
    lunar_day integer,
    description character varying(500),
    default_greeting character varying(500),
    default_action_type character varying(30),
    default_action_content text,
    applicable character varying(20) DEFAULT 'ALL'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 跟进记录
CREATE TABLE scrm.scrm_follow_up_record (
    id bigint NOT NULL,
    task_id bigint,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    contact_method character varying(30) NOT NULL,
    contact_result character varying(30) NOT NULL,
    content text NOT NULL,
    duration_minutes integer,
    sentiment character varying(20),
    next_action character varying(500),
    next_follow_up_at timestamp without time zone,
    recorded_by character varying(100) NOT NULL,
    recorded_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 跟进任务
CREATE TABLE scrm.scrm_follow_up_task (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    account_id bigint,
    assignee_id character varying(100) NOT NULL,
    assignee_name character varying(100),
    task_type character varying(30) NOT NULL,
    title character varying(200) NOT NULL,
    content text,
    planned_at timestamp without time zone NOT NULL,
    completed_at timestamp without time zone,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    priority character varying(10) DEFAULT 'MEDIUM'::character varying NOT NULL,
    reminder_minutes integer DEFAULT 30,
    reminded boolean DEFAULT false NOT NULL,
    follow_up_result character varying(500),
    next_follow_up_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 跟进模板
CREATE TABLE scrm.scrm_follow_up_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    task_type character varying(30) NOT NULL,
    title_template character varying(200) NOT NULL,
    content_template text,
    default_priority character varying(10) DEFAULT 'MEDIUM'::character varying,
    default_reminder_minutes integer DEFAULT 30,
    platform_type character varying(30),
    scenario character varying(50),
    enabled boolean DEFAULT true NOT NULL,
    use_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 预测模型
CREATE TABLE scrm.scrm_forecast_model (
    id bigint NOT NULL,
    model_name character varying(200) NOT NULL,
    model_code character varying(50) NOT NULL,
    description character varying(500),
    model_type character varying(30) NOT NULL,
    algorithm character varying(100),
    target_metric character varying(30) NOT NULL,
    granularity character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    lookback_periods integer DEFAULT 12 NOT NULL,
    forecast_periods integer DEFAULT 3 NOT NULL,
    seasonality_period integer,
    parameters text,
    training_data_start date,
    training_data_end date,
    training_data_points integer DEFAULT 0,
    last_trained_at timestamp without time zone,
    last_accuracy_score double precision DEFAULT 0,
    last_mape double precision DEFAULT 0,
    last_mae double precision DEFAULT 0,
    last_rmse double precision DEFAULT 0,
    last_r2 double precision DEFAULT 0,
    cross_validation_score double precision DEFAULT 0,
    is_trained boolean DEFAULT false NOT NULL,
    is_auto_retrain boolean DEFAULT false NOT NULL,
    retrain_frequency character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    applicable_segments character varying(500),
    applicable_products character varying(500),
    applicable_channels character varying(500),
    applicable_regions character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    model_version integer DEFAULT 1,
    notes character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 预测结果
CREATE TABLE scrm.scrm_forecast_result (
    id bigint NOT NULL,
    scenario_id bigint NOT NULL,
    scenario_name character varying(200),
    model_id bigint,
    model_name character varying(200),
    forecast_date date NOT NULL,
    period_label character varying(50),
    period_index integer DEFAULT 0,
    forecast_value double precision DEFAULT 0 NOT NULL,
    actual_value double precision,
    variance double precision DEFAULT 0,
    variance_percent double precision DEFAULT 0,
    confidence_lower double precision DEFAULT 0,
    confidence_upper double precision DEFAULT 0,
    confidence_range double precision DEFAULT 0,
    is_actual boolean DEFAULT false NOT NULL,
    segment character varying(200),
    product character varying(200),
    channel character varying(200),
    region character varying(200),
    breakdown text,
    contributing_factors text,
    metadata text,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 预测场景
CREATE TABLE scrm.scrm_forecast_scenario (
    id bigint NOT NULL,
    scenario_name character varying(200) NOT NULL,
    scenario_code character varying(50) NOT NULL,
    description character varying(500),
    model_id bigint NOT NULL,
    model_name character varying(200),
    scenario_type character varying(30) DEFAULT 'BASELINE'::character varying NOT NULL,
    target_period character varying(20) NOT NULL,
    target_start_date date NOT NULL,
    target_end_date date NOT NULL,
    granularity character varying(20) DEFAULT 'MONTHLY'::character varying NOT NULL,
    assumptions character varying(2000),
    input_parameters text,
    adjustment_factors text,
    segments character varying(500),
    products character varying(500),
    channels character varying(500),
    regions character varying(500),
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    run_started_at timestamp without time zone,
    run_completed_at timestamp without time zone,
    run_duration_ms integer DEFAULT 0,
    total_forecast_value double precision DEFAULT 0,
    confidence_level double precision DEFAULT 0.95,
    confidence_lower_bound double precision DEFAULT 0,
    confidence_upper_bound double precision DEFAULT 0,
    accuracy_estimate double precision DEFAULT 0,
    risk_factors character varying(1000),
    opportunities character varying(1000),
    recommendations character varying(2000),
    approved_by character varying(100),
    approved_at timestamp without time zone,
    is_approved boolean DEFAULT false NOT NULL,
    shared_with character varying(500),
    tags character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 漏斗表
CREATE TABLE scrm.scrm_funnel (
    id bigint NOT NULL,
    funnel_name character varying(200) NOT NULL,
    description character varying(500),
    is_default boolean DEFAULT false NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 漏斗分析表
CREATE TABLE scrm.scrm_funnel_analysis (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    funnel_name character varying(200) NOT NULL,
    funnel_code character varying(50) NOT NULL,
    description character varying(500),
    campaign_id bigint,
    campaign_name character varying(200),
    funnel_type character varying(30) NOT NULL,
    stages text NOT NULL,
    stage_count integer NOT NULL,
    total_entrants integer,
    total_completed integer,
    overall_conversion_rate double precision,
    overall_drop_off_rate double precision,
    avg_time_to_complete double precision,
    stage_metrics text,
    bottlenecks text,
    best_performing_stage character varying(200),
    worst_performing_stage character varying(200),
    max_drop_off_stage character varying(200),
    segment_analysis text,
    device_analysis text,
    channel_analysis text,
    time_range character varying(50) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    last_calculated_at timestamp without time zone,
    calculation_status character varying(20) NOT NULL,
    insights character varying(2000),
    recommendations character varying(2000),
    tags character varying(500),
    created_by character varying(100)
);
-- 漏斗阶段表
CREATE TABLE scrm.scrm_funnel_stage (
    id bigint NOT NULL,
    funnel_id bigint NOT NULL,
    stage_name character varying(100) NOT NULL,
    stage_order integer NOT NULL,
    description character varying(500),
    enter_condition character varying(500),
    exit_condition character varying(500),
    is_closed_stage boolean DEFAULT false NOT NULL,
    is_lost_stage boolean DEFAULT false NOT NULL,
    probability integer,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 健康告警
CREATE TABLE scrm.scrm_health_alert (
    id bigint NOT NULL,
    alert_name character varying(200) NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    health_score_id bigint,
    alert_type character varying(30) NOT NULL,
    severity character varying(20) DEFAULT 'WARNING'::character varying NOT NULL,
    trigger_value double precision DEFAULT 0,
    threshold_value double precision DEFAULT 0,
    condition character varying(100),
    description character varying(500),
    risk_factors character varying(1000),
    recommended_actions character varying(1000),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    assigned_to character varying(100),
    assigned_at timestamp without time zone,
    acknowledged_by character varying(100),
    acknowledged_at timestamp without time zone,
    resolved_by character varying(100),
    resolved_at timestamp without time zone,
    resolution_note character varying(500),
    triggered_at timestamp without time zone NOT NULL,
    metadata text,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 健康分模型
CREATE TABLE scrm.scrm_health_score_model (
    id bigint NOT NULL,
    model_name character varying(200) NOT NULL,
    model_code character varying(50) NOT NULL,
    description character varying(500),
    applicable_segment character varying(500),
    metrics text NOT NULL,
    scoring_type character varying(20) DEFAULT 'WEIGHTED'::character varying NOT NULL,
    total_max_score integer DEFAULT 100 NOT NULL,
    health_thresholds text,
    is_default boolean DEFAULT false NOT NULL,
    is_published boolean DEFAULT false NOT NULL,
    version_no integer DEFAULT 1,
    applied_count integer DEFAULT 0,
    last_applied_at timestamp without time zone,
    update_frequency character varying(20) DEFAULT 'DAILY'::character varying NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 身份合并历史
CREATE TABLE scrm.scrm_identity_merge_history (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    source_customer_id bigint NOT NULL,
    source_customer_name character varying(200),
    target_customer_id bigint NOT NULL,
    target_customer_name character varying(200),
    merged_identities_count integer DEFAULT 0,
    merged_transactions_count integer DEFAULT 0,
    merged_tags character varying(1000),
    field_changes text,
    identities_merged character varying(1000),
    pre_merge_ltv double precision DEFAULT 0,
    post_merge_ltv double precision DEFAULT 0,
    data_integrity_checked boolean DEFAULT false NOT NULL,
    data_integrity_passed boolean DEFAULT false NOT NULL,
    rollback_available boolean DEFAULT true NOT NULL,
    rolled_back boolean DEFAULT false NOT NULL,
    rolled_back_at timestamp without time zone,
    rolled_back_by character varying(100),
    merged_at timestamp without time zone NOT NULL,
    merged_by character varying(100),
    notes character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 身份合并规则
CREATE TABLE scrm.scrm_identity_merge_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    description character varying(500),
    match_fields character varying(500) NOT NULL,
    match_threshold double precision DEFAULT 0.8 NOT NULL,
    fuzzy_match_fields character varying(500),
    fuzzy_match_threshold double precision DEFAULT 0.9,
    auto_merge boolean DEFAULT false NOT NULL,
    field_strategy text,
    exclude_fields character varying(500),
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    match_count integer DEFAULT 0,
    merge_count integer DEFAULT 0,
    last_executed_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 身份合并任务
CREATE TABLE scrm.scrm_identity_merge_task (
    id bigint NOT NULL,
    task_name character varying(200) NOT NULL,
    source_customer_id bigint NOT NULL,
    source_customer_name character varying(200),
    target_customer_id bigint NOT NULL,
    target_customer_name character varying(200),
    merge_type character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    match_reasons character varying(1000),
    match_score double precision DEFAULT 0,
    matched_fields character varying(500),
    merge_config text,
    conflict_fields character varying(1000),
    identity_count integer DEFAULT 0,
    transaction_count integer DEFAULT 0,
    review_by character varying(100),
    reviewed_at timestamp without time zone,
    review_comment character varying(500),
    approved_by character varying(100),
    approved_at timestamp without time zone,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    failed_reason character varying(1000),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 导入任务
CREATE TABLE scrm.scrm_import_task (
    id bigint NOT NULL,
    template_id bigint,
    task_name character varying(200) NOT NULL,
    data_type character varying(30) NOT NULL,
    file_path character varying(500) NOT NULL,
    file_name character varying(200) NOT NULL,
    file_size integer,
    file_type character varying(20) DEFAULT 'CSV'::character varying NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    total_records integer DEFAULT 0,
    valid_records integer DEFAULT 0,
    invalid_records integer DEFAULT 0,
    imported_records integer DEFAULT 0,
    failed_records integer DEFAULT 0,
    skipped_records integer DEFAULT 0,
    error_report character varying(500),
    error_details text,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    duration_ms integer,
    triggered_by character varying(100) NOT NULL,
    options text,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_import_task IS 'SCRM 数据导入任务表, 批量数据导入任务';
COMMENT ON COLUMN scrm.scrm_import_task.template_id IS '关联导入模板 ID (可空)';
COMMENT ON COLUMN scrm.scrm_import_task.task_name IS '任务名称';
COMMENT ON COLUMN scrm.scrm_import_task.data_type IS '数据类型: CUSTOMER/CONTACT/FOLLOW_RECORD/TAG/PRODUCT/ORDER/OTHER';
COMMENT ON COLUMN scrm.scrm_import_task.file_path IS '文件路径';
COMMENT ON COLUMN scrm.scrm_import_task.file_name IS '文件名称';
COMMENT ON COLUMN scrm.scrm_import_task.file_size IS '文件大小 KB (可空)';
COMMENT ON COLUMN scrm.scrm_import_task.file_type IS '文件类型: CSV/EXCEL/JSON (默认 CSV)';
COMMENT ON COLUMN scrm.scrm_import_task.status IS '状态: PENDING/VALIDATING/IMPORTING/SUCCESS/PARTIAL/FAILED/CANCELLED (默认 PENDING)';
COMMENT ON COLUMN scrm.scrm_import_task.total_records IS '总记录数';
COMMENT ON COLUMN scrm.scrm_import_task.valid_records IS '有效记录数';
COMMENT ON COLUMN scrm.scrm_import_task.invalid_records IS '无效记录数';
COMMENT ON COLUMN scrm.scrm_import_task.imported_records IS '已导入记录数';
COMMENT ON COLUMN scrm.scrm_import_task.failed_records IS '失败记录数';
COMMENT ON COLUMN scrm.scrm_import_task.skipped_records IS '跳过记录数';
COMMENT ON COLUMN scrm.scrm_import_task.error_report IS '错误报告路径 (可空)';
COMMENT ON COLUMN scrm.scrm_import_task.error_details IS '错误详情 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_import_task.start_time IS '开始执行时间 (可空)';
COMMENT ON COLUMN scrm.scrm_import_task.end_time IS '结束执行时间 (可空)';
COMMENT ON COLUMN scrm.scrm_import_task.duration_ms IS '执行耗时毫秒 (可空)';
COMMENT ON COLUMN scrm.scrm_import_task.triggered_by IS '触发人';
COMMENT ON COLUMN scrm.scrm_import_task.options IS '导入选项 JSON (可空)';
-- 导入模板
CREATE TABLE scrm.scrm_import_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    data_type character varying(30) NOT NULL,
    description character varying(500),
    columns text NOT NULL,
    sample_file_url character varying(500),
    validation_rules text,
    deduplication_key character varying(200),
    update_if_exists boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    usage_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_import_template IS 'SCRM 数据导入模板表, 定义批量导入的列映射与校验规则';
COMMENT ON COLUMN scrm.scrm_import_template.template_name IS '模板名称';
COMMENT ON COLUMN scrm.scrm_import_template.data_type IS '数据类型: CUSTOMER/CONTACT/FOLLOW_RECORD/TAG/PRODUCT/ORDER/OTHER';
COMMENT ON COLUMN scrm.scrm_import_template.description IS '模板描述 (可空)';
COMMENT ON COLUMN scrm.scrm_import_template.columns IS '列定义 JSON: [{name,field,type,required,enum}]';
COMMENT ON COLUMN scrm.scrm_import_template.sample_file_url IS '示例文件 URL (可空)';
COMMENT ON COLUMN scrm.scrm_import_template.validation_rules IS '校验规则 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_import_template.deduplication_key IS '去重字段 (可空)';
COMMENT ON COLUMN scrm.scrm_import_template.update_if_exists IS '存在则更新 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_import_template.enabled IS '是否启用 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_import_template.usage_count IS '使用次数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_import_template.created_by IS '创建人';
-- 继承条目表
CREATE TABLE scrm.scrm_inheritance_item (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    item_type character varying(20) NOT NULL,
    item_id bigint NOT NULL,
    item_label character varying(200),
    status character varying(20) NOT NULL,
    error_message character varying(500),
    processed_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_inheritance_item IS '离职继承明细 (逐条记录任务处理的客户/群/会话转移结果)';
COMMENT ON COLUMN scrm.scrm_inheritance_item.id IS '主键 ID (Snowflake)';
COMMENT ON COLUMN scrm.scrm_inheritance_item.task_id IS '任务 ID (引用 scrm_inheritance_task.id)';
COMMENT ON COLUMN scrm.scrm_inheritance_item.item_type IS '明细类型: CUSTOMER/GROUP/CONVERSATION';
COMMENT ON COLUMN scrm.scrm_inheritance_item.item_id IS '明细对象 ID (客户ID/群ID/会话ID)';
COMMENT ON COLUMN scrm.scrm_inheritance_item.item_label IS '明细标签 (客户昵称/群名, 便于展示)';
COMMENT ON COLUMN scrm.scrm_inheritance_item.status IS '明细状态: PENDING/SUCCESS/FAILED/SKIPPED';
COMMENT ON COLUMN scrm.scrm_inheritance_item.error_message IS '失败原因 (FAILED 状态时记录)';
COMMENT ON COLUMN scrm.scrm_inheritance_item.processed_at IS '处理时间';
COMMENT ON COLUMN scrm.scrm_inheritance_item.create_time IS '创建时间';
COMMENT ON COLUMN scrm.scrm_inheritance_item.update_time IS '更新时间';
COMMENT ON COLUMN scrm.scrm_inheritance_item.version IS '乐观锁版本号';
-- 继承任务表
CREATE TABLE scrm.scrm_inheritance_task (
    id bigint NOT NULL,
    task_name character varying(200) NOT NULL,
    from_user_id character varying(100) NOT NULL,
    to_user_id character varying(100) NOT NULL,
    platform_type character varying(30),
    status character varying(20) NOT NULL,
    total_items integer,
    success_items integer,
    fail_items integer,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    created_by character varying(100),
    note character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_inheritance_task IS '离职继承任务 (员工离职时将其名下客户/群/会话转移给他人)';
COMMENT ON COLUMN scrm.scrm_inheritance_task.id IS '主键 ID (Snowflake)';
COMMENT ON COLUMN scrm.scrm_inheritance_task.task_name IS '任务名称 (便于审计与展示)';
COMMENT ON COLUMN scrm.scrm_inheritance_task.from_user_id IS '离职人 userId';
COMMENT ON COLUMN scrm.scrm_inheritance_task.to_user_id IS '接收人 userId';
COMMENT ON COLUMN scrm.scrm_inheritance_task.platform_type IS '指定平台类型 (可空, 空表示全平台)';
COMMENT ON COLUMN scrm.scrm_inheritance_task.status IS '任务状态: PENDING/RUNNING/COMPLETED/FAILED/PARTIAL';
COMMENT ON COLUMN scrm.scrm_inheritance_task.total_items IS '总明细数';
COMMENT ON COLUMN scrm.scrm_inheritance_task.success_items IS '成功明细数';
COMMENT ON COLUMN scrm.scrm_inheritance_task.fail_items IS '失败明细数';
COMMENT ON COLUMN scrm.scrm_inheritance_task.started_at IS '任务开始时间';
COMMENT ON COLUMN scrm.scrm_inheritance_task.completed_at IS '任务完成时间';
COMMENT ON COLUMN scrm.scrm_inheritance_task.created_by IS '任务创建人 userId';
COMMENT ON COLUMN scrm.scrm_inheritance_task.note IS '任务备注';
COMMENT ON COLUMN scrm.scrm_inheritance_task.create_time IS '创建时间';
COMMENT ON COLUMN scrm.scrm_inheritance_task.update_time IS '更新时间';
COMMENT ON COLUMN scrm.scrm_inheritance_task.version IS '乐观锁版本号';
-- 互动日历
CREATE TABLE scrm.scrm_interaction_calendar (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    calendar_name character varying(200) NOT NULL,
    calendar_code character varying(50) NOT NULL,
    description character varying(500),
    calendar_type character varying(30) NOT NULL,
    owner_id character varying(100) NOT NULL,
    owner_name character varying(100),
    shared_with character varying(500),
    is_public boolean DEFAULT false NOT NULL,
    color character varying(20),
    icon character varying(200),
    working_hours_start character varying(10) NOT NULL,
    working_hours_end character varying(10) NOT NULL,
    working_days character varying(20) NOT NULL,
    timezone character varying(50) NOT NULL,
    default_reminder_minutes integer,
    default_duration_minutes integer,
    plan_count integer,
    completed_count integer,
    cancelled_count integer,
    completion_rate double precision,
    last_activity_date date,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100)
);
-- 互动计划
CREATE TABLE scrm.scrm_interaction_plan (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    plan_name character varying(200) NOT NULL,
    plan_code character varying(50) NOT NULL,
    description character varying(500),
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    interaction_type character varying(30) NOT NULL,
    interaction_method character varying(30) NOT NULL,
    title character varying(500) NOT NULL,
    content character varying(2000),
    objectives character varying(1000),
    prepare_materials character varying(1000),
    scheduled_start timestamp without time zone NOT NULL,
    scheduled_end timestamp without time zone,
    actual_start timestamp without time zone,
    actual_end timestamp without time zone,
    timezone character varying(50) NOT NULL,
    location character varying(500),
    location_type character varying(30),
    owner_id character varying(100) NOT NULL,
    owner_name character varying(100),
    participant_ids character varying(500),
    customer_contact_id bigint,
    customer_contact_name character varying(200),
    reminder_type character varying(30) NOT NULL,
    reminder_minutes_before integer,
    is_reminder_sent boolean DEFAULT false NOT NULL,
    reminder_sent_at timestamp without time zone,
    repeat_type character varying(30) NOT NULL,
    repeat_interval integer,
    repeat_end_date date,
    repeat_count integer,
    max_repeat_count integer,
    week_days character varying(20),
    month_day integer,
    priority character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    completion_notes character varying(2000),
    outcome character varying(30),
    follow_up_action character varying(500),
    follow_up_date date,
    tags character varying(500),
    color character varying(20),
    is_all_day boolean DEFAULT false NOT NULL,
    is_pinned boolean DEFAULT false NOT NULL,
    attachments character varying(1000),
    related_plan_id bigint,
    created_by character varying(100)
);
-- 发票表
CREATE TABLE scrm.scrm_invoice (
    id bigint NOT NULL,
    invoice_no character varying(100) NOT NULL,
    application_no character varying(100),
    invoice_type character varying(30) NOT NULL,
    invoice_category character varying(30) NOT NULL,
    title_type character varying(20) NOT NULL,
    invoice_title character varying(500) NOT NULL,
    tax_number character varying(50),
    bank_name character varying(200),
    bank_account character varying(100),
    company_address character varying(500),
    company_phone character varying(50),
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    customer_contact character varying(200),
    customer_phone character varying(50),
    customer_email character varying(200),
    order_id character varying(100),
    contract_id bigint,
    amount double precision DEFAULT 0 NOT NULL,
    tax_rate double precision DEFAULT 0,
    tax_amount double precision DEFAULT 0,
    total_amount double precision DEFAULT 0 NOT NULL,
    discount_amount double precision DEFAULT 0,
    actual_amount double precision DEFAULT 0,
    currency character varying(10) DEFAULT 'CNY'::character varying NOT NULL,
    invoice_date date,
    invoice_items text,
    remark character varying(500),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    apply_reason character varying(500),
    applied_by character varying(100),
    applied_at timestamp without time zone,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    approval_comment character varying(500),
    issued_by character varying(100),
    issued_at timestamp without time zone,
    invoice_url character varying(500),
    invoice_image character varying(500),
    delivery_method character varying(20),
    delivery_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    sent_at timestamp without time zone,
    delivered_at timestamp without time zone,
    tracking_number character varying(200),
    delivery_address character varying(500),
    delivery_recipient character varying(100),
    delivery_phone character varying(50),
    void_reason character varying(500),
    voided_by character varying(100),
    voided_at timestamp without time zone,
    red_flush_reason character varying(500),
    red_flushed_by character varying(100),
    red_flushed_at timestamp without time zone,
    original_invoice_id bigint,
    red_flush_invoice_id bigint,
    tax_bureau_code character varying(50),
    tax_bureau_name character varying(200),
    device_no character varying(100),
    invoice_code character varying(50),
    invoice_number character varying(50),
    check_code character varying(100),
    qr_code character varying(1000),
    tags character varying(500),
    notes character varying(1000),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 发票模板
CREATE TABLE scrm.scrm_invoice_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    template_code character varying(50) NOT NULL,
    description character varying(500),
    invoice_type character varying(30) NOT NULL,
    default_tax_rate double precision DEFAULT 0.13,
    default_items text,
    applicable_products character varying(500),
    remarks character varying(500),
    required_fields character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    usage_count integer DEFAULT 0 NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 旅程报名
CREATE TABLE scrm.scrm_journey_enrollment (
    id bigint NOT NULL,
    journey_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_nickname character varying(200),
    current_step_id bigint,
    entry_source character varying(50) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    entered_at timestamp without time zone NOT NULL,
    completed_at timestamp without time zone,
    exited_at timestamp without time zone,
    exit_reason character varying(500),
    last_step_at timestamp without time zone,
    next_step_at timestamp without time zone,
    progress integer DEFAULT 0,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 旅程进度日志
CREATE TABLE scrm.scrm_journey_progress_log (
    id bigint NOT NULL,
    enrollment_id bigint NOT NULL,
    journey_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    step_id bigint NOT NULL,
    step_name character varying(200),
    step_type character varying(30) NOT NULL,
    action_result character varying(20) NOT NULL,
    action_detail character varying(500),
    executed_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 旅程步骤
CREATE TABLE scrm.scrm_journey_step (
    id bigint NOT NULL,
    journey_id bigint NOT NULL,
    step_name character varying(200) NOT NULL,
    step_type character varying(30) NOT NULL,
    step_order integer NOT NULL,
    config text NOT NULL,
    next_step_id bigint,
    is_entry_point boolean DEFAULT false NOT NULL,
    description character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 知识文章
CREATE TABLE scrm.scrm_knowledge_article (
    id bigint NOT NULL,
    title character varying(500) NOT NULL,
    article_code character varying(50) NOT NULL,
    category_id bigint,
    category_name character varying(200),
    summary character varying(1000),
    content text NOT NULL,
    content_type character varying(20) DEFAULT 'MARKDOWN'::character varying NOT NULL,
    article_type character varying(30) DEFAULT 'ARTICLE'::character varying NOT NULL,
    tags character varying(500),
    keywords character varying(500),
    cover_image character varying(500),
    attachments character varying(1000),
    related_articles character varying(500),
    related_products character varying(500),
    applicable_scenarios character varying(500),
    difficulty_level character varying(20) DEFAULT 'BEGINNER'::character varying NOT NULL,
    reading_time_minutes integer DEFAULT 5,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    version_number integer DEFAULT 1,
    current_version_id bigint,
    review_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    reviewed_by character varying(100),
    reviewed_at timestamp without time zone,
    review_comment character varying(500),
    published_at timestamp without time zone,
    last_modified_at timestamp without time zone,
    author_id character varying(100),
    author_name character varying(100),
    view_count integer DEFAULT 0,
    unique_view_count integer DEFAULT 0,
    like_count integer DEFAULT 0,
    dislike_count integer DEFAULT 0,
    favorite_count integer DEFAULT 0,
    share_count integer DEFAULT 0,
    comment_count integer DEFAULT 0,
    helpful_count integer DEFAULT 0,
    not_helpful_count integer DEFAULT 0,
    helpful_rate double precision DEFAULT 0,
    avg_rating double precision DEFAULT 0,
    rating_count integer DEFAULT 0,
    is_featured boolean DEFAULT false NOT NULL,
    is_pinned boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 知识分类
CREATE TABLE scrm.scrm_knowledge_category (
    id bigint NOT NULL,
    category_name character varying(200) NOT NULL,
    category_code character varying(50) NOT NULL,
    description character varying(500),
    parent_id bigint,
    category_level integer DEFAULT 1,
    category_path character varying(1000),
    sort_order integer DEFAULT 0,
    icon character varying(100),
    color character varying(20),
    article_count integer DEFAULT 0,
    total_views integer DEFAULT 0,
    total_likes integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    visible_to_roles character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 知识反馈
CREATE TABLE scrm.scrm_knowledge_feedback (
    id bigint NOT NULL,
    article_id bigint NOT NULL,
    article_title character varying(500),
    feedback_type character varying(20) NOT NULL,
    user_id character varying(100) NOT NULL,
    user_name character varying(100),
    user_role character varying(50),
    rating integer,
    comment character varying(2000),
    comment_type character varying(20),
    parent_comment_id bigint,
    is_internal boolean DEFAULT false NOT NULL,
    report_reason character varying(500),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    resolved_by character varying(100),
    resolved_at timestamp without time zone,
    resolution_note character varying(500),
    upvote_count integer DEFAULT 0,
    metadata text,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 线索分配
CREATE TABLE scrm.scrm_lead_assignment (
    id bigint NOT NULL,
    public_sea_customer_id bigint NOT NULL,
    assigned_to character varying(100) NOT NULL,
    assigned_by character varying(100),
    assignment_type character varying(20) NOT NULL,
    previous_owner character varying(100),
    status character varying(20) NOT NULL,
    assigned_at timestamp without time zone NOT NULL,
    recalled_at timestamp without time zone,
    expire_at timestamp without time zone,
    note character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_lead_assignment IS '线索分配流水 (领取/分配/转移/回收审计轨迹)';
COMMENT ON COLUMN scrm.scrm_lead_assignment.id IS '主键 ID (Snowflake)';
COMMENT ON COLUMN scrm.scrm_lead_assignment.public_sea_customer_id IS '公海客户 ID (引用 scrm_public_sea_customer.id)';
COMMENT ON COLUMN scrm.scrm_lead_assignment.assigned_to IS '被分配人 userId';
COMMENT ON COLUMN scrm.scrm_lead_assignment.assigned_by IS '分配人 userId (CLAIM 类型时与 assigned_to 相同)';
COMMENT ON COLUMN scrm.scrm_lead_assignment.assignment_type IS '分配类型: CLAIM(领取)/ASSIGN(分配)/TRANSFER(转移)';
COMMENT ON COLUMN scrm.scrm_lead_assignment.previous_owner IS '上一手归属人 userId (TRANSFER 时记录, 可空)';
COMMENT ON COLUMN scrm.scrm_lead_assignment.status IS '分配状态: ACTIVE/RECALLED/TRANSFERRED/CONVERTED';
COMMENT ON COLUMN scrm.scrm_lead_assignment.assigned_at IS '分配时间';
COMMENT ON COLUMN scrm.scrm_lead_assignment.recalled_at IS '回收时间 (RECALLED 状态时记录)';
COMMENT ON COLUMN scrm.scrm_lead_assignment.expire_at IS '分配过期时间 (超时自动回收的阈值)';
COMMENT ON COLUMN scrm.scrm_lead_assignment.note IS '分配备注 (可空)';
COMMENT ON COLUMN scrm.scrm_lead_assignment.create_time IS '创建时间';
COMMENT ON COLUMN scrm.scrm_lead_assignment.update_time IS '更新时间';
COMMENT ON COLUMN scrm.scrm_lead_assignment.version IS '乐观锁版本号';
-- 线索维度
CREATE TABLE scrm.scrm_lead_dimension (
    id bigint NOT NULL,
    dimension_name character varying(100) NOT NULL,
    dimension_code character varying(50) NOT NULL,
    description character varying(500),
    dimension_category character varying(50) NOT NULL,
    default_weight double precision DEFAULT 1.0 NOT NULL,
    default_max_score integer DEFAULT 20 NOT NULL,
    scoring_rules text NOT NULL,
    applicable_fields character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    usage_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 线索评分
CREATE TABLE scrm.scrm_lead_score (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    model_id bigint NOT NULL,
    total_score double precision DEFAULT 0 NOT NULL,
    max_score double precision DEFAULT 100,
    score_percent double precision DEFAULT 0,
    grade character varying(20),
    grade_label character varying(50),
    dimension_scores text,
    conversion_probability double precision DEFAULT 0,
    predicted_value double precision DEFAULT 0,
    is_hot_lead boolean DEFAULT false NOT NULL,
    is_qualified boolean DEFAULT false NOT NULL,
    last_calculated_at timestamp without time zone NOT NULL,
    score_trend character varying(20),
    trend_change double precision DEFAULT 0,
    previous_score double precision DEFAULT 0,
    assigned_to character varying(100),
    assigned_at timestamp without time zone,
    contacted_at timestamp without time zone,
    converted_at timestamp without time zone,
    is_converted boolean DEFAULT false NOT NULL,
    conversion_value double precision DEFAULT 0,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 线索评分模型
CREATE TABLE scrm.scrm_lead_scoring_model (
    id bigint NOT NULL,
    model_name character varying(200) NOT NULL,
    model_code character varying(50) NOT NULL,
    description character varying(500),
    model_type character varying(20) DEFAULT 'RULE_BASED'::character varying NOT NULL,
    dimensions text NOT NULL,
    total_max_score integer DEFAULT 100 NOT NULL,
    grade_thresholds text,
    is_default boolean DEFAULT false NOT NULL,
    is_published boolean DEFAULT false NOT NULL,
    version_no integer DEFAULT 1,
    applied_count integer DEFAULT 0,
    last_applied_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 生命周期历史
CREATE TABLE scrm.scrm_lifecycle_history (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    from_stage_id bigint,
    from_stage_code character varying(50),
    from_stage_name character varying(100),
    to_stage_id bigint NOT NULL,
    to_stage_code character varying(50) NOT NULL,
    to_stage_name character varying(100),
    transition_id bigint,
    transition_type character varying(20) NOT NULL,
    trigger_event character varying(100),
    trigger_description character varying(500),
    duration_in_previous_stage integer,
    operator_id character varying(100),
    operator_name character varying(100),
    transition_time timestamp without time zone NOT NULL,
    metadata text,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 生命周期阶段
CREATE TABLE scrm.scrm_lifecycle_stage (
    id bigint NOT NULL,
    stage_name character varying(100) NOT NULL,
    stage_code character varying(50) NOT NULL,
    description character varying(500),
    stage_order integer NOT NULL,
    stage_category character varying(30) NOT NULL,
    color character varying(20),
    icon character varying(100),
    entry_criteria text,
    exit_criteria text,
    target_duration_days integer,
    is_start_stage boolean DEFAULT false NOT NULL,
    is_end_stage boolean DEFAULT false NOT NULL,
    is_churn_stage boolean DEFAULT false NOT NULL,
    customer_count integer DEFAULT 0,
    total_entered_count integer DEFAULT 0,
    avg_duration_days double precision DEFAULT 0,
    conversion_rate double precision DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    min_duration_days integer,
    max_duration_days integer,
    is_conversion_stage boolean DEFAULT false NOT NULL,
    conversion_target_stage character varying(50),
    auto_transition boolean DEFAULT false NOT NULL,
    transition_rules character varying(2000),
    stage_value double precision DEFAULT 0,
    stage_value_description character varying(500),
    applicable_segments character varying(500),
    actions character varying(2000),
    automations character varying(2000),
    metrics character varying(1000),
    benchmarks character varying(1000),
    display_order integer DEFAULT 0,
    is_visible boolean DEFAULT true NOT NULL,
    is_system boolean DEFAULT false NOT NULL,
    churn_rate double precision DEFAULT 0,
    entry_rate double precision DEFAULT 0,
    exit_rate double precision DEFAULT 0,
    stage_color character varying(20),
    stage_icon character varying(200)
);
-- 生命周期流转
CREATE TABLE scrm.scrm_lifecycle_transition (
    id bigint NOT NULL,
    from_stage_id bigint,
    from_stage_code character varying(50),
    to_stage_id bigint NOT NULL,
    to_stage_code character varying(50) NOT NULL,
    transition_name character varying(200) NOT NULL,
    transition_type character varying(20) DEFAULT 'AUTO'::character varying NOT NULL,
    trigger_condition text,
    trigger_events character varying(500),
    priority integer DEFAULT 0,
    cooldown_days integer DEFAULT 0,
    is_enabled boolean DEFAULT true NOT NULL,
    trigger_count integer DEFAULT 0,
    last_triggered_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    customer_id bigint,
    customer_name character varying(200),
    lifecycle_id bigint,
    from_stage character varying(50),
    to_stage character varying(50),
    trigger character varying(200) DEFAULT 'MANUAL'::character varying NOT NULL,
    trigger_detail character varying(500),
    trigger_event_id bigint,
    transition_date timestamp without time zone,
    duration_in_previous_stage integer,
    expected_duration integer,
    is_on_time boolean,
    is_positive boolean,
    transition_value double precision DEFAULT 0,
    revenue_impact double precision DEFAULT 0,
    automation_triggered boolean DEFAULT false NOT NULL,
    automation_name character varying(200),
    actions_taken character varying(1000),
    follow_up_actions character varying(1000),
    assigned_to character varying(100),
    approved_by character varying(100),
    approved_at timestamp without time zone,
    is_reversed boolean DEFAULT false NOT NULL,
    reversed_at timestamp without time zone,
    reversed_by character varying(100),
    reverse_reason character varying(500),
    notes character varying(1000)
);
-- LTV 分群
CREATE TABLE scrm.scrm_ltv_cohort (
    id bigint NOT NULL,
    cohort_name character varying(200) NOT NULL,
    cohort_type character varying(30) DEFAULT 'ACQUISITION_MONTH'::character varying NOT NULL,
    cohort_key character varying(200) NOT NULL,
    cohort_start_date date NOT NULL,
    cohort_size integer DEFAULT 0 NOT NULL,
    period_months integer NOT NULL,
    avg_ltv double precision DEFAULT 0,
    median_ltv double precision DEFAULT 0,
    total_revenue double precision DEFAULT 0,
    avg_revenue double precision DEFAULT 0,
    avg_orders integer DEFAULT 0,
    retention_rate double precision DEFAULT 0,
    active_customers integer DEFAULT 0,
    churned_customers integer DEFAULT 0,
    avg_customer_age_days integer DEFAULT 0,
    top_tier_customers integer DEFAULT 0,
    calculated_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- LTV 模型
CREATE TABLE scrm.scrm_ltv_model (
    id bigint NOT NULL,
    model_name character varying(200) NOT NULL,
    model_code character varying(50) NOT NULL,
    description character varying(500),
    model_type character varying(30) NOT NULL,
    calculation_method character varying(30) NOT NULL,
    lookback_days integer DEFAULT 365 NOT NULL,
    forecast_days integer DEFAULT 365 NOT NULL,
    discount_rate double precision DEFAULT 0.1,
    churn_rate double precision DEFAULT 0.05,
    avg_profit_margin double precision DEFAULT 0.3,
    purchase_frequency_threshold integer DEFAULT 2,
    tier_thresholds text,
    is_default boolean DEFAULT false NOT NULL,
    is_published boolean DEFAULT false NOT NULL,
    model_version integer DEFAULT 1,
    applied_count integer DEFAULT 0,
    last_applied_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 营销活动表
CREATE TABLE scrm.scrm_marketing_campaign (
    id bigint NOT NULL,
    campaign_name character varying(200) NOT NULL,
    campaign_type character varying(30) NOT NULL,
    description character varying(1000),
    objective character varying(500),
    target_segment character varying(500),
    channels character varying(500) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    budget double precision DEFAULT 0,
    actual_cost double precision DEFAULT 0,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    manager_id character varying(100),
    manager_name character varying(100),
    priority integer DEFAULT 0,
    tags character varying(500),
    metrics_json text,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 营销活动渠道
CREATE TABLE scrm.scrm_marketing_campaign_channel (
    id bigint NOT NULL,
    campaign_id bigint NOT NULL,
    channel character varying(30) NOT NULL,
    channel_config text,
    content_title character varying(200),
    content_body text,
    content_image character varying(500),
    link_url character varying(500),
    scheduled_at timestamp without time zone,
    sent_at timestamp without time zone,
    target_count integer DEFAULT 0,
    sent_count integer DEFAULT 0,
    delivered_count integer DEFAULT 0,
    read_count integer DEFAULT 0,
    click_count integer DEFAULT 0,
    convert_count integer DEFAULT 0,
    cost double precision DEFAULT 0,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 营销活动参与者
CREATE TABLE scrm.scrm_marketing_campaign_participant (
    id bigint NOT NULL,
    campaign_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    channel character varying(30) NOT NULL,
    participated_at timestamp without time zone NOT NULL,
    actions character varying(500),
    converted boolean DEFAULT false NOT NULL,
    converted_at timestamp without time zone,
    conversion_value double precision DEFAULT 0,
    response_content character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 营销触发器
CREATE TABLE scrm.scrm_marketing_trigger (
    id bigint NOT NULL,
    trigger_name character varying(200) NOT NULL,
    description character varying(500),
    event_type character varying(50) NOT NULL,
    event_condition text,
    condition_type character varying(20) DEFAULT 'ALL'::character varying NOT NULL,
    cooldown_hours integer DEFAULT 0,
    max_triggers_per_customer integer DEFAULT 0,
    action_type character varying(30) NOT NULL,
    action_params text NOT NULL,
    action_delay_minutes integer DEFAULT 0,
    priority integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    trigger_count integer DEFAULT 0,
    last_trigger_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_marketing_trigger IS 'SCRM 触发式营销规则表, 事件驱动的自动营销';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.trigger_name IS '触发器名称';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.description IS '描述 (可空)';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.event_type IS '事件类型: CUSTOMER_ADDED/CUSTOMER_TAGGED/LIFECYCLE_CHANGED/CONVERSATION_STARTED/OPPORTUNITY_STAGE_CHANGED/MASS_SEND_COMPLETED/CART_ABANDONED/INTERACTION_TIMEOUT/BIRTHDAY/ANNIVERSARY';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.event_condition IS '触发条件 JSON (可空, 如 {platformType,tagIds,lifecycle})';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.condition_type IS '条件类型: ALL(全部满足)/ANY(任一满足)';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.cooldown_hours IS '同一客户冷却期小时数, 0=不限';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.max_triggers_per_customer IS '每客户最大触发次数, 0=不限';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.action_type IS '动作类型: SEND_MESSAGE/ADD_TAG/SET_LIFECYCLE/ENROLL_JOURNEY/NOTIFY_USER/TRIGGER_MASS_SEND';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.action_params IS '动作参数 JSON: {messageTemplateId,tagIds,lifecycle,journeyId,notifyUserId,massSendTaskId}';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.action_delay_minutes IS '延迟执行分钟数';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.priority IS '优先级 (数值越大优先级越高)';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.enabled IS '是否启用';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.trigger_count IS '已触发次数';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.last_trigger_at IS '最近触发时间';
COMMENT ON COLUMN scrm.scrm_marketing_trigger.created_by IS '创建人';
-- 营销触发事件
CREATE TABLE scrm.scrm_marketing_trigger_event (
    id bigint NOT NULL,
    trigger_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_nickname character varying(200),
    event_type character varying(50) NOT NULL,
    event_data text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    action_type character varying(30) NOT NULL,
    action_result character varying(500),
    error_message character varying(500),
    scheduled_at timestamp without time zone NOT NULL,
    executed_at timestamp without time zone,
    trigger_count integer DEFAULT 0,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_marketing_trigger_event IS 'SCRM 触发式营销事件记录表, 记录每次事件触发的执行明细';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.trigger_id IS '触发器 ID (引用 scrm_marketing_trigger.id)';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.customer_id IS '客户 ID (引用 scrm_customer.id)';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.customer_nickname IS '客户昵称 (冗余存储便于展示)';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.event_type IS '事件类型';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.event_data IS '事件数据 JSON (触发上下文)';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.status IS '状态: PENDING/EXECUTING/SUCCESS/FAILED/SKIPPED/COOLDOWN';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.action_type IS '动作类型';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.action_result IS '动作执行结果';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.error_message IS '错误信息 (执行失败时填充)';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.scheduled_at IS '计划执行时间 (含延迟)';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.executed_at IS '实际执行时间';
COMMENT ON COLUMN scrm.scrm_marketing_trigger_event.trigger_count IS '该客户已触发次数 (用于 maxTriggersPerCustomer 限流判断)';
-- 群发目标
CREATE TABLE scrm.scrm_mass_send_target (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_nickname character varying(200),
    platform_customer_uid character varying(200),
    status character varying(20) NOT NULL,
    error_message character varying(500),
    sent_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_mass_send_target IS 'SCRM 群发目标明细表, 记录每个目标客户的发送状态';
COMMENT ON COLUMN scrm.scrm_mass_send_target.task_id IS '群发任务 ID';
COMMENT ON COLUMN scrm.scrm_mass_send_target.customer_id IS '客户 ID';
COMMENT ON COLUMN scrm.scrm_mass_send_target.customer_nickname IS '客户昵称';
COMMENT ON COLUMN scrm.scrm_mass_send_target.platform_customer_uid IS '平台客户唯一标识';
COMMENT ON COLUMN scrm.scrm_mass_send_target.status IS '发送状态: PENDING/SENT/FAILED';
COMMENT ON COLUMN scrm.scrm_mass_send_target.error_message IS '发送失败原因';
COMMENT ON COLUMN scrm.scrm_mass_send_target.sent_at IS '实际发送时间';
-- 群发任务
CREATE TABLE scrm.scrm_mass_send_task (
    id bigint NOT NULL,
    task_name character varying(200) NOT NULL,
    platform_type character varying(30) NOT NULL,
    message_template_id bigint,
    content text NOT NULL,
    target_type character varying(20) NOT NULL,
    target_filter text,
    sender_account_id bigint NOT NULL,
    status character varying(20) NOT NULL,
    total_count integer DEFAULT 0,
    sent_count integer DEFAULT 0,
    success_count integer DEFAULT 0,
    fail_count integer DEFAULT 0,
    scheduled_at timestamp without time zone,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_mass_send_task IS 'SCRM 群发任务表, 批量触达客户';
COMMENT ON COLUMN scrm.scrm_mass_send_task.task_name IS '任务名称';
COMMENT ON COLUMN scrm.scrm_mass_send_task.platform_type IS '平台类型';
COMMENT ON COLUMN scrm.scrm_mass_send_task.message_template_id IS '关联消息模板 ID (可空)';
COMMENT ON COLUMN scrm.scrm_mass_send_task.content IS '群发内容文本';
COMMENT ON COLUMN scrm.scrm_mass_send_task.target_type IS '目标类型: ALL/SEGMENT/TAG/LIST';
COMMENT ON COLUMN scrm.scrm_mass_send_task.target_filter IS '目标筛选条件 JSON (targetType=TAG/SEGMENT/LIST 时使用)';
COMMENT ON COLUMN scrm.scrm_mass_send_task.sender_account_id IS '发送账号 ID';
COMMENT ON COLUMN scrm.scrm_mass_send_task.status IS '任务状态: DRAFT/PENDING/RUNNING/PAUSED/COMPLETED/FAILED';
COMMENT ON COLUMN scrm.scrm_mass_send_task.total_count IS '目标客户总数';
COMMENT ON COLUMN scrm.scrm_mass_send_task.sent_count IS '已发送数';
COMMENT ON COLUMN scrm.scrm_mass_send_task.success_count IS '发送成功数';
COMMENT ON COLUMN scrm.scrm_mass_send_task.fail_count IS '发送失败数';
COMMENT ON COLUMN scrm.scrm_mass_send_task.scheduled_at IS '计划发送时间';
COMMENT ON COLUMN scrm.scrm_mass_send_task.started_at IS '实际开始发送时间';
COMMENT ON COLUMN scrm.scrm_mass_send_task.completed_at IS '发送完成时间';
COMMENT ON COLUMN scrm.scrm_mass_send_task.created_by IS '创建人';
-- 素材表
CREATE TABLE scrm.scrm_material (
    id bigint NOT NULL,
    material_name character varying(200) NOT NULL,
    material_type character varying(20) NOT NULL,
    file_url character varying(500) NOT NULL,
    file_size bigint,
    file_size_text character varying(50),
    thumbnail_url character varying(500),
    description character varying(500),
    tags character varying(500),
    category_id bigint,
    download_count integer DEFAULT 0,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    uploaded_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_material IS 'SCRM 素材库表, 支持图文/视频/文件/音频/链接';
COMMENT ON COLUMN scrm.scrm_material.material_name IS '素材名称';
COMMENT ON COLUMN scrm.scrm_material.material_type IS '素材类型: IMAGE/VIDEO/FILE/AUDIO/LINK';
COMMENT ON COLUMN scrm.scrm_material.file_url IS '文件 URL';
COMMENT ON COLUMN scrm.scrm_material.file_size IS '文件大小 (字节)';
COMMENT ON COLUMN scrm.scrm_material.file_size_text IS '文件大小文本, 如 1.5MB';
COMMENT ON COLUMN scrm.scrm_material.thumbnail_url IS '缩略图 URL';
COMMENT ON COLUMN scrm.scrm_material.description IS '素材描述';
COMMENT ON COLUMN scrm.scrm_material.tags IS '标签 (逗号分隔)';
COMMENT ON COLUMN scrm.scrm_material.category_id IS '分类 ID, 可空表示未分类';
COMMENT ON COLUMN scrm.scrm_material.download_count IS '下载次数';
COMMENT ON COLUMN scrm.scrm_material.status IS '状态: ACTIVE/INACTIVE';
COMMENT ON COLUMN scrm.scrm_material.uploaded_by IS '上传人';
COMMENT ON COLUMN scrm.scrm_material.create_time IS '创建时间';
COMMENT ON COLUMN scrm.scrm_material.update_time IS '更新时间';
COMMENT ON COLUMN scrm.scrm_material.version IS '乐观锁版本号';
-- 会员权益
CREATE TABLE scrm.scrm_membership_benefit (
    id bigint NOT NULL,
    benefit_name character varying(200) NOT NULL,
    benefit_code character varying(50) NOT NULL,
    benefit_type character varying(30) NOT NULL,
    description character varying(500),
    tier_id bigint,
    tier_name character varying(200),
    value double precision DEFAULT 0,
    value_type character varying(20),
    applicable_products character varying(500),
    applicable_categories character varying(500),
    applicable_channels character varying(500),
    usage_limit_per_member integer DEFAULT 0,
    usage_limit_per_day integer DEFAULT 0,
    usage_limit_per_month integer DEFAULT 0,
    usage_limit_total integer DEFAULT 0,
    current_usage_count integer DEFAULT 0,
    member_usage_count integer DEFAULT 0,
    start_date date,
    end_date date,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    redemption_instructions character varying(1000),
    terms character varying(1000),
    icon character varying(200),
    display_order integer DEFAULT 0,
    is_visible boolean DEFAULT true NOT NULL,
    total_redeemed_value double precision DEFAULT 0,
    total_saved_amount double precision DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_membership_benefit IS 'SCRM 会员权益表';
COMMENT ON COLUMN scrm.scrm_membership_benefit.benefit_name IS '权益名称';
COMMENT ON COLUMN scrm.scrm_membership_benefit.benefit_code IS '权益编码 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.benefit_type IS '权益类型: DISCOUNT/FREE_SHIPPING/POINTS_MULTIPLIER/EXCLUSIVE_PRODUCT/PRIORITY_SUPPORT/BIRTHDAY_BONUS/FREE_RETURN/COUPON/GIFT/EXPERIENCE/SERVICE/CUSTOM';
COMMENT ON COLUMN scrm.scrm_membership_benefit.description IS '描述 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.tier_id IS '适用等级 ID (可空, null=所有等级)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.tier_name IS '适用等级名称 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.value IS '权益值 (如折扣率, 默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.value_type IS '值类型: PERCENTAGE/AMOUNT/COUNT/DAYS (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.applicable_products IS '适用商品 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.applicable_categories IS '适用类目 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.applicable_channels IS '适用渠道 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.usage_limit_per_member IS '每人限制 (0=无限, 默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.usage_limit_per_day IS '每日限制 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.usage_limit_per_month IS '每月限制 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.usage_limit_total IS '总限制 (0=无限, 默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.current_usage_count IS '当前使用次数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.member_usage_count IS '会员使用次数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.start_date IS '生效开始日期 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.end_date IS '生效结束日期 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.status IS '状态: ACTIVE/INACTIVE/EXPIRED (默认 ACTIVE)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.redemption_instructions IS '兑换说明 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.terms IS '使用条款 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.icon IS '图标 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.display_order IS '展示顺序 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.is_visible IS '是否可见 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.total_redeemed_value IS '总兑换价值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.total_saved_amount IS '总节省金额 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_benefit.created_by IS '创建人 (可空)';
-- 会员等级
CREATE TABLE scrm.scrm_membership_tier (
    id bigint NOT NULL,
    tier_name character varying(200) NOT NULL,
    tier_code character varying(50) NOT NULL,
    tier_level integer NOT NULL,
    description character varying(500),
    tier_color character varying(20),
    tier_icon character varying(200),
    upgrade_threshold double precision DEFAULT 0 NOT NULL,
    downgrade_threshold double precision DEFAULT 0,
    validity_period_months integer DEFAULT 12,
    upgrade_rule_type character varying(30) DEFAULT 'SPEND'::character varying NOT NULL,
    benefits_summary character varying(1000),
    point_multiplier double precision DEFAULT 1.0,
    discount_rate double precision DEFAULT 1.0,
    free_shipping boolean DEFAULT false NOT NULL,
    priority_support boolean DEFAULT false NOT NULL,
    exclusive_products boolean DEFAULT false NOT NULL,
    birthday_bonus double precision DEFAULT 0,
    signup_bonus_points integer DEFAULT 0,
    monthly_bonus_points integer DEFAULT 0,
    annual_bonus_points integer DEFAULT 0,
    tier_up_bonus_points integer DEFAULT 0,
    max_redeem_rate double precision DEFAULT 0.5,
    free_return_days integer DEFAULT 7,
    custom_benefits text,
    applicable_products character varying(500),
    applicable_channels character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    is_visible boolean DEFAULT true NOT NULL,
    member_count integer DEFAULT 0,
    total_spend double precision DEFAULT 0,
    avg_spend double precision DEFAULT 0,
    sort_order integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_membership_tier IS 'SCRM 会员等级表';
COMMENT ON COLUMN scrm.scrm_membership_tier.tier_name IS '等级名称';
COMMENT ON COLUMN scrm.scrm_membership_tier.tier_code IS '等级编码 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_membership_tier.tier_level IS '等级序号 (1-10, 数值越大等级越高)';
COMMENT ON COLUMN scrm.scrm_membership_tier.description IS '描述 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_tier.tier_color IS '等级颜色 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_tier.tier_icon IS '等级图标 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_tier.upgrade_threshold IS '升级阈值 (累计消费/积分, 默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.downgrade_threshold IS '降级阈值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.validity_period_months IS '等级有效期月 (默认 12)';
COMMENT ON COLUMN scrm.scrm_membership_tier.upgrade_rule_type IS '升级规则类型: SPEND/POINTS/ORDER_COUNT/MANUAL (默认 SPEND)';
COMMENT ON COLUMN scrm.scrm_membership_tier.benefits_summary IS '权益摘要 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_tier.point_multiplier IS '积分倍数 (默认 1.0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.discount_rate IS '折扣率 0-1 (默认 1.0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.free_shipping IS '免费配送 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_membership_tier.priority_support IS '优先支持 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_membership_tier.exclusive_products IS '专属商品 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_membership_tier.birthday_bonus IS '生日礼金 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.signup_bonus_points IS '注册赠送积分 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.monthly_bonus_points IS '每月赠送积分 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.annual_bonus_points IS '年度赠送积分 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.tier_up_bonus_points IS '升级赠送积分 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.max_redeem_rate IS '最大兑换比例 (默认 0.5)';
COMMENT ON COLUMN scrm.scrm_membership_tier.free_return_days IS '免费退货天数 (默认 7)';
COMMENT ON COLUMN scrm.scrm_membership_tier.custom_benefits IS 'JSON 自定义权益: [{benefitName,benefitType,value,description}] (可空)';
COMMENT ON COLUMN scrm.scrm_membership_tier.applicable_products IS '适用商品 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_tier.applicable_channels IS '适用渠道 (可空)';
COMMENT ON COLUMN scrm.scrm_membership_tier.enabled IS '是否启用 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_membership_tier.is_visible IS '是否可见 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_membership_tier.member_count IS '该等级会员数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.total_spend IS '该等级总消费 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.avg_spend IS '平均消费 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.sort_order IS '排序序号 (默认 0)';
COMMENT ON COLUMN scrm.scrm_membership_tier.created_by IS '创建人 (可空)';
-- 消息阅读日志
CREATE TABLE scrm.scrm_message_read_log (
    id bigint NOT NULL,
    message_tracking_id bigint NOT NULL,
    message_id character varying(200) NOT NULL,
    reader_id character varying(200) NOT NULL,
    reader_name character varying(200),
    reader_type character varying(20) DEFAULT 'CUSTOMER'::character varying NOT NULL,
    read_at timestamp without time zone NOT NULL,
    read_duration_seconds integer DEFAULT 0,
    read_source character varying(50),
    device_type character varying(30),
    os character varying(50),
    browser character varying(100),
    client_ip character varying(100),
    location character varying(200),
    is_repeated_read boolean DEFAULT false NOT NULL,
    sequence integer DEFAULT 1,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 消息撤回记录
CREATE TABLE scrm.scrm_message_recall (
    id bigint NOT NULL,
    message_tracking_id bigint NOT NULL,
    message_id character varying(200) NOT NULL,
    sender_id character varying(100) NOT NULL,
    sender_name character varying(100),
    recall_type character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    recall_reason character varying(500),
    recall_status character varying(20) DEFAULT 'SUCCESS'::character varying NOT NULL,
    total_recipients integer DEFAULT 0,
    successful_recalls integer DEFAULT 0,
    failed_recalls integer DEFAULT 0,
    recalled_at timestamp without time zone NOT NULL,
    completed_at timestamp without time zone,
    recall_window_minutes integer DEFAULT 2,
    is_within_window boolean DEFAULT true NOT NULL,
    failure_reason character varying(500),
    affected_readers character varying(1000),
    notes character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 消息模板表
CREATE TABLE scrm.scrm_message_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    category character varying(50) NOT NULL,
    content text NOT NULL,
    platform_type character varying(30),
    variables text,
    enabled boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_message_template IS 'SCRM 消息模板表 (快捷回复), 支持变量插值';
COMMENT ON COLUMN scrm.scrm_message_template.template_name IS '模板名称';
COMMENT ON COLUMN scrm.scrm_message_template.category IS '分类: greeting/promotion/service/follow_up/apology';
COMMENT ON COLUMN scrm.scrm_message_template.content IS '模板内容, 支持 {{nickname}}/{{platformType}}/{{customerName}}/{{ownerName}} 变量插值';
COMMENT ON COLUMN scrm.scrm_message_template.platform_type IS '适用平台类型, 空表示通用';
COMMENT ON COLUMN scrm.scrm_message_template.variables IS '变量列表 JSON 数组字符串, 由 content 自动提取';
COMMENT ON COLUMN scrm.scrm_message_template.enabled IS '是否启用';
COMMENT ON COLUMN scrm.scrm_message_template.sort_order IS '排序值, 数字越小越靠前';
COMMENT ON COLUMN scrm.scrm_message_template.created_by IS '创建人';
-- 消息模板中心
CREATE TABLE scrm.scrm_message_template_center (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    template_code character varying(50) NOT NULL,
    group_id bigint,
    group_name character varying(200),
    description character varying(500),
    template_type character varying(30) NOT NULL,
    channels character varying(500) NOT NULL,
    subject character varying(500),
    content text NOT NULL,
    plain_content character varying(2000),
    html_content text,
    wechat_link character varying(500),
    mini_program_path character varying(500),
    variables text,
    attachments character varying(1000),
    category character varying(100),
    tags character varying(500),
    applicable_scenarios character varying(500),
    language character varying(20) DEFAULT 'zh_CN'::character varying NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    version_number integer DEFAULT 1,
    current_version_id bigint,
    review_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    reviewed_by character varying(100),
    reviewed_at timestamp without time zone,
    review_comment character varying(500),
    usage_count integer DEFAULT 0,
    last_used_at timestamp without time zone,
    success_rate double precision DEFAULT 0,
    avg_response_rate double precision DEFAULT 0,
    is_standard boolean DEFAULT false NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 消息模板分组
CREATE TABLE scrm.scrm_message_template_group (
    id bigint NOT NULL,
    group_name character varying(200) NOT NULL,
    group_code character varying(50) NOT NULL,
    description character varying(500),
    group_type character varying(50) NOT NULL,
    parent_group_id bigint,
    sort_order integer DEFAULT 0,
    template_count integer DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    color character varying(20),
    icon character varying(100),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 消息模板版本
CREATE TABLE scrm.scrm_message_template_version (
    id bigint NOT NULL,
    template_id bigint NOT NULL,
    version_number integer NOT NULL,
    subject character varying(500),
    content text NOT NULL,
    plain_content character varying(2000),
    html_content text,
    variables text,
    change_log character varying(500),
    status character varying(20) DEFAULT 'ARCHIVED'::character varying NOT NULL,
    created_from_version integer,
    approved_by character varying(100),
    approved_at timestamp without time zone,
    created_by character varying(100),
    created_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 消息追踪表
CREATE TABLE scrm.scrm_message_tracking (
    id bigint NOT NULL,
    message_id character varying(200) NOT NULL,
    batch_id bigint,
    sender_id character varying(100) NOT NULL,
    sender_name character varying(100),
    recipient_id character varying(200) NOT NULL,
    recipient_name character varying(200),
    recipient_type character varying(20) DEFAULT 'CUSTOMER'::character varying NOT NULL,
    channel character varying(30) NOT NULL,
    message_content character varying(2000),
    content_type character varying(20) DEFAULT 'TEXT'::character varying NOT NULL,
    send_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    sent_at timestamp without time zone,
    delivered_at timestamp without time zone,
    first_read_at timestamp without time zone,
    last_read_at timestamp without time zone,
    read_count integer DEFAULT 0,
    is_read boolean DEFAULT false NOT NULL,
    is_recalled boolean DEFAULT false NOT NULL,
    recalled_at timestamp without time zone,
    recall_reason character varying(500),
    is_forwarded boolean DEFAULT false NOT NULL,
    forward_count integer DEFAULT 0,
    first_forwarded_at timestamp without time zone,
    is_replied boolean DEFAULT false NOT NULL,
    replied_at timestamp without time zone,
    reply_content character varying(500),
    engagement_score double precision DEFAULT 0,
    client_ip character varying(100),
    device_type character varying(30),
    read_duration_seconds integer DEFAULT 0,
    metadata text,
    error_message character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 监控指标
CREATE TABLE scrm.scrm_monitor_metric (
    id bigint NOT NULL,
    metric_name character varying(200) NOT NULL,
    metric_code character varying(50) NOT NULL,
    metric_group character varying(100) NOT NULL,
    metric_type character varying(30) NOT NULL,
    unit character varying(50),
    description character varying(500),
    current_value double precision DEFAULT 0,
    min_value double precision DEFAULT 0,
    max_value double precision DEFAULT 0,
    avg_value double precision DEFAULT 0,
    target_value double precision DEFAULT 0,
    warning_threshold double precision DEFAULT 0,
    critical_threshold double precision DEFAULT 0,
    threshold_direction character varying(20) DEFAULT 'ABOVE'::character varying NOT NULL,
    collection_interval_seconds integer DEFAULT 60,
    last_collected_at timestamp without time zone,
    last_value_at timestamp without time zone,
    collection_method character varying(30) DEFAULT 'POLLING'::character varying NOT NULL,
    data_source character varying(200),
    query_expression character varying(1000),
    tags character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    is_alert_active boolean DEFAULT false NOT NULL,
    last_alert_at timestamp without time zone,
    alert_count integer DEFAULT 0,
    history_data text,
    metadata text,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_monitor_metric IS 'SCRM 监控指标表';
COMMENT ON COLUMN scrm.scrm_monitor_metric.metric_name IS '指标名称';
COMMENT ON COLUMN scrm.scrm_monitor_metric.metric_code IS '指标编码 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.metric_group IS '指标分组: SYSTEM/BUSINESS/PERFORMANCE/AVAILABILITY/SECURITY/RESOURCE/API/DATABASE/CACHE/QUEUE';
COMMENT ON COLUMN scrm.scrm_monitor_metric.metric_type IS '指标类型: GAUGE/COUNTER/HISTOGRAM/TIMER/SUMMARY';
COMMENT ON COLUMN scrm.scrm_monitor_metric.unit IS '单位 (可空)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.description IS '描述 (可空)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.current_value IS '当前值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.min_value IS '最小值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.max_value IS '最大值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.avg_value IS '平均值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.target_value IS '目标值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.warning_threshold IS '预警阈值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.critical_threshold IS '严重阈值 (默认 0)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.threshold_direction IS '阈值方向: ABOVE/BELOW/RANGE (默认 ABOVE)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.collection_interval_seconds IS '采集间隔秒 (默认 60)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.last_collected_at IS '最近采集时间 (可空)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.last_value_at IS '最近值时间 (可空)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.collection_method IS '采集方式: POLLING/PUSH/CALCULATED/EXTERNAL (默认 POLLING)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.data_source IS '数据源 (可空)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.query_expression IS '查询表达式 (可空)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.tags IS '标签 (可空)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.enabled IS '是否启用 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.is_alert_active IS '是否告警激活 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.last_alert_at IS '最近告警时间 (可空)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.alert_count IS '告警次数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.history_data IS '历史数据 JSON: [{timestamp,value}] (可空)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.metadata IS '附加数据 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_monitor_metric.created_by IS '创建人 (可空)';
-- 通知表
CREATE TABLE scrm.scrm_notification (
    id bigint NOT NULL,
    template_id bigint,
    template_code character varying(100),
    channel character varying(20) NOT NULL,
    category character varying(50) NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    recipient_type character varying(20) DEFAULT 'USER'::character varying NOT NULL,
    recipient_id character varying(200) NOT NULL,
    recipient_name character varying(200),
    recipient_contact character varying(200),
    sender_id character varying(100),
    sender_name character varying(100),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    priority integer DEFAULT 0,
    scheduled_at timestamp without time zone,
    sent_at timestamp without time zone,
    delivered_at timestamp without time zone,
    read_at timestamp without time zone,
    error_message character varying(500),
    retry_count integer DEFAULT 0,
    max_retries integer DEFAULT 3,
    metadata text,
    related_type character varying(50),
    related_id character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 通知批次
CREATE TABLE scrm.scrm_notification_batch (
    id bigint NOT NULL,
    batch_name character varying(200) NOT NULL,
    template_code character varying(100),
    channel character varying(20) NOT NULL,
    category character varying(50) NOT NULL,
    total_count integer DEFAULT 0,
    sent_count integer DEFAULT 0,
    success_count integer DEFAULT 0,
    failed_count integer DEFAULT 0,
    read_count integer DEFAULT 0,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    triggered_by character varying(100) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 通知偏好
CREATE TABLE scrm.scrm_notification_preference (
    id bigint NOT NULL,
    user_id character varying(100) NOT NULL,
    channel character varying(20) NOT NULL,
    category character varying(50) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    quiet_hours_start character varying(10),
    quiet_hours_end character varying(10),
    min_priority integer DEFAULT 0,
    updated_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 通知模板
CREATE TABLE scrm.scrm_notification_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    template_code character varying(100) NOT NULL,
    category character varying(50) NOT NULL,
    channel character varying(20) NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    variables character varying(500),
    sender_name character varying(100),
    sender_email character varying(200),
    sms_sign_name character varying(100),
    is_html boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    usage_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- NPS 基准
CREATE TABLE scrm.scrm_nps_benchmark (
    id bigint NOT NULL,
    period_type character varying(20) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    total_responses integer DEFAULT 0,
    promoters integer DEFAULT 0,
    passives integer DEFAULT 0,
    detractors integer DEFAULT 0,
    nps_score integer DEFAULT 0,
    promoter_percent double precision DEFAULT 0,
    passive_percent double precision DEFAULT 0,
    detractor_percent double precision DEFAULT 0,
    avg_csat_score double precision DEFAULT 0,
    avg_ces_score double precision DEFAULT 0,
    response_rate double precision DEFAULT 0,
    benchmark_industry character varying(100),
    benchmark_score integer,
    generated_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 商机表
CREATE TABLE scrm.scrm_opportunity (
    id bigint NOT NULL,
    opportunity_name character varying(200) NOT NULL,
    customer_id bigint NOT NULL,
    funnel_id bigint NOT NULL,
    current_stage_id bigint NOT NULL,
    amount double precision,
    expected_close_date timestamp without time zone,
    probability integer,
    owner_user_id character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    source character varying(100),
    competitor character varying(200),
    note text,
    won_at timestamp without time zone,
    lost_at timestamp without time zone,
    lost_reason character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 商机阶段历史
CREATE TABLE scrm.scrm_opportunity_stage_history (
    id bigint NOT NULL,
    opportunity_id bigint NOT NULL,
    from_stage_id bigint,
    to_stage_id bigint NOT NULL,
    changed_by character varying(100) NOT NULL,
    changed_at timestamp without time zone NOT NULL,
    note character varying(500),
    duration_days integer
);
-- 订单表
CREATE TABLE scrm.scrm_order (
    id bigint NOT NULL,
    order_no character varying(50) NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    order_type character varying(20) DEFAULT 'SALE'::character varying NOT NULL,
    order_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    payment_status character varying(20) DEFAULT 'UNPAID'::character varying NOT NULL,
    payment_method character varying(30),
    total_amount double precision DEFAULT 0 NOT NULL,
    discount_amount double precision DEFAULT 0,
    shipping_amount double precision DEFAULT 0,
    tax_amount double precision DEFAULT 0,
    paid_amount double precision DEFAULT 0,
    currency character varying(10) DEFAULT 'CNY'::character varying NOT NULL,
    coupon_id bigint,
    coupon_code character varying(100),
    salesperson_id character varying(100),
    salesperson_name character varying(100),
    channel character varying(50),
    shipping_address character varying(500),
    shipping_name character varying(100),
    shipping_phone character varying(50),
    tracking_no character varying(200),
    tracking_company character varying(100),
    remark character varying(500),
    paid_at timestamp without time zone,
    shipped_at timestamp without time zone,
    delivered_at timestamp without time zone,
    completed_at timestamp without time zone,
    cancelled_at timestamp without time zone,
    items_json text,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 订单明细
CREATE TABLE scrm.scrm_order_item (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    product_id bigint,
    product_code character varying(100),
    product_name character varying(200) NOT NULL,
    product_image character varying(500),
    spec character varying(500),
    unit_price double precision DEFAULT 0 NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    discount_amount double precision DEFAULT 0,
    subtotal double precision DEFAULT 0 NOT NULL,
    tax_rate double precision DEFAULT 0,
    tax_amount double precision DEFAULT 0,
    remark character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 人设表
CREATE TABLE scrm.scrm_persona (
    id bigint NOT NULL,
    persona_id character varying(100) NOT NULL,
    account_id bigint,
    nickname character varying(200),
    avatar_url character varying(500),
    gender character varying(20),
    age_range character varying(30),
    region character varying(100),
    signature character varying(500),
    style_tags text,
    script_template_ids text,
    tags text,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 平台配置表
CREATE TABLE scrm.scrm_platform_config (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    platform_type character varying(30) NOT NULL,
    corp_id character varying(200),
    agent_id integer,
    secret character varying(500),
    aes_key character varying(100),
    token character varying(200),
    base_url character varying(500),
    callback_url character varying(500),
    mock_mode boolean DEFAULT true NOT NULL,
    real_api_enabled boolean DEFAULT false NOT NULL,
    timeout integer DEFAULT 30000 NOT NULL,
    retry_count integer DEFAULT 3 NOT NULL,
    connection_status character varying(20) DEFAULT 'UNKNOWN'::character varying,
    last_tested_at timestamp without time zone
);
-- 积分账户
CREATE TABLE scrm.scrm_points_account (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    current_points integer DEFAULT 0 NOT NULL,
    frozen_points integer DEFAULT 0,
    total_earned integer DEFAULT 0,
    total_redeemed integer DEFAULT 0,
    total_expired integer DEFAULT 0,
    level character varying(50),
    last_earn_at timestamp without time zone,
    last_redeem_at timestamp without time zone,
    updated_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 积分兑换
CREATE TABLE scrm.scrm_points_exchange (
    id bigint NOT NULL,
    item_name character varying(200) NOT NULL,
    item_image character varying(500),
    item_description character varying(500),
    item_category character varying(50),
    points_required integer NOT NULL,
    stock_quantity integer NOT NULL,
    exchanged_quantity integer DEFAULT 0,
    per_user_limit integer DEFAULT 1,
    exchange_type character varying(20) NOT NULL,
    exchange_value double precision,
    validity_days integer,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 积分兑换记录
CREATE TABLE scrm.scrm_points_exchange_record (
    id bigint NOT NULL,
    exchange_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    points_cost integer NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    exchange_code character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    shipping_address character varying(500),
    shipping_no character varying(200),
    exchanged_at timestamp without time zone NOT NULL,
    completed_at timestamp without time zone,
    cancelled_at timestamp without time zone,
    notes character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 积分规则
CREATE TABLE scrm.scrm_points_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    rule_type character varying(20) NOT NULL,
    trigger_event character varying(50) NOT NULL,
    points_value integer NOT NULL,
    points_type character varying(20) DEFAULT 'FIXED'::character varying NOT NULL,
    basis_field character varying(50),
    daily_limit integer,
    monthly_limit integer,
    min_points integer DEFAULT 0,
    max_points integer,
    description character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    trigger_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 积分流水
CREATE TABLE scrm.scrm_points_transaction (
    id bigint NOT NULL,
    account_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    transaction_type character varying(20) NOT NULL,
    points integer NOT NULL,
    balance_after integer NOT NULL,
    rule_id bigint,
    trigger_event character varying(50),
    source_type character varying(30) NOT NULL,
    source_id character varying(100),
    description character varying(500),
    expires_at timestamp without time zone,
    expired boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 产品表
CREATE TABLE scrm.scrm_product (
    id bigint NOT NULL,
    product_code character varying(100) NOT NULL,
    product_name character varying(200) NOT NULL,
    category character varying(100),
    brand character varying(100),
    spec character varying(500),
    description text,
    price double precision DEFAULT 0 NOT NULL,
    original_price double precision,
    cost double precision,
    currency character varying(10) DEFAULT 'CNY'::character varying NOT NULL,
    stock integer DEFAULT 0,
    unit character varying(50),
    image_url character varying(500),
    images character varying(1000),
    tags character varying(500),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    sku character varying(100),
    barcode character varying(100),
    weight double precision,
    sales_count integer DEFAULT 0,
    view_count integer DEFAULT 0,
    rating_score double precision DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 画像对比
CREATE TABLE scrm.scrm_profile_comparison (
    id bigint NOT NULL,
    customer_id1 bigint NOT NULL,
    customer_name1 character varying(200),
    customer_id2 bigint NOT NULL,
    customer_name2 character varying(200),
    comparison_type character varying(30) NOT NULL,
    dimensions text NOT NULL,
    overall_similarity double precision DEFAULT 0,
    common_traits character varying(1000),
    key_differences character varying(1000),
    recommendation character varying(500),
    compared_at timestamp without time zone NOT NULL,
    compared_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_profile_comparison IS 'SCRM 画像对比表';
COMMENT ON COLUMN scrm.scrm_profile_comparison.customer_id1 IS '客户 ID 1';
COMMENT ON COLUMN scrm.scrm_profile_comparison.customer_name1 IS '客户名称 1 (对比时快照, 可空)';
COMMENT ON COLUMN scrm.scrm_profile_comparison.customer_id2 IS '客户 ID 2';
COMMENT ON COLUMN scrm.scrm_profile_comparison.customer_name2 IS '客户名称 2 (对比时快照, 可空)';
COMMENT ON COLUMN scrm.scrm_profile_comparison.comparison_type IS '对比类型: INDIVIDUAL/SEGMENT/TEMPLATE';
COMMENT ON COLUMN scrm.scrm_profile_comparison.dimensions IS '对比维度结果 JSON: [{dimension,similarity,differences}]';
COMMENT ON COLUMN scrm.scrm_profile_comparison.overall_similarity IS '总体相似度 (0-1, 默认 0)';
COMMENT ON COLUMN scrm.scrm_profile_comparison.common_traits IS '共同特征 (可空)';
COMMENT ON COLUMN scrm.scrm_profile_comparison.key_differences IS '关键差异 (可空)';
COMMENT ON COLUMN scrm.scrm_profile_comparison.recommendation IS '对比建议 (可空)';
COMMENT ON COLUMN scrm.scrm_profile_comparison.compared_at IS '对比时间';
COMMENT ON COLUMN scrm.scrm_profile_comparison.compared_by IS '对比人 (可空)';
-- 画像模板
CREATE TABLE scrm.scrm_profile_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    template_code character varying(50) NOT NULL,
    description character varying(500),
    applicable_segment character varying(500),
    dimensions text NOT NULL,
    scoring_model text,
    tag_rules text,
    summary_template character varying(1000),
    min_confidence double precision DEFAULT 0.5,
    update_frequency character varying(20) DEFAULT 'WEEKLY'::character varying NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    usage_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_profile_template IS 'SCRM 画像模板表';
COMMENT ON COLUMN scrm.scrm_profile_template.template_name IS '模板名称';
COMMENT ON COLUMN scrm.scrm_profile_template.template_code IS '模板编码 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_profile_template.description IS '模板描述 (可空)';
COMMENT ON COLUMN scrm.scrm_profile_template.applicable_segment IS '适用客群 (可空)';
COMMENT ON COLUMN scrm.scrm_profile_template.dimensions IS '维度配置 JSON: [{dimension,weight,fields,scoringRules}]';
COMMENT ON COLUMN scrm.scrm_profile_template.scoring_model IS '评分模型 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_profile_template.tag_rules IS '标签规则 JSON: [{condition,tags}] (可空)';
COMMENT ON COLUMN scrm.scrm_profile_template.summary_template IS '摘要模板 (可空)';
COMMENT ON COLUMN scrm.scrm_profile_template.min_confidence IS '最低置信度 (默认 0.5)';
COMMENT ON COLUMN scrm.scrm_profile_template.update_frequency IS '更新频率: REALTIME/DAILY/WEEKLY/MONTHLY (默认 WEEKLY)';
COMMENT ON COLUMN scrm.scrm_profile_template.is_default IS '是否默认模板 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_profile_template.enabled IS '是否启用 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_profile_template.usage_count IS '使用次数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_profile_template.created_by IS '创建人 (可空)';
-- 公海客户表
CREATE TABLE scrm.scrm_public_sea_customer (
    id bigint NOT NULL,
    platform_type character varying(30) NOT NULL,
    platform_customer_uid character varying(200) NOT NULL,
    nickname character varying(200),
    avatar_url character varying(500),
    source_channel character varying(100),
    source_channel_code_id bigint,
    lifecycle character varying(20) DEFAULT 'NEW'::character varying NOT NULL,
    tags character varying(500),
    remark text,
    assigned_to character varying(100),
    assigned_at timestamp without time zone,
    assignment_expire_at timestamp without time zone,
    recall_count integer DEFAULT 0 NOT NULL,
    last_assigned_at timestamp without time zone,
    status character varying(20) DEFAULT 'AVAILABLE'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_public_sea_customer IS '客户公海池 (线索集中管理 / 销售领取分配 / 超时回收 / 转移)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.id IS '主键 ID (Snowflake)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.platform_type IS '平台类型 (wework/douyin/kuaishou/xiaohongshu/bilibili/wechat_personal)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.platform_customer_uid IS '平台客户唯一标识';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.nickname IS '客户昵称';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.avatar_url IS '客户头像 URL';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.source_channel IS '来源渠道 (直播/广告/搜索/裂变等)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.source_channel_code_id IS '来源渠道码 ID (可空, 关联渠道码记录)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.lifecycle IS '生命周期: NEW/PROSPECT/ACTIVE/DORMANT/CHURNED';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.tags IS '客户标签 (逗号分隔)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.remark IS '备注 (TEXT)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.assigned_to IS '当前归属人 userId (可空, AVAILABLE 状态时为空)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.assigned_at IS '最近一次分配时间';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.assignment_expire_at IS '分配过期时间 (超时自动回收)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.recall_count IS '被回收次数 (累计, 用于评估线索质量)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.last_assigned_at IS '最近一次分配时间 (用于分配历史)';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.status IS '公海状态: AVAILABLE/ASSIGNED/LOCKED/RECALLED';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.create_time IS '创建时间';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.update_time IS '更新时间';
COMMENT ON COLUMN scrm.scrm_public_sea_customer.version IS '乐观锁版本号';
-- 质检结果
CREATE TABLE scrm.scrm_quality_inspection_result (
    id bigint NOT NULL,
    task_id bigint,
    conversation_id bigint NOT NULL,
    customer_id bigint,
    customer_name character varying(200),
    assignee_id character varying(100),
    assignee_name character varying(100),
    total_score double precision NOT NULL,
    passed boolean NOT NULL,
    rule_results text NOT NULL,
    issues_found text,
    suggestions character varying(2000),
    inspected_at timestamp without time zone NOT NULL,
    inspector_type character varying(20) DEFAULT 'AI'::character varying NOT NULL,
    inspector_id character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_quality_inspection_result IS 'SCRM 质检结果表, 单会话质检明细';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.task_id IS '关联质检任务 (可空, 单会话质检时为空)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.conversation_id IS '关联会话 ID';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.customer_id IS '客户 ID (可空)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.customer_name IS '客户名称 (可空)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.assignee_id IS '被质检人 ID (账号 ID 字符串)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.assignee_name IS '被质检人名称';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.total_score IS '质检总分 (0-100)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.passed IS '是否通过';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.rule_results IS '各规则质检结果 JSON: [{ruleId,ruleName,category,score,passed,detail}]';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.issues_found IS '发现的问题列表 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.suggestions IS '改进建议 (可空)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.inspected_at IS '质检时间';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.inspector_type IS '质检人类型: AI/MANUAL (默认 AI)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_result.inspector_id IS '质检人 ID (可空)';
-- 质检规则
CREATE TABLE scrm.scrm_quality_inspection_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    category character varying(30) NOT NULL,
    description character varying(500),
    rule_type character varying(20) NOT NULL,
    rule_config text NOT NULL,
    pass_condition character varying(20) DEFAULT 'GTE:80'::character varying NOT NULL,
    score_weight double precision DEFAULT 1.0 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    match_count integer DEFAULT 0,
    pass_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_quality_inspection_rule IS 'SCRM 质检规则表, 定义会话质检评估规则';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.rule_name IS '规则名称';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.category IS '质检类别: SCRIPT_COMPLIANCE/SERVICE_ATTITUDE/SENSITIVE_WORD/RESPONSE_TIME/PROFESSIONALISM/COMPLIANCE';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.description IS '规则描述 (可空)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.rule_type IS '规则类型: KEYWORD_MATCH/REGEX/DURATION/RESPONSE_TIME/AI_EVALUATE';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.rule_config IS '规则配置 JSON: {keywords:[], regex:"", maxResponseSeconds:300, promptTemplate:"", scoreWeight:1.0}';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.pass_condition IS '通过条件: GTE:80/LTE:30/CONTAINS/NOT_CONTAINS';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.score_weight IS '评分权重 (默认 1.0)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.enabled IS '是否启用 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.match_count IS '累计匹配次数';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.pass_count IS '累计通过次数';
COMMENT ON COLUMN scrm.scrm_quality_inspection_rule.created_by IS '创建人';
-- 质检任务
CREATE TABLE scrm.scrm_quality_inspection_task (
    id bigint NOT NULL,
    task_name character varying(200) NOT NULL,
    inspection_scope character varying(30) NOT NULL,
    scope_value character varying(500),
    start_time timestamp without time zone NOT NULL,
    end_time timestamp without time zone NOT NULL,
    rule_ids text NOT NULL,
    total_conversations integer DEFAULT 0,
    inspected_count integer DEFAULT 0,
    passed_count integer DEFAULT 0,
    failed_count integer DEFAULT 0,
    average_score double precision DEFAULT 0,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_quality_inspection_task IS 'SCRM 质检任务表, 批量会话质检任务';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.task_name IS '任务名称';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.inspection_scope IS '质检范围: ALL/ASSIGNEE/ACCOUNT/CUSTOMER';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.scope_value IS '范围值 (assigneeId/accountId/customerId, ALL 时为空)';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.start_time IS '质检会话起始时间';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.end_time IS '质检会话截止时间';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.rule_ids IS '质检规则 ID 列表 JSON 数组';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.total_conversations IS '待质检会话数';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.inspected_count IS '已质检数';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.passed_count IS '通过数';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.failed_count IS '不通过数';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.average_score IS '平均分';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.status IS '状态: PENDING/RUNNING/COMPLETED/FAILED';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.started_at IS '开始执行时间';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.completed_at IS '完成执行时间';
COMMENT ON COLUMN scrm.scrm_quality_inspection_task.created_by IS '创建人';
-- 快捷回复表
CREATE TABLE scrm.scrm_quick_reply (
    id bigint NOT NULL,
    category_id bigint,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    reply_type character varying(20) DEFAULT 'TEXT'::character varying NOT NULL,
    media_urls text,
    shortcut character varying(50),
    platform_type character varying(30),
    scenario character varying(50),
    tags character varying(500),
    sort_order integer DEFAULT 0,
    use_count integer DEFAULT 0,
    is_personal boolean DEFAULT false NOT NULL,
    owner_user_id character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_quick_reply IS 'SCRM 快捷回复条目表';
COMMENT ON COLUMN scrm.scrm_quick_reply.category_id IS '分类 ID, 可空表示未分类';
COMMENT ON COLUMN scrm.scrm_quick_reply.title IS '快捷回复标题/摘要';
COMMENT ON COLUMN scrm.scrm_quick_reply.content IS '回复内容';
COMMENT ON COLUMN scrm.scrm_quick_reply.reply_type IS '回复类型: TEXT/IMAGE/LINK/MIXED';
COMMENT ON COLUMN scrm.scrm_quick_reply.media_urls IS '媒体 URL JSON 数组字符串, 如 ["url1","url2"]';
COMMENT ON COLUMN scrm.scrm_quick_reply.shortcut IS '快捷键, 如 /你好';
COMMENT ON COLUMN scrm.scrm_quick_reply.platform_type IS '适用平台, 可空表示全部';
COMMENT ON COLUMN scrm.scrm_quick_reply.scenario IS '使用场景';
COMMENT ON COLUMN scrm.scrm_quick_reply.tags IS '标签 (逗号分隔)';
COMMENT ON COLUMN scrm.scrm_quick_reply.sort_order IS '排序值, 数字越小越靠前';
COMMENT ON COLUMN scrm.scrm_quick_reply.use_count IS '使用次数';
COMMENT ON COLUMN scrm.scrm_quick_reply.is_personal IS '是否个人专属 (TRUE 时按 owner_user_id 隔离)';
COMMENT ON COLUMN scrm.scrm_quick_reply.owner_user_id IS '个人专属时归属人用户 ID';
COMMENT ON COLUMN scrm.scrm_quick_reply.status IS '状态: ACTIVE/INACTIVE';
COMMENT ON COLUMN scrm.scrm_quick_reply.created_by IS '创建人';
COMMENT ON COLUMN scrm.scrm_quick_reply.create_time IS '创建时间';
COMMENT ON COLUMN scrm.scrm_quick_reply.update_time IS '更新时间';
COMMENT ON COLUMN scrm.scrm_quick_reply.version IS '乐观锁版本号';
-- 快捷回复分类
CREATE TABLE scrm.scrm_quick_reply_category (
    id bigint NOT NULL,
    category_name character varying(100) NOT NULL,
    icon character varying(50),
    sort_order integer DEFAULT 0,
    platform_type character varying(30),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_quick_reply_category IS 'SCRM 快捷回复分类表';
COMMENT ON COLUMN scrm.scrm_quick_reply_category.category_name IS '分类名称';
COMMENT ON COLUMN scrm.scrm_quick_reply_category.icon IS '图标 (可空, 如 emoji 或图标类名)';
COMMENT ON COLUMN scrm.scrm_quick_reply_category.sort_order IS '排序值, 数字越小越靠前';
COMMENT ON COLUMN scrm.scrm_quick_reply_category.platform_type IS '适用平台 (可空, 空表示全部)';
COMMENT ON COLUMN scrm.scrm_quick_reply_category.status IS '状态: ACTIVE/INACTIVE';
COMMENT ON COLUMN scrm.scrm_quick_reply_category.create_time IS '创建时间';
COMMENT ON COLUMN scrm.scrm_quick_reply_category.update_time IS '更新时间';
COMMENT ON COLUMN scrm.scrm_quick_reply_category.version IS '乐观锁版本号';
-- 推荐表
CREATE TABLE scrm.scrm_referral (
    id bigint NOT NULL,
    program_id bigint NOT NULL,
    referral_code character varying(100) NOT NULL,
    referrer_customer_id bigint NOT NULL,
    referrer_name character varying(200),
    referee_customer_id bigint,
    referee_name character varying(200),
    referee_contact character varying(200),
    referral_channel character varying(30),
    referral_link character varying(500),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    signed_up_at timestamp without time zone,
    qualified_at timestamp without time zone,
    rewarded_at timestamp without time zone,
    referrer_reward_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    referee_reward_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    referrer_reward_details character varying(500),
    referee_reward_details character varying(500),
    purchase_amount double precision DEFAULT 0,
    notes character varying(500),
    expired_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 推荐计划
CREATE TABLE scrm.scrm_referral_program (
    id bigint NOT NULL,
    program_name character varying(200) NOT NULL,
    program_code character varying(50) NOT NULL,
    description character varying(500),
    program_type character varying(30) NOT NULL,
    referrer_reward_type character varying(30) NOT NULL,
    referrer_reward_value double precision DEFAULT 0 NOT NULL,
    referrer_reward_config text,
    referee_reward_type character varying(30) NOT NULL,
    referee_reward_value double precision DEFAULT 0 NOT NULL,
    referee_reward_config text,
    reward_trigger character varying(30) DEFAULT 'SIGNUP'::character varying NOT NULL,
    reward_trigger_value double precision,
    max_referrals_per_referrer integer DEFAULT 0,
    max_referrals_total integer DEFAULT 0,
    double_sided_reward boolean DEFAULT true NOT NULL,
    start_date date NOT NULL,
    end_date date,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    terms_conditions text,
    total_referrals integer DEFAULT 0,
    successful_referrals integer DEFAULT 0,
    total_reward_value double precision DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 推荐奖励
CREATE TABLE scrm.scrm_referral_reward (
    id bigint NOT NULL,
    referral_id bigint NOT NULL,
    program_id bigint NOT NULL,
    recipient_type character varying(20) NOT NULL,
    recipient_customer_id bigint NOT NULL,
    recipient_name character varying(200),
    reward_type character varying(30) NOT NULL,
    reward_value double precision DEFAULT 0 NOT NULL,
    reward_config text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    issued_at timestamp without time zone,
    redeemed_at timestamp without time zone,
    expired_at timestamp without time zone,
    coupon_code character varying(100),
    points_account bigint,
    transaction_id character varying(100),
    notes character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 报表结果
CREATE TABLE scrm.scrm_report_result (
    id bigint NOT NULL,
    template_id bigint NOT NULL,
    run_by character varying(100) NOT NULL,
    run_at timestamp without time zone NOT NULL,
    time_range_start timestamp without time zone,
    time_range_end timestamp without time zone,
    result_data text NOT NULL,
    row_count integer,
    status character varying(20) NOT NULL,
    error_message character varying(500)
);
-- 报表模板
CREATE TABLE scrm.scrm_report_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    report_type character varying(30) NOT NULL,
    data_source character varying(50) NOT NULL,
    dimensions text NOT NULL,
    metrics text NOT NULL,
    filters text,
    time_range_field character varying(100),
    chart_type character varying(30),
    description character varying(500),
    is_public boolean DEFAULT false NOT NULL,
    created_by character varying(100),
    last_run_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- RFM 分析表
CREATE TABLE scrm.scrm_rfm_analysis (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    config_id bigint NOT NULL,
    recency_days integer NOT NULL,
    frequency integer NOT NULL,
    monetary double precision NOT NULL,
    r_score integer NOT NULL,
    f_score integer NOT NULL,
    m_score integer NOT NULL,
    rfm_segment character varying(20) NOT NULL,
    segment_name character varying(50) NOT NULL,
    segment_category character varying(30) NOT NULL,
    value_score double precision NOT NULL,
    calculated_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- RFM 配置
CREATE TABLE scrm.scrm_rfm_config (
    id bigint NOT NULL,
    config_name character varying(200) NOT NULL,
    description character varying(500),
    r_weight double precision DEFAULT 0.3 NOT NULL,
    f_weight double precision DEFAULT 0.3 NOT NULL,
    m_weight double precision DEFAULT 0.4 NOT NULL,
    r_threshold integer NOT NULL,
    f_threshold integer NOT NULL,
    m_threshold double precision NOT NULL,
    recency_source character varying(50) DEFAULT 'LAST_INTERACTION'::character varying NOT NULL,
    monetary_source character varying(50) DEFAULT 'TOTAL_SPENT'::character varying NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    last_calculated_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- RFM 分群策略
CREATE TABLE scrm.scrm_rfm_segment_strategy (
    id bigint NOT NULL,
    strategy_name character varying(200) NOT NULL,
    segment_category character varying(30) NOT NULL,
    segment_code character varying(20),
    strategy_type character varying(30) NOT NULL,
    description character varying(500),
    actions text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    priority integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 风控事件
CREATE TABLE scrm.scrm_risk_event (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    event_no character varying(100) NOT NULL,
    rule_id bigint,
    rule_name character varying(200),
    rule_code character varying(50),
    customer_id bigint,
    customer_name character varying(200),
    target_type character varying(30) NOT NULL,
    target_value character varying(500) NOT NULL,
    risk_category character varying(50) NOT NULL,
    risk_level character varying(20) NOT NULL,
    risk_score double precision DEFAULT 0 NOT NULL,
    trigger_reason character varying(1000) NOT NULL,
    trigger_data text,
    trigger_time timestamp without time zone NOT NULL,
    detected_by character varying(100),
    detection_method character varying(50),
    action character varying(30) DEFAULT 'ALERT'::character varying NOT NULL,
    action_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    action_executed_at timestamp without time zone,
    action_result character varying(500),
    blocked_action character varying(500),
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    assigned_to character varying(100),
    assigned_at timestamp without time zone,
    investigated_by character varying(100),
    investigated_at timestamp without time zone,
    investigation_notes character varying(2000),
    confirmed_risk boolean,
    resolution character varying(1000),
    resolved_by character varying(100),
    resolved_at timestamp without time zone,
    resolution_time_hours integer DEFAULT 0,
    is_false_positive boolean DEFAULT false NOT NULL,
    escalated_to character varying(100),
    escalated_at timestamp without time zone,
    related_event_ids character varying(500),
    affected_entities character varying(500),
    impact_assessment character varying(1000),
    notifications_sent integer DEFAULT 0,
    metadata text,
    tags character varying(500),
    created_by character varying(100)
);
-- 风控规则表
CREATE TABLE scrm.scrm_risk_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    rule_code character varying(100) NOT NULL,
    condition_expression text NOT NULL,
    risk_level character varying(20) NOT NULL,
    signal_type character varying(50) NOT NULL,
    description text,
    enabled boolean DEFAULT true NOT NULL,
    priority integer DEFAULT 100,
    action character varying(50) DEFAULT 'ALERT'::character varying,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 风控信号表
CREATE TABLE scrm.scrm_risk_signal (
    id bigint NOT NULL,
    rule_id character varying(100),
    persona_id character varying(100),
    account_id bigint,
    signal_type character varying(50),
    risk_level character varying(20),
    detail text,
    triggered_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying,
    resolved_at timestamp without time zone,
    resolved_by character varying(100),
    resolve_remark text
);
COMMENT ON COLUMN scrm.scrm_risk_signal.status IS '处理状态: PENDING(待处理) / RESOLVED(已处理) / IGNORED(已忽略)';
COMMENT ON COLUMN scrm.scrm_risk_signal.resolved_at IS '处理时间';
COMMENT ON COLUMN scrm.scrm_risk_signal.resolved_by IS '处理人用户名';
COMMENT ON COLUMN scrm.scrm_risk_signal.resolve_remark IS '处理备注';
-- 销售达成
CREATE TABLE scrm.scrm_sales_achievement (
    id bigint NOT NULL,
    target_id bigint NOT NULL,
    target_type character varying(20) NOT NULL,
    target_id_ref character varying(100) NOT NULL,
    target_name_ref character varying(200),
    period_type character varying(10) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    metric_type character varying(30) NOT NULL,
    achieved_value double precision NOT NULL,
    achievement_date date NOT NULL,
    source_type character varying(30) NOT NULL,
    source_id character varying(100),
    note character varying(500),
    recorded_at timestamp without time zone NOT NULL,
    recorded_by character varying(100) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 销售排行榜
CREATE TABLE scrm.scrm_sales_ranking (
    id bigint NOT NULL,
    period_type character varying(10) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    target_type character varying(20) NOT NULL,
    target_id character varying(100) NOT NULL,
    target_name_ref character varying(200),
    metric_type character varying(30) NOT NULL,
    achieved_value double precision NOT NULL,
    target_value double precision NOT NULL,
    achievement_rate double precision NOT NULL,
    rank integer NOT NULL,
    ranking_date date NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 销售话术
CREATE TABLE scrm.scrm_sales_speech (
    id bigint NOT NULL,
    scenario_id bigint NOT NULL,
    scenario_name character varying(200),
    speech_title character varying(200) NOT NULL,
    speech_content text NOT NULL,
    speech_type character varying(30) DEFAULT 'TEXT'::character varying NOT NULL,
    speech_style character varying(30),
    target_audience character varying(500),
    applicable_products character varying(500),
    applicable_scenes character varying(500),
    keywords character varying(500),
    variables character varying(500),
    media_attachments character varying(1000),
    difficulty_level character varying(20) DEFAULT 'INTERMEDIATE'::character varying NOT NULL,
    estimated_duration integer DEFAULT 0,
    rating double precision DEFAULT 0,
    usage_count integer DEFAULT 0,
    success_count integer DEFAULT 0,
    feedback_count integer DEFAULT 0,
    positive_feedback integer DEFAULT 0,
    negative_feedback integer DEFAULT 0,
    is_recommended boolean DEFAULT false NOT NULL,
    is_verified boolean DEFAULT false NOT NULL,
    version_no integer DEFAULT 1,
    author_id character varying(100),
    author_name character varying(100),
    tags character varying(500),
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 销售目标
CREATE TABLE scrm.scrm_sales_target (
    id bigint NOT NULL,
    target_name character varying(200) NOT NULL,
    target_type character varying(20) NOT NULL,
    target_id character varying(100) NOT NULL,
    target_name_ref character varying(200),
    period_type character varying(10) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    metric_type character varying(30) NOT NULL,
    target_value double precision NOT NULL,
    actual_value double precision DEFAULT 0 NOT NULL,
    achievement_rate double precision DEFAULT 0 NOT NULL,
    last_updated timestamp without time zone,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    notes character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 定时任务
CREATE TABLE scrm.scrm_scheduled_task (
    id bigint NOT NULL,
    task_name character varying(200) NOT NULL,
    task_code character varying(50) NOT NULL,
    description character varying(500),
    task_category character varying(50) NOT NULL,
    task_type character varying(30) NOT NULL,
    cron_expression character varying(100),
    fixed_rate_ms integer,
    fixed_delay_ms integer,
    execute_at timestamp without time zone,
    handler_class character varying(500) NOT NULL,
    handler_method character varying(100) DEFAULT 'execute'::character varying NOT NULL,
    parameters text,
    timeout_seconds integer DEFAULT 300,
    max_retries integer DEFAULT 3,
    retry_delay_seconds integer DEFAULT 60,
    retry_backoff_multiplier double precision DEFAULT 2.0,
    priority integer DEFAULT 0,
    dependencies character varying(500),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    last_executed_at timestamp without time zone,
    last_execution_status character varying(20),
    last_execution_duration_ms integer,
    last_error_message character varying(1000),
    next_scheduled_at timestamp without time zone,
    total_executions integer DEFAULT 0,
    success_count integer DEFAULT 0,
    failure_count integer DEFAULT 0,
    timeout_count integer DEFAULT 0,
    avg_execution_ms integer DEFAULT 0,
    last_success_at timestamp without time zone,
    last_failure_at timestamp without time zone,
    consecutive_failures integer DEFAULT 0,
    is_enabled boolean DEFAULT true NOT NULL,
    tags character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 分群表
CREATE TABLE scrm.scrm_segment (
    id bigint NOT NULL,
    segment_name character varying(200) NOT NULL,
    segment_code character varying(50) NOT NULL,
    description character varying(500),
    segment_type character varying(20) DEFAULT 'DYNAMIC'::character varying NOT NULL,
    category character varying(100),
    condition_type character varying(20) DEFAULT 'ALL'::character varying NOT NULL,
    conditions text NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    member_count integer DEFAULT 0,
    last_calculated_at timestamp without time zone,
    calculation_frequency character varying(20) DEFAULT 'DAILY'::character varying NOT NULL,
    auto_update boolean DEFAULT true NOT NULL,
    color character varying(20),
    icon character varying(100),
    tags character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 分群历史
CREATE TABLE scrm.scrm_segment_history (
    id bigint NOT NULL,
    segment_id bigint NOT NULL,
    snapshot_date date NOT NULL,
    member_count integer NOT NULL,
    added_count integer DEFAULT 0,
    removed_count integer DEFAULT 0,
    avg_order_count double precision DEFAULT 0,
    avg_total_amount double precision DEFAULT 0,
    avg_engagement_score double precision DEFAULT 0,
    top_levels character varying(500),
    top_tags character varying(500),
    calculated_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 分群成员
CREATE TABLE scrm.scrm_segment_member (
    id bigint NOT NULL,
    segment_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    customer_level character varying(50),
    joined_at timestamp without time zone NOT NULL,
    left_at timestamp without time zone,
    match_score double precision DEFAULT 1.0,
    match_details text,
    is_current_member boolean DEFAULT true NOT NULL,
    source character varying(20) DEFAULT 'AUTO'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 话术表
CREATE TABLE scrm.scrm_speech (
    id bigint NOT NULL,
    category_id bigint,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    speech_type character varying(20) DEFAULT 'TEXT'::character varying NOT NULL,
    media_urls text,
    platform_type character varying(30),
    scenario character varying(50),
    tags character varying(500),
    sort_order integer DEFAULT 0,
    use_count integer DEFAULT 0,
    like_count integer DEFAULT 0,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_speech IS 'SCRM 话术条目表, 团队共享话术库';
COMMENT ON COLUMN scrm.scrm_speech.category_id IS '分类 ID, 可空表示未分类';
COMMENT ON COLUMN scrm.scrm_speech.title IS '话术标题';
COMMENT ON COLUMN scrm.scrm_speech.content IS '话术内容 (TEXT/IMAGE/VIDEO/FILE/LINK/MIXED 时为主文本)';
COMMENT ON COLUMN scrm.scrm_speech.speech_type IS '话术类型: TEXT/IMAGE/VIDEO/FILE/LINK/MIXED';
COMMENT ON COLUMN scrm.scrm_speech.media_urls IS '媒体 URL JSON 数组字符串, 如 ["url1","url2"]';
COMMENT ON COLUMN scrm.scrm_speech.platform_type IS '适用平台, 可空表示全部';
COMMENT ON COLUMN scrm.scrm_speech.scenario IS '使用场景: GREETING/FOLLOW_UP/REJECTION/HOLIDAY/AFTER_SALE/ETC';
COMMENT ON COLUMN scrm.scrm_speech.tags IS '标签 (逗号分隔)';
COMMENT ON COLUMN scrm.scrm_speech.sort_order IS '排序值, 数字越小越靠前';
COMMENT ON COLUMN scrm.scrm_speech.use_count IS '使用次数';
COMMENT ON COLUMN scrm.scrm_speech.like_count IS '点赞数';
COMMENT ON COLUMN scrm.scrm_speech.status IS '状态: ACTIVE/INACTIVE/DRAFT';
COMMENT ON COLUMN scrm.scrm_speech.created_by IS '创建人';
COMMENT ON COLUMN scrm.scrm_speech.create_time IS '创建时间';
COMMENT ON COLUMN scrm.scrm_speech.update_time IS '更新时间';
COMMENT ON COLUMN scrm.scrm_speech.version IS '乐观锁版本号';
-- 话术分类
CREATE TABLE scrm.scrm_speech_category (
    id bigint NOT NULL,
    category_name character varying(100) NOT NULL,
    parent_id bigint,
    sort_order integer DEFAULT 0,
    description character varying(500),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_speech_category IS 'SCRM 话术分类表, 支持多级父子分类';
COMMENT ON COLUMN scrm.scrm_speech_category.category_name IS '分类名称';
COMMENT ON COLUMN scrm.scrm_speech_category.parent_id IS '父分类 ID, 可空表示顶级分类';
COMMENT ON COLUMN scrm.scrm_speech_category.sort_order IS '排序值, 数字越小越靠前';
COMMENT ON COLUMN scrm.scrm_speech_category.description IS '分类描述';
COMMENT ON COLUMN scrm.scrm_speech_category.status IS '状态: ACTIVE/INACTIVE';
COMMENT ON COLUMN scrm.scrm_speech_category.create_time IS '创建时间';
COMMENT ON COLUMN scrm.scrm_speech_category.update_time IS '更新时间';
COMMENT ON COLUMN scrm.scrm_speech_category.version IS '乐观锁版本号';
-- 话术推荐
CREATE TABLE scrm.scrm_speech_recommendation (
    id bigint NOT NULL,
    customer_id bigint,
    customer_name character varying(200),
    scenario_id bigint NOT NULL,
    scenario_name character varying(200),
    recommended_speech_ids character varying(1000) NOT NULL,
    match_context text NOT NULL,
    match_score double precision DEFAULT 0,
    match_reasons character varying(1000),
    selected_speech_id bigint,
    used_at timestamp without time zone,
    feedback character varying(20),
    feedback_comment character varying(500),
    outcome character varying(20),
    recommended_at timestamp without time zone NOT NULL,
    recommended_by character varying(100),
    recommended_by_name character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 话术场景
CREATE TABLE scrm.scrm_speech_scenario (
    id bigint NOT NULL,
    scenario_name character varying(200) NOT NULL,
    scenario_code character varying(50) NOT NULL,
    scenario_category character varying(50) NOT NULL,
    description character varying(500),
    trigger_conditions text,
    applicable_products character varying(500),
    applicable_channels character varying(500),
    customer_stage character varying(50),
    priority integer DEFAULT 0,
    speech_count integer DEFAULT 0,
    avg_rating double precision DEFAULT 0,
    usage_count integer DEFAULT 0,
    success_rate double precision DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 调研表
CREATE TABLE scrm.scrm_survey (
    id bigint NOT NULL,
    survey_name character varying(200) NOT NULL,
    survey_type character varying(20) NOT NULL,
    description character varying(500),
    title character varying(200) NOT NULL,
    intro_text text,
    outro_text text,
    questions text NOT NULL,
    scale_type character varying(20),
    trigger_event character varying(50),
    trigger_delay_hours integer DEFAULT 0,
    target_segment character varying(500),
    channels character varying(200),
    estimated_time_minutes integer DEFAULT 2,
    start_date date,
    end_date date,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    response_count integer DEFAULT 0,
    completion_rate double precision DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 调研邀请
CREATE TABLE scrm.scrm_survey_invitation (
    id bigint NOT NULL,
    survey_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    channel character varying(20) NOT NULL,
    contact_info character varying(200),
    invitation_code character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    sent_at timestamp without time zone,
    opened_at timestamp without time zone,
    completed_at timestamp without time zone,
    expired_at timestamp without time zone,
    reminder_count integer DEFAULT 0,
    last_reminder_at timestamp without time zone,
    source_event character varying(100),
    source_id character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 调研响应
CREATE TABLE scrm.scrm_survey_response (
    id bigint NOT NULL,
    survey_id bigint NOT NULL,
    invitation_id bigint,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    responses text NOT NULL,
    nps_score integer,
    csat_score integer,
    ces_score integer,
    overall_score double precision,
    sentiment character varying(20),
    feedback_text text,
    tags character varying(500),
    follow_up_required boolean DEFAULT false NOT NULL,
    follow_up_status character varying(20),
    assignee_id character varying(100),
    submitted_at timestamp without time zone NOT NULL,
    duration_seconds integer,
    client_ip character varying(100),
    user_agent character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 系统配置
CREATE TABLE scrm.scrm_system_config (
    id bigint NOT NULL,
    config_key character varying(200) NOT NULL,
    config_value text,
    default_value text,
    config_name character varying(200) NOT NULL,
    description character varying(500),
    config_group character varying(100) DEFAULT 'GENERAL'::character varying NOT NULL,
    config_type character varying(30) NOT NULL,
    data_type character varying(50),
    enum_options character varying(1000),
    validation_regex character varying(500),
    validation_message character varying(500),
    min_value double precision,
    max_value double precision,
    max_length integer,
    is_required boolean DEFAULT false NOT NULL,
    is_read_only boolean DEFAULT false NOT NULL,
    is_encrypted boolean DEFAULT false NOT NULL,
    is_sensitive boolean DEFAULT false NOT NULL,
    is_system boolean DEFAULT false NOT NULL,
    is_visible boolean DEFAULT true NOT NULL,
    is_searchable boolean DEFAULT false NOT NULL,
    display_order integer DEFAULT 0,
    help_text character varying(1000),
    placeholder character varying(500),
    ui_component character varying(50),
    ui_props text,
    depends_on character varying(200),
    dependency_condition character varying(500),
    applicable_modules character varying(500),
    applicable_roles character varying(500),
    environment character varying(50) DEFAULT 'ALL'::character varying NOT NULL,
    is_overridable boolean DEFAULT true NOT NULL,
    is_cachable boolean DEFAULT true NOT NULL,
    cache_ttl_seconds integer DEFAULT 300,
    change_count integer DEFAULT 0,
    last_changed_at timestamp without time zone,
    last_changed_by character varying(100),
    enabled boolean DEFAULT true NOT NULL,
    tags character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_system_config IS 'SCRM 系统配置表';
COMMENT ON COLUMN scrm.scrm_system_config.config_key IS '配置键 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_system_config.config_value IS '配置值 (可空, TEXT)';
COMMENT ON COLUMN scrm.scrm_system_config.default_value IS '默认值 (可空, TEXT)';
COMMENT ON COLUMN scrm.scrm_system_config.config_name IS '配置名称';
COMMENT ON COLUMN scrm.scrm_system_config.description IS '描述 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.config_group IS '配置分组 (默认 GENERAL)';
COMMENT ON COLUMN scrm.scrm_system_config.config_type IS '配置类型: STRING/INTEGER/DOUBLE PRECISION/BOOLEAN/JSON/XML/DATE/TIME/DATETIME/ENUM/PASSWORD/ENCRYPTED/FILE/URL/EMAIL/PHONE/COLOR/RICH_TEXT';
COMMENT ON COLUMN scrm.scrm_system_config.data_type IS '数据类型 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.enum_options IS '枚举选项 JSON: [{label,value}] (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.validation_regex IS '验证正则 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.validation_message IS '验证提示 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.min_value IS '最小值 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.max_value IS '最大值 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.max_length IS '最大长度 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.is_required IS '是否必填 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_system_config.is_read_only IS '是否只读 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_system_config.is_encrypted IS '是否加密 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_system_config.is_sensitive IS '是否敏感信息 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_system_config.is_system IS '是否系统级 (默认 FALSE, 系统级不可删)';
COMMENT ON COLUMN scrm.scrm_system_config.is_visible IS '是否可见 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_system_config.is_searchable IS '是否可搜索 (默认 FALSE)';
COMMENT ON COLUMN scrm.scrm_system_config.display_order IS '显示顺序 (默认 0)';
COMMENT ON COLUMN scrm.scrm_system_config.help_text IS '帮助文本 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.placeholder IS '占位提示 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.ui_component IS 'UI 组件: INPUT/TEXTAREA/SELECT/MULTI_SELECT/RADIO/CHECKBOX/SWITCH/SLIDER/DATE_PICKER/TIME_PICKER/COLOR_PICKER/FILE_UPLOAD/RICH_EDITOR/CODE_EDITOR/JSON_EDITOR (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.ui_props IS 'UI 属性 JSON (可空, TEXT)';
COMMENT ON COLUMN scrm.scrm_system_config.depends_on IS '依赖配置键 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.dependency_condition IS '依赖条件 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.applicable_modules IS '适用模块 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.applicable_roles IS '可见角色 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.environment IS '环境限定: ALL/DEV/STAGING/PRODUCTION (默认 ALL)';
COMMENT ON COLUMN scrm.scrm_system_config.is_overridable IS '可覆盖 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_system_config.is_cachable IS '可缓存 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_system_config.cache_ttl_seconds IS '缓存 TTL 秒 (默认 300)';
COMMENT ON COLUMN scrm.scrm_system_config.change_count IS '变更次数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_system_config.last_changed_at IS '最近变更时间 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.last_changed_by IS '最近变更人 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.enabled IS '是否启用 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_system_config.tags IS '标签 (可空)';
COMMENT ON COLUMN scrm.scrm_system_config.created_by IS '创建人 (可空)';
-- 标签表
CREATE TABLE scrm.scrm_tag (
    id bigint NOT NULL,
    group_id bigint,
    tag_name character varying(100) NOT NULL,
    tag_code character varying(50) NOT NULL,
    tag_type character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    value_type character varying(20),
    tag_value character varying(500),
    description character varying(500),
    color character varying(20),
    icon character varying(100),
    sort_order integer DEFAULT 0,
    customer_count integer DEFAULT 0,
    rule_id bigint,
    is_system boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 标签-客户关联
CREATE TABLE scrm.scrm_tag_customer (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    tag_id bigint NOT NULL,
    tag_value character varying(500),
    tag_source character varying(20) DEFAULT 'MANUAL'::character varying NOT NULL,
    assigned_by character varying(100),
    assigned_by_name character varying(100),
    assigned_at timestamp without time zone NOT NULL,
    expires_at timestamp without time zone,
    confidence double precision DEFAULT 1.0,
    note character varying(500),
    is_auto boolean DEFAULT false NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 标签分组
CREATE TABLE scrm.scrm_tag_group (
    id bigint NOT NULL,
    group_name character varying(100) NOT NULL,
    group_code character varying(50) NOT NULL,
    description character varying(500),
    color character varying(20),
    icon character varying(100),
    sort_order integer DEFAULT 0,
    tag_count integer DEFAULT 0,
    customer_count integer DEFAULT 0,
    is_system boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 标签规则
CREATE TABLE scrm.scrm_tag_rule (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    tag_id bigint NOT NULL,
    description character varying(500),
    condition_type character varying(20) DEFAULT 'ALL'::character varying NOT NULL,
    conditions text NOT NULL,
    target_fields character varying(500),
    execution_frequency character varying(20) DEFAULT 'DAILY'::character varying NOT NULL,
    last_executed_at timestamp without time zone,
    matched_count integer DEFAULT 0,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 任务依赖
CREATE TABLE scrm.scrm_task_dependency (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    task_code character varying(50),
    depends_on_task_id bigint NOT NULL,
    depends_on_task_code character varying(50),
    dependency_type character varying(20) DEFAULT 'ON_SUCCESS'::character varying NOT NULL,
    condition_expression character varying(500),
    delay_seconds integer DEFAULT 0,
    is_required boolean DEFAULT true NOT NULL,
    max_wait_minutes integer DEFAULT 60,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 任务执行
CREATE TABLE scrm.scrm_task_execution (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    task_name character varying(200),
    task_code character varying(50),
    execution_no character varying(100),
    trigger_type character varying(20) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    scheduled_at timestamp without time zone,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    duration_ms integer,
    worker_id character varying(100),
    worker_name character varying(100),
    parameters text,
    result text,
    return_value character varying(2000),
    output_logs text,
    error_message character varying(2000),
    error_stack text,
    retry_count integer DEFAULT 0,
    max_retries integer DEFAULT 3,
    next_retry_at timestamp without time zone,
    triggered_by character varying(100),
    triggered_by_name character varying(100),
    dependency_execution_id bigint,
    is_retried boolean DEFAULT false NOT NULL,
    retry_execution_id bigint,
    progress integer DEFAULT 0,
    progress_message character varying(500),
    metadata text,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 工单表
CREATE TABLE scrm.scrm_ticket (
    id bigint NOT NULL,
    ticket_no character varying(50) NOT NULL,
    title character varying(200) NOT NULL,
    description text,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    account_id bigint,
    category character varying(50) NOT NULL,
    priority character varying(10) DEFAULT 'MEDIUM'::character varying NOT NULL,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    source character varying(30) DEFAULT 'CUSTOMER'::character varying NOT NULL,
    assignee_id character varying(100),
    assignee_name character varying(100),
    team_id character varying(100),
    related_order_id character varying(100),
    related_product_id character varying(100),
    sla_due_at timestamp without time zone,
    first_response_at timestamp without time zone,
    resolved_at timestamp without time zone,
    closed_at timestamp without time zone,
    resolution_time_minutes integer,
    satisfaction_score integer,
    satisfaction_comment character varying(500),
    tags character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 工单评论
CREATE TABLE scrm.scrm_ticket_comment (
    id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    comment_type character varying(20) NOT NULL,
    author_id character varying(100) NOT NULL,
    author_name character varying(100),
    content text NOT NULL,
    attachments character varying(1000),
    is_internal boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 工单历史
CREATE TABLE scrm.scrm_ticket_history (
    id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    action_type character varying(30) NOT NULL,
    from_value character varying(200),
    to_value character varying(200),
    operator_id character varying(100) NOT NULL,
    operator_name character varying(100),
    action_time timestamp without time zone NOT NULL,
    note character varying(500),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 触点表
CREATE TABLE scrm.scrm_touchpoint (
    id bigint NOT NULL,
    touchpoint_name character varying(100) NOT NULL,
    touchpoint_code character varying(50) NOT NULL,
    touchpoint_type character varying(50) NOT NULL,
    url character varying(500),
    app_id character varying(200),
    description character varying(500),
    is_active boolean DEFAULT true NOT NULL,
    total_events integer DEFAULT 0,
    unique_visitors integer DEFAULT 0,
    conversion_count integer DEFAULT 0,
    last_event_at timestamp without time zone,
    config text,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 平台登录用户表
CREATE TABLE scrm.scrm_user (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    username character varying(64) NOT NULL,
    password character varying(100) NOT NULL,
    display_name character varying(100),
    email character varying(200),
    roles character varying(200) NOT NULL,
    status integer NOT NULL,
    last_login_at timestamp without time zone
);
COMMENT ON TABLE scrm.scrm_user IS 'SCRM 平台登录用户表';
COMMENT ON COLUMN scrm.scrm_user.username IS '登录用户名 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_user.password IS '口令 BCrypt 密文, 不落明文';
COMMENT ON COLUMN scrm.scrm_user.display_name IS '展示昵称 (可空, 为空时前端回退展示 username)';
COMMENT ON COLUMN scrm.scrm_user.email IS '邮箱 (可空)';
COMMENT ON COLUMN scrm.scrm_user.roles IS '角色列表 (逗号分隔, 如 ADMIN,OPERATOR)';
COMMENT ON COLUMN scrm.scrm_user.status IS '状态: 1=启用, 0=禁用 (禁用后不可登录)';
COMMENT ON COLUMN scrm.scrm_user.last_login_at IS '最后登录时间';
-- 用户-账号关联表
CREATE TABLE scrm.scrm_user_account (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    user_id character varying(64) NOT NULL,
    account_id bigint NOT NULL,
    department_id character varying(64)
);
-- 用户设备表
CREATE TABLE scrm.scrm_user_device (
    id bigint NOT NULL,
    user_id character varying(64) NOT NULL,
    client_id character varying(128) NOT NULL,
    device_id character varying(128),
    platform character varying(16) DEFAULT 'android'::character varying NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    last_active_at timestamp without time zone,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_user_device IS 'SCRM 用户设备表, 存储业务员 APP 推送 token (个推 client_id)';
COMMENT ON COLUMN scrm.scrm_user_device.user_id IS '用户 ID, 关联 sys_user.user_id';
COMMENT ON COLUMN scrm.scrm_user_device.client_id IS '个推 client_id, 推送目标标识';
COMMENT ON COLUMN scrm.scrm_user_device.device_id IS '设备唯一标识 (可空)';
COMMENT ON COLUMN scrm.scrm_user_device.platform IS '平台: android / ios';
COMMENT ON COLUMN scrm.scrm_user_device.status IS '状态: ACTIVE / INACTIVE';
COMMENT ON COLUMN scrm.scrm_user_device.last_active_at IS '最后活跃时间';
COMMENT ON COLUMN scrm.scrm_user_device.create_time IS '创建时间';
COMMENT ON COLUMN scrm.scrm_user_device.update_time IS '更新时间';
COMMENT ON COLUMN scrm.scrm_user_device.version IS '乐观锁版本号';
-- 拜访计划
CREATE TABLE scrm.scrm_visit_plan (
    id bigint NOT NULL,
    plan_name character varying(200) NOT NULL,
    plan_code character varying(50) NOT NULL,
    description character varying(500),
    plan_type character varying(30) NOT NULL,
    target_type character varying(20) NOT NULL,
    target_criteria text,
    visit_frequency character varying(20) NOT NULL,
    frequency_config text,
    visit_method character varying(30) DEFAULT 'PHONE'::character varying NOT NULL,
    template_id bigint,
    assigned_to character varying(100),
    team_id character varying(100),
    start_date date NOT NULL,
    end_date date,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    total_tasks integer DEFAULT 0,
    completed_tasks integer DEFAULT 0,
    pending_tasks integer DEFAULT 0,
    overdue_tasks integer DEFAULT 0,
    completion_rate double precision DEFAULT 0,
    avg_satisfaction_score double precision DEFAULT 0,
    success_rate double precision DEFAULT 0,
    priority integer DEFAULT 0,
    tags character varying(500),
    auto_generate boolean DEFAULT false NOT NULL,
    last_generated_at timestamp without time zone,
    next_generate_at timestamp without time zone,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 拜访任务
CREATE TABLE scrm.scrm_visit_task (
    id bigint NOT NULL,
    task_no character varying(100) NOT NULL,
    plan_id bigint,
    plan_name character varying(200),
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    customer_level character varying(50),
    customer_phone character varying(50),
    visit_type character varying(30) NOT NULL,
    visit_method character varying(30) NOT NULL,
    scheduled_date date NOT NULL,
    scheduled_time time without time zone,
    actual_visit_date date,
    actual_visit_time time without time zone,
    actual_duration_minutes integer,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    assigned_to character varying(100),
    assigned_at timestamp without time zone,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    visit_outcome character varying(20),
    satisfaction_score integer,
    nps_score integer,
    feedback character varying(2000),
    summary character varying(1000),
    action_items character varying(1000),
    follow_up_required boolean DEFAULT false NOT NULL,
    follow_up_date date,
    follow_up_type character varying(30),
    opportunity_found boolean DEFAULT false NOT NULL,
    opportunity_description character varying(500),
    issue_found boolean DEFAULT false NOT NULL,
    issue_description character varying(500),
    issue_resolved boolean DEFAULT false NOT NULL,
    recording_url character varying(500),
    notes character varying(500),
    location character varying(200),
    reminder_sent boolean DEFAULT false NOT NULL,
    reminder_sent_at timestamp without time zone,
    reschedule_count integer DEFAULT 0,
    original_date date,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- 拜访模板
CREATE TABLE scrm.scrm_visit_template (
    id bigint NOT NULL,
    template_name character varying(200) NOT NULL,
    template_code character varying(50) NOT NULL,
    description character varying(500),
    visit_type character varying(30) NOT NULL,
    visit_method character varying(30) NOT NULL,
    questions text NOT NULL,
    introduction character varying(1000),
    closing character varying(1000),
    success_criteria character varying(500),
    estimated_duration_minutes integer DEFAULT 15,
    applicable_products character varying(500),
    tags character varying(500),
    usage_count integer DEFAULT 0,
    avg_satisfaction_score double precision DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- VOC 洞察
CREATE TABLE scrm.scrm_voc_insight (
    id bigint NOT NULL,
    insight_title character varying(500) NOT NULL,
    insight_type character varying(30) NOT NULL,
    description character varying(2000),
    summary character varying(2000),
    source_topic_ids character varying(500),
    source_voice_ids character varying(500),
    related_voice_count integer DEFAULT 0 NOT NULL,
    data_points text,
    analysis text,
    impact_level character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    impact_areas character varying(500),
    affected_segments character varying(500),
    estimated_impact double precision DEFAULT 0 NOT NULL,
    recommendations character varying(2000),
    action_items character varying(2000),
    priority character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    created_by character varying(100),
    reviewed_by character varying(100),
    reviewed_at timestamp without time zone,
    published_at timestamp without time zone,
    published_by character varying(100),
    shared_with character varying(500),
    feedback_count integer DEFAULT 0 NOT NULL,
    feedback_rating double precision DEFAULT 0 NOT NULL,
    tags character varying(500),
    period character varying(50),
    analysis_date date,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- VOC 话题
CREATE TABLE scrm.scrm_voc_topic (
    id bigint NOT NULL,
    topic_name character varying(200) NOT NULL,
    topic_code character varying(50) NOT NULL,
    description character varying(500),
    parent_topic_id bigint,
    topic_level integer DEFAULT 1 NOT NULL,
    category character varying(100),
    keywords character varying(500),
    voice_count integer DEFAULT 0 NOT NULL,
    positive_count integer DEFAULT 0 NOT NULL,
    negative_count integer DEFAULT 0 NOT NULL,
    neutral_count integer DEFAULT 0 NOT NULL,
    positive_rate double precision DEFAULT 0 NOT NULL,
    negative_rate double precision DEFAULT 0 NOT NULL,
    avg_sentiment_score double precision DEFAULT 0 NOT NULL,
    avg_rating double precision DEFAULT 0 NOT NULL,
    trend_direction character varying(20) DEFAULT 'STABLE'::character varying NOT NULL,
    trend_percent double precision DEFAULT 0 NOT NULL,
    last_voice_at timestamp without time zone,
    first_voice_at timestamp without time zone,
    urgency_score double precision DEFAULT 0 NOT NULL,
    impact_score double precision DEFAULT 0 NOT NULL,
    priority_score double precision DEFAULT 0 NOT NULL,
    is_hot_topic boolean DEFAULT false NOT NULL,
    is_emerging boolean DEFAULT false NOT NULL,
    assigned_department character varying(200),
    action_taken boolean DEFAULT false NOT NULL,
    last_analysis_at timestamp without time zone,
    enabled boolean DEFAULT true NOT NULL,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- VOC 原声
CREATE TABLE scrm.scrm_voc_voice (
    id bigint NOT NULL,
    voice_no character varying(100) NOT NULL,
    customer_id bigint,
    customer_name character varying(200),
    customer_contact character varying(200),
    source character varying(30) NOT NULL,
    source_detail character varying(200),
    source_url character varying(500),
    voice_type character varying(30) NOT NULL,
    title character varying(500),
    content text NOT NULL,
    original_content text,
    language character varying(20) DEFAULT 'zh-CN'::character varying NOT NULL,
    sentiment character varying(20) DEFAULT 'NEUTRAL'::character varying NOT NULL,
    sentiment_score double precision DEFAULT 0 NOT NULL,
    priority character varying(20) DEFAULT 'MEDIUM'::character varying NOT NULL,
    category character varying(100),
    sub_category character varying(100),
    tags character varying(500),
    rating integer,
    product_id bigint,
    product_name character varying(200),
    order_id character varying(100),
    service_id character varying(100),
    department character varying(200),
    assigned_to character varying(100),
    assigned_at timestamp without time zone,
    status character varying(20) DEFAULT 'NEW'::character varying NOT NULL,
    collected_at timestamp without time zone NOT NULL,
    collected_by character varying(100),
    analyzed_at timestamp without time zone,
    resolved_at timestamp without time zone,
    resolution_time_hours integer DEFAULT 0 NOT NULL,
    resolution character varying(2000),
    customer_satisfaction integer,
    is_public boolean DEFAULT false NOT NULL,
    is_verified boolean DEFAULT false NOT NULL,
    verified_by character varying(100),
    response_count integer DEFAULT 0 NOT NULL,
    like_count integer DEFAULT 0 NOT NULL,
    view_count integer DEFAULT 0 NOT NULL,
    share_count integer DEFAULT 0 NOT NULL,
    attachments character varying(1000),
    metadata text,
    related_voice_ids character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
-- Webhook 配置
CREATE TABLE scrm.scrm_webhook_config (
    id bigint NOT NULL,
    webhook_name character varying(200) NOT NULL,
    target_url character varying(500) NOT NULL,
    secret character varying(200),
    subscribed_events text NOT NULL,
    event_filter text,
    http_method character varying(10) DEFAULT 'POST'::character varying NOT NULL,
    headers text,
    timeout_seconds integer DEFAULT 10,
    max_retries integer DEFAULT 3,
    retry_interval_seconds integer DEFAULT 60,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    last_trigger_at timestamp without time zone,
    last_status_code integer,
    last_error character varying(500),
    success_count integer DEFAULT 0,
    fail_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_webhook_config IS 'SCRM Webhook 配置表, 事件订阅与推送配置';
COMMENT ON COLUMN scrm.scrm_webhook_config.webhook_name IS 'Webhook 名称';
COMMENT ON COLUMN scrm.scrm_webhook_config.target_url IS '接收 URL';
COMMENT ON COLUMN scrm.scrm_webhook_config.secret IS '签名密钥 (可空, 用于 HMAC-SHA256 签名验证)';
COMMENT ON COLUMN scrm.scrm_webhook_config.subscribed_events IS '订阅事件类型列表 JSON (如 ["CUSTOMER_CREATED","MESSAGE_RECEIVED"])';
COMMENT ON COLUMN scrm.scrm_webhook_config.event_filter IS '事件过滤条件 JSON (可空, 用于细粒度过滤)';
COMMENT ON COLUMN scrm.scrm_webhook_config.http_method IS 'HTTP 方法, 默认 POST';
COMMENT ON COLUMN scrm.scrm_webhook_config.headers IS '自定义 HTTP 头 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_webhook_config.timeout_seconds IS '请求超时秒数';
COMMENT ON COLUMN scrm.scrm_webhook_config.max_retries IS '最大重试次数';
COMMENT ON COLUMN scrm.scrm_webhook_config.retry_interval_seconds IS '重试间隔秒数';
COMMENT ON COLUMN scrm.scrm_webhook_config.status IS '状态: ACTIVE/INACTIVE/ERROR';
COMMENT ON COLUMN scrm.scrm_webhook_config.last_trigger_at IS '最近触发时间';
COMMENT ON COLUMN scrm.scrm_webhook_config.last_status_code IS '最近一次响应码';
COMMENT ON COLUMN scrm.scrm_webhook_config.last_error IS '最近一次错误信息';
COMMENT ON COLUMN scrm.scrm_webhook_config.success_count IS '成功推送次数';
COMMENT ON COLUMN scrm.scrm_webhook_config.fail_count IS '失败推送次数';
COMMENT ON COLUMN scrm.scrm_webhook_config.created_by IS '创建人';
-- Webhook 日志
CREATE TABLE scrm.scrm_webhook_log (
    id bigint NOT NULL,
    webhook_id bigint NOT NULL,
    event_type character varying(50) NOT NULL,
    event_id character varying(200) NOT NULL,
    payload text NOT NULL,
    request_body text,
    response_status integer,
    response_body character varying(2000),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0,
    max_attempts integer DEFAULT 3,
    next_retry_at timestamp without time zone,
    sent_at timestamp without time zone,
    completed_at timestamp without time zone,
    error_message character varying(500),
    duration_ms integer,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_webhook_log IS 'SCRM Webhook 推送日志表, 记录每次推送的执行明细';
COMMENT ON COLUMN scrm.scrm_webhook_log.webhook_id IS 'Webhook 配置 ID (引用 scrm_webhook_config.id)';
COMMENT ON COLUMN scrm.scrm_webhook_log.event_type IS '事件类型';
COMMENT ON COLUMN scrm.scrm_webhook_log.event_id IS '事件唯一 ID';
COMMENT ON COLUMN scrm.scrm_webhook_log.payload IS '事件负载 JSON';
COMMENT ON COLUMN scrm.scrm_webhook_log.request_body IS '实际发送的请求体 (可空)';
COMMENT ON COLUMN scrm.scrm_webhook_log.response_status IS 'HTTP 响应码 (可空)';
COMMENT ON COLUMN scrm.scrm_webhook_log.response_body IS '响应体摘要 (可空, 截取前 2000 字符)';
COMMENT ON COLUMN scrm.scrm_webhook_log.status IS '状态: PENDING/SENDING/SUCCESS/FAILED/RETRY/EXPIRED';
COMMENT ON COLUMN scrm.scrm_webhook_log.attempt_count IS '已尝试次数';
COMMENT ON COLUMN scrm.scrm_webhook_log.max_attempts IS '最大尝试次数';
COMMENT ON COLUMN scrm.scrm_webhook_log.next_retry_at IS '下次重试时间 (RETRY 状态下到期由 processRetries 捞取)';
COMMENT ON COLUMN scrm.scrm_webhook_log.sent_at IS '实际发送时间';
COMMENT ON COLUMN scrm.scrm_webhook_log.completed_at IS '完成时间 (成功或最终失败时填充)';
COMMENT ON COLUMN scrm.scrm_webhook_log.error_message IS '错误信息 (可空)';
COMMENT ON COLUMN scrm.scrm_webhook_log.duration_ms IS '耗时毫秒 (可空)';
-- 欢迎语消息表
CREATE TABLE scrm.scrm_welcome_message (
    id bigint NOT NULL,
    rule_name character varying(200) NOT NULL,
    account_id bigint,
    channel_code_id bigint,
    platform_type character varying(30) DEFAULT 'wework'::character varying NOT NULL,
    message_type character varying(20) DEFAULT 'TEXT'::character varying NOT NULL,
    content text NOT NULL,
    media_url character varying(500),
    link_title character varying(200),
    link_url character varying(500),
    link_desc character varying(500),
    miniprogram_title character varying(200),
    miniprogram_app_id character varying(100),
    miniprogram_page character varying(500),
    secondary_messages text,
    delay_seconds integer DEFAULT 0,
    cooldown_minutes integer DEFAULT 0,
    effective_time_start character varying(5),
    effective_time_end character varying(5),
    weekend_enabled boolean DEFAULT true NOT NULL,
    priority integer DEFAULT 0,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    trigger_count integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_welcome_message IS 'SCRM 企微欢迎语配置表, 新客户添加时自动发送欢迎语';
COMMENT ON COLUMN scrm.scrm_welcome_message.rule_name IS '规则名称';
COMMENT ON COLUMN scrm.scrm_welcome_message.account_id IS '绑定账号 ID (可空, 空=所有账号)';
COMMENT ON COLUMN scrm.scrm_welcome_message.channel_code_id IS '绑定渠道活码 ID (可空, 空=非渠道来源)';
COMMENT ON COLUMN scrm.scrm_welcome_message.platform_type IS '适用平台 (默认 wework)';
COMMENT ON COLUMN scrm.scrm_welcome_message.message_type IS '消息类型: TEXT/IMAGE/LINK/MINIPROGRAM/MIXED';
COMMENT ON COLUMN scrm.scrm_welcome_message.content IS '文本内容 (支持变量nickname等)';
COMMENT ON COLUMN scrm.scrm_welcome_message.media_url IS '图片/文件 URL (可空)';
COMMENT ON COLUMN scrm.scrm_welcome_message.link_title IS '链接标题 (可空)';
COMMENT ON COLUMN scrm.scrm_welcome_message.link_url IS '链接 URL (可空)';
COMMENT ON COLUMN scrm.scrm_welcome_message.link_desc IS '链接描述 (可空)';
COMMENT ON COLUMN scrm.scrm_welcome_message.miniprogram_title IS '小程序标题 (可空)';
COMMENT ON COLUMN scrm.scrm_welcome_message.miniprogram_app_id IS '小程序 AppId (可空)';
COMMENT ON COLUMN scrm.scrm_welcome_message.miniprogram_page IS '小程序页面路径 (可空)';
COMMENT ON COLUMN scrm.scrm_welcome_message.secondary_messages IS '跟进消息序列 JSON 数组 (可空)';
COMMENT ON COLUMN scrm.scrm_welcome_message.delay_seconds IS '延迟发送秒数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_welcome_message.cooldown_minutes IS '同一客户冷却期分钟数 (默认 0=不限制)';
COMMENT ON COLUMN scrm.scrm_welcome_message.effective_time_start IS '生效开始时间 HH:mm (可空)';
COMMENT ON COLUMN scrm.scrm_welcome_message.effective_time_end IS '生效结束时间 HH:mm (可空)';
COMMENT ON COLUMN scrm.scrm_welcome_message.weekend_enabled IS '周末是否生效 (默认 TRUE)';
COMMENT ON COLUMN scrm.scrm_welcome_message.priority IS '优先级 (默认 0, 多规则取最高者)';
COMMENT ON COLUMN scrm.scrm_welcome_message.status IS '状态: ACTIVE/INACTIVE';
COMMENT ON COLUMN scrm.scrm_welcome_message.trigger_count IS '触发次数';
COMMENT ON COLUMN scrm.scrm_welcome_message.created_by IS '创建人';
-- 企微归档配置
CREATE TABLE scrm.scrm_wework_archive_config (
    id bigint NOT NULL,
    config_name character varying(200) NOT NULL,
    corp_id character varying(100) NOT NULL,
    agent_id character varying(100),
    secret character varying(500) NOT NULL,
    private_key character varying(2000) NOT NULL,
    sdk_lib_path character varying(500),
    last_seq bigint,
    last_fetch_at timestamp without time zone,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    error_message character varying(500),
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_wework_archive_config IS '企微会话存档配置: corpid/secret/RSA私钥/SDK路径与拉取状态';
COMMENT ON COLUMN scrm.scrm_wework_archive_config.corp_id IS '企业 corpid';
COMMENT ON COLUMN scrm.scrm_wework_archive_config.agent_id IS '应用 agentid (可空)';
COMMENT ON COLUMN scrm.scrm_wework_archive_config.secret IS '会话存档 secret (应用层加密存储)';
COMMENT ON COLUMN scrm.scrm_wework_archive_config.private_key IS 'RSA 私钥 PEM (应用层加密存储)';
COMMENT ON COLUMN scrm.scrm_wework_archive_config.sdk_lib_path IS '企微会话存档 SDK 路径 (可空)';
COMMENT ON COLUMN scrm.scrm_wework_archive_config.last_seq IS '最后拉取的 seq (可空)';
COMMENT ON COLUMN scrm.scrm_wework_archive_config.status IS '状态: ACTIVE(启用) / INACTIVE(停用) / ERROR(异常)';
COMMENT ON COLUMN scrm.scrm_wework_archive_config.error_message IS '错误信息 (可空)';
-- 企微归档游标
CREATE TABLE scrm.scrm_wework_archive_cursor (
    id bigint NOT NULL,
    config_id bigint NOT NULL,
    cursor_seq bigint NOT NULL,
    last_seq bigint NOT NULL,
    fetched_count integer DEFAULT 0 NOT NULL,
    error_count integer DEFAULT 0 NOT NULL,
    last_error character varying(500),
    last_fetch_at timestamp without time zone NOT NULL,
    status character varying(20) DEFAULT 'IDLE'::character varying NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_wework_archive_cursor IS '企微会话存档拉取游标: 每配置一条, 记录增量拉取位置';
COMMENT ON COLUMN scrm.scrm_wework_archive_cursor.config_id IS '存档配置 ID (引用 scrm_wework_archive_config.id)';
COMMENT ON COLUMN scrm.scrm_wework_archive_cursor.cursor_seq IS '当前游标 seq';
COMMENT ON COLUMN scrm.scrm_wework_archive_cursor.last_seq IS '企微最新 seq';
COMMENT ON COLUMN scrm.scrm_wework_archive_cursor.fetched_count IS '已拉取数';
COMMENT ON COLUMN scrm.scrm_wework_archive_cursor.error_count IS '错误次数';
COMMENT ON COLUMN scrm.scrm_wework_archive_cursor.status IS '状态: IDLE(空闲) / FETCHING(拉取中) / ERROR(异常)';
-- 企微归档消息
CREATE TABLE scrm.scrm_wework_archive_message (
    id bigint NOT NULL,
    config_id bigint NOT NULL,
    seq bigint NOT NULL,
    msg_id character varying(200) NOT NULL,
    action character varying(20) NOT NULL,
    from_user character varying(200) NOT NULL,
    to_list character varying(1000) NOT NULL,
    room_id character varying(200),
    msg_type character varying(30) NOT NULL,
    content text,
    raw_content text,
    media_url character varying(500),
    file_name character varying(200),
    file_size bigint,
    sent_at timestamp without time zone NOT NULL,
    archived_at timestamp without time zone NOT NULL,
    processed boolean DEFAULT false NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_wework_archive_message IS '企微会话存档消息: 解密后的会话内容, 支持合规审计与检索';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.config_id IS '存档配置 ID (引用 scrm_wework_archive_config.id)';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.seq IS '企微消息 seq';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.msg_id IS '消息 ID';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.action IS '动作: send(发送) / recall(撤回)';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.from_user IS '发送者';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.to_list IS '接收者列表 (逗号分隔)';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.room_id IS '群 ID (可空)';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.msg_type IS '消息类型: text/image/voice/video/file/emoji/revoke/agree/disagree/card/location/link/weapp/chatrecord/todo/vote/collect/redpacket/meeting_voice_call/voip_doc_share/external_redpacket';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.content IS '解密后的消息内容 JSON';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.raw_content IS '原始加密内容 (可空, 用于溯源)';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.sent_at IS '消息发送时间';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.archived_at IS '入库时间';
COMMENT ON COLUMN scrm.scrm_wework_archive_message.processed IS '是否已处理';
-- 工单表
CREATE TABLE scrm.scrm_work_order (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    order_no character varying(100) NOT NULL,
    title character varying(200) NOT NULL,
    description text,
    order_type character varying(50) NOT NULL,
    order_category character varying(100),
    priority character varying(20) DEFAULT 'NORMAL'::character varying NOT NULL,
    order_status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    customer_phone character varying(50),
    customer_email character varying(100),
    contact_person character varying(100),
    contact_phone character varying(50),
    contact_email character varying(100),
    product_id bigint,
    product_name character varying(200),
    product_category character varying(100),
    serial_number character varying(200),
    contract_id bigint,
    contract_no character varying(100),
    campaign_id bigint,
    source character varying(50) NOT NULL,
    channel character varying(50),
    assigned_to character varying(100),
    assigned_to_id bigint,
    assigned_department character varying(100),
    assigned_at timestamp without time zone,
    accepted_at timestamp without time zone,
    started_at timestamp without time zone,
    resolved_at timestamp without time zone,
    closed_at timestamp without time zone,
    last_response_at timestamp without time zone,
    response_time_minutes integer,
    resolution_time_minutes integer,
    sla_policy character varying(100),
    sla_response_due timestamp without time zone,
    sla_resolution_due timestamp without time zone,
    sla_response_met boolean,
    sla_resolution_met boolean,
    sla_breached boolean DEFAULT false NOT NULL,
    satisfaction_score integer,
    satisfaction_comment character varying(500),
    resolution character varying(2000),
    resolution_code character varying(50),
    root_cause character varying(500),
    is_repeated boolean DEFAULT false NOT NULL,
    related_order_ids character varying(500),
    escalated boolean DEFAULT false NOT NULL,
    escalated_to character varying(100),
    escalated_at timestamp without time zone,
    escalation_reason character varying(500),
    tags character varying(500),
    attachments character varying(1000),
    internal_notes character varying(1000),
    expected_resolution_date date,
    actual_resolution_date date,
    resolution_deadline date,
    is_urgent boolean DEFAULT false NOT NULL,
    is_vip_customer boolean DEFAULT false NOT NULL,
    follow_up_required boolean DEFAULT false NOT NULL,
    follow_up_date date,
    follow_up_by character varying(100),
    created_by character varying(100)
);
-- 工单日志
CREATE TABLE scrm.scrm_work_order_log (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    order_id bigint NOT NULL,
    order_no character varying(100),
    log_type character varying(30) NOT NULL,
    log_title character varying(200),
    log_content text,
    from_status character varying(20),
    to_status character varying(20),
    from_assignee character varying(100),
    to_assignee character varying(100),
    from_department character varying(100),
    to_department character varying(100),
    field_changes character varying(2000),
    is_internal boolean DEFAULT false NOT NULL,
    is_customer_visible boolean DEFAULT true NOT NULL,
    operator_id bigint,
    operator_name character varying(100),
    operator_type character varying(30) DEFAULT 'AGENT'::character varying,
    attachments character varying(1000),
    mentioned_users character varying(500),
    mentioned_departments character varying(500),
    time_spent_minutes integer DEFAULT 0,
    billable_time integer DEFAULT 0,
    ip_address character varying(50),
    user_agent character varying(500),
    created_by character varying(100)
);
-- 工单 SLA
CREATE TABLE scrm.scrm_work_order_sla (
    id bigint NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    policy_name character varying(100) NOT NULL,
    policy_code character varying(50) NOT NULL,
    description character varying(500),
    order_type character varying(50),
    priority character varying(20),
    customer_segment character varying(100),
    response_time_minutes integer NOT NULL,
    resolution_time_minutes integer NOT NULL,
    response_time_hours integer,
    resolution_time_hours integer,
    business_hours_only boolean DEFAULT false NOT NULL,
    business_hours_start character varying(10) DEFAULT '09:00'::character varying,
    business_hours_end character varying(10) DEFAULT '18:00'::character varying,
    business_days character varying(50) DEFAULT 'MON-FRI'::character varying,
    timezone character varying(50) DEFAULT 'Asia/Shanghai'::character varying,
    escalation_enabled boolean DEFAULT true NOT NULL,
    escalation_levels character varying(2000),
    first_response_breach_action character varying(200),
    resolution_breach_action character varying(200),
    warning_before_breach integer DEFAULT 30,
    auto_close_after_resolution integer DEFAULT 72,
    reopen_allowed boolean DEFAULT true NOT NULL,
    reopen_within_hours integer DEFAULT 168,
    penalty_per_breach double precision DEFAULT 0,
    credit_per_met double precision DEFAULT 0,
    target_compliance_rate double precision DEFAULT 95,
    current_compliance_rate double precision DEFAULT 0,
    total_orders integer DEFAULT 0,
    breached_orders integer DEFAULT 0,
    met_orders integer DEFAULT 0,
    avg_response_time double precision DEFAULT 0,
    avg_resolution_time double precision DEFAULT 0,
    enabled boolean DEFAULT true NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    created_by character varying(100)
);
-- 工作流表
CREATE TABLE scrm.scrm_workflow (
    id bigint NOT NULL,
    workflow_name character varying(200) NOT NULL,
    workflow_code character varying(50) NOT NULL,
    description character varying(500),
    workflow_type character varying(30) DEFAULT 'MARKETING'::character varying NOT NULL,
    trigger_type character varying(30) NOT NULL,
    trigger_config text NOT NULL,
    nodes text NOT NULL,
    edges text,
    entry_node character varying(100),
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    version_number integer DEFAULT 1 NOT NULL,
    priority integer DEFAULT 0,
    execution_count integer DEFAULT 0,
    success_count integer DEFAULT 0,
    failure_count integer DEFAULT 0,
    active_instance_count integer DEFAULT 0,
    avg_execution_time_ms integer DEFAULT 0,
    last_triggered_at timestamp without time zone,
    target_segment character varying(500),
    exclusion_segment character varying(500),
    max_concurrent_instances integer DEFAULT 1000 NOT NULL,
    cooldown_hours integer DEFAULT 0,
    created_by character varying(100),
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_workflow IS 'SCRM 营销自动化工作流定义表';
COMMENT ON COLUMN scrm.scrm_workflow.workflow_name IS '工作流名称';
COMMENT ON COLUMN scrm.scrm_workflow.workflow_code IS '工作流编码 (全局唯一)';
COMMENT ON COLUMN scrm.scrm_workflow.description IS '工作流描述 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow.workflow_type IS '工作流类型: MARKETING/ONBOARDING/RETENTION/RE_ENGAGEMENT/POST_PURCHASE/ABANDONED_CART/BIRTHDAY/ANNIVERSARY/CUSTOM (默认 MARKETING)';
COMMENT ON COLUMN scrm.scrm_workflow.trigger_type IS '触发器类型: EVENT/SCHEDULE/SEGMENT/WEBHOOK/MANUAL';
COMMENT ON COLUMN scrm.scrm_workflow.trigger_config IS '触发器配置 JSON: {event,schedule,cron,segmentId,webhookUrl}';
COMMENT ON COLUMN scrm.scrm_workflow.nodes IS '节点定义 JSON: [{id,type,name,config,next}]';
COMMENT ON COLUMN scrm.scrm_workflow.edges IS '连接定义 JSON: [{from,to,condition}] (可空)';
COMMENT ON COLUMN scrm.scrm_workflow.entry_node IS '入口节点 ID (可空)';
COMMENT ON COLUMN scrm.scrm_workflow.status IS '状态: DRAFT/ACTIVE/PAUSED/ARCHIVED (默认 DRAFT)';
COMMENT ON COLUMN scrm.scrm_workflow.version_number IS '工作流发布版本号 (默认 1)';
COMMENT ON COLUMN scrm.scrm_workflow.priority IS '优先级 (默认 0, 数值越大越优先)';
COMMENT ON COLUMN scrm.scrm_workflow.execution_count IS '累计执行次数';
COMMENT ON COLUMN scrm.scrm_workflow.success_count IS '累计成功次数';
COMMENT ON COLUMN scrm.scrm_workflow.failure_count IS '累计失败次数';
COMMENT ON COLUMN scrm.scrm_workflow.active_instance_count IS '活跃实例数';
COMMENT ON COLUMN scrm.scrm_workflow.avg_execution_time_ms IS '平均执行耗时 (毫秒)';
COMMENT ON COLUMN scrm.scrm_workflow.last_triggered_at IS '最近触发时间 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow.target_segment IS '目标客群 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow.exclusion_segment IS '排除客群 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow.max_concurrent_instances IS '最大并发实例数 (默认 1000)';
COMMENT ON COLUMN scrm.scrm_workflow.cooldown_hours IS '冷却时间 (小时, 默认 0)';
COMMENT ON COLUMN scrm.scrm_workflow.created_by IS '创建人 (可空)';
-- 工作流实例
CREATE TABLE scrm.scrm_workflow_instance (
    id bigint NOT NULL,
    workflow_id bigint NOT NULL,
    workflow_name character varying(200),
    customer_id bigint NOT NULL,
    customer_name character varying(200),
    trigger_type character varying(30) NOT NULL,
    trigger_event character varying(200),
    trigger_data text,
    current_node_id character varying(100),
    current_node_name character varying(200),
    current_node_type character varying(50),
    status character varying(20) DEFAULT 'RUNNING'::character varying NOT NULL,
    started_at timestamp without time zone NOT NULL,
    completed_at timestamp without time zone,
    duration_ms integer,
    execution_log text,
    variables text,
    error_message character varying(1000),
    retry_count integer DEFAULT 0,
    next_execution_at timestamp without time zone,
    priority integer DEFAULT 0,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_workflow_instance IS 'SCRM 营销自动化工作流执行实例表';
COMMENT ON COLUMN scrm.scrm_workflow_instance.workflow_id IS '工作流 ID';
COMMENT ON COLUMN scrm.scrm_workflow_instance.workflow_name IS '工作流名称 (触发时快照, 可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.customer_id IS '客户 ID';
COMMENT ON COLUMN scrm.scrm_workflow_instance.customer_name IS '客户名称 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.trigger_type IS '触发器类型: EVENT/SCHEDULE/SEGMENT/WEBHOOK/MANUAL';
COMMENT ON COLUMN scrm.scrm_workflow_instance.trigger_event IS '触发事件 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.trigger_data IS '触发数据 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.current_node_id IS '当前节点 ID (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.current_node_name IS '当前节点名称 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.current_node_type IS '当前节点类型 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.status IS '状态: RUNNING/PAUSED/COMPLETED/FAILED/CANCELLED/WAITING (默认 RUNNING)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.started_at IS '开始执行时间';
COMMENT ON COLUMN scrm.scrm_workflow_instance.completed_at IS '完成时间 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.duration_ms IS '执行耗时 (毫秒, 可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.execution_log IS '执行日志 JSON: [{nodeId,nodeName,action,status,timestamp,duration}] (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.variables IS '工作流变量 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.error_message IS '错误信息 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.retry_count IS '重试次数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.next_execution_at IS '下次执行时间 (可空, 用于延迟节点)';
COMMENT ON COLUMN scrm.scrm_workflow_instance.priority IS '优先级 (默认 0)';
-- 工作流节点日志
CREATE TABLE scrm.scrm_workflow_node_log (
    id bigint NOT NULL,
    instance_id bigint NOT NULL,
    workflow_id bigint NOT NULL,
    node_id character varying(100) NOT NULL,
    node_name character varying(200),
    node_type character varying(50) NOT NULL,
    action_type character varying(50),
    action_config text,
    input_variables text,
    output_result text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    duration_ms integer,
    error_message character varying(1000),
    condition_result character varying(20),
    retry_count integer DEFAULT 0,
    sequence integer DEFAULT 0 NOT NULL,
    create_time timestamp without time zone NOT NULL,
    update_time timestamp without time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
COMMENT ON TABLE scrm.scrm_workflow_node_log IS 'SCRM 营销自动化工作流节点执行日志表';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.instance_id IS '工作流实例 ID';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.workflow_id IS '工作流 ID';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.node_id IS '节点 ID';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.node_name IS '节点名称 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.node_type IS '节点类型: START/END/ACTION/CONDITION/DELAY/LOOP/SWITCH/PARALLEL/WAIT/SUB_WORKFLOW';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.action_type IS '动作类型 (可空): SEND_MESSAGE/SEND_EMAIL/SEND_SMS/ADD_TAG/REMOVE_TAG/UPDATE_FIELD/CREATE_TASK/NOTIFY/WEBHOOK/CALL_API/ADD_TO_SEGMENT/REMOVE_FROM_SEGMENT/ASSIGN_OWNER/CREATE_TICKET';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.action_config IS '动作配置 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.input_variables IS '输入变量 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.output_result IS '输出结果 JSON (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.status IS '状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/WAITING (默认 PENDING)';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.started_at IS '开始时间 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.completed_at IS '完成时间 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.duration_ms IS '执行耗时 (毫秒, 可空)';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.error_message IS '错误信息 (可空)';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.condition_result IS '条件结果 (可空): TRUE/FALSE';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.retry_count IS '重试次数 (默认 0)';
COMMENT ON COLUMN scrm.scrm_workflow_node_log.sequence IS '执行顺序 (默认 0, 同实例内递增)';
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202607 FOR VALUES FROM ('2026-07-01 00:00:00') TO ('2026-08-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202608 FOR VALUES FROM ('2026-08-01 00:00:00') TO ('2026-09-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202609 FOR VALUES FROM ('2026-09-01 00:00:00') TO ('2026-10-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202610 FOR VALUES FROM ('2026-10-01 00:00:00') TO ('2026-11-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202611 FOR VALUES FROM ('2026-11-01 00:00:00') TO ('2026-12-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202612 FOR VALUES FROM ('2026-12-01 00:00:00') TO ('2027-01-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202701 FOR VALUES FROM ('2027-01-01 00:00:00') TO ('2027-02-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202702 FOR VALUES FROM ('2027-02-01 00:00:00') TO ('2027-03-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202703 FOR VALUES FROM ('2027-03-01 00:00:00') TO ('2027-04-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202704 FOR VALUES FROM ('2027-04-01 00:00:00') TO ('2027-05-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202705 FOR VALUES FROM ('2027-05-01 00:00:00') TO ('2027-06-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_202706 FOR VALUES FROM ('2027-06-01 00:00:00') TO ('2027-07-01 00:00:00');
ALTER TABLE ONLY scrm.scrm_conversation_message ATTACH PARTITION scrm.scrm_conversation_message_default DEFAULT;
ALTER TABLE ONLY scrm.scrm_competitor
    ADD CONSTRAINT pk_scrm_competitor PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_competitor_activity
    ADD CONSTRAINT pk_scrm_competitor_activity PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_competitor_product
    ADD CONSTRAINT pk_scrm_competitor_product PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ab_test_assignment
    ADD CONSTRAINT scrm_ab_test_assignment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ab_test
    ADD CONSTRAINT scrm_ab_test_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ab_test_variant
    ADD CONSTRAINT scrm_ab_test_variant_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_account_health
    ADD CONSTRAINT scrm_account_health_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_account_login_log
    ADD CONSTRAINT scrm_account_login_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_account
    ADD CONSTRAINT scrm_account_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ai_assistant_config
    ADD CONSTRAINT scrm_ai_assistant_config_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ai_conversation
    ADD CONSTRAINT scrm_ai_conversation_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ai_intent
    ADD CONSTRAINT scrm_ai_intent_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ai_knowledge_base
    ADD CONSTRAINT scrm_ai_knowledge_base_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ai_knowledge_document
    ADD CONSTRAINT scrm_ai_knowledge_document_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_alert_event
    ADD CONSTRAINT scrm_alert_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_alert_rule
    ADD CONSTRAINT scrm_alert_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_api_access_log
    ADD CONSTRAINT scrm_api_access_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_api_app
    ADD CONSTRAINT scrm_api_app_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_api_key
    ADD CONSTRAINT scrm_api_key_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_api_scope
    ADD CONSTRAINT scrm_api_scope_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_approval_flow
    ADD CONSTRAINT scrm_approval_flow_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_approval_instance
    ADD CONSTRAINT scrm_approval_instance_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_approval_log
    ADD CONSTRAINT scrm_approval_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_archive_rule
    ADD CONSTRAINT scrm_archive_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_asset_category
    ADD CONSTRAINT scrm_asset_category_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_asset
    ADD CONSTRAINT scrm_asset_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_asset_usage
    ADD CONSTRAINT scrm_asset_usage_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_attribution_conversion
    ADD CONSTRAINT scrm_attribution_conversion_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_attribution_model
    ADD CONSTRAINT scrm_attribution_model_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_attribution_touchpoint
    ADD CONSTRAINT scrm_attribution_touchpoint_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_audit_log
    ADD CONSTRAINT scrm_audit_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_auto_reply_log
    ADD CONSTRAINT scrm_auto_reply_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_auto_reply_rule
    ADD CONSTRAINT scrm_auto_reply_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_auto_reply_template
    ADD CONSTRAINT scrm_auto_reply_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_auto_tag_rule_log
    ADD CONSTRAINT scrm_auto_tag_rule_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_auto_tag_rule
    ADD CONSTRAINT scrm_auto_tag_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_behavior_path
    ADD CONSTRAINT scrm_behavior_path_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_behavior_track
    ADD CONSTRAINT scrm_behavior_track_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_blacklist
    ADD CONSTRAINT scrm_blacklist_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_blacklist_rule
    ADD CONSTRAINT scrm_blacklist_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_budget_allocation
    ADD CONSTRAINT scrm_budget_allocation_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_budget_expense
    ADD CONSTRAINT scrm_budget_expense_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_budget_plan
    ADD CONSTRAINT scrm_budget_plan_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_budget_roi
    ADD CONSTRAINT scrm_budget_roi_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_calendar_conflict
    ADD CONSTRAINT scrm_calendar_conflict_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_calendar_event
    ADD CONSTRAINT scrm_calendar_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_calendar_holiday
    ADD CONSTRAINT scrm_calendar_holiday_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_campaign_account
    ADD CONSTRAINT scrm_campaign_account_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_campaign_analysis
    ADD CONSTRAINT scrm_campaign_analysis_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_campaign_channel
    ADD CONSTRAINT scrm_campaign_channel_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_campaign_effect
    ADD CONSTRAINT scrm_campaign_effect_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_campaign_execution_log
    ADD CONSTRAINT scrm_campaign_execution_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_campaign_funnel
    ADD CONSTRAINT scrm_campaign_funnel_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_campaign
    ADD CONSTRAINT scrm_campaign_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_campaign_template
    ADD CONSTRAINT scrm_campaign_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_care_record
    ADD CONSTRAINT scrm_care_record_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_care_rule
    ADD CONSTRAINT scrm_care_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_care_task
    ADD CONSTRAINT scrm_care_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_channel_code
    ADD CONSTRAINT scrm_channel_code_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_channel_code_scan
    ADD CONSTRAINT scrm_channel_code_scan_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_chat_archive
    ADD CONSTRAINT scrm_chat_archive_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_churn_recovery
    ADD CONSTRAINT scrm_churn_recovery_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_churn_rule
    ADD CONSTRAINT scrm_churn_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_churn_warning
    ADD CONSTRAINT scrm_churn_warning_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_commission_plan
    ADD CONSTRAINT scrm_commission_plan_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_commission_record
    ADD CONSTRAINT scrm_commission_record_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_commission_rule
    ADD CONSTRAINT scrm_commission_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_community_member
    ADD CONSTRAINT scrm_community_member_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_community_message
    ADD CONSTRAINT scrm_community_message_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_community
    ADD CONSTRAINT scrm_community_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_community_sop
    ADD CONSTRAINT scrm_community_sop_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_config_group
    ADD CONSTRAINT scrm_config_group_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_config_history
    ADD CONSTRAINT scrm_config_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_content_asset
    ADD CONSTRAINT scrm_content_asset_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_content_channel
    ADD CONSTRAINT scrm_content_channel_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_content
    ADD CONSTRAINT scrm_content_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_content_schedule
    ADD CONSTRAINT scrm_content_schedule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_contract_change
    ADD CONSTRAINT scrm_contract_change_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_contract_payment
    ADD CONSTRAINT scrm_contract_payment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_contract
    ADD CONSTRAINT scrm_contract_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_contract_reminder
    ADD CONSTRAINT scrm_contract_reminder_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_contract_template
    ADD CONSTRAINT scrm_contract_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_conversation_message
    ADD CONSTRAINT scrm_conversation_message_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202607
    ADD CONSTRAINT scrm_conversation_message_202607_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202608
    ADD CONSTRAINT scrm_conversation_message_202608_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202609
    ADD CONSTRAINT scrm_conversation_message_202609_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202610
    ADD CONSTRAINT scrm_conversation_message_202610_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202611
    ADD CONSTRAINT scrm_conversation_message_202611_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202612
    ADD CONSTRAINT scrm_conversation_message_202612_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202701
    ADD CONSTRAINT scrm_conversation_message_202701_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202702
    ADD CONSTRAINT scrm_conversation_message_202702_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202703
    ADD CONSTRAINT scrm_conversation_message_202703_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202704
    ADD CONSTRAINT scrm_conversation_message_202704_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202705
    ADD CONSTRAINT scrm_conversation_message_202705_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_202706
    ADD CONSTRAINT scrm_conversation_message_202706_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation_message_default
    ADD CONSTRAINT scrm_conversation_message_default_pkey PRIMARY KEY (id, sent_at);
ALTER TABLE ONLY scrm.scrm_conversation
    ADD CONSTRAINT scrm_conversation_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_coupon
    ADD CONSTRAINT scrm_coupon_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_coupon_template
    ADD CONSTRAINT scrm_coupon_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_coupon_usage_log
    ADD CONSTRAINT scrm_coupon_usage_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_duplicate
    ADD CONSTRAINT scrm_customer_duplicate_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_group_member
    ADD CONSTRAINT scrm_customer_group_member_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_group
    ADD CONSTRAINT scrm_customer_group_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_health_score
    ADD CONSTRAINT scrm_customer_health_score_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_identity
    ADD CONSTRAINT scrm_customer_identity_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_journey
    ADD CONSTRAINT scrm_customer_journey_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_level_history
    ADD CONSTRAINT scrm_customer_level_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_level
    ADD CONSTRAINT scrm_customer_level_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_level_rule
    ADD CONSTRAINT scrm_customer_level_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_lifecycle_history
    ADD CONSTRAINT scrm_customer_lifecycle_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_lifecycle
    ADD CONSTRAINT scrm_customer_lifecycle_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_ltv
    ADD CONSTRAINT scrm_customer_ltv_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_membership
    ADD CONSTRAINT scrm_customer_membership_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_merge_record
    ADD CONSTRAINT scrm_customer_merge_record_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer
    ADD CONSTRAINT scrm_customer_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_profile
    ADD CONSTRAINT scrm_customer_profile_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_tag_def
    ADD CONSTRAINT scrm_customer_tag_def_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_tag
    ADD CONSTRAINT scrm_customer_tag_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_tag_rule
    ADD CONSTRAINT scrm_customer_tag_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_customer_timeline
    ADD CONSTRAINT scrm_customer_timeline_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_data_dictionary_item
    ADD CONSTRAINT scrm_data_dictionary_item_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_data_dictionary
    ADD CONSTRAINT scrm_data_dictionary_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_data_dictionary_usage
    ADD CONSTRAINT scrm_data_dictionary_usage_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_data_transfer_log
    ADD CONSTRAINT scrm_data_transfer_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_engagement_event
    ADD CONSTRAINT scrm_engagement_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_engagement_level
    ADD CONSTRAINT scrm_engagement_level_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_engagement_rule
    ADD CONSTRAINT scrm_engagement_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_engagement_score
    ADD CONSTRAINT scrm_engagement_score_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_export_task
    ADD CONSTRAINT scrm_export_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_external_contact_mapping
    ADD CONSTRAINT scrm_external_contact_mapping_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_external_contact_sync_config
    ADD CONSTRAINT scrm_external_contact_sync_config_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_external_contact_sync_log
    ADD CONSTRAINT scrm_external_contact_sync_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_external_contact_sync_task
    ADD CONSTRAINT scrm_external_contact_sync_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_feedback_category
    ADD CONSTRAINT scrm_feedback_category_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_feedback_comment
    ADD CONSTRAINT scrm_feedback_comment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_feedback
    ADD CONSTRAINT scrm_feedback_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_festival
    ADD CONSTRAINT scrm_festival_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_follow_up_record
    ADD CONSTRAINT scrm_follow_up_record_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_follow_up_task
    ADD CONSTRAINT scrm_follow_up_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_follow_up_template
    ADD CONSTRAINT scrm_follow_up_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_forecast_model
    ADD CONSTRAINT scrm_forecast_model_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_forecast_result
    ADD CONSTRAINT scrm_forecast_result_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_forecast_scenario
    ADD CONSTRAINT scrm_forecast_scenario_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_funnel_analysis
    ADD CONSTRAINT scrm_funnel_analysis_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_funnel
    ADD CONSTRAINT scrm_funnel_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_funnel_stage
    ADD CONSTRAINT scrm_funnel_stage_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_health_alert
    ADD CONSTRAINT scrm_health_alert_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_health_score_model
    ADD CONSTRAINT scrm_health_score_model_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_identity_merge_history
    ADD CONSTRAINT scrm_identity_merge_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_identity_merge_rule
    ADD CONSTRAINT scrm_identity_merge_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_identity_merge_task
    ADD CONSTRAINT scrm_identity_merge_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_import_task
    ADD CONSTRAINT scrm_import_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_import_template
    ADD CONSTRAINT scrm_import_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_inheritance_item
    ADD CONSTRAINT scrm_inheritance_item_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_inheritance_task
    ADD CONSTRAINT scrm_inheritance_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_interaction_calendar
    ADD CONSTRAINT scrm_interaction_calendar_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_interaction_plan
    ADD CONSTRAINT scrm_interaction_plan_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_invoice
    ADD CONSTRAINT scrm_invoice_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_invoice_template
    ADD CONSTRAINT scrm_invoice_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_journey_enrollment
    ADD CONSTRAINT scrm_journey_enrollment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_journey_progress_log
    ADD CONSTRAINT scrm_journey_progress_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_journey_step
    ADD CONSTRAINT scrm_journey_step_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_knowledge_article
    ADD CONSTRAINT scrm_knowledge_article_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_knowledge_category
    ADD CONSTRAINT scrm_knowledge_category_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_knowledge_feedback
    ADD CONSTRAINT scrm_knowledge_feedback_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_lead_assignment
    ADD CONSTRAINT scrm_lead_assignment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_lead_dimension
    ADD CONSTRAINT scrm_lead_dimension_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_lead_score
    ADD CONSTRAINT scrm_lead_score_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_lead_scoring_model
    ADD CONSTRAINT scrm_lead_scoring_model_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_lifecycle_history
    ADD CONSTRAINT scrm_lifecycle_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_lifecycle_stage
    ADD CONSTRAINT scrm_lifecycle_stage_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_lifecycle_transition
    ADD CONSTRAINT scrm_lifecycle_transition_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ltv_cohort
    ADD CONSTRAINT scrm_ltv_cohort_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ltv_model
    ADD CONSTRAINT scrm_ltv_model_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_marketing_campaign_channel
    ADD CONSTRAINT scrm_marketing_campaign_channel_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_marketing_campaign_participant
    ADD CONSTRAINT scrm_marketing_campaign_participant_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_marketing_campaign
    ADD CONSTRAINT scrm_marketing_campaign_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_marketing_trigger_event
    ADD CONSTRAINT scrm_marketing_trigger_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_marketing_trigger
    ADD CONSTRAINT scrm_marketing_trigger_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_mass_send_target
    ADD CONSTRAINT scrm_mass_send_target_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_mass_send_task
    ADD CONSTRAINT scrm_mass_send_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_material
    ADD CONSTRAINT scrm_material_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_membership_benefit
    ADD CONSTRAINT scrm_membership_benefit_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_membership_tier
    ADD CONSTRAINT scrm_membership_tier_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_message_read_log
    ADD CONSTRAINT scrm_message_read_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_message_recall
    ADD CONSTRAINT scrm_message_recall_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_message_template_center
    ADD CONSTRAINT scrm_message_template_center_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_message_template_group
    ADD CONSTRAINT scrm_message_template_group_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_message_template
    ADD CONSTRAINT scrm_message_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_message_template_version
    ADD CONSTRAINT scrm_message_template_version_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_message_tracking
    ADD CONSTRAINT scrm_message_tracking_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_monitor_metric
    ADD CONSTRAINT scrm_monitor_metric_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_notification_batch
    ADD CONSTRAINT scrm_notification_batch_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_notification
    ADD CONSTRAINT scrm_notification_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_notification_preference
    ADD CONSTRAINT scrm_notification_preference_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_notification_template
    ADD CONSTRAINT scrm_notification_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_nps_benchmark
    ADD CONSTRAINT scrm_nps_benchmark_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_opportunity
    ADD CONSTRAINT scrm_opportunity_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_opportunity_stage_history
    ADD CONSTRAINT scrm_opportunity_stage_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_order_item
    ADD CONSTRAINT scrm_order_item_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_order
    ADD CONSTRAINT scrm_order_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_persona
    ADD CONSTRAINT scrm_persona_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_platform_config
    ADD CONSTRAINT scrm_platform_config_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_points_account
    ADD CONSTRAINT scrm_points_account_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_points_exchange
    ADD CONSTRAINT scrm_points_exchange_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_points_exchange_record
    ADD CONSTRAINT scrm_points_exchange_record_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_points_rule
    ADD CONSTRAINT scrm_points_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_points_transaction
    ADD CONSTRAINT scrm_points_transaction_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_product
    ADD CONSTRAINT scrm_product_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_profile_comparison
    ADD CONSTRAINT scrm_profile_comparison_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_profile_template
    ADD CONSTRAINT scrm_profile_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_public_sea_customer
    ADD CONSTRAINT scrm_public_sea_customer_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_quality_inspection_result
    ADD CONSTRAINT scrm_quality_inspection_result_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_quality_inspection_rule
    ADD CONSTRAINT scrm_quality_inspection_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_quality_inspection_task
    ADD CONSTRAINT scrm_quality_inspection_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_quick_reply_category
    ADD CONSTRAINT scrm_quick_reply_category_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_quick_reply
    ADD CONSTRAINT scrm_quick_reply_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_referral
    ADD CONSTRAINT scrm_referral_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_referral_program
    ADD CONSTRAINT scrm_referral_program_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_referral_reward
    ADD CONSTRAINT scrm_referral_reward_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_report_result
    ADD CONSTRAINT scrm_report_result_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_report_template
    ADD CONSTRAINT scrm_report_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_rfm_analysis
    ADD CONSTRAINT scrm_rfm_analysis_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_rfm_config
    ADD CONSTRAINT scrm_rfm_config_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_rfm_segment_strategy
    ADD CONSTRAINT scrm_rfm_segment_strategy_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_risk_event
    ADD CONSTRAINT scrm_risk_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_risk_rule
    ADD CONSTRAINT scrm_risk_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_risk_signal
    ADD CONSTRAINT scrm_risk_signal_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_sales_achievement
    ADD CONSTRAINT scrm_sales_achievement_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_sales_ranking
    ADD CONSTRAINT scrm_sales_ranking_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_sales_speech
    ADD CONSTRAINT scrm_sales_speech_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_sales_target
    ADD CONSTRAINT scrm_sales_target_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_scheduled_task
    ADD CONSTRAINT scrm_scheduled_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_segment_history
    ADD CONSTRAINT scrm_segment_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_segment_member
    ADD CONSTRAINT scrm_segment_member_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_segment
    ADD CONSTRAINT scrm_segment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_speech_category
    ADD CONSTRAINT scrm_speech_category_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_speech
    ADD CONSTRAINT scrm_speech_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_speech_recommendation
    ADD CONSTRAINT scrm_speech_recommendation_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_speech_scenario
    ADD CONSTRAINT scrm_speech_scenario_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_survey_invitation
    ADD CONSTRAINT scrm_survey_invitation_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_survey
    ADD CONSTRAINT scrm_survey_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_survey_response
    ADD CONSTRAINT scrm_survey_response_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_system_config
    ADD CONSTRAINT scrm_system_config_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_tag_customer
    ADD CONSTRAINT scrm_tag_customer_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_tag_group
    ADD CONSTRAINT scrm_tag_group_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_tag
    ADD CONSTRAINT scrm_tag_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_tag_rule
    ADD CONSTRAINT scrm_tag_rule_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_task_dependency
    ADD CONSTRAINT scrm_task_dependency_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_task_execution
    ADD CONSTRAINT scrm_task_execution_execution_no_key UNIQUE (execution_no);
ALTER TABLE ONLY scrm.scrm_task_execution
    ADD CONSTRAINT scrm_task_execution_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ticket_comment
    ADD CONSTRAINT scrm_ticket_comment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ticket_history
    ADD CONSTRAINT scrm_ticket_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_ticket
    ADD CONSTRAINT scrm_ticket_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_touchpoint
    ADD CONSTRAINT scrm_touchpoint_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_user
    ADD CONSTRAINT scrm_user_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_user_account
    ADD CONSTRAINT scrm_user_account_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_user_device
    ADD CONSTRAINT scrm_user_device_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_visit_plan
    ADD CONSTRAINT scrm_visit_plan_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_visit_task
    ADD CONSTRAINT scrm_visit_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_visit_template
    ADD CONSTRAINT scrm_visit_template_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_voc_insight
    ADD CONSTRAINT scrm_voc_insight_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_voc_topic
    ADD CONSTRAINT scrm_voc_topic_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_voc_voice
    ADD CONSTRAINT scrm_voc_voice_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_webhook_config
    ADD CONSTRAINT scrm_webhook_config_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_webhook_log
    ADD CONSTRAINT scrm_webhook_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_welcome_message
    ADD CONSTRAINT scrm_welcome_message_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_wework_archive_config
    ADD CONSTRAINT scrm_wework_archive_config_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_wework_archive_cursor
    ADD CONSTRAINT scrm_wework_archive_cursor_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_wework_archive_message
    ADD CONSTRAINT scrm_wework_archive_message_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_work_order_log
    ADD CONSTRAINT scrm_work_order_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_work_order
    ADD CONSTRAINT scrm_work_order_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_work_order_sla
    ADD CONSTRAINT scrm_work_order_sla_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_workflow_instance
    ADD CONSTRAINT scrm_workflow_instance_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_workflow_node_log
    ADD CONSTRAINT scrm_workflow_node_log_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_workflow
    ADD CONSTRAINT scrm_workflow_pkey PRIMARY KEY (id);
ALTER TABLE ONLY scrm.scrm_engagement_score
    ADD CONSTRAINT uk_engagement_score_customer UNIQUE (customer_id);
ALTER TABLE ONLY scrm.scrm_customer_group_member
    ADD CONSTRAINT uk_group_member_group_customer UNIQUE (group_id, customer_id);
ALTER TABLE ONLY scrm.scrm_persona
    ADD CONSTRAINT uk_persona_persona_id UNIQUE (persona_id);
ALTER TABLE ONLY scrm.scrm_platform_config
    ADD CONSTRAINT uk_platconfig_platform UNIQUE (platform_type);
ALTER TABLE ONLY scrm.scrm_points_account
    ADD CONSTRAINT uk_points_account_customer UNIQUE (customer_id);
ALTER TABLE ONLY scrm.scrm_points_exchange_record
    ADD CONSTRAINT uk_points_ex_record_code UNIQUE (exchange_code);
ALTER TABLE ONLY scrm.scrm_scheduled_task
    ADD CONSTRAINT uk_scheduled_task_code UNIQUE (task_code);
CREATE INDEX idx_ab_assign_converted ON scrm.scrm_ab_test_assignment USING btree (converted);
CREATE INDEX idx_ab_assign_customer ON scrm.scrm_ab_test_assignment USING btree (customer_id);

CREATE INDEX idx_ab_assign_test ON scrm.scrm_ab_test_assignment USING btree (test_id);
CREATE UNIQUE INDEX idx_ab_assign_unique ON scrm.scrm_ab_test_assignment USING btree (test_id, customer_id);
CREATE INDEX idx_ab_assign_variant ON scrm.scrm_ab_test_assignment USING btree (variant_id);
CREATE INDEX idx_ab_test_analyzed ON scrm.scrm_ab_test USING btree (last_analyzed_at);
CREATE INDEX idx_ab_test_campaign ON scrm.scrm_ab_test USING btree (campaign_id);
CREATE UNIQUE INDEX idx_ab_test_code ON scrm.scrm_ab_test USING btree (test_code);
CREATE INDEX idx_ab_test_metric ON scrm.scrm_ab_test USING btree (metric);
CREATE INDEX idx_ab_test_objective ON scrm.scrm_ab_test USING btree (test_objective);
CREATE INDEX idx_ab_test_status ON scrm.scrm_ab_test USING btree (status);

CREATE INDEX idx_ab_test_type ON scrm.scrm_ab_test USING btree (test_type);
CREATE INDEX idx_ab_variant_control ON scrm.scrm_ab_test_variant USING btree (is_control);

CREATE INDEX idx_ab_variant_test ON scrm.scrm_ab_test_variant USING btree (test_id);
CREATE INDEX idx_ab_variant_winner ON scrm.scrm_ab_test_variant USING btree (is_winner);
CREATE INDEX idx_account_device_id ON scrm.scrm_account USING btree (device_id);
CREATE INDEX idx_account_persona_id ON scrm.scrm_account USING btree (persona_id);
CREATE INDEX idx_account_platform_uid ON scrm.scrm_account USING btree (platform_type, platform_account_uid);

CREATE INDEX idx_ai_config_default ON scrm.scrm_ai_assistant_config USING btree (is_default);
CREATE INDEX idx_ai_config_enabled ON scrm.scrm_ai_assistant_config USING btree (enabled);
CREATE INDEX idx_ai_config_provider ON scrm.scrm_ai_assistant_config USING btree (provider);

CREATE INDEX idx_ai_conversation_created ON scrm.scrm_ai_conversation USING btree (created_at);
CREATE INDEX idx_ai_conversation_customer ON scrm.scrm_ai_conversation USING btree (customer_id);
CREATE INDEX idx_ai_conversation_intent ON scrm.scrm_ai_conversation USING btree (detected_intent);
CREATE INDEX idx_ai_conversation_sentiment ON scrm.scrm_ai_conversation USING btree (sentiment);
CREATE INDEX idx_ai_conversation_session ON scrm.scrm_ai_conversation USING btree (conversation_id);

CREATE INDEX idx_ai_doc_enabled ON scrm.scrm_ai_knowledge_document USING btree (enabled);
CREATE INDEX idx_ai_doc_kb ON scrm.scrm_ai_knowledge_document USING btree (knowledge_base_id);
CREATE INDEX idx_ai_doc_source ON scrm.scrm_ai_knowledge_document USING btree (source_type);

CREATE INDEX idx_ai_intent_category ON scrm.scrm_ai_intent USING btree (intent_category);
CREATE INDEX idx_ai_intent_enabled ON scrm.scrm_ai_intent USING btree (enabled);
CREATE INDEX idx_ai_intent_priority ON scrm.scrm_ai_intent USING btree (priority);

CREATE INDEX idx_ai_kb_category ON scrm.scrm_ai_knowledge_base USING btree (category);
CREATE INDEX idx_ai_kb_enabled ON scrm.scrm_ai_knowledge_base USING btree (enabled);

CREATE INDEX idx_alert_event_metric ON scrm.scrm_alert_event USING btree (metric_id);
CREATE UNIQUE INDEX idx_alert_event_no ON scrm.scrm_alert_event USING btree (event_no);
CREATE INDEX idx_alert_event_rule ON scrm.scrm_alert_event USING btree (rule_id);
CREATE INDEX idx_alert_event_severity ON scrm.scrm_alert_event USING btree (severity);
CREATE INDEX idx_alert_event_status ON scrm.scrm_alert_event USING btree (status);

CREATE INDEX idx_alert_event_trigger_time ON scrm.scrm_alert_event USING btree (trigger_time);
CREATE UNIQUE INDEX idx_alert_rule_code ON scrm.scrm_alert_rule USING btree (rule_code);
CREATE INDEX idx_alert_rule_enabled ON scrm.scrm_alert_rule USING btree (enabled);
CREATE INDEX idx_alert_rule_metric ON scrm.scrm_alert_rule USING btree (metric_id);
CREATE INDEX idx_alert_rule_severity ON scrm.scrm_alert_rule USING btree (severity);

CREATE INDEX idx_api_access_log_api_key ON scrm.scrm_api_access_log USING btree (api_key_id);
CREATE INDEX idx_api_access_log_app ON scrm.scrm_api_access_log USING btree (app_id);
CREATE INDEX idx_api_access_log_at ON scrm.scrm_api_access_log USING btree (accessed_at);
CREATE INDEX idx_api_access_log_client ON scrm.scrm_api_access_log USING btree (client_id);
CREATE INDEX idx_api_access_log_endpoint ON scrm.scrm_api_access_log USING btree (endpoint);
CREATE INDEX idx_api_access_log_method ON scrm.scrm_api_access_log USING btree (method);
CREATE INDEX idx_api_access_log_status ON scrm.scrm_api_access_log USING btree (response_status);

CREATE INDEX idx_api_app_client ON scrm.scrm_api_app USING btree (client_id);
CREATE INDEX idx_api_app_status ON scrm.scrm_api_app USING btree (status);

CREATE INDEX idx_api_app_type ON scrm.scrm_api_app USING btree (app_type);
CREATE INDEX idx_api_key_app ON scrm.scrm_api_key USING btree (app_id);
CREATE INDEX idx_api_key_key ON scrm.scrm_api_key USING btree (api_key);
CREATE INDEX idx_api_key_status ON scrm.scrm_api_key USING btree (status);

CREATE INDEX idx_api_scope_default ON scrm.scrm_api_scope USING btree (is_default);
CREATE INDEX idx_api_scope_enabled ON scrm.scrm_api_scope USING btree (enabled);
CREATE INDEX idx_api_scope_resource ON scrm.scrm_api_scope USING btree (resource);

CREATE UNIQUE INDEX idx_approval_flow_code ON scrm.scrm_approval_flow USING btree (flow_code);
CREATE INDEX idx_approval_flow_default ON scrm.scrm_approval_flow USING btree (is_default);
CREATE INDEX idx_approval_flow_status ON scrm.scrm_approval_flow USING btree (status);

CREATE INDEX idx_approval_flow_type ON scrm.scrm_approval_flow USING btree (flow_type);
CREATE INDEX idx_approval_instance_applicant ON scrm.scrm_approval_instance USING btree (applicant_id);
CREATE INDEX idx_approval_instance_business ON scrm.scrm_approval_instance USING btree (business_type, business_id);
CREATE INDEX idx_approval_instance_flow ON scrm.scrm_approval_instance USING btree (flow_id);
CREATE UNIQUE INDEX idx_approval_instance_no ON scrm.scrm_approval_instance USING btree (instance_no);
CREATE INDEX idx_approval_instance_started ON scrm.scrm_approval_instance USING btree (started_at);
CREATE INDEX idx_approval_instance_status ON scrm.scrm_approval_instance USING btree (status);

CREATE INDEX idx_approval_log_acted ON scrm.scrm_approval_log USING btree (acted_at);
CREATE INDEX idx_approval_log_action ON scrm.scrm_approval_log USING btree (action_type);
CREATE INDEX idx_approval_log_instance ON scrm.scrm_approval_log USING btree (instance_id);
CREATE INDEX idx_approval_log_operator ON scrm.scrm_approval_log USING btree (operator_id);

CREATE INDEX idx_archive_rule_enabled ON scrm.scrm_archive_rule USING btree (enabled);

CREATE INDEX idx_asset_category ON scrm.scrm_asset USING btree (category_id);
CREATE INDEX idx_asset_category_code ON scrm.scrm_asset_category USING btree (category_code);
CREATE INDEX idx_asset_category_enabled ON scrm.scrm_asset_category USING btree (enabled);
CREATE INDEX idx_asset_category_parent ON scrm.scrm_asset_category USING btree (parent_id);

CREATE INDEX idx_asset_expiry ON scrm.scrm_asset USING btree (expiry_date);
CREATE INDEX idx_asset_review_status ON scrm.scrm_asset USING btree (review_status);
CREATE INDEX idx_asset_status ON scrm.scrm_asset USING btree (status);
CREATE INDEX idx_asset_storage_type ON scrm.scrm_asset USING btree (storage_type);

CREATE INDEX idx_asset_type ON scrm.scrm_asset USING btree (asset_type);
CREATE INDEX idx_asset_uploaded_at ON scrm.scrm_asset USING btree (uploaded_at);
CREATE INDEX idx_asset_usage_asset ON scrm.scrm_asset_usage USING btree (asset_id);
CREATE INDEX idx_asset_usage_module ON scrm.scrm_asset_usage USING btree (usage_module);

CREATE INDEX idx_asset_usage_type ON scrm.scrm_asset_usage USING btree (usage_type);
CREATE INDEX idx_asset_usage_used_at ON scrm.scrm_asset_usage USING btree (used_at);
CREATE INDEX idx_asset_usage_user ON scrm.scrm_asset_usage USING btree (user_id);
CREATE INDEX idx_attribution_conversion_attributed ON scrm.scrm_attribution_conversion USING btree (attributed_at);
CREATE INDEX idx_attribution_conversion_customer ON scrm.scrm_attribution_conversion USING btree (customer_id, conversion_time DESC);
CREATE INDEX idx_attribution_conversion_model ON scrm.scrm_attribution_conversion USING btree (model_id);
CREATE INDEX idx_attribution_conversion_order ON scrm.scrm_attribution_conversion USING btree (order_id);

CREATE INDEX idx_attribution_conversion_time ON scrm.scrm_attribution_conversion USING btree (conversion_time);
CREATE INDEX idx_attribution_conversion_type ON scrm.scrm_attribution_conversion USING btree (conversion_type);
CREATE UNIQUE INDEX idx_attribution_model_code ON scrm.scrm_attribution_model USING btree (model_code);
CREATE INDEX idx_attribution_model_default ON scrm.scrm_attribution_model USING btree (is_default);
CREATE INDEX idx_attribution_model_published ON scrm.scrm_attribution_model USING btree (is_published);

CREATE INDEX idx_attribution_model_type ON scrm.scrm_attribution_model USING btree (model_type);
CREATE INDEX idx_attribution_touchpoint_attributed ON scrm.scrm_attribution_touchpoint USING btree (is_attributed);
CREATE INDEX idx_attribution_touchpoint_campaign ON scrm.scrm_attribution_touchpoint USING btree (campaign_id);
CREATE INDEX idx_attribution_touchpoint_channel ON scrm.scrm_attribution_touchpoint USING btree (channel);
CREATE INDEX idx_attribution_touchpoint_customer ON scrm.scrm_attribution_touchpoint USING btree (customer_id, touchpoint_time DESC);
CREATE INDEX idx_attribution_touchpoint_session ON scrm.scrm_attribution_touchpoint USING btree (session_id);

CREATE INDEX idx_attribution_touchpoint_time ON scrm.scrm_attribution_touchpoint USING btree (touchpoint_time);
CREATE INDEX idx_attribution_touchpoint_type ON scrm.scrm_attribution_touchpoint USING btree (touchpoint_type);
CREATE INDEX idx_audit_operated_at ON scrm.scrm_audit_log USING btree (operated_at);
CREATE INDEX idx_audit_resource ON scrm.scrm_audit_log USING btree (resource);
CREATE INDEX idx_audit_result ON scrm.scrm_audit_log USING btree (result);

CREATE INDEX idx_audit_user ON scrm.scrm_audit_log USING btree (user_id);
CREATE INDEX idx_auto_reply_log_customer ON scrm.scrm_auto_reply_log USING btree (customer_id);
CREATE INDEX idx_auto_reply_log_rule ON scrm.scrm_auto_reply_log USING btree (rule_id);
CREATE INDEX idx_auto_reply_log_sent ON scrm.scrm_auto_reply_log USING btree (sent_at);
CREATE INDEX idx_auto_reply_log_status ON scrm.scrm_auto_reply_log USING btree (status);

CREATE INDEX idx_auto_reply_rule_fallback ON scrm.scrm_auto_reply_rule USING btree (fallback_rule);
CREATE INDEX idx_auto_reply_rule_priority ON scrm.scrm_auto_reply_rule USING btree (priority);

CREATE INDEX idx_auto_reply_rule_enabled ON scrm.scrm_auto_reply_rule USING btree (enabled);
CREATE INDEX idx_auto_reply_rule_type ON scrm.scrm_auto_reply_rule USING btree (rule_type);
CREATE INDEX idx_auto_reply_template_category ON scrm.scrm_auto_reply_template USING btree (category);
CREATE INDEX idx_auto_reply_template_enabled ON scrm.scrm_auto_reply_template USING btree (enabled);

CREATE INDEX idx_auto_reply_template_type ON scrm.scrm_auto_reply_template USING btree (template_type);
CREATE INDEX idx_auto_tag_rule_enabled ON scrm.scrm_auto_tag_rule USING btree (enabled);
CREATE INDEX idx_auto_tag_rule_log_customer ON scrm.scrm_auto_tag_rule_log USING btree (customer_id);
CREATE INDEX idx_auto_tag_rule_log_executed ON scrm.scrm_auto_tag_rule_log USING btree (executed_at);
CREATE INDEX idx_auto_tag_rule_log_rule ON scrm.scrm_auto_tag_rule_log USING btree (rule_id);

CREATE INDEX idx_auto_tag_rule_priority ON scrm.scrm_auto_tag_rule USING btree (priority);

CREATE INDEX idx_auto_tag_rule_event ON scrm.scrm_auto_tag_rule USING btree (trigger_event);
CREATE INDEX idx_behavior_path_conversion ON scrm.scrm_behavior_path USING btree (has_conversion);
CREATE INDEX idx_behavior_path_customer ON scrm.scrm_behavior_path USING btree (customer_id, session_start_time DESC);
CREATE INDEX idx_behavior_path_session ON scrm.scrm_behavior_path USING btree (session_id);

CREATE INDEX idx_behavior_path_time ON scrm.scrm_behavior_path USING btree (session_start_time);
CREATE INDEX idx_behavior_track_conversion ON scrm.scrm_behavior_track USING btree (is_conversion);
CREATE INDEX idx_behavior_track_customer ON scrm.scrm_behavior_track USING btree (customer_id, behavior_time DESC);
CREATE INDEX idx_behavior_track_funnel ON scrm.scrm_behavior_track USING btree (funnel_stage);
CREATE INDEX idx_behavior_track_session ON scrm.scrm_behavior_track USING btree (session_id);

CREATE INDEX idx_behavior_track_time ON scrm.scrm_behavior_track USING btree (behavior_time);
CREATE INDEX idx_behavior_track_touchpoint ON scrm.scrm_behavior_track USING btree (touchpoint);
CREATE INDEX idx_behavior_track_type ON scrm.scrm_behavior_track USING btree (behavior_type);
CREATE INDEX idx_blacklist_customer ON scrm.scrm_blacklist USING btree (customer_id);
CREATE INDEX idx_blacklist_expiry ON scrm.scrm_blacklist USING btree (expiry_date);
CREATE INDEX idx_blacklist_list_type ON scrm.scrm_blacklist USING btree (list_type);
CREATE INDEX idx_blacklist_risk_level ON scrm.scrm_blacklist USING btree (risk_level);
CREATE INDEX idx_blacklist_rule_category ON scrm.scrm_blacklist_rule USING btree (risk_category);
CREATE INDEX idx_blacklist_rule_enabled ON scrm.scrm_blacklist_rule USING btree (enabled);
CREATE INDEX idx_blacklist_rule_severity ON scrm.scrm_blacklist_rule USING btree (severity);

CREATE INDEX idx_blacklist_rule_type ON scrm.scrm_blacklist_rule USING btree (rule_type);
CREATE INDEX idx_blacklist_status ON scrm.scrm_blacklist USING btree (status);
CREATE INDEX idx_blacklist_target ON scrm.scrm_blacklist USING btree (target_type, target_value);

CREATE INDEX idx_budget_allocation_period ON scrm.scrm_budget_allocation USING btree (period_start, period_end);
CREATE INDEX idx_budget_allocation_plan ON scrm.scrm_budget_allocation USING btree (plan_id);
CREATE INDEX idx_budget_allocation_status ON scrm.scrm_budget_allocation USING btree (status);
CREATE INDEX idx_budget_allocation_target ON scrm.scrm_budget_allocation USING btree (target_type);

CREATE INDEX idx_budget_allocation_type ON scrm.scrm_budget_allocation USING btree (allocation_type);
CREATE INDEX idx_budget_expense_allocation ON scrm.scrm_budget_expense USING btree (allocation_id);
CREATE INDEX idx_budget_expense_campaign ON scrm.scrm_budget_expense USING btree (campaign_id);
CREATE INDEX idx_budget_expense_date ON scrm.scrm_budget_expense USING btree (expense_date);
CREATE UNIQUE INDEX idx_budget_expense_no ON scrm.scrm_budget_expense USING btree (expense_no);
CREATE INDEX idx_budget_expense_plan ON scrm.scrm_budget_expense USING btree (plan_id);
CREATE INDEX idx_budget_expense_status ON scrm.scrm_budget_expense USING btree (status);

CREATE INDEX idx_budget_expense_type ON scrm.scrm_budget_expense USING btree (expense_type);
CREATE UNIQUE INDEX idx_budget_plan_code ON scrm.scrm_budget_plan USING btree (plan_code);
CREATE INDEX idx_budget_plan_fiscal ON scrm.scrm_budget_plan USING btree (fiscal_year, fiscal_period);
CREATE INDEX idx_budget_plan_period ON scrm.scrm_budget_plan USING btree (period_start, period_end);
CREATE INDEX idx_budget_plan_status ON scrm.scrm_budget_plan USING btree (status);

CREATE INDEX idx_budget_plan_type ON scrm.scrm_budget_plan USING btree (budget_type);
CREATE INDEX idx_budget_roi_allocation ON scrm.scrm_budget_roi USING btree (allocation_id);
CREATE INDEX idx_budget_roi_calculated ON scrm.scrm_budget_roi USING btree (calculated_at);
CREATE INDEX idx_budget_roi_campaign ON scrm.scrm_budget_roi USING btree (campaign_id);
CREATE INDEX idx_budget_roi_period ON scrm.scrm_budget_roi USING btree (period);
CREATE INDEX idx_budget_roi_plan ON scrm.scrm_budget_roi USING btree (plan_id);

CREATE INDEX idx_calendar_conflict_detected ON scrm.scrm_calendar_conflict USING btree (detected_at);
CREATE INDEX idx_calendar_conflict_event1 ON scrm.scrm_calendar_conflict USING btree (event1_id);
CREATE INDEX idx_calendar_conflict_event2 ON scrm.scrm_calendar_conflict USING btree (event2_id);
CREATE INDEX idx_calendar_conflict_severity ON scrm.scrm_calendar_conflict USING btree (severity);
CREATE INDEX idx_calendar_conflict_status ON scrm.scrm_calendar_conflict USING btree (resolved_status);

CREATE INDEX idx_calendar_event_date_range ON scrm.scrm_calendar_event USING btree (start_date, end_date);
CREATE INDEX idx_calendar_event_owner ON scrm.scrm_calendar_event USING btree (owner_id);
CREATE INDEX idx_calendar_event_status ON scrm.scrm_calendar_event USING btree (status);

CREATE INDEX idx_calendar_event_type ON scrm.scrm_calendar_event USING btree (event_type);
CREATE INDEX idx_calendar_holiday_active ON scrm.scrm_calendar_holiday USING btree (is_active);
CREATE INDEX idx_calendar_holiday_date ON scrm.scrm_calendar_holiday USING btree (holiday_date);

CREATE INDEX idx_calendar_holiday_type ON scrm.scrm_calendar_holiday USING btree (holiday_type);
CREATE INDEX idx_campaign_account_account ON scrm.scrm_campaign_account USING btree (account_id);
CREATE INDEX idx_campaign_account_campaign ON scrm.scrm_campaign_account USING btree (campaign_id);
CREATE INDEX idx_campaign_analysis_approved ON scrm.scrm_campaign_analysis USING btree (is_approved);
CREATE INDEX idx_campaign_analysis_campaign ON scrm.scrm_campaign_analysis USING btree (campaign_id);
CREATE INDEX idx_campaign_analysis_period ON scrm.scrm_campaign_analysis USING btree (start_date, end_date);
CREATE INDEX idx_campaign_analysis_status ON scrm.scrm_campaign_analysis USING btree (status);

CREATE INDEX idx_campaign_analysis_type ON scrm.scrm_campaign_analysis USING btree (campaign_type);
CREATE INDEX idx_campaign_behavior_flow_id ON scrm.scrm_campaign USING btree (behavior_flow_id);
CREATE INDEX idx_campaign_channel_analysis ON scrm.scrm_campaign_channel USING btree (analysis_id);
CREATE INDEX idx_campaign_channel_best ON scrm.scrm_campaign_channel USING btree (is_best_performer, is_underperforming);
CREATE INDEX idx_campaign_channel_campaign ON scrm.scrm_campaign_channel USING btree (campaign_id);

CREATE INDEX idx_campaign_channel_type ON scrm.scrm_campaign_channel USING btree (channel_type);
CREATE INDEX idx_campaign_effect_calc ON scrm.scrm_campaign_effect USING btree (calculation_status);
CREATE INDEX idx_campaign_effect_campaign ON scrm.scrm_campaign_effect USING btree (campaign_id);
CREATE INDEX idx_campaign_effect_objective ON scrm.scrm_campaign_effect USING btree (objective);
CREATE INDEX idx_campaign_effect_period ON scrm.scrm_campaign_effect USING btree (start_date, end_date);
CREATE INDEX idx_campaign_effect_status ON scrm.scrm_campaign_effect USING btree (status);

CREATE INDEX idx_campaign_effect_type ON scrm.scrm_campaign_effect USING btree (campaign_type);
CREATE INDEX idx_campaign_funnel_analysis ON scrm.scrm_campaign_funnel USING btree (analysis_id);
CREATE INDEX idx_campaign_funnel_bottleneck ON scrm.scrm_campaign_funnel USING btree (is_bottleneck);
CREATE INDEX idx_campaign_funnel_campaign ON scrm.scrm_campaign_funnel USING btree (campaign_id);

CREATE INDEX idx_campaign_funnel_type ON scrm.scrm_campaign_funnel USING btree (funnel_type);
CREATE INDEX idx_campaign_platform_type ON scrm.scrm_campaign USING btree (platform_type);
CREATE INDEX idx_campaign_status ON scrm.scrm_campaign USING btree (status);
CREATE INDEX idx_campaign_template_platform ON scrm.scrm_campaign_template USING btree (platform_type);
CREATE INDEX idx_campaign_template_type ON scrm.scrm_campaign_template USING btree (campaign_type);

CREATE INDEX idx_care_record_assignee ON scrm.scrm_care_record USING btree (assignee_id);
CREATE INDEX idx_care_record_care_type ON scrm.scrm_care_record USING btree (care_type);
CREATE INDEX idx_care_record_customer ON scrm.scrm_care_record USING btree (customer_id);
CREATE INDEX idx_care_record_executed ON scrm.scrm_care_record USING btree (executed_at);
CREATE INDEX idx_care_record_result ON scrm.scrm_care_record USING btree (care_result);

CREATE INDEX idx_care_rule_care_type ON scrm.scrm_care_rule USING btree (care_type);
CREATE INDEX idx_care_rule_enabled ON scrm.scrm_care_rule USING btree (enabled);
CREATE INDEX idx_care_rule_priority ON scrm.scrm_care_rule USING btree (priority);

CREATE INDEX idx_care_task_assignee ON scrm.scrm_care_task USING btree (assignee_id);
CREATE INDEX idx_care_task_care_date ON scrm.scrm_care_task USING btree (care_date);
CREATE INDEX idx_care_task_care_type ON scrm.scrm_care_task USING btree (care_type);
CREATE INDEX idx_care_task_customer ON scrm.scrm_care_task USING btree (customer_id);
CREATE INDEX idx_care_task_rule ON scrm.scrm_care_task USING btree (rule_id);
CREATE INDEX idx_care_task_scheduled ON scrm.scrm_care_task USING btree (scheduled_at);
CREATE INDEX idx_care_task_status ON scrm.scrm_care_task USING btree (status);

CREATE INDEX idx_channel_code_platform ON scrm.scrm_channel_code USING btree (platform_type);
CREATE INDEX idx_channel_code_scan_added ON scrm.scrm_channel_code_scan USING btree (added);
CREATE INDEX idx_channel_code_scan_code ON scrm.scrm_channel_code_scan USING btree (channel_code_id);
CREATE INDEX idx_channel_code_scan_scanner ON scrm.scrm_channel_code_scan USING btree (scanner_uid);

CREATE INDEX idx_channel_code_status ON scrm.scrm_channel_code USING btree (status);

CREATE INDEX idx_chat_archive_account ON scrm.scrm_chat_archive USING btree (account_id);
CREATE INDEX idx_chat_archive_archived_at ON scrm.scrm_chat_archive USING btree (archived_at);
CREATE INDEX idx_chat_archive_conversation ON scrm.scrm_chat_archive USING btree (conversation_id);
CREATE INDEX idx_chat_archive_customer ON scrm.scrm_chat_archive USING btree (customer_id);
CREATE INDEX idx_chat_archive_quality ON scrm.scrm_chat_archive USING btree (quality_flag);
CREATE INDEX idx_chat_archive_sent_at ON scrm.scrm_chat_archive USING btree (sent_at);

CREATE INDEX idx_chat_archive_sent ON scrm.scrm_chat_archive USING btree (sent_at);
CREATE INDEX idx_churn_recovery_customer ON scrm.scrm_churn_recovery USING btree (customer_id);
CREATE INDEX idx_churn_recovery_executed ON scrm.scrm_churn_recovery USING btree (action_executed_at);
CREATE INDEX idx_churn_recovery_result ON scrm.scrm_churn_recovery USING btree (result);

CREATE INDEX idx_churn_recovery_warning ON scrm.scrm_churn_recovery USING btree (warning_id);
CREATE INDEX idx_churn_rule_enabled ON scrm.scrm_churn_rule USING btree (enabled);
CREATE INDEX idx_churn_rule_priority ON scrm.scrm_churn_rule USING btree (priority);
CREATE INDEX idx_churn_rule_risk_level ON scrm.scrm_churn_rule USING btree (risk_level);

CREATE INDEX idx_churn_warning_assignee ON scrm.scrm_churn_warning USING btree (assignee_id);
CREATE INDEX idx_churn_warning_customer ON scrm.scrm_churn_warning USING btree (customer_id, detected_at DESC);
CREATE INDEX idx_churn_warning_detected ON scrm.scrm_churn_warning USING btree (detected_at);
CREATE INDEX idx_churn_warning_risk_level ON scrm.scrm_churn_warning USING btree (risk_level);
CREATE INDEX idx_churn_warning_rule ON scrm.scrm_churn_warning USING btree (rule_id);
CREATE INDEX idx_churn_warning_status ON scrm.scrm_churn_warning USING btree (status);

CREATE UNIQUE INDEX idx_commission_plan_code ON scrm.scrm_commission_plan USING btree (plan_code);
CREATE INDEX idx_commission_plan_default ON scrm.scrm_commission_plan USING btree (is_default);
CREATE INDEX idx_commission_plan_period ON scrm.scrm_commission_plan USING btree (start_date, end_date);
CREATE INDEX idx_commission_plan_status ON scrm.scrm_commission_plan USING btree (status);

CREATE INDEX idx_commission_plan_type ON scrm.scrm_commission_plan USING btree (plan_type);
CREATE UNIQUE INDEX idx_commission_record_no ON scrm.scrm_commission_record USING btree (record_no);
CREATE INDEX idx_commission_record_order ON scrm.scrm_commission_record USING btree (order_id);
CREATE INDEX idx_commission_record_period ON scrm.scrm_commission_record USING btree (period);
CREATE INDEX idx_commission_record_plan ON scrm.scrm_commission_record USING btree (plan_id);
CREATE INDEX idx_commission_record_sales ON scrm.scrm_commission_record USING btree (sales_person_id);
CREATE INDEX idx_commission_record_status ON scrm.scrm_commission_record USING btree (status);

CREATE INDEX idx_commission_rule_enabled ON scrm.scrm_commission_rule USING btree (enabled);
CREATE INDEX idx_commission_rule_plan ON scrm.scrm_commission_rule USING btree (plan_id);

CREATE INDEX idx_commission_rule_type ON scrm.scrm_commission_rule USING btree (rule_type);
CREATE INDEX idx_community_member_active ON scrm.scrm_community_member USING btree (community_id, is_active);
CREATE INDEX idx_community_member_community ON scrm.scrm_community_member USING btree (community_id);
CREATE INDEX idx_community_member_customer ON scrm.scrm_community_member USING btree (customer_id);
CREATE INDEX idx_community_member_status ON scrm.scrm_community_member USING btree (status);

CREATE INDEX idx_community_msg_community ON scrm.scrm_community_message USING btree (community_id);
CREATE INDEX idx_community_msg_sender_type ON scrm.scrm_community_message USING btree (sender_type);
CREATE INDEX idx_community_msg_sent_at ON scrm.scrm_community_message USING btree (community_id, sent_at);

CREATE INDEX idx_community_msg_type ON scrm.scrm_community_message USING btree (message_type);
CREATE INDEX idx_community_owner ON scrm.scrm_community USING btree (owner_id);
CREATE INDEX idx_community_platform_type ON scrm.scrm_community USING btree (platform_type);
CREATE INDEX idx_community_sop_community ON scrm.scrm_community_sop USING btree (community_id);
CREATE INDEX idx_community_sop_enabled ON scrm.scrm_community_sop USING btree (enabled);

CREATE INDEX idx_community_sop_trigger ON scrm.scrm_community_sop USING btree (trigger_type);
CREATE INDEX idx_community_status ON scrm.scrm_community USING btree (status);

CREATE INDEX idx_community_type ON scrm.scrm_community USING btree (community_type);
CREATE INDEX idx_competitor_activity_competitor ON scrm.scrm_competitor_activity USING btree (competitor_id);
CREATE INDEX idx_competitor_activity_date ON scrm.scrm_competitor_activity USING btree (activity_date);
CREATE INDEX idx_competitor_activity_impact ON scrm.scrm_competitor_activity USING btree (impact_level);
CREATE INDEX idx_competitor_activity_importance ON scrm.scrm_competitor_activity USING btree (importance_score);
CREATE INDEX idx_competitor_activity_response ON scrm.scrm_competitor_activity USING btree (response_status);

CREATE INDEX idx_competitor_activity_type ON scrm.scrm_competitor_activity USING btree (activity_type);
CREATE UNIQUE INDEX idx_competitor_code ON scrm.scrm_competitor USING btree (competitor_code);
CREATE INDEX idx_competitor_industry ON scrm.scrm_competitor USING btree (industry);
CREATE INDEX idx_competitor_last_monitored ON scrm.scrm_competitor USING btree (last_monitored_at);
CREATE INDEX idx_competitor_monitoring ON scrm.scrm_competitor USING btree (monitoring_enabled);
CREATE INDEX idx_competitor_product_category ON scrm.scrm_competitor_product USING btree (product_category);
CREATE INDEX idx_competitor_product_competitor ON scrm.scrm_competitor_product USING btree (competitor_id);
CREATE INDEX idx_competitor_product_last_monitored ON scrm.scrm_competitor_product USING btree (last_monitored_at);
CREATE INDEX idx_competitor_product_monitoring ON scrm.scrm_competitor_product USING btree (monitoring_enabled);
CREATE INDEX idx_competitor_product_status ON scrm.scrm_competitor_product USING btree (status);

CREATE INDEX idx_competitor_status ON scrm.scrm_competitor USING btree (status);

CREATE INDEX idx_competitor_threat_level ON scrm.scrm_competitor USING btree (threat_level);
CREATE UNIQUE INDEX idx_config_group_code ON scrm.scrm_config_group USING btree (group_code);
CREATE INDEX idx_config_group_enabled ON scrm.scrm_config_group USING btree (enabled);
CREATE INDEX idx_config_group_parent ON scrm.scrm_config_group USING btree (parent_group_code);

CREATE INDEX idx_config_history_config ON scrm.scrm_config_history USING btree (config_id);
CREATE INDEX idx_config_history_key ON scrm.scrm_config_history USING btree (config_key);

CREATE INDEX idx_config_history_time ON scrm.scrm_config_history USING btree (changed_at);
CREATE INDEX idx_config_history_type ON scrm.scrm_config_history USING btree (change_type);
CREATE INDEX idx_config_history_user ON scrm.scrm_config_history USING btree (changed_by);
CREATE INDEX idx_content_asset_category ON scrm.scrm_content_asset USING btree (category);
CREATE INDEX idx_content_asset_source ON scrm.scrm_content_asset USING btree (source_type);

CREATE INDEX idx_content_asset_type ON scrm.scrm_content_asset USING btree (asset_type);
CREATE INDEX idx_content_author ON scrm.scrm_content USING btree (author_id);
CREATE INDEX idx_content_category ON scrm.scrm_content USING btree (category);
CREATE INDEX idx_content_channel_channel ON scrm.scrm_content_channel USING btree (channel);
CREATE INDEX idx_content_channel_content ON scrm.scrm_content_channel USING btree (content_id);
CREATE INDEX idx_content_channel_status ON scrm.scrm_content_channel USING btree (status);

CREATE INDEX idx_content_schedule_content ON scrm.scrm_content_schedule USING btree (content_id);
CREATE INDEX idx_content_schedule_scheduled ON scrm.scrm_content_schedule USING btree (scheduled_at);
CREATE INDEX idx_content_schedule_status ON scrm.scrm_content_schedule USING btree (status);

CREATE INDEX idx_content_status ON scrm.scrm_content USING btree (status);

CREATE INDEX idx_content_type ON scrm.scrm_content USING btree (content_type);
CREATE INDEX idx_contract_change_contract ON scrm.scrm_contract_change USING btree (contract_id);
CREATE INDEX idx_contract_change_new_contract ON scrm.scrm_contract_change USING btree (new_contract_id);
CREATE UNIQUE INDEX idx_contract_change_no ON scrm.scrm_contract_change USING btree (change_no);
CREATE INDEX idx_contract_change_status ON scrm.scrm_contract_change USING btree (change_status);

CREATE INDEX idx_contract_change_type ON scrm.scrm_contract_change USING btree (change_type);
CREATE INDEX idx_contract_customer ON scrm.scrm_contract USING btree (customer_id);
CREATE INDEX idx_contract_expires ON scrm.scrm_contract USING btree (status, end_date);
CREATE UNIQUE INDEX idx_contract_no ON scrm.scrm_contract USING btree (contract_no);
CREATE INDEX idx_contract_payment_contract ON scrm.scrm_contract_payment USING btree (contract_id);
CREATE INDEX idx_contract_payment_due ON scrm.scrm_contract_payment USING btree (payment_status, planned_date);
CREATE UNIQUE INDEX idx_contract_payment_no ON scrm.scrm_contract_payment USING btree (payment_no);
CREATE INDEX idx_contract_payment_planned_date ON scrm.scrm_contract_payment USING btree (planned_date);
CREATE INDEX idx_contract_payment_status ON scrm.scrm_contract_payment USING btree (payment_status);

CREATE INDEX idx_contract_reminder_contract ON scrm.scrm_contract_reminder USING btree (contract_id);
CREATE INDEX idx_contract_reminder_date ON scrm.scrm_contract_reminder USING btree (reminder_date);
CREATE INDEX idx_contract_reminder_pending ON scrm.scrm_contract_reminder USING btree (status, reminder_date);
CREATE INDEX idx_contract_reminder_status ON scrm.scrm_contract_reminder USING btree (status);

CREATE INDEX idx_contract_renewal ON scrm.scrm_contract USING btree (renewal_of_id);
CREATE INDEX idx_contract_sales_person ON scrm.scrm_contract USING btree (sales_person_id);
CREATE INDEX idx_contract_status ON scrm.scrm_contract USING btree (status);
CREATE UNIQUE INDEX idx_contract_template_code ON scrm.scrm_contract_template USING btree (template_code);
CREATE INDEX idx_contract_template_ref ON scrm.scrm_contract USING btree (template_id);
CREATE INDEX idx_contract_template_status ON scrm.scrm_contract_template USING btree (status);

CREATE INDEX idx_contract_template_type ON scrm.scrm_contract_template USING btree (contract_type);

CREATE INDEX idx_conversation_account ON scrm.scrm_conversation USING btree (account_id);
CREATE INDEX idx_conversation_customer ON scrm.scrm_conversation USING btree (customer_id);
CREATE INDEX idx_conversation_platform_id ON scrm.scrm_conversation USING btree (platform_conversation_id);
CREATE INDEX idx_conversation_last_msg ON scrm.scrm_conversation USING btree (last_message_at);
CREATE INDEX idx_coupon_customer ON scrm.scrm_coupon USING btree (customer_id);
CREATE INDEX idx_coupon_expires ON scrm.scrm_coupon USING btree (status, expires_at);
CREATE INDEX idx_coupon_log_action ON scrm.scrm_coupon_usage_log USING btree (action_type);
CREATE INDEX idx_coupon_log_coupon ON scrm.scrm_coupon_usage_log USING btree (coupon_id);
CREATE INDEX idx_coupon_log_template ON scrm.scrm_coupon_usage_log USING btree (template_id);
CREATE INDEX idx_coupon_log_time ON scrm.scrm_coupon_usage_log USING btree (action_time);
CREATE INDEX idx_coupon_status ON scrm.scrm_coupon USING btree (status);
CREATE INDEX idx_coupon_template_ref ON scrm.scrm_coupon USING btree (template_id);
CREATE INDEX idx_coupon_template_status ON scrm.scrm_coupon_template USING btree (status);

CREATE INDEX idx_coupon_template_type ON scrm.scrm_coupon_template USING btree (coupon_type);

CREATE INDEX idx_customer_dup_customer ON scrm.scrm_customer_duplicate USING btree (customer_id);
CREATE INDEX idx_customer_dup_dup_customer ON scrm.scrm_customer_duplicate USING btree (duplicate_customer_id);
CREATE INDEX idx_customer_dup_status ON scrm.scrm_customer_duplicate USING btree (status);

CREATE INDEX idx_customer_group_owner ON scrm.scrm_customer_group USING btree (owner_account_id);
CREATE INDEX idx_customer_group_platform_group_uid ON scrm.scrm_customer_group USING btree (platform_group_uid);

CREATE INDEX idx_customer_health_score_at_risk ON scrm.scrm_customer_health_score USING btree (is_at_risk);
CREATE INDEX idx_customer_health_score_calculated ON scrm.scrm_customer_health_score USING btree (calculated_at);
CREATE INDEX idx_customer_health_score_churn ON scrm.scrm_customer_health_score USING btree (is_churn_risk);
CREATE INDEX idx_customer_health_score_customer ON scrm.scrm_customer_health_score USING btree (customer_id);
CREATE INDEX idx_customer_health_score_level ON scrm.scrm_customer_health_score USING btree (health_level);
CREATE INDEX idx_customer_health_score_model ON scrm.scrm_customer_health_score USING btree (model_id);
CREATE INDEX idx_customer_health_score_risk_level ON scrm.scrm_customer_health_score USING btree (risk_level);

CREATE INDEX idx_customer_health_score_total ON scrm.scrm_customer_health_score USING btree (total_score);
CREATE INDEX idx_customer_identity_customer ON scrm.scrm_customer_identity USING btree (customer_id);
CREATE INDEX idx_customer_identity_platform ON scrm.scrm_customer_identity USING btree (platform);
CREATE INDEX idx_customer_identity_primary ON scrm.scrm_customer_identity USING btree (customer_id, is_primary);

CREATE INDEX idx_customer_identity_type_value ON scrm.scrm_customer_identity USING btree (identity_type, identity_value);
CREATE INDEX idx_customer_journey_entry_type ON scrm.scrm_customer_journey USING btree (entry_type);
CREATE INDEX idx_customer_journey_status ON scrm.scrm_customer_journey USING btree (status);

CREATE INDEX idx_customer_level_default ON scrm.scrm_customer_level USING btree (is_default);
CREATE INDEX idx_customer_level_enabled ON scrm.scrm_customer_level USING btree (enabled);
CREATE INDEX idx_customer_level_hist_changed ON scrm.scrm_customer_level_history USING btree (changed_at);
CREATE INDEX idx_customer_level_hist_customer ON scrm.scrm_customer_level_history USING btree (customer_id, changed_at DESC);

CREATE INDEX idx_customer_level_hist_to_level ON scrm.scrm_customer_level_history USING btree (to_level_id);
CREATE INDEX idx_customer_level_order ON scrm.scrm_customer_level USING btree (level_order);
CREATE INDEX idx_customer_level_rule_enabled ON scrm.scrm_customer_level_rule USING btree (enabled);
CREATE INDEX idx_customer_level_rule_target ON scrm.scrm_customer_level_rule USING btree (target_level_id);

CREATE INDEX idx_customer_level_rule_type ON scrm.scrm_customer_level_rule USING btree (rule_type);

CREATE INDEX idx_customer_lifecycle ON scrm.scrm_customer USING btree (lifecycle);
CREATE INDEX idx_customer_lifecycle_active ON scrm.scrm_customer_lifecycle USING btree (is_active);
CREATE INDEX idx_customer_lifecycle_churned ON scrm.scrm_customer_lifecycle USING btree (is_churned);
CREATE INDEX idx_customer_lifecycle_churnrisk ON scrm.scrm_customer_lifecycle USING btree (churn_risk_score);
CREATE UNIQUE INDEX idx_customer_lifecycle_customer ON scrm.scrm_customer_lifecycle USING btree (customer_id);
CREATE INDEX idx_customer_lifecycle_overdue ON scrm.scrm_customer_lifecycle USING btree (is_overdue);
CREATE INDEX idx_customer_lifecycle_risk ON scrm.scrm_customer_lifecycle USING btree (risk_level);
CREATE INDEX idx_customer_lifecycle_stage ON scrm.scrm_customer_lifecycle USING btree (current_stage_id);

CREATE INDEX idx_customer_lifecycle_value ON scrm.scrm_customer_lifecycle USING btree (value_segment);
CREATE UNIQUE INDEX idx_customer_membership_card ON scrm.scrm_customer_membership USING btree (member_card_no);
CREATE INDEX idx_customer_membership_created ON scrm.scrm_customer_membership USING btree (create_time);
CREATE INDEX idx_customer_membership_customer ON scrm.scrm_customer_membership USING btree (customer_id);
CREATE INDEX idx_customer_membership_expiry ON scrm.scrm_customer_membership USING btree (tier_expiry_date);
CREATE INDEX idx_customer_membership_level ON scrm.scrm_customer_membership USING btree (tier_level);
CREATE INDEX idx_customer_membership_status ON scrm.scrm_customer_membership USING btree (membership_status);

CREATE INDEX idx_customer_membership_tier ON scrm.scrm_customer_membership USING btree (tier_id);
CREATE INDEX idx_customer_owner ON scrm.scrm_customer USING btree (owner_account_id);
CREATE INDEX idx_customer_platform_uid ON scrm.scrm_customer USING btree (platform_type, platform_customer_uid);
CREATE INDEX idx_customer_profile_customer_id ON scrm.scrm_customer_profile USING btree (customer_id);
CREATE INDEX idx_customer_profile_status ON scrm.scrm_customer_profile USING btree (profile_status);

CREATE INDEX idx_customer_tag_auto ON scrm.scrm_customer_tag_def USING btree (auto_apply);
CREATE INDEX idx_customer_tag_category ON scrm.scrm_customer_tag_def USING btree (category);
CREATE UNIQUE INDEX idx_customer_tag_code ON scrm.scrm_customer_tag_def USING btree (tag_code);
CREATE INDEX idx_customer_tag_customer ON scrm.scrm_customer_tag USING btree (customer_id);
CREATE INDEX idx_customer_tag_enabled ON scrm.scrm_customer_tag_def USING btree (enabled);
CREATE INDEX idx_customer_tag_group ON scrm.scrm_customer_tag_def USING btree (group_id);
CREATE UNIQUE INDEX idx_customer_tag_rule_code ON scrm.scrm_customer_tag_rule USING btree (rule_code);
CREATE INDEX idx_customer_tag_rule_enabled ON scrm.scrm_customer_tag_rule USING btree (enabled);
CREATE INDEX idx_customer_tag_rule_tag ON scrm.scrm_customer_tag_rule USING btree (tag_id);

CREATE INDEX idx_customer_tag_rule_type ON scrm.scrm_customer_tag_rule USING btree (rule_type);

CREATE INDEX idx_customer_tag_type ON scrm.scrm_customer_tag_def USING btree (tag_type);
CREATE INDEX idx_customer_tag_value ON scrm.scrm_customer_tag_def USING btree (value_type);

CREATE INDEX idx_customer_timeline_customer ON scrm.scrm_customer_timeline USING btree (customer_id);
CREATE INDEX idx_customer_timeline_event_time ON scrm.scrm_customer_timeline USING btree (event_time);
CREATE INDEX idx_customer_timeline_event_type ON scrm.scrm_customer_timeline USING btree (event_type);

CREATE INDEX idx_customer_timeline_customer_time ON scrm.scrm_customer_timeline USING btree (customer_id, event_time);
CREATE INDEX idx_data_dictionary_category ON scrm.scrm_data_dictionary USING btree (category);
CREATE INDEX idx_data_dictionary_code ON scrm.scrm_data_dictionary USING btree (dict_code);
CREATE INDEX idx_data_dictionary_enabled ON scrm.scrm_data_dictionary USING btree (enabled);
CREATE INDEX idx_data_dictionary_item_code ON scrm.scrm_data_dictionary_item USING btree (dict_code);
CREATE INDEX idx_data_dictionary_item_dict ON scrm.scrm_data_dictionary_item USING btree (dict_id);
CREATE INDEX idx_data_dictionary_item_enabled ON scrm.scrm_data_dictionary_item USING btree (enabled);
CREATE INDEX idx_data_dictionary_item_parent ON scrm.scrm_data_dictionary_item USING btree (parent_id);
CREATE INDEX idx_data_dictionary_item_path ON scrm.scrm_data_dictionary_item USING btree (dict_code, item_path);

CREATE INDEX idx_data_dictionary_item_value ON scrm.scrm_data_dictionary_item USING btree (dict_code, item_value);
CREATE INDEX idx_data_dictionary_module ON scrm.scrm_data_dictionary USING btree (module);
CREATE INDEX idx_data_dictionary_parent ON scrm.scrm_data_dictionary USING btree (parent_id);

CREATE INDEX idx_data_dictionary_type ON scrm.scrm_data_dictionary USING btree (dict_type);
CREATE INDEX idx_data_dictionary_usage_code ON scrm.scrm_data_dictionary_usage USING btree (dict_code);
CREATE INDEX idx_data_dictionary_usage_dict ON scrm.scrm_data_dictionary_usage USING btree (dict_id);
CREATE INDEX idx_data_dictionary_usage_item ON scrm.scrm_data_dictionary_usage USING btree (item_id);
CREATE INDEX idx_data_dictionary_usage_last ON scrm.scrm_data_dictionary_usage USING btree (last_used_at);
CREATE INDEX idx_data_dictionary_usage_module ON scrm.scrm_data_dictionary_usage USING btree (usage_module);

CREATE INDEX idx_dt_log_status ON scrm.scrm_data_transfer_log USING btree (status);
CREATE INDEX idx_dt_log_task ON scrm.scrm_data_transfer_log USING btree (task_id);
CREATE INDEX idx_ecs_config_corp ON scrm.scrm_external_contact_sync_config USING btree (corp_id);
CREATE INDEX idx_ecs_config_enabled ON scrm.scrm_external_contact_sync_config USING btree (enabled);
CREATE INDEX idx_ecs_config_platform ON scrm.scrm_external_contact_sync_config USING btree (platform);

CREATE INDEX idx_ecs_log_customer ON scrm.scrm_external_contact_sync_log USING btree (customer_id);
CREATE INDEX idx_ecs_log_op_type ON scrm.scrm_external_contact_sync_log USING btree (operation_type);
CREATE INDEX idx_ecs_log_status ON scrm.scrm_external_contact_sync_log USING btree (status);
CREATE INDEX idx_ecs_log_task ON scrm.scrm_external_contact_sync_log USING btree (task_id);

CREATE INDEX idx_ecs_mapping_customer ON scrm.scrm_external_contact_mapping USING btree (customer_id);
CREATE INDEX idx_ecs_mapping_follow_user ON scrm.scrm_external_contact_mapping USING btree (follow_user_id);
CREATE INDEX idx_ecs_mapping_platform ON scrm.scrm_external_contact_mapping USING btree (platform);
CREATE INDEX idx_ecs_mapping_sync_status ON scrm.scrm_external_contact_mapping USING btree (sync_status);

CREATE INDEX idx_ecs_task_config ON scrm.scrm_external_contact_sync_task USING btree (config_id);
CREATE INDEX idx_ecs_task_start ON scrm.scrm_external_contact_sync_task USING btree (start_time);
CREATE INDEX idx_ecs_task_status ON scrm.scrm_external_contact_sync_task USING btree (status);

CREATE INDEX idx_ecs_task_triggered_by ON scrm.scrm_external_contact_sync_task USING btree (triggered_by);
CREATE INDEX idx_engagement_event_behavior ON scrm.scrm_engagement_event USING btree (behavior_type);
CREATE INDEX idx_engagement_event_channel ON scrm.scrm_engagement_event USING btree (channel);
CREATE INDEX idx_engagement_event_customer ON scrm.scrm_engagement_event USING btree (customer_id);
CREATE INDEX idx_engagement_event_rule ON scrm.scrm_engagement_event USING btree (rule_id);

CREATE INDEX idx_engagement_event_time ON scrm.scrm_engagement_event USING btree (event_time);
CREATE INDEX idx_engagement_level_code ON scrm.scrm_engagement_level USING btree (level_code);
CREATE INDEX idx_engagement_level_enabled ON scrm.scrm_engagement_level USING btree (enabled);
CREATE INDEX idx_engagement_level_priority ON scrm.scrm_engagement_level USING btree (priority);

CREATE INDEX idx_engagement_rule_behavior ON scrm.scrm_engagement_rule USING btree (behavior_type);
CREATE INDEX idx_engagement_rule_channel ON scrm.scrm_engagement_rule USING btree (channel);
CREATE INDEX idx_engagement_rule_enabled ON scrm.scrm_engagement_rule USING btree (enabled);

CREATE INDEX idx_engagement_score_current ON scrm.scrm_engagement_score USING btree (current_score);
CREATE INDEX idx_engagement_score_customer ON scrm.scrm_engagement_score USING btree (customer_id);
CREATE INDEX idx_engagement_score_level ON scrm.scrm_engagement_score USING btree (engagement_level);

CREATE INDEX idx_exec_log_campaign ON scrm.scrm_campaign_execution_log USING btree (campaign_id);
CREATE INDEX idx_exec_log_operated_at ON scrm.scrm_campaign_execution_log USING btree (operated_at);
CREATE INDEX idx_exec_log_status ON scrm.scrm_campaign_execution_log USING btree (status);

CREATE INDEX idx_export_task_datatype ON scrm.scrm_export_task USING btree (data_type);
CREATE INDEX idx_export_task_status ON scrm.scrm_export_task USING btree (status);
CREATE INDEX idx_feedback_assignee ON scrm.scrm_feedback USING btree (assignee_id);
CREATE INDEX idx_feedback_category ON scrm.scrm_feedback USING btree (category);
CREATE INDEX idx_feedback_category_enabled ON scrm.scrm_feedback_category USING btree (enabled);

CREATE INDEX idx_feedback_comment_created ON scrm.scrm_feedback_comment USING btree (created_at);
CREATE INDEX idx_feedback_comment_feedback ON scrm.scrm_feedback_comment USING btree (feedback_id);
CREATE INDEX idx_feedback_comment_parent ON scrm.scrm_feedback_comment USING btree (parent_comment_id);

CREATE INDEX idx_feedback_comment_type ON scrm.scrm_feedback_comment USING btree (comment_type);
CREATE INDEX idx_feedback_created ON scrm.scrm_feedback USING btree (create_time);
CREATE INDEX idx_feedback_customer ON scrm.scrm_feedback USING btree (customer_id);
CREATE INDEX idx_feedback_priority ON scrm.scrm_feedback USING btree (priority);
CREATE INDEX idx_feedback_sentiment ON scrm.scrm_feedback USING btree (sentiment);
CREATE INDEX idx_feedback_status ON scrm.scrm_feedback USING btree (status);

CREATE INDEX idx_feedback_type ON scrm.scrm_feedback USING btree (feedback_type);
CREATE INDEX idx_festival_date ON scrm.scrm_festival USING btree (festival_date);
CREATE INDEX idx_festival_enabled ON scrm.scrm_festival USING btree (enabled);

CREATE INDEX idx_festival_type ON scrm.scrm_festival USING btree (festival_type);
CREATE INDEX idx_follow_up_record_customer ON scrm.scrm_follow_up_record USING btree (customer_id);
CREATE INDEX idx_follow_up_record_recorded_at ON scrm.scrm_follow_up_record USING btree (recorded_at);
CREATE INDEX idx_follow_up_record_task ON scrm.scrm_follow_up_record USING btree (task_id);

CREATE INDEX idx_follow_up_task_assignee ON scrm.scrm_follow_up_task USING btree (assignee_id);
CREATE INDEX idx_follow_up_task_customer ON scrm.scrm_follow_up_task USING btree (customer_id);
CREATE INDEX idx_follow_up_task_planned_at ON scrm.scrm_follow_up_task USING btree (planned_at);
CREATE INDEX idx_follow_up_task_reminder ON scrm.scrm_follow_up_task USING btree (reminded, planned_at);
CREATE INDEX idx_follow_up_task_status ON scrm.scrm_follow_up_task USING btree (status);

CREATE INDEX idx_follow_up_template_enabled ON scrm.scrm_follow_up_template USING btree (enabled);
CREATE INDEX idx_follow_up_template_scenario ON scrm.scrm_follow_up_template USING btree (scenario);
CREATE INDEX idx_follow_up_template_task_type ON scrm.scrm_follow_up_template USING btree (task_type);

CREATE UNIQUE INDEX idx_forecast_model_code ON scrm.scrm_forecast_model USING btree (model_code);
CREATE INDEX idx_forecast_model_enabled ON scrm.scrm_forecast_model USING btree (enabled);
CREATE INDEX idx_forecast_model_metric ON scrm.scrm_forecast_model USING btree (target_metric);

CREATE INDEX idx_forecast_model_trained ON scrm.scrm_forecast_model USING btree (is_trained);
CREATE INDEX idx_forecast_model_type ON scrm.scrm_forecast_model USING btree (model_type);
CREATE INDEX idx_forecast_result_actual ON scrm.scrm_forecast_result USING btree (is_actual);
CREATE INDEX idx_forecast_result_date ON scrm.scrm_forecast_result USING btree (forecast_date);
CREATE INDEX idx_forecast_result_model ON scrm.scrm_forecast_result USING btree (model_id);
CREATE INDEX idx_forecast_result_period ON scrm.scrm_forecast_result USING btree (period_label);
CREATE INDEX idx_forecast_result_scenario ON scrm.scrm_forecast_result USING btree (scenario_id);

CREATE UNIQUE INDEX idx_forecast_scenario_code ON scrm.scrm_forecast_scenario USING btree (scenario_code);
CREATE INDEX idx_forecast_scenario_model ON scrm.scrm_forecast_scenario USING btree (model_id);
CREATE INDEX idx_forecast_scenario_period ON scrm.scrm_forecast_scenario USING btree (target_period);
CREATE INDEX idx_forecast_scenario_status ON scrm.scrm_forecast_scenario USING btree (status);

CREATE INDEX idx_forecast_scenario_type ON scrm.scrm_forecast_scenario USING btree (scenario_type);
CREATE INDEX idx_funnel_campaign ON scrm.scrm_funnel_analysis USING btree (campaign_id);
CREATE INDEX idx_funnel_code ON scrm.scrm_funnel_analysis USING btree (funnel_code);
CREATE INDEX idx_funnel_is_default ON scrm.scrm_funnel USING btree (is_default);
CREATE INDEX idx_funnel_period ON scrm.scrm_funnel_analysis USING btree (start_date, end_date);
CREATE INDEX idx_funnel_stage_funnel_id ON scrm.scrm_funnel_stage USING btree (funnel_id);
CREATE INDEX idx_funnel_stage_order ON scrm.scrm_funnel_stage USING btree (funnel_id, stage_order);

CREATE INDEX idx_funnel_status ON scrm.scrm_funnel USING btree (status);

CREATE INDEX idx_funnel_type ON scrm.scrm_funnel_analysis USING btree (funnel_type);
CREATE INDEX idx_group_member_customer ON scrm.scrm_customer_group_member USING btree (customer_id);
CREATE INDEX idx_group_member_group ON scrm.scrm_customer_group_member USING btree (group_id);
CREATE INDEX idx_health_account ON scrm.scrm_account_health USING btree (account_id);
CREATE INDEX idx_health_alert_assigned ON scrm.scrm_health_alert USING btree (assigned_to);
CREATE INDEX idx_health_alert_customer ON scrm.scrm_health_alert USING btree (customer_id);
CREATE INDEX idx_health_alert_score ON scrm.scrm_health_alert USING btree (health_score_id);
CREATE INDEX idx_health_alert_severity ON scrm.scrm_health_alert USING btree (severity);
CREATE INDEX idx_health_alert_status ON scrm.scrm_health_alert USING btree (status);

CREATE INDEX idx_health_alert_triggered ON scrm.scrm_health_alert USING btree (triggered_at);
CREATE INDEX idx_health_alert_type ON scrm.scrm_health_alert USING btree (alert_type);
CREATE INDEX idx_health_checked_at ON scrm.scrm_account_health USING btree (checked_at);
CREATE INDEX idx_health_result ON scrm.scrm_account_health USING btree (check_result);
CREATE UNIQUE INDEX idx_health_score_model_code ON scrm.scrm_health_score_model USING btree (model_code);
CREATE INDEX idx_health_score_model_default ON scrm.scrm_health_score_model USING btree (is_default);
CREATE INDEX idx_health_score_model_published ON scrm.scrm_health_score_model USING btree (is_published);

CREATE INDEX idx_health_score_model_type ON scrm.scrm_health_score_model USING btree (scoring_type);

CREATE INDEX idx_identity_merge_history_rollback ON scrm.scrm_identity_merge_history USING btree (rollback_available, rolled_back);
CREATE INDEX idx_identity_merge_history_source ON scrm.scrm_identity_merge_history USING btree (source_customer_id);
CREATE INDEX idx_identity_merge_history_target ON scrm.scrm_identity_merge_history USING btree (target_customer_id);
CREATE INDEX idx_identity_merge_history_task ON scrm.scrm_identity_merge_history USING btree (task_id);

CREATE INDEX idx_identity_merge_history_time ON scrm.scrm_identity_merge_history USING btree (merged_at);
CREATE INDEX idx_identity_merge_rule_enabled ON scrm.scrm_identity_merge_rule USING btree (enabled);
CREATE INDEX idx_identity_merge_rule_name ON scrm.scrm_identity_merge_rule USING btree (rule_name);
CREATE INDEX idx_identity_merge_rule_priority ON scrm.scrm_identity_merge_rule USING btree (priority);

CREATE INDEX idx_identity_merge_task_source ON scrm.scrm_identity_merge_task USING btree (source_customer_id);
CREATE INDEX idx_identity_merge_task_status ON scrm.scrm_identity_merge_task USING btree (status);
CREATE INDEX idx_identity_merge_task_target ON scrm.scrm_identity_merge_task USING btree (target_customer_id);

CREATE INDEX idx_identity_merge_task_time ON scrm.scrm_identity_merge_task USING btree (create_time);
CREATE INDEX idx_identity_merge_task_type ON scrm.scrm_identity_merge_task USING btree (merge_type);
CREATE INDEX idx_import_task_datatype ON scrm.scrm_import_task USING btree (data_type);
CREATE INDEX idx_import_task_status ON scrm.scrm_import_task USING btree (status);
CREATE INDEX idx_import_tpl_datatype ON scrm.scrm_import_template USING btree (data_type);
CREATE INDEX idx_import_tpl_enabled ON scrm.scrm_import_template USING btree (enabled);
CREATE INDEX idx_inheritance_item_status ON scrm.scrm_inheritance_item USING btree (status);
CREATE INDEX idx_inheritance_item_task ON scrm.scrm_inheritance_item USING btree (task_id);

CREATE INDEX idx_inheritance_item_type ON scrm.scrm_inheritance_item USING btree (item_type);
CREATE INDEX idx_inheritance_task_from_user ON scrm.scrm_inheritance_task USING btree (from_user_id);
CREATE INDEX idx_inheritance_task_status ON scrm.scrm_inheritance_task USING btree (status);

CREATE INDEX idx_inheritance_task_to_user ON scrm.scrm_inheritance_task USING btree (to_user_id);
CREATE INDEX idx_interaction_calendar_enabled ON scrm.scrm_interaction_calendar USING btree (enabled);
CREATE INDEX idx_interaction_calendar_owner ON scrm.scrm_interaction_calendar USING btree (owner_id);

CREATE INDEX idx_interaction_calendar_type ON scrm.scrm_interaction_calendar USING btree (calendar_type);
CREATE INDEX idx_interaction_plan_customer ON scrm.scrm_interaction_plan USING btree (customer_id);
CREATE INDEX idx_interaction_plan_date_range ON scrm.scrm_interaction_plan USING btree (scheduled_start, scheduled_end);
CREATE INDEX idx_interaction_plan_owner ON scrm.scrm_interaction_plan USING btree (owner_id);
CREATE INDEX idx_interaction_plan_related ON scrm.scrm_interaction_plan USING btree (related_plan_id);
CREATE INDEX idx_interaction_plan_status ON scrm.scrm_interaction_plan USING btree (status);

CREATE INDEX idx_interaction_plan_type ON scrm.scrm_interaction_plan USING btree (interaction_type);
CREATE INDEX idx_invoice_customer ON scrm.scrm_invoice USING btree (customer_id);
CREATE INDEX idx_invoice_date ON scrm.scrm_invoice USING btree (invoice_date);
CREATE INDEX idx_invoice_order ON scrm.scrm_invoice USING btree (order_id);
CREATE INDEX idx_invoice_red_flush ON scrm.scrm_invoice USING btree (original_invoice_id);
CREATE INDEX idx_invoice_red_flush_to ON scrm.scrm_invoice USING btree (red_flush_invoice_id);
CREATE INDEX idx_invoice_status ON scrm.scrm_invoice USING btree (status);
CREATE INDEX idx_invoice_template_enabled ON scrm.scrm_invoice_template USING btree (enabled);

CREATE INDEX idx_invoice_template_type ON scrm.scrm_invoice_template USING btree (invoice_type);

CREATE INDEX idx_invoice_type ON scrm.scrm_invoice USING btree (invoice_type);
CREATE INDEX idx_journey_enrollment_customer_id ON scrm.scrm_journey_enrollment USING btree (customer_id);
CREATE INDEX idx_journey_enrollment_journey_id ON scrm.scrm_journey_enrollment USING btree (journey_id);
CREATE INDEX idx_journey_enrollment_next_step_at ON scrm.scrm_journey_enrollment USING btree (next_step_at);
CREATE INDEX idx_journey_enrollment_status ON scrm.scrm_journey_enrollment USING btree (status);

CREATE INDEX idx_journey_progress_log_customer_id ON scrm.scrm_journey_progress_log USING btree (customer_id);
CREATE INDEX idx_journey_progress_log_enrollment_id ON scrm.scrm_journey_progress_log USING btree (enrollment_id);
CREATE INDEX idx_journey_progress_log_journey_id ON scrm.scrm_journey_progress_log USING btree (journey_id);

CREATE INDEX idx_journey_step_journey_id ON scrm.scrm_journey_step USING btree (journey_id);
CREATE INDEX idx_journey_step_order ON scrm.scrm_journey_step USING btree (journey_id, step_order);

CREATE INDEX idx_knowledge_article_category ON scrm.scrm_knowledge_article USING btree (category_id);
CREATE UNIQUE INDEX idx_knowledge_article_code ON scrm.scrm_knowledge_article USING btree (article_code);
CREATE INDEX idx_knowledge_article_featured ON scrm.scrm_knowledge_article USING btree (is_featured);
CREATE INDEX idx_knowledge_article_pinned ON scrm.scrm_knowledge_article USING btree (is_pinned);
CREATE INDEX idx_knowledge_article_status ON scrm.scrm_knowledge_article USING btree (status);

CREATE INDEX idx_knowledge_article_type ON scrm.scrm_knowledge_article USING btree (article_type);
CREATE UNIQUE INDEX idx_knowledge_category_code ON scrm.scrm_knowledge_category USING btree (category_code);
CREATE INDEX idx_knowledge_category_level ON scrm.scrm_knowledge_category USING btree (category_level);
CREATE INDEX idx_knowledge_category_parent ON scrm.scrm_knowledge_category USING btree (parent_id);

CREATE INDEX idx_knowledge_feedback_article ON scrm.scrm_knowledge_feedback USING btree (article_id);
CREATE INDEX idx_knowledge_feedback_status ON scrm.scrm_knowledge_feedback USING btree (status);

CREATE INDEX idx_knowledge_feedback_type ON scrm.scrm_knowledge_feedback USING btree (feedback_type);
CREATE INDEX idx_knowledge_feedback_user ON scrm.scrm_knowledge_feedback USING btree (user_id);
CREATE INDEX idx_lead_assignment_assignee ON scrm.scrm_lead_assignment USING btree (assigned_to);
CREATE INDEX idx_lead_assignment_customer ON scrm.scrm_lead_assignment USING btree (public_sea_customer_id);
CREATE INDEX idx_lead_assignment_status ON scrm.scrm_lead_assignment USING btree (status);

CREATE INDEX idx_lead_dimension_category ON scrm.scrm_lead_dimension USING btree (dimension_category);
CREATE UNIQUE INDEX idx_lead_dimension_code ON scrm.scrm_lead_dimension USING btree (dimension_code);
CREATE INDEX idx_lead_dimension_enabled ON scrm.scrm_lead_dimension USING btree (enabled);

CREATE INDEX idx_lead_score_assigned ON scrm.scrm_lead_score USING btree (assigned_to);
CREATE INDEX idx_lead_score_converted ON scrm.scrm_lead_score USING btree (is_converted);
CREATE INDEX idx_lead_score_customer ON scrm.scrm_lead_score USING btree (customer_id);
CREATE INDEX idx_lead_score_grade ON scrm.scrm_lead_score USING btree (grade);
CREATE INDEX idx_lead_score_hot ON scrm.scrm_lead_score USING btree (is_hot_lead);
CREATE INDEX idx_lead_score_model ON scrm.scrm_lead_score USING btree (model_id);
CREATE INDEX idx_lead_score_qualified ON scrm.scrm_lead_score USING btree (is_qualified);

CREATE INDEX idx_lead_score_total ON scrm.scrm_lead_score USING btree (total_score);
CREATE UNIQUE INDEX idx_lead_scoring_model_code ON scrm.scrm_lead_scoring_model USING btree (model_code);
CREATE INDEX idx_lead_scoring_model_default ON scrm.scrm_lead_scoring_model USING btree (is_default);
CREATE INDEX idx_lead_scoring_model_published ON scrm.scrm_lead_scoring_model USING btree (is_published);

CREATE INDEX idx_lead_scoring_model_type ON scrm.scrm_lead_scoring_model USING btree (model_type);
CREATE INDEX idx_lifecycle_hist_changed_at ON scrm.scrm_customer_lifecycle_history USING btree (changed_at);
CREATE INDEX idx_lifecycle_hist_customer ON scrm.scrm_customer_lifecycle_history USING btree (customer_id);
CREATE INDEX idx_lifecycle_history_customer ON scrm.scrm_lifecycle_history USING btree (customer_id);

CREATE INDEX idx_lifecycle_history_time ON scrm.scrm_lifecycle_history USING btree (transition_time);
CREATE INDEX idx_lifecycle_history_to_stage ON scrm.scrm_lifecycle_history USING btree (to_stage_id);
CREATE INDEX idx_lifecycle_history_type ON scrm.scrm_lifecycle_history USING btree (transition_type);
CREATE INDEX idx_lifecycle_stage_category ON scrm.scrm_lifecycle_stage USING btree (stage_category);
CREATE UNIQUE INDEX idx_lifecycle_stage_code ON scrm.scrm_lifecycle_stage USING btree (stage_code);
CREATE INDEX idx_lifecycle_stage_enabled ON scrm.scrm_lifecycle_stage USING btree (enabled);
CREATE INDEX idx_lifecycle_stage_order ON scrm.scrm_lifecycle_stage USING btree (stage_order);
CREATE INDEX idx_lifecycle_stage_system ON scrm.scrm_lifecycle_stage USING btree (is_system);

CREATE INDEX idx_lifecycle_stage_visible ON scrm.scrm_lifecycle_stage USING btree (is_visible);
CREATE INDEX idx_lifecycle_transition_customer ON scrm.scrm_lifecycle_transition USING btree (customer_id);
CREATE INDEX idx_lifecycle_transition_date ON scrm.scrm_lifecycle_transition USING btree (transition_date);
CREATE INDEX idx_lifecycle_transition_enabled ON scrm.scrm_lifecycle_transition USING btree (is_enabled);
CREATE INDEX idx_lifecycle_transition_from ON scrm.scrm_lifecycle_transition USING btree (from_stage_id);
CREATE INDEX idx_lifecycle_transition_lifecycle ON scrm.scrm_lifecycle_transition USING btree (lifecycle_id);
CREATE INDEX idx_lifecycle_transition_reversed ON scrm.scrm_lifecycle_transition USING btree (is_reversed);

CREATE INDEX idx_lifecycle_transition_to ON scrm.scrm_lifecycle_transition USING btree (to_stage_id);
CREATE INDEX idx_lifecycle_transition_type ON scrm.scrm_lifecycle_transition USING btree (transition_type);
CREATE INDEX idx_login_log_account_operate ON scrm.scrm_account_login_log USING btree (account_id, operate_at);
CREATE INDEX idx_ltv_cohort_key ON scrm.scrm_ltv_cohort USING btree (cohort_type, cohort_key);

CREATE INDEX idx_ltv_cohort_type ON scrm.scrm_ltv_cohort USING btree (cohort_type);
CREATE INDEX idx_ltv_customer_churn ON scrm.scrm_customer_ltv USING btree (churn_probability DESC);
CREATE INDEX idx_ltv_customer_customer ON scrm.scrm_customer_ltv USING btree (customer_id);
CREATE INDEX idx_ltv_customer_ltv ON scrm.scrm_customer_ltv USING btree (predicted_ltv DESC);
CREATE INDEX idx_ltv_customer_model ON scrm.scrm_customer_ltv USING btree (model_id);
CREATE INDEX idx_ltv_customer_tier ON scrm.scrm_customer_ltv USING btree (value_tier);
CREATE UNIQUE INDEX idx_ltv_model_code ON scrm.scrm_ltv_model USING btree (model_code);
CREATE INDEX idx_ltv_model_default ON scrm.scrm_ltv_model USING btree (is_default);
CREATE INDEX idx_ltv_model_published ON scrm.scrm_ltv_model USING btree (is_published);

CREATE INDEX idx_marketing_trigger_action_type ON scrm.scrm_marketing_trigger USING btree (action_type);
CREATE INDEX idx_marketing_trigger_enabled ON scrm.scrm_marketing_trigger USING btree (enabled);
CREATE INDEX idx_marketing_trigger_event_customer ON scrm.scrm_marketing_trigger_event USING btree (customer_id);
CREATE INDEX idx_marketing_trigger_event_scheduled ON scrm.scrm_marketing_trigger_event USING btree (scheduled_at);
CREATE INDEX idx_marketing_trigger_event_status ON scrm.scrm_marketing_trigger_event USING btree (status);

CREATE INDEX idx_marketing_trigger_event_trigger_id ON scrm.scrm_marketing_trigger_event USING btree (trigger_id);
CREATE INDEX idx_marketing_trigger_event_type ON scrm.scrm_marketing_trigger USING btree (event_type);

CREATE INDEX idx_mass_send_target_customer ON scrm.scrm_mass_send_target USING btree (customer_id);
CREATE INDEX idx_mass_send_target_status ON scrm.scrm_mass_send_target USING btree (status);
CREATE INDEX idx_mass_send_target_task ON scrm.scrm_mass_send_target USING btree (task_id);

CREATE INDEX idx_mass_send_task_platform ON scrm.scrm_mass_send_task USING btree (platform_type);
CREATE INDEX idx_mass_send_task_status ON scrm.scrm_mass_send_task USING btree (status);

CREATE INDEX idx_material_category ON scrm.scrm_material USING btree (category_id);
CREATE INDEX idx_material_status ON scrm.scrm_material USING btree (status);

CREATE INDEX idx_material_type ON scrm.scrm_material USING btree (material_type);
CREATE UNIQUE INDEX idx_membership_benefit_code ON scrm.scrm_membership_benefit USING btree (benefit_code);
CREATE INDEX idx_membership_benefit_created ON scrm.scrm_membership_benefit USING btree (create_time);
CREATE INDEX idx_membership_benefit_display ON scrm.scrm_membership_benefit USING btree (display_order);
CREATE INDEX idx_membership_benefit_status ON scrm.scrm_membership_benefit USING btree (status);

CREATE INDEX idx_membership_benefit_tier ON scrm.scrm_membership_benefit USING btree (tier_id);
CREATE INDEX idx_membership_benefit_type ON scrm.scrm_membership_benefit USING btree (benefit_type);
CREATE UNIQUE INDEX idx_membership_tier_code ON scrm.scrm_membership_tier USING btree (tier_code);
CREATE INDEX idx_membership_tier_created ON scrm.scrm_membership_tier USING btree (create_time);
CREATE INDEX idx_membership_tier_enabled ON scrm.scrm_membership_tier USING btree (enabled);
CREATE INDEX idx_membership_tier_level ON scrm.scrm_membership_tier USING btree (tier_level);
CREATE INDEX idx_membership_tier_sort ON scrm.scrm_membership_tier USING btree (sort_order);

CREATE INDEX idx_merge_record_merged_at ON scrm.scrm_customer_merge_record USING btree (merged_at);
CREATE INDEX idx_merge_record_primary ON scrm.scrm_customer_merge_record USING btree (primary_customer_id);
CREATE INDEX idx_merge_record_status ON scrm.scrm_customer_merge_record USING btree (status);

CREATE INDEX idx_message_conversation ON ONLY scrm.scrm_conversation_message USING btree (conversation_id);
CREATE INDEX idx_message_message_id ON ONLY scrm.scrm_conversation_message USING btree (message_id);
CREATE INDEX idx_message_platform_message_id ON ONLY scrm.scrm_conversation_message USING btree (platform_message_id);
CREATE INDEX idx_message_sent_at ON ONLY scrm.scrm_conversation_message USING btree (sent_at);
CREATE INDEX idx_message_template_category ON scrm.scrm_message_template USING btree (category);
CREATE INDEX idx_message_template_center_code ON scrm.scrm_message_template_center USING btree (template_code);
CREATE INDEX idx_message_template_center_group ON scrm.scrm_message_template_center USING btree (group_id);
CREATE INDEX idx_message_template_center_review ON scrm.scrm_message_template_center USING btree (review_status);
CREATE INDEX idx_message_template_center_standard ON scrm.scrm_message_template_center USING btree (is_standard);
CREATE INDEX idx_message_template_center_status ON scrm.scrm_message_template_center USING btree (status);

CREATE INDEX idx_message_template_center_type ON scrm.scrm_message_template_center USING btree (template_type);
CREATE INDEX idx_message_template_center_usage ON scrm.scrm_message_template_center USING btree (usage_count);
CREATE INDEX idx_message_template_enabled ON scrm.scrm_message_template USING btree (enabled);
CREATE INDEX idx_message_template_group_code ON scrm.scrm_message_template_group USING btree (group_code);
CREATE INDEX idx_message_template_group_enabled ON scrm.scrm_message_template_group USING btree (enabled);
CREATE INDEX idx_message_template_group_parent ON scrm.scrm_message_template_group USING btree (parent_group_id);

CREATE INDEX idx_message_template_group_type ON scrm.scrm_message_template_group USING btree (group_type);
CREATE INDEX idx_message_template_platform ON scrm.scrm_message_template USING btree (platform_type);

CREATE INDEX idx_message_template_version_status ON scrm.scrm_message_template_version USING btree (status);
CREATE INDEX idx_message_template_version_template ON scrm.scrm_message_template_version USING btree (template_id);

CREATE INDEX idx_message_template_version_tpl_ver ON scrm.scrm_message_template_version USING btree (template_id, version_number);
CREATE INDEX idx_mkt_campaign_date_range ON scrm.scrm_marketing_campaign USING btree (start_date, end_date);
CREATE INDEX idx_mkt_campaign_manager ON scrm.scrm_marketing_campaign USING btree (manager_id);
CREATE INDEX idx_mkt_campaign_status ON scrm.scrm_marketing_campaign USING btree (status);

CREATE INDEX idx_mkt_campaign_type ON scrm.scrm_marketing_campaign USING btree (campaign_type);
CREATE INDEX idx_mkt_channel_campaign ON scrm.scrm_marketing_campaign_channel USING btree (campaign_id);
CREATE INDEX idx_mkt_channel_channel ON scrm.scrm_marketing_campaign_channel USING btree (channel);
CREATE INDEX idx_mkt_channel_status ON scrm.scrm_marketing_campaign_channel USING btree (status);

CREATE INDEX idx_mkt_participant_campaign ON scrm.scrm_marketing_campaign_participant USING btree (campaign_id);
CREATE INDEX idx_mkt_participant_channel ON scrm.scrm_marketing_campaign_participant USING btree (channel);
CREATE INDEX idx_mkt_participant_converted ON scrm.scrm_marketing_campaign_participant USING btree (converted);
CREATE INDEX idx_mkt_participant_customer ON scrm.scrm_marketing_campaign_participant USING btree (customer_id);

CREATE INDEX idx_monitor_metric_alert_active ON scrm.scrm_monitor_metric USING btree (is_alert_active);
CREATE UNIQUE INDEX idx_monitor_metric_code ON scrm.scrm_monitor_metric USING btree (metric_code);
CREATE INDEX idx_monitor_metric_enabled ON scrm.scrm_monitor_metric USING btree (enabled);
CREATE INDEX idx_monitor_metric_group ON scrm.scrm_monitor_metric USING btree (metric_group);

CREATE INDEX idx_monitor_metric_type ON scrm.scrm_monitor_metric USING btree (metric_type);
CREATE INDEX idx_msg_read_log_message_id ON scrm.scrm_message_read_log USING btree (message_id);
CREATE INDEX idx_msg_read_log_read_at ON scrm.scrm_message_read_log USING btree (read_at);
CREATE INDEX idx_msg_read_log_reader ON scrm.scrm_message_read_log USING btree (reader_id);

CREATE INDEX idx_msg_read_log_tracking ON scrm.scrm_message_read_log USING btree (message_tracking_id, read_at DESC);
CREATE INDEX idx_msg_recall_message_id ON scrm.scrm_message_recall USING btree (message_id);
CREATE INDEX idx_msg_recall_recalled_at ON scrm.scrm_message_recall USING btree (recalled_at);
CREATE INDEX idx_msg_recall_sender ON scrm.scrm_message_recall USING btree (sender_id);
CREATE INDEX idx_msg_recall_status ON scrm.scrm_message_recall USING btree (recall_status);

CREATE INDEX idx_msg_recall_tracking ON scrm.scrm_message_recall USING btree (message_tracking_id);
CREATE INDEX idx_msg_tracking_batch ON scrm.scrm_message_tracking USING btree (batch_id);
CREATE INDEX idx_msg_tracking_channel ON scrm.scrm_message_tracking USING btree (channel);
CREATE INDEX idx_msg_tracking_is_read ON scrm.scrm_message_tracking USING btree (is_read);
CREATE INDEX idx_msg_tracking_is_recalled ON scrm.scrm_message_tracking USING btree (is_recalled);
CREATE UNIQUE INDEX idx_msg_tracking_message_id ON scrm.scrm_message_tracking USING btree (message_id);
CREATE INDEX idx_msg_tracking_recipient ON scrm.scrm_message_tracking USING btree (recipient_id);
CREATE INDEX idx_msg_tracking_send_status ON scrm.scrm_message_tracking USING btree (send_status);
CREATE INDEX idx_msg_tracking_sender ON scrm.scrm_message_tracking USING btree (sender_id);
CREATE INDEX idx_msg_tracking_sent_at ON scrm.scrm_message_tracking USING btree (sent_at);

CREATE INDEX idx_notification_batch_status ON scrm.scrm_notification_batch USING btree (status);

CREATE INDEX idx_notification_batch_triggered ON scrm.scrm_notification_batch USING btree (triggered_by);
CREATE INDEX idx_notification_category ON scrm.scrm_notification USING btree (category);
CREATE INDEX idx_notification_channel ON scrm.scrm_notification USING btree (channel);

CREATE INDEX idx_notification_preference_ucc ON scrm.scrm_notification_preference USING btree (user_id, channel, category);
CREATE INDEX idx_notification_preference_user ON scrm.scrm_notification_preference USING btree (user_id);
CREATE INDEX idx_notification_recipient ON scrm.scrm_notification USING btree (recipient_id);
CREATE INDEX idx_notification_scheduled ON scrm.scrm_notification USING btree (status, scheduled_at);
CREATE INDEX idx_notification_sent ON scrm.scrm_notification USING btree (sent_at);
CREATE INDEX idx_notification_status ON scrm.scrm_notification USING btree (status);
CREATE INDEX idx_notification_template ON scrm.scrm_notification USING btree (template_id);
CREATE INDEX idx_notification_template_category ON scrm.scrm_notification_template USING btree (category);
CREATE INDEX idx_notification_template_channel ON scrm.scrm_notification_template USING btree (channel);
CREATE UNIQUE INDEX idx_notification_template_code ON scrm.scrm_notification_template USING btree (template_code);
CREATE INDEX idx_notification_template_enabled ON scrm.scrm_notification_template USING btree (enabled);

CREATE INDEX idx_nps_benchmark_generated ON scrm.scrm_nps_benchmark USING btree (generated_at);
CREATE INDEX idx_nps_benchmark_period_end ON scrm.scrm_nps_benchmark USING btree (period_end);
CREATE INDEX idx_nps_benchmark_period_start ON scrm.scrm_nps_benchmark USING btree (period_start);
CREATE INDEX idx_nps_benchmark_period_type ON scrm.scrm_nps_benchmark USING btree (period_type);

CREATE INDEX idx_opp_stage_hist_changed_at ON scrm.scrm_opportunity_stage_history USING btree (changed_at);
CREATE INDEX idx_opp_stage_hist_opp_id ON scrm.scrm_opportunity_stage_history USING btree (opportunity_id);

CREATE INDEX idx_opportunity_customer_id ON scrm.scrm_opportunity USING btree (customer_id);
CREATE INDEX idx_opportunity_funnel_id ON scrm.scrm_opportunity USING btree (funnel_id);
CREATE INDEX idx_opportunity_owner ON scrm.scrm_opportunity USING btree (owner_user_id);
CREATE INDEX idx_opportunity_stage_id ON scrm.scrm_opportunity USING btree (current_stage_id);
CREATE INDEX idx_opportunity_status ON scrm.scrm_opportunity USING btree (status);

CREATE INDEX idx_order_create_time ON scrm.scrm_order USING btree (create_time);
CREATE INDEX idx_order_customer ON scrm.scrm_order USING btree (customer_id);
CREATE INDEX idx_order_item_order ON scrm.scrm_order_item USING btree (order_id);
CREATE INDEX idx_order_item_product ON scrm.scrm_order_item USING btree (product_id);

CREATE INDEX idx_order_payment_status ON scrm.scrm_order USING btree (payment_status);
CREATE INDEX idx_order_status ON scrm.scrm_order USING btree (order_status);

CREATE INDEX idx_order_type ON scrm.scrm_order USING btree (order_type);
CREATE INDEX idx_persona_account_id ON scrm.scrm_persona USING btree (account_id);
CREATE INDEX idx_persona_persona_id ON scrm.scrm_persona USING btree (persona_id);

CREATE INDEX idx_platconfig_platform_type ON scrm.scrm_platform_config USING btree (platform_type);

CREATE INDEX idx_points_account_customer ON scrm.scrm_points_account USING btree (customer_id);
CREATE INDEX idx_points_account_level ON scrm.scrm_points_account USING btree (level);

CREATE INDEX idx_points_ex_record_customer ON scrm.scrm_points_exchange_record USING btree (customer_id);
CREATE INDEX idx_points_ex_record_exchange ON scrm.scrm_points_exchange_record USING btree (exchange_id);
CREATE INDEX idx_points_ex_record_status ON scrm.scrm_points_exchange_record USING btree (status);

CREATE INDEX idx_points_exchange_category ON scrm.scrm_points_exchange USING btree (item_category);
CREATE INDEX idx_points_exchange_status ON scrm.scrm_points_exchange USING btree (status);

CREATE INDEX idx_points_exchange_type ON scrm.scrm_points_exchange USING btree (exchange_type);
CREATE INDEX idx_points_rule_enabled ON scrm.scrm_points_rule USING btree (enabled);
CREATE INDEX idx_points_rule_event ON scrm.scrm_points_rule USING btree (trigger_event);

CREATE INDEX idx_points_rule_type ON scrm.scrm_points_rule USING btree (rule_type);
CREATE INDEX idx_points_txn_account ON scrm.scrm_points_transaction USING btree (account_id);
CREATE INDEX idx_points_txn_created ON scrm.scrm_points_transaction USING btree (created_at);
CREATE INDEX idx_points_txn_customer ON scrm.scrm_points_transaction USING btree (customer_id);
CREATE INDEX idx_points_txn_expires ON scrm.scrm_points_transaction USING btree (expires_at, expired);

CREATE INDEX idx_points_txn_type ON scrm.scrm_points_transaction USING btree (transaction_type);
CREATE INDEX idx_product_brand ON scrm.scrm_product USING btree (brand);
CREATE INDEX idx_product_category ON scrm.scrm_product USING btree (category);
CREATE INDEX idx_product_sku ON scrm.scrm_product USING btree (sku);
CREATE INDEX idx_product_status ON scrm.scrm_product USING btree (status);

CREATE INDEX idx_profile_comparison_customer1 ON scrm.scrm_profile_comparison USING btree (customer_id1);
CREATE INDEX idx_profile_comparison_customer2 ON scrm.scrm_profile_comparison USING btree (customer_id2);

CREATE INDEX idx_profile_comparison_type ON scrm.scrm_profile_comparison USING btree (comparison_type);
CREATE UNIQUE INDEX idx_profile_template_code ON scrm.scrm_profile_template USING btree (template_code);
CREATE INDEX idx_profile_template_enabled ON scrm.scrm_profile_template USING btree (enabled);

CREATE INDEX idx_public_sea_assigned_to ON scrm.scrm_public_sea_customer USING btree (assigned_to);
CREATE INDEX idx_public_sea_lifecycle ON scrm.scrm_public_sea_customer USING btree (lifecycle);
CREATE INDEX idx_public_sea_platform_uid ON scrm.scrm_public_sea_customer USING btree (platform_type, platform_customer_uid);
CREATE INDEX idx_public_sea_status ON scrm.scrm_public_sea_customer USING btree (status);
CREATE INDEX idx_public_sea_expire ON scrm.scrm_public_sea_customer USING btree (assignment_expire_at);

CREATE INDEX idx_qi_result_assignee ON scrm.scrm_quality_inspection_result USING btree (assignee_id);
CREATE INDEX idx_qi_result_conv ON scrm.scrm_quality_inspection_result USING btree (conversation_id);
CREATE INDEX idx_qi_result_inspected ON scrm.scrm_quality_inspection_result USING btree (inspected_at);
CREATE INDEX idx_qi_result_task ON scrm.scrm_quality_inspection_result USING btree (task_id);
CREATE INDEX idx_qi_rule_category ON scrm.scrm_quality_inspection_rule USING btree (category);
CREATE INDEX idx_qi_rule_enabled ON scrm.scrm_quality_inspection_rule USING btree (enabled);
CREATE INDEX idx_qi_task_status ON scrm.scrm_quality_inspection_task USING btree (status);
CREATE INDEX idx_quick_reply_category ON scrm.scrm_quick_reply USING btree (category_id);
CREATE INDEX idx_quick_reply_category_platform ON scrm.scrm_quick_reply_category USING btree (platform_type);

CREATE INDEX idx_quick_reply_owner ON scrm.scrm_quick_reply USING btree (owner_user_id);
CREATE INDEX idx_quick_reply_personal ON scrm.scrm_quick_reply USING btree (is_personal);
CREATE INDEX idx_quick_reply_platform ON scrm.scrm_quick_reply USING btree (platform_type);
CREATE INDEX idx_quick_reply_shortcut ON scrm.scrm_quick_reply USING btree (shortcut);
CREATE INDEX idx_quick_reply_status ON scrm.scrm_quick_reply USING btree (status);

CREATE INDEX idx_referral_channel ON scrm.scrm_referral USING btree (referral_channel);
CREATE INDEX idx_referral_created ON scrm.scrm_referral USING btree (create_time);
CREATE INDEX idx_referral_program ON scrm.scrm_referral USING btree (program_id);
CREATE INDEX idx_referral_program_created ON scrm.scrm_referral_program USING btree (create_time);
CREATE INDEX idx_referral_program_dates ON scrm.scrm_referral_program USING btree (start_date, end_date);
CREATE INDEX idx_referral_program_status ON scrm.scrm_referral_program USING btree (status);

CREATE INDEX idx_referral_program_type ON scrm.scrm_referral_program USING btree (program_type);
CREATE INDEX idx_referral_referee ON scrm.scrm_referral USING btree (referee_customer_id);
CREATE INDEX idx_referral_referrer ON scrm.scrm_referral USING btree (referrer_customer_id);
CREATE INDEX idx_referral_reward_created ON scrm.scrm_referral_reward USING btree (create_time);
CREATE INDEX idx_referral_reward_program ON scrm.scrm_referral_reward USING btree (program_id);
CREATE INDEX idx_referral_reward_recipient ON scrm.scrm_referral_reward USING btree (recipient_customer_id);
CREATE INDEX idx_referral_reward_referral ON scrm.scrm_referral_reward USING btree (referral_id);
CREATE INDEX idx_referral_reward_status ON scrm.scrm_referral_reward USING btree (status);

CREATE INDEX idx_referral_reward_type ON scrm.scrm_referral_reward USING btree (reward_type);
CREATE INDEX idx_referral_status ON scrm.scrm_referral USING btree (status);

CREATE INDEX idx_report_result_run_at ON scrm.scrm_report_result USING btree (run_at);
CREATE INDEX idx_report_result_template_id ON scrm.scrm_report_result USING btree (template_id);

CREATE INDEX idx_report_template_public ON scrm.scrm_report_template USING btree (is_public);

CREATE INDEX idx_report_template_type ON scrm.scrm_report_template USING btree (report_type);
CREATE INDEX idx_rfm_analysis_category ON scrm.scrm_rfm_analysis USING btree (segment_category);
CREATE INDEX idx_rfm_analysis_config ON scrm.scrm_rfm_analysis USING btree (config_id);
CREATE INDEX idx_rfm_analysis_customer ON scrm.scrm_rfm_analysis USING btree (customer_id);
CREATE INDEX idx_rfm_analysis_segment ON scrm.scrm_rfm_analysis USING btree (rfm_segment);
CREATE INDEX idx_rfm_analysis_value ON scrm.scrm_rfm_analysis USING btree (value_score DESC);
CREATE INDEX idx_rfm_config_default ON scrm.scrm_rfm_config USING btree (is_default);
CREATE INDEX idx_rfm_config_enabled ON scrm.scrm_rfm_config USING btree (enabled);

CREATE INDEX idx_rfm_strategy_category ON scrm.scrm_rfm_segment_strategy USING btree (segment_category);
CREATE INDEX idx_rfm_strategy_enabled ON scrm.scrm_rfm_segment_strategy USING btree (enabled);

CREATE INDEX idx_risk_event_category ON scrm.scrm_risk_event USING btree (risk_category);
CREATE INDEX idx_risk_event_customer ON scrm.scrm_risk_event USING btree (customer_id);
CREATE INDEX idx_risk_event_level ON scrm.scrm_risk_event USING btree (risk_level);
CREATE INDEX idx_risk_event_rule ON scrm.scrm_risk_event USING btree (rule_id);
CREATE INDEX idx_risk_event_status ON scrm.scrm_risk_event USING btree (status);

CREATE INDEX idx_risk_event_trigger_time ON scrm.scrm_risk_event USING btree (trigger_time);
CREATE UNIQUE INDEX idx_risk_rule_code ON scrm.scrm_risk_rule USING btree (rule_code);
CREATE INDEX idx_risk_rule_enabled ON scrm.scrm_risk_rule USING btree (enabled);
CREATE INDEX idx_risk_rule_priority ON scrm.scrm_risk_rule USING btree (priority);

CREATE INDEX idx_risk_signal_account ON scrm.scrm_risk_signal USING btree (account_id);
CREATE INDEX idx_risk_signal_persona ON scrm.scrm_risk_signal USING btree (persona_id);
CREATE INDEX idx_risk_signal_status ON scrm.scrm_risk_signal USING btree (status);

CREATE INDEX idx_risk_signal_triggered_at ON scrm.scrm_risk_signal USING btree (triggered_at);
CREATE INDEX idx_sales_achievement_date ON scrm.scrm_sales_achievement USING btree (achievement_date);
CREATE INDEX idx_sales_achievement_source ON scrm.scrm_sales_achievement USING btree (source_type);
CREATE INDEX idx_sales_achievement_target ON scrm.scrm_sales_achievement USING btree (target_id);
CREATE INDEX idx_sales_ranking_date ON scrm.scrm_sales_ranking USING btree (ranking_date);
CREATE INDEX idx_sales_ranking_period ON scrm.scrm_sales_ranking USING btree (period_type, period_start, period_end, metric_type, target_type);
CREATE INDEX idx_sales_ranking_target ON scrm.scrm_sales_ranking USING btree (target_type, target_id);
CREATE INDEX idx_sales_speech_difficulty ON scrm.scrm_sales_speech USING btree (difficulty_level);
CREATE INDEX idx_sales_speech_enabled ON scrm.scrm_sales_speech USING btree (enabled);
CREATE INDEX idx_sales_speech_recommended ON scrm.scrm_sales_speech USING btree (is_recommended);
CREATE INDEX idx_sales_speech_scenario ON scrm.scrm_sales_speech USING btree (scenario_id);
CREATE INDEX idx_sales_speech_style ON scrm.scrm_sales_speech USING btree (speech_style);

CREATE INDEX idx_sales_speech_type ON scrm.scrm_sales_speech USING btree (speech_type);
CREATE INDEX idx_sales_speech_verified ON scrm.scrm_sales_speech USING btree (is_verified);
CREATE INDEX idx_sales_target_assignee ON scrm.scrm_sales_target USING btree (target_type, target_id);
CREATE INDEX idx_sales_target_metric ON scrm.scrm_sales_target USING btree (metric_type);
CREATE INDEX idx_sales_target_period ON scrm.scrm_sales_target USING btree (period_type, period_start, period_end);
CREATE INDEX idx_sales_target_status ON scrm.scrm_sales_target USING btree (status);

CREATE INDEX idx_scheduled_task_code ON scrm.scrm_scheduled_task USING btree (task_code);
CREATE INDEX idx_scheduled_task_enabled ON scrm.scrm_scheduled_task USING btree (is_enabled);
CREATE INDEX idx_scheduled_task_next_scheduled ON scrm.scrm_scheduled_task USING btree (next_scheduled_at);

CREATE INDEX idx_scheduled_task_category ON scrm.scrm_scheduled_task USING btree (task_category);
CREATE INDEX idx_scheduled_task_status ON scrm.scrm_scheduled_task USING btree (status);
CREATE INDEX idx_scheduled_task_type ON scrm.scrm_scheduled_task USING btree (task_type);
CREATE INDEX idx_segment_category ON scrm.scrm_segment USING btree (category);
CREATE UNIQUE INDEX idx_segment_code ON scrm.scrm_segment USING btree (segment_code);
CREATE INDEX idx_segment_history_segment ON scrm.scrm_segment_history USING btree (segment_id);

CREATE UNIQUE INDEX idx_segment_history_unique ON scrm.scrm_segment_history USING btree (segment_id, snapshot_date);
CREATE INDEX idx_segment_member_current ON scrm.scrm_segment_member USING btree (is_current_member);
CREATE INDEX idx_segment_member_customer ON scrm.scrm_segment_member USING btree (customer_id);
CREATE INDEX idx_segment_member_segment ON scrm.scrm_segment_member USING btree (segment_id);

CREATE UNIQUE INDEX idx_segment_member_unique ON scrm.scrm_segment_member USING btree (segment_id, customer_id);
CREATE INDEX idx_segment_status ON scrm.scrm_segment USING btree (status);

CREATE INDEX idx_segment_type ON scrm.scrm_segment USING btree (segment_type);
CREATE INDEX idx_speech_category ON scrm.scrm_speech USING btree (category_id);
CREATE INDEX idx_speech_category_parent ON scrm.scrm_speech_category USING btree (parent_id);

CREATE INDEX idx_speech_platform ON scrm.scrm_speech USING btree (platform_type);
CREATE INDEX idx_speech_rec_customer ON scrm.scrm_speech_recommendation USING btree (customer_id);
CREATE INDEX idx_speech_rec_feedback ON scrm.scrm_speech_recommendation USING btree (feedback);
CREATE INDEX idx_speech_rec_outcome ON scrm.scrm_speech_recommendation USING btree (outcome);
CREATE INDEX idx_speech_rec_scenario ON scrm.scrm_speech_recommendation USING btree (scenario_id);

CREATE INDEX idx_speech_rec_time ON scrm.scrm_speech_recommendation USING btree (recommended_at);
CREATE INDEX idx_speech_scenario ON scrm.scrm_speech USING btree (scenario);
CREATE INDEX idx_speech_scenario_category ON scrm.scrm_speech_scenario USING btree (scenario_category);
CREATE UNIQUE INDEX idx_speech_scenario_code ON scrm.scrm_speech_scenario USING btree (scenario_code);
CREATE INDEX idx_speech_scenario_enabled ON scrm.scrm_speech_scenario USING btree (enabled);
CREATE INDEX idx_speech_scenario_stage ON scrm.scrm_speech_scenario USING btree (customer_stage);

CREATE INDEX idx_speech_status ON scrm.scrm_speech USING btree (status);

CREATE INDEX idx_survey_end_date ON scrm.scrm_survey USING btree (end_date);
CREATE INDEX idx_survey_invitation_channel ON scrm.scrm_survey_invitation USING btree (channel);
CREATE INDEX idx_survey_invitation_customer ON scrm.scrm_survey_invitation USING btree (customer_id);
CREATE INDEX idx_survey_invitation_expired ON scrm.scrm_survey_invitation USING btree (expired_at);
CREATE INDEX idx_survey_invitation_sent ON scrm.scrm_survey_invitation USING btree (sent_at);
CREATE INDEX idx_survey_invitation_status ON scrm.scrm_survey_invitation USING btree (status);
CREATE INDEX idx_survey_invitation_survey ON scrm.scrm_survey_invitation USING btree (survey_id);

CREATE INDEX idx_survey_response_customer ON scrm.scrm_survey_response USING btree (customer_id);
CREATE INDEX idx_survey_response_follow_up ON scrm.scrm_survey_response USING btree (follow_up_required);
CREATE INDEX idx_survey_response_invitation ON scrm.scrm_survey_response USING btree (invitation_id);
CREATE INDEX idx_survey_response_nps ON scrm.scrm_survey_response USING btree (nps_score);
CREATE INDEX idx_survey_response_sentiment ON scrm.scrm_survey_response USING btree (sentiment);
CREATE INDEX idx_survey_response_submitted ON scrm.scrm_survey_response USING btree (submitted_at);
CREATE INDEX idx_survey_response_survey ON scrm.scrm_survey_response USING btree (survey_id);

CREATE INDEX idx_survey_start_date ON scrm.scrm_survey USING btree (start_date);
CREATE INDEX idx_survey_status ON scrm.scrm_survey USING btree (status);

CREATE INDEX idx_survey_trigger_event ON scrm.scrm_survey USING btree (trigger_event);
CREATE INDEX idx_survey_type ON scrm.scrm_survey USING btree (survey_type);
CREATE INDEX idx_system_config_enabled ON scrm.scrm_system_config USING btree (enabled);
CREATE INDEX idx_system_config_env ON scrm.scrm_system_config USING btree (environment);
CREATE INDEX idx_system_config_group ON scrm.scrm_system_config USING btree (config_group);
CREATE UNIQUE INDEX idx_system_config_key ON scrm.scrm_system_config USING btree (config_key);
CREATE INDEX idx_system_config_overridable ON scrm.scrm_system_config USING btree (is_overridable);
CREATE INDEX idx_system_config_sensitive ON scrm.scrm_system_config USING btree (is_sensitive);

CREATE INDEX idx_system_config_type ON scrm.scrm_system_config USING btree (config_type);
CREATE UNIQUE INDEX idx_tag_code ON scrm.scrm_tag USING btree (tag_code);
CREATE INDEX idx_tag_customer_customer ON scrm.scrm_tag_customer USING btree (customer_id);
CREATE INDEX idx_tag_customer_expires ON scrm.scrm_tag_customer USING btree (expires_at);
CREATE INDEX idx_tag_customer_source ON scrm.scrm_tag_customer USING btree (tag_source);
CREATE INDEX idx_tag_customer_tag ON scrm.scrm_tag_customer USING btree (tag_id);

CREATE UNIQUE INDEX idx_tag_customer_unique ON scrm.scrm_tag_customer USING btree (customer_id, tag_id);
CREATE INDEX idx_tag_enabled ON scrm.scrm_tag USING btree (enabled);
CREATE INDEX idx_tag_group ON scrm.scrm_tag USING btree (group_id);
CREATE UNIQUE INDEX idx_tag_group_code ON scrm.scrm_tag_group USING btree (group_code);
CREATE INDEX idx_tag_group_enabled ON scrm.scrm_tag_group USING btree (enabled);
CREATE INDEX idx_tag_group_sort ON scrm.scrm_tag_group USING btree (sort_order);

CREATE INDEX idx_tag_rule_freq ON scrm.scrm_tag_rule USING btree (execution_frequency);
CREATE INDEX idx_tag_rule_status ON scrm.scrm_tag_rule USING btree (status);
CREATE INDEX idx_tag_rule_tag ON scrm.scrm_tag_rule USING btree (tag_id);

CREATE INDEX idx_tag_type ON scrm.scrm_tag USING btree (tag_type);
CREATE INDEX idx_task_dependency_depends_on ON scrm.scrm_task_dependency USING btree (depends_on_task_id);
CREATE INDEX idx_task_dependency_task ON scrm.scrm_task_dependency USING btree (task_id);

CREATE INDEX idx_task_execution_no ON scrm.scrm_task_execution USING btree (execution_no);
CREATE INDEX idx_task_execution_started ON scrm.scrm_task_execution USING btree (started_at);
CREATE INDEX idx_task_execution_status ON scrm.scrm_task_execution USING btree (status);
CREATE INDEX idx_task_execution_task ON scrm.scrm_task_execution USING btree (task_id);

CREATE INDEX idx_task_execution_trigger ON scrm.scrm_task_execution USING btree (trigger_type);

CREATE INDEX idx_ticket_assignee ON scrm.scrm_ticket USING btree (assignee_id);
CREATE INDEX idx_ticket_category ON scrm.scrm_ticket USING btree (category);
CREATE INDEX idx_ticket_comment_created ON scrm.scrm_ticket_comment USING btree (created_at);

CREATE INDEX idx_ticket_comment_ticket ON scrm.scrm_ticket_comment USING btree (ticket_id);
CREATE INDEX idx_ticket_comment_type ON scrm.scrm_ticket_comment USING btree (comment_type);
CREATE INDEX idx_ticket_customer ON scrm.scrm_ticket USING btree (customer_id);
CREATE INDEX idx_ticket_history_action ON scrm.scrm_ticket_history USING btree (action_type);

CREATE INDEX idx_ticket_history_ticket ON scrm.scrm_ticket_history USING btree (ticket_id);
CREATE INDEX idx_ticket_history_time ON scrm.scrm_ticket_history USING btree (action_time);
CREATE INDEX idx_ticket_priority ON scrm.scrm_ticket USING btree (priority);
CREATE INDEX idx_ticket_sla ON scrm.scrm_ticket USING btree (status, sla_due_at);
CREATE INDEX idx_ticket_status ON scrm.scrm_ticket USING btree (status);

CREATE INDEX idx_touchpoint_active ON scrm.scrm_touchpoint USING btree (is_active);
CREATE UNIQUE INDEX idx_touchpoint_code ON scrm.scrm_touchpoint USING btree (touchpoint_code);

CREATE INDEX idx_touchpoint_type ON scrm.scrm_touchpoint USING btree (touchpoint_type);
CREATE INDEX idx_user_account_department_id ON scrm.scrm_user_account USING btree (department_id);

CREATE INDEX idx_user_account_user_id ON scrm.scrm_user_account USING btree (user_id);
CREATE INDEX idx_user_id ON scrm.scrm_user_device USING btree (user_id);
CREATE INDEX idx_user_status ON scrm.scrm_user USING btree (status);
CREATE INDEX idx_visit_plan_assigned ON scrm.scrm_visit_plan USING btree (assigned_to);
CREATE INDEX idx_visit_plan_status ON scrm.scrm_visit_plan USING btree (status);
CREATE INDEX idx_visit_plan_template ON scrm.scrm_visit_plan USING btree (template_id);

CREATE INDEX idx_visit_plan_type ON scrm.scrm_visit_plan USING btree (plan_type);
CREATE INDEX idx_visit_task_assigned ON scrm.scrm_visit_task USING btree (assigned_to);
CREATE INDEX idx_visit_task_customer ON scrm.scrm_visit_task USING btree (customer_id);
CREATE INDEX idx_visit_task_date ON scrm.scrm_visit_task USING btree (scheduled_date);
CREATE INDEX idx_visit_task_plan ON scrm.scrm_visit_task USING btree (plan_id);
CREATE INDEX idx_visit_task_status ON scrm.scrm_visit_task USING btree (status);

CREATE INDEX idx_visit_task_type ON scrm.scrm_visit_task USING btree (visit_type);
CREATE INDEX idx_visit_template_enabled ON scrm.scrm_visit_template USING btree (enabled);
CREATE INDEX idx_visit_template_method ON scrm.scrm_visit_template USING btree (visit_method);

CREATE INDEX idx_visit_template_type ON scrm.scrm_visit_template USING btree (visit_type);
CREATE INDEX idx_voc_insight_impact ON scrm.scrm_voc_insight USING btree (impact_level);
CREATE INDEX idx_voc_insight_period ON scrm.scrm_voc_insight USING btree (period);
CREATE INDEX idx_voc_insight_published_at ON scrm.scrm_voc_insight USING btree (published_at);
CREATE INDEX idx_voc_insight_status ON scrm.scrm_voc_insight USING btree (status);

CREATE INDEX idx_voc_insight_type ON scrm.scrm_voc_insight USING btree (insight_type);
CREATE INDEX idx_voc_topic_category ON scrm.scrm_voc_topic USING btree (category);
CREATE INDEX idx_voc_topic_emerging ON scrm.scrm_voc_topic USING btree (is_emerging);
CREATE INDEX idx_voc_topic_hot ON scrm.scrm_voc_topic USING btree (is_hot_topic);
CREATE INDEX idx_voc_topic_parent ON scrm.scrm_voc_topic USING btree (parent_topic_id);
CREATE INDEX idx_voc_topic_priority_score ON scrm.scrm_voc_topic USING btree (priority_score);

CREATE INDEX idx_voc_voice_assigned ON scrm.scrm_voc_voice USING btree (assigned_to);
CREATE INDEX idx_voc_voice_category ON scrm.scrm_voc_voice USING btree (category);
CREATE INDEX idx_voc_voice_collected ON scrm.scrm_voc_voice USING btree (collected_at);
CREATE INDEX idx_voc_voice_customer ON scrm.scrm_voc_voice USING btree (customer_id);
CREATE INDEX idx_voc_voice_priority ON scrm.scrm_voc_voice USING btree (priority);
CREATE INDEX idx_voc_voice_sentiment ON scrm.scrm_voc_voice USING btree (sentiment);
CREATE INDEX idx_voc_voice_source ON scrm.scrm_voc_voice USING btree (source);
CREATE INDEX idx_voc_voice_status ON scrm.scrm_voc_voice USING btree (status);

CREATE INDEX idx_voc_voice_type ON scrm.scrm_voc_voice USING btree (voice_type);
CREATE INDEX idx_webhook_config_last_trigger ON scrm.scrm_webhook_config USING btree (last_trigger_at);
CREATE INDEX idx_webhook_config_status ON scrm.scrm_webhook_config USING btree (status);

CREATE INDEX idx_webhook_log_event_type ON scrm.scrm_webhook_log USING btree (event_type);
CREATE INDEX idx_webhook_log_next_retry ON scrm.scrm_webhook_log USING btree (next_retry_at);
CREATE INDEX idx_webhook_log_status ON scrm.scrm_webhook_log USING btree (status);

CREATE INDEX idx_webhook_log_webhook_id ON scrm.scrm_webhook_log USING btree (webhook_id);
CREATE INDEX idx_welcome_message_account ON scrm.scrm_welcome_message USING btree (account_id);
CREATE INDEX idx_welcome_message_channel_code ON scrm.scrm_welcome_message USING btree (channel_code_id);
CREATE INDEX idx_welcome_message_platform ON scrm.scrm_welcome_message USING btree (platform_type);
CREATE INDEX idx_welcome_message_status ON scrm.scrm_welcome_message USING btree (status);

CREATE INDEX idx_wework_archive_config_corp ON scrm.scrm_wework_archive_config USING btree (corp_id);
CREATE INDEX idx_wework_archive_config_status ON scrm.scrm_wework_archive_config USING btree (status);

CREATE INDEX idx_wework_archive_cursor_config ON scrm.scrm_wework_archive_cursor USING btree (config_id);
CREATE INDEX idx_wework_archive_cursor_status ON scrm.scrm_wework_archive_cursor USING btree (status);

CREATE INDEX idx_wework_archive_msg_config ON scrm.scrm_wework_archive_message USING btree (config_id);
CREATE INDEX idx_wework_archive_msg_from ON scrm.scrm_wework_archive_message USING btree (from_user);
CREATE INDEX idx_wework_archive_msg_msgid ON scrm.scrm_wework_archive_message USING btree (msg_id);
CREATE INDEX idx_wework_archive_msg_room ON scrm.scrm_wework_archive_message USING btree (room_id);
CREATE INDEX idx_wework_archive_msg_sent_at ON scrm.scrm_wework_archive_message USING btree (sent_at);
CREATE INDEX idx_wework_archive_msg_seq ON scrm.scrm_wework_archive_message USING btree (seq);

CREATE INDEX idx_wework_archive_msg_sent ON scrm.scrm_wework_archive_message USING btree (sent_at);
CREATE INDEX idx_wework_archive_msg_type ON scrm.scrm_wework_archive_message USING btree (msg_type);
CREATE INDEX idx_wf_instance_customer ON scrm.scrm_workflow_instance USING btree (customer_id);
CREATE INDEX idx_wf_instance_next_exec ON scrm.scrm_workflow_instance USING btree (next_execution_at);
CREATE INDEX idx_wf_instance_status ON scrm.scrm_workflow_instance USING btree (status);

CREATE INDEX idx_wf_instance_workflow ON scrm.scrm_workflow_instance USING btree (workflow_id);
CREATE INDEX idx_wf_nodelog_instance ON scrm.scrm_workflow_node_log USING btree (instance_id);
CREATE INDEX idx_wf_nodelog_node ON scrm.scrm_workflow_node_log USING btree (workflow_id, node_id);
CREATE INDEX idx_wf_nodelog_status ON scrm.scrm_workflow_node_log USING btree (status);

CREATE INDEX idx_wf_nodelog_workflow ON scrm.scrm_workflow_node_log USING btree (workflow_id);
CREATE INDEX idx_work_order_assignee ON scrm.scrm_work_order USING btree (assigned_to_id);
CREATE INDEX idx_work_order_customer ON scrm.scrm_work_order USING btree (customer_id);
CREATE INDEX idx_work_order_log_operator ON scrm.scrm_work_order_log USING btree (operator_id);
CREATE INDEX idx_work_order_log_order ON scrm.scrm_work_order_log USING btree (order_id);

CREATE INDEX idx_work_order_log_time ON scrm.scrm_work_order_log USING btree (create_time);
CREATE INDEX idx_work_order_log_type ON scrm.scrm_work_order_log USING btree (log_type);
CREATE UNIQUE INDEX idx_work_order_no ON scrm.scrm_work_order USING btree (order_no);
CREATE INDEX idx_work_order_priority ON scrm.scrm_work_order USING btree (priority);
CREATE INDEX idx_work_order_sla ON scrm.scrm_work_order USING btree (sla_breached, sla_resolution_due);
CREATE UNIQUE INDEX idx_work_order_sla_code ON scrm.scrm_work_order_sla USING btree (policy_code);
CREATE INDEX idx_work_order_sla_default ON scrm.scrm_work_order_sla USING btree (is_default);
CREATE INDEX idx_work_order_sla_enabled ON scrm.scrm_work_order_sla USING btree (enabled);
CREATE UNIQUE INDEX idx_work_order_sla_name ON scrm.scrm_work_order_sla USING btree (policy_name);

CREATE INDEX idx_work_order_status ON scrm.scrm_work_order USING btree (order_status);

CREATE INDEX idx_work_order_type ON scrm.scrm_work_order USING btree (order_type);
CREATE UNIQUE INDEX idx_workflow_code ON scrm.scrm_workflow USING btree (workflow_code);
CREATE INDEX idx_workflow_status ON scrm.scrm_workflow USING btree (status);

CREATE INDEX idx_workflow_trigger ON scrm.scrm_workflow USING btree (trigger_type);
CREATE INDEX idx_workflow_type ON scrm.scrm_workflow USING btree (workflow_type);
CREATE INDEX scrm_conversation_message_202607_conversation_id_idx ON scrm.scrm_conversation_message_202607 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202607_message_id_idx ON scrm.scrm_conversation_message_202607 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202607_platform_message_id_idx ON scrm.scrm_conversation_message_202607 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202607_sent_at_idx ON scrm.scrm_conversation_message_202607 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202608_conversation_id_idx ON scrm.scrm_conversation_message_202608 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202608_message_id_idx ON scrm.scrm_conversation_message_202608 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202608_platform_message_id_idx ON scrm.scrm_conversation_message_202608 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202608_sent_at_idx ON scrm.scrm_conversation_message_202608 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202609_conversation_id_idx ON scrm.scrm_conversation_message_202609 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202609_message_id_idx ON scrm.scrm_conversation_message_202609 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202609_platform_message_id_idx ON scrm.scrm_conversation_message_202609 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202609_sent_at_idx ON scrm.scrm_conversation_message_202609 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202610_conversation_id_idx ON scrm.scrm_conversation_message_202610 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202610_message_id_idx ON scrm.scrm_conversation_message_202610 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202610_platform_message_id_idx ON scrm.scrm_conversation_message_202610 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202610_sent_at_idx ON scrm.scrm_conversation_message_202610 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202611_conversation_id_idx ON scrm.scrm_conversation_message_202611 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202611_message_id_idx ON scrm.scrm_conversation_message_202611 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202611_platform_message_id_idx ON scrm.scrm_conversation_message_202611 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202611_sent_at_idx ON scrm.scrm_conversation_message_202611 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202612_conversation_id_idx ON scrm.scrm_conversation_message_202612 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202612_message_id_idx ON scrm.scrm_conversation_message_202612 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202612_platform_message_id_idx ON scrm.scrm_conversation_message_202612 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202612_sent_at_idx ON scrm.scrm_conversation_message_202612 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202701_conversation_id_idx ON scrm.scrm_conversation_message_202701 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202701_message_id_idx ON scrm.scrm_conversation_message_202701 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202701_platform_message_id_idx ON scrm.scrm_conversation_message_202701 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202701_sent_at_idx ON scrm.scrm_conversation_message_202701 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202702_conversation_id_idx ON scrm.scrm_conversation_message_202702 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202702_message_id_idx ON scrm.scrm_conversation_message_202702 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202702_platform_message_id_idx ON scrm.scrm_conversation_message_202702 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202702_sent_at_idx ON scrm.scrm_conversation_message_202702 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202703_conversation_id_idx ON scrm.scrm_conversation_message_202703 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202703_message_id_idx ON scrm.scrm_conversation_message_202703 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202703_platform_message_id_idx ON scrm.scrm_conversation_message_202703 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202703_sent_at_idx ON scrm.scrm_conversation_message_202703 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202704_conversation_id_idx ON scrm.scrm_conversation_message_202704 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202704_message_id_idx ON scrm.scrm_conversation_message_202704 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202704_platform_message_id_idx ON scrm.scrm_conversation_message_202704 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202704_sent_at_idx ON scrm.scrm_conversation_message_202704 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202705_conversation_id_idx ON scrm.scrm_conversation_message_202705 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202705_message_id_idx ON scrm.scrm_conversation_message_202705 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202705_platform_message_id_idx ON scrm.scrm_conversation_message_202705 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202705_sent_at_idx ON scrm.scrm_conversation_message_202705 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_202706_conversation_id_idx ON scrm.scrm_conversation_message_202706 USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_202706_message_id_idx ON scrm.scrm_conversation_message_202706 USING btree (message_id);
CREATE INDEX scrm_conversation_message_202706_platform_message_id_idx ON scrm.scrm_conversation_message_202706 USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_202706_sent_at_idx ON scrm.scrm_conversation_message_202706 USING btree (sent_at);
CREATE INDEX scrm_conversation_message_default_conversation_id_idx ON scrm.scrm_conversation_message_default USING btree (conversation_id);
CREATE INDEX scrm_conversation_message_default_message_id_idx ON scrm.scrm_conversation_message_default USING btree (message_id);
CREATE INDEX scrm_conversation_message_default_platform_message_id_idx ON scrm.scrm_conversation_message_default USING btree (platform_message_id);
CREATE INDEX scrm_conversation_message_default_sent_at_idx ON scrm.scrm_conversation_message_default USING btree (sent_at);
CREATE UNIQUE INDEX uk_api_app_client ON scrm.scrm_api_app USING btree (client_id);
CREATE UNIQUE INDEX uk_api_app_code ON scrm.scrm_api_app USING btree (app_code);
CREATE UNIQUE INDEX uk_api_key_value ON scrm.scrm_api_key USING btree (api_key);
CREATE UNIQUE INDEX uk_api_scope_name ON scrm.scrm_api_scope USING btree (scope_name);
CREATE UNIQUE INDEX uk_asset_category_code ON scrm.scrm_asset_category USING btree (category_code);
CREATE UNIQUE INDEX uk_asset_code ON scrm.scrm_asset USING btree (asset_code);
CREATE UNIQUE INDEX uk_blacklist_rule_code ON scrm.scrm_blacklist_rule USING btree (rule_code);
CREATE UNIQUE INDEX uk_contract_no ON scrm.scrm_contract USING btree (contract_no);
CREATE UNIQUE INDEX uk_contract_template_code ON scrm.scrm_contract_template USING btree (template_code);
CREATE UNIQUE INDEX uk_coupon_code ON scrm.scrm_coupon USING btree (coupon_code);
CREATE UNIQUE INDEX uk_data_dictionary_item_code ON scrm.scrm_data_dictionary_item USING btree (dict_code, item_code) WHERE (item_code IS NOT NULL);
CREATE UNIQUE INDEX uk_data_dictionary_code ON scrm.scrm_data_dictionary USING btree (dict_code);
CREATE UNIQUE INDEX uk_ecs_mapping_platform_ext ON scrm.scrm_external_contact_mapping USING btree (platform, external_contact_id);
CREATE UNIQUE INDEX uk_feedback_category_code ON scrm.scrm_feedback_category USING btree (category_code);
CREATE UNIQUE INDEX uk_feedback_no ON scrm.scrm_feedback USING btree (feedback_no);
CREATE UNIQUE INDEX uk_interaction_calendar_code ON scrm.scrm_interaction_calendar USING btree (calendar_code);
CREATE UNIQUE INDEX uk_interaction_plan_code ON scrm.scrm_interaction_plan USING btree (plan_code);
CREATE UNIQUE INDEX uk_invoice_no ON scrm.scrm_invoice USING btree (invoice_no);
CREATE UNIQUE INDEX uk_invoice_template_code ON scrm.scrm_invoice_template USING btree (template_code);
CREATE UNIQUE INDEX uk_order_no ON scrm.scrm_order USING btree (order_no);
CREATE UNIQUE INDEX uk_product_code ON scrm.scrm_product USING btree (product_code);
CREATE UNIQUE INDEX uk_referral_code ON scrm.scrm_referral USING btree (referral_code);
CREATE UNIQUE INDEX uk_referral_program_code ON scrm.scrm_referral_program USING btree (program_code);
CREATE UNIQUE INDEX uk_risk_event_no ON scrm.scrm_risk_event USING btree (event_no);
CREATE UNIQUE INDEX uk_survey_invitation_code ON scrm.scrm_survey_invitation USING btree (invitation_code);
CREATE UNIQUE INDEX uk_ticket_no ON scrm.scrm_ticket USING btree (ticket_no);
CREATE UNIQUE INDEX uk_user_client ON scrm.scrm_user_device USING btree (user_id, client_id);
CREATE UNIQUE INDEX uk_user_username ON scrm.scrm_user USING btree (username);
CREATE UNIQUE INDEX uk_visit_plan_code ON scrm.scrm_visit_plan USING btree (plan_code);
CREATE UNIQUE INDEX uk_visit_task_no ON scrm.scrm_visit_task USING btree (task_no);
CREATE UNIQUE INDEX uk_visit_template_code ON scrm.scrm_visit_template USING btree (template_code);
CREATE UNIQUE INDEX uk_voc_topic_code ON scrm.scrm_voc_topic USING btree (topic_code);
CREATE UNIQUE INDEX uk_voc_voice_no ON scrm.scrm_voc_voice USING btree (voice_no);
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202607_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202607_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202607_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202607_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202607_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202608_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202608_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202608_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202608_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202608_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202609_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202609_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202609_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202609_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202609_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202610_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202610_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202610_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202610_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202610_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202611_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202611_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202611_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202611_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202611_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202612_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202612_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202612_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202612_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202612_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202701_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202701_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202701_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202701_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202701_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202702_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202702_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202702_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202702_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202702_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202703_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202703_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202703_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202703_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202703_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202704_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202704_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202704_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202704_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202704_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202705_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202705_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202705_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202705_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202705_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_202706_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_202706_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_202706_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_202706_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_202706_sent_at_idx;
ALTER INDEX scrm.idx_message_conversation ATTACH PARTITION scrm.scrm_conversation_message_default_conversation_id_idx;
ALTER INDEX scrm.idx_message_message_id ATTACH PARTITION scrm.scrm_conversation_message_default_message_id_idx;
ALTER INDEX scrm.scrm_conversation_message_pkey ATTACH PARTITION scrm.scrm_conversation_message_default_pkey;
ALTER INDEX scrm.idx_message_platform_message_id ATTACH PARTITION scrm.scrm_conversation_message_default_platform_message_id_idx;
ALTER INDEX scrm.idx_message_sent_at ATTACH PARTITION scrm.scrm_conversation_message_default_sent_at_idx;
ALTER TABLE ONLY scrm.scrm_account
    ADD CONSTRAINT fk_account_persona FOREIGN KEY (persona_id) REFERENCES scrm.scrm_persona(persona_id) ON DELETE SET NULL;
ALTER TABLE ONLY scrm.scrm_campaign_account
    ADD CONSTRAINT fk_campaign_account_account FOREIGN KEY (account_id) REFERENCES scrm.scrm_account(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_campaign_account
    ADD CONSTRAINT fk_campaign_account_campaign FOREIGN KEY (campaign_id) REFERENCES scrm.scrm_campaign(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_conversation
    ADD CONSTRAINT fk_conversation_account FOREIGN KEY (account_id) REFERENCES scrm.scrm_account(id) ON DELETE RESTRICT;
ALTER TABLE ONLY scrm.scrm_conversation
    ADD CONSTRAINT fk_conversation_customer FOREIGN KEY (customer_id) REFERENCES scrm.scrm_customer(id) ON DELETE RESTRICT;
ALTER TABLE ONLY scrm.scrm_customer_group
    ADD CONSTRAINT fk_customer_group_owner FOREIGN KEY (owner_account_id) REFERENCES scrm.scrm_account(id) ON DELETE RESTRICT;
ALTER TABLE ONLY scrm.scrm_customer
    ADD CONSTRAINT fk_customer_owner_account FOREIGN KEY (owner_account_id) REFERENCES scrm.scrm_account(id) ON DELETE RESTRICT;
ALTER TABLE ONLY scrm.scrm_customer_tag
    ADD CONSTRAINT fk_customer_tag_customer FOREIGN KEY (customer_id) REFERENCES scrm.scrm_customer(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_campaign_execution_log
    ADD CONSTRAINT fk_exec_log_campaign FOREIGN KEY (campaign_id) REFERENCES scrm.scrm_campaign(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_follow_up_record
    ADD CONSTRAINT fk_follow_up_record_task FOREIGN KEY (task_id) REFERENCES scrm.scrm_follow_up_task(id) ON DELETE SET NULL;
ALTER TABLE ONLY scrm.scrm_funnel_stage
    ADD CONSTRAINT fk_funnel_stage_funnel FOREIGN KEY (funnel_id) REFERENCES scrm.scrm_funnel(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_customer_group_member
    ADD CONSTRAINT fk_group_member_customer FOREIGN KEY (customer_id) REFERENCES scrm.scrm_customer(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_customer_group_member
    ADD CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES scrm.scrm_customer_group(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_account_health
    ADD CONSTRAINT fk_health_account FOREIGN KEY (account_id) REFERENCES scrm.scrm_account(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_journey_enrollment
    ADD CONSTRAINT fk_journey_enrollment_journey FOREIGN KEY (journey_id) REFERENCES scrm.scrm_customer_journey(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_journey_progress_log
    ADD CONSTRAINT fk_journey_progress_log_enrollment FOREIGN KEY (enrollment_id) REFERENCES scrm.scrm_journey_enrollment(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_journey_progress_log
    ADD CONSTRAINT fk_journey_progress_log_journey FOREIGN KEY (journey_id) REFERENCES scrm.scrm_customer_journey(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_journey_step
    ADD CONSTRAINT fk_journey_step_journey FOREIGN KEY (journey_id) REFERENCES scrm.scrm_customer_journey(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_account_login_log
    ADD CONSTRAINT fk_login_log_account FOREIGN KEY (account_id) REFERENCES scrm.scrm_account(id) ON DELETE CASCADE;
ALTER TABLE scrm.scrm_conversation_message
    ADD CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES scrm.scrm_conversation(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_opportunity_stage_history
    ADD CONSTRAINT fk_opp_stage_hist_opp FOREIGN KEY (opportunity_id) REFERENCES scrm.scrm_opportunity(id) ON DELETE CASCADE;
ALTER TABLE ONLY scrm.scrm_opportunity
    ADD CONSTRAINT fk_opportunity_funnel FOREIGN KEY (funnel_id) REFERENCES scrm.scrm_funnel(id) ON DELETE RESTRICT;
ALTER TABLE ONLY scrm.scrm_report_result
    ADD CONSTRAINT fk_report_result_template FOREIGN KEY (template_id) REFERENCES scrm.scrm_report_template(id) ON DELETE CASCADE;