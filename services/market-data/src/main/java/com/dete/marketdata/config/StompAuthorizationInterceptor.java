package com.dete.marketdata.config;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.security.JwtTokenValidator;
import com.dete.marketdata.model.Interval;
import java.time.Instant;
import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/** Public market feeds are anonymous; private subscriptions must belong to the JWT subject. */
@Component
public class StompAuthorizationInterceptor implements ChannelInterceptor {
  private final JwtTokenValidator validator;

  public StompAuthorizationInterceptor(JwtTokenValidator validator) {
    this.validator = validator;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (headers == null) return message;
    if (headers.getCommand() == StompCommand.CONNECT) {
      String authorization = headers.getFirstNativeHeader("Authorization");
      if (authorization != null) {
        if (!authorization.startsWith("Bearer ")) throw denied();
        try {
          var claims = validator.validateAndExtract(authorization.substring(7).trim());
          var authentication =
              new UsernamePasswordAuthenticationToken(
                  validator.extractAccountId(claims), null, List.of());
          authentication.setDetails(claims.getExpiration().toInstant());
          headers.setUser(authentication);
        } catch (RuntimeException ex) {
          throw denied();
        }
      }
    } else if (headers.getCommand() == StompCommand.SUBSCRIBE) {
      String destination = headers.getDestination();
      if (isPublicDestination(destination)) return message;
      if (!(headers.getUser() instanceof UsernamePasswordAuthenticationToken user)
          || !user.isAuthenticated()
          || !(user.getDetails() instanceof Instant expiry)
          || !expiry.isAfter(Instant.now())
          || !("/topic/orders." + user.getName()).equals(destination)) throw denied();
    } else if (headers.getCommand() == StompCommand.SEND) {
      // No client-published destinations exist. In particular, never relay client data to /topic.
      throw denied();
    }
    return message;
  }

  private boolean isPublicDestination(String destination) {
    for (Instrument instrument : Instrument.values()) {
      for (String symbol : List.of(instrument.name(), instrument.symbol())) {
        if (("/topic/orderbook." + symbol).equals(destination)
            || ("/topic/trades." + symbol).equals(destination)) return true;
        for (Interval interval : Interval.values()) {
          if (("/topic/candles." + symbol + "." + interval.getCode()).equals(destination))
            return true;
        }
      }
    }
    return false;
  }

  private AccessDeniedException denied() {
    return new AccessDeniedException("WebSocket destination is not authorized");
  }
}
