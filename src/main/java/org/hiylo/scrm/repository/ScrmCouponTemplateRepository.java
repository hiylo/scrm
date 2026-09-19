/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponTemplateRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCouponTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM 优惠券模板数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCouponTemplateRepository extends JpaRepository<ScrmCouponTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmCouponTemplateEntity> {
}
