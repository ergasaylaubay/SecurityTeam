package kz.securityteam.client;

import com.fasterxml.jackson.databind.*;
import kz.securityteam.crypto.CryptoService;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.*;

/** One instance per logged-in user; HTTPS certificates and hostnames are verified. */
public class ApiClient {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;
    private final URI base;
    private String csrfToken, csrfHeader;
    private UUID userId;
    private PublicKey publicKey;
    public ApiClient(String url, String trustStore, String password) throws Exception {
        base = URI.create(url);
        if (!"https".equalsIgnoreCase(base.getScheme())) throw new IllegalArgumentException("HTTPS required");
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(Path.of(trustStore))) { store.load(input, password.toCharArray()); }
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        SSLContext ssl = SSLContext.getInstance("TLS"); ssl.init(null, trust.getTrustManagers(), null);
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        http = HttpClient.newBuilder().sslContext(ssl).cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build();
    }
    private JsonNode request(String method, String path, String body, String contentType) throws Exception {
        var builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(20));
        if (!"GET".equals(method) && csrfToken != null) builder.header(csrfHeader, csrfToken);
        builder.header("Content-Type", contentType);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) throw new ApiException(response.statusCode());
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }
    private JsonNode get(String path) throws Exception { return request("GET", path, null, "application/json"); }
    private JsonNode post(String path, Object body) throws Exception {
        return request("POST", path, json.writeValueAsString(body), "application/json");
    }
    private void csrf() throws Exception {
        var result = get("/auth/csrf"); csrfHeader = result.get("headerName").asText(); csrfToken = result.get("token").asText();
    }
    public void register(String username, String password) throws Exception {
        csrf(); post("/auth/register", Map.of("username", username, "password", password));
    }
    public void login(String username, String password) throws Exception {
        csrf();
        request("POST", "/auth/login", "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8), "application/x-www-form-urlencoded");
        csrf();
        userId = UUID.fromString(get("/auth/me").get("id").asText());
        publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(
                Base64.getDecoder().decode(get("/crypto/key").get("publicKey").asText())));
    }
    public void logout() throws Exception { post("/auth/logout", Map.of()); userId = null; publicKey = null; }
    public record Chat(UUID id, String username) { @Override public String toString() { return username; } }
    public List<Chat> chats() throws Exception {
        List<Chat> result = new ArrayList<>();
        for (var node : get("/chats")) result.add(new Chat(UUID.fromString(node.get("id").asText()), node.get("username").asText()));
        return result;
    }
    public UUID createChat(String username) throws Exception {
        return UUID.fromString(post("/chats", Map.of("username", username)).get("id").asText());
    }
    public String history(UUID chat) throws Exception {
        StringBuilder result = new StringBuilder();
        for (int offset = 0; ; offset += 200) {
            var page = get("/chats/" + chat + "/messages?offset=" + offset);
            for (var message : page) result.append(message.get("sender").asText()).append(": ")
                    .append(message.get("text").asText()).append('\n');
            if (page.size() < 200) break;
        }
        return result.toString();
    }
    public void send(UUID chat, String text) throws Exception {
        UUID message = UUID.randomUUID();
        var encrypted = new CryptoService().encrypt(text, publicKey, CryptoService.context(chat, userId, message));
        post("/messages", Map.of("chatId", chat, "clientMessageId", message, "wrappedKey", encrypted.wrappedKey(),
                "nonce", encrypted.nonce(), "ciphertext", encrypted.ciphertext()));
    }
    public static class ApiException extends Exception {
        private final int status;
        public ApiException(int status) { super("HTTP " + status); this.status = status; }
        public int status() { return status; }
    }
}
