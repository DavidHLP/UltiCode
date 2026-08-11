package com.ulticode.modules.contest.port;
import com.ulticode.app.api.command.AddContestProblemCommand;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.RemoveContestProblemCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestProblemAdminDTO;
import com.ulticode.app.api.dto.ContestProblemInputDTO;
import com.ulticode.app.error.ContestErrorCode;
import com.ulticode.common.error.BaseErrorCode;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.service.ContestLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
 * the same {@code ContestErrorCode.CONTEST_NOT_STARTED} / {@code CONTEST_ENDED}
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
    private final ContestLifecycleService contestLifecycleService;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    // ─── Contest row writes ──────────────────────────────────────

    @Override
    @Transactional
    public String createContest(CreateContestCommand command) {
        final Contest contest = new Contest();
        contest.setId(uuidGenerator.newId());
        contest.setTitle(command.title());
        contest.setDescription(command.description());
        final LocalDateTime startTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(command.startEpochMs()), ZoneOffset.UTC);
        contest.setStartTime(startTime);
        contest.setDurationMinutes(command.durationMinutes());
        contest.setEndTime(startTime.plusMinutes(command.durationMinutes()));
        contest.setMaxParticipants(command.maxParticipants());
        contest.setIsVisible(command.isPublished() != null && command.isPublished());
        contest.setCreatedBy(command.creatorAccountId());
        contest.setContestType(command.contestType());
        contest.setScoringMode(command.scoringMode() != null ? command.scoringMode() : "SCORE");
        contest.setScoringRuleId(command.scoringRuleId());
        contest.setStatus(ContestStatus.UPCOMING.name());
        contest.setRegisteredCount(0);
        contest.setParticipantCount(0);
        contest.setSubmissionCount(0);
        contest.setIsDeleted(false);

        final String slug = StringUtils.hasText(command.slug())
                ? command.slug().trim()
                : generateSlug(command.title());
        contest.setSlug(slug);

        try {
            contestMapper.insert(contest);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ContestErrorCode.CONTEST_SLUG_EXISTS,
                    "Contest slug '" + slug + "' already exists");
        }

        final List<ContestProblem> contestProblems = buildScoredContestProblems(
                contest.getId(), command.problems(), command.problemIds());
        if (!contestProblems.isEmpty()) {
            contestProblemMapper.batchInsert(contestProblems);
        }
        return contest.getId();
    }

    @Override
    @Transactional
    public void updateContest(UpdateContestCommand command) {
        final Contest contest = lockContestOrThrow(command.contestId());
        if (!ContestStatus.UPCOMING.name().equalsIgnoreCase(contest.getStatus())) {
            throw new BusinessException(ContestErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);
        }

        if (command.title() != null) {
            contest.setTitle(command.title());
        }
        if (command.description() != null) {
            contest.setDescription(command.description());
        }
        final LocalDateTime requestedStart = command.startEpochMs() == null
                ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(command.startEpochMs()), ZoneOffset.UTC);
        if (requestedStart != null) {
            contest.setStartTime(requestedStart);
        }
        if (command.durationMinutes() != null) {
            contest.setDurationMinutes(command.durationMinutes());
            final LocalDateTime base = requestedStart != null ? requestedStart : contest.getStartTime();
            contest.setEndTime(base.plusMinutes(command.durationMinutes()));
        }
        if (command.maxParticipants() != null) {
            contest.setMaxParticipants(command.maxParticipants());
        }
        if (command.isPublished() != null) {
            contest.setIsVisible(command.isPublished());
        }
        if (command.slug() != null) {
            contest.setSlug(command.slug().trim());
        }
        if (command.contestType() != null) {
            contest.setContestType(command.contestType());
        }
        if (command.scoringRuleId() != null) {
            contest.setScoringRuleId(command.scoringRuleId());
        }

        if (command.problems() != null || command.problemIds() != null) {
            contestProblemMapper.deleteByContestId(command.contestId());
            final List<ContestProblem> contestProblems = buildScoredContestProblems(
                    command.contestId(), command.problems(), command.problemIds());
            if (!contestProblems.isEmpty()) {
                contestProblemMapper.batchInsert(contestProblems);
            }
        }
        contestMapper.updateById(contest);
    }

    @Override
    @Transactional
    public void deleteContest(String id, String deletedBy) {
        contestLifecycleService.deleteContestCascade(id, deletedBy);
        log.info("ContestOwnerPort.deleteContest id={} by user={}", id, deletedBy);
    }

    @Override
    @Transactional
    public void startContest(String id) {
        final Contest contest = lockContestOrThrow(id);
        if (contest == null) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_FOUND);
        }

        if (!ContestStatus.UPCOMING.name().equalsIgnoreCase(contest.getStatus())) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_STARTED);
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
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_FOUND);
        }

        contest.setStatus(ContestStatus.RUNNING.name());
        contest.setActualStartTime(LocalDateTime.now(clock));
        contestMapper.updateById(contest);
        contestLifecycleService.batchStartParticipants(id);

        log.info("ContestOwnerPort.startContest id={}", id);
    }

    @Override
    @Transactional
    public void endContest(String id) {
        final Contest contest = lockContestOrThrow(id);
        if (contest == null) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_FOUND);
        }

        if (!ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            throw new BusinessException(ContestErrorCode.CONTEST_ENDED);
        }

        // End commands enter the same recoverable lifecycle as the scheduler.
        // A conditional claim prevents an admin request from overwriting a
        // concurrent FINISHING row and losing its pending side effects.
        int transitioned = contestMapper.tryTransitionToFinishing(id, LocalDateTime.now(clock));
        if (transitioned == 0) {
            throw new BusinessException(ContestErrorCode.CONTEST_ENDED);
        }

        log.info("ContestOwnerPort.endContest id={}", id);
    }

    @Override
    @Transactional
    public ContestProblemAdminDTO addProblem(AddContestProblemCommand command) {
        ContestProblem existing = contestProblemMapper.findByContestIdAndProblemId(
                command.contestId(), command.problem().problemId());
        if (existing != null) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Problem already exists in this contest");
        }
        lockContestOrThrow(command.contestId());
        long count = contestProblemMapper.countByContestId(command.contestId());
        ContestProblem problem = new ContestProblem();
        problem.setContestId(command.contestId());
        problem.setProblemId(command.problem().problemId());
        problem.setProblemIndex(problemIndex((int) count));
        int score = command.problem().score() != null
                ? command.problem().score() : DEFAULT_PROBLEM_SCORE;
        problem.setScore(score);
        problem.setBaseScore(score);
        problem.setSolvedCount(0);
        problem.setSubmissionCount(0);
        contestProblemMapper.insert(problem);
        return toProblemDTO(problem);
    }

    @Override
    @Transactional
    public void removeProblem(RemoveContestProblemCommand command) {
        lockContestOrThrow(command.contestId());
        ContestProblem problem = contestProblemMapper.findByContestIdAndProblemId(
                command.contestId(), command.problemId());
        if (problem == null) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Problem not found in this contest");
        }
        if (contestProblemMapper.hasContestOwnedResults(problem.getId())) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Cannot remove a problem after contest submissions or results exist");
        }
        contestProblemMapper.deleteById(problem.getId());
    }

    private static ContestProblemAdminDTO toProblemDTO(ContestProblem problem) {
        return new ContestProblemAdminDTO(
                problem.getId(), problem.getContestId(), problem.getProblemId(),
                problem.getProblemIndex(), problem.getScore(), problem.getPenaltyPerWrong(),
                null, null, null, problem.getSolvedCount(), problem.getSubmissionCount(), null);
    }

    // ─── Announcement writes ────────────────────────────────────

    @Override
    @Transactional
    public String createAnnouncement(String contestId, String title, String content, Boolean isPinned) {
        final Contest contest = lockContestOrThrow(contestId);
        if (contest == null) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_FOUND);
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
        lockContestOrThrow(contestId);
        final ContestAnnouncement announcement = contestAnnouncementMapper
                .findByContestIdAndId(contestId, announcementId);
        if (announcement == null) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST);
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
        lockContestOrThrow(contestId);
        final ContestAnnouncement announcement = contestAnnouncementMapper
                .findByContestIdAndId(contestId, announcementId);
        if (announcement == null) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST);
        }

        contestAnnouncementMapper.deleteById(announcementId);

        log.info("ContestOwnerPort.deleteAnnouncement id={} for contest={}",
                announcementId, contestId);
    }

    // ─── Private helpers ───────────────────────────────────────

    private Contest lockContestOrThrow(String contestId) {
        final Contest contest = contestMapper.selectByIdForUpdate(contestId);
        if (contest == null) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_FOUND);
        }
        return contest;
    }

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
            String contestId, List<ContestProblemInputDTO> problems, List<Long> problemIds) {
        final List<ContestProblemInputDTO> source;
        if (problems != null && !problems.isEmpty()) {
            source = problems;
        } else if (problemIds != null && !problemIds.isEmpty()) {
            source = new ArrayList<>(problemIds.size());
            for (Long pid : problemIds) {
                source.add(new ContestProblemInputDTO(pid, null));
            }
        } else {
            return List.of();
        }

        final List<ContestProblem> list = new ArrayList<>(source.size());
        final LocalDateTime now = LocalDateTime.now(clock);
        for (int i = 0; i < source.size(); i++) {
            ContestProblemInputDTO item = source.get(i);
            int score = item.score() != null ? item.score() : DEFAULT_PROBLEM_SCORE;
            ContestProblem cp = new ContestProblem();
            cp.setId(uuidGenerator.newId());
            cp.setContestId(contestId);
            cp.setProblemId(item.problemId());
            cp.setProblemIndex(problemIndex(i));
            cp.setScore(score);
            cp.setBaseScore(score);
            cp.setSolvedCount(0);
            cp.setSubmissionCount(0);
            cp.setCreatedAt(now);
            cp.setUpdatedAt(now);
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
