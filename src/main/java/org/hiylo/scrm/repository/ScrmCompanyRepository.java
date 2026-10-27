/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompanyRepository.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 公司主体数据访问层。
 * <p>
 * 提供按归属账号（数据隔离）/ 信用代码 / 服务状态查询能力；模糊检索与多条件
 * 组合由 {@link JpaSpecificationExecutor} 在服务层构建。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmCompanyRepository
        extends JpaRepository<ScrmCompanyEntity, Long>,
                JpaSpecificationExecutor<ScrmCompanyEntity> {

    /**
     * 按归属账号分页查询（数据隔离入口）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param pageable       分页参数
     * @return 公司分页
     */
    Page<ScrmCompanyEntity> findByOwnerAccountId(Long ownerAccountId, Pageable pageable);

    /**
     * 按统一社会信用代码精确查询（同一归属内唯一）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param creditCode     统一社会信用代码
     * @return 公司（可能为空）
     */
    Optional<ScrmCompanyEntity> findByOwnerAccountIdAndCreditCode(Long ownerAccountId, String creditCode);

    /**
     * 判断同一归属下信用代码是否已存在（创建/更新时的唯一性校验）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param creditCode     统一社会信用代码
     * @return 是否存在
     */
    boolean existsByOwnerAccountIdAndCreditCode(Long ownerAccountId, String creditCode);

    /**
     * 按企业名称模糊匹配（候选匹配用，候选公司维度）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param keyword        名称关键词
     * @return 候选公司列表
     */
    List<ScrmCompanyEntity> findByOwnerAccountIdAndCompanyNameContainingIgnoreCase(
            Long ownerAccountId, String keyword);

    /**
     * 查询同一归属下全部公司（候选对比用，数据量小）。
     *
     * @param ownerAccountId 归属账号 ID
     * @return 公司列表
     */
    List<ScrmCompanyEntity> findByOwnerAccountId(Long ownerAccountId);

    /**
     * 按服务状态查询（续费/流失预警扫描）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param serviceStatus  服务状态
     * @return 公司列表
     */
    List<ScrmCompanyEntity> findByOwnerAccountIdAndServiceStatus(Long ownerAccountId, String serviceStatus);

    /**
     * 按服务结束日期区间查询（续费预警：N 天内到期）。
     *
     * @param ownerAccountId 归属账号 ID
     * @param from           起始日期（含）
     * @param to             结束日期（含）
     * @return 公司列表
     */
    List<ScrmCompanyEntity> findByOwnerAccountIdAndServiceEndDateBetween(
            Long ownerAccountId, java.time.LocalDate from, java.time.LocalDate to);

    /**
     * 查询某会计负责的全部主体 ID（按会计筛选业务数据时下推 IN 条件）。
     * <p>
     * 只取 ID 列，不加载实体，避免为了过滤而把整批公司读进内存。
     * </p>
     *
     * @param accountantId 负责会计的用户 ID
     * @return 公司主体 ID 列表（无则空集合）
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT c.id FROM ScrmCompanyEntity c WHERE c.accountantId = :accountantId")
    java.util.List<Long> findIdsByAccountantId(@org.springframework.data.repository.query.Param("accountantId") Long accountantId);

    /**
     * 查询某会计负责的全部主体（批量指派 / 工作台列表用）。
     *
     * @param accountantId 负责会计用户 ID
     * @return 公司列表（按创建时间倒序）
     */
    List<ScrmCompanyEntity> findByAccountantIdOrderByCreateTimeDesc(Long accountantId);

    /**
     * 统计某会计负责的主体数量（工作量视图）。
     *
     * @param accountantId 负责会计用户 ID
     * @return 主体数量
     */
    long countByAccountantId(Long accountantId);

    /**
     * 按会计分组统计主体数量（主管查看全部会计工作量）。
     * <p>
     * 返回 {@code [accountantId, count]} 二元组，未指派（accountant_id 为空）的不计入。
     * </p>
     *
     * @return 分组统计结果
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT c.accountantId, COUNT(c) FROM ScrmCompanyEntity c "
                    + "WHERE c.accountantId IS NOT NULL GROUP BY c.accountantId")
    java.util.List<Object[]> countGroupedByAccountant();
}
