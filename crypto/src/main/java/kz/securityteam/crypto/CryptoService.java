package kz.securityteam.crypto;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/** Stateless hybrid encryption. Authentication and key ownership are checked by callers. */
public final class CryptoService {
    public static final int MAX_MESSAGE_BYTES = 65536;
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private final SecureRandom random = new SecureRandom();

    public KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, random);
        return generator.generateKeyPair();
    }

    /** Stable context binds ciphertext to chat, authenticated sender and message identifier. */
    public static byte[] context(UUID chatId, UUID senderId, UUID messageId) {
        return ("secure-messenger:v1:" + Objects.requireNonNull(chatId) + ":"
                + Objects.requireNonNull(senderId) + ":" + Objects.requireNonNull(messageId))
                .getBytes(StandardCharsets.UTF_8);
    }

    public EncryptedMessage encrypt(String text, PublicKey recipientKey, byte[] context)
            throws GeneralSecurityException {
        Objects.requireNonNull(text);
        validateKey(recipientKey);
        validateContext(context);
        byte[] plaintext = text.getBytes(StandardCharsets.UTF_8);
        if (plaintext.length > MAX_MESSAGE_BYTES) throw new IllegalArgumentException("Message too long");
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256, random);
        byte[] aesKey = generator.generateKey().getEncoded();
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, nonce));
            aes.updateAAD(context);
            byte[] ciphertext = aes.doFinal(plaintext);
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.ENCRYPT_MODE, recipientKey, OAEP, random);
            return new EncryptedMessage(rsa.doFinal(aesKey), nonce, ciphertext);
        } finally {
            Arrays.fill(aesKey, (byte) 0);
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    public String decrypt(EncryptedMessage message, PrivateKey recipientKey, byte[] context)
            throws GeneralSecurityException {
        Objects.requireNonNull(message);
        validateKey(recipientKey);
        validateContext(context);
        if (message.ciphertext().length > MAX_MESSAGE_BYTES + 16)
            throw new IllegalArgumentException("Message too long");
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, recipientKey, OAEP);
        byte[] aesKey = rsa.doFinal(message.wrappedKey());
        try {
            if (aesKey.length != 32) throw new GeneralSecurityException("Invalid encrypted message");
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, message.nonce()));
            aes.updateAAD(context);
            byte[] plaintext = aes.doFinal(message.ciphertext());
            try { return new String(plaintext, StandardCharsets.UTF_8); }
            finally { Arrays.fill(plaintext, (byte) 0); }
        } finally { Arrays.fill(aesKey, (byte) 0); }
    }

    private static void validateKey(Key key) {
        if (!(key instanceof RSAKey rsa) || rsa.getModulus().bitLength() < 3072)
            throw new IllegalArgumentException("RSA key must be at least 3072 bits");
    }
    private static void validateContext(byte[] context) {
        if (context == null || context.length == 0) throw new IllegalArgumentException("Context required");
    }
}
