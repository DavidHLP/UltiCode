package com.ulticode.modules.problem.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problem.dto.LanguageConfigDTO;
import com.ulticode.modules.problem.dto.ProblemDetailPublicVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemDetail;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default (and only) adapter for {@link ProblemDetailPort}. Owns the
 * detail-satellite write lifecycle — see the interface javadoc for why this is
 * a deep module.
 *
 * <p>All four satellite writes (detail upsert, language rebuild, example
 * rebuild, tag rebuild) run inside the {@code @Transactional}
 * {@link #applyDetailUpdate} so a failure in any one rolls the whole batch
 * back, matching the atomicity the {@code ProblemServiceImpl.updateProblem}
 * caller previously relied on.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultProblemDetailPort implements ProblemDetailPort {

    private final ProblemDetailMapper problemDetailMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final ProblemLanguageMapper problemLanguageMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void applyDetailUpdate(Long problemId, Problem problem, UpdateProblemDTO updateDTO) {
        Objects.requireNonNull(problem, "Problem must not be null");
        Objects.requireNonNull(updateDTO, "UpdateProblemDTO must not be null");

        boolean hasDetailUpdate = updateDTO.getSummary() != null || updateDTO.getContent() != null
                || updateDTO.getConstraintsJson() != null || updateDTO.getHints() != null;
        if (!hasDetailUpdate && updateDTO.getLanguages() == null
                && updateDTO.getTags() == null
                && (updateDTO.getExamples() == null || updateDTO.getExamples().isBlank())) {
            return;
        }

        if (hasDetailUpdate) {
            upsertDetailContent(problemId, problem, updateDTO);
        }

        if (updateDTO.getLanguages() != null) {
            rebuildLanguages(problemId, updateDTO.getLanguages());
        }

        if (updateDTO.getExamples() != null && !updateDTO.getExamples().isBlank()) {
            rebuildExamples(problemId, updateDTO.getExamples());
        }

        if (updateDTO.getTags() != null) {
            rebuildTags(problemId, updateDTO.getTags());
        }
    }

    private void upsertDetailContent(Long problemId, Problem problem, UpdateProblemDTO updateDTO) {
        LambdaQueryWrapper<ProblemDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemDetail::getProblemId, problemId);
        ProblemDetail detail = problemDetailMapper.selectOne(wrapper);

        boolean isNew = false;
        if (detail == null) {
            detail = new ProblemDetail();
            detail.setProblemId(problemId);
            detail.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
            // problem_details.slug NOT NULL — denormalize from Problem on insert.
            // Defensive null check: problems.slug is also NOT NULL in DB, so this
            // should never be null, but assert it to fail fast with a clear message.
            Objects.requireNonNull(problem.getSlug(),
                    "Problem.slug must not be null (DB constraint guarantees it, but assert defensively)");
            detail.setSlug(problem.getSlug());
            // constraints_json NOT NULL with no DB default — initialize to empty array
            detail.setConstraintsJson(ProblemDetail.EMPTY_JSON_ARRAY);
            isNew = true;
        }

        if (updateDTO.getSummary() != null) {
            detail.setSummary(updateDTO.getSummary());
        }
        if (updateDTO.getContent() != null) {
            detail.setContent(updateDTO.getContent());
        }
        if (updateDTO.getConstraintsJson() != null) {
            detail.setConstraintsJson(updateDTO.getConstraintsJson());
        }
        if (updateDTO.getHints() != null) {
            detail.setHints(updateDTO.getHints());
        }
        detail.setUpdatedAt(LocalDateTime.now());

        if (isNew) {
            problemDetailMapper.insert(detail);
        } else {
            problemDetailMapper.updateById(detail);
        }
    }

    private void rebuildLanguages(Long problemId, List<LanguageConfigDTO> languages) {
        if (languages == null || languages.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<ProblemLanguage> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ProblemLanguage::getProblemId, problemId);
        problemLanguageMapper.delete(deleteWrapper);

        for (LanguageConfigDTO config : languages) {
            ProblemLanguage template = problemLanguageMapper.findByValue(config.getLanguage());
            if (template == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Unsupported language: " + config.getLanguage());
            }

            ProblemLanguage language = new ProblemLanguage();
            language.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
            language.setProblemId(problemId);
            language.setLabel(template.getLabel());
            language.setValue(template.getValue());
            language.setStyle(template.getStyle());
            language.setStarterCode(config.getStarterCode() != null ? config.getStarterCode() : template.getStarterCode());
            problemLanguageMapper.insert(language);
        }
    }

    private void rebuildExamples(Long problemId, String examplesJson) {
        try {
            List<ProblemDetailPublicVO.ExampleData> examples = objectMapper.readValue(
                    examplesJson,
                    new TypeReference<List<ProblemDetailPublicVO.ExampleData>>() {}
            );

            LambdaQueryWrapper<ProblemExample> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(ProblemExample::getProblemId, problemId);
            problemExampleMapper.delete(deleteWrapper);

            for (int i = 0; i < examples.size(); i++) {
                ProblemDetailPublicVO.ExampleData ex = examples.get(i);
                ProblemExample example = new ProblemExample();
                example.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
                example.setProblemId(problemId);
                example.setExampleOrder(i + 1);
                example.setInputText(ex.getInputText());
                example.setOutputText(ex.getOutputText());
                example.setExplanation(ex.getExplanation());
                if (ex.getInputs() != null) {
                    example.setInputs(objectMapper.writeValueAsString(ex.getInputs()));
                }
                problemExampleMapper.insert(example);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse examples JSON for problem {}", problemId, e);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid examples JSON format");
        }
    }

    private void rebuildTags(Long problemId, List<String> tagLabels) {
        List<ProblemTag> existingTags = new ArrayList<>();
        for (String label : tagLabels) {
            LambdaQueryWrapper<ProblemTag> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.eq(ProblemTag::getLabel, label);
            ProblemTag tag = problemTagMapper.selectOne(tagWrapper);
            if (tag == null) {
                throw new BusinessException(ErrorCode.PROBLEM_TAG_NOT_FOUND, "Tag not found: " + label);
            }
            existingTags.add(tag);
        }

        LambdaQueryWrapper<ProblemTagRelation> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(ProblemTagRelation::getProblemId, problemId);
        problemTagRelationMapper.delete(relationWrapper);

        for (ProblemTag tag : existingTags) {
            ProblemTagRelation relation = new ProblemTagRelation();
            relation.setProblemId(problemId);
            relation.setTagId(tag.getId());
            problemTagRelationMapper.insert(relation);
        }
    }
}
