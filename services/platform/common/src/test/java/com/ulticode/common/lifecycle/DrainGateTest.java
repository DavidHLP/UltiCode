package com.ulticode.common.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DrainGate")
class DrainGateTest {

    @Test
    @DisplayName("shutdown rejects new cycles while admitted work drains")
    void shutdownStopsNewWorkAndDrainsExistingWork() throws Exception {
        DrainGate gate = new DrainGate();

        assertThat(gate.tryEnter()).isTrue();
        gate.beginDrain();

        assertThat(gate.isDraining()).isTrue();
        assertThat(gate.tryEnter()).isFalse();
        assertThat(gate.awaitDrained(Duration.ofMillis(10))).isFalse();

        gate.leave();

        assertThat(gate.inFlight()).isZero();
        assertThat(gate.awaitDrained(Duration.ofSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("a shutdown race cannot admit work after the drain flag wins")
    void shutdownRaceFailsClosed() {
        DrainGate gate = new DrainGate();
        gate.beginDrain();

        assertThat(gate.tryEnter()).isFalse();
        assertThat(gate.inFlight()).isZero();
    }
}
