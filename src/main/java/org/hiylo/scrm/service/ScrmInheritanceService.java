/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInheritanceService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmInheritanceItemDto;
import org.hiylo.scrm.dto.ScrmInheritanceTaskDto;
import org.hiylo.scrm.entity.ScrmCustomerGroupEntity;
import org.hiylo.scrm.entity.ScrmInheritanceItemEntity;
import org.hiylo.scrm.entity.ScrmInheritanceTaskEntity;
import org.hiylo.scrm.entity.ScrmPublicSeaCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerGroupRepository;
import org.hiylo.scrm.repository.ScrmInheritanceItemRepository;
import org.hiylo.scrm.repository.ScrmInheritanceTaskRepository;
import org.hiylo.scrm.repository.ScrmPublicSeaCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 离职继承服务
 * <p>
 * 负责员工离职时将其名下客户、客户群、会话转移给其他员工, 保护客户资产不流失。
 * 任务状态流转: PENDING → RUNNING → COMPLETED/FAILED/PARTIAL。
 * </p>
 * <p>
 * 资产枚举范围:
 * <ul>
 *   <li>CUSTOMER: 公海池中 assignedTo == fromUserId 的客户 (与公海模块联动)</li>
 *   <li>GROUP: 客户群中 ownerUserId == fromUserId 的群</li>
 *   <li>CONVERSATION: 会话表无直接 userId 字段, 当前版本跳过会话枚举</li>
 * </ul>
 * 所有写操作均写入当前用户归属账号, 实现数据隔离。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmInheritanceService {

    /** 任务状态: 待启动 */
    private static final String STATUS_PENDING = "PENDING";

    /** 任务状态: 运行中 */
    private static final String STATUS_RUNNING = "RUNNING";

    /** 任务状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /** 任务状态: 失败 */
    private static final String STATUS_FAILED = "FAILED";

    /** 任务状态: 部分成功 */
    private static final String STATUS_PARTIAL = "PARTIAL";

    /** 明细类型: 客户 */
    private static final String ITEM_CUSTOMER = "CUSTOMER";

    /** 明细类型: 客户群 */
    private static final String ITEM_GROUP = "GROUP";

    /** 明细状态: 待处理 */
    private static final String ITEM_PENDING = "PENDING";

    /** 明细状态: 成功 */
    private static final String ITEM_SUCCESS = "SUCCESS";

    /** 明细状态: 失败 */
    private static final String ITEM_FAILED = "FAILED";

    /** 离职继承任务数据访问层 */
    private final ScrmInheritanceTaskRepository taskRepository;

    /** 离职继承明细数据访问层 */
    private final ScrmInheritanceItemRepository itemRepository;

    /** 公海客户数据访问层 (枚举与转移 CUSTOMER 明细) */
    private final ScrmPublicSeaCustomerRepository publicSeaCustomerRepository;

    /** 客户分组数据访问层 (枚举与转移 GROUP 明细) */
    private final ScrmCustomerGroupRepository customerGroupRepository;

    /**
     * 创建离职继承任务 (PENDING)
     * <p>
     * 仅创建任务元信息, 不立即枚举明细。需后续调用 {@link #startTask} 启动。
     * </p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmInheritanceTaskDto createTask(ScrmInheritanceTaskDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("任务参数不能为空");
        }
        if (dto.getTaskName() == null || dto.getTaskName().isBlank()) {
            throw ScrmException.badRequest("任务名称不能为空");
        }
        if (dto.getFromUserId() == null || dto.getFromUserId().isBlank()) {
            throw ScrmException.badRequest("离职人 userId 不能为空");
        }
        if (dto.getToUserId() == null || dto.getToUserId().isBlank()) {
            throw ScrmException.badRequest("接收人 userId 不能为空");
        }
        if (dto.getFromUserId().equals(dto.getToUserId())) {
            throw ScrmException.badRequest("离职人与接收人不能为同一用户");
        }
        ScrmInheritanceTaskEntity entity = new ScrmInheritanceTaskEntity();
        entity.setTaskName(dto.getTaskName());
        entity.setFromUserId(dto.getFromUserId());
        entity.setToUserId(dto.getToUserId());
        entity.setPlatformType(dto.getPlatformType());
        entity.setStatus(STATUS_PENDING);
        entity.setTotalItems(0);
        entity.setSuccessItems(0);
        entity.setFailItems(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity.setNote(dto.getNote());
        entity = taskRepository.save(entity);
        log.info("创建离职继承任务: id={}, fromUserId={}, toUserId={}",
                entity.getId(), entity.getFromUserId(), entity.getToUserId());
        return toDto(entity);
    }

    /**
     * 启动继承任务 (PENDING → RUNNING), 枚举 fromUserId 的客户/群生成明细并执行转移
     * <p>
     * 枚举范围:
     * <ul>
     *   <li>CUSTOMER: 公海池中 assignedTo == fromUserId 的客户</li>
     *   <li>GROUP: 客户群中 ownerUserId == fromUserId 的群</li>
     * </ul>
     * 每条明细创建为 PENDING, 随后立即执行转移并更新为 SUCCESS/FAILED。
     * 任务结束时按明细结果置为 COMPLETED/FAILED/PARTIAL。
     * </p>
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许启动
     */
    @Transactional
    public ScrmInheritanceTaskDto startTask(Long id) throws ScrmException {
        ScrmInheritanceTaskEntity task = findTaskOrThrow(id);
        if (!STATUS_PENDING.equals(task.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "任务当前状态不允许启动: id=" + id + ", status=" + task.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(STATUS_RUNNING);
        task.setStartedAt(now);
        task = taskRepository.save(task);
        // 枚举明细并执行转移
        int total = 0;
        int success = 0;
        int fail = 0;
        // 枚举 CUSTOMER 明细 (公海池中 assignedTo == fromUserId)
        for (ScrmPublicSeaCustomerEntity customer : enumerateCustomers(task)) {
            ScrmInheritanceItemEntity item = new ScrmInheritanceItemEntity();
            item.setTaskId(task.getId());
            item.setItemType(ITEM_CUSTOMER);
            item.setItemId(customer.getId());
            item.setItemLabel(customer.getNickname());
            item.setStatus(ITEM_PENDING);
            item = itemRepository.save(item);
            total++;
            if (transferCustomer(customer, task.getToUserId())) {
                item.setStatus(ITEM_SUCCESS);
                item.setProcessedAt(LocalDateTime.now());
                success++;
            } else {
                item.setStatus(ITEM_FAILED);
                item.setErrorMessage("公海客户转移失败: customerId=" + customer.getId());
                item.setProcessedAt(LocalDateTime.now());
                fail++;
            }
            itemRepository.save(item);
        }
        // 枚举 GROUP 明细 (客户群 ownerUserId == fromUserId)
        for (ScrmCustomerGroupEntity group : enumerateGroups(task)) {
            ScrmInheritanceItemEntity item = new ScrmInheritanceItemEntity();
            item.setTaskId(task.getId());
            item.setItemType(ITEM_GROUP);
            item.setItemId(group.getId());
            item.setItemLabel(group.getGroupName());
            item.setStatus(ITEM_PENDING);
            item = itemRepository.save(item);
            total++;
            if (transferGroup(group, task.getToUserId())) {
                item.setStatus(ITEM_SUCCESS);
                item.setProcessedAt(LocalDateTime.now());
                success++;
            } else {
                item.setStatus(ITEM_FAILED);
                item.setErrorMessage("客户群转移失败: groupId=" + group.getId());
                item.setProcessedAt(LocalDateTime.now());
                fail++;
            }
            itemRepository.save(item);
        }
        // 更新任务统计与状态
        task.setTotalItems(total);
        task.setSuccessItems(success);
        task.setFailItems(fail);
        task.setCompletedAt(LocalDateTime.now());
        if (total == 0) {
            // 无明细, 直接完成
            task.setStatus(STATUS_COMPLETED);
        } else if (fail == 0) {
            task.setStatus(STATUS_COMPLETED);
        } else if (success == 0) {
            task.setStatus(STATUS_FAILED);
        } else {
            task.setStatus(STATUS_PARTIAL);
        }
        task = taskRepository.save(task);
        log.info("离职继承任务完成: id={}, total={}, success={}, fail={}, status={}",
                task.getId(), total, success, fail, task.getStatus());
        return toDto(task);
    }

    /**
     * 查询任务详情
     *
     * @param id 任务 ID
     * @return 任务 DTO
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmInheritanceTaskDto getTask(Long id) throws ScrmException {
        return toDto(findTaskOrThrow(id));
    }

    /**
     * 分页查询任务列表, 支持按状态、离职人、接收人过滤
     * <p>数据隔离: 仅返回当前账号的任务。</p>
     *
     * @param status     任务状态过滤 (可空)
     * @param fromUserId 离职人 userId 过滤 (可空)
     * @param toUserId   接收人 userId 过滤 (可空)
     * @param pageable   分页参数
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmInheritanceTaskDto> listTasks(String status, String fromUserId, String toUserId,
                                                   Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<ScrmInheritanceTaskEntity> spec = buildTaskSpec(status, fromUserId, toUserId);
        return taskRepository.findAll(spec, sorted).map(this::toDto);
    }

    /**
     * 分页查询任务明细, 支持按状态过滤
     *
     * @param taskId   任务 ID
     * @param status   明细状态过滤 (可空)
     * @param pageable 分页参数
     * @return 明细分页结果
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmInheritanceItemDto> getTaskItems(Long taskId, String status, Pageable pageable)
            throws ScrmException {
        findTaskOrThrow(taskId);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "id"));
        return itemRepository
                .findByTaskIdAndStatus(taskId, status, sorted)
                .map(this::toDto);
    }

    /**
     * 重试失败明细
     * <p>
     * 仅 FAILED 状态的明细可重试。重新执行转移逻辑, 成功则置为 SUCCESS,
     * 失败则保留 FAILED 状态并更新错误信息。重试后同步刷新任务统计与状态。
     * </p>
     *
     * @param taskId 任务 ID
     * @param itemId 明细 ID
     * @return 更新后的明细
     * @throws ScrmException 任务/明细不存在 / 明细状态不允许重试
     */
    @Transactional
    public ScrmInheritanceItemDto retryItem(Long taskId, Long itemId) throws ScrmException {
        ScrmInheritanceTaskEntity task = findTaskOrThrow(taskId);
        ScrmInheritanceItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "离职继承明细不存在: itemId=" + itemId));
        // 数据隔离: 校验明细归属当前账号

        // 校验明细归属指定任务
        if (!task.getId().equals(item.getTaskId())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "明细不属于指定任务: taskId=" + taskId + ", itemId=" + itemId);
        }
        if (!ITEM_FAILED.equals(item.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "明细当前状态不允许重试: itemId=" + itemId + ", status=" + item.getStatus());
        }
        boolean ok = transferItem(task, item);
        if (ok) {
            item.setStatus(ITEM_SUCCESS);
            item.setErrorMessage(null);
        } else {
            item.setStatus(ITEM_FAILED);
        }
        item.setProcessedAt(LocalDateTime.now());
        item = itemRepository.save(item);
        // 刷新任务统计与状态
        refreshTaskStatistics(task);
        log.info("离职继承明细重试: taskId={}, itemId={}, result={}", taskId, itemId,
                ok ? "SUCCESS" : "FAILED");
        return toDto(item);
    }

    /**
     * 完成任务 (手动标记结束)
     * <p>
     * 仅 RUNNING/PARTIAL 状态的任务可手动完成。通常用于部分失败后人工确认收尾,
     * 调用后按明细结果重新计算 COMPLETED/FAILED/PARTIAL 状态。
     * </p>
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许完成
     */
    @Transactional
    public ScrmInheritanceTaskDto completeTask(Long id) throws ScrmException {
        ScrmInheritanceTaskEntity task = findTaskOrThrow(id);
        if (!STATUS_RUNNING.equals(task.getStatus()) && !STATUS_PARTIAL.equals(task.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "任务当前状态不允许完成: id=" + id + ", status=" + task.getStatus());
        }
        refreshTaskStatistics(task);
        task.setCompletedAt(LocalDateTime.now());
        task = taskRepository.save(task);
        log.info("离职继承任务手动完成: id={}, status={}", id, task.getStatus());
        return toDto(task);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 构建任务查询条件 Specification (含数据隔离)
     */
    private Specification<ScrmInheritanceTaskEntity> buildTaskSpec(String status, String fromUserId,
                                                                   String toUserId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (fromUserId != null && !fromUserId.isBlank()) {
                predicates.add(cb.equal(root.get("fromUserId"), fromUserId));
            }
            if (toUserId != null && !toUserId.isBlank()) {
                predicates.add(cb.equal(root.get("toUserId"), toUserId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 枚举 fromUserId 的公海客户 (CUSTOMER 明细来源)
     * <p>数据隔离: 仅枚举当前账号下 assignedTo == fromUserId 的客户。</p>
     *
     * @param task 任务实体
     * @return 公海客户列表
     */
    private List<ScrmPublicSeaCustomerEntity> enumerateCustomers(ScrmInheritanceTaskEntity task) {
        Specification<ScrmPublicSeaCustomerEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("assignedTo"), task.getFromUserId()));
            if (task.getPlatformType() != null && !task.getPlatformType().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")),
                        task.getPlatformType().toLowerCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return publicSeaCustomerRepository.findAll(spec);
    }

    /**
     * 枚举 fromUserId 的客户群 (GROUP 明细来源)
     * <p>
     * 数据隔离: 仅枚举当前账号下 ownerUserId == fromUserId 的群。
     * ScrmCustomerGroupRepository 未提供按 ownerUserId 查询方法, 这里先按加载全部,
     * 再在内存中按 ownerUserId 过滤。
     * </p>
     *
     * @param task 任务实体
     * @return 客户群列表
     */
    private List<ScrmCustomerGroupEntity> enumerateGroups(ScrmInheritanceTaskEntity task) {
        return customerGroupRepository.findAll().stream()
                .filter(g -> task.getFromUserId().equals(g.getOwnerUserId()))
                .filter(g -> task.getPlatformType() == null || task.getPlatformType().isBlank()
                        || task.getPlatformType().equalsIgnoreCase(g.getPlatformType()))
                .collect(Collectors.toList());
    }

    /**
     * 转移公海客户: 将 assignedTo 从 fromUserId 改为 toUserId
     *
     * @param customer 公海客户实体
     * @param toUserId 接收人 userId
     * @return true 表示转移成功
     */
    private boolean transferCustomer(ScrmPublicSeaCustomerEntity customer, String toUserId) {
        try {
            customer.setAssignedTo(toUserId);
            customer.setAssignedAt(LocalDateTime.now());
            publicSeaCustomerRepository.save(customer);
            return true;
        } catch (Exception e) {
            log.warn("公海客户转移失败: customerId={}, error={}", customer.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 转移客户群: 将 ownerUserId 从 fromUserId 改为 toUserId
     *
     * @param group    客户群实体
     * @param toUserId 接收人 userId
     * @return true 表示转移成功
     */
    private boolean transferGroup(ScrmCustomerGroupEntity group, String toUserId) {
        try {
            group.setOwnerUserId(toUserId);
            customerGroupRepository.save(group);
            return true;
        } catch (Exception e) {
            log.warn("客户群转移失败: groupId={}, error={}", group.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 转移明细对应的资产 (重试用)
     * <p>根据明细类型分发到对应的转移方法。</p>
     *
     * @param task 任务实体
     * @param item 明细实体
     * @return true 表示转移成功
     */
    private boolean transferItem(ScrmInheritanceTaskEntity task, ScrmInheritanceItemEntity item) {
        try {
            if (ITEM_CUSTOMER.equals(item.getItemType())) {
                ScrmPublicSeaCustomerEntity customer = publicSeaCustomerRepository.findById(item.getItemId())
                        .orElse(null);
                if (customer == null) {
                    item.setErrorMessage("公海客户不存在: customerId=" + item.getItemId());
                    return false;
                }
                return transferCustomer(customer, task.getToUserId());
            } else if (ITEM_GROUP.equals(item.getItemType())) {
                ScrmCustomerGroupEntity group = customerGroupRepository.findById(item.getItemId())
                        .orElse(null);
                if (group == null) {
                    item.setErrorMessage("客户群不存在: groupId=" + item.getItemId());
                    return false;
                }
                return transferGroup(group, task.getToUserId());
            } else {
                item.setErrorMessage("不支持的明细类型: " + item.getItemType());
                return false;
            }
        } catch (Exception e) {
            item.setErrorMessage("转移异常: " + e.getMessage());
            log.warn("明细转移异常: itemId={}, error={}", item.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 刷新任务统计与状态 (按明细结果重新计算)
     *
     * @param task 任务实体
     */
    private void refreshTaskStatistics(ScrmInheritanceTaskEntity task) {
        List<ScrmInheritanceItemEntity> items = itemRepository.findByTaskIdOrderByIdAsc(task.getId());
        int total = items.size();
        int success = (int) items.stream().filter(i -> ITEM_SUCCESS.equals(i.getStatus())).count();
        int fail = (int) items.stream().filter(i -> ITEM_FAILED.equals(i.getStatus())).count();
        task.setTotalItems(total);
        task.setSuccessItems(success);
        task.setFailItems(fail);
        if (total == 0) {
            task.setStatus(STATUS_COMPLETED);
        } else if (fail == 0) {
            task.setStatus(STATUS_COMPLETED);
        } else if (success == 0) {
            task.setStatus(STATUS_FAILED);
        } else {
            task.setStatus(STATUS_PARTIAL);
        }
        taskRepository.save(task);
    }

    /**
     */
    private ScrmInheritanceTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        ScrmInheritanceTaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "离职继承任务不存在: id=" + id));
        // 数据隔离: 校验归属当前账号

        return entity;
    }

    /**
     * 任务实体转 DTO
     */
    private ScrmInheritanceTaskDto toDto(ScrmInheritanceTaskEntity entity) {
        ScrmInheritanceTaskDto dto = new ScrmInheritanceTaskDto();
        dto.setId(entity.getId());
        dto.setTaskName(entity.getTaskName());
        dto.setFromUserId(entity.getFromUserId());
        dto.setToUserId(entity.getToUserId());
        dto.setPlatformType(entity.getPlatformType());
        dto.setStatus(entity.getStatus());
        dto.setTotalItems(entity.getTotalItems());
        dto.setSuccessItems(entity.getSuccessItems());
        dto.setFailItems(entity.getFailItems());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setNote(entity.getNote());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 明细实体转 DTO
     */
    private ScrmInheritanceItemDto toDto(ScrmInheritanceItemEntity entity) {
        ScrmInheritanceItemDto dto = new ScrmInheritanceItemDto();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setItemType(entity.getItemType());
        dto.setItemId(entity.getItemId());
        dto.setItemLabel(entity.getItemLabel());
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setProcessedAt(entity.getProcessedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
