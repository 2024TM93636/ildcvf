package com.ildcvf.traffic.web;

import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.repository.TrafficEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/traffic")
public class TrafficDebugController {

    private final TrafficEventRepository repository;

    public TrafficDebugController(
            TrafficEventRepository repository
    ) {
        this.repository = repository;
    }

    @GetMapping
    public List<TrafficEvent> getAll() {
        return repository.findAll();
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<TrafficEvent> getById(
            @PathVariable String eventId
    ) {
        return repository
                .findById(eventId)
                .map(ResponseEntity::ok)
                .orElseGet(
                        () -> ResponseEntity
                                .notFound()
                                .build()
                );
    }
}