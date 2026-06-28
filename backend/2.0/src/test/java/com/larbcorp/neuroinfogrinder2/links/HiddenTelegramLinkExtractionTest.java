package com.larbcorp.neuroinfogrinder2.links;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HiddenTelegramLinkExtractionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LinkExtractor extractor = new LinkExtractor(objectMapper);

    @Test
    void extractsRealUrlAndAnchorFromTextUrlEntity() throws Exception {
        String text = "тыкать тут";
        var entities = objectMapper.readTree("""
                [
                  {
                    "offset": 7,
                    "length": 3,
                    "type": {
                      "@type": "textEntityTypeTextUrl",
                      "url": "https://example.com/page"
                    }
                  }
                ]
                """);

        var links = extractor.extract(text, entities, "TEXT");

        assertThat(links).hasSize(1);
        assertThat(links.get(0).url()).isEqualTo("https://example.com/page");
        assertThat(links.get(0).anchorText()).isEqualTo("тут");
        assertThat(links.get(0).hidden()).isTrue();
        assertThat(links.get(0).entityType()).isEqualTo("TEXT_URL");
    }
}
