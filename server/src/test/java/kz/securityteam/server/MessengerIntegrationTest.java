package kz.securityteam.server;

import kz.securityteam.crypto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.KeyGenerator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties={"server.ssl.enabled=false", "spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=test", "messenger.keystore-password=test"})
@AutoConfigureMockMvc @ActiveProfiles("test")
@Import(MessengerIntegrationTest.Keys.class)
class MessengerIntegrationTest {
    @TestConfiguration static class Keys {
        @Bean KeyMaterial keys() throws Exception {
            var aes=KeyGenerator.getInstance("AES"); aes.init(256);
            return new KeyMaterial(new CryptoService().generateKeyPair(),aes.generateKey());
        }
    }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired AuthService auth; @Autowired ChatAccessService access; @Autowired MessageService messages;
    @Autowired KeyMaterial keys;
    UUID alice,bob,eve,chat;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM audit_event"); jdbc.update("DELETE FROM message");
        jdbc.update("DELETE FROM chat_member"); jdbc.update("DELETE FROM chat"); jdbc.update("DELETE FROM app_user");
        alice=auth.register("alice","Password123!"); bob=auth.register("bob","Password123!"); eve=auth.register("eve","Password123!");
        chat=messages.directChat(alice,"bob");
    }
    MockHttpSession login(String user) throws Exception {
        return (MockHttpSession)mvc.perform(post("/auth/login").with(csrf()).param("username",user).param("password","Password123!"))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    @Test void anonymousHistoryDenied() throws Exception { mvc.perform(get("/chats/"+chat+"/messages")).andExpect(status().isUnauthorized()); }
    @Test void wrongPasswordDenied() throws Exception { mvc.perform(post("/auth/login").with(csrf()).param("username","alice").param("password","wrong")).andExpect(status().isUnauthorized()); }
    @Test void csrfRequired() throws Exception { mvc.perform(post("/chats").session(login("alice")).contentType("application/json").content("{\"username\":\"bob\"}")).andExpect(status().isForbidden()); }
    @Test void outsiderCannotRead() throws Exception { mvc.perform(get("/chats/"+chat+"/messages").session(login("eve"))).andExpect(status().isForbidden()); }
    @Test void outsiderCannotSend() throws Exception {
        UUID id=UUID.randomUUID(); var enc=new CryptoService().encrypt("secret",keys.transport().getPublic(),CryptoService.context(chat,eve,id));
        var body=new MessageController.SendRequest(chat,id,enc.wrappedKey(),enc.nonce(),enc.ciphertext());
        mvc.perform(post("/messages").session(login("eve")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isForbidden());
    }
    @Test void encryptedRoundTripAndDatabase() throws Exception {
        UUID id=UUID.randomUUID(); var enc=new CryptoService().encrypt("Сәлем!",keys.transport().getPublic(),CryptoService.context(chat,alice,id));
        var body=new MessageController.SendRequest(chat,id,enc.wrappedKey(),enc.nonce(),enc.ciphertext());
        mvc.perform(post("/messages").session(login("alice")).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
        mvc.perform(get("/chats/"+chat+"/messages").session(login("bob"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].text").value("Сәлем!"));
        assertEquals("Сәлем!",messages.history(alice,chat,0).get(0).text());
        byte[] stored=jdbc.queryForObject("SELECT ciphertext FROM message",byte[].class);
        assertFalse(Arrays.equals(stored,"Сәлем!".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void retryIsIdempotentAndChangedRetryConflicts() throws Exception {
        UUID id=UUID.randomUUID(); var crypto=new CryptoService(); byte[] ctx=CryptoService.context(chat,alice,id);
        var enc=crypto.encrypt("one",keys.transport().getPublic(),ctx);
        assertEquals(messages.send(alice,chat,id,enc),messages.send(alice,chat,id,enc));
        var changed=crypto.encrypt("two",keys.transport().getPublic(),ctx);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->messages.send(alice,chat,id,changed));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM message",Integer.class));
    }
    @Test void passwordsAreHashed() { String hash=jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?",String.class,alice); assertTrue(hash.startsWith("$2")); assertNotEquals("Password123!",hash); }
    @Test void duplicateUsernameRejected() throws Exception { mvc.perform(post("/auth/register").with(csrf()).contentType("application/json").content("{\"username\":\"alice\",\"password\":\"Password123!\"}")).andExpect(status().isConflict()); }
    @Test void logoutRemovesAccess() throws Exception { var session=login("alice"); mvc.perform(post("/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent()); assertTrue(session.isInvalid()); }
    @Test void blockedUserDenied() throws Exception { var session=login("alice"); jdbc.update("UPDATE app_user SET status='BLOCKED' WHERE id=?",alice); mvc.perform(get("/chats").session(session)).andExpect(status().isForbidden()); }
    @Test void userCannotUseAdminApi() throws Exception { mvc.perform(get("/admin/audit").session(login("alice"))).andExpect(status().isForbidden()); }
}
