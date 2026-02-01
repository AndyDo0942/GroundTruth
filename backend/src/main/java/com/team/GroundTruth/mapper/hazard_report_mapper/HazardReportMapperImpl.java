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
    public HazardReportRequest fromDto(HazardReportRequestDto dto, byte[] imageBytes, String imageContentType) {
        return new HazardReportRequest(imageBytes, imageContentType, dto.latitude(), dto.longitude());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HazardReportDto toDto(HazardReport report) {
        return new HazardReportDto(
                report.getId(),
                report.getImageBytes(),
                report.getCreatedAt(),
                report.getHazards()
        );
    }
}
