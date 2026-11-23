package kz.securityteam.server;

import kz.securityteam.crypto.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
public class MessageService {
    private final JdbcTemplate jdbc;
    private final ChatAccessService access;
    private final CryptoBox crypto;
    public MessageService(JdbcTemplate jdbc, ChatAccessService access, CryptoBox crypto) {
        this.jdbc = jdbc; this.access = access; this.crypto = crypto;
    }
    public record ChatView(UUID id, String username) {}
    public record MessageView(UUID id, String sender, String text, OffsetDateTime createdAt) {}
    @Transactional public UUID directChat(UUID user, String peer) {
        UUID other = access.activeUser(peer);
        if (user.equals(other)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String key = user.compareTo(other) < 0 ? user + ":" + other : other + ":" + user;
        var existing = jdbc.query("SELECT id FROM chat WHERE direct_key=?", (rs, row) -> rs.getObject(1, UUID.class), key);
        if (!existing.isEmpty()) return existing.get(0);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO chat(id,direct_key,created_at) VALUES(?,?,?)", id, key, OffsetDateTime.now());
        jdbc.update("INSERT INTO chat_member(chat_id,user_id) VALUES(?,?),(?,?)", id, user, id, other);
        return id;
    }
    public List<ChatView> chats(UUID user) {
        return jdbc.query("SELECT c.id,u.username FROM chat c JOIN chat_member mine ON mine.chat_id=c.id "
                + "JOIN chat_member peer ON peer.chat_id=c.id AND peer.user_id<>mine.user_id "
                + "JOIN app_user u ON u.id=peer.user_id WHERE mine.user_id=? ORDER BY u.username",
                (rs, row) -> new ChatView(rs.getObject(1, UUID.class), rs.getString(2)), user);
    }
    @Transactional public UUID send(UUID user, UUID chat, UUID clientId, EncryptedMessage encrypted)
            throws GeneralSecurityException {
        access.requireMember(chat, user);
        byte[] context = CryptoService.context(chat, user, clientId);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(context); digest.update(encrypted.wrappedKey()); digest.update(encrypted.nonce());
        String fingerprint = HexFormat.of().formatHex(digest.digest(encrypted.ciphertext()));
        var old = jdbc.query("SELECT id,payload_hash FROM message WHERE sender_id=? AND client_message_id=?",
                (rs, row) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2)), user, clientId);
        if (!old.isEmpty()) {
            if (!old.get(0).getValue().equals(fingerprint)) throw new ResponseStatusException(HttpStatus.CONFLICT);
            return old.get(0).getKey();
        }
        String text = crypto.openTransport(encrypted, context);
        UUID id = UUID.randomUUID();
        byte[] storedContext = CryptoService.context(chat, user, id);
        var stored = crypto.store(text, storedContext);
        jdbc.update("INSERT INTO message(id,chat_id,sender_id,client_message_id,ciphertext,nonce,key_id,payload_hash,created_at) "
                + "VALUES(?,?,?,?,?,?,'storage-v1',?,?)", id, chat, user, clientId,
                stored.ciphertext(), stored.nonce(), fingerprint, OffsetDateTime.now());
        jdbc.update("INSERT INTO audit_event(id,actor_id,action,target_id,created_at) VALUES(?,?,'MESSAGE_ACCEPTED',?,?)",
                UUID.randomUUID(), user, id.toString(), OffsetDateTime.now());
        return id;
    }
    public List<MessageView> history(UUID user, UUID chat, int offset) {
        access.requireMember(chat, user);
        if (offset < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return jdbc.query("SELECT m.id,m.sender_id,u.username,m.nonce,m.ciphertext,m.created_at FROM message m "
                + "JOIN app_user u ON u.id=m.sender_id WHERE m.chat_id=? ORDER BY m.created_at,m.id LIMIT 200 OFFSET ?",
                (rs, row) -> {
                    UUID id = rs.getObject(1, UUID.class), sender = rs.getObject(2, UUID.class);
                    try {
                        return new MessageView(id, rs.getString(3),
                                crypto.read(rs.getBytes(4), rs.getBytes(5), CryptoService.context(chat, sender, id)),
                                rs.getObject(6, OffsetDateTime.class));
                    } catch (GeneralSecurityException error) {
                        throw new IllegalStateException("Stored message failed integrity check");
                    }
                }, chat, offset);
    }
}
