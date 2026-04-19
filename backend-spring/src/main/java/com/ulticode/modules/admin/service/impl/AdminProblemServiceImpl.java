package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.dto.problem.*;
import com.ulticode.modules.admin.service.AdminProblemService;
import com.ulticode.modules.problem.entity.*;
import com.ulticode.modules.problem.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of AdminProblemService.
 * Provides tab-specific data for problem management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProblemServiceImpl implements AdminProblemService {

    private final ProblemMapper problemMapper;
    private final ProblemDetailMapper problemDetailMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final ProblemLanguageMapper problemLanguageMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ObjectMapper objectMapper;
    private final com.ulticode.modules.problem.service.ProblemService problemService;

    @Override
    public HeaderDataVO getHeaderData(Long id) {
        Problem problem = findProblemById(id);
        return toHeaderDataVO(problem);
    }

    @Override
    public DescriptionDataVO getDescriptionData(Long id) {
        Problem problem = findProblemById(id);

        DescriptionDataVO vo = new DescriptionDataVO();
        vo.setId(String.valueOf(problem.getId()));
        vo.setTitle(problem.getTitle());
        vo.setSlug(problem.getSlug());
        vo.setDifficulty(problem.getDifficulty());
        vo.setStatus(problem.getStatus());
        vo.setIsPremium(problem.getIsPremium());
        vo.setIsPublished(problem.getIsPublished());
        vo.setCreatedAt(problem.getCreatedAt());
        vo.setUpdatedAt(problem.getUpdatedAt());
        vo.setPublishedAt(problem.getPublishedAt());

        // Fetch detail
        ProblemDetail detail = findProblemDetailByProblemId(id);
        if (detail != null) {
            DescriptionDataVO.DetailInfo detailInfo = new DescriptionDataVO.DetailInfo();
            detailInfo.setSummary(detail.getSummary());
            detailInfo.setConstraintsJson(parseJsonArray(detail.getConstraintsJson()));
            detailInfo.setHints(parseJsonArray(detail.getHints()));
            vo.setDetail(detailInfo);
        }

        // Fetch tags
        vo.setTags(findTagsByProblemId(id));

        // Fetch examples
        vo.setExamples(findExamplesByProblemId(id));

        return vo;
    }

    @Override
    public CodeDataVO getCodeData(Long id) {
        findProblemById(id); // Verify problem exists

        CodeDataVO vo = new CodeDataVO();
        vo.setId(String.valueOf(id));

        // Fetch languages
        List<ProblemLanguage> languages = problemLanguageMapper.findByProblemId(id);
        if (languages != null && !languages.isEmpty()) {
            List<CodeDataVO.LanguageInfo> languageInfos = languages.stream()
                    .map(lang -> {
                        CodeDataVO.LanguageInfo info = new CodeDataVO.LanguageInfo();
                        info.setId(lang.getId());
                        info.setLanguage(lang.getLabel());
                        info.setValue(lang.getValue());
                        info.setStyle(lang.getStyle());
                        info.setStarterCode(lang.getStarterCode());
                        return info;
                    })
                    .collect(Collectors.toList());
            vo.setLanguages(languageInfos);
        }

        return vo;
    }

    @Override
    public CasesDataVO getCasesData(Long id) {
        findProblemById(id); // Verify problem exists

        CasesDataVO vo = new CasesDataVO();
        vo.setId(String.valueOf(id));

        // Fetch examples
        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(id);
        if (examples != null && !examples.isEmpty()) {
            List<CasesDataVO.ExampleInfo> exampleInfos = examples.stream()
                    .map(ex -> {
                        CasesDataVO.ExampleInfo info = new CasesDataVO.ExampleInfo();
                        info.setId(ex.getId());
                        info.setInput(ex.getInputText());
                        info.setOutput(ex.getOutputText());
                        info.setExplanation(ex.getExplanation());
                        info.setOrder(ex.getExampleOrder());
                        return info;
                    })
                    .collect(Collectors.toList());
            vo.setExamples(exampleInfos);
        }

        // Fetch detail for constraints and hints
        ProblemDetail detail = findProblemDetailByProblemId(id);
        if (detail != null) {
            CasesDataVO.DetailInfo detailInfo = new CasesDataVO.DetailInfo();
            detailInfo.setConstraintsJson(parseJsonArray(detail.getConstraintsJson()));
            detailInfo.setHints(parseJsonArray(detail.getHints()));
            vo.setDetail(detailInfo);
        }

        // Fetch tags
        vo.setTags(findTagsByProblemId(id));

        return vo;
    }

    @Override
    public List<BulkProblemResultDTO> bulkAction(BulkProblemRequestDTO request) {
        List<BulkProblemResultDTO> results = new ArrayList<>();
        for (String idStr : request.getIds()) {
            try {
                Long id = Long.parseLong(idStr);
                switch (request.getAction()) {
                    case publish -> problemService.publishProblem(id);
                    case unpublish -> problemService.unpublishProblem(id);
                    case delete -> problemService.deleteProblem(id);
                    case edit -> {
                        var params = request.getParams();
                        if (params != null && params.containsKey("difficulty")) {
                            // Update difficulty via the Problem entity
                            Problem problem = problemMapper.selectById(id);
                            if (problem != null) {
                                problem.setDifficulty((String) params.get("difficulty"));
                                problemMapper.updateById(problem);
                            }
                        }
                    }
                }
                results.add(new BulkProblemResultDTO(idStr, true, null));
            } catch (Exception e) {
                log.error("Bulk action failed for problem id={}: {}", idStr, e.getMessage(), e);
                results.add(new BulkProblemResultDTO(idStr, false, e.getMessage()));
            }
        }
        return results;
    }

    // ========== Private Helper Methods ==========

    private Problem findProblemById(Long id) {
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        return problem;
    }

    private ProblemDetail findProblemDetailByProblemId(Long problemId) {
        LambdaQueryWrapper<ProblemDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemDetail::getProblemId, problemId);
        return problemDetailMapper.selectOne(wrapper);
    }

    private List<ProblemTagVO> findTagsByProblemId(Long problemId) {
        List<String> tagIds = problemTagRelationMapper.findTagIdsByProblemId(problemId);
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProblemTag> tags = problemTagMapper.selectBatchIds(tagIds);
        return tags.stream()
                .map(tag -> {
                    ProblemTagVO tagVO = new ProblemTagVO();
                    tagVO.setId(tag.getId());
                    tagVO.setLabel(tag.getLabel());
                    return tagVO;
                })
                .collect(Collectors.toList());
    }

    private List<ProblemExampleVO> findExamplesByProblemId(Long problemId) {
        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(problemId);
        if (examples == null || examples.isEmpty()) {
            return Collections.emptyList();
        }

        return examples.stream()
                .map(ex -> {
                    ProblemExampleVO vo = new ProblemExampleVO();
                    vo.setId(ex.getId());
                    vo.setInput(ex.getInputText());
                    vo.setOutput(ex.getOutputText());
                    vo.setExplanation(ex.getExplanation());
                    vo.setOrder(ex.getExampleOrder());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private HeaderDataVO toHeaderDataVO(Problem problem) {
        HeaderDataVO vo = new HeaderDataVO();
        vo.setId(String.valueOf(problem.getId()));
        vo.setTitle(problem.getTitle());
        vo.setSlug(problem.getSlug());
        vo.setDifficulty(problem.getDifficulty());
        vo.setStatus(problem.getStatus());
        vo.setIsPremium(problem.getIsPremium());
        vo.setIsPublished(problem.getIsPublished());
        vo.setPublishedAt(problem.getPublishedAt());
        return vo;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array: {}", json, e);
            return null;
        }
    }
}
