package kz.securityteam.server;

import kz.securityteam.crypto.EncryptedMessage;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.*;
import java.util.*;

@RestController
public class MessageController {
    private final AuthService auth;
    private final ChatAccessService access;
    private final MessageService messages;
    private final CryptoBox crypto;
    public MessageController(AuthService auth, ChatAccessService access, MessageService messages, CryptoBox crypto) {
        this.auth = auth; this.access = access; this.messages = messages; this.crypto = crypto;
    }
    public record Registration(@NotBlank String username, @NotBlank String password) {
        @Override public String toString() { return "Registration[redacted]"; }
    }
    public record DirectRequest(@NotBlank String username) {}
    public record SendRequest(@NotNull UUID chatId, @NotNull UUID clientMessageId,
                              @NotNull @Size(min=384,max=1024) byte[] wrappedKey,
                              @NotNull @Size(min=12,max=12) byte[] nonce,
                              @NotNull @Size(min=16,max=65552) byte[] ciphertext) {}
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status", "ok"); }
    @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
    @PostMapping("/auth/register") public Map<String,UUID> register(@Valid @RequestBody Registration request) {
        return Map.of("id", auth.register(request.username(), request.password()));
    }
    @GetMapping("/auth/me") public Map<String,Object> me(Principal principal) {
        return Map.of("id", access.activeUser(principal.getName()), "username", principal.getName());
    }
    @GetMapping("/crypto/key") public Map<String,String> key(Principal principal) {
        access.activeUser(principal.getName());
        return Map.of("keyId", "transport-v1", "publicKey", crypto.publicKey());
    }
    @PostMapping("/chats") public Map<String,UUID> direct(@Valid @RequestBody DirectRequest request, Principal principal) {
        return Map.of("id", messages.directChat(access.activeUser(principal.getName()), request.username()));
    }
    @GetMapping("/chats") public List<MessageService.ChatView> chats(Principal principal) {
        return messages.chats(access.activeUser(principal.getName()));
    }
    @PostMapping("/messages") public Map<String,UUID> send(@Valid @RequestBody SendRequest request, Principal principal)
            throws GeneralSecurityException {
        return Map.of("id", messages.send(access.activeUser(principal.getName()), request.chatId(), request.clientMessageId(),
                new EncryptedMessage(request.wrappedKey(), request.nonce(), request.ciphertext())));
    }
    @GetMapping("/chats/{chatId}/messages") public List<MessageService.MessageView> history(
            @PathVariable("chatId") UUID chatId, @RequestParam(name="offset", defaultValue="0") int offset, Principal principal) {
        return messages.history(access.activeUser(principal.getName()), chatId, offset);
    }
}
