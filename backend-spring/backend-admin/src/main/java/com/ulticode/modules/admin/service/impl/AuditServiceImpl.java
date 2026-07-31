package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import com.ulticode.modules.admin.service.AuditService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;

    @Value("${audit.export.limit:10000}")
    private int exportLimit;

    @Override
    public AuditLog log(String performerId, String userId, String action,
                         String entityType, String entityId,
                         Map<String, Object> oldValues, Map<String, Object> newValues,
                         String ipAddress, String userAgent) {
        AuditLog auditLog = new AuditLog();
        auditLog.setPerformerId(performerId);
        auditLog.setUserId(userId);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId != null ? entityId : "N/A");
        auditLog.setOldValues(oldValues);
        auditLog.setNewValues(newValues);
        auditLog.setIpAddress(ipAddress);
        auditLog.setUserAgent(userAgent);

        auditLogMapper.insert(auditLog);
        log.debug("Audit log created: {} by {}", action, performerId);
        return auditLog;
    }

    @Override
    public PageResult<AuditLogVO> getAuditLogs(AuditLogQueryDTO query) {
        LambdaQueryWrapper<AuditLog> wrapper = buildQueryWrapper(query);
        wrapper.orderByDesc(AuditLog::getCreatedAt);

        Page<AuditLog> page = new Page<>(query.getPage(), query.getLimit());
        Page<AuditLog> result = auditLogMapper.selectPage(page, wrapper);

        Map<String, User> userMap = batchFetchUsers(result.getRecords());
        List<AuditLogVO> voList = result.getRecords().stream()
                .map(auditLog -> toVO(auditLog, userMap))
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public List<AuditLogVO> getAuditLogsForExport(AuditLogQueryDTO query) {
        LambdaQueryWrapper<AuditLog> wrapper = buildQueryWrapper(query);
        wrapper.orderByDesc(AuditLog::getCreatedAt);
        wrapper.last("LIMIT " + exportLimit);

        List<AuditLog> logs = auditLogMapper.selectList(wrapper);
        Map<String, User> userMap = batchFetchUsers(logs);

        return logs.stream()
                .map(auditLog -> toVO(auditLog, userMap))
                .collect(Collectors.toList());
    }

    @Override
    public AuditStatsVO getAuditStats(AuditLogQueryDTO query) {
        AuditStatsVO stats = new AuditStatsVO();

        LambdaQueryWrapper<AuditLog> wrapper = buildQueryWrapper(query);
        stats.setTotalActions(auditLogMapper.selectCount(wrapper));

        List<Map<String, Object>> entityMaps = auditLogMapper.selectStatsByEntityType(
            query.getStartDate(), query.getEndDate(), query.getPerformerId(),
            query.getUserId(), query.getEntityType(), query.getAction(), query.getSearch());
        List<EntityTypeStat> entityStats = entityMaps.stream()
            .map(m -> new EntityTypeStat(
                (String) m.get("entityType"),
                ((Number) m.get("count")).longValue()
            ))
            .collect(Collectors.toList());
        stats.setActionsByEntity(entityStats);

        List<Map<String, Object>> performerMaps = auditLogMapper.selectStatsByPerformer(
            query.getStartDate(), query.getEndDate(), query.getPerformerId(),
            query.getUserId(), query.getEntityType(), query.getAction(), query.getSearch());

        Set<String> performerIds = performerMaps.stream()
            .map(m -> (String) m.get("performerId"))
            .collect(Collectors.toSet());

        Map<String, User> userMap = performerIds.isEmpty() ? Collections.emptyMap()
            : userMapper.selectBatchIds(performerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<PerformerStat> topPerformers = performerMaps.stream().map(m -> {
            String performerId = (String) m.get("performerId");
            Long count = ((Number) m.get("count")).longValue();
            User user = userMap.get(performerId);
            return new PerformerStat(
                performerId,
                user != null ? user.getUsername() : null,
                user != null ? user.getName() : null,
                user != null ? user.getRole() : null,
                count
            );
        }).collect(Collectors.toList());
        stats.setTopPerformers(topPerformers);

        List<Map<String, Object>> actionTypeMaps = auditLogMapper.selectStatsByActionType(
            query.getStartDate(), query.getEndDate(), query.getPerformerId(),
            query.getUserId(), query.getEntityType(), query.getAction(), query.getSearch());
        List<ActionTypeStat> actionTypeStats = actionTypeMaps.stream()
            .map(m -> new ActionTypeStat(
                (String) m.get("actionType"),
                ((Number) m.get("count")).longValue()
            ))
            .collect(Collectors.toList());
        stats.setActionsByType(actionTypeStats);

        return stats;
    }

    private LambdaQueryWrapper<AuditLog> buildQueryWrapper(AuditLogQueryDTO query) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();

        if (query.getPerformerId() != null) {
            wrapper.eq(AuditLog::getPerformerId, query.getPerformerId());
        }
        if (query.getUserId() != null) {
            wrapper.eq(AuditLog::getUserId, query.getUserId());
        }
        if (query.getEntityType() != null) {
            wrapper.eq(AuditLog::getEntityType, query.getEntityType());
        }
        if (query.getEntityId() != null) {
            wrapper.eq(AuditLog::getEntityId, query.getEntityId());
        }
        if (query.getAction() != null) {
            wrapper.eq(AuditLog::getAction, query.getAction());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(AuditLog::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.lt(AuditLog::getCreatedAt, query.getEndDate());
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            wrapper.and(w -> w
                .like(AuditLog::getAction, query.getSearch())
                .or().like(AuditLog::getEntityType, query.getSearch())
                .or().like(AuditLog::getEntityId, query.getSearch())
            );
        }

        return wrapper;
    }

    private Map<String, User> batchFetchUsers(List<AuditLog> logs) {
        Set<String> userIds = logs.stream()
            .flatMap(log -> Stream.of(
                log.getPerformerId(),
                log.getUserId()
            ))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u));
    }

    private AuditLogVO toVO(AuditLog auditLog, Map<String, User> userMap) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(auditLog.getId());
        vo.setAction(auditLog.getAction());
        vo.setEntityType(auditLog.getEntityType());
        vo.setEntityId(auditLog.getEntityId());
        vo.setOldValues(auditLog.getOldValues());
        vo.setNewValues(auditLog.getNewValues());
        vo.setIpAddress(auditLog.getIpAddress());
        vo.setUserAgent(auditLog.getUserAgent());
        vo.setCreatedAt(auditLog.getCreatedAt());

        User performer = userMap.get(auditLog.getPerformerId());
        if (performer != null) {
            AuditLogVO.PerformerInfo performerInfo = new AuditLogVO.PerformerInfo();
            performerInfo.setId(performer.getId());
            performerInfo.setUsername(performer.getUsername());
            performerInfo.setName(performer.getName());
            performerInfo.setRole(performer.getRole());
            vo.setPerformer(performerInfo);
        }

        User user = userMap.get(auditLog.getUserId());
        if (user != null) {
            AuditLogVO.UserInfo userInfo = new AuditLogVO.UserInfo();
            userInfo.setId(user.getId());
            userInfo.setUsername(user.getUsername());
            userInfo.setName(user.getName());
            vo.setUser(userInfo);
        }

        return vo;
    }
}
