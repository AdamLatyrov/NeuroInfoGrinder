package com.larbcorp.neuroinfogrinder.telegram.model;

public record TelegramAuthStateResponse(
        String state,
        boolean ready,
        boolean waitPhoneNumber,
        boolean waitCode,
        boolean waitPassword,
        String lastError
) {
}
