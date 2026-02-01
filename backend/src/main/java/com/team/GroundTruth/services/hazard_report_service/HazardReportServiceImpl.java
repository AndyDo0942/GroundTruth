package com.team.GroundTruth.services.hazard_report_service;

import com.team.GroundTruth.domain.HazardReportRequest;
import com.team.GroundTruth.domain.entity.Hazard.Hazard;
import com.team.GroundTruth.domain.entity.HazardReport.HazardReport;
import com.team.GroundTruth.domain.entity.User.User;
import com.team.GroundTruth.exception.HazardReportNotFoundException;
import com.team.GroundTruth.repository.HazardReportRepository;
import com.team.GroundTruth.repository.UserRepository;
import com.team.GroundTruth.routing.model.HazardType;
import com.team.GroundTruth.routing.service.WalkAccessibilityService;
import com.team.GroundTruth.services.hazard_analysis_service.HazardAnalysisResult;
import com.team.GroundTruth.services.hazard_analysis_service.HazardAnalysisService;
import com.team.GroundTruth.services.hazard_analysis_service.HazardScore;
import com.team.GroundTruth.services.pothole_inference.PotholeInferenceClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link HazardReportService}.
 */
@Service
public class HazardReportServiceImpl implements HazardReportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HazardReportServiceImpl.class);

    /**
     * Repository used to persist and load hazard reports.
     */
    private final HazardReportRepository hazardReportRepository;
    /**
     * Repository used to load users for report ownership.
     */
    private final UserRepository userRepository;
    /**
     * AI service used to analyze report images.
     */
    private final HazardAnalysisService hazardAnalysisService;
    /**
     * Service used to update walk accessibility edge costs when hazards change.
     */
    private final WalkAccessibilityService walkAccessibilityService;
    /**
     * Client for the Python inference API (pothole depth analysis).
     */
    private final PotholeInferenceClient potholeInferenceClient;

    /**
     * Creates the service with its repository dependencies.
     *
     * @param hazardReportRepository repository for hazard report persistence
     * @param userRepository repository for user lookups
     * @param hazardAnalysisService AI service for image analysis
     * @param walkAccessibilityService service for updating edge costs
     * @param potholeInferenceClient client for inference API (pothole depth)
     */
    public HazardReportServiceImpl(
            HazardReportRepository hazardReportRepository,
            UserRepository userRepository,
            HazardAnalysisService hazardAnalysisService,
            WalkAccessibilityService walkAccessibilityService,
            PotholeInferenceClient potholeInferenceClient
    ) {
        this.hazardReportRepository = hazardReportRepository;
        this.userRepository = userRepository;
        this.hazardAnalysisService = hazardAnalysisService;
        this.walkAccessibilityService = walkAccessibilityService;
        this.potholeInferenceClient = potholeInferenceClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HazardReport createHazardReport(HazardReportRequest request) {
        User user = userRepository.findById(request.userId()).orElse(null);

        HazardReport report = new HazardReport();
        report.setUser(user);
        report.setImageUrl(request.imageUrl());
        report.setLatitude(request.latitude());
        report.setLongitude(request.longitude());
        report.setHazards(new ArrayList<>());

        HazardReport savedReport = hazardReportRepository.save(report);

        try {
            HazardAnalysisResult result = hazardAnalysisService.analyzeImage(request.imageUrl());
            if (result != null && result.hazards() != null && !result.hazards().isEmpty()) {
                List<Hazard> hazards = new ArrayList<>();
                boolean hasPothole = false;
                for (HazardScore score : result.hazards()) {
                    if (score == null || score.hazardType() == null) {
                        continue;
                    }
                    if (HazardType.fromLabel(score.hazardType()) == HazardType.POTHOLE) {
                        hasPothole = true;
                    }
                    Hazard hazard = new Hazard();
                    hazard.setReport(savedReport);
                    hazard.setLabel(score.hazardType());
                    hazard.setConfidence(score.severityScore());
                    hazards.add(hazard);
                }
                savedReport.setHazards(hazards);
                savedReport = hazardReportRepository.save(savedReport);

                boolean hasDeepPothole = false;
                if (hasPothole) {
                    try {
                        hasDeepPothole = potholeInferenceClient.hasDeepPothole(request.imageUrl());
                    } catch (RuntimeException e) {
                        LOGGER.warn("Inference API call failed for report {}: {}", savedReport.getId(), e.getMessage());
                    }
                }

                updateAccessibilityEdgeCosts(savedReport, hasDeepPothole);
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("AI hazard analysis failed for report {}", savedReport.getId(), ex);
        }

        return savedReport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<HazardReport> getHazardReports() {
        return hazardReportRepository.findAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HazardReport updateHazardReport(UUID id, HazardReportRequest updateHazardReportRequest) {
        HazardReport existingReport = hazardReportRepository.findById(id)
                .orElseThrow(() -> new HazardReportNotFoundException(id));
        User user = userRepository.findById(updateHazardReportRequest.userId()).orElse(null);

        existingReport.setUser(user);
        existingReport.setImageUrl(updateHazardReportRequest.imageUrl());
        existingReport.setLatitude(updateHazardReportRequest.latitude());
        existingReport.setLongitude(updateHazardReportRequest.longitude());

        return hazardReportRepository.save(existingReport);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteHazardReport(UUID id) {
        HazardReport report = hazardReportRepository.findById(id)
                .orElseThrow(() -> new HazardReportNotFoundException(id));

        // Remove hazard contributions from edge costs before deleting
        removeAccessibilityEdgeCosts(report);

        hazardReportRepository.deleteById(id);
    }

    /**
     * Updates walk accessibility edge costs for hazards on the report (accessibility + potholes).
     *
     * @param report the saved hazard report with hazards
     * @param hasDeepPothole true if inference API found any pothole deeper than 5 cm (applies 1.5x for potholes)
     */
    private void updateAccessibilityEdgeCosts(HazardReport report, boolean hasDeepPothole) {
        if (report.getHazards() == null || report.getHazards().isEmpty()) {
            return;
        }
        if (report.getLatitude() == null || report.getLongitude() == null) {
            LOGGER.debug("Skipping edge cost update - report {} has no location", report.getId());
            return;
        }

        double lat = report.getLatitude().doubleValue();
        double lon = report.getLongitude().doubleValue();

        for (Hazard hazard : report.getHazards()) {
            HazardType hazardType = HazardType.fromLabel(hazard.getLabel());
            if (hazardType == null) {
                continue;
            }
            double severity = hazard.getConfidence() != null ? hazard.getConfidence() : 50.0;
            try {
                if (hazardType.isAccessibilityHazard()) {
                    int updated = walkAccessibilityService.updateForHazard(
                            hazard.getId(),
                            hazard.getLabel(),
                            lat,
                            lon,
                            severity
                    );
                    LOGGER.info("Updated {} edge costs for hazard {} ({})",
                            updated, hazard.getId(), hazard.getLabel());
                } else if (hazardType == HazardType.POTHOLE) {
                    int updated = walkAccessibilityService.updateForPotholeHazard(
                            hazard.getId(),
                            hazard.getLabel(),
                            lat,
                            lon,
                            severity,
                            hasDeepPothole
                    );
                    LOGGER.info("Updated {} edge costs for pothole hazard {} (hasDeepPothole={})",
                            updated, hazard.getId(), hasDeepPothole);
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to update edge costs for hazard {}", hazard.getId(), ex);
            }
        }
    }

    /**
     * Removes hazard contributions from edge costs before deleting the report.
     *
     * @param report the hazard report being deleted
     */
    private void removeAccessibilityEdgeCosts(HazardReport report) {
        if (report.getHazards() == null) {
            return;
        }
        for (Hazard hazard : report.getHazards()) {
            HazardType hazardType = HazardType.fromLabel(hazard.getLabel());
            if (hazardType != null && (hazardType.isAccessibilityHazard() || hazardType == HazardType.POTHOLE)) {
                try {
                    int updated = walkAccessibilityService.removeHazard(hazard.getId());
                    LOGGER.info("Removed hazard {} from {} edge costs", hazard.getId(), updated);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Failed to remove hazard {} from edge costs", hazard.getId(), ex);
                }
            }
        }
    }
}
