package com.changeguard.repository;

import java.util.Optional;
import com.changeguard.entity.ApplicationUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<ApplicationUser> findByUsername(String username) {
        return jdbc.query("SELECT username, password_hash, tenant_id, enabled FROM application_user WHERE username = ?",
                (rs, row) -> new ApplicationUser(rs.getString("username"), rs.getString("password_hash"),
                        rs.getString("tenant_id"), rs.getBoolean("enabled")), username).stream().findFirst();
    }
}
