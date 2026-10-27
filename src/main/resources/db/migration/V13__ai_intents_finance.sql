-- ======================================================================
-- 财税数字化 - 模块⑤ 财税 AI 接待 + 意向分级数据入库（V13）
-- ======================================================================
-- 用途: 按 docs/tasks/T04/corpus/intents.json 将 13 条财税 AI 接待意图导入
--   scrm_ai_intent（字段对齐 ScrmAiIntentEntity: intent_name / intent_category /
--   keywords / examples / response_template / suggested_action / priority / enabled），
--   使既有 ScrmAiAssistantIntentService.detectIntent（关键词 + 示例 Jaccard 相似度）
--   开箱即可识别财税意图并驱动建议动作（RECOMMEND_PRODUCT / ASSIGN_AGENT / ESCALATE / NONE）。
--   同时:
--   1) 新增 AiAssistantConfig（scrm_ai_assistant_config）：按 T04 system-prompt.md 配置
--      财税 AI 接待话术（temperature=0.7, maxTokens=1000）；仅当库内尚无默认配置时才置
--      is_default=true，不覆盖运营已设默认。
--   2) 新增 LeadScoring 财税维度（scrm_lead_dimension × 4）+ 评分模型
--      （scrm_lead_scoring_model, modelCode=FIN_LEAD_SCORE_V1）：按 T04 scoring-model.json
--      的维度权重（price_inquiry 40 / company_info_given 30 / urgency 20 /
--      conversation_depth 10）与六档阈值入库，配套说明见 docs/tasks/D01-weights.md。
--   3) 预置高意向推送站内信模板（scrm_notification_template, template_code=lead-hot-push），
--      供 ScrmFinanceHotLeadPushService 复用 NotificationCenter 推送销售。
-- 数据库: PostgreSQL 16+   Schema: scrm
-- 幂等: 本迁移仅插入显式 id 的数据；重跑由 Flyway 校验和阻止（不做 ON CONFLICT 掩盖）。
-- ======================================================================

-- 1. 财税 AI 接待意图（13 条, enabled=true, 来源 T04/corpus/intents.json）
INSERT INTO scrm.scrm_ai_intent
    (id, intent_name, intent_category, keywords, examples, response_template,
     suggested_action, priority, enabled, match_count, created_by,
     create_time, update_time, version)
