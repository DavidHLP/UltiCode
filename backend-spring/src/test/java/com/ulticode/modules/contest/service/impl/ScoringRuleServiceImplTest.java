package com.ulticode.modules.contest.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.contest.dto.CreateScoringRuleDTO;
import com.ulticode.modules.contest.dto.ScoringRuleVO;
import com.ulticode.modules.contest.dto.UpdateScoringRuleDTO;
import com.ulticode.modules.contest.entity.ScoringRule;
import com.ulticode.modules.contest.mapper.ScoringRuleMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ScoringRuleServiceImpl}.
 *
 * <p>These tests lock down two previously-fixed bugs:
 *
 * <ul>
 *   <li><b>BUG-1</b>: Resource-not-found must throw {@link ErrorCode#SCORING_RULE_NOT_FOUND},
 *       never {@code CONTEST_NOT_FOUND} (which misleads the frontend with the wrong entity name).
 *   <li><b>BUG-2</b>: {@code update()} must refresh {@code updatedAt} on every PUT, even though
 *       MyBatis-Plus {@code strictUpdateFill} only fires when the field is {@code null} (and
 *       {@code selectById} has already populated it).
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ScoringRuleServiceImpl")
class ScoringRuleServiceImplTest {

    private static final String RULE_ID = "rule-1234";
    private static final String MISSING_ID = "rule-does-not-exist";

    @Mock
    private ScoringRuleMapper scoringRuleMapper;
    @Mock
    private Clock clock;

    private ScoringRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(java.time.ZoneId.systemDefault());
        service = new ScoringRuleServiceImpl(scoringRuleMapper, clock);
    }

    // ----- helpers ----------------------------------------------------------

    private static ScoringRule stubRule(String id, String name, boolean isActive) {
        ScoringRule rule = new ScoringRule();
        rule.setId(id);
        rule.setName(name);
        rule.setIsActive(isActive);
        rule.setIsDefault(false);
        rule.setBaseScorePerProblem(100);
        rule.setTimeBonusPerMinute(2);
        rule.setWrongAnswerPenalty(50);
        rule.setTimeLimitPenalty(0);
        rule.setFirstSolveBonus(50);
        rule.setFullScoreBonus(100);
        rule.setCreatedAt(LocalDateTime.of(2026, 6, 9, 12, 0, 0));
        rule.setUpdatedAt(LocalDateTime.of(2026, 6, 9, 12, 0, 0));
        return rule;
    }

    // ======================================================================
    // findAll
    // ======================================================================

    @Nested
    @DisplayName("findAll(boolean includeInactive)")
    class FindAllTests {

        @Test
        @DisplayName("includeInactive=true → mapper.findAllOrdered()")
        void findAll_includeInactive_usesFindAllOrdered() {
            when(scoringRuleMapper.findAllOrdered())
                    .thenReturn(List.of(stubRule(RULE_ID, "A", true), stubRule("rule-2", "B", false)));
            when(scoringRuleMapper.countContestsUsingRule(any())).thenReturn(0L);

            List<ScoringRuleVO> result = service.findAll(true);

            assertThat(result).hasSize(2).extracting(ScoringRuleVO::getId).contains(RULE_ID, "rule-2");
            verify(scoringRuleMapper).findAllOrdered();
            verify(scoringRuleMapper, never()).findActive();
        }

        @Test
        @DisplayName("includeInactive=false → mapper.findActive()")
        void findAll_excludeInactive_usesFindActive() {
            when(scoringRuleMapper.findActive()).thenReturn(List.of(stubRule(RULE_ID, "A", true)));
            when(scoringRuleMapper.countContestsUsingRule(any())).thenReturn(0L);

            List<ScoringRuleVO> result = service.findAll(false);

            assertThat(result).hasSize(1);
            verify(scoringRuleMapper).findActive();
            verify(scoringRuleMapper, never()).findAllOrdered();
        }
    }

    // ======================================================================
    // findById  -- locks BUG-1
    // ======================================================================

    @Nested
    @DisplayName("findById(String)")
    class FindByIdTests {

        @Test
        @DisplayName("existing id → returns VO populated with contestCount")
        void findById_existing_returnsVO() {
            when(scoringRuleMapper.selectById(RULE_ID)).thenReturn(stubRule(RULE_ID, "A", true));
            when(scoringRuleMapper.countContestsUsingRule(RULE_ID)).thenReturn(3L);

            ScoringRuleVO vo = service.findById(RULE_ID);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(RULE_ID);
            assertThat(vo.getName()).isEqualTo("A");
            assertThat(vo.getContestCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("missing id → throws SCORING_RULE_NOT_FOUND (BUG-1 lock)")
        void findById_missing_throwsScoringRuleNotFound() {
            when(scoringRuleMapper.selectById(MISSING_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.findById(MISSING_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SCORING_RULE_NOT_FOUND);
        }
    }

    // ======================================================================
    // create
    // ======================================================================

    @Nested
    @DisplayName("create(CreateScoringRuleDTO)")
    class CreateTests {

        @Test
        @DisplayName("isDefault=true → calls clearDefault() before insert")
        void create_isDefaultTrue_clearsExistingDefaults() {
            CreateScoringRuleDTO dto = new CreateScoringRuleDTO();
            dto.setName("Default Rule");
            dto.setBaseScorePerProblem(100);
            dto.setTimeBonusPerMinute(2);
            dto.setWrongAnswerPenalty(50);
            dto.setFirstSolveBonus(50);
            dto.setIsDefault(true);

            when(scoringRuleMapper.clearDefault()).thenReturn(1);

            ScoringRuleVO vo = service.create(dto);

            verify(scoringRuleMapper, times(1)).clearDefault();
            verify(scoringRuleMapper, times(1)).insert(any(ScoringRule.class));
            assertThat(vo.getIsActive()).isTrue();
            assertThat(vo.getName()).isEqualTo("Default Rule");
        }

        @Test
        @DisplayName("isActive left null in DTO → service defaults it to true")
        void create_isActiveNull_defaultsToTrue() {
            CreateScoringRuleDTO dto = new CreateScoringRuleDTO();
            dto.setName("Rule");
            dto.setBaseScorePerProblem(100);
            dto.setTimeBonusPerMinute(2);
            dto.setWrongAnswerPenalty(50);
            dto.setFirstSolveBonus(50);

            ScoringRuleVO vo = service.create(dto);

            verify(scoringRuleMapper, never()).clearDefault();
            assertThat(vo.getIsActive()).isTrue();
        }
    }

    // ======================================================================
    // update  -- locks BUG-2
    // ======================================================================

    @Nested
    @DisplayName("update(String, UpdateScoringRuleDTO)")
    class UpdateTests {

        @Test
        @DisplayName("existing id → updatedAt is refreshed to a value AFTER the original (BUG-2 lock)")
        void update_existing_refreshesUpdatedAt() throws InterruptedException {
            // Arrange: existing record with an old updatedAt (simulating selectById result)
            LocalDateTime oldUpdatedAt = LocalDateTime.of(2026, 6, 9, 12, 0, 0);
            ScoringRule existing = stubRule(RULE_ID, "Old", true);
            existing.setUpdatedAt(oldUpdatedAt);
            when(scoringRuleMapper.selectById(RULE_ID)).thenReturn(existing);
            when(scoringRuleMapper.countContestsUsingRule(RULE_ID)).thenReturn(0L);

            UpdateScoringRuleDTO dto = new UpdateScoringRuleDTO();
            dto.setName("New");
            dto.setDescription("updated");
            dto.setBaseScorePerProblem(150);
            dto.setTimeBonusPerMinute(5);
            dto.setWrongAnswerPenalty(30);
            dto.setFirstSolveBonus(80);
            dto.setFullScoreBonus(120);
            dto.setIsDefault(true);

            // Act
            Thread.sleep(5);  // ensure clock moves forward
            ScoringRuleVO vo = service.update(RULE_ID, dto);

            // Assert: returned VO must have updatedAt > old value (regression lock for BUG-2)
            assertThat(vo).isNotNull();
            assertThat(vo.getUpdatedAt()).isAfter(oldUpdatedAt);
            assertThat(vo.getName()).isEqualTo("New");
            assertThat(vo.getIsDefault()).isTrue();
            verify(scoringRuleMapper, times(1)).clearDefault();
            verify(scoringRuleMapper, times(1)).updateById(any(ScoringRule.class));
        }

        @Test
        @DisplayName("missing id → throws SCORING_RULE_NOT_FOUND (BUG-1 lock)")
        void update_missing_throwsScoringRuleNotFound() {
            when(scoringRuleMapper.selectById(MISSING_ID)).thenReturn(null);

            UpdateScoringRuleDTO dto = new UpdateScoringRuleDTO();
            dto.setName("X");
            dto.setBaseScorePerProblem(100);
            dto.setTimeBonusPerMinute(2);
            dto.setWrongAnswerPenalty(50);
            dto.setFirstSolveBonus(50);

            assertThatThrownBy(() -> service.update(MISSING_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SCORING_RULE_NOT_FOUND);

            verify(scoringRuleMapper, never()).updateById(any(ScoringRule.class));
        }
    }

    // ======================================================================
    // delete  -- locks BUG-1 + in-use guard
    // ======================================================================

    @Nested
    @DisplayName("delete(String)")
    class DeleteTests {

        @Test
        @DisplayName("existing + not in use → calls mapper.deleteById")
        void delete_existingAndNotInUse_deletes() {
            when(scoringRuleMapper.selectById(RULE_ID)).thenReturn(stubRule(RULE_ID, "A", true));
            when(scoringRuleMapper.countContestsUsingRule(RULE_ID)).thenReturn(0L);

            service.delete(RULE_ID);

            verify(scoringRuleMapper, times(1)).deleteById(RULE_ID);
        }

        @Test
        @DisplayName("existing + in use by contests → throws BAD_REQUEST, no delete")
        void delete_inUse_throwsBadRequest() {
            when(scoringRuleMapper.selectById(RULE_ID)).thenReturn(stubRule(RULE_ID, "A", true));
            when(scoringRuleMapper.countContestsUsingRule(RULE_ID)).thenReturn(2L);

            assertThatThrownBy(() -> service.delete(RULE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BAD_REQUEST);

            verify(scoringRuleMapper, never()).deleteById(any(String.class));
        }

        @Test
        @DisplayName("missing id → throws SCORING_RULE_NOT_FOUND (BUG-1 lock)")
        void delete_missing_throwsScoringRuleNotFound() {
            when(scoringRuleMapper.selectById(MISSING_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.delete(MISSING_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SCORING_RULE_NOT_FOUND);

            verify(scoringRuleMapper, never()).deleteById(any(String.class));
            verify(scoringRuleMapper, never()).countContestsUsingRule(any(String.class));
        }
    }
}
