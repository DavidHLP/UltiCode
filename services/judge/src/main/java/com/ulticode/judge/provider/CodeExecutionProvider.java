package com.ulticode.judge.provider;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.judge.api.JudgeRunCommand;
import com.ulticode.judge.api.JudgeRunResult;
import com.ulticode.judge.api.JudgeRunService;
import com.ulticode.modules.submission.runtime.JudgeRunRequest;
import com.ulticode.modules.submission.runtime.JudgeRunResponse;
import com.ulticode.modules.submission.runtime.async.AsyncSandboxExecutor;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.TestCase;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Judge provider mapping wire commands to sync or async runtime seams. */
@DubboService(group = "backend-judge", version = "1.0.0")
public class CodeExecutionProvider implements JudgeRunService {

    private static final int MAX_ASYNC_METADATA = 1_024;
    private static final long ASYNC_METADATA_TTL_NANOS = TimeUnit.MINUTES.toNanos(15);
    private final Map<String, AsyncExecutionMetadata> activeAsyncMetadata =
            new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, AsyncExecutionMetadata> terminalAsyncMetadata =
            new LinkedHashMap<>(16, 0.75f, true);
    private final CodeExecutionService delegate;
    private final AsyncSandboxExecutor asyncExecutor;
    private final long asyncMetadataTtlNanos;
    private final LongSupplier nanoTime;

    public CodeExecutionProvider(CodeExecutionService delegate) {
        this(delegate, null);
    }

    @Autowired
    public CodeExecutionProvider(CodeExecutionService delegate,
                                 AsyncSandboxExecutor asyncExecutor) {
        this(delegate, asyncExecutor, ASYNC_METADATA_TTL_NANOS, System::nanoTime);
    }

    CodeExecutionProvider(CodeExecutionService delegate,
                          AsyncSandboxExecutor asyncExecutor,
                          long asyncMetadataTtlNanos,
                          LongSupplier nanoTime) {
        if (asyncMetadataTtlNanos < 1 || nanoTime == null) {
            throw new IllegalArgumentException("async metadata retention configuration is invalid");
        }
        this.delegate = delegate;
        this.asyncExecutor = asyncExecutor;
        this.asyncMetadataTtlNanos = asyncMetadataTtlNanos;
        this.nanoTime = nanoTime;
    }

