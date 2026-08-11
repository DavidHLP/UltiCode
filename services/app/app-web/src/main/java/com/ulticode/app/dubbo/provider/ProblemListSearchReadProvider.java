package com.ulticode.app.dubbo.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.service.ProblemListSearchReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Dubbo Provider implementation of {@link ProblemListSearchReadPort} in
 * {@code backend-app}. Owns the query-wrapper assembly (search across
 * name/description, featured/public filters, sort selector, page
 * normalization) and the entity → summary DTO projection with per-list
 * problem counts.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ProblemListSearchReadProvider implements ProblemListSearchReadPort {

    private final ProblemListMapper problemListMapper;
    private final ProblemListProblemMapper problemListProblemMapper;

    @Override
    public PageResult<ProblemListSummaryDTO> searchAdminLists(
            String search,
            Boolean isFeatured,
            Boolean isPublic,
            String sortBy,
            String sortOrder,
            int page,
            int limit) {
        Page<ProblemList> result = problemListMapper.selectPage(
                new Page<>(page, limit), buildWrapper(search, isFeatured, isPublic, sortBy, sortOrder));

        List<ProblemListSummaryDTO> dtos = result.getRecords().stream()
                .map(this::toSummaryDTO)
                .collect(Collectors.toList());

        return PageResult.of(dtos, result.getTotal(), page, limit);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Build the query wrapper backing the paginated admin list read:
     * search across name + description, featured / public filters, sort
     * selector (name / bannerOrder / createdAt default).
     */
    private LambdaQueryWrapper<ProblemList> buildWrapper(
            String search, Boolean isFeatured, Boolean isPublic, String sortBy, String sortOrder) {
        LambdaQueryWrapper<ProblemList> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(search)) {
            String like = "%" + search + "%";
            wrapper.and(w -> w
                    .like(ProblemList::getName, like)
                    .or()
                    .like(ProblemList::getDescription, like));
        }

        if (isFeatured != null) {
            wrapper.eq(ProblemList::getIsFeatured, isFeatured);
        }

        if (isPublic != null) {
            wrapper.eq(ProblemList::getIsPublic, isPublic);
        }

        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        String sort = StringUtils.hasText(sortBy) ? sortBy : "createdAt";
        switch (sort) {
            case "name" -> wrapper.orderBy(true, asc, ProblemList::getName);
            case "bannerOrder" -> wrapper.orderBy(true, asc, ProblemList::getBannerOrder);
            default -> wrapper.orderBy(true, asc, ProblemList::getCreatedAt);
        }

        return wrapper;
    }

    private ProblemListSummaryDTO toSummaryDTO(ProblemList list) {
        ProblemListSummaryDTO dto = new ProblemListSummaryDTO();
        dto.setId(list.getId());
        dto.setName(list.getName());
        dto.setDescription(list.getDescription());
        dto.setAuthorId(list.getAuthorId());
        dto.setIsPublic(list.getIsPublic());
        dto.setIsFeatured(list.getIsFeatured());
        dto.setBannerTag(list.getBannerTag());
        dto.setBannerIcon(list.getBannerIcon());
        dto.setBannerTheme(list.getBannerTheme());
        dto.setBannerOrder(list.getBannerOrder());
        dto.setProblemCount((int) problemListProblemMapper.countByListId(list.getId()));
        dto.setCreatedAt(list.getCreatedAt());
        dto.setUpdatedAt(list.getUpdatedAt());
        return dto;
    }
}
