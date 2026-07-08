package com.ulticode.modules.problem.service;

import com.ulticode.modules.problem.service.codec.ProblemSnapshotCodec;
import com.ulticode.modules.problem.service.codec.ProblemVersionDiff;
import com.ulticode.modules.problem.vo.VersionDiffVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Problem-version codec + diff")
class ProblemVersionCodecTest {

    private ProblemSnapshotCodec codec;
    private ProblemVersionDiff differ;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        codec = new ProblemSnapshotCodec(objectMapper);
        differ = new ProblemVersionDiff(objectMapper);
    }

    @Nested
    @DisplayName("ProblemSnapshotCodec")
    class CodecTests {

        @Test
        @DisplayName("serialize + deserialize round-trip preserves all fields")
        void roundTrip() {
            Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
            snapshot.put("title", "Two Sum");
            snapshot.put("difficulty", "EASY");
            snapshot.put("tags", List.of("array", "hash-table"));
            snapshot.put("isPremium", false);

            String json = codec.serialize(snapshot);
            Map<String, Object> restored = codec.deserialize(json);

            assertEquals("Two Sum", restored.get("title"));
            assertEquals("EASY", restored.get("difficulty"));
            assertEquals(false, restored.get("isPremium"));
        }

        @Test
        @DisplayName("deserialize returns empty map for null/blank input")
        void deserializeBlank() {
            assertTrue(codec.deserialize(null).isEmpty());
            assertTrue(codec.deserialize("").isEmpty());
        }

        @Test
        @DisplayName("deserialize throws on malformed JSON")
        void deserializeMalformed() {
            assertThrows(RuntimeException.class, () -> codec.deserialize("{broken"));
        }
    }

    @Nested
    @DisplayName("ProblemVersionDiff")
    class DiffTests {

        @Test
        @DisplayName("returns empty diff for identical snapshots")
        void noDiffs() {
            String json = codec.serialize(Map.of("title", "A"));
            assertTrue(differ.diff(json, json).isEmpty());
        }

        @Test
        @DisplayName("detects changed field")
        void changedField() {
            String from = codec.serialize(Map.of("title", "A", "difficulty", "EASY"));
            String to = codec.serialize(Map.of("title", "A", "difficulty", "MEDIUM"));

            List<VersionDiffVO> diffs = differ.diff(from, to);

            assertEquals(1, diffs.size());
            assertEquals("difficulty", diffs.get(0).getField());
        }

        @Test
        @DisplayName("detects added field (old=null)")
        void addedField() {
            String from = codec.serialize(Map.of("title", "A"));
            String to = codec.serialize(Map.of("title", "A", "slug", "two-sum"));

            List<VersionDiffVO> diffs = differ.diff(from, to);

            assertEquals(1, diffs.size());
            assertNull(diffs.get(0).getOldValue());
        }

        @Test
        @DisplayName("detects removed field (new=null)")
        void removedField() {
            String from = codec.serialize(Map.of("title", "A", "slug", "s"));
            String to = codec.serialize(Map.of("title", "A"));

            List<VersionDiffVO> diffs = differ.diff(from, to);

            assertEquals(1, diffs.size());
            assertNull(diffs.get(0).getNewValue());
        }
    }
}