VALUES
    (910001, '代理记账咨询', 'PURCHASE',
     '代理记账,代账,做账,记账报税,账务处理,会计代账,记账,报税服务',
     '["代理记账多少钱一个月","你们代账收费怎么算","小规模纳税人代账一年多少钱","做账报税一年费用多少","可以代理记账吗，价格多少","一般纳税人代账要多少钱","零申报代账怎么收费","代账服务包含哪些内容","你们会计代账靠谱吗，怎么收费","代理记账有优惠活动吗"]',
     '您好，很高兴为您介绍代理记账服务~我们根据企业类型（小规模/一般纳税人）和业务量提供不同套餐，收费在 {{priceRange}} 区间。方便留下您的企业类型和开票需求，我们的财税顾问会为您出详细报价方案。',
     'RECOMMEND_PRODUCT', 90, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910002, '注册公司咨询', 'PURCHASE',
     '注册公司,公司注册,注册企业,新办执照,办执照,注册执照,公司新设,注册个体户',
     '["注册公司需要什么材料","代办注册公司多少钱","注册一家有限公司流程是什么","注册公司要多久能拿到执照","可以帮我注册公司吗","注册个体户需要什么条件","注册公司地址怎么解决","公司注册免费吗还是收费","注册资本写多少合适","异地可以注册公司吗"]',
     '您好，注册公司我们提供一站式代办服务，包含核名、材料整理、提交、领取执照。您只需要提供身份证和注册地址相关材料，其余流程我们全程跟进。方便留下您的公司类型和所在地区，顾问为您报价并拉通流程清单。',
     'RECOMMEND_PRODUCT', 90, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910003, '公司注销咨询', 'SUPPORT',
     '公司注销,注销公司,注销营业执照,注销流程,简易注销,一般注销,清算注销,税务注销,注销代办',
     '["公司不经营了怎么注销","注销公司流程复杂吗","简易注销需要什么条件","公司注销要多少钱","注销营业执照要什么材料","税务注销怎么办理","注销公司要多久","公司解散了什么时候注销","注销代办收费多少","名下公司一直没注销有影响吗"]',
     '您好，公司注销分为简易注销和一般注销（含税务注销、清算）两种路径，具体取决于公司状态和税务情况。需要帮您先评估适合哪种方式吗？方便留下企业名称和经营状态，我们的顾问为您安排注销方案评估。',
     'ASSIGN_AGENT', 85, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910004, '资质证照代办咨询', 'PURCHASE',
     '资质代办,资质办理,办理资质,许可证代办,食品经营许可证,ICP许可证,劳务派遣资质,建筑资质,道路运输许可证,经营许可证',
     '["食品经营许可证怎么办理","办理资质要多少钱","资质代办需要什么材料","ICP许可证可以代办吗","劳务派遣资质办理流程","食品证多久能办下来","代理办证安全吗","建筑资质代办收费多少","道路运输许可证怎么申请","开餐饮店需要办什么证"]',
     '您好，资质证照代办是我们的核心服务之一，包含食品、ICP、劳务派遣、建筑等各类资质。不同资质材料要求和办理周期不同，方便告知您需要办理哪类资质、企业所在地区吗？顾问会为您评估条件和报价。',
     'RECOMMEND_PRODUCT', 85, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910005, '开票咨询', 'SUPPORT',
     '开发票,开票,发票,开具发票,专票,普票,数电票,电子发票,红冲,作废发票,发票额度',
     '["你们可以帮忙开发票吗","一般纳税人开专票要注意什么","数电票怎么开具","发票红冲流程是什么","开票额度不够怎么办","小规模企业可以开专票吗","电子发票和纸质发票的区别","发票怎么领购","发票抬头开错怎么处理","什么情况可以作废发票"]',
     '您好，发票开具和日常管理（含数电票、红冲、额度调整）我们都能协助处理。方便说明您遇到的具体开票场景吗？如果涉及数电票操作或额度问题，顾问会给出具体操作指引或直接协助办理。',
     'ASSIGN_AGENT', 80, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910006, '税务申报与政策咨询', 'INQUIRY',
     '报税,纳税申报,税务申报,零申报,汇算清缴,年度申报,税种,税率,优惠政策,税收政策,核定征收,查账征收,企业所得税,增值税,个税',
     '["小规模纳税人免增值税政策是怎么规定的","企业所得税怎么算","什么时候报税","零申报有什么风险吗","个税怎么申报","汇算清缴什么时候做","核定征收和查账征收有什么区别","新公司第一年报税要注意什么","老板私下收款税务风险大吗","开票和报税数字要一致吗"]',
     '您好，税务政策专业且变动频繁，我为您梳理涉及的政策要点和申报节点，但具体以最新税务政策和您专管员确认为准。方便留下您的企业类型和所在地区吗？顾问会结合您的情况给到针对性的申报建议。',
     'ASSIGN_AGENT', 80, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910007, '公司变更咨询', 'SUPPORT',
     '公司变更,变更法人,变更股东,变更经营范围,变更地址,变更股权,法人变更,股权变更,增资,减资,监事变更',
     '["变更法人需要什么材料","股权变更流程是什么","公司地址变更要多少钱","变更经营范围麻烦吗","法人可以远程办理变更吗","增资减资怎么操作","变更股东需要本人到场吗","公司名称变更要多久","变更银行账户信息要注意什么","多人股权变更复杂吗"]',
     '您好，公司变更（法人/股权/地址/经营范围等）我们可以全程代办，材料一次告知，进度实时同步。方便告知您需要变更哪类事项、公司所在地区吗？顾问会为您提供所需材料清单和办理周期。',
     'ASSIGN_AGENT', 85, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910008, '价格与套餐咨询', 'INQUIRY',
     '多少钱,价格,费用,收费,报价,套餐,价钱,价位,怎么收费',
     '["你们服务怎么收费","记账一年多少钱","注册公司标准是1999吗","有没有便宜的套餐","套餐里包含记账和报税吗","注销公司费用是多少","代办资质报价单可以发我吗","上门服务要另外收费吗","首年有没有优惠","服务费可以开发票吗"]',
     '您好，我们的服务按项目分档报价，价格透明、无隐藏收费。方便告知您需要的是哪类服务、企业规模多大吗？顾问会把对应方案和报价明细发给您。',
     'RECOMMEND_PRODUCT', 88, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910009, '问候与开场', 'GREETING',
     '你好,您好,在吗,早上好,下午好,晚上好,hi,hello,哈喽,你们好',
     '["你好","您好，在吗","早上好","你们好，想咨询点事","哈喽","在不在","你好，能帮我看看吗","hello","老板你好，想问下记账","下午好"]',
     '您好，我是财税顾问助手，很高兴为您服务！公司注册、代理记账、资质代办、税务申报等问题都可以咨询我，请问有什么可以帮您？',
     'NONE', 10, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910010, '告别语', 'FAREWELL',
     '再见,拜拜,谢谢再见,好的再见,先这样,回头再聊,晚点再说,谢谢不聊了',
     '["好的，谢谢，再见","拜拜","先这样吧，改天再聊","好的那我再看看","谢谢，不用了","明白了，回聊","有事再来找你","那先这样吧"]',
     '好的，感谢您的咨询！如需任何财税服务，随时找我，祝您生意兴隆！',
     'NONE', 10, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910011, '普通问题咨询', 'QUESTION',
     '请问,我想问,咨询一下,了解下,问下,什么,怎么,如何,是否,为什么,需要吗',
     '["我想问问记账一般怎么收费","请问注册公司需要几个人","怎么判断自己该用哪种征收方式","我想了解下你们公司实力怎么样","公司一直零申报有问题吗","什么是核定征收","能不能介绍下你们的服务流程","新开的公司需要马上报税吗","个人可以注册公司吗","你们服务覆盖哪些城市"]',
     '您的问题我记下了（{{summary}}）。关于这个问题，我会为您整理准确的政策依据和办理路径，如有进一步细节需要确认，会有财税顾问跟进为您解答。',
     'NONE', 50, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910012, '请求与指令', 'REQUEST',
     '帮我,麻烦你,安排一下,尽快,马上,请,给我,发我,拉我,加我,报价单,联系方式,电话',
     '["麻烦把代理记账的报价发我","帮我约你们顾问见一面","请尽快安排人联系我","把我的联系方式发给顾问","给我发一份注册公司的材料清单","麻烦拉我进你们客户群","帮我看看我的执照还有多久要年审","我着急办理注销，请尽快","给我你们的联系电话","麻烦帮我核对下开票信息"]',
     '好的，已为您记录需求（{{summary}}），稍后顾问会尽快与您联系。方便留下您的称呼和联系电话，以便顾问第一时间为您对接吗？',
     'ASSIGN_AGENT', 70, true, 0, 'finance-tax-v13', now(), now(), 0),

    (910013, '投诉与不满', 'COMPLAINT',
     '投诉,差评,不满意,太差了,骗人,退款,态度差,不负责,误事,耽误,办得不好,要投诉',
     '["你们服务太差了，我要投诉","报税搞错了害我被罚款","退钱，你们办事太不靠谱","销售每次都不回消息","之前承诺的都没兑现","我要向工商投诉你们","办事拖了三个月还没办好","你们顾问态度太差了","材料弄丢了我怎么办","两年都没给我报过税，太过分了"]',
     '非常抱歉给您带来不好的体验。您反映的问题我已第一时间上报，会有专人马上核查处理，1 个工作日内给您明确答复。请问方便留下您的合同号或企业名称，方便我们尽快定位问题吗？',
     'ESCALATE', 100, true, 0, 'finance-tax-v13', now(), now(), 0);

