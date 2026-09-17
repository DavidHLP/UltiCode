package com.ulticode.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Jackson receipt payload codec")
class JacksonReceiptPayloadCodecTest {

    private final JacksonReceiptPayloadCodec codec =
            new JacksonReceiptPayloadCodec(new ObjectMapper());

    @Test
    void roundTripsPayloadValues() throws Exception {
        String payload = codec.encode(new Payload("sub-1", 3));

        Payload decoded = codec.decode(payload, Payload.class);

        assertThat(decoded).isEqualTo(new Payload("sub-1", 3));
    }

    @Test
    void rejectsMalformedPayload() {
        assertThatThrownBy(() -> codec.decode("{not-json", Payload.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void requiresObjectMapper() {
        assertThatThrownBy(() -> new JacksonReceiptPayloadCodec(null))
                .isInstanceOf(NullPointerException.class);
    }

    record Payload(String id, int count) {
    }
}
