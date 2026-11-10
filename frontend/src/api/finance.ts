/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : finance.ts
 * Date : 2026/10/24 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { apiClient, apiClientInstance, LONG_TIMEOUT } from './client';

/** 分页结果（Spring Page 结构） */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 公司主体 */
export interface Company {
  id: string;
  companyName: string;
  creditCode?: string;
  companyType?: string;
  legalRepresentative?: string;
  registeredCapital?: string;
  registeredAddress?: string;
  establishDate?: string;
  businessStatus?: string;
  taxStatus?: string;
  serviceType?: string;
  serviceStatus?: string;
  serviceEndDate?: string;
  verified?: boolean;
  accountantId?: string;
  accountantName?: string;
  source?: string;
  remark?: string;
  contactCount?: number;
  createTime?: string;
}

/** 公司下联系人 */
export interface CompanyContact {
  id: string;
  nickname?: string;
  phone?: string;
  contactRole?: string;
  platformType?: string;
  companyId?: string;
}

/** 公司 360 概览 */
export interface CompanyOverview {
  companyId: string;
  companyName: string;
  creditCode?: string;
  companyType?: string;
  legalRepresentative?: string;
  businessStatus?: string;
  taxStatus?: string;
  serviceType?: string;
  serviceStatus?: string;
  serviceEndDate?: string;
  verified?: boolean;
  contacts: { total: number; byRole: Record<string, number> };
  business: {
    contracts: number;
    taxProfiles: number;
    licenseOcrTasks: number;
    inspectionTasks: number;
    renewalTasks: number;
  };
  risks: string[];
}

/** 待归并联系人 */
export interface UnlinkedContact {
  customerId: string;
  nickname?: string;
  phone?: string;
  platformType?: string;
  sourceChannel?: string;
  createTime?: string;
  signals: string[];
}

/** 补录/归并完成度 */
export interface BackfillStatus {
  total: number;
  withPhone: number;
  phoneMissing: number;
  linked: number;
  unlinked: number;
  phoneCoverage: number;
  linkCoverage: number;
}

/** 批量挂靠结果 */
export interface BulkBindResult {
  companyId: string;
  requested: number;
  bound: number;
  skipped: Array<{ customerId?: string; reason: string }>;
}

/** 证照识别字段 */
export interface OcrField {
  name: string;
  value: string;
  confidence?: number;
}

/** 证照识别任务 */
export interface LicenseOcrTask {
  id: string;
  customerId?: string;
  companyId?: string;
  fileName?: string;
  licenseType?: string;
  taskStatus?: string;
  overallOk?: boolean;
  reviewStatus?: string;
  mode?: string;
  engine?: string;
  docType?: string;
  fields?: OcrField[];
  verification?: Record<string, unknown>;
  warnings?: string[];
  rawText?: string;
  createTime?: string;
}

/** 公告源状态 */
export interface NoticeSource {
  code: string;
  name: string;
  enabled: boolean;
  ready: boolean;
  detail: string;
  blocker?: string | null;
}

/** 公告源自检结果 */
export interface NoticeStatus {
  usableSourceCount: number;
  sources: NoticeSource[];
}

/** 巡检执行历史 */
export interface InspectionRun {
  id: string;
  runType: string;
  triggerType: string;
  status: string;
  taskScanned: number;
  successCount: number;
  failedCount: number;
  changedCount: number;
  skipReason?: string;
  errorMessage?: string;
  operator?: string;
  startedAt?: string;
  finishedAt?: string;
  costMs?: number;
}

/** 执行统计 */
export interface InspectionRunStats {
  days: number;
  since: string;
  byType: Array<{
    runType: string;
    totalRuns: number;
    success: number;
    partial: number;
    failed: number;
    skipped: number;
    taskScanned: number;
    successCount: number;
    failedCount: number;
    lastRunAt?: string;
    lastStatus?: string;
  }>;
}

/** 税务抓取汇总 */
export interface ScrapeSummary {
  taskScanned: number;
  recordsMatched: number;
  captchaBlocked: number;
  failed: number;
}

/** 工商巡检汇总 */
export interface BusinessSummary {
  taskScanned: number;
  success: number;
  failed: number;
  changed: number;
  skipReason?: string | null;
}

/** 财税域就绪模块 */
export interface ReadinessModule {
  key: string;
  name: string;
  ready: boolean;
  detail: string;
  blocker?: string | null;
}

/** 财税域就绪汇总 */
export interface Readiness {
  generatedAt: string;
  readyCount: number;
  totalCount: number;
  allReady: boolean;
  modules: ReadinessModule[];
  note?: string;
}

// ============================================================
// 公司主体
// ============================================================

/** 分页查询公司主体 */
export const listCompanies = (
  params: { keyword?: string; serviceStatus?: string; businessStatus?: string; page?: number; size?: number },
) => apiClient.get<Page<Company>>('/scrm/companies', { params });

/** 公司详情 */
export const getCompany = (id: string) => apiClient.get<Company>(`/scrm/companies/${id}`);

/** 公司 360 概览 */
export const getCompanyOverview = (id: string) =>
  apiClient.get<CompanyOverview>(`/scrm/companies/${id}/overview`);

/** 公司下联系人 */
export const listCompanyContacts = (id: string) =>
  apiClient.get<CompanyContact[]>(`/scrm/companies/${id}/contacts`);

/** 创建公司主体 */
export const createCompany = (payload: Partial<Company> & { ownerAccountId?: number }) =>
  apiClient.post<Company>('/scrm/companies', payload);

/** 更新公司主体 */
export const updateCompany = (id: string, payload: Partial<Company>) =>
  apiClient.put<Company>(`/scrm/companies/${id}`, payload);

/** 删除公司主体 */
export const deleteCompany = (id: string) => apiClient.delete<void>(`/scrm/companies/${id}`);

// ============================================================
// 存量归并 / 资料补录
// ============================================================

/** 待归并清单 */
export const listUnlinkedContacts = (params: { keyword?: string; page?: number; size?: number }) =>
  apiClient.get<Page<UnlinkedContact>>('/scrm/companies/unlinked-contacts', { params });

/** 为公司推荐候选联系人 */
export const suggestContacts = (companyId: string, size = 20) =>
  apiClient.get<Array<Record<string, unknown>>>(
    `/scrm/companies/${companyId}/suggest-contacts`, { params: { size } });

/** 为联系人推荐候选公司 */
export const suggestCompanies = (customerId: string, size = 20) =>
  apiClient.get<Array<Record<string, unknown>>>(
    '/scrm/companies/suggest-companies', { params: { customerId, size } });

/** 批量挂靠 */
export const bulkBindContacts = (companyId: string, customerIds: string[], contactRole?: string) =>
  apiClient.post<BulkBindResult>(
    `/scrm/companies/${companyId}/contacts/bulk`,
    customerIds,
    { params: contactRole ? { contactRole } : {} },
  );

/** 解除联系人归属 */
export const unbindContact = (customerId: string) =>
  apiClient.delete<void>(`/scrm/companies/contacts/${customerId}`);

/** 补录完成度 */
export const getBackfillStatus = () =>
  apiClient.get<BackfillStatus>('/scrm/companies/backfill/status');

/** 批量补录手机号 */
export const backfillPhones = (items: Array<{ customerId: string; phone: string }>) =>
  apiClient.post<Record<string, unknown>>('/scrm/companies/backfill/phones', items);

