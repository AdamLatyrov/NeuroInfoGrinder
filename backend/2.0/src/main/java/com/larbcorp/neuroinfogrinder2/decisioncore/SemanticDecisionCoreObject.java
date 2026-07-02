package com.larbcorp.neuroinfogrinder2.decisioncore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SemanticDecisionCoreObject(
    String traceVersion,
    String decisionVersion,
    DecisionCoreEnums.ObjectType objectType,
    Long objectId,
    List<Long> sourceMessageIds,
    List<String> meaningLabels,
    List<String> contentClassLabels,
    JsonNode topicCandidates,
    JsonNode entityCandidates,
    JsonNode linkEntities,
    BigDecimal valueScore,
    BigDecimal readinessScore,
    BigDecimal contextNeedScore,
    BigDecimal evidenceScore,
    BigDecimal riskScore,
    BigDecimal duplicateScore,
    BigDecimal noveltyScore,
    BigDecimal sourceQualityScore,
    BigDecimal actionabilityScore,
    JsonNode materialRouteCandidates,
    DecisionCoreEnums.FinalRoute finalRoute,
    JsonNode artifactTypeCandidates,
    String requiredArtifactType,
    String dedupeIdentity,
    String clusterIdentity,
    String discussionIdentity,
    String timeWindowId,
    DecisionCoreEnums.Eligibility contextEligibility,
    DecisionCoreEnums.Eligibility materialEligibility,
    DecisionCoreEnums.Eligibility signalEligibility,
    DecisionCoreEnums.Eligibility enrichmentEligibility,
    DecisionCoreEnums.Eligibility llmEligibility,
    DecisionCoreEnums.Eligibility manualReviewEligibility,
    List<String> reasonCodes,
    List<String> hardBlocks,
    List<String> softWarnings,
    JsonNode modelOutputs,
    JsonNode ruleOutputs,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static final String TRACE_VERSION = "decision-core-shadow-v1";
    public static final String DECISION_VERSION = "decision-core-shadow-v1";
}
