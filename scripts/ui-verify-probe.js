/*
 * 全量 UI 验收走查脚本
 *
 * 用途：逐页打开 48 个菜单路由，收集失败请求与 console 错误，
 *       作为浏览器端可复跑的冒烟走查。
 *
 * 用法：在登录后的浏览器会话中注入执行（CDP evaluate），
 *       或由 scripts/ui-verify-run.sh 驱动。
 */

/** 48 个菜单路由（来源：frontend/src/components/Layout.tsx） */
const ROUTES = [
  { path: '/', label: '财税概览', batch: 1 },
  { path: '/workbench', label: '会计工作台', batch: 1 },
  { path: '/vouchers', label: '记账凭证', batch: 1 },
  { path: '/accounting-reports', label: '账务报表', batch: 1 },
  { path: '/companies', label: '公司主体', batch: 1 },
  { path: '/fixed-assets', label: '固定资产', batch: 1 },
  { path: '/customers', label: '客户列表', batch: 2 },
  { path: '/customer-groups', label: '客户分组', batch: 2 },
  { path: '/customer-tags', label: '标签管理', batch: 2 },
  { path: '/follow-ups', label: '客户跟进', batch: 2 },
  { path: '/customer-care', label: '客户关怀', batch: 2 },
  { path: '/compliance', label: '合规到期', batch: 2 },
  { path: '/payroll', label: '社保个税', batch: 2 },
  { path: '/documents', label: '资料归档', batch: 2 },
  { path: '/tax-calendar', label: '报税日历', batch: 3 },
  { path: '/client-portal', label: '客户查账', batch: 3 },
  { path: '/tax-risk-profile', label: '税负风险', batch: 3 },
  { path: '/tax-holidays', label: '假期维护', batch: 3 },
  { path: '/renewals', label: '服务续费', batch: 3 },
  { path: '/policy-push', label: '政策推送', batch: 3 },
  { path: '/inspections', label: '巡检与公告', batch: 3 },
  { path: '/finance-readiness', label: '就绪看板', batch: 3 },
  { path: '/license-ocr', label: '证照识别', batch: 3 },
  { path: '/contracts', label: '合同管理', batch: 4 },
  { path: '/invoices', label: '发票管理', batch: 4 },
  { path: '/approvals', label: '审批中心', batch: 4 },
  { path: '/work-orders', label: '工单管理', batch: 4 },
  { path: '/notifications', label: '通知中心', batch: 4 },
  { path: '/webhooks', label: 'Webhook', batch: 4 },
  { path: '/conversations', label: '会话列表', batch: 5 },
  { path: '/knowledge-base', label: '知识库', batch: 5 },
  { path: '/reports', label: '报表中心', batch: 5 },
  { path: '/message-template-center', label: '模板中心', batch: 5 },
  { path: '/data-dictionaries', label: '数据字典', batch: 6 },
  { path: '/data-transfers', label: '导入导出', batch: 6 },
  { path: '/task-scheduler', label: '任务调度', batch: 6 },
  { path: '/open-api-apps', label: 'OpenAPI', batch: 6 },
  { path: '/dashboard', label: '数据看板', batch: 6 },
  { path: '/audit-logs', label: '审计日志', batch: 6 },
  { path: '/users', label: '用户管理', batch: 6 },
  { path: '/settings', label: '平台配置', batch: 6 },
];

/**
 * 安装探针：拦截 fetch/XHR 记录非 2xx 的 /scrm/ 请求，并收集 JS 运行时错误。
 *
 * @returns {string} 安装结果标识
 */
function installProbe() {
  window.__probe = { fails: [], errors: [], ready: true };
  if (window.__fetchPatched) {
    return 'already';
  }
  const origFetch = window.fetch;
  window.fetch = async function patchedFetch(...args) {
    const url = typeof args[0] === 'string' ? args[0] : args[0]?.url;
    const res = await origFetch.apply(this, args);
    if (!res.ok && String(url || '').includes('/scrm/')) {
      window.__probe.fails.push({ url: String(url).split('?')[0], status: res.status });
    }
    return res;
  };
  window.addEventListener('error', (e) => {
    window.__probe.errors.push({ msg: String(e.message).slice(0, 120) });
  });
  window.__fetchPatched = true;
  return 'installed';
}

/**
 * 读取并清空探针结果。
 *
 * @returns {{fails: Array, errors: Array}} 本次采集到的失败
 */
function drainProbe() {
  const p = window.__probe || { fails: [], errors: [] };
  const out = { fails: p.fails.slice(), errors: p.errors.slice() };
  p.fails.length = 0;
  p.errors.length = 0;
  return out;
}

/**
 * 页面结构快照：标题、卡片、统计、表格行数、按钮、空态、错误提示、溢出检测。
 *
 * @returns {object} 结构度量
 */
function snapshot() {
  const overflow = [];
  document.querySelectorAll('.ant-card-body, .ant-table-body, .ant-tabs-tabpane').forEach((el) => {
    if (el.scrollWidth > el.clientWidth + 2 && el.clientWidth > 0) {
      overflow.push({
        cls: String(el.className).slice(0, 40),
        sw: el.scrollWidth,
        cw: el.clientWidth,
      });
    }
  });
  return {
    url: location.href,
    title: document.querySelector('.ant-page-header-heading-title')?.innerText?.trim()
      || document.querySelector('h1, h2')?.innerText?.trim() || '',
    cards: [...document.querySelectorAll('.ant-card-head-title')].map((e) => e.innerText.trim()),
    stats: [...document.querySelectorAll('.ant-statistic')].map((e) => e.innerText.replace(/\n/g, '|')),
    tables: [...document.querySelectorAll('.ant-table')].map((t) => t.querySelectorAll('tbody tr').length),
    buttons: [...document.querySelectorAll('button')].map((b) => b.textContent.trim()).filter(Boolean),
    empty: [...document.querySelectorAll('.ant-empty-description')].map((e) => e.innerText.trim()),
    alerts: [...document.querySelectorAll('.ant-alert')].map((e) => e.innerText.replace(/\n/g, '|').slice(0, 120)),
    overflow,
    hasLoadFailText: document.body.innerText.includes('加载失败'),
  };
}

if (typeof module !== 'undefined') {
  module.exports = { ROUTES, installProbe, drainProbe, snapshot };
}
