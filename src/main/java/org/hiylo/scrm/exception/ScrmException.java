/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmException.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * SCRM 服务统一业务异常
 * <p>
 * 携带错误码 code 与对应 HTTP 状态, 由 GlobalExceptionHandler 统一捕获并转换为
 * OperationResponse 返回前端。所有 SCRM 模块的业务校验失败应抛出此异常或其子类。
 * </p>
 *
 * @author Hsi Chu
 */
@Getter
public class ScrmException extends RuntimeException {

    /** 错误码 (字符串形式, 兼容数字状态码与语义化错误码) */
    private final String code;

    /** HTTP 状态码, 默认 400 Bad Request */
    private final HttpStatus httpStatus;

    /**
     * 构造业务异常 (默认 HTTP 400)
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public ScrmException(String code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = HttpStatus.BAD_REQUEST;
    }

    /**
     * 构造业务异常, 指定 HTTP 状态
     *
     * @param code        错误码
     * @param message     错误消息
     * @param httpStatus  HTTP 状态码
     */
    public ScrmException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /**
     * 构造业务异常, 带原始 cause (默认 HTTP 500)
     *
     * @param code    错误码
     * @param message 错误消息
     * @param cause   原始异常
     */
    public ScrmException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 快速构造 404 资源不存在异常
     *
     * @param message 错误消息
     * @return ScrmException 实例
     */
    public static ScrmException notFound(String message) {
        return new ScrmException(ScrmExceptionConstants.NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    /**
     * 快速构造 400 请求参数异常
     *
     * @param message 错误消息
     * @return ScrmException 实例
     */
    public static ScrmException badRequest(String message) {
        return new ScrmException(ScrmExceptionConstants.BAD_REQUEST, message, HttpStatus.BAD_REQUEST);
    }

    /**
     * 快速构造 403 权限不足异常
     *
     * @param message 错误消息
     * @return ScrmException 实例
     */
    public static ScrmException forbidden(String message) {
        return new ScrmException(ScrmExceptionConstants.FORBIDDEN, message, HttpStatus.FORBIDDEN);
    }

    /**
     * 快速构造 401 未认证异常 (回调鉴权失败等)
     *
     * @param message 错误消息
     * @return ScrmException 实例
     */
    public static ScrmException unauthorized(String message) {
        return new ScrmException(ScrmExceptionConstants.UNAUTHORIZED, message, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 快速构造 500 内部错误异常
     *
     * @param message 错误消息
     * @return ScrmException 实例
     */
    public static ScrmException internal(String message) {
        return new ScrmException(ScrmExceptionConstants.INTERNAL_ERROR,
                message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 快速构造 409 资源冲突异常 (如人设名称重复)
     *
     * @param message 错误消息
     * @return ScrmException 实例
     */
    public static ScrmException conflict(String message) {
        return new ScrmException(ScrmExceptionConstants.CONFLICT, message, HttpStatus.CONFLICT);
    }

    /**
     * 快速构造 429 请求过多异常 (触发 API 限流)
     *
     * @param message 错误消息
     * @return ScrmException 实例
     */
    public static ScrmException tooManyRequests(String message) {
        return new ScrmException(ScrmExceptionConstants.TOO_MANY_REQUESTS,
                message, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * 快速构造 501 未启用异常 (占位实现, 避免前端误用)
     * <p>
     * 用于标记 Controller 中尚未对接真实业务逻辑的占位端点, 由 GlobalExceptionHandler
     * 统一捕获并返回 HTTP 501 Not Implemented。
     * </p>
     *
     * @param message 错误消息 (建议格式: "该功能未启用: &lt;功能描述&gt;")
     * @return ScrmException 实例
     */
    public static ScrmException notImplemented(String message) {
        return new ScrmException(ScrmExceptionConstants.NOT_IMPLEMENTED,
                message, HttpStatus.NOT_IMPLEMENTED);
    }
}
