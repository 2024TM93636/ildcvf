package com.ildcvf.contract.web;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/contract")
public class BaselineController {

    private final Path baselinePath;

    public BaselineController(
            @Value("${ildcvf.contract.baseline-path}") String baselinePath) {

        this.baselinePath = Path.of(baselinePath)
                .toAbsolutePath()
                .normalize();
    }

    @GetMapping(
            value = "/baseline",
            produces = "application/yaml"
    )
    public ResponseEntity<String> getBaseline() throws IOException {

        String yaml = Files.readString(
                baselinePath,
                StandardCharsets.UTF_8
        );

        return ResponseEntity.ok(yaml);
    }
}