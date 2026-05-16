package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.AuditStatsVO;
import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.common.response.PageResult;
import java.util.List;
import java.util.Map;

public interface AuditService {
    AuditLog log(String performerId, String userId, String action,
                 String entityType, String entityId,
                 Map<String, Object> oldValues, Map<String, Object> newValues,
                 String ipAddress, String userAgent);

    PageResult<AuditLogVO> getAuditLogs(AuditLogQueryDTO query);
    AuditStatsVO getAuditStats(AuditLogQueryDTO query);
    List<AuditLogVO> getAuditLogsForExport(AuditLogQueryDTO query);
}
