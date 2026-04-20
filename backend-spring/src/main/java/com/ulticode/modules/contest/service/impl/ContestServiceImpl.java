package com.ulticode.modules.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.GlobalRanking;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.service.ContestSchedulerService;
import com.ulticode.modules.contest.service.ContestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Facade for contest operations.
 * Delegates scheduling/lifecycle to ContestSchedulerService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestServiceImpl implements ContestService {

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper participantMapper;
    private final GlobalRankingMapper globalRankingMapper;
    private final ContestSchedulerService schedulerService;

    // =========================================================================
    // CRUD Operations (Admin)
    // =========================================================================

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    public ContestVO createContest(CreateContestDTO dto, String userId) {
        if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
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
        contest.setSlug(generateSlug(dto.getTitle()));
        contestMapper.insert(contest);
        log.info("Contest created: {} by user {}", contest.getId(), userId);
        return toVO(contest, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    public ContestVO updateContest(String id, UpdateContestDTO dto) {
        if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        if (dto.getTitle() != null) { contest.setTitle(dto.getTitle()); contest.setSlug(generateSlug(dto.getTitle())); }
        if (dto.getDescription() != null) contest.setDescription(dto.getDescription());
        if (dto.getStartTime() != null) {
            contest.setStartTime(dto.getStartTime());
            contest.setEndTime(dto.getDuration() != null ? dto.getStartTime().plusMinutes(dto.getDuration())
                    : contest.getStartTime().plusMinutes(contest.getDurationMinutes()));
        }
        if (dto.getDuration() != null) {
            contest.setDurationMinutes(dto.getDuration());
            if (contest.getStartTime() != null) contest.setEndTime(contest.getStartTime().plusMinutes(dto.getDuration()));
        }
        if (dto.getMaxParticipants() != null) contest.setMaxParticipants(dto.getMaxParticipants());
        if (dto.getIsPublished() != null) contest.setIsVisible(dto.getIsPublished());
        contestMapper.updateById(contest);
        log.info("Contest updated: {}", id);
        return toVO(contest, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    public void deleteContest(String id) {
        if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
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
        int currentPage = (query.getPage() != null && query.getPage() > 0) ? query.getPage() : 1;
        int currentPageSize = Math.min(query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 20, 100);
        LambdaQueryWrapper<Contest> qw = new LambdaQueryWrapper<>();
        qw.eq(Contest::getIsDeleted, false).eq(Contest::getIsVisible, true);
        if (query.getStatus() != null && !query.getStatus().isBlank()) qw.eq(Contest::getStatus, query.getStatus().toUpperCase());
        if (query.getSearch() != null && !query.getSearch().isBlank())
            qw.and(w -> w.like(Contest::getTitle, "%" + query.getSearch() + "%").or().like(Contest::getSlug, "%" + query.getSearch() + "%"));
        String sortField = query.getSort() != null ? query.getSort() : "startTime";
        String direction = query.getDirection() != null ? query.getDirection() : "asc";
        boolean isAsc = "asc".equalsIgnoreCase(direction);
        switch (sortField) {
            case "endTime" -> { if (isAsc) qw.orderByAsc(Contest::getEndTime); else qw.orderByDesc(Contest::getEndTime); }
            case "createdAt" -> { if (isAsc) qw.orderByAsc(Contest::getCreatedAt); else qw.orderByDesc(Contest::getCreatedAt); }
            case "title" -> { if (isAsc) qw.orderByAsc(Contest::getTitle); else qw.orderByDesc(Contest::getTitle); }
            default -> { if (isAsc) qw.orderByAsc(Contest::getStartTime); else qw.orderByDesc(Contest::getStartTime); }
        }
        Page<Contest> page = contestMapper.selectPage(new Page<>(currentPage, currentPageSize), qw);
        List<ContestVO> items = page.getRecords().stream().map(c -> toVO(c, userId)).collect(Collectors.toList());
        return PageResult.of(items, page.getTotal(), currentPage, currentPageSize);
    }

    @Override
    public Optional<Contest> findById(String id) {
        return Optional.ofNullable(contestMapper.selectById(id));
    }

    @Override
    public Optional<Contest> findBySlug(String slug) {
        return Optional.ofNullable(contestMapper.findBySlug(slug));
    }

    @Override
    public ContestVO getContestById(String id, String userId) {
        return toVO(findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND)), userId);
    }

    @Override
    public List<ContestVO> findUpcoming(String userId) {
        return contestMapper.findByStatus(ContestStatus.UPCOMING.name()).stream().map(c -> toVO(c, userId)).collect(Collectors.toList());
    }

    @Override
    public List<ContestVO> findRunning(String userId) {
        return contestMapper.findByStatus(ContestStatus.RUNNING.name()).stream().map(c -> toVO(c, userId)).collect(Collectors.toList());
    }

    @Override
    public PageResult<ContestVO> findPast(Integer page, Integer pageSize, String userId) {
        int p = Math.max(page != null ? page : 1, 1);
        int ps = Math.min(pageSize != null && pageSize > 0 ? pageSize : 10, 50);
        LambdaQueryWrapper<Contest> qw = new LambdaQueryWrapper<>();
        qw.eq(Contest::getIsDeleted, false).eq(Contest::getStatus, ContestStatus.FINISHED.name()).orderByDesc(Contest::getEndTime);
        Page<Contest> result = contestMapper.selectPage(new Page<>(p, ps), qw);
        List<ContestVO> items = result.getRecords().stream().map(c -> toVO(c, userId)).collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(), p, ps);
    }

    @Override
    public ContestStatsVO getStats() {
        ContestStatsVO stats = new ContestStatsVO();
        long upcoming = contestMapper.countByStatus(ContestStatus.UPCOMING.name());
        long running = contestMapper.countByStatus(ContestStatus.RUNNING.name());
        long finished = contestMapper.countByStatus(ContestStatus.FINISHED.name());
        stats.setRegisteredParticipants((int) upcoming);
        stats.setActiveParticipants((int) running);
        stats.setCompletedParticipants((int) finished);
        stats.setTotalSubmissions(upcoming + running + finished);
        return stats;
    }

    @Override
    @Cacheable(value = "contestRanking", key = "'getGlobalRanking:' + #limit")
    public List<ContestRankingVO> getGlobalRanking(Integer limit) {
        int max = (limit != null && limit > 0) ? Math.min(limit, 100) : 10;
        return globalRankingMapper.findTopRankings(max).stream().map(this::toRankingVO).collect(Collectors.toList());
    }

    // =========================================================================
    // Scheduling (delegated to ContestSchedulerService)
    // =========================================================================

    @Override
    public void registerForContest(String contestId, String userId) {
        schedulerService.registerForContest(contestId, userId);
    }

    @Override
    public void unregisterFromContest(String contestId, String userId) {
        schedulerService.unregisterFromContest(contestId, userId);
    }

    @Override
    public ParticipationStatusDTO getParticipationStatus(String contestId, String userId) {
        return schedulerService.getParticipationStatus(contestId, userId);
    }

    @Override
    public List<ContestVO> getUserContests(String userId, String type) {
        return schedulerService.getUserContests(userId, type);
    }

    @Override
    public ParticipationStatusDTO startVirtualContest(String contestId, String userId) {
        return schedulerService.startVirtualContest(contestId, userId);
    }

    @Override
    public ParticipationStatusDTO getVirtualSession(String contestId, String userId) {
        return schedulerService.getVirtualSession(contestId, userId);
    }

    @Override
    public void finishVirtualContest(String contestId, String sessionId, String userId) {
        schedulerService.finishVirtualContest(contestId, sessionId, userId);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @Override
    public ContestVO toVO(Contest contest, String userId) {
        if (contest == null) return null;
        ContestVO vo = new ContestVO();
        BeanUtils.copyProperties(contest, vo);
        vo.setId(contest.getId());
        vo.setDuration(contest.getDurationMinutes());
        vo.setCurrentParticipants(contest.getParticipantCount());
        vo.setIsPremium(false);
        vo.setIsPublished(contest.getIsVisible());
        vo.setCreatedById(contest.getCreatedBy() != null ? Long.parseLong(contest.getCreatedBy()) : null);
        if (userId != null && !userId.isBlank()) {
            Optional<?> participantOpt = participantMapper.findByContestIdAndUserId(contest.getId(), userId);
            if (participantOpt.isPresent()) {
                var p = (com.ulticode.modules.contest.entity.ContestParticipant) participantOpt.get();
                vo.setIsParticipating(true);
                vo.setUserRanking(p.getFinalRank());
                vo.setUserScore(p.getTotalScore() != null ? p.getTotalScore().longValue() : null);
            } else {
                vo.setIsParticipating(false);
            }
        }
        return vo;
    }

    private String generateSlug(String title) {
        if (title == null || title.isBlank()) return "contest-" + UUID.randomUUID().toString().substring(0, 8);
        String slug = title.toLowerCase().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        return slug.length() < 3 ? slug + "-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }

    private ContestRankingVO toRankingVO(GlobalRanking ranking) {
        if (ranking == null) return null;
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
