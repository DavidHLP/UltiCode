package com.ulticode.modules.moderation.port;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.moderation.dto.AppealVO;
import com.ulticode.modules.moderation.dto.BatchActionResultVO;
import com.ulticode.modules.moderation.dto.BatchModerationActionDTO;
import com.ulticode.modules.moderation.dto.CreateAppealDTO;
import com.ulticode.modules.moderation.dto.CreateReportDTO;
import com.ulticode.modules.moderation.dto.ModerationQueueVO;
import com.ulticode.modules.moderation.dto.PerformModerationActionDTO;
import com.ulticode.modules.moderation.dto.ReviewAppealDTO;
import com.ulticode.modules.moderation.entity.Appeal;
import com.ulticode.modules.moderation.entity.ModerationAction;
import com.ulticode.modules.moderation.entity.ModerationQueue;
import com.ulticode.modules.moderation.entity.Report;
import com.ulticode.modules.moderation.entity.UserBan;
import com.ulticode.modules.moderation.entity.UserWarning;
import com.ulticode.modules.moderation.entity.enums.ModerationActionType;
import com.ulticode.modules.moderation.mapper.AppealMapper;
import com.ulticode.modules.moderation.mapper.ModerationActionMapper;
import com.ulticode.modules.moderation.mapper.ModerationQueueMapper;
import com.ulticode.modules.moderation.mapper.ReportMapper;
import com.ulticode.modules.moderation.mapper.UserBanMapper;
import com.ulticode.modules.moderation.mapper.UserWarningMapper;
import com.ulticode.modules.moderation.projection.ModerationProjection;
import com.ulticode.modules.moderation.service.impl.ModerationActionHandler;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Default (and only) adapter for {@link ModerationWritePort}. Owns the
 * moderation state machine — every queue mutation, the report intake, the
 * appeal lifecycle, and the action-sink callbacks the
 * {@link ModerationActionHandler} strategies invoke. See the interface javadoc
 * for why this is a deep module.
 *
 * <p>Logic moved verbatim from the {@code ModerationServiceImpl} facade. The
 * facade stays as a thin delegate (write paths forward to this port; the
 * authorisation-guarded {@code getAppeal} read stays on the facade because it
 * is a read with a guard, not a state change) so {@code ModerationController}
 * and any cross-module caller see zero behavioural change. Every guard the
 * facade used to inline is preserved here: atomic conditional claim,
 * duplicate-report rejection, appealable-state and appellant-author checks,
 * action-record-on-every-mutation, and the
 * {@code PENDING → UNDER_REVIEW → RESOLVED / DISMISSED / APPEAL_PENDING}
 * queue transitions.
 *
 * <p>The {@link ModerationActionHandler} strategies receive an
 * {@code ActionContext} whose first component is {@code this} port, so they
 * reach the warning / ban / content-flag writers through the
 * {@link ModerationWritePort} action-sink methods without holding a
 * back-reference to the facade.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultModerationWritePort implements ModerationWritePort {

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
    private final ModerationProjection moderationProjection;
    private final Clock clock;

    // ==================== Queue Operations ====================

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
        return moderationProjection.queueItemById(id);
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
        return moderationProjection.queueItemById(id);
    }

    @Override
    @Transactional
    public ModerationQueueVO unassignItem(String id, String moderatorId) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
        }

        queueMapper.unassign(id);
        return moderationProjection.queueItemById(id);
    }

    @Override
    @Transactional
    public ModerationQueueVO performAction(String id, PerformModerationActionDTO dto, String moderatorId) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.MODERATION_QUEUE_NOT_FOUND);
        }

        ModerationActionType actionType = dto.getAction();

        // Create moderation action record
        ModerationAction moderationAction = new ModerationAction();
        moderationAction.setQueueId(id);
        moderationAction.setAction(actionType.name());
        moderationAction.setPerformedById(moderatorId);
        moderationAction.setNote(dto.getNote());
        moderationAction.setDurationDays(dto.getDurationDays());
        actionMapper.insert(moderationAction);

        // Update queue item based on action via strategy handler
        LocalDateTime now = LocalDateTime.now(clock);
        item.setReviewedById(moderatorId);
        item.setReviewedAt(now);
        item.setResolution(actionType.name());
        item.setResolutionNote(dto.getNote());

        ModerationActionHandler handler = ModerationActionHandler.from(actionType);
        ModerationActionHandler.ActionContext context = new ModerationActionHandler.ActionContext(this, id, moderationAction.getId());
        handler.perform(context, item, moderatorId, dto.getNote(), dto.getDurationDays(), now);

        queueMapper.updateById(item);

        // Update related reports
        updateReportsStatus(id, actionType == ModerationActionType.DISMISSED ? "DISMISSED" : "RESOLVED");

        log.info("Moderation action {} performed on queue item {} by moderator {}", actionType, id, moderatorId);
        return moderationProjection.queueItemById(id);
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

        // Always return BatchActionResultVO so callers receive per-item error details,
        // even when every item fails. Caller inspects successCount/errors to decide UX.
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
        return moderationProjection.toAppealVO(appeal);
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
        LocalDateTime now = LocalDateTime.now(clock);

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
        return moderationProjection.toAppealVO(appeal);
    }

    // ==================== Action-sink callbacks (ModerationActionHandler strategies) ====================

    @Override
    public void createUserWarning(String userId, String queueId, String reason, String category, String actionId) {
        UserWarning warning = new UserWarning();
        warning.setUserId(userId);
        warning.setQueueId(queueId);
        warning.setReason(reason != null ? reason : "No reason provided");
        warning.setCategory(category != null ? category : "OTHER");
        warning.setActionId(actionId);
        warning.setExpiresAt(LocalDateTime.now(clock).plusDays(90));
        warningMapper.insert(warning);
    }

    @Override
    public void createUserBan(String userId, String queueId, String reason, String category, String bannedById, String actionId, Integer durationDays, boolean isPermanent) {
        UserBan ban = new UserBan();
        ban.setUserId(userId);
        ban.setQueueId(queueId);
        ban.setReason(reason != null ? reason : "No reason provided");
        ban.setCategory(category);
        ban.setBannedById(bannedById);
        ban.setActionId(actionId);
        ban.setIsPermanent(isPermanent);
        LocalDateTime now = LocalDateTime.now(clock);
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

    @Override
    public void updateContentFlagStatus(String entityType, String entityId, boolean isFlagged, String reason) {
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
            default:
                // Unknown entity type — no content flag to update.
                break;
        }
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

    private void updateReportsStatus(String queueId, String status) {
        reportMapper.updateStatusByQueueId(queueId, status);
    }
}
