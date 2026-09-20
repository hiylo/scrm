/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractReminderRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmContractReminderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

/**
 * SCRM 合同提醒数据访问层。
 * <p>
 * 提供按合同 ID 查询提醒、按状态与提醒日期扫描待发送提醒 (定时任务批量发送用),
 * 供 {@code ScrmContractService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmContractReminderRepository extends JpaRepository<ScrmContractReminderEntity, Long>,
        JpaSpecificationExecutor<ScrmContractReminderEntity> {

    /**
     * 按、状态与提醒日期查询提醒 (批量发送定时任务扫描)。
     *
     * @param status       状态 (PENDING)
     * @param reminderDate 提醒日期上限 (reminderDate 早于或等于此日期)
     * @return 待发送提醒列表
     */
    List<ScrmContractReminderEntity> findByStatusAndReminderDateLessThanEqual(String status, LocalDate reminderDate);
}
