package com.ulticode.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.command.ClaimCommandReceiptStore;
import com.ulticode.common.command.GenericFingerprintStrategy;
import com.ulticode.common.command.ReceiptCommandMetadata;
import com.ulticode.common.command.ReceiptCommandValidation;
import com.ulticode.common.command.ReceiptErrorCatalog;
import com.ulticode.common.command.ReceiptExecutor;
import com.ulticode.common.command.ReceiptFingerprintStrategy;
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
     * Creates the default claim-mutate-finalize executor using the generic
     * fingerprint strategy.
     */
    public static <C extends WriteCommand> ReceiptExecutor<C> claim(
            ClaimCommandReceiptStore store,
            ObjectMapper objectMapper,
            ReceiptErrorCatalog errors,
            Clock clock) {
        return claim(store, objectMapper, new GenericFingerprintStrategy(), errors, clock);
    }

    /**
     * Creates a claim-mutate-finalize executor with an owner-local matcher.
     * New receipts always use the supplied strategy's fingerprint; its
     * compatibility matcher is consulted only while replaying existing rows.
     */
    public static <C extends WriteCommand> ReceiptExecutor<C> claim(
            ClaimCommandReceiptStore store,
            ObjectMapper objectMapper,
            ReceiptFingerprintStrategy<? super C> fingerprintStrategy,
            ReceiptErrorCatalog errors,
            Clock clock) {
        return ReceiptExecutor.claimMutateFinalize(
                store,
                new JacksonReceiptPayloadCodec(objectMapper),
                fingerprintStrategy,
                errors,
                ReceiptCommandMetadata::from,
                ReceiptCommandValidation::delegatedCommand,
                clock);
    }
}
