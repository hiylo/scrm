/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : Login.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Form, Input, Button, App, Checkbox } from 'antd';
import { UserOutlined, LockOutlined, ArrowRightOutlined } from '@ant-design/icons';
import { login, saveAuthData } from '../api/auth';

/** localStorage 中保存被记住用户名的键 */
const REMEMBERED_USER_KEY = 'rememberedUser';

/** 品牌区特性点图标（统一线性 SVG，随当前色渲染） */
function FeatureGlyph({ icon }: { icon: string }) {
  const common = {
    width: 20,
    height: 20,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
  };
  switch (icon) {
    case 'scan':
      return (
        <svg {...common}>
          <path d="M3 7V5a2 2 0 0 1 2-2h2" />
          <path d="M17 3h2a2 2 0 0 1 2 2v2" />
          <path d="M21 17v2a2 2 0 0 1-2 2h-2" />
          <path d="M7 21H5a2 2 0 0 1-2-2v-2" />
          <rect x="7" y="7" width="10" height="10" rx="1.6" />
          <path d="M12 10.5v3M10.5 12h3" />
        </svg>
      );
    case 'calendar':
      return (
        <svg {...common}>
          <rect x="3" y="5" width="18" height="16" rx="2.5" />
          <path d="M3 10h18M8 3v4M16 3v4" />
          <circle cx="12" cy="15" r="1" fill="currentColor" stroke="none" />
        </svg>
      );
    case 'refresh':
      return (
        <svg {...common}>
          <path d="M21 12a9 9 0 1 1-2.64-6.36" />
          <path d="M21 3v6h-6" />
        </svg>
      );
    default:
      return null;
  }
}

/**
 * 财税特性点（登录页品牌区展示）
 */
const FEATURES = [
  { icon: 'scan', title: '证照识别', desc: '多模态 OCR，一键建档' },
  { icon: 'calendar', title: '报税日历', desc: '征期自动顺延，不再逾期' },
  { icon: 'refresh', title: '续费闭环', desc: '到期提醒，跟进不漏单' },
];

/**
 * 登录页面
 * 通过网关 OAuth 端点认证, 保存 token 后跳转原页面或首页
 *
 * 视觉：双栏品牌布局 —— 左侧深蓝渐变品牌区（产品名 + 标语 + 特性点玻璃卡片），
 * 右侧浅底登录面板。全部使用 CSS 变量，跟随亮色/暗色主题。
 * 品牌区含网格纹理 / 光晕圆环等纯 CSS 装饰；<900px 时切换为纵向布局：
 * 品牌区保留为顶部深蓝渐变横幅（背景装饰可见），下方为登录面板。
 */
