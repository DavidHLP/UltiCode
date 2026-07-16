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
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.projection.ProblemListProjection;
import com.ulticode.modules.problemlist.service.ProblemListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
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
    private final ProblemListService problemListService;
    private final ProblemListProjection problemListProjection;

    @Override
    public PageResult<ProblemListSummaryVO> getProblemLists(AdminProblemListQueryDTO query) {
        LambdaQueryWrapper<ProblemList> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getSearch())) {
            String search = "%" + query.getSearch() + "%";
            wrapper.and(w -> w
                    .like(ProblemList::getName, search)
                    .or()
                    .like(ProblemList::getDescription, search));
        }

        if (query.getIsFeatured() != null) {
            wrapper.eq(ProblemList::getIsFeatured, query.getIsFeatured());
        }

        if (query.getIsPublic() != null) {
            wrapper.eq(ProblemList::getIsPublic, query.getIsPublic());
        }

        boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        switch (sortBy) {
            case "name" -> wrapper.orderBy(true, isAsc, ProblemList::getName);
            case "bannerOrder" -> wrapper.orderBy(true, isAsc, ProblemList::getBannerOrder);
            default -> wrapper.orderBy(true, isAsc, ProblemList::getCreatedAt);
        }

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
        return problemListProjection.toAdminDetailVO(problemListService.findEntityById(id));
    }

    @Override
    public ProblemListSummaryVO createProblemList(CreateProblemListDTO dto, String authorId) {
        return problemListService.createList(authorId, dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateProblemList(String id, UpdateProblemListDTO dto, String userId) {
        ProblemList list = problemListService.findEntityById(id);

        AuditContext.setOldValues(oldSnapshot(list));

        ProblemListSummaryVO vo = problemListService.adminUpdateProblemList(id, dto);

        AuditContext.setNewValues(newSnapshot(vo));

        return vo;
    }

    @Override
    @Audited(action = AuditVocabulary.DELETE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId", entityIdFrom = "id")
    public void deleteProblemList(String id, String userId) {
        ProblemList list = problemListService.findEntityById(id);
        AuditContext.setOldValues(deleteSnapshot(list));
        problemListService.adminDeleteProblemList(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId", entityIdFrom = "id")
    public void updateListProblems(String id, UpdateProblemListProblemsDTO dto, String userId) {
        problemListService.findEntityById(id);

        if (dto.getProblems() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Problems list is required");
        }

        problemListService.adminReplaceListProblems(id, dto);

        AuditContext.setNewValues(Map.of("updatedProblems", dto.getProblems().size()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateBasicInfo(String id, String userId, UpdateBasicInfoDTO dto) {
        ProblemList list = problemListService.findEntityById(id);

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("name", list.getName() != null ? list.getName() : "");
        oldValues.put("description", list.getDescription() != null ? list.getDescription() : "");
        AuditContext.setOldValues(oldValues);

        ProblemListSummaryVO vo = problemListService.adminUpdateBasicInfo(id, dto);

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("name", vo.getName() != null ? vo.getName() : "");
        newValues.put("description", vo.getDescription() != null ? vo.getDescription() : "");
        AuditContext.setNewValues(newValues);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateVisibility(String id, String userId, UpdateVisibilityDTO dto) {
        ProblemList list = problemListService.findEntityById(id);

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("isPublic", list.getIsPublic() != null ? list.getIsPublic() : false);
        oldValues.put("isFeatured", list.getIsFeatured() != null ? list.getIsFeatured() : false);
        AuditContext.setOldValues(oldValues);

        ProblemListSummaryVO vo = problemListService.adminUpdateVisibility(id, dto);

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("isPublic", vo.getIsPublic() != null ? vo.getIsPublic() : false);
        newValues.put("isFeatured", vo.getIsFeatured() != null ? vo.getIsFeatured() : false);
        AuditContext.setNewValues(newValues);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Audited(action = AuditVocabulary.UPDATE_PROBLEM_LIST, entityType = AuditVocabulary.ENTITY_PROBLEM_LIST, userIdFrom = "userId")
    public ProblemListSummaryVO updateBanner(String id, String userId, UpdateBannerDTO dto) {
        ProblemList list = problemListService.findEntityById(id);

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("bannerTag", list.getBannerTag() != null ? list.getBannerTag() : "");
        oldValues.put("bannerTheme", list.getBannerTheme() != null ? list.getBannerTheme() : "");
        oldValues.put("bannerOrder", list.getBannerOrder() != null ? list.getBannerOrder() : 0);
        AuditContext.setOldValues(oldValues);

        ProblemListSummaryVO vo = problemListService.adminUpdateBanner(id, dto);

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("bannerTag", vo.getBannerTag() != null ? vo.getBannerTag() : "");
        newValues.put("bannerTheme", vo.getBannerTheme() != null ? vo.getBannerTheme() : "");
        newValues.put("bannerOrder", vo.getBannerOrder() != null ? vo.getBannerOrder() : 0);
        AuditContext.setNewValues(newValues);

        return vo;
    }

    private static Map<String, Object> oldSnapshot(ProblemList list) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", list.getName() != null ? list.getName() : "");
        values.put("description", list.getDescription() != null ? list.getDescription() : "");
        values.put("isPublic", list.getIsPublic() != null ? list.getIsPublic() : false);
        values.put("isFeatured", list.getIsFeatured() != null ? list.getIsFeatured() : false);
        values.put("bannerTag", list.getBannerTag() != null ? list.getBannerTag() : "");
        values.put("bannerOrder", list.getBannerOrder() != null ? list.getBannerOrder() : 0);
        return values;
    }

    private static Map<String, Object> newSnapshot(ProblemListSummaryVO vo) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", vo.getName() != null ? vo.getName() : "");
        values.put("isPublic", vo.getIsPublic() != null ? vo.getIsPublic() : false);
        values.put("isFeatured", vo.getIsFeatured() != null ? vo.getIsFeatured() : false);
        return values;
    }

    private static Map<String, Object> deleteSnapshot(ProblemList list) {
        Map<String, Object> values = new HashMap<>();
        values.put("name", list.getName() != null ? list.getName() : "");
        values.put("authorId", list.getAuthorId() != null ? list.getAuthorId() : "");
        return values;
    }
}
