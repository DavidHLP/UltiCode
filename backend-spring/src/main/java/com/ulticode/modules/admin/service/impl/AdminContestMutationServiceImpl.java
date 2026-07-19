package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.PartialUpdate;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.port.AdminContestReadPort;
import com.ulticode.modules.admin.port.ContestAnnouncementPushPort;
import com.ulticode.modules.admin.projection.AdminContestProjection;
import com.ulticode.modules.admin.service.AdminContestMutationService;
import com.ulticode.modules.contest.dto.AddContestProblemDTO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.websocket.contest.dto.AnnouncementPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link AdminContestMutationService} &mdash; the admin
 * contest write state machine.
 *
 * <p>Owns every contest mutation the admin module performs: create, update,
 * soft-delete, start, end, announcement CRUD, and problem-add. Each write
 * enforces its status guard / existence invariant, performs the mapper write,
 * and publishes the {@link AuditContext} old/new values the
 * {@link Audited @Audited} aspect records.
 *
 * <p><b>Side-effect adapters beside it (not inside it):</b>
 * <ul>
 *   <li>{@link ContestAnnouncementPushPort} &mdash; the WebSocket announcement
 *       broadcast (best-effort, D-12). The persisted announcement row is the
 *       durable record; the push is the live signal.</li>
 *   <li>{@link AdminContestReadPort} &mdash; the cross-module problem-count
 *       read (start guard, add-problem index). Contest-problem writes stay
 *       here because they are admin's own CRUD targets.</li>
 *   <li>{@link AdminContestProjection} &mdash; the entity-to-VO shape rule
 *       and the URL-slug generator. Write paths that return an
 *       {@link AdminContestVO} call
 *       {@link AdminContestProjection#toAdminVO} so the controller contract
 *       is unchanged; the shape rule simply no longer lives in the write
 *       module.</li>
 * </ul>
 *
 * <p>This completes the write side of ADR-0011 Stage 3 without reopening the
 * projection (read) decision: the read contract on
 * {@link com.ulticode.modules.admin.service.AdminContestService} is untouched.
 *
 * <p>Behavior is preserved exactly from the legacy single
 * {@code AdminContestServiceImpl}: same status guards, error codes,
 * audit-context payloads, slug-conflict catch (P0-5 / H2), and
 * fire-and-forget announcement push.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminContestMutationServiceImpl implements AdminContestMutationService {

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestAnnouncementMapper contestAnnouncementMapper;
    private final ContestAnnouncementPushPort contestAnnouncementPushPort;
    private final AdminContestReadPort contestReadPort;
    private final Clock clock;
    private final AdminContestProjection adminContestProjection;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, captureOldState = false)
    public AdminContestVO createContest(CreateContestDTO dto, String userId) {
        Contest contest = new Contest();
        contest.setTitle(dto.getTitle());
        contest.setDescription(dto.getDescription());
        contest.setStartTime(dto.getStartTime());
        contest.setDurationMinutes(dto.getDuration());
        contest.setEndTime(dto.getStartTime().plusMinutes(dto.getDuration()));
        contest.setMaxParticipants(dto.getMaxParticipants());
        contest.setIsVisible(dto.getIsPublished() != null ? dto.getIsPublished() : false);
        contest.setCreatedBy(userId);
        contest.setStatus(ContestStatus.UPCOMING.name());
        contest.setRegisteredCount(0);
        contest.setParticipantCount(0);
        contest.setSubmissionCount(0);
        contest.setIsDeleted(false);

        String slug = (dto.getSlug() != null && !dto.getSlug().isBlank())
                ? dto.getSlug().trim()
                : adminContestProjection.generateSlug(dto.getTitle());
        contest.setSlug(slug);

        try {
            contestMapper.insert(contest);
        } catch (DataIntegrityViolationException e) {
            // P0-5 / H2: uk_contest_slug rejected. Catch the parent class so we
            // surface 409 regardless of whether the driver throws
            // DuplicateKeyException (mysql-connector-j) or the parent
            // DataIntegrityViolationException (some MariaDB / older drivers).
            throw new BusinessException(ErrorCode.CONTEST_SLUG_EXISTS,
                    "Contest slug '" + slug + "' already exists");
        }

        // Bulk-insert scored contest problems atomically with the contest row
        // (C01 deepening). buildScoredContestProblems concentrates the score,
        // index, and base-score rule shared by create and update; a failure
        // here rolls back the whole @Transactional create so no partial
        // contest persists.
        List<ContestProblem> contestProblems = buildScoredContestProblems(
                contest.getId(), dto.getProblems(), dto.getProblemIds());
        if (!contestProblems.isEmpty()) {
            contestProblemMapper.batchInsert(contestProblems);
        }

        AuditContext.setNewValues(Map.of("title", contest.getTitle(), "slug", contest.getSlug()));
        AuditContext.setUserId(userId);

        log.info("Admin created contest: {} by user {}", contest.getId(), userId);
        return adminContestProjection.toAdminVO(contest);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, entityIdFrom = "id")
    public AdminContestVO updateContest(String id, UpdateContestDTO dto) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        if (!ContestStatus.UPCOMING.name().equalsIgnoreCase(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);
        }

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("title", contest.getTitle());
        oldValues.put("status", contest.getStatus());
        oldValues.put("description", contest.getDescription());
        oldValues.put("startTime", contest.getStartTime());
        oldValues.put("durationMinutes", contest.getDurationMinutes());
        oldValues.put("maxParticipants", contest.getMaxParticipants());
        oldValues.put("isVisible", contest.getIsVisible());

        PartialUpdate.setIfPresentText(dto, UpdateContestDTO::getTitle, contest::setTitle);
        PartialUpdate.setIfPresentText(dto, UpdateContestDTO::getDescription, contest::setDescription);
        PartialUpdate.setIfPresent(dto, UpdateContestDTO::getStartTime, contest::setStartTime);
        // Duration has a coupled side effect (recompute endTime) so it stays inline.
        if (dto.getDuration() != null) {
            contest.setDurationMinutes(dto.getDuration());
            contest.setEndTime(dto.getStartTime() != null
                    ? dto.getStartTime().plusMinutes(dto.getDuration())
                    : contest.getStartTime().plusMinutes(dto.getDuration()));
        }
        PartialUpdate.setIfPresent(dto, UpdateContestDTO::getMaxParticipants, contest::setMaxParticipants);
        PartialUpdate.setIfPresent(dto, UpdateContestDTO::getIsPublished, contest::setIsVisible);

        // Replace contest problems when scored problems or legacy problemIds
        // are provided. The delete + scored bulk-insert runs in this
        // @Transactional update, so a mid-list failure rolls back the whole
        // update (no half-replaced problem set).
        if (dto.getProblems() != null || dto.getProblemIds() != null) {
            contestProblemMapper.deleteByContestId(id);
            List<ContestProblem> contestProblems = buildScoredContestProblems(
                    id, dto.getProblems(), dto.getProblemIds());
            if (!contestProblems.isEmpty()) {
                contestProblemMapper.batchInsert(contestProblems);
            }
        }

        contestMapper.updateById(contest);

        AuditContext.setOldValues(oldValues);
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("title", contest.getTitle());
        newValues.put("status", contest.getStatus());
        AuditContext.setNewValues(newValues);

        log.info("Admin updated contest: {}", id);
        return adminContestProjection.toAdminVO(contest);
    }

    @Override
    @Audited(action = AuditVocabulary.DELETE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, entityIdFrom = "id")
    public void deleteContest(String id) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        String status = contest.getStatus();
        if (!ContestStatus.UPCOMING.name().equals(status)
                && !ContestStatus.FINISHED.name().equals(status)) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        contest.setIsDeleted(true);
        contest.setDeletedAt(LocalDateTime.now(clock));
        contest.setDeletedBy(currentUserProvider.getCurrentUserId());
        contestMapper.updateById(contest);

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("title", contest.getTitle());
        oldValues.put("status", contest.getStatus());
        AuditContext.setOldValues(oldValues);
        AuditContext.setNewValues(null);

        log.info("Admin deleted contest: {}", id);
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, entityIdFrom = "id")
    public AdminContestVO startContest(String id) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        if (!ContestStatus.UPCOMING.name().equalsIgnoreCase(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_STARTED);
        }

        long problemCount = contestReadPort.countProblemsByContestId(id);
        if (problemCount == 0) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        contest.setStatus(ContestStatus.RUNNING.name());
        contestMapper.updateById(contest);

        AuditContext.setOldValues(Map.of("status", ContestStatus.UPCOMING.name()));
        AuditContext.setNewValues(Map.of("status", ContestStatus.RUNNING.name()));

        log.info("Admin started contest: {}", id);
        return adminContestProjection.toAdminVO(contest);
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, entityIdFrom = "id")
    public AdminContestVO endContest(String id) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        if (!ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ENDED);
        }

        contest.setStatus(ContestStatus.FINISHED.name());
        contestMapper.updateById(contest);

        AuditContext.setOldValues(Map.of("status", ContestStatus.RUNNING.name()));
        AuditContext.setNewValues(Map.of("status", ContestStatus.FINISHED.name()));

        log.info("Admin ended contest: {}", id);
        return adminContestProjection.toAdminVO(contest);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_CONTEST_ANNOUNCEMENT, entityType = AuditVocabulary.ENTITY_CONTEST_ANNOUNCEMENT, captureOldState = false)
    public ContestAnnouncement createAnnouncement(String contestId, String title, String content, Boolean isPinned) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        ContestAnnouncement announcement = new ContestAnnouncement();
        announcement.setContestId(contestId);
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setIsPinned(isPinned != null ? isPinned : false);

        contestAnnouncementMapper.insert(announcement);

        // WebSocket push (D-12) via ContestAnnouncementPushPort
        contestAnnouncementPushPort.emitAnnouncement(contestId,
                AnnouncementPayload.of(announcement.getId(), contestId, title, content));

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("title", title);
        newValues.put("contestId", contestId);
        AuditContext.setNewValues(newValues);

        log.info("Admin created announcement {} for contest {}", announcement.getId(), contestId);
        return announcement;
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_CONTEST_ANNOUNCEMENT, entityType = AuditVocabulary.ENTITY_CONTEST_ANNOUNCEMENT, entityIdFrom = "announcementId")
    public ContestAnnouncement updateAnnouncement(String contestId, String announcementId, String title, String content, Boolean isPinned) {
        ContestAnnouncement announcement = contestAnnouncementMapper.findByContestIdAndId(contestId, announcementId);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("title", announcement.getTitle());
        oldValues.put("isPinned", announcement.getIsPinned());

        if (title != null) {
            announcement.setTitle(title);
        }
        if (content != null) {
            announcement.setContent(content);
        }
        if (isPinned != null) {
            announcement.setIsPinned(isPinned);
        }

        contestAnnouncementMapper.updateById(announcement);

        AuditContext.setOldValues(oldValues);
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("title", announcement.getTitle());
        newValues.put("isPinned", announcement.getIsPinned());
        AuditContext.setNewValues(newValues);

        log.info("Admin updated announcement {} for contest {}", announcementId, contestId);
        return announcement;
    }

    @Override
    @Audited(action = AuditVocabulary.DELETE_CONTEST_ANNOUNCEMENT, entityType = AuditVocabulary.ENTITY_CONTEST_ANNOUNCEMENT, entityIdFrom = "announcementId")
    public void deleteAnnouncement(String contestId, String announcementId) {
        ContestAnnouncement announcement = contestAnnouncementMapper.findByContestIdAndId(contestId, announcementId);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        contestAnnouncementMapper.deleteById(announcementId);

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("title", announcement.getTitle());
        oldValues.put("contestId", contestId);
        AuditContext.setOldValues(oldValues);
        AuditContext.setNewValues(null);

        log.info("Admin deleted announcement {} for contest {}", announcementId, contestId);
    }

    // =========================================================================
    // Shared contest-problem shaping
    // =========================================================================

    /**
     * Build the scored {@link ContestProblem} list for a contest from the
     * request's {@code problems} (preferred) or the legacy {@code problemIds}
     * fallback. Concentrates the score-shaping, problem-index, and base-score
     * rule so create and the update replacement share one transactional
     * bulk-insert path.
     *
     * <p>Each problem receives the author's score (default {@code 100}) in
     * both {@code score} &mdash; the field {@link ContestAdjudicationServiceImpl}
     * reads for ranking &mdash; and {@code baseScore}, a letter problem-index
     * ({@code "A"+i}, matching the live add-problem seam in
     * {@link ContestServiceImpl}), and zeroed counters. The previous
     * inline paths wrote {@code score=0} and a {@code "Q"+n} index, which was
     * either dead (create bulk-insert was never reached by any caller) or
     * inconsistent with the ranking reader.
     *
     * @param contestId  the contest the problems attach to
     * @param problems   scored attachments; wins over {@code problemIds} when present
     * @param problemIds legacy unscored ids; each attaches with the default score
     * @return the shaped contest-problem list (empty when neither input is given)
     */
    private List<ContestProblem> buildScoredContestProblems(
            String contestId, List<AddContestProblemDTO> problems, List<Long> problemIds) {
        List<AddContestProblemDTO> source;
        if (problems != null && !problems.isEmpty()) {
            source = problems;
        } else if (problemIds != null && !problemIds.isEmpty()) {
            source = new ArrayList<>(problemIds.size());
            for (Long pid : problemIds) {
                AddContestProblemDTO item = new AddContestProblemDTO();
                item.setProblemId(pid);
                source.add(item);
            }
        } else {
            return List.of();
        }

        List<ContestProblem> list = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            AddContestProblemDTO item = source.get(i);
            int score = item.getScore() != null ? item.getScore() : DEFAULT_PROBLEM_SCORE;
            ContestProblem cp = new ContestProblem();
            cp.setContestId(contestId);
            cp.setProblemId(item.getProblemId());
            cp.setProblemIndex(problemIndex(i));
            cp.setScore(score);
            cp.setBaseScore(score);
            cp.setSolvedCount(0);
            cp.setSubmissionCount(0);
            list.add(cp);
        }
        return list;
    }

    /**
     * Default per-problem score when the author did not supply one, applied to
     * both {@code score} (the field {@link ContestAdjudicationServiceImpl}
     * reads for ranking) and {@code baseScore}.
     */
    private static final int DEFAULT_PROBLEM_SCORE = 100;

    /**
     * Compute a stable, human-readable problem index for the zero-based slot
     * {@code i}. Slots 0&ndash;25 map to the single letters A&ndash;Z; slot
     * 26+ (a contest with more than 26 problems) falls back to a deterministic
     * {@code P<i+1>} label instead of the silent non-letter overflow that a
     * bare {@code (char) ('A' + i)} would produce.
     */
    private static String problemIndex(int i) {
        if (i >= 0 && i < 26) {
            return String.valueOf((char) ('A' + i));
        }
        return "P" + (i + 1);
    }
}
