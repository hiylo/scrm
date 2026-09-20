/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerMergeViewVo.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.entity.ScrmCustomerMergeRecordEntity;

import java.util.List;

/**
 * 客户统一视图 VO。
 * <p>
 * 聚合主客户档案与该客户相关的合并历史记录, 供客户详情页展示统一视图。
 * 由 {@code ScrmCustomerMergeService.getCustomerMergeView} 组装返回。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerMergeViewVo {

    /** 主客户档案 */
    private ScrmCustomerDto customer;

    /** 该客户参与的合并记录列表 (作为主客户或被合并客户) */
    private List<ScrmCustomerMergeRecordEntity> mergeRecords;
}
