package com.ildcvf.demo.resident.controller;

import com.ildcvf.demo.resident.domain.Resident;
import com.ildcvf.demo.resident.domain.ResidentStatus;
import com.ildcvf.demo.resident.service.ResidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ResidentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResidentService residentService;

    @Test
    void shouldCreateResident() throws Exception {
        String request = """
                {
                  "firstName": "Asha",
                  "lastName": "Nair",
                  "email": "asha.create@example.com",
                  "dateOfBirth": "1994-05-20",
                  "phoneNumber": "+919876543210",
                  "status": "ACTIVE",
                  "roomNumber": "A-101"
                }
                """;

        mockMvc.perform(post("/api/residents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Asha"))
                .andExpect(jsonPath("$.lastName").value("Nair"))
                .andExpect(jsonPath("$.email")
                        .value("asha.create@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roomNumber").value("A-101"));
    }

    @Test
    void shouldReturnAllResidents() throws Exception {
        residentService.create(
                resident("resident.list@example.com")
        );

        mockMvc.perform(get("/api/residents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(
                        jsonPath("$[*].email",
                                hasItem("resident.list@example.com"))
                );
    }

    @Test
    void shouldReturnResidentById() throws Exception {
        Resident created = residentService.create(
                resident("resident.get@example.com")
        );

        mockMvc.perform(
                        get("/api/residents/{id}", created.id())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id()))
                .andExpect(jsonPath("$.email")
                        .value("resident.get@example.com"));
    }

    @Test
    void shouldReturn404WhenResidentDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/residents/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("RESIDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path")
                        .value("/api/residents/999999"));
    }

    @Test
    void shouldUpdateResident() throws Exception {
        Resident created = residentService.create(
                resident("resident.before@example.com")
        );

        String request = """
                {
                  "firstName": "Asha",
                  "lastName": "Nair",
                  "email": "resident.after@example.com",
                  "dateOfBirth": "1994-05-20",
                  "phoneNumber": "+919876543211",
                  "status": "INACTIVE",
                  "roomNumber": "B-202"
                }
                """;

        mockMvc.perform(
                        put("/api/residents/{id}", created.id())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id()))
                .andExpect(jsonPath("$.email")
                        .value("resident.after@example.com"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.roomNumber").value("B-202"));
    }

    @Test
    void shouldDeleteResident() throws Exception {
        Resident created = residentService.create(
                resident("resident.delete@example.com")
        );

        mockMvc.perform(
                        delete("/api/residents/{id}", created.id())
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/residents/{id}", created.id())
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400ForValidationFailure() throws Exception {
        String request = """
                {
                  "firstName": "",
                  "lastName": "N",
                  "email": "invalid-email",
                  "dateOfBirth": "2999-01-01",
                  "phoneNumber": "123",
                  "status": null,
                  "roomNumber": "A-101"
                }
                """;

        mockMvc.perform(post("/api/residents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.lastName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").exists())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void shouldAllowOptionalFieldsToBeOmitted() throws Exception {
        String request = """
                {
                  "firstName": "Maya",
                  "lastName": "Thomas",
                  "email": "maya.optional@example.com",
                  "dateOfBirth": "1990-03-10",
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(post("/api/residents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.roomNumber").doesNotExist());
    }

    @Test
    void shouldReturn400ForMalformedJson() throws Exception {
        String malformedJson = """
                {
                  "firstName":
                """;

        mockMvc.perform(post("/api/residents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MALFORMED_REQUEST"));
    }

    @Test
    void shouldReturn400ForInvalidResidentId() throws Exception {
        mockMvc.perform(get("/api/residents/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));
    }

    private Resident resident(String email) {
        return new Resident(
                null,
                "Asha",
                "Nair",
                email,
                LocalDate.of(1994, 5, 20),
                "+919876543210",
                ResidentStatus.ACTIVE,
                "A-101"
        );
    }
}