    @Override
    public RpcResult<JudgeRunResult> execute(JudgeRunCommand command) {
        if (command == null || command.visibility() != JudgeRunCommand.Visibility.PUBLIC_PREVIEW) {
            return RpcResult.failure(BaseErrorCode.BAD_REQUEST, traceId(command));
        }
        try {
            JudgeRunResponse result = delegate.execute(
                    toRuntimeRequest(command), command.problemId(), command.userId());
            return RpcResult.success(toContractResult(result), traceId(command));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == null) {
                return RpcResult.failure(BaseErrorCode.UNKNOWN_ERROR, traceId(command));
            }
            return RpcResult.failure(exception.getErrorCode(), traceId(command));
        }
    }

    @Override
    public synchronized RpcResult<AsyncExecutionHandle> submit(JudgeRunCommand command) {
        if (!isPublic(command) || asyncExecutor == null) {
            return RpcResult.failure(BaseErrorCode.BAD_REQUEST, traceId(command));
        }
        if (command.testCases().size() != 1) {
            return RpcResult.failure(BaseErrorCode.BAD_REQUEST, traceId(command));
        }
        if (!hasAsyncMetadataCapacity(command.requestId())) {
            return RpcResult.failure(BaseErrorCode.UNKNOWN_ERROR, traceId(command));
        }
        try {
            JudgeRunRequest runtimeRequest = toRuntimeRequest(command);
            delegate.preparePreview(runtimeRequest, command.problemId());
            AsyncSandboxExecutor.ExecutionHandle handle = asyncExecutor.submit(
                    toAsyncRequest(command, runtimeRequest));
            AsyncExecutionHandle contractHandle = new AsyncExecutionHandle(handle.id());
            rememberAsyncMetadata(handle.id(), command);
            return RpcResult.success(contractHandle, traceId(command));
        } catch (BusinessException exception) {
            return RpcResult.failure(exception.getErrorCode() == null
                    ? BaseErrorCode.UNKNOWN_ERROR : exception.getErrorCode(), traceId(command));
        } catch (IllegalArgumentException exception) {
            return RpcResult.failure(BaseErrorCode.BAD_REQUEST, traceId(command));
        } catch (RuntimeException exception) {
            return RpcResult.failure(BaseErrorCode.UNKNOWN_ERROR, traceId(command));
        }
    }

    @Override
    public RpcResult<AsyncExecutionSnapshot> poll(AsyncExecutionHandle handle) {
        if (asyncExecutor == null || handle == null) {
            return RpcResult.failure(BaseErrorCode.BAD_REQUEST, "t-system");
        }
        AsyncExecutionMetadata metadata = asyncMetadata(handle.id());
        if (metadata == null) {
            return RpcResult.failure(BaseErrorCode.BAD_REQUEST, "t-system");
        }
        try {
            AsyncSandboxExecutor.ExecutionSnapshot snapshot = asyncExecutor.poll(
                    new AsyncSandboxExecutor.ExecutionHandle(handle.id()));
            State state = State.valueOf(snapshot.state().name());
            RpcResult<AsyncExecutionSnapshot> result = RpcResult.success(
                    new AsyncExecutionSnapshot(
                            state,
                            snapshot.result() == null
                                    ? null : toAsyncResult(snapshot.result(), handle.id(), metadata),
                            snapshot.error()),
                    "t-system");
            // FAILED can be a recoverable adapter state (e.g. a transient
            // Judge0 transport error keeps the token live), so only
            // definitive terminal states retire metadata; the TTL bounds
            // any handle that never reaches them.
            if (state == State.COMPLETED || state == State.CANCELLED
                    || state == State.TIMED_OUT) {
                completeAsyncMetadata(handle.id());
            }
            return result;
        } catch (IllegalArgumentException exception) {
            return RpcResult.failure(BaseErrorCode.BAD_REQUEST, "t-system");
        } catch (RuntimeException exception) {
            return RpcResult.failure(BaseErrorCode.UNKNOWN_ERROR, "t-system");
        }
    }

    @Override
    public RpcResult<Void> cancel(AsyncExecutionHandle handle) {
        if (asyncExecutor == null || handle == null) {
            return RpcResult.failure(BaseErrorCode.BAD_REQUEST, "t-system");
        }
        try {
            asyncExecutor.cancel(new AsyncSandboxExecutor.ExecutionHandle(handle.id()));
            completeAsyncMetadata(handle.id());
            return RpcResult.success("t-system");
        } catch (IllegalArgumentException exception) {
            return RpcResult.failure(BaseErrorCode.BAD_REQUEST, "t-system");
        } catch (RuntimeException exception) {
            return RpcResult.failure(BaseErrorCode.UNKNOWN_ERROR, "t-system");
        }
    }

    private static boolean isPublic(JudgeRunCommand command) {
        return command != null && command.visibility() == JudgeRunCommand.Visibility.PUBLIC_PREVIEW;
    }

    private static String traceId(JudgeRunCommand command) {
        return command == null || command.trace() == null
                || command.trace().traceId() == null
                ? "t-system" : command.trace().traceId();
    }

    private synchronized boolean hasAsyncMetadataCapacity(String requestId) {
        pruneAsyncMetadata();
        boolean knownRequest = activeAsyncMetadata.values().stream()
                .anyMatch(metadata -> metadata.requestId().equals(requestId))
                || terminalAsyncMetadata.values().stream()
                .anyMatch(metadata -> metadata.requestId().equals(requestId));
        return knownRequest || activeAsyncMetadata.size() < MAX_ASYNC_METADATA;
    }

    private synchronized void rememberAsyncMetadata(String handleId, JudgeRunCommand command) {
        JudgeRunCommand.TestCase testCase = command.testCases().get(0);
        AsyncExecutionMetadata metadata = new AsyncExecutionMetadata(
                command.requestId(), command.problemId(), command.userId(),
                testCase.id(), testCase.label(), nanoTime.getAsLong());
        AsyncExecutionMetadata existing = activeAsyncMetadata.get(handleId);
        if (existing == null) {
            existing = terminalAsyncMetadata.get(handleId);
        }
        if (existing != null && !existing.matches(metadata)) {
            throw new IllegalArgumentException("execution handle metadata conflicts");
        }
        if (existing == null) {
            activeAsyncMetadata.put(handleId, metadata);
        }
    }

    private synchronized AsyncExecutionMetadata asyncMetadata(String handleId) {
        pruneAsyncMetadata();
        AsyncExecutionMetadata metadata = activeAsyncMetadata.get(handleId);
        return metadata == null ? terminalAsyncMetadata.get(handleId) : metadata;
    }

    private synchronized void completeAsyncMetadata(String handleId) {
        AsyncExecutionMetadata metadata = activeAsyncMetadata.remove(handleId);
        if (metadata == null) {
            return;
        }
        terminalAsyncMetadata.put(handleId, metadata.completedAt(nanoTime.getAsLong()));
        while (terminalAsyncMetadata.size() > MAX_ASYNC_METADATA) {
            terminalAsyncMetadata.remove(terminalAsyncMetadata.keySet().iterator().next());
        }
    }

    private void pruneAsyncMetadata() {
        long now = nanoTime.getAsLong();
        pruneExpired(activeAsyncMetadata, now);
        pruneExpired(terminalAsyncMetadata, now);
    }

    private void pruneExpired(Map<String, AsyncExecutionMetadata> metadata, long now) {
        Iterator<Map.Entry<String, AsyncExecutionMetadata>> iterator =
                metadata.entrySet().iterator();
        while (iterator.hasNext()) {
            AsyncExecutionMetadata value = iterator.next().getValue();
            if (now - value.createdAtNanos() > asyncMetadataTtlNanos) {
                iterator.remove();
            }
        }
    }

    private AsyncSandboxExecutor.ExecutionRequest toAsyncRequest(
            JudgeRunCommand command, JudgeRunRequest preparedRequest) {
        JudgeRunRequest.TestCase source = preparedRequest.getTestCases().get(0);
        TestCase testCase = new TestCase(
                source.getId(), source.getLabel(), source.getInputs().stream()
                .map(input -> new TestCase.Input(
                        input.getId(), input.getLabel(), input.getName(),
                        input.getValue(), input.getType()))
                .toList(), source.getOutput());
        CodeExecutionService.ExecutionLimits limits =
                delegate.resolveExecutionLimits(command.problemId());
        String runId = UUID.nameUUIDFromBytes(
                ("judge-async:" + command.requestId()).getBytes(StandardCharsets.UTF_8)).toString();
        SandboxJob job = new SandboxJob(
                runId, command.userId() == null ? "" : command.userId(),
                runId, 0L, preparedRequest.getLanguage(), command.code(),
                limits.timeoutSeconds(), limits.memoryMb());
        return new AsyncSandboxExecutor.ExecutionRequest(
                job, testCase, AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW,
                command.requestId());
    }

    private static JudgeRunRequest toRuntimeRequest(JudgeRunCommand command) {
        JudgeRunRequest request = new JudgeRunRequest();
        request.setLanguage(command.language());
        request.setCode(command.code());
        request.setTestCases(command.testCases().stream()
                .map(CodeExecutionProvider::toRuntimeCase)
                .toList());
        return request;
    }

    private static JudgeRunRequest.TestCase toRuntimeCase(
            JudgeRunCommand.TestCase testCase) {
        JudgeRunRequest.TestCase result = new JudgeRunRequest.TestCase();
        result.setId(testCase.id());
        result.setLabel(testCase.label());
        result.setOutput(testCase.expectedOutput());
        result.setInputs(testCase.inputs().stream()
                .map(input -> {
                    JudgeRunRequest.Input value = new JudgeRunRequest.Input();
                    value.setId(input.id());
                    value.setLabel(input.label());
                    value.setName(input.name());
                    value.setValue(input.value());
                    value.setType(input.type());
                    return value;
                })
                .toList());
        return result;
    }

    private static JudgeRunResult toContractResult(JudgeRunResponse result) {
        List<JudgeRunResult.CaseResult> cases = result.getCases() == null
                ? List.of()
                : result.getCases().stream()
                .map(CodeExecutionProvider::toContractCase)
                .toList();
        return new JudgeRunResult(
                result.getId(), result.getProblemId(), result.getUserId(), result.getVerdict(),
                result.getRuntime(), result.getMemory(), result.getRuntimeMs(), result.getMemoryMb(),
                result.getRuntimeUs(), result.getCpuMs(), cases, result.getPassedCases(),
                result.getTotalCases(), result.getErrorMessage());
    }

    private static JudgeRunResult toAsyncResult(
            RunCaseResult result, String handleId, AsyncExecutionMetadata metadata) {
        String wireStatus = result.status().wireValue();
        JudgeRunResult.CaseResult caseResult = new JudgeRunResult.CaseResult(
                null, handleId, null, metadata.testCaseId(), metadata.caseLabel(), wireStatus,
                result.elapsedMs() + "ms", (result.memoryBytes() / 1_048_576.0) + "MB",
                result.elapsedMs(), result.memoryBytes() / 1_048_576.0,
                result.elapsedUs(), result.cpuMs(), result.detail(), result.output(),
                result.expectedOutput(), result.inputs() == null ? List.of() : result.inputs().stream()
                .map(input -> new JudgeRunResult.InputParam(
                        input.id(), input.label(), input.name(), input.value()))
                .toList());
        return new JudgeRunResult(
                handleId, metadata.problemId(), metadata.userId(), wireStatus,
                result.elapsedMs() + "ms", (result.memoryBytes() / 1_048_576.0) + "MB",
                result.elapsedMs(), result.memoryBytes() / 1_048_576.0, result.elapsedUs(),
                result.cpuMs(), List.of(caseResult),
                result.status() == com.ulticode.domain.submission.enums.SubmissionStatus.ACCEPTED ? 1 : 0,
                1, result.detail());
    }

    private static JudgeRunResult.CaseResult toContractCase(
            JudgeRunResponse.RunCaseResult result) {
        List<JudgeRunResult.InputParam> inputs = result.getInputs() == null
                ? List.of()
                : result.getInputs().stream()
                .map(input -> new JudgeRunResult.InputParam(
                        input.getId(), input.getLabel(), input.getName(), input.getValue()))
                .toList();
        return new JudgeRunResult.CaseResult(
                result.getId(), result.getRunId(), result.getSubmissionTestId(),
                result.getTestCaseId(), result.getCaseLabel(), result.getStatus(),
                result.getRuntime(), result.getMemory(), result.getRuntimeMs(),
                result.getMemoryMb(), result.getRuntimeUs(), result.getCpuMs(),
                result.getDetail(), result.getOutput(), result.getExpectedOutput(), inputs);
    }

    private record AsyncExecutionMetadata(
            String requestId, Long problemId, String userId, String testCaseId, String caseLabel,
            long createdAtNanos) {
        private boolean matches(AsyncExecutionMetadata other) {
            return requestId.equals(other.requestId)
                    && java.util.Objects.equals(problemId, other.problemId)
                    && java.util.Objects.equals(userId, other.userId)
                    && testCaseId.equals(other.testCaseId)
                    && java.util.Objects.equals(caseLabel, other.caseLabel);
        }

        private AsyncExecutionMetadata completedAt(long now) {
            return new AsyncExecutionMetadata(
                    requestId, problemId, userId, testCaseId, caseLabel, now);
        }
    }
}
