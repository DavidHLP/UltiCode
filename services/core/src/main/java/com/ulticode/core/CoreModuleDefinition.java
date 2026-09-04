package com.ulticode.core;

/** Immutable definition of one explicitly assembled Owner Module. */
record CoreModuleDefinition(
        String name,
        String environmentPrefix,
        Class<?> bootConfiguration,
        String transactionManagerBean) {

    CoreModuleDefinition {
        if (name == null || name.isBlank()
                || environmentPrefix == null || environmentPrefix.isBlank()
                || bootConfiguration == null) {
            throw new IllegalArgumentException("Core module definition is incomplete");
        }
    }
}
