package com.dete.audit.config;

import com.dete.common.security.JwtAuthenticationFilter;
import com.dete.common.security.JwtTokenValidator;
import jakarta.servlet.http.HttpServletResponse;
import java.security.PublicKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  @ConditionalOnMissingBean
  public PublicKey rsaPublicKey(
      @org.springframework.beans.factory.annotation.Value("${security.jwt.public-key:}")
          String publicKeyB64) {
    if (publicKeyB64 != null && !publicKeyB64.isBlank()) {
      return com.dete.common.security.RsaKeyUtils.parsePublicKey(publicKeyB64);
    }
    return com.dete.common.security.RsaKeyUtils.parsePublicKey(
        com.dete.common.security.RsaKeyUtils.DEFAULT_PUBLIC_KEY);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtTokenValidator jwtTokenValidator(PublicKey rsaPublicKey) {
    return new JwtTokenValidator(rsaPublicKey);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator) {
    return new JwtAuthenticationFilter(jwtTokenValidator);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, authException) -> {
                          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response
                              .getWriter()
                              .write(
                                  "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication is required to access audit resources\"}");
                        })
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response
                              .getWriter()
                              .write(
                                  "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Admin role required to access audit resources\"}");
                        }))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers("/admin/audit", "/admin/audit/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
