/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAccountDto;
import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmAccountLoginLogEntity;
import org.hiylo.scrm.execution.TaskExecutionService;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.integration.wework.dto.WeworkUserDto;
import org.hiylo.scrm.repository.ScrmAccountLoginLogRepository;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmCampaignAccountRepository;
import org.hiylo.scrm.integration.wework.service.WeworkService;
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
 * SCRM 平台账号服务
 * <p>
 * 负责平台社媒账号的创建、更新、查询、设备绑定、登录态维护与人设绑定。
 * 项目仅支持企业微信一个平台, 平台同步 / 可用性检查均委托 {@link WeworkService}。
 * 所有写操作写入当前用户归属账号, 实现数据隔离。
 * 登录态变更同步记录到 {@code scrm_account_login_log} 表, 便于排查登录异常。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAccountService {

    /** 平台类型: 企业微信 (项目唯一支持的平台) */
    private static final String PLATFORM_TYPE_WEWORK = "wework";

    /** 登录态常量: 未知 (新建账号默认值) */
    private static final String LOGIN_STATE_UNKNOWN = "UNKNOWN";

    /** 登录态常量: 已登录 */
    private static final String LOGIN_STATE_LOGIN = "LOGIN";

    /** 登录态常量: 已登出 */
    private static final String LOGIN_STATE_LOGOUT = "LOGOUT";

    /** 登录态常量: 已冻结 */
    private static final String LOGIN_STATE_FROZEN = "FROZEN";

    /** 合法的登录态集合 */
    private static final java.util.Set<String> VALID_LOGIN_STATES =
            java.util.Set.of(LOGIN_STATE_LOGIN, LOGIN_STATE_LOGOUT, LOGIN_STATE_FROZEN, LOGIN_STATE_UNKNOWN);

    /** 账户数据仓库 */
    private final ScrmAccountRepository accountRepository;
    /** 账户登录日志数据仓库 */
    private final ScrmAccountLoginLogRepository loginLogRepository;
    /** 营销任务-账号关联数据访问层, 用于删除账号时级联清理营销任务关联 */
    private final ScrmCampaignAccountRepository campaignAccountRepository;
    /** 数据隔离服务 (计算当前用户可访问账号范围) */
    private final DataScopeService dataScopeService;
    /** 营销任务执行引擎扩展点, 用于创建/确认执行侧人设记录 */
    private final TaskExecutionService taskExecutionService;
    /** 感知当前账号的企业微信服务, 用于拉取用户资料与检查配置可用性 */
    private final WeworkService weworkService;

    /**
     * 创建账号
     * <p>
     * 校验 platformType + platformAccountUid 唯一, 设置归属账号 ID 与初始登录态 UNKNOWN,
     * 并写入一条登录日志记录账号创建。
     * </p>
     * <p>参数校验: platformType 与 platformAccountUid 必填且非空白。</p>
     *
     * @param dto 账号参数
     * @return 创建后的账号
     * @throws ScrmException 平台账号已存在 / 参数非法
     */
    @Transactional
    public ScrmAccountDto createAccount(ScrmAccountDto dto) throws ScrmException {
        // 参数校验
        if (dto == null) {
            throw ScrmException.badRequest("账号参数不能为空");
        }
        if (dto.getPlatformType() == null || dto.getPlatformType().isBlank()) {
            throw ScrmException.badRequest("平台类型不能为空");
        }
        if (dto.getPlatformAccountUid() == null || dto.getPlatformAccountUid().isBlank()) {
            throw ScrmException.badRequest("平台账号 UID 不能为空");
        }
        // 唯一性校验: 同平台同账号 UID 不允许重复
        Optional<ScrmAccountEntity> existed = accountRepository
                .findByPlatformTypeAndPlatformAccountUid(dto.getPlatformType(), dto.getPlatformAccountUid());
        if (existed.isPresent()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "平台账号已存在: platformType=" + dto.getPlatformType()
                            + ", platformAccountUid=" + dto.getPlatformAccountUid());
        }
        ScrmAccountEntity entity = new ScrmAccountEntity();
        entity.setPlatformType(dto.getPlatformType());
        entity.setPlatformAccountUid(dto.getPlatformAccountUid());
        entity.setAccountName(dto.getAccountName());
        entity.setDisplayName(dto.getDisplayName());
        entity.setAvatarUrl(dto.getAvatarUrl());
        entity.setDeviceId(dto.getDeviceId());
        entity.setPersonaId(dto.getPersonaId());
        // 归属当前用户 (数据隔离): 下级用户创建的账号自动归属本人
        entity.setOwnerUserId(currentUserIdOrNull());
        // 新建账号登录态默认 UNKNOWN, 后续由登录流程更新为 LOGIN
        entity.setLoginState(LOGIN_STATE_UNKNOWN);
        entity = accountRepository.save(entity);
        // 记录账号创建登录日志
        recordLoginLog(entity.getId(), null, LOGIN_STATE_UNKNOWN, "账号创建");
        log.info("创建 SCRM 账号: id={}, platformType={}, platformAccountUid={}",
                entity.getId(), entity.getPlatformType(), entity.getPlatformAccountUid());
        return toDto(entity);
    }

    /**
     * 更新账号信息
     *
     * @param id  账号 ID
     * @param dto 账号参数
     * @return 更新后的账号
     * @throws ScrmException 账号不存在
     */
    @Transactional
    public ScrmAccountDto updateAccount(Long id, ScrmAccountDto dto) throws ScrmException {
        ScrmAccountEntity entity = findOrThrow(id);
        if (dto.getAccountName() != null) entity.setAccountName(dto.getAccountName());
        if (dto.getDisplayName() != null) entity.setDisplayName(dto.getDisplayName());
        if (dto.getAvatarUrl() != null) entity.setAvatarUrl(dto.getAvatarUrl());
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getPlatformAccountUid() != null) entity.setPlatformAccountUid(dto.getPlatformAccountUid());
        if (dto.getPersonaId() != null) entity.setPersonaId(dto.getPersonaId());
        if (dto.getLastLoginAt() != null) entity.setLastLoginAt(dto.getLastLoginAt());
        entity = accountRepository.save(entity);
        return toDto(entity);
    }

    /**
     * 查询账号
     *
     * @param id 账号 ID
     * @return 账号 DTO
     * @throws ScrmException 账号不存在
     */
    @Transactional(readOnly = true)
    public ScrmAccountDto getAccount(Long id) throws ScrmException {
        return toDto(findOrThrow(id));
    }

    /**
     * 按平台类型与平台账号 UID 查询账号
     *
     * @param platformType       平台类型
     * @param platformAccountUid 平台账号 UID
     * @return 账号 DTO (不存在返回 null)
     */
    @Transactional(readOnly = true)
    public ScrmAccountDto getAccountByPlatform(String platformType, String platformAccountUid) {
        return accountRepository
                .findByPlatformTypeAndPlatformAccountUid(platformType, platformAccountUid)
                .map(this::toDto)
                .orElse(null);
    }

    /**
     * 按设备 ID 查询关联账号列表
     *
     * @param deviceId 设备 ID
     * @return 账号列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAccountDto> getAccountsByDeviceId(String deviceId) {
        return accountRepository.findByDeviceId(deviceId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 绑定设备
     *
     * @param accountId 账号 ID
     * @param deviceId  设备 ID
     * @return 更新后的账号
     * @throws ScrmException 账号不存在
     */
    @Transactional
    public ScrmAccountDto bindDevice(Long accountId, String deviceId) throws ScrmException {
        if (deviceId == null || deviceId.isBlank()) {
            throw ScrmException.badRequest("设备 ID 不能为空");
        }
        ScrmAccountEntity entity = findOrThrow(accountId);
        entity.setDeviceId(deviceId);
        entity = accountRepository.save(entity);
        log.info("账号绑定设备: accountId={}, deviceId={}", accountId, deviceId);
        return toDto(entity);
    }

    /**
     * 解绑设备
     *
     * @param accountId 账号 ID
     * @return 更新后的账号
     * @throws ScrmException 账号不存在
     */
    @Transactional
    public ScrmAccountDto unbindDevice(Long accountId) throws ScrmException {
        ScrmAccountEntity entity = findOrThrow(accountId);
        String previousDeviceId = entity.getDeviceId();
        entity.setDeviceId(null);
        entity = accountRepository.save(entity);
        log.info("账号解绑设备: accountId={}, previousDeviceId={}", accountId, previousDeviceId);
        return toDto(entity);
    }

    /**
     * 解绑人设
     * <p>
     * 清除账号的 personaId 引用, 不删除人设本身。
     * 解绑后人设可重新分配给其他账号。
     * </p>
     *
     * @param accountId 账号 ID
     * @return 更新后的账号
     * @throws ScrmException 账号不存在
     */
    @Transactional
    public ScrmAccountDto unbindPersona(Long accountId) throws ScrmException {
        ScrmAccountEntity entity = findOrThrow(accountId);
        String previousPersonaId = entity.getPersonaId();
        if (previousPersonaId == null || previousPersonaId.isBlank()) {
            log.info("账号未绑定人设, 无需解绑: accountId={}", accountId);
            return toDto(entity);
        }
        entity.setPersonaId(null);
        entity = accountRepository.save(entity);
        log.info("账号解绑人设: accountId={}, previousPersonaId={}", accountId, previousPersonaId);
        return toDto(entity);
    }

    /**
     * 删除账号
     * <p>
     * 级联清理以下关联数据, 避免删除后产生悬空引用:
     * <ul>
     *   <li>营销任务关联 (scrm_campaign_account): 删除该账号在所有任务中的分配记录</li>
     *   <li>设备绑定: 清除 deviceId 引用 (设备本身不删除)</li>
     *   <li>人设绑定: 清除 personaId 引用 (人设本身不删除)</li>
     * </ul>
     * 登录日志保留用于审计追溯, 不删除。
     * </p>
     * <p>
     * 注意: 运行中的营销任务关联账号删除后, 执行侧行为流可能因账号不存在而失败,
     * 建议在删除前先停止相关任务。
     * </p>
     *
     * @param accountId 账号 ID
     * @throws ScrmException 账号不存在
     */
    @Transactional
    public void deleteAccount(Long accountId) throws ScrmException {
        ScrmAccountEntity entity = findOrThrow(accountId);
        // 级联清理: 删除该账号在所有营销任务中的分配记录
        campaignAccountRepository.findByAccountId(accountId)
                .forEach(rel -> campaignAccountRepository.delete(rel));
        // 清除设备绑定引用 (设备本身不删除)
        entity.setDeviceId(null);
        // 清除人设绑定引用 (人设本身不删除)
        entity.setPersonaId(null);
        // 记录删除日志 (在删除前记录, 便于审计)
        log.info("删除 SCRM 账号: id={}, platformType={}, platformAccountUid={}, displayName={}",
                accountId, entity.getPlatformType(), entity.getPlatformAccountUid(), entity.getDisplayName());
        accountRepository.delete(entity);
        // 登录日志保留用于审计追溯, 不删除
    }

    /**
     * 更新登录态并记录日志
     *
     * @param accountId 账号 ID
     * @param newState  新登录态: LOGIN / LOGOUT / FROZEN / UNKNOWN
     * @param reason    变更原因
     * @return 更新后的账号
     * @throws ScrmException 账号不存在 / 登录态非法
     */
    @Transactional
    public ScrmAccountDto updateLoginState(Long accountId, String newState, String reason) throws ScrmException {
        if (newState == null || newState.isBlank()) {
            throw ScrmException.badRequest("登录态不能为空");
        }
        String upperState = newState.toUpperCase();
        if (!VALID_LOGIN_STATES.contains(upperState)) {
            throw ScrmException.badRequest("登录态非法: " + newState + ", 仅支持 LOGIN/LOGOUT/FROZEN/UNKNOWN");
        }
        ScrmAccountEntity entity = findOrThrow(accountId);
        String fromState = entity.getLoginState();
        entity.setLoginState(upperState);
        if (LOGIN_STATE_LOGIN.equals(upperState)) {
            entity.setLastLoginAt(LocalDateTime.now());
        }
        entity = accountRepository.save(entity);
        recordLoginLog(accountId, fromState, upperState, reason);
        log.info("账号登录态变更: accountId={}, {} -> {}, reason={}",
                accountId, fromState, upperState, reason);
        return toDto(entity);
    }

    /**
     * 绑定人设
     * <p>
     * 同时通过 {@link TaskExecutionService#ensureExecutionPersona} 创建/确认执行侧 Persona,
     * 保证业务侧与执行侧人设记录一致。扩展点失败时记录日志但保留本地绑定,
     * 因为执行侧可能已存在该人设, 由后续重试或对账补偿。
     * </p>
     *
     * @param accountId 账号 ID
     * @param personaId 人设 ID
     * @return 更新后的账号
     * @throws ScrmException 账号不存在
     */
    @Transactional
    public ScrmAccountDto bindPersona(Long accountId, String personaId) throws ScrmException {
        if (personaId == null || personaId.isBlank()) {
            throw ScrmException.badRequest("人设 ID 不能为空");
        }
        ScrmAccountEntity entity = findOrThrow(accountId);
        // 通过扩展点创建/确认执行侧 Persona (幂等保证)
        try {
            Long executionPersonaId = taskExecutionService.ensureExecutionPersona(
                    personaId, entity.getPlatformType(), entity.getDisplayName(), entity.getAvatarUrl());
            if (executionPersonaId != null) {
                log.info("执行侧 Persona 创建/确认: personaId={}, executionPersonaId={}",
                        personaId, executionPersonaId);
            } else {
                // 降级: 无执行引擎或创建失败, 仅记录日志, 不阻断本地绑定
                log.debug("执行侧 Persona 创建/确认未返回 ID (无执行引擎或失败, 保留本地绑定): personaId={}",
                        personaId);
            }
        } catch (Exception e) {
            log.warn("执行侧 Persona 创建/确认异常(忽略, 保留本地绑定): personaId={}, err={}",
                    personaId, e.getMessage());
        }
        entity.setPersonaId(personaId);
        entity = accountRepository.save(entity);
        log.info("账号绑定人设: accountId={}, personaId={}", accountId, personaId);
        return toDto(entity);
    }

    /**
     * 分页查询账号
     *
     * @param platformType 平台类型过滤 (可空)
     * @param loginState   登录态过滤 (可空)
     * @param page         页码 (从 0 开始)
     * @param size         每页大小
     * @return 账号分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAccountDto> listAccounts(String platformType, String loginState, int page, int size) {
        return listAccounts(platformType, loginState, null, page, size);
    }

    /**
     * 分页查询账号，支持关键词搜索
     * <p>
     * 使用 JPA Specification 在数据库层完成过滤, 避免全表加载。
     * </p>
     *
     * @param platformType 平台类型过滤 (可空)
     * @param loginState   登录态过滤 (可空)
     * @param keyword      关键词搜索（匹配显示名/平台账号UID，可空）
     * @param page         页码 (从 0 开始)
     * @param size         每页大小
     * @return 账号分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAccountDto> listAccounts(
            String platformType, String loginState, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<ScrmAccountEntity> spec = buildAccountSpec(platformType, loginState, keyword);
        Page<ScrmAccountEntity> entities = accountRepository.findAll(spec, pageable);
        return entities.map(this::toDto);
    }

    /**
     * 构建账号查询条件 Specification
     * <p>
     * 数据隔离: 下级用户仅能查询自己归属的账号; ADMIN 可见全部。
     * </p>
     */
    private Specification<ScrmAccountEntity> buildAccountSpec(String platformType, String loginState, String keyword) {
        Long ownerUserId = currentUserIdOrNull();
        String currentRole = currentRole();
        boolean admin = dataScopeService != null && dataScopeService.isAdmin(currentRole);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 下级用户仅可见自己归属的账号; 无归属(系统账号)对下级用户不可见
            if (!admin) {
                if (ownerUserId == null) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(cb.equal(root.get("ownerUserId"), ownerUserId));
                }
            }
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            }
            if (loginState != null && !loginState.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("loginState")), loginState.toLowerCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("displayName")), kw),
                        cb.like(cb.lower(root.get("platformAccountUid")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 从企业微信同步账号信息
     * <p>
     * 通过 {@link WeworkService#getUserDetail} 拉取企微侧账号资料 (昵称/头像),
     * 同步更新到本地 scrm_account 表的 displayName / avatarUrl 字段。
     * 项目仅支持企业微信平台, 传入其他 platformType 直接抛出异常。
     * </p>
     *
     * @param platformType       平台类型代码 (必须为 wework)
     * @param platformAccountUid 平台账号 UID
     * @return 同步后的账号 DTO, 本地账号不存在或企微返回空数据时返回 null
     * @throws ScrmException 平台类型不支持
     */
    @Transactional
    public ScrmAccountDto syncAccountFromPlatform(
            String platformType, String platformAccountUid) throws ScrmException {
        if (!PLATFORM_TYPE_WEWORK.equalsIgnoreCase(platformType)) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_PLATFORM_NOT_SUPPORTED,
                    "平台类型不支持: " + platformType);
        }
        WeworkUserDto userDto = weworkService.getUserDetail(platformAccountUid);
        if (userDto == null) {
            log.warn("企微返回空用户信息: platformAccountUid={}", platformAccountUid);
            return null;
        }
        // 查找本地账号, 不存在则跳过 (账号需先通过 createAccount 创建)
        ScrmAccountEntity entity = accountRepository
                .findByPlatformTypeAndPlatformAccountUid(platformType, platformAccountUid)
                .orElse(null);
        if (entity == null) {
            log.warn("本地账号不存在, 跳过同步: platformType={}, platformAccountUid={}",
                    platformType, platformAccountUid);
            return null;
        }
        // 用企微返回的昵称/头像更新本地账号
        if (userDto.getNickname() != null) {
            entity.setDisplayName(userDto.getNickname());
        }
        if (userDto.getAvatar() != null) {
            entity.setAvatarUrl(userDto.getAvatar());
        }
        entity = accountRepository.save(entity);
        log.info("账号信息已从企微同步: id={}, platformAccountUid={}, nickname={}",
                entity.getId(), platformAccountUid, userDto.getNickname());
        return toDto(entity);
    }

    /**
     * 检查平台账号可用性
     * <p>
     * 项目仅支持企业微信平台, 委托 {@link WeworkService#isAvailable}
     * 检查当前账号的企微配置是否完整可用。
     * 注意: 此方法仅检查平台配置是否可用, 不检查具体账号是否登录/有效。
     * </p>
     *
     * @param platformType       平台类型代码 (必须为 wework)
     * @param platformAccountUid 平台账号 UID (当前实现仅做平台级检查, 保留参数以备后续账号级校验)
     * @return 平台可用返回 true, 否则 false
     */
    public boolean checkPlatformAccountAvailable(String platformType, String platformAccountUid) {
        if (!PLATFORM_TYPE_WEWORK.equalsIgnoreCase(platformType)) {
            log.warn("平台类型不支持, 视为不可用: platformType={}", platformType);
            return false;
        }
        try {
            boolean available = weworkService.isAvailable();
            log.debug("企微账号可用性检查: platformAccountUid={}, available={}", platformAccountUid, available);
            return available;
        } catch (Exception e) {
            log.warn("企微账号可用性检查异常: platformAccountUid={}, err={}",
                    platformAccountUid, e.getMessage());
            return false;
        }
    }

    /**
     * 获取可用平台列表
     * <p>
     * 项目仅支持企业微信一个平台, 直接返回固定列表。
     * </p>
     *
     * @return 可用平台类型列表 (仅含 wework)
     */
    public List<String> getAvailablePlatforms() {
        return List.of(PLATFORM_TYPE_WEWORK);
    }

    /**
     * 记录登录态变更日志
     *
     * @param accountId 账号 ID
     * @param fromState 变更前登录态 (首次可为 null)
     * @param toState   变更后登录态
     * @param reason    变更原因
     */
    private void recordLoginLog(Long accountId, String fromState, String toState, String reason) {
        ScrmAccountLoginLogEntity logEntity = new ScrmAccountLoginLogEntity();
        logEntity.setAccountId(accountId);
        logEntity.setFromState(fromState);
        logEntity.setToState(toState);
        logEntity.setReason(reason);
        logEntity.setOperateAt(LocalDateTime.now());
        loginLogRepository.save(logEntity);
    }

    /**
     * 按主键查询账号并校验账号归属, 不存在或不属于当前账号抛异常
     * <p>
     * 数据隔离: ADMIN 可操作任意账号; 下级用户只能操作自己归属的账号,
     * 其他账号视为不存在 (返回 SCRM_ACCOUNT_NOT_FOUND)。
     * </p>
     */
    private ScrmAccountEntity findOrThrow(Long id) throws ScrmException {
        ScrmAccountEntity entity = accountRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.SCRM_ACCOUNT_NOT_FOUND,
                        "SCRM 账号不存在: id=" + id));
        // 数据隔离: 校验账号归属当前用户 (ADMIN 不受限)
        Long ownerUserId = currentUserIdOrNull();
        boolean admin = dataScopeService != null && dataScopeService.isAdmin(currentRole());
        if (!admin && (ownerUserId == null || !ownerUserId.equals(entity.getOwnerUserId()))) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_ACCOUNT_NOT_FOUND,
                    "SCRM 账号不存在: id=" + id);
        }
        return entity;
    }

    /**
     * 获取当前登录用户 ID (Long), 无请求上下文或格式非法时返回 null。
     *
     * @return 当前用户 ID, 无法解析返回 null
     */
    private Long currentUserIdOrNull() {
        String userId = dataScopeService != null ? dataScopeService.getCurrentUserId() : null;
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取当前用户角色串 (逗号分隔), 无请求上下文时返回 null。
     *
     * @return 角色串, 不存在返回 null
     */
    private String currentRole() {
        return dataScopeService != null ? dataScopeService.getCurrentRole() : null;
    }

    /**
     * 实体转 DTO
     */
    private ScrmAccountDto toDto(ScrmAccountEntity entity) {
        ScrmAccountDto dto = new ScrmAccountDto();
        dto.setId(entity.getId());
        dto.setPlatformType(entity.getPlatformType());
        dto.setPlatformAccountUid(entity.getPlatformAccountUid());
        dto.setAccountName(entity.getAccountName());
        dto.setDisplayName(entity.getDisplayName());
        dto.setAvatarUrl(entity.getAvatarUrl());
        dto.setOwnerUserId(entity.getOwnerUserId());
        dto.setDeviceId(entity.getDeviceId());
        dto.setPersonaId(entity.getPersonaId());
        dto.setLoginState(entity.getLoginState());
        dto.setLastLoginAt(entity.getLastLoginAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
