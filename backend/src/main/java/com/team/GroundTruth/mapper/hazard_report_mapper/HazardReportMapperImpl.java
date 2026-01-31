package com.team.GroundTruth.mapper.hazard_report_mapper;

import com.team.GroundTruth.domain.HazardReportRequest;
import com.team.GroundTruth.domain.dto.hazardreport.HazardReportDto;
import com.team.GroundTruth.domain.dto.hazardreport.HazardReportRequestDto;
import com.team.GroundTruth.domain.entity.HazardReport.HazardReport;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Spring component that implements hazard report DTO mapping.
 */
@Component
public class HazardReportMapperImpl implements HazardReportMapper {
    /**
     * {@inheritDoc}
     */
    @Override
    public HazardReportRequest fromDto(HazardReportRequestDto dto) {
        return new HazardReportRequest(dto.userId(), dto.imageUrl());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HazardReportDto toDto(HazardReport report) {
        UUID userId = report.getUser() != null ? report.getUser().getId() : null;
        return new HazardReportDto(
                report.getId(),
                userId,
                report.getImageUrl(),
                report.getCreatedAt(),
                report.getHazards()
        );
    }
}
