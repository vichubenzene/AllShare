package com.example.share.service;

import com.example.share.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@Component
public class AccessTokenService {

    private static final String PREFIX = "v1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;

    public AccessTokenService(AppProperties properties) {
        this.secret = properties.security().accessTokenSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String issue(String shareToken, Instant expiresAt) {
        String payload = shareToken + "|" + expiresAt.getEpochSecond();
        String body = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = ENCODER.encodeToString(hmac(body));
        return PREFIX + "." + body + "." + signature;
    }

    public boolean isValid(String token, String shareToken, Instant now) {
        if (token == null || token.isBlank() || shareToken == null) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0]) || parts[1].isEmpty() || parts[2].isEmpty()) {
            return false;
        }
        byte[] expected = hmac(parts[1]);
        byte[] actual;
        try {
            actual = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            return false;
        }
        String payload;
        try {
            payload = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        int separator = payload.lastIndexOf('|');
        if (separator <= 0 || separator == payload.length() - 1) {
            return false;
        }
        String tokenShare = payload.substring(0, separator);
        long expiry;
        try {
            expiry = Long.parseLong(payload.substring(separator + 1));
        } catch (NumberFormatException ex) {
            return false;
        }
        if (!shareToken.equals(tokenShare)) {
            return false;
        }
        return Instant.ofEpochSecond(expiry).isAfter(now);
    }

    private byte[] hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
