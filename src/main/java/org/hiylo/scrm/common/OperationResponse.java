/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OperationResponse.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.common;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用操作响应对象
 * <p>
 * 封装接口统一响应格式, 包含状态 (SUCCESS / ERROR / PARTIAL_SUCCESS)、
 * 响应代码、消息、业务数据与分页信息, 提供静态工厂方法快速构建响应。
 * </p>
 *
 * @param <T> 业务数据类型
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class OperationResponse<T> {

    /** 状态: SUCCESS / ERROR / PARTIAL_SUCCESS */
    protected String status;

    /** 响应消息 */
    protected String message;

    /** 响应代码 */
    protected String code;

    /** 业务数据 */
    protected T data;

    /** 总数 (用于分页响应) */
    protected Integer total;

    /** 页大小 (用于分页响应) */
    protected Integer pageSize;

    /** 操作警告信息 (如功能受限提示) */
    protected List<String> warnings;

    /** 受限功能的操作指引 (key=功能名, value=指引步骤列表) */
    protected Map<String, ?> guidance;

    /**
     * 构建成功响应 (无数据)
     *
     * @return 成功响应对象
     */
    public static <T> OperationResponse<T> build() {
        return build(null);
    }

    /**
     * 构建成功响应 (带数据)
     *
     * @param data 业务数据
     * @return 成功响应对象
     */
    public static <T> OperationResponse<T> build(T data) {
        OperationResponse<T> response = new OperationResponse<>();
        response.setData(data);
        response.setMessage("成功");
        response.setCode("200");
        response.setStatus("SUCCESS");
        return response;
    }

    /**
     * 构建成功响应 (带消息和数据)
     *
     * @param message 响应消息
     * @param data    业务数据
     * @return 成功响应对象
     */
    public static <T> OperationResponse<T> build(String message, T data) {
        OperationResponse<T> response = new OperationResponse<>();
        response.setMessage(message);
        response.setData(data);
        response.setCode("200");
        response.setStatus("SUCCESS");
        return response;
    }

    /**
     * 构建成功响应 (无数据)
     *
     * @return 成功响应对象
     */
    public static <T> OperationResponse<T> success() {
        return build(null);
    }

    /**
     * 构建成功响应 (带数据)
     *
     * @param data 业务数据
     * @return 成功响应对象
     */
    public static <T> OperationResponse<T> success(T data) {
        return build(data);
    }

    /**
     * 构建成功响应 (带消息)
     *
     * @param message 响应消息
     * @return 成功响应对象
     */
    public static <T> OperationResponse<T> success(String message) {
        OperationResponse<T> response = new OperationResponse<>();
        response.setMessage(message);
        response.setCode("200");
        response.setStatus("SUCCESS");
        return response;
    }

    /**
     * 构建错误响应
     *
     * @param message 错误消息
     * @return 错误响应对象
     */
    public static <T> OperationResponse<T> error(String message) {
        OperationResponse<T> response = new OperationResponse<>();
        response.setMessage(message);
        response.setCode("500");
        response.setStatus("ERROR");
        return response;
    }

    /**
     * 构建错误响应 (带字符串错误码)
     *
     * @param code    错误代码
     * @param message 错误消息
     * @return 错误响应对象
     */
    public static <T> OperationResponse<T> fail(String code, String message) {
        OperationResponse<T> response = new OperationResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setStatus("ERROR");
        return response;
    }

    /**
     * 构建错误响应 (带整数错误码)
     *
     * @param code    错误代码
     * @param message 错误消息
     * @return 错误响应对象
     */
    public static <T> OperationResponse<T> fail(Integer code, String message) {
        return fail(String.valueOf(code), message);
    }

    /**
     * 构建错误响应 (等价于 {@link #error(String)})
     *
     * @param message 错误消息
     * @return 错误响应对象
     */
    public static <T> OperationResponse<T> fail(String message) {
        return error(message);
    }

    /**
     * 构建部分成功响应 (操作已执行但有降级或需要手动步骤), code=206
     *
     * @param data     业务数据
     * @param message  响应消息
     * @param warnings 警告信息列表
     * @param guidance 受限功能操作指引
     * @return 部分成功响应对象
     */
    public static <T> OperationResponse<T> partialSuccess(T data, String message,
                                                           List<String> warnings,
                                                           Map<String, ?> guidance) {
        OperationResponse<T> response = new OperationResponse<>();
        response.setData(data);
        response.setMessage(message);
        response.setCode("206");
        response.setStatus("PARTIAL_SUCCESS");
        response.setWarnings(warnings);
        response.setGuidance(guidance);
        return response;
    }

    /**
     * 设置错误状态 (整数错误码)
     *
     * @param code    错误代码
     * @param message 错误消息
     * @return 当前响应对象
     */
    public OperationResponse<T> error(Integer code, String message) {
        return error(String.valueOf(code), message);
    }

    /**
     * 设置错误状态 (字符串错误码)
     *
     * @param code    错误代码
     * @param message 错误消息
     * @return 当前响应对象
     */
    public OperationResponse<T> error(String code, String message) {
        this.code = code;
        this.message = message;
        this.status = "ERROR";
        return this;
    }

    /**
     * 判断是否成功 (含部分成功)
     *
     * @return true 表示成功或部分成功
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status) || "PARTIAL_SUCCESS".equals(status)
                || "200".equals(code) || "206".equals(code);
    }

    /**
     * 判断是否部分成功 (降级)
     *
     * @return true 表示部分成功
     */
    public boolean isPartialSuccess() {
        return "PARTIAL_SUCCESS".equals(status) || "206".equals(code);
    }

    /** 设置警告信息列表, 存入副本以隔离外部修改 */
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? null : new ArrayList<>(warnings);
    }

    /** 获取警告信息列表副本 */
    public List<String> getWarnings() {
        return warnings == null ? null : new ArrayList<>(warnings);
    }

    /** 设置受限功能操作指引, 存入副本以隔离外部修改 */
    public void setGuidance(Map<String, ?> guidance) {
        this.guidance = guidance == null ? null : new HashMap<>(guidance);
    }

    /** 获取受限功能操作指引副本 */
    public Map<String, ?> getGuidance() {
        return guidance == null ? null : new HashMap<>(guidance);
    }
}
