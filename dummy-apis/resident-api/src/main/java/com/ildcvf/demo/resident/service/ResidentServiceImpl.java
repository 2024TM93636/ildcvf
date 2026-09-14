package com.ildcvf.demo.resident.service;

import com.ildcvf.demo.resident.domain.Resident;
import com.ildcvf.demo.resident.exception.ResidentNotFoundException;
import com.ildcvf.demo.resident.repository.ResidentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResidentServiceImpl implements ResidentService {

    private final ResidentRepository residentRepository;

    public ResidentServiceImpl(ResidentRepository residentRepository) {
        this.residentRepository = residentRepository;
    }

    @Override
    public Resident create(Resident resident) {
        Resident newResident = new Resident(
                null,
                resident.firstName(),
                resident.lastName(),
                resident.email(),
                resident.dateOfBirth(),
                resident.phoneNumber(),
                resident.status(),
                resident.roomNumber()
        );

        return residentRepository.save(newResident);
    }

    @Override
    public List<Resident> getAll() {
        return residentRepository.findAll();
    }

    @Override
    public Resident getById(Long id) {
        return residentRepository.findById(id)
                .orElseThrow(() -> new ResidentNotFoundException(id));
    }

    @Override
    public Resident update(Long id, Resident resident) {
        getById(id);

        Resident updatedResident = new Resident(
                id,
                resident.firstName(),
                resident.lastName(),
                resident.email(),
                resident.dateOfBirth(),
                resident.phoneNumber(),
                resident.status(),
                resident.roomNumber()
        );

        return residentRepository.save(updatedResident);
    }

    @Override
    public void delete(Long id) {
        getById(id);
        residentRepository.deleteById(id);
    }
}