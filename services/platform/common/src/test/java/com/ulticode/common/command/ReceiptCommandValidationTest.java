package com.ulticode.common.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Receipt command validation")
class ReceiptCommandValidationTest {

    private static final TraceMetadata TRACE = new TraceMetadata("trace-1", null, null, null);
    private static final ActorDelegation ACTOR =
            new ActorDelegation("ADMIN", "actor-1", "delegator-1", null);
    private static final IdMetadata KEY = IdMetadata.of("key-1", null);

    @Test
    void acceptsDelegatedCommandWithKeyTraceAndActor() {
        assertThat(ReceiptCommandValidation.delegatedCommand(command(KEY, TRACE, ACTOR))).isTrue();
    }

    @Test
    void rejectsMissingOrBlankPieces() {
        assertThat(ReceiptCommandValidation.delegatedCommand(null)).isFalse();
        assertThat(ReceiptCommandValidation.delegatedCommand(command(null, TRACE, ACTOR))).isFalse();
        assertThat(ReceiptCommandValidation.delegatedCommand(
                command(IdMetadata.of(" ", null), TRACE, ACTOR))).isFalse();
        assertThat(ReceiptCommandValidation.delegatedCommand(command(KEY, null, ACTOR))).isFalse();
        assertThat(ReceiptCommandValidation.delegatedCommand(command(KEY, TRACE, null))).isFalse();
        assertThat(ReceiptCommandValidation.delegatedCommand(command(KEY, TRACE,
                new ActorDelegation("ADMIN", " ", "delegator-1", null)))).isFalse();
        assertThat(ReceiptCommandValidation.delegatedCommand(command(KEY, TRACE,
                new ActorDelegation("ADMIN", "actor-1", null, null)))).isFalse();
        assertThat(ReceiptCommandValidation.delegatedCommand(command(KEY, TRACE,
                new ActorDelegation("ADMIN", "actor-1", " ", null)))).isFalse();
    }

    private static TestCommand command(IdMetadata idempotency, TraceMetadata trace, ActorDelegation actor) {
        return new TestCommand("command-1", idempotency, actor, trace);
    }

    private record TestCommand(
            String commandId,
            IdMetadata idempotency,
            ActorDelegation actor,
            TraceMetadata trace) implements WriteCommand {
    }
}
