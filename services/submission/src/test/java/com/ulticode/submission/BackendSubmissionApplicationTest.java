package com.ulticode.submission;

import com.ulticode.submission.compat.SubmissionFenceCompatibilityProvider;
import com.ulticode.submission.compat.SubmissionWriteCompatibilityProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(
        classes = BackendSubmissionApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration,"
                        + "com.alibaba.cloud.dubbo.bootstrap.DubboBootstrapAutoConfiguration"
        })
@DisplayName("Submission owner boot boundary")
class BackendSubmissionApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("boots without a business datasource and keeps compatibility providers test-disabled")
    void bootsWithoutBusinessDatabase() {
        assertThat(context).isNotNull();
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(SubmissionWriteCompatibilityProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(SubmissionFenceCompatibilityProvider.class)).isEmpty();
    }
}
