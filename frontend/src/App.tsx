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
const AiAssistant = lazy(() => import('./pages/AiAssistant'));
const ChannelCodes = lazy(() => import('./pages/ChannelCodes'));
const WelcomeMessages = lazy(() => import('./pages/WelcomeMessages'));
const AutoTags = lazy(() => import('./pages/AutoTags'));
const MassSends = lazy(() => import('./pages/MassSends'));
const GroupBroadcast = lazy(() => import('./pages/GroupBroadcast'));
const QuickReplies = lazy(() => import('./pages/QuickReplies'));
const Speeches = lazy(() => import('./pages/Speeches'));
const Assets = lazy(() => import('./pages/Assets'));
const PublicSea = lazy(() => import('./pages/PublicSea'));
const Opportunities = lazy(() => import('./pages/Opportunities'));
const Tickets = lazy(() => import('./pages/Tickets'));
const Membership = lazy(() => import('./pages/Membership'));
const Points = lazy(() => import('./pages/Points'));
const Coupons = lazy(() => import('./pages/Coupons'));
const Surveys = lazy(() => import('./pages/Surveys'));
const KnowledgeBase = lazy(() => import('./pages/KnowledgeBase'));
const ContentMarketing = lazy(() => import('./pages/ContentMarketing'));
const QualityInspections = lazy(() => import('./pages/QualityInspections'));
const RfmAnalysis = lazy(() => import('./pages/RfmAnalysis'));
const LtvPrediction = lazy(() => import('./pages/LtvPrediction'));
const Attribution = lazy(() => import('./pages/Attribution'));
const Blacklist = lazy(() => import('./pages/Blacklist'));
const Contracts = lazy(() => import('./pages/Contracts'));
const Orders = lazy(() => import('./pages/Orders'));
const Commissions = lazy(() => import('./pages/Commissions'));
const Invoices = lazy(() => import('./pages/Invoices'));
const LeadScoring = lazy(() => import('./pages/LeadScoring'));
const Notifications = lazy(() => import('./pages/Notifications'));
const Webhooks = lazy(() => import('./pages/Webhooks'));
const DataDictionaries = lazy(() => import('./pages/DataDictionaries'));
const DataTransfers = lazy(() => import('./pages/DataTransfers'));
const CustomerJourneys = lazy(() => import('./pages/CustomerJourneys'));
const JourneyCanvas = lazy(() => import('./pages/JourneyCanvas'));
const Approvals = lazy(() => import('./pages/Approvals'));
const Reports = lazy(() => import('./pages/Reports'));
const MarketingCalendar = lazy(() => import('./pages/MarketingCalendar'));
const CustomerCare = lazy(() => import('./pages/CustomerCare'));
const Visits = lazy(() => import('./pages/Visits'));
const FollowUps = lazy(() => import('./pages/FollowUps'));
const WorkOrders = lazy(() => import('./pages/WorkOrders'));
const InteractionCalendars = lazy(() => import('./pages/InteractionCalendars'));
const Competitors = lazy(() => import('./pages/Competitors'));
const Budgets = lazy(() => import('./pages/Budgets'));
const Segments = lazy(() => import('./pages/Segments'));
const TaskScheduler = lazy(() => import('./pages/TaskScheduler'));
const OpenApiApps = lazy(() => import('./pages/OpenApiApps'));
const MessageTemplateCenter = lazy(() => import('./pages/MessageTemplateCenter'));

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
  { path: 'ai-assistant', element: <AiAssistant /> },
  { path: 'channel-codes', element: <ChannelCodes /> },
  { path: 'welcome-messages', element: <WelcomeMessages /> },
  { path: 'auto-tags', element: <AutoTags /> },
  { path: 'mass-sends', element: <MassSends /> },
  { path: 'group-broadcast', element: <GroupBroadcast /> },
  { path: 'quick-replies', element: <QuickReplies /> },
  { path: 'speeches', element: <Speeches /> },
  { path: 'assets', element: <Assets /> },
  { path: 'public-sea', element: <PublicSea /> },
  { path: 'opportunities', element: <Opportunities /> },
  { path: 'tickets', element: <Tickets /> },
  { path: 'membership', element: <Membership /> },
  { path: 'points', element: <Points /> },
  { path: 'coupons', element: <Coupons /> },
  { path: 'surveys', element: <Surveys /> },
  { path: 'knowledge-base', element: <KnowledgeBase /> },
  { path: 'content-marketing', element: <ContentMarketing /> },
  { path: 'quality-inspections', element: <QualityInspections /> },
  { path: 'rfm', element: <RfmAnalysis /> },
  { path: 'ltv', element: <LtvPrediction /> },
  { path: 'attribution', element: <Attribution /> },
  { path: 'blacklist', element: <Blacklist /> },
  { path: 'contracts', element: <Contracts /> },
  { path: 'orders', element: <Orders /> },
  { path: 'commissions', element: <Commissions /> },
  { path: 'invoices', element: <Invoices /> },
  { path: 'lead-scoring', element: <LeadScoring /> },
  { path: 'notifications', element: <Notifications /> },
  { path: 'webhooks', element: <Webhooks /> },
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
