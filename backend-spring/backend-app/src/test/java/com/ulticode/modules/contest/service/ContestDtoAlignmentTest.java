package com.ulticode.modules.contest.service;

import com.ulticode.modules.contest.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that verify frontend-backend DTO alignment for the contests module.
 * These tests ensure that:
 * 1. ContestVO exposes all fields the frontend expects
 * 2. CreateContestDTO / UpdateContestDTO accept all fields the frontend sends
 * 3. ContestQueryDTO supports all filter parameters
 * 4. ScoringRuleVO uses camelCase field names (not snake_case)
 * 5. ContestRankingVO uses flat structure (not nested user objects)
 */
@DisplayName("Contest DTO Frontend-Backend Alignment")
class ContestDtoAlignmentTest {

    private static Set<String> getFieldNames(Class<?> clazz) {
        return java.util.Arrays.stream(clazz.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    @Nested
    @DisplayName("ContestVO alignment")
    class ContestVOAlignmentTests {

        @Test
        @DisplayName("should have contestType field")
        void contestVO_hasContestType() {
            Set<String> fields = getFieldNames(ContestVO.class);
            assertThat(fields).contains("contestType");
        }

        @Test
        @DisplayName("should have isVisible field")
        void contestVO_hasIsVisible() {
            Set<String> fields = getFieldNames(ContestVO.class);
            assertThat(fields).contains("isVisible");
        }

        @Test
        @DisplayName("should have participantCount field")
        void contestVO_hasParticipantCount() {
            Set<String> fields = getFieldNames(ContestVO.class);
            assertThat(fields).contains("participantCount");
        }

        @Test
        @DisplayName("should have problemCount field")
        void contestVO_hasProblemCount() {
            Set<String> fields = getFieldNames(ContestVO.class);
            assertThat(fields).contains("problemCount");
        }

        @Test
        @DisplayName("should have scoringRuleId field")
        void contestVO_hasScoringRuleId() {
            Set<String> fields = getFieldNames(ContestVO.class);
            assertThat(fields).contains("scoringRuleId");
        }
    }

    @Nested
    @DisplayName("CreateContestDTO alignment")
    class CreateContestDTOAlignmentTests {

        @Test
        @DisplayName("should have slug field")
        void createContestDTO_hasSlug() {
            Set<String> fields = getFieldNames(CreateContestDTO.class);
            assertThat(fields).contains("slug");
        }

        @Test
        @DisplayName("should have contestType field")
        void createContestDTO_hasContestType() {
            Set<String> fields = getFieldNames(CreateContestDTO.class);
            assertThat(fields).contains("contestType");
        }

        @Test
        @DisplayName("should have scoringRuleId field")
        void createContestDTO_hasScoringRuleId() {
            Set<String> fields = getFieldNames(CreateContestDTO.class);
            assertThat(fields).contains("scoringRuleId");
        }
    }

    @Nested
    @DisplayName("UpdateContestDTO alignment")
    class UpdateContestDTOAlignmentTests {

        @Test
        @DisplayName("should have slug field")
        void updateContestDTO_hasSlug() {
            Set<String> fields = getFieldNames(UpdateContestDTO.class);
            assertThat(fields).contains("slug");
        }

        @Test
        @DisplayName("should have contestType field")
        void updateContestDTO_hasContestType() {
            Set<String> fields = getFieldNames(UpdateContestDTO.class);
            assertThat(fields).contains("contestType");
        }

        @Test
        @DisplayName("should have scoringRuleId field")
        void updateContestDTO_hasScoringRuleId() {
            Set<String> fields = getFieldNames(UpdateContestDTO.class);
            assertThat(fields).contains("scoringRuleId");
        }

        @Test
        @DisplayName("title should NOT have @NotBlank (PATCH allows null)")
        void updateContestDTO_titleAllowsNull() throws NoSuchFieldException {
            var titleField = UpdateContestDTO.class.getDeclaredField("title");
            var notBlank = titleField.getAnnotationsByType(jakarta.validation.constraints.NotBlank.class);
            assertThat(notBlank).isEmpty();
        }
    }

    @Nested
    @DisplayName("ContestQueryDTO alignment")
    class ContestQueryDTOAlignmentTests {

        @Test
        @DisplayName("should have contestType filter field")
        void contestQueryDTO_hasContestType() {
            Set<String> fields = getFieldNames(ContestQueryDTO.class);
            assertThat(fields).contains("contestType");
        }

        @Test
        @DisplayName("should NOT have sortBy field (alias removed)")
        void contestQueryDTO_noSortBy() {
            Set<String> fields = getFieldNames(ContestQueryDTO.class);
            assertThat(fields).doesNotContain("sortBy");
        }

        @Test
        @DisplayName("should NOT have limit field (alias removed)")
        void contestQueryDTO_noLimit() {
            Set<String> fields = getFieldNames(ContestQueryDTO.class);
            assertThat(fields).doesNotContain("limit");
        }

        @Test
        @DisplayName("should NOT have isPublic field (alias removed)")
        void contestQueryDTO_noIsPublic() {
            Set<String> fields = getFieldNames(ContestQueryDTO.class);
            assertThat(fields).doesNotContain("isPublic");
        }

        @Test
        @DisplayName("status filter should include 'running' and 'draft'")
        void contestQueryDTO_statusIncludesRunningAndDraft() throws NoSuchFieldException {
            var statusField = ContestQueryDTO.class.getDeclaredField("status");
            var schema = statusField.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
            assertThat(schema).isNotNull();
            assertThat(schema.allowableValues()).contains("running", "draft");
        }
    }

    @Nested
    @DisplayName("ScoringRuleVO camelCase alignment")
    class ScoringRuleVOAlignmentTests {

        @Test
        @DisplayName("should use camelCase field names (not snake_case)")
        void scoringRuleVO_usesCamelCase() {
            Set<String> fields = getFieldNames(ScoringRuleVO.class);
            assertThat(fields).contains("baseScorePerProblem");
            assertThat(fields).contains("timeBonusPerMinute");
            assertThat(fields).contains("wrongAnswerPenalty");
            assertThat(fields).contains("timeLimitPenalty");
            assertThat(fields).contains("firstSolveBonus");
            assertThat(fields).contains("fullScoreBonus");
            assertThat(fields).contains("isDefault");
            assertThat(fields).contains("isActive");
            assertThat(fields).contains("createdAt");
            assertThat(fields).contains("updatedAt");
        }

        @Test
        @DisplayName("should NOT have snake_case fields")
        void scoringRuleVO_noSnakeCase() {
            Set<String> fields = getFieldNames(ScoringRuleVO.class);
            assertThat(fields).doesNotContain("base_score_per_problem");
            assertThat(fields).doesNotContain("time_bonus_per_minute");
            assertThat(fields).doesNotContain("wrong_answer_penalty");
            assertThat(fields).doesNotContain("time_limit_penalty");
            assertThat(fields).doesNotContain("first_solve_bonus");
            assertThat(fields).doesNotContain("full_score_bonus");
            assertThat(fields).doesNotContain("is_default");
            assertThat(fields).doesNotContain("is_active");
            assertThat(fields).doesNotContain("created_at");
            assertThat(fields).doesNotContain("updated_at");
        }
    }

    @Nested
    @DisplayName("ContestRankingVO flat structure alignment")
    class ContestRankingVOAlignmentTests {

        @Test
        @DisplayName("should have flat userId field (not nested user.id)")
        void contestRankingVO_hasFlatUserId() {
            Set<String> fields = getFieldNames(ContestRankingVO.class);
            assertThat(fields).contains("userId");
        }

        @Test
        @DisplayName("should have flat username field (not nested user.username)")
        void contestRankingVO_hasFlatUsername() {
            Set<String> fields = getFieldNames(ContestRankingVO.class);
            assertThat(fields).contains("username");
        }

        @Test
        @DisplayName("should have flat name field (not nested user.name)")
        void contestRankingVO_hasFlatName() {
            Set<String> fields = getFieldNames(ContestRankingVO.class);
            assertThat(fields).contains("name");
        }

        @Test
        @DisplayName("should have 'score' field (not 'totalScore')")
        void contestRankingVO_hasScore() {
            Set<String> fields = getFieldNames(ContestRankingVO.class);
            assertThat(fields).contains("score");
            assertThat(fields).doesNotContain("totalScore");
        }

        @Test
        @DisplayName("should have 'penalty' field (not 'totalPenalty')")
        void contestRankingVO_hasPenalty() {
            Set<String> fields = getFieldNames(ContestRankingVO.class);
            assertThat(fields).contains("penalty");
            assertThat(fields).doesNotContain("totalPenalty");
        }

        @Test
        @DisplayName("should NOT have nested 'user' object field")
        void contestRankingVO_noNestedUser() {
            Set<String> fields = getFieldNames(ContestRankingVO.class);
            assertThat(fields).doesNotContain("user");
        }
    }

    @Nested
    @DisplayName("ContestProblemVO alignment")
    class ContestProblemVOAlignmentTests {

        @Test
        @DisplayName("ContestProblemVO class should exist")
        void contestProblemVO_exists() {
            assertThat(ContestProblemVO.class).isNotNull();
        }

        @Test
        @DisplayName("should have required fields")
        void contestProblemVO_hasFields() {
            Set<String> fields = getFieldNames(ContestProblemVO.class);
            assertThat(fields).contains("id", "contestId", "problemId", "problemIndex", "score");
        }
    }

    @Nested
    @DisplayName("UserContestHistoryVO alignment")
    class UserContestHistoryVOAlignmentTests {

        @Test
        @DisplayName("should have required fields")
        void userContestHistoryVO_hasRequiredFields() {
            Set<String> fields = getFieldNames(UserContestHistoryVO.class);
            assertThat(fields).contains("contestId", "title", "slug", "startTime", "finishTime",
                    "rank", "score", "penalty", "problemsSolved", "totalParticipants", "isRated");
        }
    }

    @Nested
    @DisplayName("LiveRankingEntryVO alignment")
    class LiveRankingEntryVOAlignmentTests {

        @Test
        @DisplayName("should have required fields")
        void liveRankingEntryVO_hasRequiredFields() {
            Set<String> fields = getFieldNames(LiveRankingEntryVO.class);
            assertThat(fields).contains("rank", "userId", "username", "name", "avatar",
                    "score", "penalty", "problemsSolved", "isCurrentUser");
        }
    }

    @Nested
    @DisplayName("AddContestProblemDTO alignment")
    class AddContestProblemDTOAlignmentTests {

        @Test
        @DisplayName("AddContestProblemDTO class should exist")
        void addContestProblemDTO_exists() {
            assertThat(AddContestProblemDTO.class).isNotNull();
        }

        @Test
        @DisplayName("should have problemId field with validation")
        void addContestProblemDTO_hasProblemId() throws NoSuchFieldException {
            var problemIdField = AddContestProblemDTO.class.getDeclaredField("problemId");
            var notNull = problemIdField.getAnnotationsByType(jakarta.validation.constraints.NotNull.class);
            assertThat(notNull).isNotEmpty();
        }

        @Test
        @DisplayName("should have optional score field")
        void addContestProblemDTO_hasScore() {
            Set<String> fields = getFieldNames(AddContestProblemDTO.class);
            assertThat(fields).contains("score");
        }
    }
}
