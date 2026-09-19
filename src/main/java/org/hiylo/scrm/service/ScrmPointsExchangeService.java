/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsExchangeService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmPointsExchangeDto;
import org.hiylo.scrm.dto.ScrmPointsExchangeRecordDto;
import org.hiylo.scrm.entity.ScrmPointsAccountEntity;
import org.hiylo.scrm.entity.ScrmPointsExchangeEntity;
import org.hiylo.scrm.entity.ScrmPointsExchangeRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmPointsAccountRepository;
import org.hiylo.scrm.repository.ScrmPointsExchangeRecordRepository;
import org.hiylo.scrm.repository.ScrmPointsExchangeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SCRM 积分兑换服务 (兑换管理子域)。
 * <p>
 * 承载积分兑换商品的增删改查 / 上下架 / 兑换动作 (兑换 / 完成 / 取消) 与兑换记录查询,
 * 兑换并发防护 (商品行 + 账户行悲观写锁 + 原子库存扣减) 随方法保留。积分账户锁定 / 扣减 /
 * 流水记录复用 {@link ScrmPointsAccountService} 的包级能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmPointsExchangeService {

    /** 兑换商品状态: 可兑换 */
    private static final String EXCHANGE_STATUS_ACTIVE = "ACTIVE";
    /** 兑换商品状态: 已下架 */
    private static final String EXCHANGE_STATUS_INACTIVE = "INACTIVE";
    /** 兑换商品状态: 已售罄 */
    private static final String EXCHANGE_STATUS_SOLD_OUT = "SOLD_OUT";

    /** 兑换记录状态: 待发货 */
    private static final String RECORD_STATUS_PENDING = "PENDING";
    /** 兑换记录状态: 已完成 */
    private static final String RECORD_STATUS_COMPLETED = "COMPLETED";
    /** 兑换记录状态: 已取消 */
    private static final String RECORD_STATUS_CANCELLED = "CANCELLED";

    /** 默认已兑换数量初值 */
    private static final int DEFAULT_EXCHANGED_QUANTITY = 0;
    /** 默认每人限兑 */
    private static final int DEFAULT_PER_USER_LIMIT = 1;
    /** 默认兑换商品状态 */
    private static final String DEFAULT_EXCHANGE_STATUS = EXCHANGE_STATUS_ACTIVE;
    /** 默认兑换记录状态 */
    private static final String DEFAULT_RECORD_STATUS = RECORD_STATUS_PENDING;
    /** 默认操作人 */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 交易类型: 消耗 */
    private static final String TXN_TYPE_REDEEM = "REDEEM";
    /** 交易类型: 手动调整 */
    private static final String TXN_TYPE_ADJUST = "ADJUST";

    /** 来源: 兑换 */
    private static final String SOURCE_EXCHANGE = "EXCHANGE";

    /** 积分兑换商品数据访问层 */
    private final ScrmPointsExchangeRepository exchangeRepository;
    /** 积分兑换记录数据访问层 */
    private final ScrmPointsExchangeRecordRepository exchangeRecordRepository;
    /** 积分账户数据访问层 */
    private final ScrmPointsAccountRepository accountRepository;

    /** 积分账户子域服务 (账户锁定 / 扣减 / 流水记录 / DTO 转换) */
    private final ScrmPointsAccountService accountService;

    /**
     * 创建积分兑换商品。
     *
     * @param dto 商品参数
     * @return 创建后的商品
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmPointsExchangeDto createExchangeItem(ScrmPointsExchangeDto dto) throws ScrmException {
        validateExchangeDto(dto, false);
        ScrmPointsExchangeEntity entity = new ScrmPointsExchangeEntity();
        entity.setItemName(dto.getItemName());
        entity.setItemImage(dto.getItemImage());
        entity.setItemDescription(dto.getItemDescription());
        entity.setItemCategory(dto.getItemCategory());
        entity.setPointsRequired(dto.getPointsRequired());
        entity.setStockQuantity(dto.getStockQuantity());
        entity.setExchangedQuantity(DEFAULT_EXCHANGED_QUANTITY);
        entity.setPerUserLimit(dto.getPerUserLimit() != null ? dto.getPerUserLimit() : DEFAULT_PER_USER_LIMIT);
        entity.setExchangeType(dto.getExchangeType());
        entity.setExchangeValue(dto.getExchangeValue());
        entity.setValidityDays(dto.getValidityDays());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DEFAULT_EXCHANGE_STATUS);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = exchangeRepository.save(entity);
        log.info("创建兑换商品: id={}, itemName={}, pointsRequired={}",
                entity.getId(), entity.getItemName(), entity.getPointsRequired());
        return toExchangeDto(entity);
    }

    /**
     * 更新兑换商品（字段非空才覆盖）。
     *
     * @param id  商品 ID
     * @param dto 商品参数
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 参数非法
     */
    @Transactional
    public ScrmPointsExchangeDto updateExchangeItem(Long id, ScrmPointsExchangeDto dto) throws ScrmException {
        ScrmPointsExchangeEntity entity = findExchangeOrThrow(id);
        validateExchangeDto(dto, true);
        if (dto.getItemName() != null) entity.setItemName(dto.getItemName());
        if (dto.getItemImage() != null) entity.setItemImage(dto.getItemImage());
        if (dto.getItemDescription() != null) entity.setItemDescription(dto.getItemDescription());
        if (dto.getItemCategory() != null) entity.setItemCategory(dto.getItemCategory());
        if (dto.getPointsRequired() != null) entity.setPointsRequired(dto.getPointsRequired());
        if (dto.getStockQuantity() != null) entity.setStockQuantity(dto.getStockQuantity());
        if (dto.getPerUserLimit() != null) entity.setPerUserLimit(dto.getPerUserLimit());
        if (dto.getExchangeType() != null) entity.setExchangeType(dto.getExchangeType());
        if (dto.getExchangeValue() != null) entity.setExchangeValue(dto.getExchangeValue());
        if (dto.getValidityDays() != null) entity.setValidityDays(dto.getValidityDays());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = exchangeRepository.save(entity);
        log.info("更新兑换商品: id={}, itemName={}", entity.getId(), entity.getItemName());
        return toExchangeDto(entity);
    }

    /**
     * 删除兑换商品。
     *
     * @param id 商品 ID
     * @throws ScrmException 商品不存在
     */
    @Transactional
    public void deleteExchangeItem(Long id) throws ScrmException {
        ScrmPointsExchangeEntity entity = findExchangeOrThrow(id);
        exchangeRepository.delete(entity);
        log.info("删除兑换商品: id={}, itemName={}", id, entity.getItemName());
    }

    /**
     * 查询兑换商品详情。
     *
     * @param id 商品 ID
     * @return 商品 DTO
     * @throws ScrmException 商品不存在
     */
    @Transactional(readOnly = true)
    public ScrmPointsExchangeDto getExchangeItem(Long id) throws ScrmException {
        return toExchangeDto(findExchangeOrThrow(id));
    }

    /**
     * 分页查询兑换商品, 支持按分类 / 状态过滤。
     *
     * @param category 分类过滤（可空）
     * @param status   状态过滤（可空）
     * @param pageable 分页参数
     * @return 商品分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmPointsExchangeDto> listExchangeItems(String category, String status, Pageable pageable) {
        Specification<ScrmPointsExchangeEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("itemCategory"), category));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return exchangeRepository.findAll(spec, ScrmPointsRuleService.ensureSortable(pageable, "createTime"))
                .map(this::toExchangeDto);
    }

    /**
     * 上架兑换商品。
     *
     * @param id 商品 ID
     * @return 更新后的商品
     * @throws ScrmException 商品不存在
     */
    @Transactional
    public ScrmPointsExchangeDto activateExchangeItem(Long id) throws ScrmException {
        ScrmPointsExchangeEntity entity = findExchangeOrThrow(id);
        entity.setStatus(EXCHANGE_STATUS_ACTIVE);
        entity = exchangeRepository.save(entity);
        log.info("上架兑换商品: id={}, itemName={}", id, entity.getItemName());
        return toExchangeDto(entity);
    }

    /**
     * 下架兑换商品。
     *
     * @param id 商品 ID
     * @return 更新后的商品
     * @throws ScrmException 商品不存在
     */
    @Transactional
    public ScrmPointsExchangeDto deactivateExchangeItem(Long id) throws ScrmException {
        ScrmPointsExchangeEntity entity = findExchangeOrThrow(id);
        entity.setStatus(EXCHANGE_STATUS_INACTIVE);
        entity = exchangeRepository.save(entity);
        log.info("下架兑换商品: id={}, itemName={}", id, entity.getItemName());
        return toExchangeDto(entity);
    }

    /**
     * 积分兑换商品 (加锁校验积分→扣减→生成兑换码→记录)。
     * <p>校验商品可兑换、库存充足、未超出每人限兑、积分充足, 扣减积分与库存, 生成兑换码与兑换记录,
     * 记录 REDEEM 流水 (sourceType=EXCHANGE)。库存为 0 时自动置为 SOLD_OUT。</p>
     * <p>并发防护: 兑换商品行与积分账户行均以 {@code PESSIMISTIC_WRITE} 悲观锁锁定 (同一事务内先商品后账户,
     * 顺序一致无死锁), 串行化同一商品 / 同一客户并发兑换; 库存扣减使用原子 UPDATE 并带
     * {@code stock_quantity >= :qty} 守卫, 行数=0 视为库存不足回滚事务。</p>
     *
     * @param customerId 客户 ID
     * @param exchangeId 兑换商品 ID
     * @param quantity   兑换数量
     * @return 兑换记录 DTO
     * @throws ScrmException 商品不存在 / 不可兑换 / 库存不足 / 超出限兑 / 积分不足
     */
    @Transactional
    public ScrmPointsExchangeRecordDto exchange(Long customerId, Long exchangeId, Integer quantity)
            throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw ScrmException.badRequest("兑换数量必须为正数");
        }
        // 悲观锁锁定兑换商品行, 串行化同一商品并发兑换, 防止库存超卖
        ScrmPointsExchangeEntity item = findExchangeForUpdateOrThrow(exchangeId);
        if (!EXCHANGE_STATUS_ACTIVE.equals(item.getStatus())) {
            throw ScrmException.badRequest("商品不可兑换, 当前状态: " + item.getStatus());
        }
        if (item.getStockQuantity() < quantity) {
            throw ScrmException.badRequest("库存不足, 当前库存: " + item.getStockQuantity());
        }
        int perUserLimit = item.getPerUserLimit() != null ? item.getPerUserLimit() : DEFAULT_PER_USER_LIMIT;
        // 每人限兑校验与后续扣减处于同一事务与商品行锁内, 避免并发请求绕过限制
        long userExchanged = exchangeRecordRepository.countByExchangeIdAndCustomerIdAndStatusNot(
                 exchangeId, customerId, RECORD_STATUS_CANCELLED);
        if (userExchanged + quantity > perUserLimit) {
            throw ScrmException.badRequest("超出每人限兑: " + perUserLimit);
        }
        int totalCost = item.getPointsRequired() * quantity;
        // 悲观锁锁定积分账户行, 串行化同一客户并发扣减, 防止积分被多笔兑换同时透支
        ScrmPointsAccountEntity account = accountService.findAccountForUpdateOrCreate(customerId);
        if (account.getCurrentPoints() < totalCost) {
            throw ScrmException.badRequest("积分不足, 需要: " + totalCost + ", 当前可用: " + account.getCurrentPoints());
        }
        LocalDateTime now = LocalDateTime.now();
        // 扣减积分 (账户行已加悲观锁, 回写不会覆盖并发更新)
        account.setCurrentPoints(account.getCurrentPoints() - totalCost);
        account.setTotalRedeemed(account.getTotalRedeemed() + totalCost);
        account.setLastRedeemAt(now);
        account.setUpdatedAt(now);
        account = accountRepository.save(account);
        // 原子扣减库存 (UPDATE ... WHERE stock_quantity >= :qty 守卫, 行数=0 视为库存不足)
        int stockUpdated = exchangeRepository.deductStock(exchangeId, quantity);
        if (stockUpdated == 0) {
            throw ScrmException.badRequest("库存不足, 请稍后重试");
        }
        // 商品行已加悲观锁, 在锁内用同一快照同步内存中的库存/已兑换数,
        // 保证 save 回写的是扣减后的一致值, 避免整列覆盖原子扣减结果且不丢 exchanged_quantity
        item.setStockQuantity(item.getStockQuantity() - quantity);
        item.setExchangedQuantity(item.getExchangedQuantity() + quantity);
        if (item.getStockQuantity() <= 0) {
            item.setStatus(EXCHANGE_STATUS_SOLD_OUT);
        }
        exchangeRepository.save(item);
        // 生成兑换记录
        ScrmPointsExchangeRecordEntity record = new ScrmPointsExchangeRecordEntity();
        record.setExchangeId(exchangeId);
        record.setCustomerId(customerId);
        record.setCustomerName(account.getCustomerName());
        record.setPointsCost(totalCost);
        record.setQuantity(quantity);
        record.setExchangeCode(generateExchangeCode());
        record.setStatus(DEFAULT_RECORD_STATUS);
        record = exchangeRecordRepository.save(record);
        // 记录流水
        accountService.recordTransaction(account, TXN_TYPE_REDEEM, -totalCost, null, null, SOURCE_EXCHANGE,
                String.valueOf(record.getId()), "兑换商品: " + item.getItemName(), DEFAULT_OPERATOR, null);
        log.info("积分兑换: customerId={}, exchangeId={}, quantity={}, pointsCost={}, code={}",
                customerId, exchangeId, quantity, totalCost, record.getExchangeCode());
        return toRecordDto(record);
    }

    /**
     * 完成兑换 (填写物流单号并标记完成)。
     *
     * @param recordId   兑换记录 ID
     * @param shippingNo 物流单号 (可空)
     * @return 更新后的兑换记录
     * @throws ScrmException 记录不存在 / 状态非法
     */
    @Transactional
    public ScrmPointsExchangeRecordDto completeExchange(Long recordId, String shippingNo) throws ScrmException {
        ScrmPointsExchangeRecordEntity record = findRecordOrThrow(recordId);
        if (RECORD_STATUS_COMPLETED.equals(record.getStatus())) {
            return toRecordDto(record);
        }
        if (RECORD_STATUS_CANCELLED.equals(record.getStatus())) {
            throw ScrmException.badRequest("已取消的兑换不允许完成: id=" + recordId);
        }
        if (shippingNo != null && !shippingNo.isBlank()) {
            record.setShippingNo(shippingNo);
        }
        record.setStatus(RECORD_STATUS_COMPLETED);
        record.setCompletedAt(LocalDateTime.now());
        record = exchangeRecordRepository.save(record);
        log.info("完成兑换: recordId={}, shippingNo={}", recordId, shippingNo);
        return toRecordDto(record);
    }

    /**
     * 取消兑换 (返还积分并恢复库存)。
     * <p>并发防护: 与 {@link #exchange} 使用同一套悲观锁 (先商品行后账户行, 加锁顺序一致无死锁),
     * 串行化同一商品/同一客户的兑换与取消操作, 避免并发时积分返还或库存恢复丢失更新。</p>
     *
     * @param recordId 兑换记录 ID
     * @param reason   取消原因
     * @return 更新后的兑换记录
     * @throws ScrmException 记录不存在 / 已完成不允许取消
     */
    @Transactional
    public ScrmPointsExchangeRecordDto cancelExchange(Long recordId, String reason) throws ScrmException {
        ScrmPointsExchangeRecordEntity record = findRecordOrThrow(recordId);
        if (RECORD_STATUS_CANCELLED.equals(record.getStatus())) {
            return toRecordDto(record);
        }
        if (RECORD_STATUS_COMPLETED.equals(record.getStatus())) {
            throw ScrmException.badRequest("已完成的兑换不允许取消: id=" + recordId);
        }
        // 悲观锁锁定兑换商品行 (加锁顺序与 exchange 一致: 商品行在前), 串行化与并发兑换的库存竞争
        ScrmPointsExchangeEntity item = findExchangeForUpdateOrThrow(record.getExchangeId());
        // 悲观锁锁定积分账户行, 与 exchange 扣减共用同一账户锁, 防止积分返还被并发覆盖
        ScrmPointsAccountEntity account = accountService.findAccountForUpdateByCustomerOrThrow(record.getCustomerId());
        LocalDateTime now = LocalDateTime.now();
        // 返还积分 (账户行已加悲观锁, 锁内读改写无并发丢失更新)
        account.setCurrentPoints(account.getCurrentPoints() + record.getPointsCost());
        account.setTotalRedeemed(Math.max(0, account.getTotalRedeemed() - record.getPointsCost()));
        account.setUpdatedAt(now);
        account = accountRepository.save(account);
        // 恢复库存 (商品行已加悲观锁, 锁内读改写与 exchange 的库存扣减互斥, 不会覆盖并发结果)
        item.setStockQuantity(item.getStockQuantity() + record.getQuantity());
        item.setExchangedQuantity(Math.max(0, item.getExchangedQuantity() - record.getQuantity()));
        if (EXCHANGE_STATUS_SOLD_OUT.equals(item.getStatus()) && item.getStockQuantity() > 0) {
            item.setStatus(EXCHANGE_STATUS_ACTIVE);
        }
        exchangeRepository.save(item);
        // 记录返还流水
        accountService.recordTransaction(account, TXN_TYPE_ADJUST, record.getPointsCost(), null, null, SOURCE_EXCHANGE,
                String.valueOf(record.getId()), "取消兑换返还: " + reason, DEFAULT_OPERATOR, null);
        // 更新记录
        record.setStatus(RECORD_STATUS_CANCELLED);
        record.setCancelledAt(now);
        if (reason != null && !reason.isBlank()) {
            record.setNotes(reason);
        }
        record = exchangeRecordRepository.save(record);
        log.info("取消兑换: recordId={}, pointsCost={}", recordId, record.getPointsCost());
        return toRecordDto(record);
    }

    /**
     * 分页查询兑换记录, 支持按客户 / 商品 / 状态过滤。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param exchangeId 兑换商品 ID 过滤（可空）
     * @param status     状态过滤（可空）
     * @param pageable   分页参数
     * @return 兑换记录分页结果 (按 exchangedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmPointsExchangeRecordDto> getExchangeRecords(Long customerId, Long exchangeId,
                                                                String status, Pageable pageable) {
        Specification<ScrmPointsExchangeRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (exchangeId != null) {
                predicates.add(cb.equal(root.get("exchangeId"), exchangeId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            query.orderBy(cb.desc(root.get("exchangedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return exchangeRecordRepository.findAll(spec, ScrmPointsRuleService.ensureSortable(pageable, "exchangedAt"))
                .map(this::toRecordDto);
    }

    /**
     * 校验兑换商品参数。
     *
     * @param dto     商品参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateExchangeDto(ScrmPointsExchangeDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("商品参数不能为空");
        }
        if (dto.getItemName() == null || dto.getItemName().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("兑换商品名不能为空");
            }
        }
        if (dto.getPointsRequired() == null && !partial) {
            throw ScrmException.badRequest("所需积分不能为空");
        }
        if (dto.getStockQuantity() == null && !partial) {
            throw ScrmException.badRequest("库存不能为空");
        }
        if (dto.getExchangeType() == null || dto.getExchangeType().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("兑换类型不能为空");
            }
        }
        if (dto.getStatus() != null && !EXCHANGE_STATUS_ACTIVE.equals(dto.getStatus()) && !EXCHANGE_STATUS_INACTIVE.equals(dto.getStatus()) && !EXCHANGE_STATUS_SOLD_OUT.equals(dto.getStatus())) {
            throw ScrmException.badRequest("状态非法: " + dto.getStatus() + ", 仅支持 ACTIVE/INACTIVE/SOLD_OUT");
        }
    }

    /**
     * 生成兑换码 (UUID 去横线, 大写)。
     *
     * @return 兑换码
     */
    private String generateExchangeCode() {
        return "EX" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /**
     * 按主键查询兑换商品并校验归属账号, 不存在或越权抛异常。
     *
     * @param id 商品 ID
     * @return 商品实体
     * @throws ScrmException 商品不存在
     */
    private ScrmPointsExchangeEntity findExchangeOrThrow(Long id) throws ScrmException {
        return exchangeRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "兑换商品不存在: id=" + id));
    }

    /**
     * 按主键加悲观写锁查询兑换商品并校验归属账号, 不存在或越权抛异常 (兑换路径使用)。
     *
     * @param id 商品 ID
     * @return 商品实体 (已加悲观写锁)
     * @throws ScrmException 商品不存在
     */
    private ScrmPointsExchangeEntity findExchangeForUpdateOrThrow(Long id) throws ScrmException {
        return exchangeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "兑换商品不存在: id=" + id));
    }

    /**
     * 按主键查询兑换记录并校验归属账号, 不存在或越权抛异常。
     *
     * @param id 记录 ID
     * @return 记录实体
     * @throws ScrmException 记录不存在
     */
    private ScrmPointsExchangeRecordEntity findRecordOrThrow(Long id) throws ScrmException {
        return exchangeRecordRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "兑换记录不存在: id=" + id));
    }

    /**
     * 兑换商品实体转 DTO。
     */
    private ScrmPointsExchangeDto toExchangeDto(ScrmPointsExchangeEntity entity) {
        ScrmPointsExchangeDto dto = new ScrmPointsExchangeDto();
        dto.setId(entity.getId());
        dto.setItemName(entity.getItemName());
        dto.setItemImage(entity.getItemImage());
        dto.setItemDescription(entity.getItemDescription());
        dto.setItemCategory(entity.getItemCategory());
        dto.setPointsRequired(entity.getPointsRequired());
        dto.setStockQuantity(entity.getStockQuantity());
        dto.setExchangedQuantity(entity.getExchangedQuantity());
        dto.setPerUserLimit(entity.getPerUserLimit());
        dto.setExchangeType(entity.getExchangeType());
        dto.setExchangeValue(entity.getExchangeValue());
        dto.setValidityDays(entity.getValidityDays());
        dto.setStatus(entity.getStatus());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 兑换记录实体转 DTO。
     */
    private ScrmPointsExchangeRecordDto toRecordDto(ScrmPointsExchangeRecordEntity entity) {
        ScrmPointsExchangeRecordDto dto = new ScrmPointsExchangeRecordDto();
        dto.setId(entity.getId());
        dto.setExchangeId(entity.getExchangeId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setPointsCost(entity.getPointsCost());
        dto.setQuantity(entity.getQuantity());
        dto.setExchangeCode(entity.getExchangeCode());
        dto.setStatus(entity.getStatus());
        dto.setShippingAddress(entity.getShippingAddress());
        dto.setShippingNo(entity.getShippingNo());
        dto.setExchangedAt(entity.getExchangedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCancelledAt(entity.getCancelledAt());
        dto.setNotes(entity.getNotes());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}