-- 2. 财税 AI 接待配置（AiAssistantConfig, 来源 T04/corpus/system-prompt.md）
INSERT INTO scrm.scrm_ai_assistant_config
    (id, config_name, provider, model, api_key, api_endpoint, system_prompt,
     temperature, max_tokens, knowledge_base_id, enabled, is_default,
     request_count, last_used_at, created_by, create_time, update_time, version)
VALUES
    (95001, '财税AI接待配置', 'OPENAI', 'gpt-4o-mini', NULL, NULL,
'你是「XX财税顾问」企业微信上的 AI 接待助手，代表一家专业财税服务公司，为公司注册、代理记账、
资质代办、税务申报、公司注销等业务提供初步咨询接待。你不是销售，是顾问型接待助手：
**首轮接待、基础答疑、识别意向、引导留资**，复杂与专业问题主动转人工。

## 二、服务范围与回答原则

1. 只回答与公司财税服务相关的问题：注册公司、代理记账、报税申报、开票、注销、资质代办、公司变更等。
2. 超出服务范围或你不确定的问题，如实说明「需要顾问确认」，不编造答案。
3. 涉及具体政策（税率、优惠、截止日期）时，先说明「以最新政策及您的主管税务机关/专管员确认为准」，
   再给出一般性说明；不承诺任何政策口径的最终结果。
4. **禁止承诺收益与结果**：不得承诺「保证注册成功」「保证不查账」「包过」「百分百拿证」等任何保证性话术；
   不得承诺具体的退税额、节税比例或免罚结果。
5. 不得泄露其他客户的任何信息；不索要与业务无关的敏感信息（如银行卡号、密码）。
6. 保持专业、简洁、友好，使用中文，语气亲切但不过度热情。

## 三、引导留资（本系统核心目标之一）

1. 客户表现出咨询意向（问价、问材料、问流程）后，**主动、自然地引导留资**，一次只问 1-2 个问题：
   - 企业类型：小规模纳税人 / 一般纳税人 / 个体户
   - 所在地区（方便安排当地顾问）
   - 是否需要开票
   - 联系称呼 / 电话 / 微信
2. 引导话术示例：
   - 「方便告知您的企业类型和所在地区吗？顾问会给您针对性的方案。」
   - 「我可以把材料清单和报价发给您，方便留个称呼或电话吗？」
3. 客户拒绝留资时**不强求**，礼貌结束，记录为待培育线索。

## 四、意图识别与分级（对齐 intents.json / scoring-model.json）

1. 命中以下意图时按对应动作处理：
   - PURCHASE（代理记账/注册公司/资质代办/价格套餐）→ 推荐服务 → 引导留资
   - SUPPORT（注销/开票/变更/税务申报）→ 提供流程指引 → 转顾问跟进
   - COMPLAINT → 立即安抚，记录问题，转人工（ESCALATE），**不得与客户争辩**
   - GREETING / FAREWELL → 简短回应
   - QUESTION / REQUEST → 按问题作答或响应请求
2. 对话过程中留意评分维度（是否问价、是否留企业信息、紧迫度词、对话轮次），
   高意向线索（问价 + 留资 + 紧迫）在对话内即可告知「顾问会尽快联系您」。

## 五、对话节奏

1. 回复简洁：单次回复尽量控制在 2-4 句，先直接回答问题，再视情况引导留资。
2. 客户连续追问同一专业问题时，回答第 2 次后仍深度追问 → 转人工，话术如：
   「这个问题比较专业，涉及您公司的具体情况，我安排专业顾问为您一对一解答，方便留个联系方式吗？」
3. 客户明确表示要人工/电话/当面谈时，立即转人工并留下联系方式。
4. 客户消息含大量超长或无关内容时，抓取核心问题回复，避免被带偏。

## 六、合规红线（优先级最高，不可违反）

1. 不承诺收益、不保证结果（注册成功/免查/包过/稳拿证）。
2. 政策口径一律加「以最新政策及主管机关确认为准」。
3. 不索要银行卡号、密码、验证码等敏感信息。
4. 客户投诉时：先道歉、再记录、立即上报，不解释借口。
5. 客户明确要求人工时，必须转人工，不得强行 AI 接待。
6. 所有答复可被审计；不虚构案例、不编造服务细节。

## 七、开场与收尾话术示例

- 开场：「您好，我是财税顾问助手，很高兴为您服务！公司注册、代理记账、资质代办、税务申报等问题都可以咨询我，请问有什么可以帮您？」
- 收尾（已留资）：「好的，已为您记下联系方式，顾问会尽快与您联系，请保持电话畅通～」
- 收尾（未留资）：「您的问题我已记录，后续如有需要随时找我，祝您一切顺利！」
- 转人工：「这个问题涉及您的具体情况，我安排专业顾问为您一对一解答，方便留个联系方式吗？」
- 投诉安抚：「非常抱歉给您带来不好的体验，您的问题我已第一时间上报，会有专人尽快核查处理。」',
     0.7, 1000, NULL, true,
     (SELECT NOT EXISTS (SELECT 1 FROM scrm.scrm_ai_assistant_config WHERE is_default = true)),
     0, NULL, 'finance-tax-v13', now(), now(), 0);

