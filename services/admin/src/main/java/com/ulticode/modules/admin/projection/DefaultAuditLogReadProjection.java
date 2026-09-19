package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.ActionTypeStat;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.AuditStatsVO;
import com.ulticode.modules.admin.dto.EntityTypeStat;
import com.ulticode.modules.admin.dto.PerformerStat;
import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Deep read module for Admin audit data. It owns filter application, typed
 * aggregate mapping, and cross-owner user enrichment while export formatting
 * remains in the controller.
 */
@Service
@RequiredArgsConstructor
public class DefaultAuditLogReadProjection implements AuditLogReadProjection {

    private final AuditLogMapper auditLogMapper;
    private final AdminUserEnricher userEnricher;

    @Override
    public PageResult<AuditLogVO> findPage(AuditLogQuery query) {
        LambdaQueryWrapper<AuditLog> wrapper = query.toWrapper();
        wrapper.orderByDesc(AuditLog::getCreatedAt);

        Page<AuditLog> page = new Page<>(query.getPage(), query.getLimit());
        Page<AuditLog> result = auditLogMapper.selectPage(page, wrapper);
        List<AuditLog> records = result.getRecords();
        Map<String, AdminUserSummary> userMap = batchFetchUsers(records);
        List<AuditLogVO> items = records.stream()
                .map(auditLog -> toVO(auditLog, userMap))
                .collect(Collectors.toList());

        return PageResult.of(items, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public AuditStatsVO findStats(AuditLogQuery query) {
        AuditStatsVO stats = new AuditStatsVO();
        stats.setTotalActions(auditLogMapper.selectCount(query.toWrapper()));

        List<AuditLogMapper.EntityTypeCount> entityCounts =
                auditLogMapper.selectStatsByEntityType(query);
        stats.setActionsByEntity(entityCounts.stream()
                .map(row -> new EntityTypeStat(row.entityType(), row.count()))
                .collect(Collectors.toList()));

        List<AuditLogMapper.PerformerCount> performerCounts =
                auditLogMapper.selectStatsByPerformer(query);
        Set<String> performerIds = performerCounts.stream()
                .map(AuditLogMapper.PerformerCount::performerId)
                .collect(Collectors.toSet());
        Map<String, AdminUserSummary> userMap = performerIds.isEmpty()
                ? Collections.emptyMap()
                : userEnricher.enrich(performerIds);
        stats.setTopPerformers(performerCounts.stream()
                .map(row -> toPerformerStat(row, userMap))
                .collect(Collectors.toList()));

        List<AuditLogMapper.ActionTypeCount> actionTypeCounts =
                auditLogMapper.selectStatsByActionType(query);
        stats.setActionsByType(actionTypeCounts.stream()
                .map(row -> new ActionTypeStat(row.actionType(), row.count()))
                .collect(Collectors.toList()));
        return stats;
    }

    @Override
    public List<AuditLogVO> findForExport(AuditLogQuery query, int limit) {
        LambdaQueryWrapper<AuditLog> wrapper = query.toWrapper();
        wrapper.orderByDesc(AuditLog::getCreatedAt);
        wrapper.last("LIMIT " + limit);

        List<AuditLog> logs = auditLogMapper.selectList(wrapper);
        Map<String, AdminUserSummary> userMap = batchFetchUsers(logs);
        return logs.stream()
                .map(auditLog -> toVO(auditLog, userMap))
                .collect(Collectors.toList());
    }

    private PerformerStat toPerformerStat(
            AuditLogMapper.PerformerCount row,
            Map<String, AdminUserSummary> userMap) {
        AdminUserSummary user = findUser(userMap, row.performerId());
        return new PerformerStat(
                row.performerId(),
                user != null ? user.username() : null,
                user != null ? user.name() : null,
                user != null ? user.role() : null,
                row.count());
    }

    private Map<String, AdminUserSummary> batchFetchUsers(List<AuditLog> logs) {
        Set<String> userIds = logs.stream()
                .flatMap(log -> Stream.of(log.getPerformerId(), log.getUserId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return userIds.isEmpty() ? Collections.emptyMap() : userEnricher.enrich(userIds);
    }

    private AuditLogVO toVO(AuditLog auditLog, Map<String, AdminUserSummary> userMap) {
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

        AdminUserSummary performer = findUser(userMap, auditLog.getPerformerId());
        if (performer != null) {
            AuditLogVO.PerformerInfo performerInfo = new AuditLogVO.PerformerInfo();
            performerInfo.setId(performer.accountId());
            performerInfo.setUsername(performer.username());
            performerInfo.setName(performer.name());
            performerInfo.setRole(performer.role());
            vo.setPerformer(performerInfo);
        }

        AdminUserSummary user = findUser(userMap, auditLog.getUserId());
        if (user != null) {
            AuditLogVO.UserInfo userInfo = new AuditLogVO.UserInfo();
            userInfo.setId(user.accountId());
            userInfo.setUsername(user.username());
            userInfo.setName(user.name());
            vo.setUser(userInfo);
        }
        return vo;
    }

    private AdminUserSummary findUser(Map<String, AdminUserSummary> userMap, String accountId) {
        return accountId == null ? null : userMap.get(accountId);
    }
}
