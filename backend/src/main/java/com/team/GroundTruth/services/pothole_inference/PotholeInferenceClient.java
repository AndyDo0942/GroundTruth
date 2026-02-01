package com.team.GroundTruth.services.pothole_inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private final WebClient inferenceWebClient;
	private final ObjectMapper objectMapper;

	public PotholeInferenceClient(
			@org.springframework.beans.factory.annotation.Qualifier("inferenceWebClient") WebClient inferenceWebClient,
			ObjectMapper objectMapper
	) {
		this.inferenceWebClient = Objects.requireNonNull(inferenceWebClient, "inferenceWebClient");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
	}

	/**
	 * Calls the inference API with the provided image bytes.
	 * Returns true if any detected pothole has depth greater than 5 cm.
	 *
	 * @param imageBytes report image bytes
	 * @param imageContentType image MIME type
	 * @return true if any pothole depth_cm &gt; 5, false otherwise or on error
	 */
	public boolean hasDeepPothole(byte[] imageBytes, String imageContentType) {
		if (imageBytes == null || imageBytes.length == 0) {
			return false;
		}
		try {
			String json = callAnalyzePotholes(imageBytes, imageContentType);
			if (json == null || json.isBlank()) {
				return false;
			}
			return parseHasDeepPothole(json);
		} catch (Exception e) {
			LOG.warn("Inference API call failed for image bytes: {}", e.getMessage());
			return false;
		}
	}

	private String callAnalyzePotholes(byte[] imageBytes, String imageContentType) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		MediaType mediaType = resolveMediaType(imageContentType);
		builder.part("file", imageBytes, mediaType).filename("image");

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

	private MediaType resolveMediaType(String imageContentType) {
		if (imageContentType == null || imageContentType.isBlank()) {
			return MediaType.IMAGE_JPEG;
		}
		try {
			return MediaType.parseMediaType(imageContentType);
		} catch (IllegalArgumentException ex) {
			return MediaType.IMAGE_JPEG;
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
