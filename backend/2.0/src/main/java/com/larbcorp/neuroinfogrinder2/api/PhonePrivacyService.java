package com.larbcorp.neuroinfogrinder2.api;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PhonePrivacyService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final byte[] encryptionKey;
    private final byte[] hashKey;

    public PhonePrivacyService(Environment environment) {
        String encryption = environment == null ? System.getenv("PHONE_ENCRYPTION_KEY") : environment.getProperty("app.phone.encryption-key", System.getenv("PHONE_ENCRYPTION_KEY"));
        String hash = environment == null ? System.getenv("PHONE_HASH_KEY") : environment.getProperty("app.phone.hash-key", System.getenv("PHONE_HASH_KEY"));
        this.encryptionKey = decodeKey(encryption);
        this.hashKey = decodeKey(hash);
    }

    public String normalize(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.isBlank()) return null;
        return "+" + digits;
    }

    public String mask(String phone) {
        String normalized = normalize(phone);
        if (normalized == null) return null;
        String digits = normalized.replaceAll("\\D", "");
        if (digits.length() <= 4) return normalized;
        return "+" + digits.substring(0, Math.min(1, digits.length())) + "******" + digits.substring(digits.length() - 4);
    }

    public String hash(String normalizedPhone) {
        if (normalizedPhone == null) return null;
        try {
            if (hashKey != null) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8)));
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalizedPhone.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to hash phone", error);
        }
    }

    public String encrypt(String normalizedPhone) {
        if (normalizedPhone == null || encryptionKey == null) return null;
        try {
            byte[] nonce = new byte[12];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to encrypt phone", error);
        }
    }

    public String decrypt(String encryptedPhone) {
        if (encryptedPhone == null || encryptedPhone.isBlank() || encryptionKey == null) return null;
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedPhone);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] nonce = new byte[12];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception error) {
            return null;
        }
    }

    private byte[] decodeKey(String value) {
        if (value == null || value.isBlank()) return null;
        byte[] decoded = Base64.getDecoder().decode(value.trim());
        if (decoded.length != 32) throw new IllegalStateException("Phone privacy keys must decode to 32 bytes");
        return decoded;
    }
}
