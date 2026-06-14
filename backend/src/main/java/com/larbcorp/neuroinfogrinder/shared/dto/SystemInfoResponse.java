package com.larbcorp.neuroinfogrinder.shared.dto;

public record SystemInfoResponse(
        String application,
        String version,
        String status
) {
}
