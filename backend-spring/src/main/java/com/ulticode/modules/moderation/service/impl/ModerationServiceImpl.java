package com.ulticode.modules.moderation.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.moderation.dto.AppealVO;
import com.ulticode.modules.moderation.dto.BatchActionResultVO;
import com.ulticode.modules.moderation.dto.BatchModerationActionDTO;
import com.ulticode.modules.moderation.dto.CreateAppealDTO;
import com.ulticode.modules.moderation.dto.CreateReportDTO;
import com.ulticode.modules.moderation.dto.ModerationQueueVO;
import com.ulticode.modules.moderation.dto.PerformModerationActionDTO;
import com.ulticode.modules.moderation.dto.ReviewAppealDTO;
import com.ulticode.modules.moderation.entity.Appeal;
import com.ulticode.modules.moderation.mapper.AppealMapper;
import com.ulticode.modules.moderation.port.ModerationWritePort;
import com.ulticode.modules.moderation.projection.ModerationProjection;
import com.ulticode.modules.moderation.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Thin delegate facade over {@link ModerationWritePort}.
 *
 * <p>The moderation state machine — every queue mutation, the report intake,
 * the appeal lifecycle, and the action-sink callbacks the
 * {@link ModerationActionHandler} strategies invoke — now lives behind
 * {@link ModerationWritePort} (see its javadoc for why it is a deep module).
 * This service forwards the eight write paths verbatim and keeps only the
 * authorisation-guarded {@code getAppeal} read, which is a read-with-a-guard
 * rather than a state change and so stays on the facade next to the caller.
 *
 * <p>Behaviour is unchanged: {@code ModerationController} and any cross-module
 * caller depending on {@link ModerationService} see the same contract. The
 * {@code @Transactional} boundaries move with the write paths onto the port
 * adapter ({@code DefaultModerationWritePort}); this facade adds no
 * transaction of its own.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    private final ModerationWritePort moderationWritePort;
    private final ModerationProjection moderationProjection;
    private final AppealMapper appealMapper;
    private final CurrentUserProvider currentUserProvider;

    // ==================== Queue Operations ====================

    @Override
    public ModerationQueueVO claimItem(String id, String moderatorId) {
        return moderationWritePort.claimItem(id, moderatorId);
    }

    @Override
    public ModerationQueueVO assignItem(String id, String moderatorId, String assignedTo) {
        return moderationWritePort.assignItem(id, moderatorId, assignedTo);
    }

    @Override
    public ModerationQueueVO unassignItem(String id, String moderatorId) {
        return moderationWritePort.unassignItem(id, moderatorId);
    }

    @Override
    public ModerationQueueVO performAction(String id, PerformModerationActionDTO dto, String moderatorId) {
        return moderationWritePort.performAction(id, dto, moderatorId);
    }

    @Override
    public BatchActionResultVO batchAction(BatchModerationActionDTO dto, String moderatorId) {
        return moderationWritePort.batchAction(dto, moderatorId);
    }

    // ==================== Report Operations ====================

    @Override
    public void createReport(CreateReportDTO dto, String reporterId) {
        moderationWritePort.createReport(dto, reporterId);
    }

    // ==================== Appeal Operations ====================

    @Override
    public AppealVO createAppeal(CreateAppealDTO dto, String appellantId) {
        return moderationWritePort.createAppeal(dto, appellantId);
    }

    @Override
    public AppealVO getAppeal(String id, String currentUserId) {
        Appeal appeal = appealMapper.selectById(id);
        if (appeal == null) {
            throw new BusinessException(ErrorCode.MODERATION_APPEAL_NOT_FOUND);
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
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return moderationProjection.toAppealVO(appeal);
    }

    @Override
    public AppealVO reviewAppeal(String id, ReviewAppealDTO dto, String moderatorId) {
        return moderationWritePort.reviewAppeal(id, dto, moderatorId);
    }
}
