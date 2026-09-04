package com.ulticode.modules.submission.runtime.async;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded process-local idempotency receipts for async submissions.
 *
 * <p>ponytail: process-local is sufficient for the disabled/local Adapter;
 * shared Redis receipts are required before multi-replica Judge0 rollout.
 */
final class AsyncExecutionReceiptStore {
    private static final int MAX_ENTRIES = 1_024;
    private static final long TTL_NANOS = Duration.ofMinutes(15).toNanos();

    private final Map<String, Receipt> byKey = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Receipt> byHandle = new LinkedHashMap<>(16, 0.75f, true);

    synchronized Receipt findByKey(String key, String fingerprint) {
        prune();
        Receipt receipt = byKey.get(key);
        verifyFingerprint(receipt, fingerprint);
        return receipt;
    }

    synchronized Receipt register(String key, String fingerprint, String handle) {
        prune();
        Receipt existing = byKey.get(key);
        if (existing != null) {
            verifyFingerprint(existing, fingerprint);
            return existing;
        }
        Receipt receipt = new Receipt(key, fingerprint, handle, null, System.nanoTime());
        byKey.put(key, receipt);
        byHandle.put(handle, receipt);
        trimToCapacity();
        return null;
    }

    synchronized void complete(String key, String handle,
                               AsyncSandboxExecutor.ExecutionSnapshot snapshot) {
        prune();
        Receipt current = byKey.get(key);
        if (current == null || !current.handle().equals(handle)) {
            return;
        }
        Receipt completed = new Receipt(
                current.key(), current.fingerprint(), current.handle(), snapshot, System.nanoTime());
        byKey.put(key, completed);
        byHandle.put(handle, completed);
    }

    synchronized Receipt findByHandle(String handle) {
        prune();
        return byHandle.get(handle);
    }

    private void verifyFingerprint(Receipt receipt, String fingerprint) {
        if (receipt != null && !receipt.fingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("idempotency key conflicts with request");
        }
    }

    private void prune() {
        long now = System.nanoTime();
        Iterator<Map.Entry<String, Receipt>> iterator = byKey.entrySet().iterator();
        while (iterator.hasNext()) {
            Receipt receipt = iterator.next().getValue();
            if (now - receipt.createdAtNanos() > TTL_NANOS) {
                iterator.remove();
                byHandle.remove(receipt.handle());
            }
        }
    }

    private void trimToCapacity() {
        while (byKey.size() > MAX_ENTRIES) {
            Iterator<Map.Entry<String, Receipt>> iterator = byKey.entrySet().iterator();
            Map.Entry<String, Receipt> evictable = null;
            while (iterator.hasNext()) {
                Map.Entry<String, Receipt> candidate = iterator.next();
                if (candidate.getValue().snapshot() != null) {
                    evictable = candidate;
                    break;
                }
            }
            if (evictable == null) {
                return;
            }
            byKey.remove(evictable.getKey());
            byHandle.remove(evictable.getValue().handle());
        }
    }

    record Receipt(
            String key,
            String fingerprint,
            String handle,
            AsyncSandboxExecutor.ExecutionSnapshot snapshot,
            long createdAtNanos) {
    }
}
