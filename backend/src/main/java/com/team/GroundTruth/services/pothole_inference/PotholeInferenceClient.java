package com.team.GroundTruth.services.pothole_inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.GroundTruth.config.InferenceApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.util.Objects;

/**
 * Client for the Python inference API that analyzes images for potholes and returns depth.
 * Used to apply a 1.5x cost multiplier when any pothole is deeper than 5 cm.
 */
@Service
public class PotholeInferenceClient {

	private static final Logger LOG = LoggerFactory.getLogger(PotholeInferenceClient.class);
	private static final double DEEP_POTHOLE_THRESHOLD_CM = 5.0;

	private final WebClient fetchWebClient;
	private final WebClient inferenceWebClient;
	private final InferenceApiConfig config;
	private final ObjectMapper objectMapper;

	public PotholeInferenceClient(
			@Qualifier("webClient") WebClient webClient,
			@Qualifier("inferenceWebClient") WebClient inferenceWebClient,
			InferenceApiConfig config,
			ObjectMapper objectMapper
	) {
		this.fetchWebClient = Objects.requireNonNull(webClient, "webClient");
		this.inferenceWebClient = Objects.requireNonNull(inferenceWebClient, "inferenceWebClient");
		this.config = Objects.requireNonNull(config, "config");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
	}

	/**
	 * Calls the inference API with the image at the given URL.
	 * Returns true if any detected pothole has depth greater than 5 cm.
	 *
	 * @param imageUrl URL of the report image (fetched and sent to inference API)
	 * @return true if any pothole depth_cm &gt; 5, false otherwise or on error
	 */
	public boolean hasDeepPothole(String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return false;
		}
		try {
			byte[] imageBytes = fetchImage(imageUrl);
			if (imageBytes == null || imageBytes.length == 0) {
				return false;
			}
			String json = callAnalyzePotholes(imageBytes);
			if (json == null || json.isBlank()) {
				return false;
			}
			return parseHasDeepPothole(json);
		} catch (Exception e) {
			LOG.warn("Inference API call failed for image {}: {}", imageUrl, e.getMessage());
			return false;
		}
	}

	private byte[] fetchImage(String imageUrl) {
		try {
			return fetchWebClient.get()
					.uri(imageUrl)
					.retrieve()
					.bodyToMono(byte[].class)
					.block();
		} catch (Exception e) {
			LOG.warn("Failed to fetch image from {}: {}", imageUrl, e.getMessage());
			return null;
		}
	}

	private String callAnalyzePotholes(byte[] imageBytes) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("file", imageBytes, MediaType.IMAGE_JPEG).filename("image.jpg");

		try {
			return inferenceWebClient.post()
					.uri("/analyze-potholes")
					.body(BodyInserters.fromMultipartData(builder.build()))
					.retrieve()
					.bodyToMono(String.class)
					.block();
		} catch (WebClientResponseException e) {
			LOG.warn("Inference API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
			return null;
		}
	}

	private boolean parseHasDeepPothole(String json) {
		try {
			JsonNode root = objectMapper.readTree(json);
			JsonNode results = root.get("results");
			if (results == null || !results.isArray()) {
				return false;
			}
			for (JsonNode item : results) {
				JsonNode depthCm = item.get("depth_cm");
				if (depthCm != null && depthCm.isNumber()) {
					double depth = depthCm.asDouble();
					if (depth > DEEP_POTHOLE_THRESHOLD_CM) {
						return true;
					}
				}
			}
			return false;
		} catch (IOException e) {
			LOG.warn("Failed to parse inference response: {}", e.getMessage());
			return false;
		}
	}
}
