package com.ulticode.app.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueueHealthSnapshotDTOTest {
    @Test
    void builderAndNoArgConstructorHaveTheSameDefaults() {
        QueueHealthSnapshotDTO snapshot = QueueHealthSnapshotDTO.builder().build();
        assertThat(snapshot.getWaitingDepth()).isZero();
        assertThat(snapshot.getFailedCount()).isZero();
        assertThat(snapshot.getCompletedCount()).isZero();
        assertThat(snapshot.getProbeStatus()).isEqualTo(ProbeStatus.OK);
        assertThat(new QueueHealthSnapshotDTO().getProbeStatus()).isEqualTo(ProbeStatus.OK);
        assertThat(QueueHealthSnapshotDTO.builder().probeStatus(ProbeStatus.PROBE_FAILED)
                .build().getProbeStatus()).isEqualTo(ProbeStatus.PROBE_FAILED);
    }
}
