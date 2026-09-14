package com.ildcvf.demo.resident.repository;

import com.ildcvf.demo.resident.domain.Resident;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryResidentRepository implements ResidentRepository {

    private final ConcurrentMap<Long, Resident> residents = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @Override
    public Resident save(Resident resident) {
        Resident residentToSave = resident;

        if (resident.id() == null) {
            long generatedId = idSequence.getAndIncrement();

            residentToSave = new Resident(
                    generatedId,
                    resident.firstName(),
                    resident.lastName(),
                    resident.email(),
                    resident.dateOfBirth(),
                    resident.phoneNumber(),
                    resident.status(),
                    resident.roomNumber()
            );
        }

        residents.put(residentToSave.id(), residentToSave);

        return residentToSave;
    }

    @Override
    public Optional<Resident> findById(Long id) {
        return Optional.ofNullable(residents.get(id));
    }

    @Override
    public List<Resident> findAll() {
        return residents.values()
                .stream()
                .sorted(Comparator.comparing(Resident::id))
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        residents.remove(id);
    }
}