/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPersonaService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmPersonaDto;
import org.hiylo.scrm.entity.ScrmPersonaEntity;
import org.hiylo.scrm.execution.TaskExecutionService;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmPersonaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 人设服务
 * <p>
 * 维护人设业务侧字段 (昵称、头像、性别、年龄段、地区、签名、话术风格标签、话术模板、自定义标签),
 * 同时通过 {@link TaskExecutionService#ensureExecutionPersona} 扩展点创建/确认执行侧 Persona 记录,
 * 保证业务侧与执行侧人设 ID 一致; 无外部执行引擎时仅记录业务侧数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmPersonaService {

    /** 合法的性别值: MALE 男 / FEMALE 女 / UNKNOWN 未知 (与 ScrmPersonaDto @Pattern 保持一致) */
    private static final Set<String> VALID_GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");

    /** 客户画像数据仓库 */
    private final ScrmPersonaRepository personaRepository;
    /** 营销任务执行引擎扩展点, 用于创建/确认执行侧人设记录 */
    private final TaskExecutionService taskExecutionService;
    /** 账户数据仓库 */
    private final ScrmAccountRepository accountRepository;

    /**
     * 创建人设
     * <p>
     * 先在业务侧创建记录, 同时通过 {@link TaskExecutionService#ensureExecutionPersona} 创建/确认
     * 执行侧 Persona 记录, 确保两侧 personaId 一致。扩展点失败时记录日志但不阻断业务侧创建
     * (执行侧可后续补偿)。
     * </p>
     * <p>参数校验:
     * <ul>
     *   <li>personaId 必填且非空白</li>
     *   <li>nickname 必填且非空白</li>
     *   <li>gender (如有) 必须为 male/female/unknown 之一</li>
     * </ul>
     * </p>
     *
     * @param dto 人设参数
     * @return 创建后的人设
     * @throws ScrmException 人设 ID 已存在 / 参数非法
     */
    @Transactional
    public ScrmPersonaDto createPersona(ScrmPersonaDto dto) throws ScrmException {
        // 参数校验
        if (dto == null) {
            throw ScrmException.badRequest("人设参数不能为空");
        }
        if (dto.getPersonaId() == null || dto.getPersonaId().isBlank()) {
            throw ScrmException.badRequest("人设 ID 不能为空");
        }
        if (dto.getNickname() == null || dto.getNickname().isBlank()) {
            throw ScrmException.badRequest("人设昵称不能为空");
        }
        // gender 合法性校验 (如有值)
        if (dto.getGender() != null && !VALID_GENDERS.contains(dto.getGender())) {
            throw ScrmException.badRequest(
                    "性别值非法: " + dto.getGender() + ", 仅支持 " + VALID_GENDERS);
        }
        // 唯一性校验: personaId 业务唯一

        ScrmPersonaEntity entity = new ScrmPersonaEntity();
        entity.setPersonaId(dto.getPersonaId());
        entity.setAccountId(dto.getAccountId());
        entity.setNickname(dto.getNickname());
        entity.setAvatarUrl(dto.getAvatarUrl());
        entity.setGender(dto.getGender());
        entity.setAgeRange(dto.getAgeRange());
        entity.setRegion(dto.getRegion());
        entity.setSignature(dto.getSignature());
        entity.setStyleTags(dto.getStyleTags());
        entity.setScriptTemplateIds(dto.getScriptTemplateIds());
        entity.setTags(dto.getTags());
        entity = personaRepository.save(entity);

        // 通过执行引擎扩展点创建/确认执行侧 Persona
        createExecutionPersona(dto);

        log.info("创建 SCRM 人设: personaId={}, accountId={}", entity.getPersonaId(), entity.getAccountId());
        return toDto(entity);
    }

    /**
     * 更新人设业务字段
     * <p>部分更新场景: 仅校验非空字段的合法性, gender (如有) 必须为合法值。</p>
     *
     * @param personaId 人设 ID
     * @param dto       人设参数
     * @return 更新后的人设
     * @throws ScrmException 人设不存在 / 参数非法
     */
    @Transactional
    public ScrmPersonaDto updatePersona(String personaId, ScrmPersonaDto dto) throws ScrmException {
        ScrmPersonaEntity entity = findOrThrow(personaId);
        // gender 合法性校验 (如有值)
        if (dto.getGender() != null && !VALID_GENDERS.contains(dto.getGender())) {
            throw ScrmException.badRequest(
                    "性别值非法: " + dto.getGender() + ", 仅支持 " + VALID_GENDERS);
        }
        if (dto.getNickname() != null) entity.setNickname(dto.getNickname());
        if (dto.getAvatarUrl() != null) entity.setAvatarUrl(dto.getAvatarUrl());
        if (dto.getGender() != null) entity.setGender(dto.getGender());
        if (dto.getAgeRange() != null) entity.setAgeRange(dto.getAgeRange());
        if (dto.getRegion() != null) entity.setRegion(dto.getRegion());
        if (dto.getSignature() != null) entity.setSignature(dto.getSignature());
        if (dto.getStyleTags() != null) entity.setStyleTags(dto.getStyleTags());
        if (dto.getScriptTemplateIds() != null) entity.setScriptTemplateIds(dto.getScriptTemplateIds());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getAccountId() != null) entity.setAccountId(dto.getAccountId());
        entity = personaRepository.save(entity);
        log.info("更新 SCRM 人设: personaId={}", personaId);
        return toDto(entity);
    }

    /**
     * 查询人设
     *
     * @param personaId 人设 ID
     * @return 人设 DTO
     * @throws ScrmException 人设不存在
     */
    @Transactional(readOnly = true)
    public ScrmPersonaDto getPersona(String personaId) throws ScrmException {
        return toDto(findOrThrow(personaId));
    }

    /**
     * 按账号 ID 查询人设列表
     *
     * @param accountId 账号 ID
     * @return 人设列表
     */
    @Transactional(readOnly = true)
    public List<ScrmPersonaDto> getPersonaByAccountId(Long accountId) {
        return personaRepository.findByAccountId(accountId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 删除人设
     * <p>
     * 删除前检查是否有账号仍引用该人设, 若有则阻止删除并返回引用数量,
     * 避免删除后产生悬空引用导致账号人设配置失效。
     * 当前实现仅删除业务侧记录, 执行侧的 Persona 记录由执行引擎独立维护, 不做同步删除。
     * </p>
     *
     * @param personaId 人设 ID
     * @throws ScrmException 人设不存在或仍有账号引用
     */
    @Transactional
    public void deletePersona(String personaId) throws ScrmException {
        ScrmPersonaEntity entity = findOrThrow(personaId);
        // 检查是否有账号仍引用该人设, 避免删除后产生悬空引用
        List<org.hiylo.scrm.entity.ScrmAccountEntity> referencingAccounts =
                accountRepository.findByPersonaId(personaId);
        if (!referencingAccounts.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除人设: 仍有 %d 个账号引用该人设, 请先解绑后再删除",
                            referencingAccounts.size()));
        }
        personaRepository.delete(entity);
        log.info("删除 SCRM 人设: personaId={}, accountId={}", personaId, entity.getAccountId());
    }

    /**
     * 分页查询人设
     *
     * @param page 页码 (从 0 开始)
     * @param size 每页大小
     * @return 人设分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmPersonaDto> listPersonas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        // 数据隔离: 始终按当前账号过滤
        Specification<ScrmPersonaEntity> spec = (root, query, cb) ->
                cb.and();
        return personaRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * 创建/确认执行侧 Persona 记录。
     * <p>
     * 委托 {@link TaskExecutionService#ensureExecutionPersona} 完成幂等创建;
     * 失败或无执行引擎时仅记录日志, 不阻断业务侧创建 (执行侧可后续补偿)。
     * </p>
     *
     * @param dto 业务侧人设参数
     */
    private void createExecutionPersona(ScrmPersonaDto dto) {
        // 业务侧人设未直接持有 platformType, 通过归属账号查询; 未关联账号时传 null
        String platformType = null;
        if (dto.getAccountId() != null) {
            Optional<org.hiylo.scrm.entity.ScrmAccountEntity> accountOpt =
                    Optional.ofNullable(accountRepository.findById(dto.getAccountId()))
                            .orElse(Optional.empty());
            platformType = accountOpt.map(org.hiylo.scrm.entity.ScrmAccountEntity::getPlatformType)
                    .orElse(null);
        }
        try {
            Long executionPersonaId = taskExecutionService.ensureExecutionPersona(
                    dto.getPersonaId(), platformType, dto.getNickname(), dto.getAvatarUrl());
            if (executionPersonaId != null) {
                log.info("执行侧 Persona 创建/确认成功: personaId={}, executionPersonaId={}",
                        dto.getPersonaId(), executionPersonaId);
            } else {
                log.debug("执行侧 Persona 创建/确认未返回 ID (无执行引擎或失败, 忽略): personaId={}",
                        dto.getPersonaId());
            }
        } catch (Exception e) {
            log.warn("执行侧 Persona 创建/确认异常(忽略, 业务侧已创建): personaId={}, err={}",
                    dto.getPersonaId(), e.getMessage());
        }
    }

    /**
     * 按 personaId 查询人设, 不存在抛异常
     */
    private ScrmPersonaEntity findOrThrow(String personaId) throws ScrmException {
        ScrmPersonaEntity entity = personaRepository.findByPersonaId(personaId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.SCRM_PERSONA_NOT_FOUND,
                        "人设不存在: personaId=" + personaId));
        // 数据隔离: 校验人设归属当前账号

        return entity;
    }

    /**
     * 实体转 DTO
     */
    private ScrmPersonaDto toDto(ScrmPersonaEntity entity) {
        ScrmPersonaDto dto = new ScrmPersonaDto();
        dto.setId(entity.getId());
        dto.setPersonaId(entity.getPersonaId());
        dto.setAccountId(entity.getAccountId());
        dto.setNickname(entity.getNickname());
        dto.setAvatarUrl(entity.getAvatarUrl());
        dto.setGender(entity.getGender());
        dto.setAgeRange(entity.getAgeRange());
        dto.setRegion(entity.getRegion());
        dto.setSignature(entity.getSignature());
        dto.setStyleTags(entity.getStyleTags());
        dto.setScriptTemplateIds(entity.getScriptTemplateIds());
        dto.setTags(entity.getTags());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
