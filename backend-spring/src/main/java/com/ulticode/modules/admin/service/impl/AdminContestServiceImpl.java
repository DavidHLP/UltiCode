package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.service.AdminContestService;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.mapper.ContestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Implementation of AdminContestService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminContestServiceImpl implements AdminContestService {

    private final ContestMapper contestMapper;

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

        return vo;
    }
}
