package com.larbcorp.neuroinfogrinder2.links;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegexFallbackUrlExtractionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LinkExtractor extractor = new LinkExtractor(objectMapper);

    @Test
    void findsPlainUrlWithoutTdlibEntityAndMasksSensitiveLogView() {
        var links = extractor.extract(
                "open https://example.com/callback?access_token=dummy-value&ok=1",
                objectMapper.createArrayNode(),
                "TEXT"
        );

        assertThat(links).hasSize(1);
        assertThat(links.get(0).url()).isEqualTo("https://example.com/callback?access_token=dummy-value&ok=1");
        assertThat(links.get(0).rawEntityJson().path("detectors").get(0).asText()).isEqualTo("REGEX_FALLBACK");
        assertThat(extractor.maskSensitiveUrlForLog(links.get(0).url()))
                .isEqualTo("https://example.com/callback?access_token=***&ok=1");
    }
}
