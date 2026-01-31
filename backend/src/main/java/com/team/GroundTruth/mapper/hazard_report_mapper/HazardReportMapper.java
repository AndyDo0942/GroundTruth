package com.team.GroundTruth.mapper.hazard_report_mapper;

import com.team.GroundTruth.domain.HazardReportRequest;
import com.team.GroundTruth.domain.dto.hazardreport.HazardReportRequestDto;
import com.team.GroundTruth.domain.dto.hazardreport.HazardReportDto;
import com.team.GroundTruth.domain.entity.HazardReport.HazardReport;

/**
 * Maps hazard-report DTOs to domain models and back.
 */
public interface HazardReportMapper {
    /**
     * Converts the incoming hazard-report DTO into a domain request.
     *
     * @param dto incoming request DTO
     * @return domain request
     */
    HazardReportRequest fromDto(HazardReportRequestDto dto);

    /**
     * Converts a domain hazard report to an outbound DTO.
     *
     * @param report hazard report entity
     * @return hazard report DTO
     */
    HazardReportDto toDto(HazardReport report);
}
