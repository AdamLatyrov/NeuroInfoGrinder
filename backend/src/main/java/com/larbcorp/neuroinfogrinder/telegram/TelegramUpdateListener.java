package com.larbcorp.neuroinfogrinder.telegram;

import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;

public interface TelegramUpdateListener {

    void onNewMessage(TelegramMessageDto message);

    default void onNewMessage(Long telegramAccountId, TelegramMessageDto message) {
        onNewMessage(message);
    }
}