-- 3. LeadScoring 财税维度（4 条, 来源 T04/corpus/scoring-model.json dimensions）
INSERT INTO scrm.scrm_lead_dimension
    (id, dimension_name, dimension_code, description, dimension_category,
     default_weight, default_max_score, scoring_rules, applicable_fields,
     enabled, usage_count, created_by, create_time, update_time, version)
VALUES
    (93001, '是否问价', 'price_inquiry', '客户是否主动询问价格/费用/收费/报价（PURCHASE/价格咨询类意图命中即算）。财税代账/注册/注销客单价高、决策链路短，问价是最强购买信号，权重最高', 'NEED_BASED',
     1.0, 40,
     '[{"field":"priceInquiry","operator":"ge","value":3,"score":40,"description":"精准问价：明确到具体服务（代理记账/注册/注销/资质任一）"},{"field":"priceInquiry","operator":"eq","value":1,"score":35,"description":"AI 引导后给出预算范围/认可价"},{"field":"priceInquiry","operator":"eq","value":2,"score":25,"description":"泛问价格，未指定服务（多少钱/有没有优惠）"}]',
     'priceInquiry', true, 0, 'finance-tax-v13', now(), now(), 0),

    (93002, '是否留企业信息', 'company_info_given', '客户是否提供企业/个人信息：企业名称、行业类型、小规模/一般纳税人、注册地/所在城市、联系方式（电话/微信）、是否需要开票等。留资可验证真实需求并直接进入跟进队列', 'FIRMOGRAPHIC',
     1.0, 30,
     '[{"field":"companyInfoGiven","operator":"ge","value":3,"score":30,"description":"完整留资：联系方式+企业关键信息（名称/类型/地区 ≥2 项）"},{"field":"companyInfoGiven","operator":"eq","value":2,"score":20,"description":"部分留资：联系方式或企业信息其中一类"},{"field":"companyInfoGiven","operator":"eq","value":1,"score":15,"description":"AI 引导后给出企业类型/地区等任一信息"}]',
     'companyInfoGiven', true, 0, 'finance-tax-v13', now(), now(), 0),

    (93003, '紧迫度词', 'urgency', '对话中出现的紧迫信号词/事件：时间节点（月底/季末/报税截止/执照到期/马上开业）、后果词（被罚款/逾期/异常/列入异常名录/影响经营）、催促词（尽快/着急/马上/一周内）。财税事项普遍涉截止日期，紧迫度直接决定介入时效', 'TIMING',
     1.0, 20,
     '[{"field":"urgencyLevel","operator":"ge","value":3,"score":20,"description":"强紧迫：明确期限+后果（如月底前要注册好/执照被列入异常）"},{"field":"urgencyLevel","operator":"eq","value":2,"score":12,"description":"中紧迫：催办/期限词（尽快/着急/马上/这周内/下个月前）"},{"field":"urgencyLevel","operator":"eq","value":1,"score":5,"description":"弱紧迫：提及事件但无明确时间（如听说现在有政策优惠）"}]',
     'urgencyLevel', true, 0, 'finance-tax-v13', now(), now(), 0),

    (93004, '对话轮次', 'conversation_depth', 'AI 接待对话的实质往返轮次（客户消息数，不含互致问候）。轮次越高说明提问越具体、对服务细节越关注，配合前三维度区分「真咨询」与「随手一问」', 'ENGAGEMENT',
     1.0, 10,
     '[{"field":"conversationDepth","operator":"ge","value":6,"score":10,"description":"深度咨询：≥6 轮实质对话（多轮追问细节）"},{"field":"conversationDepth","operator":"between","value":[3,5],"score":6,"description":"中等深入：3-5 轮实质对话"},{"field":"conversationDepth","operator":"eq","value":2,"score":3,"description":"初步接触：2 轮实质对话"}]',
     'conversationDepth', true, 0, 'finance-tax-v13', now(), now(), 0);

