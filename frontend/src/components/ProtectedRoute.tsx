/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ProtectedRoute.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { Navigate, useLocation } from 'react-router-dom';
import { ReactNode } from 'react';
import { isAuthenticated } from '../api/auth';

/**
 * 路由守卫组件
 * 未登录用户重定向到 /login, 并携带原始位置以便登录后回跳
 * @param children 受保护的子组件
 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const location = useLocation();
  if (!isAuthenticated()) {
    // 重定向到登录页, 通过 state 携带当前路径, 登录成功后跳回
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <>{children}</>;
}
