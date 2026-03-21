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
        if (query.getStartDate() != null) {
            wrapper.ge(AuditLog::getCreatedAt, query.getStartDate());
        }
        if (query.getEndDate() != null) {
            wrapper.le(AuditLog::getCreatedAt, query.getEndDate());
        }

        wrapper.orderByDesc(AuditLog::getCreatedAt);

        Page<AuditLog> page = new Page<>(query.getPage(), query.getLimit());
        Page<AuditLog> result = auditLogMapper.selectPage(page, wrapper);

        // Collect user IDs to batch fetch
        Set<String> userIds = result.getRecords().stream()
            .flatMap(log -> Stream.of(
                log.getPerformerId(),
                log.getUserId()
            ))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // Create user map once
        Map<String, User> userMap;
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        } else {
            userMap = Collections.emptyMap();
        }

        List<AuditLogVO> voList = result.getRecords().stream()
                .map(auditLog -> toVO(auditLog, userMap))
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public AuditStatsVO getAuditStats(AuditLogQueryDTO query) {
        AuditStatsVO stats = new AuditStatsVO();

        // Build query wrapper for common conditions
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(query.getStartDate() != null, AuditLog::getCreatedAt, query.getStartDate())
              .le(query.getEndDate() != null, AuditLog::getCreatedAt, query.getEndDate())
              .eq(query.getPerformerId() != null, AuditLog::getPerformerId, query.getPerformerId());

        // Get total actions count
        stats.setTotalActions(auditLogMapper.selectCount(wrapper));

        // Get actions by entity type using MyBatis-Plus selectMaps with SQL
        LambdaQueryWrapper<AuditLog> entityWrapper = new LambdaQueryWrapper<>();
        entityWrapper.ge(query.getStartDate() != null, AuditLog::getCreatedAt, query.getStartDate())
                     .le(query.getEndDate() != null, AuditLog::getCreatedAt, query.getEndDate())
                     .eq(query.getPerformerId() != null, AuditLog::getPerformerId, query.getPerformerId());
        List<Map<String, Object>> entityStats = auditLogMapper.selectMaps(
            entityWrapper.apply("SELECT entity_type as entityType, COUNT(*) as count FROM audit_logs WHERE ${ew.customSqlSegment} GROUP BY entity_type ORDER BY count DESC LIMIT 10")
        );
        stats.setActionsByEntity(entityStats);

        // Get top performers using MyBatis-Plus selectMaps with SQL
        LambdaQueryWrapper<AuditLog> performerWrapper = new LambdaQueryWrapper<>();
        performerWrapper.ge(query.getStartDate() != null, AuditLog::getCreatedAt, query.getStartDate())
                        .le(query.getEndDate() != null, AuditLog::getCreatedAt, query.getEndDate())
                        .eq(query.getPerformerId() != null, AuditLog::getPerformerId, query.getPerformerId());
        List<Map<String, Object>> performerStats = auditLogMapper.selectMaps(
            performerWrapper.apply("SELECT performer_id as performerId, COUNT(*) as count FROM audit_logs WHERE ${ew.customSqlSegment} GROUP BY performer_id ORDER BY count DESC LIMIT 10")
        );
        stats.setTopPerformers(performerStats);

        return stats;
    }

    private AuditLogVO toVO(AuditLog auditLog, Map<String, User> userMap) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(auditLog.getId());
        vo.setPerformerId(auditLog.getPerformerId());
        vo.setUserId(auditLog.getUserId());
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
            vo.setPerformerName(performer.getName());
            vo.setPerformerUsername(performer.getUsername());
        }

        User user = userMap.get(auditLog.getUserId());
        if (user != null) {
            vo.setUserName(user.getName());
            vo.setUserUsername(user.getUsername());
        }

        return vo;
    }
}