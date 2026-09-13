package com.ulticode.modules.reconciliation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** Shared bounded paging and parent-existence logic for owner orphan scans. */
final class OrphanScan {

    private static final int PARENT_LOOKUP_BATCH_SIZE = 500;

    private OrphanScan() {
    }

    static <T> KeysetResult keyset(
            String initialCursor,
            int pageSize,
            int maxPages,
            KeysetPage<T> page,
            Function<T, String> key,
            ToLongFunction<T> rowCount,
            Function<Set<String>, Set<String>> existingIds) {
        validateArguments(pageSize, maxPages);
        String cursor = initialCursor;
        long missing = 0L;
        int pages = 0;
        while (pages < maxPages) {
            List<T> references = page.read(cursor, pageSize);
            validatePage(references, pageSize);
            if (references.isEmpty()) {
                return new KeysetResult(missing, cursor, true);
            }

            String previous = cursor;
            Set<String> candidates = new LinkedHashSet<>();
            for (T reference : references) {
                String candidate = validateReference(reference, key, rowCount, previous);
                if (!candidates.add(candidate)) {
                    throw new InvalidPageException("orphan scan page contains duplicate keys");
                }
                previous = candidate;
            }
            Set<String> existing = lookupInBatches(candidates, existingIds);
            for (T reference : references) {
                if (!existing.contains(key.apply(reference))) {
                    missing += rowCount.applyAsLong(reference);
                }
            }
            cursor = previous;
            pages++;
            if (references.size() < pageSize) {
                return new KeysetResult(missing, cursor, true);
            }
        }
        return new KeysetResult(missing, cursor, false);
    }

    static <T> OffsetResult offset(
            int pageSize,
            int maxPages,
            OffsetPage<T> page,
            Function<T, String> key,
            ToLongFunction<T> rowCount,
            Function<Set<String>, Set<String>> existingIds) {
        validateArguments(pageSize, maxPages);
        int offset = 0;
        String previous = "";
        long missing = 0L;
        int pages = 0;
        while (pages < maxPages) {
            List<T> references = page.read(offset, pageSize);
            validatePage(references, pageSize);
            if (references.isEmpty()) {
                return new OffsetResult(missing, offset, true);
            }

            Set<String> candidates = new LinkedHashSet<>();
            for (T reference : references) {
                String candidate = validateReference(reference, key, rowCount, previous);
                if (!candidates.add(candidate)) {
                    throw new InvalidPageException("orphan scan page contains duplicate keys");
                }
                previous = candidate;
            }
            Set<String> existing = lookupInBatches(candidates, existingIds);
            for (T reference : references) {
                if (!existing.contains(key.apply(reference))) {
                    missing += rowCount.applyAsLong(reference);
                }
            }
            pages++;
            if (references.size() < pageSize) {
                return new OffsetResult(missing, offset, true);
            }
            try {
                offset = Math.addExact(offset, pageSize);
            } catch (ArithmeticException exception) {
                throw new InvalidPageException("orphan scan offset exceeds integer range");
            }
        }
        return new OffsetResult(missing, offset, false);
    }

    private static void validateArguments(int pageSize, int maxPages) {
        if (pageSize <= 0 || maxPages <= 0) {
            throw new InvalidPageException("orphan scan page budget must be positive");
        }
    }

    private static <T> String validateReference(
            T reference,
            Function<T, String> key,
            ToLongFunction<T> rowCount,
            String previous) {
        if (reference == null) {
            throw new InvalidPageException("orphan scan page contains null row");
        }
        String candidate = key.apply(reference);
        if (candidate == null || candidate.isBlank() || rowCount.applyAsLong(reference) < 0
                || candidate.compareTo(previous) <= 0) {
            throw new InvalidPageException("orphan scan page is not ordered or contains invalid data");
        }
        return candidate;
    }

    private static void validatePage(List<?> references, int pageSize) {
        if (references == null || references.size() > pageSize || pageSize <= 0) {
            throw new InvalidPageException("orphan scan page is unavailable or oversized");
        }
    }

    private static Set<String> lookupInBatches(
            Set<String> candidates,
            Function<Set<String>, Set<String>> existingIds) {
        Set<String> existing = new LinkedHashSet<>();
        List<String> ids = new ArrayList<>(candidates);
        for (int start = 0; start < ids.size(); start += PARENT_LOOKUP_BATCH_SIZE) {
            Set<String> batch = Set.copyOf(
                    ids.subList(start, Math.min(start + PARENT_LOOKUP_BATCH_SIZE, ids.size())));
            Set<String> batchExisting = existingIds.apply(batch);
            if (batchExisting == null) {
                throw new InvalidPageException("orphan parent lookup is unavailable");
            }
            existing.addAll(batchExisting);
        }
        return existing;
    }

    @FunctionalInterface
    interface KeysetPage<T> {
        List<T> read(String afterKey, int pageSize);
    }

    @FunctionalInterface
    interface OffsetPage<T> {
        List<T> read(int offset, int pageSize);
    }

    record KeysetResult(long missing, String nextCursor, boolean complete) {
    }

    record OffsetResult(long missing, int nextOffset, boolean complete) {
    }

    static final class InvalidPageException extends IllegalArgumentException {
        InvalidPageException(String message) {
            super(message);
        }
    }
}
