package com.dete.auth.service;

import com.dete.auth.model.User;
import com.dete.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoUserSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

  private final UserRepository userRepository;
  private final PasswordService passwordService;
  private final boolean enabled;
  private final String email;
  private final String username;
  private final String password;

  public DemoUserSeeder(
      UserRepository userRepository,
      PasswordService passwordService,
      @Value("${auth.demo.enabled:true}") boolean enabled,
      @Value("${auth.demo.email:demo@dete.io}") String email,
      @Value("${auth.demo.username:demo}") String username,
      @Value("${auth.demo.password:DemoPassword123!}") String password) {
    this.userRepository = userRepository;
    this.passwordService = passwordService;
    this.enabled = enabled;
    this.email = email;
    this.username = username;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      log.info("Demo user seeding is disabled.");
      return;
    }

    if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
      log.info("Demo user '{}' ({}) already exists. Skipping seeding.", username, email);
      return;
    }

    String passwordHash = passwordService.hash(password);
    User demoUser = User.createNew(username, email, passwordHash, true);
    userRepository.save(demoUser);
    log.info(
        "Successfully seeded demo user '{}' ({}) with ID {}", username, email, demoUser.userId());
  }
}
