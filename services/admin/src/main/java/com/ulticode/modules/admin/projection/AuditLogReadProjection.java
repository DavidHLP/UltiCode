package com.ulticode.modules.admin.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.AuditStatsVO;

import java.util.List;

/** Read seam for Admin audit pages, aggregates, and bounded exports. */
public interface AuditLogReadProjection {

    PageResult<AuditLogVO> findPage(AuditLogQuery query);

    AuditStatsVO findStats(AuditLogQuery query);

    List<AuditLogVO> findForExport(AuditLogQuery query, int limit);
}
