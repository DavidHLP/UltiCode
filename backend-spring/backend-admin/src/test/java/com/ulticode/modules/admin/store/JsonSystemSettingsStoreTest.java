package com.ulticode.modules.admin.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.modules.admin.dto.settings.FeatureTogglesVO;
import com.ulticode.modules.admin.dto.settings.GeneralSettingsVO;
import com.ulticode.modules.admin.entity.SystemSetting;
import com.ulticode.modules.admin.mapper.SystemSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JsonSystemSettingsStore} — the JSON storage seam
 * behind {@code SystemSettingsServiceImpl}.
 *
 * <p>Covers the deep-module contract:
 * <ul>
 *   <li>Category keys are stable (5 in the documented order).</li>
 *   <li>{@code loadOrDefault} returns the DDL default when the row is
 *       missing, blank, or unparseable — the caller supplies the default,
 *       the store never invents one.</li>
 *   <li>{@code save} serializes to JSON and stamps {@code updated_at} from
 *       the injected {@link Clock} (not {@code System.currentTimeMillis()}).</li>
 *   <li>{@code loadAllRaw} maps null fields and missing rows correctly.</li>
 *   <li>{@code deleteAll} delegates one DELETE per key (the existing
 *       MyBatis-Plus contract — no bulk delete is required).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JsonSystemSettingsStore")
class JsonSystemSettingsStoreTest {

