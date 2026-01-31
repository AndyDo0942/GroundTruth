package com.team.GroundTruth.controller;


import com.team.GroundTruth.domain.dto.ErrorResponseDto;
import com.team.GroundTruth.exception.HazardReportNotFoundException;
import com.team.GroundTruth.exception.UserNotFoundException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream().findFirst().map(DefaultMessageSourceResolvable::getDefaultMessage).orElse("Validation failed.");
        ErrorResponseDto errorDto = new ErrorResponseDto(errorMessage);

        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleExceptions(UserNotFoundException ex) {
        ErrorResponseDto errorResponseDto = new ErrorResponseDto(String.format("User with ID '%s' not found", ex.getId()));

        return new ResponseEntity<>(errorResponseDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HazardReportNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleHazardReportNotFound(HazardReportNotFoundException ex) {
        ErrorResponseDto errorResponseDto = new ErrorResponseDto(String.format("Hazard report with ID '%s' not found", ex.getId()));

        return new ResponseEntity<>(errorResponseDto, HttpStatus.BAD_REQUEST);
    }
}
