package com.larbcorp.neuroinfogrinder2.links;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptionLinkExtractionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LinkExtractor extractor = new LinkExtractor(objectMapper);

    @Test
    void marksCaptionLinksWithCaptionSource() throws Exception {
        String caption = "caption тут";
        var entities = objectMapper.readTree("""
                [
                  {
                    "offset": 8,
                    "length": 3,
                    "type": "TEXT_URL",
                    "url": "https://media.example.com"
                  }
                ]
                """);

        var links = extractor.extract(caption, entities, "CAPTION");

        assertThat(links).hasSize(1);
        assertThat(links.get(0).source()).isEqualTo("CAPTION");
        assertThat(links.get(0).hidden()).isTrue();
        assertThat(links.get(0).url()).isEqualTo("https://media.example.com");
    }
}
