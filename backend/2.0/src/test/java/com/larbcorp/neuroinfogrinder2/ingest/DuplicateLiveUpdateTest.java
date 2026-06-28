package com.larbcorp.neuroinfogrinder2.ingest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateLiveUpdateTest {
    @Test
    void duplicateLiveUpdateKeepsSingleIngestKey() {
        DuplicateUpdateDetector detector = new DuplicateUpdateDetector();

        assertThat(detector.markIfNew(1, "updateNewMessage", 100, 200)).isTrue();
        assertThat(detector.markIfNew(1, "updateNewMessage", 100, 200)).isFalse();
    }
}
