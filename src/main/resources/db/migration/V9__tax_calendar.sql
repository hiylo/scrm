-- ======================================================================
-- 财税数字化 - 报税日历/续费流失/政策推送域（V9）
-- ======================================================================
-- 用途: 按 docs/财税数字化系统立项方案.md §2 与 docs/tasks/T08/（tax-calendar.md /
--   renewal.md / policy-push.md）落地三张业务表:
--   1. scrm_tax_declaration_profile 税种申报档案（客户 × 税种 × 申报周期静态配置,
--      可人工复核 last_verified_at / 暂停 / 取消）;
--   2. scrm_tax_declaration_record 申报记录（周期实例 + 状态机主体:
--      NOT_STARTED → PENDING → FILED → PAID → ARCHIVED, 逾期支线 OVERDUE;
--      uk_record_profile_period 防重复生成）;
--   3. scrm_policy_push_task 政策推送任务（D 域核心表: 匹配规则参数 + 双重人工闸门
--      状态机 DRAFT → PENDING_AUDIT → APPROVED → PUBLISHING → COMPLETED/FAILED）。
-- 复用约定: 日历展示聚合档案+记录（ScrmCalendarEventEntity 只落 TAX_* 展示事件）;
--   预警推送复用 scrm_notification_template（本迁移预置 tax-deadline-remind /
--   renewal-task-remind 两个模板）; 调度复用 scrm_scheduled_task（不在本迁移预置,
--   由运营在调度中心按需创建, 处理器类名见对应服务 Javadoc）。
-- 数据库: PostgreSQL 16+   Schema: scrm
-- 幂等: 仅 CREATE TABLE + CREATE INDEX + INSERT 模板; 重跑由 Flyway 校验和阻止。
-- ======================================================================

-- 1. 税种申报档案
CREATE TABLE scrm.scrm_tax_declaration_profile (
    id                  bigint NOT NULL,
    create_time         timestamp without time zone NOT NULL,
    update_time         timestamp without time zone NOT NULL,
    version             bigint DEFAULT 0 NOT NULL,
    customer_id         bigint NOT NULL,
    customer_name       character varying(200),
    tax_type            character varying(30) NOT NULL,
    declaration_cycle   character varying(10) DEFAULT 'MONTHLY' NOT NULL,
    taxpayer_type       character varying(20) DEFAULT 'SMALL' NOT NULL,
    deadline_rule       character varying(30) DEFAULT 'DAY15' NOT NULL,
    region              character varying(100),
    start_date          date NOT NULL,
    end_date            date,
    declaration_org     character varying(200),
    remark              character varying(500),
    status              character varying(20) DEFAULT 'ACTIVE' NOT NULL,
    last_verified_at    date,
    created_by          character varying(100),
    CONSTRAINT pk_tax_declaration_profile PRIMARY KEY (id),
    CONSTRAINT uk_profile_customer_tax UNIQUE (customer_id, tax_type, taxpayer_type, start_date)
);
COMMENT ON TABLE scrm.scrm_tax_declaration_profile IS '税种申报档案: 客户×税种×申报周期静态配置（T08A）';
COMMENT ON COLUMN scrm.scrm_tax_declaration_profile.tax_type IS '税种: VAT(增值税) / IIT(个人所得税) / CIT(企业所得税) / SOCIAL_SECURITY(社保)';
COMMENT ON COLUMN scrm.scrm_tax_declaration_profile.declaration_cycle IS '申报周期: MONTHLY / QUARTERLY / YEARLY / ANNUAL_SETTLEMENT(年度汇算)';
COMMENT ON COLUMN scrm.scrm_tax_declaration_profile.taxpayer_type IS '纳税人类型: SMALL(小规模) / GENERAL(一般纳税人) / INDIVIDUAL(个体户)';
COMMENT ON COLUMN scrm.scrm_tax_declaration_profile.deadline_rule IS '征期规则: DAY15(次/季终后15日) / DAY31(汇算次年3/31) / DAY05_31(汇算5/31) / LOCAL(属地自定义)';
COMMENT ON COLUMN scrm.scrm_tax_declaration_profile.status IS '状态: ACTIVE / PAUSED / CANCELED';
CREATE INDEX idx_tax_profile_customer ON scrm.scrm_tax_declaration_profile USING btree (customer_id);
CREATE INDEX idx_tax_profile_tax_status ON scrm.scrm_tax_declaration_profile USING btree (tax_type, status);

