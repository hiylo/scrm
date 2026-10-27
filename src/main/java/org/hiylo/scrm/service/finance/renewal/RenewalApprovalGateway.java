/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : RenewalApprovalGateway.java
 * Date : 2026/10/27 08:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.renewal;

import org.hiylo.scrm.entity.ScrmRenewalTaskEntity;

/**
 * 续费审批网关。
 * <p>
 * 续费域与审批域通过本接口解耦：续费服务只负责「需要审批」这一业务判断，
 * 由审批侧的实现决定如何创建实例、如何回填。
 * 这样避免 {@code ScrmRenewalService} 与 {@code ScrmApprovalService} 直接互相依赖形成环。
 * </p>
 * <p>
 * 实现须保证<b>幂等</b>：同一任务重复调用不应产生多个审批实例。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface RenewalApprovalGateway {

    /**
     * 为非标准续费（折扣/赠期）发起审批。
     * <p>
     * 找不到可用的 RENEWAL 审批流程时不得抛异常中断续费主流程，
     * 只记录告警并返回 null——续费任务本身仍应创建成功。
     * </p>
     *
     * @param task 已落库的续费任务（需已有 id）
     * @return 审批实例 ID；未发起或无需审批时返回 null
     */
    Long startApproval(ScrmRenewalTaskEntity task);
}