-- 4. LeadScoring 财税评分模型（FIN_LEAD_SCORE_V1, 满分 100, 六档阈值）
INSERT INTO scrm.scrm_lead_scoring_model
    (id, model_name, model_code, description, model_type, dimensions,
     total_max_score, grade_thresholds, is_default, is_published, version_no,
     applied_count, last_applied_at, created_by, create_time, update_time, version)
VALUES
    (92001, '财税线索意向分级模型', 'FIN_LEAD_SCORE_V1',
     '财税版线索意向分级模型 v1（来源 docs/tasks/T04/corpus/scoring-model.json）：是否问价(40) + 是否留企业信息(30) + 紧迫度词(20) + 对话轮次(10)，总分 100 分制，产出 grade 六档 + isHotLead。维度 tier 编码说明见 docs/tasks/D01-weights.md；conversation 上下文键（priceInquiry/companyInfoGiven/urgencyLevel/conversationDepth）由 AI 接待对话层评估后填充客户上下文再调用 calculateScore。',
     'RULE_BASED',
     '[{"dimension":"price_inquiry","weight":1.0,"maxScore":40,"fields":[{"field":"priceInquiry","operator":"ge","value":3,"score":40},{"field":"priceInquiry","operator":"eq","value":1,"score":35},{"field":"priceInquiry","operator":"eq","value":2,"score":25}]},{"dimension":"company_info_given","weight":1.0,"maxScore":30,"fields":[{"field":"companyInfoGiven","operator":"ge","value":3,"score":30},{"field":"companyInfoGiven","operator":"eq","value":2,"score":20},{"field":"companyInfoGiven","operator":"eq","value":1,"score":15}]},{"dimension":"urgency","weight":1.0,"maxScore":20,"fields":[{"field":"urgencyLevel","operator":"ge","value":3,"score":20},{"field":"urgencyLevel","operator":"eq","value":2,"score":12},{"field":"urgencyLevel","operator":"eq","value":1,"score":5}]},{"dimension":"conversation_depth","weight":1.0,"maxScore":10,"fields":[{"field":"conversationDepth","operator":"ge","value":6,"score":10},{"field":"conversationDepth","operator":"between","value":[3,5],"score":6},{"field":"conversationDepth","operator":"eq","value":2,"score":3}]}]',
     100,
     '[{"grade":"A_PLUS","minScore":90,"maxScore":100,"color":"#f5222d"},{"grade":"SUPER_HOT","minScore":80,"maxScore":89.99,"color":"#fa8c16"},{"grade":"HOT","minScore":65,"maxScore":79.99,"color":"#faad14"},{"grade":"WARM","minScore":45,"maxScore":64.99,"color":"#1677ff"},{"grade":"COLD","minScore":20,"maxScore":44.99,"color":"#8c8c8c"},{"grade":"DEAD","minScore":0,"maxScore":19.99,"color":"#bfbfbf"}]',
     (SELECT NOT EXISTS (SELECT 1 FROM scrm.scrm_lead_scoring_model WHERE is_default = true)),
     true, 1, 0, NULL, 'finance-tax-v13', now(), now(), 0);

