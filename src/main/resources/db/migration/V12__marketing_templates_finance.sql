-- ======================================================================
-- 财税数字化 - 模块④ 财税营销模板库入库（V12）
-- ======================================================================
-- 用途: 按 docs/tasks/T05/templates.json 将财税行业营销文案模板导入
--   scrm_message_template_center，作为「AI 批量生成 + 人工审核闸门」分发的基准模板。
--   1) 模板入库即人工审核流入口: status=DRAFT / review_status=PENDING
--      （DRAFT → PENDING_REVIEW → APPROVED/REJECTED → PUBLISHED，见 ScrmMessageTemplateCenterReviewService）。
--   2) 每个模板同步写入 scrm_message_template_version 版本快照(version_number=1, created_from_version=1)，
--      使版本管理/审核历史开箱可用；模板 current_version_id 指向快照。
--   3) 模板编码 TAX_MKT_* 全局唯一；分组编码 OPENING/ANNIVERSARY/FESTIVAL/QUALIFICATION/POLICY/
--      RENEWAL/CHURN_RECOVERY 唯一（本迁移自建，不依赖既有分组）。
--   4) 占位符约定 {{客户类型}} {{服务内容}} {{活动信息}} {{期限}}，与
--      ScrmMessageTemplateCenterRenderService 的 {{variable}} 渲染规则一致。
-- 合规: 文案本身按 T05 complianceRules 编写（无保证收益/绝对化用语/虚假紧迫/违规服务暗示），
--   AI 生成变体必须 review_status=APPROVED 后方可分发（服务层 publishTemplate 闸门）。
-- 数据库: PostgreSQL 16+   Schema: scrm
-- 幂等: 本迁移仅插入；如重跑已应用迁移，Flyway 校验和会阻止（不做 ON CONFLICT 掩盖）。
-- ======================================================================

-- 1. 模板分组（7 个场景）
INSERT INTO scrm.scrm_message_template_group
    (id, group_name, group_code, description, group_type, parent_group_id, sort_order,
     template_count, enabled, color, icon, created_by, create_time, update_time, version)
VALUES
    (900100, '开业推广',    'OPENING',           '财税营销 - 开业推广模板组', 'MARKETING', NULL, 1, 0, true, NULL, NULL, 'finance-tax-v12', now(), now(), 0),
    (900101, '周年优惠',    'ANNIVERSARY',       '财税营销 - 周年优惠模板组', 'MARKETING', NULL, 2, 0, true, NULL, NULL, 'finance-tax-v12', now(), now(), 0),
    (900102, '节日问候',    'FESTIVAL',          '财税营销 - 节日问候模板组', 'MARKETING', NULL, 3, 0, true, NULL, NULL, 'finance-tax-v12', now(), now(), 0),
    (900103, '资质宣传',    'QUALIFICATION',     '财税营销 - 资质宣传模板组', 'MARKETING', NULL, 4, 0, true, NULL, NULL, 'finance-tax-v12', now(), now(), 0),
    (900104, '政策解读',    'POLICY',            '财税营销 - 政策解读模板组', 'SERVICE',   NULL, 5, 0, true, NULL, NULL, 'finance-tax-v12', now(), now(), 0),
    (900105, '续费提醒',    'RENEWAL',           '财税营销 - 续费提醒模板组', 'REMINDER',  NULL, 6, 0, true, NULL, NULL, 'finance-tax-v12', now(), now(), 0),
    (900106, '流失挽回',    'CHURN_RECOVERY',    '财税营销 - 流失挽回模板组', 'SERVICE',   NULL, 7, 0, true, NULL, NULL, 'finance-tax-v12', now(), now(), 0);

-- 2. 模板定义（21 条，DRAFT + review_status=PENDING，人工审核闸门开启）
INSERT INTO scrm.scrm_message_template_center
    (id, template_name, template_code, group_id, group_name, description, template_type,
     channels, subject, content, plain_content, html_content, wechat_link, mini_program_path,
     variables, attachments, category, tags, applicable_scenarios, language, status,
     version_number, current_version_id, review_status, reviewed_by, reviewed_at, review_comment,
     usage_count, last_used_at, success_rate, avg_response_rate, is_standard, created_by,
     create_time, update_time, version)
