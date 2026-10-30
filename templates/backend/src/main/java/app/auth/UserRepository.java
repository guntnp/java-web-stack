package app.auth;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import app.security.CurrentUser;

/** All SQL for the users table. Always bind values with :params, never concatenate input. */
@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CurrentUser> findByEmail(String email) {
        return jdbc.sql("SELECT id, email, password_hash, role FROM users WHERE email = :email")
                .param("email", email)
                .query((rs, n) -> new CurrentUser(rs.getLong("id"), rs.getString("email"),
                        rs.getString("password_hash"), rs.getString("role")))
                .optional();
    }

    public boolean existsByEmail(String email) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE email = :email")
                .param("email", email)
                .query(Long.class).single() > 0;
    }

    public boolean anyAdmin() {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'").query(Long.class).single() > 0;
    }

    public long insert(String email, String passwordHash, String role) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO users (email, password_hash, role) VALUES (:email, :hash, :role)")
                .param("email", email)
                .param("hash", passwordHash)
                .param("role", role)
                .update(key);
        return key.getKey().longValue();
    }
}
