package kz.securityteam.server;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@Service
public class ChatAccessService {
    private final JdbcTemplate jdbc;
    public ChatAccessService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public UUID activeUser(String username) {
        return jdbc.query("SELECT id FROM app_user WHERE username=? AND status='ACTIVE'",
                (rs, row) -> rs.getObject(1, UUID.class), username).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
    public void requireMember(UUID chat, UUID user) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM chat_member WHERE chat_id=? AND user_id=?", Integer.class, chat, user);
        if (count == null || count != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
