package com.ulticode.common.lifecycle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Test-only child process used by {@link DrainGateSignalIT}. */
public final class DrainGateSignalHarness {

    private DrainGateSignalHarness() {
    }

    public static void main(String[] args) throws Exception {
        Path marker = Path.of(args[0]);
        DrainGate gate = new DrainGate();
        gate.tryEnter();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            gate.beginDrain();
            try {
                Files.writeString(marker,
                        "draining=" + gate.isDraining() + " inFlight=" + gate.inFlight(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                gate.leave();
                Files.writeString(marker, " drained=" + (gate.inFlight() == 0),
                        StandardOpenOption.APPEND);
            } catch (Exception exception) {
                throw new IllegalStateException("drain probe failed", exception);
            }
        }, "drain-signal-hook"));
        System.out.println("READY");
        System.out.flush();
        Thread.sleep(300_000L);
    }
}
