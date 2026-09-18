package com.ulticode.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Jackson receipt payload codec")
class JacksonReceiptPayloadCodecTest {

    private final JacksonReceiptPayloadCodec codec =
            new JacksonReceiptPayloadCodec(new ObjectMapper());

    @Test
    void roundTripsPayloadValues() throws Exception {
        String payload = codec.encode(new Payload("sub-1", 3));

        Payload decoded = codec.decode(payload, Payload.class);

        assertEquals(new Payload("sub-1", 3), decoded);
    }

    @Test
    void rejectsMalformedPayload() {
        assertThrows(JsonProcessingException.class, () -> codec.decode("{not-json", Payload.class));
    }

    @Test
    void requiresObjectMapper() {
        assertThrows(NullPointerException.class, () -> new JacksonReceiptPayloadCodec(null));
    }

    record Payload(String id, int count) {
    }
}
