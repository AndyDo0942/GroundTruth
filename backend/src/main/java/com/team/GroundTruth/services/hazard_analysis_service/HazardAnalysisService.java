package com.team.GroundTruth.services.hazard_analysis_service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Uses Spring AI with Gemini to analyze hazard images.
 */
@Service
public class HazardAnalysisService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HazardAnalysisService.class);

    private static final String SYSTEM_PROMPT = """
            Act as a Civil Infrastructure Safety Inspector. Analyze the provided image to identify hazards based on the environment.
            Rules:

            Identify Environment: Determine if the surface is a Road or a Sidewalk.
            Allowed Categories:
            Universal (Both): Use Ice.
            Sidewalk Only: Use Cracks or Blocked Sidewalk.
            Road Only: Use Pothole.
            Scoring: Assign a score from 0 to 100 for each hazard found.
            Constraint: Do not report environment-specific hazards (e.g., 'Blocked Sidewalk') if the identified environment is a 'Road'.
            Output Format (Strict):
            Return ONLY a JSON object in the following format, with no preamble or explanation:{"road_type": {"hazard_type": severity_score}}
            """;

    private static final TypeReference<Map<String, Map<String, Double>>> RESPONSE_TYPE =
            new TypeReference<>() {};

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public HazardAnalysisService(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Analyze a report image and return parsed hazards.
     *
     * @param imageUrl image URL
     * @return parsed hazard analysis result
     */
    public HazardAnalysisResult analyzeImage(String imageUrl) {
        Objects.requireNonNull(imageUrl, "imageUrl");

        String response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userSpec -> userSpec
                        .text("Analyze the image and return the JSON output only.")
                        .media(resolveMimeType(imageUrl), toUrl(imageUrl)))
                .call()
                .content();

        if (response == null || response.isBlank()) {
            LOGGER.warn("Gemini response was empty for image {}", imageUrl);
            return new HazardAnalysisResult("Unknown", List.of());
        }

        try {
            Map<String, Map<String, Double>> parsed = objectMapper.readValue(cleanJson(response), RESPONSE_TYPE);
            if (parsed.isEmpty()) {
                return new HazardAnalysisResult("Unknown", List.of());
            }

            Map.Entry<String, Map<String, Double>> entry = parsed.entrySet().iterator().next();
            String roadType = entry.getKey();
            Map<String, Double> hazards = entry.getValue();

            List<HazardScore> scores = new ArrayList<>();
            if (hazards != null) {
                for (Map.Entry<String, Double> hazardEntry : hazards.entrySet()) {
                    if (hazardEntry.getKey() == null) {
                        continue;
                    }
                    scores.add(new HazardScore(hazardEntry.getKey(), hazardEntry.getValue()));
                }
            }

            return new HazardAnalysisResult(roadType, scores);
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Failed to parse Gemini response: {}", response, ex);
            return new HazardAnalysisResult("Unknown", List.of());
        }
    }

    private MimeType resolveMimeType(String imageUrl) {
        String lower = imageUrl.toLowerCase();
        if (lower.endsWith(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MimeTypeUtils.parseMimeType("image/webp");
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }

    private String cleanJson(String response) {
        String trimmed = response.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private URL toUrl(String imageUrl) {
        try {
            return new java.net.URI(imageUrl).toURL();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid image URL: " + imageUrl, ex);
        }
    }
}
