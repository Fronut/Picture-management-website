package com.imagemanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.imagemanagement.cache.CacheNames;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.NonNull;

@Configuration
@EnableCaching
@Profile("!test")
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer serializer = jacksonRedisSerializer();
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, CacheProperties cacheProperties) {
        GenericJackson2JsonRedisSerializer serializer = jacksonRedisSerializer();
        RedisSerializationContext.SerializationPair<Object> pair = RedisSerializationContext.SerializationPair.fromSerializer(serializer);

        Duration defaultTtl = Objects.requireNonNull(cacheProperties.getDefaultTtl(), "Default cache TTL must be configured");
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(pair)
            .entryTtl(defaultTtl);

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(CacheNames.USERS,
            defaultConfig.entryTtl(Objects.requireNonNull(cacheProperties.getUsersTtl(), "User cache TTL must be configured")));
        cacheConfigurations.put(CacheNames.IMAGES,
            defaultConfig.entryTtl(Objects.requireNonNull(cacheProperties.getImagesTtl(), "Image cache TTL must be configured")));
        cacheConfigurations.put(CacheNames.IMAGE_SEARCH,
            defaultConfig.entryTtl(Objects.requireNonNull(cacheProperties.getSearchTtl(), "Search cache TTL must be configured")));

        RedisConnectionFactory safeConnectionFactory = Objects.requireNonNull(connectionFactory, "Redis connection factory must not be null");
        return RedisCacheManager.builder(safeConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private @NonNull GenericJackson2JsonRedisSerializer jacksonRedisSerializer() {
        ObjectMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        return new GenericJackson2JsonRedisSerializer(Objects.requireNonNull(mapper));
    }
}