package com.ulticode.app.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.forum.controller.ForumController;
import com.ulticode.modules.problem.controller.ProblemController;
import com.ulticode.modules.solution.controller.SolutionController;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContentWebExceptionHandlerTest {

    static Stream<ContentWebExceptionHandler> handlers() {
        return Stream.of(new ForumWebExceptionHandler(), new SolutionWebExceptionHandler());
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void preservesAllDomainAndBaseMappings(ContentWebExceptionHandler handler) {
        Map<? extends NamespacedErrorCode, HttpStatus> domainStatuses = handler instanceof ForumWebExceptionHandler
                ? Map.of(
                        ForumErrorCode.FORUM_POST_NOT_FOUND, HttpStatus.NOT_FOUND,
                        ForumErrorCode.FORUM_COMMUNITY_NOT_FOUND, HttpStatus.NOT_FOUND,
                        ForumErrorCode.FORUM_COMMUNITY_RESTRICTED, HttpStatus.FORBIDDEN,
                        ForumErrorCode.FORUM_CANNOT_EDIT_POST, HttpStatus.FORBIDDEN,
                        ForumErrorCode.FORUM_CANNOT_DELETE_POST, HttpStatus.FORBIDDEN,
                        ForumErrorCode.FORUM_COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        ForumErrorCode.FORUM_POST_LOCKED, HttpStatus.FORBIDDEN,
                        ForumErrorCode.FORUM_INVALID_SORT, HttpStatus.BAD_REQUEST)
                : Map.of(
                        SolutionErrorCode.USER_CANNOT_EDIT_OTHERS, HttpStatus.FORBIDDEN,
                        SolutionErrorCode.USER_BANNED, HttpStatus.FORBIDDEN,
                        SolutionErrorCode.PROBLEM_NOT_FOUND, HttpStatus.NOT_FOUND,
                        SolutionErrorCode.SOLUTION_NOT_FOUND, HttpStatus.NOT_FOUND,
                        SolutionErrorCode.SOLUTION_CANNOT_DELETE_OTHERS, HttpStatus.FORBIDDEN,
                        SolutionErrorCode.SOLUTION_CANNOT_UPDATE_OTHERS, HttpStatus.FORBIDDEN,
                        SolutionErrorCode.SOLUTION_COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        SolutionErrorCode.SOLUTION_ALREADY_EXISTS, HttpStatus.CONFLICT);
        assertThat(domainStatuses).hasSize(handler instanceof ForumWebExceptionHandler
                ? ForumErrorCode.values().length : SolutionErrorCode.values().length);
        domainStatuses.forEach((error, status) -> assertError(handler.handleBusinessException(
                new BusinessException(error, error.message(), "domain-trace")),
                status, error.code(), error.message(), "domain-trace"));

        Map<BaseErrorCode, HttpStatus> baseStatuses = Map.of(
                BaseErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
                BaseErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                BaseErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
                BaseErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                BaseErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                BaseErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED,
                BaseErrorCode.CONFLICT, HttpStatus.CONFLICT,
                BaseErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS);
        for (BaseErrorCode error : BaseErrorCode.values()) {
            assertError(handler.handleBusinessException(new BusinessException(error, error.message(), "base-trace")),
                    baseStatuses.getOrDefault(error, HttpStatus.INTERNAL_SERVER_ERROR),
                    error.code(), error.message(), "base-trace");
        }
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void otherDomainsRemainInternalServerErrors(ContentWebExceptionHandler handler) {
        NamespacedErrorCode[] otherDomain = handler instanceof ForumWebExceptionHandler
                ? SolutionErrorCode.values() : ForumErrorCode.values();
        for (NamespacedErrorCode error : otherDomain) {
            assertError(handler.handleBusinessException(new BusinessException(error, error.message(), "other-trace")),
                    HttpStatus.INTERNAL_SERVER_ERROR, error.code(), error.message(), "other-trace");
        }
        NamespacedErrorCode unknown = mock(NamespacedErrorCode.class);
        when(unknown.code()).thenReturn(71999);
        when(unknown.message()).thenReturn("Other domain failure");
        assertError(handler.handleBusinessException(new BusinessException(unknown, unknown.message(), "other-trace")),
                HttpStatus.INTERNAL_SERVER_ERROR, 71999, "Other domain failure", "other-trace");
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void businessFallbacksPreserveMessageAndExceptionTrace(ContentWebExceptionHandler handler) {
        assertError(handler.handleBusinessException(new BusinessException(BaseErrorCode.BAD_REQUEST,
                        "Custom message", "exception-trace")),
                HttpStatus.BAD_REQUEST, 40000, "Custom message", "exception-trace");
        assertError(handler.handleBusinessException(new BusinessException(BaseErrorCode.BAD_REQUEST,
                        null, "exception-trace")),
                HttpStatus.BAD_REQUEST, 40000, "Bad request", "exception-trace");
        assertError(handler.handleBusinessException(new BusinessException(null, "Custom message", "exception-trace")),
                HttpStatus.INTERNAL_SERVER_ERROR, 50000, "Custom message", "exception-trace");
        assertError(handler.handleBusinessException(new BusinessException(null, null, "exception-trace")),
                HttpStatus.INTERNAL_SERVER_ERROR, 50000, "Unknown error", "exception-trace");
        assertError(handler.handleBusinessException(new BusinessException(null, null, (String) null)),
                HttpStatus.INTERNAL_SERVER_ERROR, 50000, "Unknown error", null);
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void validationKeepsFieldMapAndLastErrorForDuplicateFields(ContentWebExceptionHandler handler) {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "title", "First error"));
        binding.addError(new FieldError("request", "content", "Content is required"));
        binding.addError(new FieldError("request", "title", "Last error"));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(binding);

        try (var trace = mockStatic(TraceIdUtil.class)) {
            trace.when(TraceIdUtil::current).thenReturn("validation-trace");
            ResponseEntity<Result<Map<String, String>>> response = handler.handleValidationException(exception);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(40000);
            assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
            assertThat(response.getBody().getData()).containsExactlyInAnyOrderEntriesOf(
                    Map.of("title", "Last error", "content", "Content is required"));
            assertThat(response.getBody().getTraceId()).isEqualTo("validation-trace");
        }
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void genericFailuresKeepStatusesMessagesAndCurrentTrace(ContentWebExceptionHandler handler) {
        try (var trace = mockStatic(TraceIdUtil.class)) {
            trace.when(TraceIdUtil::current).thenReturn("request-trace");
            assertError(handler.handleHttpMessageNotReadable(new HttpMessageNotReadableException(
                            "Sensitive parser details", new MockHttpInputMessage(new byte[0]))),
                    HttpStatus.BAD_REQUEST, 40000, "Malformed request body", "request-trace");
            assertError(handler.handleTypeMismatch(new MethodArgumentTypeMismatchException(
                            "invalid", Long.class, "id", null, null)),
                    HttpStatus.BAD_REQUEST, 40000, "Invalid value for parameter 'id': expected Long", "request-trace");
            assertError(handler.handleTypeMismatch(new MethodArgumentTypeMismatchException(
                            "invalid", null, "id", null, null)),
                    HttpStatus.BAD_REQUEST, 40000, "Invalid value for parameter 'id': expected ?", "request-trace");
            assertError(handler.handleAccessDenied(new AccessDeniedException("Sensitive permission details")),
                    HttpStatus.FORBIDDEN, 40300, "Forbidden", "request-trace");
            assertError(handler.handleAuthenticationException(new BadCredentialsException("Sensitive auth details")),
                    HttpStatus.UNAUTHORIZED, 40100, "Unauthorized", "request-trace");
        }
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void springDiscoversAllInheritedHandlers(ContentWebExceptionHandler handler) {
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(handler.getClass());
        Map<Class<? extends Exception>, String> methods = Map.of(
                BusinessException.class, "handleBusinessException",
                MethodArgumentNotValidException.class, "handleValidationException",
                HttpMessageNotReadableException.class, "handleHttpMessageNotReadable",
                MethodArgumentTypeMismatchException.class, "handleTypeMismatch",
                AccessDeniedException.class, "handleAccessDenied",
                AuthenticationException.class, "handleAuthenticationException");
        methods.forEach((exception, methodName) -> {
            var method = resolver.resolveMethodByExceptionType(exception);
            assertThat(method).isNotNull();
            assertThat(method.getName()).isEqualTo(methodName);
            assertThat(method.getDeclaringClass()).isEqualTo(ContentWebExceptionHandler.class);
        });
        assertThat(resolver.resolveMethodByExceptionType(IllegalStateException.class)).isNull();
    }

    @Test
    void adviceScopesAndPriorityRemainSeparate() {
        assertThat(AnnotatedElementUtils.findMergedAnnotation(ContentWebExceptionHandler.class, ControllerAdvice.class))
                .isNull();
        try (var context = new AnnotationConfigApplicationContext(
                ForumWebExceptionHandler.class, SolutionWebExceptionHandler.class)) {
            List<ControllerAdviceBean> advices = ControllerAdviceBean.findAnnotatedBeans(context);
            assertThat(advices).extracting(ControllerAdviceBean::getBeanType)
                    .containsExactlyInAnyOrder(ForumWebExceptionHandler.class, SolutionWebExceptionHandler.class);
            for (ControllerAdviceBean advice : advices) {
                boolean forum = advice.getBeanType() == ForumWebExceptionHandler.class;
                assertThat(advice.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
                assertThat(advice.isApplicableToBeanType(ForumController.class)).isEqualTo(forum);
                assertThat(advice.isApplicableToBeanType(SolutionController.class)).isEqualTo(!forum);
                assertThat(advice.isApplicableToBeanType(ProblemController.class)).isFalse();
            }
        }
    }

    @Test
    void mvcDispatchesBothDomainsToInheritedHandlers() throws Exception {
        ForumController forum = mock(ForumController.class);
        SolutionController solution = mock(SolutionController.class);
        when(forum.getPostById("missing")).thenThrow(new BusinessException(
                ForumErrorCode.FORUM_POST_NOT_FOUND, "Missing post", "forum-trace"));
        when(solution.findByProblemId(1L, null, null)).thenThrow(new BusinessException(
                SolutionErrorCode.SOLUTION_NOT_FOUND, "Missing solution", "solution-trace"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(forum, solution)
                .setControllerAdvice(new ForumWebExceptionHandler(), new SolutionWebExceptionHandler()).build();

        mvc.perform(get("/forum/posts/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(60001))
                .andExpect(jsonPath("$.message").value("Missing post"))
                .andExpect(jsonPath("$.traceId").value("forum-trace"));
        mvc.perform(get("/api/problems/1/solutions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(50401))
                .andExpect(jsonPath("$.message").value("Missing solution"))
                .andExpect(jsonPath("$.traceId").value("solution-trace"));
    }

    private static void assertError(ResponseEntity<Result<Void>> response, HttpStatus status,
            int code, String message, String traceId) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(code);
        assertThat(response.getBody().getMessage()).isEqualTo(message);
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getTraceId()).isEqualTo(traceId);
    }
}
