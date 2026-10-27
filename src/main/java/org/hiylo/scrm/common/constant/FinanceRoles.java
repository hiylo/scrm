/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : FinanceRoles.java
 * Date : 2026/10/25 09:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.common.constant;

import java.util.List;

/**
 * 财税域角色常量。
 * <p>
 * 代账公司的岗位分工与通用 CRM 销售岗不同，核心是「会计」这一角色：
 * 会计负责一批公司主体的报税、续费与客户对接，需要独立的「我的客户」视图。
 * </p>
 * <p>
 * <b>与 {@code DataScopeService} 的关系</b>：数据隔离仍沿用原有的
 * {@code ADMIN/VIEWER} 规则（ADMIN 不过滤、VIEWER 只读），本类只定义
 * 财税业务层的岗位标识，用于工作台分流与提醒路由，<b>不改变</b>既有数据权限语义。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public final class FinanceRoles {

    /** 会计：负责公司主体的报税/续费/客户对接，只看自己负责的主体 */
    public static final String ACCOUNTANT = "ACCOUNTANT";

    /** 主管：可查看全部会计的工作量与待办分布 */
    public static final String SUPERVISOR = "SUPERVISOR";

    /** 可指派「负责会计」的角色（管理员、经理与主管） */
    public static final List<String> ASSIGNABLE_ROLES = List.of("ADMIN", "MANAGER", SUPERVISOR);

    /** 工具类，私有构造防止实例化 */
    private FinanceRoles() {
    }

    /**
     * 判断角色串（逗号分隔）是否包含指定角色（忽略大小写）。
     *
     * @param rolesCsv 角色串，如 {@code "ACCOUNTANT,MANAGER"}
     * @param target   目标角色
     * @return 是否包含
     */
    public static boolean contains(String rolesCsv, String target) {
        if (rolesCsv == null || rolesCsv.isBlank() || target == null) {
            return false;
        }
        for (String item : rolesCsv.split(",")) {
            if (target.equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }
}
