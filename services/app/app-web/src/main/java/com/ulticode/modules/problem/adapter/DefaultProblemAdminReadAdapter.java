package com.ulticode.modules.problem.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.dto.ProblemAdminCasesDTO;
import com.ulticode.common.response.PageResult;
import com.ulticode.app.api.dto.ProblemAdminCodeDTO;
import com.ulticode.app.api.dto.ProblemAdminDescriptionDTO;
import com.ulticode.app.api.dto.ProblemAdminExampleDTO;
import com.ulticode.app.api.dto.ProblemAdminLanguageDTO;
import com.ulticode.app.api.dto.ProblemAdminQueryDTO;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.dto.ProblemAdminTagDTO;
import com.ulticode.app.api.dto.ProblemAdminTestCaseDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemDetail;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.problem.projection.ProblemProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (owner-side) adapter for {@link ProblemAdminReadPort}.
 *
 * <p>Lives in the App problem module so the Admin BFF never imports the
 * problem entities/mappers/projection directly. Every query executes inside
 * App; composed tab payloads and batch reads keep the Admin edge at one
 * bounded RPC per request.
 *
 * <p>Wire compatibility notes:
 * <ul>
 *   <li>row conversion mirrors {@code ProblemVO.from(entity)} — difficulty
 *       is uppercased, tags empty and counts zeroed for single-row reads;</li>
 *   <li>{@link #findProblemsByIds} keeps the raw (un-uppercased) difficulty
 *       because the problem-list Admin projection historically read the
 *       entity column verbatim;</li>
 *   <li>detail JSON lists (constraints/hints) are parsed tolerantly exactly
 *       like the legacy Admin mapper: blank or unparseable input yields
 *       {@code null}.</li>
 * </ul>
 */
@Component
@Primary
@RequiredArgsConstructor
public class DefaultProblemAdminReadAdapter implements ProblemAdminReadPort {
    private static final int MAX_TEST_CASE_BATCH_SIZE = 1000;

    private final ProblemMapper problemMapper;
    private final ProblemDetailMapper problemDetailMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final ProblemLanguageMapper problemLanguageMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final TestCaseMapper testCaseMapper;
    private final ProblemProjection problemProjection;

    // ── Problem rows ────────────────────────────────────────────

    @Override
    public ProblemAdminRowDTO findProblem(Long id) {
        if (id == null) {
            return null;
        }
        Problem problem = problemMapper.selectById(id);
        return problem == null ? null : toRow(problem);
    }

    @Override
    public ProblemAdminRowDTO findBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        Problem problem = problemMapper.selectOne(
                new LambdaQueryWrapper<Problem>().eq(Problem::getSlug, slug));
        return problem == null ? null : toRow(problem);
    }

    @Override
    public List<ProblemAdminRowDTO> findBySlugs(Collection<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return Collections.emptyList();
        }
        if (slugs.size() > ProblemOwnerPort.MAX_IMPORT_SIZE) {
            throw new IllegalArgumentException("Too many problem slugs");
        }
        List<String> validSlugs = slugs.stream()
                .filter(StringUtils::hasText)
                .toList();
        if (validSlugs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Problem> problems = problemMapper.selectList(
                new LambdaQueryWrapper<Problem>().in(Problem::getSlug, validSlugs));
        if (problems == null || problems.isEmpty()) {
            return Collections.emptyList();
        }
        return problems.stream().map(this::toRow).collect(Collectors.toList());
    }

    @Override
    public List<ProblemAdminRowDTO> findProblemsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Problem> problems = problemMapper.selectBatchIds(ids);
        if (problems == null || problems.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<ProblemAdminTagDTO>> tagMap = batchTagsByProblemId(
                problems.stream().map(Problem::getId).collect(Collectors.toList()));
        return problems.stream()
                .map(p -> toRawRow(p, tagMap.getOrDefault(p.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    // ── Admin tab payloads ──────────────────────────────────────

    @Override
    public ProblemAdminDescriptionDTO findDescription(Long problemId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            return null;
        }
        ProblemDetail detail = findDetailByProblemId(problemId);
        return new ProblemAdminDescriptionDTO(
                toRow(problem),
                detail == null ? null : detail.getSummary(),
                detail == null ? null : detail.getContent(),
                detail == null ? null : parseJsonList(detail.getConstraintsJson()),
                detail == null ? null : parseJsonList(detail.getHints()),
                findTagsByProblemId(problemId),
                toExampleDtos(problemExampleMapper.findByProblemIdOrderByOrder(problemId)));
    }

    @Override
    public ProblemAdminCodeDTO findCode(Long problemId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            return null;
        }
        List<ProblemLanguage> languages = problemLanguageMapper.findByProblemId(problemId);
        List<ProblemAdminLanguageDTO> languageDtos = languages == null
                ? Collections.emptyList()
                : languages.stream().map(l -> new ProblemAdminLanguageDTO(
                        l.getId(), l.getLabel(), l.getValue(), l.getStyle(), l.getStarterCode()))
                        .collect(Collectors.toList());
        return new ProblemAdminCodeDTO(toRow(problem), languageDtos);
    }

    @Override
    public ProblemAdminCasesDTO findCases(Long problemId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            return null;
        }
        ProblemDetail detail = findDetailByProblemId(problemId);
        return new ProblemAdminCasesDTO(
                toRow(problem),
                toExampleDtos(problemExampleMapper.findByProblemIdOrderByOrder(problemId)),
                detail == null ? null : parseJsonList(detail.getConstraintsJson()),
                detail == null ? null : parseJsonList(detail.getHints()),
                findTagsByProblemId(problemId));
    }

    // ── List / export / moderation reads ────────────────────────

    @Override
    public PageResult<ProblemAdminRowDTO> listProblems(ProblemAdminQueryDTO query) {
        PageResult<ProblemVO> page = problemProjection.listProblems(toQuery(query));
        List<ProblemAdminRowDTO> rows = page.getItems() == null
                ? Collections.emptyList()
                : page.getItems().stream().map(this::toRow).collect(Collectors.toList());
        return PageResult.of(rows, page.getTotal(), page.getPage(), page.getPageSize());
    }

    @Override
    public List<ProblemAdminRowDTO> listAllProblems(ProblemAdminQueryDTO query) {
        List<ProblemVO> problems = problemProjection.listAllProblems(toQuery(query));
        if (problems == null || problems.isEmpty()) {
            return Collections.emptyList();
        }
        return problems.stream().map(this::toRow).collect(Collectors.toList());
    }

    @Override
    public PageResult<ProblemAdminRowDTO> listFlaggedProblems(String status, int page, int limit) {
        int offset = (page - 1) * limit;
        List<Problem> problems = problemMapper.selectFlaggedProblems(status, limit, offset);
        long total = problemMapper.countFlaggedProblems(status);
        List<ProblemAdminRowDTO> rows = problems == null
                ? Collections.emptyList()
                : problems.stream().map(this::toRow).collect(Collectors.toList());
        return PageResult.of(rows, total, page, limit);
    }

    @Override
    public List<Long> searchProblemIdsByTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return Collections.emptyList();
        }
        return problemMapper.selectList(
                        new LambdaQueryWrapper<Problem>().like(Problem::getTitle, title))
                .stream().map(Problem::getId).collect(Collectors.toList());
    }

    // ── Test cases ──────────────────────────────────────────────

    @Override
    public PageResult<ProblemAdminTestCaseDTO> listTestCases(
            Long problemId, Boolean isSample, Boolean isHidden, int page, int limit) {
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getProblemId, problemId);
        if (isSample != null) {
            wrapper.eq(TestCase::getIsSample, isSample);
        }
        if (isHidden != null) {
            wrapper.eq(TestCase::getIsHidden, isHidden);
        }
        wrapper.orderByAsc(TestCase::getTestOrder);
        Page<TestCase> result = testCaseMapper.selectPage(new Page<>(page, limit), wrapper);
        List<ProblemAdminTestCaseDTO> rows = result.getRecords() == null
                ? Collections.emptyList()
                : result.getRecords().stream().map(this::toTestCaseDto).collect(Collectors.toList());
        return PageResult.of(rows, result.getTotal(), page, limit);
    }

    @Override
    public ProblemAdminTestCaseDTO getTestCase(Long problemId, String testCaseId) {
        TestCase testCase = testCaseMapper.selectById(testCaseId);
        if (testCase == null || !testCase.getProblemId().equals(problemId)) {
            return null;
        }
        return toTestCaseDto(testCase);
    }

    @Override
    public List<ProblemAdminTestCaseDTO> findTestCasesByIds(
            Long problemId, Collection<String> testCaseIds) {
        if (problemId == null || testCaseIds == null || testCaseIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (testCaseIds.size() > MAX_TEST_CASE_BATCH_SIZE) {
            throw new IllegalArgumentException("Too many test case IDs");
        }
        List<TestCase> testCases = testCaseMapper.selectList(
                new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProblemId, problemId)
                        .in(TestCase::getId, testCaseIds));
        if (testCases == null || testCases.isEmpty()) {
            return Collections.emptyList();
        }
        return testCases.stream()
                .filter(testCase -> problemId.equals(testCase.getProblemId()))
                .map(this::toTestCaseDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProblemAdminTestCaseDTO> exportTestCases(Long problemId) {
        List<TestCase> cases = testCaseMapper.selectList(
                new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProblemId, problemId)
                        .orderByAsc(TestCase::getTestOrder));
        if (cases == null || cases.isEmpty()) {
            return Collections.emptyList();
        }
        return cases.stream().map(this::toTestCaseDto).collect(Collectors.toList());
    }

    // ── Tags ────────────────────────────────────────────────────

    @Override
    public PageResult<ProblemAdminTagDTO> listTags(
            String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(search)) {
            wrapper.like(ProblemTag::getLabel, search).or().like(ProblemTag::getSlug, search);
        }
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        if ("usageCount".equalsIgnoreCase(sortBy) || "usage_count".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, ProblemTag::getUsageCount);
        } else if ("createdAt".equalsIgnoreCase(sortBy) || "created_at".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, ProblemTag::getCreatedAt);
        } else if ("slug".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, isAsc, ProblemTag::getSlug);
        } else {
            wrapper.orderBy(true, isAsc, ProblemTag::getLabel);
        }
        Page<ProblemTag> result = problemTagMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<ProblemAdminTagDTO> rows = result.getRecords() == null
                ? Collections.emptyList()
                : result.getRecords().stream().map(this::toTagDto).collect(Collectors.toList());
        return PageResult.of(rows, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public ProblemAdminTagDTO getTagById(String id) {
        ProblemTag tag = problemTagMapper.selectById(id);
        return tag == null ? null : toTagDto(tag);
    }

    @Override
    public boolean tagNameExists(String name) {
        return problemTagMapper.selectCount(
                new LambdaQueryWrapper<ProblemTag>().eq(ProblemTag::getLabel, name)) > 0;
    }

    @Override
    public boolean tagSlugExists(String slug) {
        return problemTagMapper.selectCount(
                new LambdaQueryWrapper<ProblemTag>().eq(ProblemTag::getSlug, slug)) > 0;
    }

    // ── helpers ─────────────────────────────────────────────────

    private ProblemDetail findDetailByProblemId(Long problemId) {
        return problemDetailMapper.selectOne(
                new LambdaQueryWrapper<ProblemDetail>().eq(ProblemDetail::getProblemId, problemId));
    }

    private List<ProblemAdminTagDTO> findTagsByProblemId(Long problemId) {
        List<String> tagIds = problemTagRelationMapper.findTagIdsByProblemId(problemId);
        if (tagIds == null || tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProblemTag> tags = problemTagMapper.selectBatchIds(tagIds);
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return tags.stream().map(this::toTagDto).collect(Collectors.toList());
    }

    private Map<Long, List<ProblemAdminTagDTO>> batchTagsByProblemId(List<Long> problemIds) {
        List<ProblemMapper.ProblemTagDTO> rows = problemMapper.selectTagsByProblemIds(problemIds);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<ProblemAdminTagDTO>> tagMap = new HashMap<>();
        for (ProblemMapper.ProblemTagDTO row : rows) {
            tagMap.computeIfAbsent(row.problemId(), k -> new ArrayList<>())
                    .add(new ProblemAdminTagDTO(row.tagId(), row.tagName(), null, null, null, null, null, null));
        }
        return tagMap;
    }

    private static List<ProblemAdminExampleDTO> toExampleDtos(List<ProblemExample> examples) {
        if (examples == null || examples.isEmpty()) {
            return Collections.emptyList();
        }
        return examples.stream()
                .map(e -> new ProblemAdminExampleDTO(
                        e.getId(), e.getExampleOrder(), e.getInputText(), e.getOutputText(),
                        e.getExplanation(), e.getInputs()))
                .collect(Collectors.toList());
    }

    private ProblemAdminTestCaseDTO toTestCaseDto(TestCase tc) {
        return new ProblemAdminTestCaseDTO(
                tc.getId(), tc.getProblemId(), tc.getIsSample(), tc.getIsHidden(),
                tc.getTestOrder(), tc.getInputText(), tc.getOutputText(), tc.getInputs(),
                tc.getExplanation(), tc.getConstraints(), tc.getCreatedAt(), tc.getUpdatedAt(),
                tc.getIsDeleted(), tc.getDeletedAt());
    }

    private ProblemAdminTagDTO toTagDto(ProblemTag tag) {
        return new ProblemAdminTagDTO(
                tag.getId(), tag.getLabel(), tag.getSlug(), tag.getDescription(), tag.getColor(),
                tag.getUsageCount(), tag.getCreatedAt(), tag.getUpdatedAt());
    }

    /**
     * Row conversion mirroring {@code ProblemVO.from(entity)}: difficulty is
     * uppercased, tags empty and counts zeroed.
     */
    private ProblemAdminRowDTO toRow(Problem p) {
        return new ProblemAdminRowDTO(
                p.getId(), p.getSlug(), p.getTitle(),
                p.getDifficulty() != null ? p.getDifficulty().toUpperCase() : null,
                p.getAcceptanceRate(), p.getStatus(), p.getIsPremium(), p.getHasSolution(),
                p.getIsPublished(), p.getPublishedAt(), p.getPublishedBy(),
                p.getIsDeleted(), p.getDeletedAt(),
                p.getIsFlagged(), p.getFlagReason(), p.getFlagReportedBy(), p.getFlagReportedAt(),
                p.getFlagStatus(), p.getFlagReviewedBy(), p.getFlagReviewedAt(), p.getFlagNotes(),
                0L, 0L, Collections.emptyList(), p.getCreatedAt(), p.getUpdatedAt(),
                p.getVersion() == null ? null : p.getVersion().longValue());
    }

    /**
     * Row conversion from the projection's list VO (tags + real counts).
     */
    private ProblemAdminRowDTO toRow(ProblemVO vo) {
        List<ProblemAdminTagDTO> tags = vo.getTags() == null
                ? Collections.emptyList()
                : vo.getTags().stream()
                        .map(t -> new ProblemAdminTagDTO(t.getId(), t.getLabel(), null, null, null, null, null, null))
                        .collect(Collectors.toList());
        return new ProblemAdminRowDTO(
                vo.getId(), vo.getSlug(), vo.getTitle(), vo.getDifficulty(), vo.getAcceptanceRate(),
                vo.getStatus(), vo.getIsPremium(), vo.getHasSolution(),
                vo.getIsPublished(), vo.getPublishedAt(), vo.getPublishedBy(),
                vo.getIsDeleted(), vo.getDeletedAt(),
                vo.getIsFlagged(), vo.getFlagReason(), vo.getFlagReportedBy(), vo.getFlagReportedAt(),
                vo.getFlagStatus(), vo.getFlagReviewedBy(), vo.getFlagReviewedAt(), vo.getFlagNotes(),
                vo.getSubmissionCount(), vo.getSolutionCount(), tags, vo.getCreatedAt(), vo.getUpdatedAt());
    }

    /**
     * Raw row conversion for the problem-list Admin projection: difficulty is
     * kept verbatim (the legacy list-detail mapping read the entity column
     * without normalisation).
     */
    private ProblemAdminRowDTO toRawRow(Problem p, List<ProblemAdminTagDTO> tags) {
        return new ProblemAdminRowDTO(
                p.getId(), p.getSlug(), p.getTitle(), p.getDifficulty(), p.getAcceptanceRate(),
                p.getStatus(), p.getIsPremium(), p.getHasSolution(),
                p.getIsPublished(), p.getPublishedAt(), p.getPublishedBy(),
                p.getIsDeleted(), p.getDeletedAt(),
                p.getIsFlagged(), p.getFlagReason(), p.getFlagReportedBy(), p.getFlagReportedAt(),
                p.getFlagStatus(), p.getFlagReviewedBy(), p.getFlagReviewedAt(), p.getFlagNotes(),
                0L, 0L, tags, p.getCreatedAt(), p.getUpdatedAt(),
                p.getVersion() == null ? null : p.getVersion().longValue());
    }

    private static ProblemQueryDTO toQuery(ProblemAdminQueryDTO q) {
        ProblemQueryDTO query = new ProblemQueryDTO();
        query.setPage(q.getPage());
        query.setPageSize(q.getPageSize());
        query.setLimit(q.getLimit());
        query.setDifficulty(q.getDifficulty());
        query.setStatus(q.getStatus());
        query.setSearch(q.getSearch());
        query.setSortBy(q.getSortBy());
        query.setSortOrder(q.getSortOrder());
        query.setIsPublished(q.getIsPublished());
        query.setIsDeleted(q.getIsDeleted());
        query.setTag(q.getTag());
        query.setPublishStatus(q.getPublishStatus());
        query.setCategory(q.getCategory());
        query.setIsPremium(q.getIsPremium());
        return query;
    }

    private static List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<String> list = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            return list == null ? null : List.copyOf(list);
        } catch (Exception e) {
            return null;
        }
    }
}
