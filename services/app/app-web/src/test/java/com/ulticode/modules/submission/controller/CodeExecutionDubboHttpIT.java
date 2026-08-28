package com.ulticode.modules.submission.controller;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.dto.RunSubmissionDTO;
import com.ulticode.app.api.service.CodeExecutionPort;
import com.ulticode.app.error.ProblemWebExceptionHandler;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.judge.provider.CodeExecutionProvider;
import com.ulticode.modules.submission.port.adapter.RemoteCodeExecutionPort;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import com.ulticode.submission.api.service.SubmissionUserQueryPort;
import jakarta.validation.Validator;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CodeExecutionDubboHttpIT {

    private static ServiceConfig<CodeExecutionPort> service;
    private static ReferenceConfig<CodeExecutionPort> reference;
    private static ReferenceConfig<CodeExecutionPort> missingReference;
    private static RemoteCodeExecutionPort remote;
    private static MockMvc mockMvc;

    @BeforeAll
    static void setUp() {
        CodeExecutionService runtime = mock(CodeExecutionService.class);
        when(runtime.execute(any(), eq(42L), isNull())).thenReturn(
                RunResultDTO.builder()
                        .id("run-dubbo-1")
                        .problemId(42L)
                        .verdict("Accepted")
                        .cases(List.of())
                        .build());

        ApplicationConfig application = new ApplicationConfig("code-execution-http-it");
        RegistryConfig registry = new RegistryConfig(RegistryConfig.NO_AVAILABLE);
        ProtocolConfig protocol = new ProtocolConfig("dubbo", -1);

        service = new ServiceConfig<>();
        service.setApplication(application);
        service.setRegistry(registry);
        service.setProtocol(protocol);
        service.setInterface(CodeExecutionPort.class);
        service.setGroup("backend-judge");
        service.setVersion("1.0.0");
        service.setRef(new CodeExecutionProvider(runtime));
        service.export();

        reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setRegistry(registry);
        reference.setInterface(CodeExecutionPort.class);
        reference.setGroup("backend-judge");
        reference.setVersion("1.0.0");
        reference.setInjvm(true);
        reference.setCheck(false);

        missingReference = new ReferenceConfig<>();
        missingReference.setApplication(application);
        missingReference.setRegistry(registry);
        missingReference.setInterface(CodeExecutionPort.class);
        missingReference.setGroup("missing-backend-judge");
        missingReference.setVersion("1.0.0");
        missingReference.setInjvm(true);
        missingReference.setCheck(false);

        remote = new RemoteCodeExecutionPort();
        ReflectionTestUtils.setField(remote, "judgeExecution", reference.get());
        ProblemSubmissionController controller = new ProblemSubmissionController(
                mock(SubmissionUserQueryPort.class),
                mock(SubmissionIntakePort.class),
                remote,
                mock(Validator.class),
                mock(CurrentUserProvider.class));
        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new ProblemWebExceptionHandler())
                .build();
    }

    @AfterAll
    static void tearDown() {
        if (reference != null) {
            reference.destroy();
        }
        if (missingReference != null) {
            missingReference.destroy();
        }
        if (service != null) {
            service.unexport();
        }
        DubboBootstrap.reset();
    }

    @Test
    @Order(1)
    void httpRequestTraversesDubboJudgeProvider() throws Exception {
        mockMvc.perform(runRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("run-dubbo-1"))
                .andExpect(jsonPath("$.data.verdict").value("Accepted"));
    }

    @Test
    @Order(2)
    void missingJudgeProviderMapsToHttp503() throws Exception {
        ReflectionTestUtils.setField(remote, "judgeExecution", missingReference.get());

        mockMvc.perform(runRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(30022))
                .andExpect(jsonPath("$.message").value("Code execution is unavailable"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder runRequest() {
        return post("/problems/42/submissions/run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "language": "python",
                          "code": "print('ok')",
                          "testCases": [{"inputs": [], "output": "ok"}]
                        }
                        """);
    }
}
