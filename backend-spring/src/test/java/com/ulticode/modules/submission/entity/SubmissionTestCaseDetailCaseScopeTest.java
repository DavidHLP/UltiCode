package com.ulticode.modules.submission.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.enums.CaseScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0-1: {@code Submission.TestCaseDetail} now carries nullable
 * {@code caseId} and {@code caseScope} (SAMPLE|HIDDEN). Legacy JSON rows
 * have no scope — deserialization must accept {@code caseScope=null}
 * and never silently coerce to HIDDEN.
 *
 * <p>Round-trip property: serializing a detail with {@code caseScope=SAMPLE}
 * produces the wire string {@code "SAMPLE"}; deserializing the legacy form
 * ({@code caseScope=null}) leaves the field {@code null}; parsing an unknown
 * wire value throws {@code IllegalArgumentException}.
 */
@DisplayName("Submission.TestCaseDetail caseScope (P0-1)")
class SubmissionTestCaseDetailCaseScopeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("caseScope null on legacy rows is preserved (not coerced to HIDDEN)")
    void legacyRowHasNullScope() throws Exception {
        String legacyJson = "{\"status\":\"Accepted\",\"time\":10,\"memory\":1.5}";
        Submission.TestCaseDetail detail = mapper.readValue(legacyJson, Submission.TestCaseDetail.class);

        assertThat(detail.getStatus()).isEqualTo("Accepted");
        assertThat(detail.getCaseScope()).isNull();
        assertThat(detail.getCaseId()).isNull();
        // Critical: legacy null scope must be treated as user-visible sample,
        // never as HIDDEN. This is what the projection layer relies on.
        assertThat(com.ulticode.modules.submission.enums.CaseScope.isUserVisible(detail.getCaseScope())).isTrue();
    }

    @Test
    @DisplayName("SAMPLE scope round-trips as 'SAMPLE' wire value")
    void sampleScopeRoundTrip() throws Exception {
        Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
        detail.setStatus("Accepted");
        detail.setCaseScope(CaseScope.SAMPLE);
        detail.setCaseId("tc-sample-1");

        String json = mapper.writeValueAsString(detail);
        assertThat(json).contains("\"caseScope\":\"SAMPLE\"");
        assertThat(json).contains("\"caseId\":\"tc-sample-1\"");

        Submission.TestCaseDetail parsed = mapper.readValue(json, Submission.TestCaseDetail.class);
        assertThat(parsed.getCaseScope()).isEqualTo(CaseScope.SAMPLE);
        assertThat(parsed.getCaseId()).isEqualTo("tc-sample-1");
    }

    @Test
    @DisplayName("HIDDEN scope round-trips as 'HIDDEN' and isUserVisible=false")
    void hiddenScopeIsNotUserVisible() throws Exception {
        Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
        detail.setCaseScope(CaseScope.HIDDEN);
        detail.setCaseId("tc-hidden-1");

        String json = mapper.writeValueAsString(detail);
        Submission.TestCaseDetail parsed = mapper.readValue(json, Submission.TestCaseDetail.class);
        assertThat(parsed.getCaseScope()).isEqualTo(CaseScope.HIDDEN);
        assertThat(CaseScope.isUserVisible(parsed.getCaseScope())).isFalse();
    }

    @Test
    @DisplayName("Unknown wire value throws — guards against silently dropped scope")
    void unknownWireValueRejected() {
        assertThatThrownBy(() -> CaseScope.fromWire("LEGACY_SAMPLE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LEGACY_SAMPLE");
        assertThatThrownBy(() -> CaseScope.fromWire("SOMETHING_ELSE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("LEGACY_SAMPLE is intentionally not an enum value — projection handles null")
    void legacySampleIsNotAnEnumValue() {
        // Reviewer explicitly required: do NOT add LEGACY_SAMPLE to the enum,
        // so it can never accidentally be persisted into test_details JSON.
        assertThat(CaseScope.values()).doesNotContain(
                java.util.Arrays.stream(CaseScope.values()).filter(c -> "LEGACY_SAMPLE".equals(c.wireValue())).findAny().orElse(null));
    }
}
