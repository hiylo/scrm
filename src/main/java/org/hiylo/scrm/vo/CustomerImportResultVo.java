/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerImportResultVo.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户批量导入结果 VO。
 * <p>
 * 描述一次导入的总行数、成功数、失败数与跳过数 (重复客户),
 * 同时携带失败行详情, 便于前端展示与用户排查。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerImportResultVo {

    /** 总行数 (不含表头) */
    private int totalCount;

    /** 成功导入数 */
    private int successCount;

    /** 失败数 (校验失败或创建异常) */
    private int failedCount;

    /** 跳过数 (重复客户, 不计入失败) */
    private int skippedCount;

    /** 失败行详情列表 */
    private List<FailedRow> failedRows;

    /**
     * 失败行详情。
     * <p>
     * 记录行号、失败原因与原始数据摘要, 用于前端定位与排查。
     * </p>
     *
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedRow {

        /** 行号 (从 2 开始, 1 为表头) */
        private int rowNumber;

        /** 失败原因 (如「平台类型为空」「平台客户UID为空」「客户已存在」) */
        private String reason;

        /** 原始数据摘要 (用于排查, JSON 字符串) */
        private String rowData;
    }
}
