package com.team.GroundTruth.services.hazard_report_service;

import com.team.GroundTruth.domain.HazardReportRequest;
import com.team.GroundTruth.domain.entity.HazardReport.HazardReport;
import com.team.GroundTruth.domain.entity.User.User;
import com.team.GroundTruth.exception.HazardReportNotFoundException;
import com.team.GroundTruth.repository.HazardReportRepository;
import com.team.GroundTruth.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link HazardReportService}.
 */
@Service
public class HazardReportServiceImpl implements HazardReportService {

    /**
     * Repository used to persist and load hazard reports.
     */
    private final HazardReportRepository hazardReportRepository;
    /**
     * Repository used to load users for report ownership.
     */
    private final UserRepository userRepository;

    /**
     * Creates the service with its repository dependencies.
     *
     * @param hazardReportRepository repository for hazard report persistence
     * @param userRepository repository for user lookups
     */
    public HazardReportServiceImpl(HazardReportRepository hazardReportRepository, UserRepository userRepository) {
        this.hazardReportRepository = hazardReportRepository;
        this.userRepository = userRepository;
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
        report.setHazards(new ArrayList<>());

        return hazardReportRepository.save(report);
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

        return hazardReportRepository.save(existingReport);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteHazardReport(UUID id) {
        if (!hazardReportRepository.existsById(id)) {
            throw new HazardReportNotFoundException(id);
        }
        hazardReportRepository.deleteById(id);
    }
}
