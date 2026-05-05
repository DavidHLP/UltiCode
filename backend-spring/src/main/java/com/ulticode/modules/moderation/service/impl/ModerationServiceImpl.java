package com.ulticode.modules.moderation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.moderation.dto.*;
import com.ulticode.modules.moderation.entity.*;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.moderation.mapper.*;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.moderation.service.ModerationService;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
    private final ForumPostMapper forumPostMapper;
    private final ForumCommentMapper forumCommentMapper;
    private final SolutionMapper solutionMapper;
    private final SolutionCommentMapper solutionCommentMapper;
    private final ProblemMapper problemMapper;

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

        List<ModerationQueue> records = result.getRecords();
        Map<String, User> userMap = buildUserMap(records);

        List<ModerationQueueVO> voList = records.stream()
                .map(item -> toQueueVO(item, userMap))
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public ModerationQueueVO getQueueItem(String id) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
        }
        Map<String, User> userMap = buildUserMap(List.of(item));
        return toQueueVO(item, userMap);
    }

    @Override
    public ModerationStatsVO getStats() {
        ModerationStatsVO stats = new ModerationStatsVO();
        stats.setPendingCount(queueMapper.countPending());
        stats.setUnderReviewCount(queueMapper.countUnderReview());
        stats.setResolvedToday(queueMapper.countResolvedToday());
        stats.setPendingAppealsCount(appealMapper.countPending());
        stats.setAvgResolutionTimeHours(queueMapper.avgResolutionTimeHours());
        return stats;
    }

    @Override
    public ModerationQueueVO findByEntity(String entityType, String entityId) {
        ModerationQueue item = queueMapper.findByEntity(entityType, entityId);
        if (item == null) {
            return null;
        }
        Map<String, User> userMap = buildUserMap(List.of(item));
        return toQueueVO(item, userMap);
    }

    @Override
    @Transactional
    public ModerationQueueVO claimItem(String id, String moderatorId) {
        // Use atomic conditional update to prevent race condition
        int updated = queueMapper.assignToModeratorIfUnassigned(id, moderatorId);
        if (updated == 0) {
            // Check why it failed
            ModerationQueue item = queueMapper.selectById(id);
            if (item == null) {
                throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
            }
            if (item.getAssignedToId() != null && !item.getAssignedToId().equals(moderatorId)) {
                throw new BusinessException(ErrorCode.MODERATION_ALREADY_ASSIGNED);
            }
            // If already assigned to current moderator, consider it success
        }
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

        // Validate action before inserting to avoid database enum mismatch
        // Must match: moderation_actions.action enum and moderation_queue.resolution enum
        Set<String> validActions = Set.of(
                "DELETED", "HIDDEN", "RESTORED", "DISMISSED", "RESOLVED",
                "WARNED", "TEMP_BANNED", "PERM_BANNED", "APPEAL_PENDING", "APPEAL_APPROVED", "APPEAL_REJECTED");
        if (!validActions.contains(action)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown action: " + action);
        }

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
                updateContentFlagStatus(item.getEntityType(), item.getEntityId(), true, dto.getNote());
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
                break;
            case "RESTORED":
            case "DISMISSED":
            case "RESOLVED":
                updateContentFlagStatus(item.getEntityType(), item.getEntityId(), false, null);
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
                break;
            case "WARNED":
                // Create user warning
                createUserWarning(item.getAuthorId(), id, dto.getNote(), item.getPrimaryCategory(), moderationAction.getId());
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
                break;
            case "TEMP_BANNED":
                // Create temporary ban
                createUserBan(item.getAuthorId(), id, dto.getNote(), item.getPrimaryCategory(), moderatorId, moderationAction.getId(), dto.getDurationDays(), false);
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
                break;
            case "PERM_BANNED":
                // Create permanent ban
                createUserBan(item.getAuthorId(), id, dto.getNote(), item.getPrimaryCategory(), moderatorId, moderationAction.getId(), null, true);
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
                break;
            case "APPEAL_PENDING":
                item.setStatus("APPEAL_PENDING");
                break;
            case "APPEAL_APPROVED":
                updateContentFlagStatus(item.getEntityType(), item.getEntityId(), false, null);
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
                break;
            case "APPEAL_REJECTED":
                item.setStatus("RESOLVED");
                item.setResolvedAt(now);
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
    @Transactional(rollbackFor = Exception.class)
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
            } catch (BusinessException e) {
                log.warn("Batch action failed for queue {}: {}", queueId, e.getMessage());
                errors.add(new BatchActionResultVO.BatchError(queueId, e.getMessage()));
            } catch (Exception e) {
                log.error("Batch action failed for queue item {}", queueId, e);
                errors.add(new BatchActionResultVO.BatchError(queueId, "Processing failed. Please try again."));
            }
        }

        if (successCount == 0 && !errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "All batch operations failed");
        }

        return new BatchActionResultVO(successCount, errors.size(), errors);
    }

    // ==================== Report Operations ====================

    @Override
    @Transactional
    public void createReport(CreateReportDTO dto, String reporterId) {
        Report report = new Report();
        report.setReporterId(reporterId);
        report.setEntityType(dto.getEntityType());
        report.setEntityId(dto.getEntityId());
        report.setCategory(dto.getCategory().toUpperCase());
        report.setReason(dto.getReason());
        report.setEvidence(dto.getEvidence());
        report.setStatus("PENDING");

        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.MODERATION_ALREADY_REPORTED);
        }

        // Resolve author ID from entity
        String authorId = resolveAuthorId(dto.getEntityType(), dto.getEntityId());

        // Update or create moderation queue item
        ModerationQueue queueItem = queueMapper.findByEntity(dto.getEntityType(), dto.getEntityId());
        if (queueItem == null) {
            queueItem = new ModerationQueue();
            queueItem.setEntityType(dto.getEntityType());
            queueItem.setEntityId(dto.getEntityId());
            queueItem.setAuthorId(authorId);
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

    private String resolveAuthorId(String entityType, String entityId) {
        switch (entityType) {
            case "forum_post":
                var post = forumPostMapper.selectById(entityId);
                return post != null ? post.getUserId() : null;
            case "forum_comment":
                var comment = forumCommentMapper.selectById(entityId);
                return comment != null ? comment.getAuthorId() : null;
            case "solution":
                var solution = solutionMapper.selectById(entityId);
                return solution != null ? solution.getUserId() : null;
            case "solution_comment":
                var solComment = solutionCommentMapper.selectById(entityId);
                return solComment != null ? solComment.getUserId() : null;
            case "problem":
                var problem = problemMapper.selectById(entityId);
                return problem != null ? problem.getPublishedBy() : null;
            default:
                return null;
        }
    }

    private void createUserWarning(String userId, String queueId, String reason, String category, String actionId) {
        UserWarning warning = new UserWarning();
        warning.setUserId(userId);
        warning.setQueueId(queueId);
        warning.setReason(reason != null ? reason : "No reason provided");
        warning.setCategory(category != null ? category : "OTHER");
        warning.setActionId(actionId);
        warning.setExpiresAt(LocalDateTime.now().plusDays(90));
        warningMapper.insert(warning);
    }

    private void createUserBan(String userId, String queueId, String reason, String category, String bannedById, String actionId, Integer durationDays, boolean isPermanent) {
        UserBan ban = new UserBan();
        ban.setUserId(userId);
        ban.setQueueId(queueId);
        ban.setReason(reason != null ? reason : "No reason provided");
        ban.setCategory(category);
        ban.setBannedById(bannedById);
        ban.setActionId(actionId);
        ban.setIsPermanent(isPermanent);
        LocalDateTime now = LocalDateTime.now();
        ban.setStartedAt(now);
        if (!isPermanent && durationDays != null) {
            ban.setEndsAt(now.plusDays(durationDays));
        }
        banMapper.insert(ban);

        // Update user's ban status
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setIsBanned(true);
            if (!isPermanent && durationDays != null) {
                user.setBannedUntil(now.plusDays(durationDays));
            }
            user.setBannedReason(reason);
            userMapper.updateById(user);
        }
    }

    private void updateContentFlagStatus(String entityType, String entityId, boolean isFlagged, String reason) {
        switch (entityType) {
            case "forum_post":
                forumPostMapper.updateFlagStatus(entityId, isFlagged, reason);
                break;
            case "forum_comment":
                forumCommentMapper.updateFlagStatus(entityId, isFlagged, reason);
                break;
            case "solution":
                solutionMapper.updateFlagStatus(entityId, isFlagged, reason);
                break;
            case "solution_comment":
                solutionCommentMapper.updateFlagStatus(entityId, isFlagged, reason);
                break;
            case "problem":
                problemMapper.updateFlagStatus(entityId, isFlagged, reason);
                break;
        }
    }

    private void updateReportsStatus(String queueId, String status) {
        reportMapper.updateStatusByQueueId(queueId, status);
    }

    private Map<String, User> buildUserMap(List<ModerationQueue> items) {
        Set<String> userIds = new HashSet<>();
        for (ModerationQueue item : items) {
            if (item.getAuthorId() != null) userIds.add(item.getAuthorId());
            if (item.getAssignedToId() != null) userIds.add(item.getAssignedToId());
            if (item.getReviewedById() != null) userIds.add(item.getReviewedById());
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private ModerationQueueVO toQueueVO(ModerationQueue item, Map<String, User> userMap) {
        ModerationQueueVO vo = new ModerationQueueVO();
        vo.setId(item.getId());
        vo.setEntityType(item.getEntityType());
        vo.setEntityId(item.getEntityId());
        // 解析 parentId（对于 solution_comment 类型）
        if ("solution_comment".equals(item.getEntityType())) {
            SolutionComment comment = solutionCommentMapper.selectById(item.getEntityId());
            if (comment != null) {
                vo.setParentId(comment.getSolutionId());
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

        User author = userMap.get(item.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.getName());
            vo.setAuthorUsername(author.getUsername());
        }
        User assignedTo = userMap.get(item.getAssignedToId());
        if (assignedTo != null) {
            vo.setAssignedToName(assignedTo.getName());
            vo.setAssignedToUsername(assignedTo.getUsername());
        }
        User reviewedBy = userMap.get(item.getReviewedById());
        if (reviewedBy != null) {
            vo.setReviewedByName(reviewedBy.getName());
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
