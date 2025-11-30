package com.imagemanagement.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.util.Assert;

public final class TokenHashUtils {

    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private TokenHashUtils() {
    }

    public static String sha256(String tokenValue) {
        Assert.hasText(tokenValue, "Token value must not be blank");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash refresh token", ex);
        }
    }
}
