package com.ulticode.common.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Result response wrapper.
 */
class ResultTest {

    @Test
    void testSuccessWithData() {
        // Arrange
        String testData = "test data";

        // Act
        Result<String> result = Result.success(testData);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals(testData, result.getData());
        assertNotNull(result.getTraceId());
        assertTrue(result.getTraceId().startsWith("t-"));
    }

    @Test
    void testSuccessWithoutData() {
        // Act
        Result<Void> result = Result.success();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getCode());
        assertEquals("success", result.getMessage());
        assertNull(result.getData());
        assertNotNull(result.getTraceId());
        assertTrue(result.getTraceId().startsWith("t-"));
    }

    @Test
    void testErrorWithCodeAndMessage() {
        // Arrange
        Integer errorCode = 40001;
        String errorMessage = "Submission not found";

        // Act
        Result<Void> result = Result.error(errorCode, errorMessage);

        // Assert
        assertNotNull(result);
        assertEquals(errorCode, result.getCode());
        assertEquals(errorMessage, result.getMessage());
        assertNull(result.getData());
        assertNotNull(result.getTraceId());
        assertTrue(result.getTraceId().startsWith("t-"));
    }

    @Test
    void testErrorWithCodeMessageAndTraceId() {
        // Arrange
        Integer errorCode = 10001;
        String errorMessage = "Invalid credentials";
        String customTraceId = "t-custom-12345";

        // Act
        Result<Void> result = Result.error(errorCode, errorMessage, customTraceId);

        // Assert
        assertNotNull(result);
        assertEquals(errorCode, result.getCode());
        assertEquals(errorMessage, result.getMessage());
        assertNull(result.getData());
        assertEquals(customTraceId, result.getTraceId());
    }

    @Test
    void testSuccessWithComplexObject() {
        // Arrange
        PageResult<String> pageResult = PageResult.of(
                java.util.List.of("item1", "item2"),
                100L,
                1,
                10
        );

        // Act
        Result<PageResult<String>> result = Result.success(pageResult);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getCode());
        assertEquals("success", result.getMessage());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().getItems().size());
        assertEquals(100L, result.getData().getTotal());
    }

    @Test
    void testTraceIdFormat() {
        // Act
        Result<Void> result = Result.success();

        // Assert
        assertNotNull(result.getTraceId());
        assertTrue(result.getTraceId().startsWith("t-"));
        // Verify the trace ID contains a numeric timestamp
        String timestampPart = result.getTraceId().substring(2);
        assertTrue(timestampPart.matches("\\d+"),
                "Trace ID should contain numeric timestamp after 't-' prefix");
    }
}
