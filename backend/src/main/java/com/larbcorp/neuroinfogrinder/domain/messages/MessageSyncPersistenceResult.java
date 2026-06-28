package com.larbcorp.neuroinfogrinder.domain.messages;

public record MessageSyncPersistenceResult(
        int created,
        int repaired,
        int skipped,
        int tooOld,
        int sourceCount
) {
    public int changed() {
        return created + repaired;
    }
}
