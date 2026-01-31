package com.team.GroundTruth.domain.dto.user;

import com.team.GroundTruth.domain.entity.HazardReport.HazardReport;

import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String username,
        List<HazardReport> hazardReports
) {
}
