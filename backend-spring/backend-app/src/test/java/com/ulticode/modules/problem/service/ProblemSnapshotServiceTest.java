package com.ulticode.modules.problem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.uuid.FixedAppUuidGenerator;
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
import com.ulticode.modules.problem.service.impl.ProblemSnapshotServiceImpl;
import com.ulticode.modules.problem.vo.ProblemVersionDetailVO;
import com.ulticode.modules.problem.vo.VersionDiffVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused test surface for the deep Problem snapshot module — covers the
 * capture/serialize round-trip, schema interpretation (populateDetail), diff,
 * and restore, all on a mock-mapper graph. Replaces the prior split
 * ProblemVersionCodecTest (codec + diff) and folds in the restore coverage that
 * previously lived only indirectly inside ProblemVersionServiceTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemSnapshotService (deep capture/restore module)")
class ProblemSnapshotServiceTest {

    private static final Long PROBLEM_ID = 1L;

    @Mock
    private ProblemMapper problemMapper;

    @Mock
    private ProblemDetailMapper problemDetailMapper;

    @Mock
    private ProblemExampleMapper problemExampleMapper;

    @Mock
    private ProblemLanguageMapper problemLanguageMapper;

    @Mock
    private ProblemTagMapper problemTagMapper;

    @Mock
    private ProblemTagRelationMapper problemTagRelationMapper;

