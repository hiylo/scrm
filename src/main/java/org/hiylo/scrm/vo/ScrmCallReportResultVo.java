/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCallReportResultVo.java
 * Date : 2026/10/22 10:00:00
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
 * 通话批量上报结果 VO。
 * <p>
 * 返回本次批量上报的统计：新增 / 重复跳过 / 匹配到的客户数，
 * 以及每条的匹配结果（便于客户端展示「打给谁」）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmCallReportResultVo {

    /** 上报总数 */
    private Integer total;

    /** 新增记录数 */
    private Integer created;

    /** 重复跳过数（已存在） */
    private Integer duplicated;

    /** 匹配到客户的记录数 */
    private Integer matchedCustomer;

    /** 各条明细（与请求顺序一致） */
    private List<Item> items;

    /**
     * 单条结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** 是否新增 */
        private Boolean created;
        /** 是否重复 */
        private Boolean duplicated;
        /** 记录 ID（新增时有值） */
        private Long callRecordId;
        /** 匹配到的客户 ID（可为空） */
        private Long customerId;
        /** 匹配到的归属类型：CUSTOMER / PUBLIC_SEA（可为空） */
        private String customerType;
        /** 匹配到的客户昵称（可为空） */
        private String customerName;
    }
}
