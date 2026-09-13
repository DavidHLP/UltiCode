package com.ulticode.modules.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OrphanScanTest {

    @Test
    void keysetScanPagesInOrderAndChecksParentsInBatches() {
        List<List<Reference>> pages = List.of(
                List.of(new Reference("a", 2), new Reference("b", 1)),
                List.of(new Reference("c", 3)),
                List.of());
        List<String> cursors = new ArrayList<>();
        AtomicInteger page = new AtomicInteger();
        AtomicInteger parentLookups = new AtomicInteger();

        OrphanScan.KeysetResult result = OrphanScan.keyset(
                "",
                2,
                4,
                (after, ignored) -> {
                    cursors.add(after);
                    return pages.get(page.getAndIncrement());
                },
                Reference::id,
                Reference::count,
                candidates -> {
                    parentLookups.incrementAndGet();
                    return Set.of("a", "c");
                });

        assertThat(result.missing()).isEqualTo(1);
        assertThat(result.complete()).isTrue();
        assertThat(cursors).containsExactly("", "b");
        assertThat(parentLookups.get()).isEqualTo(2);
    }

    @Test
    void keysetScanReturnsAContinuationAfterItsPageBudget() {
        AtomicInteger page = new AtomicInteger();

        OrphanScan.KeysetResult first = OrphanScan.keyset(
                "",
                1,
                32,
                (after, ignored) -> {
                    int index = page.getAndIncrement();
                    return index == 33
                            ? List.of()
                            : List.of(new Reference("user-%02d".formatted(index), 1));
                },
                Reference::id,
                Reference::count,
                candidates -> Set.of());

        assertThat(first.missing()).isEqualTo(32);
        assertThat(first.nextCursor()).isEqualTo("user-31");
        assertThat(first.complete()).isFalse();

        OrphanScan.KeysetResult second = OrphanScan.keyset(
                first.nextCursor(),
                1,
                32,
                (after, ignored) -> {
                    int index = page.getAndIncrement();
                    return index == 33
                            ? List.of()
                            : List.of(new Reference("user-%02d".formatted(index), 1));
                },
                Reference::id,
                Reference::count,
                candidates -> Set.of());

        assertThat(second.missing()).isEqualTo(1);
        assertThat(second.complete()).isTrue();
        assertThat(page).hasValue(34);
    }

    @Test
    void offsetScanReturnsAContinuationAfterItsPageBudget() {
        AtomicInteger page = new AtomicInteger();

        OrphanScan.OffsetResult first = OrphanScan.offset(
                1,
                32,
                (offset, ignored) -> {
                    page.incrementAndGet();
                    return offset == 33
                            ? List.of()
                            : List.of(new Reference("user-%02d".formatted(offset), 1));
                },
                Reference::id,
                Reference::count,
                candidates -> Set.of());

        assertThat(first.missing()).isEqualTo(32);
        assertThat(first.nextOffset()).isEqualTo(32);
        assertThat(first.complete()).isFalse();

        OrphanScan.OffsetResult second = OrphanScan.offset(
                1,
                32,
                (offset, ignored) -> {
                    page.incrementAndGet();
                    return offset == 0
                            ? List.of(new Reference("user-32", 1))
                            : List.of();
                },
                Reference::id,
                Reference::count,
                candidates -> Set.of());

        assertThat(second.missing()).isEqualTo(1);
        assertThat(second.complete()).isTrue();
        assertThat(page).hasValue(34);
    }

    @Test
    void rejectsUnorderedOrDuplicatePageKeys() {
        assertThatThrownBy(() -> OrphanScan.keyset(
                "",
                3,
                1,
                (after, ignored) -> List.of(new Reference("b", 1), new Reference("a", 1)),
                Reference::id,
                Reference::count,
                candidates -> Set.of()))
                .isInstanceOf(OrphanScan.InvalidPageException.class);
    }

    private record Reference(String id, long count) {
    }
}
