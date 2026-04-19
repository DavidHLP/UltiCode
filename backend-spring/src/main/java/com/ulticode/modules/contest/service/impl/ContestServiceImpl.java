package com.ulticode.modules.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.GlobalRanking;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.service.ContestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of ContestService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestServiceImpl implements ContestService {

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper participantMapper;
    private final GlobalRankingMapper globalRankingMapper;

    // =========================================================================
    // CRUD Operations (Admin)
    // =========================================================================

    @Override
    @Transactional
    public ContestVO createContest(CreateContestDTO dto, String userId) {
        // Verify admin role
        if (!SecurityUtil.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Contest contest = new Contest();
        contest.setTitle(dto.getTitle());
        contest.setDescription(dto.getDescription());
        contest.setStartTime(dto.getStartTime());
        contest.setDurationMinutes(dto.getDuration());
        contest.setEndTime(dto.getStartTime().plusMinutes(dto.getDuration()));
        contest.setMaxParticipants(dto.getMaxParticipants());
        contest.setIsVisible(dto.getIsPublished() != null ? dto.getIsPublished() : false);
        contest.setCreatedBy(userId);
        contest.setStatus(ContestStatus.DRAFT.name());
        contest.setRegisteredCount(0);
        contest.setParticipantCount(0);
        contest.setSubmissionCount(0);
        contest.setIsDeleted(false);

        // Generate slug from title
        String slug = generateSlug(dto.getTitle());
        contest.setSlug(slug);

        contestMapper.insert(contest);

        log.info("Contest created: {} by user {}", contest.getId(), userId);

        return toVO(contest, userId);
    }

    @Override
    @Transactional
    public ContestVO updateContest(String id, UpdateContestDTO dto) {
        // Verify admin role
        if (!SecurityUtil.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Contest contest = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));

        // Update fields
        if (dto.getTitle() != null) {
            contest.setTitle(dto.getTitle());
            contest.setSlug(generateSlug(dto.getTitle()));
        }
        if (dto.getDescription() != null) {
            contest.setDescription(dto.getDescription());
        }
        if (dto.getStartTime() != null) {
            contest.setStartTime(dto.getStartTime());
            if (dto.getDuration() != null) {
                contest.setEndTime(dto.getStartTime().plusMinutes(dto.getDuration()));
            } else {
                contest.setEndTime(dto.getStartTime().plusMinutes(contest.getDurationMinutes()));
            }
        }
        if (dto.getDuration() != null) {
            contest.setDurationMinutes(dto.getDuration());
            if (contest.getStartTime() != null) {
                contest.setEndTime(contest.getStartTime().plusMinutes(dto.getDuration()));
            }
        }
        if (dto.getMaxParticipants() != null) {
            contest.setMaxParticipants(dto.getMaxParticipants());
        }
        if (dto.getIsPublished() != null) {
            contest.setIsVisible(dto.getIsPublished());
        }

        contestMapper.updateById(contest);

        log.info("Contest updated: {}", id);

        return toVO(contest, null);
    }

    @Override
    @Transactional
    public void deleteContest(String id) {
        // Verify admin role
        if (!SecurityUtil.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Contest contest = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));

        contest.setIsDeleted(true);
        contest.setDeletedAt(LocalDateTime.now());
        contest.setDeletedBy(SecurityUtil.getCurrentUserId());

        contestMapper.updateById(contest);

        log.info("Contest deleted: {}", id);
    }

    // =========================================================================
    // Query Operations
    // =========================================================================

    @Override
    public PageResult<ContestVO> findAll(ContestQueryDTO query, String userId) {
        // Set default pagination values
        int currentPage = (query.getPage() != null && query.getPage() > 0) ? query.getPage() : 1;
        int currentPageSize = (query.getPageSize() != null && query.getPageSize() > 0) ? query.getPageSize() : 20;

        // Limit page size to prevent large queries
        currentPageSize = Math.min(currentPageSize, 100);

        LambdaQueryWrapper<Contest> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Contest::getIsDeleted, false)
                .eq(Contest::getIsVisible, true);

        // Apply filters
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            queryWrapper.eq(Contest::getStatus, query.getStatus().toUpperCase());
        }
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            queryWrapper.and(wrapper -> wrapper
                    .like(Contest::getTitle, "%" + query.getSearch() + "%")
                    .or()
                    .like(Contest::getSlug, "%" + query.getSearch() + "%")
            );
        }

        // Apply sorting
        String sortField = query.getSort() != null ? query.getSort() : "startTime";
        String direction = query.getDirection() != null ? query.getDirection() : "asc";
        boolean isAsc = "asc".equalsIgnoreCase(direction);

        switch (sortField) {
            case "startTime":
                if (isAsc) {
                    queryWrapper.orderByAsc(Contest::getStartTime);
                } else {
                    queryWrapper.orderByDesc(Contest::getStartTime);
                }
                break;
            case "endTime":
                if (isAsc) {
                    queryWrapper.orderByAsc(Contest::getEndTime);
                } else {
                    queryWrapper.orderByDesc(Contest::getEndTime);
                }
                break;
            case "createdAt":
                if (isAsc) {
                    queryWrapper.orderByAsc(Contest::getCreatedAt);
                } else {
                    queryWrapper.orderByDesc(Contest::getCreatedAt);
                }
                break;
            case "title":
            default:
                if (isAsc) {
                    queryWrapper.orderByAsc(Contest::getTitle);
                } else {
                    queryWrapper.orderByDesc(Contest::getTitle);
                }
                break;
        }

        Page<Contest> contestPage = new Page<>(currentPage, currentPageSize);
        Page<Contest> result = contestMapper.selectPage(contestPage, queryWrapper);

        String finalUserId = userId;
        List<ContestVO> contestVOList = result.getRecords().stream()
                .map(contest -> toVO(contest, finalUserId))
                .collect(Collectors.toList());

        return PageResult.of(contestVOList, result.getTotal(), currentPage, currentPageSize);
    }

    @Override
    public Optional<Contest> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(contestMapper.selectById(id));
    }

    @Override
    public Optional<Contest> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(contestMapper.findBySlug(slug));
    }

    @Override
    public ContestVO getContestById(String id, String userId) {
        Contest contest = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        return toVO(contest, userId);
    }

    @Override
    public List<ContestVO> findUpcoming(String userId) {
        List<Contest> contests = contestMapper.findByStatus(ContestStatus.UPCOMING.name());
        return contests.stream()
                .map(contest -> toVO(contest, userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ContestVO> findRunning(String userId) {
        List<Contest> contests = contestMapper.findByStatus(ContestStatus.RUNNING.name());
        return contests.stream()
                .map(contest -> toVO(contest, userId))
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<ContestVO> findPast(Integer page, Integer pageSize, String userId) {
        int currentPage = (page != null && page > 0) ? page : 1;
        int currentPageSize = (pageSize != null && pageSize > 0) ? pageSize : 10;
        currentPageSize = Math.min(currentPageSize, 50);

        LambdaQueryWrapper<Contest> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Contest::getIsDeleted, false)
                .eq(Contest::getStatus, ContestStatus.FINISHED.name())
                .orderByDesc(Contest::getEndTime);

        Page<Contest> contestPage = new Page<>(currentPage, currentPageSize);
        Page<Contest> result = contestMapper.selectPage(contestPage, queryWrapper);

        List<ContestVO> contestVOList = result.getRecords().stream()
                .map(contest -> toVO(contest, userId))
                .collect(Collectors.toList());

        return PageResult.of(contestVOList, result.getTotal(), currentPage, currentPageSize);
    }

    @Override
    public ContestStatsVO getStats() {
        ContestStatsVO stats = new ContestStatsVO();

        // Count contests by status
        long upcomingCount = contestMapper.countByStatus(ContestStatus.UPCOMING.name());
        long runningCount = contestMapper.countByStatus(ContestStatus.RUNNING.name());
        long finishedCount = contestMapper.countByStatus(ContestStatus.FINISHED.name());
        long totalContests = upcomingCount + runningCount + finishedCount;

        stats.setRegisteredParticipants((int) upcomingCount);
        stats.setActiveParticipants((int) runningCount);
        stats.setCompletedParticipants((int) finishedCount);
        stats.setTotalSubmissions(totalContests);

        return stats;
    }

    @Override
    public List<ContestRankingVO> getGlobalRanking(Integer limit) {
        int maxResults = (limit != null && limit > 0) ? Math.min(limit, 100) : 10;
        List<GlobalRanking> rankings = globalRankingMapper.findTopRankings(maxResults);

        return rankings.stream()
                .map(this::toRankingVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void registerForContest(String contestId, String userId) {
        // Verify contest exists and is upcoming
        Contest contest = findById(contestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));

        if (!ContestStatus.UPCOMING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);
        }

        // Check if registration is closed
        LocalDateTime now = LocalDateTime.now();
        if (contest.getRegistrationEnd() != null && now.isAfter(contest.getRegistrationEnd())) {
            throw new BusinessException(ErrorCode.CONTEST_REGISTRATION_CLOSED);
        }

        // Check if already registered (before atomic increment to fail fast)
        if (participantMapper.existsByContestIdAndUserId(contestId, userId)) {
            throw new BusinessException(ErrorCode.CONTEST_ALREADY_REGISTERED);
        }

        // Atomically increment registered count with capacity check (prevents race condition)
        int updated = contestMapper.tryIncrementRegisteredCount(contestId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONTEST_FULL);
        }

        // Create participant record (only after capacity check succeeds)
        ContestParticipant participant = new ContestParticipant();
        participant.setContestId(contestId);
        participant.setUserId(userId);
        participant.setStatus(ContestParticipantStatus.REGISTERED.name());
        participant.setRegisteredAt(now);
        participant.setIsVirtual(false);

        participantMapper.insert(participant);

        log.info("User {} registered for contest {}", userId, contestId);
    }

    @Override
    @Transactional
    public void unregisterFromContest(String contestId, String userId) {
        // Verify contest exists
        Contest contest = findById(contestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));

        // Find participant record
        ContestParticipant participant = participantMapper.findByContestIdAndUserId(contestId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_REGISTERED));

        // Can only unregister from upcoming contests
        if (!ContestStatus.UPCOMING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);
        }

        // Delete participant record
        participantMapper.deleteById(participant.getId());

        // Decrement registered count
        contestMapper.decrementRegisteredCount(contestId);

        log.info("User {} unregistered from contest {}", userId, contestId);
    }

    @Override
    public ParticipationStatusDTO getParticipationStatus(String contestId, String userId) {
        // Verify contest exists
        Contest contest = findById(contestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));

        ParticipationStatusDTO status = new ParticipationStatusDTO();
        status.setContestId(Long.parseLong(contestId));
        status.setTitle(contest.getTitle());
        status.setStartTime(contest.getStartTime());
        status.setEndTime(contest.getEndTime());

        // Check if user is registered/participating
        Optional<ContestParticipant> participantOpt = participantMapper.findByContestIdAndUserId(contestId, userId);

        if (participantOpt.isEmpty()) {
            status.setStatus("not_participated");
            status.setHasStarted(false);
            status.setIsActive(false);
            status.setIsCompleted(false);
            status.setCanParticipate(ContestStatus.UPCOMING.name().equals(contest.getStatus()) ||
                    ContestStatus.RUNNING.name().equals(contest.getStatus()));
            return status;
        }

        ContestParticipant participant = participantOpt.get();
        status.setStatus(participant.getStatus().toLowerCase());
        status.setRegisteredAt(participant.getRegisteredAt());
        status.setStartedAt(participant.getStartedAt());
        status.setCompletedAt(participant.getFinishedAt());
        status.setRanking(participant.getFinalRank());
        status.setScore(participant.getTotalScore() != null ? participant.getTotalScore().longValue() : null);
        status.setHasStarted(participant.getStartedAt() != null);
        status.setIsCompleted(ContestParticipantStatus.FINISHED.name().equals(participant.getStatus()));
        status.setIsActive(ContestParticipantStatus.STARTED.name().equals(participant.getStatus()));

        return status;
    }

    @Override
    public List<ContestVO> getUserContests(String userId, String type) {
        List<ContestParticipant> participants;

        switch (type) {
            case "registered":
                participants = participantMapper.findByUserId(userId).stream()
                        .filter(p -> ContestParticipantStatus.REGISTERED.name().equals(p.getStatus()))
                        .collect(Collectors.toList());
                break;
            case "virtual":
                participants = participantMapper.findByUserId(userId).stream()
                        .filter(p -> Boolean.TRUE.equals(p.getIsVirtual()))
                        .collect(Collectors.toList());
                break;
            case "participated":
            default:
                participants = participantMapper.findByUserId(userId).stream()
                        .filter(p -> ContestParticipantStatus.FINISHED.name().equals(p.getStatus()) ||
                                ContestParticipantStatus.STARTED.name().equals(p.getStatus()))
                        .collect(Collectors.toList());
                break;
        }

        return participants.stream()
                .map(p -> {
                    Contest contest = contestMapper.selectById(p.getContestId());
                    ContestVO vo = toVO(contest, userId);
                    // Set participation-specific fields
                    vo.setUserRanking(p.getFinalRank());
                    vo.setUserScore(p.getTotalScore() != null ? p.getTotalScore().longValue() : null);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationStatusDTO startVirtualContest(String contestId, String userId) {
        // Verify contest exists and is finished
        Contest contest = findById(contestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));

        if (!ContestStatus.FINISHED.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ENDED);
        }

        // Check if user already has an active virtual session
        Optional<ContestParticipant> existingParticipant = participantMapper.findByContestIdAndUserId(contestId, userId);
        if (existingParticipant.isPresent() && Boolean.TRUE.equals(existingParticipant.get().getIsVirtual())) {
            // Return existing session
            return getVirtualSession(contestId, userId);
        }

        // Create new virtual participant
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        ContestParticipant participant = new ContestParticipant();
        participant.setContestId(contestId);
        participant.setUserId(userId);
        participant.setStatus(ContestParticipantStatus.STARTED.name());
        participant.setRegisteredAt(now);
        participant.setStartedAt(now);
        participant.setIsVirtual(true);
        participant.setVirtualSessionId(sessionId);

        participantMapper.insert(participant);

        log.info("User {} started virtual contest {} with session {}", userId, contestId, sessionId);

        return getVirtualSession(contestId, userId);
    }

    @Override
    public ParticipationStatusDTO getVirtualSession(String contestId, String userId) {
        Optional<ContestParticipant> participantOpt = participantMapper.findByContestIdAndUserId(contestId, userId);

        if (participantOpt.isEmpty() || !Boolean.TRUE.equals(participantOpt.get().getIsVirtual())) {
            return null;
        }

        ContestParticipant participant = participantOpt.get();
        Contest contest = contestMapper.selectById(contestId);

        ParticipationStatusDTO status = new ParticipationStatusDTO();
        status.setContestId(Long.parseLong(contestId));
        status.setTitle(contest.getTitle());
        status.setStatus(participant.getStatus().toLowerCase());
        status.setRegisteredAt(participant.getRegisteredAt());
        status.setStartedAt(participant.getStartedAt());
        status.setStartTime(participant.getStartedAt());
        status.setEndTime(participant.getStartedAt().plusMinutes(contest.getDurationMinutes()));
        status.setHasStarted(true);
        status.setIsActive(ContestParticipantStatus.STARTED.name().equals(participant.getStatus()));
        status.setIsCompleted(ContestParticipantStatus.FINISHED.name().equals(participant.getStatus()));

        return status;
    }

    @Override
    @Transactional
    public void finishVirtualContest(String contestId, String sessionId, String userId) {
        Optional<ContestParticipant> participantOpt = participantMapper.findByContestIdAndUserId(contestId, userId);

        if (participantOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_REGISTERED);
        }

        ContestParticipant participant = participantOpt.get();

        if (!Boolean.TRUE.equals(participant.getIsVirtual())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_REGISTERED);
        }

        if (!sessionId.equals(participant.getVirtualSessionId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        participant.setStatus(ContestParticipantStatus.FINISHED.name());
        participant.setFinishedAt(LocalDateTime.now());

        participantMapper.updateById(participant);

        log.info("User {} finished virtual contest {} session {}", userId, contestId, sessionId);
    }

    @Override
    public ContestVO toVO(Contest contest, String userId) {
        if (contest == null) {
            return null;
        }

        ContestVO vo = new ContestVO();
        BeanUtils.copyProperties(contest, vo);

        // Map field names
        vo.setId(contest.getId());
        vo.setDuration(contest.getDurationMinutes());
        vo.setCurrentParticipants(contest.getParticipantCount());
        vo.setIsPremium(false); // Default value, can be enhanced later
        vo.setIsPublished(contest.getIsVisible());
        vo.setCreatedById(contest.getCreatedBy() != null ? Long.parseLong(contest.getCreatedBy()) : null);

        // Calculate time remaining for upcoming contests
        if (ContestStatus.UPCOMING.name().equals(contest.getStatus()) && contest.getStartTime() != null) {
            long remaining = java.time.Duration.between(LocalDateTime.now(), contest.getStartTime()).getSeconds();
            if (remaining > 0) {
                // Can add time remaining field if needed
            }
        }

        // Populate user-specific fields if userId is provided
        if (userId != null && !userId.isBlank()) {
            Optional<ContestParticipant> participantOpt = participantMapper.findByContestIdAndUserId(contest.getId(), userId);
            if (participantOpt.isPresent()) {
                ContestParticipant participant = participantOpt.get();
                vo.setIsParticipating(true);
                vo.setUserRanking(participant.getFinalRank());
                vo.setUserScore(participant.getTotalScore() != null ? participant.getTotalScore().longValue() : null);
            } else {
                vo.setIsParticipating(false);
            }
        }

        return vo;
    }

    /**
     * Generate a URL-friendly slug from a title.
     */
    private String generateSlug(String title) {
        if (title == null || title.isBlank()) {
            return "contest-" + UUID.randomUUID().toString().substring(0, 8);
        }
        // Convert to lowercase, replace spaces with hyphens, remove special characters
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        // Add a random suffix if the slug is too short
        if (slug.length() < 3) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        }

        return slug;
    }

    /**
     * Convert GlobalRanking to ContestRankingVO.
     */
    private ContestRankingVO toRankingVO(GlobalRanking ranking) {
        if (ranking == null) {
            return null;
        }

        ContestRankingVO vo = new ContestRankingVO();
        vo.setRank(ranking.getGlobalRank());
        vo.setUserId(Long.parseLong(ranking.getUserId()));
        vo.setUsername(ranking.getUsername());
        vo.setAvatar(ranking.getAvatar());
        vo.setScore(ranking.getRating().longValue());
        vo.setProblemsSolved(ranking.getContestsAttended());

        return vo;
    }
}
