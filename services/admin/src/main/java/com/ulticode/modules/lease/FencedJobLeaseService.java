package com.ulticode.modules.lease;

import com.ulticode.common.lease.FencedLease;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin adapter for the database-clock-backed fenced lease protocol.
 *
 * <p>All ownership decisions are made by the conditional SQL operations;
 * JVM time is used only to expose the immutable common handle for tests and
 * diagnostics.
 */
@Service
@RequiredArgsConstructor
public class FencedJobLeaseService {

    private static final Pattern LEASE_NAME = Pattern.compile("[A-Za-z0-9:_-]{1,120}");
    private static final long MIN_TTL_MILLIS = 1_000L;
    private static final long MAX_TTL_MILLIS = Duration.ofHours(24).toMillis();

    private final FencedJobLeaseMapper leaseMapper;
    private final Clock clock;

    @Value("${admin.lease.ttl-ms:600000}")
    private long defaultTtlMillis = 600_000L;

    public FencedLease tryAcquire(String leaseName) {
        return tryAcquire(leaseName, Duration.ofMillis(defaultTtlMillis));
    }

    /** Return {@code null} when another live owner holds the lease. */
    public FencedLease tryAcquire(String leaseName, Duration ttl) {
        validateName(leaseName);
        long leaseMicros = toMicros(ttl);
        String ownerToken = "admin-" + UUID.randomUUID();
        leaseMapper.acquireLease(leaseName, ownerToken, leaseMicros);
        FencedJobLease row = leaseMapper.findByName(leaseName);
        if (row == null || row.getFenceToken() == null || row.getLeasedUntil() == null
                || row.getUpdatedAt() == null) {
            throw new IllegalStateException("fenced lease row missing after acquire: " + leaseName);
        }
        if (!ownerToken.equals(row.getOwnerToken())) {
            return null;
        }
        return toCommonLease(row);
    }

    public boolean renew(FencedLease lease) {
        requireLease(lease);
        return leaseMapper.renewLease(lease.name(), lease.ownerToken(),
                lease.fenceToken(), toMicros(Duration.ofMillis(defaultTtlMillis))) == 1;
    }

    public boolean isHeld(FencedLease lease) {
        requireLease(lease);
        return leaseMapper.isHeld(lease.name(), lease.ownerToken(), lease.fenceToken()) == 1;
    }

    public boolean release(FencedLease lease) {
        requireLease(lease);
        return leaseMapper.releaseLease(lease.name(), lease.ownerToken(), lease.fenceToken()) == 1;
    }

    private FencedLease toCommonLease(FencedJobLease row) {
        return new FencedLease(
                row.getLeaseName(),
                row.getFenceToken(),
                row.getOwnerToken(),
                row.getUpdatedAt().atZone(clock.getZone()).toInstant(),
                row.getLeasedUntil().atZone(clock.getZone()).toInstant());
    }

    private static long toMicros(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("lease ttl must be positive");
        }
        long millis = ttl.toMillis();
        if (millis < MIN_TTL_MILLIS || millis > MAX_TTL_MILLIS) {
            throw new IllegalArgumentException("lease ttl must be between 1000ms and 24h");
        }
        return Math.multiplyExact(millis, 1_000L);
    }

    private static void validateName(String leaseName) {
        if (leaseName == null || !LEASE_NAME.matcher(leaseName).matches()) {
            throw new IllegalArgumentException("invalid fenced lease name");
        }
    }

    private static void requireLease(FencedLease lease) {
        if (lease == null) {
            throw new IllegalArgumentException("lease is required");
        }
    }
}
