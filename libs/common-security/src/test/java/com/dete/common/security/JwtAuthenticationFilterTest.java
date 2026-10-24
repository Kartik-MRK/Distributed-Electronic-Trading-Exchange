package com.dete.common.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {
  @ParameterizedTest
  @ValueSource(strings = {"ADMIN", "ROLE_ADMIN"})
  void normalizesAdminRoleOnce(String role) throws Exception {
    var validator = mock(JwtTokenValidator.class);
    var claims = mock(Claims.class);
    when(validator.validateAndExtract("token")).thenReturn(claims);
    when(validator.extractRoles(claims)).thenReturn(List.of(role));
    when(validator.extractAccountId(claims)).thenReturn(UUID.randomUUID());
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer token");
    try {
      new JwtAuthenticationFilter(validator)
          .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
      assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
          .extracting("authority")
          .containsExactly("ROLE_ADMIN");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
