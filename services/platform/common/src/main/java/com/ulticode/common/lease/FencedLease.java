package com.ulticode.common.lease;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable fencing handle for work that must not be completed by a stale
 * runner.
 *
 * <p>The database adapter owns the atomic compare-and-swap. This value keeps
 * the shared rules deterministic: an active lease cannot be duplicated, a
 * fence token must increase after expiry, and an uncertain clock stops work
 * before the expiry boundary.
 */
public record FencedLease(
        String name,
        long fenceToken,
        String ownerToken,
        Instant leasedAt,
        Instant leasedUntil) {

    public FencedLease {
        requireText(name, "name");
        requireText(ownerToken, "ownerToken");
        if (fenceToken < 1) {
            throw new IllegalArgumentException("fenceToken must be positive");
        }
        Objects.requireNonNull(leasedAt, "leasedAt");
        Objects.requireNonNull(leasedUntil, "leasedUntil");
        if (!leasedUntil.isAfter(leasedAt)) {
            throw new IllegalArgumentException("leasedUntil must be after leasedAt");
        }
    }

    /** Return whether the lease has expired at the supplied instant. */
    public boolean isExpiredAt(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(leasedUntil);
    }

    /**
     * Return whether this token is safe to use for a database-side completion.
     * The skew window is applied on both sides: a clock that moved backwards
     * beyond the tolerated window or is too close to expiry fails closed.
     */
    public boolean permits(String candidateOwner, long candidateFence,
                           Instant now, Duration maxClockSkew) {
        if (candidateOwner == null || candidateFence != fenceToken) {
            return false;
        }
        Duration skew = requireNonNegative(maxClockSkew, "maxClockSkew");
        Instant observed = Objects.requireNonNull(now, "now");
        return ownerToken.equals(candidateOwner)
                && !observed.isBefore(leasedAt.minus(skew))
                && observed.plus(skew).isBefore(leasedUntil);
    }

    /** Renew this token only while it remains safe to use. */
    public Optional<FencedLease> renew(String candidateOwner, long candidateFence,
                                       Instant now, Duration ttl, Duration maxClockSkew) {
        Instant observed = Objects.requireNonNull(now, "now");
        Duration requestedTtl = requirePositive(ttl, "ttl");
        if (!permits(candidateOwner, candidateFence, observed, maxClockSkew)) {
            return Optional.empty();
        }
        return Optional.of(new FencedLease(
                name, fenceToken, ownerToken, leasedAt, observed.plus(requestedTtl)));
    }

    /**
     * Model the atomic acquire decision performed by a persistent adapter.
     * The adapter must provide the next token from its serialized row update.
     */
    public static Optional<FencedLease> tryAcquire(
            FencedLease current, String name, String ownerToken, long nextFenceToken,
            Instant now, Duration ttl) {
        Instant observed = Objects.requireNonNull(now, "now");
        Duration requestedTtl = requirePositive(ttl, "ttl");
        requireText(name, "name");
        requireText(ownerToken, "ownerToken");
        if (current != null) {
            if (!current.isExpiredAt(observed)) {
                return Optional.empty();
            }
            if (nextFenceToken <= current.fenceToken()) {
                throw new IllegalArgumentException("nextFenceToken must increase after expiry");
            }
        } else if (nextFenceToken < 1) {
            throw new IllegalArgumentException("nextFenceToken must be positive");
        }
        return Optional.of(new FencedLease(
                name, nextFenceToken, ownerToken, observed, observed.plus(requestedTtl)));
    }

    private static Duration requirePositive(Duration duration, String name) {
        Duration value = requireNonNegative(duration, name);
        if (value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Duration requireNonNegative(Duration duration, String name) {
        Duration value = Objects.requireNonNull(duration, name);
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
