package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.admin.service.AdminProblemListService;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.CreateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListProblemsDTO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListProblemRelation;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.problemlist.service.ProblemListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
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
    private final AuditHelper auditHelper;

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
                .map(this::toSummaryVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), page, limit);
    }

    @Override
    public ProblemListDetailVO getProblemList(String id) {
        return problemListService.getListOverview(id, null, "en");
    }

    @Override
    public ProblemListSummaryVO createProblemList(CreateProblemListDTO dto, String authorId) {
        return problemListService.createList(authorId, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProblemListSummaryVO updateProblemList(String id, UpdateProblemListDTO dto, String userId) {
        ProblemList list = problemListMapper.selectById(id);
        if (list == null) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND);
        }

        java.util.Map<String, Object> oldValues = new java.util.HashMap<>();
        oldValues.put("name", list.getName());
        oldValues.put("description", list.getDescription());
        oldValues.put("isPublic", list.getIsPublic());
        oldValues.put("isFeatured", list.getIsFeatured());
        oldValues.put("bannerTag", list.getBannerTag());
        oldValues.put("bannerIcon", list.getBannerIcon());
        oldValues.put("bannerTheme", list.getBannerTheme());
        oldValues.put("bannerOrder", list.getBannerOrder());

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

        auditHelper.log(
            AuditActionUtil.UPDATE_PROBLEM_LIST,
            AuditActionUtil.ENTITY_PROBLEM_LIST,
            id,
            oldValues,
            Map.of("name", list.getName(), "isPublic", list.getIsPublic(), "isFeatured", list.getIsFeatured())
        );

        return toSummaryVO(list);
    }

    @Override
    public void deleteProblemList(String id) {
        ProblemList list = problemListMapper.selectById(id);
        if (list == null) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND);
        }
        auditHelper.log(
            AuditActionUtil.DELETE_PROBLEM_LIST,
            AuditActionUtil.ENTITY_PROBLEM_LIST,
            id,
            Map.of("name", list.getName(), "authorId", list.getAuthorId()),
            null
        );
        problemListService.deleteList(id, list.getAuthorId());
    }

    @Override
    public void updateListProblems(String id, UpdateProblemListProblemsDTO dto) {
        ProblemList list = problemListMapper.selectById(id);
        if (list == null) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND);
        }

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

        auditHelper.log(
            AuditActionUtil.UPDATE_PROBLEM_LIST,
            AuditActionUtil.ENTITY_PROBLEM_LIST,
            id,
            null,
            Map.of("updatedProblems", dto.getProblems().size())
        );
    }

    private ProblemListSummaryVO toSummaryVO(ProblemList list) {
        ProblemListSummaryVO vo = new ProblemListSummaryVO();
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

        // Count problems
        vo.setProblemCount((int) problemListProblemMapper.countByListId(list.getId()));

        return vo;
    }
}
