package com.ulticode.core;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Explicit imports keep each Owner mapper set tied to one factory. */
@Configuration(proxyBeanMethods = false)
@Import(CoreOwnerDataSourceConfiguration.class)
public class CoreAssemblyConfiguration {
}
