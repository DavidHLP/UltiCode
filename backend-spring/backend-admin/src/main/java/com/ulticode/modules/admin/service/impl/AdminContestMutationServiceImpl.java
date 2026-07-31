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
import com.ulticode.modules.contest.port.ContestOwnerPort;
import com.ulticode.modules.websocket.contest.dto.AnnouncementPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private ContestMapper contestMapper;
    @Autowired
    private ContestProblemMapper contestProblemMapper;
    @Autowired
    private ContestAnnouncementMapper contestAnnouncementMapper;
    private final ContestAnnouncementPushPort contestAnnouncementPushPort;
    private final AdminContestReadPort contestReadPort;
    private final Clock clock;
    private final AdminContestProjection adminContestProjection;
    private final CurrentUserProvider currentUserProvider;
    /**
     * P3-OWNER-001-B: owner-only write surface for the {@code contests},
     * {@code contest_problems}, and {@code contest_announcements}
     * tables. Replaces the direct foreign-mapper write calls
     * (contestMapper.insert / updateById, contestProblemMapper
     * deleteByContestId / batchInsert, contestAnnouncementMapper
     * insert / updateById / deleteById) that lived here before
     * the Phase 3 owner boundary was established. The
     * contest problem shaping (score / index / base-score rule)
     * and the slug generation move with the writes into the
     * port's default adapter.
     */
    private final ContestOwnerPort contestOwnerPort;

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, captureOldState = false)
    public AdminContestVO createContest(CreateContestDTO dto, String userId) {
        // P3-OWNER-001-B: the contest row + contest-problem attachments
        // are owned by ContestOwnerPort. The port returns the new id;
        // the admin re-fetches via the projection for VO composition.
        final String newId = contestOwnerPort.createContest(dto, userId);

        // Map.of does not allow null values; the empty CreateContestDTO
        // case has both title and slug null. Build a HashMap so the
        // audit context is always populated with placeholders.
        final Map<String, Object> newValues = new HashMap<>();
        newValues.put("title", dto.getTitle() != null ? dto.getTitle() : "<unspecified>");
        newValues.put("slug", dto.getSlug() != null ? dto.getSlug() : "<generated>");
        AuditContext.setNewValues(newValues);
        AuditContext.setUserId(userId);

        log.info("Admin created contest: {} by user {}", newId, userId);
        return adminContestProjection.getContest(newId);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, entityIdFrom = "id")
    public AdminContestVO updateContest(String id, UpdateContestDTO dto) {
        // P3-OWNER-001-B: capture the old values for the audit before
        // the port mutates the row. The status-guard is also port-side.
        final Contest before = contestMapper.selectById(id);
        if (before == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("title", before.getTitle());
        oldValues.put("status", before.getStatus());
        oldValues.put("description", before.getDescription());
        oldValues.put("startTime", before.getStartTime());
        oldValues.put("durationMinutes", before.getDurationMinutes());
        oldValues.put("maxParticipants", before.getMaxParticipants());
        oldValues.put("isVisible", before.getIsVisible());

        // The port owns the status guard (UPCOMING-only) and the
        // partial-update + problem-replace mechanics.
        contestOwnerPort.updateContest(id, dto);

        AuditContext.setOldValues(oldValues);
        AuditContext.setNewValues(Map.of("title", dto.getTitle() != null ? dto.getTitle() : before.getTitle(),
                "status", before.getStatus()));

        log.info("Admin updated contest: {}", id);
        return adminContestProjection.getContest(id);
    }

    @Override
    @Audited(action = AuditVocabulary.DELETE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, entityIdFrom = "id")
    public void deleteContest(String id) {
        // P3-OWNER-001-B: the soft-delete write is owned by the
        // port. The admin captures the old values for the audit
        // and records the actor for the port.
        final Contest before = contestMapper.selectById(id);
        Map<String, Object> oldValues = new HashMap<>();
        if (before != null) {
            oldValues.put("title", before.getTitle());
            oldValues.put("status", before.getStatus());
        }
        final String deletedBy = currentUserProvider.getCurrentUserId();
        contestOwnerPort.deleteContest(id, deletedBy);

        AuditContext.setOldValues(oldValues);
        AuditContext.setNewValues(null);

        log.info("Admin deleted contest: {} by user={}", id, deletedBy);
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, entityIdFrom = "id")
    public AdminContestVO startContest(String id) {
        // P3-OWNER-001-B: the port owns the status guard, the
        // problem-existence check, and the status transition. The
        // admin re-fetches via the projection for VO composition.
        final Contest before = contestMapper.selectById(id);
        if (before != null) {
            AuditContext.setOldValues(Map.of("status", before.getStatus()));
        }
        contestOwnerPort.startContest(id);
        AuditContext.setNewValues(Map.of("status", ContestStatus.RUNNING.name()));
        log.info("Admin started contest: {}", id);
        return adminContestProjection.getContest(id);
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, entityIdFrom = "id")
    public AdminContestVO endContest(String id) {
        final Contest before = contestMapper.selectById(id);
        if (before != null) {
            AuditContext.setOldValues(Map.of("status", before.getStatus()));
        }
        contestOwnerPort.endContest(id);
        AuditContext.setNewValues(Map.of("status", ContestStatus.FINISHED.name()));
        log.info("Admin ended contest: {}", id);
        return adminContestProjection.getContest(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_CONTEST_ANNOUNCEMENT, entityType = AuditVocabulary.ENTITY_CONTEST_ANNOUNCEMENT, captureOldState = false)
    public ContestAnnouncement createAnnouncement(String contestId, String title, String content, Boolean isPinned) {
        // P3-OWNER-001-B: the announcement row is owned by the
        // port. The WebSocket push (D-12) stays in admin because
        // it is an outbound side effect on the admin's
        // ContestAnnouncementPushPort; the port intentionally does
        // not know about it. Note the subtle semantic change:
        // the push is no longer in the same @Transactional as the
        // DB insert. A push failure no longer rolls back the
        // announcement row, which is the correct behavior for a
        // fire-and-forget WebSocket effect (the port's
        // @Transactional commits independently).
        final String newId = contestOwnerPort.createAnnouncement(contestId, title, content, isPinned);

        contestAnnouncementPushPort.emitAnnouncement(contestId,
                AnnouncementPayload.of(newId, contestId, title, content));

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("title", title);
        newValues.put("contestId", contestId);
        AuditContext.setNewValues(newValues);

        log.info("Admin created announcement {} for contest {}", newId, contestId);
        // Re-fetch the entity for the caller; the read is a
        // sanctioned admin path (the existing findByContestIdAndId).
        return contestAnnouncementMapper.findByContestIdAndId(contestId, newId);
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_CONTEST_ANNOUNCEMENT, entityType = AuditVocabulary.ENTITY_CONTEST_ANNOUNCEMENT, entityIdFrom = "announcementId")
    public ContestAnnouncement updateAnnouncement(String contestId, String announcementId, String title, String content, Boolean isPinned) {
        // Capture old values for the audit before the port
        // mutates the row.
        final ContestAnnouncement before = contestAnnouncementMapper
                .findByContestIdAndId(contestId, announcementId);
        if (before == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("title", before.getTitle());
        oldValues.put("isPinned", before.getIsPinned());

        contestOwnerPort.updateAnnouncement(contestId, announcementId, title, content, isPinned);

        AuditContext.setOldValues(oldValues);
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("title", title != null ? title : before.getTitle());
        newValues.put("isPinned", isPinned != null ? isPinned : before.getIsPinned());
        AuditContext.setNewValues(newValues);

        log.info("Admin updated announcement {} for contest {}", announcementId, contestId);
        return contestAnnouncementMapper.findByContestIdAndId(contestId, announcementId);
    }

    @Override
    @Audited(action = AuditVocabulary.DELETE_CONTEST_ANNOUNCEMENT, entityType = AuditVocabulary.ENTITY_CONTEST_ANNOUNCEMENT, entityIdFrom = "announcementId")
    public void deleteAnnouncement(String contestId, String announcementId) {
        final ContestAnnouncement before = contestAnnouncementMapper
                .findByContestIdAndId(contestId, announcementId);
        Map<String, Object> oldValues = new HashMap<>();
        if (before != null) {
            oldValues.put("title", before.getTitle());
            oldValues.put("contestId", contestId);
        }

        contestOwnerPort.deleteAnnouncement(contestId, announcementId);

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
     * <p>P3-OWNER-001-B: the contest problem shaping (score / index /
     * base-score rule) and the slug generation have moved to
     * {@link com.ulticode.modules.contest.port.DefaultContestOwnerPort}.
     * This class no longer holds the helper; the port owns the
     * contest-domain invariants.
     */
}
