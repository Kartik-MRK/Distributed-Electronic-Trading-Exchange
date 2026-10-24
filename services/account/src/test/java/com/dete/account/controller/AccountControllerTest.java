package com.dete.account.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dete.account.service.AccountService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountControllerTest {
  @Test
  void balancesRequireOwnerOrAdmin() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID stranger = UUID.randomUUID();
    var service = mock(AccountService.class);
    var mvc =
        MockMvcBuilders.standaloneSetup(new AccountController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    var user =
        new UsernamePasswordAuthenticationToken(
            stranger, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    mvc.perform(get("/accounts/" + owner + "/balances").principal(user))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
    var self =
        new UsernamePasswordAuthenticationToken(
            owner, null, List.of(new SimpleGrantedAuthority("ROLE_TRADER")));
    mvc.perform(get("/accounts/" + owner).principal(self)).andExpect(status().isOk());
    var admin =
        new UsernamePasswordAuthenticationToken(
            stranger, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    mvc.perform(get("/accounts/" + owner).principal(admin)).andExpect(status().isOk());
    verify(service, times(2)).getAllBalances(owner);
  }
}
