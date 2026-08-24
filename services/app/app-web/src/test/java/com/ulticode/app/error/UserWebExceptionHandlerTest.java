package com.ulticode.app.error;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserWebExceptionHandlerTest {

    @Test
    void userNotFoundMapsToNotFound() {
        ResponseEntity<Result<Void>> response = new UserWebExceptionHandler()
                .handleBusinessException(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        assertEquals(404, response.getStatusCode().value());
        assertEquals(20001, response.getBody().getCode());
    }
}