export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);

  // 从路由 state 中获取登录前用户试图访问的原始路径 (由 ProtectedRoute 传入)
  const from = (location.state as any)?.from?.pathname;

  // 读取已记住的用户名, 用于回填用户名输入框
  const [rememberedUser] = useState(() => localStorage.getItem(REMEMBERED_USER_KEY) || '');

  const handleLogin = async (values: { username: string; password: string; remember?: boolean }) => {
    setLoading(true);
    try {
      // 记住我: 勾选时保存用户名, 取消勾选时清除
      if (values.remember) {
        localStorage.setItem(REMEMBERED_USER_KEY, values.username);
      } else {
        localStorage.removeItem(REMEMBERED_USER_KEY);
      }
      const response = await login(values.username, values.password);
      saveAuthData(response);
      message.success(`欢迎回来, ${response.displayName}!`);
      // 登录成功后跳转回原始页面, 无原始页面则跳转首页
      navigate(from || '/');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '登录失败';
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-shell">
      {/* ===== 左侧品牌区（<900px 隐藏） ===== */}
      <div className="login-brand">
        {/* 纯 CSS 装饰层: 网格纹理 + 光晕圆环 */}
        <div className="login-brand-decor" aria-hidden="true">
          <div className="decor-grid" />
          <div className="decor-ring decor-ring-1" />
          <div className="decor-ring decor-ring-2" />
        </div>

        <div className="login-brand-inner">
          <div className="login-brand-head">
            <span className="login-logo-mark">财</span>
            <div className="login-brand-title">
              <div className="login-brand-name">财税 SCRM</div>
              <div className="login-brand-sub">自动化运营引擎</div>
            </div>
          </div>

          <div className="login-slogan-wrap">
            <div className="login-eyebrow">FINANCIAL · DIGITAL · PLATFORM</div>
            <h1 className="login-slogan">代账财税作业平台</h1>
            <p className="login-slogan-sub">一份执照到一本账，全链路数字化代账</p>
          </div>

          <div className="login-features">
            {FEATURES.map(f => (
              <div className="login-feature" key={f.title}>
                <span className="login-feature-icon">
                  <FeatureGlyph icon={f.icon} />
                </span>
                <div>
                  <div className="login-feature-title">{f.title}</div>
                  <div className="login-feature-desc">{f.desc}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="login-copyright">© 2026 财税数字化系统 · 内部使用</div>
      </div>

      {/* ===== 右侧登录面板 ===== */}
      <div className="login-panel">
        <div className="login-card">
          {/* 移动端品牌（品牌区隐藏时显示） */}
          <div className="login-mobile-brand">
            <span className="login-logo-mark">财</span>
            <div className="login-brand-title">
              <div className="login-brand-name">财税 SCRM</div>
              <div className="login-brand-sub">自动化运营引擎</div>
            </div>
          </div>

          <div className="login-card-title">欢迎登录</div>
          <div className="login-card-sub">请使用管理员分配的账号登录工作台</div>

          <Form
            name="login"
            onFinish={handleLogin}
            autoComplete="off"
            size="large"
            // 初始值: 回填已记住的用户名并勾选"记住我"
            initialValues={{ username: rememberedUser, remember: !!rememberedUser }}
          >
            <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input prefix={<UserOutlined />} placeholder="用户名" size="large" />
            </Form.Item>
            <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
            </Form.Item>
            <div className="login-option-row">
              <Form.Item name="remember" valuePropName="checked" className="login-remember no-mb">
                <Checkbox>记住我</Checkbox>
              </Form.Item>
            </div>
            <Form.Item className="login-submit">
              <Button type="primary" htmlType="submit" loading={loading} block size="large">
                登 录 <ArrowRightOutlined />
              </Button>
            </Form.Item>
          </Form>

          <div className="login-foot">遇到问题？请联系系统管理员</div>
        </div>
      </div>

      {/* ===== 登录页局部样式 ===== */}
      <style>{`
        .login-shell {
          min-height: 100vh;
          display: flex;
          background: var(--color-bg-layout);
        }
        /* ---- 品牌区 ---- */
        .login-brand {
          position: relative;
          overflow: hidden;
          flex: 1 1 55%;
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          padding: 56px 64px 32px;
          color: #fff;
          background:
            radial-gradient(1100px 600px at 12% -10%, rgba(129,140,248,.55) 0%, transparent 55%),
            radial-gradient(900px 500px at 100% 110%, rgba(56,189,248,.30) 0%, transparent 50%),
            radial-gradient(740px 560px at 0% 114%, rgba(99,102,241,.35) 0%, transparent 62%),
            linear-gradient(160deg, #1e2a78 0%, #302e8a 48%, #4635c6 100%);
        }
        /* 装饰层（纯 CSS, 无额外 DOM） */
        .login-brand-decor { position: absolute; inset: 0; pointer-events: none; }
        .decor-grid {
          position: absolute; inset: 0;
          background-image:
            linear-gradient(rgba(255,255,255,.07) 1px, transparent 1px),
            linear-gradient(90deg, rgba(255,255,255,.07) 1px, transparent 1px);
          background-size: 44px 44px;
          mask-image: linear-gradient(180deg, rgba(0,0,0,.8), transparent 82%);
          -webkit-mask-image: linear-gradient(180deg, rgba(0,0,0,.8), transparent 82%);
        }
        .decor-ring {
          position: absolute;
          border: 1px solid rgba(255,255,255,.20);
          border-radius: 50%;
        }
        .decor-ring-1 {
          width: 420px; height: 420px; right: -140px; top: -120px;
          box-shadow: 0 0 90px rgba(129,140,248,.35) inset;
        }
        .decor-ring-2 {
          width: 260px; height: 260px; right: -70px; bottom: 90px;
          box-shadow: 0 0 70px rgba(56,189,248,.28) inset;
        }
        /* 底部大背景环（加深品牌区背景的呼吸感） */
        .login-brand-decor::after {
          content: '';
          position: absolute;
          width: 780px; height: 780px;
          left: 50%; bottom: -430px;
          transform: translateX(-50%);
          border: 1px solid rgba(255,255,255,.12);
          border-radius: 50%;
          box-shadow: 0 0 0 130px rgba(255,255,255,.022), 0 0 140px rgba(129,140,248,.22) inset;
        }
        .login-brand-inner { position: relative; max-width: 500px; }
        .login-brand-head { display: flex; align-items: center; gap: 14px; }
        .login-logo-mark {
          display: inline-flex; align-items: center; justify-content: center;
          width: 48px; height: 48px; border-radius: 14px;
          background: rgba(255,255,255,.14);
          backdrop-filter: blur(4px);
          border: 1px solid rgba(255,255,255,.25);
          font-size: 24px; font-weight: 700; color: #fff;
          box-shadow: 0 8px 24px rgba(99,102,241,.45);
          flex-shrink: 0;
        }
        .login-brand-name { font-size: 20px; font-weight: 700; letter-spacing: .5px; }
        .login-brand-sub { font-size: 13px; opacity: .75; margin-top: 2px; }
        .login-slogan-wrap { margin: 68px 0 52px; }
        .login-eyebrow {
          font-size: 11px; letter-spacing: 3px; text-transform: uppercase;
          opacity: .55; margin-bottom: 18px;
        }
        .login-slogan {
          font-size: 46px; line-height: 1.18; font-weight: 800; margin: 0;
          letter-spacing: 2px; text-shadow: 0 4px 30px rgba(30,41,120,.35);
        }
        .login-slogan-sub { font-size: 17px; opacity: .85; margin: 14px 0 0; }
        .login-features { display: flex; flex-direction: column; gap: 14px; }
        .login-feature {
          display: flex; align-items: center; gap: 14px;
          padding: 16px 18px;
          border-radius: 14px;
          background: linear-gradient(135deg, rgba(255,255,255,.13), rgba(255,255,255,.07));
          border: 1px solid rgba(255,255,255,.18);
          backdrop-filter: blur(8px);
          box-shadow: 0 10px 26px rgba(30,41,120,.20), inset 0 1px 0 rgba(255,255,255,.14);
          transition: transform .2s ease, background .2s ease, box-shadow .2s ease;
        }
        .login-feature:hover {
          transform: translateY(-2px) translateX(3px);
          background: linear-gradient(135deg, rgba(255,255,255,.18), rgba(255,255,255,.09));
          box-shadow: 0 14px 32px rgba(30,41,120,.28), inset 0 1px 0 rgba(255,255,255,.18);
        }
        .login-feature-icon {
          display: inline-flex; align-items: center; justify-content: center;
          width: 42px; height: 42px; flex-shrink: 0;
          border-radius: 12px;
          color: #e0e7ff;
          background: linear-gradient(135deg, rgba(129,140,248,.35), rgba(99,102,241,.22));
          border: 1px solid rgba(129,140,248,.45);
          box-shadow: 0 4px 12px rgba(99,102,241,.30);
        }
        .login-feature-title { font-size: 15px; font-weight: 600; }
        .login-feature-desc { font-size: 13px; opacity: .75; margin-top: 2px; }
        .login-copyright { position: relative; font-size: 13px; opacity: .65; }
        /* ---- 登录面板 ---- */
        .login-panel {
          position: relative;
          overflow: hidden;
          flex: 1 1 45%;
          display: flex; align-items: center; justify-content: center;
          padding: 32px 48px;
          background:
            radial-gradient(900px 560px at 88% 6%, rgba(99,102,241,.08) 0%, transparent 55%),
            radial-gradient(700px 520px at 4% 96%, rgba(56,189,248,.06) 0%, transparent 55%),
            var(--color-bg-layout);
        }
        /* 面板水印装饰: 主题色同心圆 + 光晕, 填补右侧视觉空白（纯 CSS） */
        .login-panel::before,
        .login-panel::after {
          content: '';
          position: absolute;
          border-radius: 50%;
          border: 1px solid rgba(99,102,241,.20);
          pointer-events: none;
        }
        .login-panel::before {
          width: 620px; height: 620px;
          right: -190px; bottom: -170px;
          box-shadow: 0 0 0 90px rgba(99,102,241,.05), 0 0 0 200px rgba(99,102,241,.03);
        }
        .login-panel::after {
          width: 300px; height: 300px;
          left: -90px; top: -80px;
          box-shadow: 0 0 0 60px rgba(99,102,241,.045);
        }
        .login-card {
          position: relative;
          z-index: 1;
          width: 100%; max-width: 424px;
          padding: 44px 40px 30px;
          border-radius: 18px;
          background: var(--color-bg-container);
          border: 1px solid var(--color-border);
          box-shadow: 0 24px 64px rgba(30,41,120,.12), 0 4px 16px rgba(30,41,120,.05);
          overflow: hidden;
        }
        .login-card::before {
          content: '';
          position: absolute; top: 0; left: 0; right: 0; height: 4px;
          background: linear-gradient(90deg, #111827, #374151 55%, #6b7280);
        }
        .login-mobile-brand { display: none; align-items: center; gap: 12px; margin-bottom: 28px; }
        .login-mobile-brand .login-logo-mark { width: 44px; height: 44px; font-size: 22px; }
        .login-card-title { font-size: 24px; font-weight: 700; color: var(--color-text); }
        .login-card-sub { font-size: 13px; color: var(--color-text-secondary); margin: 8px 0 28px; }
        .login-option-row { margin-bottom: 8px; }
        .login-remember.no-mb { margin-bottom: 0; }
        .login-submit { margin-top: 8px; }
        .login-submit .ant-btn {
          height: 44px; border-radius: 8px; font-size: 15px; letter-spacing: 2px;
          background: linear-gradient(135deg, #111827 0%, #374151 100%);
          border: none;
          box-shadow: 0 8px 20px rgba(99,102,241,.32);
          transition: transform .15s ease, box-shadow .2s ease, filter .2s ease;
        }
        .login-submit .ant-btn:not(:disabled):hover {
          transform: translateY(-1px);
          filter: brightness(1.05);
          box-shadow: 0 10px 24px rgba(99,102,241,.4);
        }
        .login-submit .ant-btn:active { transform: translateY(0); }
        .login-submit .ant-btn[disabled] {
          background: linear-gradient(135deg, #111827 0%, #374151 100%);
          opacity: .6;
        }
        .login-foot { text-align: center; font-size: 13px; color: var(--color-text-tertiary); margin-top: 8px; }
        /* ---- 输入框辨识度增强: 内阴影 + 加深 border + focus 光晕 ---- */
        .login-shell .ant-input-affix-wrapper {
          border-radius: 10px;
          border-color: var(--color-text-tertiary);
          background: var(--color-bg-container);
          box-shadow: inset 0 1px 2px rgba(0,0,0,.04);
          transition: border-color .2s ease, box-shadow .2s ease;
        }
        .login-shell .ant-input-affix-wrapper:hover {
          border-color: var(--color-primary-light);
        }
        .login-shell .ant-input-affix-wrapper-focused {
          box-shadow: inset 0 1px 2px rgba(0,0,0,.04), 0 0 0 3px var(--color-primary-bg);
          border-color: var(--color-primary);
        }
        /* ---- 响应式（<900px 纵向布局：品牌区改为顶部深蓝横幅, 背景始终可见） ---- */
        @media (max-width: 900px) {
          .login-shell { flex-direction: column; }
          /* 品牌区不再隐藏, 改为顶部深蓝渐变横幅, 保留网格/光晕/大圆环背景装饰 */
          .login-brand {
            display: flex;
            flex: 0 0 auto;
            width: 100%;
            padding: 36px 28px 26px;
            min-height: 330px;
            justify-content: space-between;
          }
          .login-brand-inner { max-width: none; }
          .login-slogan-wrap { margin: 30px 0 18px; }
          /* 特性卡片在窄屏隐藏, 节省纵向空间 */
          .login-features { display: none; }
          /* 装饰环按窄屏收缩, 避免大面积被裁切 */
          .decor-ring-1 { width: 300px; height: 300px; right: -90px; top: -70px; }
          .decor-ring-2 { width: 190px; height: 190px; right: -45px; bottom: 30px; }
          .login-brand-decor::after { width: 480px; height: 480px; bottom: -260px; }
          /* 登录面板置于下方 */
          .login-panel {
            flex: 1 1 auto;
            width: 100%;
            padding: 28px 20px 36px;
          }
          .login-card { max-width: 440px; padding: 36px 28px 26px; }
          /* 品牌区已可见, 无需移动端迷你品牌 */
          .login-mobile-brand { display: none; }
        }
        /* ---- 暗色模式（品牌区配色加深, 亮色时正常） ---- */
        @media (min-width: 901px) {
          [data-theme="dark"] .login-brand {
            background:
              radial-gradient(1100px 600px at 12% -10%, rgba(129,140,248,.30) 0%, transparent 55%),
              radial-gradient(900px 500px at 100% 110%, rgba(56,189,248,.15) 0%, transparent 50%),
              radial-gradient(740px 560px at 0% 114%, rgba(99,102,241,.22) 0%, transparent 62%),
              linear-gradient(160deg, #101a3c 0%, #1e1f4d 52%, #332c86 100%);
          }
          [data-theme="dark"] .login-panel {
            background:
              radial-gradient(900px 560px at 88% 6%, rgba(129,140,248,.10) 0%, transparent 55%),
              radial-gradient(700px 520px at 4% 96%, rgba(56,189,248,.06) 0%, transparent 55%),
              var(--color-bg-layout);
          }
          [data-theme="dark"] .login-card {
            box-shadow: 0 24px 64px rgba(0,0,0,.5), 0 4px 16px rgba(0,0,0,.25);
          }
          [data-theme="dark"] .login-panel::before,
          [data-theme="dark"] .login-panel::after {
            border-color: rgba(129,140,248,.20);
          }
          [data-theme="dark"] .login-panel::before {
            box-shadow: 0 0 0 90px rgba(129,140,248,.05), 0 0 0 200px rgba(129,140,248,.03);
          }
        }
      `}</style>
    </div>
  );
}