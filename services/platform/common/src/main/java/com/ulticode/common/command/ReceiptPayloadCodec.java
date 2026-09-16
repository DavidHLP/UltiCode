package com.ulticode.common.command;

/** Owner-supplied payload codec; common must not depend on a JSON implementation. */
public interface ReceiptPayloadCodec {

    /** Encodes a mutation result for durable replay. */
    String encode(Object value) throws Exception;

    /** Decodes a stored replay payload to the requested result type. */
    <T> T decode(String payload, Class<T> resultType) throws Exception;
}