    private ObjectMapper objectMapper;
    private ProblemSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        snapshotService = new ProblemSnapshotServiceImpl(
                problemMapper,
                problemDetailMapper,
                problemExampleMapper,
                problemLanguageMapper,
                problemTagMapper,
                problemTagRelationMapper,
                new FixedAppUuidGenerator(),
                objectMapper);
    }

    private Problem createProblem() {
        Problem problem = new Problem();
        problem.setId(PROBLEM_ID);
        problem.setTitle("Two Sum");
        problem.setSlug("two-sum");
        problem.setDifficulty("Easy");
        problem.setIsPremium(false);
        problem.setIsPublished(true);
        return problem;
    }

    private ProblemDetail createProblemDetail() {
        ProblemDetail detail = new ProblemDetail();
        detail.setId("detail-1");
        detail.setProblemId(PROBLEM_ID);
        detail.setSlug("two-sum");
        detail.setSummary("Find two numbers that add up to target");
        detail.setFollowUp("Can you do it in O(n) time?");
        detail.setConstraintsJson("[\"2 <= nums.length <= 10^4\"]");
        detail.setHints("[\"Use a hash map\"]");
        return detail;
    }

    private ProblemExample createProblemExample(int order) {
        ProblemExample example = new ProblemExample();
        example.setId("ex-" + order);
        example.setProblemId(PROBLEM_ID);
        example.setExampleOrder(order);
        example.setInputText("nums = [2,7,11,15], target = 9");
        example.setOutputText("[0,1]");
        example.setExplanation("Because nums[0] + nums[1] == 9, we return [0, 1].");
        example.setInputs(null);
        return example;
    }

    private ProblemLanguage createProblemLanguage() {
        ProblemLanguage lang = new ProblemLanguage();
        lang.setId("lang-1");
        lang.setProblemId(PROBLEM_ID);
        lang.setLabel("Python");
        lang.setValue("python");
        lang.setStyle("python");
        lang.setStarterCode("class Solution:\n    def twoSum(self, nums, target):\n        pass");
        return lang;
    }

    private ProblemTag createProblemTag() {
        ProblemTag tag = new ProblemTag();
        tag.setId("tag-1");
        tag.setLabel("Array");
        tag.setSlug("array");
        return tag;
    }

    @SuppressWarnings("unchecked")
    private void mockCaptureGraph() {
        when(problemMapper.selectById(PROBLEM_ID)).thenReturn(createProblem());
        when(problemDetailMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(createProblemDetail());
        when(problemExampleMapper.findByProblemIdOrderByOrder(PROBLEM_ID))
                .thenReturn(List.of(createProblemExample(1)));
        when(problemLanguageMapper.findByProblemId(PROBLEM_ID)).thenReturn(List.of(createProblemLanguage()));
        when(problemMapper.selectTagsByProblemIds(anyList())).thenReturn(
                List.of(new ProblemMapper.ProblemTagDTO(PROBLEM_ID, createProblemTag().getId(), "Array")));
    }

    @Nested
    @DisplayName("capture() — round-trip serialize/interpret")
    class CaptureTests {

        @Test
        @DisplayName("capture produces non-empty JSON and round-trips through populateDetail")
        void captureRoundTrip() throws Exception {
            mockCaptureGraph();

            String json = snapshotService.capture(PROBLEM_ID);

            assertThat(json).isNotBlank();
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            assertThat(parsed.get("title")).isEqualTo("Two Sum");
            assertThat(parsed.get("slug")).isEqualTo("two-sum");
            assertThat(parsed.get("difficulty")).isEqualTo("Easy");
            assertThat(parsed.get("isPremium")).isEqualTo(false);
            assertThat(parsed.get("isPublished")).isEqualTo(true);
            assertThat(parsed.get("summary")).isEqualTo("Find two numbers that add up to target");
            assertThat(parsed.get("constraints")).isEqualTo(List.of("2 <= nums.length <= 10^4"));
            assertThat(parsed.get("hints")).isEqualTo(List.of("Use a hash map"));
            assertThat(parsed.get("tags")).isEqualTo(List.of("Array"));
            assertThat((List<?>) parsed.get("examples")).hasSize(1);
            assertThat((List<?>) parsed.get("languages")).hasSize(1);

            ProblemVersionDetailVO detail = new ProblemVersionDetailVO();
            snapshotService.populateDetail(detail, json);
            assertThat(detail.getTitle()).isEqualTo("Two Sum");
            assertThat(detail.getSlug()).isEqualTo("two-sum");
            assertThat(detail.getDifficulty()).isEqualTo("Easy");
            assertThat(detail.getIsPremium()).isFalse();
            assertThat(detail.getIsPublished()).isTrue();
            assertThat(detail.getSummary()).isEqualTo("Find two numbers that add up to target");
            assertThat(detail.getConstraints()).containsExactly("2 <= nums.length <= 10^4");
            assertThat(detail.getHints()).containsExactly("Use a hash map");
            assertThat(detail.getTags()).containsExactly("Array");
            assertThat(detail.getExamples()).hasSize(1);
            assertThat(detail.getLanguages()).hasSize(1);
        }

        @Test
        @DisplayName("capture throws PROBLEM_NOT_FOUND when problem row is missing")
        void captureThrowsWhenProblemMissing() {
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> snapshotService.capture(PROBLEM_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("populateDetail is a no-op on null/blank JSON (detail fields stay null)")
        void populateDetailHandlesNullBlank() {
            ProblemVersionDetailVO detail = new ProblemVersionDetailVO();
            detail.setTitle("preset");

            snapshotService.populateDetail(detail, null);
            assertThat(detail.getTitle()).isEqualTo("preset");

            snapshotService.populateDetail(detail, "");
            assertThat(detail.getTitle()).isEqualTo("preset");
        }

        @Test
        @DisplayName("populateDetail throws on malformed JSON")
        void populateDetailThrowsOnMalformed() {
            assertThatThrownBy(() -> snapshotService.populateDetail(new ProblemVersionDetailVO(), "{broken"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("deserialize round-trip preserves all fields (legacy codec assertion)")
        void deserializeRoundTrip() throws Exception {
            // Legacy shape: serialize a hand-built map and assert deserialize (via
            // populateDetail) round-trips every field without touching mappers.
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("title", "Two Sum");
            snapshot.put("difficulty", "EASY");
            snapshot.put("tags", List.of("array", "hash-table"));
            snapshot.put("isPremium", false);
            String json = objectMapper.writeValueAsString(snapshot);

            ProblemVersionDetailVO detail = new ProblemVersionDetailVO();
            snapshotService.populateDetail(detail, json);

            assertThat(detail.getTitle()).isEqualTo("Two Sum");
            assertThat(detail.getDifficulty()).isEqualTo("EASY");
            assertThat(detail.getTags()).containsExactly("array", "hash-table");
            assertThat(detail.getIsPremium()).isFalse();
        }

        @Test
        @DisplayName("deserialize returns empty map for null/blank input (legacy codec assertion)")
        void deserializeEmptyOnBlank() {
            // Indirect: populateDetail on null/blank must NOT throw and must NOT overwrite
            ProblemVersionDetailVO detail = new ProblemVersionDetailVO();
            snapshotService.populateDetail(detail, null);
            snapshotService.populateDetail(detail, "");
            // title stays null → equivalent to "empty map produced no fields"
            assertThat(detail.getTitle()).isNull();
        }
    }

    @Nested
    @DisplayName("diff()")
    class DiffTests {

        private String toJson(Map<String, Object> map) {
            try {
                return objectMapper.writeValueAsString(map);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("returns empty diff for identical snapshots")
        void noDiffs() {
            String json = toJson(Map.of("title", "A"));
            assertThat(snapshotService.diff(json, json)).isEmpty();
        }

        @Test
        @DisplayName("detects changed field")
        void changedField() {
            String from = toJson(Map.of("title", "A", "difficulty", "EASY"));
            String to = toJson(Map.of("title", "A", "difficulty", "MEDIUM"));

            List<VersionDiffVO> diffs = snapshotService.diff(from, to);

            assertThat(diffs).hasSize(1);
            assertThat(diffs.get(0).getField()).isEqualTo("difficulty");
        }

        @Test
        @DisplayName("detects added field (old=null)")
        void addedField() {
            String from = toJson(Map.of("title", "A"));
            String to = toJson(Map.of("title", "A", "slug", "two-sum"));

            List<VersionDiffVO> diffs = snapshotService.diff(from, to);

            assertThat(diffs).hasSize(1);
            assertThat(diffs.get(0).getOldValue()).isNull();
        }

        @Test
        @DisplayName("detects removed field (new=null)")
        void removedField() {
            String from = toJson(Map.of("title", "A", "slug", "s"));
            String to = toJson(Map.of("title", "A"));

            List<VersionDiffVO> diffs = snapshotService.diff(from, to);

            assertThat(diffs).hasSize(1);
            assertThat(diffs.get(0).getNewValue()).isNull();
        }

        @Test
        @DisplayName("diff detects changed nested list value")
        void changedList() {
            String from = toJson(Map.of("title", "A", "tags", List.of("Array")));
            String to = toJson(Map.of("title", "A", "tags", List.of("Array", "Hash Table")));

            List<VersionDiffVO> diffs = snapshotService.diff(from, to);

            assertThat(diffs).hasSize(1);
            assertThat(diffs.get(0).getField()).isEqualTo("tags");
            assertThat(diffs.get(0).getOldValue()).isEqualTo(List.of("Array"));
            assertThat(diffs.get(0).getNewValue()).isEqualTo(List.of("Array", "Hash Table"));
        }

        @Test
        @DisplayName("diff throws on malformed JSON")
        void diffThrowsOnMalformed() {
            assertThatThrownBy(() -> snapshotService.diff("{broken", "{}"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("restore()")
    class RestoreTests {

        @Test
        @DisplayName("restore updates problem/detail and reinserts examples/languages/tags")
        @SuppressWarnings("unchecked")
        void restoreFullSnapshot() throws Exception {
            Problem currentProblem = createProblem();
            currentProblem.setTitle("Two Sum Modified");
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(currentProblem);

            ProblemDetail currentDetail = createProblemDetail();
            when(problemDetailMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(currentDetail);
            when(problemDetailMapper.selectById(currentDetail.getId())).thenReturn(currentDetail);

            when(problemTagMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(createProblemTag());

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("title", "Two Sum");
            snapshot.put("slug", "two-sum");
            snapshot.put("difficulty", "Easy");
            snapshot.put("isPremium", false);
            snapshot.put("isPublished", true);
            snapshot.put("summary", "Find two numbers that add up to target");
            snapshot.put("followUp", "Can you do it in O(n) time?");
            snapshot.put("constraints", List.of("2 <= nums.length <= 10^4"));
            snapshot.put("hints", List.of("Use a hash map"));
            snapshot.put("examples", List.of(Map.of(
                    "input", "nums = [2,7,11,15], target = 9",
                    "output", "[0,1]",
                    "explanation", "Because nums[0] + nums[1] == 9, we return [0, 1].")));
            snapshot.put("languages", List.of(Map.of(
                    "label", "Python",
                    "value", "python",
                    "style", "python",
                    "starterCode", "class Solution:\n    pass")));
            snapshot.put("tags", List.of("Array"));

            snapshotService.restore(PROBLEM_ID, objectMapper.writeValueAsString(snapshot));

            ArgumentCaptor<Problem> problemCaptor = ArgumentCaptor.forClass(Problem.class);
            verify(problemMapper).updateById(problemCaptor.capture());
            assertThat(problemCaptor.getValue().getTitle()).isEqualTo("Two Sum");
            assertThat(problemCaptor.getValue().getSlug()).isEqualTo("two-sum");
            assertThat(problemCaptor.getValue().getDifficulty()).isEqualTo("Easy");

            ArgumentCaptor<ProblemDetail> detailCaptor = ArgumentCaptor.forClass(ProblemDetail.class);
            verify(problemDetailMapper).updateById(detailCaptor.capture());
            assertThat(detailCaptor.getValue().getSummary()).isEqualTo("Find two numbers that add up to target");
            assertThat(detailCaptor.getValue().getConstraintsJson()).isEqualTo("[\"2 <= nums.length <= 10^4\"]");
            assertThat(detailCaptor.getValue().getHints()).isEqualTo("[\"Use a hash map\"]");

            verify(problemExampleMapper).delete(any());
            verify(problemLanguageMapper).delete(any());
            verify(problemTagRelationMapper).delete(any());

            ArgumentCaptor<ProblemExample> exampleCaptor = ArgumentCaptor.forClass(ProblemExample.class);
            verify(problemExampleMapper).insert(exampleCaptor.capture());
            assertThat(exampleCaptor.getValue().getInputText()).isEqualTo("nums = [2,7,11,15], target = 9");
            assertThat(exampleCaptor.getValue().getOutputText()).isEqualTo("[0,1]");
            assertThat(exampleCaptor.getValue().getExampleOrder()).isEqualTo(1);

            ArgumentCaptor<ProblemLanguage> langCaptor = ArgumentCaptor.forClass(ProblemLanguage.class);
            verify(problemLanguageMapper).insert(langCaptor.capture());
            assertThat(langCaptor.getValue().getLabel()).isEqualTo("Python");

            ArgumentCaptor<ProblemTagRelation> relCaptor = ArgumentCaptor.forClass(ProblemTagRelation.class);
            verify(problemTagRelationMapper).insert(relCaptor.capture());
            assertThat(relCaptor.getValue().getTagId()).isEqualTo("tag-1");
        }

        @Test
        @DisplayName("restore inserts new detail row when none exists")
        void restoreInsertsNewDetail() throws Exception {
            Problem currentProblem = createProblem();
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(currentProblem);
            when(problemDetailMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(problemDetailMapper.selectById(any())).thenReturn(null);

            // A real captured snapshot always carries slug; include it so the
            // restoreProblem -> restoreDetail re-read (which the original
            // ProblemVersionRollback also performs) finds a non-null slug.
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("title", "Two Sum");
            snapshot.put("slug", "two-sum");
            snapshot.put("constraints", null);
            snapshot.put("hints", null);
            snapshot.put("examples", List.of());
            snapshot.put("languages", List.of());
            snapshot.put("tags", List.of());

            snapshotService.restore(PROBLEM_ID, objectMapper.writeValueAsString(snapshot));

            verify(problemDetailMapper).insert(any(ProblemDetail.class));
        }

        @Test
        @DisplayName("restore accepts inputText/outputText fallback keys for examples")
        @SuppressWarnings("unchecked")
        void restoreAcceptsInputTextFallback() throws Exception {
            Problem currentProblem = createProblem();
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(currentProblem);
            when(problemDetailMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(problemDetailMapper.selectById(any())).thenReturn(null);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("slug", "two-sum");
            snapshot.put("examples", List.of(Map.of(
                    "inputText", "legacy input",
                    "outputText", "legacy output")));
            snapshot.put("languages", List.of());
            snapshot.put("tags", List.of());

            snapshotService.restore(PROBLEM_ID, objectMapper.writeValueAsString(snapshot));

            ArgumentCaptor<ProblemExample> exampleCaptor = ArgumentCaptor.forClass(ProblemExample.class);
            verify(problemExampleMapper).insert(exampleCaptor.capture());
            assertThat(exampleCaptor.getValue().getInputText()).isEqualTo("legacy input");
            assertThat(exampleCaptor.getValue().getOutputText()).isEqualTo("legacy output");
        }

        @Test
        @DisplayName("restore with null constraints/hints writes null columns")
        void restoreNullConstraintsHints() throws Exception {
            Problem currentProblem = createProblem();
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(currentProblem);
            ProblemDetail currentDetail = createProblemDetail();
            when(problemDetailMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(currentDetail);
            when(problemDetailMapper.selectById(any())).thenReturn(currentDetail);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("title", "Two Sum");
            snapshot.put("constraints", null);
            snapshot.put("hints", null);
            snapshot.put("examples", List.of());
            snapshot.put("languages", List.of());
            snapshot.put("tags", List.of());

            snapshotService.restore(PROBLEM_ID, objectMapper.writeValueAsString(snapshot));

            ArgumentCaptor<ProblemDetail> detailCaptor = ArgumentCaptor.forClass(ProblemDetail.class);
            verify(problemDetailMapper).updateById(detailCaptor.capture());
            assertThat(detailCaptor.getValue().getConstraintsJson()).isNull();
            assertThat(detailCaptor.getValue().getHints()).isNull();
        }
    }
}
