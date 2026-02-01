package com.team.GroundTruth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for the Python inference API (pothole analysis).
 */
@Configuration
public class InferenceApiConfig {

	@Value("${inference.api.base-url:http://localhost:8000}")
	private String baseUrl;

	@Value("${inference.api.timeout-seconds:30}")
	private int timeoutSeconds;

	public String getBaseUrl() {
		return baseUrl;
	}

	public int getTimeoutSeconds() {
		return timeoutSeconds;
	}

	@Bean(name = "inferenceWebClient")
	public WebClient inferenceWebClient(WebClient.Builder builder) {
		return builder
				.baseUrl(baseUrl)
				.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
				.build();
	}
}
