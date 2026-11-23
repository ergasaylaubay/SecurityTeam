package kz.securityteam.server;

import java.security.*;
import javax.crypto.SecretKey;
import java.nio.file.*;
import java.util.Arrays;

public record KeyMaterial(KeyPair transport, SecretKey storage) {
    public static KeyMaterial load(String path, String password) throws Exception {
        char[] chars = password.toCharArray();
        try (var input = Files.newInputStream(Path.of(path))) {
            KeyStore store = KeyStore.getInstance("PKCS12"); store.load(input, chars);
            var transport = new KeyPair(store.getCertificate("transport").getPublicKey(),
                    (PrivateKey) store.getKey("transport", chars));
            return new KeyMaterial(transport, (SecretKey) store.getKey("storage", chars));
        } finally { Arrays.fill(chars, '\0'); }
    }
    @Override public String toString() { return "KeyMaterial[redacted]"; }
}
