package com.ulticode.modules.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemDetailPublicVO;
import com.ulticode.modules.problem.dto.ProblemVO;
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
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.service.ProblemService;
import com.ulticode.modules.problem.service.ProblemVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * State-machine implementation of {@link ProblemService}.
 *
 * <p>This service owns the problem write surface: create / update / publish /
 * unpublish / delete, the premium-access guard on the read entry points
 * ({@code getProblemById} / {@code getProblemBySlug}), and the cross-module
 * entity lookups ({@code findById} / {@code findBySlug}) used by auth,
 * submission, contest, forum and other modules.
 *
 * <p>All entity-to-VO projection and read-side aggregation (list, detail,
 * adjacent, random, {@code toVO}) lives in {@link ProblemProjection} — see
 * that interface for why the seam exists. {@code toVO(Problem)} is kept on
 * this interface as a thin facade so the four cross-module callers
 * ({@code AdminProblemServiceImpl}, {@code AuthController},
 * {@code AuthSessionModule}, {@code SubmissionServiceImpl}) that already hold
 * a {@code ProblemService} reference need not be rewired.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;
    private final ProblemDetailMapper problemDetailMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final ProblemLanguageMapper problemLanguageMapper;
    private final ObjectMapper objectMapper;
    private final ProblemVersionService problemVersionService;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ProblemProjection problemProjection;

    @Override
    public Optional<Problem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(problemMapper.selectById(id));
    }

    @Override
    public Optional<Problem> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<Problem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Problem::getSlug, slug);
        return Optional.ofNullable(problemMapper.selectOne(queryWrapper));
    }

    @Override
    @Cacheable(value = "problem", key = "'getProblemById:' + #id")
    public ProblemVO getProblemById(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Check if problem is locked (premium and user doesn't have access)
        if (Boolean.TRUE.equals(problem.getIsPremium())) {
            if (!SecurityUtil.hasRole("ADMIN") && !SecurityUtil.hasRole("SUPER_ADMIN")) {
                // Return limited info for premium problems without access
                throw new BusinessException(ErrorCode.PROBLEM_PREMIUM_REQUIRED);
            }
        }

        return toVO(problem);
    }

    @Override
    public ProblemVO getProblemBySlug(String slug) {
        Problem problem = findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Check if problem is locked (premium and user doesn't have access)
        if (Boolean.TRUE.equals(problem.getIsPremium())) {
            if (!SecurityUtil.hasRole("ADMIN") && !SecurityUtil.hasRole("SUPER_ADMIN")) {
                throw new BusinessException(ErrorCode.PROBLEM_PREMIUM_REQUIRED);
            }
        }

        return toVO(problem);
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public ProblemVO createProblem(CreateProblemDTO createDTO) {
        // Check if slug already exists
        Optional<Problem> existingProblem = findBySlug(createDTO.getSlug());
        if (existingProblem.isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Problem with this slug already exists");
        }

        Problem problem = new Problem();
        problem.setSlug(createDTO.getSlug());
        problem.setTitle(createDTO.getTitle());
        problem.setDifficulty(createDTO.getDifficulty());
        problem.setIsPremium(createDTO.getIsPremium() != null ? createDTO.getIsPremium() : false);
        problem.setIsPublished(createDTO.getIsPublished() != null ? createDTO.getIsPublished() : true);
        problem.setStatus("todo");
        problem.setHasSolution(false);
        problem.setAcceptanceRate(BigDecimal.ZERO);
        problem.setIsFlagged(false);
        problem.setIsDeleted(false);
        problem.setVersion(1);

        // Set published info if publishing
        if (Boolean.TRUE.equals(problem.getIsPublished())) {
            problem.setPublishedAt(LocalDateTime.now());
            problem.setPublishedBy(SecurityUtil.getCurrentUserId());
        }

        problemMapper.insert(problem);

        String operatorId = SecurityUtil.getCurrentUserId();
        problemVersionService.createInitialVersion(problem.getId(), operatorId);

        log.info("Problem created: {} by user {}", problem.getId(), operatorId);
        return toVO(problem);
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public ProblemVO updateProblem(Long id, UpdateProblemDTO updateDTO) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Update fields from DTO (only non-null fields)
        if (updateDTO.getSlug() != null && !updateDTO.getSlug().equals(problem.getSlug())) {
            // Check if new slug already exists
            Optional<Problem> existingProblem = findBySlug(updateDTO.getSlug());
            if (existingProblem.isPresent() && !existingProblem.get().getId().equals(id)) {
                throw new BusinessException(ErrorCode.CONFLICT, "Problem with this slug already exists");
            }
            problem.setSlug(updateDTO.getSlug());
        }
        if (updateDTO.getTitle() != null) {
            problem.setTitle(updateDTO.getTitle());
        }
        if (updateDTO.getDifficulty() != null) {
            problem.setDifficulty(updateDTO.getDifficulty());
        }
        if (updateDTO.getIsPremium() != null) {
            problem.setIsPremium(updateDTO.getIsPremium());
        }
        if (updateDTO.getIsPublished() != null) {
            problem.setIsPublished(updateDTO.getIsPublished());
            // Set published info if publishing for the first time
            if (Boolean.TRUE.equals(updateDTO.getIsPublished()) && problem.getPublishedAt() == null) {
                problem.setPublishedAt(LocalDateTime.now());
                problem.setPublishedBy(SecurityUtil.getCurrentUserId());
            }
        }
        if (updateDTO.getHasSolution() != null) {
            problem.setHasSolution(updateDTO.getHasSolution());
        }

        problemMapper.updateById(problem);

        updateProblemDetail(id, problem, updateDTO);

        String operatorId = SecurityUtil.getCurrentUserId();
        problemVersionService.createVersion(id, "UPDATE", null, operatorId);

        log.info("Problem updated: {} by user {}", id, operatorId);
        return toVO(problem);
    }

    private void updateProblemDetail(Long problemId, Problem problem, UpdateProblemDTO updateDTO) {
        boolean hasDetailUpdate = updateDTO.getSummary() != null || updateDTO.getContent() != null
                || updateDTO.getConstraintsJson() != null || updateDTO.getHints() != null;
        if (!hasDetailUpdate && updateDTO.getLanguages() == null
                && updateDTO.getTags() == null
                && (updateDTO.getExamples() == null || updateDTO.getExamples().isBlank())) {
            return;
        }

        if (hasDetailUpdate) {
            LambdaQueryWrapper<ProblemDetail> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProblemDetail::getProblemId, problemId);
            ProblemDetail detail = problemDetailMapper.selectOne(wrapper);

            boolean isNew = false;
            if (detail == null) {
                detail = new ProblemDetail();
                detail.setProblemId(problemId);
                detail.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
                // problem_details.slug NOT NULL — denormalize from Problem on insert
                // Defensive null check: problems.slug is also NOT NULL in DB, so this
                // should never be null, but assert it to fail fast with a clear message.
                java.util.Objects.requireNonNull(problem.getSlug(),
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

        if (updateDTO.getLanguages() != null) {
            updateProblemLanguages(problemId, updateDTO.getLanguages());
        }

        if (updateDTO.getExamples() != null && !updateDTO.getExamples().isBlank()) {
            try {
                List<ProblemDetailPublicVO.ExampleData> examples = objectMapper.readValue(
                        updateDTO.getExamples(),
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

        if (updateDTO.getTags() != null) {
            List<String> tagLabels = updateDTO.getTags();
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

    private void updateProblemLanguages(Long problemId, List<com.ulticode.modules.problem.dto.LanguageConfigDTO> languages) {
        if (languages == null || languages.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<ProblemLanguage> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ProblemLanguage::getProblemId, problemId);
        problemLanguageMapper.delete(deleteWrapper);

        for (com.ulticode.modules.problem.dto.LanguageConfigDTO config : languages) {
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

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public void deleteProblem(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Soft delete is handled by MyBatis-Plus @TableLogic
        problemMapper.deleteById(id);

        log.info("Problem deleted: {} by user {}", id, SecurityUtil.getCurrentUserId());
    }

    @Override
    @Transactional
    public ProblemVO publishProblem(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        problem.setIsPublished(true);
        if (problem.getPublishedAt() == null) {
            problem.setPublishedAt(LocalDateTime.now());
            problem.setPublishedBy(SecurityUtil.getCurrentUserId());
        }

        problemMapper.updateById(problem);

        log.info("Problem published: {} by user {}", id, SecurityUtil.getCurrentUserId());
        return toVO(problem);
    }

    @Override
    @Transactional
    public ProblemVO unpublishProblem(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        problem.setIsPublished(false);

        problemMapper.updateById(problem);

        log.info("Problem unpublished: {} by user {}", id, SecurityUtil.getCurrentUserId());
        return toVO(problem);
    }

    /**
     * Convert a {@code Problem} entity to a {@code ProblemVO}. Thin facade over
     * {@link ProblemProjection#toVO(Problem)} — kept on this interface because
     * four cross-module callers ({@code AdminProblemServiceImpl},
     * {@code AuthController}, {@code AuthSessionModule},
     * {@code SubmissionServiceImpl}) already hold a {@code ProblemService}
     * reference. The state-change methods below also call it to shape their
     * return value.
     */
    @Override
    public ProblemVO toVO(Problem problem) {
        return problemProjection.toVO(problem);
    }
}
