package com.larbcorp.neuroinfogrinder2.ingest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateUpdateTest {
    @Test
    void detectsSecondIdenticalTdlibMessageUpdateAsDuplicate() {
        DuplicateUpdateDetector detector = new DuplicateUpdateDetector();

        boolean first = detector.markIfNew(1, "updateNewMessage", 100, 200);
        boolean second = detector.markIfNew(1, "updateNewMessage", 100, 200);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }
}
