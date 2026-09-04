package com.ulticode.modules.submission.port.adapter;

import com.ulticode.modules.submission.controller.RunResultDTO;
import com.ulticode.modules.submission.controller.RunSubmissionDTO;
import com.ulticode.app.error.ProblemErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.judge.api.JudgeRunCommand;
import com.ulticode.judge.api.JudgeRunResult;
import com.ulticode.judge.api.JudgeRunService;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class RemoteCodeExecutionPortTest {

    private JudgeRunService judge;
    private RemoteCodeExecutionPort adapter;

    @BeforeEach
    void setUp() {
        judge = mock(JudgeRunService.class);
        adapter = new RemoteCodeExecutionPort();
        ReflectionTestUtils.setField(adapter, "judgeExecution", judge);
    }

    @Test
    void mapsAppRequestToJudgeContractAndBack() {
        RunSubmissionDTO request = new RunSubmissionDTO();
        request.setLanguage("python");
        request.setCode("print('ok')");
        RunSubmissionDTO.RunTestCase testCase = new RunSubmissionDTO.RunTestCase();
        testCase.setId("case-1");
        testCase.setOutput("ok");
        request.setTestCases(List.of(testCase));
        RunResultDTO expected = RunResultDTO.builder()
                .id("run-1").problemId(42L).userId("user-1")
                .verdict("Accepted").cases(List.of()).build();
        when(judge.execute(any(JudgeRunCommand.class)))
                .thenReturn(RpcResult.success(toJudgeResult(expected), "trace-1"));

        assertThat(adapter.run(request, 42L, "user-1").getId()).isEqualTo("run-1");
        ArgumentCaptor<JudgeRunCommand> captor =
                ArgumentCaptor.forClass(JudgeRunCommand.class);
        verify(judge).execute(captor.capture());
        assertThat(captor.getValue().trace().hasTraceId()).isTrue();
    }

    @Test
    void mapsTransportFailureToTypedUnavailableError() {
        RunSubmissionDTO request = new RunSubmissionDTO();
        request.setLanguage("python");
        request.setCode("print('ok')");
        when(judge.execute(any(JudgeRunCommand.class)))
                .thenThrow(new RpcException("offline"));

        assertThatThrownBy(() -> adapter.run(request, 42L, "user-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ProblemErrorCode.CODE_EXECUTION_UNAVAILABLE));
    }
    @Test
    void missingJudgeReferenceFailsClosed() {
        ReflectionTestUtils.setField(adapter, "judgeExecution", null);

        assertThatThrownBy(() -> adapter.run(new RunSubmissionDTO(), 42L, "user-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ProblemErrorCode.CODE_EXECUTION_UNAVAILABLE));
    }
    @Test
    void invalidProblemIdFailsBeforeRemoteCall() {
        assertThatThrownBy(() -> adapter.run(new RunSubmissionDTO(), 0L, "user-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ProblemErrorCode.CODE_EXECUTION_INVALID_REQUEST));
    }

    @Test
    void rejectsNullNestedInputBeforeRemoteCall() {
        RunSubmissionDTO request = new RunSubmissionDTO();
        request.setLanguage("python");
        request.setCode("print('ok')");
        RunSubmissionDTO.RunTestCase testCase = new RunSubmissionDTO.RunTestCase();
        testCase.setId("case-1");
        testCase.setInputs(java.util.Collections.singletonList(null));
        request.setTestCases(List.of(testCase));

        assertThatThrownBy(() -> adapter.run(request, 42L, "user-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ProblemErrorCode.CODE_EXECUTION_INVALID_REQUEST));
        verify(judge, never()).execute(any(JudgeRunCommand.class));
    }
    @Test
    void mapsJudgeValidationFailureToBadRequest() {
        when(judge.execute(any(JudgeRunCommand.class)))
                .thenReturn(RpcResult.failure(BaseErrorCode.BAD_REQUEST, "invalid"));

        RunSubmissionDTO request = new RunSubmissionDTO();
        request.setLanguage("python");
        request.setCode("print('ok')");
        assertThatThrownBy(() -> adapter.run(request, 42L, "user-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ProblemErrorCode.CODE_EXECUTION_INVALID_REQUEST));
    }



    private static JudgeRunResult toJudgeResult(RunResultDTO result) {
        return new JudgeRunResult(
                result.getId(), result.getProblemId(), result.getUserId(), result.getVerdict(),
                result.getRuntime(), result.getMemory(), result.getRuntimeMs(), result.getMemoryMb(),
                result.getRuntimeUs(), result.getCpuMs(), List.of(), result.getPassedCases(),
                result.getTotalCases(), result.getErrorMessage());
    }
}
