package com.ulticode.modules.problem.service.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problem.vo.ProblemVersionDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Owns the serialization shape of a Problem-version snapshot.
 *
 * <p><strong>Deep module</strong> &mdash; extracted from the 494-LOC
 * {@code ProblemVersionServiceImpl}, which inlined {@code ObjectMapper}
 * serialize/parse calls in four separate methods.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemSnapshotCodec {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String serialize(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize snapshot", e);
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to serialize problem snapshot");
        }
    }

    public Map<String, Object> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse snapshot JSON", e);
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to parse version snapshot");
        }
    }

    @SuppressWarnings("unchecked")
    public void populateDetail(ProblemVersionDetailVO detail, String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return;
        }
        Map<String, Object> snapshot = deserialize(snapshotJson);
        detail.setTitle((String) snapshot.get("title"));
        detail.setSlug((String) snapshot.get("slug"));
        detail.setDifficulty((String) snapshot.get("difficulty"));
        detail.setIsPremium((Boolean) snapshot.get("isPremium"));
        detail.setIsPublished((Boolean) snapshot.get("isPublished"));
        detail.setSummary((String) snapshot.get("summary"));
        detail.setContent((String) snapshot.get("content"));
        detail.setConstraints((List<String>) snapshot.get("constraints"));
        detail.setHints((List<String>) snapshot.get("hints"));
        detail.setExamples((List<Map<String, Object>>) snapshot.get("examples"));
        detail.setLanguages((List<Map<String, Object>>) snapshot.get("languages"));
        detail.setTags((List<String>) snapshot.get("tags"));
    }
}
