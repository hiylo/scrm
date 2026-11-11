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
 * 安装探针：拦截 fetch 与 XHR 记录失败请求、识别「接口返回 HTML」、收集 JS 错误。
 *
 * 注意：本项目走 axios（底层是 XHR），只拦 fetch 会漏掉全部业务请求——
 * 曾因此把「接口 400」误判为「无数据」，所以两路都必须挂。
 *
 * @returns {string} 安装结果标识
 */
function installProbe() {
  window.__probe = { fails: [], errors: [], htmlResponses: [], ready: true };
  if (window.__fetchPatched) {
    return 'already';
  }
  const P = window.__probe;
  const isScrm = (u) => String(u || '').includes('/scrm/');
  const note = (url, status, contentType, bodyHead) => {
    if (!isScrm(url)) return;
    const ct = String(contentType || '');
    // 接口路径却返回 HTML：说明路由不存在，落到了 SPA 兜底页
    if (ct.includes('text/html') || String(bodyHead || '').trimStart().startsWith('<!DOCTYPE')) {
      P.htmlResponses.push(String(url).split('?')[0]);
      return;
    }
    if (status && (status < 200 || status >= 300)) {
      P.fails.push({ url: String(url).split('?')[0], status });
    }
  };

  const origFetch = window.fetch;
  window.fetch = async function patchedFetch(...args) {
    const url = typeof args[0] === 'string' ? args[0] : args[0]?.url;
    const res = await origFetch.apply(this, args);
    let head = '';
    try {
      head = await res.clone().text();
    } catch {
      head = '';
    }
    note(url, res.status, res.headers.get('content-type'), head.slice(0, 40));
    return res;
  };

  const origOpen = XMLHttpRequest.prototype.open;
  const origSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function patchedOpen(method, url, ...rest) {
    this.__probeUrl = url;
    return origOpen.call(this, method, url, ...rest);
  };
  XMLHttpRequest.prototype.send = function patchedSend(...args) {
    this.addEventListener('loadend', () => {
      let ct = '';
      try {
        ct = this.getResponseHeader('content-type') || '';
      } catch {
        ct = '';
      }
      const body = typeof this.responseText === 'string' ? this.responseText : '';
      note(this.__probeUrl, this.status, ct, body.slice(0, 40));
    });
    return origSend.apply(this, args);
  };

  window.addEventListener('error', (e) => {
    P.errors.push({ msg: String(e.message).slice(0, 120) });
  });
  window.__fetchPatched = true;
  return 'installed';
}

/**
 * 读取并清空探针结果。
 *
 * @returns {{fails: Array, errors: Array, htmlResponses: Array}} 本次采集到的失败
 */
function drainProbe() {
  const p = window.__probe || { fails: [], errors: [], htmlResponses: [] };
  const out = {
    fails: (p.fails || []).slice(),
    errors: (p.errors || []).slice(),
    htmlResponses: (p.htmlResponses || []).slice(),
  };
  p.fails.length = 0;
  p.errors.length = 0;
  p.htmlResponses.length = 0;
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

/**
 * 可直接判定的断言：把「看走眼」的常见来源变成机器可查的结论。
 *
 * 背景：本项目全量验收中，误报缺陷的根因几乎只有两类——
 *   ① 读取时机太早（React 未重渲染就去读，把「还没渲染」当成「没有数据」）；
 *   ② 拿错接口/路径（拿到 SPA 首页 HTML 或 400，误以为接口正常/无数据）。
 * 这两类都能在脚本层自动化，避免下一次重复踩。
 *
 * @param {object} snap   snapshot() 的结果
 * @param {object} probe  drainProbe() 的结果，含 fails / errors
 * @returns {{ok: boolean, issues: Array<{level: string, code: string, message: string}>}}
 */
function assertPage(snap, probe) {
  const issues = [];
  const p = probe || { fails: [], errors: [] };

  // ① 接口层：任何非 2xx 的 /scrm/ 请求都算问题，不能当作「无数据」
  (p.fails || []).forEach((f) => {
    issues.push({
      level: 'P1',
      code: 'API_FAIL',
      message: `接口失败 ${f.status} ${f.url}（不要据此判定页面为空）`,
    });
  });

  // ② 接口层：请求返回 HTML 说明路径不存在，落到 SPA 兜底
  (p.htmlResponses || []).forEach((u) => {
    issues.push({
      level: 'P1',
      code: 'API_HTML',
      message: `接口返回 HTML，疑似路径不存在：${u}`,
    });
  });

  // ③ 运行层：JS 未捕获错误
  (p.errors || []).forEach((e) => {
    issues.push({ level: 'P2', code: 'JS_ERROR', message: `JS 错误：${e.msg}` });
  });

  // ④ 页面层：明确渲染了「加载失败」
  if (snap && snap.hasLoadFailText) {
    issues.push({ level: 'P1', code: 'LOAD_FAIL_TEXT', message: '页面出现「加载失败」文案' });
  }

  // ⑤ 判定纪律：既没有数据行也没有空态文案，说明可能还在加载，不能判定为「空」
  if (snap && Array.isArray(snap.tables) && snap.tables.length > 0) {
    const noRows = snap.tables.every((n) => n <= 1);
    const noEmptyCopy = !(snap.empty && snap.empty.length > 0);
    const noTable = snap.tables.length === 0;
    if (noRows && noEmptyCopy && !noTable) {
      issues.push({
        level: 'P2',
        code: 'AMBIGUOUS_LOADING',
        message: '表格无数据行且无空态文案：可能仍在渲染，需等待后重读，勿直接判为缺陷',
      });
    }
  }

  return { ok: issues.length === 0, issues };
}

if (typeof module !== 'undefined') {
  module.exports = { ROUTES, installProbe, drainProbe, snapshot, assertPage };
}
