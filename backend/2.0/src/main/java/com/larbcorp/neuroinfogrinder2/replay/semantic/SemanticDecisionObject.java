package com.larbcorp.neuroinfogrinder2.replay.semantic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SemanticDecisionObject(
    String schemaVersion,
    DecisionMode decisionMode,
    Long messageId,
    Long segmentId,
    Long clusterId,
    UnitType unitType,
    String unitScope,
    List<Long> sourceMessageIds,
    String processableScope,
    List<Meaning> meaning,
    ValueLevel valueLevel,
    UsefulnessKind usefulnessKind,
    EvidenceSufficiency evidenceSufficiency,
    AssemblyStrategy assemblyStrategy,
    MaterialRoute materialRoute,
    UiReason uiReason,
    double confidence,
    JsonNode modelVotes,
    JsonNode classicalMlScores,
    JsonNode classicalMlResult,
    JsonNode embeddingScores,
    String embeddingModel,
    Integer embeddingDimension,
    LinkEnrichmentStatus linkEnrichmentStatus,
    JsonNode linkEnrichmentResult,
    boolean requiresLlmJudge,
    String llmProviderRoute,
    String llmModel,
    JsonNode llmJudgeDecision,
    boolean draftAllowed,
    boolean materializationAllowed,
    List<String> riskFlags,
    JsonNode dedupeResult,
    JsonNode capResult,
    JsonNode safetyResult,
    String noGenerationReason,
    List<String> generationBlockedBy,
    Long reviewQueueId,
    boolean signalStored,
    Long candidateId,
    Long materialId,
    String modelVersion,
    String featureVersion,
    String trainingDatasetVersion,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    JsonNode trace
) {
    public static final String SCHEMA_VERSION = "1.0";
    public static final String NO_GENERATION_REASON_TRACE_ONLY = "SEMANTIC_TRACE_ONLY";
    public static final String BLOCKED_BY_SHADOW_MODE = "SHADOW_MODE";
    public static final String DEFAULT_MODEL_VERSION = "semantic-shadow-v1";
    public static final String DEFAULT_FEATURE_VERSION = "semantic-shadow-v1";
    public static final String DEFAULT_EMBEDDING_MODEL = "BAAI/bge-m3";
    public static final int DEFAULT_EMBEDDING_DIMENSION = 1024;

    public static SemanticDecisionObject shadowForMessage(
        long messageId,
        String unitScope,
        String processableScope,
        ObjectMapper mapper
    ) {
        return shadow(
            messageId,
            null,
            null,
            UnitType.MESSAGE,
            unitScope,
            List.of(messageId),
            processableScope,
            mapper
        );
    }

    public static SemanticDecisionObject shadowForDiscussionSegment(
        long segmentId,
        List<Long> sourceMessageIds,
        String unitScope,
        String processableScope,
        ObjectMapper mapper
    ) {
        return shadow(
            null,
            segmentId,
            null,
            UnitType.DISCUSSION_SEGMENT,
            unitScope,
            sourceMessageIds,
            processableScope,
            mapper
        );
    }

    private static SemanticDecisionObject shadow(
        Long messageId,
        Long segmentId,
        Long clusterId,
        UnitType unitType,
        String unitScope,
        List<Long> sourceMessageIds,
        String processableScope,
        ObjectMapper mapper
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        return new SemanticDecisionObject(
            SCHEMA_VERSION,
            DecisionMode.SHADOW,
            messageId,
            segmentId,
            clusterId,
            unitType,
            unitScope,
            List.copyOf(sourceMessageIds),
            processableScope,
            List.of(Meaning.UNKNOWN),
            ValueLevel.NOT_GARBAGE_NO_MATERIAL,
            UsefulnessKind.NONE,
            EvidenceSufficiency.INSUFFICIENT_CONTEXT,
            AssemblyStrategy.REJECT,
            MaterialRoute.NO_MATERIAL,
            UiReason.LOW_CONFIDENCE_REVIEW,
            0.0,
            emptyObject(mapper),
            emptyObject(mapper),
            null,
            emptyObject(mapper),
            DEFAULT_EMBEDDING_MODEL,
            DEFAULT_EMBEDDING_DIMENSION,
            LinkEnrichmentStatus.NOT_REQUESTED,
            null,
            false,
            null,
            null,
            null,
            false,
            false,
            List.of(),
            null,
            null,
            null,
            NO_GENERATION_REASON_TRACE_ONLY,
            List.of(BLOCKED_BY_SHADOW_MODE),
            null,
            false,
            null,
            null,
            DEFAULT_MODEL_VERSION,
            DEFAULT_FEATURE_VERSION,
            null,
            now,
            null,
            defaultTrace(unitType, mapper)
        );
    }

    private static ObjectNode defaultTrace(UnitType unitType, ObjectMapper mapper) {
        ObjectNode trace = emptyObject(mapper);
        trace.put("mode", "trace-only");
        trace.put("origin", "semantic-shadow-bootstrap");
        trace.put("unitType", unitType.name());
        return trace;
    }

    private static ObjectNode emptyObject(ObjectMapper mapper) {
        return mapper == null ? JsonNodeFactory.instance.objectNode() : mapper.createObjectNode();
    }

    public enum DecisionMode {
        SHADOW
    }

    public enum UnitType {
        MESSAGE,
        DISCUSSION_SEGMENT,
        CLUSTER
    }

    public enum Meaning {
        PRACTICAL_INSTRUCTION,
        TROUBLESHOOTING,
        QUESTION,
        ANSWER,
        NEWS_UPDATE,
        MODEL_RELEASE,
        PRODUCT_RELEASE,
        STATUS_OUTAGE,
        TOOL_OR_REPOSITORY,
        RESOURCE_REFERENCE,
        WARNING_RISK,
        SECURITY_PRIVACY_RISK,
        PRODUCT_FEEDBACK,
        LINK_SHARE,
        PROMO_AD,
        ABUSE_FRAUD,
        ACCESS_CIRCUMVENTION,
        CHATTER,
        ENTITY_ONLY,
        MEDIA_ONLY,
        UNKNOWN
    }

    public enum ValueLevel {
        GARBAGE,
        NOT_GARBAGE_NO_MATERIAL,
        AWARENESS_SIGNAL,
        CONTEXT_SIGNAL,
        MATERIAL_CANDIDATE
    }

    public enum UsefulnessKind {
        ACTIONABLE,
        DIAGNOSTIC,
        REFERENCE,
        WARNING,
        AWARENESS,
        CONTEXTUAL,
        ANALYTICAL,
        EDUCATIONAL,
        NONE
    }

    public enum EvidenceSufficiency {
        ENOUGH_SINGLE_MESSAGE,
        NEEDS_DISCUSSION_CONTEXT,
        NEEDS_CLUSTER_CONTEXT,
        NEEDS_LINK_ENRICHMENT,
        NEEDS_EXTERNAL_VERIFICATION,
        INSUFFICIENT_CONTEXT,
        RISK_SENSITIVE_MANUAL_ONLY,
        NOT_MATERIALIZABLE
    }

    public enum AssemblyStrategy {
        SINGLE_MESSAGE,
        DISCUSSION_SEGMENT,
        CLUSTER,
        LINK_ENRICHED_SINGLE,
        MANUAL_REVIEW,
        REJECT
    }

    public enum MaterialRoute {
        GUIDE,
        ANSWER,
        SUMMARY,
        REFERENCE,
        WARNING,
        CHECKLIST,
        COMPARISON,
        DIGEST,
        NO_MATERIAL
    }

    public enum UiReason {
        SIGNAL_ONLY_STATUS,
        NEEDS_LOCAL_CONTEXT,
        NEEDS_CLUSTER_CONTEXT,
        QUESTION_WITHOUT_ANSWER,
        LINK_NEEDS_ENRICHMENT,
        LINK_INSUFFICIENT_CONTEXT,
        REFERENCE_CANDIDATE,
        EXTERNAL_VERIFICATION_REQUIRED,
        RISK_MANUAL_ONLY,
        ACCESS_CIRCUMVENTION_BLOCKED,
        ABUSE_FRAUD_REJECTED,
        PROMO_NO_MATERIAL,
        ENTITY_ONLY_NO_MATERIAL,
        MEDIA_ONLY_NO_MATERIAL,
        ENOUGH_FOR_DRAFT,
        DEDUPED_EXISTING_MATERIAL,
        CAPPED_GENERATION,
        SAFETY_REJECTED,
        MODEL_DISAGREEMENT_REVIEW,
        LOW_CONFIDENCE_REVIEW,
        ML_SHADOW_ONLY,
        LLM_JUDGE_REJECTED
    }

    public enum LinkEnrichmentStatus {
        NOT_REQUESTED,
        PENDING,
        SUCCESS,
        FAILED
    }
}
