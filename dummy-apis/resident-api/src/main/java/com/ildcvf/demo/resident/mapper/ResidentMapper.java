package com.ildcvf.demo.resident.mapper;

import com.ildcvf.demo.resident.domain.Resident;
import com.ildcvf.demo.resident.dto.request.CreateResidentRequest;
import com.ildcvf.demo.resident.dto.request.UpdateResidentRequest;
import com.ildcvf.demo.resident.dto.response.ResidentResponse;
import org.springframework.stereotype.Component;

@Component
public class ResidentMapper {

    public Resident toDomain(CreateResidentRequest request) {
        return new Resident(
                null,
                request.firstName(),
                request.lastName(),
                request.email(),
                request.dateOfBirth(),
                request.phoneNumber(),
                request.status(),
                request.roomNumber()
        );
    }

    public Resident toDomain(UpdateResidentRequest request) {
        return new Resident(
                null,
                request.firstName(),
                request.lastName(),
                request.email(),
                request.dateOfBirth(),
                request.phoneNumber(),
                request.status(),
                request.roomNumber()
        );
    }

    public ResidentResponse toResponse(Resident resident) {
        return new ResidentResponse(
                resident.id(),
                resident.firstName(),
                resident.lastName(),
                resident.email(),
                resident.dateOfBirth(),
                resident.phoneNumber(),
                resident.status(),
                resident.roomNumber()
        );
    }
}