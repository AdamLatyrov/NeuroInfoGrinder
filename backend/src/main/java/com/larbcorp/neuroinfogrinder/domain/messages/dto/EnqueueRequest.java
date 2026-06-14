package com.larbcorp.neuroinfogrinder.domain.messages.dto;

public record EnqueueRequest(
        boolean force
) {
    public EnqueueRequest() {
        this(false);
    }
}
