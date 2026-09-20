package com.ulticode.common.storage;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Auto-configures the single mandatory S3-backed storage port. */
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    public FileStoragePort fileStoragePort(StorageProperties properties) {
        return new S3Storage(properties);
    }

    @Bean
    public StorageStartupProbe storageStartupProbe(FileStoragePort storage, StorageProperties properties) {
        return new StorageStartupProbe((S3Storage) storage, properties);
    }
}
