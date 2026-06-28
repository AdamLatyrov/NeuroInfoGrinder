package com.larbcorp.neuroinfogrinder.domain.findings;

import java.util.Locale;

public enum ContentType {
    GUIDE,
    NEWS,
    USEFUL_INFO,
    FAQ,
    WARNING,
    RISK_INSIGHT,
    PRODUCT_UPDATE,
    REFERENCE,
    DISCUSSION_ONLY,
    DEFERRED;

    public static ContentType from(String value) {
        if (value == null || value.isBlank()) {
            return GUIDE;
        }
        try {
            return ContentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GUIDE;
        }
    }

    public boolean createsMaterial() {
        return this != DISCUSSION_ONLY && this != DEFERRED;
    }

    public boolean generatesFullGuide() {
        return this == GUIDE;
    }
}
