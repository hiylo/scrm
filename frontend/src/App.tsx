/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : App.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, Spin, App as AntApp, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { ThemeProvider, useTheme } from './contexts/ThemeContext';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import { ErrorBoundary } from './components/ErrorBoundary';
import Login from './pages/Login';

// 懒加载业务页面
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Customers = lazy(() => import('./pages/Customers'));
const CustomerDetail = lazy(() => import('./pages/CustomerDetail'));
const CustomerGroups = lazy(() => import('./pages/CustomerGroups'));
const CustomerTags = lazy(() => import('./pages/CustomerTags'));
const Conversations = lazy(() => import('./pages/Conversations'));
const Campaigns = lazy(() => import('./pages/Campaigns'));
const MessageTemplates = lazy(() => import('./pages/MessageTemplates'));
const RiskRules = lazy(() => import('./pages/RiskRules'));
const RiskSignals = lazy(() => import('./pages/RiskSignals'));
const AuditLogs = lazy(() => import('./pages/AuditLogs'));
const Accounts = lazy(() => import('./pages/Accounts'));
const AccountHealth = lazy(() => import('./pages/AccountHealth'));
const Personas = lazy(() => import('./pages/Personas'));
const Settings = lazy(() => import('./pages/Settings'));
const Wework = lazy(() => import('./pages/Wework'));
const Groups = lazy(() => import('./pages/Groups'));
const Users = lazy(() => import('./pages/Users'));

/** 页面加载占位符 */
const PageLoading = () => (
  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
    <Spin size="large" />
  </div>
);

/** 路由配置数组 */
const appRoutes: Array<{ path?: string; index?: boolean; element: React.ReactElement }> = [
  { index: true, element: <Dashboard /> },
  { path: 'customers', element: <Customers /> },
  { path: 'customers/:id', element: <CustomerDetail /> },
  { path: 'customer-groups', element: <CustomerGroups /> },
  { path: 'customer-tags', element: <CustomerTags /> },
  { path: 'conversations', element: <Conversations /> },
  { path: 'campaigns', element: <Campaigns /> },
  { path: 'message-templates', element: <MessageTemplates /> },
  { path: 'risk-rules', element: <RiskRules /> },
  { path: 'risk-signals', element: <RiskSignals /> },
  { path: 'audit-logs', element: <AuditLogs /> },
  { path: 'accounts', element: <Accounts /> },
  { path: 'account-health', element: <AccountHealth /> },
  { path: 'personas', element: <Personas /> },
  { path: 'settings', element: <Settings /> },
  { path: 'wework', element: <Wework /> },
  { path: 'wework-departments', element: <Wework singleView defaultTab="departments" /> },
  { path: 'groups', element: <Groups /> },
  { path: 'users', element: <Users /> },
  { path: 'dashboard', element: <Dashboard /> },
];

/** 主路由组件 */
function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        {appRoutes.map(r => (
          <Route key={r.path || 'index'} path={r.path} index={r.index} element={r.element} />
        ))}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

/** 主题化应用 */
function ThemedApp() {
  const { theme: currentTheme } = useTheme();
  const isDark = currentTheme === 'dark';

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: isDark ? {
          colorPrimary: '#818cf8',
          colorBgContainer: '#1a2332',
          colorBgElevated: '#1e2a3a',
          colorBgLayout: '#0c1222',
          colorBorder: '#2a3548',
          colorBorderSecondary: '#1e2a3a',
          colorText: '#e2e8f0',
          colorTextSecondary: '#94a3b8',
          borderRadius: 8,
        } : {
          colorPrimary: '#6366f1',
          borderRadius: 8,
        },
      }}
    >
      <AntApp>
        <BrowserRouter basename={import.meta.env.BASE_URL}>
          <ErrorBoundary>
            <Suspense fallback={<PageLoading />}>
              <AppRoutes />
            </Suspense>
          </ErrorBoundary>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  );
}

/** 应用根组件 */
export default function App() {
  return (
    <ThemeProvider>
      <ThemedApp />
    </ThemeProvider>
  );
}
