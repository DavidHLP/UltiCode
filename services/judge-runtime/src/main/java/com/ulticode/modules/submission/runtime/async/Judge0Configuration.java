package com.ulticode.modules.submission.runtime.async;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers optional Judge0 configuration without enabling the Adapter. */
@Configuration
@EnableConfigurationProperties(Judge0Properties.class)
public class Judge0Configuration {
}
