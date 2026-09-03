package com.ulticode.app.architecture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P6-APP-002: admission gate for App implementation-locality changes.
 *
 * <p>This gate is deliberately evidence-shaped rather than LOC-shaped. A
 * candidate is admissible only when deletion is safe, ownership and
 * transaction boundaries remain local, the dependency direction is sound,
 * and the existing test surface can prove the change. A new process,
 * database, RPC, Redis boundary, transaction break, or POM cycle is always a
 * hard rejection.
 */
class AppModuleSplitAdmissionGateTest {

    @Test
    void admission_requires_all_locality_and_safety_evidence() {
        Candidate accepted = new Candidate(
                "ProblemDifficultyReadPort", true, true, 2, true, true, true,
                true, false, false, false);

        assertThat(isAdmissible(accepted)).isTrue();
        assertThat(isAdmissible(accepted.withRealChangeTrigger(false))).isFalse();
        assertThat(isAdmissible(accepted.withDeletionTest(false))).isFalse();
        assertThat(isAdmissible(accepted.withConsumerCount(0))).isFalse();
        assertThat(isAdmissible(accepted.withDataTransactionOwnership(false))).isFalse();
        assertThat(isAdmissible(accepted.withDependencyDirectionSafe(false))).isFalse();
        assertThat(isAdmissible(accepted.withTestSurface(false))).isFalse();
        assertThat(isAdmissible(accepted.withStableNarrowInterface(false))).isFalse();
        assertThat(isAdmissible(accepted.withRuntimeBoundary(true))).isFalse();
        assertThat(isAdmissible(accepted.withTransactionBreak(true))).isFalse();
        assertThat(isAdmissible(accepted.withPomCycle(true))).isFalse();
    }

    @Test
    void forum_and_solution_are_no_go_without_a_real_business_change_trigger() {
        Candidate forum = candidateWithoutTrigger("Forum");
        Candidate solution = candidateWithoutTrigger("Solution");

        assertThat(isAdmissible(forum)).isFalse();
        assertThat(isAdmissible(solution)).isFalse();
    }

    private static Candidate candidateWithoutTrigger(String name) {
        return new Candidate(name, false, true, 2, true, true, true,
                true, false, false, false);
    }

    private static boolean isAdmissible(Candidate candidate) {
        return candidate.realChangeTrigger()
                && candidate.deletionTestPassed()
                && candidate.consumerCount() > 0
                && candidate.dataTransactionOwnership()
                && candidate.dependencyDirectionSafe()
                && candidate.testSurface()
                && candidate.stableNarrowInterface()
                && !candidate.introducesRuntimeBoundary()
                && !candidate.breaksTransaction()
                && !candidate.pomCycle();
    }

    private record Candidate(
            String name,
            boolean realChangeTrigger,
            boolean deletionTestPassed,
            int consumerCount,
            boolean dataTransactionOwnership,
            boolean dependencyDirectionSafe,
            boolean testSurface,
            boolean stableNarrowInterface,
            boolean introducesRuntimeBoundary,
            boolean breaksTransaction,
            boolean pomCycle) {

        private Candidate withRealChangeTrigger(boolean value) {
            return new Candidate(name, value, deletionTestPassed, consumerCount,
                    dataTransactionOwnership, dependencyDirectionSafe, testSurface,
                    stableNarrowInterface, introducesRuntimeBoundary, breaksTransaction, pomCycle);
        }

        private Candidate withDeletionTest(boolean value) {
            return new Candidate(name, realChangeTrigger, value, consumerCount,
                    dataTransactionOwnership, dependencyDirectionSafe, testSurface,
                    stableNarrowInterface, introducesRuntimeBoundary, breaksTransaction, pomCycle);
        }

        private Candidate withConsumerCount(int value) {
            return new Candidate(name, realChangeTrigger, deletionTestPassed, value,
                    dataTransactionOwnership, dependencyDirectionSafe, testSurface,
                    stableNarrowInterface, introducesRuntimeBoundary, breaksTransaction, pomCycle);
        }

        private Candidate withDataTransactionOwnership(boolean value) {
            return new Candidate(name, realChangeTrigger, deletionTestPassed, consumerCount,
                    value, dependencyDirectionSafe, testSurface, stableNarrowInterface,
                    introducesRuntimeBoundary, breaksTransaction, pomCycle);
        }

        private Candidate withDependencyDirectionSafe(boolean value) {
            return new Candidate(name, realChangeTrigger, deletionTestPassed, consumerCount,
                    dataTransactionOwnership, value, testSurface, stableNarrowInterface,
                    introducesRuntimeBoundary, breaksTransaction, pomCycle);
        }

        private Candidate withTestSurface(boolean value) {
            return new Candidate(name, realChangeTrigger, deletionTestPassed, consumerCount,
                    dataTransactionOwnership, dependencyDirectionSafe, value,
                    stableNarrowInterface, introducesRuntimeBoundary, breaksTransaction, pomCycle);
        }

        private Candidate withStableNarrowInterface(boolean value) {
            return new Candidate(name, realChangeTrigger, deletionTestPassed, consumerCount,
                    dataTransactionOwnership, dependencyDirectionSafe, testSurface, value,
                    introducesRuntimeBoundary, breaksTransaction, pomCycle);
        }

        private Candidate withRuntimeBoundary(boolean value) {
            return new Candidate(name, realChangeTrigger, deletionTestPassed, consumerCount,
                    dataTransactionOwnership, dependencyDirectionSafe, testSurface,
                    stableNarrowInterface, value, breaksTransaction, pomCycle);
        }

        private Candidate withTransactionBreak(boolean value) {
            return new Candidate(name, realChangeTrigger, deletionTestPassed, consumerCount,
                    dataTransactionOwnership, dependencyDirectionSafe, testSurface,
                    stableNarrowInterface, introducesRuntimeBoundary, value, pomCycle);
        }

        private Candidate withPomCycle(boolean value) {
            return new Candidate(name, realChangeTrigger, deletionTestPassed, consumerCount,
                    dataTransactionOwnership, dependencyDirectionSafe, testSurface,
                    stableNarrowInterface, introducesRuntimeBoundary, breaksTransaction, value);
        }
    }
}
