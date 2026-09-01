package com.ulticode.modules.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.app.error.ProblemErrorCode;
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
import com.ulticode.modules.problem.service.ProblemSnapshotService;
import com.ulticode.modules.problem.vo.ProblemVersionDetailVO;
import com.ulticode.modules.problem.vo.VersionDiffVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deep capture/restore module for the Problem-version snapshot schema.
 *
 * <p>Absorbs the prior four-piece split ({@code buildSnapshot} inside
 * {@code ProblemVersionServiceImpl}, {@code ProblemSnapshotCodec},
 * {@code ProblemVersionDiff}, {@code ProblemVersionRollback}) so the fragile
 * snapshot shape &mdash; the {@code title}/{@code slug}/{@code examples}/
 * {@code inputs}/{@code tags} keys and their entity-to-Map mappings &mdash;
 * becomes one module's internal concern. Callers see only
 * {@link #capture}, {@link #populateDetail}, {@link #diff}, and {@link #restore}.
 *
 * <p><strong>Behavior is preserved exactly</strong>: the JSON key set, the
 * {@code input}/{@code inputText} fallback during restore, the null/blank
 * handling in {@code deserialize}, and the JSON-normalized value comparison in
 * the diff are all carried over verbatim.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemSnapshotServiceImpl implements ProblemSnapshotService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<>() {};

    private final ProblemMapper problemMapper;
    private final ProblemDetailMapper problemDetailMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final ProblemLanguageMapper problemLanguageMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final UuidGenerator uuidGenerator;
    private final ObjectMapper objectMapper;

    @Override
    public String capture(Long problemId) {
        return serialize(buildSnapshot(problemId));
    }

    @Override
    public void populateDetail(ProblemVersionDetailVO detail, String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return;
        }
        Map<String, Object> snapshot = deserialize(snapshotJson);
        detail.setTitle((String) snapshot.get("title"));
        detail.setSlug((String) snapshot.get("slug"));
        detail.setDifficulty((String) snapshot.get("difficulty"));
        detail.setIsPremium((Boolean) snapshot.get("isPremium"));
        detail.setIsPublished((Boolean) snapshot.get("isPublished"));
        detail.setSummary((String) snapshot.get("summary"));
        detail.setContent((String) snapshot.get("content"));
        @SuppressWarnings("unchecked")
        List<String> constraints = (List<String>) snapshot.get("constraints");
        detail.setConstraints(constraints);
        @SuppressWarnings("unchecked")
        List<String> hints = (List<String>) snapshot.get("hints");
        detail.setHints(hints);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> examples = (List<Map<String, Object>>) snapshot.get("examples");
        detail.setExamples(examples);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> languages = (List<Map<String, Object>>) snapshot.get("languages");
        detail.setLanguages(languages);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) snapshot.get("tags");
        detail.setTags(tags);
    }

    @Override
    public List<VersionDiffVO> diff(String fromJson, String toJson) {
        Map<String, Object> from = parseOrThrow(fromJson, "from");
        Map<String, Object> to = parseOrThrow(toJson, "to");

        List<VersionDiffVO> diffs = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(from.keySet());
        allKeys.addAll(to.keySet());

        for (String key : allKeys) {
            Object oldValue = from.get(key);
            Object newValue = to.get(key);
            if (!isValueEqual(oldValue, newValue)) {
                VersionDiffVO diff = new VersionDiffVO();
                diff.setField(key);
                diff.setOldValue(oldValue);
                diff.setNewValue(newValue);
                diffs.add(diff);
            }
        }
        return diffs;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restore(Long problemId, String snapshotJson) {
        Map<String, Object> snapshot = deserialize(snapshotJson);
        restoreProblem(problemId, snapshot);
        restoreDetail(problemId, snapshot);
        restoreExamples(problemId, snapshot);
        restoreLanguages(problemId, snapshot);
        restoreTags(problemId, snapshot);
    }

    // ---- capture ----

    private Map<String, Object> buildSnapshot(Long problemId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();

        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ProblemErrorCode.PROBLEM_NOT_FOUND);
        }

        snapshot.put("title", problem.getTitle());
        snapshot.put("slug", problem.getSlug());
        snapshot.put("difficulty", problem.getDifficulty());
        snapshot.put("isPremium", problem.getIsPremium());
        snapshot.put("isPublished", problem.getIsPublished());

        ProblemDetail detail = problemDetailMapper.selectOne(
                new LambdaQueryWrapper<ProblemDetail>().eq(ProblemDetail::getProblemId, problemId));
        if (detail != null) {
            snapshot.put("summary", detail.getSummary());
            snapshot.put("followUp", detail.getFollowUp());
            snapshot.put("constraints", parseJsonArray(detail.getConstraintsJson()));
            snapshot.put("hints", parseJsonArray(detail.getHints()));
        }

        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(problemId);
        List<Map<String, Object>> exampleSnapshots = new ArrayList<>();
        for (ProblemExample ex : examples) {
            Map<String, Object> exMap = new LinkedHashMap<>();
            exMap.put("input", ex.getInputText());
            exMap.put("output", ex.getOutputText());
            exMap.put("explanation", ex.getExplanation());
            // HIDDEN test-case payload (ProblemExample.inputs) intentionally
            // excluded from the snapshot whitelist: only input/output/explanation
            // (the user-visible example text) belong in the version rollback.
            exampleSnapshots.add(exMap);
        }
        snapshot.put("examples", exampleSnapshots);

        List<ProblemLanguage> languages = problemLanguageMapper.findByProblemId(problemId);
        List<Map<String, Object>> languageSnapshots = new ArrayList<>();
        for (ProblemLanguage lang : languages) {
            Map<String, Object> langMap = new LinkedHashMap<>();
            langMap.put("label", lang.getLabel());
            langMap.put("value", lang.getValue());
            langMap.put("style", lang.getStyle());
            langMap.put("starterCode", lang.getStarterCode());
            languageSnapshots.add(langMap);
        }
        snapshot.put("languages", languageSnapshots);

        List<String> tagLabels = getTagLabels(problemId);
        snapshot.put("tags", tagLabels);

        return snapshot;
    }

    private List<String> getTagLabels(Long problemId) {
        List<ProblemMapper.ProblemTagDTO> tagDTOs = problemMapper.selectTagsByProblemIds(List.of(problemId));
        return tagDTOs.stream()
                .map(ProblemMapper.ProblemTagDTO::tagName)
                .collect(Collectors.toList());
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, LIST_STRING_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array: {}", json, e);
            return Collections.emptyList();
        }
    }

    // ---- serialize / deserialize (schema interpretation) ----

    private String serialize(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize snapshot", e);
            throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Failed to serialize problem snapshot");
        }
    }

    private Map<String, Object> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse snapshot JSON", e);
            throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Failed to parse version snapshot");
        }
    }

    private Map<String, Object> parseOrThrow(String json, String label) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse {} snapshot JSON for diff", label, e);
            throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Failed to parse version snapshots");
        }
    }

    private boolean isValueEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        try {
            return objectMapper.writeValueAsString(a).equals(objectMapper.writeValueAsString(b));
        } catch (JsonProcessingException e) {
            return a.equals(b);
        }
    }

    // ---- restore (multi-table coordination) ----

    private void restoreProblem(Long problemId, Map<String, Object> snapshot) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ProblemErrorCode.PROBLEM_NOT_FOUND);
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
            Objects.requireNonNull(problem, "Problem must exist to seed ProblemDetail")
                    .getSlug();
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
            // HIDDEN test-case payload intentionally not restored: the snapshot
            // whitelist excludes `inputs` so a rollback can never reintroduce it.
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