VALUES
    (90070101, '新办企业开业套餐推广（工商+代账组合）', 'TAX_MKT_OPENING_001', 900100, '开业推广',
     '开业推广场景: 工商注册+代账组合套餐', 'RICH_TEXT', 'WORK_WECHAT,WECHAT',
     '恭喜开业｜{{客户类型}}工商注册+代理记账服务方案',
     '恭喜开业！{{客户类型}}办理「{{服务内容}}」一站式落地，营业执照、刻章、开户、记账报税按流程衔接，少跑冤枉路。{{期限}}前签约享开业礼包，详情可咨询您的财税顾问。服务不含注册资本实缴及前置审批事项，以双方服务合同为准。',
     '恭喜开业！{{客户类型}}办理「{{服务内容}}」一站式落地，营业执照、刻章、开户、记账报税按流程衔接，少跑冤枉路。{{期限}}前签约享开业礼包，详情可咨询您的财税顾问。服务不含注册资本实缴及前置审批事项，以双方服务合同为准。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"新设企业","description":"客户类型，如：初创公司/个体工商户","required":true},{"name":"服务内容","type":"text","defaultValue":"工商注册+代理记账","description":"服务内容，如：工商注册+代理记账","required":true},{"name":"期限","type":"date","defaultValue":"","description":"活动期限","required":true}]',
     NULL, '开业推广', '财税,开业', '开业推广,新客', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070102, '个体工商户开业基础服务介绍', 'TAX_MKT_OPENING_002', 900100, '开业推广',
     '开业推广场景: 个体户税务登记+记账报税', 'TEXT', 'WORK_WECHAT,WECHAT,SMS',
     '个体开业更省心｜{{客户类型}}税务登记+记账报税指引',
     '您好！为您的「{{客户类型}}」提供{{服务内容}}服务：开业税务登记、月度记账、按期申报，做到每一笔账目清晰可查。{{期限}}前预约可享受首月体验优惠。具体价格与服务范围以报价单为准。',
     '您好！为您的「{{客户类型}}」提供{{服务内容}}服务：开业税务登记、月度记账、按期申报，做到每一笔账目清晰可查。{{期限}}前预约可享受首月体验优惠。具体价格与服务范围以报价单为准。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"个体工商户","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"税务登记+记账报税","description":"服务内容","required":true},{"name":"期限","type":"date","defaultValue":"","description":"活动期限","required":false}]',
     NULL, '开业推广', '财税,个体户', '开业推广,新客,个体工商户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070103, '新设企业开票与税务登记提醒', 'TAX_MKT_OPENING_003', 900100, '开业推广',
     '开业推广场景: 税务登记/发票申领辅导提醒', 'RICH_TEXT', 'WORK_WECHAT,SMS,EMAIL',
     '新办企业税务事项提醒｜{{客户类型}}开票/申报准备',
     '企业开办后别忘了这几件税务事：税务登记、发票申领、按期申报。我们可为{{客户类型}}的{{服务内容}}提供全程指引，{{期限}}内联系可安排专人对接。具体涉税事项以主管税务机关要求为准。',
     '企业开办后别忘了这几件税务事：税务登记、发票申领、按期申报。我们可为{{客户类型}}的{{服务内容}}提供全程指引，{{期限}}内联系可安排专人对接。具体涉税事项以主管税务机关要求为准。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"新设企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"税务登记+发票申领辅导","description":"服务内容，如：税务登记/发票申领辅导","required":true},{"name":"期限","type":"date","defaultValue":"","description":"期限","required":false}]',
     NULL, '开业推广', '财税,开票', '开业推广,新客', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070201, '客户周年回馈续费优惠', 'TAX_MKT_ANNIVERSARY_001', 900101, '周年优惠',
     '周年优惠场景: 存量客户续费专享', 'RICH_TEXT', 'WORK_WECHAT,WECHAT',
     '周年回馈｜{{客户类型}}续费专享方案',
     '感谢您对{{服务内容}}的信任！公司周年回馈，{{客户类型}}在{{期限}}前确认续费，可享「{{活动信息}}」专属方案，账务服务不间断。活动细则以合同附件为准，详情请咨询您的专属顾问。',
     '感谢您对{{服务内容}}的信任！公司周年回馈，{{客户类型}}在{{期限}}前确认续费，可享「{{活动信息}}」专属方案，账务服务不间断。活动细则以合同附件为准，详情请咨询您的专属顾问。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"老客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"代理记账服务","description":"服务内容，如：代理记账服务","required":true},{"name":"活动信息","type":"text","defaultValue":"续费赠1个月服务","description":"活动信息，如：续费赠1个月服务","required":true},{"name":"期限","type":"date","defaultValue":"","description":"活动期限","required":true}]',
     NULL, '周年优惠', '财税,续费', '周年优惠,存量客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070202, '合作周年致谢与业务体检', 'TAX_MKT_ANNIVERSARY_002', 900101, '周年优惠',
     '周年优惠场景: 合作周年致谢 + 财税健康检查邀约', 'TEXT', 'WORK_WECHAT',
     '合作周年｜{{客户类型}}财税健康检查邀约',
     '合作一周年，谢谢您的支持！本季度我们可为您的{{服务内容}}做一次免费梳理，帮您看清账务结构。{{客户类型}}可于{{期限}}前预约，{{活动信息}}服务不额外收费。梳理结果仅供内部参考，不构成涉税意见。',
     '合作一周年，谢谢您的支持！本季度我们可为您的{{服务内容}}做一次免费梳理，帮您看清账务结构。{{客户类型}}可于{{期限}}前预约，{{活动信息}}服务不额外收费。梳理结果仅供内部参考，不构成涉税意见。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"合作客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"账务与税务申报情况","description":"服务内容，如：账务与税务申报情况","required":true},{"name":"活动信息","type":"text","defaultValue":"免费财税体检","description":"活动信息","required":false},{"name":"期限","type":"date","defaultValue":"","description":"期限","required":true}]',
     NULL, '周年优惠', '财税,回馈', '周年优惠,存量客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070203, '周年套餐升级建议', 'TAX_MKT_ANNIVERSARY_003', 900101, '周年优惠',
     '周年优惠场景: 存量客户套餐升级', 'RICH_TEXT', 'WORK_WECHAT,EMAIL',
     '周年升级｜{{客户类型}}服务套餐调整建议',
     '您的企业规模在成长，财税服务也可以升级。周年期间，{{客户类型}}升级至「{{服务内容}}」可按年付享{{活动信息}}。{{期限}}前确认即可生效，详情以服务合同为准。',
     '您的企业规模在成长，财税服务也可以升级。周年期间，{{客户类型}}升级至「{{服务内容}}」可按年付享{{活动信息}}。{{期限}}前确认即可生效，详情以服务合同为准。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"成长型企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"财务顾问服务","description":"服务内容，如：财务顾问/税务筹划辅导","required":true},{"name":"活动信息","type":"text","defaultValue":"按年付享阶梯价","description":"活动信息","required":false},{"name":"期限","type":"date","defaultValue":"","description":"期限","required":true}]',
     NULL, '周年优惠', '财税,升级', '周年优惠,存量客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070301, '春节财税事项问候', 'TAX_MKT_FESTIVAL_001', 900102, '节日问候',
     '节日问候场景: 春节账务申报时间提醒', 'TEXT', 'WORK_WECHAT,WECHAT',
     '新春问候｜{{客户类型}}春节账务申报时间提醒',
     '新春将至，祝{{客户类型}}生意兴隆！温馨提醒：假期前后的{{服务内容}}请留意时间安排，{{期限}}前如需临时加急处理请提前与我们沟通。具体申报期限以税务机关公告为准。',
     '新春将至，祝{{客户类型}}生意兴隆！温馨提醒：假期前后的{{服务内容}}请留意时间安排，{{期限}}前如需临时加急处理请提前与我们沟通。具体申报期限以税务机关公告为准。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"月度申报与开票业务","description":"服务内容，如：月度申报/开票业务","required":true},{"name":"期限","type":"date","defaultValue":"节后征期","description":"假期申报期限","required":false}]',
     NULL, '节日问候', '财税,春节', '节日问候,全体客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070302, '中秋国庆双节问候', 'TAX_MKT_FESTIVAL_002', 900102, '节日问候',
     '节日问候场景: 双节假期涉税事项提示', 'TEXT', 'WORK_WECHAT',
     '双节快乐｜{{客户类型}}假期涉税事项提示',
     '中秋快乐、国庆愉快！{{客户类型}}的{{服务内容}}若有收尾工作，请留意{{期限}}安排。长假期间如有涉税事项，可在工作群留言，我们节后第一时间处理。',
     '中秋快乐、国庆愉快！{{客户类型}}的{{服务内容}}若有收尾工作，请留意{{期限}}安排。长假期间如有涉税事项，可在工作群留言，我们节后第一时间处理。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"发票开具与申报事项","description":"服务内容","required":false},{"name":"期限","type":"date","defaultValue":"节后征期","description":"申报期限","required":false}]',
     NULL, '节日问候', '财税,中秋,国庆', '节日问候,全体客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070303, '端午/劳动节财税温馨提醒', 'TAX_MKT_FESTIVAL_003', 900102, '节日问候',
     '节日问候场景: 假期报税时间提醒', 'TEXT', 'WORK_WECHAT,SMS',
     '节日问候｜{{客户类型}}报税时间提醒',
     '节日快乐！{{客户类型}}别忘了{{期限}}前的{{服务内容}}，已签约客户我们会主动跟进无需您操心；如有疑问随时联系您的财税顾问。',
     '节日快乐！{{客户类型}}别忘了{{期限}}前的{{服务内容}}，已签约客户我们会主动跟进无需您操心；如有疑问随时联系您的财税顾问。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"月度申报","description":"服务内容","required":true},{"name":"期限","type":"date","defaultValue":"节后征期","description":"申报期限","required":false}]',
     NULL, '节日问候', '财税,端午,劳动节', '节日问候,全体客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070401, '代理记账资质合规宣传', 'TAX_MKT_QUALIFICATION_001', 900103, '资质宣传',
     '资质宣传场景: 代理记账许可资质合规展示', 'RICH_TEXT', 'WORK_WECHAT,WECHAT',
     '合规资质｜{{客户类型}}的代账服务选择',
     '公司持有行业主管部门认可的代理记账许可资质，团队具备专业会计任职资格，为{{客户类型}}提供{{服务内容}}服务，账务档案完整、按期申报。欢迎{{期限}}前预约了解服务流程与合同条款。',
     '公司持有行业主管部门认可的代理记账许可资质，团队具备专业会计任职资格，为{{客户类型}}提供{{服务内容}}服务，账务档案完整、按期申报。欢迎{{期限}}前预约了解服务流程与合同条款。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"小微企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"代理记账","description":"服务内容，如：代理记账","required":true},{"name":"期限","type":"date","defaultValue":"","description":"期限","required":false}]',
     NULL, '资质宣传', '财税,资质', '资质宣传,新客', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070402, '专业团队资质展示', 'TAX_MKT_QUALIFICATION_002', 900103, '资质宣传',
     '资质宣传场景: 持证团队展示', 'RICH_TEXT', 'WORK_WECHAT',
     '专业立身｜{{客户类型}}财税服务团队介绍',
     '我们的财税顾问团队持证上岗，熟悉{{服务内容}}实务流程，可为{{客户类型}}提供合规、清晰的财税服务。{{期限}}前咨询可预约一次免费初步沟通，具体服务方案以合同为准。',
     '我们的财税顾问团队持证上岗，熟悉{{服务内容}}实务流程，可为{{客户类型}}提供合规、清晰的财税服务。{{期限}}前咨询可预约一次免费初步沟通，具体服务方案以合同为准。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"创业者","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"税务咨询","description":"服务内容，如：税务咨询/记账服务","required":true},{"name":"期限","type":"date","defaultValue":"","description":"期限","required":false}]',
     NULL, '资质宣传', '财税,团队', '资质宣传,新客', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070403, '服务流程透明说明', 'TAX_MKT_QUALIFICATION_003', 900103, '资质宣传',
     '资质宣传场景: 记账报税流程透明展示', 'RICH_TEXT', 'WORK_WECHAT,EMAIL',
     '透明服务｜{{客户类型}}的记账报税流程说明',
     '服务过程透明可查：凭证单据交接、月度记账、按期申报、年度汇算各环节均有记录，{{客户类型}}可随时查看进度。{{期限}}前签约{{服务内容}}合同即可开通。',
     '服务过程透明可查：凭证单据交接、月度记账、按期申报、年度汇算各环节均有记录，{{客户类型}}可随时查看进度。{{期限}}前签约{{服务内容}}合同即可开通。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"记账报税服务","description":"服务内容","required":true},{"name":"期限","type":"date","defaultValue":"","description":"期限","required":false}]',
     NULL, '资质宣传', '财税,流程', '资质宣传', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070501, '新政策变化解读通知', 'TAX_MKT_POLICY_001', 900104, '政策解读',
     '政策解读场景: 涉税新政要点解读（附免责）', 'RICH_TEXT', 'WORK_WECHAT,EMAIL',
     '政策速递｜{{客户类型}}关注的涉税新政解读',
     '近期涉税政策有调整（{{期限}}），我们为您整理了{{客户类型}}关心的{{服务内容}}要点。此解读仅供参考，具体执行口径以官方文件及主管税务机关答复为准，如需确认可联系我们单独沟通。',
     '近期涉税政策有调整（{{期限}}），我们为您整理了{{客户类型}}关心的{{服务内容}}要点。此解读仅供参考，具体执行口径以官方文件及主管税务机关答复为准，如需确认可联系我们单独沟通。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"最新政策要点解读","description":"服务内容，如：最新政策要点解读","required":true},{"name":"期限","type":"date","defaultValue":"","description":"政策有效期","required":false}]',
     NULL, '政策解读', '财税,政策', '政策解读,全体客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070502, '税收优惠申报提醒', 'TAX_MKT_POLICY_002', 900104, '政策解读',
     '政策解读场景: 优惠适用性评估提醒（不承诺节税）', 'TEXT', 'WORK_WECHAT,SMS',
     '优惠申报提醒｜{{客户类型}}可关注的政策适用情况',
     '提醒{{客户类型}}关注：现行税收优惠政策可能适用于您的{{服务内容}}。是否符合条件需结合企业经营情况判断，{{期限}}前可联系我们做一次适用性梳理。具体以政策原文为准。',
     '提醒{{客户类型}}关注：现行税收优惠政策可能适用于您的{{服务内容}}。是否符合条件需结合企业经营情况判断，{{期限}}前可联系我们做一次适用性梳理。具体以政策原文为准。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"小微企业","description":"客户类型，如：小微企业/个体工商户","required":true},{"name":"服务内容","type":"text","defaultValue":"优惠政策适用性评估","description":"服务内容，如：税收优惠政策适用性评估","required":true},{"name":"期限","type":"date","defaultValue":"","description":"申报/政策期限","required":true}]',
     NULL, '政策解读', '财税,优惠', '政策解读,全体客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070503, '政策咨询邀约（不承诺节税）', 'TAX_MKT_POLICY_003', 900104, '政策解读',
     '政策解读场景: 一对一政策答疑邀约', 'TEXT', 'WORK_WECHAT',
     '政策解读预约｜{{客户类型}}涉税疑问一对一沟通',
     '政策解读不替代专业判断。{{客户类型}}如对近期的{{服务内容}}有疑问，可在{{期限}}前预约一对一沟通，我们会结合企业实际情况给出行之有理的分析与建议。',
     '政策解读不替代专业判断。{{客户类型}}如对近期的{{服务内容}}有疑问，可在{{期限}}前预约一对一沟通，我们会结合企业实际情况给出行之有理的分析与建议。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"涉税政策答疑","description":"服务内容，如：涉税政策答疑","required":true},{"name":"期限","type":"date","defaultValue":"","description":"预约期限","required":false}]',
     NULL, '政策解读', '财税,答疑', '政策解读', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070601, '代账服务合同到期续费提醒', 'TAX_MKT_RENEWAL_001', 900105, '续费提醒',
     '续费提醒场景: 代账合同到期续费', 'RICH_TEXT', 'WORK_WECHAT,SMS,EMAIL',
     '续费提醒｜{{客户类型}}代账服务即将到期',
     '您好，{{客户类型}}！您的{{服务内容}}将于{{期限}}到期。为避免账务申报空档，建议您提前确认续费安排，我们会同步安排服务衔接。续费方案与价格以报价单为准，如有疑问请随时联系。',
     '您好，{{客户类型}}！您的{{服务内容}}将于{{期限}}到期。为避免账务申报空档，建议您提前确认续费安排，我们会同步安排服务衔接。续费方案与价格以报价单为准，如有疑问请随时联系。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"代理记账服务","description":"服务内容，如：代理记账服务","required":true},{"name":"期限","type":"date","defaultValue":"","description":"服务到期/续费截止时间","required":true}]',
     NULL, '续费提醒', '财税,续费', '续费提醒,存量客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070602, '年度服务合同续签提醒', 'TAX_MKT_RENEWAL_002', 900105, '续费提醒',
     '续费提醒场景: 年度服务合同续签', 'RICH_TEXT', 'WORK_WECHAT,EMAIL',
     '年度续签｜{{客户类型}}服务合同续签提醒',
     '{{客户类型}}您好！您与我们的{{服务内容}}合同将于{{期限}}到期。若续签意愿确认，我们可提前准备下一服务周期的排期与对接人安排。谢谢长期信任！',
     '{{客户类型}}您好！您与我们的{{服务内容}}合同将于{{期限}}到期。若续签意愿确认，我们可提前准备下一服务周期的排期与对接人安排。谢谢长期信任！',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"年度财税服务","description":"服务内容","required":true},{"name":"期限","type":"date","defaultValue":"","description":"续签截止时间","required":true}]',
     NULL, '续费提醒', '财税,续签', '续费提醒,存量客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070603, '资质年审/年检代办续费提醒', 'TAX_MKT_RENEWAL_003', 900105, '续费提醒',
     '续费提醒场景: 资质年检代办续费', 'TEXT', 'WORK_WECHAT,SMS',
     '年审提醒｜{{客户类型}}资质年检续费安排',
     '{{客户类型}}您好！您的{{服务内容}}需在{{期限}}前完成。若继续委托我们办理，请确认续费安排，我们会按年份节点推进，避免逾期影响资质状态。具体办理时限以主管部门要求为准。',
     '{{客户类型}}您好！您的{{服务内容}}需在{{期限}}前完成。若继续委托我们办理，请确认续费安排，我们会按年份节点推进，避免逾期影响资质状态。具体办理时限以主管部门要求为准。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"持证企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"资质年检代办","description":"服务内容，如：资质年检代办","required":true},{"name":"期限","type":"date","defaultValue":"","description":"年检完成期限","required":true}]',
     NULL, '续费提醒', '财税,年审', '续费提醒,存量客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070701, '流失客户关怀回访', 'TAX_MKT_CHURN_001', 900106, '流失挽回',
     '流失挽回场景: 存量流失客户关怀回访', 'TEXT', 'WORK_WECHAT',
     '好久不见｜{{客户类型}}近期可还好？',
     '您好，{{客户类型}}，好久不见！之前为您提供过{{服务内容}}服务，不知近期记账、申报、资质等事项是否都还顺利？如有需要，可随时联系我们，重新了解当下的服务方案，{{期限}}前沟通可预约免费初步咨询。',
     '您好，{{客户类型}}，好久不见！之前为您提供过{{服务内容}}服务，不知近期记账、申报、资质等事项是否都还顺利？如有需要，可随时联系我们，重新了解当下的服务方案，{{期限}}前沟通可预约免费初步咨询。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"前合作客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"财税服务","description":"服务内容","required":true},{"name":"期限","type":"date","defaultValue":"","description":"期限","required":false}]',
     NULL, '流失挽回', '财税,回访', '流失挽回,存量客户', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070702, '换人跟进挽回（低姿态表达）', 'TAX_MKT_CHURN_002', 900106, '流失挽回',
     '流失挽回场景: 换人跟进 + 低姿态征求建议', 'TEXT', 'WORK_WECHAT',
     '服务改进｜想听听您对{{服务内容}}的建议',
     '{{客户类型}}您好！您之前体验过我们的{{服务内容}}，我们一直在改进。想听听您的真实建议，无论是否继续合作，都欢迎{{期限}}前告诉我们，我们愿意据此优化服务。',
     '{{客户类型}}您好！您之前体验过我们的{{服务内容}}，我们一直在改进。想听听您的真实建议，无论是否继续合作，都欢迎{{期限}}前告诉我们，我们愿意据此优化服务。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"客户企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"之前的代账服务","description":"服务内容，如：之前的代账服务","required":true},{"name":"期限","type":"date","defaultValue":"","description":"期限","required":false}]',
     NULL, '流失挽回', '财税,挽回', '流失挽回,存量客户,投诉处理', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0),

    (90070703, '流失客户转型线索', 'TAX_MKT_CHURN_003', 900106, '流失挽回',
     '流失挽回场景: 新服务上线线索激活', 'RICH_TEXT', 'WORK_WECHAT,EMAIL',
     '新服务上线｜{{客户类型}}或有关注',
     '近期我们上线了{{服务内容}}相关服务，{{客户类型}}若有需要可以了解一下。{{活动信息}}，{{期限}}内预约有效，具体内容欢迎咨询，仅供参考，以实际沟通为准。',
     '近期我们上线了{{服务内容}}相关服务，{{客户类型}}若有需要可以了解一下。{{活动信息}}，{{期限}}内预约有效，具体内容欢迎咨询，仅供参考，以实际沟通为准。',
     NULL, NULL, NULL,
     '[{"name":"客户类型","type":"text","defaultValue":"曾经合作的企业","description":"客户类型","required":true},{"name":"服务内容","type":"text","defaultValue":"新政解读","description":"服务内容，如：新上线的政策解读/资质代办","required":true},{"name":"活动信息","type":"text","defaultValue":"","description":"活动信息","required":false},{"name":"期限","type":"date","defaultValue":"","description":"期限","required":false}]',
     NULL, '流失挽回', '财税,新服务', '流失挽回', 'zh_CN', 'DRAFT',
     1, NULL, 'PENDING', NULL, NULL, NULL,
     0, NULL, 0, 0, true, 'finance-tax-v12', now(), now(), 0);

