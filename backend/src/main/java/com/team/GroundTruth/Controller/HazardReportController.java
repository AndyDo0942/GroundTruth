package com.team.GroundTruth.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.GroundTruth.domain.HazardReportRequest;
import com.team.GroundTruth.domain.dto.hazardreport.HazardReportDto;
import com.team.GroundTruth.domain.dto.hazardreport.HazardReportRequestDto;
import com.team.GroundTruth.domain.entity.HazardReport.HazardReport;
import com.team.GroundTruth.mapper.hazard_report_mapper.HazardReportMapper;
import com.team.GroundTruth.services.hazard_report_service.HazardReportService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for hazard-report endpoints.
 */
@RestController
@RequestMapping("/api/v1/hazards")
public class HazardReportController {
    /**
     * Service layer for hazard report operations.
     */
    private final HazardReportService hazardReportService;
    /**
     * Mapper between DTOs and domain objects.
     */
    private final HazardReportMapper hazardReportMapper;
    /**
     * Object mapper for parsing metadata JSON.
     */
    private final ObjectMapper objectMapper;

    /**
     * Creates a controller with required collaborators.
     *
     * @param hazardReportService service handling hazard report operations
     * @param hazardReportMapper mapper between DTOs and domain models
     */
    public HazardReportController(
            HazardReportService hazardReportService,
            HazardReportMapper hazardReportMapper,
            ObjectMapper objectMapper
    ) {
        this.hazardReportService = hazardReportService;
        this.hazardReportMapper = hazardReportMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new hazard report from the supplied request payload.
     *
     * @param dto request payload containing the report data
     * @return created hazard report representation
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HazardReportDto> createHazardReport(
            @RequestPart("image") MultipartFile image,
            @RequestPart("metadata") String metadataJson
    ) throws IOException {
        HazardReportRequestDto dto = objectMapper.readValue(metadataJson, HazardReportRequestDto.class);
        HazardReportRequest request = hazardReportMapper.fromDto(
                dto,
                image.getBytes(),
                image.getContentType()
        );

        HazardReport report = hazardReportService.createHazardReport(request);

        HazardReportDto reportDto = hazardReportMapper.toDto(report);

        return new ResponseEntity<>(reportDto, HttpStatus.CREATED);
    }

    @GetMapping
    /**
     * Returns all hazard reports.
     *
     * @return list of hazard reports
     */
    public ResponseEntity<List<HazardReportDto>> getAllHazardReports() {
        List<HazardReport> reports = hazardReportService.getHazardReports();
        List<HazardReportDto> reportDtos = reports.stream().map(hazardReportMapper :: toDto).toList();
        return new ResponseEntity<>(reportDtos, HttpStatus.OK);
    }

    @PutMapping("/{reportId}")
    /**
     * Updates a hazard report by id.
     *
     * @param reportId report identifier
     * @param dto request payload containing updated report data
     * @return updated hazard report representation
     */
    public ResponseEntity<HazardReportDto> updateHazardReport(
            @PathVariable UUID reportId,
            @Valid @RequestBody HazardReportRequestDto dto
    ) {
        HazardReportRequest request = hazardReportMapper.fromDto(dto, null, null);

        HazardReport updated = hazardReportService.updateHazardReport(reportId, request);

        HazardReportDto reportDto = hazardReportMapper.toDto(updated);

        return new ResponseEntity<>(reportDto, HttpStatus.OK);
    }

    @DeleteMapping("/{reportId}")
    /**
     * Deletes a hazard report by id.
     *
     * @param reportId report identifier
     * @return no content on success
     */
    public ResponseEntity<HazardReportDto> deleteHazardReport(@PathVariable UUID reportId) {
        hazardReportService.deleteHazardReport(reportId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
