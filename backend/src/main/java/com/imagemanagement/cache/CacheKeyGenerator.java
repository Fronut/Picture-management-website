package com.imagemanagement.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CacheKeyGenerator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CacheKeyGenerator() {
    }

    public static String imageSearchKey(Long userId, Object criteria) {
        return "user:" + userId + ":search:" + digest(criteria);
    }

    public static String imageKey(Long userId, Long imageId) {
        return "user:" + userId + ":image:" + imageId;
    }

    public static String userKey(Long userId) {
        return "user:" + userId;
    }

    private static String digest(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            byte[] jsonBytes = OBJECT_MAPPER.writeValueAsBytes(value);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(jsonBytes);
            return HexFormat.of().formatHex(hashBytes);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            byte[] fallback = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(fallback);
        }
    }
}