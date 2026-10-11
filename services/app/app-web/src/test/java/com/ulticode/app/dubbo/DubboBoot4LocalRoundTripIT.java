package com.ulticode.app.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.app.dubbo.boot4fixture.LocalEchoPort;
import com.ulticode.app.dubbo.boot4fixture.LocalEchoService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
        classes = DubboBoot4LocalRoundTripIT.TestApplication.class,
        properties = {
                "dubbo.application.name=boot4-dubbo-local-roundtrip-test",
                "dubbo.application.qos-enable=false",
                "dubbo.registry.address=N/A",
                "dubbo.protocol.port=-1"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DubboBoot4LocalRoundTripIT {

    @Autowired
    private EchoClient client;

    @Test
    @Timeout(15)
    void bootAutoConfigurationWiresLocalServiceReferenceWithoutRegistry() {
        assertThat(client.echo("boot4")).isEqualTo("echo:boot4");
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableDubbo(scanBasePackageClasses = LocalEchoService.class)
    @ImportAutoConfiguration(DubboAutoConfiguration.class)
    @Import(ClientConfiguration.class)
    static class TestApplication {
    }

    static class EchoClient {

        @DubboReference(interfaceClass = LocalEchoPort.class, scope = "local", check = false, retries = 0)
        private LocalEchoPort echoPort;

        String echo(String value) {
            return echoPort.echo(value);
        }
    }

    static class ClientConfiguration {

        @Bean
        EchoClient echoClient() {
            return new EchoClient();
        }
    }
}
