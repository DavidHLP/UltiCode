package com.ulticode.receipt;

import com.ulticode.common.command.ClaimCommandReceiptStore;
import com.ulticode.common.command.ReceiptView;
import com.ulticode.common.command.ReceiptWrite;

import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

/**
 * Claim-protocol extension of {@link CommandReceiptStoreBridge}: adds the
 * conditional finalize/delete forwarding that claim owners need.
 *
 * @param <E> owner receipt entity type
 */
public class ClaimCommandReceiptStoreBridge<E> extends CommandReceiptStoreBridge<E>
        implements ClaimCommandReceiptStore {

    private final ToIntBiFunction<String, String> markSuccess;
    private final ToIntFunction<String> deleteClaim;

    public ClaimCommandReceiptStoreBridge(
            Function<ReceiptWrite, E> toEntity,
            Function<E, ReceiptView> toView,
            ToIntFunction<E> insert,
            FindByKey<E> findByKey,
            ToIntBiFunction<String, String> markSuccess,
            ToIntFunction<String> deleteClaim) {
        super(toEntity, toView, insert, findByKey);
        this.markSuccess = markSuccess;
        this.deleteClaim = deleteClaim;
    }

    @Override
    public int markSuccess(String id, String resultPayload) {
        return markSuccess.applyAsInt(id, resultPayload);
    }

    @Override
    public int deleteClaim(String id) {
        return deleteClaim.applyAsInt(id);
    }
}
