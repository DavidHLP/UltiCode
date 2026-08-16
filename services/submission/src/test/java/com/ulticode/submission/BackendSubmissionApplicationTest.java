package com.ulticode.submission;

import com.ulticode.modules.submission.port.DefaultSubmissionWritePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(
        classes = BackendSubmissionApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration,"
                        + "com.alibaba.cloud.dubbo.bootstrap.DubboBootstrapAutoConfiguration",
                "spring.datasource.url=jdbc:mysql://localhost:1/none?useSSL=false",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"
        })
@DisplayName("Submission owner boot boundary")
class BackendSubmissionApplicationTest {

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedissonClient redissonClient;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("boots the local storage writer without Dubbo")
    void bootsLocalStorageWriter() {
        assertThat(context).isNotNull();
        assertThat(context.getBeansOfType(DefaultSubmissionWritePort.class)).hasSize(1);
    }
}
