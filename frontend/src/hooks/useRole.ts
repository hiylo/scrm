/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : useRole.ts
 * Date : 2026/07/27
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

import { getRole } from '../api/auth';

/**
 * 角色权限 Hook
 * 提供当前用户角色及常用权限判断
 * @returns 角色信息与权限标志
 */
export function useRole() {
  const role = getRole();
  return {
    /** 当前用户角色 (小写: admin/manager/sales/viewer) */
    role,
    /** 是否是管理员 */
    isAdmin: role === 'admin',
    /** 是否是主管 */
    isManager: role === 'manager',
    /** 是否是业务员 */
    isSales: role === 'sales',
    /** 是否是访客 */
    isViewer: role === 'viewer',
    /** 是否可编辑 (admin/manager/sales 可以, viewer 不可以) */
    canEdit: role !== 'viewer',
    /** 是否可删除 (admin/manager 可以, sales/viewer 不可以) */
    canDelete: role === 'admin' || role === 'manager',
  };
}
