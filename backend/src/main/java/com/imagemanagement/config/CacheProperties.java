package com.imagemanagement.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    @NotNull
    private Duration defaultTtl = Duration.ofMinutes(5);

    @NotNull
    private Duration usersTtl = Duration.ofMinutes(30);

    @NotNull
    private Duration imagesTtl = Duration.ofMinutes(10);

    @NotNull
    private Duration searchTtl = Duration.ofMinutes(5);

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Duration getUsersTtl() {
        return usersTtl;
    }

    public void setUsersTtl(Duration usersTtl) {
        this.usersTtl = usersTtl;
    }

    public Duration getImagesTtl() {
        return imagesTtl;
    }

    public void setImagesTtl(Duration imagesTtl) {
        this.imagesTtl = imagesTtl;
    }

    public Duration getSearchTtl() {
        return searchTtl;
    }

    public void setSearchTtl(Duration searchTtl) {
        this.searchTtl = searchTtl;
    }
}