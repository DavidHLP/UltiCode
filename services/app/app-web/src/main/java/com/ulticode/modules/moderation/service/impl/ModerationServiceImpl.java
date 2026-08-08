package com.ulticode.modules.moderation.service.impl;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.ModerationErrorCode;
import com.ulticode.common.error.BaseErrorCode;
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
import com.ulticode.modules.moderation.entity.enums.ModerationStatus;
import com.ulticode.modules.moderation.mapper.AppealMapper;
import com.ulticode.modules.moderation.mapper.ModerationActionMapper;
import com.ulticode.modules.moderation.mapper.ModerationQueueMapper;
import com.ulticode.modules.moderation.mapper.ReportMapper;
import com.ulticode.modules.moderation.mapper.UserBanMapper;
import com.ulticode.modules.moderation.mapper.UserWarningMapper;
import com.ulticode.modules.moderation.port.ContentModerationPort;
import com.ulticode.modules.moderation.projection.ModerationProjection;
import com.ulticode.modules.moderation.service.ModerationService;
import com.ulticode.app.api.dto.ModerationUserInfo;
import com.ulticode.app.api.service.ModerationAccountPort;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The moderation state machine — every queue mutation, the report intake, the appeal lifecycle,
 * and the action-sink side effects the owned {@code applyAction} switch dispatches.
 *
 * <p>This module owns the full write invariant directly: the
 * {@code PENDING → UNDER_REVIEW → RESOLVED / DISMISSED / APPEAL_PENDING} queue transitions, the
 * appeal decision flow, the action-record-on-every-mutation rule, and the warning / ban /
 * content-flag side effects dispatched by {@code applyAction}. The state machine previously sat
 * behind a one-adapter {@code ModerationWritePort} seam that the service forwarded to verbatim;
 * that seam fronted the service's own logic (eight pure-delegate write paths) and carried no second
 * provider, so it was removed and the logic absorbed here. The guards join the transitions they
 * protect instead of being split across a facade and an adapter. The per-action variation likewise
 * moved out of a sealed {@code ModerationActionHandler} strategy + {@code ActionContext} proxy and
 * into the {@code applyAction} switch (C06 deepening), so the resolve/flag/warn/ban behavior and
 * the side-effect sinks sit in one module.
 *
 * <p>Reads live on {@link ModerationProjection}; controllers and cross-module callers depend on
 * {@link ModerationService} for writes and for the authorisation-guarded appeal lookup
 * ({@code getAppeal}). The {@code @Transactional} boundaries sit on the proxy-reached write methods
 * below; {@code batchAction} reuses {@code performAction} internally exactly as before (the
 * per-item loop shares the batch transaction and captures failures rather than rolling back).
 *
 * <p>The action-sink methods ({@link #createUserWarning}, {@link #createUserBan},
 * {@link #updateContentFlagStatus}) are package-private: only {@code applyAction} reaches them
 * directly, so they stay off the public service contract while remaining callable from inside
 * this module.
 *
 * @author ulticode
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
    private final ModerationAccountPort accountPort;
    private final ContentModerationPort contentModerationPort;
    private final ModerationProjection moderationProjection;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;

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
                throw new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND);
            }
            if (item.getAssignedToId() != null && !item.getAssignedToId().equals(moderatorId)) {
                throw new BusinessException(ModerationErrorCode.ALREADY_ASSIGNED);
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
            throw new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND);
        }

        // Verify the target moderator exists
        ModerationUserInfo targetModerator = accountPort.findById(assignedTo).orElse(null);
        if (targetModerator == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND);
        }

        queueMapper.assignToModerator(id, assignedTo);
        return moderationProjection.queueItemById(id);
    }

    @Override
    @Transactional
    public ModerationQueueVO unassignItem(String id, String moderatorId) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND);
        }

        queueMapper.unassign(id);
        return moderationProjection.queueItemById(id);
    }

    @Override
    @Transactional
    public ModerationQueueVO performAction(String id, PerformModerationActionDTO dto, String moderatorId) {
        ModerationQueue item = queueMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND);
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

        // Apply the action's queue transition + side effect in one owned switch
        // (C06 deepening). The sealed ModerationActionHandler strategy + the
        // ActionContext proxy that called back into this service are gone: every
        // action's resolve/flag/warn/ban behavior lives here, the package-private
        // sink methods are reached directly, and the variation stays internal.
        // The per-action inputs travel in a narrow ActionRequest value record so
        // the switch signature is not an 8-parameter data clump (the record is a
        // pure value — not the old service-callback ActionContext proxy).
        applyAction(new ActionRequest(actionType, moderatorId, dto.getNote(),
                dto.getDurationDays(), now, id, moderationAction.getId()), item);

        queueMapper.updateById(item);

        // Update related reports
        updateReportsStatus(id, actionType == ModerationActionType.DISMISSED ? "DISMISSED" : "RESOLVED");

        log.info("Moderation action {} performed on queue item {} by moderator {}", actionType, id, moderatorId);
        return moderationProjection.queueItemById(id);
    }

    @Override
    @Transactional
    public BatchActionResultVO batchAction(BatchModerationActionDTO dto, String moderatorId) {
        // Single shared transaction for the batch: performAction is invoked in-process
        // (no proxy), so a per-item failure is caught here without marking the
        // transaction rollback-only. Successful items commit together; failures are
        // reported per item rather than aborting the whole batch.
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
            throw new BusinessException(ModerationErrorCode.ALREADY_REPORTED);
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
            throw new BusinessException(ModerationErrorCode.QUEUE_NOT_FOUND);
        }

        // Only the author of the content can appeal
        if (!queueItem.getAuthorId().equals(appellantId)) {
            throw new BusinessException(ModerationErrorCode.NOT_AUTHOR);
        }

        // Check if queue item is in appealable state
        if (!"RESOLVED".equals(queueItem.getStatus())) {
            throw new BusinessException(ModerationErrorCode.CANNOT_APPEAL);
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
    public AppealVO getAppeal(String id, String currentUserId) {
        Appeal appeal = appealMapper.selectById(id);
        if (appeal == null) {
            throw new BusinessException(ModerationErrorCode.APPEAL_NOT_FOUND);
        }
        // Authorization guard: only appellant or MOD/ADMIN/SUPER_ADMIN may read.
        // Use Objects.equals for null-safety on BOTH sides — if appellantId is null
        // (data corruption), return false (deny) rather than NPE (HTTP 500).
        boolean isOwner = Objects.equals(appeal.getAppellantId(), currentUserId);
        boolean isModerator = currentUserProvider.hasRole("MODERATOR")
                            || currentUserProvider.hasRole("ADMIN")
                            || currentUserProvider.hasRole("SUPER_ADMIN");
        if (!isOwner && !isModerator) {
            log.warn("User {} attempted to read appeal {} owned by {}",
                    currentUserId, id, appeal.getAppellantId());
            throw new BusinessException(BaseErrorCode.FORBIDDEN);
        }
        return moderationProjection.toAppealVO(appeal);
    }

    @Override
    @Transactional
    public AppealVO reviewAppeal(String id, ReviewAppealDTO dto, String moderatorId) {
        Appeal appeal = appealMapper.selectById(id);
        if (appeal == null) {
            throw new BusinessException(ModerationErrorCode.APPEAL_NOT_FOUND);
        }

        if (!"PENDING".equals(appeal.getStatus()) && !"UNDER_REVIEW".equals(appeal.getStatus())) {
            throw new BusinessException(ModerationErrorCode.APPEAL_ALREADY_REVIEWED);
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

    // ==================== Action application (owned state machine) ====================

    /**
     * Per-action inputs the {@link #applyAction} switch needs. A narrow value
     * record so the switch signature is not an 8-parameter data clump. This is
     * a pure value (action + actor + note + timing + sink foreign keys) &mdash;
     * deliberately not the old {@code ActionContext}, which proxied callbacks
     * back into this service.
     *
     * @param action        the moderation action to apply
     * @param moderatorId   the acting moderator id (ban sink uses it as banned-by)
     * @param note          the moderator note (flag reason / warn / ban reason)
     * @param durationDays  temp-ban duration (ignored for non-ban actions)
     * @param now           the transition timestamp
     * @param queueId       the queue id (sink foreign key)
     * @param actionId      the moderation-action record id (sink foreign key)
     */
    private record ActionRequest(
            ModerationActionType action,
            String moderatorId,
            String note,
            Integer durationDays,
            LocalDateTime now,
            String queueId,
            String actionId) {
    }

    /**
     * Apply one moderation action's queue transition and side effect in a
     * single owned switch. Replaces the former sealed
     * {@code ModerationActionHandler} strategy + {@code ActionContext} proxy:
     * every action's resolve/flag/warn/ban behavior is concentrated here, the
     * package-private sink methods are reached directly (no callback through a
     * record wrapper), and the meaningful per-action variation stays internal.
     *
     * <p>Behavior matches the deleted handlers exactly: the same status set,
     * resolved-at stamp, content-flag polarity, and warn/ban sink calls.
     *
     * @param request the per-action inputs (action, actor, note, timing, sink ids)
     * @param item    the queue item being resolved (mutated in place)
     */
    private void applyAction(ActionRequest request, ModerationQueue item) {
        switch (request.action()) {
            case DELETED, HIDDEN -> {
                updateContentFlagStatus(item.getEntityType(), item.getEntityId(), true, request.note());
                resolve(item, request.now());
            }
            case RESTORED, DISMISSED, RESOLVED -> {
                updateContentFlagStatus(item.getEntityType(), item.getEntityId(), false, null);
                resolve(item, request.now());
            }
            case WARNED -> {
                createUserWarning(item.getAuthorId(), request.queueId(), request.note(),
                        item.getPrimaryCategory(), request.actionId());
                resolve(item, request.now());
            }
            case TEMP_BANNED -> {
                createUserBan(item.getAuthorId(), request.queueId(), request.note(),
                        item.getPrimaryCategory(), request.moderatorId(), request.actionId(),
                        request.durationDays(), false);
                resolve(item, request.now());
            }
            case PERM_BANNED -> {
                createUserBan(item.getAuthorId(), request.queueId(), request.note(),
                        item.getPrimaryCategory(), request.moderatorId(), request.actionId(),
                        null, true);
                resolve(item, request.now());
            }
            case APPEAL_PENDING -> item.setStatus(ModerationStatus.APPEAL_PENDING.name());
            case APPEAL_APPROVED -> {
                updateContentFlagStatus(item.getEntityType(), item.getEntityId(), false, null);
                resolve(item, request.now());
            }
            case APPEAL_REJECTED -> resolve(item, request.now());
        }
    }

    /** Mark the queue item RESOLVED and stamp its resolved-at timestamp. */
    private void resolve(ModerationQueue item, LocalDateTime now) {
        item.setStatus(ModerationStatus.RESOLVED.name());
        item.setResolvedAt(now);
    }

    // ==================== Action-sink callbacks (package-private; reached by applyAction) ====================

    void createUserWarning(String userId, String queueId, String reason, String category, String actionId) {
        UserWarning warning = new UserWarning();
        warning.setUserId(userId);
        warning.setQueueId(queueId);
        warning.setReason(reason != null ? reason : "No reason provided");
        warning.setCategory(category != null ? category : "OTHER");
        warning.setActionId(actionId);
        warning.setExpiresAt(LocalDateTime.now(clock).plusDays(90));
        warningMapper.insert(warning);
    }

    void createUserBan(String userId, String queueId, String reason, String category, String bannedById,
                       String actionId, Integer durationDays, boolean isPermanent) {
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

        // Update user's ban status via owner account port
        accountPort.updateBanStatus(userId, true, reason);
    }

    void updateContentFlagStatus(String entityType, String entityId, boolean isFlagged, String reason) {
        contentModerationPort.updateFlagStatus(entityType, entityId, isFlagged, reason);
    }

    // ==================== Private Helper Methods ====================

    private String resolveAuthorId(String entityType, String entityId) {
        return contentModerationPort.resolveAuthorId(entityType, entityId);
    }

    private void updateReportsStatus(String queueId, String status) {
        reportMapper.updateStatusByQueueId(queueId, status);
    }
}
