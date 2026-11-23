package kz.securityteam.crypto;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {
    static final CryptoService crypto = new CryptoService();
    static KeyPair keys;
    static byte[] context;
    @BeforeAll static void setup() throws Exception {
        keys = crypto.generateKeyPair();
        context = CryptoService.context(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
    @ParameterizedTest @ValueSource(strings = {"Сәлем!", "", "Hello", "Қазақша 🔐\nжаңа жол"})
    void roundTrip(String text) throws Exception {
        assertEquals(text, crypto.decrypt(crypto.encrypt(text, keys.getPublic(), context), keys.getPrivate(), context));
    }
    @Test void wrongPrivateKeyRejected() throws Exception {
        var message = crypto.encrypt("Сәлем!", keys.getPublic(), context);
        var other = crypto.generateKeyPair();
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(message, other.getPrivate(), context));
    }
    @Test void tamperedCiphertextRejected() throws Exception {
        var m = crypto.encrypt("Сәлем!", keys.getPublic(), context);
        byte[] changed = m.ciphertext(); changed[0] ^= 1;
        var bad = new EncryptedMessage(m.wrappedKey(), m.nonce(), changed);
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(bad, keys.getPrivate(), context));
    }
    @Test void tamperedTagRejected() throws Exception {
        var m = crypto.encrypt("Сәлем!", keys.getPublic(), context);
        byte[] changed = m.ciphertext(); changed[changed.length - 1] ^= 1;
        var bad = new EncryptedMessage(m.wrappedKey(), m.nonce(), changed);
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(bad, keys.getPrivate(), context));
    }
    @Test void wrongContextRejected() throws Exception {
        var m = crypto.encrypt("Сәлем!", keys.getPublic(), context);
        byte[] changed = context.clone(); changed[0] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(m, keys.getPrivate(), changed));
    }
    @Test void repeatedTextUsesFreshEncryption() throws Exception {
        var a = crypto.encrypt("Сәлем!", keys.getPublic(), context);
        var b = crypto.encrypt("Сәлем!", keys.getPublic(), context);
        assertFalse(Arrays.equals(a.nonce(), b.nonce()));
        assertFalse(Arrays.equals(a.wrappedKey(), b.wrappedKey()));
        assertFalse(Arrays.equals(a.ciphertext(), b.ciphertext()));
    }
    @Test void oversizedMessageRejected() {
        assertThrows(IllegalArgumentException.class, () -> crypto.encrypt("a".repeat(65537), keys.getPublic(), context));
    }
    @Test void limitRoundTrip() throws Exception {
        String text = "a".repeat(65536);
        assertEquals(text, crypto.decrypt(crypto.encrypt(text, keys.getPublic(), context), keys.getPrivate(), context));
    }
    @Test void emptyContextRejected() {
        assertThrows(IllegalArgumentException.class, () -> crypto.encrypt("Hi", keys.getPublic(), new byte[0]));
    }
    @Test void weakRsaRejected() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var weak = generator.generateKeyPair();
        assertThrows(IllegalArgumentException.class, () -> crypto.encrypt("Hi", weak.getPublic(), context));
    }
    @Test void alteredNonceRejected() throws Exception {
        var m = crypto.encrypt("Сәлем!", keys.getPublic(), context);
        byte[] nonce = m.nonce(); nonce[0] ^= 1;
        var bad = new EncryptedMessage(m.wrappedKey(), nonce, m.ciphertext());
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(bad, keys.getPrivate(), context));
    }
    @Test void defensiveCopiesProtectEnvelope() throws Exception {
        var m = crypto.encrypt("Сәлем!", keys.getPublic(), context);
        byte[] ciphertext = m.ciphertext();
        var copy = new EncryptedMessage(m.wrappedKey(), m.nonce(), ciphertext);
        Arrays.fill(ciphertext, (byte) 0);
        Arrays.fill(copy.ciphertext(), (byte) 0);
        assertEquals("Сәлем!", crypto.decrypt(copy, keys.getPrivate(), context));
    }
}
