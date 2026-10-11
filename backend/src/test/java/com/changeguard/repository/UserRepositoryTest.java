package com.changeguard.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserRepositoryTest {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Test void localFlywayUserUsesEncodedPasswordAndTrustedTenant() {
        var user = users.findByUsername("pramod").orElseThrow();
        assertEquals("local-team", user.tenantId());
        assertTrue(user.enabled());
        assertTrue(user.passwordHash().startsWith("{bcrypt}"));
        assertTrue(passwords.matches("ChangeGuard123!", user.passwordHash()));
        assertFalse(passwords.matches("incorrect", user.passwordHash()));
    }
    @Test void unknownAndSqlInjectionUsernamesDoNotMatch() {
        assertTrue(users.findByUsername("missing-user").isEmpty());
        assertTrue(users.findByUsername("' OR 1=1 --").isEmpty());
    }
}
