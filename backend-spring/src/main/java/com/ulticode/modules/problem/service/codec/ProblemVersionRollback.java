package com.ulticode.modules.problem.service.codec;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemDetail;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Owns the multi-table restore algorithm that reverts a Problem to a
 * prior snapshot version.
 *
 * <p><strong>Deep module</strong> &mdash; extracted from the 142-LOC
 * {@code rollbackToVersion} body inside {@code ProblemVersionServiceImpl}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemVersionRollback {

    private final ProblemMapper problemMapper;
    private final ProblemDetailMapper problemDetailMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final ProblemLanguageMapper problemLanguageMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public void restore(Long problemId, Map<String, Object> snapshot) {
        restoreProblem(problemId, snapshot);
        restoreDetail(problemId, snapshot);
        restoreExamples(problemId, snapshot);
        restoreLanguages(problemId, snapshot);
        restoreTags(problemId, snapshot);
    }

    private void restoreProblem(Long problemId, Map<String, Object> snapshot) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            return;
        }
        problem.setTitle((String) snapshot.get("title"));
        problem.setSlug((String) snapshot.get("slug"));
        problem.setDifficulty((String) snapshot.get("difficulty"));
        if (snapshot.get("isPremium") != null) {
            problem.setIsPremium((Boolean) snapshot.get("isPremium"));
        }
        if (snapshot.get("isPublished") != null) {
            problem.setIsPublished((Boolean) snapshot.get("isPublished"));
        }
        problemMapper.updateById(problem);
    }

    @SuppressWarnings("unchecked")
    private void restoreDetail(Long problemId, Map<String, Object> snapshot) {
        Problem problem = problemMapper.selectById(problemId);
        ProblemDetail detail = problemDetailMapper.selectOne(
                new LambdaQueryWrapper<ProblemDetail>().eq(ProblemDetail::getProblemId, problemId));
        if (detail == null) {
            detail = new ProblemDetail();
            detail.setId(uuidGenerator.newId());
            detail.setProblemId(problemId);
            java.util.Objects.requireNonNull(problem != null ? problem.getSlug() : null,
                    "Problem.slug must not be null");
            detail.setSlug(problem.getSlug());
            detail.setConstraintsJson(ProblemDetail.EMPTY_JSON_ARRAY);
        }
        detail.setSummary((String) snapshot.get("summary"));
        detail.setFollowUp((String) snapshot.get("followUp"));
        detail.setConstraintsJson(serializeOrCatch((List<String>) snapshot.get("constraints")));
        detail.setHints(serializeOrCatch((List<String>) snapshot.get("hints")));

        if (problemDetailMapper.selectById(detail.getId()) != null) {
            problemDetailMapper.updateById(detail);
        } else {
            problemDetailMapper.insert(detail);
        }
    }

    private String serializeOrCatch(List<String> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize list during rollback", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreExamples(Long problemId, Map<String, Object> snapshot) {
        problemExampleMapper.delete(
                new LambdaQueryWrapper<ProblemExample>().eq(ProblemExample::getProblemId, problemId));
        List<Map<String, Object>> examples = (List<Map<String, Object>>) snapshot.get("examples");
        if (examples == null) {
            return;
        }
        for (int i = 0; i < examples.size(); i++) {
            Map<String, Object> ex = examples.get(i);
            ProblemExample example = new ProblemExample();
            example.setId(uuidGenerator.newId());
            example.setProblemId(problemId);
            example.setExampleOrder(i + 1);
            example.setInputText((String) (ex.get("input") != null ? ex.get("input") : ex.get("inputText")));
            example.setOutputText((String) (ex.get("output") != null ? ex.get("output") : ex.get("outputText")));
            example.setExplanation((String) ex.get("explanation"));
            Object inputs = ex.get("inputs");
            if (inputs != null) {
                try {
                    example.setInputs(objectMapper.writeValueAsString(inputs));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize inputs during rollback", e);
                }
            }
            problemExampleMapper.insert(example);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreLanguages(Long problemId, Map<String, Object> snapshot) {
        problemLanguageMapper.delete(
                new LambdaQueryWrapper<ProblemLanguage>().eq(ProblemLanguage::getProblemId, problemId));
        List<Map<String, Object>> languages = (List<Map<String, Object>>) snapshot.get("languages");
        if (languages == null) {
            return;
        }
        for (Map<String, Object> lang : languages) {
            ProblemLanguage language = new ProblemLanguage();
            language.setId(uuidGenerator.newId());
            language.setProblemId(problemId);
            language.setLabel((String) lang.get("label"));
            language.setValue((String) lang.get("value"));
            language.setStyle((String) lang.get("style"));
            language.setStarterCode((String) lang.get("starterCode"));
            problemLanguageMapper.insert(language);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreTags(Long problemId, Map<String, Object> snapshot) {
        problemTagRelationMapper.delete(
                new LambdaQueryWrapper<ProblemTagRelation>().eq(ProblemTagRelation::getProblemId, problemId));
        List<String> tagLabels = (List<String>) snapshot.get("tags");
        if (tagLabels == null) {
            return;
        }
        for (String tagLabel : tagLabels) {
            ProblemTag tag = problemTagMapper.selectOne(
                    new LambdaQueryWrapper<ProblemTag>().eq(ProblemTag::getLabel, tagLabel));
            if (tag != null) {
                ProblemTagRelation relation = new ProblemTagRelation();
                relation.setProblemId(problemId);
                relation.setTagId(tag.getId());
                problemTagRelationMapper.insert(relation);
            }
        }
    }
}
