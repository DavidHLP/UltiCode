package com.ulticode.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.command.ClaimCommandReceiptStore;
import com.ulticode.common.command.GenericFingerprintStrategy;
import com.ulticode.common.command.ReceiptCommandMetadata;
import com.ulticode.common.command.ReceiptCommandValidation;
import com.ulticode.common.command.ReceiptErrorCatalog;
import com.ulticode.common.command.ReceiptExecutor;
import com.ulticode.common.command.WriteCommand;

import java.time.Clock;

/**
 * Owner-side construction profile for the shared receipt protocol.
 *
 * <p>Collapses the assembly every claim-based owner repeated: the Jackson
 * payload codec, the shared request fingerprint, metadata projection, and
 * delegated-command validation. Owners still supply their persistence
 * adapter, error namespace, and clock. The Jackson codec deliberately lives
 * here rather than in backend-common, which must remain dependency-free.</p>
 */
public final class ReceiptExecutorFactory {

    private ReceiptExecutorFactory() {
    }

    /**
     * Creates the claim-mutate-finalize executor used by delegated owner
     * commands: a processing claim is reserved before the mutation and
     * finalized or deleted by the shared protocol.
     */
    public static <C extends WriteCommand> ReceiptExecutor<C> claim(
            ClaimCommandReceiptStore store,
            ObjectMapper objectMapper,
            ReceiptErrorCatalog errors,
            Clock clock) {
        return ReceiptExecutor.claimMutateFinalize(
                store,
                new JacksonReceiptPayloadCodec(objectMapper),
                new GenericFingerprintStrategy(),
                errors,
                ReceiptCommandMetadata::from,
                ReceiptCommandValidation::delegatedCommand,
                clock);
    }
}
