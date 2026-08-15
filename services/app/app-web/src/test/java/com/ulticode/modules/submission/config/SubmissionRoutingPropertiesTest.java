package com.ulticode.modules.submission.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Submission route flag")
class SubmissionRoutingPropertiesTest {

    @Test
    @DisplayName("defaults to the local single-writer path")
    void defaultsToLocal() {
        SubmissionRoutingProperties properties = new SubmissionRoutingProperties();

        assertThat(properties.getMode()).isEqualTo(SubmissionRoutingProperties.LOCAL);
        assertThat(properties.isRemote()).isFalse();
        properties.validate();
    }

    @Test
    @DisplayName("accepts the documented remote mode")
    void acceptsRemoteMode() {
        SubmissionRoutingProperties properties = new SubmissionRoutingProperties();
        properties.setMode("remote");

        properties.validate();

        assertThat(properties.isRemote()).isTrue();
    }

    @Test
    @DisplayName("rejects an unknown mode before the App starts")
    void rejectsUnknownMode() {
        SubmissionRoutingProperties properties = new SubmissionRoutingProperties();
        properties.setMode("dual");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local")
                .hasMessageContaining("remote");
    }
}
