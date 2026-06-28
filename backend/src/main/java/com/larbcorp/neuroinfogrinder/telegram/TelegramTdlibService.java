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

    public TelegramAuthStateResponse getAuthorizationState(Long accountId) {
        return tdlibClientManager.getAuthorizationState(accountId);
    }

    public TelegramAuthStateResponse submitPhoneNumber(String phoneNumber) {
        return tdlibClientManager.submitPhoneNumber(phoneNumber);
    }

    public TelegramAuthStateResponse submitPhoneNumber(Long accountId, String phoneNumber) {
        return tdlibClientManager.submitPhoneNumber(accountId, phoneNumber);
    }

    public TelegramAuthStateResponse submitCode(String code) {
        return tdlibClientManager.submitCode(code);
    }

    public TelegramAuthStateResponse submitCode(Long accountId, String code) {
        return tdlibClientManager.submitCode(accountId, code);
    }

    public TelegramAuthStateResponse submitPassword(String password) {
        return tdlibClientManager.submitPassword(password);
    }

    public TelegramAuthStateResponse submitPassword(Long accountId, String password) {
        return tdlibClientManager.submitPassword(accountId, password);
    }

    public List<TelegramChatDto> getChats(int limit) {
        return tdlibClientManager.getChats(limit);
    }

    public List<TelegramChatDto> getChats(Long accountId, int limit) {
        return tdlibClientManager.getChats(accountId, limit);
    }

    public List<TelegramTopicDto> getTopics(long chatId, int limit) {
        return tdlibClientManager.getTopics(chatId, limit);
    }

    public List<TelegramTopicDto> getTopics(Long accountId, long chatId, int limit) {
        return tdlibClientManager.getTopics(accountId, chatId, limit);
    }

    public List<TelegramTopicDto> getCachedTopics(long chatId, int limit) {
        return tdlibClientManager.getCachedTopics(chatId, limit);
    }

    public List<TelegramTopicDto> getCachedTopics(Long accountId, long chatId, int limit) {
        return tdlibClientManager.getCachedTopics(accountId, chatId, limit);
    }

    public List<TelegramMessageDto> getMessages(long chatId, long fromMessageId, int limit) {
        return tdlibClientManager.getMessages(chatId, fromMessageId, limit);
    }

    public List<TelegramMessageDto> getMessages(Long accountId, long chatId, long fromMessageId, int limit) {
        return tdlibClientManager.getMessages(accountId, chatId, fromMessageId, limit);
    }

    public TelegramMessagesBatchResponse getMessagesForChats(List<Long> chatIds, int limitPerChat) {
        return tdlibClientManager.getMessagesForChats(chatIds, limitPerChat);
    }

    public TelegramMessagesBatchResponse getMessagesForChats(Long accountId, List<Long> chatIds, int limitPerChat) {
        return tdlibClientManager.getMessagesForChats(accountId, chatIds, limitPerChat);
    }

    public Optional<TelegramMessageDto> getLatestMessage(long chatId) {
        return tdlibClientManager.getLatestMessage(chatId);
    }

    public Optional<TelegramMessageDto> getLatestMessage(Long accountId, long chatId) {
        return tdlibClientManager.getLatestMessage(accountId, chatId);
    }

    public long sendTextMessage(long chatId, Long topicId, String text) {
        return tdlibClientManager.sendTextMessage(chatId, topicId, text);
    }

    public long sendTextMessage(Long accountId, long chatId, Long topicId, String text) {
        return tdlibClientManager.sendTextMessage(accountId, chatId, topicId, text);
    }

    public String resolveUserDisplayName(long userId) {
        return tdlibClientManager.resolveUserDisplayName(userId);
    }

    public String resolveUserDisplayName(Long accountId, long userId) {
        return tdlibClientManager.resolveUserDisplayName(accountId, userId);
    }

    public String resolveUserUsername(long userId) {
        return tdlibClientManager.resolveUserUsername(userId);
    }

    public String resolveUserUsername(Long accountId, long userId) {
        return tdlibClientManager.resolveUserUsername(accountId, userId);
    }
}
