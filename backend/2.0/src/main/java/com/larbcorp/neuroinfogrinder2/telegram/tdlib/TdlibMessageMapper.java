package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.ingest.LocalMessageInput;
import org.drinkless.tdlib.TdApi;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class TdlibMessageMapper {
    private final ObjectMapper objectMapper;

    public TdlibMessageMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LocalMessageInput toLocalMessageInput(long accountId, TdApi.Message message, String senderName, String senderUsername, Boolean senderIsBot) {
        TdApi.FormattedText text = text(message.content);
        TdApi.FormattedText caption = caption(message.content);
        ObjectNode media = media(message.content);
        String contentType = message.content == null ? "Unknown" : message.content.getClass().getSimpleName();
        Long senderId = senderUserId(message);
        Long replyToMessageId = replyToMessageId(message);

        return new LocalMessageInput(
                accountId,
                message.chatId,
                message.id,
                topicId(message.topicId),
                senderId,
                senderName,
                senderUsername,
                senderIsBot,
                replyToMessageId,
                text == null ? null : text.text,
                caption == null ? null : caption.text,
                message.date > 0 ? Instant.ofEpochSecond(message.date).toString() : Instant.now().toString(),
                message.editDate > 0 ? Instant.ofEpochSecond(message.editDate).toString() : null,
                contentType,
                text == null ? List.of() : jsonArray(text.entities),
                caption == null ? List.of() : jsonArray(caption.entities),
                media,
                rawMessageJson(message, contentType, text, caption, senderName, senderUsername, senderIsBot)
        );
    }

    public ObjectNode rawMessageJson(TdApi.Message message, String contentType, TdApi.FormattedText text, TdApi.FormattedText caption, String senderName, String senderUsername, Boolean senderIsBot) {
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("@type", "tdlibMessage");
        raw.put("id", message.id);
        raw.put("chat_id", message.chatId);
        raw.put("date", message.date);
        raw.put("edit_date", message.editDate);
        raw.put("content_type", contentType);
        raw.put("sender_name", senderName);
        raw.put("sender_username", senderUsername);
        raw.put("sender_is_bot", Boolean.TRUE.equals(senderIsBot));
        Long topic = topicId(message.topicId);
        if (topic != null) {
            raw.put("message_thread_id", topic);
        }
        Long reply = replyToMessageId(message);
        if (reply != null) {
            ObjectNode replyNode = raw.putObject("reply_to");
            replyNode.put("message_id", reply);
        }
        ObjectNode content = raw.putObject("content");
        content.put("@type", contentType);
        if (text != null) {
            ObjectNode formatted = content.putObject("text");
            formatted.put("text", text.text);
            formatted.set("entities", entities(text.entities));
        }
        if (caption != null) {
            ObjectNode formatted = content.putObject("caption");
            formatted.put("text", caption.text);
            formatted.set("entities", entities(caption.entities));
        }
        ObjectNode media = media(message.content);
        if (media != null) {
            content.set("media", media);
        }
        return raw;
    }

    public ArrayNode entities(TdApi.TextEntity[] entities) {
        ArrayNode array = objectMapper.createArrayNode();
        if (entities == null) {
            return array;
        }
        for (TdApi.TextEntity entity : entities) {
            if (entity == null || entity.type == null) {
                continue;
            }
            ObjectNode node = array.addObject();
            node.put("@type", "textEntity");
            node.put("offset", entity.offset);
            node.put("length", entity.length);
            ObjectNode type = node.putObject("type");
            if (entity.type instanceof TdApi.TextEntityTypeTextUrl textUrl) {
                type.put("@type", "textEntityTypeTextUrl");
                type.put("url", textUrl.url);
            } else if (entity.type instanceof TdApi.TextEntityTypeUrl) {
                type.put("@type", "textEntityTypeUrl");
            } else if (entity.type instanceof TdApi.TextEntityTypeMention) {
                type.put("@type", "textEntityTypeMention");
            } else if (entity.type instanceof TdApi.TextEntityTypeMentionName mentionName) {
                type.put("@type", "textEntityTypeMentionName");
                type.put("user_id", mentionName.userId);
            } else if (entity.type instanceof TdApi.TextEntityTypeEmailAddress) {
                type.put("@type", "textEntityTypeEmailAddress");
            } else if (entity.type instanceof TdApi.TextEntityTypePhoneNumber) {
                type.put("@type", "textEntityTypePhoneNumber");
            } else if (entity.type instanceof TdApi.TextEntityTypeBotCommand) {
                type.put("@type", "textEntityTypeBotCommand");
            } else {
                type.put("@type", entity.type.getClass().getSimpleName());
            }
        }
        return array;
    }

    private List<JsonNode> jsonArray(TdApi.TextEntity[] entities) {
        ArrayNode array = entities(entities);
        return objectMapper.convertValue(array, objectMapper.getTypeFactory().constructCollectionType(List.class, JsonNode.class));
    }

    private TdApi.FormattedText text(TdApi.MessageContent content) {
        if (content instanceof TdApi.MessageText messageText) {
            return messageText.text;
        }
        return null;
    }

    private TdApi.FormattedText caption(TdApi.MessageContent content) {
        if (content instanceof TdApi.MessagePhoto messagePhoto) {
            return messagePhoto.caption;
        }
        if (content instanceof TdApi.MessageVideo messageVideo) {
            return messageVideo.caption;
        }
        if (content instanceof TdApi.MessageDocument messageDocument) {
            return messageDocument.caption;
        }
        if (content instanceof TdApi.MessageAnimation messageAnimation) {
            return messageAnimation.caption;
        }
        if (content instanceof TdApi.MessageAudio messageAudio) {
            return messageAudio.caption;
        }
        if (content instanceof TdApi.MessageVoiceNote messageVoiceNote) {
            return messageVoiceNote.caption;
        }
        return null;
    }

    private ObjectNode media(TdApi.MessageContent content) {
        if (content == null || content instanceof TdApi.MessageText) {
            return null;
        }
        ObjectNode media = objectMapper.createObjectNode();
        media.put("@type", content.getClass().getSimpleName());
        if (content instanceof TdApi.MessageDocument messageDocument && messageDocument.document != null) {
            media.put("file_name", messageDocument.document.fileName);
            media.put("mime_type", messageDocument.document.mimeType);
            putFile(media, messageDocument.document.document);
        } else if (content instanceof TdApi.MessageVideo messageVideo && messageVideo.video != null) {
            media.put("file_name", messageVideo.video.fileName);
            media.put("mime_type", messageVideo.video.mimeType);
            putFile(media, messageVideo.video.video);
        } else if (content instanceof TdApi.MessageAnimation messageAnimation && messageAnimation.animation != null) {
            media.put("file_name", messageAnimation.animation.fileName);
            media.put("mime_type", messageAnimation.animation.mimeType);
            putFile(media, messageAnimation.animation.animation);
        } else if (content instanceof TdApi.MessageAudio messageAudio && messageAudio.audio != null) {
            media.put("file_name", messageAudio.audio.fileName);
            media.put("mime_type", messageAudio.audio.mimeType);
            putFile(media, messageAudio.audio.audio);
        } else if (content instanceof TdApi.MessageVoiceNote messageVoiceNote && messageVoiceNote.voiceNote != null) {
            media.put("mime_type", messageVoiceNote.voiceNote.mimeType);
            putFile(media, messageVoiceNote.voiceNote.voice);
        } else if (content instanceof TdApi.MessagePhoto) {
            media.put("mime_type", "image/jpeg");
        }
        return media;
    }

    private void putFile(ObjectNode media, TdApi.File file) {
        if (file == null) {
            return;
        }
        media.put("file_id", file.remote != null && file.remote.id != null && !file.remote.id.isBlank()
                ? file.remote.id
                : String.valueOf(file.id));
        if (file.remote != null && file.remote.uniqueId != null && !file.remote.uniqueId.isBlank()) {
            media.put("file_unique_id", file.remote.uniqueId);
        }
        media.put("file_size", file.size);
        ObjectNode fileNode = media.putObject("file");
        fileNode.put("id", file.id);
        fileNode.put("size", file.size);
        fileNode.put("expected_size", file.expectedSize);
        if (file.remote != null) {
            ObjectNode remote = fileNode.putObject("remote");
            remote.put("id", file.remote.id);
            remote.put("unique_id", file.remote.uniqueId);
            remote.put("uploaded_size", file.remote.uploadedSize);
        }
    }

    private Long senderUserId(TdApi.Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser senderUser) {
            return senderUser.userId;
        }
        if (message.senderId instanceof TdApi.MessageSenderChat senderChat) {
            return senderChat.chatId;
        }
        return null;
    }

    private Long replyToMessageId(TdApi.Message message) {
        if (message.replyTo instanceof TdApi.MessageReplyToMessage replyToMessage) {
            return replyToMessage.messageId;
        }
        return null;
    }

    private Long topicId(TdApi.MessageTopic topic) {
        if (topic instanceof TdApi.MessageTopicThread thread) {
            return thread.messageThreadId;
        }
        if (topic instanceof TdApi.MessageTopicForum forum) {
            return (long) forum.forumTopicId;
        }
        return null;
    }
}
