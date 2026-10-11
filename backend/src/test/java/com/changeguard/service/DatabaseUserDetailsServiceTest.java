package com.changeguard.service;

import java.util.Optional;
import com.changeguard.entity.ApplicationUser;
import com.changeguard.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseUserDetailsServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final DatabaseUserDetailsService service = new DatabaseUserDetailsService(users);
    @Test void missingUserHasGenericFailure() {
        when(users.findByUsername("missing")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing"));
    }
    @Test void disabledUserStaysDisabled() {
        when(users.findByUsername("disabled")).thenReturn(Optional.of(new ApplicationUser("disabled", "hash", "tenant-a", false)));
        assertFalse(service.loadUserByUsername("disabled").isEnabled());
    }
}
