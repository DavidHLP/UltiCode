package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminContestProjection}. Owns every
 * entity-to-{@code AdminContestVO} projection rule and URL-slug shape for the
 * admin contest surface &mdash; see the interface javadoc for the deepening
 * rationale.
 *
 * <p>All read methods are pure reads; none mutate contest state. Cross-module
 * enrichment ({@link ContestProblemMapper} for the {@code problemCount} field
 * on the VO) lives here and only here &mdash; the {@code AdminContestService}
 * write state machine no longer imports {@code ContestProblemMapper} for read
 * enrichment after the extraction.
 *
 * <p>Mirrors the {@code DefaultAdminSubmissionProjection} /
 * {@code DefaultModerationProjection} / {@code DefaultAchievementProjection}
 * shape exactly: {@link org.springframework.stereotype.Service @Service} +
 * Lombok's {@link lombok.RequiredArgsConstructor} for constructor injection,
 * {@link lombok.extern.slf4j.Slf4j @Slf4j} for the SLF4J Logger, and a
 * small, focused surface that callers compose with the service's write
 * methods.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminContestProjection implements AdminContestProjection {

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;

    // ------------------------------------------------------------------
    // Paginated list read (query build + shape)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminContestVO> getContests(AdminContestQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);
        int page = pageRequest.page();
        int limit = pageRequest.pageSize();

        Page<Contest> result = contestMapper.selectPage(
                new Page<>(page, limit), buildWrapper(query));

        List<AdminContestVO> vos = result.getRecords().stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());

        return PageResult.of(vos, result.getTotal(), page, limit);
    }

    @Override
    public AdminContestVO getContest(String id) {
        Contest contest = contestMapper.selectById(id);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return toAdminVO(contest);
    }

    // ------------------------------------------------------------------
    // Projection helpers (entity -> AdminContestVO)
    // ------------------------------------------------------------------

    @Override
    public AdminContestVO toAdminVO(Contest contest) {
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

    @Override
    public String generateSlug(String title) {
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

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Build the {@link LambdaQueryWrapper} that backs the paginated list read
     * for the admin contest surface. Pure query-shape concern &mdash; no
     * service-layer imports needed.
     */
    private LambdaQueryWrapper<Contest> buildWrapper(AdminContestQueryDTO query) {
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

        return wrapper;
    }
}
