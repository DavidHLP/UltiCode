package com.ulticode.core;

/** Immutable definition of one explicitly assembled Owner Module. */
record CoreModuleDefinition(
        String name,
        String environmentPrefix,
        Class<?> bootConfiguration,
        String transactionManagerBean,
        String ownerArtifactId,
        boolean enabled) {

    CoreModuleDefinition(
            String name,
            String environmentPrefix,
            Class<?> bootConfiguration,
            String transactionManagerBean,
            String ownerArtifactId) {
        this(name, environmentPrefix, bootConfiguration, transactionManagerBean, ownerArtifactId, true);
    }

    CoreModuleDefinition {
        if (name == null || name.isBlank()
                || environmentPrefix == null || environmentPrefix.isBlank()
                || bootConfiguration == null
                || ownerArtifactId == null || ownerArtifactId.isBlank()) {
            throw new IllegalArgumentException("Core module definition is incomplete");
        }
    }
}