-- 3. 版本快照（每个模板一行，version_number=1，created_from_version=1）
INSERT INTO scrm.scrm_message_template_version
    (id, template_id, version_number, subject, content, plain_content, html_content,
     variables, change_log, status, created_from_version, approved_by, approved_at,
     created_by, created_at, create_time, update_time, version)
VALUES
    (90071001, 90070101, 1, '恭喜开业｜{{客户类型}}工商注册+代理记账服务方案', '恭喜开业！{{客户类型}}办理「{{服务内容}}」一站式落地，营业执照、刻章、开户、记账报税按流程衔接，少跑冤枉路。{{期限}}前签约享开业礼包，详情可咨询您的财税顾问。服务不含注册资本实缴及前置审批事项，以双方服务合同为准。', NULL, NULL, '[{"name":"客户类型","type":"text","defaultValue":"新设企业","description":"客户类型，如：初创公司/个体工商户","required":true},{"name":"服务内容","type":"text","defaultValue":"工商注册+代理记账","description":"服务内容，如：工商注册+代理记账","required":true},{"name":"期限","type":"date","defaultValue":"","description":"活动期限","required":true}]', '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90071002, 90070102, 1, NULL, '您好！为您的「{{客户类型}}」提供{{服务内容}}服务：开业税务登记、月度记账、按期申报，做到每一笔账目清晰可查。{{期限}}前预约可享受首月体验优惠。具体价格与服务范围以报价单为准。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90071003, 90070103, 1, '新办企业税务事项提醒｜{{客户类型}}开票/申报准备', '企业开办后别忘了这几件税务事：税务登记、发票申领、按期申报。我们可为{{客户类型}}的{{服务内容}}提供全程指引，{{期限}}内联系可安排专人对接。具体涉税事项以主管税务机关要求为准。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90072001, 90070201, 1, '周年回馈｜{{客户类型}}续费专享方案', '感谢您对{{服务内容}}的信任！公司周年回馈，{{客户类型}}在{{期限}}前确认续费，可享「{{活动信息}}」专属方案，账务服务不间断。活动细则以合同附件为准，详情请咨询您的专属顾问。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90072002, 90070202, 1, '合作周年｜{{客户类型}}财税健康检查邀约', '合作一周年，谢谢您的支持！本季度我们可为您的{{服务内容}}做一次免费梳理，帮您看清账务结构。{{客户类型}}可于{{期限}}前预约，{{活动信息}}服务不额外收费。梳理结果仅供内部参考，不构成涉税意见。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90072003, 90070203, 1, '周年升级｜{{客户类型}}服务套餐调整建议', '您的企业规模在成长，财税服务也可以升级。周年期间，{{客户类型}}升级至「{{服务内容}}」可按年付享{{活动信息}}。{{期限}}前确认即可生效，详情以服务合同为准。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90073001, 90070301, 1, '新春问候｜{{客户类型}}春节账务申报时间提醒', '新春将至，祝{{客户类型}}生意兴隆！温馨提醒：假期前后的{{服务内容}}请留意时间安排，{{期限}}前如需临时加急处理请提前与我们沟通。具体申报期限以税务机关公告为准。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90073002, 90070302, 1, '双节快乐｜{{客户类型}}假期涉税事项提示', '中秋快乐、国庆愉快！{{客户类型}}的{{服务内容}}若有收尾工作，请留意{{期限}}安排。长假期间如有涉税事项，可在工作群留言，我们节后第一时间处理。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90073003, 90070303, 1, '节日问候｜{{客户类型}}报税时间提醒', '节日快乐！{{客户类型}}别忘了{{期限}}前的{{服务内容}}，已签约客户我们会主动跟进无需您操心；如有疑问随时联系您的财税顾问。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90074001, 90070401, 1, '合规资质｜{{客户类型}}的代账服务选择', '公司持有行业主管部门认可的代理记账许可资质，团队具备专业会计任职资格，为{{客户类型}}提供{{服务内容}}服务，账务档案完整、按期申报。欢迎{{期限}}前预约了解服务流程与合同条款。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90074002, 90070402, 1, '专业立身｜{{客户类型}}财税服务团队介绍', '我们的财税顾问团队持证上岗，熟悉{{服务内容}}实务流程，可为{{客户类型}}提供合规、清晰的财税服务。{{期限}}前咨询可预约一次免费初步沟通，具体服务方案以合同为准。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90074003, 90070403, 1, '透明服务｜{{客户类型}}的记账报税流程说明', '服务过程透明可查：凭证单据交接、月度记账、按期申报、年度汇算各环节均有记录，{{客户类型}}可随时查看进度。{{期限}}前签约{{服务内容}}合同即可开通。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90075001, 90070501, 1, '政策速递｜{{客户类型}}关注的涉税新政解读', '近期涉税政策有调整（{{期限}}），我们为您整理了{{客户类型}}关心的{{服务内容}}要点。此解读仅供参考，具体执行口径以官方文件及主管税务机关答复为准，如需确认可联系我们单独沟通。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90075002, 90070502, 1, '优惠申报提醒｜{{客户类型}}可关注的政策适用情况', '提醒{{客户类型}}关注：现行税收优惠政策可能适用于您的{{服务内容}}。是否符合条件需结合企业经营情况判断，{{期限}}前可联系我们做一次适用性梳理。具体以政策原文为准。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90075003, 90070503, 1, '政策解读预约｜{{客户类型}}涉税疑问一对一沟通', '政策解读不替代专业判断。{{客户类型}}如对近期的{{服务内容}}有疑问，可在{{期限}}前预约一对一沟通，我们会结合企业实际情况给出行之有理的分析与建议。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90076001, 90070601, 1, '续费提醒｜{{客户类型}}代账服务即将到期', '您好，{{客户类型}}！您的{{服务内容}}将于{{期限}}到期。为避免账务申报空档，建议您提前确认续费安排，我们会同步安排服务衔接。续费方案与价格以报价单为准，如有疑问请随时联系。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90076002, 90070602, 1, '年度续签｜{{客户类型}}服务合同续签提醒', '{{客户类型}}您好！您与我们的{{服务内容}}合同将于{{期限}}到期。若续签意愿确认，我们可提前准备下一服务周期的排期与对接人安排。谢谢长期信任！', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90076003, 90070603, 1, '年审提醒｜{{客户类型}}资质年检续费安排', '{{客户类型}}您好！您的{{服务内容}}需在{{期限}}前完成。若继续委托我们办理，请确认续费安排，我们会按年份节点推进，避免逾期影响资质状态。具体办理时限以主管部门要求为准。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90077001, 90070701, 1, '好久不见｜{{客户类型}}近期可还好？', '您好，{{客户类型}}，好久不见！之前为您提供过{{服务内容}}服务，不知近期记账、申报、资质等事项是否都还顺利？如有需要，可随时联系我们，重新了解当下的服务方案，{{期限}}前沟通可预约免费初步咨询。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90077002, 90070702, 1, '服务改进｜想听听您对{{服务内容}}的建议', '{{客户类型}}您好！您之前体验过我们的{{服务内容}}，我们一直在改进。想听听您的真实建议，无论是否继续合作，都欢迎{{期限}}前告诉我们，我们愿意据此优化服务。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0),
    (90077003, 90070703, 1, '新服务上线｜{{客户类型}}或有关注', '近期我们上线了{{服务内容}}相关服务，{{客户类型}}若有需要可以了解一下。{{活动信息}}，{{期限}}内预约有效，具体内容欢迎咨询，仅供参考，以实际沟通为准。', NULL, NULL, NULL, '财税模板 V12 入库', 'ARCHIVED', 1, NULL, NULL, 'finance-tax-v12', now(), now(), now(), 0);

-- 4. 回填模板 current_version_id（指向第 3 步的版本快照）
UPDATE scrm.scrm_message_template_center AS t
SET current_version_id = v.id
FROM scrm.scrm_message_template_version AS v
WHERE v.template_id = t.id
  AND v.version_number = 1;

-- 5. 分组 template_count 回填（本组入库模板数）
UPDATE scrm.scrm_message_template_group AS g
SET template_count = sub.cnt
FROM (SELECT group_id, count(*) AS cnt FROM scrm.scrm_message_template_center WHERE group_id >= 900100 AND group_id <= 900106 GROUP BY group_id) AS sub
WHERE sub.group_id = g.id;