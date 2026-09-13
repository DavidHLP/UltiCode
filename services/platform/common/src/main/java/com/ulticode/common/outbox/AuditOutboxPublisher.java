package com.ulticode.common.outbox;

/**
 * Owner-specific publication or local sink for an audit outbox row.
 *
 * <p>A publisher returns an external publication id when it writes a stream.
 * A local sink may return {@code null} after its durable write commits.</p>
 */
@FunctionalInterface
public interface AuditOutboxPublisher<T> {

    String publish(T record) throws Exception;
}
