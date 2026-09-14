package com.ildcvf.demo.resident.service;

import com.ildcvf.demo.resident.domain.Resident;
import com.ildcvf.demo.resident.domain.ResidentStatus;
import com.ildcvf.demo.resident.exception.ResidentNotFoundException;
import com.ildcvf.demo.resident.repository.InMemoryResidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResidentServiceImplTest {

    private ResidentService residentService;

    @BeforeEach
    void setUp() {
        residentService = new ResidentServiceImpl(
                new InMemoryResidentRepository()
        );
    }

    @Test
    void shouldCreateResidentWithGeneratedId() {
        Resident resident = createResident("asha@example.com");

        Resident created = residentService.create(resident);

        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.firstName()).isEqualTo("Asha");
        assertThat(created.email()).isEqualTo("asha@example.com");
        assertThat(created.status()).isEqualTo(ResidentStatus.ACTIVE);
    }

    @Test
    void shouldReturnResidentsInIdOrder() {
        residentService.create(createResident("first@example.com"));
        residentService.create(createResident("second@example.com"));

        var residents = residentService.getAll();

        assertThat(residents).hasSize(2);
        assertThat(residents.get(0).id()).isEqualTo(1L);
        assertThat(residents.get(1).id()).isEqualTo(2L);
    }

    @Test
    void shouldReturnResidentById() {
        Resident created = residentService.create(
                createResident("asha@example.com")
        );

        Resident result = residentService.getById(created.id());

        assertThat(result.email()).isEqualTo("asha@example.com");
    }

    @Test
    void shouldThrowWhenResidentDoesNotExist() {
        assertThatThrownBy(() -> residentService.getById(999L))
                .isInstanceOf(ResidentNotFoundException.class)
                .hasMessage("Resident not found with id: 999");
    }

    @Test
    void shouldUpdateExistingResident() {
        Resident created = residentService.create(
                createResident("asha@example.com")
        );

        Resident replacement = new Resident(
                null,
                "Asha",
                "Nair",
                "asha.updated@example.com",
                LocalDate.of(1994, 5, 20),
                "+919876543211",
                ResidentStatus.INACTIVE,
                "B-202"
        );

        Resident updated = residentService.update(
                created.id(),
                replacement
        );

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.email())
                .isEqualTo("asha.updated@example.com");
        assertThat(updated.status())
                .isEqualTo(ResidentStatus.INACTIVE);
        assertThat(updated.roomNumber())
                .isEqualTo("B-202");
    }

    @Test
    void shouldThrowWhenUpdatingUnknownResident() {
        Resident replacement = createResident("unknown@example.com");

        assertThatThrownBy(
                () -> residentService.update(999L, replacement)
        )
                .isInstanceOf(ResidentNotFoundException.class);
    }

    @Test
    void shouldDeleteExistingResident() {
        Resident created = residentService.create(
                createResident("asha@example.com")
        );

        residentService.delete(created.id());

        assertThatThrownBy(
                () -> residentService.getById(created.id())
        )
                .isInstanceOf(ResidentNotFoundException.class);
    }

    private Resident createResident(String email) {
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