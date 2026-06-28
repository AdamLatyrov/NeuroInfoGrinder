package com.larbcorp.neuroinfogrinder2.reset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "neuroinfogrinder.local-reset")
public class LocalDbResetSafety {
    private Set<String> allowedHosts = Set.of("localhost", "127.0.0.1", "host.docker.internal", "postgres");
    private Set<String> allowedProfiles = Set.of("local", "dev", "test");
    private Set<String> allowedDbNameMarkers = Set.of("dev", "local", "test", "neuroinfogrinder2");

    public SafetyDecision evaluate(String jdbcUrl, String activeProfile) {
        String profile = normalize(activeProfile);
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:mem:")) {
            if ("test".equals(profile)) {
                return SafetyDecision.allowed("h2-mem", "test", profile);
            }
            return SafetyDecision.denied("In-memory H2 database is allowed only for test profile", "h2-mem", "test", profile);
        }
        ParsedJdbcUrl parsed = parsePostgresUrl(jdbcUrl);
        if (!allowedProfiles.contains(profile)) {
            return SafetyDecision.denied("Active profile is not local/dev/test", parsed.host(), parsed.database(), profile);
        }
        if (!allowedHosts.contains(normalize(parsed.host()))) {
            return SafetyDecision.denied("DB host is not explicitly local", parsed.host(), parsed.database(), profile);
        }
        String dbName = normalize(parsed.database());
        boolean dbNameAllowed = allowedDbNameMarkers.stream().map(this::normalize).anyMatch(dbName::contains);
        if (!dbNameAllowed) {
            return SafetyDecision.denied("DB name does not contain a local/dev/test marker", parsed.host(), parsed.database(), profile);
        }
        return SafetyDecision.allowed(parsed.host(), parsed.database(), profile);
    }

    public ParsedJdbcUrl parsePostgresUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Only jdbc:postgresql URLs are supported by reset safety checks");
        }
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        return new ParsedJdbcUrl(uri.getHost(), database);
    }

    public void setAllowedHosts(String allowedHosts) {
        this.allowedHosts = csv(allowedHosts);
    }

    public void setAllowedProfiles(String allowedProfiles) {
        this.allowedProfiles = csv(allowedProfiles);
    }

    public void setAllowedDbNameMarkers(String allowedDbNameMarkers) {
        this.allowedDbNameMarkers = csv(allowedDbNameMarkers);
    }

    private Set<String> csv(String value) {
        return Arrays.stream(value.split(","))
                .map(this::normalize)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record ParsedJdbcUrl(String host, String database) {
    }

    public record SafetyDecision(boolean allowed, String reason, String host, String database, String profile) {
        public static SafetyDecision allowed(String host, String database, String profile) {
            return new SafetyDecision(true, "local/dev/test database confirmed", host, database, profile);
        }

        public static SafetyDecision denied(String reason, String host, String database, String profile) {
            return new SafetyDecision(false, reason, host, database, profile);
        }
    }
}
