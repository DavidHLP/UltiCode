package com.ulticode.modules.admin.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.entity.SystemSetting;
import com.ulticode.modules.admin.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * MyBatis-Plus + Jackson production adapter for {@link SystemSettingsStore}.
 *
 * <p>Owns:
 * <ul>
 *   <li>The five category keys ({@code general}, {@code email},
 *       {@code rate-limits}, {@code uploads}, {@code features}).</li>
 *   <li>JSON encode / decode of the {@code value} column.</li>
 *   <li>The batched read used by {@code GET /admin/settings/all}.</li>
 *   <li>The row upsert / delete paths.</li>
 * </ul>
 *
 * <p>Does <em>not</em> own DDL defaults (caller passes a {@code defaultFactory}),
 * masking rules, the all-defaults feature-toggle safety check, or audit
 * anchors &mdash; those are policy decisions, not storage shape.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonSystemSettingsStore implements SystemSettingsStore {

    private static final List<String> CATEGORY_KEYS = List.of(
            KEY_GENERAL, KEY_EMAIL, KEY_RATE_LIMITS, KEY_UPLOADS, KEY_FEATURES);

    private final SystemSettingMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public List<String> categoryKeys() {
        return CATEGORY_KEYS;
    }

    @Override
    public <T> T loadOrDefault(String key, Class<T> type, Supplier<T> defaultFactory) {
        SystemSetting row = mapper.selectById(key);
        if (row == null || row.getValue() == null || row.getValue().isBlank()) {
            return defaultFactory.get();
        }
        return parseOrDefault(row.getValue(), type, defaultFactory);
    }

    @Override
    public <T> T parseOrDefault(String json, Class<T> type, Supplier<T> defaultFactory) {
        if (json == null || json.isBlank()) {
            return defaultFactory.get();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize {} payload, returning default",
                    type.getSimpleName(), e);
            return defaultFactory.get();
        }
    }

    @Override
    public void save(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            SystemSetting row = new SystemSetting();
            row.setKey(key);
            row.setValue(json);
            row.setUpdatedAt(LocalDateTime.now(clock));
            mapper.insertOrUpdate(row);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize setting key={}", key, e);
            throw new BusinessException(ErrorCode.SETTING_PERSISTENCE_FAILED);
        }
    }

    @Override
    public Map<String, String> loadAllRaw(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SystemSetting> rows = mapper.selectBatchIds(keys);
        Map<String, String> result = new HashMap<>(rows.size());
        for (SystemSetting row : rows) {
            if (row != null && row.getKey() != null) {
                result.put(row.getKey(), row.getValue());
            }
        }
        return result;
    }

    @Override
    public void deleteAll(Collection<String> keys) {
        for (String key : keys) {
            mapper.deleteById(key);
        }
    }
}
