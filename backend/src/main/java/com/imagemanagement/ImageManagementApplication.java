package com.imagemanagement;

import com.imagemanagement.config.AiServiceProperties;
import com.imagemanagement.config.CacheProperties;
import com.imagemanagement.config.FileStorageProperties;
import com.imagemanagement.config.JwtProperties;
import com.imagemanagement.config.ThumbnailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({JwtProperties.class, FileStorageProperties.class, ThumbnailProperties.class, CacheProperties.class, AiServiceProperties.class})
public class ImageManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageManagementApplication.class, args);
    }
}