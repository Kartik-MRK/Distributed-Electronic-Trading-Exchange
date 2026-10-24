package com.dete.marketdata.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dete.common.security.JwtTokenValidator;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

class StompAuthorizationInterceptorTest {
  private final JwtTokenValidator validator = mock(JwtTokenValidator.class);
  private final StompAuthorizationInterceptor interceptor =
      new StompAuthorizationInterceptor(validator);

  private void send(StompHeaderAccessor headers) {
    headers.setLeaveMutable(true);
    interceptor.preSend(
        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
  }

  @Test
  void anonymousClientsCanOnlySubscribeToExactPublicFeeds() {
    var publicFeed = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    publicFeed.setDestination("/topic/trades.BTC-USD");
    send(publicFeed);
    for (String destination :
        new String[] {"/topic/orders." + UUID.randomUUID(), "/topic/**", "/topic/orders.*"}) {
      var privateFeed = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
      privateFeed.setDestination(destination);
      assertThatThrownBy(() -> send(privateFeed)).isInstanceOf(AccessDeniedException.class);
    }
  }

  @Test
  void authenticatedClientCannotReadAnotherAccountOrPublishToBroker() {
    UUID owner = UUID.randomUUID();
    var claims = mock(Claims.class);
    when(validator.validateAndExtract("valid")).thenReturn(claims);
    when(validator.extractAccountId(claims)).thenReturn(owner);
    when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(60)));
    var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
    connect.setNativeHeader("Authorization", "Bearer valid");
    send(connect);
    var own = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    own.setUser(connect.getUser());
    own.setDestination("/topic/orders." + owner);
    send(own);
    var other = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    other.setUser(connect.getUser());
    other.setDestination("/topic/orders." + UUID.randomUUID());
    assertThatThrownBy(() -> send(other)).isInstanceOf(AccessDeniedException.class);
    var publish = StompHeaderAccessor.create(StompCommand.SEND);
    publish.setUser(connect.getUser());
    publish.setDestination("/topic/trades.BTC-USD");
    assertThatThrownBy(() -> send(publish)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void invalidJwtCannotAuthenticate() {
    when(validator.validateAndExtract("invalid")).thenThrow(new IllegalArgumentException());
    var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
    connect.setNativeHeader("Authorization", "Bearer invalid");
    assertThatThrownBy(() -> send(connect)).isInstanceOf(AccessDeniedException.class);
  }
}
