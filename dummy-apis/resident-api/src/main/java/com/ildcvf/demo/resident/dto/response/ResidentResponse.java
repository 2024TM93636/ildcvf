package com.ildcvf.demo.resident.dto.response;

import com.ildcvf.demo.resident.domain.ResidentStatus;

import java.time.LocalDate;

public record ResidentResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth,
        String phoneNumber,
        ResidentStatus status,
        String roomNumber
) {
}