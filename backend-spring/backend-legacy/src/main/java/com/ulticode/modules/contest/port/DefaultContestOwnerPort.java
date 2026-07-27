package com.ulticode.modules.contest.port;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * P3-OWNER-001-B: default {@link ContestOwnerPort} implementation.
 * Lives in the contest module (the OWNER). Uses the contest
 * module's own mappers for the actual writes. The legacy admin
 * code never imports this class directly; it injects the port
 * interface so the seam is a real abstraction.
 *
 * <p>Behavior is preserved exactly from the legacy
 * {@code AdminContestMutationServiceImpl}: same status guards,
 * same error codes, same contest-problem shaping (score, index,
 * base-score rule), same slug generation (H1-3), same
 * announcement push responsibility (admin side).
 *
 * <p>Status guard rationale: start/end guards check the in-memory
 * contest state (loaded via the existing {@link ContestMapper#selectById}
 * read method, allowed per ADR-0011) and reject the write with
 * the same {@code ErrorCode.CONTEST_NOT_STARTED} / {@code CONTEST_ENDED}
 * that the admin path used.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultContestOwnerPort implements ContestOwnerPort {

    /** Default per-problem score when the author did not supply one. */
    private static final int DEFAULT_PROBLEM_SCORE = 100;

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestAnnouncementMapper contestAnnouncementMapper;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    // ─── Contest row writes ──────────────────────────────────────

    @Override
    @Transactional
    public String createContest(CreateContestDTO command, String userId) {
        final Contest contest = new Contest();
        contest.setId(uuidGenerator.newId());
        contest.setTitle(command.getTitle());
        contest.setDescription(command.getDescription());
        contest.setStartTime(command.getStartTime());
        contest.setDurationMinutes(command.getDuration());
        contest.setEndTime(command.getStartTime().plusMinutes(command.getDuration()));
        contest.setMaxParticipants(command.getMaxParticipants());
        contest.setIsVisible(command.getIsPublished() != null && command.getIsPublished());
        contest.setCreatedBy(userId);
        contest.setStatus(ContestStatus.UPCOMING.name());
        contest.setRegisteredCount(0);
        contest.setParticipantCount(0);
        contest.setSubmissionCount(0);
        contest.setIsDeleted(false);

        final String slug = StringUtils.hasText(command.getSlug())
                ? command.getSlug().trim()
                : generateSlug(command.getTitle());
        contest.setSlug(slug);

        try {
            contestMapper.insert(contest);
        } catch (DataIntegrityViolationException e) {
            // P0-5 / H2: uk_contest_slug rejected.
            throw new BusinessException(ErrorCode.CONTEST_SLUG_EXISTS,
                    "Contest slug '" + slug + "' already exists");
        }

        // Bulk-insert scored contest problems atomically with the contest
        // row. A failure here rolls back the whole @Transactional create.
        final List<ContestProblem> contestProblems = buildScoredContestProblems(
                contest.getId(), command.getProblems(), command.getProblemIds());
        if (!contestProblems.isEmpty()) {
            contestProblemMapper.batchInsert(contestProblems);
        }

        log.info("ContestOwnerPort.createContest id={} by user={}", contest.getId(), userId);
        return contest.getId();
    }

    @Override
    @Transactional
    public void updateContest(String id, UpdateContestDTO command) {
        final Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        if (!ContestStatus.UPCOMING.name().equalsIgnoreCase(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);
        }

        if (command.getTitle() != null) {
            contest.setTitle(command.getTitle());
        }
        if (command.getDescription() != null) {
            contest.setDescription(command.getDescription());
        }
        if (command.getStartTime() != null) {
            contest.setStartTime(command.getStartTime());
        }
        // Duration has a coupled side effect (recompute endTime) so it
        // stays inline. We don't have a setIfPresent helper here; the
        // explicit guard mirrors the original admin logic.
        if (command.getDuration() != null) {
            contest.setDurationMinutes(command.getDuration());
            final LocalDateTime base = command.getStartTime() != null
                    ? command.getStartTime()
                    : contest.getStartTime();
            contest.setEndTime(base.plusMinutes(command.getDuration()));
        }
        if (command.getMaxParticipants() != null) {
            contest.setMaxParticipants(command.getMaxParticipants());
        }
        if (command.getIsPublished() != null) {
            contest.setIsVisible(command.getIsPublished());
        }

        // Replace contest problems when scored problems or legacy
        // problemIds are provided. The delete + scored bulk-insert
        // runs in this @Transactional update, so a mid-list failure
        // rolls back the whole update.
        if (command.getProblems() != null || command.getProblemIds() != null) {
            contestProblemMapper.deleteByContestId(id);
            final List<ContestProblem> contestProblems = buildScoredContestProblems(
                    id, command.getProblems(), command.getProblemIds());
            if (!contestProblems.isEmpty()) {
                contestProblemMapper.batchInsert(contestProblems);
            }
        }

        contestMapper.updateById(contest);

        log.info("ContestOwnerPort.updateContest id={}", id);
    }

    @Override
    @Transactional
    public void deleteContest(String id, String deletedBy) {
        final Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        final String status = contest.getStatus();
        if (!ContestStatus.UPCOMING.name().equals(status)
                && !ContestStatus.FINISHED.name().equals(status)) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        contest.setIsDeleted(true);
        contest.setDeletedAt(LocalDateTime.now(clock));
        contest.setDeletedBy(deletedBy);
        contestMapper.updateById(contest);

        log.info("ContestOwnerPort.deleteContest id={} by user={}", id, deletedBy);
    }

    @Override
    @Transactional
    public void startContest(String id) {
        final Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        if (!ContestStatus.UPCOMING.name().equalsIgnoreCase(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_STARTED);
        }

        // The original admin path asked the read port
        // (AdminContestReadPort#countProblemsByContestId) for the
        // problem count. The port's read path (selectList on
        // ContestProblemMapper) is a sanctioned admin read per
        // ADR-0011; the port implementation goes through its own
        // mapper here so the port owns the invariant.
        final List<ContestProblem> existing =
                contestProblemMapper.findByContestId(id);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        contest.setStatus(ContestStatus.RUNNING.name());
        contestMapper.updateById(contest);

        log.info("ContestOwnerPort.startContest id={}", id);
    }

    @Override
    @Transactional
    public void endContest(String id) {
        final Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        if (!ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ENDED);
        }

        contest.setStatus(ContestStatus.FINISHED.name());
        contestMapper.updateById(contest);

        log.info("ContestOwnerPort.endContest id={}", id);
    }

    // ─── Announcement writes ────────────────────────────────────

    @Override
    @Transactional
    public String createAnnouncement(String contestId, String title, String content, Boolean isPinned) {
        final Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        final ContestAnnouncement announcement = new ContestAnnouncement();
        announcement.setId(uuidGenerator.newId());
        announcement.setContestId(contestId);
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setIsPinned(isPinned != null && isPinned);

        contestAnnouncementMapper.insert(announcement);

        log.info("ContestOwnerPort.createAnnouncement id={} for contest={}",
                announcement.getId(), contestId);
        return announcement.getId();
    }

    @Override
    @Transactional
    public void updateAnnouncement(String contestId, String announcementId,
                                  String title, String content, Boolean isPinned) {
        final ContestAnnouncement announcement = contestAnnouncementMapper
                .findByContestIdAndId(contestId, announcementId);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

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

        log.info("ContestOwnerPort.updateAnnouncement id={} for contest={}",
                announcementId, contestId);
    }

    @Override
    @Transactional
    public void deleteAnnouncement(String contestId, String announcementId) {
        final ContestAnnouncement announcement = contestAnnouncementMapper
                .findByContestIdAndId(contestId, announcementId);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        contestAnnouncementMapper.deleteById(announcementId);

        log.info("ContestOwnerPort.deleteAnnouncement id={} for contest={}",
                announcementId, contestId);
    }

    // ─── Private helpers ───────────────────────────────────────

    /**
     * Build the scored {@link ContestProblem} list for a contest
     * from the request's {@code problems} (preferred, scored) or
     * the legacy {@code problemIds} fallback. Each problem
     * receives the author's score (default 100) in both
     * {@code score} and {@code baseScore}, a letter problem-index
     * ({@code "A"+i}, matching the live add-problem seam in
     * {@code ContestServiceImpl}), and zeroed counters.
     */
    private List<ContestProblem> buildScoredContestProblems(
            String contestId, List<AddContestProblemDTO> problems, List<Long> problemIds) {
        final List<AddContestProblemDTO> source;
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

        final List<ContestProblem> list = new ArrayList<>(source.size());
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
     * Compute a stable, human-readable problem index for the
     * zero-based slot {@code i}. Slots 0-25 map to A-Z; slot 26+
     * falls back to {@code P<i+1>}. Mirrors the original admin
     * helper.
     */
    private static String problemIndex(int i) {
        if (i >= 0 && i < 26) {
            return String.valueOf((char) ('A' + i));
        }
        return "P" + (i + 1);
    }

    /**
     * Generate a URL-friendly slug from the title. Mirrors the
     * original {@code AdminContestProjection.generateSlug} helper
     * (the projection is a read-side seam; the port owns the
     * write-side generation). When the title is blank, fall back
     * to a short uuid-based slug to satisfy the NOT NULL column
     * constraint.
     */
    private String generateSlug(String title) {
        if (title == null || title.isBlank()) {
            return "contest-" + uuidGenerator.newId().substring(0, 8);
        }
        final String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "contest-" + uuidGenerator.newId().substring(0, 8) : slug;
    }
}
