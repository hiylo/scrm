/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : useRole.test.ts
 * Description : 角色权限 Hook 的权限矩阵测试
 */

import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useRole } from './useRole';

/** 以给定角色渲染 Hook */
function renderWithRole(role: string) {
  localStorage.setItem('userRole', role);
  return renderHook(() => useRole());
}

describe('useRole', () => {
  it('无角色信息时按 viewer 处理', () => {
    const { result } = renderHook(() => useRole());
    expect(result.current.role).toBe('viewer');
    expect(result.current.canEdit).toBe(false);
    expect(result.current.canDelete).toBe(false);
  });

  it('admin 可编辑可删除', () => {
    const { result } = renderWithRole('admin');
    expect(result.current).toMatchObject({
      role: 'admin',
      isAdmin: true,
      isManager: false,
      isSales: false,
      isViewer: false,
      canEdit: true,
      canDelete: true,
    });
  });

  it('manager 可编辑可删除', () => {
    const { result } = renderWithRole('manager');
    expect(result.current.isManager).toBe(true);
    expect(result.current.canEdit).toBe(true);
    expect(result.current.canDelete).toBe(true);
  });

  it('sales 可编辑但不可删除', () => {
    const { result } = renderWithRole('sales');
    expect(result.current.isSales).toBe(true);
    expect(result.current.canEdit).toBe(true);
    expect(result.current.canDelete).toBe(false);
  });

  it('viewer 只读', () => {
    const { result } = renderWithRole('viewer');
    expect(result.current.isViewer).toBe(true);
    expect(result.current.canEdit).toBe(false);
    expect(result.current.canDelete).toBe(false);
  });

  it('未知角色仍视为可编辑 (仅按 viewer 特判)', () => {
    const { result } = renderWithRole('auditor');
    expect(result.current.role).toBe('auditor');
    expect(result.current.canEdit).toBe(true);
    expect(result.current.canDelete).toBe(false);
    expect(result.current.isAdmin).toBe(false);
  });

  it('userRole 缺失时从 userRoles 列表推导主角色', () => {
    localStorage.setItem('userRoles', JSON.stringify(['ROLE_SALES', 'ROLE_MANAGER']));
    const { result } = renderHook(() => useRole());
    expect(result.current.role).toBe('manager');
    expect(result.current.canDelete).toBe(true);
  });
});
