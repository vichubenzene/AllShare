package com.example.share.service;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class TokenService {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int ALPHABET_BOUND = ALPHABET.length;
    private static final int UNBIASED_LIMIT = 256 - (256 % ALPHABET_BOUND);

    private final SecureRandom random = new SecureRandom();

    public String newShareToken() {
        char[] out = new char[12];
        byte[] buffer = new byte[1];
        for (int i = 0; i < out.length; i++) {
            int value;
            do {
                random.nextBytes(buffer);
                value = buffer[0] & 0xff;
            } while (value >= UNBIASED_LIMIT);
            out[i] = ALPHABET[value % ALPHABET_BOUND];
        }
        return new String(out);
    }

    public String newManagementToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public boolean matchesHash(String value, String expectedHex) {
        if (value == null || expectedHex == null || expectedHex.length() != 64) {
            return false;
        }
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(expectedHex);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        byte[] actual = sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return MessageDigest.isEqual(actual, expected);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
