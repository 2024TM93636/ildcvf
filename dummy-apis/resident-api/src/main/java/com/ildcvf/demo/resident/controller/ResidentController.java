package com.ildcvf.demo.resident.controller;

import com.ildcvf.demo.resident.domain.Resident;
import com.ildcvf.demo.resident.dto.request.CreateResidentRequest;
import com.ildcvf.demo.resident.dto.request.UpdateResidentRequest;
import com.ildcvf.demo.resident.dto.response.ResidentResponse;
import com.ildcvf.demo.resident.mapper.ResidentMapper;
import com.ildcvf.demo.resident.service.ResidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/residents")
@Validated
public class ResidentController {

    private final ResidentService residentService;
    private final ResidentMapper residentMapper;

    public ResidentController(
            ResidentService residentService,
            ResidentMapper residentMapper) {
        this.residentService = residentService;
        this.residentMapper = residentMapper;
    }

    @PostMapping
    public ResponseEntity<ResidentResponse> createResident(
            @Valid @RequestBody CreateResidentRequest request) {
        Resident resident = residentMapper.toDomain(request);
        Resident createdResident = residentService.create(resident);

        URI location = URI.create("/api/residents/" + createdResident.id());

        return ResponseEntity
                .created(location)
                .body(residentMapper.toResponse(createdResident));
    }

    @GetMapping
    public ResponseEntity<List<ResidentResponse>> getAllResidents() {
        List<ResidentResponse> response = residentService.getAll()
                .stream()
                .map(residentMapper::toResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getResident(
            @PathVariable @Positive(message = "Resident id must be greater than zero") Long id) {
        Resident resident = residentService.getById(id);
        ResidentResponse response = residentMapper.toResponse(resident);

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("id", String.valueOf(response.id()));
        body.put("firstName", response.firstName());
        body.put("lastName", response.lastName());
        body.put("email", response.email());
        body.put("dateOfBirth", response.dateOfBirth());
        body.put("phoneNumber", response.phoneNumber());
        body.put("status", response.status());
        body.put("roomNumber", response.roomNumber());

        return ResponseEntity.ok(body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResidentResponse> updateResident(
            @PathVariable @Positive(message = "Resident id must be greater than zero") Long id,
            @Valid @RequestBody UpdateResidentRequest request) {
        Resident resident = residentMapper.toDomain(request);
        Resident updatedResident = residentService.update(id, resident);

        return ResponseEntity.ok(residentMapper.toResponse(updatedResident));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResident(
            @PathVariable @Positive(message = "Resident id must be greater than zero") Long id) {
        residentService.delete(id);

        return ResponseEntity.noContent().build();
    }
}