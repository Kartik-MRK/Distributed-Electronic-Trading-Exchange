package com.dete.auth.controller;

import com.dete.auth.dto.AuthResponse;
import com.dete.auth.dto.JwksResponse;
import com.dete.auth.dto.LoginRequest;
import com.dete.auth.dto.LogoutRequest;
import com.dete.auth.dto.RegisterRequest;
import com.dete.auth.dto.TokenRefreshRequest;
import com.dete.auth.dto.UserResponse;
import com.dete.auth.service.AuthService;
import com.dete.auth.service.JwtKeyProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;
  private final JwtKeyProvider jwtKeyProvider;

  public AuthController(AuthService authService, JwtKeyProvider jwtKeyProvider) {
    this.authService = authService;
    this.jwtKeyProvider = jwtKeyProvider;
  }

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    UserResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    String clientIp = extractClientIp(servletRequest);
    AuthResponse response = authService.login(request, clientIp);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
    AuthResponse response = authService.refresh(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
    authService.logout(request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal UUID userId) {
    UserResponse response = authService.getMe(userId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/.well-known/jwks.json")
  public ResponseEntity<JwksResponse> getJwks() {
    return ResponseEntity.ok(jwtKeyProvider.getJwksResponse());
  }

  private String extractClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
