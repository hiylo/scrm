/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : GlobalExceptionHandler.java
 * Date : 2026/06/19 02:59:50
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.BindException;

/**
 * SCRM 服务全局异常处理器
 * <p>
 * 统一捕获 Controller 层抛出的异常, 转换为 OperationResponse 标准响应体,
 * 避免异常堆栈泄露给前端, 并按异常类型映射到合适的 HTTP 状态码。
 * </p>
 *
 * @author Hsi Chu
 */
@RestControllerAdvice
@Order(2)
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理 SCRM 业务异常
     *
     * @param e ScrmException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(ScrmException.class)
    public ResponseEntity<OperationResponse<Void>> handleScrmException(ScrmException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        int code = parseCode(e.getCode(), e.getHttpStatus().value());
        return ResponseEntity.status(e.getHttpStatus()).body(OperationResponse.fail(code, e.getMessage()));
    }

    /**
     * 处理 @Valid 请求体校验失败异常
     *
     * @param e MethodArgumentNotValidException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OperationResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("请求参数校验失败");
        log.warn("请求参数校验失败: {}", errorMsg);
        return ResponseEntity.badRequest().body(OperationResponse.fail(400, errorMsg));
    }

    /**
     * 处理表单参数绑定异常
     *
     * @param e BindException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<OperationResponse<Void>> handleBindException(BindException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数绑定失败");
        return ResponseEntity.badRequest().body(OperationResponse.fail(400, errorMsg));
    }

    /**
     * 处理 @Validated 路径/查询参数校验失败异常
     *
     * @param e ConstraintViolationException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<OperationResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String errorMsg = e.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数约束校验失败");
        return ResponseEntity.badRequest().body(OperationResponse.fail(400, errorMsg));
    }

    /**
     * 处理请求体反序列化失败异常
     *
     * @param e HttpMessageNotReadableException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OperationResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(OperationResponse.fail(400, "请求体格式错误"));
    }

    /**
     * 处理参数类型不匹配异常
     *
     * @param e MethodArgumentTypeMismatchException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<OperationResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(OperationResponse.fail(400,
                String.format("参数 '%s' 类型错误", e.getName())));
    }

    /**
     * 处理缺少必填参数异常
     *
     * @param e MissingServletRequestParameterException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<OperationResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(OperationResponse.fail(400,
                String.format("缺少必要参数: %s", e.getParameterName())));
    }

    /**
     * 处理安全相关异常
     *
     * @param e SecurityException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<OperationResponse<Void>> handleSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(OperationResponse.fail(403, "权限不足"));
    }

    /**
     * 处理不支持的请求方法异常
     *
     * @param e HttpRequestMethodNotSupportedException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<OperationResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(OperationResponse.fail(405,
                String.format("请求方法 '%s' 不支持", e.getMethod())));
    }

    /**
     * 处理数据库访问异常, 屏蔽底层 SQL 细节
     *
     * @param e DataAccessException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<OperationResponse<Void>> handleDataAccess(DataAccessException e) {
        log.error("数据库操作异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(OperationResponse.fail(500, "数据库操作失败"));
    }

    /**
     * 处理静态资源 404 异常 (API 路径返回 JSON 404)
     *
     * @param e NoResourceFoundException
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<OperationResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        String path = e.getResourcePath();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(OperationResponse.fail(404, "资源不存在: " + path));
    }

    /**
     * 兜底处理未捕获的异常
     *
     * @param e Exception
     * @return OperationResponse 响应体
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<OperationResponse<Void>> handleGenericException(Exception e) {
        log.error("未处理异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(OperationResponse.fail(500, "服务器内部错误"));
    }

    /**
     * 将字符串错误码解析为 int, 解析失败回退到 HTTP 状态码
     *
     * @param code      字符串错误码
     * @param fallback  回退状态码
     * @return int 错误码
     */
    private int parseCode(String code, int fallback) {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
