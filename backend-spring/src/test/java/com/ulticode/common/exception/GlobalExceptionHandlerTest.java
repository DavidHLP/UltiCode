package com.ulticode.common.exception;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.ulticode.common.response.Result;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler#handleHttpMessageNotReadable(HttpMessageNotReadableException)}.
 *
 * <p>Verifies the InvalidFormatException sanitization branch added to
 * prevent Jackson's verbose "not one of the values accepted for Enum
 * class: [...]" message from leaking the backend enum surface. Reported in
 * docs/edge-operations-api-test-report-2026-06-11.md §4.3.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    /** Build a real Jackson InvalidFormatException rooted at {@code path}. */
    private static InvalidFormatException buildInvalidFormatException(
            String value, Class<?> targetType, String... pathFieldNames) {
        final String msg = "Cannot deserialize value of type "
                + targetType.getSimpleName() + " from String \"" + value + "\"";
        // Jackson's Reference(String) is actually Reference(Object) and silently
        // nulls _fieldName for non-Class/non-Reference types. Subclass to
        // override getFieldName() and override getPath() to inject our refs.
        return new InvalidFormatException(
                (JsonParser) null, msg, value, targetType) {
            @Override
            public List<Reference> getPath() {
                if (pathFieldNames.length == 0) {
                    return java.util.Collections.emptyList();
                }
                List<Reference> refs = new java.util.ArrayList<>();
                for (String name : pathFieldNames) {
                    refs.add(new Reference(name) {
                        @Override
                        public String getFieldName() {
                            return name;
                        }
                    });
                }
                return refs;
            }
        };
    }

    @Nested
    @DisplayName("InvalidFormatException branch (enum body)")
    class EnumBody {

        @Test
        @DisplayName("strips Jackson verbose message and surfaces generic field name")
        void stripsVerboseForOperationType() {
            // Arrange: Jackson throws InvalidFormatException for an unknown enum value
            // in the body. Root cause chain is the IFE itself.
            InvalidFormatException ife = buildInvalidFormatException(
                    "HACK", EdgeOperationType.class, "operationType");
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("JSON parse error", ife);

            // Act
            ResponseEntity<Result<Void>> response = handler.handleHttpMessageNotReadable(ex);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            Result<Void> body = response.getBody();
            assertNotNull(body, "Response body must not be null");
            assertEquals(40000, body.getCode().intValue());
            assertEquals("Invalid value for parameter 'operationType'", body.getMessage());
            // SECURITY: must not leak the input value or the backend enum surface
            assertFalse(body.getMessage().contains("HACK"),
                    "Response must not echo the input value");
            assertFalse(body.getMessage().contains("Enum class:"),
                    "Response must not expose Jackson's verbose enum list");
            assertFalse(body.getMessage().contains("VOTE_UP")
                            || body.getMessage().contains("DISLIKE")
                            || body.getMessage().contains("FAVORITE"),
                    "Response must not leak individual backend enum values");
        }

        @Test
        @DisplayName("works for targetType enum path field as well")
        void stripsVerboseForTargetType() {
            InvalidFormatException ife = buildInvalidFormatException(
                    "INVALID", EdgeOperationTargetType.class, "targetType");
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("JSON parse error", ife);

            ResponseEntity<Result<Void>> response = handler.handleHttpMessageNotReadable(ex);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Invalid value for parameter 'targetType'",
                    response.getBody().getMessage());
        }

        @Test
        @DisplayName("falls back to 'body' label when path is empty")
        void emptyPathReturnsBodyLabel() {
            // Empty path: leaf-field name unavailable. Must NOT crash.
            InvalidFormatException ife = buildInvalidFormatException(
                    "HACK", EdgeOperationType.class);
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("JSON parse error", ife);

            ResponseEntity<Result<Void>> response = handler.handleHttpMessageNotReadable(ex);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Invalid value for parameter 'body'",
                    response.getBody().getMessage());
        }

        @Test
        @DisplayName("does NOT sanitize when targetType is not an enum (numeric etc.)")
        void doesNotSanitizeForNonEnumInvalidFormat() {
            // Simulate a number-format error: targetType is Integer, not an enum.
            // Should fall through to the original handler behavior.
            InvalidFormatException ife = buildInvalidFormatException(
                    "abc", Integer.class, "count");
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("JSON parse error", ife);

            ResponseEntity<Result<Void>> response = handler.handleHttpMessageNotReadable(ex);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            String message = response.getBody().getMessage();
            assertTrue(message.startsWith("Malformed request body: "),
                    "Non-enum IFE must take the original 'Malformed request body:' path, got: "
                            + message);
        }
    }

    @Nested
    @DisplayName("Fallback (non-enum Jackson failure)")
    class Fallback {

        @Test
        @DisplayName("generic Jackson failure still gets compact 'Malformed request body:' prefix")
        void genericJacksonFailureUsesFallback() {
            // Simulate a JsonMappingException with no IFE wrapping (e.g. wrong body shape)
            JsonMappingException jme = new JsonMappingException((JsonParser) null, "bad shape");
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("JSON parse error", jme);

            ResponseEntity<Result<Void>> response = handler.handleHttpMessageNotReadable(ex);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().getMessage().startsWith("Malformed request body: "));
        }

        @Test
        @DisplayName("IFE with null targetType takes fallback (defensive)")
        void nullTargetTypeTakesFallback() {
            // Mock IFE that returns null targetType (defensive against any version drift)
            InvalidFormatException ife = mock(InvalidFormatException.class);
            when(ife.getTargetType()).thenReturn(null);
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("JSON parse error", ife);

            ResponseEntity<Result<Void>> response = handler.handleHttpMessageNotReadable(ex);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().getMessage().startsWith("Malformed request body: "));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("preserves traceId in the response")
        void preservesTraceId() {
            InvalidFormatException ife = buildInvalidFormatException(
                    "HACK", EdgeOperationType.class, "operationType");
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("JSON parse error", ife);

            ResponseEntity<Result<Void>> response = handler.handleHttpMessageNotReadable(ex);

            String traceId = response.getBody().getTraceId();
            assertNotNull(traceId, "traceId must be present");
            assertTrue(traceId.startsWith("t-"),
                    "traceId must follow project convention (t-<millis>), got: " + traceId);
        }
    }

    /**
     * Regression guard for the new {@code handleBadSqlGrammarException}
     * added in Task 3 (docs/.claude/PRPs/plans/achievement-api-fixes.plan.md
     * §MEDIUM #4). Without this handler the exception falls through to
     * {@code handleGenericException} and returns code=50000 "Unknown error",
     * which masks the SQL root cause. See
     * docs/achievement-api-test-report-2026-06-11.md §6 MEDIUM #4.
     */
    @Nested
    @DisplayName("BadSqlGrammarException handler (MEDIUM #4)")
    class BadSqlGrammarExceptionTests {

        @Test
        @DisplayName("returns 500 with DATABASE_ERROR (50001), not generic UNKNOWN_ERROR (50000)")
        void returns500WithDatabaseErrorCode() {
            // Mirror the real failure path: SELECT referencing unquoted MySQL
            // reserved word 'key' produces SQLSyntaxErrorException → Spring
            // exception translator → BadSqlGrammarException.
            java.sql.SQLException sqlEx = new java.sql.SQLException(
                    "You have an error in your SQL syntax; ... near 'key' at line 1");
            org.springframework.jdbc.BadSqlGrammarException ex =
                    new org.springframework.jdbc.BadSqlGrammarException(
                            "selectByKey",
                            "SELECT id,key,name FROM achievements WHERE id=?",
                            sqlEx);

            ResponseEntity<Result<Void>> response = handler.handleBadSqlGrammarException(ex);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(50001, response.getBody().getCode(),
                    "Must use DATABASE_ERROR (50001), not UNKNOWN_ERROR (50000)");
            assertEquals("Database error", response.getBody().getMessage());
        }

        @Test
        @DisplayName("preserves traceId in the response")
        void preservesTraceId() {
            java.sql.SQLException sqlEx = new java.sql.SQLException("syntax error");
            org.springframework.jdbc.BadSqlGrammarException ex =
                    new org.springframework.jdbc.BadSqlGrammarException(
                            "selectByKey", "SELECT * FROM achievements WHERE key = ?", sqlEx);

            ResponseEntity<Result<Void>> response = handler.handleBadSqlGrammarException(ex);

            String traceId = response.getBody().getTraceId();
            assertNotNull(traceId, "traceId must be present");
            assertTrue(traceId.startsWith("t-"),
                    "traceId must follow project convention (t-<millis>), got: " + traceId);
        }
    }
}
