package com.ulticode.common.storage;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/** Auto-configures the single mandatory S3-backed storage port. */
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    public FileStoragePort fileStoragePort(StorageProperties properties) {
        return new S3Storage(properties);
    }

    @Bean
    public StorageReadiness storageReadiness() {
        return new StorageReadiness();
    }

    @Bean
    @Lazy(false)
    public StorageStartupProbe storageStartupProbe(FileStoragePort storage, StorageProperties properties,
                                                   StorageReadiness readiness) {
        // @Lazy(false) is deliberate: with spring.main.lazy-initialization=true (supported by the Core owner-context
        // manager and its journey IT) Spring Boot would otherwise leave this gate lazy, letting the first object
        // operation run before the store was ever verified.
        return new StorageStartupProbe((S3Storage) storage, properties, readiness);
    }
}
