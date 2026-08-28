package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.dto.RunSubmissionDTO;
import com.ulticode.app.api.service.CodeExecutionPort;
import com.ulticode.app.error.ProblemErrorCode;
import com.ulticode.common.exception.BusinessException;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteCodeExecutionPortTest {

    private final CodeExecutionPort judge = mock(CodeExecutionPort.class);
    private final RemoteCodeExecutionPort adapter = new RemoteCodeExecutionPort();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "judgeExecution", judge);
    }

    @Test
    void delegatesToJudge() {
        RunSubmissionDTO request = new RunSubmissionDTO();
        RunResultDTO expected = RunResultDTO.builder().id("run-1").build();
        when(judge.execute(request, 42L, "user-1")).thenReturn(expected);

        assertSame(expected, adapter.execute(request, 42L, "user-1"));
    }

    @Test
    void mapsTransportFailureToTypedUnavailableError() {
        RunSubmissionDTO request = new RunSubmissionDTO();
        when(judge.execute(request, 42L, "user-1")).thenThrow(new RpcException("offline"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> adapter.execute(request, 42L, "user-1"));

        assertEquals(ProblemErrorCode.CODE_EXECUTION_UNAVAILABLE, error.getErrorCode());
    }
}
