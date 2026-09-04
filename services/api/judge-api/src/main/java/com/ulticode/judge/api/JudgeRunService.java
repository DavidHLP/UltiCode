package com.ulticode.judge.api;

import com.ulticode.common.rpc.RpcResult;
import java.io.Serializable;

/** Cross-process Judge execution seam for public preview runs. */
public interface JudgeRunService {

    /** Executes only caller-supplied public cases; hidden cases never cross this seam. */
    RpcResult<JudgeRunResult> execute(JudgeRunCommand command);
    RpcResult<AsyncExecutionHandle> submit(JudgeRunCommand command);

    RpcResult<AsyncExecutionSnapshot> poll(AsyncExecutionHandle handle);

    /**
     * Requests cancellation. On a failed cancellation response, callers must
     * continue polling until a terminal state is observed.
     */
    RpcResult<Void> cancel(AsyncExecutionHandle handle);

    record AsyncExecutionHandle(String id) implements Serializable {
        public AsyncExecutionHandle {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("execution handle id is required");
            }
        }
    }

    record AsyncExecutionSnapshot(
            State state, JudgeRunResult result, String error) implements java.io.Serializable {
        public AsyncExecutionSnapshot {
            if (state == null) {
                throw new IllegalArgumentException("execution state is required");
            }
        }
    }

    enum State {
        QUEUED,
        RUNNING,
        COMPLETED,
        CANCELLED,
        TIMED_OUT,
        FAILED
    }
}
