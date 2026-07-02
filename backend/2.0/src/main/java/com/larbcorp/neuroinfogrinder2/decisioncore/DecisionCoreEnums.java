package com.larbcorp.neuroinfogrinder2.decisioncore;

public final class DecisionCoreEnums {
    private DecisionCoreEnums() {
    }

    public enum ObjectType {
        MESSAGE,
        DISCUSSION_SEGMENT,
        CLUSTER,
        LINK,
        MATERIAL_CANDIDATE
    }

    public enum Eligibility {
        UNKNOWN,
        ELIGIBLE,
        INELIGIBLE,
        PENDING,
        MANUAL_ONLY
    }

    public enum FinalRoute {
        NO_DECISION,
        HARD_REJECT_ALL,
        REJECT_FOR_MATERIAL_NOW,
        RETAIN_FOR_CONTEXT,
        PERSIST_AS_KNOWLEDGE_SIGNAL,
        ROUTE_TO_LINK_ENRICHMENT,
        ROUTE_TO_MANUAL_REVIEW,
        ROUTE_TO_DISCUSSION_ASSEMBLY,
        ROUTE_TO_CLUSTER_CANDIDATE,
        ROUTE_TO_SINGLE_CANDIDATE,
        ROUTE_TO_LLM_JUDGE,
        MATERIAL_DRAFT_ALLOWED,
        DRAFT_FALLBACK_NEEDS_REVIEW
    }

    public enum CandidateType {
        SINGLE_MESSAGE,
        DISCUSSION_SEGMENT,
        CLUSTER,
        LINK_ENRICHED,
        SIGNAL_ONLY,
        MANUAL_REVIEW,
        MATERIAL_CANDIDATE
    }

    public enum LedgerEventType {
        CANDIDATE_CREATED,
        CANDIDATE_REJECTED,
        CANDIDATE_RETAINED_AS_CONTEXT,
        SIGNAL_SELECTED,
        MATERIAL_ROUTE_SELECTED,
        ARTIFACT_TYPE_SELECTED,
        DUPLICATE_SUPPRESSED,
        CLUSTER_EXCLUDED_MESSAGE,
        CONTEXT_NODE_RETAINED,
        LLM_ELIGIBILITY_DECIDED,
        LLM_SENT,
        LLM_SKIPPED,
        FALLBACK_DRAFT_ALLOWED,
        FALLBACK_DRAFT_FORBIDDEN,
        WINNER_SELECTED,
        LOSER_RECORDED
    }
}
