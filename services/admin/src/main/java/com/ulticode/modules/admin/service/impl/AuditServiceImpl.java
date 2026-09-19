package com.ulticode.modules.admin.service.impl;

import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.AuditStatsVO;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import com.ulticode.modules.admin.projection.AuditLogQuery;
import com.ulticode.modules.admin.projection.AuditLogReadProjection;
import com.ulticode.modules.admin.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;
    private final AuditLogReadProjection auditLogReadProjection;

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
        return auditLogReadProjection.findPage(AuditLogQuery.from(query));
    }

    @Override
    public List<AuditLogVO> getAuditLogsForExport(AuditLogQueryDTO query) {
        return auditLogReadProjection.findForExport(AuditLogQuery.from(query), exportLimit);
    }

    @Override
    public AuditStatsVO getAuditStats(AuditLogQueryDTO query) {
        return auditLogReadProjection.findStats(AuditLogQuery.from(query));
    }

}
