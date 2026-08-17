package com.ulticode.submission.api.codec;

import com.ulticode.domain.submission.enums.CaseScope;
import com.ulticode.submission.api.dto.SubmissionTestCaseDetailDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Submission test-case detail wire codec")
class TestCaseDetailCodecTest {

    @Test
    @DisplayName("round-trips the canonical persisted JSON shape")
    void roundTripsCanonicalShape() {
        SubmissionTestCaseDetailDTO detail = new SubmissionTestCaseDetailDTO(
                "Accepted", 12, 1.5, "ok", "2", "2",
                List.of(new SubmissionTestCaseDetailDTO.InputParam(
                        "input-1", "n", "n", "2")),
                "tc-1", CaseScope.SAMPLE);

        String json = TestCaseDetailCodec.toJson(List.of(detail));
        List<SubmissionTestCaseDetailDTO> decoded = TestCaseDetailCodec.fromJson(json);

        assertThat(json)
                .contains("\"status\":\"Accepted\"")
                .contains("\"caseId\":\"tc-1\"")
                .contains("\"caseScope\":\"SAMPLE\"")
                .contains("\"inputs\":[{\"id\":\"input-1\"");
        assertThat(decoded).containsExactly(detail);
    }

    @Test
    @DisplayName("keeps legacy rows and null/blank conventions")
    void keepsLegacyConventions() {
        assertThat(TestCaseDetailCodec.toJson(null)).isNull();
        assertThat(TestCaseDetailCodec.toJson(List.of())).isNull();
        assertThat(TestCaseDetailCodec.fromJson(null)).isNull();
        assertThat(TestCaseDetailCodec.fromJson(" ")).isNull();

        List<SubmissionTestCaseDetailDTO> legacy = TestCaseDetailCodec.fromJson(
                "[{\"status\":\"Accepted\",\"time\":10}]");
        assertThat(legacy).singleElement().satisfies(detail -> {
            assertThat(detail.status()).isEqualTo("Accepted");
            assertThat(detail.caseId()).isNull();
            assertThat(detail.caseScope()).isNull();
        });
    }

    @Test
    void invalidJsonFailsClosed() {
        assertThat(TestCaseDetailCodec.fromJson("not-json")).isNull();
    }
}
