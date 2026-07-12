package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared parser for a case's inputs JSON / inputText, lifted verbatim from
 * the pre-refactor {@code DefaultJudgeExecutionPipeline.parseInputs} so both
 * the canonical and legacy {@link com.ulticode.modules.queue.port.JudgingCaseSource}
 * adapters apply identical parsing policy.
 */
@Slf4j
public final class JudgingCaseInputs {

    private JudgingCaseInputs() {
    }

    /**
     * Parse the JSON inputs array from a test case / problem example. Falls
     * back to wrapping inputText as a single input if JSON is absent or
     * malformed.
     */
    public static List<RunSubmissionDTO.RunInput> parse(
            ObjectMapper objectMapper, String inputsJson, String inputText, Object entityId) {
        List<RunSubmissionDTO.RunInput> runInputs = new ArrayList<>();
        if (inputsJson != null && !inputsJson.isBlank()) {
            try {
                List<Map<String, Object>> inputs = objectMapper.readValue(
                        inputsJson, new TypeReference<List<Map<String, Object>>>() {});
                for (int i = 0; i < inputs.size(); i++) {
                    Map<String, Object> item = inputs.get(i);
                    RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
                    ri.setId(String.valueOf(i));
                    Object nameObj = item.get("name");
                    Object labelObj = item.get("label");
                    String name = (nameObj != null ? nameObj : (labelObj != null ? labelObj : "input")).toString();
                    ri.setLabel(name);
                    ri.setName(name);
                    Object valueObj = item.get("value");
                    ri.setValue(valueObj != null ? valueObj.toString() : "");
                    Object typeObj = item.get("type");
                    if (typeObj != null && !typeObj.toString().isBlank()) {
                        ri.setType(typeObj.toString());
                    }
                    runInputs.add(ri);
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse inputs JSON for entity {}, falling back to inputText", entityId);
            }
        }
        if (runInputs.isEmpty() && inputText != null) {
            RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
            ri.setId("0");
            ri.setLabel("input");
            ri.setName("input");
            ri.setValue(inputText);
            runInputs.add(ri);
        }
        return runInputs;
    }
}
