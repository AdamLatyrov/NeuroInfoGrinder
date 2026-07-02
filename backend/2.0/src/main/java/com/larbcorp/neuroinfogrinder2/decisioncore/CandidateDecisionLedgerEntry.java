package com.larbcorp.neuroinfogrinder2.decisioncore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CandidateDecisionLedgerEntry(
    Long decisionObjectId,
    Long runId,
    String candidateGroupId,
    String candidateId,
    DecisionCoreEnums.CandidateType candidateType,
    DecisionCoreEnums.LedgerEventType eventType,
    String eventStatus,
    boolean winner,
    List<String> competingCandidateIds,
    String duplicateAnchorId,
    Long materialCandidateId,
    Long knowledgeItemId,
    Long providerCallId,
    BigDecimal rankScore,
    DecisionCoreEnums.FinalRoute routeBefore,
    DecisionCoreEnums.FinalRoute routeAfter,
    String artifactTypeBefore,
    String artifactTypeAfter,
    List<String> reasonCodes,
    List<Long> contextNodesRetained,
    List<Long> excludedMessageIds,
    JsonNode details,
    OffsetDateTime createdAt
) {
}
