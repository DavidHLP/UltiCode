package com.ulticode.modules.problem.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemDetail;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.entity.ProblemLanguage;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemVersion;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.problem.mapper.ProblemVersionMapper;
import com.ulticode.modules.problem.service.codec.ProblemSnapshotCodec;
import com.ulticode.modules.problem.service.codec.ProblemVersionDiff;
import com.ulticode.modules.problem.service.codec.ProblemVersionRollback;
import com.ulticode.modules.problem.service.impl.ProblemVersionServiceImpl;
import com.ulticode.modules.problem.vo.ProblemVersionDetailVO;
import com.ulticode.modules.problem.vo.ProblemVersionVO;
import com.ulticode.modules.problem.vo.VersionDiffVO;
import com.ulticode.modules.problem.vo.VersionWithDiffVO;
import com.ulticode.modules.problem.vo.VersionsResponseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemVersionService")
class ProblemVersionServiceTest {

    private static final Long PROBLEM_ID = 1L;
    private static final String OPERATOR_ID = "user-001";
    private static final String VERSION_ID = "100";

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

    @Mock
    private ProblemVersionMapper problemVersionMapper;

    private ObjectMapper objectMapper;

    private ProblemVersionService problemVersionService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        FixedUuidGenerator uuidGenerator = new FixedUuidGenerator();
        ProblemSnapshotCodec snapshotCodec = new ProblemSnapshotCodec(objectMapper);
        ProblemVersionDiff versionDiff = new ProblemVersionDiff(objectMapper);
        ProblemVersionRollback versionRollback = new ProblemVersionRollback(
                problemMapper, problemDetailMapper, problemExampleMapper,
                problemLanguageMapper, problemTagMapper, problemTagRelationMapper,
                uuidGenerator, objectMapper);
        problemVersionService = new ProblemVersionServiceImpl(
                problemMapper,
                problemDetailMapper,
                problemExampleMapper,
                problemLanguageMapper,
                problemVersionMapper,
                objectMapper,
                uuidGenerator,
                snapshotCodec,
                versionDiff,
                versionRollback
        );
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
        lang.setStarterCode("class Solution:\n    def twoSum(self, nums: List[int], target: int) -> List[int]:\n        pass");
        return lang;
    }

    private ProblemTag createProblemTag() {
        ProblemTag tag = new ProblemTag();
        tag.setId("tag-1");
        tag.setLabel("Array");
        tag.setSlug("array");
        return tag;
    }

    private ProblemVersion createProblemVersion(Long id, int versionNumber, String changeType) {
        ProblemVersion version = new ProblemVersion();
        version.setId(id);
        version.setProblemId(PROBLEM_ID);
        version.setVersionNumber(versionNumber);
        version.setChangeType(changeType);
        version.setChangeSummary(changeType + " summary");
        version.setCreatedBy(OPERATOR_ID);
        version.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        return version;
    }

    private void mockBuildSnapshot() {
        when(problemMapper.selectById(PROBLEM_ID)).thenReturn(createProblem());
        when(problemDetailMapper.selectOne(any())).thenReturn(createProblemDetail());
        when(problemExampleMapper.findByProblemIdOrderByOrder(PROBLEM_ID)).thenReturn(List.of(createProblemExample(1)));
        when(problemLanguageMapper.findByProblemId(PROBLEM_ID)).thenReturn(List.of(createProblemLanguage()));
        when(problemMapper.selectTagsByProblemIds(anyList())).thenReturn(
                List.of(new ProblemMapper.ProblemTagDTO(PROBLEM_ID, createProblemTag().getId(), createProblemTag().getLabel()))
        );
    }

    private void mockInsertVersion(Long assignedId) {
        doAnswer(invocation -> {
            ProblemVersion version = invocation.getArgument(0);
            version.setId(assignedId);
            return 1;
        }).when(problemVersionMapper).insert(any(ProblemVersion.class));
    }

    private String buildSnapshotJson() throws Exception {
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
                "inputText", "nums = [2,7,11,15], target = 9",
                "outputText", "[0,1]",
                "explanation", "Because nums[0] + nums[1] == 9, we return [0, 1]."
        )));
        snapshot.put("languages", List.of(Map.of(
                "label", "Python",
                "value", "python",
                "style", "python",
                "starterCode", "class Solution:\n    def twoSum(self, nums: List[int], target: int) -> List[int]:\n        pass"
        )));
        snapshot.put("tags", List.of("Array"));
        return objectMapper.writeValueAsString(snapshot);
    }

    @Nested
    @DisplayName("createInitialVersion()")
    class CreateInitialVersionTests {

        @Test
        @DisplayName("should create version 1 with CREATE type and non-empty snapshot")
        void shouldCreateInitialVersion() throws Exception {
            mockBuildSnapshot();
            mockInsertVersion(100L);

            ProblemVersionVO result = problemVersionService.createInitialVersion(PROBLEM_ID, OPERATOR_ID);

            assertThat(result).isNotNull();
            assertThat(result.getVersionNumber()).isEqualTo(1);
            assertThat(result.getChangeType()).isEqualTo("CREATE");

            ArgumentCaptor<ProblemVersion> captor = ArgumentCaptor.forClass(ProblemVersion.class);
            verify(problemVersionMapper).insert(captor.capture());
            ProblemVersion saved = captor.getValue();
            assertThat(saved.getVersionNumber()).isEqualTo(1);
            assertThat(saved.getChangeType()).isEqualTo("CREATE");
            assertThat(saved.getSnapshotJson()).isNotBlank();
            assertThat(saved.getProblemId()).isEqualTo(PROBLEM_ID);
            assertThat(saved.getCreatedBy()).isEqualTo(OPERATOR_ID);
        }

        @Test
        @DisplayName("should throw exception when problem not found")
        void shouldThrowWhenProblemNotFound() {
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> problemVersionService.createInitialVersion(PROBLEM_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND));
        }

        @Test
        @DisplayName("should throw PROBLEM_VERSION_ALREADY_EXISTS when initial version already exists")
        void shouldThrowWhenInitialVersionAlreadyExists() {
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(createProblem());
            // simulate existing versionNumber=1 in DB
            when(problemVersionMapper.selectLatestVersionNumber(PROBLEM_ID)).thenReturn(1);

            assertThatThrownBy(() -> problemVersionService.createInitialVersion(PROBLEM_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode())
                                .isEqualTo(ErrorCode.PROBLEM_VERSION_ALREADY_EXISTS);
                        assertThat(be.getMessage())
                                .contains("Initial version already exists")
                                .contains(PROBLEM_ID.toString());
                    });

            // Must not insert a new version
            verify(problemVersionMapper, never()).insert(any(ProblemVersion.class));
        }

        @Test
        @DisplayName("should translate DuplicateKeyException to PROBLEM_VERSION_ALREADY_EXISTS (race condition)")
        void shouldTranslateDuplicateKeyOnRaceCondition() {
            // SELECT sees no version (passes pre-check), but concurrent transaction inserted
            // a row in the meantime, so INSERT throws DuplicateKeyException
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(createProblem());
            when(problemVersionMapper.selectLatestVersionNumber(PROBLEM_ID)).thenReturn(null);
            mockBuildSnapshot();
            org.mockito.Mockito.doThrow(
                    new org.springframework.dao.DuplicateKeyException(
                            "Duplicate entry '1' for key 'problem_versions.uk_problem_version'"))
                    .when(problemVersionMapper).insert(any(ProblemVersion.class));

            assertThatThrownBy(() -> problemVersionService.createInitialVersion(PROBLEM_ID, OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode())
                                .isEqualTo(ErrorCode.PROBLEM_VERSION_ALREADY_EXISTS);
                        assertThat(be.getMessage())
                                .contains("Initial version already exists")
                                .contains(PROBLEM_ID.toString());
                    });
        }
    }

    @Nested
    @DisplayName("createVersion()")
    class CreateVersionTests {

        @Test
        @DisplayName("should create version with incremented number and UPDATE type")
        void shouldCreateUpdateVersion() throws Exception {
            mockBuildSnapshot();
            when(problemVersionMapper.selectLatestVersionNumber(PROBLEM_ID)).thenReturn(2);
            mockInsertVersion(101L);

            ProblemVersionVO result = problemVersionService.createVersion(
                    PROBLEM_ID, "UPDATE", "Fixed typo", OPERATOR_ID);

            assertThat(result).isNotNull();
            assertThat(result.getVersionNumber()).isEqualTo(3);
            assertThat(result.getChangeType()).isEqualTo("UPDATE");

            ArgumentCaptor<ProblemVersion> captor = ArgumentCaptor.forClass(ProblemVersion.class);
            verify(problemVersionMapper).insert(captor.capture());
            ProblemVersion saved = captor.getValue();
            assertThat(saved.getVersionNumber()).isEqualTo(3);
            assertThat(saved.getChangeType()).isEqualTo("UPDATE");
            assertThat(saved.getChangeSummary()).isEqualTo("Fixed typo");
            assertThat(saved.getSnapshotJson()).isNotBlank();
        }

        @Test
        @DisplayName("should start at version 1 when no previous versions exist")
        void shouldStartAtVersionOneWhenNoPreviousVersions() throws Exception {
            mockBuildSnapshot();
            when(problemVersionMapper.selectLatestVersionNumber(PROBLEM_ID)).thenReturn(null);
            mockInsertVersion(102L);

            ProblemVersionVO result = problemVersionService.createVersion(
                    PROBLEM_ID, "UPDATE", "First update", OPERATOR_ID);

            assertThat(result.getVersionNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw exception when problem not found")
        void shouldThrowWhenProblemNotFound() {
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> problemVersionService.createVersion(
                    PROBLEM_ID, "UPDATE", "Summary", OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("listVersions()")
    class ListVersionsTests {

        @Test
        @DisplayName("should return paginated results with correct metadata")
        void shouldReturnPaginatedResults() {
            ProblemVersion v1 = createProblemVersion(1L, 1, "CREATE");
            ProblemVersion v2 = createProblemVersion(2L, 2, "UPDATE");

            Page<ProblemVersion> pageResult = new Page<>(1, 20);
            pageResult.setRecords(List.of(v2, v1));
            pageResult.setTotal(2);

            when(problemVersionMapper.selectByProblemId(eq(PROBLEM_ID), any(Page.class)))
                    .thenReturn(pageResult);

            VersionsResponseVO result = problemVersionService.listVersions(PROBLEM_ID, 1, 20);

            assertThat(result).isNotNull();
            assertThat(result.getVersions()).hasSize(2);
            assertThat(result.getPagination().getTotal()).isEqualTo(2);
            assertThat(result.getPagination().getPage()).isEqualTo(1);
            assertThat(result.getPagination().getLimit()).isEqualTo(20);
            assertThat(result.getPagination().getTotalPages()).isEqualTo(1);
            assertThat(result.getVersions().get(0).getVersionNumber()).isEqualTo(2);
            assertThat(result.getVersions().get(1).getVersionNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("should use default page and limit when null or invalid")
        void shouldUseDefaultsWhenNullOrInvalid() {
            Page<ProblemVersion> pageResult = new Page<>(1, 20);
            pageResult.setRecords(List.of());
            pageResult.setTotal(0);

            when(problemVersionMapper.selectByProblemId(eq(PROBLEM_ID), any(Page.class)))
                    .thenReturn(pageResult);

            VersionsResponseVO result = problemVersionService.listVersions(PROBLEM_ID, null, null);

            assertThat(result.getPagination().getPage()).isEqualTo(1);
            assertThat(result.getPagination().getLimit()).isEqualTo(20);
        }

        @Test
        @DisplayName("should cap limit at 100")
        void shouldCapLimitAt100() {
            Page<ProblemVersion> pageResult = new Page<>(1, 100);
            pageResult.setRecords(List.of());
            pageResult.setTotal(0);

            when(problemVersionMapper.selectByProblemId(eq(PROBLEM_ID), any(Page.class)))
                    .thenReturn(pageResult);

            VersionsResponseVO result = problemVersionService.listVersions(PROBLEM_ID, 1, 200);

            assertThat(result.getPagination().getLimit()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("getVersionDetail()")
    class GetVersionDetailTests {

        @Test
        @DisplayName("should return full snapshot data")
        void shouldReturnFullSnapshotData() throws Exception {
            String snapshotJson = buildSnapshotJson();

            ProblemVersion version = createProblemVersion(100L, 1, "CREATE");
            version.setSnapshotJson(snapshotJson);

            when(problemVersionMapper.selectById(100L)).thenReturn(version);

            ProblemVersionDetailVO result = problemVersionService.getVersionDetail(PROBLEM_ID, VERSION_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("100");
            assertThat(result.getVersionNumber()).isEqualTo(1);
            assertThat(result.getChangeType()).isEqualTo("CREATE");
            assertThat(result.getTitle()).isEqualTo("Two Sum");
            assertThat(result.getSlug()).isEqualTo("two-sum");
            assertThat(result.getDifficulty()).isEqualTo("Easy");
            assertThat(result.getIsPremium()).isFalse();
            assertThat(result.getIsPublished()).isTrue();
            assertThat(result.getSummary()).isEqualTo("Find two numbers that add up to target");
            assertThat(result.getConstraints()).containsExactly("2 <= nums.length <= 10^4");
            assertThat(result.getHints()).containsExactly("Use a hash map");
            assertThat(result.getExamples()).hasSize(1);
            assertThat(result.getLanguages()).hasSize(1);
            assertThat(result.getTags()).containsExactly("Array");
        }

        @Test
        @DisplayName("should handle null snapshot gracefully")
        void shouldHandleNullSnapshot() {
            ProblemVersion version = createProblemVersion(100L, 1, "CREATE");
            version.setSnapshotJson(null);

            when(problemVersionMapper.selectById(100L)).thenReturn(version);

            ProblemVersionDetailVO result = problemVersionService.getVersionDetail(PROBLEM_ID, VERSION_ID);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isNull();
        }

        @Test
        @DisplayName("should throw exception when version not found")
        void shouldThrowWhenVersionNotFound() {
            when(problemVersionMapper.selectById(100L)).thenReturn(null);

            assertThatThrownBy(() -> problemVersionService.getVersionDetail(PROBLEM_ID, VERSION_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @Test
        @DisplayName("should throw exception when version belongs to different problem")
        void shouldThrowWhenVersionBelongsToDifferentProblem() {
            ProblemVersion version = createProblemVersion(100L, 1, "CREATE");
            version.setProblemId(999L);

            when(problemVersionMapper.selectById(100L)).thenReturn(version);

            assertThatThrownBy(() -> problemVersionService.getVersionDetail(PROBLEM_ID, VERSION_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("compareVersions()")
    class CompareVersionsTests {

        @Test
        @DisplayName("should return only changed fields with correct old and new values")
        void shouldReturnOnlyChangedFields() throws Exception {
            Map<String, Object> fromSnapshot = new LinkedHashMap<>();
            fromSnapshot.put("title", "Two Sum");
            fromSnapshot.put("difficulty", "Easy");
            fromSnapshot.put("tags", List.of("Array"));

            Map<String, Object> toSnapshot = new LinkedHashMap<>();
            toSnapshot.put("title", "Two Sum Updated");
            toSnapshot.put("difficulty", "Easy");
            toSnapshot.put("tags", List.of("Array", "Hash Table"));

            ProblemVersion fromVersion = createProblemVersion(100L, 1, "CREATE");
            fromVersion.setSnapshotJson(objectMapper.writeValueAsString(fromSnapshot));

            ProblemVersion toVersion = createProblemVersion(101L, 2, "UPDATE");
            toVersion.setSnapshotJson(objectMapper.writeValueAsString(toSnapshot));

            when(problemVersionMapper.selectById(100L)).thenReturn(fromVersion);
            when(problemVersionMapper.selectById(101L)).thenReturn(toVersion);

            VersionWithDiffVO result = problemVersionService.compareVersions(PROBLEM_ID, "100", "101");

            assertThat(result).isNotNull();
            assertThat(result.getFromVersion().getId()).isEqualTo("100");
            assertThat(result.getToVersion().getId()).isEqualTo("101");
            assertThat(result.getDiffs()).hasSize(2);

            VersionDiffVO titleDiff = result.getDiffs().stream()
                    .filter(d -> d.getField().equals("title"))
                    .findFirst()
                    .orElseThrow();
            assertThat(titleDiff.getOldValue()).isEqualTo("Two Sum");
            assertThat(titleDiff.getNewValue()).isEqualTo("Two Sum Updated");

            VersionDiffVO tagsDiff = result.getDiffs().stream()
                    .filter(d -> d.getField().equals("tags"))
                    .findFirst()
                    .orElseThrow();
            assertThat(tagsDiff.getOldValue()).isEqualTo(List.of("Array"));
            assertThat(tagsDiff.getNewValue()).isEqualTo(List.of("Array", "Hash Table"));

            assertThat(result.getDiffs().stream().noneMatch(d -> d.getField().equals("difficulty"))).isTrue();
        }

        @Test
        @DisplayName("should return empty diffs when snapshots are identical")
        void shouldReturnEmptyDiffsWhenIdentical() throws Exception {
            Map<String, Object> snapshot = Map.of("title", "Same Title", "difficulty", "Easy");

            ProblemVersion v1 = createProblemVersion(100L, 1, "CREATE");
            v1.setSnapshotJson(objectMapper.writeValueAsString(snapshot));

            ProblemVersion v2 = createProblemVersion(101L, 2, "UPDATE");
            v2.setSnapshotJson(objectMapper.writeValueAsString(snapshot));

            when(problemVersionMapper.selectById(100L)).thenReturn(v1);
            when(problemVersionMapper.selectById(101L)).thenReturn(v2);

            VersionWithDiffVO result = problemVersionService.compareVersions(PROBLEM_ID, "100", "101");

            assertThat(result.getDiffs()).isEmpty();
        }

        @Test
        @DisplayName("should throw exception when from version not found")
        void shouldThrowWhenFromVersionNotFound() {
            when(problemVersionMapper.selectById(100L)).thenReturn(null);

            assertThatThrownBy(() -> problemVersionService.compareVersions(PROBLEM_ID, "100", "101"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @Test
        @DisplayName("should throw exception when to version not found")
        void shouldThrowWhenToVersionNotFound() {
            ProblemVersion fromVersion = createProblemVersion(100L, 1, "CREATE");
            fromVersion.setSnapshotJson("{}");

            when(problemVersionMapper.selectById(100L)).thenReturn(fromVersion);
            when(problemVersionMapper.selectById(101L)).thenReturn(null);

            assertThatThrownBy(() -> problemVersionService.compareVersions(PROBLEM_ID, "100", "101"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @Test
        @DisplayName("should throw exception when from version belongs to different problem")
        void shouldThrowWhenFromVersionBelongsToDifferentProblem() {
            ProblemVersion fromVersion = createProblemVersion(100L, 1, "CREATE");
            fromVersion.setProblemId(999L);
            fromVersion.setSnapshotJson("{}");

            when(problemVersionMapper.selectById(100L)).thenReturn(fromVersion);

            assertThatThrownBy(() -> problemVersionService.compareVersions(PROBLEM_ID, "100", "101"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("rollbackToVersion()")
    class RollbackToVersionTests {

        @Test
        @DisplayName("should create ROLLBACK version and update problem data")
        void shouldCreateRollbackVersionAndUpdateData() throws Exception {
            ProblemVersion targetVersion = createProblemVersion(100L, 2, "UPDATE");
            targetVersion.setSnapshotJson(buildSnapshotJson());

            when(problemVersionMapper.selectById(100L)).thenReturn(targetVersion);

            Problem currentProblem = createProblem();
            currentProblem.setTitle("Two Sum Modified");
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(currentProblem);

            ProblemDetail currentDetail = createProblemDetail();
            when(problemDetailMapper.selectOne(any())).thenReturn(currentDetail);
            when(problemDetailMapper.selectById(currentDetail.getId())).thenReturn(currentDetail);

            when(problemTagMapper.selectOne(any())).thenReturn(createProblemTag());

            when(problemVersionMapper.selectLatestVersionNumber(PROBLEM_ID)).thenReturn(3);
            mockBuildSnapshot();
            mockInsertVersion(200L);

            ProblemVersionVO result = problemVersionService.rollbackToVersion(
                    PROBLEM_ID, VERSION_ID, "Rollback to stable version", OPERATOR_ID);

            assertThat(result).isNotNull();
            assertThat(result.getChangeType()).isEqualTo("ROLLBACK");

            ArgumentCaptor<Problem> problemCaptor = ArgumentCaptor.forClass(Problem.class);
            verify(problemMapper).updateById(problemCaptor.capture());
            Problem updatedProblem = problemCaptor.getValue();
            assertThat(updatedProblem.getTitle()).isEqualTo("Two Sum");
            assertThat(updatedProblem.getSlug()).isEqualTo("two-sum");
            assertThat(updatedProblem.getDifficulty()).isEqualTo("Easy");

            ArgumentCaptor<ProblemDetail> detailCaptor = ArgumentCaptor.forClass(ProblemDetail.class);
            verify(problemDetailMapper).updateById(detailCaptor.capture());
            ProblemDetail updatedDetail = detailCaptor.getValue();
            assertThat(updatedDetail.getSummary()).isEqualTo("Find two numbers that add up to target");

            verify(problemExampleMapper).delete(any());
            verify(problemLanguageMapper).delete(any());
            verify(problemTagRelationMapper).delete(any());

            ArgumentCaptor<ProblemVersion> versionCaptor = ArgumentCaptor.forClass(ProblemVersion.class);
            verify(problemVersionMapper).insert(versionCaptor.capture());
            ProblemVersion rollbackVersion = versionCaptor.getValue();
            assertThat(rollbackVersion.getChangeType()).isEqualTo("ROLLBACK");
            assertThat(rollbackVersion.getChangeSummary()).isEqualTo("Rollback to stable version");
            assertThat(rollbackVersion.getVersionNumber()).isEqualTo(4);
        }

        @Test
        @DisplayName("should use default summary when reason is null")
        void shouldUseDefaultSummaryWhenReasonIsNull() throws Exception {
            ProblemVersion targetVersion = createProblemVersion(100L, 2, "UPDATE");
            targetVersion.setSnapshotJson(buildSnapshotJson());

            when(problemVersionMapper.selectById(100L)).thenReturn(targetVersion);

            Problem currentProblem = createProblem();
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(currentProblem);

            ProblemDetail currentDetail = createProblemDetail();
            when(problemDetailMapper.selectOne(any())).thenReturn(currentDetail);
            when(problemDetailMapper.selectById(currentDetail.getId())).thenReturn(currentDetail);

            when(problemTagMapper.selectOne(any())).thenReturn(createProblemTag());

            when(problemVersionMapper.selectLatestVersionNumber(PROBLEM_ID)).thenReturn(3);
            mockBuildSnapshot();
            mockInsertVersion(200L);

            problemVersionService.rollbackToVersion(PROBLEM_ID, VERSION_ID, null, OPERATOR_ID);

            ArgumentCaptor<ProblemVersion> versionCaptor = ArgumentCaptor.forClass(ProblemVersion.class);
            verify(problemVersionMapper).insert(versionCaptor.capture());
            assertThat(versionCaptor.getValue().getChangeSummary()).isEqualTo("Rollback to version 2");
        }

        @Test
        @DisplayName("should insert new detail when current detail does not exist")
        void shouldInsertNewDetailWhenNotExists() throws Exception {
            ProblemVersion targetVersion = createProblemVersion(100L, 2, "UPDATE");
            targetVersion.setSnapshotJson(buildSnapshotJson());

            when(problemVersionMapper.selectById(100L)).thenReturn(targetVersion);

            Problem currentProblem = createProblem();
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(currentProblem);

            when(problemDetailMapper.selectOne(any())).thenReturn(null);
            when(problemDetailMapper.selectById(any())).thenReturn(null);

            when(problemTagMapper.selectOne(any())).thenReturn(createProblemTag());

            when(problemVersionMapper.selectLatestVersionNumber(PROBLEM_ID)).thenReturn(3);
            mockBuildSnapshot();
            mockInsertVersion(200L);

            problemVersionService.rollbackToVersion(PROBLEM_ID, VERSION_ID, "reason", OPERATOR_ID);

            verify(problemDetailMapper).insert(any(ProblemDetail.class));
            verify(problemDetailMapper, never()).updateById(any(ProblemDetail.class));
        }

        @Test
        @DisplayName("should throw exception when target version not found")
        void shouldThrowWhenTargetVersionNotFound() {
            when(problemVersionMapper.selectById(100L)).thenReturn(null);

            assertThatThrownBy(() -> problemVersionService.rollbackToVersion(
                    PROBLEM_ID, VERSION_ID, "reason", OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @Test
        @DisplayName("should throw exception when target version belongs to different problem")
        void shouldThrowWhenTargetVersionBelongsToDifferentProblem() {
            ProblemVersion targetVersion = createProblemVersion(100L, 2, "UPDATE");
            targetVersion.setProblemId(999L);
            targetVersion.setSnapshotJson("{}");

            when(problemVersionMapper.selectById(100L)).thenReturn(targetVersion);

            assertThatThrownBy(() -> problemVersionService.rollbackToVersion(
                    PROBLEM_ID, VERSION_ID, "reason", OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_FOUND));
        }

        @Test
        @DisplayName("should throw exception when problem not found during rollback")
        void shouldThrowWhenProblemNotFoundDuringRollback() throws Exception {
            ProblemVersion targetVersion = createProblemVersion(100L, 2, "UPDATE");
            targetVersion.setSnapshotJson(buildSnapshotJson());

            when(problemVersionMapper.selectById(100L)).thenReturn(targetVersion);
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> problemVersionService.rollbackToVersion(
                    PROBLEM_ID, VERSION_ID, "reason", OPERATOR_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND));
        }

        @Test
        @DisplayName("should handle snapshot with null constraints and hints")
        void shouldHandleSnapshotWithNullConstraintsAndHints() throws Exception {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("title", "Two Sum");
            snapshot.put("slug", "two-sum");
            snapshot.put("difficulty", "Easy");
            snapshot.put("isPremium", false);
            snapshot.put("isPublished", true);
            snapshot.put("summary", "Summary");
            snapshot.put("constraints", null);
            snapshot.put("hints", null);
            snapshot.put("examples", List.of());
            snapshot.put("languages", List.of());
            snapshot.put("tags", List.of());

            ProblemVersion targetVersion = createProblemVersion(100L, 2, "UPDATE");
            targetVersion.setSnapshotJson(objectMapper.writeValueAsString(snapshot));

            when(problemVersionMapper.selectById(100L)).thenReturn(targetVersion);

            Problem currentProblem = createProblem();
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(currentProblem);

            ProblemDetail currentDetail = createProblemDetail();
            when(problemDetailMapper.selectOne(any())).thenReturn(currentDetail);
            when(problemDetailMapper.selectById(any())).thenReturn(currentDetail);

            when(problemVersionMapper.selectLatestVersionNumber(PROBLEM_ID)).thenReturn(3);
            mockBuildSnapshot();
            mockInsertVersion(200L);

            problemVersionService.rollbackToVersion(PROBLEM_ID, VERSION_ID, "reason", OPERATOR_ID);

            ArgumentCaptor<ProblemDetail> detailCaptor = ArgumentCaptor.forClass(ProblemDetail.class);
            verify(problemDetailMapper).updateById(detailCaptor.capture());
            assertThat(detailCaptor.getValue().getConstraintsJson()).isNull();
            assertThat(detailCaptor.getValue().getHints()).isNull();
        }
    }
}
