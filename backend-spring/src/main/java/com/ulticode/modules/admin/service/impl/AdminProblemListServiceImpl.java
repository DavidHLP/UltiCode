package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
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
        return problemListService.updateList(id, userId, dto);
    }

    @Override
    public void deleteProblemList(String id) {
        ProblemList list = problemListMapper.selectById(id);
        if (list == null) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND);
        }
        problemListService.deleteList(id, list.getAuthorId());
    }

    @Override
    public void updateListProblems(String id, UpdateProblemListProblemsDTO dto) {
        ProblemList list = problemListMapper.selectById(id);
        if (list == null) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND);
        }

        problemListProblemMapper.deleteByListId(id);

        for (UpdateProblemListProblemsDTO.ProblemEntry entry : dto.getProblems()) {
            ProblemListProblemRelation relation = new ProblemListProblemRelation();
            relation.setListId(id);
            relation.setProblemId(entry.getProblemId());
            relation.setSortOrder(entry.getSortOrder());
            problemListProblemMapper.insert(relation);
        }
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
