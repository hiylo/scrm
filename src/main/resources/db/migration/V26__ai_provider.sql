-- ======================================================================
-- V26: AI Provider 统一配置体系（模型能力路由）
--
-- 背景: 原本 AI/OCR 的模型接入全是环境变量静态注入（scrm.ai.* /
--       scrm.vision.* / scrm.ocr.*），存在三个问题:
--         1. 换模型/加模型必须改 /etc/scrm-finance.env + 重启进程;
--         2. 系统和「具体某个模型」强耦合, 无法在系统内自由切换;
--         3. 文本(NL)与视觉(多模态)能力无法按任务动态路由。
--       本迁移引入 Provider 注册表: 模型能力从「provider 配置」获取,
--       应用运行期按任务能力(文本/图像)选择 provider, 避免把系统锁死
--       在单一模型上。配置全部落库, 管理端可增删改查/连通测试/设默认,
--       改完即时生效, 无需改环境变量、无需重启。
--
-- 能力模型:
--   capability 用逗号分隔的能力位串: text / image / embedding
--     text  = 可做对话/客服/文案（文本生成）
--     image = 可做视觉理解（证照 OCR 结构化抽取）
--     embedding = 可做向量化（预留）
--   文本任务路由到「同时具备 text 能力」的默认 provider;
--   证照 OCR 路由到「具备 image 能力」的默认 provider（多模态）。
--   同一 provider 可同时具备 text + image（如 Magpie 网关）。
--
-- 数据库: PostgreSQL 16+
-- Schema: scrm
-- ======================================================================

CREATE TABLE scrm.scrm_ai_provider (
    id                  bigint NOT NULL,
    create_time         timestamp without time zone NOT NULL,
    update_time         timestamp without time zone NOT NULL,
    version             bigint DEFAULT 0 NOT NULL,

    -- Provider 名称（用于管理端展示与选择，如「Magpie 网关」）
    provider_name       character varying(100) NOT NULL,
    -- Provider 类型: OPENAI_LLM / OLLAMA / MAGPIE / OPENAI_VISION / LOCAL_LLM
    provider_type       character varying(30) NOT NULL,
    -- API 基础地址（OpenAI 兼容，如 http://192.168.1.150:3425 或 http://x.x.x.x:11434/v1）
    api_base_url        character varying(500) NOT NULL,
    -- 对话补全路径（默认 /v1/chat/completions）
    chat_path           character varying(100) DEFAULT '/v1/chat/completions' NOT NULL,
    -- 健康/模型探测路径（默认 /v1/models）
    health_path         character varying(100) DEFAULT '/v1/models' NOT NULL,
    -- API 密钥（AES-GCM 加密存储, ENC: 前缀; 本地无鉴权可为空）
    api_key             character varying(500),
    -- 能力位串: 逗号分隔 text,image,embedding
    capability          character varying(100) DEFAULT 'text' NOT NULL,
    -- 默认模型名（如 group/sensenova-pool / qwen3:8b; 留空则由服务端 /v1/models 自动推荐）
    default_model       character varying(100),
    -- 采样温度（默认 0.7）
    temperature         double precision DEFAULT 0.7 NOT NULL,
    -- 单次生成最大 token 数（默认 500）
    max_tokens          integer DEFAULT 500 NOT NULL,
    -- 是否启用（false 不参与路由）
    enabled             boolean DEFAULT true NOT NULL,
    -- 是否默认文本 provider（文本任务路由目标; 全局仅允许一个）
    is_default_text     boolean DEFAULT false NOT NULL,
    -- 是否默认多模态 provider（证照 OCR 路由目标; 全局仅允许一个）
    is_default_image    boolean DEFAULT false NOT NULL,
    -- 路由优先级（数值越小越优先, 同能力多 provider 时按此排序）
    priority            integer DEFAULT 100 NOT NULL,
    -- 连接状态: CONNECTED / DISCONNECTED / UNKNOWN（由连通测试维护）
    connection_status   character varying(20) DEFAULT 'UNKNOWN' NOT NULL,
    -- 最后连通测试时间
    last_tested_at      timestamp without time zone,
    -- 最近模型清单缓存（JSON 数组, 由「拉取模型列表」维护, 仅用于管理端展示）
    models_cache        text,
    -- 创建人（UserContext 审计用）
    created_by          character varying(100),
    -- Provider 备注
    remark              character varying(500)
);

-- 主键
ALTER TABLE ONLY scrm.scrm_ai_provider
    ADD CONSTRAINT scrm_ai_provider_pkey PRIMARY KEY (id);

-- 唯一: 名称
CREATE UNIQUE INDEX uk_ai_provider_name ON scrm.scrm_ai_provider USING btree (provider_name);

-- 查询: 启用/能力/默认/优先级
CREATE INDEX idx_ai_provider_enabled ON scrm.scrm_ai_provider USING btree (enabled);
CREATE INDEX idx_ai_provider_priority ON scrm.scrm_ai_provider USING btree (priority);

-- 部分唯一索引: 全局仅一个默认文本 provider
CREATE UNIQUE INDEX uk_ai_provider_default_text
    ON scrm.scrm_ai_provider USING btree (is_default_text)
    WHERE is_default_text = true;

-- 部分唯一索引: 全局仅一个默认多模态 provider
CREATE UNIQUE INDEX uk_ai_provider_default_image
    ON scrm.scrm_ai_provider USING btree (is_default_image)
    WHERE is_default_image = true;

COMMENT ON TABLE scrm.scrm_ai_provider IS
    'AI Provider 注册表: 模型能力从 provider 配置获取, 文本/多模态按能力动态路由 (V26)';
COMMENT ON COLUMN scrm.scrm_ai_provider.capability IS
    '能力位串(逗号分隔): text=对话/客服/文案, image=视觉理解/证照OCR, embedding=向量化(预留)';
COMMENT ON COLUMN scrm.scrm_ai_provider.api_key IS
    'API 密钥: AES-256-GCM 加密存储(ENC: 前缀), 空=无需鉴权; 只从管理端写入, 不回显明文';