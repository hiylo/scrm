/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AuditLogAspect.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import org.hiylo.scrm.entity.ScrmAuditLogEntity;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmAuditLogService;
import org.hiylo.scrm.common.util.SensitiveDataUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SCRM 操作审计日志切面。
 * <p>
 * 通过 AOP 自动拦截 {@code org.hiylo.scrm.controller} 包下所有 {@code @RestController}
 * 的写操作 (POST / PUT / DELETE), 提取操作人、资源、动作、请求参数、响应结果、执行耗时与异常信息,
 * 异步写入 {@code scrm_audit_log} 表, 不阻塞主流程。
 * </p>
 * <p>
 * 资源与动作优先取自方法或类上的 {@link RequirePermission} 注解; 注解缺失时回退为 "unknown",
 * 保证回调类 (无 @RequirePermission) 的写操作也能被审计。
 * </p>
 * <p>
 * 请求参数落库前会对字段名命中 password/pwd/secret/api-key/token 的值递归脱敏,
 * 防止明文密钥 / 口令 / Token 写入 {@code scrm_audit_log} 表。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    /** 请求/响应参数最大保留长度, 超过截断防止 DB 字段过大 */
    private static final int MAX_PAYLOAD_LENGTH = 2000;

    /** 默认资源标识 (方法无 @RequirePermission 时使用) */
    private static final String DEFAULT_RESOURCE = "unknown";

    /** 默认动作 (方法无 @RequirePermission 时使用) */
    private static final String DEFAULT_ACTION = "unknown";

    /** 操作人用户 ID 请求头名称 */
    private static final String USER_ID_HEADER = "X-User-Id";

    /** 操作人用户名请求头名称 */
    private static final String USERNAME_HEADER = "X-Username";

    /** 敏感参数名正则: password / pwd / secret / api-key / token (不区分大小写) */
    private static final Pattern SENSITIVE_KEY_PATTERN =
            Pattern.compile("(?i).*(password|pwd|secret|api[-_]?key|token).*");

    /** 反向代理转发的客户端 IP 头 */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    };

    /** Jackson JSON 序列化器, 复用 Spring Boot 自动配置的实例 */
    private final ObjectMapper objectMapper;

    /** 审计日志服务 (异步保存入口) */
    private final ScrmAuditLogService auditLogService;

    /**
     * 切点: 匹配类上 @RestController (绑定注解, 用于通知参数)。
     *
     * @param restController @RestController 注解实例
     */
    @Pointcut("@within(restController)")
    public void restControllerBean(RestController restController) {
        // 仅作 Pointcut 引用, 实现为空
    }

    /**
     * 切点: 匹配方法上 @PostMapping / @PutMapping / @DeleteMapping 任一写操作注解。
     */
    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping)"
            + "|| @annotation(org.springframework.web.bind.annotation.PutMapping)"
            + "|| @annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void writeMappingMethod() {
        // 仅作 Pointcut 引用, 实现为空
    }

    /**
     * 环绕通知: 拦截 Controller 写操作, 记录审计日志后异步保存。
     *
     * @param pjp             连接点
     * @param restController  类上 @RestController (来自 Pointcut 绑定)
     * @return 目标方法的原始返回值
     * @throws Throwable 目标方法抛出的异常 (原样抛出, 不吞异常)
     */
    @Around("restControllerBean(restController) && writeMappingMethod()")
    public Object aroundWriteOperation(ProceedingJoinPoint pjp,
                                       RestController restController) throws Throwable {
        long startNanos = System.nanoTime();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        HttpServletRequest request = currentRequest();

        // 提取 @RequirePermission 元数据 (方法优先, 其次类)
        RequirePermission permission = resolvePermission(method);
        String resource = permission != null ? permission.resource() : DEFAULT_RESOURCE;
        String action = permission != null ? permission.action() : DEFAULT_ACTION;

        // 解析 HTTP 方法与请求路径
        String httpMethod = request != null ? request.getMethod() : "UNKNOWN";
        String requestUri = request != null ? request.getRequestURI() : "";

        // 构建审计日志实体
        ScrmAuditLogEntity auditLog = new ScrmAuditLogEntity();
        auditLog.setResource(resource);
        auditLog.setAction(action);
        auditLog.setMethod(httpMethod);
        auditLog.setRequestUri(requestUri);
        auditLog.setOperatedAt(LocalDateTime.now());
        if (request != null) {
            auditLog.setUserId(request.getHeader(USER_ID_HEADER));
            auditLog.setUsername(request.getHeader(USERNAME_HEADER));
            auditLog.setClientIp(resolveClientIp(request));
            auditLog.setRequestParams(truncate(buildRequestParams(request, method, pjp.getArgs())));
        }
        // 归属账号在切面线程同步获取 (异步线程无 ThreadLocal)

        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable ex) {
            error = ex;
            throw ex;
        } finally {
            long executionTime = (System.nanoTime() - startNanos) / 1_000_000L;
            auditLog.setExecutionTime(executionTime);
            if (error != null) {
                auditLog.setResult(ScrmAuditLogService.RESULT_FAILED);
                auditLog.setErrorMessage(truncate(safeErrorMessage(error)));
            } else {
                auditLog.setResult(ScrmAuditLogService.RESULT_SUCCESS);
                auditLog.setResponseBody(truncate(safeToJson(result)));
            }
            // 异步保存, 不阻塞主流程
            try {
                auditLogService.saveAsync(auditLog);
            } catch (Exception e) {
                log.warn("提交审计日志异步保存失败: uri={}, err={}", requestUri, e.getMessage());
            }
        }
    }

    /**
     * 解析方法或类上的 {@link RequirePermission} 注解, 方法优先。
     *
     * @param method 当前方法
     * @return 注解实例, 不存在返回 null
     */
    private RequirePermission resolvePermission(Method method) {
        RequirePermission methodLevel = method.getAnnotation(RequirePermission.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        Class<?> declaringClass = method.getDeclaringClass();
        return declaringClass.getAnnotation(RequirePermission.class);
    }

    /**
     * 从 Spring RequestContext 获取当前 HttpServletRequest。
     *
     * @return 当前请求, 不在请求上下文时返回 null
     */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    /**
     * 解析客户端真实 IP, 优先从反向代理头取首个 IP。
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return comma > 0 ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 构建请求参数 JSON 字符串。
     * <p>
     * 合并 query 参数 (request.getParameterMap) 与方法参数 (排除 Servlet / 文件等不可序列化对象)。
     * </p>
     *
     * @param request HTTP 请求
     * @param method  当前方法
     * @param args    方法实参
     * @return JSON 字符串
     */
    private String buildRequestParams(HttpServletRequest request, Method method, Object[] args) {
        Map<String, Object> params = new HashMap<>();
        // query / form 参数
        Map<String, String[]> paramMap = request.getParameterMap();
        if (paramMap != null && !paramMap.isEmpty()) {
            for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                String[] values = entry.getValue();
                if (values == null || values.length == 0) {
                    params.put(entry.getKey(), "");
                } else if (values.length == 1) {
                    params.put(entry.getKey(), values[0]);
                } else {
                    params.put(entry.getKey(), values);
                }
            }
        }
        // 方法参数 (按参数名收集, 排除 Servlet / MultipartFile 等不可序列化对象)
        Parameter[] parameters = method.getParameters();
        if (args != null && parameters != null) {
            for (int i = 0; i < args.length && i < parameters.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    continue;
                }
                if (isExcludedArg(arg)) {
                    continue;
                }
                String name = parameters[i].getName();
                params.put(name, arg);
            }
        }
        return maskSensitivePayload(params);
    }

    /**
     * 对请求参数进行敏感字段脱敏后序列化为 JSON 字符串。
     * <p>
     * 递归遍历参数树, 字段名命中 {@link #SENSITIVE_KEY_PATTERN} (password/pwd/secret/api-key/token,
     * 不区分大小写) 的值 (含嵌套 DTO 中的字段) 统一用 {@link SensitiveDataUtils#maskGeneric} 脱敏,
     * 防止明文密钥 / 口令 / Token 落入审计日志。
     * </p>
     *
     * @param params 请求参数
     * @return 脱敏后的 JSON 字符串
     */
    private String maskSensitivePayload(Map<String, Object> params) {
        ObjectNode root = objectMapper.valueToTree(params);
        maskSensitiveKeys(root);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.debug("审计日志参数序列化失败, 回退脱敏文本: {}", e.getMessage());
            return SensitiveDataUtils.maskSensitiveMessage(params.toString());
        }
    }

    /**
     * 递归遍历 JSON 树, 将字段名命中 {@link #SENSITIVE_KEY_PATTERN} 的叶子值脱敏。
     *
     * @param node 待遍历节点
     */
    private void maskSensitiveKeys(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveKey(field.getKey())) {
                    object.set(field.getKey(), TextNode.valueOf(maskSensitiveValue(field.getValue())));
                } else {
                    maskSensitiveKeys(field.getValue());
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (JsonNode element : array) {
                maskSensitiveKeys(element);
            }
        }
    }

    /**
     * 判断字段名是否命中敏感关键词。
     *
     * @param key 字段名
     * @return true=命中, 需要脱敏
     */
    private boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_KEY_PATTERN.matcher(key).matches();
    }

    /**
     * 对敏感字段值脱敏 (通用脱敏: 保留前4后4, 长度不超过8统一返回 ****)。
     *
     * @param value 敏感字段值节点
     * @return 脱敏后的字符串
     */
    private String maskSensitiveValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "****";
        }
        return SensitiveDataUtils.maskGeneric(value.asText());
    }

    /**
     * 判断方法实参是否应排除 (Servlet 对象 / MultipartFile / 响应对象等)。
     *
     * @param arg 实参
     * @return true 表示排除
     */
    private boolean isExcludedArg(Object arg) {
        return arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse
                || arg instanceof MultipartFile
                || arg instanceof org.springframework.ui.Model
                || arg instanceof org.springframework.validation.BindingResult;
    }

    /**
     * 安全序列化对象为 JSON, 失败时返回对象 toString。
     *
     * @param obj 待序列化对象
     * @return JSON 字符串
     */
    private String safeToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.debug("审计日志响应序列化失败, 回退 toString: {}", e.getMessage());
            return obj.toString();
        }
    }

    /**
     * 提取异常信息 (含类型), 截断到限定长度。
     *
     * @param ex 异常
     * @return 异常描述
     */
    private String safeErrorMessage(Throwable ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex.getClass().getSimpleName();
        }
        return ex.getClass().getSimpleName() + ": " + msg;
    }

    /**
     * 截断字符串到 {@link #MAX_PAYLOAD_LENGTH}。
     *
     * @param value 原始字符串
     * @return 截断后的字符串
     */
    private String truncate(String value) {
        if (value == null || value.length() <= MAX_PAYLOAD_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_PAYLOAD_LENGTH);
    }
}
