package com.larbcorp.neuroinfogrinder.domain.questions.dto;

public record QueueStatusResponse(
    long queued,
    long running,
    long stuck,
    boolean paused
) {}
