package com.larbcorp.neuroinfogrinder2.telegram.model;

public record TelegramAuthStateResponse(
        String state,
        String status,
        boolean ready,
        boolean waitPhoneNumber,
        boolean waitCode,
        boolean waitPassword,
        String lastError
) {
}
