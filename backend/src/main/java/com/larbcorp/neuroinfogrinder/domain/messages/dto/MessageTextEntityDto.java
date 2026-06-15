package com.larbcorp.neuroinfogrinder.domain.messages.dto;

public record MessageTextEntityDto(
    String type,
    Integer offset,
    Integer length,
    String url,
    String text
) {
}
