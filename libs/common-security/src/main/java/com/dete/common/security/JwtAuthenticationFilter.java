package com.dete.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security filter that validates the RS256 JWT on every incoming request.
 *
 * <p>Wired into the security filter chain of every service (except Auth Service itself). On
 * success, populates the SecurityContext with the authenticated user's ID and roles. On failure,
 * lets the request continue without authentication — Spring Security will enforce access control at
 * the endpoint level.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenValidator validator;

  public JwtAuthenticationFilter(JwtTokenValidator validator) {
    this.validator = validator;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      // No token present — continue unauthenticated; endpoint security handles access control
      chain.doFilter(request, response);
      return;
    }

    String token = authHeader.substring(BEARER_PREFIX.length()).trim();

    try {
      Claims claims = validator.validateAndExtract(token);

      List<String> roles = validator.extractRoles(claims);
      var authorities = roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();

      var authentication =
          new UsernamePasswordAuthenticationToken(
              validator.extractAccountId(claims), // principal = accountId (UUID)
              null,
              authorities);

      // Store username in details for logging
      authentication.setDetails(validator.extractUsername(claims));

      SecurityContextHolder.getContext().setAuthentication(authentication);
      log.debug("Authenticated user: {} roles: {}", validator.extractUsername(claims), roles);

    } catch (JwtException e) {
      log.debug("JWT authentication failed: {}", e.getMessage());
      // Clear any stale context
      SecurityContextHolder.clearContext();
    }

    chain.doFilter(request, response);
  }
}
