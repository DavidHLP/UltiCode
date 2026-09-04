package com.ulticode.modules.submission.port.adapter;

import com.ulticode.modules.submission.controller.RunResultDTO;
import com.ulticode.modules.submission.controller.RunSubmissionDTO;
import com.ulticode.app.error.ProblemErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.judge.api.JudgeRunCommand;
import com.ulticode.judge.api.JudgeRunResult;
import com.ulticode.judge.api.JudgeRunService;
import com.ulticode.modules.submission.port.InteractiveCodeRunner;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** App Adapter mapping HTTP run DTOs to the Judge-owned execution contract. */
@Component
@Primary
public class RemoteCodeExecutionPort implements InteractiveCodeRunner {

    @DubboReference(group = "backend-judge", version = "1.0.0",
            timeout = RpcPolicy.EXECUTION_TIMEOUT_MS, retries = RpcPolicy.EXECUTION_RETRIES,
            check = false)
    private JudgeRunService judgeExecution;

    @Override
    public RunResultDTO run(RunSubmissionDTO request, Long problemId, String userId) {
        if (problemId == null || problemId < 1 || request == null) {
            throw new BusinessException(ProblemErrorCode.CODE_EXECUTION_INVALID_REQUEST);
        }
        validateNestedRequest(request);
        try {
            if (judgeExecution == null) {
                throw new BusinessException(ProblemErrorCode.CODE_EXECUTION_UNAVAILABLE);
            }
            RpcResult<JudgeRunResult> result = judgeExecution.execute(toJudgeCommand(
                    request, problemId, userId));
            if (result == null || !result.success() || result.data() == null) {
                throw mapFailure(result);
            }
            return toAppResult(result.data());
        } catch (RpcException exception) {
            throw new BusinessException(ProblemErrorCode.CODE_EXECUTION_UNAVAILABLE, exception);
        }
    }
    private static BusinessException mapFailure(RpcResult<?> result) {
        if (result != null && result.error() != null) {
            int code = result.error().code();
            if (code == BaseErrorCode.BAD_REQUEST.code()
                    || code == BaseErrorCode.VALIDATION_FAILED.code()
                    || code == ProblemErrorCode.CODE_EXECUTION_INVALID_REQUEST.code()) {
                return new BusinessException(ProblemErrorCode.CODE_EXECUTION_INVALID_REQUEST);
            }
        }
        return new BusinessException(ProblemErrorCode.CODE_EXECUTION_UNAVAILABLE);
    }

    private static void validateNestedRequest(RunSubmissionDTO request) {
        if (request.getTestCases() == null) {
            return;
        }
        for (RunSubmissionDTO.RunTestCase testCase : request.getTestCases()) {
            if (testCase == null || testCase.getId() == null || testCase.getId().isBlank()) {
                throw new BusinessException(ProblemErrorCode.CODE_EXECUTION_INVALID_REQUEST);
            }
            if (testCase.getInputs() != null
                    && testCase.getInputs().stream().anyMatch(java.util.Objects::isNull)) {
                throw new BusinessException(ProblemErrorCode.CODE_EXECUTION_INVALID_REQUEST);
            }
        }
    }

    private static JudgeRunCommand toJudgeCommand(
            RunSubmissionDTO request, Long problemId, String userId) {
        List<JudgeRunCommand.TestCase> cases = request.getTestCases() == null
                ? List.of()
                : request.getTestCases().stream()
                .map(RemoteCodeExecutionPort::toJudgeCase)
                .toList();
        String traceId = TraceIdUtil.current();
        return new JudgeRunCommand(
                UUID.randomUUID().toString(), problemId, userId,
                request.getLanguage(), request.getCode(), cases,
                new TraceMetadata(traceId, null, null, null),
                JudgeRunCommand.Visibility.PUBLIC_PREVIEW);
    }

    private static JudgeRunCommand.TestCase toJudgeCase(RunSubmissionDTO.RunTestCase testCase) {
        List<JudgeRunCommand.Input> inputs = testCase.getInputs() == null
                ? List.of()
                : testCase.getInputs().stream()
                .map(input -> new JudgeRunCommand.Input(
                        input.getId(), input.getLabel(), input.getName(),
                        input.getValue(), input.getType()))
                .toList();
        return new JudgeRunCommand.TestCase(
                testCase.getId() == null ? UUID.randomUUID().toString() : testCase.getId(),
                testCase.getLabel(), testCase.getOutput(), inputs);
    }

    private static RunResultDTO toAppResult(JudgeRunResult result) {
        return RunResultDTO.builder()
                .id(result.id())
                .problemId(result.problemId())
                .userId(result.userId())
                .verdict(result.verdict())
                .runtime(result.runtime())
                .memory(result.memory())
                .runtimeMs(result.runtimeMs())
                .memoryMb(result.memoryMb())
                .runtimeUs(result.runtimeUs())
                .cpuMs(result.cpuMs())
                .cases(result.cases().stream()
                        .map(RemoteCodeExecutionPort::toAppCase)
                        .toList())
                .passedCases(result.passedCases())
                .totalCases(result.totalCases())
                .errorMessage(result.errorMessage())
                .build();
    }

    private static RunResultDTO.RunCaseResult toAppCase(JudgeRunResult.CaseResult result) {
        return RunResultDTO.RunCaseResult.builder()
                .id(result.id())
                .runId(result.runId())
                .submissionTestId(result.submissionTestId())
                .testCaseId(result.testCaseId())
                .caseLabel(result.caseLabel())
                .status(result.status())
                .runtime(result.runtime())
                .memory(result.memory())
                .runtimeMs(result.runtimeMs())
                .memoryMb(result.memoryMb())
                .runtimeUs(result.runtimeUs())
                .cpuMs(result.cpuMs())
                .detail(result.detail())
                .output(result.output())
                .expectedOutput(result.expectedOutput())
                .inputs(result.inputs().stream()
                        .map(input -> RunResultDTO.RunCaseResult.InputParam.builder()
                                .id(input.id())
                                .label(input.label())
                                .name(input.name())
                                .value(input.value())
                                .build())
                        .toList())
                .build();
    }
}
