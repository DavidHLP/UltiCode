package com.ulticode.modules.queue.port;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link VerdictMetricsParser}.
 *
 * <p>Before the deepening, asserting on wire-string parsing required wiring
 * the full {@code JudgeWorkerProcessor} (18 mocked collaborators). After the
 * deepening, the parser is a deep module exercised in isolation — these
 * tests are the win the seam exists for.
 */
@DisplayName("VerdictMetricsParser")
class VerdictMetricsParserTest {

    private final VerdictMetricsParser parser = new VerdictMetricsParser();

    @Nested
    @DisplayName("parseRuntimeMs")
    class ParseRuntimeMs {

        @Test
        @DisplayName("strips 'ms' suffix and returns integer ms")
        void parsesStandardRuntime() {
            assertEquals(123L, parser.parseRuntimeMs("123ms"));
        }

        @Test
        @DisplayName("trims whitespace")
        void trimsWhitespace() {
            assertEquals(45L, parser.parseRuntimeMs("  45ms "));
        }

        @Test
        @DisplayName("returns 0 for null")
        void nullReturnsZero() {
            assertEquals(VerdictMetricsParser.RUNTIME_PARSE_FAILED, parser.parseRuntimeMs(null));
        }

        @Test
        @DisplayName("returns 0 for blank")
        void blankReturnsZero() {
            assertEquals(VerdictMetricsParser.RUNTIME_PARSE_FAILED, parser.parseRuntimeMs("   "));
        }

        @Test
        @DisplayName("returns 0 for unparseable")
        void unparseableReturnsZero() {
            assertEquals(VerdictMetricsParser.RUNTIME_PARSE_FAILED, parser.parseRuntimeMs("oops"));
        }
    }

    @Nested
    @DisplayName("parseMemoryMb")
    class ParseMemoryMb {

        @Test
        @DisplayName("strips 'MB' suffix and returns double MB")
        void parsesStandardMemory() {
            assertEquals(4.2, parser.parseMemoryMb("4.2MB"));
        }

        @Test
        @DisplayName("trims whitespace")
        void trimsWhitespace() {
            assertEquals(8.0, parser.parseMemoryMb("  8.0MB "));
        }

        @Test
        @DisplayName("returns 0 for null")
        void nullReturnsZero() {
            assertEquals(VerdictMetricsParser.MEMORY_PARSE_FAILED, parser.parseMemoryMb(null));
        }

        @Test
        @DisplayName("returns 0 for blank")
        void blankReturnsZero() {
            assertEquals(VerdictMetricsParser.MEMORY_PARSE_FAILED, parser.parseMemoryMb(""));
        }

        @Test
        @DisplayName("returns 0 for unparseable")
        void unparseableReturnsZero() {
            assertEquals(VerdictMetricsParser.MEMORY_PARSE_FAILED, parser.parseMemoryMb("garbage"));
        }
    }
}