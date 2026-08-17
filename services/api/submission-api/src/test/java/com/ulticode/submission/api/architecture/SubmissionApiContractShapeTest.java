package com.ulticode.submission.api.architecture;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.submission.api.command.BatchRejudgeCommand;
import com.ulticode.submission.api.command.RejudgeCommand;
import com.ulticode.submission.api.dto.BatchRejudgeResultDTO;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.submission.api.dto.SubmissionAdminRowDTO;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.api.dto.SubmissionResultPayload;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import com.ulticode.submission.api.dto.SubmissionTestCaseDetailDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.dto.RejudgeResult;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import com.ulticode.submission.api.event.SubmissionLifecycleEventContract;
import com.ulticode.submission.api.service.ProblemSubmissionStatsPort;
import com.ulticode.submission.api.service.RejudgePolicy;
import com.ulticode.submission.api.service.SubmissionActivityAnalyticsPort;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import com.ulticode.submission.api.service.SubmissionAdministrationService;
import com.ulticode.submission.api.service.SubmissionAnalyticsPort;
import com.ulticode.submission.api.service.SubmissionFencePort;
import com.ulticode.submission.api.service.SubmissionGenerationReadPort;
import com.ulticode.submission.api.service.SubmissionReadPort;
import com.ulticode.submission.api.service.SubmissionStreakPort;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import com.ulticode.submission.api.service.SubmissionWritePort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionApiContractShapeTest {

    private static final List<Class<?>> CONTRACTS = List.of(
            RejudgePolicy.class,
            SubmissionActivityAnalyticsPort.class,
            SubmissionAdminReadPort.class,
            SubmissionAdministrationService.class,
            SubmissionAnalyticsPort.class,
            SubmissionFencePort.class,
            SubmissionGenerationReadPort.class,
            SubmissionReadPort.class,
            SubmissionStreakPort.class,
            SubmissionUserQueryPort.class,
            SubmissionUserStatsPort.class,
            SubmissionWritePort.class);

    @Test
    void submissionContractsAreInTheProviderOwnedNamespace() {
        assertThat(CONTRACTS)
                .allSatisfy(type -> assertThat(type.getPackageName())
                        .startsWith("com.ulticode.submission.api"));
        assertThat(List.of(
                BatchRejudgeCommand.class, RejudgeCommand.class,
                BatchRejudgeResultDTO.class, CreateSubmissionDTO.class,
                SubmissionAdminQueryDTO.class, SubmissionAdminRowDTO.class,
                SubmissionDetailVO.class, SubmissionHistoryDTO.class,
                SubmissionListItemVO.class, SubmissionQueryDTO.class,
                SubmissionResultPayload.class, SubmissionStatusMeta.class,
                SubmissionTestCaseDetailDTO.class, SubmissionVO.class,
                RejudgeResult.class, RejudgeResultDTO.class,
                SubmissionLifecycleEventContract.class))
                .allSatisfy(type -> assertThat(type.getPackageName())
                        .startsWith("com.ulticode.submission.api"));
    }

    @Test
    void rejudgeCommandsKeepTheExistingMetadataAndValidationShape() {
        ActorDelegation actor = new ActorDelegation(
                "ADMIN", "admin-1", "admin-1", "contract-test");
        RejudgeCommand command = new RejudgeCommand(
                "command-1", IdMetadata.mint(), actor,
                new TraceMetadata("trace-1", null, null, null),
                "submission-1", true);
        BatchRejudgeCommand batch = new BatchRejudgeCommand(
                "command-2", IdMetadata.mint(), actor,
                new TraceMetadata("trace-2", null, null, null),
                List.of("submission-1", "submission-2"), false);

        assertThat(command.commandId()).isEqualTo("command-1");
        assertThat(command.submissionId()).isEqualTo("submission-1");
        assertThat(batch.submissionIds()).containsExactly(
                "submission-1", "submission-2");
        assertThat(batch).isInstanceOf(com.ulticode.common.command.WriteCommand.class);
    }

    @Test
    void serviceMethodsDoNotExposeImplementationTypes() {
        for (Class<?> contract : CONTRACTS) {
            for (Method method : contract.getDeclaredMethods()) {
                assertThat(method.getReturnType().getName())
                        .as("return type of %s#%s", contract.getName(), method.getName())
                        .doesNotStartWith("com.ulticode.modules.");
                assertThat(Arrays.stream(method.getParameterTypes())
                        .map(Class::getName).toList())
                        .as("parameter types of %s#%s", contract.getName(), method.getName())
                        .noneMatch(type -> type.startsWith("com.ulticode.modules."));
            }
        }
    }

    @Test
    void lifecycleWireContractKeepsVersionOwnerAndSensitiveFieldGuard() {
        assertThat(SubmissionLifecycleEventContract.SCHEMA_VERSION).isEqualTo(1);
        assertThat(SubmissionLifecycleEventContract.OWNER).isEqualTo("Submission");
        assertThat(SubmissionLifecycleEventContract.CREATED_FIELDS)
                .contains("submissionId", "userId", "problemId", "language");
        assertThat(SubmissionLifecycleEventContract.FORBIDDEN_FIELDS)
                .contains("code", "hiddenTestCases", "accessToken", "refreshToken", "password")
                .doesNotContain("verdict", "language");
    }
}