-- 2. 申报记录（状态机主体）
CREATE TABLE scrm.scrm_tax_declaration_record (
    id                  bigint NOT NULL,
    create_time         timestamp without time zone NOT NULL,
    update_time         timestamp without time zone NOT NULL,
    version             bigint DEFAULT 0 NOT NULL,
    profile_id          bigint NOT NULL,
    customer_id         bigint NOT NULL,
    tax_type            character varying(30) NOT NULL,
    region              character varying(100),
    period_start        date NOT NULL,
    period_end          date NOT NULL,
    due_date            date NOT NULL,
    tax_amount          numeric(15,2),
    paid_amount         numeric(15,2),
    declaration_status  character varying(20) DEFAULT 'NOT_STARTED' NOT NULL,
    overdue_days        integer DEFAULT 0 NOT NULL,
    escalated_at        timestamp without time zone,
    escalated_to        character varying(100),
    filed_at            timestamp without time zone,
    filed_by            character varying(100),
    paid_at             timestamp without time zone,
    tax_receipt_no      character varying(100),
    source              character varying(20) DEFAULT 'SYSTEM' NOT NULL,
    remark              character varying(500),
    created_by          character varying(100),
    CONSTRAINT pk_tax_declaration_record PRIMARY KEY (id),
    CONSTRAINT uk_record_profile_period UNIQUE (profile_id, period_start, period_end)
);
COMMENT ON TABLE scrm.scrm_tax_declaration_record IS '税种申报记录: 周期实例+状态机（T08A §1.2）';
COMMENT ON COLUMN scrm.scrm_tax_declaration_record.declaration_status IS '状态: NOT_STARTED / PENDING / FILED / PAID / OVERDUE / ARCHIVED / CANCELED';
COMMENT ON COLUMN scrm.scrm_tax_declaration_record.source IS '生成来源: SYSTEM(自动生成) / MANUAL(手工补录) / IMPORT(批量导入)';
COMMENT ON COLUMN scrm.scrm_tax_declaration_record.due_date IS '申报截止日期（按 deadline_rule + 节假日顺延计算）';
CREATE INDEX idx_tax_record_due ON scrm.scrm_tax_declaration_record USING btree (declaration_status, due_date);
CREATE INDEX idx_tax_record_customer_period ON scrm.scrm_tax_declaration_record USING btree (customer_id, period_start, period_end);
CREATE INDEX idx_tax_record_overdue ON scrm.scrm_tax_declaration_record USING btree (declaration_status, overdue_days);

-- 2.5 服务续费任务（T08B §2.3）
CREATE TABLE scrm.scrm_renewal_task (
    id                      bigint NOT NULL,
    create_time             timestamp without time zone NOT NULL,
    update_time             timestamp without time zone NOT NULL,
    version                 bigint DEFAULT 0 NOT NULL,
    task_no                 character varying(50) NOT NULL,
    contract_id             bigint NOT NULL,
    contract_no             character varying(100),
    contract_name           character varying(500),
    customer_id             bigint,
    customer_name           character varying(200),
    sales_person_id         character varying(100),
    sales_person_name       character varying(100),
    planned_renewal_date    date NOT NULL,
    remind_days_before      integer DEFAULT 30 NOT NULL,
    renewal_option          character varying(30),
    discount_pct            numeric(5,2),
    grace_months            integer,
    renewal_amount          numeric(15,2),
    approval_instance_id    bigint,
    approval_required       boolean DEFAULT false NOT NULL,
    status                  character varying(20) DEFAULT 'PENDING' NOT NULL,
    source                  character varying(20) DEFAULT 'SYSTEM_SCHEDULE' NOT NULL,
    last_reminded_at        timestamp without time zone,
    remind_count            integer DEFAULT 0 NOT NULL,
    notes                   character varying(500),
    created_by              character varying(100),
    CONSTRAINT pk_renewal_task PRIMARY KEY (id),
    CONSTRAINT uk_renewal_task_no UNIQUE (task_no)
);
COMMENT ON TABLE scrm.scrm_renewal_task IS '服务续费任务: 合同到期前生成, 30/7/3 三级提醒 + 续费结果录入（T08B）';
COMMENT ON COLUMN scrm.scrm_renewal_task.renewal_option IS '续费选项: STANDARD(标准价) / DISCOUNT(折扣) / GRACE(赠期) / CANCEL';
COMMENT ON COLUMN scrm.scrm_renewal_task.status IS '状态: PENDING / APPROVAL_PENDING / APPROVED / IN_PROGRESS / RENEWED / LOST / OVERDUE / CANCELLED';
COMMENT ON COLUMN scrm.scrm_renewal_task.approval_required IS '是否需审批（renewal_option != STANDARD → true, 审批通过才回填金额）';
CREATE INDEX idx_renewal_task_contract ON scrm.scrm_renewal_task USING btree (contract_id);
CREATE INDEX idx_renewal_task_customer ON scrm.scrm_renewal_task USING btree (customer_id);
CREATE INDEX idx_renewal_task_status ON scrm.scrm_renewal_task USING btree (status);
CREATE INDEX idx_renewal_task_due ON scrm.scrm_renewal_task USING btree (status, planned_renewal_date);

