/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExceptionConstants.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.exception;

/**
 * SCRM 服务异常码常量
 * <p>
 * 统一管理所有 SCRM 模块的业务错误码, 便于前端按码差异化处理。
 * 命名规则: SCRM_<模块>_<场景>。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public final class ScrmExceptionConstants {

    private ScrmExceptionConstants() {
        // 常量类, 禁止实例化
    }

    // ==================== 通用错误码 ====================

    /** 资源不存在 */
    public static final String NOT_FOUND = "SCRM_NOT_FOUND";

    /** 请求参数错误 */
    public static final String BAD_REQUEST = "SCRM_BAD_REQUEST";

    /** 未认证 (令牌缺失/非法/过期, 回调鉴权失败等) */
    public static final String UNAUTHORIZED = "SCRM_UNAUTHORIZED";

    /** 权限不足 */
    public static final String FORBIDDEN = "SCRM_FORBIDDEN";

    /** 资源冲突 (重复创建) */
    public static final String CONFLICT = "SCRM_CONFLICT";

    /** 服务器内部错误 */
    public static final String INTERNAL_ERROR = "SCRM_INTERNAL_ERROR";

    /** 请求过于频繁 (触发限流) */
    public static final String TOO_MANY_REQUESTS = "SCRM_TOO_MANY_REQUESTS";

    /** 功能未启用 (占位实现, HTTP 501 Not Implemented) */
    public static final String NOT_IMPLEMENTED = "SCRM_NOT_IMPLEMENTED";

    // ==================== 认证模块 ====================

    /** 登录用户名或密码错误 */
    public static final String SCRM_AUTH_BAD_CREDENTIALS = "SCRM_AUTH_BAD_CREDENTIALS";

    /** 账号已被禁用, 禁止登录 */
    public static final String SCRM_AUTH_USER_DISABLED = "SCRM_AUTH_USER_DISABLED";

    /** 用户名已被占用 (注册冲突) */
    public static final String SCRM_AUTH_USERNAME_TAKEN = "SCRM_AUTH_USERNAME_TAKEN";

    /** 密码强度不足 (少于 8 位或不含字母/数字) */
    public static final String SCRM_AUTH_PASSWORD_WEAK = "SCRM_AUTH_PASSWORD_WEAK";

    /** 令牌缺失或非法 (未认证访问受保护端点) */
    public static final String SCRM_AUTH_TOKEN_INVALID = "SCRM_AUTH_TOKEN_INVALID";

    /** 用户不存在或已删除 (按 uid 查询失败) */
    public static final String SCRM_AUTH_USER_NOT_FOUND = "SCRM_AUTH_USER_NOT_FOUND";

    // ==================== 账号模块 ====================

    /** SCRM 账号不存在 */
    public static final String SCRM_ACCOUNT_NOT_FOUND = "SCRM_ACCOUNT_NOT_FOUND";

    /** SCRM 账号已被禁用 */
    public static final String SCRM_ACCOUNT_DISABLED = "SCRM_ACCOUNT_DISABLED";

    /** SCRM 账号 cookie/token 已过期 */
    public static final String SCRM_ACCOUNT_TOKEN_EXPIRED = "SCRM_ACCOUNT_TOKEN_EXPIRED";

    /** SCRM 账号在目标平台已被风控 */
    public static final String SCRM_ACCOUNT_RISK_CONTROL = "SCRM_ACCOUNT_RISK_CONTROL";

    /** 平台类型不支持 */
    public static final String SCRM_PLATFORM_NOT_SUPPORTED = "SCRM_PLATFORM_NOT_SUPPORTED";

    // ==================== 人设模块 ====================

    /** 人设名称重复 */
    public static final String SCRM_PERSONA_DUPLICATED = "SCRM_PERSONA_DUPLICATED";

    /** 人设不存在 */
    public static final String SCRM_PERSONA_NOT_FOUND = "SCRM_PERSONA_NOT_FOUND";

    // ==================== 内容分发模块 ====================

    /** 内容素材不存在 */
    public static final String SCRM_CONTENT_NOT_FOUND = "SCRM_CONTENT_NOT_FOUND";

    /** 内容分发任务不存在 */
    public static final String SCRM_TASK_NOT_FOUND = "SCRM_TASK_NOT_FOUND";

    /** 内容分发任务状态非法, 不允许当前操作 */
    public static final String SCRM_TASK_INVALID_STATE = "SCRM_TASK_INVALID_STATE";

    /** 平台 adapter 调用失败 (登录态失效 / 接口限流 / 网络异常) */
    public static final String SCRM_PLATFORM_INVOKE_FAILED = "SCRM_PLATFORM_INVOKE_FAILED";

    // ==================== 会话模块 ====================

    /** SCRM 会话不存在 */
    public static final String SCRM_CONVERSATION_NOT_FOUND = "SCRM_CONVERSATION_NOT_FOUND";

    /** SCRM 会话重复创建 (platformConversationId 已存在) */
    public static final String SCRM_CONVERSATION_DUPLICATED = "SCRM_CONVERSATION_DUPLICATED";

    /** SCRM 会话消息不存在 */
    public static final String SCRM_MESSAGE_NOT_FOUND = "SCRM_MESSAGE_NOT_FOUND";

    /** SCRM 消息类型非法 */
    public static final String SCRM_MESSAGE_TYPE_INVALID = "SCRM_MESSAGE_TYPE_INVALID";

    /** SCRM 消息方向非法 */
    public static final String SCRM_MESSAGE_DIRECTION_INVALID = "SCRM_MESSAGE_DIRECTION_INVALID";

    /** SCRM 媒体消息缺少媒体对象 key */
    public static final String SCRM_MESSAGE_MEDIA_MISSING = "SCRM_MESSAGE_MEDIA_MISSING";

    /** SCRM 文本消息缺少内容 */
    public static final String SCRM_MESSAGE_CONTENT_MISSING = "SCRM_MESSAGE_CONTENT_MISSING";

    // ==================== 媒体存储模块 ====================

    /** 媒体文件上传失败 */
    public static final String SCRM_MEDIA_UPLOAD_FAILED = "SCRM_MEDIA_UPLOAD_FAILED";

    /** 媒体文件下载失败 */
    public static final String SCRM_MEDIA_DOWNLOAD_FAILED = "SCRM_MEDIA_DOWNLOAD_FAILED";

    /** 生成媒体预签名 URL 失败 */
    public static final String SCRM_MEDIA_PRESIGN_FAILED = "SCRM_MEDIA_PRESIGN_FAILED";

    /** 媒体文件删除失败 */
    public static final String SCRM_MEDIA_DELETE_FAILED = "SCRM_MEDIA_DELETE_FAILED";

    // ==================== 客户分组模块 ====================

    /** SCRM 客户分组不存在 */
    public static final String SCRM_CUSTOMER_GROUP_NOT_FOUND = "SCRM_CUSTOMER_GROUP_NOT_FOUND";

    /** 客户已存在于分组（重复加入） */
    public static final String SCRM_CUSTOMER_GROUP_MEMBER_DUPLICATED = "SCRM_CUSTOMER_GROUP_MEMBER_DUPLICATED";

    // ==================== 平台配置模块 ====================

    /** 平台配置不存在 */
    public static final String SCRM_PLATFORM_CONFIG_NOT_FOUND = "SCRM_PLATFORM_CONFIG_NOT_FOUND";

    /** 平台配置连接测试失败 */
    public static final String SCRM_PLATFORM_CONFIG_TEST_FAILED = "SCRM_PLATFORM_CONFIG_TEST_FAILED";

    // ==================== 风控信号模块 ====================

    /** 风控信号参数非法（缺字段 / 等级越界等） */
    public static final String SCRM_RISK_SIGNAL_INVALID = "SCRM_RISK_SIGNAL_INVALID";

    // ==================== SOP 模板模块 ====================

    /** SOP 模板不存在 */
    public static final String SCRM_CAMPAIGN_TEMPLATE_NOT_FOUND = "SCRM_CAMPAIGN_TEMPLATE_NOT_FOUND";

    /** SOP 模板重复创建（同账号下模板名重复） */
    public static final String SCRM_CAMPAIGN_TEMPLATE_DUPLICATED = "SCRM_CAMPAIGN_TEMPLATE_DUPLICATED";
}
