package com.larbcorp.neuroinfogrinder2.links;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisibleUrlExtractionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LinkExtractor extractor = new LinkExtractor(objectMapper);

    @Test
    void extractsVisibleUrlFromTdlibEntity() throws Exception {
        String text = "go https://example.com/page";
        var entities = objectMapper.readTree("""
                [
                  {
                    "offset": 3,
                    "length": 24,
                    "type": {
                      "@type": "textEntityTypeUrl"
                    }
                  }
                ]
                """);

        var links = extractor.extract(text, entities, "TEXT");

        assertThat(links).hasSize(1);
        assertThat(links.get(0).url()).isEqualTo("https://example.com/page");
        assertThat(links.get(0).anchorText()).isEqualTo("https://example.com/page");
        assertThat(links.get(0).visibleUrl()).isTrue();
    }
}
