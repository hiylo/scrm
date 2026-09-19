/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmServerApplication.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm;

import org.hiylo.scrm.id.sequence.annotation.EnableSequence;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SCRM 服务启动入口
 * <p>
 * 客户成功 CRM 单体应用, 聚焦企业微信渠道的账号健康、会话分析、内容生成与外呼编排。
 * 仅依赖本应用内配置, 不引入组件库或私有中间件。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableSequence
public class ScrmServerApplication {

    /**
     * 应用启动入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ScrmServerApplication.class, args);
    }
}
