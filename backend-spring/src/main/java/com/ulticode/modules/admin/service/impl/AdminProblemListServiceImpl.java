package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.admin.service.AdminProblemListService;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.CreateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListProblemsDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListProblemRelation;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.problemlist.projection.ProblemListProjection;
import com.ulticode.modules.problemlist.service.ProblemListService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of AdminProblemListService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProblemListServiceImpl implements AdminProblemListService {

    private final ProblemListMapper problemListMapper;
    private final ProblemListProblemMapper problemListProblemMapper;
    private final ProblemListService problemListService;
    private final ProblemListProjection problemListProjection;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;

    @Override
    public PageResult<ProblemListSummaryVO> getProblemLists(AdminProblemListQueryDTO query) {
        LambdaQueryWrapper<ProblemList> wrapper = new LambdaQueryWrapper<>();

        // Search filter
        if (StringUtils.hasText(query.getSearch())) {
            String search = "%" + query.getSearch() + "%";
            wrapper.and(w -> w
                    .like(ProblemList::getName, search)
                    .or()
                    .like(ProblemList::getDescription, search));
        }

        // Featured filter
        if (query.getIsFeatured() != null) {
            wrapper.eq(ProblemList::getIsFeatured, query.getIsFeatured());
        }

        // Public filter
        if (query.getIsPublic() != null) {
            wrapper.eq(ProblemList::getIsPublic, query.getIsPublic());
        }

        // Sorting
        boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        switch (sortBy) {
            case "name" -> wrapper.orderBy(true, isAsc, ProblemList::getName);
            case "bannerOrder" -> wrapper.orderBy(true, isAsc, ProblemList::getBannerOrder);
            default -> wrapper.orderBy(true, isAsc, ProblemList::getCreatedAt);
        }

        // Pagination
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        Page<ProblemList> pageResult = new Page<>(page, limit);
        Page<ProblemList> result = problemListMapper.selectPage(pageResult, wrapper);

        List<ProblemListSummaryVO> voList = result.getRecords().stream()
                .map(problemListProjection::toSummaryVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), page, limit);
    }

    @Override
    public ProblemListDetailVO getProblemList(String id) {
        ProblemList list = findByIdOrThrow(id);

        ProblemListDetailVO vo = new ProblemListDetailVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setDescription(list.getDescription());
        vo.setAuthorId(list.getAuthorId());
        vo.setIsPublic(list.getIsPublic());
        vo.setIsFeatured(list.getIsFeatured());
        vo.setBannerTag(list.getBannerTag());
        vo.setBannerIcon(list.getBannerIcon());
        vo.setBannerTheme(list.getBannerTheme());
        vo.setBannerOrder(list.getBannerOrder());
        vo.setCreatedAt(list.getCreatedAt());
        vo.setUpdatedAt(list.getUpdatedAt());

        // Admin view: not owner, not saved
        vo.setIsOwner(false);
        vo.setIsSaved(false);

        // Get author info
        User author = userMapper.selectById(list.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.getName());
            vo.setAuthorUsername(author.getUsername());
        }

        // Get problems in the list
        List<ProblemListProblemRelation> relations = problemListProblemMapper.findByListId(id);
        if (!relations.isEmpty()) {
            Set<Long> problemIds = relations.stream()
                    .map(ProblemListProblemRelation::getProblemId)
                    .collect(Collectors.toSet());
            List<Problem> problems = problemMapper.selectBatchIds(problemIds);
            Map<Long, Problem> problemMap = problems.stream()
                    .collect(Collectors.toMap(Problem::getId, p -> p));

            // Batch-fetch tags for problems in the list
            List<ProblemMapper.ProblemTagDTO> tagDTOs = problemMapper.selectTagsByProblemIds(new ArrayList<>(problemIds));
            Map<Long, List<com.ulticode.modules.problem.dto.ProblemVO.ProblemTagVO>> tagMap = tagDTOs.stream()
                    .collect(Collectors.groupingBy(
                            ProblemMapper.ProblemTagDTO::problemId,
                            Collectors.mapping(dto -> {
                                com.ulticode.modules.problem.dto.ProblemVO.ProblemTagVO tagVO = new com.ulticode.modules.problem.dto.ProblemVO.ProblemTagVO();
                                tagVO.setId(dto.tagId());
                                tagVO.setLabel(dto.tagName());
                                return tagVO;
                            }, Collectors.toList())
                    ));

            List<ProblemListDetailVO.ProblemInListVO> problemVOs = relations.stream()
                    .map(rel -> {
                        Problem problem = problemMap.get(rel.getProblemId());
                        if (problem == null) return null;

                        ProblemListDetailVO.ProblemInListVO pvo = new ProblemListDetailVO.ProblemInListVO();
                        pvo.setId(problem.getId());
                        pvo.setSlug(problem.getSlug());
                        pvo.setTitle(problem.getTitle());
                        pvo.setDifficulty(problem.getDifficulty());
                        pvo.setStatus(problem.getStatus());
                        pvo.setSortOrder(rel.getSortOrder());
                        pvo.setAddedAt(rel.getAddedAt());
                        pvo.setAcceptanceRate(problem.getAcceptanceRate());
                        pvo.setIsPremium(problem.getIsPremium());
                        pvo.setHasSolution(problem.getHasSolution());
                        pvo.setTags(tagMap.getOrDefault(problem.getId(), List.of()));
                        return pvo;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            vo.setProblems(problemVOs);
        } else {
            vo.setProblems(Collections.emptyList());
        }

        // Build stats
        ProblemListDetailVO.ProblemListStatsVO statsVO = new ProblemListDetailVO.ProblemListStatsVO();
        statsVO.setListId(id);
        List<ProblemListDetailVO.ProblemInListVO> problems = vo.getProblems();
        int totalCount = problems.size();
        int solvedCount = 0;
        int attemptedCount = 0;
        for (ProblemListDetailVO.ProblemInListVO p : problems) {
            String status = p.getStatus();
            if ("solved".equalsIgnoreCase(status)) {
                solvedCount++;
            } else if ("attempted".equalsIgnoreCase(status)) {
                attemptedCount++;
            }
        }
        int todoCount = Math.max(0, totalCount - solvedCount - attemptedCount);
        double progress = totalCount == 0 ? 0.0 : ((double) solvedCount / totalCount) * 100.0;
        statsVO.setTotalCount(totalCount);
        statsVO.setSolvedCount(solvedCount);
        statsVO.setAttemptedCount(attemptedCount);
        statsVO.setTodoCount(todoCount);
        statsVO.setProgress(progress);
        vo.setStats(statsVO);

        // Admin view: no viewer state, no categories
        vo.setViewer(null);
        vo.setCategories(Collections.emptyList());

        return vo;
    }

    @Override
    public ProblemListSummaryVO createProblemList(CreateProblemListDTO dto, String authorId) {
        return problemListService.createList(authorId, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditActionUtil.UPDATE_PROBLEM_LIST, entityType = AuditActionUtil.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateProblemList(String id, UpdateProblemListDTO dto, String userId) {
        ProblemList list = findByIdOrThrow(id);

        AuditContext.setOldValues(java.util.Map.of(
            "name", list.getName() != null ? list.getName() : "",
            "description", list.getDescription() != null ? list.getDescription() : "",
            "isPublic", list.getIsPublic() != null ? list.getIsPublic() : false,
            "isFeatured", list.getIsFeatured() != null ? list.getIsFeatured() : false,
            "bannerTag", list.getBannerTag() != null ? list.getBannerTag() : "",
            "bannerOrder", list.getBannerOrder() != null ? list.getBannerOrder() : 0
        ));

        // Admin bypass: update fields directly without ownership check
        if (dto.getName() != null) {
            list.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            list.setDescription(dto.getDescription());
        }
        if (dto.getIsPublic() != null) {
            list.setIsPublic(dto.getIsPublic());
        }
        if (dto.getBannerTag() != null) {
            list.setBannerTag(dto.getBannerTag());
        }
        if (dto.getBannerIcon() != null) {
            list.setBannerIcon(dto.getBannerIcon());
        }
        if (dto.getBannerTheme() != null) {
            list.setBannerTheme(dto.getBannerTheme());
        }
        if (dto.getBannerOrder() != null) {
            list.setBannerOrder(dto.getBannerOrder());
        }
        if (dto.getIsFeatured() != null) {
            list.setIsFeatured(dto.getIsFeatured());
        }

        problemListMapper.updateById(list);

        AuditContext.setNewValues(java.util.Map.of(
            "name", list.getName() != null ? list.getName() : "",
            "isPublic", list.getIsPublic() != null ? list.getIsPublic() : false,
            "isFeatured", list.getIsFeatured() != null ? list.getIsFeatured() : false
        ));

        return toSummaryVO(list);
    }

    @Override
    @Audited(action = AuditActionUtil.DELETE_PROBLEM_LIST, entityType = AuditActionUtil.ENTITY_PROBLEM_LIST, userIdFrom = "id")
    public void deleteProblemList(String id) {
        ProblemList list = findByIdOrThrow(id);
        AuditContext.setOldValues(java.util.Map.of(
            "name", list.getName() != null ? list.getName() : "",
            "authorId", list.getAuthorId() != null ? list.getAuthorId() : ""
        ));
        problemListService.deleteList(id, list.getAuthorId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditActionUtil.UPDATE_PROBLEM_LIST, entityType = AuditActionUtil.ENTITY_PROBLEM_LIST, userIdFrom = "id")
    public void updateListProblems(String id, UpdateProblemListProblemsDTO dto) {
        ProblemList list = findByIdOrThrow(id);

        problemListProblemMapper.deleteByListId(id);

        if (dto.getProblems() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Problems list is required");
        }

        for (UpdateProblemListProblemsDTO.ProblemEntry entry : dto.getProblems()) {
            ProblemListProblemRelation relation = new ProblemListProblemRelation();
            relation.setListId(id);
            relation.setProblemId(entry.getProblemId());
            relation.setSortOrder(entry.getSortOrder());
            problemListProblemMapper.insert(relation);
        }

        AuditContext.setNewValues(java.util.Map.of("updatedProblems", dto.getProblems().size()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditActionUtil.UPDATE_PROBLEM_LIST, entityType = AuditActionUtil.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateBasicInfo(String id, String userId, UpdateBasicInfoDTO dto) {
        ProblemList list = findByIdOrThrow(id);

        AuditContext.setOldValues(java.util.Map.of(
            "name", list.getName() != null ? list.getName() : "",
            "description", list.getDescription() != null ? list.getDescription() : ""
        ));

        list.setName(dto.getName());
        list.setDescription(dto.getDescription());
        problemListMapper.updateById(list);

        AuditContext.setNewValues(java.util.Map.of(
            "name", list.getName() != null ? list.getName() : "",
            "description", list.getDescription() != null ? list.getDescription() : ""
        ));

        return toSummaryVO(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditActionUtil.UPDATE_PROBLEM_LIST, entityType = AuditActionUtil.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateVisibility(String id, String userId, UpdateVisibilityDTO dto) {
        ProblemList list = findByIdOrThrow(id);

        AuditContext.setOldValues(java.util.Map.of(
            "isPublic", list.getIsPublic() != null ? list.getIsPublic() : false,
            "isFeatured", list.getIsFeatured() != null ? list.getIsFeatured() : false
        ));

        if (dto.getIsPublic() != null) {
            list.setIsPublic(dto.getIsPublic());
        }
        if (dto.getIsFeatured() != null) {
            list.setIsFeatured(dto.getIsFeatured());
        }
        problemListMapper.updateById(list);

        AuditContext.setNewValues(java.util.Map.of(
            "isPublic", list.getIsPublic() != null ? list.getIsPublic() : false,
            "isFeatured", list.getIsFeatured() != null ? list.getIsFeatured() : false
        ));

        return toSummaryVO(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditActionUtil.UPDATE_PROBLEM_LIST, entityType = AuditActionUtil.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateBanner(String id, String userId, UpdateBannerDTO dto) {
        ProblemList list = findByIdOrThrow(id);

        AuditContext.setOldValues(java.util.Map.of(
            "bannerTag", list.getBannerTag() != null ? list.getBannerTag() : "",
            "bannerTheme", list.getBannerTheme() != null ? list.getBannerTheme() : "",
            "bannerOrder", list.getBannerOrder() != null ? list.getBannerOrder() : 0
        ));

        if (dto.getBannerTag() != null) {
            list.setBannerTag(dto.getBannerTag());
        }
        if (dto.getBannerIcon() != null) {
            list.setBannerIcon(dto.getBannerIcon());
        }
        if (dto.getBannerTheme() != null) {
            list.setBannerTheme(dto.getBannerTheme());
        }
        if (dto.getBannerOrder() != null) {
            list.setBannerOrder(dto.getBannerOrder());
        }
        problemListMapper.updateById(list);

        AuditContext.setNewValues(java.util.Map.of(
            "bannerTag", list.getBannerTag() != null ? list.getBannerTag() : "",
            "bannerTheme", list.getBannerTheme() != null ? list.getBannerTheme() : "",
            "bannerOrder", list.getBannerOrder() != null ? list.getBannerOrder() : 0
        ));

        return toSummaryVO(list);
    }

    private ProblemList findByIdOrThrow(String id) {
        ProblemList list = problemListMapper.selectById(id);
        if (list == null) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND);
        }
        return list;
    }

    private ProblemListSummaryVO toSummaryVO(ProblemList list) {
        // Delegate to the projection — entity→VO rules + author enrichment live there now.
        // Kept as a private helper so the four write-path returns stay short.
        return problemListProjection.toSummaryVO(list);
    }
}
