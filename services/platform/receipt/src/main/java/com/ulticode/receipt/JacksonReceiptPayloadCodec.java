package com.ulticode.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.command.ReceiptPayloadCodec;

import java.util.Objects;

/** Jackson implementation of the shared receipt payload codec. */
public final class JacksonReceiptPayloadCodec implements ReceiptPayloadCodec {

    private final ObjectMapper objectMapper;

    public JacksonReceiptPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public String encode(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Override
    public <T> T decode(String payload, Class<T> resultType) throws Exception {
        return objectMapper.readValue(payload, resultType);
    }
}
