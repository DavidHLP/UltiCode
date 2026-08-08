package com.ulticode.modules.submission.fence;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SubmissionStateMachine} (ADR-003 §2.5). Pure-function
 * checks over the full transition matrix; no Spring context, no DB.
 */
@DisplayName("SubmissionStateMachine")
class SubmissionStateMachineTest {

    @Nested
    @DisplayName("canSystemTransition()")
    class CanSystemTransition {

        @Test
        @DisplayName("Pending -> Judging is allowed")
        void pendingToJudging() {
            assertThat(SubmissionStateMachine.canSystemTransition(
                    SubmissionStatus.PENDING, SubmissionStatus.JUDGING)).isTrue();
        }

        @Test
        @DisplayName("Pending -> System Error is allowed (infra failure before judging)")
        void pendingToSystemError() {
            assertThat(SubmissionStateMachine.canSystemTransition(
                    SubmissionStatus.PENDING, SubmissionStatus.SYSTEM_ERROR)).isTrue();
        }

        @Test
        @DisplayName("Judging -> any terminal verdict is allowed")
        void judgingToAnyTerminal() {
            for (SubmissionStatus terminal : SubmissionStatus.values()) {
                if (terminal == SubmissionStatus.PENDING || terminal == SubmissionStatus.JUDGING) {
                    continue;
                }
                assertThat(SubmissionStateMachine.canSystemTransition(
                        SubmissionStatus.JUDGING, terminal))
                        .as("Judging -> %s", terminal)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("Judging -> Pending is allowed (lease-expiry reaper path)")
        void judgingToPending() {
            assertThat(SubmissionStateMachine.canSystemTransition(
                    SubmissionStatus.JUDGING, SubmissionStatus.PENDING)).isTrue();
        }

        @Test
        @DisplayName("Terminal statuses have no outgoing system transitions")
        void terminalsHaveNoOutgoingSystemEdges() {
            SubmissionStatus[] terminals = {
                    SubmissionStatus.ACCEPTED,
                    SubmissionStatus.WRONG_ANSWER,
                    SubmissionStatus.TIME_LIMIT_EXCEEDED,
                    SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                    SubmissionStatus.OUTPUT_LIMIT_EXCEEDED,
                    SubmissionStatus.RUNTIME_ERROR,
                    SubmissionStatus.COMPILE_ERROR,
                    SubmissionStatus.PRESENTATION_ERROR,
                    SubmissionStatus.SANDBOX_ERROR,
                    SubmissionStatus.SYSTEM_ERROR
            };
            for (SubmissionStatus terminal : terminals) {
                for (SubmissionStatus target : SubmissionStatus.values()) {
                    assertThat(SubmissionStateMachine.canSystemTransition(terminal, target))
                            .as("%s -> %s must be forbidden on system channel", terminal, target)
                            .isFalse();
                }
            }
        }

        @Test
        @DisplayName("Pending -> Pending is forbidden (no self-loop)")
        void pendingToPendingForbidden() {
            assertThat(SubmissionStateMachine.canSystemTransition(
                    SubmissionStatus.PENDING, SubmissionStatus.PENDING)).isFalse();
        }

        @Test
        @DisplayName("null inputs return false (defensive)")
        void nullInputsReturnFalse() {
            assertThat(SubmissionStateMachine.canSystemTransition(null, SubmissionStatus.JUDGING)).isFalse();
            assertThat(SubmissionStateMachine.canSystemTransition(SubmissionStatus.PENDING, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("canAdminRejudgeFrom()")
    class CanAdminRejudgeFrom {

        @Test
        @DisplayName("every terminal status is rejudgeable")
        void allTerminalsRejudgeable() {
            SubmissionStatus[] terminals = {
                    SubmissionStatus.ACCEPTED,
                    SubmissionStatus.PRESENTATION_ERROR,
                    SubmissionStatus.WRONG_ANSWER,
                    SubmissionStatus.TIME_LIMIT_EXCEEDED,
                    SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                    SubmissionStatus.OUTPUT_LIMIT_EXCEEDED,
                    SubmissionStatus.RUNTIME_ERROR,
                    SubmissionStatus.COMPILE_ERROR,
                    SubmissionStatus.SANDBOX_ERROR,
                    SubmissionStatus.SYSTEM_ERROR
            };
            for (SubmissionStatus terminal : terminals) {
                assertThat(SubmissionStateMachine.canAdminRejudgeFrom(terminal))
                        .as("%s must be rejudgeable", terminal)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("Pending is NOT admin-rejudgeable (no terminal to escape)")
        void pendingNotRejudgeable() {
            assertThat(SubmissionStateMachine.canAdminRejudgeFrom(SubmissionStatus.PENDING)).isFalse();
        }

        @Test
        @DisplayName("Judging is NOT in the admin-rejudge set (handled by force-lease-expiry path)")
        void judgingNotRejudgeable() {
            assertThat(SubmissionStateMachine.canAdminRejudgeFrom(SubmissionStatus.JUDGING)).isFalse();
        }

        @Test
        @DisplayName("null returns false (defensive)")
        void nullReturnsFalse() {
            assertThat(SubmissionStateMachine.canAdminRejudgeFrom(null)).isFalse();
        }
    }
}
