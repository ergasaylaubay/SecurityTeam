package kz.securityteam.server;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwords) { this.jdbc = jdbc; this.passwords = passwords; }
    @Transactional public UUID register(String username, String password) {
        if (username == null || !username.matches("[a-zA-Z0-9_]{3,32}") || password == null
                || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,username,password_hash,role,status,created_at) VALUES(?,?,?,'USER','ACTIVE',?)",
                id, username, passwords.encode(password), OffsetDateTime.now());
        jdbc.update("INSERT INTO audit_event(id,actor_id,action,target_id,created_at) VALUES(?,?,'REGISTER',?,?)",
                UUID.randomUUID(), id, id.toString(), OffsetDateTime.now());
        return id;
    }
}
