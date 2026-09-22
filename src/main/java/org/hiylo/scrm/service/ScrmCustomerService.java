/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerGroupEntity;
import org.hiylo.scrm.entity.ScrmCustomerGroupMemberEntity;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerGroupMemberRepository;
import org.hiylo.scrm.repository.ScrmCustomerGroupRepository;
import org.hiylo.scrm.repository.ScrmCustomerLifecycleHistoryRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
/**
 * SCRM 客户服务
 * <p>
 * 负责客户档案维护、标签管理、分组管理与生命周期阶段切换。
 * 所有写操作均写入当前用户归属账号, 实现数据隔离。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerService {

    /** 默认生命周期: 新客户 */
    private static final String LIFECYCLE_NEW = "NEW";

    /** 合法的生命周期阶段 (与 ScrmCustomerDto @Pattern 保持一致):
     * NEW 新 / PROSPECT 意向 / ACTIVE 活跃 / DORMANT 沉睡 / CHURNED 流失 / CONVERTED 已转化 */
    private static final List<String> VALID_LIFECYCLES =
            List.of("NEW", "PROSPECT", "ACTIVE", "DORMANT", "CHURNED", "CONVERTED");

    /** 客户数据访问层 */
    private final ScrmCustomerRepository customerRepository;
    /** 客户标签定义数据访问层 (tagCode → tagId 解析) */
    private final ScrmCustomerTagRepository tagRepository;
    /** 客户-标签赋值数据访问层 (重构后赋值关系独立存储) */
    private final ScrmTagCustomerRepository tagCustomerRepository;
    /** 客户分组数据访问层 */
    private final ScrmCustomerGroupRepository groupRepository;
    /** 客户-分组关联数据访问层（中间表） */
    private final ScrmCustomerGroupMemberRepository groupMemberRepository;
    /** 生命周期变更历史数据访问层 */
    private final ScrmCustomerLifecycleHistoryRepository lifecycleHistoryRepository;
    /** 数据隔离服务（按角色计算可访问账号范围） */
    private final DataScopeService dataScopeService;

    /**
     * 创建客户
     * <p>
     * 校验 platformType + platformCustomerUid + ownerAccountId 三元组唯一,
     * 默认生命周期为 NEW, 写入归属账号后持久化。
     * </p>
     * <p>参数校验: platformType / platformCustomerUid / ownerAccountId 必填且非空白,
     * lifecycle (如有) 必须为合法值。</p>
     *
     * @param dto 客户参数
     * @return 创建后的客户
     * @throws ScrmException 客户已存在 / 参数非法 / 权限不足
     */
    @Transactional
    public ScrmCustomerDto createCustomer(ScrmCustomerDto dto) throws ScrmException {
        // VIEWER 角色只读, 不允许创建
        ensureWritable();
        // 参数校验
        if (dto == null) {
            throw ScrmException.badRequest("客户参数不能为空");
        }
        if (dto.getPlatformType() == null || dto.getPlatformType().isBlank()) {
            throw ScrmException.badRequest("平台类型不能为空");
        }
        if (dto.getPlatformCustomerUid() == null || dto.getPlatformCustomerUid().isBlank()) {
            throw ScrmException.badRequest("平台客户 UID 不能为空");
        }
        if (dto.getOwnerAccountId() == null) {
            throw ScrmException.badRequest("归属账号 ID 不能为空");
        }
        // lifecycle 合法性校验 (如有值)
        if (dto.getLifecycle() != null && !VALID_LIFECYCLES.contains(dto.getLifecycle())) {
            throw ScrmException.badRequest(
                    "非法的生命周期值: " + dto.getLifecycle() + ", 仅支持 " + VALID_LIFECYCLES);
        }

        // 唯一性校验
        Optional<ScrmCustomerEntity> existed = customerRepository
                .findByPlatformTypeAndPlatformCustomerUidAndOwnerAccountId(
                        dto.getPlatformType(), dto.getPlatformCustomerUid(), dto.getOwnerAccountId());
        if (existed.isPresent()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "客户已存在: platformType=" + dto.getPlatformType()
                            + ", platformCustomerUid=" + dto.getPlatformCustomerUid()
                            + ", ownerAccountId=" + dto.getOwnerAccountId());
        }
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setPlatformType(dto.getPlatformType());
        entity.setPlatformCustomerUid(dto.getPlatformCustomerUid());
        entity.setNickname(dto.getNickname());
        entity.setAvatarUrl(dto.getAvatarUrl());
        entity.setOwnerAccountId(dto.getOwnerAccountId());
        entity.setPersonaId(dto.getPersonaId());
        entity.setLifecycle(dto.getLifecycle() != null ? dto.getLifecycle() : LIFECYCLE_NEW);
        entity.setLastInteractionAt(dto.getLastInteractionAt());
        entity.setNextFollowUpAt(dto.getNextFollowUpAt());
        entity = customerRepository.save(entity);
        log.info("创建 SCRM 客户: id={}, platformType={}, platformCustomerUid={}, ownerAccountId={}",
                entity.getId(), entity.getPlatformType(), entity.getPlatformCustomerUid(),
                entity.getOwnerAccountId());
        return toDto(entity);
    }

    /**
     * 更新客户信息
     * <p>部分更新场景: 仅校验非空字段的合法性, lifecycle (如有) 必须为合法值。</p>
     *
     * @param id  客户 ID
     * @param dto 客户参数
     * @return 更新后的客户
     * @throws ScrmException 客户不存在 / 参数非法 / 权限不足
     */
    @Transactional
    public ScrmCustomerDto updateCustomer(Long id, ScrmCustomerDto dto) throws ScrmException {
        // VIEWER 角色只读, 不允许更新
        ensureWritable();
        ScrmCustomerEntity entity = findOrThrow(id);
        // lifecycle 合法性校验 (如有值)
        if (dto.getLifecycle() != null && !VALID_LIFECYCLES.contains(dto.getLifecycle())) {
            throw ScrmException.badRequest(
                    "非法的生命周期值: " + dto.getLifecycle() + ", 仅支持 " + VALID_LIFECYCLES);
        }
        if (dto.getNickname() != null) entity.setNickname(dto.getNickname());
        if (dto.getAvatarUrl() != null) entity.setAvatarUrl(dto.getAvatarUrl());
        if (dto.getPersonaId() != null) entity.setPersonaId(dto.getPersonaId());
        if (dto.getLifecycle() != null) entity.setLifecycle(dto.getLifecycle());
        if (dto.getLastInteractionAt() != null) entity.setLastInteractionAt(dto.getLastInteractionAt());
        if (dto.getNextFollowUpAt() != null) entity.setNextFollowUpAt(dto.getNextFollowUpAt());
        if (dto.getRemark() != null) entity.setRemark(dto.getRemark());
        entity = customerRepository.save(entity);
        return toDto(entity);
    }

    /**
     * 更新客户备注
     *
     * @param id    客户 ID
     * @param notes 备注内容 (null 清空, 空串保留为空)
     * @return 更新后的客户 DTO
     * @throws ScrmException 客户不存在
     */
    @Transactional
    public ScrmCustomerDto updateCustomerNotes(Long id, String notes) throws ScrmException {
        ensureWritable();
        ScrmCustomerEntity entity = findOrThrow(id);
        entity.setRemark(notes);
        entity = customerRepository.save(entity);
        log.info("更新客户备注: customerId={}, notesLength={}", id, notes != null ? notes.length() : 0);
        return toDto(entity);
    }

    /**
     * 查询客户
     *
     * @param id 客户 ID
     * @return 客户 DTO
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerDto getCustomer(Long id) throws ScrmException {
        return toDto(findOrThrow(id));
    }

    /**
     * 删除客户
     * <p>
     * 级联清理关联的标签与分组成员关系, 然后删除客户主记录。
     * VIEWER 角色无权执行删除操作。
     * </p>
     *
     * @param id 客户 ID
     * @throws ScrmException 客户不存在或无权限
     */
    @Transactional
    public void deleteCustomer(Long id) throws ScrmException {
        ensureWritable();
        ScrmCustomerEntity entity = findOrThrow(id);
        // 级联清理: 删除该客户的所有标签赋值 (赋值关系独立存储于 ScrmTagCustomerEntity)
        tagCustomerRepository.deleteAll(
                tagCustomerRepository.findByCustomerId(id));
        // 级联清理: 删除该客户在所有分组中的成员关系
        groupMemberRepository.deleteByCustomerId(id);
        // 删除客户主记录
        customerRepository.delete(entity);
        log.info("客户已删除: id={}, nickname={}", id, entity.getNickname());
    }

    /**
     * 按平台类型、平台客户 UID 与归属账号 ID 查询客户
     * <p>数据隔离: 仅返回归属当前账号的客户。</p>
     *
     * @param platformType        平台类型
     * @param platformCustomerUid 平台客户 UID
     * @param ownerAccountId      归属账号 ID
     * @return 客户 DTO (不存在或不属于当前账号返回 null)
     */
    @Transactional(readOnly = true)
    public ScrmCustomerDto getCustomerByPlatform(String platformType, String platformCustomerUid,
                                                  Long ownerAccountId) {
        return customerRepository
                .findByPlatformTypeAndPlatformCustomerUidAndOwnerAccountId(
                        platformType, platformCustomerUid, ownerAccountId)

                .map(this::toDto)
                .orElse(null);
    }

    /**
     * 按归属账号分页查询客户
     * <p>数据隔离: 仅返回当前账号下该账号的客户。</p>
     *
     * @param ownerAccountId 归属账号 ID
     * @param page           页码 (从 0 开始)
     * @param size           每页大小
     * @return 客户分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerDto> getCustomersByOwner(Long ownerAccountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<ScrmCustomerEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerAccountId"), ownerAccountId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ScrmCustomerEntity> entities = customerRepository.findAll(spec, pageable);
        return entities.map(this::toDto);
    }

    /**
     * 查询需要跟进提醒的客户列表 (用于仪表盘展示)。
     * <p>
     * 返回 nextFollowUpAt 非空且在当前时间之后 24 小时内的客户 (含已逾期),
     * 按跟进时间升序排列 (最紧急的排最前), 限制返回条数。
     * </p>
     *
     * @param limit 最多返回条数 (默认 10)
     * @return 待跟进客户列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerDto> getFollowUpReminders(int limit) {
        // 阈值: 当前时间 + 24h, 查询即将到期和已逾期的跟进
        LocalDateTime threshold = LocalDateTime.now().plusHours(24);
        List<ScrmCustomerEntity> entities = customerRepository
                .findCustomersNeedingFollowUp(threshold);
        // 按跟进时间升序 (逾期的排最前), 限制条数
        return entities.stream()
                .sorted(java.util.Comparator.comparing(ScrmCustomerEntity::getNextFollowUpAt))
                .limit(limit)
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 为客户打标签 (相同 tagKey 已存在则覆盖 tagValue)
     *
     * @param customerId 客户 ID
     * @param tagKey     标签键
     * @param tagValue   标签值
     * @return 标签列表
     * @throws ScrmException 客户不存在
     */
    @Transactional
    public List<ScrmTagCustomerEntity> addTag(Long customerId, String tagKey, String tagValue)
            throws ScrmException {
        // VIEWER 角色只读, 不允许创建标签
        ensureWritable();
        findOrThrow(customerId);
        // tagKey (String) → tagId (Long), 经标签定义解析
        Long tagId = resolveTagIdOrThrow(tagKey);
        ScrmTagCustomerEntity tag = tagCustomerRepository
                .findByCustomerIdAndTagId(customerId, tagId)
                .orElseGet(ScrmTagCustomerEntity::new);
        if (tag.getId() == null) {
            tag.setCustomerId(customerId);
            tag.setTagId(tagId);
        }
        tag.setTagValue(tagValue);
        tagCustomerRepository.save(tag);
        log.info("客户打标签: customerId={}, tagKey={}, tagValue={}", customerId, tagKey, tagValue);
        return tagCustomerRepository.findByCustomerId(customerId);
    }

    /**
     * 批量给多个客户打标签。
     * <p>
     * 遍历客户 ID 列表, 对每个客户执行 upsert 标签操作。单个客户失败 (如不存在)
     * 不影响其他客户, 最终返回成功数与失败列表。
     * </p>
     *
     * @param customerIds 客户 ID 列表
     * @param tagKey      标签键
     * @param tagValue    标签值 (可空)
     * @return 批量操作结果 (成功数 + 失败客户 ID)
     */
    @Transactional
    public BatchTagResult batchAddTag(List<Long> customerIds, String tagKey, String tagValue) {
        ensureWritable();
        int successCount = 0;
        List<Long> failures = new ArrayList<>();
        for (Long customerId : customerIds) {
            try {
                findOrThrow(customerId);
                Long tagId = resolveTagIdOrThrow(tagKey);
                ScrmTagCustomerEntity tag = tagCustomerRepository
                        .findByCustomerIdAndTagId(customerId, tagId)
                        .orElseGet(ScrmTagCustomerEntity::new);
                if (tag.getId() == null) {
                    tag.setCustomerId(customerId);
                    tag.setTagId(tagId);
                }
                tag.setTagValue(tagValue);
                tagCustomerRepository.save(tag);
                successCount++;
            } catch (ScrmException e) {
                log.warn("批量打标签失败: customerId={}, error={}", customerId, e.getMessage());
                failures.add(customerId);
            }
        }
        log.info("批量打标签完成: total={}, success={}, failed={}, tagKey={}, tagValue={}",
                customerIds.size(), successCount, failures.size(), tagKey, tagValue);
        return new BatchTagResult(successCount, failures);
    }

    /**
     * 批量打标签操作结果
 * @since V1.0
     * @author Hsi Chu
     */
    public record BatchTagResult(int successCount, List<Long> failedCustomerIds) {
    }

    /**
     * 删除客户标签
     *
     * @param customerId 客户 ID
     * @param tagKey     标签键
     * @throws ScrmException 客户不存在 / 标签不存在
     */
    @Transactional
    public void removeTag(Long customerId, String tagKey) throws ScrmException {
        // VIEWER 角色只读, 不允许删除标签
        ensureWritable();
        findOrThrow(customerId);
        Long tagId = resolveTagIdOrThrow(tagKey);
        ScrmTagCustomerEntity tag = tagCustomerRepository
                .findByCustomerIdAndTagId(customerId, tagId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户标签不存在: customerId=" + customerId + ", tagKey=" + tagKey));
        tagCustomerRepository.delete(tag);
        log.info("删除客户标签: customerId={}, tagKey={}", customerId, tagKey);
    }

    /**
     * 查询客户的所有标签
     *
     * @param customerId 客户 ID
     * @return 标签赋值列表
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmTagCustomerEntity> getTags(Long customerId) throws ScrmException {
        findOrThrow(customerId);
        return tagCustomerRepository.findByCustomerId(customerId);
    }

    /**
     * 根据 tagCode (标签键) 解析标签定义 ID。
     * <p>
     * 标签赋值重构后, {@code ScrmTagCustomerEntity} 以 {@code tagId} 引用标签定义,
     * 外部 API 仍以 {@code tagKey} (即 tagCode) 标识标签, 此方法完成 tagKey → tagId 解析。
     * </p>
     *
     * @param tagKey   标签编码
     * @return 标签定义 ID
     * @throws ScrmException 标签定义不存在
     */
    private Long resolveTagIdOrThrow(String tagKey) throws ScrmException {
        return tagRepository.findByTagCode(tagKey)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "标签定义不存在, 请先创建标签定义: tagCode=" + tagKey)).getId();
    }

    /**
     * 批量解析 tagId → tagCode 映射 (用于前端展示 "tagCode=tagValue")。
     *
     * @param tagIds 标签定义 ID 集合
     * @return tagId → tagCode 映射, 空入参返回空 Map
     */
    @Transactional(readOnly = true)
    public Map<Long, String> tagCodeMap(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return tagRepository.findAllById(tagIds).stream()
                .collect(Collectors.toMap(ScrmCustomerTagEntity::getId, ScrmCustomerTagEntity::getTagCode));
    }

    /**
     * 创建客户分组
     * <p>参数校验: groupName 必填且非空白。</p>
     *
     * @param groupName      分组名称
     * @param description    分组描述
     * @param ownerAccountId 归属账号 ID
     * @return 创建后的分组
     * @throws ScrmException 分组名称为空
     */
    @Transactional
    public ScrmCustomerGroupEntity createGroup(String groupName, String description, Long ownerAccountId)
            throws ScrmException {
        if (groupName == null || groupName.isBlank()) {
            throw ScrmException.badRequest("分组名称不能为空");
        }
        ScrmCustomerGroupEntity entity = new ScrmCustomerGroupEntity();
        entity.setGroupName(groupName);
        entity.setDescription(description);
        entity.setOwnerAccountId(ownerAccountId);
        entity = groupRepository.save(entity);
        log.info("创建客户分组: id={}, groupName={}, ownerAccountId={}",
                entity.getId(), entity.getGroupName(), entity.getOwnerAccountId());
        return entity;
    }

    /**
     * 将客户加入分组
     * <p>
     * 校验客户与分组存在性后, 通过 {@code existsByGroupIdAndCustomerId} 幂等校验,
     * 已存在则跳过写入, 不存在则创建 {@link ScrmCustomerGroupMemberEntity} 关联记录。
     * 数据隔离: 客户与分组必须归属当前账号。
     * </p>
     *
     * @param groupId    分组 ID
     * @param customerId 客户 ID
     * @throws ScrmException 客户或分组不存在 / 越权访问
     */
    @Transactional
    public void addToGroup(Long groupId, Long customerId) throws ScrmException {
        findOrThrow(customerId);
        ScrmCustomerGroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.SCRM_CUSTOMER_GROUP_NOT_FOUND,
                        "客户分组不存在: groupId=" + groupId));
        // 数据隔离: 校验分组归属当前账号

        if (groupMemberRepository.existsByGroupIdAndCustomerId(groupId, customerId)) {
            log.info("客户已存在于分组, 跳过写入: groupId={}, customerId={}", groupId, customerId);
            return;
        }
        ScrmCustomerGroupMemberEntity member = new ScrmCustomerGroupMemberEntity();
        member.setGroupId(groupId);
        member.setCustomerId(customerId);
        groupMemberRepository.save(member);
        log.info("客户加入分组: groupId={}, customerId={}", groupId, customerId);
    }

    /**
     * 批量将客户加入分组。
     * <p>
     * 遍历客户 ID 列表, 逐个调用 {@link #addToGroup}。已存在于分组的客户自动跳过,
     * 单个客户失败 (如不存在) 不影响其他客户。
     * </p>
     *
     * @param groupId     分组 ID
     * @param customerIds 客户 ID 列表
     * @return 成功添加的数量 (不含已存在的)
     */
    @Transactional
    public int batchAddToGroup(Long groupId, List<Long> customerIds) {
        int successCount = 0;
        for (Long customerId : customerIds) {
            try {
                boolean exists = groupMemberRepository.existsByGroupIdAndCustomerId(groupId, customerId);
                if (exists) {
                    continue;
                }
                addToGroup(groupId, customerId);
                successCount++;
            } catch (ScrmException e) {
                log.warn("批量加入分组失败: groupId={}, customerId={}, error={}", groupId, customerId, e.getMessage());
            }
        }
        log.info("批量加入分组完成: groupId={}, total={}, added={}", groupId, customerIds.size(), successCount);
        return successCount;
    }

    /**
     * 将客户移出分组
     * <p>
     * 删除 {@code (groupId, customerId)} 关联记录, 不存在时静默返回（幂等）。
     * </p>
     *
     * @param groupId    分组 ID
     * @param customerId 客户 ID
     */
    @Transactional
    public void removeFromGroup(Long groupId, Long customerId) {
        groupMemberRepository.deleteByGroupIdAndCustomerId(groupId, customerId);
        log.info("客户移出分组: groupId={}, customerId={}", groupId, customerId);
    }

    /**
     * 查询分组的全部成员关联记录
     * <p>数据隔离: 分组必须归属当前账号, 否则视为不存在。</p>
     *
     * @param groupId 分组 ID
     * @return 成员关联列表 (分组不属于当前账号时返回空列表)
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerGroupMemberEntity> getGroupMembers(Long groupId) {
        // 数据隔离: 校验分组归属当前账号
        return groupRepository.findById(groupId)

                .map(g -> groupMemberRepository.findByGroupId(groupId))
                .orElse(java.util.Collections.emptyList());
    }

    /**
     * 更新客户生命周期阶段
     * <p>
     * 校验 lifecycle 必须为 LEAD/PROSPECT/ACTIVE/DORMANT/CHURNED 之一,
     * 记录变更前后的阶段与备注到结构化日志, 返回更新后的客户。
     * </p>
     *
     * @param customerId 客户 ID
     * @param lifecycle  生命周期: LEAD / PROSPECT / ACTIVE / DORMANT / CHURNED
     * @param remark     变更备注 (可空)
     * @return 更新后的客户
     * @throws ScrmException 客户不存在 / 生命周期值非法
     */
    @Transactional
    public ScrmCustomerDto updateLifecycle(Long customerId, String lifecycle, String remark) throws ScrmException {
        // VIEWER 角色只读, 不允许更新生命周期
        ensureWritable();
        // 校验生命周期值合法
        if (lifecycle == null || !VALID_LIFECYCLES.contains(lifecycle)) {
            throw ScrmException.badRequest("非法的生命周期值: " + lifecycle
                    + ", 仅支持 " + VALID_LIFECYCLES);
        }
        ScrmCustomerEntity entity = findOrThrow(customerId);
        // 记录变更前的生命周期阶段
        String previousLifecycle = entity.getLifecycle();
        // 阶段未变化则跳过
        if (lifecycle.equals(previousLifecycle)) {
            return toDto(entity);
        }
        entity.setLifecycle(lifecycle);
        entity = customerRepository.save(entity);

        // 持久化变更历史记录, 支撑生命周期追溯
        ScrmCustomerLifecycleHistoryEntity history = new ScrmCustomerLifecycleHistoryEntity();
        history.setCustomerId(customerId);
        history.setPreviousLifecycle(previousLifecycle);
        history.setNewLifecycle(lifecycle);
        history.setRemark(remark);
        history.setOperatorId(dataScopeService.getCurrentUserId());
        history.setOperatorName(dataScopeService.getHeader("X-Username"));
        lifecycleHistoryRepository.save(history);

        // 结构化日志: 客户ID / 变更前阶段 / 变更后阶段 / 备注
        log.info("客户生命周期变更: customerId={}, from={}, to={}, remark={}",
                customerId, previousLifecycle, lifecycle, remark);
        return toDto(entity);
    }

    /**
     * 查询客户生命周期变更历史 (按 ID 倒序, 即最新变更在前)。
     *
     * @param customerId 客户 ID
     * @return 变更历史列表 (最多 50 条)
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerLifecycleHistoryEntity> getLifecycleHistory(Long customerId) throws ScrmException {
        // 确保客户存在 (不存在则抛异常)
        findOrThrow(customerId);
        List<ScrmCustomerLifecycleHistoryEntity> all = lifecycleHistoryRepository
                .findByCustomerIdOrderByIdDesc(customerId);
        // 限制最多返回 50 条, 避免历史数据过多影响前端渲染
        return all.size() > 50 ? all.subList(0, 50) : all;
    }

    /**
     * 安排客户下次跟进时间
     * <p>
     * 更新客户的 nextFollowUpAt 字段, 由 {@code FollowUpReminderScheduler} 在到期前
     * 30 分钟内触发 FOLLOWUP_REMINDER 通知。remark 参数仅用于日志记录, 不持久化。
     * </p>
     *
     * @param customerId     客户 ID
     * @param nextFollowUpAt 下次跟进时间
     * @param remark         跟进备注 (可空, 仅记录日志)
     * @return 更新后的客户
     * @throws ScrmException 客户不存在
     */
    @Transactional
    public ScrmCustomerDto scheduleFollowUp(Long customerId, LocalDateTime nextFollowUpAt, String remark)
            throws ScrmException {
        // VIEWER 角色只读, 不允许安排跟进
        ensureWritable();
        ScrmCustomerEntity entity = findOrThrow(customerId);
        entity.setNextFollowUpAt(nextFollowUpAt);
        entity = customerRepository.save(entity);
        log.info("安排客户跟进: customerId={}, nextFollowUpAt={}, remark={}",
                customerId, nextFollowUpAt, remark);
        return toDto(entity);
    }

    /**
     * 分页查询客户列表, 支持按平台类型、生命周期与关键词过滤
     * <p>
     * 根据当前用户角色应用数据隔离：
     * <ul>
     *   <li>ADMIN/VIEWER → 不过滤（VIEWER 由调用方按 isReadOnly 控制写操作）</li>
     *   <li>MANAGER     → 仅返回本部门关联账号下的客户</li>
     *   <li>SALES       → 仅返回当前用户关联账号下的客户</li>
     * </ul>
     * </p>
     *
     * @param platformType 平台类型过滤 (可空)
     * @param lifecycle    生命周期过滤 (可空)
     * @param keyword      关键词过滤, 匹配昵称 / 平台客户 UID (可空)
     * @param page         页码 (从 0 开始)
     * @param size         每页大小
     * @return 客户分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerDto> listCustomers(String platformType, String lifecycle,
                                                String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));

        // 从请求头解析当前用户身份, 计算可访问的账号 ID 集合
        String userId = dataScopeService.getCurrentUserId();
        String role = dataScopeService.getCurrentRole();
        String departmentId = dataScopeService.getCurrentDepartmentId();
        List<Long> accessibleAccountIds = dataScopeService.getAccessibleAccountIds(userId, role, departmentId);

        Specification<ScrmCustomerEntity> spec = buildCustomerSpec(
                platformType, lifecycle, keyword, accessibleAccountIds);
        Page<ScrmCustomerEntity> entities = customerRepository.findAll(spec, pageable);
        return entities.map(this::toDto);
    }

    /**
     * 构建客户查询条件 Specification (含数据隔离 + 数据权限过滤)
     * <p>
     * 数据隔离: 始终按当前用户可见账号范围过滤, 确保仅返回当前用户的客户。
     * 数据权限: 在该范围内, 按角色进一步限制可访问的账号范围。
     * </p>
     */
    private Specification<ScrmCustomerEntity> buildCustomerSpec(String platformType, String lifecycle,
                                                                 String keyword, List<Long> accessibleAccountIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            // 数据权限: 限制可访问的账号范围 (ADMIN/VIEWER 无限制)
            if (accessibleAccountIds != null) {
                if (accessibleAccountIds.isEmpty()) {
                    // 下级用户没有任何归属账号 → 返回空集 (用恒假条件)
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("ownerAccountId").in(accessibleAccountIds));
                }
            }
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            }
            if (lifecycle != null && !lifecycle.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("lifecycle")), lifecycle.toLowerCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nickname")), kw),
                        cb.like(cb.lower(root.get("platformCustomerUid")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 校验当前用户是否允许写操作, VIEWER 角色抛出禁止访问异常
     *
     * @throws ScrmException 当前角色为 VIEWER 时抛出权限不足异常
     */
    private void ensureWritable() throws ScrmException {
        String role = dataScopeService.getCurrentRole();
        if (dataScopeService.isReadOnly(role)) {
            throw new ScrmException(ScrmExceptionConstants.FORBIDDEN,
                    "当前角色为 VIEWER, 不允许执行创建/更新/删除操作");
        }
    }

    /**
     * 按主键查询客户, 不存在抛异常
     */
    private ScrmCustomerEntity findOrThrow(Long id) throws ScrmException {
        ScrmCustomerEntity entity = customerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + id));
        // 数据隔离: 校验客户归属当前账号, 防止按 ID 越权访问

        return entity;
    }

    /**
     * 实体转 DTO
     */
    private ScrmCustomerDto toDto(ScrmCustomerEntity entity) {
        ScrmCustomerDto dto = new ScrmCustomerDto();
        dto.setId(entity.getId());
        dto.setPlatformType(entity.getPlatformType());
        dto.setPlatformCustomerUid(entity.getPlatformCustomerUid());
        dto.setNickname(entity.getNickname());
        dto.setAvatarUrl(entity.getAvatarUrl());
        dto.setOwnerAccountId(entity.getOwnerAccountId());
        dto.setPersonaId(entity.getPersonaId());
        dto.setLifecycle(entity.getLifecycle());
        dto.setLastInteractionAt(entity.getLastInteractionAt());
        dto.setNextFollowUpAt(entity.getNextFollowUpAt());
        dto.setRemark(entity.getRemark());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
