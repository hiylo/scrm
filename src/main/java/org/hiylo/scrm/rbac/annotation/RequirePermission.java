/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RequirePermission.java
 * Date : 2026/04/30 19:48:19
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.rbac.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SCRM 权限校验注解
 * <p>
 * 标记在 Controller 方法或类上,声明访问所需资源与动作。
 * SCRM 服务自身不做认证(由 gateway-server 统一鉴权),此注解作为元数据声明,
 * 供网关侧的权限拦截器读取并执行细粒度授权。
 * </p>
 *
 * @author Hsi Chu
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 资源标识 (如 conversation / message / account) */
    String resource();

    /** 动作标识 (如 read / write / control) */
    String action();
}
