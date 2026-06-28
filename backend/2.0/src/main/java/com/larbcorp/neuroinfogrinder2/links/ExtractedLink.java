package com.larbcorp.neuroinfogrinder2.links;

import com.fasterxml.jackson.databind.JsonNode;

public record ExtractedLink(
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
        JsonNode rawEntityJson
) {
}