-- 3. 政策推送任务（D 域）
CREATE TABLE scrm.scrm_policy_push_task (
    id                      bigint NOT NULL,
    create_time             timestamp without time zone NOT NULL,
    update_time             timestamp without time zone NOT NULL,
    version                 bigint DEFAULT 0 NOT NULL,
    task_no                 character varying(50) NOT NULL,
    policy_article_id       bigint NOT NULL,
    policy_code             character varying(50),
    policy_title            character varying(500),
    tax_type                character varying(30) DEFAULT 'ALL',
    size_range              character varying(30) DEFAULT 'ALL',
    region                  character varying(100),
    tag_codes               character varying(500),
    segment_id              bigint,
    effective_from          date,
    effective_until         date,
    audit_mode              character varying(20) DEFAULT 'SAMPLING' NOT NULL,
    audit_status            character varying(20) DEFAULT 'PENDING' NOT NULL,
    audited_by              character varying(100),
    audited_at              timestamp without time zone,
    expected_recipient_count integer DEFAULT 0,
    snapshot_customer_ids   text,
    status                  character varying(20) DEFAULT 'DRAFT' NOT NULL,
    published_at            timestamp without time zone,
    completed_at            timestamp without time zone,
    fail_reason             character varying(500),
    notes                   character varying(500),
    created_by              character varying(100),
    CONSTRAINT pk_policy_push_task PRIMARY KEY (id),
    CONSTRAINT uk_policy_push_task_no UNIQUE (task_no)
);
COMMENT ON TABLE scrm.scrm_policy_push_task IS '政策推送任务: 匹配规则参数+双重人工闸门状态机（T08D）';
COMMENT ON COLUMN scrm.scrm_policy_push_task.audit_mode IS '二级人工闸门: SAMPLING(抽样核验) / FULL(全量核验)';
COMMENT ON COLUMN scrm.scrm_policy_push_task.audit_status IS '闸门状态: PENDING / SAMPLING_AUDIT / APPROVED / REJECTED / CANCELED';
COMMENT ON COLUMN scrm.scrm_policy_push_task.status IS '任务状态: DRAFT / PENDING_AUDIT / APPROVED / PUBLISHING / COMPLETED / FAILED / REJECTED / CANCELED';
COMMENT ON COLUMN scrm.scrm_policy_push_task.snapshot_customer_ids IS '草稿期预计算命中客户 ID 快照（逗号分隔, 发布直接用, 不在线上逐客户跑条件）';
CREATE INDEX idx_policy_push_article ON scrm.scrm_policy_push_task USING btree (policy_article_id);
CREATE INDEX idx_policy_push_status ON scrm.scrm_policy_push_task USING btree (status);

-- 4. 预置站内信模板（报税提醒 / 续费任务提醒; 模板 ID 与 V8 88001 / V13 88002 错开）
INSERT INTO scrm.scrm_notification_template (
    id, template_name, template_code, category, channel, title, content,
    variables, sender_name, is_html, enabled, usage_count,
    create_time, update_time, version
) VALUES
    (88003, '报税截止提醒', 'tax-deadline-remind', 'ALERT', 'IN_APP',
     '报税提醒: {customerName} {taxTypeLabel} {periodLabel}',
     '【报税提醒】客户: {customerName}　税种: {taxTypeLabel}　所属期: {periodLabel}\n申报截止: {dueDate}　当前状态: {statusLabel}\n距截止还有 {daysLeft} 天 / 已逾期 {overdueDays} 天\n① 未申报: 请登录对应电子税务局在截止日前完成申报；\n② 申报≠缴款: 申报完成后请确认缴款并回填凭证号；\n③ 属地口径: {region} 电子税务局如有征期顺延公告，以公告为准。\n（内部提醒，勿直接转发客户）',
     'customerName,taxTypeLabel,periodLabel,dueDate,statusLabel,daysLeft,overdueDays,region',
     '报税系统', false, true, 0, now(), now(), 0),
    (88004, '合同续费任务提醒', 'renewal-task-remind', 'REMINDER', 'IN_APP',
     '续费提醒: {customerName} 合同 {contractName}',
     '【续费提醒】客户: {customerName}\n合同: {contractName}（{contractNo}）\n到期日: {endDate}　距到期还有 {daysLeft} 天\n当前状态: {statusLabel}\n请销售跟进续费意向，在任务上录入续费结果（已续/放弃/待定/流失）。',
     'customerName,contractName,contractNo,endDate,daysLeft,statusLabel',
     '续费系统', false, true, 0, now(), now(), 0),
    (88005, '政策推送内部提醒', 'policy-push-remind', 'SERVICE', 'IN_APP',
     '政策推送: {policyTitle}（{policyCode}）',
     '【政策推送】政策: {policyTitle}（{policyCode}）\n适用税种: {taxType}　适用地区: {region}\n生效: {effectiveFrom} ~ {effectiveUntil}\n命中客户: {customerName}（ID {customerId}）\n政策要点: {summary}\n（内部提醒，客户触达请走企微官方通道并经人工审核后转发；仅供参考，以最新政策及主管机关确认为准）',
     'policyTitle,policyCode,taxType,region,effectiveFrom,effectiveUntil,customerId,customerName,summary',
     '政策推送', false, true, 0, now(), now(), 0);
COMMENT ON TABLE scrm.scrm_notification_template IS '通知模板表（含报税日历/续费流失/政策推送域 V9 预置 tax-deadline-remind / renewal-task-remind / policy-push-remind 模板）';
