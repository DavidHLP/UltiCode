package com.ulticode.app.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for {@link SolutionWebExceptionHandler} verifying
 * HTTP status and Result envelope mapping.
 *
 * <p>P7-RELOCATE-SOLUTION-001: proves the handler maps SolutionErrorCode
 * and BaseErrorCode to correct HTTP statuses and result codes.
 */
class SolutionWebExceptionHandlerTest {

    private final SolutionWebExceptionHandler handler = new SolutionWebExceptionHandler();

    @Test
    @DisplayName("SOLUTION_NOT_FOUND returns HTTP 404 with code 50401")
    void solutionNotFound_returns404() {
        BusinessException ex = new BusinessException(SolutionErrorCode.SOLUTION_NOT_FOUND);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo(50401);
        assertThat(response.getBody().getMessage()).isEqualTo("Solution not found");
    }

    @Test
    @DisplayName("BaseErrorCode.UNAUTHORIZED returns HTTP 401 with code 40100")
    void unauthorized_returns401() {
        BusinessException ex = new BusinessException(BaseErrorCode.UNAUTHORIZED);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getCode()).isEqualTo(40100);
    }

    @Test
    @DisplayName("BaseErrorCode.BAD_REQUEST returns HTTP 400 with code 40000")
    void badRequest_returns400() {
        BusinessException ex = new BusinessException(BaseErrorCode.BAD_REQUEST);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(40000);
    }

    @Test
    @DisplayName("USER_BANNED returns HTTP 403 with code 20003")
    void userBanned_returns403() {
        BusinessException ex = new BusinessException(SolutionErrorCode.USER_BANNED);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getCode()).isEqualTo(20003);
    }

    @Test
    @DisplayName("SOLUTION_ALREADY_EXISTS returns HTTP 409 with code 50008")
    void solutionAlreadyExists_returns409() {
        BusinessException ex = new BusinessException(SolutionErrorCode.SOLUTION_ALREADY_EXISTS);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo(50008);
    }
}
