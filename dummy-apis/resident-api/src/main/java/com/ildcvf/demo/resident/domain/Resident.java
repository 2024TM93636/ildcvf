package com.ildcvf.demo.resident.domain;

import java.time.LocalDate;

public record Resident(
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