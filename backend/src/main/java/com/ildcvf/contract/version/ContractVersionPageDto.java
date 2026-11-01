package com.ildcvf.contract.version;

import org.springframework.data.domain.Page;

import java.util.List;

public record ContractVersionPageDto(
        List<ContractVersionMetadataDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static ContractVersionPageDto from(
            Page<ContractVersionRecord> source) {

        return new ContractVersionPageDto(
                source.getContent().stream()
                        .map(ContractVersionMetadataDto::from)
                        .toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages()
        );
    }
}
