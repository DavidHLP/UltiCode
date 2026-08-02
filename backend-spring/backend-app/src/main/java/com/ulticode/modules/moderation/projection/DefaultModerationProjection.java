package com.ulticode.modules.moderation.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.ModerationErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.moderation.dto.AppealStatsVO;
import com.ulticode.modules.moderation.dto.AppealVO;
import com.ulticode.modules.moderation.dto.ModerationQueueVO;
import com.ulticode.modules.moderation.dto.ModerationStatsVO;
import com.ulticode.modules.moderation.dto.QueryAppealsDTO;
import com.ulticode.modules.moderation.dto.QueryModerationQueueDTO;
import com.ulticode.modules.moderation.dto.QueryReportsDTO;
import com.ulticode.modules.moderation.dto.ReportVO;
import com.ulticode.modules.moderation.entity.Appeal;
import com.ulticode.modules.moderation.entity.ModerationQueue;
import com.ulticode.modules.moderation.entity.Report;
import com.ulticode.modules.moderation.mapper.AppealMapper;
import com.ulticode.modules.moderation.mapper.ModerationQueueMapper;
import com.ulticode.modules.moderation.mapper.ReportMapper;
import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.app.api.dto.ModerationUserInfo;
import com.ulticode.modules.moderation.port.ModerationUserReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link ModerationProjection}. Owns every
 * entity-to-VO projection rule and read-side aggregation for the moderation
 * domain — see the interface javadoc for why this is a deep module.
 *
 * <p>All methods are pure reads; none mutate moderation state. The existence
 * checks on the single-item endpoints throw {@link ErrorCode#MODERATION_QUEUE_NOT_FOUND}
 * so callers receive the same semantics whether they read a queue item or a
 * report by id (the report path keeps the legacy error code on purpose — it
 * predates a dedicated report-not-found code and changing it would alter the
 * contract observed by the frontend).
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultModerationProjection implements ModerationProjection {

    private final ModerationQueueMapper queueMapper;
    private final ReportMapper reportMapper;
    private final AppealMapper appealMapper;
    private final ModerationUserReadPort userReadPort;
    private final SolutionCommentOwnerPort solutionCommentOwnerPort;

    // ------------------------------------------------------------------
    // Queue projection
    // ------------------------------------------------------------------

    @Override
    public PageResult<ModerationQueueVO> listQueueItems(QueryModerationQueueDTO query) {
        LambdaQueryWrapper<ModerationQueue> wrapper = buildQueueWrapper(query);

        Page<ModerationQueue> page = new Page<>(query.getPage(), query.getLimit());
        Page<ModerationQueue> result = queueMapper.selectPage(page, wrapper);

        List<ModerationQueue> records = result.getRecords();
        Map<String, ModerationUserInfo> userMap = buildUserMap(records);

        List<ModerationQueueVO> voList = records.stream()
                .map(item -> toQueueVO(item, userMap))
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public ModerationQueueVO queueItemById(String id) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND);
        }
        Map<String, ModerationUserInfo> userMap = buildUserMap(List.of(item));
        return toQueueVO(item, userMap);
    }

    @Override
    public ModerationQueueVO queueItemByEntity(String entityType, String entityId) {
        ModerationQueue item = queueMapper.findByEntity(entityType, entityId);
        if (item == null) {
            return null;
        }
        Map<String, ModerationUserInfo> userMap = buildUserMap(List.of(item));
        return toQueueVO(item, userMap);
    }

    @Override
    public ModerationStatsVO stats() {
        ModerationStatsVO stats = new ModerationStatsVO();
        stats.setPendingCount(queueMapper.countPending());
        stats.setUnderReviewCount(queueMapper.countUnderReview());
        stats.setResolvedCount(queueMapper.countResolved());
        stats.setDismissedCount(queueMapper.countDismissed());
        stats.setResolvedToday(queueMapper.countResolvedToday());
        stats.setPendingAppealsCount(appealMapper.countPending());
        stats.setAvgResolutionTimeHours(queueMapper.avgResolutionTimeHours());
        stats.setByCategory(toCountMap(queueMapper.countByCategory()));
        stats.setByEntityType(toCountMap(queueMapper.countByEntityType()));
        return stats;
    }

    // ------------------------------------------------------------------
    // Report projection
    // ------------------------------------------------------------------

    @Override
    public List<ReportVO> reportsForEntity(String entityType, String entityId) {
        List<Report> reports = reportMapper.findByEntity(entityType, entityId);
        return reports.stream()
                .map(this::toReportVO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<ReportVO> listReports(QueryReportsDTO query) {
        LambdaQueryWrapper<Report> wrapper = buildReportWrapper(query);

        Page<Report> page = new Page<>(query.getPage(), query.getLimit());
        Page<Report> result = reportMapper.selectPage(page, wrapper);

        List<ReportVO> voList = result.getRecords().stream()
                .map(this::toReportVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public ReportVO reportById(String id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND, "Report not found: " + id);
        }
        return toReportVO(report);
    }

    // ------------------------------------------------------------------
    // Appeal projection
    // ------------------------------------------------------------------

    @Override
    public PageResult<AppealVO> listAppeals(QueryAppealsDTO query) {
        LambdaQueryWrapper<Appeal> wrapper = buildAppealWrapper(query);

        Page<Appeal> page = new Page<>(query.getPage(), query.getLimit());
        Page<Appeal> result = appealMapper.selectPage(page, wrapper);

        List<AppealVO> voList = result.getRecords().stream()
                .map(this::toAppealVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public List<AppealVO> myAppeals(String appellantId) {
        LambdaQueryWrapper<Appeal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Appeal::getAppellantId, appellantId);
        wrapper.orderByDesc(Appeal::getCreatedAt);
        List<Appeal> appeals = appealMapper.selectList(wrapper);
        return appeals.stream()
                .map(this::toAppealVO)
                .collect(Collectors.toList());
    }

    @Override
    public AppealStatsVO appealStats() {
        AppealStatsVO stats = new AppealStatsVO();
        stats.setTotalPending(appealMapper.countByStatus("PENDING"));
        stats.setTotalUnderReview(appealMapper.countByStatus("UNDER_REVIEW"));
        stats.setTotalApproved(appealMapper.countByStatus("APPROVED"));
        stats.setTotalRejected(appealMapper.countByStatus("REJECTED"));
        return stats;
    }

    @Override
    public AppealVO toAppealVO(Appeal appeal) {
        if (appeal == null) {
            return null;
        }
        AppealVO vo = new AppealVO();
        vo.setId(appeal.getId());
        vo.setQueueId(appeal.getQueueId());
        vo.setAppellantId(appeal.getAppellantId());
        vo.setReason(appeal.getReason());
        vo.setEvidence(appeal.getEvidence());
        vo.setStatus(appeal.getStatus());
        vo.setReviewedById(appeal.getReviewedById());
        vo.setReviewedAt(appeal.getReviewedAt());
        vo.setResponse(appeal.getResponse());
        vo.setCreatedAt(appeal.getCreatedAt());
        vo.setUpdatedAt(appeal.getUpdatedAt());

        if (appeal.getAppellantId() != null) {
            ModerationUserInfo appellant = userReadPort.findById(appeal.getAppellantId());
            if (appellant != null) {
                vo.setAppellantName(appellant.username());
                vo.setAppellantUsername(appellant.username());
            }
        }
        if (appeal.getReviewedById() != null) {
            ModerationUserInfo reviewedBy = userReadPort.findById(appeal.getReviewedById());
            if (reviewedBy != null) {
                vo.setReviewedByName(reviewedBy.username());
            }
        }

        return vo;
    }

    // ------------------------------------------------------------------
    // Internal: query builders
    // ------------------------------------------------------------------

    private LambdaQueryWrapper<ModerationQueue> buildQueueWrapper(QueryModerationQueueDTO query) {
        LambdaQueryWrapper<ModerationQueue> wrapper = new LambdaQueryWrapper<>();

        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            wrapper.eq(ModerationQueue::getStatus, query.getStatus());
        }
        if (query.getEntityType() != null && !query.getEntityType().isEmpty()) {
            wrapper.eq(ModerationQueue::getEntityType, query.getEntityType());
        }
        if (query.getAssignedTo() != null && !query.getAssignedTo().isEmpty()) {
            wrapper.eq(ModerationQueue::getAssignedToId, query.getAssignedTo());
        }
        if (query.getPrimaryCategory() != null && !query.getPrimaryCategory().isEmpty()) {
            wrapper.eq(ModerationQueue::getPrimaryCategory, query.getPrimaryCategory());
        }
        if (query.getMinPriority() != null) {
            wrapper.ge(ModerationQueue::getPriority, query.getMinPriority());
        }

        boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
        if (query.getSortBy() != null && !query.getSortBy().isEmpty()) {
            switch (query.getSortBy()) {
                case "priority":
                    wrapper.orderBy(true, isAsc, ModerationQueue::getPriority);
                    break;
                case "createdAt":
                    wrapper.orderBy(true, isAsc, ModerationQueue::getCreatedAt);
                    break;
                case "updatedAt":
                    wrapper.orderBy(true, isAsc, ModerationQueue::getUpdatedAt);
                    break;
                default:
                    wrapper.orderByDesc(ModerationQueue::getPriority)
                           .orderByAsc(ModerationQueue::getCreatedAt);
            }
        } else {
            wrapper.orderByDesc(ModerationQueue::getPriority)
                   .orderByAsc(ModerationQueue::getCreatedAt);
        }

        return wrapper;
    }

    private LambdaQueryWrapper<Report> buildReportWrapper(QueryReportsDTO query) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();

        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            wrapper.eq(Report::getStatus, query.getStatus());
        }
        if (query.getCategory() != null && !query.getCategory().isEmpty()) {
            wrapper.eq(Report::getCategory, query.getCategory());
        }
        if (query.getReporterId() != null && !query.getReporterId().isEmpty()) {
            wrapper.eq(Report::getReporterId, query.getReporterId());
        }
        if (query.getEntityType() != null && !query.getEntityType().isEmpty()) {
            wrapper.eq(Report::getEntityType, query.getEntityType());
        }
        if (query.getEntityId() != null && !query.getEntityId().isEmpty()) {
            wrapper.eq(Report::getEntityId, query.getEntityId());
        }

        boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
        if (query.getSortBy() != null && !query.getSortBy().isEmpty()) {
            switch (query.getSortBy()) {
                case "createdAt":
                    wrapper.orderBy(true, isAsc, Report::getCreatedAt);
                    break;
                case "updatedAt":
                    wrapper.orderBy(true, isAsc, Report::getUpdatedAt);
                    break;
                default:
                    wrapper.orderByDesc(Report::getCreatedAt);
            }
        } else {
            wrapper.orderByDesc(Report::getCreatedAt);
        }

        return wrapper;
    }

    private LambdaQueryWrapper<Appeal> buildAppealWrapper(QueryAppealsDTO query) {
        LambdaQueryWrapper<Appeal> wrapper = new LambdaQueryWrapper<>();

        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            wrapper.eq(Appeal::getStatus, query.getStatus());
        }
        if (query.getQueueId() != null && !query.getQueueId().isEmpty()) {
            wrapper.eq(Appeal::getQueueId, query.getQueueId());
        }
        if (query.getAppellantId() != null && !query.getAppellantId().isEmpty()) {
            wrapper.eq(Appeal::getAppellantId, query.getAppellantId());
        }

        boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
        if (query.getSortBy() != null && !query.getSortBy().isEmpty()) {
            switch (query.getSortBy()) {
                case "createdAt":
                    wrapper.orderBy(true, isAsc, Appeal::getCreatedAt);
                    break;
                case "updatedAt":
                    wrapper.orderBy(true, isAsc, Appeal::getUpdatedAt);
                    break;
                default:
                    wrapper.orderByDesc(Appeal::getCreatedAt);
            }
        } else {
            wrapper.orderByDesc(Appeal::getCreatedAt);
        }

        return wrapper;
    }

    // ------------------------------------------------------------------
    // Internal: projection helpers
    // ------------------------------------------------------------------

    /**
     * Convert raw SQL group-by rows ({key, value}) to a Map<String, Long>.
     * Preserves the order returned by the SQL (LinkedHashMap).
     *
     * @param raw list of row maps from a group-by query
     * @return ordered map of key to count, empty if input is null/empty
     */
    private Map<String, Long> toCountMap(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : raw) {
            Object key = row.get("key");
            Object value = row.get("value");
            if (key != null && value instanceof Number) {
                result.put(key.toString(), ((Number) value).longValue());
            }
        }
        return result;
    }

    private Map<String, ModerationUserInfo> buildUserMap(List<ModerationQueue> items) {
        Set<String> userIds = new HashSet<>();
        for (ModerationQueue item : items) {
            if (item.getAuthorId() != null) userIds.add(item.getAuthorId());
            if (item.getAssignedToId() != null) userIds.add(item.getAssignedToId());
            if (item.getReviewedById() != null) userIds.add(item.getReviewedById());
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userReadPort.findByIds(userIds);
    }

    private ModerationQueueVO toQueueVO(ModerationQueue item, Map<String, ModerationUserInfo> userMap) {
        ModerationQueueVO vo = new ModerationQueueVO();
        vo.setId(item.getId());
        vo.setEntityType(item.getEntityType());
        vo.setEntityId(item.getEntityId());
        // 解析 parentId（对于 solution_comment 类型）
        if ("solution_comment".equals(item.getEntityType())) {
            String parentId = solutionCommentOwnerPort.resolveSolutionId(item.getEntityId());
            if (parentId != null) {
                vo.setParentId(parentId);
            }
        }
        vo.setAuthorId(item.getAuthorId());
        vo.setPriority(item.getPriority());
        vo.setStatus(item.getStatus());
        vo.setReportCount(item.getReportCount());
        vo.setPrimaryCategory(item.getPrimaryCategory());
        vo.setAssignedToId(item.getAssignedToId());
        vo.setAssignedAt(item.getAssignedAt());
        vo.setReviewedById(item.getReviewedById());
        vo.setReviewedAt(item.getReviewedAt());
        vo.setResolution(item.getResolution());
        vo.setResolutionNote(item.getResolutionNote());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        vo.setResolvedAt(item.getResolvedAt());

        ModerationUserInfo author = userMap.get(item.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.username());
            vo.setAuthorUsername(author.username());
        }
        ModerationUserInfo assignedTo = userMap.get(item.getAssignedToId());
        if (assignedTo != null) {
            vo.setAssignedToName(assignedTo.username());
            vo.setAssignedToUsername(assignedTo.username());
        }
        ModerationUserInfo reviewedBy = userMap.get(item.getReviewedById());
        if (reviewedBy != null) {
            vo.setReviewedByName(reviewedBy.username());
        }

        return vo;
    }

    private ReportVO toReportVO(Report report) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setReporterId(report.getReporterId());
        vo.setEntityType(report.getEntityType());
        vo.setEntityId(report.getEntityId());
        vo.setCategory(report.getCategory());
        vo.setReason(report.getReason());
        vo.setEvidence(report.getEvidence());
        vo.setStatus(report.getStatus());
        vo.setQueueId(report.getQueueId());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setUpdatedAt(report.getUpdatedAt());

        if (report.getReporterId() != null) {
            ModerationUserInfo reporter = userReadPort.findById(report.getReporterId());
            if (reporter != null) {
                vo.setReporterName(reporter.username());
                vo.setReporterUsername(reporter.username());
            }
        }

        return vo;
    }
}
