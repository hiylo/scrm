/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmFinanceKnowledgeProperties.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模块③ 财税知识库增强配置。
 * <p>
 * 绑定 {@code scrm.finance-knowledge.*}: T06 模板批量导入与版本复核参数。
 * </p>
 * <ul>
 *   <li>kb-name - 财税知识库名称（默认「财税知识库」, 批量导入自动创建/复用）</li>
 *   <li>review-cycle-days - 默认复核周期天数（默认 180, 政策类信息建议 ≤180 天,
 *       knowledge-template.md 总则第 6 条）</li>
 *   <li>import-max-files - 单次批量导入文件数上限（默认 100）</li>
 * </ul>
 * <p>
 * 配置由 {@link FinanceKnowledgeConfig} 注册（沿用 D01/D04/D05 的
 * {@code @EnableConfigurationProperties} 装配模式）, 不修改 application.yml。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.finance-knowledge")
public class ScrmFinanceKnowledgeProperties {

    /** 财税知识库名称（默认「财税知识库」） */
    private String kbName = "财税知识库";

    /** 默认复核周期天数（默认 180, 政策类信息建议 ≤180 天） */
    private int reviewCycleDays = 180;

    /** 单次批量导入文件数上限（默认 100） */
    private int importMaxFiles = 100;
}
