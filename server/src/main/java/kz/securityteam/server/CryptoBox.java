package kz.securityteam.server;

import kz.securityteam.crypto.*;
import org.springframework.stereotype.Service;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

@Service
public class CryptoBox {
    private final KeyMaterial keys;
    private final CryptoService crypto = new CryptoService();
    private final SecureRandom random = new SecureRandom();
    public CryptoBox(KeyMaterial keys) { this.keys = keys; }
    public String publicKey() { return Base64.getEncoder().encodeToString(keys.transport().getPublic().getEncoded()); }
    public String openTransport(EncryptedMessage message, byte[] context) throws GeneralSecurityException {
        return crypto.decrypt(message, keys.transport().getPrivate(), context);
    }
    public record Stored(byte[] nonce, byte[] ciphertext) {}
    public Stored store(String text, byte[] context) throws GeneralSecurityException {
        byte[] nonce = new byte[12]; random.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keys.storage(), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(context);
        return new Stored(nonce, cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }
    public String read(byte[] nonce, byte[] ciphertext, byte[] context) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keys.storage(), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(context);
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
