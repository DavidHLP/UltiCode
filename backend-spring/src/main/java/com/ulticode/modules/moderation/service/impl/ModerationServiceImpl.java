package com.ulticode.modules.moderation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.moderation.dto.*;
import com.ulticode.modules.moderation.entity.*;
import com.ulticode.modules.moderation.mapper.*;
import com.ulticode.modules.moderation.service.ModerationService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of ModerationService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private final ModerationQueueMapper queueMapper;
    private final ModerationActionMapper actionMapper;
    private final ReportMapper reportMapper;
    private final AppealMapper appealMapper;
    private final UserWarningMapper warningMapper;
    private final UserBanMapper banMapper;
    private final UserMapper userMapper;

    // ==================== Queue Operations ====================

    @Override
    public PageResult<ModerationQueueVO> getQueueItems(QueryModerationQueueDTO query) {
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

        wrapper.orderByDesc(ModerationQueue::getPriority)
               .orderByAsc(ModerationQueue::getCreatedAt);

        Page<ModerationQueue> page = new Page<>(query.getPage(), query.getLimit());
        Page<ModerationQueue> result = queueMapper.selectPage(page, wrapper);

        List<ModerationQueueVO> voList = result.getRecords().stream()
                .map(this::toQueueVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public ModerationQueueVO getQueueItem(String id) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
        }
        return toQueueVO(item);
    }

    @Override
    public ModerationStatsVO getStats() {
        ModerationStatsVO stats = new ModerationStatsVO();
        stats.setPendingCount(queueMapper.countPending());
        stats.setUnderReviewCount(queueMapper.countUnderReview());
        stats.setResolvedToday(queueMapper.countResolvedToday());
        stats.setPendingAppealsCount(appealMapper.countPending());
        stats.setAvgResolutionTimeMinutes(0L); // TODO: Calculate from historical data
        return stats;
    }

    @Override
    public ModerationQueueVO findByEntity(String entityType, String entityId) {
        ModerationQueue item = queueMapper.findByEntity(entityType, entityId);
        return item != null ? toQueueVO(item) : null;
    }

    @Override
    @Transactional
    public ModerationQueueVO claimItem(String id, String moderatorId) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
        }

        // Check if already assigned
        if (item.getAssignedToId() != null && !item.getAssignedToId().equals(moderatorId)) {
            throw new BusinessException(ErrorCode.MODERATION_ALREADY_ASSIGNED);
        }

        queueMapper.assignToModerator(id, moderatorId);
        return getQueueItem(id);
    }

    @Override
    @Transactional
    public ModerationQueueVO assignItem(String id, String moderatorId, String assignedTo) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
        }

        // Verify the target moderator exists
        User targetModerator = userMapper.selectById(assignedTo);
        if (targetModerator == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        queueMapper.assignToModerator(id, assignedTo);
        return getQueueItem(id);
    }

    @Override
    @Transactional
    public ModerationQueueVO unassignItem(String id, String moderatorId) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
        }

        queueMapper.unassign(id);
        return getQueueItem(id);
    }

    @Override
    @Transactional
    public ModerationQueueVO performAction(String id, PerformModerationActionDTO dto, String moderatorId) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
        }

        String action = dto.getAction().toUpperCase();

        // Create moderation action record
        ModerationAction moderationAction = new ModerationAction();
        moderationAction.setQueueId(id);
        moderationAction.setAction(action);
        moderationAction.setPerformedById(moderatorId);
        moderationAction.setNote(dto.getNote());
        moderationAction.setDurationDays(dto.getDurationDays());
        actionMapper.insert(moderationAction);

        // Update queue item based on action
        LocalDateTime now = LocalDateTime.now();
        item.setReviewedById(moderatorId);
        item.setReviewedAt(now);
        item.setResolution(action);
        item.setResolutionNote(dto.getNote());

        switch (action) {
            case "DELETED":
            case "HIDDEN":
            case "RESTORED":
            case "DISMISSED":
            case "RESOLVED":
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
                break;
            case "WARNED":
                // Create user warning
                createUserWarning(item.getAuthorId(), id, dto.getNote(), moderatorId);
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
                break;
            case "TEMP_BANNED":
                // Create temporary ban
                createUserBan(item.getAuthorId(), id, dto.getNote(), moderatorId, dto.getDurationDays(), false);
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
                break;
            case "PERM_BANNED":
                // Create permanent ban
                createUserBan(item.getAuthorId(), id, dto.getNote(), moderatorId, null, true);
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
                break;
            case "APPEAL_PENDING":
                item.setStatus("APPEAL_PENDING");
                break;
            default:
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown action: " + action);
        }

        queueMapper.updateById(item);

        // Update related reports
        updateReportsStatus(id, action.equals("DISMISSED") ? "DISMISSED" : "RESOLVED");

        log.info("Moderation action {} performed on queue item {} by moderator {}", action, id, moderatorId);
        return getQueueItem(id);
    }

    @Override
    @Transactional
    public BatchActionResultVO batchAction(BatchModerationActionDTO dto, String moderatorId) {
        List<BatchActionResultVO.BatchError> errors = new ArrayList<>();
        int successCount = 0;

        for (String queueId : dto.getQueueIds()) {
            try {
                PerformModerationActionDTO actionDto = new PerformModerationActionDTO();
                actionDto.setAction(dto.getAction());
                actionDto.setNote(dto.getNote());
                actionDto.setDurationDays(dto.getDurationDays());

                performAction(queueId, actionDto, moderatorId);
                successCount++;
            } catch (Exception e) {
                errors.add(new BatchActionResultVO.BatchError(queueId, e.getMessage()));
            }
        }

        return new BatchActionResultVO(successCount, errors.size(), errors);
    }

    // ==================== Report Operations ====================

    @Override
    @Transactional
    public void createReport(CreateReportDTO dto, String reporterId) {
        // Check if user already reported this entity
        long existingCount = reportMapper.countByReporterAndEntity(
                reporterId, dto.getEntityType(), dto.getEntityId());
        if (existingCount > 0) {
            throw new BusinessException(ErrorCode.MODERATION_ALREADY_REPORTED);
        }

        // Create the report
        Report report = new Report();
        report.setReporterId(reporterId);
        report.setEntityType(dto.getEntityType());
        report.setEntityId(dto.getEntityId());
        report.setCategory(dto.getCategory().toUpperCase());
        report.setReason(dto.getReason());
        report.setEvidence(dto.getEvidence());
        report.setStatus("PENDING");
        reportMapper.insert(report);

        // Update or create moderation queue item
        ModerationQueue queueItem = queueMapper.findByEntity(dto.getEntityType(), dto.getEntityId());
        if (queueItem == null) {
            queueItem = new ModerationQueue();
            queueItem.setEntityType(dto.getEntityType());
            queueItem.setEntityId(dto.getEntityId());
            queueItem.setPriority(1);
            queueItem.setStatus("PENDING");
            queueItem.setReportCount(1);
            queueItem.setPrimaryCategory(dto.getCategory().toUpperCase());
            queueMapper.insert(queueItem);
        } else {
            queueItem.setReportCount(queueItem.getReportCount() + 1);
            queueMapper.updateById(queueItem);
        }

        // Link report to queue
        report.setQueueId(queueItem.getId());
        reportMapper.updateById(report);

        log.info("Report created by user {} for entity {}/{}", reporterId, dto.getEntityType(), dto.getEntityId());
    }

    @Override
    public List<ReportVO> getReportsForEntity(String entityType, String entityId) {
        List<Report> reports = reportMapper.findByEntity(entityType, entityId);
        return reports.stream()
                .map(this::toReportVO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<ReportVO> getReports(QueryReportsDTO query) {
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

        wrapper.orderByDesc(Report::getCreatedAt);

        Page<Report> page = new Page<>(query.getPage(), query.getLimit());
        Page<Report> result = reportMapper.selectPage(page, wrapper);

        List<ReportVO> voList = result.getRecords().stream()
                .map(this::toReportVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    // ==================== Appeal Operations ====================

    @Override
    @Transactional
    public AppealVO createAppeal(CreateAppealDTO dto, String appellantId) {
        ModerationQueue queueItem = queueMapper.selectById(dto.getQueueId());
        if (queueItem == null) {
            throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
        }

        // Only the author of the content can appeal
        if (!queueItem.getAuthorId().equals(appellantId)) {
            throw new BusinessException(ErrorCode.MODERATION_NOT_AUTHOR);
        }

        // Check if queue item is in appealable state
        if (!"RESOLVED".equals(queueItem.getStatus())) {
            throw new BusinessException(ErrorCode.MODERATION_CANNOT_APPEAL);
        }

        Appeal appeal = new Appeal();
        appeal.setQueueId(dto.getQueueId());
        appeal.setAppellantId(appellantId);
        appeal.setReason(dto.getReason());
        appeal.setEvidence(dto.getEvidence());
        appeal.setStatus("PENDING");
        appealMapper.insert(appeal);

        // Update queue item status
        queueItem.setStatus("APPEAL_PENDING");
        queueMapper.updateById(queueItem);

        log.info("Appeal created by user {} for queue item {}", appellantId, dto.getQueueId());
        return toAppealVO(appeal);
    }

    @Override
    public PageResult<AppealVO> getAppeals(QueryAppealsDTO query) {
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

        wrapper.orderByDesc(Appeal::getCreatedAt);

        Page<Appeal> page = new Page<>(query.getPage(), query.getLimit());
        Page<Appeal> result = appealMapper.selectPage(page, wrapper);

        List<AppealVO> voList = result.getRecords().stream()
                .map(this::toAppealVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public AppealVO getAppeal(String id) {
        Appeal appeal = appealMapper.selectById(id);
        if (appeal == null) {
            throw new BusinessException(ErrorCode.MODERATION_APPEAL_NOT_FOUND);
        }
        return toAppealVO(appeal);
    }

    @Override
    @Transactional
    public AppealVO reviewAppeal(String id, ReviewAppealDTO dto, String moderatorId) {
        Appeal appeal = appealMapper.selectById(id);
        if (appeal == null) {
            throw new BusinessException(ErrorCode.MODERATION_APPEAL_NOT_FOUND);
        }

        if (!"PENDING".equals(appeal.getStatus()) && !"UNDER_REVIEW".equals(appeal.getStatus())) {
            throw new BusinessException(ErrorCode.MODERATION_APPEAL_ALREADY_REVIEWED);
        }

        String decision = dto.getDecision().toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        appeal.setStatus(decision);
        appeal.setReviewedById(moderatorId);
        appeal.setReviewedAt(now);
        appeal.setResponse(dto.getResponse());
        appealMapper.updateById(appeal);

        // Create action record
        ModerationAction action = new ModerationAction();
        action.setQueueId(appeal.getQueueId());
        action.setAction("APPEAL_" + decision);
        action.setPerformedById(moderatorId);
        action.setNote(dto.getResponse());
        actionMapper.insert(action);

        // Update queue item
        ModerationQueue queueItem = queueMapper.selectById(appeal.getQueueId());
        if (queueItem != null) {
            if ("APPROVED".equals(decision)) {
                // Restore the content or revert the action
                queueItem.setStatus("RESOLVED");
                queueItem.setResolution("APPEAL_APPROVED");
            } else {
                queueItem.setStatus("RESOLVED");
                queueItem.setResolution("APPEAL_REJECTED");
            }
            queueMapper.updateById(queueItem);
        }

        log.info("Appeal {} {} by moderator {}", id, decision, moderatorId);
        return toAppealVO(appeal);
    }

    // ==================== Private Helper Methods ====================

    private void createUserWarning(String userId, String queueId, String reason, String issuedById) {
        UserWarning warning = new UserWarning();
        warning.setUserId(userId);
        warning.setQueueId(queueId);
        warning.setReason(reason);
        warning.setIssuedById(issuedById);
        warning.setExpiresAt(LocalDateTime.now().plusDays(90)); // Warnings expire after 90 days
        warningMapper.insert(warning);
    }

    private void createUserBan(String userId, String queueId, String reason, String issuedById, Integer durationDays, boolean isPermanent) {
        UserBan ban = new UserBan();
        ban.setUserId(userId);
        ban.setQueueId(queueId);
        ban.setReason(reason);
        ban.setIssuedById(issuedById);
        ban.setIsPermanent(isPermanent);
        if (!isPermanent && durationDays != null) {
            ban.setExpiresAt(LocalDateTime.now().plusDays(durationDays));
        }
        banMapper.insert(ban);

        // Update user's ban status
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setIsBanned(true);
            if (!isPermanent && durationDays != null) {
                user.setBannedUntil(LocalDateTime.now().plusDays(durationDays));
            }
            user.setBannedReason(reason);
            userMapper.updateById(user);
        }
    }

    private void updateReportsStatus(String queueId, String status) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getQueueId, queueId);
        List<Report> reports = reportMapper.selectList(wrapper);
        for (Report report : reports) {
            report.setStatus(status);
            reportMapper.updateById(report);
        }
    }

    private ModerationQueueVO toQueueVO(ModerationQueue item) {
        ModerationQueueVO vo = new ModerationQueueVO();
        vo.setId(item.getId());
        vo.setEntityType(item.getEntityType());
        vo.setEntityId(item.getEntityId());
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

        // Fetch user details
        if (item.getAuthorId() != null) {
            User author = userMapper.selectById(item.getAuthorId());
            if (author != null) {
                vo.setAuthorName(author.getName());
                vo.setAuthorUsername(author.getUsername());
            }
        }
        if (item.getAssignedToId() != null) {
            User assignedTo = userMapper.selectById(item.getAssignedToId());
            if (assignedTo != null) {
                vo.setAssignedToName(assignedTo.getName());
                vo.setAssignedToUsername(assignedTo.getUsername());
            }
        }
        if (item.getReviewedById() != null) {
            User reviewedBy = userMapper.selectById(item.getReviewedById());
            if (reviewedBy != null) {
                vo.setReviewedByName(reviewedBy.getName());
            }
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
            User reporter = userMapper.selectById(report.getReporterId());
            if (reporter != null) {
                vo.setReporterName(reporter.getName());
                vo.setReporterUsername(reporter.getUsername());
            }
        }

        return vo;
    }

    private AppealVO toAppealVO(Appeal appeal) {
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
            User appellant = userMapper.selectById(appeal.getAppellantId());
            if (appellant != null) {
                vo.setAppellantName(appellant.getName());
                vo.setAppellantUsername(appellant.getUsername());
            }
        }
        if (appeal.getReviewedById() != null) {
            User reviewedBy = userMapper.selectById(appeal.getReviewedById());
            if (reviewedBy != null) {
                vo.setReviewedByName(reviewedBy.getName());
            }
        }

        return vo;
    }
}
