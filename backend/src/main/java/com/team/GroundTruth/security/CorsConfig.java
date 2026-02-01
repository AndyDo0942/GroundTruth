package com.team.GroundTruth.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")   // allow all origins
                .allowedMethods("*")          // GET, POST, PUT, PATCH, DELETE, OPTIONS
                .allowedHeaders("*")
                .allowCredentials(false)      // MUST be false when allowing all origins
                .maxAge(3600);
    }
}
