package com.ulticode.modules.achievement.criteria;

import com.ulticode.modules.achievement.constants.AchievementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link AchievementCriteria} deep module.
 *
 * <p>Drives the criteria interface directly &mdash; the test surface that was
 * previously buried inside {@code DefaultAchievementProjection} and
 * {@code AchievementTriggerServiceImpl} (architecture-review candidate 3).
 * Pure domain object, no Spring or mapper dependencies.</p>
 */
class AchievementCriteriaTest {

    private static final AchievementCounters COUNTERS = new AchievementCounters(7L, 42L);

    private static Map<String, Object> criteria(String type, Object target) {
        Map<String, Object> map = new HashMap<>();
        if (type != null) {
            map.put("type", type);
        }
        if (target != null) {
            map.put("target", target);
        }
        return map;
    }

    @Nested
    @DisplayName("from(Map) decoding")
    class DecodingTests {

        @Test
        @DisplayName("null map decodes to empty criteria")
        void fromNullMapIsEmpty() {
            AchievementCriteria criteria = AchievementCriteria.from(null);

            assertNull(criteria.type());
            assertEquals(0, criteria.target());
            assertEquals(0, criteria.currentValue(COUNTERS));
            assertNull(criteria.nextMilestone(0));
        }

        @Test
        @DisplayName("map without type or target decodes to empty values")
        void fromEmptyMapIsEmpty() {
            AchievementCriteria criteria = AchievementCriteria.from(new HashMap<>());

            assertNull(criteria.type());
            assertEquals(0, criteria.target());
        }

        @Test
        @DisplayName("non-numeric target coerces to zero")
        void nonNumericTargetIsZero() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", "not-a-number"));

            assertEquals(0, criteria.target());
        }

        @Test
        @DisplayName("numeric target coerces via intValue")
        void numericTargetIsCoerced() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", 5.7));

            assertEquals(5, criteria.target());
        }
    }

    @Nested
    @DisplayName("type matching")
    class TypeMatchTests {

        @Test
        @DisplayName("matches the achievement type equal to the decoded value")
        void matchesEqualType() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", 10));

            assertTrue(criteria.matches(AchievementType.PROBLEMS_SOLVED));
        }

        @Test
        @DisplayName("does not match a different achievement type")
        void doesNotMatchDifferentType() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", 10));

            assertFalse(criteria.matches(AchievementType.SUBMISSIONS_MADE));
        }

        @Test
        @DisplayName("empty criteria matches nothing")
        void emptyMatchesNothing() {
            AchievementCriteria criteria = AchievementCriteria.from(null);

            assertFalse(criteria.matches(AchievementType.PROBLEMS_SOLVED));
        }
    }

    @Nested
    @DisplayName("current value & percentage")
    class CurrentValueTests {

        @Test
        @DisplayName("problems_solved resolves to the problems counter")
        void problemsSolvedCounter() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", 10));

            assertEquals(7, criteria.currentValue(COUNTERS));
        }

        @Test
        @DisplayName("submissions_made resolves to the submissions counter")
        void submissionsMadeCounter() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("submissions_made", 100));

            assertEquals(42, criteria.currentValue(COUNTERS));
        }

        @Test
        @DisplayName("unknown type resolves to zero")
        void unknownTypeIsZero() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("rating_milestone", 1500));

            assertEquals(0, criteria.currentValue(COUNTERS));
        }

        @Test
        @DisplayName("percentage clamps to 100")
        void percentageClampsToHundred() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", 10));

            assertEquals(70, criteria.progressPercent(7));
            assertEquals(100, criteria.progressPercent(10));
            assertEquals(100, criteria.progressPercent(11));
        }

        @Test
        @DisplayName("zero target yields zero percentage")
        void zeroTargetIsZeroPercent() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", null));

            assertEquals(0, criteria.progressPercent(50));
        }
    }

    @Nested
    @DisplayName("award eligibility")
    class AwardEligibilityTests {

        @Test
        @DisplayName("met when current value reaches target")
        void metAtTarget() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", 10));

            assertTrue(criteria.isMetBy(10));
        }

        @Test
        @DisplayName("not met below target")
        void notMetBelowTarget() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", 10));

            assertFalse(criteria.isMetBy(9));
        }

        @Test
        @DisplayName("zero target is met by any non-negative value")
        void zeroTargetAlwaysMet() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", null));

            assertTrue(criteria.isMetBy(0));
        }
    }

    @Nested
    @DisplayName("next milestone")
    class NextMilestoneTests {

        @Test
        @DisplayName("problems_solved milestones advance by threshold")
        void problemsMilestones() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("problems_solved", 100));

            assertEquals("1 problems", criteria.nextMilestone(0));
            assertEquals("10 problems", criteria.nextMilestone(1));
            assertEquals("10 problems", criteria.nextMilestone(9));
            assertEquals("50 problems", criteria.nextMilestone(10));
            assertEquals("Max milestone reached", criteria.nextMilestone(500));
        }

        @Test
        @DisplayName("submissions_made milestones advance by threshold")
        void submissionsMilestones() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("submissions_made", 1000));

            assertEquals("1 submissions", criteria.nextMilestone(0));
            assertEquals("1000 submissions", criteria.nextMilestone(999));
            assertEquals("Max milestone reached", criteria.nextMilestone(1000));
        }

        @Test
        @DisplayName("unknown type yields null milestone")
        void unknownTypeMilestoneIsNull() {
            AchievementCriteria criteria = AchievementCriteria.from(criteria("contest_participation", 1));

            assertNull(criteria.nextMilestone(5));
        }

        @Test
        @DisplayName("empty criteria yields null milestone")
        void emptyMilestoneIsNull() {
            AchievementCriteria criteria = AchievementCriteria.from(null);

            assertNull(criteria.nextMilestone(0));
        }
    }
}
