package com.ildcvf.contract.version;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/contract/versions")
public class ContractVersionController {

    private final ContractCandidateCreationService creation;
    private final ContractVersionPersistenceService versions;

    public ContractVersionController(
            ContractCandidateCreationService creation,
            ContractVersionPersistenceService versions) {
        this.creation = creation;
        this.versions = versions;
    }

    @PostMapping
    public ResponseEntity<ContractVersionMetadataDto> create(
            @RequestBody ContractVersionCreateRequest request) {

        if (request == null || request.proposalAttemptId() == null) {
            throw new IllegalArgumentException(
                    "proposalAttemptId is required"
            );
        }

        ContractVersionRecord created =
                creation.create(request.proposalAttemptId());

        var location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(ContractVersionMetadataDto.from(created));
    }

    @GetMapping
    public ContractVersionPageDto history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ContractVersionPageDto.from(
                versions.history(page, size)
        );
    }

    @GetMapping("/by-proposal/{proposalAttemptId}")
    public ContractVersionMetadataDto byProposal(
            @PathVariable UUID proposalAttemptId) {

        return versions.findByProposalAttempt(proposalAttemptId)
                .map(ContractVersionMetadataDto::from)
                .orElseThrow(() -> new ContractVersionNotFoundException(
                        "Contract version not found"
                ));
    }

    @GetMapping("/{id}")
    public ContractVersionMetadataDto detail(
            @PathVariable UUID id) {

        return versions.find(id)
                .map(ContractVersionMetadataDto::from)
                .orElseThrow(() -> new ContractVersionNotFoundException(
                        "Contract version not found"
                ));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID id) {

        ContractVersionRecord version = versions.find(id)
                .orElseThrow(() -> new ContractVersionNotFoundException(
                        "Contract version not found"
                ));

        byte[] yaml = version.openapiYaml()
                .getBytes(StandardCharsets.UTF_8);

        String filename = "contract-version-" + version.id() + ".yaml";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/yaml;charset=UTF-8"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .contentLength(yaml.length)
                .body(yaml);
    }
}
