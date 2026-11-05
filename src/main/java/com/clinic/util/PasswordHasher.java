package com.clinic.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SHA-256 password hashing with a per-user random salt, as required by the brief.
 *
 * <p>Plain text passwords never touch the data files. This class is deliberately dependency
 * free so it cannot accidentally log or serialise a password.</p>
 */
public final class PasswordHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String ALGORITHM = "SHA-256";
    private static final String ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private PasswordHasher() {
    }

    /** Creates a fresh 16 byte salt encoded as URL-safe Base64. */
    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return ENCODER.encodeToString(salt);
    }

    /** Hashes {@code salt + password} with SHA-256 and returns URL-safe Base64. */
    public static String hash(String rawPassword, String salt) {
        if (rawPassword == null || salt == null) {
            throw new IllegalArgumentException("Password and salt are required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return ENCODER.encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", ex);
        }
    }

    /** Constant-time comparison of a raw password against the stored hash and salt. */
    public static boolean matches(String rawPassword, String storedHash, String salt) {
        if (rawPassword == null || storedHash == null || salt == null) {
            return false;
        }
        String candidate = hash(rawPassword, salt);
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    /** Readable random initial password shown once when a receptionist creates an account. */
    public static String generatePassword() {
        StringBuilder builder = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
