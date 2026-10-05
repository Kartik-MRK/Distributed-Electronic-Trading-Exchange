package com.dete.marketdata.config;

import com.dete.common.security.JwtAuthenticationFilter;
import com.dete.common.security.JwtTokenValidator;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
  public PublicKey rsaPublicKey() {
    try {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      return gen.generateKeyPair().getPublic();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize RSA public key", e);
    }
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
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/market-data/**", "/ws/**", "/actuator/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
