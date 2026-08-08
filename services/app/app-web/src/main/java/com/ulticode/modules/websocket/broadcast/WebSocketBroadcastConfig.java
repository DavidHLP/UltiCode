package com.ulticode.modules.websocket.broadcast;

import com.ulticode.modules.websocket.config.WebSocketProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Spring configuration for Redis WebSocket broadcast listener container.
 *
 * <p>Conditioned on {@code app.websocket.broadcast.enabled=true}.
 *
 * @author ulticode
 */
@Configuration
@ConditionalOnProperty(prefix = "app.websocket.broadcast", name = "enabled", havingValue = "true")
public class WebSocketBroadcastConfig {

  @Bean
  public RedisMessageListenerContainer wsBroadcastMessageListenerContainer(
      ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
      WebSocketBroadcastListener listener,
      WebSocketProperties properties) {
    RedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
    if (connectionFactory == null) {
      return null;
    }
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    String channel =
        properties.getBroadcast() != null
            ? properties.getBroadcast().getChannel()
            : "ulticode:ws:broadcast";
    container.addMessageListener(new MessageListenerAdapter(listener), new ChannelTopic(channel));
    return container;
  }
}
