package com.ulticode.app.dubbo.boot4fixture;

import org.apache.dubbo.config.annotation.DubboService;

@DubboService(
        interfaceClass = LocalEchoPort.class,
        scope = "local",
        register = false)
public class LocalEchoService implements LocalEchoPort {

    @Override
    public String echo(String value) {
        return "echo:" + value;
    }
}
