/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScheduledTaskHandler.java
 * Date : 2026-09-19 10:12:40
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;

/**
 * 可被统一任务调度中心调度的处理器接口。
 * <p>
 * {@code scrm_scheduled_task} 表通过 {@code ScrmTaskSchedulerController} 对用户开放, 其中的
 * {@code handlerClass} 字段属于用户可写数据。调度时<b>绝不</b>用
 * {@code Class.forName(handlerClass)} 反射实例化任意类 —— 那等于把任意代码执行权限交给任何能写该表的
 * 账号 (即便有 {@code @RequirePermission}, 仍是提权面)。
 * </p>
 * <p>
 * 因此约定: 只有实现本接口并注册为 Spring bean 的处理器才是白名单成员, 由
 * {@link ScheduledTaskHandlerRegistry} 启动时收集成 {@code bean 名 / 类名 → handler} 映射;
 * 调度执行时仅按白名单查找并调用 {@link #handle}, 未注册的 {@code handlerClass} 记为执行失败。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScheduledTaskHandler {

    /**
     * 执行一次调度任务。
     * <p>
     * 返回值写入执行记录的 {@code return_value} (超长会被截断), 建议返回简要结果说明或统计数字的字符串;
     * 需要落库的业务细节应由处理器自身的服务完成。
     * </p>
     *
     * @param task 任务配置实体 (只读, 含 taskCode / handlerClass / parameters 等)
     * @return 处理器返回内容, 可为 null
     * @throws Exception 处理器执行失败, 由调度侧记为 FAILED 执行结果
     */
    String handle(ScrmScheduledTaskEntity task) throws Exception;
}
