package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.dto.problem.*;
import com.ulticode.modules.admin.service.AdminProblemService;
import com.ulticode.modules.admin.dto.problem.AdminProblemMapper;
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
    private final AdminProblemMapper mapper;
    private final com.ulticode.modules.problem.service.ProblemService problemService;

    @Override
    public HeaderDataVO getHeaderData(Long id) {
        Problem problem = findProblemById(id);
        return toHeaderDataVO(problem);
    }

    @Override
    public DescriptionDataVO getDescriptionData(Long id) {
        Problem problem = findProblemById(id);
        DescriptionDataVO vo = mapper.toDescriptionDataVO(problem);

        ProblemDetail detail = findProblemDetailByProblemId(id);
        vo.setDetail(mapper.toDetailInfo(detail));
        vo.setTags(findTagsByProblemId(id));
        vo.setExamples(findExamplesByProblemId(id));

        return vo;
    }

    @Override
    public CodeDataVO getCodeData(Long id) {
        findProblemById(id);
        CodeDataVO vo = mapper.toCodeDataVO(id);

        List<ProblemLanguage> languages = problemLanguageMapper.findByProblemId(id);
        vo.setLanguages(mapper.toLanguageInfoList(languages));

        return vo;
    }

    @Override
    public CasesDataVO getCasesData(Long id) {
        findProblemById(id);
        CasesDataVO vo = mapper.toCasesDataVO(id);

        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(id);
        vo.setExamples(mapper.toExampleInfoList(examples));

        ProblemDetail detail = findProblemDetailByProblemId(id);
        vo.setDetail(mapper.toCasesDetailInfo(detail));

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
        return mapper.toProblemTagVOList(problemTagMapper.selectBatchIds(tagIds));
    }

    private List<ProblemExampleVO> findExamplesByProblemId(Long problemId) {
        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(problemId);
        if (examples == null || examples.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.toProblemExampleVOList(examples);
    }

    private HeaderDataVO toHeaderDataVO(Problem problem) {
        return mapper.toHeaderDataVO(problem);
    }
}
