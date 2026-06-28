package com.larbcorp.neuroinfogrinder.domain.messages;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.MessageTextEntityDto;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.MessageEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MessageTextFormatter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MessageTextFormatter() {
    }

    public static String promptText(MessageEntity message) {
        if (message == null) {
            return "";
        }
        return withMarkdownLinks(message.getText(), message.getTextEntitiesJson());
    }

    public static String withMarkdownLinks(String text, String textEntitiesJson) {
        String source = text == null ? "" : text;
        if (source.isBlank() || textEntitiesJson == null || textEntitiesJson.isBlank()) {
            return source;
        }

        List<LinkRange> ranges = linkRanges(source, textEntitiesJson);
        if (ranges.isEmpty()) {
            return source;
        }

        StringBuilder builder = new StringBuilder(source);
        ranges.stream()
            .sorted(Comparator.comparingInt(LinkRange::start).reversed())
            .forEach(range -> builder.replace(
                range.start(),
                range.end(),
                "[" + escapeLinkLabel(source.substring(range.start(), range.end())) + "](" + range.url() + ")"
            ));
        return builder.toString();
    }

    public static List<String> links(String text, String textEntitiesJson) {
        String source = text == null ? "" : text;
        List<String> result = new ArrayList<>();
        for (LinkRange range : linkRanges(source, textEntitiesJson)) {
            if (!result.contains(range.url())) {
                result.add(range.url());
            }
        }
        return result;
    }

    private static List<LinkRange> linkRanges(String text, String textEntitiesJson) {
        List<MessageTextEntityDto> entities = parseEntities(textEntitiesJson);
        if (entities.isEmpty()) {
            return List.of();
        }

        List<LinkRange> ranges = entities.stream()
            .map(entity -> toLinkRange(text, entity))
            .filter(range -> range != null)
            .sorted(Comparator.comparingInt(LinkRange::start).thenComparing(Comparator.comparingInt(LinkRange::end).reversed()))
            .toList();

        List<LinkRange> nonOverlapping = new ArrayList<>();
        int lastEnd = 0;
        for (LinkRange range : ranges) {
            if (range.start() < lastEnd) {
                continue;
            }
            nonOverlapping.add(range);
            lastEnd = range.end();
        }
        return nonOverlapping;
    }

    private static LinkRange toLinkRange(String text, MessageTextEntityDto entity) {
        if (entity == null || entity.offset() == null || entity.length() == null) {
            return null;
        }

        int start = Math.max(0, Math.min(entity.offset(), text.length()));
        int end = Math.max(start, Math.min(start + entity.length(), text.length()));
        if (start >= end) {
            return null;
        }

        String label = text.substring(start, end);
        if (entity.text() != null && !entity.text().equals(label)) {
            return null;
        }

        String url = entity.url();
        if ((url == null || url.isBlank()) && "url".equalsIgnoreCase(entity.type())) {
            url = label;
        }
        url = normalizeHttpUrl(url);
        if (url == null) {
            return null;
        }

        return new LinkRange(start, end, url);
    }

    private static List<MessageTextEntityDto> parseEntities(String textEntitiesJson) {
        try {
            return OBJECT_MAPPER.readValue(
                textEntitiesJson,
                new TypeReference<List<MessageTextEntityDto>>() {}
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String normalizeHttpUrl(String url) {
        if (url == null) {
            return null;
        }
        String value = url.trim();
        if (value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return value;
        }
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return null;
        }
        return "https://" + value;
    }

    private static String escapeLinkLabel(String label) {
        return label
            .replace("\\", "\\\\")
            .replace("[", "\\[")
            .replace("]", "\\]");
    }

    private record LinkRange(int start, int end, String url) {
    }
}
