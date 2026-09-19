---
version: alpha
name: scrm-server-design-analysis
description: |
  SCRM customer management platform. Light/dark dual theme, indigo primary,
  Ant Design component overrides, login gradient. Built on React 18 + Ant Design 5.

colors:
  primary: "#6366f1"
  primary-light: "#818cf8"
  primary-dark: "#4f46e5"
  primary-lightest: "#eef2ff"

  success: "#10b981"
  success-bg: "#ecfdf5"
  warning: "#f59e0b"
  warning-bg: "#fffbeb"
  danger: "#ef4444"
  danger-bg: "#fef2f2"
  info: "#64748b"
  info-bg: "#f1f5f9"

  ink: "#0f172a"
  ink-regular: "#475569"
  ink-muted: "#64748b"
  ink-subtle: "#94a3b8"

  canvas: "#ffffff"
  canvas-page: "#f5f5f5"
  canvas-hover: "#f1f5f9"

  hairline: "#e2e8f0"
  hairline-light: "#f1f5f9"

  login-gradient: "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"

typography:
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif
  display: 24px / 600 / 1.2
  heading: 18px / 600 / 1.4
  body: 14px / 400 / 1.6
  body-sm: 13px / 400 / 1.5
  caption: 12px / 400 / 1.4

rounded:
  sm: 4px
  md: 6px
  lg: 8px
  xl: 12px

shadow:
  sm: "0 1px 2px rgba(0, 0, 0, 0.05)"
  md: "0 4px 12px rgba(0, 0, 0, 0.08)"

components:
  button-primary:
    background: "linear-gradient(135deg, #818cf8 0%, #6366f1 100%)"
    color: "#ffffff"
    rounded: "{rounded.lg}"
    padding: 6px 16px
  button-secondary:
    backgroundColor: "{colors.canvas}"
    color: "{colors.ink-regular}"
    border: 1px solid {colors.hairline}
    rounded: "{rounded.lg}"

  card:
    backgroundColor: "{colors.canvas}"
    borderRadius: "{rounded.xl}"
    border: 1px solid {colors.hairline}
    boxShadow: "{shadow.sm}"
  card-dark:
    backgroundColor: "#1a2332"
    borderRadius: "{rounded.xl}"
    border: 1px solid "rgba(255,255,255,0.06)"

  table:
    headerBackground: "{colors.canvas-page}"
    rowHover: "{colors.canvas-hover}"
    borderRadius: "{rounded.xl}"
    fontSize: 13px

  input:
    backgroundColor: "{colors.canvas}"
    border: 1px solid {colors.hairline}
    borderRadius: "{rounded.lg}"
    focusBorder: "{colors.primary}"

  sidebar:
    backgroundColor: "#0c1222"
    width: 240px
    item-active: "rgba(99, 102, 241, 0.15)"

layout:
  pagePadding: 24px

darkMode:
  selector: '[data-theme="dark"]'
  canvas: "#0c1222"
  canvas-card: "#1a2332"
  ink: "#f1f5f9"
  hairline: "#1e293b"
