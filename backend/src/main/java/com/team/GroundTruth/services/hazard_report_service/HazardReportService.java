package com.team.GroundTruth.services.hazard_report_service;

import com.team.GroundTruth.domain.HazardReportRequest;
import com.team.GroundTruth.domain.entity.HazardReport.HazardReport;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for hazard report operations.
 */
public interface HazardReportService {
    /**
     * Creates and persists a new hazard report.
     *
     * @param hazardReportRequest request containing report details
     * @return created hazard report
     */
    HazardReport createHazardReport(HazardReportRequest hazardReportRequest);

    /**
     * Returns a list of all hazard reports in the database.
     *
     * @return list of hazard reports
     */
    List<HazardReport> getHazardReports();

    /**
     * Updates a hazard report by id with the supplied data.
     *
     * @param hazardReportId report identifier
     * @param updateHazardReportRequest updated report data
     * @return updated hazard report
     */
    HazardReport updateHazardReport(UUID hazardReportId, HazardReportRequest updateHazardReportRequest);

    /**
     * Deletes a hazard report by id.
     *
     * @param hazardReportId report identifier
     */
    void deleteHazardReport(UUID hazardReportId);
}
