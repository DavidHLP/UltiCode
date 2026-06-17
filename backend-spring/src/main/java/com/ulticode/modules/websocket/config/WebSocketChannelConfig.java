package com.ulticode.modules.websocket.config;

import com.ulticode.modules.websocket.interceptor.ContestSubscribeAuthInterceptor;
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
  /** R6.4 / F-17: per-topic authorization for /topic/contest/{id} SUBSCRIBEs. */
  private final ContestSubscribeAuthInterceptor contestSubscribeAuthInterceptor;

  public WebSocketChannelConfig(
      JwtChannelInterceptor jwtChannelInterceptor,
      ContestSubscribeAuthInterceptor contestSubscribeAuthInterceptor) {
    this.jwtChannelInterceptor = jwtChannelInterceptor;
    this.contestSubscribeAuthInterceptor = contestSubscribeAuthInterceptor;
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    // Order matters: JWT validates session first, then ContestSubscribeAuthInterceptor
    // can rely on a non-null user in session attributes.
    registration.interceptors(jwtChannelInterceptor, contestSubscribeAuthInterceptor);
  }
}