/** 按手机号批量挂靠 */
export const bindByPhones = (companyId: string, items: Array<{ phone: string }>, contactRole?: string) =>
  apiClient.post<Record<string, unknown>>(
    `/scrm/companies/${companyId}/contacts/by-phones`,
    items,
    { params: contactRole ? { contactRole } : {} },
  );

/** 客户下拉选项（证照上传时用于确定归属账号） */
export interface CustomerOption {
  id: string;
  nickname?: string;
  phone?: string;
  platformType?: string;
}

/**
 * 按昵称/手机号搜索客户，供下拉选择使用。
 * <p>
 * 只取一页（默认 20 条）足够下拉展示；用户输入关键字时再按需检索。
 * 不做全量预加载——客户表有 1269 条，一次性拉全量既慢又没必要。
 * </p>
 *
 * @param keyword 昵称或手机号关键字，空则取默认一页
 * @param size    返回条数，默认 20
 * @returns 客户分页结果
 */
export const searchCustomers = (keyword?: string, size = 20) =>
  apiClient.get<Page<CustomerOption>>('/scrm/customers', {
    params: { ...(keyword ? { keyword } : {}), page: 0, size },
  });

// ============================================================
// 证照 OCR
// ============================================================

/** 上传证照并识别（multipart，长耗时） */
export const uploadLicense = (file: File, payload: { licenseType: string; customerId?: string }) => {
  const form = new FormData();
  form.append('file', file);
  form.append('dto', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
  return apiClientInstance
    .post('/scrm/license-ocr/tasks', form, {
      timeout: LONG_TIMEOUT,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((r) => {
      const body = r.data as { status?: string; data?: LicenseOcrTask; message?: string };
      if (body?.status === 'SUCCESS') return body.data as LicenseOcrTask;
      throw new Error(body?.message || '识别失败');
    });
};

/** 证照任务详情 */
export const getLicenseTask = (id: string) =>
  apiClient.get<LicenseOcrTask>(`/scrm/license-ocr/tasks/${id}`);

/** 证照任务列表 */
export const listLicenseTasks = (params: { page?: number; size?: number; taskStatus?: string }) =>
  apiClient.get<Page<LicenseOcrTask>>('/scrm/license-ocr/tasks', { params });

/** 复核证照任务 */
export const reviewLicenseTask = (id: string, passed: boolean, remark?: string) =>
  apiClient.post<LicenseOcrTask>(`/scrm/license-ocr/tasks/${id}/review`, undefined, {
    params: { passed, ...(remark ? { remark } : {}) },
  });

/** 由证照识别结果建主体（闭环） */
export const createCompanyFromLicense = (taskId: string) =>
  apiClient.post<Company>(`/scrm/companies/from-license-task/${taskId}`);

// ============================================================
// 巡检 / 公告
// ============================================================

/** 公告源自检 */
export const getNoticeStatus = () =>
  apiClient.get<NoticeStatus>('/scrm/inspections/notice-status');

/** 启信宝接入状态 */
export const getQixinStatus = () =>
  apiClient.get<Record<string, unknown>>('/scrm/inspections/api-status');

/** 手动触发税务抓取（真实抓取，长耗时） */
export const runTaxInspection = () =>
  apiClient.post<ScrapeSummary>('/scrm/inspections/run-tax', undefined, { timeout: LONG_TIMEOUT });

/** 手动触发工商巡检 */
export const runBusinessInspection = () =>
  apiClient.post<BusinessSummary>('/scrm/inspections/run-business', undefined,
    { timeout: LONG_TIMEOUT });

/** 执行历史 */
export const listInspectionRuns = (params: { runType?: string; page?: number; size?: number }) =>
  apiClient.get<Page<InspectionRun>>('/scrm/inspections/runs', { params });

/** 执行统计 */
export const getInspectionRunStats = (days = 7) =>
  apiClient.get<InspectionRunStats>('/scrm/inspections/runs/stats', { params: { days } });

// ============================================================
// 就绪汇总
// ============================================================

/** 财税域就绪汇总 */
export const getReadiness = () => apiClient.get<Readiness>('/scrm/finance/readiness');

// ============================================================
// 报税日历
// ============================================================

/** 税种申报档案 */
export interface TaxProfile {
  id: string;
  customerId: string;
  companyId?: string;
  customerName?: string;
  taxType: string;
  declarationCycle?: string;
  taxpayerType?: string;
  deadlineRule?: string;
  region?: string;
  startDate: string;
  endDate?: string;
  declarationOrg?: string;
  remark?: string;
  status?: string;
  lastVerifiedAt?: string;
  createTime?: string;
}

/** 申报记录 */
export interface TaxRecord {
  id: string;
  profileId: string;
  customerId: string;
  taxType: string;
  region?: string;
  periodStart: string;
  periodEnd: string;
  dueDate: string;
  taxAmount?: number;
  paidAmount?: number;
  declarationStatus: string;
  overdueDays?: number;
  escalatedAt?: string;
  escalatedTo?: string;
  filedAt?: string;
  filedBy?: string;
  paidAt?: string;
  taxReceiptNo?: string;
  source?: string;
  remark?: string;
}

/** 申报档案创建/更新入参 */
export interface TaxProfilePayload {
  customerId: string;
  customerName?: string;
  taxType: string;
  declarationCycle?: string;
  taxpayerType?: string;
  deadlineRule?: string;
  region?: string;
  startDate: string;
  endDate?: string;
  declarationOrg?: string;
  remark?: string;
  status?: string;
}

/** 分页查询申报档案（companyId 为代账场景主查询维度） */
export const listTaxProfiles = (params: {
  companyId?: string;
  customerId?: string;
  taxType?: string;
  status?: string;
  page?: number;
  size?: number;
}) => apiClient.get<Page<TaxProfile>>('/scrm/tax-calendar/profiles', { params });

/** 档案详情 */
export const getTaxProfile = (id: string) =>
  apiClient.get<TaxProfile>(`/scrm/tax-calendar/profiles/${id}`);

/** 创建申报档案 */
export const createTaxProfile = (payload: TaxProfilePayload) =>
  apiClient.post<TaxProfile>('/scrm/tax-calendar/profiles', payload);

/** 更新申报档案 */
export const updateTaxProfile = (id: string, payload: Partial<TaxProfilePayload>) =>
  apiClient.put<TaxProfile>(`/scrm/tax-calendar/profiles/${id}`, payload);

/** 删除申报档案 */
export const deleteTaxProfile = (id: string) =>
  apiClient.delete<void>(`/scrm/tax-calendar/profiles/${id}`);

/** 档案下的申报记录 */
export const listRecordsByProfile = (profileId: string) =>
  apiClient.get<TaxRecord[]>(`/scrm/tax-calendar/profiles/${profileId}/records`);

/** 客户下的申报记录 */
export const listRecordsByCustomer = (customerId: string) =>
  apiClient.get<TaxRecord[]>(`/scrm/tax-calendar/customers/${customerId}/records`);

/** 公司主体下的申报记录（经档案关联） */
export const listRecordsByCompany = (companyId: string) =>
  apiClient.get<TaxRecord[]>(`/scrm/tax-calendar/companies/${companyId}/records`);

/** 报税日历月视图（含全部状态，仅排除已取消） */
export const listRecordsByMonth = (year: number, month: number) =>
  apiClient.get<TaxRecord[]>('/scrm/tax-calendar/records/month', { params: { year, month } });

/** 本月申报待办单项（会计工作台） */
export interface MonthTodo {
  recordId: string;
  companyId?: string;
  companyName?: string;
  taxType: string;
  periodStart?: string;
  periodEnd?: string;
  dueDate?: string;
  taxAmount?: number;
  paidAmount?: number;
  status: string;
  overdueDays?: number;
}

/** 本月申报待办（三组分组，按当前登录会计数据隔离） */
export interface MonthTodoResult {
  pending: MonthTodo[];
  toPay: MonthTodo[];
  overdue: MonthTodo[];
  pendingCount: number;
  toPayCount: number;
  overdueCount: number;
}

/** 会计工作台本月申报待办（分 待申报/待缴款/已逾期 三组） */
export const getMonthTodo = (year: number, month: number) =>
  apiClient.get<MonthTodoResult>('/scrm/tax-calendar/records/month/todo', {
    params: { year, month },
  });

/** 申报记录详情 */
export const getTaxRecord = (id: string) =>
  apiClient.get<TaxRecord>(`/scrm/tax-calendar/records/${id}`);

/** 申报完成（PENDING/OVERDUE → FILED） */
export const markRecordFiled = (id: string, taxAmount?: number, receiptNo?: string) =>
  apiClient.post<TaxRecord>(`/scrm/tax-calendar/records/${id}/filed`, undefined, {
    params: {
      ...(taxAmount != null ? { taxAmount } : {}),
      ...(receiptNo ? { receiptNo } : {}),
    },
  });

/** 批量申报完成（逐条校验状态，返回成功/失败明细） */
export const batchMarkFiled = (ids: string[]) =>
  apiClient.post<{
    successCount: number;
    failCount: number;
    failures: Array<{ id: string; message: string }>;
  }>('/scrm/tax-calendar/records/batch-filed', ids);

/** 缴款完成（FILED/OVERDUE → PAID） */
export const markRecordPaid = (id: string, paidAmount?: number, receiptNo?: string) =>
  apiClient.post<TaxRecord>(`/scrm/tax-calendar/records/${id}/paid`, undefined, {
    params: {
      ...(paidAmount != null ? { paidAmount } : {}),
      ...(receiptNo ? { receiptNo } : {}),
    },
  });

/** 补报完成（OVERDUE → PAID 快捷） */
export const markRecordBackFilled = (
  id: string,
  payload: { taxAmount?: number; paidAmount?: number; receiptNo?: string },
) => apiClient.post<TaxRecord>(`/scrm/tax-calendar/records/${id}/backfill`, undefined, {
  params: {
    ...(payload.taxAmount != null ? { taxAmount: payload.taxAmount } : {}),
    ...(payload.paidAmount != null ? { paidAmount: payload.paidAmount } : {}),
    ...(payload.receiptNo ? { receiptNo: payload.receiptNo } : {}),
  },
});

/** 期后归档（PAID → ARCHIVED） */
export const archiveRecord = (id: string) =>
  apiClient.post<TaxRecord>(`/scrm/tax-calendar/records/${id}/archive`);

/** 取消记录（减免/政策豁免） */
export const cancelRecord = (id: string, remark?: string) =>
  apiClient.post<TaxRecord>(`/scrm/tax-calendar/records/${id}/cancel`, undefined, {
    params: remark ? { remark } : {},
  });

// ============================================================
// 服务续费
// ============================================================

/** 续费任务 */
export interface RenewalTask {
  id: string;
  taskNo: string;
  contractId: string;
  contractNo?: string;
  contractName?: string;
  customerId?: string;
  companyId?: string;
  customerName?: string;
  salesPersonId?: string;
  salesPersonName?: string;
  plannedRenewalDate: string;
  remindDaysBefore?: number;
  renewalOption?: string;
  discountPct?: number;
  graceMonths?: number;
  renewalAmount?: number;
  approvalInstanceId?: string;
  approvalRequired?: boolean;
  status: string;
  source?: string;
  remindCount?: number;
  lastRemindedAt?: string;
  notes?: string;
  createTime?: string;
}

/** 续费任务创建入参 */
export interface RenewalTaskPayload {
  contractId: string;
  plannedRenewalDate?: string;
  remindDaysBefore?: number;
  renewalOption?: string;
  source?: string;
  notes?: string;
}

/** 分页查询续费任务（companyId 为代账场景主查询维度） */
export const listRenewalTasks = (params: {
  status?: string;
  customerId?: string;
  companyId?: string;
  page?: number;
  size?: number;
}) => apiClient.get<Page<RenewalTask>>('/scrm/renewals', { params });

/** 续费任务详情 */
export const getRenewalTask = (id: string) =>
  apiClient.get<RenewalTask>(`/scrm/renewals/${id}`);

/** 手动创建续费任务 */
export const createRenewalTask = (payload: RenewalTaskPayload) =>
  apiClient.post<RenewalTask>('/scrm/renewals', payload);

/** 录入续费结果（RENEWED / LOST / CANCELLED） */
export const recordRenewalResult = (id: string, option: string, amount?: number) =>
  apiClient.post<RenewalTask>(`/scrm/renewals/${id}/result`, undefined, {
    params: { option, ...(amount != null ? { amount } : {}) },
  });

/** 审批通过回填（折扣/赠期） */
export const approveRenewal = (
  id: string,
  payload: { discountPct?: number; graceMonths?: number; amount?: number; approvalInstanceId?: string },
) => apiClient.post<RenewalTask>(`/scrm/renewals/${id}/approve`, undefined, {
  params: {
    ...(payload.discountPct != null ? { discountPct: payload.discountPct } : {}),
    ...(payload.graceMonths != null ? { graceMonths: payload.graceMonths } : {}),
    ...(payload.amount != null ? { amount: payload.amount } : {}),
    ...(payload.approvalInstanceId ? { approvalInstanceId: payload.approvalInstanceId } : {}),
  },
});

// ============================================================
// 合同（续费建单需要选合同）
// ============================================================

/** 合同简要信息 */
export interface ContractOption {
  id: string;
  contractNo?: string;
  contractName?: string;
  customerId?: string;
  customerName?: string;
  companyId?: string;
  contractAmount?: number;
  status?: string;
  endDate?: string;
}

/** 分页查询合同（续费建单下拉用） */
export const listContracts = (params: {
  keyword?: string;
  customerId?: string;
  status?: string;
  page?: number;
  size?: number;
}) => apiClient.get<Page<ContractOption>>('/scrm/contracts/list', { params });

// ============================================================
// 政策推送
// ============================================================

/** 政策推送任务 */
export interface PolicyPushTask {
  id: string;
  taskNo: string;
  policyArticleId: string;
  policyCode?: string;
  policyTitle?: string;
  taxType?: string;
  sizeRange?: string;
  region?: string;
  tagCodes?: string;
  segmentId?: string;
  effectiveFrom?: string;
  effectiveUntil?: string;
  auditMode?: string;
  auditStatus?: string;
  auditedBy?: string;
  auditedAt?: string;
  expectedRecipientCount?: number;
  snapshotCustomerIds?: string;
  status: string;
  publishedAt?: string;
  completedAt?: string;
  failReason?: string;
  notes?: string;
  createTime?: string;
}

/** 政策推送任务创建入参 */
export interface PolicyPushPayload {
  policyArticleId: string;
  taxType?: string;
  sizeRange?: string;
  region?: string;
  tagCodes?: string;
  segmentId?: string;
  effectiveFrom?: string;
  effectiveUntil?: string;
  auditMode?: string;
  notes?: string;
}

/** 采样审核条目 */
export interface SamplingItem {
  customerId: string;
  customerName: string;
}

/** 分页查询政策推送任务 */
export const listPolicyPushTasks = (params: {
  status?: string;
  keyword?: string;
  page?: number;
  size?: number;
}) => apiClient.get<Page<PolicyPushTask>>('/scrm/policy-pushes', { params });

/** 政策推送任务详情 */
export const getPolicyPushTask = (id: string) =>
  apiClient.get<PolicyPushTask>(`/scrm/policy-pushes/${id}`);

/** 创建政策推送任务 */
export const createPolicyPushTask = (payload: PolicyPushPayload) =>
  apiClient.post<PolicyPushTask>('/scrm/policy-pushes', payload);

/** 重算命中客户快照 */
export const refreshPolicySnapshot = (id: string) =>
  apiClient.post<PolicyPushTask>(`/scrm/policy-pushes/${id}/refresh`);

/** 提交审核 */
export const submitPolicyPush = (id: string) =>
  apiClient.post<PolicyPushTask>(`/scrm/policy-pushes/${id}/submit`);

/** 审核通过 */
export const approvePolicyPush = (id: string, auditor?: string) =>
  apiClient.post<PolicyPushTask>(`/scrm/policy-pushes/${id}/approve`, undefined, {
    params: auditor ? { auditor } : {},
  });

/** 审核驳回 */
export const rejectPolicyPush = (id: string, reason: string) =>
  apiClient.post<PolicyPushTask>(`/scrm/policy-pushes/${id}/reject`, undefined, {
    params: { reason },
  });

/** 撤销任务（未发布前） */
export const cancelPolicyPush = (id: string) =>
  apiClient.post<PolicyPushTask>(`/scrm/policy-pushes/${id}/cancel`);

/** 发布推送（真实下发, 长耗时） */
export const publishPolicyPush = (id: string) =>
  apiClient.post<PolicyPushTask>(`/scrm/policy-pushes/${id}/publish`, undefined, {
    timeout: LONG_TIMEOUT,
  });

/** 采样审核名单（二级人工闸门） */
export const listPolicySampling = (id: string) =>
  apiClient.get<SamplingItem[]>(`/scrm/policy-pushes/${id}/sampling`);

// ============================================================
// 知识条目（政策推送选文章）
// ============================================================

/** 知识条目简要信息 */
export interface KnowledgeArticleOption {
  id: string;
  title: string;
  articleCode?: string;
  articleType?: string;
  status?: string;
  reviewStatus?: string;
  tags?: string;
  publishedAt?: string;
}

/**
 * 搜索可推送的政策条目。
 * <p>
 * 政策推送只接受 {@code articleType=POLICY} 且已发布、已审核通过的条目，
 * 因此这里把三重条件一次性带进查询，避免让用户选到不可用的条目后再被后端拒绝。
 * </p>
 *
 * @param keyword 标题/摘要关键字
 * @param size    返回条数
 * @returns 政策条目分页结果
 */
export const searchPolicyArticles = (keyword?: string, size = 20) =>
  apiClient.get<Page<KnowledgeArticleOption>>('/scrm/knowledge-base/articles/list', {
    params: {
      articleType: 'POLICY',
      status: 'PUBLISHED',
      ...(keyword ? { keyword } : {}),
      page: 0,
      size,
    },
  });

// ============================================================
// 负责会计分派
// ============================================================

/** 用户简要信息（指派会计用） */
export interface UserOption {
  id: string;
  username?: string;
  displayName?: string;
  roles?: string[];
}

/**
 * 搜索可指派的用户。
 * <p>
 * 代账公司的会计与普通销售在同一个用户表里，权限上不做强区分（由 RBAC 控制操作），
 * 因此这里只按关键字搜索在职用户，交给管理员判断谁负责哪些主体。
 * </p>
 *
 * @param keyword 用户名/姓名关键字
 * @param size    返回条数
 * @returns 用户分页结果
 */
export const searchUsers = (keyword?: string, size = 20) =>
  apiClient.get<Page<UserOption>>('/scrm/users', {
    params: { ...(keyword ? { keyword } : {}), page: 0, size },
  });

/**
 * 指派/取消负责会计。
 * <p>
 * {@code accountantId} 传 {@code null} 或 {@code 0} 表示取消指派；
 * 取消后相关提醒自动回落到配置的默认收件人。
 * </p>
 *
 * @param companyId    公司主体 ID
 * @param accountantId 负责会计的用户 ID（null/0 = 取消指派）
 * @param accountantName 负责会计姓名（冗余展示用）
 * @returns 更新后的公司主体
 */
export const assignAccountant = (
  companyId: string,
  accountantId: string | null,
  accountantName?: string,
) =>
  apiClient.put<Company>(`/scrm/companies/${companyId}/accountant`, undefined, {
    params: {
      accountantId: accountantId ?? 0,
      ...(accountantName ? { accountantName } : {}),
    },
  });

// ============================================================
// 多会计工作台
// ============================================================

/** 批量指派结果 */
export interface BatchAssignResult {
  requested: number;
  assigned: number;
  failed: Array<{ companyId: string; reason: string }>;
}

/** 会计工作量条目 */
export interface AccountantWorkload {
  accountantId: string | null;
  accountantName: string;
  companyCount: number;
}

/**
 * 我的客户（当前登录会计负责的主体）。
 * <p>数据范围由后端按登录用户解析，前端不传 accountantId，避免越权。</p>
 */
export const listMyCompanies = (keyword?: string) =>
  apiClient.get<Company[]>('/scrm/companies/mine', {
    params: keyword ? { keyword } : {},
  });

/** 批量指派负责会计 */
export const batchAssignAccountant = (
  companyIds: string[],
  accountantId: string | null,
  accountantName?: string,
) =>
  apiClient.put<BatchAssignResult>('/scrm/companies/accountants/batch', {
    companyIds,
    accountantId: accountantId ?? 0,
    accountantName: accountantName ?? '',
  });

/** 会计工作量概览 */
export const getAccountantWorkload = () =>
  apiClient.get<AccountantWorkload[]>('/scrm/companies/accountants/workload');

// ============================================================
// 合规域：工商年报 + 资质许可
// ============================================================

/** 工商年报 */
export interface AnnualReport {
  id: string;
  companyId: string;
  companyName?: string;
  reportYear: number;
  dueDate: string;
  status: string;
  submittedAt?: string;
  submittedBy?: string;
  receiptNo?: string;
  remark?: string;
  remindCount?: number;
  lastRemindedAt?: string;
}

/** 资质许可 */
export interface Qualification {
  id: string;
  companyId: string;
  companyName?: string;
  qualificationName: string;
  qualificationType?: string;
  certNo?: string;
  issuer?: string;
  issueDate?: string;
  expireDate?: string;
  status: string;
  remindDaysBefore?: number;
  remindCount?: number;
  lastRemindedAt?: string;
  remark?: string;
}

/** 年报统计 */
export interface AnnualSummary {
  pending: number;
  submitted: number;
  overdue: number;
  exempt: number;
  total: number;
}

/** 资质统计 */
export interface QualificationSummary {
  valid: number;
  expiring: number;
  expired: number;
  renewed: number;
  dueSoon90d: number;
  total: number;
}

/** 分页查询工商年报 */
export const listAnnualReports = (params: {
  status?: string;
  year?: number;
  companyId?: string;
  mine?: boolean;
  page?: number;
  size?: number;
}) => apiClient.get<Page<AnnualReport>>('/scrm/compliance/annual-reports', { params });

/** 年报统计 */
export const getAnnualSummary = (year?: number) =>
  apiClient.get<AnnualSummary>('/scrm/compliance/annual-reports/summary', {
    params: year ? { year } : {},
  });

/** 手动生成年报任务 */
export const generateAnnualReports = (year?: number) =>
  apiClient.post<number>('/scrm/compliance/annual-reports/generate', undefined, {
    params: year ? { year } : {},
  });

/** 标记年报已报送 */
export const submitAnnualReport = (id: string, receiptNo?: string, remark?: string) =>
  apiClient.post<AnnualReport>(`/scrm/compliance/annual-reports/${id}/submit`, undefined, {
    params: { ...(receiptNo ? { receiptNo } : {}), ...(remark ? { remark } : {}) },
  });

/** 标记年报免报 */
export const exemptAnnualReport = (id: string, remark: string) =>
  apiClient.post<AnnualReport>(`/scrm/compliance/annual-reports/${id}/exempt`, undefined, {
    params: { remark },
  });

/** 分页查询资质许可 */
export const listQualifications = (params: {
  status?: string;
  companyId?: string;
  keyword?: string;
  mine?: boolean;
  page?: number;
  size?: number;
}) => apiClient.get<Page<Qualification>>('/scrm/compliance/qualifications', { params });

/** 资质统计 */
export const getQualificationSummary = () =>
  apiClient.get<QualificationSummary>('/scrm/compliance/qualifications/summary');

/** 新增资质许可 */
export const createQualification = (payload: Partial<Qualification>) =>
  apiClient.post<Qualification>('/scrm/compliance/qualifications', payload);

/** 更新资质许可 */
export const updateQualification = (id: string, payload: Partial<Qualification>) =>
  apiClient.put<Qualification>(`/scrm/compliance/qualifications/${id}`, payload);

/** 删除资质许可 */
export const deleteQualification = (id: string) =>
  apiClient.delete<void>(`/scrm/compliance/qualifications/${id}`);

/** 标记证照已续期 */
export const renewQualification = (id: string, newExpire: string, remark?: string) =>
  apiClient.post<Qualification>(`/scrm/compliance/qualifications/${id}/renew`, undefined, {
    params: { newExpire, ...(remark ? { remark } : {}) },
  });

/** 公司合规数据（年报 + 证照） */
export const getCompanyCompliance = (companyId: string) =>
  apiClient.get<{ annualReports: AnnualReport[]; qualifications: Qualification[] }>(
    `/scrm/compliance/companies/${companyId}`,
  );

/** 立即推进合规状态（年报逾期 + 证照到期） */
export const advanceCompliance = () =>
  apiClient.post<{ annualOverdue: number; qualificationChanged: number }>('/scrm/compliance/advance');

/** 立即推送到期提醒 */
export const remindCompliance = () =>
  apiClient.post<{ annualPushed: number; qualificationPushed: number }>('/scrm/compliance/remind');

/** 月历默认月份建议（本月为空时自动跳到下一个有记录的月份） */
export const suggestCalendarMonth = () =>
  apiClient.get<{ year: number; month: number }>('/scrm/tax-calendar/records/month/suggest');

// ============================================================
// 薪酬域：员工档案 + 社保明细 + 个税明细
// ============================================================

/** 员工档案 */
export interface Employee {
  id: string;
  companyId: string;
  companyName?: string;
  name: string;
  idCardNo?: string;
  idCardMasked?: string;
  phone?: string;
  position?: string;
  hireDate?: string;
  leaveDate?: string;
  socialStatus: string;
  socialBase?: number;
  housingFundBase?: number;
  specialDeduction?: number;
  status: string;
  remark?: string;
}

/** 社保缴费明细 */
export interface SocialRecord {
  id: string;
  companyId: string;
  companyName?: string;
  employeeId: string;
  employeeName?: string;
  periodYear: number;
  periodMonth: number;
  baseAmount: number;
  pensionPersonal: number;
  pensionCompany: number;
  medicalPersonal: number;
  medicalCompany: number;
  unemploymentPersonal: number;
  unemploymentCompany: number;
  injuryCompany: number;
  maternityCompany: number;
  housingFundPersonal: number;
  housingFundCompany: number;
  totalPersonal: number;
  totalCompany: number;
  totalAmount: number;
  status: string;
  declaredAt?: string;
  paidAt?: string;
  remark?: string;
}

/** 个税扣缴明细 */
export interface IitRecord {
  id: string;
  companyId: string;
  companyName?: string;
  employeeId: string;
  employeeName?: string;
  periodYear: number;
  periodMonth: number;
  incomeAmount: number;
  basicDeduction: number;
  socialDeduction: number;
  specialDeduction: number;
  cumulativeIncome: number;
  cumulativeDeduction: number;
  cumulativeTaxable: number;
  taxRate: number;
  quickDeduction: number;
  cumulativeTax: number;
  paidTax: number;
  currentTax: number;
  status: string;
  declaredAt?: string;
  paidAt?: string;
  remark?: string;
}

/** 社保汇总 */
export interface SocialSummary {
  headcount: number;
  personalTotal: number;
  companyTotal: number;
  grandTotal: number;
  pending: number;
  declared: number;
  paid: number;
}

/** 个税汇总 */
export interface IitSummary {
  headcount: number;
  incomeTotal: number;
  taxTotal: number;
  pending: number;
  declared: number;
  paid: number;
}

/** 分页查询员工 */
export const listEmployees = (params: {
  companyId?: string;
  status?: string;
  keyword?: string;
  page?: number;
  size?: number;
}) => apiClient.get<Page<Employee>>('/scrm/payroll/employees', { params });

/** 员工详情 */
export const getEmployee = (id: string) => apiClient.get<Employee>(`/scrm/payroll/employees/${id}`);

/** 新增员工 */
export const createEmployee = (payload: Partial<Employee>) =>
  apiClient.post<Employee>('/scrm/payroll/employees', payload);

/** 更新员工 */
export const updateEmployee = (id: string, payload: Partial<Employee>) =>
  apiClient.put<Employee>(`/scrm/payroll/employees/${id}`, payload);

/** 删除员工 */
export const deleteEmployee = (id: string) =>
  apiClient.delete<void>(`/scrm/payroll/employees/${id}`);

/** 批量生成某月社保 + 个税明细（incomes 形如 "id:8000,id2:12000"） */
export const generatePayroll = (companyId: string, year: number, month: number, incomes?: string) =>
  apiClient.post<{
    employeeCount: number;
    socialCreated: number;
    socialSkipped: number;
    iitCreated: number;
    iitSkipped: number;
    recalculated?: number;
    skippedLocked?: number;
  }>('/scrm/payroll/generate', undefined, {
    params: { companyId, year, month, ...(incomes ? { incomes } : {}) },
  });

/** 某月社保明细 */
export const listSocialRecords = (companyId: string, year: number, month: number) =>
  apiClient.get<SocialRecord[]>('/scrm/payroll/social', { params: { companyId, year, month } });

/** 社保汇总 */
export const getSocialSummary = (year: number, month: number, companyId?: string) =>
  apiClient.get<SocialSummary>('/scrm/payroll/social/summary', {
    params: { year, month, ...(companyId ? { companyId } : {}) },
  });

/** 标记社保已申报 */
export const markSocialDeclared = (id: string) =>
  apiClient.post<SocialRecord>(`/scrm/payroll/social/${id}/declared`);

/** 标记社保已缴费 */
export const markSocialPaid = (id: string) =>
  apiClient.post<SocialRecord>(`/scrm/payroll/social/${id}/paid`);

/** 批量变更社保状态（toPaid: true=缴款, false=申报）；逐条状态机校验，返回成功/失败明细 */
export const batchMarkSocialStatus = (ids: string[], toPaid: boolean) =>
  apiClient.post<{
    successCount: number;
    failCount: number;
    failures: Array<{ id: string; message: string }>;
  }>('/scrm/payroll/social/batch-status', ids, { params: { toPaid } });

/** 某月个税明细 */
export const listIitRecords = (companyId: string, year: number, month: number) =>
  apiClient.get<IitRecord[]>('/scrm/payroll/iit', { params: { companyId, year, month } });

/** 个税汇总 */
export const getIitSummary = (year: number, month: number, companyId?: string) =>
  apiClient.get<IitSummary>('/scrm/payroll/iit/summary', {
    params: { year, month, ...(companyId ? { companyId } : {}) },
  });

/** 标记个税已申报 */
export const markIitDeclared = (id: string) =>
  apiClient.post<IitRecord>(`/scrm/payroll/iit/${id}/declared`);

/** 标记个税已缴款 */
export const markIitPaid = (id: string) =>
  apiClient.post<IitRecord>(`/scrm/payroll/iit/${id}/paid`);

/** 批量变更个税状态（toPaid: true=缴款, false=申报）；逐条状态机校验，返回成功/失败明细 */
export const batchMarkIitStatus = (ids: string[], toPaid: boolean) =>
  apiClient.post<{
    successCount: number;
    failCount: number;
    failures: Array<{ id: string; message: string }>;
  }>('/scrm/payroll/iit/batch-status', ids, { params: { toPaid } });

// ============================================================
// 资料归档域：客户资料归档
// ============================================================

/** 客户资料 */
export interface CustomerDocument {
  id: string;
  companyId: string;
  companyName?: string;
  docType: string;
  docName: string;
  objectKey: string;
  fileName?: string;
  fileSize?: number;
  contentType?: string;
  issueDate?: string;
  expireDate?: string;
  status: string;
  remark?: string;
  uploadedBy?: string;
  uploadedAt?: string;
}

/** 齐全度清单项 */
export interface ChecklistItem {
  docType: string;
  present: boolean;
  count: number;
  nearestExpireDate?: string;
}

/** 齐全度清单 */
export interface DocumentChecklist {
  companyId: string;
  companyName?: string;
  requiredTotal: number;
  requiredPresent: number;
  completeness: number;
  missingTypes: string[];
  items: ChecklistItem[];
  documents: CustomerDocument[];
}

/** 齐全度总览行 */
export interface DocumentOverviewRow {
  companyId: string;
  companyName?: string;
  requiredTotal: number;
  requiredPresent: number;
  completeness: number;
  complete: boolean;
  missingTypes: string[];
}

/** 齐全度总览 */
export interface DocumentOverview {
  companyTotal: number;
  complete: number;
  incomplete: number;
  rows: DocumentOverviewRow[];
}

/** 分页查询资料 */
export const listDocuments = (params: {
  companyId?: string;
  docType?: string;
  status?: string;
  keyword?: string;
  page?: number;
  size?: number;
}) => apiClient.get<Page<CustomerDocument>>('/scrm/documents', { params });

/** 资料齐全度清单 */
export const getDocumentChecklist = (companyId: string) =>
  apiClient.get<DocumentChecklist>(`/scrm/documents/checklist/${companyId}`);

/** 资料齐全度总览 */
export const getDocumentOverview = () =>
  apiClient.get<DocumentOverview>('/scrm/documents/overview');

/** 资料详情 */
export const getDocument = (id: string) =>
  apiClient.get<CustomerDocument>(`/scrm/documents/${id}`);

/** 生成下载链接（预签名，直连对象存储） */
export const getDocumentDownloadUrl = (id: string) =>
  apiClient.get<{ url: string }>(`/scrm/documents/${id}/download-url`);

/** 上传资料（multipart） */
export const uploadDocument = (
  file: File,
  params: {
    companyId: string;
    docType: string;
    docName?: string;
    issueDate?: string;
    expireDate?: string;
    remark?: string;
  },
) => {
  const form = new FormData();
  form.append('file', file);
  form.append('companyId', params.companyId);
  form.append('docType', params.docType);
  if (params.docName) form.append('docName', params.docName);
  if (params.issueDate) form.append('issueDate', params.issueDate);
  if (params.expireDate) form.append('expireDate', params.expireDate);
  if (params.remark) form.append('remark', params.remark);
  // 覆盖全局 application/json，让浏览器自动带 boundary
  return apiClient.post<CustomerDocument>('/scrm/documents/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  });
};

/** 更新资料元数据 */
export const updateDocument = (
  id: string,
  params: {
    docName?: string;
    docType?: string;
    issueDate?: string;
    expireDate?: string;
    remark?: string;
    status?: string;
  },
) => apiClient.put<CustomerDocument>(`/scrm/documents/${id}`, undefined, { params });

/** 删除资料 */
export const deleteDocument = (id: string) =>
  apiClient.delete<void>(`/scrm/documents/${id}`);

// ============================================================
// 凭证域：会计科目 + 记账凭证 + 试算平衡
// ============================================================

/** 会计科目 */
export interface AccountSubject {
  id: string;
  companyId: string;
  companyName?: string;
  subjectCode: string;
  subjectName: string;
  category: string;
  direction: string;
  parentCode?: string;
  subjectLevel?: number;
  status: string;
  remark?: string;
}

/** 凭证头 */
export interface Voucher {
  id: string;
  companyId: string;
  companyName?: string;
  periodYear: number;
  periodMonth: number;
  voucherWord: string;
  voucherNo: number;
  voucherDate: string;
  voucherLabel?: string;
  totalDebit: number;
  totalCredit: number;
  entryCount: number;
  status: string;
  /** 纸质附件张数（会计手工声明） */
  attachmentCount?: number;
  /** 已挂载的电子单据份数（后端列表回填，无单据为 0） */
  attachmentFileCount?: number;
  remark?: string;
  createdBy?: string;
  postedBy?: string;
  postedAt?: string;
  voidedAt?: string;
}

/** AI 智能凭证建议的分录行 */
export interface AiVoucherSuggestionEntry {
  subjectCode: string;
  subjectName: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: number;
  summary?: string;
}

/** AI 智能凭证：票据识别建议（含票据字段 + 已过滤/已平衡的分录） */
export interface AiVoucherSuggestion {
  valid: boolean;
  billType?: string;
  billNo?: string;
  billDate?: string;
  counterparty?: string;
  amount?: number;
  summary?: string;
  warnings?: string[];
  entries?: AiVoucherSuggestionEntry[];
}

/** 凭证分录 */
export interface VoucherEntry {
  id?: string;
  voucherId?: string;
  lineNo?: number;
  subjectCode: string;
  subjectName?: string;
  summary?: string;
  debitAmount: number;
  creditAmount: number;
}

/** 凭证详情 */
export interface VoucherDetail {
  voucher: Voucher;
  entries: VoucherEntry[];
}

/** 试算平衡行 */
export interface TrialBalanceRow {
  subjectCode: string;
  subjectName: string;
  category: string;
  direction: string;
  openingDebit: number;
  openingCredit: number;
  periodDebit: number;
  periodCredit: number;
  closingDebit: number;
  closingCredit: number;
}

/** 试算平衡表 */
export interface TrialBalance {
  period: number;
  rows: TrialBalanceRow[];
  totalOpeningDebit: number;
  totalOpeningCredit: number;
  totalPeriodDebit: number;
  totalPeriodCredit: number;
  totalClosingDebit: number;
  totalClosingCredit: number;
  balanced: boolean;
}

/** 期间汇总 */
export interface VoucherPeriodSummary {
  total: number;
  draft: number;
  posted: number;
  voided: number;
  postedDebitTotal: number;
}

/** 初始化标准科目表（幂等） */
export const initSubjects = (companyId: string) =>
  apiClient.post<number>('/scrm/vouchers/subjects/init', undefined, { params: { companyId } });

/** 查询科目 */
export const listSubjects = (companyId: string) =>
  apiClient.get<AccountSubject[]>('/scrm/vouchers/subjects', { params: { companyId } });

/** AI 智能凭证：上传票据图片识别 → 凭证草稿建议（科目已按企业科目表过滤、借贷已校验平衡） */
export const aiRecognizeVoucher = (companyId: string, file: File) => {
  const form = new FormData();
  form.append('file', file);
  return apiClient.post<AiVoucherSuggestion>('/scrm/vouchers/ai/recognize-entry', form, {
    params: { companyId },
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

/** 新增科目 */
export const createSubject = (payload: Partial<AccountSubject>) =>
  apiClient.post<AccountSubject>('/scrm/vouchers/subjects', payload);

/** 启用/停用科目 */
export const updateSubjectStatus = (id: string, status: string) =>
  apiClient.put<AccountSubject>(`/scrm/vouchers/subjects/${id}/status`, undefined, {
    params: { status },
  });

/** 新建凭证草稿 */
export const createVoucher = (
  companyId: string,
  payload: {
    periodYear: number;
    periodMonth: number;
    voucherWord?: string;
    voucherDate?: string;
    attachmentCount?: number;
    remark?: string;
    entries: VoucherEntry[];
  },
) => apiClient.post<VoucherDetail>('/scrm/vouchers', payload, { params: { companyId } });

/** 修改凭证草稿 */
export const updateVoucher = (
  id: string,
  payload: {
    periodYear: number;
    periodMonth: number;
    voucherWord?: string;
    voucherDate?: string;
    attachmentCount?: number;
    remark?: string;
    entries: VoucherEntry[];
  },
) => apiClient.put<VoucherDetail>(`/scrm/vouchers/${id}`, payload);

/** 分页查询凭证 */
export const listVouchers = (params: {
  companyId: string;
  periodYear?: number;
  periodMonth?: number;
  status?: string;
  keyword?: string;
  page?: number;
  size?: number;
}) => apiClient.get<Page<Voucher>>('/scrm/vouchers', { params });

/** 凭证详情 */
export const getVoucher = (id: string) =>
  apiClient.get<VoucherDetail>(`/scrm/vouchers/${id}`);

/** 记账 */
export const postVoucher = (id: string) =>
  apiClient.post<VoucherDetail>(`/scrm/vouchers/${id}/post`);

/** 作废凭证 */
export const voidVoucher = (id: string, reason?: string) =>
  apiClient.post<VoucherDetail>(`/scrm/vouchers/${id}/void`, undefined, {
    params: reason ? { reason } : {},
  });

/** 删除凭证（仅草稿） */
export const deleteVoucher = (id: string) =>
  apiClient.delete<void>(`/scrm/vouchers/${id}`);

/** 批量操作单条失败明细 */
export interface BatchFailure {
  id: string;
  label?: string | null;
  message?: string | null;
}

/** 批量记账 / 批量作废的统一返回 */
export interface BatchVoucherResult {
  successCount: number;
  failCount: number;
  failures: BatchFailure[];
}

/** 批量记账（草稿 → 已记账；逐条校验，单张失败不影响其余） */
export const batchPostVouchers = (ids: string[]) =>
  apiClient.post<BatchVoucherResult>('/scrm/vouchers/batch-post', ids);

/** 批量作废（保留痕迹，退出余额） */
export const batchVoidVouchers = (ids: string[], reason?: string) =>
  apiClient.post<BatchVoucherResult>('/scrm/vouchers/batch-void', ids, {
    params: reason ? { reason } : {},
  });

// ============================================================
// 凭证原始单据（电子附件）
// ============================================================

/** 凭证原始单据（发票 / 银行回单 / 合同 / 工资表） */
export interface VoucherAttachment {
  id: string;
  voucherId: string;
  companyId: string;
  objectKey: string;
  fileName?: string;
  fileSize?: number;
  contentType?: string;
  docType: string;
  remark?: string;
  uploadedBy?: string;
  uploadedAt?: string;
}

/** 单据类型 → 中文与颜色（前端展示用） */
export const VOUCHER_ATTACHMENT_DOC_TYPES: Array<{ value: string; label: string }> = [
  { value: 'INVOICE', label: '发票' },
  { value: 'BANK_RECEIPT', label: '银行回单' },
  { value: 'CONTRACT', label: '合同' },
  { value: 'PAYROLL', label: '工资表' },
  { value: 'RECEIPT', label: '收据' },
  { value: 'OTHER', label: '其他' },
];

/** 查某张凭证的全部原始单据 */
export const listVoucherAttachments = (voucherId: string) =>
  apiClient.get<VoucherAttachment[]>(`/scrm/vouchers/${voucherId}/attachments`);

/** 给凭证挂一份原始单据（文件本体进对象存储，库里只存元数据） */
export const uploadVoucherAttachment = (
  voucherId: string,
  file: File,
  params: { docType?: string; remark?: string } = {},
) => {
  const form = new FormData();
  form.append('file', file);
  if (params.docType) form.append('docType', params.docType);
  if (params.remark) form.append('remark', params.remark);
  // 覆盖全局 application/json，让浏览器自动带 boundary
  return apiClient.post<VoucherAttachment>(`/scrm/vouchers/${voucherId}/attachments`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  });
};

/** 取单据下载预签名 URL（直连对象存储，不经应用进程中转） */
export const getVoucherAttachmentDownloadUrl = (attachmentId: string) =>
  apiClient.get<{ url: string }>(`/scrm/vouchers/attachments/${attachmentId}/download-url`);

/** 删除一份原始单据（删记录 + 删存储对象） */
export const deleteVoucherAttachment = (attachmentId: string) =>
  apiClient.delete<void>(`/scrm/vouchers/attachments/${attachmentId}`);

/** 试算平衡表 */
export const getTrialBalance = (companyId: string, periodYear: number, periodMonth: number) =>
  apiClient.get<TrialBalance>('/scrm/vouchers/trial-balance', {
    params: { companyId, periodYear, periodMonth },
  });

/** 期间汇总 */
export const getVoucherPeriodSummary = (
  companyId: string,
  periodYear: number,
  periodMonth: number,
) =>
  apiClient.get<VoucherPeriodSummary>('/scrm/vouchers/period-summary', {
    params: { companyId, periodYear, periodMonth },
  });

// ============================================================
// 期初余额（接户结转数）
// ============================================================

/** 期初余额行（科目维度） */
export interface OpeningBalanceItem {
  subjectCode: string;
  subjectName: string;
  category: string;
  direction: string;
  /** 期初借方余额 */
  debitAmount: number;
  /** 期初贷方余额 */
  creditAmount: number;
  /** 该科目是否已录入非零余额 */
  configured: boolean;
  /** 科目是否仍启用（停用但已有余额的科目要展示出来，否则钱"消失"了却还在参与试算平衡） */
  active: boolean;
  openingPeriod?: number | null;
  remark?: string | null;
}

/** 期初余额录入视图 */
export interface OpeningBalanceView {
  companyId: string;
  /** 账套启用期间（形如 202601），未设置时为 null */
  openingPeriod?: number | null;
  items: OpeningBalanceItem[];
  totalDebit: number;
  totalCredit: number;
  balanced: boolean;
  configured: boolean;
}

/**
 * 查某户期初余额录入视图。
 *
 * 中途接手客户时，上家账套余额就是本系统的起点；没有它，试算平衡期初与
 * 资产负债表年初余额永远是 0，账接不上、年报不能用。
 */
export const getOpeningBalance = (companyId: string) =>
  apiClient.get<OpeningBalanceView>('/scrm/vouchers/opening-balance', {
    params: { companyId },
  });

/**
 * 重设某户期初余额（整户替换）。
 *
 * 后端强制借贷平衡，并拒绝「启用期之前已有已记账凭证」的户——否则期初与历史
 * 会重复计算。保存后试算平衡/总账/明细账/资产负债表/结转损益自动带上期初。
 */
export const saveOpeningBalance = (
  companyId: string,
  openingPeriod: number,
  items: Array<{ subjectCode: string; debitAmount: number; creditAmount: number; remark?: string }>,
) =>
  apiClient.put<OpeningBalanceView>('/scrm/vouchers/opening-balance', items, {
    params: { companyId, openingPeriod },
  });

/** 清空某户期初余额（回到「全部按本系统凭证累计」的状态） */
export const clearOpeningBalance = (companyId: string) =>
  apiClient.delete<{ deleted: number }>('/scrm/vouchers/opening-balance', {
    params: { companyId },
  });

// ============================================================
// 记账域扩展：结账 / 财务报表 / 账簿
// ============================================================

/** 会计期间 */
export interface AccountPeriod {
  id: string;
  companyId: string;
  periodYear: number;
  periodMonth: number;
  status: string;
  closingVoucherId?: string;
  closedBy?: string;
  closedAt?: string;
  reopenedAt?: string;
  remark?: string;
}

/** 资产负债表行 */
export interface BalanceSheetRow {
  name: string;
  closing: number;
  opening: number;
}

/** 资产负债表 */
export interface BalanceSheet {
  period: number;
  assets: BalanceSheetRow[];
  liabilities: BalanceSheetRow[];
  equities: BalanceSheetRow[];
  totalAssetClosing: number;
  totalAssetOpening: number;
  totalLiabilityClosing: number;
  totalLiabilityOpening: number;
  totalEquityClosing: number;
  totalEquityOpening: number;
  totalLiabilityAndEquityClosing: number;
  totalLiabilityAndEquityOpening: number;
  balanced: boolean;
}

/** 利润表行 */
export interface IncomeStatementRow {
  name: string;
  current: number;
  ytd: number;
}

/** 利润表 */
export interface IncomeStatement {
  period: number;
  rows: IncomeStatementRow[];
  revenueCurrent: number;
  revenueYtd: number;
  netProfitCurrent: number;
  netProfitYtd: number;
}

/** 总账行 */
export interface GeneralLedgerRow {
  subjectCode: string;
  subjectName: string;
  category?: string;
  openingDebit: number;
  openingCredit: number;
  periodDebit: number;
  periodCredit: number;
  closingDebit: number;
  closingCredit: number;
}

/** 总账 */
export interface GeneralLedger {
  period: number;
  rows: GeneralLedgerRow[];
  totalOpeningDebit: number;
  totalOpeningCredit: number;
  totalPeriodDebit: number;
  totalPeriodCredit: number;
  totalClosingDebit: number;
  totalClosingCredit: number;
  balanced: boolean;
}

/** 明细账行 */
export interface DetailLedgerItem {
  voucherDate: string;
  voucherLabel: string;
  summary?: string;
  voucherRemark?: string;
  subjectCode: string;
  subjectName?: string;
  debitAmount: number;
  creditAmount: number;
  balance: number;
  balanceDirection: string;
}

/** 明细账 */
export interface DetailLedger {
  period: number;
  subjectCode: string;
  openingBalance: number;
  openingDirection: string;
  items: DetailLedgerItem[];
  periodDebit: number;
  periodCredit: number;
  closingBalance: number;
  closingDirection: string;
}

/** 序时账行 */
export interface JournalItem {
  voucherDate: string;
  voucherLabel: string;
  summary?: string;
  subjectCode: string;
  subjectName?: string;
  debitAmount: number;
  creditAmount: number;
}

/** 序时账 */
export interface Journal {
  period: number;
  items: JournalItem[];
  totalDebit: number;
  totalCredit: number;
  balanced: boolean;
}

/**
 * 结账（结转损益 + 封账）。
 *
 * 未录入期初余额的企业必须显式传 `confirmNoOpening=true` 表示「本户从零起账」，
 * 否则后端拒绝结账——结转损益按累计余额算利润，缺了期初会把利润算错，
 * 而结账一旦封盘就锁死。
 */
export const closePeriod = (
  companyId: string,
  periodYear: number,
  periodMonth: number,
  confirmNoOpening = false,
) =>
  apiClient.post<{
    period: string;
    status: string;
    profit: number;
    closingVoucherId?: string;
    closingVoucherLabel?: string;
    entryCount?: number;
    message: string;
  }>('/scrm/vouchers/close-period', undefined, {
    params: { companyId, periodYear, periodMonth, confirmNoOpening },
  });

/** 反结账 */
export const reopenPeriod = (companyId: string, periodYear: number, periodMonth: number) =>
  apiClient.post<{ period: string; status: string; message: string }>(
    '/scrm/vouchers/reopen-period',
    undefined,
    { params: { companyId, periodYear, periodMonth } },
  );

/** 期间状态清单 */
export const listAccountPeriods = (companyId: string) =>
  apiClient.get<AccountPeriod[]>('/scrm/vouchers/periods', { params: { companyId } });

/** 资产负债表 */
export const getBalanceSheet = (companyId: string, periodYear: number, periodMonth: number) =>
  apiClient.get<BalanceSheet>('/scrm/vouchers/balance-sheet', {
    params: { companyId, periodYear, periodMonth },
  });

/** 利润表 */
export const getIncomeStatement = (companyId: string, periodYear: number, periodMonth: number) =>
  apiClient.get<IncomeStatement>('/scrm/vouchers/income-statement', {
    params: { companyId, periodYear, periodMonth },
  });

/** 总账 */
export const getGeneralLedger = (companyId: string, periodYear: number, periodMonth: number) =>
  apiClient.get<GeneralLedger>('/scrm/vouchers/general-ledger', {
    params: { companyId, periodYear, periodMonth },
  });

/** 明细账 */
export const getDetailLedger = (
  companyId: string,
  subjectCode: string,
  periodYear: number,
  periodMonth: number,
) =>
  apiClient.get<DetailLedger>('/scrm/vouchers/detail-ledger', {
    params: { companyId, subjectCode, periodYear, periodMonth },
  });

/** 序时账 */
export const getJournal = (companyId: string, periodYear: number, periodMonth: number) =>
  apiClient.get<Journal>('/scrm/vouchers/journal', {
    params: { companyId, periodYear, periodMonth },
  });
