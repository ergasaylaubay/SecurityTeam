package kz.securityteam.crypto;

import java.util.Objects;

/** Binary values are Base64 encoded by the API layer when transported as JSON. */
public record EncryptedMessage(byte[] wrappedKey, byte[] nonce, byte[] ciphertext) {
    public EncryptedMessage {
        wrappedKey = Objects.requireNonNull(wrappedKey).clone();
        nonce = Objects.requireNonNull(nonce).clone();
        ciphertext = Objects.requireNonNull(ciphertext).clone();
        if (nonce.length != 12 || ciphertext.length < 16 || wrappedKey.length < 384) {
            throw new IllegalArgumentException("Invalid encrypted message format");
        }
    }
    @Override public byte[] wrappedKey() { return wrappedKey.clone(); }
    @Override public byte[] nonce() { return nonce.clone(); }
    @Override public byte[] ciphertext() { return ciphertext.clone(); }
    @Override public String toString() { return "EncryptedMessage[redacted]"; }
}
