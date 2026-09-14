package com.ildcvf.demo.resident.dto.request;

import com.ildcvf.demo.resident.domain.ResidentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateResidentRequest(

        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 50, message = "First name must contain between 2 and 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 2, max = 50, message = "Last name must contain between 2 and 50 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Pattern(
                regexp = "^\\+?[1-9]\\d{7,14}$",
                message = "Phone number must contain 8 to 15 digits and may start with +"
        )
        String phoneNumber,

        @NotNull(message = "Status is required")
        ResidentStatus status,

        @Size(max = 20, message = "Room number must not exceed 20 characters")
        String roomNumber
) {
}