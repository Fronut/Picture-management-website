package com.imagemanagement;

import com.imagemanagement.config.FileStorageProperties;
import com.imagemanagement.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, FileStorageProperties.class})
public class ImageManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageManagementApplication.class, args);
    }
}