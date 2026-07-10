package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.PartialUpdate;
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
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);
        int page = pageRequest.page();
        int limit = pageRequest.pageSize();

        Page<ProblemList> pageResult = new Page<>(page, limit);
        Page<ProblemList> result = problemListMapper.selectPage(pageResult, wrapper);

        List<ProblemListSummaryVO> voList = result.getRecords().stream()
                .map(problemListProjection::toSummaryVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), page, limit);
    }

    @Override
    public ProblemListDetailVO getProblemList(String id) {
        // Detail shaping (author / problems / tags / stats) lives on the
        // projection so the admin service stays a write+audit module.
        return problemListProjection.toAdminDetailVO(findByIdOrThrow(id));
    }

    @Override
    public ProblemListSummaryVO createProblemList(CreateProblemListDTO dto, String authorId) {
        return problemListService.createList(authorId, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
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

        // Admin bypass: update fields directly without ownership check.
        // PartialUpdate silently skips null fields, so a PATCH with only one
        // field still preserves the rest of the row.
        PartialUpdate.setIfPresent(dto, UpdateProblemListDTO::getName, list::setName);
        PartialUpdate.setIfPresent(dto, UpdateProblemListDTO::getDescription, list::setDescription);
        PartialUpdate.setIfPresent(dto, UpdateProblemListDTO::getIsPublic, list::setIsPublic);
        PartialUpdate.setIfPresentText(dto, UpdateProblemListDTO::getBannerTag, list::setBannerTag);
        PartialUpdate.setIfPresentText(dto, UpdateProblemListDTO::getBannerIcon, list::setBannerIcon);
        PartialUpdate.setIfPresentText(dto, UpdateProblemListDTO::getBannerTheme, list::setBannerTheme);
        PartialUpdate.setIfPresent(dto, UpdateProblemListDTO::getBannerOrder, list::setBannerOrder);
        PartialUpdate.setIfPresent(dto, UpdateProblemListDTO::getIsFeatured, list::setIsFeatured);

        problemListMapper.updateById(list);

        AuditContext.setNewValues(java.util.Map.of(
            "name", list.getName() != null ? list.getName() : "",
            "isPublic", list.getIsPublic() != null ? list.getIsPublic() : false,
            "isFeatured", list.getIsFeatured() != null ? list.getIsFeatured() : false
        ));

        return toSummaryVO(list);
    }

    @Override
    @Audited(action = AuditVocabulary.DELETE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "id")
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
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "id")
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
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateBasicInfo(String id, String userId, UpdateBasicInfoDTO dto) {
        ProblemList list = findByIdOrThrow(id);

        AuditContext.setOldValues(java.util.Map.of(
            "name", list.getName() != null ? list.getName() : "",
            "description", list.getDescription() != null ? list.getDescription() : ""
        ));

        PartialUpdate.setIfPresentText(dto, UpdateBasicInfoDTO::getName, list::setName);
        PartialUpdate.setIfPresentText(dto, UpdateBasicInfoDTO::getDescription, list::setDescription);
        problemListMapper.updateById(list);

        AuditContext.setNewValues(java.util.Map.of(
            "name", list.getName() != null ? list.getName() : "",
            "description", list.getDescription() != null ? list.getDescription() : ""
        ));

        return toSummaryVO(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateVisibility(String id, String userId, UpdateVisibilityDTO dto) {
        ProblemList list = findByIdOrThrow(id);

        AuditContext.setOldValues(java.util.Map.of(
            "isPublic", list.getIsPublic() != null ? list.getIsPublic() : false,
            "isFeatured", list.getIsFeatured() != null ? list.getIsFeatured() : false
        ));

        PartialUpdate.setIfPresent(dto, UpdateVisibilityDTO::getIsPublic, list::setIsPublic);
        PartialUpdate.setIfPresent(dto, UpdateVisibilityDTO::getIsFeatured, list::setIsFeatured);
        problemListMapper.updateById(list);

        AuditContext.setNewValues(java.util.Map.of(
            "isPublic", list.getIsPublic() != null ? list.getIsPublic() : false,
            "isFeatured", list.getIsFeatured() != null ? list.getIsFeatured() : false
        ));

        return toSummaryVO(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateBanner(String id, String userId, UpdateBannerDTO dto) {
        ProblemList list = findByIdOrThrow(id);

        AuditContext.setOldValues(java.util.Map.of(
            "bannerTag", list.getBannerTag() != null ? list.getBannerTag() : "",
            "bannerTheme", list.getBannerTheme() != null ? list.getBannerTheme() : "",
            "bannerOrder", list.getBannerOrder() != null ? list.getBannerOrder() : 0
        ));

        PartialUpdate.setIfPresentText(dto, UpdateBannerDTO::getBannerTag, list::setBannerTag);
        PartialUpdate.setIfPresentText(dto, UpdateBannerDTO::getBannerIcon, list::setBannerIcon);
        PartialUpdate.setIfPresentText(dto, UpdateBannerDTO::getBannerTheme, list::setBannerTheme);
        PartialUpdate.setIfPresent(dto, UpdateBannerDTO::getBannerOrder, list::setBannerOrder);
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
