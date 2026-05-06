package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.service.AdminContestService;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.websocket.service.RealtimeService;
import com.ulticode.modules.websocket.contest.dto.AnnouncementPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of AdminContestService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminContestServiceImpl implements AdminContestService {

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestAnnouncementMapper contestAnnouncementMapper;
    private final RealtimeService realtimeService;
    private final com.ulticode.modules.contest.service.RankingService rankingService;

    @Override
    public PageResult<AdminContestVO> getContests(AdminContestQueryDTO query) {
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<>();

        // Search filter (title or slug)
        if (StringUtils.hasText(query.getSearch())) {
            String search = "%" + query.getSearch() + "%";
            wrapper.and(w -> w
                    .like(Contest::getTitle, search)
                    .or()
                    .like(Contest::getSlug, search));
        }

        // Type filter
        if (StringUtils.hasText(query.getType())) {
            wrapper.eq(Contest::getContestType, query.getType());
        }

        // Status filter
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Contest::getStatus, query.getStatus());
        }

        // Sorting
        boolean isAsc = !"desc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        switch (sortBy) {
            case "title" -> wrapper.orderBy(true, isAsc, Contest::getTitle);
            case "startTime" -> wrapper.orderBy(true, isAsc, Contest::getStartTime);
            case "createdAt" -> wrapper.orderBy(true, isAsc, Contest::getCreatedAt);
            case "updatedAt" -> wrapper.orderBy(true, isAsc, Contest::getUpdatedAt);
            default -> wrapper.orderBy(true, isAsc, Contest::getCreatedAt);
        }

        Page<Contest> pageResult = new Page<>(page, limit);
        Page<Contest> result = contestMapper.selectPage(pageResult, wrapper);

        return PageResult.of(
                result.getRecords().stream()
                        .map(this::toAdminVO)
                        .toList(),
                result.getTotal(),
                page,
                limit
        );
    }

    @Override
    public AdminContestVO getContest(String id) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return toAdminVO(contest);
    }

    @Override
    @Transactional
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

        String slug = generateSlug(dto.getTitle());
        contest.setSlug(slug);

        contestMapper.insert(contest);

        // Bulk-insert contest problems if provided
        List<Long> problemIds = dto.getProblemIds();
        if (problemIds != null && !problemIds.isEmpty()) {
            for (int i = 0; i < problemIds.size(); i++) {
                ContestProblem cp = new ContestProblem();
                cp.setContestId(contest.getId());
                cp.setProblemId(problemIds.get(i));
                cp.setProblemIndex("Q" + (i + 1));
                cp.setScore(0);
                cp.setBaseScore(100);
                cp.setSolvedCount(0);
                cp.setSubmissionCount(0);
                contestProblemMapper.insert(cp);
            }
        }

        log.info("Admin created contest: {} by user {}", contest.getId(), userId);
        return toAdminVO(contest);
    }

    @Override
    @Transactional
    public AdminContestVO updateContest(String id, UpdateContestDTO dto) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        if (!ContestStatus.UPCOMING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);
        }

        if (dto.getTitle() != null) {
            contest.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            contest.setDescription(dto.getDescription());
        }
        if (dto.getStartTime() != null) {
            contest.setStartTime(dto.getStartTime());
        }
        if (dto.getDuration() != null) {
            contest.setDurationMinutes(dto.getDuration());
            contest.setEndTime(dto.getStartTime() != null
                    ? dto.getStartTime().plusMinutes(dto.getDuration())
                    : contest.getStartTime().plusMinutes(dto.getDuration()));
        }
        if (dto.getMaxParticipants() != null) {
            contest.setMaxParticipants(dto.getMaxParticipants());
        }
        if (dto.getIsPublished() != null) {
            contest.setIsVisible(dto.getIsPublished());
        }

        // Replace contest problems if problemIds is provided
        if (dto.getProblemIds() != null) {
            contestProblemMapper.deleteByContestId(id);
            List<Long> problemIds = dto.getProblemIds();
            for (int i = 0; i < problemIds.size(); i++) {
                ContestProblem cp = new ContestProblem();
                cp.setContestId(id);
                cp.setProblemId(problemIds.get(i));
                cp.setProblemIndex("Q" + (i + 1));
                cp.setScore(0);
                cp.setBaseScore(100);
                cp.setSolvedCount(0);
                cp.setSubmissionCount(0);
                contestProblemMapper.insert(cp);
            }
        }

        contestMapper.updateById(contest);

        log.info("Admin updated contest: {}", id);
        return toAdminVO(contest);
    }

    @Override
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
        contest.setDeletedAt(LocalDateTime.now());
        contest.setDeletedBy(SecurityUtil.getCurrentUserId());
        contestMapper.updateById(contest);

        log.info("Admin deleted contest: {}", id);
    }

    @Override
    public AdminContestVO startContest(String id) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        if (!ContestStatus.UPCOMING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_STARTED);
        }

        long problemCount = contestProblemMapper.countByContestId(id);
        if (problemCount == 0) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }

        contest.setStatus(ContestStatus.RUNNING.name());
        contestMapper.updateById(contest);

        log.info("Admin started contest: {}", id);
        return toAdminVO(contest);
    }

    @Override
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

        log.info("Admin ended contest: {}", id);
        return toAdminVO(contest);
    }

    @Override
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

        // WebSocket push (D-12)
        realtimeService.emitAnnouncement(AnnouncementPayload.of(announcement.getId(), contestId, title, content));

        log.info("Admin created announcement {} for contest {}", announcement.getId(), contestId);
        return announcement;
    }

    @Override
    public ContestAnnouncement updateAnnouncement(String contestId, String announcementId, String title, String content, Boolean isPinned) {
        ContestAnnouncement announcement = contestAnnouncementMapper.findByContestIdAndId(contestId, announcementId);
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

        log.info("Admin updated announcement {} for contest {}", announcementId, contestId);
        return announcement;
    }

    @Override
    public void deleteAnnouncement(String contestId, String announcementId) {
        ContestAnnouncement announcement = contestAnnouncementMapper.findByContestIdAndId(contestId, announcementId);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        contestAnnouncementMapper.deleteById(announcementId);

        log.info("Admin deleted announcement {} for contest {}", announcementId, contestId);
    }

    @Override
    public List<ContestAnnouncement> getAnnouncements(String contestId) {
        return contestAnnouncementMapper.findByContestIdOrderByCreatedAtDesc(contestId);
    }

    @Override
    public List<com.ulticode.modules.contest.dto.ContestRankingVO> getRankings(String contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return rankingService.getLiveRanking(contestId, 100);
    }

    /**
     * Convert Contest entity to AdminContestVO.
     */
    private AdminContestVO toAdminVO(Contest contest) {
        if (contest == null) {
            return null;
        }

        AdminContestVO vo = new AdminContestVO();
        vo.setId(contest.getId());
        vo.setSlug(contest.getSlug());
        vo.setTitle(contest.getTitle());
        vo.setDescription(contest.getDescription());
        vo.setContestType(contest.getContestType());
        vo.setStatus(contest.getStatus());
        vo.setStartTime(contest.getStartTime());
        vo.setEndTime(contest.getEndTime());
        vo.setDurationMinutes(contest.getDurationMinutes());
        vo.setIsVisible(contest.getIsVisible());
        vo.setParticipantCount(contest.getParticipantCount());
        vo.setCreatedAt(contest.getCreatedAt());
        vo.setUpdatedAt(contest.getUpdatedAt());
        vo.setProblemCount((int) contestProblemMapper.countByContestId(contest.getId()));

        return vo;
    }

    /**
     * Generate a URL-friendly slug from a title.
     */
    private String generateSlug(String title) {
        if (title == null || title.isBlank()) {
            return "contest-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (slug.length() < 3) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 8);
        }

        return slug;
    }
}
