/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentMemberRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSegmentMemberEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户分群成员数据访问层。
 * <p>
 * 提供按分群 / 客户查询成员, 当前成员批量失效, 成员数统计等能力, 供
 * {@code ScrmSegmentService} 分群计算与成员管理使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSegmentMemberRepository extends JpaRepository<ScrmSegmentMemberEntity, Long>,
        JpaSpecificationExecutor<ScrmSegmentMemberEntity> {

    /**
     * 按与分群 ID 分页查询当前成员 (按加入时间倒序)。
     *
     * @param segmentId 分群 ID
     * @param pageable  分页参数
     * @return 成员分页结果
     */
    Page<ScrmSegmentMemberEntity> findBySegmentIdAndIsCurrentMemberTrueOrderByJoinedAtDesc(Long segmentId, Pageable pageable);

    /**
     * 按客户 ID 查询所有当前成员关系 (获取客户所在分群用)。
     *
     * @param customerId 客户 ID
     * @return 成员关系列表
     */
    List<ScrmSegmentMemberEntity> findByCustomerIdAndIsCurrentMemberTrue(Long customerId);

    /**
     * 按与分群 ID 查询所有当前成员 (计算 / 对比用)。
     *
     * @param segmentId 分群 ID
     * @return 成员列表
     */
    List<ScrmSegmentMemberEntity> findBySegmentIdAndIsCurrentMemberTrue(Long segmentId);

    /**
     * 按、分群 ID 与客户 ID 查询当前成员关系 (成员资格校验用)。
     *
     * @param segmentId 分群 ID
     * @param customerId 客户 ID
     * @return 成员关系 (可能为空)
     */
    Optional<ScrmSegmentMemberEntity> findBySegmentIdAndCustomerId(Long segmentId, Long customerId);

    /**
     * 按分群 ID 与客户 ID 集合批量查询成员关系 (分群计算批量查找已有成员用)。
     *
     * @param segmentId   分群 ID
     * @param customerIds 客户 ID 集合
     * @return 成员关系列表
     */
    List<ScrmSegmentMemberEntity> findBySegmentIdAndCustomerIdIn(Long segmentId, Collection<Long> customerIds);

    /**
     * 按分群 ID 集合批量查询当前成员 (分群重叠分析用)。
     *
     * @param segmentIds 分群 ID 集合
     * @return 当前成员列表
     */
    List<ScrmSegmentMemberEntity> findBySegmentIdInAndIsCurrentMemberTrue(Collection<Long> segmentIds);

    /**
     * 统计账号下指定分群的当前成员数。
     *
     * @param segmentId 分群 ID
     * @return 当前成员数
     */
    long countBySegmentIdAndIsCurrentMemberTrue(Long segmentId);

    /**
     * 批量将指定分群的当前成员标记为已离开 (动态分群重新计算前清场用)。
     * <p>仅对 source=AUTO 的成员生效, 保留手动添加成员。</p>
     *
     * @param segmentId 分群 ID
     * @param leftAt    离开时间
     * @return 受影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmSegmentMemberEntity m SET m.isCurrentMember = false, m.leftAt = :leftAt WHERE "
                          + "m.segmentId = :segmentId AND m.isCurrentMember = true AND m.source = 'AUTO'")
    int markAutoMembersLeft(
                            @Param("segmentId") Long segmentId,
                            @Param("leftAt") LocalDateTime leftAt);

    /**
     * 统计账号下被至少一个分群覆盖的独立客户数 (总覆盖客户用)。
     *
     * @return 独立客户数
     */
    @Query(value = "SELECT COUNT(DISTINCT m.customerId) FROM ScrmSegmentMemberEntity m WHERE m.isCurrentMember ="
                          + "true")
    long countDistinctCurrentMemberCustomers();
}
