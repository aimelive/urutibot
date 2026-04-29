package com.aimelive.urutibot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");

        registry.addMapping("/api/chatbot")
                .allowedOrigins(origins)
                .allowedMethods("POST")
                .allowedHeaders("*")
                .allowCredentials(true);

        registry.addMapping("/api/chatbot/stream")
                .allowedOrigins(origins)
                .allowedMethods("POST")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}