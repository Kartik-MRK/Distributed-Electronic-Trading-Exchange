package com.dete.auth.config;

import com.dete.auth.service.JwtKeyProvider;
import com.dete.common.security.JwtAuthenticationFilter;
import com.dete.common.security.JwtTokenValidator;
import jakarta.servlet.http.HttpServletResponse;
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
  public JwtTokenValidator jwtTokenValidator(JwtKeyProvider jwtKeyProvider) {
    return new JwtTokenValidator(jwtKeyProvider.getPublicKey());
  }

  @Bean
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
                exceptions.authenticationEntryPoint(
                    (request, response, authException) -> {
                      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                      response
                          .getWriter()
                          .write(
                              "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Full authentication is required to access this resource\"}");
                    }))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/auth/register",
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/logout",
                        "/auth/.well-known/**",
                        "/actuator/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
