package com.ulticode.judge.provider;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.service.CodeExecutionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodeExecutionProviderTest {

    @Test
    void delegatesToJudgeRuntime() {
        CodeExecutionService delegate = mock(CodeExecutionService.class);
        CodeExecutionProvider provider = new CodeExecutionProvider(delegate);
        RunSubmissionDTO request = new RunSubmissionDTO();
        RunResultDTO expected = RunResultDTO.builder().id("run-1").build();
        when(delegate.execute(request, 42L, "user-1")).thenReturn(expected);

        assertSame(expected, provider.execute(request, 42L, "user-1"));
    }
}