-- 5. 高意向线索推送站内信模板（ScrmFinanceHotLeadPushService 复用 NotificationCenter 推送销售）
INSERT INTO scrm.scrm_notification_template
    (id, template_name, template_code, category, channel, title, content,
     variables, sender_name, is_html, enabled, usage_count,
     create_time, update_time, version)
VALUES
    (88002, '高意向线索推送', 'lead-hot-push', 'SERVICE', 'IN_APP',
     '高意向线索: {customerName}',
     'AI 接待识别到高意向财税线索，建议优先跟进\n客户: {customerName}\n意向等级: {gradeLabel} ({grade})\n得分: {totalScore}/100\n意向理由: {reason}\n对话摘要: {conversationSummary}\n联系方式: {contact}',
     'customerName,grade,gradeLabel,totalScore,reason,conversationSummary,contact', 'AI接待', false, true, 0,
     now(), now(), 0);

COMMENT ON TABLE scrm.scrm_ai_intent IS 'AI 意图表（含财税版 V13 预置 13 条意图，来源 T04/corpus/intents.json）';
COMMENT ON TABLE scrm.scrm_lead_scoring_model IS '销售线索评分模型表（含财税版 V13 预置 FIN_LEAD_SCORE_V1）';
COMMENT ON TABLE scrm.scrm_notification_template IS '通知模板表（含模块⑤ V13 预置 lead-hot-push 高意向推送模板）';
