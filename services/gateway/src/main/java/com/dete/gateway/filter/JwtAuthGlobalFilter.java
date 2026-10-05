package com.dete.gateway.filter;

import com.dete.common.security.JwtTokenValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);
  private static final String BEARER_PREFIX = "Bearer ";

  public static final String USER_ID_ATTR = "userId";
  public static final String USERNAME_ATTR = "username";
  public static final String ROLES_ATTR = "roles";

  private final JwtTokenValidator jwtTokenValidator;

  public JwtAuthGlobalFilter(JwtTokenValidator jwtTokenValidator) {
    this.jwtTokenValidator = jwtTokenValidator;
  }

  @Override
  public int getOrder() {
    return -50;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    if (isPublicPath(path)) {
      // If token is optionally present on public path, extract identity for rate-limiting
      if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
        try {
          String token = authHeader.substring(BEARER_PREFIX.length()).trim();
          Claims claims = jwtTokenValidator.validateAndExtract(token);
          exchange.getAttributes().put(USER_ID_ATTR, claims.getSubject());
        } catch (Exception ignored) {
          // Public paths continue unauthenticated
        }
      }
      return chain.filter(exchange);
    }

    // Protected path - token is mandatory
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      return unauthorizedResponse(exchange, "Authentication required to access this resource");
    }

    String token = authHeader.substring(BEARER_PREFIX.length()).trim();
    Claims claims;
    try {
      claims = jwtTokenValidator.validateAndExtract(token);
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("JWT validation failed for path {}: {}", path, e.getMessage());
      return unauthorizedResponse(exchange, "Invalid or expired JWT token");
    }

    UUID accountId;
    try {
      accountId = jwtTokenValidator.extractAccountId(claims);
    } catch (Exception e) {
      return unauthorizedResponse(exchange, "Invalid user identifier in token");
    }

    String username = jwtTokenValidator.extractUsername(claims);
    List<String> roles = jwtTokenValidator.extractRoles(claims);

    // Enforce admin role if accessing admin routes
    if (path.startsWith("/admin/") && (roles == null || !roles.contains("ADMIN"))) {
      log.warn("Access denied to {}: user {} lacks ADMIN role", path, username);
      return forbiddenResponse(exchange, "Administrator role required");
    }

    // Store in exchange attributes for downstream filters (e.g. rate limiter)
    exchange.getAttributes().put(USER_ID_ATTR, accountId.toString());
    exchange.getAttributes().put(USERNAME_ATTR, username != null ? username : "");
    exchange.getAttributes().put(ROLES_ATTR, roles != null ? roles : List.of());

    // Inject headers for downstream microservices
    ServerHttpRequest mutatedRequest =
        exchange
            .getRequest()
            .mutate()
            .header("X-User-Id", accountId.toString())
            .header("X-Account-Id", accountId.toString())
            .header("X-Username", username != null ? username : "")
            .header("X-User-Roles", roles != null ? String.join(",", roles) : "")
            .build();

    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }

  private boolean isPublicPath(String path) {
    return path.startsWith("/auth/")
        || path.startsWith("/market-data/")
        || path.startsWith("/ws/")
        || path.startsWith("/actuator/")
        || path.startsWith("/fallback/")
        || path.startsWith("/internal/metrics/")
        || path.startsWith("/simulator/")
        || path.equals("/internal/metrics/snapshot")
        || path.equals("/auth")
        || path.equals("/market-data")
        || path.equals("/ws");
  }

  private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String body =
        String.format("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\"}", message);
    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(Mono.just(buffer));
  }

  private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String message) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.FORBIDDEN);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String body =
        String.format("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"%s\"}", message);
    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(Mono.just(buffer));
  }
}
