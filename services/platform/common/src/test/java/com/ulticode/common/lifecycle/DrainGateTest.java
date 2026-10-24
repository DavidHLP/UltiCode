package com.ulticode.common.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DrainGate")
class DrainGateTest {

    @Test
    @DisplayName("shutdown rejects new cycles while admitted work drains")
    void shutdownStopsNewWorkAndDrainsExistingWork() {
        DrainGate gate = new DrainGate();

        assertThat(gate.tryEnter()).isTrue();
        gate.beginDrain();

        assertThat(gate.isDraining()).isTrue();
        assertThat(gate.tryEnter()).isFalse();
        assertThat(gate.inFlight()).isEqualTo(1);

        gate.leave();

        assertThat(gate.inFlight()).isZero();
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
