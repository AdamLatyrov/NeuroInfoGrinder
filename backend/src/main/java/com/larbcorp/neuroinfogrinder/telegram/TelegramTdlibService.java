package com.larbcorp.neuroinfogrinder.telegram;

import com.larbcorp.neuroinfogrinder.telegram.model.TelegramAuthStateResponse;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramChatDto;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessageDto;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramMessagesBatchResponse;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramTopicDto;
import com.larbcorp.neuroinfogrinder.telegram.tdlib.TdlibClientManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TelegramTdlibService {

    private final TdlibClientManager tdlibClientManager;

    public TelegramTdlibService(TdlibClientManager tdlibClientManager) {
        this.tdlibClientManager = tdlibClientManager;
    }

    public TelegramAuthStateResponse getAuthorizationState() {
        return tdlibClientManager.getAuthorizationState();
    }

    public TelegramAuthStateResponse submitPhoneNumber(String phoneNumber) {
        return tdlibClientManager.submitPhoneNumber(phoneNumber);
    }

    public TelegramAuthStateResponse submitCode(String code) {
        return tdlibClientManager.submitCode(code);
    }

    public TelegramAuthStateResponse submitPassword(String password) {
        return tdlibClientManager.submitPassword(password);
    }

    public List<TelegramChatDto> getChats(int limit) {
        return tdlibClientManager.getChats(limit);
    }

    public List<TelegramTopicDto> getTopics(long chatId, int limit) {
        return tdlibClientManager.getTopics(chatId, limit);
    }

    public List<TelegramTopicDto> getCachedTopics(long chatId, int limit) {
        return tdlibClientManager.getCachedTopics(chatId, limit);
    }

    public List<TelegramMessageDto> getMessages(long chatId, long fromMessageId, int limit) {
        return tdlibClientManager.getMessages(chatId, fromMessageId, limit);
    }

    public TelegramMessagesBatchResponse getMessagesForChats(List<Long> chatIds, int limitPerChat) {
        return tdlibClientManager.getMessagesForChats(chatIds, limitPerChat);
    }

    public Optional<TelegramMessageDto> getLatestMessage(long chatId) {
        return tdlibClientManager.getLatestMessage(chatId);
    }

    public String resolveUserDisplayName(long userId) {
        return tdlibClientManager.resolveUserDisplayName(userId);
    }

    public String resolveUserUsername(long userId) {
        return tdlibClientManager.resolveUserUsername(userId);
    }
}
