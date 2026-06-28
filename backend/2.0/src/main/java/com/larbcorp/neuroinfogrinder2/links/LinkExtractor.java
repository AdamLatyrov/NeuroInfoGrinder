package com.larbcorp.neuroinfogrinder2.links;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LinkExtractor {
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)(?<![\\w@])((?:https?://|www\\.)[^\\s<>()\"']+)");
    private static final Pattern SENSITIVE_QUERY_PARAM = Pattern.compile(
            "(?i)([?&](?:token|key|api_key|password|secret|auth|access_token)=)([^&#]*)"
    );

    private final ObjectMapper objectMapper;

    public LinkExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ExtractedLink> extract(String text, JsonNode entities, String source) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Map<String, MutableLink> links = new LinkedHashMap<>();
        extractFromEntities(text, entities, source, links);
        extractByRegexFallback(text, source, links);
        return links.values().stream().map(MutableLink::toExtractedLink).toList();
    }

    public String maskSensitiveUrlForLog(String url) {
        if (url == null) {
            return null;
        }
        return SENSITIVE_QUERY_PARAM.matcher(url).replaceAll("$1***");
    }

    private void extractFromEntities(String text, JsonNode entities, String source, Map<String, MutableLink> links) {
        if (entities == null || !entities.isArray()) {
            return;
        }

        for (JsonNode entity : entities) {
            int offset = intValue(entity, "offset", -1);
            int length = intValue(entity, "length", -1);
            if (offset < 0 || length <= 0) {
                continue;
            }

            EntityShape shape = entityShape(entity);
            if (shape.entityType == null) {
                continue;
            }

            if ("TEXT_URL".equals(shape.entityType)) {
                String anchor = safeSubstring(text, offset, length);
                addLink(links, new MutableLink(
                        shape.url,
                        normalizeUrl(shape.url),
                        domain(shape.url),
                        anchor,
                        source,
                        "TEXT_URL",
                        offset,
                        offset + length,
                        true,
                        false,
                        isTelegramLink(shape.url),
                        isReferralLike(shape.url),
                        rawEntity("TDLIB_ENTITY", entity)
                ));
            } else if ("URL".equals(shape.entityType)) {
                String visibleUrl = safeSubstring(text, offset, length);
                addLink(links, new MutableLink(
                        visibleUrl,
                        normalizeUrl(visibleUrl),
                        domain(visibleUrl),
                        visibleUrl,
                        source,
                        "URL",
                        offset,
                        offset + length,
                        false,
                        true,
                        isTelegramLink(visibleUrl),
                        isReferralLike(visibleUrl),
                        rawEntity("TDLIB_ENTITY", entity)
                ));
            }
        }
    }

    private void extractByRegexFallback(String text, String source, Map<String, MutableLink> links) {
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = trimTrailingUrlPunctuation(matcher.group(1));
            if (raw.isBlank()) {
                continue;
            }
            int start = matcher.start(1);
            int end = start + raw.length();
            addLink(links, new MutableLink(
                    raw,
                    normalizeUrl(raw),
                    domain(raw),
                    raw,
                    source,
                    "URL",
                    start,
                    end,
                    false,
                    true,
                    isTelegramLink(raw),
                    isReferralLike(raw),
                    rawEntity("REGEX_FALLBACK", null)
            ));
        }
    }

    private void addLink(Map<String, MutableLink> links, MutableLink link) {
        if (link.url == null || link.url.isBlank()) {
            return;
        }
        String key = link.normalizedUrl + "|" + link.source + "|" + overlapBucket(link.offsetStart, link.offsetEnd);
        MutableLink existing = links.get(key);
        if (existing == null) {
            links.put(key, link);
        } else {
            existing.mergeRaw(link.rawEntityJson);
        }
    }

    private String overlapBucket(Integer start, Integer end) {
        if (start == null || end == null) {
            return "unknown";
        }
        return start + ":" + end;
    }

    private EntityShape entityShape(JsonNode entity) {
        JsonNode typeNode = entity.path("type");

        if (typeNode.isTextual()) {
            String type = typeNode.asText();
            if ("TEXT_URL".equalsIgnoreCase(type)) {
                return new EntityShape("TEXT_URL", textValue(entity, "url"));
            }
            if ("URL".equalsIgnoreCase(type)) {
                return new EntityShape("URL", null);
            }
        }

        String tdlibType = textValue(typeNode, "@type");
        if ("textEntityTypeTextUrl".equals(tdlibType)) {
            return new EntityShape("TEXT_URL", textValue(typeNode, "url"));
        }
        if ("textEntityTypeUrl".equals(tdlibType)) {
            return new EntityShape("URL", null);
        }

        return new EntityShape(null, null);
    }

    private ObjectNode rawEntity(String detector, JsonNode entity) {
        ObjectNode raw = objectMapper.createObjectNode();
        ArrayNode detectors = raw.putArray("detectors");
        detectors.add(detector);
        if (entity != null) {
            raw.set("entity", entity);
        }
        return raw;
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = trimTrailingUrlPunctuation(url.trim());
        try {
            URI uri = URI.create(withSchemeForParsing(trimmed));
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            return scheme + "://" + host + path + query;
        } catch (IllegalArgumentException ignored) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    private String domain(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(withSchemeForParsing(url.trim()));
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isTelegramLink(String url) {
        String domain = domain(url);
        return domain != null && (
                domain.equals("t.me")
                        || domain.equals("telegram.me")
                        || domain.equals("telegram.dog")
        );
    }

    private boolean isReferralLike(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("ref=")
                || lower.contains("referral=")
                || lower.contains("invite=")
                || lower.contains("start=")
                || lower.contains("startapp=")
                || lower.contains("affiliate=")
                || lower.contains("utm_");
    }

    private String withSchemeForParsing(String url) {
        if (url.regionMatches(true, 0, "http://", 0, 7)
                || url.regionMatches(true, 0, "https://", 0, 8)) {
            return url;
        }
        return "https://" + url;
    }

    private String trimTrailingUrlPunctuation(String value) {
        String result = value;
        while (!result.isEmpty() && ".,;:!?)]}»”’".indexOf(result.charAt(result.length() - 1)) >= 0) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String safeSubstring(String text, int offset, int length) {
        int start = Math.max(0, Math.min(offset, text.length()));
        int end = Math.max(start, Math.min(offset + length, text.length()));
        return text.substring(start, end);
    }

    private int intValue(JsonNode node, String field, int fallback) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : fallback;
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private record EntityShape(String entityType, String url) {
    }

    private final class MutableLink {
        private final String url;
        private final String normalizedUrl;
        private final String domain;
        private final String anchorText;
        private final String source;
        private final String entityType;
        private final Integer offsetStart;
        private final Integer offsetEnd;
        private final boolean hidden;
        private final boolean visibleUrl;
        private final boolean telegramLink;
        private final boolean referralLike;
        private final ObjectNode rawEntityJson;

        private MutableLink(
                String url,
                String normalizedUrl,
                String domain,
                String anchorText,
                String source,
                String entityType,
                Integer offsetStart,
                Integer offsetEnd,
                boolean hidden,
                boolean visibleUrl,
                boolean telegramLink,
                boolean referralLike,
                ObjectNode rawEntityJson
        ) {
            this.url = url;
            this.normalizedUrl = normalizedUrl;
            this.domain = domain;
            this.anchorText = anchorText;
            this.source = source;
            this.entityType = entityType;
            this.offsetStart = offsetStart;
            this.offsetEnd = offsetEnd;
            this.hidden = hidden;
            this.visibleUrl = visibleUrl;
            this.telegramLink = telegramLink;
            this.referralLike = referralLike;
            this.rawEntityJson = rawEntityJson;
        }

        private void mergeRaw(JsonNode incoming) {
            ArrayNode detectors = rawEntityJson.withArray("detectors");
            JsonNode incomingDetectors = incoming.path("detectors");
            if (incomingDetectors.isArray()) {
                for (JsonNode detector : incomingDetectors) {
                    if (detector.isTextual() && !contains(detectors, detector.asText())) {
                        detectors.add(detector.asText());
                    }
                }
            }

            if (incoming.has("entity")) {
                ArrayNode mergedEntities = rawEntityJson.withArray("mergedEntities");
                mergedEntities.add(incoming.get("entity"));
            }
        }

        private boolean contains(ArrayNode array, String value) {
            for (JsonNode node : array) {
                if (value.equals(node.asText())) {
                    return true;
                }
            }
            return false;
        }

        private ExtractedLink toExtractedLink() {
            return new ExtractedLink(
                    url,
                    normalizedUrl,
                    domain,
                    anchorText,
                    source,
                    entityType,
                    offsetStart,
                    offsetEnd,
                    hidden,
                    visibleUrl,
                    telegramLink,
                    referralLike,
                    rawEntityJson
            );
        }
    }
}
