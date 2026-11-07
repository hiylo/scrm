/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmCustomerContactRepository.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 客户联系方式数据访问层。
 * <p>
 * 联系方式按 {@code (ownerType, customerId)} 归属：
 * ownerType=CUSTOMER 指向正式客户，ownerType=PUBLIC_SEA 指向公海线索。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmCustomerContactRepository extends JpaRepository<ScrmCustomerContactEntity, Long> {

    /**
     * 查询某个归属对象的全部联系方式。
     *
     * @param ownerType  归属类型
     * @param customerId 归属对象 ID
     * @return 联系方式列表
     */
    List<ScrmCustomerContactEntity> findByOwnerTypeAndCustomerId(String ownerType, Long customerId);

    /**
     * 批量查询多个归属对象的联系方式（列表页一次性取回，避免 N+1）。
     *
     * @param ownerType   归属类型
     * @param customerIds 归属对象 ID 集合
     * @return 联系方式列表
     */
    List<ScrmCustomerContactEntity> findByOwnerTypeAndCustomerIdIn(String ownerType,
                                                                   Collection<Long> customerIds);

    /**
     * 按归属对象与号码精确查询（唯一约束对应的查询）。
     *
     * @param ownerType  归属类型
     * @param customerId 归属对象 ID
     * @param phone      号码
     * @return 联系方式（可能为空）
     */
    Optional<ScrmCustomerContactEntity> findByOwnerTypeAndCustomerIdAndPhone(String ownerType,
                                                                            Long customerId,
                                                                            String phone);

    /**
     * 按号码查询全部归属（跨客户/线索去重、撞单检测、来电匹配）。
     *
     * @param phone 规范化号码
     * @return 联系方式列表
     */
    List<ScrmCustomerContactEntity> findByPhone(String phone);

    /**
     * 统计某归属对象的联系方式数量。
     *
     * @param ownerType  归属类型
     * @param customerId 归属对象 ID
     * @return 数量
     */
    long countByOwnerTypeAndCustomerId(String ownerType, Long customerId);
}
