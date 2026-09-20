/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScheduledTaskHandlerRegistry.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 调度任务处理器白名单注册表。
 * <p>
 * 启动时收集容器内全部 {@link ScheduledTaskHandler} bean, 以「全限定类名 / 简单类名 / bean 名」三种
 * key 建立白名单映射。{@link ScrmTaskSchedulerService} 执行任务时<b>只</b>按白名单 key 查找并调用
 * {@link ScheduledTaskHandler#handle}, 不会用 {@code Class.forName} 反射
 * {@code scrm_scheduled_task.handler_class} 中存储的任意类名, 避免用户可写字段变成任意代码执行入口。
 * </p>
 * <p>
 * 白名单为空 (无任何处理器 bean) 或 {@code handler_class} 未注册时, 由调用方记为执行失败而非静默成功。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Component
@Slf4j
public class ScheduledTaskHandlerRegistry {

    /**
     * 允许的处理器方法名。
     * <p>实际调用的一律是 {@link ScheduledTaskHandler#handle} (接口方法), 不做方法级反射;
     * 此处仅校验配置书写是否落在约定集合内, 兼容历史数据默认的 {@code execute}。</p>
     */
    public static final List<String> ALLOWED_HANDLER_METHODS = List.of("handle", "execute", "");

    /** 白名单映射: key → 处理器实例 (保持注册顺序便于日志与错误消息稳定) */
    private final Map<String, ScheduledTaskHandler> whitelist;

    /**
     * 收集容器内全部处理器 bean 建立白名单。
     *
     * @param handlerProvider 处理器 bean 提供者 (无处理器时为空, 不阻断启动)
     */
    public ScheduledTaskHandlerRegistry(ObjectProvider<ScheduledTaskHandler> handlerProvider) {
        Map<String, ScheduledTaskHandler> map = new LinkedHashMap<>();
        handlerProvider.stream().forEach(handler -> register(map, handler));
        this.whitelist = Collections.unmodifiableMap(map);
        if (map.isEmpty()) {
            log.warn("调度任务处理器白名单为空, 未注册任何 ScheduledTaskHandler, 所有任务执行将记为失败");
        } else {
            log.info("调度任务处理器白名单初始化完成: handlers={}", whitelist.keySet());
        }
    }

    /**
     * 注册单个处理器的三种可查找 key。
     *
     * @param map     白名单容器
     * @param handler 处理器实例
     */
    private void register(Map<String, ScheduledTaskHandler> map, ScheduledTaskHandler handler) {
        // 处理器可能被 CGLIB 代理, 取原始类型保证类名 key 可预期
        Class<?> userClass = ClassUtils.getUserClass(handler);
        map.putIfAbsent(userClass.getName(), handler);
        map.putIfAbsent(userClass.getSimpleName(), handler);
        map.putIfAbsent(StringUtils.uncapitalize(userClass.getSimpleName()), handler);
    }

    /**
     * 按 {@code handler_class} 配置查找白名单内的处理器。
     * <p>支持全限定类名、简单类名与 bean 名; 含包名但只写了包后缀时按简单类名兜底匹配。</p>
     *
     * @param handlerClass 任务表配置的处理器类名
     * @return 命中的处理器, 未注册返回空
     */
    public Optional<ScheduledTaskHandler> find(String handlerClass) {
        if (handlerClass == null || handlerClass.isBlank()) {
            return Optional.empty();
        }
        String key = handlerClass.trim();
        ScheduledTaskHandler handler = whitelist.get(key);
        if (handler == null) {
            int lastDot = key.lastIndexOf('.');
            handler = lastDot >= 0 ? whitelist.get(key.substring(lastDot + 1)) : null;
        }
        return Optional.ofNullable(handler);
    }

    /**
     * 白名单是否为空 (无任何可执行处理器)。
     *
     * @return true 表示无任何注册处理器
     */
    public boolean isEmpty() {
        return whitelist.isEmpty();
    }

    /**
     * 白名单内全部可用 key (错误消息与运维排查用)。
     *
     * @return 不可修改的 key 集合
     */
    public Set<String> registeredHandlers() {
        return whitelist.keySet();
    }

    /**
     * 校验处理器方法名是否落在约定集合内 (空白按默认 {@code execute} 处理)。
     *
     * @param handlerMethod 任务表配置的处理器方法名
     * @return true 表示允许执行
     */
    public boolean isAllowedMethod(String handlerMethod) {
        String method = handlerMethod == null ? "" : handlerMethod.trim();
        return ALLOWED_HANDLER_METHODS.contains(method);
    }

    /**
     * 在独立事务中调用处理器。
     * <p>
     * 用 REQUIRES_NEW 与调度侧的执行记录事务隔离: 处理器抛异常时只回滚自身业务写入,
     * 不会把调度服务的事务标记为 rollback-only, 使 FAILED 执行记录仍能落库。
     * </p>
     *
     * @param handler 白名单内的处理器
     * @param task    任务配置
     * @return 处理器返回内容
     * @throws Exception 处理器执行失败
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String invoke(ScheduledTaskHandler handler, ScrmScheduledTaskEntity task) throws Exception {
        return handler.handle(task);
    }
}
