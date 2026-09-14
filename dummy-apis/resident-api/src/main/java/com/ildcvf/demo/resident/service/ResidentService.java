package com.ildcvf.demo.resident.service;

import com.ildcvf.demo.resident.domain.Resident;

import java.util.List;

public interface ResidentService {

    Resident create(Resident resident);

    List<Resident> getAll();

    Resident getById(Long id);

    Resident update(Long id, Resident resident);

    void delete(Long id);
}