    @Mock
    private SystemSettingMapper mapper;
    @Mock
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonSystemSettingsStore store;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        store = new JsonSystemSettingsStore(mapper, objectMapper, clock);
    }

    // ===== categoryKeys =====

    @Test
    @DisplayName("categoryKeys returns the 5 documented keys in stable order")
    void categoryKeys() {
        assertThat(store.categoryKeys())
                .containsExactly("general", "email", "rate-limits", "uploads", "features");
    }

    // ===== loadOrDefault =====

    @Nested
    @DisplayName("loadOrDefault")
    class LoadOrDefault {

        @Test
        @DisplayName("missing row → returns the caller-supplied default")
        void missingRowReturnsDefault() {
            when(mapper.selectById("general")).thenReturn(null);

            AtomicReference<GeneralSettingsVO> seen = new AtomicReference<>();
            GeneralSettingsVO out = store.loadOrDefault("general", GeneralSettingsVO.class, () -> {
                GeneralSettingsVO def = new GeneralSettingsVO();
                def.setSiteName("DEFAULT");
                seen.set(def);
                return def;
            });

            assertThat(seen.get()).isSameAs(out);
            assertThat(out.getSiteName()).isEqualTo("DEFAULT");
        }

        @Test
        @DisplayName("blank value column → returns the default (no JSON parse attempted)")
        void blankValueReturnsDefault() {
            SystemSetting row = new SystemSetting();
            row.setKey("general");
            row.setValue("   ");
            when(mapper.selectById("general")).thenReturn(row);

            GeneralSettingsVO out = store.loadOrDefault("general", GeneralSettingsVO.class,
                    () -> new GeneralSettingsVO());

            assertThat(out.getSiteName()).isNull();
        }

        @Test
        @DisplayName("valid JSON → decodes to the requested type")
        void validJsonDecodes() {
            SystemSetting row = new SystemSetting();
            row.setKey("general");
            row.setValue("{\"siteName\":\"Decoded\",\"maintenanceMode\":true}");
            when(mapper.selectById("general")).thenReturn(row);

            GeneralSettingsVO out = store.loadOrDefault("general", GeneralSettingsVO.class,
                    () -> {
                        throw new AssertionError("default factory must not run on valid JSON");
                    });

            assertThat(out.getSiteName()).isEqualTo("Decoded");
            assertThat(out.isMaintenanceMode()).isTrue();
        }

        @Test
        @DisplayName("corrupt JSON → returns the default and logs a warn (no exception)")
        void corruptJsonReturnsDefault() {
            SystemSetting row = new SystemSetting();
            row.setKey("general");
            row.setValue("{not valid json");
            when(mapper.selectById("general")).thenReturn(row);

            GeneralSettingsVO out = store.loadOrDefault("general", GeneralSettingsVO.class,
                    () -> {
                        GeneralSettingsVO def = new GeneralSettingsVO();
                        def.setSiteName("FALLBACK");
                        return def;
                    });

            assertThat(out.getSiteName()).isEqualTo("FALLBACK");
        }
    }

    // ===== parseOrDefault (used by getAllSettings batched path) =====

    @Nested
    @DisplayName("parseOrDefault (batched read companion)")
    class ParseOrDefault {

        @Test
        @DisplayName("null JSON → default")
        void nullJson() {
            GeneralSettingsVO out = store.parseOrDefault(null, GeneralSettingsVO.class,
                    () -> {
                        GeneralSettingsVO def = new GeneralSettingsVO();
                        def.setSiteName("X");
                        return def;
                    });
            assertThat(out.getSiteName()).isEqualTo("X");
        }

        @Test
        @DisplayName("blank JSON → default")
        void blankJson() {
            GeneralSettingsVO out = store.parseOrDefault("", GeneralSettingsVO.class,
                    () -> {
                        GeneralSettingsVO def = new GeneralSettingsVO();
                        def.setSiteName("X");
                        return def;
                    });
            assertThat(out.getSiteName()).isEqualTo("X");
        }

        @Test
        @DisplayName("valid JSON → decoded type")
        void valid() {
            GeneralSettingsVO out = store.parseOrDefault(
                    "{\"siteName\":\"Parsed\",\"enableRegistrations\":false}",
                    GeneralSettingsVO.class, GeneralSettingsVO::new);
            assertThat(out.getSiteName()).isEqualTo("Parsed");
            assertThat(out.isEnableRegistrations()).isFalse();
        }

        @Test
        @DisplayName("corrupt JSON → default, no exception")
        void corrupt() {
            GeneralSettingsVO out = store.parseOrDefault("not json", GeneralSettingsVO.class,
                    () -> {
                        GeneralSettingsVO def = new GeneralSettingsVO();
                        def.setSiteName("F");
                        return def;
                    });
            assertThat(out.getSiteName()).isEqualTo("F");
        }
    }

    // ===== save =====

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("serializes VO to JSON and stamps updated_at from the clock")
        void saveSerializesAndStamps() {
            GeneralSettingsVO vo = new GeneralSettingsVO();
            vo.setSiteName("Persisted");
            vo.setMaintenanceMode(true);

            store.save("general", vo);

            ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
            verify(mapper).insertOrUpdate(captor.capture());
            SystemSetting persisted = captor.getValue();
            assertThat(persisted.getKey()).isEqualTo("general");
            assertThat(persisted.getValue()).contains("\"siteName\":\"Persisted\"");
            assertThat(persisted.getValue()).contains("\"maintenanceMode\":true");
            assertThat(persisted.getUpdatedAt())
                    .isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        }

        @Test
        @DisplayName("JSON serialization failure → BusinessException(SETTING_PERSISTENCE_FAILED)")
        void serializationFailureThrows() {
            // Use an object whose Jackson serializer throws (unserializable self-ref).
            Object bad = new Object() {
                @SuppressWarnings("unused")
                public Object getSelf() {
                    return this;
                }
            };

            assertThatThrownBy(() -> store.save("general", bad))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(AdminErrorCode.SETTING_PERSISTENCE_FAILED.getMessage());
        }
    }

    // ===== loadAllRaw =====

    @Nested
    @DisplayName("loadAllRaw")
    class LoadAllRaw {

        @Test
        @DisplayName("empty keys → empty map (no mapper call)")
        void emptyKeys() {
            assertThat(store.loadAllRaw(List.of())).isEmpty();
            verify(mapper, never()).selectBatchIds(any());
        }

        @Test
        @DisplayName("null keys → empty map (defensive)")
        void nullKeys() {
            assertThat(store.loadAllRaw(null)).isEmpty();
            verify(mapper, never()).selectBatchIds(any());
        }

        @Test
        @DisplayName("partial result → only present keys appear in the map")
        void partialResult() {
            SystemSetting general = new SystemSetting();
            general.setKey("general");
            general.setValue("{\"siteName\":\"X\"}");
            SystemSetting features = new SystemSetting();
            features.setKey("features");
            features.setValue("{\"featureContest\":true}");
            when(mapper.selectBatchIds(List.of("general", "email", "rate-limits", "uploads", "features")))
                    .thenReturn(List.of(general, features));

            Map<String, String> out = store.loadAllRaw(store.categoryKeys());

            assertThat(out).hasSize(2);
            assertThat(out.get("general")).contains("\"siteName\":\"X\"");
            assertThat(out.get("features")).contains("\"featureContest\":true");
            assertThat(out).doesNotContainKey("email");
            assertThat(out).doesNotContainKey("rate-limits");
            assertThat(out).doesNotContainKey("uploads");
        }

        @Test
        @DisplayName("skips rows whose key is null (defensive against corrupt data)")
        void skipsNullKeyRows() {
            SystemSetting ok = new SystemSetting();
            ok.setKey("general");
            ok.setValue("{}");
            SystemSetting bad = new SystemSetting();
            bad.setKey(null);
            bad.setValue("{}");
            when(mapper.selectBatchIds(store.categoryKeys())).thenReturn(List.of(ok, bad));

            Map<String, String> out = store.loadAllRaw(store.categoryKeys());

            assertThat(out).hasSize(1);
            assertThat(out).containsKey("general");
        }
    }

    // ===== deleteAll =====

    @Nested
    @DisplayName("deleteAll")
    class DeleteAll {

        @Test
        @DisplayName("delegates one delete per key (no bulk call)")
        void oneDeletePerKey() {
            store.deleteAll(List.of("general", "email"));
            verify(mapper).deleteById((java.io.Serializable) "general");
            verify(mapper).deleteById((java.io.Serializable) "email");
        }

        @Test
        @DisplayName("empty keys → no mapper calls")
        void emptyKeys() {
            store.deleteAll(List.of());
            verify(mapper, never()).deleteById(any(java.io.Serializable.class));
        }
    }

    // ===== round-trip (the contract getAllSettings relies on) =====

    @Test
    @DisplayName("save then loadOrDefault round-trip preserves the VO")
    void roundTrip() {
        FeatureTogglesVO original = new FeatureTogglesVO();
        original.setFeatureContest(true);
        original.setFeatureBookmarks(false);

        // Capture what the store writes, then re-feed it to loadOrDefault.
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        when(mapper.insertOrUpdate(any(SystemSetting.class))).thenReturn(true);
        store.save("features", original);
        verify(mapper).insertOrUpdate(captor.capture());

        SystemSetting stored = captor.getValue();
        Supplier<FeatureTogglesVO> mustNotRun = () -> {
            throw new AssertionError("default factory must not run on stored JSON");
        };
        when(mapper.selectById("features")).thenReturn(stored);
        FeatureTogglesVO reloaded = store.loadOrDefault("features", FeatureTogglesVO.class, mustNotRun);

        assertThat(reloaded.isFeatureContest()).isTrue();
        assertThat(reloaded.isFeatureBookmarks()).isFalse();
        assertThat(reloaded.isFeatureForum()).isFalse(); // default false, persisted as false
    }
}
