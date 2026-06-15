package com.larbcorp.neuroinfogrinder.domain.findings.dto;

public record SourceMessageTextEntityDto(
    String type,
    Integer offset,
    Integer length,
    String url,
    String text
) {
}
