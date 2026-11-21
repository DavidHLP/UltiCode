package com.ulticode.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.event.SearchDocumentChangedEventContract;
import com.ulticode.common.resilience.DependencyGuard;
import com.ulticode.search.adapter.SearchIndex;
import com.ulticode.search.adapter.SearchIndexUnavailableException;
import com.ulticode.search.config.SearchWorkerProperties;
import io.micrometer.core.instrument.Counter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Applies versioned writes to the Search index.
 *
 * <p>Version ledger (DEC-016): each UPSERT carries the document version
 * (epoch millis, {@code aggregateVersion} envelope field). Search keeps
 * a per-index Redis hash ({@code versionKeyPrefix}:{@code index}) of the last
 * written version per document. An incoming UPSERT whose version is strictly
 * older than the ledger entry is skipped (counted, still ACKed) so a backfill
 * snapshot can never overwrite a newer live write; equal versions rewrite
 * (idempotent, content-convergent). DELETEs record a typed tombstone
 * ({@code D:T}) so an equal-or-older UPSERT cannot resurrect a deleted
 * document, including version zero. Legacy negative tombstones remain
 * readable for compatibility. The version is also embedded in the Meili
 * document as {@code _aggregateVersion} for diff watermark and observability.
 * A per-document Redis lease serializes operations across replicas while held.
 * The writer renews the lease before advancing the ledger.
 */
@Slf4j
final class VersionedSearchDocumentWriter {

    static final Duration DOCUMENT_LOCK_TTL = Duration.ofSeconds(30);

    private static final String DOCUMENT_VERSION_FIELD = "_aggregateVersion";
    private static final String DELETE_LEDGER_PREFIX = "D:";
    private static final TypeReference<Map<String, Object>> DOCUMENT_TYPE = new TypeReference<>() {};

