package com.ulticode.modules.submission.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.dto.RunSubmissionDTO;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.service.DFormEnvelopeCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins backend ↔ harness contract parity for the D-form envelope.
 *
 * <p>Authoritative source: {@code docker/sandbox/harness/contract/CONTRACT.md}.
 * The schemas and golden files live next to that spec; they are also
 * mirrored into {@code src/test/resources/contract/} so the JVM test lane
 * can read them off the classpath without needing the harness source tree.
 *
 * <p>Scope (additive — no runtime change):
 * <ol>
 *   <li>Decoding: a real-shaped envelope (golden) decodes via
 *       {@link CodeExecutionHelperImpl#parseDEnvelope} into the expected
 *       per-case {@link RunResultDTO.RunCaseResult} values.</li>
 *   <li>Encoding: {@link CodeExecutionHelperImpl#buildDBatchInputsJson}
 *       and {@link CodeExecutionHelperImpl#buildDInputsJson} emit JSON
 *       that contains every field the input schema requires.</li>
 *   <li>Vocabulary: every {@code status} value in the contract is
 *       decodable by {@link SubmissionStatusCodec#fromWire(String)}.</li>
 * </ol>
 *
 * <p>This test does <b>not</b> touch Docker / the sandbox image / the live
 * judge path. It runs in the regular unit-test lane.
 */
@DisplayName("D-form envelope contract parity (backend ↔ harness)")
@org.junit.jupiter.api.Disabled("Test fixtures not relocated; re-enable after resource migration")
class DFormEnvelopeContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String goldenInputJson;
    private static String goldenEnvelopeJson;
    private static JsonNode inputSchema;
    private static JsonNode envelopeSchema;

    private final DFormEnvelopeCodec helper = buildCodec();

    private DFormEnvelopeCodec buildCodec() {
        UuidGenerator fixedUuid = new UuidGenerator() {
            @Override
            public String newId() {
                return "test-uuid";
            }
        };
        com.ulticode.modules.submission.service.SandboxOutputFormatter formatter =
            new com.ulticode.modules.submission.service.impl.SandboxOutputFormatterImpl(fixedUuid);
        return new com.ulticode.modules.submission.service.impl.DFormEnvelopeCodecImpl(MAPPER, formatter);
    }

    @BeforeAll
    static void loadFixtures() throws Exception {
        goldenInputJson = readResource("/contract/golden/input.json");
        goldenEnvelopeJson = readResource("/contract/golden/envelope.json");
        JsonNode inSchema = MAPPER.readTree(readResource("/contract/input.schema.json"));
        JsonNode envSchema = MAPPER.readTree(readResource("/contract/envelope.schema.json"));
        inputSchema = inSchema;
        envelopeSchema = envSchema;
    }

    private static String readResource(String path) throws Exception {
        try (InputStream is = DFormEnvelopeContractTest.class.getResourceAsStream(path)) {
            assertThat(is).as("classpath resource %s", path).isNotNull();
            try (var br = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        }
    }

    // ── 1. Decoding: golden envelope → expected RunCaseResult values ────────

    @Test
    @DisplayName("parseDEnvelope decodes golden envelope into the expected per-case values")
    void decodeGoldenEnvelope() throws Exception {
        List<RunSubmissionDTO.RunTestCase> testCases = buildTestCasesFromGoldenInput();
        String runId = "test-run";
        String userId = "test-user";

        List<RunResultDTO.RunCaseResult> actual =
                helper.parseDEnvelope(goldenEnvelopeJson, testCases, runId, userId);

        assertThat(actual).hasSize(3);

        RunResultDTO.RunCaseResult c1 = actual.get(0);
        assertThat(c1.getStatus()).isEqualTo("Accepted");
        assertThat(c1.getCaseLabel()).isEqualTo("simple-positive");
        assertThat(c1.getRuntimeMs()).isEqualTo(3L);
        assertThat(c1.getRuntimeUs()).isEqualTo(3120L);
        assertThat(c1.getCpuMs()).isEqualTo(1L);
        // 8388608 bytes / (1024 * 1024) = 8 MiB; floor at 1 if non-zero.
        assertThat(c1.getMemoryMb()).isEqualTo(8L);

        RunResultDTO.RunCaseResult c3 = actual.get(2);
        assertThat(c3.getStatus()).isEqualTo("Accepted");
        // golden envelope has result=[] for case-003 (empty ListNode).
        assertThat(c3.getOutput()).isEqualTo("[]");
    }

    // ── 2. Encoding: buildDBatchInputsJson / buildDInputsJson shape ────────

    @Test
    @DisplayName("buildDBatchInputsJson produces JSON containing every required top-level field")
    void encodeBatchInputs_topLevelShape() throws Exception {
        List<RunSubmissionDTO.RunTestCase> cases = buildTestCasesFromGoldenInput();
        String json = helper.buildDBatchInputsJson(cases, 1000L, 134217728L);
        assertThat(json).isNotBlank();

        JsonNode root = parseOrFail(json);
        assertThat(root.has("per_case_timeout_ms")).isTrue();
        assertThat(root.has("memory_limit_bytes")).isTrue();
        assertThat(root.has("cases")).isTrue();
        assertThat(root.get("cases").isArray()).isTrue();
        assertThat(root.get("cases")).hasSize(3);

        // Every case has the schema-required fields.
        for (JsonNode c : root.get("cases")) {
            assertThat(c.has("case_id")).as("case_id").isTrue();
            assertThat(c.has("expected_output")).as("expected_output").isTrue();
            assertThat(c.has("inputs")).as("inputs").isTrue();
            assertThat(c.get("inputs").isArray()).isTrue();
            for (JsonNode in : c.get("inputs")) {
                assertThat(in.has("value")).as("inputs[i].value").isTrue();
            }
        }
    }

    @Test
    @DisplayName("buildDInputsJson (single case) wraps the case in a one-element batch")
    void encodeSingleCase_wrapsInBatch() throws Exception {
        List<RunSubmissionDTO.RunTestCase> cases = buildTestCasesFromGoldenInput();
        String json = helper.buildDInputsJson(cases.get(0), 500L, 0L);
        JsonNode root = parseOrFail(json);
        assertThat(root.get("per_case_timeout_ms").asLong()).isEqualTo(500L);
        // memory_limit_bytes is OMITTED when <=0 (buildDBatchInputsJson behaviour).
        assertThat(root.has("memory_limit_bytes")).isFalse();
        assertThat(root.get("cases")).hasSize(1);
        assertThat(root.get("cases").get(0).get("case_id").asText()).isEqualTo("case-001");
    }

    @Test
    @DisplayName("buildDInputSpecs forwards DFORM_TYPES values verbatim and drops unknowns")
    void encodeInputSpecs_typeForwarding() throws Exception {
        List<RunSubmissionDTO.RunTestCase> cases = buildTestCasesFromGoldenInput();
        String json = helper.buildDBatchInputsJson(cases, 1000L, 0L);
        JsonNode root = parseOrFail(json);

        // case-001 inputs: nums=int[], target=int → both types preserved.
        JsonNode case001 = root.get("cases").get(0);
        assertThat(case001.get("inputs").get(0).get("type").asText()).isEqualTo("int[]");
        assertThat(case001.get("inputs").get(1).get("type").asText()).isEqualTo("int");

        // case-003 inputs: head=ListNode → preserved.
        JsonNode case003 = root.get("cases").get(2);
        assertThat(case003.get("inputs").get(0).get("type").asText()).isEqualTo("ListNode");
    }

    // ── 3. Vocabulary: every contract status decodes via the codec ──────────

    @Test
    @DisplayName("every status value in the contract decodes via SubmissionStatusCodec")
    void statusVocabulary_decodesViaCodec() {
        // The full vocabulary pinned in CONTRACT.md §4.
        Set<String> vocabulary = Set.of(
                "Accepted",
                "Wrong Answer",
                "Time Limit Exceeded",
                "Memory Limit Exceeded",
                "Runtime Error",
                "Compile Error",
                "Output Limit Exceeded"
        );
        for (String wire : vocabulary) {
            SubmissionStatus s = SubmissionStatusCodec.fromWire(wire);
            assertThat(s).as("codec decodes %s", wire).isNotNull();
            // Round-trip: toWire → fromWire is stable.
            assertThat(SubmissionStatusCodec.toWire(s)).isEqualTo(wire);
        }
    }

    // ── 4. Golden envelope itself validates against envelope.schema.json ───

    @Test
    @DisplayName("golden envelope has every field the schema requires (subset check)")
    void goldenEnvelope_hasRequiredFields() throws Exception {
        JsonNode root = MAPPER.readTree(goldenEnvelopeJson);
        for (String required : List.of(
                "harness_version", "language", "exit_code",
                "total_elapsed_ms", "results")) {
            assertThat(root.has(required)).as("envelope.%s", required).isTrue();
        }
        // Per-case required fields.
        JsonNode perCaseRequired = envelopeSchema.at("/$defs/perCase/required");
        assertThat(perCaseRequired.isArray()).isTrue();
        List<String> requiredList = new ArrayList<>();
        for (JsonNode n : perCaseRequired) requiredList.add(n.asText());

        for (JsonNode c : root.get("results")) {
            for (String f : requiredList) {
                assertThat(c.has(f)).as("results[i].%s present", f).isTrue();
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private JsonNode parseOrFail(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse JSON: " + e.getMessage() + "\n" + json, e);
        }
    }

    /**
     * Synthesise a {@link RunSubmissionDTO.RunTestCase} list shaped after
     * the golden input.json. Uses {@code UuidGenerator.newId() == "test-uuid"}
     * via the test-only generator wired into {@code helper}.
     */
    private List<RunSubmissionDTO.RunTestCase> buildTestCasesFromGoldenInput() throws Exception {
        JsonNode root = MAPPER.readTree(goldenInputJson);
        List<RunSubmissionDTO.RunTestCase> out = new ArrayList<>();
        Iterator<JsonNode> it = root.get("cases").elements();
        while (it.hasNext()) {
            JsonNode c = it.next();
            RunSubmissionDTO.RunTestCase tc = new RunSubmissionDTO.RunTestCase();
            tc.setId(c.get("case_id").asText());
            tc.setLabel(c.has("label") ? c.get("label").asText() : c.get("case_id").asText());
            tc.setOutput(c.get("expected_output").asText());

            List<RunSubmissionDTO.RunInput> inputs = new ArrayList<>();
            for (JsonNode in : c.get("inputs")) {
                RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
                ri.setName(in.has("name") ? in.get("name").asText() : null);
                ri.setValue(in.get("value").asText());
                ri.setType(in.has("type") ? in.get("type").asText() : null);
                inputs.add(ri);
            }
            tc.setInputs(inputs);
            out.add(tc);
        }
        return out;
    }
}