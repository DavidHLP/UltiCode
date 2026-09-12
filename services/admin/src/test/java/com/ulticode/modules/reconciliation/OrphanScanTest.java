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

        long missing = OrphanScan.keyset(
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

        assertThat(missing).isEqualTo(1);
        assertThat(cursors).containsExactly("", "b");
        assertThat(parentLookups.get()).isEqualTo(2);
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
