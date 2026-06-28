package com.larbcorp.neuroinfogrinder2.ingest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackfillDoesNotDuplicateMessagesTest {
    @Test
    void backfillAndLiveUpdateShareSameIdempotencyKey() {
        DuplicateUpdateDetector detector = new DuplicateUpdateDetector();

        boolean backfill = detector.markIfNew(1, "updateNewMessage", 100, 200);
        boolean live = detector.markIfNew(1, "updateNewMessage", 100, 200);

        assertThat(backfill).isTrue();
        assertThat(live).isFalse();
    }
}
