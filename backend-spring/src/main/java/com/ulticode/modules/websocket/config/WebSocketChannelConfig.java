package com.ulticode.modules.websocket.config;

import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket channel configuration.
 *
 * <p>Configures the JWT authentication interceptor for WebSocket channels.
 */
@Configuration
public class WebSocketChannelConfig implements WebSocketMessageBrokerConfigurer {

  private final JwtChannelInterceptor jwtChannelInterceptor;

  public WebSocketChannelConfig(JwtChannelInterceptor jwtChannelInterceptor) {
    this.jwtChannelInterceptor = jwtChannelInterceptor;
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(jwtChannelInterceptor);
  }
}
