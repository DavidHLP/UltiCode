package com.ulticode.receipt;

import com.ulticode.common.command.CommandReceiptStore;
import com.ulticode.common.command.ReceiptView;
import com.ulticode.common.command.ReceiptWrite;

import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Base bridge from the shared receipt protocol to an owner's persistence
 * calls.
 *
 * <p>Owners supply their own entity conversion and mapper method references,
 * so table names, entity classes, and error semantics stay owner-local; the
 * bridge owns only interface conformance and the missing-entity guard.</p>
 *
 * @param <E> owner receipt entity type
 */
public class CommandReceiptStoreBridge<E> implements CommandReceiptStore {

    private final Function<ReceiptWrite, E> toEntity;
    private final Function<E, ReceiptView> toView;
    private final ToIntFunction<E> insert;
    private final FindByKey<E> findByKey;

    public CommandReceiptStoreBridge(
            Function<ReceiptWrite, E> toEntity,
            Function<E, ReceiptView> toView,
            ToIntFunction<E> insert,
            FindByKey<E> findByKey) {
        this.toEntity = toEntity;
        this.toView = toView;
        this.insert = insert;
        this.findByKey = findByKey;
    }

    @Override
    public int insert(ReceiptWrite receipt) {
        return insert.applyAsInt(toEntity.apply(receipt));
    }

    @Override
    public ReceiptView findByKey(String service, String operation, String idempotencyKey) {
        E entity = findByKey.find(service, operation, idempotencyKey);
        return entity == null ? null : toView.apply(entity);
    }

    /** Owner lookup returning the entity or {@code null} when the key is absent. */
    @FunctionalInterface
    public interface FindByKey<E> {

        E find(String service, String operation, String idempotencyKey);
    }
}
