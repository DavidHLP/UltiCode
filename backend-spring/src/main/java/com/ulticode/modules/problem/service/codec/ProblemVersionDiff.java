package com.ulticode.modules.problem.service.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problem.vo.VersionDiffVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the field-level diff between two Problem-version snapshots.
 *
 * <p><strong>Deep module</strong> &mdash; extracted from the 494-LOC
 * {@code ProblemVersionServiceImpl}. Pure logic, no mapper dependencies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemVersionDiff {

    private final ObjectMapper objectMapper;

    public List<VersionDiffVO> diff(String fromJson, String toJson) {
        Map<String, Object> from = parseOrThrow(fromJson, "from");
        Map<String, Object> to = parseOrThrow(toJson, "to");

        List<VersionDiffVO> diffs = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(from.keySet());
        allKeys.addAll(to.keySet());

        for (String key : allKeys) {
            Object oldValue = from.get(key);
            Object newValue = to.get(key);
            if (!isValueEqual(oldValue, newValue)) {
                VersionDiffVO diff = new VersionDiffVO();
                diff.setField(key);
                diff.setOldValue(oldValue);
                diff.setNewValue(newValue);
                diffs.add(diff);
            }
        }
        return diffs;
    }

    private Map<String, Object> parseOrThrow(String json, String label) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse {} snapshot JSON for diff", label, e);
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to parse version snapshots");
        }
    }

    private boolean isValueEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        try {
            return objectMapper.writeValueAsString(a).equals(objectMapper.writeValueAsString(b));
        } catch (JsonProcessingException e) {
            return a.equals(b);
        }
    }
}