    private static final RedisScript<Long> RELEASE_DOCUMENT_LOCK_SCRIPT = RedisScript.of(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    /** Re-extends the lease only while we still own it. */
    private static final RedisScript<Long> RENEW_DOCUMENT_LOCK_SCRIPT = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1]
            then return redis.call('EXPIRE', KEYS[1], ARGV[2])
            else return 0 end
            """, Long.class);

    enum Result {
        APPLIED,
        STALE,
        LOCK_BUSY
    }

    private final StringRedisTemplate redisTemplate;
    private final SearchIndex searchIndex;
    private final ObjectMapper objectMapper;
    private final SearchWorkerProperties props;
    private final Counter staleCounter;
    private final DependencyGuard meiliSearchGuard = new DependencyGuard(8, 5, Duration.ofSeconds(30));

    VersionedSearchDocumentWriter(
            StringRedisTemplate redisTemplate,
            SearchIndex searchIndex,
            ObjectMapper objectMapper,
            SearchWorkerProperties props,
            Counter staleCounter) {
        this.redisTemplate = redisTemplate;
        this.searchIndex = searchIndex;
        this.objectMapper = objectMapper;
        this.props = props;
        this.staleCounter = staleCounter;
    }

    /**
     * The callback lets the caller clear its deferral before a later failure
     * can leave the event pending for retry.
     */
    Result apply(
            String index,
            String documentId,
            long incomingVersion,
            String operation,
            Object document,
            String lockToken,
            Runnable onLeaseAcquired) throws JsonProcessingException {
        String versionKey = props.getVersionKeyPrefix() + ":" + index;
        String lockKey = versionKey + ":lock:" + documentId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey, lockToken, DOCUMENT_LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return Result.LOCK_BUSY;
        }

        try {
            onLeaseAcquired.run();
            String existing = ledgerVersion(versionKey, documentId);
            if (isStale(existing, incomingVersion)) {
                staleCounter.increment();
                return Result.STALE;
            }
            if (SearchDocumentChangedEventContract.UPSERT.equals(operation)) {
                if (document == null) {
                    throw new IllegalArgumentException("UPSERT search event without document");
                }
                Map<String, Object> doc = objectMapper.readValue(
                        objectMapper.writeValueAsString(document), DOCUMENT_TYPE);
                doc.put(DOCUMENT_VERSION_FIELD, incomingVersion);
                String serializedDocument = objectMapper.writeValueAsString(doc);
                executeMeili(() -> searchIndex.index(index).upsert(serializedDocument));
                writeLedgerIfStillOwner(lockKey, lockToken, versionKey, documentId,
                        String.valueOf(incomingVersion));
            } else if (SearchDocumentChangedEventContract.DELETE.equals(operation)) {
                executeMeili(() -> searchIndex.index(index).delete(documentId));
                writeLedgerIfStillOwner(lockKey, lockToken, versionKey, documentId,
                        DELETE_LEDGER_PREFIX + incomingVersion);
            } else {
                throw new IllegalArgumentException("unsupported operation: " + operation);
            }
            return Result.APPLIED;
        } finally {
            releaseDocumentLock(lockKey, lockToken);
        }
    }

    private void executeMeili(Runnable operation) {
        DependencyGuard.Permit permit = meiliSearchGuard.acquire();
        try (permit) {
            try {
                operation.run();
                permit.success();
            } catch (SearchIndexUnavailableException unavailable) {
                permit.failure();
                throw unavailable;
            } catch (RuntimeException failure) {
                permit.ignore();
                throw failure;
            }
        }
    }

    /**
     * Renew the lease before advancing the ledger. If Meili accepted the write
     * after the lease expired, leave the ledger alone and let the worker ACK.
     */
    private void writeLedgerIfStillOwner(
            String lockKey, String lockToken, String versionKey, String documentId, String ledgerValue) {
        if (!ownsDocumentLock(lockKey, lockToken)) {
            log.warn("Search document lease lost before ledger write: skipping ledger update for {} in {}",
                    documentId, versionKey);
            return;
        }
        redisTemplate.opsForHash().put(versionKey, documentId, ledgerValue);
    }

    /**
     * Whether the lease is still ours (renewing it in the same check).
     * Redis answers 1 (renewed) or 0 (lost). A missing or failed renewal
     * fails closed so a stale worker cannot advance the version ledger.
     */
    private boolean ownsDocumentLock(String lockKey, String lockToken) {
        try {
            Long renewed = redisTemplate.execute(
                    RENEW_DOCUMENT_LOCK_SCRIPT,
                    List.of(lockKey),
                    lockToken,
                    String.valueOf(DOCUMENT_LOCK_TTL.toSeconds()));
            return renewed != null && renewed > 0L;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Search document lease renewal unavailable", exception);
        }
    }

    private void releaseDocumentLock(String lockKey, String lockToken) {
        try {
            redisTemplate.execute(RELEASE_DOCUMENT_LOCK_SCRIPT, List.of(lockKey), lockToken);
        } catch (RuntimeException exception) {
            log.warn("Failed to release search document lock {}: {}", lockKey, exception.getMessage());
        }
    }

    /**
     * Whether an incoming versioned operation must be skipped because the
     * ledger already holds a newer state for the document:
     * <ul>
     *   <li>positive ledger value = last written version: skip iff
     *       existing &gt; incoming (equal rewrites, idempotent);</li>
     *   <li>{@code D:T} = tombstone version {@code T} recorded by a DELETE:
     *       skip iff {@code T &gt;= incoming} (including version zero);</li>
     *   <li>negative legacy value = tombstone version {@code -T};</li>
     *   <li>absent/corrupt ledger: never skip (first write or self-heal).</li>
     * </ul>
     */
    private boolean isStale(String existing, long incomingVersion) {
        if (existing == null) {
            return false;
        }
        try {
            if (existing.startsWith(DELETE_LEDGER_PREFIX)) {
                long tombstoneVersion = Long.parseLong(existing.substring(DELETE_LEDGER_PREFIX.length()));
                return tombstoneVersion >= 0L && tombstoneVersion >= incomingVersion;
            }
            long existingVersion = Long.parseLong(existing);
            if (existingVersion < 0) {
                return existingVersion == Long.MIN_VALUE
                        || -existingVersion >= incomingVersion;
            }
            return existingVersion > incomingVersion;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Last-written version for a document, or {@code null} when the ledger
     * has no entry (first write, or index rebuilt). An unparsable ledger
     * value is treated as absent so a corrupt entry cannot wedge the stream.
     */
    private String ledgerVersion(String versionKey, String documentId) {
        try {
            Object existing = redisTemplate.opsForHash().get(versionKey, documentId);
            if (existing == null) {
                return null;
            }
            String value = String.valueOf(existing);
            if (value.startsWith(DELETE_LEDGER_PREFIX)) {
                long tombstoneVersion = Long.parseLong(value.substring(DELETE_LEDGER_PREFIX.length()));
                if (tombstoneVersion < 0L) {
                    return null;
                }
            } else {
                Long.parseLong(value);
            }
            return value;
        } catch (RuntimeException e) {
            log.warn("Ignoring unparsable ledger version for {} in {}", documentId, versionKey);
            return null;
        }
    }
}
