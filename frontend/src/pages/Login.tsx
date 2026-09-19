/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Login.tsx
 * Date : 2026/07/26
 * Author : hiylo
 * Contact : hiylo@live.com
 */

import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Card, Form, Input, Button, Typography, App, Checkbox } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { login, saveAuthData } from '../api/auth';

const { Title, Text } = Typography;

/** localStorage 中保存被记住用户名的键 */
const REMEMBERED_USER_KEY = 'rememberedUser';

/**
 * 登录页面
 * 通过网关 OAuth 端点认证, 保存 token 后跳转原页面或首页
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
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      // 背景渐变使用 CSS 变量, 跟随亮色/暗色主题切换
      background: 'var(--color-bg-gradient)',
    }}>
      <Card style={{ width: 400, boxShadow: '0 8px 32px rgba(0,0,0,0.1)' }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <Title level={2} style={{ marginBottom: 8 }}>SCRM</Title>
          <Text type="secondary">自动化运营引擎</Text>
        </div>
        <Form
          name="login"
          onFinish={handleLogin}
          autoComplete="off"
          size="large"
          // 初始值: 回填已记住的用户名并勾选"记住我"
          initialValues={{ username: rememberedUser, remember: !!rememberedUser }}
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item name="remember" valuePropName="checked">
            <Checkbox>记住我</Checkbox>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
