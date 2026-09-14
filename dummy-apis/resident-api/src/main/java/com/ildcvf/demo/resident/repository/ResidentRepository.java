package com.ildcvf.demo.resident.repository;

import com.ildcvf.demo.resident.domain.Resident;

import java.util.List;
import java.util.Optional;

public interface ResidentRepository {

    Resident save(Resident resident);

    Optional<Resident> findById(Long id);

    List<Resident> findAll();

    void deleteById(Long id);
}