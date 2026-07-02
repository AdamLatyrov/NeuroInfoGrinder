package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.decisioncore.DecisionCoreEnums;
import com.larbcorp.neuroinfogrinder2.decisioncore.DecisionCoreShadowService;
import com.larbcorp.neuroinfogrinder2.decisioncore.MaterialEligibilityGate;
import com.larbcorp.neuroinfogrinder2.signals.KnowledgeSignalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.larbcorp.neuroinfogrinder2.settings.PipelineSettingsService;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.ClassicalMlStageDatasetExport;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.ClassicalMlStageEvaluationRequest;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.FeatureVectorBuilder;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.RouteIntelligenceDecision;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.RouteIntelligenceEngine;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.ReviewFeedbackService;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.SemanticAggregationEngine;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.StageDecisionEngine;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.StageDecisionSnapshot;
import com.larbcorp.neuroinfogrinder2.replay.semantic.SemanticDecisionObject;

@Service
public class ReplayV2Service {
    private static final Logger log = LoggerFactory.getLogger(ReplayV2Service.class);
    private static final String VERSION = "full-intelligence-replay-v2.0";
    private static final Pattern URL = Pattern.compile("(?i)\\bhttps?://[^\\s<>\"']+|\\bwww\\.[^\\s<>\"']+");
    private static final Pattern DOMAIN = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?([a-z0-9][a-z0-9-]{1,63}(?:\\.[a-z0-9][a-z0-9-]{1,63})+)");
    private static final Pattern CODE = Pattern.compile("(?is)(```|\\b(curl|json|yaml|docker|npm|mvn|python|java|class|function|const|select|insert|update)\\b)");
    private static final Pattern ERROR = Pattern.compile("(?i)(error|exception|traceback|stacktrace|failed|ошиб|исключ|не работает|cannot|timeout)");
    private static final Pattern PRICE = Pattern.compile("(?i)(\\$\\s?\\d+|\\d+\\s?(usd|eur|руб|₽)|price|pricing|тариф|лимит|quota|access|доступ)");
    private static final Pattern TOOL = Pattern.compile("(?i)\\b(gpt|claude|gemini|deepseek|qwen|kimi|bge|bert|openai|api|cursor|docker|postgres|telegram|fastapi|spring|react|vite)\\b");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ModelWorkerClient worker;
    private final ModelhubProviderGateway provider;
    private final Environment environment;
    private final PipelineSettingsService settings;
    private final FeatureVectorBuilder featureVectorBuilder;
    private final StageDecisionEngine stageDecisionEngine;
    private final SemanticAggregationEngine semanticAggregationEngine;
    private final RouteIntelligenceEngine routeIntelligenceEngine;
    private final ReviewFeedbackService reviewFeedbackService;
    private final KnowledgeSignalService knowledgeSignalService;
    private final DecisionCoreShadowService decisionCore;
    private final MessageUsefulnessClassifier usefulnessClassifier = new MessageUsefulnessClassifier();

    @Autowired
    public ReplayV2Service(JdbcTemplate jdbc, ObjectMapper json, ModelWorkerClient worker, ModelhubProviderGateway provider, Environment environment, PipelineSettingsService settings, KnowledgeSignalService knowledgeSignalService, DecisionCoreShadowService decisionCore) {
        this(
            jdbc,
            json,
            worker,
            provider,
            environment,
            settings,
            new com.larbcorp.neuroinfogrinder2.replay.classicalml.DefaultFeatureVectorBuilder(json),
            new com.larbcorp.neuroinfogrinder2.replay.classicalml.ShadowStageDecisionEngine(
                new com.larbcorp.neuroinfogrinder2.replay.classicalml.WorkerBackedClassicalMlInferenceClient(worker, json),
                json
            ),
            new com.larbcorp.neuroinfogrinder2.replay.classicalml.DefaultSemanticAggregationEngine(json),
            new com.larbcorp.neuroinfogrinder2.replay.classicalml.DefaultRouteIntelligenceEngine(json),
            new com.larbcorp.neuroinfogrinder2.replay.classicalml.JdbcReviewFeedbackService(json),
            knowledgeSignalService,
            decisionCore
        );
    }

    public ReplayV2Service(JdbcTemplate jdbc, ObjectMapper json, ModelWorkerClient worker, ModelhubProviderGateway provider, Environment environment, PipelineSettingsService settings, KnowledgeSignalService knowledgeSignalService) {
        this(jdbc, json, worker, provider, environment, settings, knowledgeSignalService, null);
    }

    public ReplayV2Service(JdbcTemplate jdbc, ObjectMapper json, ModelWorkerClient worker, ModelhubProviderGateway provider, Environment environment, PipelineSettingsService settings) {
        this(jdbc, json, worker, provider, environment, settings, null);
    }

    public ReplayV2Service(JdbcTemplate jdbc, ObjectMapper json, ModelWorkerClient worker, ModelhubProviderGateway provider, Environment environment, PipelineSettingsService settings,
                           FeatureVectorBuilder featureVectorBuilder, StageDecisionEngine stageDecisionEngine, SemanticAggregationEngine semanticAggregationEngine) {
        this(jdbc, json, worker, provider, environment, settings, featureVectorBuilder, stageDecisionEngine, semanticAggregationEngine,
            new com.larbcorp.neuroinfogrinder2.replay.classicalml.DefaultRouteIntelligenceEngine(json),
            new com.larbcorp.neuroinfogrinder2.replay.classicalml.JdbcReviewFeedbackService(json),
            null);
    }

    public ReplayV2Service(JdbcTemplate jdbc, ObjectMapper json, ModelWorkerClient worker, ModelhubProviderGateway provider, Environment environment, PipelineSettingsService settings,
                            FeatureVectorBuilder featureVectorBuilder, StageDecisionEngine stageDecisionEngine, SemanticAggregationEngine semanticAggregationEngine,
                            RouteIntelligenceEngine routeIntelligenceEngine, ReviewFeedbackService reviewFeedbackService) {
        this(jdbc, json, worker, provider, environment, settings, featureVectorBuilder, stageDecisionEngine, semanticAggregationEngine, routeIntelligenceEngine, reviewFeedbackService, null);
    }

    public ReplayV2Service(JdbcTemplate jdbc, ObjectMapper json, ModelWorkerClient worker, ModelhubProviderGateway provider, Environment environment, PipelineSettingsService settings,
                            FeatureVectorBuilder featureVectorBuilder, StageDecisionEngine stageDecisionEngine, SemanticAggregationEngine semanticAggregationEngine,
                            RouteIntelligenceEngine routeIntelligenceEngine, ReviewFeedbackService reviewFeedbackService, KnowledgeSignalService knowledgeSignalService) {
        this(jdbc, json, worker, provider, environment, settings, featureVectorBuilder, stageDecisionEngine, semanticAggregationEngine, routeIntelligenceEngine, reviewFeedbackService, knowledgeSignalService, null);
    }

    public ReplayV2Service(JdbcTemplate jdbc, ObjectMapper json, ModelWorkerClient worker, ModelhubProviderGateway provider, Environment environment, PipelineSettingsService settings,
                            FeatureVectorBuilder featureVectorBuilder, StageDecisionEngine stageDecisionEngine, SemanticAggregationEngine semanticAggregationEngine,
                            RouteIntelligenceEngine routeIntelligenceEngine, ReviewFeedbackService reviewFeedbackService, KnowledgeSignalService knowledgeSignalService,
                            DecisionCoreShadowService decisionCore) {
        this.jdbc = jdbc;
        this.json = json;
        this.worker = worker;
        this.provider = provider;
        this.environment = environment;
        this.settings = settings;
        this.featureVectorBuilder = featureVectorBuilder;
        this.stageDecisionEngine = stageDecisionEngine;
        this.semanticAggregationEngine = semanticAggregationEngine;
        this.routeIntelligenceEngine = routeIntelligenceEngine;
        this.reviewFeedbackService = reviewFeedbackService;
        this.knowledgeSignalService = knowledgeSignalService;
        this.decisionCore = decisionCore;
    }

    public ReplayPlan plan(ReplayRequest request) {
        ReplayRequest effective = resolveEffectiveRequest(request, null);
        ModelWorkerClient.WorkerHealth health = worker.health();
        updateWorker(health);
        long total = count("SELECT count(*) FROM dataset_messages WHERE dataset_id = ?", effective.datasetId());
        int max = effective.maxMessagesOrDefault();
        ObjectNode warnings = json.createObjectNode();
        warnings.put("modelWorker", health.status());
        warnings.put("classifier", health.classifierStatus());
        warnings.put("embeddings", health.embeddingStatus());
        warnings.put("provider", providerConfigured() ? "OK" : "PROVIDER_NOT_CONFIGURED");
        long estimatedMessages = Math.min(total, max);
        return new ReplayPlan(effective.datasetId(), total, health.status(), health.classifierReady(), health.embeddingsReady(), providerConfigured(),
                Math.round(estimatedMessages * 0.55), Math.max(1, Math.round(estimatedMessages * 0.12)), providerConfigured() ? effective.maxProviderCallsOrDefault() : 0,
                effective.maxEstimatedCostUsdOrDefault(), write(warnings));
    }

    @Transactional
    public ReplayRun run(ReplayRequest request) {
        ReplayRequest effective = resolveEffectiveRequest(request, null);
        List<Message> messages = loadMessages(effective.datasetId(), effective.maxMessagesOrDefault());
        ObjectNode config = json.createObjectNode();
        config.put("maxMessages", effective.maxMessagesOrDefault());
        config.put("maxProviderCalls", effective.maxProviderCallsOrDefault());
        config.put("maxEstimatedCostUsd", effective.maxEstimatedCostUsdOrDefault());
        config.put("semanticSimilarityThreshold", effective.semanticSimilarityThresholdOrDefault());
        config.put("minClusterScoreForJudge", effective.minClusterScoreForJudgeOrDefault());
        config.put("minJudgeConfidenceForGeneration", effective.minJudgeConfidenceForGenerationOrDefault());
        long runId = JdbcIds.insertReturningId(jdbc, """
                INSERT INTO replay_runs (dataset_id, run_name, mode, pipeline_version, config_snapshot_json,
                                         model_config_snapshot_json, provider_config_snapshot_json, status,
                                         total_messages, processed_messages)
                VALUES (?, ?, 'FULL_INTELLIGENCE_REPLAY', ?, ?::jsonb, ?::jsonb, ?::jsonb, 'RUNNING', ?, 0)
                """, effective.datasetId(), blank(effective.runName()) ? "Full replay " + Instant.now() : effective.runName(), VERSION,
                write(config), modelConfig(), providerConfig(), messages.size());
        log.info("[REPLAY_RUN_STARTED] runId={} datasetId={} messages={}", runId, effective.datasetId(), messages.size());
        return processExistingRun(runId, effective, messages);
    }

    @Transactional
    public ReplayRun runExistingLiveAutoRun(long runId) {
        ReplayRun existing = getRun(runId);
        if (!"LIVE_AUTO_RAW_MESSAGES".equals(existing.pipelineVersion())) {
            throw new IllegalArgumentException("Run is not LIVE_AUTO_RAW_MESSAGES: " + runId);
        }
        JsonNode configSnapshot = oneOrNull("SELECT config_snapshot_json::text FROM replay_runs WHERE id = ?", runId);
        ReplayRequest request = resolveEffectiveRequest(
            new ReplayRequest(existing.datasetId(), existing.runName(), null, null, null, null, null, null),
            configSnapshot
        );
        List<Message> messages = loadMessages(request.datasetId(), request.maxMessagesOrDefault());
        jdbc.update("UPDATE replay_runs SET status = 'RUNNING', started_at = COALESCE(started_at, now()), total_messages = ?, processed_messages = 0, error = NULL WHERE id = ?", messages.size(), runId);
        log.info("[LIVE_AUTO_REPLAY_STARTED] runId={} datasetId={} messages={}", runId, request.datasetId(), messages.size());
        return processExistingRun(runId, request, messages);
    }

    ReplayRequest resolveEffectiveRequest(ReplayRequest request, JsonNode snapshot) {
        JsonNode safeSnapshot = snapshot == null || !snapshot.isObject() ? json.createObjectNode() : snapshot;
        Integer maxMessages = request.maxMessages() != null
            ? request.maxMessages()
            : integerValue(safeSnapshot, "maxMessages", 500);
        Integer maxProviderCalls = request.maxProviderCalls() != null
            ? request.maxProviderCalls()
            : integerValue(safeSnapshot, "maxProviderCalls", settings.getInt("maxProviderCallsPerRun", 50));
        Double maxEstimatedCostUsd = request.maxEstimatedCostUsd() != null
            ? request.maxEstimatedCostUsd()
            : doubleValue(safeSnapshot, List.of("maxEstimatedCostUsd", "maxCostUsd"), settings.getDouble("maxCostUsdPerRun", 2.0));
        Double semanticSimilarityThreshold = request.semanticSimilarityThreshold() != null
            ? request.semanticSimilarityThreshold()
            : doubleValue(safeSnapshot, List.of("semanticSimilarityThreshold"), settings.getDouble("semanticSimilarityThreshold", 0.66));
        Double minClusterScoreForJudge = request.minClusterScoreForJudge() != null
            ? request.minClusterScoreForJudge()
            : doubleValue(safeSnapshot, List.of("minClusterScoreForJudge"), settings.getDouble("minClusterScoreForJudge", 0.65));
        Double minJudgeConfidenceForGeneration = request.minJudgeConfidenceForGeneration() != null
            ? request.minJudgeConfidenceForGeneration()
            : doubleValue(safeSnapshot, List.of("minJudgeConfidenceForGeneration"), settings.getDouble("minJudgeConfidenceForGeneration", 0.72));
        return new ReplayRequest(
            request.datasetId(), request.runName(), maxMessages, maxProviderCalls, maxEstimatedCostUsd,
            semanticSimilarityThreshold, minClusterScoreForJudge, minJudgeConfidenceForGeneration
        );
    }

    private int integerValue(JsonNode snapshot, String field, int fallback) {
        JsonNode value = snapshot.path(field);
        return value.isNumber() ? value.asInt() : fallback;
    }

    private double doubleValue(JsonNode snapshot, List<String> fields, double fallback) {
        for (String field : fields) {
            JsonNode value = snapshot.path(field);
            if (value.isNumber()) return value.asDouble();
        }
        return fallback;
    }

    private ReplayRun processExistingRun(long runId, ReplayRequest request, List<Message> messages) {
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metric(metrics, "messages_total", messages.size());
        stage(runId, "DATASET_IMPORT", "COMPLETED", messages.size(), messages.size(), 0, 0, 0, object("datasetId", request.datasetId()), null, 0);

        List<Intel> intel = normalizeFeaturesRules(runId, messages, metrics);
        classicalMlMessageShadow(runId, intel, metrics);
        ModelWorkerClient.WorkerHealth health = worker.health();
        updateWorker(health);
        log.info("[MODEL_WORKER_HEALTH] status={} classifier={} bge={}", health.status(), health.classifierStatus(), health.embeddingStatus());
        if (!health.reachable()) {
            stage(runId, "BERT_CLASSIFICATION", "MODEL_WORKER_DOWN", candidates(intel), 0, candidates(intel), 0, 0, health.raw(), "MODEL_WORKER_DOWN", 0);
            stage(runId, "BGE_EMBEDDINGS", "MODEL_WORKER_DOWN", candidates(intel), 0, candidates(intel), 0, 0, health.raw(), "MODEL_WORKER_DOWN", 0);
            finish(runId, "MODEL_WORKER_DOWN", metrics, "MODEL_WORKER_DOWN");
            return getRun(runId);
        }
        if (!health.classifierReady()) {
            stage(runId, "BERT_CLASSIFICATION", "MODEL_NOT_CONFIGURED", candidates(intel), 0, candidates(intel), 0, 0, health.raw(), "MODEL_NOT_CONFIGURED", 0);
            finish(runId, "MODEL_NOT_CONFIGURED", metrics, "BERT classifier is not configured");
            return getRun(runId);
        }
        classify(runId, intel, metrics);
        List<Embedding> embeddings = embed(runId, intel, metrics);
        dedupe(runId, intel, metrics);
        List<Neighbor> neighbors = semantic(runId, embeddings, metrics, request.semanticSimilarityThresholdOrDefault());
        List<Cluster> micro = microclusters(runId, embeddings, neighbors, intel, metrics, request.semanticSimilarityThresholdOrDefault());
        List<Cluster> macro = macroclusters(runId, micro, metrics);
        Map<String, JsonNode> topicAnalytics = topics(runId, intel, micro, metrics);
        Map<Long, Double> finalScores = score(runId, macro, metrics);
        classicalMlClusterShadow(runId, macro, finalScores, topicAnalytics, metrics);
        List<SingleMessageCandidate> singleCandidates = singleMessageDetection(runId, intel, embeddings, metrics);
        List<DiscussionSegmentCandidate> discussionCandidates = discussionSegmentDetection(runId, intel, metrics);
        classicalMlDiscussionShadow(runId, discussionCandidates, metrics);
        llm(runId, macro, finalScores, singleCandidates, discussionCandidates, intel, request, metrics);
        labeling(runId, macro, metrics);
        trainingAccumulation(runId, metrics);
        aggregate(runId, metrics);
        exportJson(runId, true, false, true);
        finish(runId, "COMPLETED", metrics, terminalReason(embeddings, macro, singleCandidates, runId));
        syncRawBackedPipelineTrace(runId);
        log.info("[REPLAY_RUN_COMPLETED] status=COMPLETED runId={}", runId);
        return getRun(runId);
    }

    private List<Intel> normalizeFeaturesRules(long runId, List<Message> messages, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        List<Intel> result = new ArrayList<>();
        for (Message message : messages) {
            String raw = coalesce(message.text(), message.caption(), "");
            String normalized = raw.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
            ObjectNode f = features(message, normalized);
            boolean hard = f.path("linkCount").asInt() > 0 || f.path("hiddenLinkCount").asInt() > 0 || f.path("hasCode").asBoolean()
                    || f.path("hasError").asBoolean() || f.path("hasPriceOrAccess").asBoolean() || f.path("hasTool").asBoolean()
                    || f.path("isQuestion").asBoolean() || f.path("isAnswerLike").asBoolean();
            String decision = (!hard && (normalized.length() < 18 || f.path("isNoiseLike").asBoolean())) ? "SUPPRESS" : (hard && normalized.length() >= 30 ? "CANDIDATE" : "ACCUMULATE");
            ArrayNode labels = labels(f, decision);
            ArrayNode reasons = json.createArrayNode();
            reasons.add("decision=" + decision);
            if (hard) reasons.add("hard signal present");
            long id = JdbcIds.insertReturningId(jdbc, """
                    INSERT INTO message_intelligence (run_id, dataset_message_id, raw_text, normalized_text, language, text_len, token_estimate,
                        structural_features_json, links_json, entities_json, code_json, errors_json, prices_json, question_answer_json,
                        weak_labels_json, rule_scores_json, hard_signal, rule_decision, decision_reasons_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb)
                    ON CONFLICT (run_id, dataset_message_id) DO UPDATE SET normalized_text = EXCLUDED.normalized_text, rule_decision = EXCLUDED.rule_decision
                    RETURNING id
                    """, runId, message.id(), raw, normalized, language(normalized), normalized.length(), Math.max(1, normalized.length() / 4),
                    write(f), write(f.path("links")), message.entitiesJson(), write(f.path("code")), write(f.path("errors")), write(f.path("prices")),
                    write(f.path("questionAnswer")), write(labels), write(object("hardSignal", hard)), hard, decision, write(reasons));
            upsertMessage(runId, message.id(), decision.equals("SUPPRESS") ? "SUPPRESSED" : "CANDIDATE", decision, labels, null, null, null, null, null, false, decision.equals("SUPPRESS") ? "RULE_SUPPRESS" : null, null);
            persistShadowSemanticDecisionForMessage(runId, message.id(), message);
            Long decisionObjectId = decisionCoreMessage(runId, message.id(), f, normalized, decision, hard);
            decisionCoreObserve(decisionObjectId, runId, "NORMALIZATION", "ReplayV2Service", "MESSAGE_NORMALIZED", object("textLength", normalized.length(), "ruleDecision", decision), List.of("NORMALIZED"), null);
            decisionCoreObserve(decisionObjectId, runId, "FEATURE_EXTRACTION", "ReplayV2Service", "STRUCTURAL_FEATURES", f, List.of(hard ? "HARD_SIGNAL" : "NO_HARD_SIGNAL"), null);
            if (decision.equals("ACCUMULATE") || f.path("linkCount").asInt() > 0 || f.path("hiddenLinkCount").asInt() > 0) {
                decisionCoreRetain(runId, message.id(), decisionObjectId, f.path("linkCount").asInt() > 0 ? "link_only" : "context", decision.equals("ACCUMULATE") ? "ACCUMULATE_RETAINED_FOR_CONTEXT" : "LINK_RETAINED_FOR_ENRICHMENT");
            }
            if (f.path("linkCount").asInt() > 0 || f.path("hiddenLinkCount").asInt() > 0) {
                for (JsonNode link : f.path("links")) decisionCoreEnqueueLink(decisionObjectId, message.id(), link.asText());
            }
            result.add(new Intel(id, message, normalized, f, hard, decision));
        }
        metric(metrics, "messages_suppressed", result.stream().filter(i -> i.ruleDecision().equals("SUPPRESS")).count());
        metric(metrics, "messages_accumulated", result.stream().filter(i -> i.ruleDecision().equals("ACCUMULATE")).count());
        metric(metrics, "messages_candidates", result.stream().filter(i -> i.ruleDecision().equals("CANDIDATE")).count());
        metric(metrics, "rule_candidates", metrics.get("messages_candidates"));
        stage(runId, "NORMALIZATION", "COMPLETED", messages.size(), messages.size(), 0, 0, 0, object("normalized", messages.size()), null, elapsed(started));
        stage(runId, "FEATURE_EXTRACTION", "COMPLETED", messages.size(), messages.size(), 0, 0, 0, object("hardSignals", result.stream().filter(Intel::hardSignal).count()), null, elapsed(started));
        stage(runId, "RULE_CLASSIFICATION", "COMPLETED", messages.size(), candidates(result), suppressed(result), 0, 0, object("candidates", candidates(result)), null, elapsed(started));
        log.info("[STAGE_COMPLETED] stage=RULE_CLASSIFICATION candidates={}", candidates(result));
        return result;
    }

    private void classify(long runId, List<Intel> intel, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        List<Intel> noText = intel.stream().filter(i -> blank(i.normalizedText())).toList();
        Map<String, List<Intel>> byNormalizedHash = new LinkedHashMap<>();
        for (Intel item : intel) {
            if (blank(item.normalizedText())) continue;
            byNormalizedHash.computeIfAbsent(Hashing.sha256(item.normalizedText().toLowerCase(Locale.ROOT)), ignored -> new ArrayList<>()).add(item);
        }
        List<Intel> input = byNormalizedHash.values().stream().map(group -> group.get(0)).toList();
        List<ModelWorkerClient.WorkerItem> items = input.stream().map(i -> new ModelWorkerClient.WorkerItem(String.valueOf(i.message().id()), i.normalizedText(), i.features())).toList();
        JsonNode response = items.isEmpty() ? object("status", "SUCCESS") : worker.classify(items);
        recordModelCall(runId, "BERT_CLASSIFICATION", "CLASSIFY", input.size(), response.path("results").size(), "SUCCESS", elapsed(started), null);
        long modelId = classifierModelId();
        long classifierRunId = JdbcIds.insertReturningId(jdbc, """
                INSERT INTO classifier_runs (run_id, model_id, status, started_at, finished_at, input_count, output_count, latency_ms, metrics_json)
                VALUES (?, ?, 'COMPLETED', now(), now(), ?, ?, ?, ?::jsonb)
                """, runId, modelId, input.size(), response.path("results").size(), elapsed(started), write(object("model", response.path("model").asText("BOOTSTRAP_BERT_CLASSIFIER"))));
        Map<Long, Intel> byId = new HashMap<>();
        intel.forEach(i -> byId.put(i.message().id(), i));
        Map<Long, JsonNode> resultsByMessage = new HashMap<>();
        response.path("results").forEach(item -> resultsByMessage.put(Long.parseLong(item.path("id").asText()), item));
        int noise = 0;
        int disagreement = 0;
        int inherited = 0;
        int skippedNoText = 0;
        int skippedError = 0;
        for (Intel item : noText) {
            insertSkippedClassification(runId, item.message().id(), modelId, "SKIPPED_NO_TEXT", "NO_TEXT", object("reason", "empty text and caption"));
            recordStageSkip(runId, item.message().id(), "BERT_CLASSIFICATION", "NO_TEXT", object("textLength", 0));
            skippedNoText++;
        }
        for (List<Intel> group : byNormalizedHash.values()) {
            Intel canonical = group.get(0);
            JsonNode result = resultsByMessage.get(canonical.message().id());
            if (result == null || result.isMissingNode() || result.isNull()) {
                for (Intel member : group) {
                    insertSkippedClassification(runId, member.message().id(), modelId, "SKIPPED_ERROR", "MODEL_RESULT_MISSING", object("canonicalDatasetMessageId", canonical.message().id()));
                    recordStageSkip(runId, member.message().id(), "BERT_CLASSIFICATION", "MODEL_RESULT_MISSING", object("canonicalDatasetMessageId", canonical.message().id()));
                    skippedError++;
                }
                continue;
            }
            noise += upsertModelClassification(runId, canonical, modelId, result, "MODEL", null, null);
            for (int index = 1; index < group.size(); index++) {
                Intel duplicate = group.get(index);
                ObjectNode inheritedRaw = result.deepCopy();
                inheritedRaw.put("classificationSource", "INHERITED_FROM_DUPLICATE");
                inheritedRaw.put("canonicalDatasetMessageId", canonical.message().id());
                noise += upsertClassificationRow(runId, duplicate, modelId, inheritedRaw, "INHERITED_FROM_DUPLICATE", canonical.message().id(), null);
                inherited++;
            }
        }
        long classificationRows = count("SELECT count(*) FROM message_classifications WHERE run_id = ?", runId);
        long modelRows = count("SELECT count(*) FROM message_classifications WHERE run_id = ? AND classification_source = 'MODEL'", runId);
        for (JsonNode item : response.path("results")) {
            long messageId = Long.parseLong(item.path("id").asText());
            String top = item.path("topLabel").asText("NOISE_OR_CHAT");
            double confidence = item.path("confidence").asDouble(0);
            Intel current = byId.get(messageId);
            boolean disagrees = current != null && (
                    (current.ruleDecision().equals("SUPPRESS") && !top.equals("NOISE_OR_CHAT"))
                            || (!current.ruleDecision().equals("SUPPRESS") && top.equals("NOISE_OR_CHAT"))
                            || (current.hardSignal() && top.equals("NOISE_OR_CHAT"))
            );
            if (disagrees || confidence < 0.60) {
                ObjectNode context = item.deepCopy();
                context.put("activeLearningReason", disagrees ? "RULE_BERT_DISAGREEMENT" : "BERT_LOW_CONFIDENCE");
                createLabeling(runId, messageId, null, null, null, "MESSAGE", current == null ? "" : current.normalizedText(), context, top, null, disagrees ? "DISAGREEMENT" : "PENDING", confidence, disagrees ? 90 : 70);
            }
        }
        metric(metrics, "bert_classified_count", classificationRows);
        metric(metrics, "bert_model_classified_count", modelRows);
        metric(metrics, "bert_inherited_duplicate_count", inherited);
        metric(metrics, "bert_skipped_no_text_count", skippedNoText);
        metric(metrics, "bert_skipped_error_count", skippedError);
        metric(metrics, "bert_candidate_count", classificationRows - noise - skippedNoText - skippedError);
        metric(metrics, "bert_noise_count", noise);
        metric(metrics, "bert_disagreement_count", disagreement);
        stage(runId, "BERT_CLASSIFICATION", "COMPLETED", intel.size(), classificationRows, skippedNoText + skippedError, skippedError, items.isEmpty() ? 0 : 1,
                object("classifierRunId", classifierRunId, "modelInputs", input.size(), "inherited", inherited, "skippedNoText", skippedNoText, "skippedError", skippedError), null, elapsed(started));
        log.info("[BERT_CLASSIFICATION_COMPLETED] rows={} modelInputs={} inherited={} skippedNoText={}", classificationRows, input.size(), inherited, skippedNoText);
    }

    private int upsertModelClassification(long runId, Intel message, long modelId, JsonNode item, String source, Long canonicalMessageId, String skipReason) {
        return upsertClassificationRow(runId, message, modelId, item, source, canonicalMessageId, skipReason);
    }

    private int upsertClassificationRow(long runId, Intel message, long modelId, JsonNode item, String source, Long canonicalMessageId, String skipReason) {
        String top = item.path("topLabel").asText("NOISE_OR_CHAT");
        double confidence = item.path("confidence").asDouble(0);
        JsonNode labels = item.path("labels").isMissingNode() ? json.createArrayNode() : item.path("labels");
        jdbc.update("""
                INSERT INTO message_classifications (
                    run_id, dataset_message_id, model_id, model_name, top_label, confidence, labels_json, raw_output_json,
                    classification_source, canonical_dataset_message_id, skip_reason, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, now())
                ON CONFLICT (run_id, dataset_message_id) DO UPDATE SET
                    model_id = EXCLUDED.model_id,
                    model_name = EXCLUDED.model_name,
                    top_label = EXCLUDED.top_label,
                    confidence = EXCLUDED.confidence,
                    labels_json = EXCLUDED.labels_json,
                    raw_output_json = EXCLUDED.raw_output_json,
                    classification_source = EXCLUDED.classification_source,
                    canonical_dataset_message_id = EXCLUDED.canonical_dataset_message_id,
                    skip_reason = EXCLUDED.skip_reason,
                    updated_at = now()
                """, runId, message.message().id(), modelId, item.path("model").asText("BOOTSTRAP_BERT_CLASSIFIER"), top, confidence,
                write(labels), write(item), source, canonicalMessageId, skipReason);
        String finalDecision = top.equals("NOISE_OR_CHAT") && !message.hardSignal() ? "SUPPRESS" : "CANDIDATE";
        jdbc.update("""
                UPDATE replay_run_messages
                SET bert_labels_json = ?::jsonb,
                    final_decision = ?,
                    status = CASE WHEN ? = 'CANDIDATE' THEN 'CANDIDATE' ELSE status END,
                    bert_skip_reason = ?,
                    updated_at = now()
                WHERE run_id = ? AND dataset_message_id = ?
                """, write(labels), finalDecision, finalDecision, skipReason, runId, message.message().id());
        Long decisionObjectId = decisionCoreMessageId(runId, message.message().id());
        decisionCoreObserve(decisionObjectId, runId, "BOOTSTRAP_CLASSIFICATION", "ModelWorkerClient", "CLASSIFICATION_RESULT", item, List.of(top), confidence);
        if (top.equals("NOISE_OR_CHAT") && message.hardSignal()) {
            decisionCoreRetain(runId, message.message().id(), decisionObjectId, "context", "NOISE_WITH_HARD_SIGNAL_RETAINED");
        }
        return top.equals("NOISE_OR_CHAT") ? 1 : 0;
    }

    private void insertSkippedClassification(long runId, long messageId, long modelId, String source, String skipReason, JsonNode details) {
        ObjectNode raw = object();
        raw.put("status", source);
        raw.put("skipReason", skipReason);
        raw.set("details", details == null ? object() : details);
        jdbc.update("""
                INSERT INTO message_classifications (
                    run_id, dataset_message_id, model_id, model_name, top_label, confidence, labels_json, raw_output_json,
                    classification_source, skip_reason, updated_at
                )
                VALUES (?, ?, ?, 'BOOTSTRAP_BERT_CLASSIFIER', 'NOISE_OR_CHAT', 0, '[]'::jsonb, ?::jsonb, ?, ?, now())
                ON CONFLICT (run_id, dataset_message_id) DO UPDATE SET
                    top_label = EXCLUDED.top_label,
                    confidence = EXCLUDED.confidence,
                    labels_json = EXCLUDED.labels_json,
                    raw_output_json = EXCLUDED.raw_output_json,
                    classification_source = EXCLUDED.classification_source,
                    skip_reason = EXCLUDED.skip_reason,
                    updated_at = now()
                """, runId, messageId, modelId, write(raw), source, skipReason);
        jdbc.update("""
                UPDATE replay_run_messages
                SET bert_labels_json = '[]'::jsonb,
                    final_decision = 'SUPPRESS',
                    bert_skip_reason = ?,
                    updated_at = now()
                WHERE run_id = ? AND dataset_message_id = ?
                """, skipReason, runId, messageId);
        Long decisionObjectId = decisionCoreMessageId(runId, messageId);
        decisionCoreObserve(decisionObjectId, runId, "BOOTSTRAP_CLASSIFICATION", "ReplayV2Service", "CLASSIFICATION_SKIPPED", details, List.of(skipReason), 0.0);
    }

    private void recordStageSkip(long runId, long messageId, String stage, String reason, JsonNode details) {
        jdbc.update("""
                INSERT INTO message_stage_skips (run_id, dataset_message_id, stage, skip_reason, details_json)
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (run_id, dataset_message_id, stage) DO UPDATE SET
                    skip_reason = EXCLUDED.skip_reason,
                    details_json = EXCLUDED.details_json
                """, runId, messageId, stage, reason, write(details == null ? object() : details));
    }

    private List<Embedding> embed(long runId, List<Intel> intel, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        Set<Long> bertNoise = new HashSet<>();
        Set<Long> bertSkipped = new HashSet<>();
        Map<Long, String> labels = new HashMap<>();
        Map<Long, Double> confidences = new HashMap<>();
        jdbc.query("SELECT dataset_message_id FROM message_classifications WHERE run_id = ? AND top_label = 'NOISE_OR_CHAT'", (RowCallbackHandler) rs -> bertNoise.add(rs.getLong(1)), runId);
        jdbc.query("SELECT dataset_message_id FROM message_classifications WHERE run_id = ? AND classification_source LIKE 'SKIPPED_%'", (RowCallbackHandler) rs -> bertSkipped.add(rs.getLong(1)), runId);
        jdbc.query("SELECT dataset_message_id, top_label, confidence FROM message_classifications WHERE run_id = ?", (RowCallbackHandler) rs -> { labels.put(rs.getLong("dataset_message_id"), rs.getString("top_label")); confidences.put(rs.getLong("dataset_message_id"), rs.getDouble("confidence")); }, runId);
        List<Intel> input = intel.stream().filter(i -> !bertSkipped.contains(i.message().id()) && (i.hardSignal() || !bertNoise.contains(i.message().id())) && i.normalizedText().length() >= 24 && !nonMaterialSignalCandidate(i, labels, confidences)).toList();
        if (input.isEmpty()) {
            metric(metrics, "embedding_count", 0);
            metric(metrics, "embedding_skipped_count", intel.size());
            stage(runId, "BGE_EMBEDDINGS", "SKIPPED", 0, 0, intel.size(), 0, 0, object("embedding_count", 0, "reason", "NO_ELIGIBLE_EMBEDDING_INPUT"), "NO_ELIGIBLE_EMBEDDING_INPUT", elapsed(started));
            return List.of();
        }
        List<ModelWorkerClient.WorkerItem> items = input.stream().map(i -> new ModelWorkerClient.WorkerItem(String.valueOf(i.message().id()), i.normalizedText(), i.features())).toList();
        JsonNode response = worker.embedBatch(items);
        String responseStatus = response.path("status").asText();
        String embeddingKind = response.path("embeddingKind").asText("BAAI/bge-m3");
        boolean degraded = "DEGRADED_HASH_VECTOR".equals(embeddingKind);
        if (!"SUCCESS".equals(responseStatus) && !degraded) {
            recordModelCall(runId, "BGE_EMBEDDINGS", "EMBED_BATCH", input.size(), 0, "MODEL_NOT_CONFIGURED", elapsed(started), responseStatus);
            throw new IllegalStateException("BGE-M3 model is not configured");
        }
        long modelId = embeddingModelId();
        Map<Long, Intel> byId = new HashMap<>();
        input.forEach(i -> byId.put(i.message().id(), i));
        List<Embedding> result = new ArrayList<>();
        for (JsonNode item : response.path("results")) {
            long messageId = Long.parseLong(item.path("id").asText());
            Intel source = byId.get(messageId);
            long embeddingId = JdbcIds.insertReturningId(jdbc, """
                    INSERT INTO message_embeddings (run_id, dataset_message_id, model_id, embedding_kind, embedding_vector, embedding_text, embedding_hash)
                    VALUES (?, ?, ?, 'MESSAGE_TEXT', ?::jsonb, ?, ?)
                    ON CONFLICT (run_id, dataset_message_id, embedding_kind) DO UPDATE SET embedding_vector = EXCLUDED.embedding_vector, embedding_text = EXCLUDED.embedding_text, embedding_hash = EXCLUDED.embedding_hash
                    RETURNING id
                    """, runId, messageId, modelId, write(item.path("embedding")), source.normalizedText(), Hashing.sha256(source.normalizedText()));
            jdbc.update("UPDATE replay_run_messages SET embedding_id = ?, updated_at = now() WHERE run_id = ? AND dataset_message_id = ?", embeddingId, runId, messageId);
            Long decisionObjectId = decisionCoreMessageId(runId, messageId);
            decisionCoreObserve(decisionObjectId, runId, "EMBEDDINGS", "ModelWorkerClient", "MESSAGE_EMBEDDED", object("embeddingId", embeddingId, "embeddingKind", embeddingKind), List.of("EMBEDDED_FOR_SIMILARITY"), null);
            result.add(new Embedding(embeddingId, messageId, vector(item.path("embedding")), embeddingKind));
        }
        recordModelCall(runId, "BGE_EMBEDDINGS", "EMBED_BATCH", input.size(), result.size(), degraded ? "PROCESSED_DEGRADED" : "SUCCESS", elapsed(started), degraded ? "DEGRADED_HASH_VECTOR" : null);
        metric(metrics, "embedding_count", result.size());
        metric(metrics, "embedding_skipped_count", intel.size() - result.size());
        stage(runId, "BGE_EMBEDDINGS", degraded ? "PROCESSED_DEGRADED" : "COMPLETED", input.size(), result.size(), intel.size() - result.size(), 0, 1, object("embedding_count", result.size(), "embeddingKind", embeddingKind), degraded ? "DEGRADED_HASH_VECTOR" : null, elapsed(started));
        log.info("[BGE_EMBEDDINGS_COMPLETED] embeddings={} kind={}", result.size(), embeddingKind);
        return result;
    }

    private boolean nonMaterialSignalCandidate(Intel intel, Map<Long, String> labels, Map<Long, Double> confidences) {
        String text = textWithFeatureLinks(intel.normalizedText(), intel.features());
        if (text == null || text.isBlank()) return true;
        MessageUsefulnessResult usefulness = usefulnessClassifier.classify(
                text,
                labels.getOrDefault(intel.message().id(), "NOISE_OR_CHAT"),
                confidences.getOrDefault(intel.message().id(), 0.0),
                intel.hardSignal()
        );
        String reason = usefulness.rejectReason() == null ? "" : usefulness.rejectReason();
        String contentClass = usefulness.contentClass() == null ? "" : usefulness.contentClass();
        return Set.of("LINK_ONLY", "PROMO_ALONE", "ABUSE_OR_FRAUD", "ACCESS_CIRCUMVENTION", "LOW_VALUE").contains(contentClass)
                || Set.of("NEEDS_LINK_ENRICHMENT", "PROMO_ALONE", "ABUSE_OR_FRAUD", "RISK_SENSITIVE_MANUAL_ONLY", "RISK_SENSITIVE_MANUAL_REVIEW", "LOW_VALUE").contains(reason);
    }

    private void dedupe(long runId, List<Intel> intel, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        Map<String, List<Intel>> groups = new LinkedHashMap<>();
        for (Intel i : intel) groups.computeIfAbsent(Hashing.sha256(canonicalSourceKey(i.normalizedText())), ignored -> new ArrayList<>()).add(i);
        int duplicates = 0;
        for (Map.Entry<String, List<Intel>> entry : groups.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            List<Intel> group = entry.getValue();
            duplicates += group.size() - 1;
            long groupId = JdbcIds.insertReturningId(jdbc, "INSERT INTO dedupe_groups (run_id, dedupe_type, canonical_dataset_message_id, message_count, metadata_json) VALUES (?, 'NORMALIZED_HASH', ?, ?, ?::jsonb)", runId, group.get(0).message().id(), group.size(), write(object("hash", entry.getKey())));
            for (Intel i : group) {
                jdbc.update("INSERT INTO dedupe_group_members (group_id, dataset_message_id, similarity, reason_json) VALUES (?, ?, 1, ?::jsonb) ON CONFLICT DO NOTHING", groupId, i.message().id(), write(object("reason", "same normalized hash")));
                jdbc.update("UPDATE replay_run_messages SET dedupe_group_id = ?, llm_skip_reason = CASE WHEN dataset_message_id <> ? THEN 'DUPLICATE' ELSE llm_skip_reason END WHERE run_id = ? AND dataset_message_id = ?", groupId, group.get(0).message().id(), runId, i.message().id());
                Long decisionObjectId = decisionCoreMessageId(runId, i.message().id());
                decisionCoreDedupeIdentity("exact_text", entry.getKey(), decisionObjectId, object("dedupeGroupId", groupId, "canonicalDatasetMessageId", group.get(0).message().id()));
                if (i.message().id() != group.get(0).message().id()) {
                    decisionCoreLedger(decisionObjectId, runId, "dedupe:" + entry.getKey(), "message:" + i.message().id(), DecisionCoreEnums.CandidateType.SINGLE_MESSAGE, DecisionCoreEnums.LedgerEventType.DUPLICATE_SUPPRESSED, false, List.of("message:" + group.get(0).message().id()), "message:" + group.get(0).message().id(), null, null, DecisionCoreEnums.FinalRoute.NO_DECISION, DecisionCoreEnums.FinalRoute.RETAIN_FOR_CONTEXT, null, null, List.of("EXACT_NORMALIZED_DUPLICATE"), List.of(i.message().id()), List.of(), object("dedupeGroupId", groupId));
                }
            }
        }
        metric(metrics, "duplicate_count", duplicates);
        metric(metrics, "llm_avoided_by_duplicate", duplicates);
        stage(runId, "DEDUPLICATION", "COMPLETED", intel.size(), groups.size(), duplicates, 0, 0, object("duplicate_count", duplicates), null, elapsed(started));
        log.info("[DEDUPLICATION_COMPLETED] duplicates={}", duplicates);
    }

    private List<Neighbor> semantic(long runId, List<Embedding> embeddings, Map<String, BigDecimal> metrics, double threshold) {
        long started = System.nanoTime();
        List<Neighbor> result = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            for (int j = i + 1; j < embeddings.size(); j++) {
                double sim = cosine(embeddings.get(i).vector(), embeddings.get(j).vector());
                if (sim >= threshold) {
                    result.add(new Neighbor(embeddings.get(i).messageId(), embeddings.get(j).messageId(), sim));
                    jdbc.update("INSERT INTO semantic_neighbors (run_id, source_dataset_message_id, target_dataset_message_id, similarity, reason_json) VALUES (?, ?, ?, ?, ?::jsonb)", runId, embeddings.get(i).messageId(), embeddings.get(j).messageId(), sim, write(object("method", embeddings.get(i).kind() + "_COSINE", "threshold", threshold)));
                    decisionCoreRelation(runId, embeddings.get(i).messageId(), embeddings.get(j).messageId(), "embedding_similarity", BigDecimal.valueOf(sim), false, object("method", embeddings.get(i).kind() + "_COSINE", "threshold", threshold));
                }
            }
        }
        metric(metrics, "semantic_neighbor_count", result.size());
        stage(runId, "SEMANTIC_SEARCH", "COMPLETED", embeddings.size(), result.size(), 0, 0, 0, object("neighbors", result.size(), "threshold", threshold), null, elapsed(started));
        return result;
    }

    private List<Cluster> microclusters(long runId, List<Embedding> embeddings, List<Neighbor> neighbors, List<Intel> intel, Map<String, BigDecimal> metrics, double semanticThreshold) {
        long started = System.nanoTime();
        Map<Long, Set<Long>> graph = new LinkedHashMap<>();
        embeddings.forEach(e -> graph.putIfAbsent(e.messageId(), new HashSet<>()));
        Map<Long, Intel> byId = new HashMap<>();
        intel.forEach(i -> byId.put(i.message().id(), i));
        neighbors.forEach(n -> {
            String sourceBucket = materialBucketForIntel(byId.get(n.sourceId()));
            String targetBucket = materialBucketForIntel(byId.get(n.targetId()));
            if (!sourceBucket.equals(targetBucket)) return;
            graph.computeIfAbsent(n.sourceId(), k -> new HashSet<>()).add(n.targetId());
            graph.computeIfAbsent(n.targetId(), k -> new HashSet<>()).add(n.sourceId());
        });
        Set<Long> seen = new HashSet<>();
        List<Cluster> clusters = new ArrayList<>();
        int seq = 1;
        for (Long id : graph.keySet()) {
            if (!seen.add(id)) continue;
            ArrayDeque<Long> q = new ArrayDeque<>();
            q.add(id);
            List<Long> members = new ArrayList<>();
            while (!q.isEmpty()) {
                Long cur = q.removeFirst();
                members.add(cur);
                for (Long next : graph.getOrDefault(cur, Set.of())) if (seen.add(next)) q.add(next);
            }
            if (members.size() < 2) continue;
            String title = title(members, byId);
            double score = Math.min(1.0, 0.45 + members.size() * 0.07);
            long clusterId = JdbcIds.insertReturningId(jdbc, "INSERT INTO microclusters (run_id, cluster_key, title, cluster_type, score, message_count, metadata_json) VALUES (?, ?, ?, 'SEMANTIC_CONNECTED_COMPONENT', ?, ?, ?::jsonb)", runId, "micro-" + seq++, title, score, members.size(), write(object("method", "SEMANTIC_CONNECTED_COMPONENT")));
            for (Long member : members) {
                jdbc.update("INSERT INTO microcluster_members (microcluster_id, dataset_message_id, role, similarity, edge_weight, reason_json) VALUES (?, ?, 'MEMBER', ?, 0.80, ?::jsonb) ON CONFLICT DO NOTHING", clusterId, member, semanticThreshold, write(object("reason", "semantic similarity plus extracted feature overlap")));
                jdbc.update("UPDATE replay_run_messages SET microcluster_id = ?, updated_at = now() WHERE run_id = ? AND dataset_message_id = ?", clusterId, runId, member);
            }
            String artifactType = materialBucketForMembers(members, byId);
            Long decisionObjectId = decisionCoreCluster(runId, "MICRO", clusterId, members, artifactType, score, object("method", "SEMANTIC_CONNECTED_COMPONENT", "title", title));
            decisionCoreLedger(decisionObjectId, runId, "cluster:micro:" + clusterId, "cluster:micro:" + clusterId, DecisionCoreEnums.CandidateType.CLUSTER, DecisionCoreEnums.LedgerEventType.CANDIDATE_CREATED, false, List.of(), null, null, BigDecimal.valueOf(score), null, DecisionCoreEnums.FinalRoute.ROUTE_TO_CLUSTER_CANDIDATE, null, artifactType, List.of("MICROCLUSTER_CANDIDATE"), List.of(), List.of(), object("memberCount", members.size()));
            clusters.add(new Cluster(clusterId, title, score, artifactType, members));
        }
        metric(metrics, "microcluster_count", clusters.size());
        stage(runId, "MICROCLUSTERING", "COMPLETED", embeddings.size(), clusters.size(), 0, 0, 0, object("microcluster_count", clusters.size()), null, elapsed(started));
        log.info("[MICROCLUSTERS_CREATED] count={}", clusters.size());
        return clusters;
    }

    private List<Cluster> macroclusters(long runId, List<Cluster> micro, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        Map<String, List<Cluster>> grouped = new LinkedHashMap<>();
        for (Cluster c : micro) grouped.computeIfAbsent(c.artifactType() + ":" + firstWord(c.title()), k -> new ArrayList<>()).add(c);
        List<Cluster> result = new ArrayList<>();
        for (Map.Entry<String, List<Cluster>> entry : grouped.entrySet()) {
            int count = entry.getValue().stream().mapToInt(c -> c.members().size()).sum();
            double score = entry.getValue().stream().mapToDouble(Cluster::score).average().orElse(0);
            long id = JdbcIds.insertReturningId(jdbc, "INSERT INTO macroclusters (run_id, title, macrocluster_type, score, message_count, microcluster_count, metadata_json) VALUES (?, ?, 'TOPIC_OR_TOOL_GROUP', ?, ?, ?, ?::jsonb)", runId, "Macro: " + entry.getKey(), score, count, entry.getValue().size(), write(object("reason", "shared topic/entity key")));
            List<Long> members = new ArrayList<>();
            for (Cluster c : entry.getValue()) {
                members.addAll(c.members());
                jdbc.update("INSERT INTO macrocluster_members (macrocluster_id, microcluster_id, similarity, reason_json) VALUES (?, ?, 0.75, ?::jsonb) ON CONFLICT DO NOTHING", id, c.id(), write(object("reason", "shared topic/entity key")));
                jdbc.update("UPDATE replay_run_messages SET macrocluster_id = ?, updated_at = now() WHERE run_id = ? AND microcluster_id = ?", id, runId, c.id());
            }
            String artifactType = entry.getValue().stream().map(Cluster::artifactType).filter(v -> !blank(v)).findFirst().orElse("SUMMARY");
            Long decisionObjectId = decisionCoreCluster(runId, "MACRO", id, members, artifactType, score, object("reason", "shared topic/entity key", "legacyMacroKey", entry.getKey()));
            decisionCoreLedger(decisionObjectId, runId, "cluster:macro:" + id, "cluster:macro:" + id, DecisionCoreEnums.CandidateType.CLUSTER, DecisionCoreEnums.LedgerEventType.CANDIDATE_CREATED, false, List.of(), null, null, BigDecimal.valueOf(score), null, DecisionCoreEnums.FinalRoute.ROUTE_TO_CLUSTER_CANDIDATE, null, artifactType, List.of("MACROCLUSTER_CANDIDATE", "COHERENCE_REPORT_REQUIRED"), List.of(), List.of(), object("memberCount", members.size(), "microclusterCount", entry.getValue().size()));
            result.add(new Cluster(id, "Macro: " + entry.getKey(), score, artifactType, members));
        }
        metric(metrics, "macrocluster_count", result.size());
        stage(runId, "MACROCLUSTERING", "COMPLETED", micro.size(), result.size(), 0, 0, 0, object("macrocluster_count", result.size()), null, elapsed(started));
        log.info("[MACROCLUSTERS_CREATED] count={}", result.size());
        return result;
    }

    private Map<String, JsonNode> topics(long runId, List<Intel> intel, List<Cluster> micro, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        Map<String, Integer> terms = new LinkedHashMap<>();
        Map<String, JsonNode> analytics = new LinkedHashMap<>();
        for (Intel i : intel) for (String t : i.normalizedText().toLowerCase(Locale.ROOT).split("[^a-zа-я0-9_.-]+")) if (t.length() > 3) terms.merge(t, 1, Integer::sum);
        int count = 0;
        for (Map.Entry<String, Integer> e : terms.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(12).toList()) {
            ObjectNode topicNode = object("topicKey", e.getKey(), "messageCount", e.getValue(), "clusterCount", micro.size(), "frequencyScore", Math.min(1.0, e.getValue() / 10.0));
            topicNode.put("noveltyScore", Math.max(0.05, 1.0 / Math.max(1, e.getValue())));
            topicNode.put("importanceScore", Math.min(1.0, 0.25 + (e.getValue() * 0.08)));
            topicNode.put("trendScore", Math.min(1.0, 0.20 + (micro.size() * 0.05)));
            analytics.put(e.getKey(), topicNode);
            jdbc.update("INSERT INTO discovered_topics (run_id, topic_key, title, description, message_count, cluster_count, top_entities_json, top_domains_json, top_terms_json, method, score, analytics_json) VALUES (?, ?, ?, 'Topic discovered by entity frequency', ?, ?, '[]'::jsonb, '[]'::jsonb, ?::jsonb, 'CLUSTER_ENTITY_FREQUENCY', ?, ?::jsonb)", runId, e.getKey(), e.getKey(), e.getValue(), micro.size(), write(array(e.getKey())), Math.min(1.0, e.getValue() / 10.0), write(topicNode));
            count++;
        }
        metric(metrics, "topic_count", count);
        stage(runId, "TOPIC_DISCOVERY", "COMPLETED", intel.size(), count, 0, 0, 0, object("method", "CLUSTER_ENTITY_FREQUENCY"), null, elapsed(started));
        log.info("[TOPICS_DISCOVERED] count={}", count);
        return analytics;
    }

    private Map<Long, Double> score(long runId, List<Cluster> clusters, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        Map<Long, Double> finalScores = new LinkedHashMap<>();
        for (Cluster c : clusters) {
            double finalScore = Math.min(1.0, c.score() + c.members().size() * 0.03);
            finalScores.put(c.id(), finalScore);
            jdbc.update("""
                    INSERT INTO cluster_scores (run_id, cluster_type, cluster_id, usefulness_score, pain_score, wtp_score, publishability_score, novelty_score, trend_score, duplicate_penalty, spam_penalty, unsafe_penalty, final_score, score_reasons_json)
                    VALUES (?, 'MACRO', ?, ?, ?, ?, ?, 0.55, ?, 0, 0, 0, ?, ?::jsonb)
                    """, runId, c.id(), finalScore, Math.min(1, 0.35 + c.members().size() * 0.04), Math.min(1, 0.25 + c.members().size() * 0.03), Math.min(1, 0.40 + c.members().size() * 0.03), Math.min(1, 0.30 + c.members().size() * 0.02), finalScore, write(array("semantic cohesion", "message count", "hard signal density")));
            log.info("[CLUSTER_SCORE] clusterId={} finalScore={}", c.id(), finalScore);
        }
        metric(metrics, "clusters_scored", clusters.size());
        stage(runId, "CLUSTER_SCORING", "COMPLETED", clusters.size(), clusters.size(), 0, 0, 0, object("clusters_scored", clusters.size()), null, elapsed(started));
        return finalScores;
    }

    private void llm(long runId, List<Cluster> clusters, Map<Long, Double> finalScores, List<SingleMessageCandidate> singleCandidates, List<DiscussionSegmentCandidate> discussionCandidates, List<Intel> intel, ReplayRequest request, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        int totalInput = clusters.size() + singleCandidates.size() + discussionCandidates.size();
        if (!providerConfigured()) {
            stage(runId, "LLM_CLUSTER_JUDGE_AND_ROUTING", "PROVIDER_NOT_CONFIGURED", totalInput, 0, totalInput, 0, 0, object("apiKeyRef", "MODELHUB_API_KEY"), "MODELHUB_API_KEY is not configured", elapsed(started));
            stage(runId, "KNOWLEDGE_GENERATION", "PROVIDER_NOT_CONFIGURED", 0, 0, 0, 0, 0, object("apiKeyRef", "MODELHUB_API_KEY"), "MODELHUB_API_KEY is not configured", 0);
            metric(metrics, "llm_real_calls_total", 0);
            metric(metrics, "llm_calls_skipped", totalInput);
            return;
        }
        ModelhubProviderGateway.ReplayBudget budget = new ModelhubProviderGateway.ReplayBudget(request.maxProviderCallsOrDefault(), BigDecimal.valueOf(request.maxEstimatedCostUsdOrDefault()));
        int clusterSent = 0; int clusterLow = 0; int clusterRejected = 0; int clusterLowConf = 0; int clusterBudget = 0;
        int singleSent = 0; int singleRejected = 0; int singleLowConf = 0; int singleBudget = 0;
        int discussionSent = 0; int discussionRejected = 0; int discussionLowConf = 0; int discussionBudget = 0;
        int generated = 0;
        Set<Long> discussionCoveredMessages = settings.getInt("preferDiscussionOverSingleMessage", 1) == 1
            ? discussionCandidates.stream().flatMap(candidate -> candidate.messages().stream()).map(i -> i.message().id()).collect(Collectors.toSet())
            : Set.of();
        for (Cluster c : clusters) {
            double gateScore = finalScores.getOrDefault(c.id(), c.score());
            if (gateScore < request.minClusterScoreForJudgeOrDefault()) { clusterLow++; markClusterLlmSkip(runId, c, "LOW_CLUSTER_SCORE"); continue; }
            RouteIntelligenceDecision routeDecision = loadClusterRouteDecision(c.id());
            if (shouldApplyClassicalRouteBlock(routeDecision)) {
                clusterRejected++;
                markClusterLlmSkip(runId, c, firstPolicyReason(routeDecision, "ROUTE_POLICY_BLOCKED"));
                continue;
            }
            clusterSent++;
            decisionCoreLedger(decisionCoreClusterId(runId, "MACRO", c.id()), runId, "cluster:macro:" + c.id(), "cluster:macro:" + c.id(), DecisionCoreEnums.CandidateType.CLUSTER, DecisionCoreEnums.LedgerEventType.LLM_SENT, false, List.of(), null, null, BigDecimal.valueOf(gateScore), DecisionCoreEnums.FinalRoute.ROUTE_TO_CLUSTER_CANDIDATE, DecisionCoreEnums.FinalRoute.ROUTE_TO_LLM_JUDGE, c.artifactType(), c.artifactType(), List.of("LLM_JUDGE_SENT_BY_LEGACY_CLUSTER_PATH"), List.of(), List.of(), object("gateScore", gateScore));
            var judge = provider.callJson(runId, "LLM_CLUSTER_JUDGE_AND_ROUTING", prompt(c, intel, "judge", routeDecision), budget, true);
            if (!judge.success()) { if ("BUDGET_BLOCKED".equals(judge.status())) { clusterBudget = 1 + Math.max(0, clusters.size() - clusterSent); break; } clusterRejected++; continue; }
            boolean lightweightOverride = !approvedJudgeDecision(judge.responseJson()) && shouldAllowLightweightMaterialAfterJudgeReject(judge.responseJson(), routeDecision, gateScore, c.members().size());
            if (!approvedJudgeDecision(judge.responseJson()) && !lightweightOverride) { clusterRejected++; continue; }
            double judgeConfidence = lightweightOverride ? Math.max(gateScore, request.minJudgeConfidenceForGenerationOrDefault()) : judge.responseJson().path("confidence").asDouble();
            if (judgeConfidence < request.minJudgeConfidenceForGenerationOrDefault()) { clusterLowConf++; continue; }
            var generation = provider.callJson(runId, "KNOWLEDGE_GENERATION", prompt(c, intel, "generation", routeDecision), budget, true);
            if (generation.success() && generationUsable(generation.responseJson())) { long id = knowledge(runId, c, generation.responseJson(), judgeConfidence); generated++; }
            else if (lightweightOverride) { long id = knowledge(runId, c, lightweightMaterial(c, intel, judge.responseJson()), judgeConfidence); generated++; }
            else if ("BUDGET_BLOCKED".equals(generation.status())) { clusterBudget = Math.max(0, clusters.size() - clusterSent); break; }
        }
        for (SingleMessageCandidate sc : singleCandidates) {
            if (discussionCoveredMessages.contains(sc.messageId())) {
                singleRejected++;
                decisionCoreLedger(decisionCoreMessageId(runId, sc.messageId()), runId, "message:" + sc.messageId(), "single:" + sc.messageId(), DecisionCoreEnums.CandidateType.SINGLE_MESSAGE, DecisionCoreEnums.LedgerEventType.LOSER_RECORDED, false, List.of("discussion:covered"), null, null, BigDecimal.valueOf(sc.score()), DecisionCoreEnums.FinalRoute.ROUTE_TO_SINGLE_CANDIDATE, DecisionCoreEnums.FinalRoute.RETAIN_FOR_CONTEXT, sc.requiredArtifactType(), null, List.of("DISCUSSION_CANDIDATE_PREFERRED_OVER_SINGLE_MESSAGE"), List.of(sc.messageId()), List.of(), object("preference", "preferDiscussionOverSingleMessage"));
                continue;
            }
            singleSent++;
            Intel match = intel.stream().filter(i -> i.message().id() == sc.messageId()).findFirst().orElse(null);
            if (match == null) { singleRejected++; continue; }
            String singleBlockReason = singleMessageMaterialBlockReason(sc, match);
            if (singleBlockReason != null) {
                singleRejected++;
                rejectSingleMessage(runId, sc.messageId(), sc.score(), singleBlockReason, sc.signals(), sc.usefulness());
                continue;
            }
            RouteIntelligenceDecision routeDecision = loadMessageRouteDecision(runId, sc.messageId());
            if (shouldApplyClassicalRouteBlock(routeDecision)) {
                singleRejected++;
                String reason = firstPolicyReason(routeDecision, "ROUTE_POLICY_BLOCKED");
                persistRejectedSignal(runId, match, textWithFeatureLinks(match.normalizedText(), match.features()), reason, null);
                rejectSingleMessage(runId, sc.messageId(), sc.score(), reason, sc.signals(), routeDecision.reasonBundle());
                continue;
            }
            var judge = provider.callJson(runId, "LLM_CLUSTER_JUDGE_AND_ROUTING", promptSingle(sc, match, intel, "judge", routeDecision), budget, true);
            decisionCoreLedger(decisionCoreMessageId(runId, sc.messageId()), runId, "message:" + sc.messageId(), "single:" + sc.messageId(), DecisionCoreEnums.CandidateType.SINGLE_MESSAGE, judge.success() ? DecisionCoreEnums.LedgerEventType.LLM_SENT : DecisionCoreEnums.LedgerEventType.LLM_SKIPPED, false, List.of(), null, judge.providerCallId(), BigDecimal.valueOf(sc.score()), DecisionCoreEnums.FinalRoute.ROUTE_TO_SINGLE_CANDIDATE, judge.success() ? DecisionCoreEnums.FinalRoute.ROUTE_TO_LLM_JUDGE : DecisionCoreEnums.FinalRoute.REJECT_FOR_MATERIAL_NOW, sc.requiredArtifactType(), sc.requiredArtifactType(), List.of(judge.status()), List.of(), List.of(), judge.responseJson());
            if (!judge.success()) { singleRejected++; continue; }
            boolean lightweightOverride = !approvedJudgeDecision(judge.responseJson()) && shouldAllowLightweightMaterialAfterJudgeReject(judge.responseJson(), routeDecision, sc.score(), 1);
            if (!approvedJudgeDecision(judge.responseJson()) && !lightweightOverride) { singleRejected++; continue; }
            double judgeConfidence = lightweightOverride ? Math.max(sc.score(), request.minJudgeConfidenceForGenerationOrDefault()) : judge.responseJson().path("confidence").asDouble();
            if (judgeConfidence < request.minJudgeConfidenceForGenerationOrDefault()) { singleLowConf++; continue; }
            var generation = provider.callJson(runId, "KNOWLEDGE_GENERATION", promptSingle(sc, match, intel, "generation", routeDecision), budget, true);
            if (generation.success() && generationUsable(generation.responseJson())) {
                long id = knowledgeSingle(runId, sc, match, generation.responseJson(), judgeConfidence);
                decisionCoreLedger(decisionCoreMessageId(runId, sc.messageId()), runId, "message:" + sc.messageId(), "single:" + sc.messageId(), DecisionCoreEnums.CandidateType.SINGLE_MESSAGE, DecisionCoreEnums.LedgerEventType.WINNER_SELECTED, true, List.of(), null, generation.providerCallId(), BigDecimal.valueOf(sc.score()), DecisionCoreEnums.FinalRoute.ROUTE_TO_LLM_JUDGE, DecisionCoreEnums.FinalRoute.MATERIAL_DRAFT_ALLOWED, sc.requiredArtifactType(), sc.requiredArtifactType(), List.of("DRAFT_CREATED_BY_LEGACY_SINGLE_PATH"), List.of(), List.of(), object("knowledgeItemId", id));
                generated++;
            } else if (lightweightOverride || approvedJudgeDecision(judge.responseJson())) {
                long id = knowledgeSingle(runId, sc, match, lightweightMaterial(sc, match, judge.responseJson()), judgeConfidence);
                decisionCoreLedger(decisionCoreMessageId(runId, sc.messageId()), runId, "message:" + sc.messageId(), "single:" + sc.messageId(), DecisionCoreEnums.CandidateType.SINGLE_MESSAGE, DecisionCoreEnums.LedgerEventType.FALLBACK_DRAFT_ALLOWED, true, List.of(), null, null, BigDecimal.valueOf(sc.score()), DecisionCoreEnums.FinalRoute.ROUTE_TO_LLM_JUDGE, DecisionCoreEnums.FinalRoute.DRAFT_FALLBACK_NEEDS_REVIEW, sc.requiredArtifactType(), sc.requiredArtifactType(), List.of("LEGACY_LIGHTWEIGHT_FALLBACK_CREATED", "REVIEW_REQUIRED_IN_DECISION_CORE"), List.of(), List.of(), object("knowledgeItemId", id));
                generated++;
            }
        }
        if (settings.getInt("discussionSegmentGenerationEnabled", 0) != 1) {
            for (DiscussionSegmentCandidate dc : discussionCandidates) markDiscussionSkipped(runId, dc, "DRY_RUN_GENERATION_DISABLED");
            metric(metrics, "discussion_dry_run_generation_disabled", discussionCandidates.size());
            discussionCandidates = List.of();
        }
        for (DiscussionSegmentCandidate dc : discussionCandidates) {
            if (!discussionEligibleForControlledGeneration(dc, runId)) {
                discussionRejected++;
                continue;
            }
            RouteIntelligenceDecision routeDecision = loadDiscussionRouteDecision(dc.segmentId());
            if (shouldApplyClassicalRouteBlock(routeDecision)) {
                discussionRejected++;
                markDiscussionSkipped(runId, dc, firstPolicyReason(routeDecision, "ROUTE_POLICY_BLOCKED"));
                continue;
            }
            discussionSent++;
            var judge = provider.callJson(runId, "DISCUSSION_SEGMENT_JUDGE", promptDiscussion(dc, "judge", null, routeDecision), budget, true);
            decisionCoreLedger(dc.segmentId() == null ? null : decisionCoreDiscussionId(runId, dc.segmentId()), runId, "discussion:" + dc.segmentId(), "discussion:" + dc.segmentId(), DecisionCoreEnums.CandidateType.DISCUSSION_SEGMENT, judge.success() ? DecisionCoreEnums.LedgerEventType.LLM_SENT : DecisionCoreEnums.LedgerEventType.LLM_SKIPPED, false, List.of(), null, judge.providerCallId(), BigDecimal.valueOf(dc.score()), DecisionCoreEnums.FinalRoute.ROUTE_TO_DISCUSSION_ASSEMBLY, judge.success() ? DecisionCoreEnums.FinalRoute.ROUTE_TO_LLM_JUDGE : DecisionCoreEnums.FinalRoute.REJECT_FOR_MATERIAL_NOW, dc.proposedMaterialType(), dc.proposedMaterialType(), List.of(judge.status()), List.of(), List.of(), judge.responseJson());
            markDiscussionStage(runId, dc, "DISCUSSION_SEGMENT_LLM_JUDGE", judge.status(), judge.responseJson(), judge.providerCallId(), null);
            if (!judge.success()) { if ("BUDGET_BLOCKED".equals(judge.status())) { discussionBudget = 1 + Math.max(0, discussionCandidates.size() - discussionSent); break; } discussionRejected++; markDiscussionSkipped(runId, dc, "GENERATION_PROVIDER_ERROR"); if (settings.getInt("discussionSegmentStopOnProviderError", 1) == 1) break; continue; }
            if (!approvedJudgeDecision(judge.responseJson())) { discussionRejected++; markDiscussionSkipped(runId, dc, "LLM_REJECTED"); continue; }
            if (judge.responseJson().path("confidence").asDouble() < request.minJudgeConfidenceForGenerationOrDefault()) { discussionLowConf++; markDiscussionSkipped(runId, dc, "LOW_CONFIDENCE"); continue; }
            markDiscussionStage(runId, dc, "DISCUSSION_SEGMENT_DEDUPE", "PASSED", object("duplicate", false), null, null);
            var generation = provider.callJson(runId, "KNOWLEDGE_GENERATION", promptDiscussion(dc, "generation", judge.responseJson(), routeDecision), budget, true);
            if (generation.success() && generationUsable(generation.responseJson())) {
                long id = knowledgeDiscussion(runId, dc, generation.responseJson(), judge.responseJson().path("confidence").asDouble());
                decisionCoreLedger(decisionCoreDiscussionId(runId, dc.segmentId()), runId, "discussion:" + dc.segmentId(), "discussion:" + dc.segmentId(), DecisionCoreEnums.CandidateType.DISCUSSION_SEGMENT, DecisionCoreEnums.LedgerEventType.WINNER_SELECTED, true, List.of(), null, generation.providerCallId(), BigDecimal.valueOf(dc.score()), DecisionCoreEnums.FinalRoute.ROUTE_TO_LLM_JUDGE, DecisionCoreEnums.FinalRoute.MATERIAL_DRAFT_ALLOWED, dc.proposedMaterialType(), dc.proposedMaterialType(), List.of("DISCUSSION_DRAFT_CREATED_BY_LEGACY_PATH"), List.of(), List.of(), object("knowledgeItemId", id));
                markDiscussionStage(runId, dc, "MATERIAL_CREATED", "COMPLETED", object("knowledgeItemId", id), generation.providerCallId(), null);
                generated++;
            } else {
                markDiscussionSkipped(runId, dc, "GENERATION_PROVIDER_ERROR");
                if (settings.getInt("discussionSegmentStopOnGenerationError", 1) == 1) break;
            }
        }
        int sent = clusterSent + singleSent + discussionSent;
        metric(metrics, "clusters_sent_to_llm", clusterSent);
        metric(metrics, "clusters_skipped_before_llm", clusterLow);
        metric(metrics, "clusters_rejected_by_judge", clusterRejected);
        metric(metrics, "clusters_skipped_low_judge_confidence", clusterLowConf);
        metric(metrics, "clusters_skipped_budget_blocked", clusterBudget);
        metric(metrics, "single_sent_to_llm", singleSent);
        metric(metrics, "single_rejected_by_judge", singleRejected);
        metric(metrics, "single_skipped_low_judge_confidence", singleLowConf);
        metric(metrics, "single_skipped_budget_blocked", singleBudget);
        metric(metrics, "discussion_sent_to_llm", discussionSent);
        metric(metrics, "discussion_rejected_by_judge", discussionRejected);
        metric(metrics, "discussion_skipped_low_judge_confidence", discussionLowConf);
        metric(metrics, "discussion_skipped_budget_blocked", discussionBudget);
        metric(metrics, "llm_real_calls_total", count("SELECT count(*) FROM provider_calls WHERE run_id = ? AND status NOT IN ('CACHE_HIT','BUDGET_BLOCKED','PROVIDER_NOT_CONFIGURED')", runId));
        metric(metrics, "provider_calls_total", count("SELECT count(*) FROM provider_calls WHERE run_id = ?", runId));
        metric(metrics, "knowledge_items_total", generated);
        ObjectNode llmMetrics = object("clusterSent", clusterSent, "singleSent", singleSent, "discussionSent", discussionSent, "rejected", clusterRejected + singleRejected + discussionRejected, "lowConf", clusterLowConf + singleLowConf + discussionLowConf);
        llmMetrics.put("budgetBlocked", clusterBudget + singleBudget + discussionBudget);
        stage(runId, "LLM_CLUSTER_JUDGE_AND_ROUTING", "COMPLETED", totalInput, sent, Math.max(0, totalInput - sent), 0, sent, llmMetrics, null, elapsed(started));
        stage(runId, "KNOWLEDGE_GENERATION", "COMPLETED", sent, generated, Math.max(0, sent - generated), 0, generated, object("generated", generated), null, elapsed(started));
    }

    private void labeling(long runId, List<Cluster> macro, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        int before = (int) count("SELECT count(*) FROM labeling_items WHERE run_id = ?", runId);
        for (Cluster c : macro) if (c.score() >= 0.75) createLabeling(runId, null, null, c.id(), null, "CLUSTER", c.title(), object("score", c.score()), null, null, "PENDING", c.score(), 65);
        int created = (int) count("SELECT count(*) FROM labeling_items WHERE run_id = ?", runId) - before;
        metric(metrics, "labeling_items_created", count("SELECT count(*) FROM labeling_items WHERE run_id = ?", runId));
        stage(runId, "LABELING_ACCUMULATION", "COMPLETED", macro.size(), created, 0, 0, 0, object("created", created), null, elapsed(started));
        log.info("[LABELING_ITEMS_CREATED] count={}", created);
    }

    private void trainingAccumulation(long runId, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        int before = (int) count("SELECT count(*) FROM training_examples WHERE run_id = ?", runId);
        jdbc.update("""
                WITH candidates AS (
                    SELECT mc.run_id,
                           CASE WHEN mc.top_label = 'NOISE_OR_CHAT' THEN 'WEAK_NOISE_SUPPRESSION' ELSE 'WEAK_BERT_REPLAY' END AS source_name,
                           mc.dataset_message_id,
                           COALESCE(mi.normalized_text, dm.text, dm.caption, '') AS text,
                           jsonb_build_object(
                               'runId', mc.run_id,
                               'classifierSource', mc.classification_source,
                               'ruleDecision', mi.rule_decision,
                               'hardSignal', mi.hard_signal,
                               'scores', mi.rule_scores_json,
                               'chatTitle', dm.chat_title
                           ) AS context_json,
                           mc.top_label AS label,
                           CASE
                               WHEN mc.top_label = 'NOISE_OR_CHAT' THEN 'NONE'
                               WHEN mc.top_label IN ('ERROR_LOG_WITH_FIX', 'TROUBLESHOOTING_FIX') THEN 'TROUBLESHOOTING_NOTE'
                               WHEN mc.top_label = 'COMPARISON_OR_BENCHMARK' THEN 'COMPARISON_INSIGHT'
                               WHEN mc.top_label = 'PRICING_OR_ACCESS_SIGNAL' THEN 'PRICE_ACCESS_CARD'
                               WHEN mc.top_label = 'RESOURCE_LINK_COLLECTION' THEN 'RESOURCE_CARD'
                               WHEN mc.top_label IN ('SECURITY_OR_RISK_WARNING', 'UNSAFE_OR_POLICY_RISK') THEN 'RISK_NOTE'
                               WHEN mc.top_label IN ('RAW_NEWS_LOW_ACTIONABILITY', 'TOOL_OR_MODEL_RELEASE', 'MARKET_OR_ECOSYSTEM_SIGNAL') THEN 'NEWS_SIGNAL'
                               WHEN mc.top_label IN ('HOW_TO_GUIDE', 'API_OR_CONFIG_SNIPPET', 'PROMPT_OR_AGENT_PATTERN', 'WORKFLOW_AUTOMATION') THEN 'GUIDE'
                               ELSE 'NOTE'
                           END AS artifact_type_label,
                           CASE WHEN mc.top_label = 'NOISE_OR_CHAT' THEN 'NOISE' ELSE 'POTENTIALLY_USEFUL' END AS usefulness_label,
                           CASE
                               WHEN mc.top_label = 'QUESTION_WITH_VALUABLE_ANSWER' THEN jsonb_build_array('QUESTION')
                               WHEN mc.top_label IN ('ERROR_LOG_WITH_FIX', 'TROUBLESHOOTING_FIX') THEN jsonb_build_array('ERROR_LOG', 'FIX')
                               WHEN mc.top_label = 'PRICING_OR_ACCESS_SIGNAL' THEN jsonb_build_array('PRICE_OR_ACCESS')
                               WHEN mc.top_label = 'RESOURCE_LINK_COLLECTION' THEN jsonb_build_array('RESOURCE_LINK')
                               WHEN mc.top_label = 'PROMO_WITH_USEFUL_DETAILS' THEN jsonb_build_array('PROMO')
                               WHEN mc.top_label IN ('SECURITY_OR_RISK_WARNING', 'UNSAFE_OR_POLICY_RISK') THEN jsonb_build_array('RISK')
                               WHEN mc.top_label = 'API_OR_CONFIG_SNIPPET' THEN jsonb_build_array('CODE_OR_CONFIG')
                               WHEN mc.top_label = 'COMPARISON_OR_BENCHMARK' THEN jsonb_build_array('COMPARISON')
                               WHEN mc.top_label = 'NOISE_OR_CHAT' THEN jsonb_build_array('CHAT')
                               ELSE jsonb_build_array('ANSWER')
                           END AS message_role_labels_json,
                           CASE
                               WHEN lower(COALESCE(mi.normalized_text, '')) ~ '(gpt|claude|anthropic|model|llm|prompt|agent|bge|bert)' THEN 'AI'
                               WHEN lower(COALESCE(mi.normalized_text, '')) ~ '(crypto|btc|eth|solana|token|airdrop|wallet)' THEN 'CRYPTO'
                               WHEN lower(COALESCE(mi.normalized_text, '')) ~ '(crm|sales|lead|customer|business|pricing|invoice)' THEN 'BUSINESS'
                               WHEN lower(COALESCE(mi.normalized_text, '')) ~ '(api|docker|postgres|spring|react|vite|github|repo|config)' THEN 'DEV_TOOLS'
                               ELSE 'OTHER'
                           END AS domain_label,
                           CASE
                               WHEN mc.top_label = 'RESOURCE_LINK_COLLECTION' THEN jsonb_build_array('HAS_SOURCE')
                               WHEN mc.top_label IN ('ERROR_LOG_WITH_FIX', 'TROUBLESHOOTING_FIX') THEN jsonb_build_array('HAS_ERROR_SIGNATURE', 'HAS_DETAILS')
                               WHEN mc.top_label = 'PRICING_OR_ACCESS_SIGNAL' THEN jsonb_build_array('HAS_NUMBERS', 'HAS_DETAILS')
                               WHEN mc.top_label IN ('HOW_TO_GUIDE', 'API_OR_CONFIG_SNIPPET') THEN jsonb_build_array('HAS_STEPS')
                               ELSE jsonb_build_array('UNSUPPORTED_CLAIM')
                           END AS evidence_labels_json,
                           CASE
                               WHEN mc.top_label = 'NOISE_OR_CHAT' THEN 'NOT_ACTIONABLE'
                               WHEN mc.top_label IN ('RAW_NEWS_LOW_ACTIONABILITY', 'MARKET_OR_ECOSYSTEM_SIGNAL') THEN 'FYI_ONLY'
                               WHEN mc.top_label IN ('RESOURCE_LINK_COLLECTION', 'SECURITY_OR_RISK_WARNING') THEN 'REFERENCE_ONLY'
                               ELSE 'ACTIONABLE'
                           END AS actionability_label,
                           mc.confidence
                    FROM message_classifications mc
                    JOIN dataset_messages dm ON dm.id = mc.dataset_message_id
                    LEFT JOIN message_intelligence mi ON mi.run_id = mc.run_id AND mi.dataset_message_id = mc.dataset_message_id
                    WHERE mc.run_id = ?
                      AND mc.classification_source IN ('MODEL', 'INHERITED_FROM_DUPLICATE')
                      AND (mc.confidence >= 0.70 OR mc.top_label = 'NOISE_OR_CHAT' OR COALESCE(mi.hard_signal, false))
                )
                INSERT INTO training_examples (
                    run_id, source, dataset_message_id, text, context_json, label, artifact_type, decision, confidence, split,
                    usefulness_label, artifact_type_label, message_role_labels_json, domain_label, evidence_labels_json, actionability_label, model_version
                )
                SELECT c.run_id, c.source_name, c.dataset_message_id, c.text, c.context_json, c.label, c.artifact_type_label,
                        c.usefulness_label, c.confidence, 'UNASSIGNED',
                        c.usefulness_label, c.artifact_type_label, c.message_role_labels_json, c.domain_label, c.evidence_labels_json, c.actionability_label,
                        'BOOTSTRAP_BERT_CLASSIFIER'
                FROM candidates c
                WHERE NOT EXISTS (
                    SELECT 1 FROM training_examples existing
                    WHERE existing.run_id = c.run_id
                      AND existing.dataset_message_id = c.dataset_message_id
                      AND existing.source = c.source_name
                )
                """, runId);
        jdbc.update("""
                WITH candidates AS (
                    SELECT ki.run_id,
                           'ACCEPTED_KNOWLEDGE_ITEM' AS source_name,
                           kis.dataset_message_id,
                           COALESCE(dm.text, dm.caption, kis.quote, '') AS text,
                           jsonb_build_object(
                               'runId', ki.run_id,
                               'knowledgeItemId', ki.id,
                               'sourceClusterType', ki.source_cluster_type,
                               'sourceClusterId', ki.source_cluster_id,
                               'sourceRole', kis.source_role,
                               'chatTitle', dm.chat_title
                           ) AS context_json,
                           'USEFUL' AS usefulness_label,
                           CASE
                               WHEN ki.artifact_type IN ('GUIDE', 'NOTE', 'TROUBLESHOOTING_NOTE', 'COMPARISON_INSIGHT', 'PRICE_ACCESS_CARD', 'RESOURCE_CARD', 'RISK_NOTE', 'NEWS_SIGNAL', 'TREND_CLUSTER', 'NONE')
                                   THEN ki.artifact_type
                               ELSE 'NOTE'
                           END AS artifact_type_label,
                           jsonb_build_array('ANSWER') AS message_role_labels_json,
                           'OTHER' AS domain_label,
                           jsonb_build_array('HAS_SOURCE', 'HAS_DETAILS') AS evidence_labels_json,
                           'ACTIONABLE' AS actionability_label,
                           COALESCE(kis.confidence, ki.knowledge_value_score, 0.7) AS confidence
                    FROM knowledge_items ki
                    JOIN knowledge_item_sources kis ON kis.knowledge_item_id = ki.id
                    JOIN dataset_messages dm ON dm.id = kis.dataset_message_id
                    WHERE ki.run_id = ?
                      AND COALESCE(ki.knowledge_value_score, 0) >= 0.70
                )
                INSERT INTO training_examples (
                    run_id, source, dataset_message_id, text, context_json, label, artifact_type, decision, confidence, split,
                    usefulness_label, artifact_type_label, message_role_labels_json, domain_label, evidence_labels_json, actionability_label, model_version
                )
                SELECT c.run_id, c.source_name, c.dataset_message_id, c.text, c.context_json, c.usefulness_label, c.artifact_type_label,
                        c.usefulness_label, c.confidence, 'UNASSIGNED',
                        c.usefulness_label, c.artifact_type_label, c.message_role_labels_json, c.domain_label, c.evidence_labels_json, c.actionability_label,
                        'KNOWLEDGE_GENERATION_V1'
                FROM candidates c
                WHERE NOT EXISTS (
                    SELECT 1 FROM training_examples existing
                    WHERE existing.run_id = c.run_id
                      AND existing.dataset_message_id = c.dataset_message_id
                      AND existing.source = c.source_name
                )
                """, runId);
        int created = (int) count("SELECT count(*) FROM training_examples WHERE run_id = ?", runId) - before;
        metric(metrics, "training_examples_created", created);
        stage(runId, "TRAINING_ACCUMULATION", "COMPLETED", count("SELECT count(*) FROM message_classifications WHERE run_id = ?", runId), created, 0, 0, 0, object("created", created), null, elapsed(started));
        log.info("[TRAINING_EXAMPLES_ACCUMULATED] runId={} created={}", runId, created);
    }

    private void aggregate(long runId, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        metric(metrics, "llm_avoided_by_rules", metrics.getOrDefault("messages_suppressed", BigDecimal.ZERO));
        metric(metrics, "llm_avoided_by_bert", metrics.getOrDefault("bert_noise_count", BigDecimal.ZERO));
        metric(metrics, "llm_avoided_by_noise", metrics.getOrDefault("messages_suppressed", BigDecimal.ZERO).add(metrics.getOrDefault("bert_noise_count", BigDecimal.ZERO)));
        metric(metrics, "llm_avoided_by_cache", count("SELECT count(*) FROM provider_calls WHERE run_id = ? AND status = 'CACHE_HIT'", runId));
        metric(metrics, "provider_calls_failed", count("SELECT count(*) FROM provider_calls WHERE run_id = ? AND status NOT IN ('SUCCESS','CACHE_HIT')", runId));
        metric(metrics, "provider_calls_timeout", count("SELECT count(*) FROM provider_calls WHERE run_id = ? AND status = 'TIMEOUT'", runId));
        metric(metrics, "provider_calls_rate_limited", count("SELECT count(*) FROM provider_calls WHERE run_id = ? AND status = 'RATE_LIMITED'", runId));
        metric(metrics, "total_input_tokens", count("SELECT COALESCE(sum(input_tokens),0) FROM provider_calls WHERE run_id = ?", runId));
        metric(metrics, "total_output_tokens", count("SELECT COALESCE(sum(output_tokens),0) FROM provider_calls WHERE run_id = ?", runId));
        metric(metrics, "guides_generated", count("SELECT count(*) FROM knowledge_items WHERE run_id = ? AND artifact_type = 'GUIDE'", runId));
        metric(metrics, "notes_generated", count("SELECT count(*) FROM knowledge_items WHERE run_id = ? AND artifact_type = 'NOTE'", runId));
        metric(metrics, "risk_notes_generated", count("SELECT count(*) FROM knowledge_items WHERE run_id = ? AND artifact_type = 'RISK_NOTE'", runId));
        metric(metrics, "price_cards_generated", count("SELECT count(*) FROM knowledge_items WHERE run_id = ? AND artifact_type = 'PRICE_ACCESS_CARD'", runId));
        metric(metrics, "training_examples_created", count("SELECT count(*) FROM training_examples"));
        for (Map.Entry<String, BigDecimal> e : metrics.entrySet()) jdbc.update("INSERT INTO replay_metrics (run_id, metric_name, metric_value_numeric) VALUES (?, ?, ?)", runId, e.getKey(), e.getValue());
        stage(runId, "METRICS_AGGREGATION", "COMPLETED", metrics.size(), metrics.size(), 0, 0, 0, object("metrics", metrics.size()), null, elapsed(started));
    }

    public ObjectNode exportJson(long runId, boolean includeMessages, boolean includeProviderResponses, boolean includeSanitizedPrompts) {
        ObjectNode root = json.createObjectNode();
        root.set("run", one("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM replay_runs WHERE id = ?) t", runId));
        Long datasetId = jdbc.queryForObject("SELECT dataset_id FROM replay_runs WHERE id = ?", Long.class, runId);
        root.set("dataset", one("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM datasets WHERE id = ?) t", datasetId));
        root.set("summary", one("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM v_replay_run_summary WHERE run_id = ?) t", runId));
        root.set("stages", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM replay_run_stages WHERE run_id = ? ORDER BY id) t", runId));
        root.set("rules", one("SELECT jsonb_build_object('suppressed', count(*) FILTER (WHERE rule_decision='SUPPRESS'), 'candidates', count(*) FILTER (WHERE rule_decision='CANDIDATE'), 'accumulated', count(*) FILTER (WHERE rule_decision='ACCUMULATE'))::text FROM message_intelligence WHERE run_id = ?", runId));
        root.set("bert", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM message_classifications WHERE run_id = ? ORDER BY id LIMIT 500) t", runId));
        root.set("bge", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT id, run_id, dataset_message_id, model_id, embedding_kind, embedding_hash, created_at FROM message_embeddings WHERE run_id = ? ORDER BY id LIMIT 500) t", runId));
        root.set("deduplication", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM dedupe_groups WHERE run_id = ? ORDER BY id) t", runId));
        root.set("semanticSearch", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM semantic_neighbors WHERE run_id = ? ORDER BY similarity DESC LIMIT 500) t", runId));
        root.set("microclusters", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM microclusters WHERE run_id = ? ORDER BY score DESC) t", runId));
        root.set("macroclusters", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM macroclusters WHERE run_id = ? ORDER BY score DESC) t", runId));
        root.set("topics", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM discovered_topics WHERE run_id = ? ORDER BY score DESC) t", runId));
        root.set("clusterScores", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM cluster_scores WHERE run_id = ? ORDER BY final_score DESC) t", runId));
        root.set("llmAvoidance", one("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM v_llm_avoidance_by_run WHERE run_id = ?) t", runId));
        root.set("providerCalls", rows(includeProviderResponses ? "SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM provider_calls WHERE run_id = ? ORDER BY id) t" : "SELECT row_to_json(t)::jsonb::text FROM (SELECT id, run_id, stage, provider_id, model_id, model_name, status, input_tokens, output_tokens, estimated_cost_usd, latency_ms, http_status, error_code, error_message, created_at FROM provider_calls WHERE run_id = ? ORDER BY id) t", runId));
        root.set("knowledgeItems", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM knowledge_items WHERE run_id = ? ORDER BY id) t", runId));
        root.set("labeling", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM labeling_items WHERE run_id = ? ORDER BY priority DESC, id) t", runId));
        root.set("trainingExamples", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM training_examples ORDER BY id DESC LIMIT 500) t"));
        root.set("messageResults", includeMessages ? rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM replay_run_messages WHERE run_id = ? ORDER BY id LIMIT 1000) t", runId) : json.createArrayNode());
        root.set("errors", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT stage, error FROM replay_run_stages WHERE run_id = ? AND error IS NOT NULL) t", runId));
        Path path = Path.of("runs", "replay-" + runId, "result.json");
        try {
            Files.createDirectories(path.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
            log.info("[JSON_EXPORT_CREATED] path={}", path.toAbsolutePath());
            stage(runId, "JSON_EXPORT", "COMPLETED", 1, 1, 0, 0, 0, object("path", path.toString()), null, 0);
        } catch (Exception e) {
            stage(runId, "JSON_EXPORT", "FAILED", 1, 0, 0, 1, 0, object("path", path.toString()), e.getMessage(), 0);
        }
        return root;
    }

    public ObjectNode auditJson(long runId) {
        ObjectNode root = json.createObjectNode();
        root.set("summary", one("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM v_replay_run_summary WHERE run_id = ?) t", runId));
        root.set("stageSummary", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM v_replay_stage_summary WHERE run_id = ? ORDER BY stage) t", runId));
        root.set("stageOrder", rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT stage, status, started_at, input_count, output_count, skipped_count,
                           error_count, provider_call_count, local_model_call_count, latency_ms,
                           metrics_json, error
                    FROM replay_run_stages
                    WHERE run_id = ?
                    ORDER BY started_at, id
                ) t
                """, runId));
        root.set("classificationCoverage", classificationCoverage(runId));
        root.set("skippedReasons", classificationSkippedReasons(runId));
        root.set("classDistribution", classificationDistribution(runId));
        root.set("topExamplesByClass", classificationTopExamplesByClass(runId));
        root.set("whyNoKnowledgeItems", one("""
                SELECT jsonb_build_object(
                    'knowledgeItems', (SELECT count(*) FROM knowledge_items WHERE run_id = ?),
                    'judgeCalls', (SELECT count(*) FROM provider_calls WHERE run_id = ? AND stage = 'LLM_CLUSTER_JUDGE_AND_ROUTING'),
                    'generationCalls', (SELECT count(*) FROM provider_calls WHERE run_id = ? AND stage = 'KNOWLEDGE_GENERATION'),
                    'note', CASE
                        WHEN (SELECT count(*) FROM knowledge_items WHERE run_id = ?) > 0 THEN 'Knowledge items were generated.'
                        WHEN (SELECT count(*) FROM provider_calls WHERE run_id = ? AND stage = 'KNOWLEDGE_GENERATION') = 0
                            THEN 'No generation calls were recorded; inspect judge decisions, confidence and cluster score gate.'
                        ELSE 'Generation calls happened but produced no stored knowledge items; inspect provider status and generation JSON.'
                    END
                )::text
                """, runId, runId, runId, runId, runId));
        root.set("topClusters", rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT m.id AS macrocluster_id, m.title, m.score AS macro_score, m.message_count,
                           s.final_score, s.usefulness_score, s.pain_score, s.wtp_score,
                           s.publishability_score, s.score_reasons_json
                    FROM macroclusters m
                    LEFT JOIN cluster_scores s ON s.run_id = m.run_id AND s.cluster_id = m.id AND s.cluster_type = 'MACRO'
                    WHERE m.run_id = ?
                    ORDER BY s.final_score DESC NULLS LAST, m.score DESC
                    LIMIT 20
                ) t
                """, runId));
        root.set("clusterScores", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM cluster_scores WHERE run_id = ? ORDER BY final_score DESC) t", runId));
        root.set("providerJudgeResponses", rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT id, stage, model_name, status, input_tokens, output_tokens, estimated_cost_usd,
                           error_code, error_message,
                           jsonb_build_object(
                               'decision', response_json->>'decision',
                               'confidence', response_json->>'confidence',
                               'artifactType', response_json->>'artifactType',
                               'title', response_json->>'title',
                               'summary', response_json->>'summary',
                               'evidenceIds', response_json->'evidenceIds',
                               'safetyNotes', response_json->>'safetyNotes'
                           ) AS response
                    FROM provider_calls
                    WHERE run_id = ? AND stage = 'LLM_CLUSTER_JUDGE_AND_ROUTING'
                    ORDER BY created_at, id
                ) t
                """, runId));
        root.set("skippedClusters", rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT m.id AS macrocluster_id, m.title, m.score AS macro_score, m.message_count,
                           s.final_score,
                           COALESCE((rr.config_snapshot_json->>'minClusterScoreForJudge')::numeric, 0.65) AS configured_min_cluster_score,
                           CASE
                               WHEN m.score < COALESCE((rr.config_snapshot_json->>'minClusterScoreForJudge')::numeric, 0.65)
                                    AND COALESCE(s.final_score, m.score) >= COALESCE((rr.config_snapshot_json->>'minClusterScoreForJudge')::numeric, 0.65)
                                    THEN 'LEGACY_GATE_USED_MACRO_SCORE_INSTEAD_OF_FINAL_SCORE'
                               WHEN COALESCE(s.final_score, m.score) < COALESCE((rr.config_snapshot_json->>'minClusterScoreForJudge')::numeric, 0.65)
                                    THEN 'LOW_FINAL_SCORE'
                               ELSE 'PASSED_SCORE_GATE_OR_SKIPPED_BY_JUDGE'
                           END AS reason
                    FROM macroclusters m
                    JOIN replay_runs rr ON rr.id = m.run_id
                    LEFT JOIN cluster_scores s ON s.run_id = m.run_id AND s.cluster_id = m.id AND s.cluster_type = 'MACRO'
                    WHERE m.run_id = ?
                    ORDER BY s.final_score DESC NULLS LAST, m.score DESC
                ) t
                """, runId));
        root.set("topUnclusteredCandidates", rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT mi.dataset_message_id, left(mi.normalized_text, 280) AS text_preview,
                           mi.rule_decision, mi.hard_signal, mc.top_label, mc.confidence,
                           rrm.embedding_id IS NOT NULL AS embedded,
                           rrm.macrocluster_id IS NOT NULL AS clustered,
                           ((CASE WHEN mi.hard_signal THEN 0.35 ELSE 0 END) + COALESCE(mc.confidence, 0) + mi.text_len / 1000.0) AS utility_score
                    FROM message_intelligence mi
                    LEFT JOIN message_classifications mc ON mc.run_id = mi.run_id AND mc.dataset_message_id = mi.dataset_message_id
                    LEFT JOIN replay_run_messages rrm ON rrm.run_id = mi.run_id AND rrm.dataset_message_id = mi.dataset_message_id
                    WHERE mi.run_id = ? AND rrm.macrocluster_id IS NULL AND mi.rule_decision <> 'SUPPRESS'
                    ORDER BY utility_score DESC
                    LIMIT 20
                ) t
                """, runId));
        root.set("ruleBertDisagreements", rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT mi.dataset_message_id, left(mi.normalized_text, 280) AS text_preview,
                           mi.rule_decision, mc.top_label AS bert_label, mc.confidence,
                           mi.hard_signal,
                           ((CASE WHEN mi.hard_signal THEN 1 ELSE 0 END) + COALESCE(mc.confidence, 0)) AS score
                    FROM message_intelligence mi
                    LEFT JOIN message_classifications mc ON mc.run_id = mi.run_id AND mc.dataset_message_id = mi.dataset_message_id
                    WHERE mi.run_id = ?
                      AND (
                          (mi.rule_decision = 'SUPPRESS' AND mc.top_label <> 'NOISE_OR_CHAT')
                          OR (mi.hard_signal AND mc.top_label = 'NOISE_OR_CHAT')
                          OR (mi.rule_decision <> 'SUPPRESS' AND mc.confidence < 0.6)
                      )
                    ORDER BY score DESC, mi.text_len DESC
                    LIMIT 50
                ) t
                """, runId));
        root.set("ruleBertDisagreementSummary", one("""
                SELECT jsonb_build_object(
                    'ruleCandidates', count(*) FILTER (WHERE mi.rule_decision = 'CANDIDATE'),
                    'bertCandidates', count(*) FILTER (WHERE mc.top_label IS NOT NULL AND mc.top_label <> 'NOISE_OR_CHAT'),
                    'ruleSuppressedBertUseful', count(*) FILTER (WHERE mi.rule_decision = 'SUPPRESS' AND mc.top_label IS NOT NULL AND mc.top_label <> 'NOISE_OR_CHAT'),
                    'bertNoiseHardSignal', count(*) FILTER (WHERE mc.top_label = 'NOISE_OR_CHAT' AND mi.hard_signal)
                )::text
                FROM message_intelligence mi
                LEFT JOIN message_classifications mc ON mc.run_id = mi.run_id AND mc.dataset_message_id = mi.dataset_message_id
                WHERE mi.run_id = ?
                """, runId));
        root.set("labelingPriorityQueue", rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT id, item_type, dataset_message_id, macrocluster_id, knowledge_item_id,
                           priority, status, suggested_label, suggested_artifact_type,
                           suggested_decision, confidence, left(text_snapshot, 280) AS text_preview
                    FROM labeling_items
                    WHERE run_id = ?
                    ORDER BY priority DESC, confidence ASC, id
                    LIMIT 100
                ) t
                """, runId));
        root.set("activeLearningQueue", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM v_active_learning_queue WHERE dataset_message_id IN (SELECT dataset_message_id FROM replay_run_messages WHERE run_id = ?) OR knowledge_item_id IN (SELECT id FROM knowledge_items WHERE run_id = ?) ORDER BY priority DESC, created_at DESC LIMIT 100) t", runId, runId));
        root.set("labelingBreakdown", one("""
                SELECT jsonb_build_object(
                    'byStatus', (SELECT coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb) FROM (SELECT status, count(*) count FROM labeling_items WHERE run_id = ? GROUP BY status ORDER BY count DESC) t),
                    'byPriority', (SELECT coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb) FROM (SELECT priority, count(*) count FROM labeling_items WHERE run_id = ? GROUP BY priority ORDER BY priority DESC) t),
                    'byItemType', (SELECT coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb) FROM (SELECT item_type, count(*) count FROM labeling_items WHERE run_id = ? GROUP BY item_type ORDER BY count DESC) t),
                    'bySuggestedLabel', (SELECT coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb) FROM (SELECT suggested_label, count(*) count FROM labeling_items WHERE run_id = ? GROUP BY suggested_label ORDER BY count DESC NULLS LAST) t),
                    'bySuggestedDecision', (SELECT coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb) FROM (SELECT suggested_decision, count(*) count FROM labeling_items WHERE run_id = ? GROUP BY suggested_decision ORDER BY count DESC NULLS LAST) t)
                )::text
                """, runId, runId, runId, runId, runId));
        root.set("calibrationRecommendations", calibrationRecommendations());
        Path path = Path.of("runs", "replay-" + runId, "audit.json");
        try {
            Files.createDirectories(path.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
            log.info("[AUDIT_JSON_CREATED] path={}", path.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[AUDIT_JSON_FAILED] runId={} error={}", runId, e.getMessage());
        }
        return root;
    }

    public ObjectNode classificationAuditJson(long runId) {
        ObjectNode root = json.createObjectNode();
        root.set("summary", one("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM v_replay_run_summary WHERE run_id = ?) t", runId));
        root.set("classificationCoverage", classificationCoverage(runId));
        root.set("skippedReasons", classificationSkippedReasons(runId));
        root.set("classDistribution", classificationDistribution(runId));
        root.set("topExamplesByClass", classificationTopExamplesByClass(runId));
        root.set("ruleBertDisagreements", rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT mi.dataset_message_id, left(mi.normalized_text, 300) AS text_preview,
                           mi.rule_decision, mc.top_label AS bert_label, mc.confidence,
                           mc.classification_source, mi.hard_signal,
                           ((CASE WHEN mi.hard_signal THEN 1 ELSE 0 END) + COALESCE(mc.confidence, 0)) AS score
                    FROM message_intelligence mi
                    LEFT JOIN message_classifications mc ON mc.run_id = mi.run_id AND mc.dataset_message_id = mi.dataset_message_id
                    WHERE mi.run_id = ?
                      AND (
                          (mi.rule_decision = 'SUPPRESS' AND mc.top_label IS NOT NULL AND mc.top_label <> 'NOISE_OR_CHAT')
                          OR (mi.hard_signal AND mc.top_label = 'NOISE_OR_CHAT')
                          OR (mc.classification_source = 'MODEL' AND mc.confidence < 0.6)
                      )
                    ORDER BY score DESC, mi.text_len DESC
                    LIMIT 80
                ) t
                """, runId));
        root.set("topClusters", topClustersForAudit(runId));
        root.set("knowledgeItemMapping", knowledgeItemMapping(runId));
        root.set("usefulUnclusteredMessages", rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT mi.dataset_message_id, left(mi.normalized_text, 360) AS text_preview,
                           mi.rule_decision, mi.hard_signal, mc.top_label, mc.confidence,
                           rrm.embedding_id IS NOT NULL AS embedded,
                           rrm.macrocluster_id IS NOT NULL AS clustered,
                           ((CASE WHEN mi.hard_signal THEN 0.35 ELSE 0 END) + COALESCE(mc.confidence, 0) + mi.text_len / 1000.0) AS utility_score
                    FROM message_intelligence mi
                    LEFT JOIN message_classifications mc ON mc.run_id = mi.run_id AND mc.dataset_message_id = mi.dataset_message_id
                    LEFT JOIN replay_run_messages rrm ON rrm.run_id = mi.run_id AND rrm.dataset_message_id = mi.dataset_message_id
                    WHERE mi.run_id = ?
                      AND rrm.macrocluster_id IS NULL
                      AND (
                          mi.hard_signal
                          OR mc.top_label IS NOT NULL AND mc.top_label <> 'NOISE_OR_CHAT'
                      )
                    ORDER BY utility_score DESC
                    LIMIT 50
                ) t
                """, runId));
        root.set("activeLearningQueue", rows("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM v_active_learning_queue WHERE dataset_message_id IN (SELECT dataset_message_id FROM replay_run_messages WHERE run_id = ?) OR knowledge_item_id IN (SELECT id FROM knowledge_items WHERE run_id = ?) ORDER BY priority DESC, created_at DESC LIMIT 100) t", runId, runId));
        root.set("fineTuningRecommendations", fineTuningRecommendations());
        Path path = Path.of("runs", "replay-" + runId, "classification-audit.json");
        try {
            Files.createDirectories(path.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
            log.info("[CLASSIFICATION_AUDIT_JSON_CREATED] path={}", path.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[CLASSIFICATION_AUDIT_JSON_FAILED] runId={} error={}", runId, e.getMessage());
        }
        return root;
    }

    private JsonNode classificationCoverage(long runId) {
        return one("""
                SELECT jsonb_build_object(
                    'datasetMessages', (SELECT count(*) FROM dataset_messages dm JOIN replay_runs rr2 ON rr2.dataset_id = dm.dataset_id WHERE rr2.id = rr.id),
                    'replayMessages', (SELECT count(*) FROM replay_run_messages WHERE run_id = rr.id),
                    'messageIntelligence', (SELECT count(*) FROM message_intelligence WHERE run_id = rr.id),
                    'messageClassifications', (SELECT count(*) FROM message_classifications WHERE run_id = rr.id),
                    'modelClassifications', (SELECT count(*) FROM message_classifications WHERE run_id = rr.id AND classification_source = 'MODEL'),
                    'inheritedFromDuplicate', (SELECT count(*) FROM message_classifications WHERE run_id = rr.id AND classification_source = 'INHERITED_FROM_DUPLICATE'),
                    'skippedNoText', (SELECT count(*) FROM message_classifications WHERE run_id = rr.id AND classification_source = 'SKIPPED_NO_TEXT'),
                    'skippedError', (SELECT count(*) FROM message_classifications WHERE run_id = rr.id AND classification_source = 'SKIPPED_ERROR'),
                    'missingClassificationRows', (
                        SELECT count(*)
                        FROM replay_run_messages rrm
                        LEFT JOIN message_classifications mc ON mc.run_id = rrm.run_id AND mc.dataset_message_id = rrm.dataset_message_id
                        WHERE rrm.run_id = rr.id AND mc.id IS NULL
                    ),
                    'meaningfulTextMessages', (SELECT count(*) FROM message_intelligence WHERE run_id = rr.id AND COALESCE(text_len, 0) > 0),
                    'textClassificationCoverage', (
                        SELECT round((count(*) FILTER (WHERE classification_source IN ('MODEL', 'INHERITED_FROM_DUPLICATE'))::numeric
                            / NULLIF((SELECT count(*) FROM message_intelligence WHERE run_id = rr.id AND COALESCE(text_len, 0) > 0), 0)), 4)
                        FROM message_classifications
                        WHERE run_id = rr.id
                    )
                )::text
                FROM replay_runs rr
                WHERE rr.id = ?
                """, runId);
    }

    private ArrayNode classificationSkippedReasons(long runId) {
        return rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT skip_reason,
                           count(*) AS count,
                           (array_agg(dataset_message_id ORDER BY dataset_message_id))[1:20] AS sample_message_ids
                    FROM (
                        SELECT rrm.dataset_message_id,
                               COALESCE(
                                   mss.skip_reason,
                                   mc.skip_reason,
                                   CASE
                                       WHEN mc.classification_source = 'SKIPPED_NO_TEXT' THEN 'NO_TEXT'
                                       WHEN mc.classification_source = 'SKIPPED_ERROR' THEN 'MODEL_ERROR'
                                       WHEN mc.id IS NULL AND COALESCE(mi.text_len, 0) = 0 THEN 'NO_TEXT'
                                       WHEN mc.id IS NULL AND rrm.llm_skip_reason = 'DUPLICATE' THEN 'EXACT_DUPLICATE'
                                       WHEN mc.id IS NULL AND COALESCE(mi.text_len, 0) < 18 THEN 'TOO_SHORT'
                                       WHEN mc.id IS NULL AND mi.rule_decision = 'SUPPRESS' THEN 'RULE_SUPPRESSED'
                                       WHEN mc.id IS NULL THEN 'OTHER'
                                   END
                               ) AS skip_reason
                        FROM replay_run_messages rrm
                        LEFT JOIN message_intelligence mi ON mi.run_id = rrm.run_id AND mi.dataset_message_id = rrm.dataset_message_id
                        LEFT JOIN message_classifications mc ON mc.run_id = rrm.run_id AND mc.dataset_message_id = rrm.dataset_message_id
                        LEFT JOIN message_stage_skips mss ON mss.run_id = rrm.run_id AND mss.dataset_message_id = rrm.dataset_message_id AND mss.stage = 'BERT_CLASSIFICATION'
                        WHERE rrm.run_id = ?
                          AND (
                              mc.id IS NULL
                              OR mc.classification_source LIKE 'SKIPPED_%'
                              OR mss.id IS NOT NULL
                          )
                    ) skipped
                    WHERE skip_reason IS NOT NULL
                    GROUP BY skip_reason
                    ORDER BY count DESC, skip_reason
                ) t
                """, runId);
    }

    private ArrayNode classificationDistribution(long runId) {
        return rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT top_label,
                           count(*) AS count,
                           round(avg(confidence)::numeric, 3) AS avg_confidence,
                           count(*) FILTER (WHERE classification_source = 'MODEL') AS model_count,
                           count(*) FILTER (WHERE classification_source = 'INHERITED_FROM_DUPLICATE') AS inherited_count
                    FROM message_classifications
                    WHERE run_id = ?
                      AND classification_source IN ('MODEL', 'INHERITED_FROM_DUPLICATE')
                    GROUP BY top_label
                    ORDER BY count DESC, top_label
                ) t
                """, runId);
    }

    private ArrayNode classificationTopExamplesByClass(long runId) {
        return rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT top_label, confidence, dataset_message_id, text_preview, chat_title,
                           hard_signal, rule_decision, rule_scores_json
                    FROM (
                        SELECT mc.top_label, mc.confidence, mc.dataset_message_id,
                               left(COALESCE(dm.text, dm.caption, ''), 500) AS text_preview,
                               dm.chat_title, mi.hard_signal, mi.rule_decision, mi.rule_scores_json,
                               row_number() OVER (PARTITION BY mc.top_label ORDER BY mc.confidence DESC, mc.dataset_message_id) AS rn
                        FROM message_classifications mc
                        JOIN dataset_messages dm ON dm.id = mc.dataset_message_id
                        LEFT JOIN message_intelligence mi ON mi.run_id = mc.run_id AND mi.dataset_message_id = mc.dataset_message_id
                        WHERE mc.run_id = ?
                          AND mc.classification_source IN ('MODEL', 'INHERITED_FROM_DUPLICATE')
                    ) ranked
                    WHERE rn <= 3
                    ORDER BY top_label, confidence DESC
                ) t
                """, runId);
    }

    private ArrayNode topClustersForAudit(long runId) {
        return rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    WITH members AS (
                        SELECT ma.id AS macrocluster_id, mm.dataset_message_id
                        FROM macroclusters ma
                        JOIN macrocluster_members mam ON mam.macrocluster_id = ma.id
                        JOIN microcluster_members mm ON mm.microcluster_id = mam.microcluster_id
                        WHERE ma.run_id = ?
                    )
                    SELECT ma.id AS cluster_id,
                           ma.title,
                           ma.macrocluster_type AS type,
                           cs.final_score,
                           ma.message_count,
                           (
                               SELECT COALESCE(jsonb_agg(to_jsonb(label_counts)), '[]'::jsonb)
                               FROM (
                                   SELECT mc.top_label, count(*) AS count
                                   FROM members m
                                   JOIN message_classifications mc ON mc.run_id = ma.run_id AND mc.dataset_message_id = m.dataset_message_id
                                   WHERE m.macrocluster_id = ma.id
                                   GROUP BY mc.top_label
                                   ORDER BY count DESC
                               ) label_counts
                           ) AS main_class_labels,
                           (
                               SELECT COALESCE(jsonb_agg(DISTINCT domain.value), '[]'::jsonb)
                               FROM members m
                               JOIN message_intelligence mi ON mi.run_id = ma.run_id AND mi.dataset_message_id = m.dataset_message_id
                               CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(mi.structural_features_json->'domains', '[]'::jsonb)) AS domain(value)
                               WHERE m.macrocluster_id = ma.id
                           ) AS source_domains,
                           (
                               SELECT COALESCE(jsonb_agg(to_jsonb(previews)), '[]'::jsonb)
                               FROM (
                                   SELECT mi.dataset_message_id, left(mi.normalized_text, 280) AS text_preview
                                   FROM members m
                                   JOIN message_intelligence mi ON mi.run_id = ma.run_id AND mi.dataset_message_id = m.dataset_message_id
                                   WHERE m.macrocluster_id = ma.id
                                   ORDER BY mi.text_len DESC
                                   LIMIT 5
                               ) previews
                           ) AS message_previews,
                           EXISTS (SELECT 1 FROM knowledge_items ki WHERE ki.run_id = ma.run_id AND ki.source_cluster_type = 'MACRO' AND ki.source_cluster_id = ma.id) AS generated_item,
                           (
                               SELECT COALESCE(jsonb_agg(ki.id), '[]'::jsonb)
                               FROM knowledge_items ki
                               WHERE ki.run_id = ma.run_id AND ki.source_cluster_type = 'MACRO' AND ki.source_cluster_id = ma.id
                           ) AS knowledge_item_ids,
                           (
                               SELECT COALESCE(jsonb_agg(DISTINCT rrm.llm_skip_reason) FILTER (WHERE rrm.llm_skip_reason IS NOT NULL), '[]'::jsonb)
                               FROM members m
                               JOIN replay_run_messages rrm ON rrm.run_id = ma.run_id AND rrm.dataset_message_id = m.dataset_message_id
                               WHERE m.macrocluster_id = ma.id
                           ) AS llm_skip_reasons,
                           CASE
                               WHEN EXISTS (SELECT 1 FROM knowledge_items ki WHERE ki.run_id = ma.run_id AND ki.source_cluster_type = 'MACRO' AND ki.source_cluster_id = ma.id) THEN 'GENERATED'
                               WHEN EXISTS (
                                   SELECT 1 FROM members m
                                   JOIN replay_run_messages rrm ON rrm.run_id = ma.run_id AND rrm.dataset_message_id = m.dataset_message_id
                                   WHERE m.macrocluster_id = ma.id AND rrm.llm_skip_reason = 'JUDGE_REJECTED'
                               ) THEN 'JUDGE_REJECTED'
                               WHEN EXISTS (
                                   SELECT 1 FROM members m
                                   JOIN replay_run_messages rrm ON rrm.run_id = ma.run_id AND rrm.dataset_message_id = m.dataset_message_id
                                   WHERE m.macrocluster_id = ma.id AND rrm.llm_skip_reason = 'BUDGET_BLOCKED'
                               ) THEN 'BUDGET_BLOCKED'
                               ELSE 'NOT_SENT_OR_LOW_SCORE'
                           END AS judge_decision
                    FROM macroclusters ma
                    LEFT JOIN cluster_scores cs ON cs.run_id = ma.run_id AND cs.cluster_id = ma.id AND cs.cluster_type = 'MACRO'
                    WHERE ma.run_id = ?
                    ORDER BY cs.final_score DESC NULLS LAST, ma.score DESC
                    LIMIT 10
                ) t
                """, runId, runId);
    }

    private ArrayNode knowledgeItemMapping(long runId) {
        return rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT ki.id,
                           ki.artifact_type,
                           ki.title,
                           ki.summary,
                           ki.knowledge_value_score,
                           ki.source_cluster_type,
                           ki.source_cluster_id,
                           kis.dataset_message_id,
                           kis.source_role,
                           kis.quote,
                           kis.confidence,
                           left(COALESCE(dm.text, dm.caption, ''), 700) AS source_text
                    FROM knowledge_items ki
                    LEFT JOIN knowledge_item_sources kis ON kis.knowledge_item_id = ki.id
                    LEFT JOIN dataset_messages dm ON dm.id = kis.dataset_message_id
                    WHERE ki.run_id = ?
                    ORDER BY ki.knowledge_value_score DESC, ki.id, kis.confidence DESC
                ) t
                """, runId);
    }

    private ObjectNode fineTuningRecommendations() {
        ObjectNode root = object();
        root.put("classifierShape", "multi_head_or_multi_label");
        root.set("usefulnessLabels", array("USEFUL", "POTENTIALLY_USEFUL", "NOT_USEFUL", "NOISE"));
        root.set("artifactTypeLabels", array("GUIDE", "NOTE", "TROUBLESHOOTING_NOTE", "COMPARISON_INSIGHT", "PRICE_ACCESS_CARD", "RESOURCE_CARD", "RISK_NOTE", "NEWS_SIGNAL", "TREND_CLUSTER", "NONE"));
        root.set("messageRoleLabels", array("QUESTION", "ANSWER", "ERROR_LOG", "FIX", "ANNOUNCEMENT", "PRICE_OR_ACCESS", "RESOURCE_LINK", "PROMO", "CHAT", "RISK", "CODE_OR_CONFIG", "COMPARISON"));
        root.set("domainLabels", array("AI", "CRYPTO", "CONSTRUCTION", "BUSINESS", "DEV_TOOLS", "OTHER"));
        root.set("evidenceLabels", array("HAS_SOURCE", "HAS_DETAILS", "HAS_NUMBERS", "HAS_STEPS", "HAS_ERROR_SIGNATURE", "UNSUPPORTED_CLAIM"));
        root.set("actionabilityLabels", array("ACTIONABLE", "REFERENCE_ONLY", "FYI_ONLY", "NOT_ACTIONABLE"));
        root.put("minimumReviewedExamplesV1", 500);
        root.put("preferredReviewedExamplesV1", "1500-3000");
        root.put("rareClassMinimum", "50-100");
        root.put("warning", "Do not train topic-specific labels such as concrete model/vendor names; train stable value axes.");
        return root;
    }

    public List<Map<String, Object>> stages(long runId) { return jdbc.queryForList("SELECT * FROM replay_run_stages WHERE run_id = ? ORDER BY id", runId); }
    public List<Map<String, Object>> messages(long runId) { return jdbc.queryForList("SELECT * FROM replay_run_messages WHERE run_id = ? ORDER BY id LIMIT 1000", runId); }
    public List<Map<String, Object>> summary(long runId) { return jdbc.queryForList("SELECT * FROM v_replay_run_summary WHERE run_id = ?", runId); }
    public List<Map<String, Object>> providerCosts(long runId) { return jdbc.queryForList("SELECT * FROM v_provider_costs_by_run WHERE run_id = ?", runId); }
    public List<Map<String, Object>> knowledgeItems(long runId) { return jdbc.queryForList("SELECT * FROM knowledge_items WHERE run_id = ? ORDER BY id DESC", runId); }
    public List<Map<String, Object>> clusters(long runId) { return jdbc.queryForList("SELECT 'MICRO' AS level, id, title, cluster_type AS type, score, message_count, metadata_json::text AS metadata_json FROM microclusters WHERE run_id = ? UNION ALL SELECT 'MACRO' AS level, id, title, macrocluster_type AS type, score, message_count, metadata_json::text AS metadata_json FROM macroclusters WHERE run_id = ? ORDER BY level, score DESC", runId, runId); }
    public Map<String, Object> metrics(long runId) { Map<String, Object> r = new LinkedHashMap<>(); jdbc.query("SELECT metric_name, metric_value_numeric FROM replay_metrics WHERE run_id = ?", (RowCallbackHandler) rs -> r.put(rs.getString(1), rs.getBigDecimal(2)), runId); return r; }
    public List<ReplayRun> runs() { return jdbc.query("SELECT id, dataset_id, run_name, mode, pipeline_version, status, started_at, finished_at, total_messages, processed_messages, provider_calls_total, estimated_cost_usd, error FROM replay_runs ORDER BY created_at DESC, id DESC", this::mapRun); }
    public ReplayRun getRun(long id) { return jdbc.queryForObject("SELECT id, dataset_id, run_name, mode, pipeline_version, status, started_at, finished_at, total_messages, processed_messages, provider_calls_total, estimated_cost_usd, error FROM replay_runs WHERE id = ?", this::mapRun, id); }
    public void cancel(long runId) { jdbc.update("UPDATE replay_runs SET status = 'CANCELLED', finished_at = now(), error = 'Cancelled by user' WHERE id = ? AND status IN ('CREATED','RUNNING')", runId); }
    public List<Map<String, Object>> labelingItems(String status, Integer priority) {
        if (blank(status) && priority == null) {
            return jdbc.queryForList("SELECT * FROM labeling_items ORDER BY priority DESC, created_at DESC LIMIT 500");
        }
        if (blank(status)) {
            return jdbc.queryForList("SELECT * FROM labeling_items WHERE priority >= ? ORDER BY priority DESC, created_at DESC LIMIT 500", priority);
        }
        if (priority == null) {
            return jdbc.queryForList("SELECT * FROM labeling_items WHERE status = ? ORDER BY priority DESC, created_at DESC LIMIT 500", status);
        }
        return jdbc.queryForList("SELECT * FROM labeling_items WHERE status = ? AND priority >= ? ORDER BY priority DESC, created_at DESC LIMIT 500", status, priority);
    }
    public List<Map<String, Object>> activeLearningQueue(String reason, String label) {
        StringBuilder sql = new StringBuilder("SELECT * FROM v_active_learning_queue WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!blank(reason)) {
            sql.append(" AND reason = ?");
            args.add(reason);
        }
        if (!blank(label)) {
            sql.append(" AND (bert_label = ? OR suggested_labels_json::text ILIKE ?)");
            args.add(label);
            args.add("%" + label + "%");
        }
        sql.append(" ORDER BY priority DESC, bert_confidence ASC NULLS FIRST, created_at DESC LIMIT 500");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> createActiveLearningBatch(ActiveLearningBatchRequest request) {
        long runId = request.runId();
        int targetSize = Math.max(1, Math.min(request.targetSize() == null ? 300 : request.targetSize(), 1000));
        String strategy = blank(request.strategy()) ? "BALANCED_ACTIVE_LEARNING" : request.strategy();
        long batchId = JdbcIds.insertReturningId(jdbc, "INSERT INTO labeling_batches (run_id, strategy, target_size, status) VALUES (?, ?, ?, 'OPEN')", runId, strategy, targetSize);
        Map<String, Integer> plan = activeLearningPlan(targetSize);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<Long> selected = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : plan.entrySet()) {
            List<Long> ids = candidateLabelingIds(runId, entry.getKey(), entry.getValue(), selected);
            for (Long id : ids) selected.add(id);
            counts.put(entry.getKey(), ids.size());
        }
        if (selected.size() < targetSize) {
            List<Long> fill = jdbc.queryForList("""
                    SELECT id FROM labeling_items
                    WHERE run_id = ? AND status IN ('PENDING','DISAGREEMENT','NEEDS_REVIEW')
                      AND (batch_id IS NULL OR batch_id = ?)
                    ORDER BY priority DESC, confidence ASC NULLS FIRST, id
                    LIMIT ?
                    """, Long.class, runId, batchId, targetSize - selected.size());
            for (Long id : fill) selected.add(id);
            counts.merge("fill", fill.size(), Integer::sum);
        }
        if (!selected.isEmpty()) {
            jdbc.update("UPDATE labeling_items SET batch_id = ?, batch_strategy = ?, batch_bucket = COALESCE(batch_bucket, 'fill'), updated_at = now() WHERE id IN (" + placeholders(selected.size()) + ")", prepend(batchId, strategy, selected).toArray());
        }
        jdbc.update("UPDATE labeling_batches SET item_count = ?, bucket_counts_json = ?::jsonb, updated_at = now() WHERE id = ?", selected.size(), write(objectFrom(counts)), batchId);
        return Map.of("batchId", batchId, "runId", runId, "strategy", strategy, "targetSize", targetSize, "itemCount", selected.size(), "bucketCounts", counts, "items", batchItems(batchId));
    }

    @Transactional
    public Map<String, Object> createDailyReviewBatch(DailyReviewBatchRequest request) {
        LocalDate reviewDate = blank(request.date()) ? LocalDate.now() : LocalDate.parse(request.date());
        int targetSize = Math.max(1, Math.min(request.targetSize() == null ? 150 : request.targetSize(), 200));
        String strategy = blank(request.strategy()) ? "DAILY_ACTIVE_LEARNING" : request.strategy();
        int lastHours = Math.max(1, request.includeRunsFromLastHours() == null ? 24 : request.includeRunsFromLastHours());
        long runId = latestRunId(lastHours);
        long batchId = JdbcIds.insertReturningId(jdbc, """
                INSERT INTO labeling_batches (run_id, strategy, target_size, status, review_date, include_runs_from_last_hours, estimated_review_minutes, priority_reasons_json)
                VALUES (?, ?, ?, 'OPEN', ?, ?, 0, '[]'::jsonb)
                """, runId, strategy, targetSize, reviewDate, lastHours);
        Map<String, Integer> plan = dailyReviewPlan(targetSize);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<Long> selected = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : plan.entrySet()) {
            List<Long> ids = dailyCandidateLabelingIds(lastHours, entry.getKey(), entry.getValue(), selected);
            selected.addAll(ids);
            counts.put(entry.getKey(), ids.size());
        }
        if (selected.size() < targetSize) {
            List<Long> fill = dailyCandidateLabelingIds(lastHours, "fill", targetSize - selected.size(), selected);
            selected.addAll(fill);
            counts.merge("fill", fill.size(), Integer::sum);
        }
        if (!selected.isEmpty()) {
            jdbc.update("UPDATE labeling_items SET batch_id = ?, batch_strategy = ?, updated_at = now() WHERE id IN (" + placeholders(selected.size()) + ")", prepend(batchId, strategy, selected).toArray());
        }
        int estimatedMinutes = Math.max(1, (int) Math.ceil(selected.size() * 0.75));
        ArrayNode priorityReasons = priorityReasons(counts);
        jdbc.update("""
                UPDATE labeling_batches
                SET item_count = ?, bucket_counts_json = ?::jsonb, estimated_review_minutes = ?, priority_reasons_json = ?::jsonb, updated_at = now()
                WHERE id = ?
                """, selected.size(), write(objectFrom(counts)), estimatedMinutes, write(priorityReasons), batchId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("runId", runId);
        result.put("date", reviewDate.toString());
        result.put("strategy", strategy);
        result.put("targetSize", targetSize);
        result.put("includeRunsFromLastHours", lastHours);
        result.put("itemCount", selected.size());
        result.put("bucketCounts", counts);
        result.put("estimatedReviewMinutes", estimatedMinutes);
        result.put("priorityReasons", priorityReasons);
        result.put("items", batchItems(batchId));
        return result;
    }

    public Map<String, Object> activeLearningBatch(long batchId) {
        Map<String, Object> batch = jdbc.queryForMap("SELECT *, bucket_counts_json::text AS bucket_counts, priority_reasons_json::text AS priority_reasons FROM labeling_batches WHERE id = ?", batchId);
        batch.put("batchId", batch.get("id"));
        batch.put("runId", batch.get("run_id"));
        batch.put("targetSize", batch.get("target_size"));
        batch.put("itemCount", batch.get("item_count"));
        batch.put("bucketCounts", parse(String.valueOf(batch.getOrDefault("bucket_counts", "{}"))));
        batch.put("estimatedReviewMinutes", batch.get("estimated_review_minutes"));
        batch.put("priorityReasons", parseArray(String.valueOf(batch.getOrDefault("priority_reasons", "[]"))));
        batch.put("items", batchItems(batchId));
        return batch;
    }

    public Map<String, Object> trainingDataReadiness() {
        long rawMessages = count("SELECT count(*) FROM dataset_messages");
        long classifiedMessages = count("SELECT count(*) FROM message_classifications");
        long labelingItems = count("SELECT count(*) FROM labeling_items");
        long weakExamples = count("SELECT count(*) FROM training_examples WHERE source LIKE 'WEAK_%'");
        long strongExamples = count("SELECT count(*) FROM training_examples WHERE source IN ('HUMAN_LABEL','CORRECTED_PIPELINE','ACCEPTED_KNOWLEDGE_ITEM_AFTER_REVIEW','REJECTED_KNOWLEDGE_ITEM_AFTER_REVIEW')");
        long humanReviewed = count("SELECT count(*) FROM training_examples WHERE source IN ('HUMAN_LABEL','CORRECTED_PIPELINE','ACCEPTED_KNOWLEDGE_ITEM_AFTER_REVIEW','REJECTED_KNOWLEDGE_ITEM_AFTER_REVIEW')");
        long corrected = count("SELECT count(*) FROM training_examples WHERE source = 'CORRECTED_PIPELINE'");
        long reviewedToday = count("SELECT count(*) FROM training_examples WHERE reviewed_at >= date_trunc('day', now())");
        long reviewedThisWeek = count("SELECT count(*) FROM training_examples WHERE reviewed_at >= now() - interval '7 days'");
        double sevenDayAverage = reviewedThisWeek / 7.0;
        int minimumHumanReviewedNeeded = 500;
        long remainingToMinimum = Math.max(0, minimumHumanReviewedNeeded - humanReviewed);
        Long estimatedDays = sevenDayAverage <= 0 ? null : (long) Math.ceil(remainingToMinimum / sevenDayAverage);
        List<Map<String, Object>> labelDistribution = jdbc.queryForList("SELECT COALESCE(usefulness_label, label, 'UNLABELED') AS label, count(*) AS count FROM training_examples WHERE source IN ('HUMAN_LABEL','CORRECTED_PIPELINE','ACCEPTED_KNOWLEDGE_ITEM_AFTER_REVIEW','REJECTED_KNOWLEDGE_ITEM_AFTER_REVIEW') GROUP BY COALESCE(usefulness_label, label, 'UNLABELED') ORDER BY count DESC");
        List<Map<String, Object>> rareCoverage = jdbc.queryForList("SELECT role AS label, count(*) AS count FROM training_examples te CROSS JOIN LATERAL jsonb_array_elements_text(te.message_role_labels_json) role WHERE te.source IN ('HUMAN_LABEL','CORRECTED_PIPELINE','ACCEPTED_KNOWLEDGE_ITEM_AFTER_REVIEW','REJECTED_KNOWLEDGE_ITEM_AFTER_REVIEW') AND role IN ('ERROR_LOG','FIX','PRICE_OR_ACCESS','RESOURCE_LINK','RISK','COMPARISON','LIMIT_OR_ACCESS_ISSUE') GROUP BY role ORDER BY count");
        Map<String, Long> rareClassGaps = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        if (humanReviewed < 500) missing.add("Need at least 500 human-reviewed examples; current=" + humanReviewed);
        if (strongExamples < 500) missing.add("Need at least 500 strong examples; current=" + strongExamples);
        for (String rare : List.of("ERROR_LOG", "FIX", "PRICE_OR_ACCESS", "RESOURCE_LINK", "RISK", "COMPARISON", "LIMIT_OR_ACCESS_ISSUE")) {
            long current = rareCoverage.stream().filter(row -> rare.equals(row.get("label"))).map(row -> ((Number) row.get("count")).longValue()).findFirst().orElse(0L);
            rareClassGaps.put(rare, Math.max(0, 50 - current));
            if (current < 50) missing.add("Rare class " + rare + " needs 50+ reviewed examples; current=" + current);
        }
        if (count("SELECT count(*) FROM training_examples WHERE source IN ('HUMAN_LABEL','CORRECTED_PIPELINE','ACCEPTED_KNOWLEDGE_ITEM_AFTER_REVIEW','REJECTED_KNOWLEDGE_ITEM_AFTER_REVIEW') AND COALESCE(usefulness_label, label) IN ('NOISE','NOT_USEFUL','NOISE_OR_CHAT')") < 100) missing.add("Need negative/noise examples in reviewed set");
        boolean ready = missing.isEmpty();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("readyForClassifierV1", ready);
        result.put("rawMessages", rawMessages);
        result.put("classifiedMessages", classifiedMessages);
        result.put("labelingItems", labelingItems);
        result.put("weakExamples", weakExamples);
        result.put("strongExamples", strongExamples);
        result.put("humanReviewedExamples", humanReviewed);
        result.put("dailyReviewedCount", reviewedToday);
        result.put("reviewedToday", reviewedToday);
        result.put("reviewedThisWeek", reviewedThisWeek);
        result.put("sevenDayAverage", Math.round(sevenDayAverage * 10.0) / 10.0);
        result.put("minimumHumanReviewedNeeded", minimumHumanReviewedNeeded);
        result.put("remainingToMinimum", remainingToMinimum);
        result.put("estimatedDaysToClassifierV1", estimatedDays);
        result.put("correctedExamples", corrected);
        result.put("labelDistribution", labelDistribution);
        result.put("rareClassCoverage", rareCoverage);
        result.put("rareClassGaps", rareClassGaps);
        result.put("missingForClassifierV1", missing);
        result.put("recommendedReviewedExamples", "1500-3000");
        return result;
    }
    public Map<String, Object> labelingItem(long id) { return jdbc.queryForMap("SELECT * FROM labeling_items WHERE id = ?", id); }
    public List<Map<String, Object>> trainingExamples() { return jdbc.queryForList("SELECT * FROM training_examples ORDER BY id DESC LIMIT 1000"); }
    public Map<String, Object> trainingExamplesSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", count("SELECT count(*) FROM training_examples"));
        result.put("bySplit", jdbc.queryForList("SELECT split, count(*) AS count FROM training_examples GROUP BY split ORDER BY split"));
        result.put("bySource", jdbc.queryForList("SELECT source, count(*) AS count FROM training_examples GROUP BY source ORDER BY count DESC"));
        result.put("byUsefulness", jdbc.queryForList("SELECT COALESCE(usefulness_label, label, 'UNLABELED') AS label, count(*) AS count FROM training_examples GROUP BY COALESCE(usefulness_label, label, 'UNLABELED') ORDER BY count DESC"));
        result.put("byArtifactType", jdbc.queryForList("SELECT COALESCE(artifact_type_label, artifact_type, 'NONE') AS label, count(*) AS count FROM training_examples GROUP BY COALESCE(artifact_type_label, artifact_type, 'NONE') ORDER BY count DESC"));
        result.put("byDomain", jdbc.queryForList("SELECT COALESCE(domain_label, 'OTHER') AS label, count(*) AS count FROM training_examples GROUP BY COALESCE(domain_label, 'OTHER') ORDER BY count DESC"));
        return result;
    }
    public ArrayNode exportTrainingExamples() { ArrayNode a = json.createArrayNode(); jdbc.query("SELECT row_to_json(t)::jsonb::text FROM (SELECT * FROM training_examples ORDER BY id) t", (RowCallbackHandler) rs -> a.add(parse(rs.getString(1)))); return a; }

    public Map<String, Object> exportClassifierDataset(TrainingDatasetExportRequest request) {
        String format = blank(request.format()) ? "jsonl" : request.format().toLowerCase(Locale.ROOT);
        if (!format.equals("jsonl")) {
            throw new IllegalArgumentException("Only jsonl export is supported");
        }
        double minConfidence = request.minConfidence() == null ? 0.0 : request.minConfidence();
        boolean includeWeak = request.includeWeakLabels() != null && request.includeWeakLabels();
        String outputPath = blank(request.outputPath()) ? "backend/2.0/training/classifier-dataset-v1.jsonl" : request.outputPath();
        Path path = trainingPath(outputPath);
        ArrayNode rows = rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT id, dataset_message_id, text, context_json::text AS context_json,
                           COALESCE(usefulness_label, label, 'POTENTIALLY_USEFUL') AS usefulness_label,
                           COALESCE(artifact_type_label, artifact_type, 'NONE') AS artifact_type_label,
                           message_role_labels_json::text AS message_role_labels_json,
                           COALESCE(domain_label, 'OTHER') AS domain_label,
                           evidence_labels_json::text AS evidence_labels_json,
                           COALESCE(actionability_label, decision, 'REFERENCE_ONLY') AS actionability_label,
                           source,
                           confidence,
                           split,
                           created_at
                    FROM training_examples
                    WHERE COALESCE(confidence, 1) >= ?
                      AND (? OR source IN ('HUMAN_LABEL','CORRECTED_PIPELINE','ACCEPTED_KNOWLEDGE_ITEM_AFTER_REVIEW','REJECTED_KNOWLEDGE_ITEM_AFTER_REVIEW'))
                    ORDER BY id
                ) t
                """, minConfidence, includeWeak);
        List<String> lines = new ArrayList<>();
        Map<String, Long> splitSummary = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            String split = exportSplit(row, request.splitStrategy());
            splitSummary.merge(split, 1L, Long::sum);
            ObjectNode item = json.createObjectNode();
            item.put("id", row.path("id").asLong());
            item.put("text", row.path("text").asText(""));
            item.set("context", parse(row.path("context_json").asText("{}")));
            ObjectNode labels = item.putObject("labels");
            labels.put("usefulness", row.path("usefulness_label").asText("POTENTIALLY_USEFUL"));
            labels.put("artifactType", row.path("artifact_type_label").asText("NONE"));
            labels.set("messageRoles", parseArray(row.path("message_role_labels_json").asText("[]")));
            labels.put("domain", row.path("domain_label").asText("OTHER"));
            labels.set("evidence", parseArray(row.path("evidence_labels_json").asText("[]")));
            labels.put("actionability", row.path("actionability_label").asText("REFERENCE_ONLY"));
            item.put("source", row.path("source").asText("UNKNOWN"));
            item.put("split", split);
            try {
                lines.add(json.writeValueAsString(item));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Cannot serialize training example " + row.path("id").asLong(), e);
            }
        }
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.write(path, lines);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write classifier dataset export: " + path, e);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", format);
        result.put("path", path.toString());
        result.put("count", lines.size());
        result.put("minConfidence", minConfidence);
        result.put("includeWeakLabels", includeWeak);
        result.put("splitStrategy", blank(request.splitStrategy()) ? "time_and_chat_grouped" : request.splitStrategy());
        result.put("splitSummary", splitSummary);
        return result;
    }

    public Map<String, Object> exportReviewedClassifierDataset() {
        return exportClassifierDataset(new TrainingDatasetExportRequest("jsonl", 0.0, false, "time_and_chat_grouped", "backend/2.0/training/classifier-reviewed-dataset-v1.jsonl"));
    }

    public Map<String, Object> exportWeakClassifierDataset() {
        return exportClassifierDataset(new TrainingDatasetExportRequest("jsonl", 0.0, true, "time_and_chat_grouped", "backend/2.0/training/classifier-weak-dataset-v1.jsonl"));
    }

    public Map<String, Object> exportStageDataset(ClassicalMlStageDatasetExport request) {
        String stage = blank(request.stage()) ? "meaning" : request.stage().trim().toLowerCase(Locale.ROOT);
        String format = blank(request.format()) ? "jsonl" : request.format().toLowerCase(Locale.ROOT);
        if (!format.equals("jsonl")) {
            throw new IllegalArgumentException("Only jsonl export is supported");
        }
        double minConfidence = request.minConfidence() == null ? 0.0 : request.minConfidence();
        boolean includeWeak = request.includeWeakLabels() != null && request.includeWeakLabels();
        String outputPath = blank(request.outputPath())
            ? "backend/2.0/training/classical-ml-" + stage + "-dataset-v1.jsonl"
            : request.outputPath();
        Path path = trainingPath(outputPath);
        ArrayNode rows = rows("""
                SELECT row_to_json(t)::jsonb::text
                FROM (
                    SELECT id, dataset_message_id, text, context_json::text AS context_json,
                           COALESCE(usefulness_label, label, 'POTENTIALLY_USEFUL') AS usefulness_label,
                           COALESCE(artifact_type_label, artifact_type, 'NONE') AS artifact_type_label,
                           message_role_labels_json::text AS message_role_labels_json,
                           COALESCE(domain_label, 'OTHER') AS domain_label,
                           evidence_labels_json::text AS evidence_labels_json,
                           COALESCE(actionability_label, decision, 'REFERENCE_ONLY') AS actionability_label,
                           COALESCE(context_need_label, 'INSUFFICIENT_CONTEXT') AS context_need_label,
                           COALESCE(decision_label, decision, 'NO_MATERIAL') AS decision_label,
                           source, confidence, split
                    FROM training_examples
                    WHERE COALESCE(confidence, 1) >= ?
                      AND (? OR source IN ('HUMAN_LABEL','CORRECTED_PIPELINE','ACCEPTED_KNOWLEDGE_ITEM_AFTER_REVIEW','REJECTED_KNOWLEDGE_ITEM_AFTER_REVIEW'))
                    ORDER BY id
                ) t
                """, minConfidence, includeWeak);
        List<String> lines = new ArrayList<>();
        Map<String, Long> splitSummary = new LinkedHashMap<>();
        Map<String, Long> labelSummary = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            String label = stageLabel(stage, row);
            if (blank(label)) continue;
            String split = exportSplit(row, request.splitStrategy());
            splitSummary.merge(split, 1L, Long::sum);
            labelSummary.merge(label, 1L, Long::sum);
            ObjectNode item = object();
            item.put("id", row.path("id").asLong());
            item.put("text", row.path("text").asText(""));
            item.set("context", parse(row.path("context_json").asText("{}")));
            item.put("stage", stage);
            item.put("label", label);
            item.put("source", row.path("source").asText("UNKNOWN"));
            item.put("split", split);
            try {
                lines.add(json.writeValueAsString(item));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Cannot serialize stage training example " + row.path("id").asLong(), e);
            }
        }
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.write(path, lines);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write stage dataset export: " + path, e);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", stage);
        result.put("path", path.toString());
        result.put("count", lines.size());
        result.put("labelSummary", labelSummary);
        result.put("splitSummary", splitSummary);
        result.put("includeWeakLabels", includeWeak);
        result.put("minConfidence", minConfidence);
        return result;
    }

    public Map<String, Object> evaluateClassicalMlStage(ClassicalMlStageEvaluationRequest request) {
        String stage = blank(request.stage()) ? "meaning" : request.stage().trim().toLowerCase(Locale.ROOT);
        Map<String, Object> exported = exportStageDataset(new ClassicalMlStageDatasetExport(
            stage, "jsonl", request.minConfidence(), request.includeWeakLabels(), request.splitStrategy(), "backend/2.0/training/classical-ml-" + stage + "-dataset-v1.jsonl"
        ));
        ObjectNode body = object();
        body.put("datasetVersion", request.datasetVersion() == null ? "local-stage-export-v1" : request.datasetVersion());
        body.put("featureVersion", request.featureVersion() == null ? FeatureVectorBuilder.FEATURE_VERSION : request.featureVersion());
        body.set("options", json.valueToTree(exported));
        JsonNode evaluation = worker.postJson("/classical-ml/evaluate/" + stage, body);
        Map<String, Object> result = new LinkedHashMap<>(exported);
        result.put("evaluation", json.convertValue(evaluation, Map.class));
        return result;
    }

    public Map<String, Object> trainClassicalMlStage(ClassicalMlStageEvaluationRequest request) {
        Map<String, Object> readiness = trainingDataReadiness();
        if (!isClassifierTrainingAllowed(readiness)) {
            throw new IllegalStateException("CLASSICAL_ML_TRAINING_GOVERNANCE_BLOCKED: human-reviewed benchmark is not ready");
        }
        String stage = blank(request.stage()) ? "meaning" : request.stage().trim().toLowerCase(Locale.ROOT);
        Map<String, Object> exported = exportStageDataset(new ClassicalMlStageDatasetExport(
            stage, "jsonl", request.minConfidence(), request.includeWeakLabels(), request.splitStrategy(), "backend/2.0/training/classical-ml-" + stage + "-dataset-v1.jsonl"
        ));
        ObjectNode body = object();
        body.put("datasetVersion", request.datasetVersion() == null ? "local-stage-export-v1" : request.datasetVersion());
        body.put("featureVersion", request.featureVersion() == null ? FeatureVectorBuilder.FEATURE_VERSION : request.featureVersion());
        body.set("options", json.valueToTree(exported));
        JsonNode training = worker.postJson("/classical-ml/train/" + stage, body);
        Map<String, Object> result = new LinkedHashMap<>(exported);
        result.put("training", json.convertValue(training, Map.class));
        return result;
    }

    public Map<String, Object> classicalMlModels() {
        return json.convertValue(worker.getJson("/classical-ml/models"), Map.class);
    }

    public Map<String, Object> classicalMlMetrics(String version) {
        String resolved = blank(version) ? "classical-ml-bootstrap-v1" : version.trim();
        return json.convertValue(worker.getJson("/classical-ml/metrics/" + resolved), Map.class);
    }

    public Map<String, Object> classicalMlPhaseReadiness() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stageDatasetCoverage", Map.of(
            "meaning", count("SELECT count(*) FROM training_examples WHERE message_role_labels_json IS NOT NULL"),
            "valueLevel", count("SELECT count(*) FROM training_examples WHERE usefulness_label IS NOT NULL OR label IS NOT NULL"),
            "usefulnessKind", count("SELECT count(*) FROM training_examples WHERE actionability_label IS NOT NULL"),
            "evidenceSufficiency", count("SELECT count(*) FROM training_examples WHERE context_need_label IS NOT NULL"),
            "materialRoute", count("SELECT count(*) FROM training_examples WHERE decision_label IS NOT NULL OR decision IS NOT NULL")
        ));
        result.put("structureCoverage", Map.of(
            "discussionSegments", count("SELECT count(*) FROM discussion_segments WHERE run_id IS NOT NULL"),
            "macroclusters", count("SELECT count(*) FROM macroclusters"),
            "topics", count("SELECT count(*) FROM discovered_topics")
        ));
        Map<String, Object> trainingReadiness = trainingDataReadiness();
        result.put("trainingGovernance", Map.of(
            "allowed", isClassifierTrainingAllowed(trainingReadiness),
            "humanReviewedExamples", trainingReadiness.getOrDefault("humanReviewedExamples", 0),
            "strongExamples", trainingReadiness.getOrDefault("strongExamples", 0),
            "minimumHumanReviewedNeeded", trainingReadiness.getOrDefault("minimumHumanReviewedNeeded", 500),
            "missing", trainingReadiness.getOrDefault("missingForClassifierV1", List.of())
        ));
        result.put("workerModels", classicalMlModels());
        return result;
    }

    static boolean isClassifierTrainingAllowed(Map<String, Object> readiness) {
        return readiness != null && Boolean.TRUE.equals(readiness.get("readyForClassifierV1"));
    }

    public Map<String, Object> exportActiveLearningBatch(long batchId) {
        List<Map<String, Object>> items = batchItems(batchId);
        Path path = trainingPath("backend/2.0/training/active-learning-batch-" + batchId + ".jsonl");
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> item : items) {
            try {
                lines.add(json.writeValueAsString(item));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Cannot serialize active learning item", e);
            }
        }
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.write(path, lines);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write active learning batch export: " + path, e);
        }
        return Map.of("batchId", batchId, "path", path.toString(), "count", lines.size());
    }

    @Transactional
    public Map<String, Object> addLabelingEvent(long itemId, LabelEvent request) {
        Map<String, Object> item = labelingItem(itemId);
        String reviewAction = normalizedReviewAction(request.reviewAction(), request.eventType());
        String status = reviewAction.equals("SKIP") ? "SKIPPED" : "LABELED";
        long eventId = JdbcIds.insertReturningId(jdbc, """
                INSERT INTO labeling_events (labeling_item_id, run_id, dataset_message_id, user_id, event_type, old_value_json, new_value_json, comment, review_action)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                RETURNING id
                """, itemId, item.get("run_id"), item.get("dataset_message_id"), blank(request.userId()) ? "local" : request.userId(),
                blank(request.eventType()) ? "QUICK_REVIEW" : request.eventType(), blank(request.oldValueJson()) ? "{}" : request.oldValueJson(),
                blank(request.newValueJson()) ? "{}" : request.newValueJson(), request.comment(), reviewAction);
        jdbc.update("UPDATE labeling_items SET status = ?, updated_at = now() WHERE id = ?", status, itemId);
        if (reviewAction.equals("SKIP")) {
            return labelingItem(itemId);
        }
        String text = String.valueOf(item.getOrDefault("text_snapshot", ""));
        String context = write(enrichReviewFeedbackContext(item, parse(String.valueOf(item.getOrDefault("context_snapshot_json", "{}")))));
        ObjectNode actionAxes = axesFromReviewAction(reviewAction, request.label(), request.artifactType(), request.decision(), text, context);
        String source = strongSourceForReviewAction(reviewAction);
        Long trainingExampleId = JdbcIds.insertReturningId(jdbc, """
                INSERT INTO training_examples (
                    run_id, source, dataset_message_id, text, context_json, label, artifact_type, decision, confidence, split,
                    usefulness_label, artifact_type_label, message_role_labels_json, domain_label, evidence_labels_json, actionability_label,
                    decision_label, context_need_label, labeling_event_id, review_action, reviewed_at
                )
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, 'UNASSIGNED', ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, now())
                RETURNING id
                """, item.get("run_id"), source, item.get("dataset_message_id"), text, context,
                coalesce(request.label(), actionAxes.path("label").asText(null)),
                coalesce(request.artifactType(), actionAxes.path("artifactType").asText(null)),
                coalesce(request.decision(), actionAxes.path("decision").asText(null)), item.get("confidence"),
                actionAxes.path("usefulness").asText(), actionAxes.path("artifactType").asText(), write(actionAxes.path("messageRoles")), actionAxes.path("domain").asText(),
                write(actionAxes.path("evidence")), actionAxes.path("actionability").asText(), actionAxes.path("decision").asText(),
                actionAxes.path("contextNeed").asText(), eventId, reviewAction);
        jdbc.update("UPDATE labeling_events SET training_example_id = ? WHERE id = ?", trainingExampleId, eventId);
        if (item.get("knowledge_item_id") != null && !String.valueOf(item.get("knowledge_item_id")).equals("null")) {
            upsertHumanKnowledgeItemReview(((Number) item.get("knowledge_item_id")).longValue(), item, reviewAction);
        }
        return labelingItem(itemId);
    }

    private String exportSplit(JsonNode row, String splitStrategy) {
        String current = row.path("split").asText("UNASSIGNED").trim();
        if (!blank(current) && !current.equalsIgnoreCase("UNASSIGNED")) {
            return current.toUpperCase(Locale.ROOT);
        }
        String strategy = blank(splitStrategy) ? "time_and_chat_grouped" : splitStrategy;
        String key = strategy + ":" + row.path("dataset_message_id").asText(row.path("id").asText());
        int bucket = Math.floorMod(Hashing.sha256(key).hashCode(), 10);
        if (bucket == 0) return "TEST";
        if (bucket == 1) return "VALIDATION";
        return "TRAIN";
    }

    private String stageLabel(String stage, JsonNode row) {
        return switch (stage) {
            case "meaning" -> firstRoleLabel(row.path("message_role_labels_json").asText("[]"));
            case "value_level", "valuelevel", "value" -> valueLevelLabel(row.path("usefulness_label").asText("POTENTIALLY_USEFUL"));
            case "usefulness_kind", "usefulnesskind", "usefulness" -> usefulnessKindLabel(row.path("actionability_label").asText("REFERENCE_ONLY"));
            case "evidence_sufficiency", "evidencesufficiency", "evidence" -> row.path("context_need_label").asText("INSUFFICIENT_CONTEXT");
            case "material_route", "materialroute", "route" -> row.path("decision_label").asText("NO_MATERIAL");
            default -> null;
        };
    }

    private String firstRoleLabel(String jsonArray) {
        JsonNode roles = parseArray(jsonArray);
        return roles.isArray() && !roles.isEmpty() ? roles.get(0).asText("UNKNOWN") : "UNKNOWN";
    }

    private String valueLevelLabel(String usefulness) {
        return switch (usefulness) {
            case "NOISE", "NOT_USEFUL", "NOISE_OR_CHAT" -> "GARBAGE";
            case "SIGNAL_ONLY", "AWARENESS_ONLY" -> "AWARENESS_SIGNAL";
            case "CONTEXT_ONLY", "CONTEXT_REQUIRED" -> "CONTEXT_SIGNAL";
            case "USEFUL", "POTENTIALLY_USEFUL", "HIGH_VALUE" -> "MATERIAL_CANDIDATE";
            default -> "NOT_GARBAGE_NO_MATERIAL";
        };
    }

    private String usefulnessKindLabel(String actionability) {
        return switch (actionability) {
            case "STEP_BY_STEP", "ACTIONABLE" -> "ACTIONABLE";
            case "DIAGNOSIS", "TROUBLESHOOTING" -> "DIAGNOSTIC";
            case "REFERENCE_ONLY", "REFERENCE" -> "REFERENCE";
            case "WARNING", "RISK" -> "WARNING";
            case "AWARENESS" -> "AWARENESS";
            case "ANALYSIS" -> "ANALYTICAL";
            case "EDUCATIONAL" -> "EDUCATIONAL";
            default -> "CONTEXTUAL";
        };
    }

    private ObjectNode inferTrainingAxes(String label, String artifactType, String decision, String text, String contextJson) {
        String normalizedLabel = blank(label) ? "" : label.toUpperCase(Locale.ROOT);
        String normalizedDecision = blank(decision) ? "" : decision.toUpperCase(Locale.ROOT);
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        ObjectNode axes = object();
        if (normalizedLabel.equals("NOISE_OR_CHAT") || normalizedDecision.contains("NOISE")) {
            axes.put("usefulness", "NOISE");
        } else if (normalizedDecision.contains("REJECT") || normalizedDecision.contains("NOT_USEFUL")) {
            axes.put("usefulness", "NOT_USEFUL");
        } else if (!blank(label) || !blank(artifactType)) {
            axes.put("usefulness", "USEFUL");
        } else {
            axes.put("usefulness", "POTENTIALLY_USEFUL");
        }
        axes.put("artifactType", artifactTypeFrom(label, artifactType));
        axes.set("messageRoles", messageRolesFrom(label, lower));
        axes.put("domain", domainFrom(lower + " " + contextJson));
        axes.set("evidence", evidenceFrom(label, lower));
        axes.put("actionability", actionabilityFrom(label, normalizedDecision));
        return axes;
    }

    private String normalizedReviewAction(String reviewAction, String fallback) {
        String value = coalesce(reviewAction, fallback, "USEFUL").trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (value.equals("NOT_USEFUL") || value.equals("NOISE") || value.equals("WRONG_CLASS") || value.equals("GOOD_MATERIAL")
                || value.equals("NEEDS_EDIT") || value.equals("BAD_SOURCE_SUPPORT") || value.equals("DUPLICATE")
                || value.equals("UNSAFE") || value.equals("NEEDS_CONTEXT") || value.equals("SKIP")) return value;
        return "USEFUL";
    }

    private String strongSourceForReviewAction(String reviewAction) {
        return switch (reviewAction) {
            case "GOOD_MATERIAL" -> "ACCEPTED_KNOWLEDGE_ITEM_AFTER_REVIEW";
            case "BAD_SOURCE_SUPPORT", "DUPLICATE", "UNSAFE" -> "REJECTED_KNOWLEDGE_ITEM_AFTER_REVIEW";
            case "WRONG_CLASS", "NEEDS_EDIT" -> "CORRECTED_PIPELINE";
            default -> "HUMAN_LABEL";
        };
    }

    private ObjectNode axesFromReviewAction(String reviewAction, String label, String artifactType, String decision, String text, String contextJson) {
        ObjectNode axes = inferTrainingAxes(label, artifactType, decision, text, contextJson);
        switch (reviewAction) {
            case "NOISE" -> {
                axes.put("label", "NOISE");
                axes.put("usefulness", "NOISE");
                axes.put("decision", "SUPPRESS");
                axes.put("artifactType", "NONE");
                axes.set("messageRoles", array("CHAT"));
                axes.set("evidence", array("LOW_SIGNAL"));
                axes.put("actionability", "NOT_ACTIONABLE");
                axes.put("contextNeed", "STANDALONE");
            }
            case "NOT_USEFUL" -> {
                axes.put("label", "NOT_USEFUL");
                axes.put("usefulness", "NOT_USEFUL");
                axes.put("decision", "REJECT");
                axes.put("artifactType", "NONE");
                axes.put("actionability", "NOT_ACTIONABLE");
                axes.put("contextNeed", "STANDALONE");
            }
            case "USEFUL" -> {
                axes.put("label", "USEFUL");
                axes.put("usefulness", "USEFUL");
                axes.put("decision", blank(decision) ? "CANDIDATE" : decision.toUpperCase(Locale.ROOT));
                axes.put("contextNeed", "STANDALONE");
            }
            case "GOOD_MATERIAL" -> {
                axes.put("label", "USEFUL");
                axes.put("usefulness", "USEFUL");
                axes.put("decision", "ACCEPT");
                axes.set("evidence", array("HAS_SOURCE", "HAS_DETAILS"));
                axes.put("actionability", "ACTIONABLE");
                axes.put("contextNeed", "STANDALONE");
            }
            case "BAD_SOURCE_SUPPORT" -> {
                axes.put("label", "BAD_SOURCE_SUPPORT");
                axes.put("usefulness", "NOT_USEFUL");
                axes.put("decision", "REJECT");
                axes.set("evidence", array("UNSUPPORTED_CLAIM"));
                axes.put("actionability", "NOT_ACTIONABLE");
                axes.put("contextNeed", "NEEDS_SOURCE_REVIEW");
            }
            case "DUPLICATE" -> {
                axes.put("label", "DUPLICATE");
                axes.put("usefulness", "NOT_USEFUL");
                axes.put("decision", "SUPPRESS_DUPLICATE");
                axes.set("evidence", array("DUPLICATE"));
                axes.put("actionability", "NOT_ACTIONABLE");
                axes.put("contextNeed", "STANDALONE");
            }
            case "UNSAFE" -> {
                axes.put("label", "UNSAFE");
                axes.put("usefulness", "NOT_USEFUL");
                axes.put("decision", "REJECT_UNSAFE");
                axes.put("artifactType", "RISK_NOTE");
                axes.set("messageRoles", array("RISK"));
                axes.set("evidence", array("SAFETY_RISK"));
                axes.put("actionability", "NOT_ACTIONABLE");
                axes.put("contextNeed", "NEEDS_POLICY_REVIEW");
            }
            case "NEEDS_CONTEXT" -> {
                axes.put("label", "NEEDS_CONTEXT");
                axes.put("usefulness", "POTENTIALLY_USEFUL");
                axes.put("decision", "NEEDS_CONTEXT");
                axes.put("contextNeed", "NEEDS_THREAD_CONTEXT");
            }
            case "WRONG_CLASS" -> {
                axes.put("label", blank(label) ? "CORRECTED_CLASS_NEEDED" : label.toUpperCase(Locale.ROOT));
                axes.put("decision", "CORRECTED");
                axes.put("contextNeed", "STANDALONE");
            }
            case "NEEDS_EDIT" -> {
                axes.put("label", "NEEDS_EDIT");
                axes.put("decision", "EDIT_BEFORE_USE");
                axes.put("contextNeed", "STANDALONE");
            }
            default -> axes.put("contextNeed", "STANDALONE");
        }
        return axes;
    }

    private void upsertHumanKnowledgeItemReview(long knowledgeItemId, Map<String, Object> item, String reviewAction) {
        String verdict = switch (reviewAction) {
            case "GOOD_MATERIAL" -> "PUBLISHABLE";
            case "NEEDS_EDIT" -> "NEEDS_EDIT";
            case "BAD_SOURCE_SUPPORT" -> "BAD_SOURCE_SUPPORT";
            case "DUPLICATE" -> "DUPLICATE";
            case "UNSAFE" -> "UNSAFE";
            default -> "NEEDS_EDIT";
        };
        ObjectNode reasons = object("reviewAction", reviewAction);
        jdbc.update("""
                INSERT INTO knowledge_item_reviews (
                    knowledge_item_id, run_id, reviewer_type, source_supported_score, hallucination_risk_score,
                    publishability_score, commercial_value_score, actionability_score, specificity_score, duplicate_risk_score,
                    artifact_type_correct, title_quality_score, body_quality_score, needs_human_review, verdict, review_reasons_json
                )
                VALUES (?, ?, 'HUMAN', ?, ?, ?, 0.8, 0.8, 0.8, ?, true, 0.8, 0.8, false, ?, ?::jsonb)
                ON CONFLICT (knowledge_item_id, reviewer_type) DO UPDATE SET
                    source_supported_score = EXCLUDED.source_supported_score,
                    hallucination_risk_score = EXCLUDED.hallucination_risk_score,
                    publishability_score = EXCLUDED.publishability_score,
                    duplicate_risk_score = EXCLUDED.duplicate_risk_score,
                    needs_human_review = false,
                    verdict = EXCLUDED.verdict,
                    review_reasons_json = EXCLUDED.review_reasons_json,
                    updated_at = now()
                """, knowledgeItemId, item.get("run_id"), verdict.equals("BAD_SOURCE_SUPPORT") ? 0.1 : 0.9,
                verdict.equals("BAD_SOURCE_SUPPORT") ? 0.9 : 0.1, verdict.equals("PUBLISHABLE") ? 0.9 : 0.2,
                verdict.equals("DUPLICATE") ? 0.9 : 0.1, verdict, write(reasons));
    }

    private String artifactTypeFrom(String label, String artifactType) {
        if (!blank(artifactType)) return artifactType.toUpperCase(Locale.ROOT);
        String normalized = blank(label) ? "" : label.toUpperCase(Locale.ROOT);
        if (normalized.contains("ERROR") || normalized.contains("TROUBLESHOOTING")) return "TROUBLESHOOTING_NOTE";
        if (normalized.contains("COMPARISON")) return "COMPARISON_INSIGHT";
        if (normalized.contains("PRICING") || normalized.contains("ACCESS")) return "PRICE_ACCESS_CARD";
        if (normalized.contains("RESOURCE")) return "RESOURCE_CARD";
        if (normalized.contains("RISK") || normalized.contains("SECURITY")) return "RISK_NOTE";
        if (normalized.contains("NEWS") || normalized.contains("RELEASE") || normalized.contains("MARKET")) return "NEWS_SIGNAL";
        if (normalized.contains("GUIDE") || normalized.contains("CONFIG") || normalized.contains("PROMPT")) return "GUIDE";
        return "NOTE";
    }

    private ArrayNode messageRolesFrom(String label, String lower) {
        ArrayNode roles = json.createArrayNode();
        String normalized = blank(label) ? "" : label.toUpperCase(Locale.ROOT);
        if (normalized.contains("QUESTION") || lower.contains("?")) roles.add("QUESTION");
        if (normalized.contains("ERROR")) roles.add("ERROR_LOG");
        if (normalized.contains("TROUBLESHOOTING") || lower.contains("fix") || lower.contains("solution")) roles.add("FIX");
        if (normalized.contains("RELEASE") || normalized.contains("NEWS")) roles.add("ANNOUNCEMENT");
        if (normalized.contains("PRICING") || normalized.contains("ACCESS")) roles.add("PRICE_OR_ACCESS");
        if (normalized.contains("RESOURCE")) roles.add("RESOURCE_LINK");
        if (normalized.contains("PROMO")) roles.add("PROMO");
        if (normalized.contains("RISK") || normalized.contains("SECURITY")) roles.add("RISK");
        if (normalized.contains("CONFIG") || normalized.contains("API")) roles.add("CODE_OR_CONFIG");
        if (normalized.contains("COMPARISON")) roles.add("COMPARISON");
        if (roles.isEmpty()) roles.add(normalized.contains("NOISE") ? "CHAT" : "ANSWER");
        return roles;
    }

    private ArrayNode evidenceFrom(String label, String lower) {
        ArrayNode evidence = json.createArrayNode();
        String normalized = blank(label) ? "" : label.toUpperCase(Locale.ROOT);
        if (lower.contains("http://") || lower.contains("https://") || normalized.contains("RESOURCE")) evidence.add("HAS_SOURCE");
        if (lower.length() > 120) evidence.add("HAS_DETAILS");
        if (lower.matches(".*\\d+.*") || normalized.contains("PRICING")) evidence.add("HAS_NUMBERS");
        if (lower.contains("1.") || lower.contains("step") || normalized.contains("GUIDE")) evidence.add("HAS_STEPS");
        if (normalized.contains("ERROR") || lower.contains("exception") || lower.contains("traceback")) evidence.add("HAS_ERROR_SIGNATURE");
        if (evidence.isEmpty()) evidence.add("UNSUPPORTED_CLAIM");
        return evidence;
    }

    private String domainFrom(String lower) {
        if (lower.matches(".*\\b(gpt|claude|anthropic|model|llm|prompt|agent|bge|bert)\\b.*")) return "AI";
        if (lower.matches(".*\\b(crypto|btc|eth|solana|token|airdrop|wallet)\\b.*")) return "CRYPTO";
        if (lower.matches(".*\\b(construction|build|contractor|бетон|строй|ремонт)\\b.*")) return "CONSTRUCTION";
        if (lower.matches(".*\\b(crm|sales|lead|customer|business|pricing|invoice)\\b.*")) return "BUSINESS";
        if (lower.matches(".*\\b(api|docker|postgres|spring|react|vite|github|repo|config)\\b.*")) return "DEV_TOOLS";
        return "OTHER";
    }

    private String actionabilityFrom(String label, String decision) {
        String normalized = blank(label) ? "" : label.toUpperCase(Locale.ROOT);
        if (decision.contains("REJECT") || normalized.contains("NOISE") || normalized.contains("UNSUPPORTED")) return "NOT_ACTIONABLE";
        if (normalized.contains("NEWS") || normalized.contains("MARKET")) return "FYI_ONLY";
        if (normalized.contains("RESOURCE") || normalized.contains("RISK")) return "REFERENCE_ONLY";
        return "ACTIONABLE";
    }

    private Map<String, Integer> activeLearningPlan(int targetSize) {
        Map<String, Integer> plan = new LinkedHashMap<>();
        plan.put("suspected_false_noise", Math.max(1, (int) Math.round(targetSize * 0.20)));
        plan.put("low_confidence", Math.max(1, (int) Math.round(targetSize * 0.20)));
        plan.put("useful_unclustered", Math.max(1, (int) Math.round(targetSize * 0.15)));
        plan.put("question_answer", Math.max(1, (int) Math.round(targetSize * 0.15)));
        plan.put("price_access", Math.max(1, (int) Math.round(targetSize * 0.10)));
        plan.put("error_fix", Math.max(1, (int) Math.round(targetSize * 0.10)));
        plan.put("resource_links", Math.max(1, (int) Math.round(targetSize * 0.05)));
        plan.put("generated_item_source_review", Math.max(1, targetSize - plan.values().stream().mapToInt(Integer::intValue).sum()));
        return plan;
    }

    private Map<String, Integer> dailyReviewPlan(int targetSize) {
        Map<String, Integer> plan = new LinkedHashMap<>();
        plan.put("suspected_false_noise", Math.max(1, (int) Math.round(targetSize * 0.20)));
        plan.put("low_confidence", Math.max(1, (int) Math.round(targetSize * 0.15)));
        plan.put("useful_unclustered", Math.max(1, (int) Math.round(targetSize * 0.15)));
        plan.put("generated_knowledge_item_review", Math.max(1, (int) Math.round(targetSize * 0.15)));
        plan.put("rare_classes", Math.max(1, (int) Math.round(targetSize * 0.10)));
        plan.put("price_access", Math.max(1, (int) Math.round(targetSize * 0.10)));
        plan.put("error_fix", Math.max(1, (int) Math.round(targetSize * 0.10)));
        plan.put("duplicate_source_support", Math.max(1, targetSize - plan.values().stream().mapToInt(Integer::intValue).sum()));
        return plan;
    }

    private long latestRunId(int lastHours) {
        Long runId = jdbc.queryForObject("""
                SELECT id FROM replay_runs
                WHERE started_at >= now() - (? || ' hours')::interval
                ORDER BY started_at DESC, id DESC
                LIMIT 1
                """, Long.class, lastHours);
        if (runId != null) return runId;
        return jdbc.queryForObject("SELECT id FROM replay_runs ORDER BY started_at DESC, id DESC LIMIT 1", Long.class);
    }

    private ArrayNode priorityReasons(Map<String, Integer> counts) {
        ArrayNode reasons = json.createArrayNode();
        counts.forEach((bucket, count) -> {
            if (count > 0) reasons.add(bucket + ": selected " + count + " highest-impact review candidates");
        });
        return reasons;
    }

    private List<Long> dailyCandidateLabelingIds(int lastHours, String bucket, int limit, Set<Long> excluded) {
        String notIn = excluded.isEmpty() ? "" : " AND li.id NOT IN (" + placeholders(excluded.size()) + ")";
        String base = """
                SELECT li.id FROM labeling_items li
                JOIN replay_runs rr ON rr.id = li.run_id
                LEFT JOIN message_intelligence mi ON mi.run_id = li.run_id AND mi.dataset_message_id = li.dataset_message_id
                LEFT JOIN message_classifications mc ON mc.run_id = li.run_id AND mc.dataset_message_id = li.dataset_message_id
                LEFT JOIN knowledge_item_reviews kir ON kir.knowledge_item_id = li.knowledge_item_id
                WHERE li.status IN ('PENDING','DISAGREEMENT','NEEDS_REVIEW')
                  AND rr.started_at >= now() - (? || ' hours')::interval
                  AND li.batch_id IS NULL
                """;
        String predicate = switch (bucket) {
            case "suspected_false_noise" -> " AND (mc.top_label = 'NOISE_OR_CHAT' OR li.suggested_label = 'NOISE_OR_CHAT') AND COALESCE(mi.hard_signal,false) = true";
            case "low_confidence" -> " AND COALESCE(mc.confidence, li.confidence, 1) < 0.60";
            case "useful_unclustered" -> " AND COALESCE(mi.hard_signal,false) = true AND li.microcluster_id IS NULL AND li.macrocluster_id IS NULL";
            case "generated_knowledge_item_review" -> " AND li.knowledge_item_id IS NOT NULL";
            case "rare_classes" -> " AND (li.suggested_labels_json::text ILIKE '%PRICE_OR_ACCESS%' OR li.suggested_labels_json::text ILIKE '%RESOURCE%' OR li.suggested_labels_json::text ILIKE '%ERROR%' OR li.suggested_labels_json::text ILIKE '%RISK%' OR li.suggested_labels_json::text ILIKE '%COMPARISON%')";
            case "price_access" -> " AND (li.suggested_labels_json::text ILIKE '%PRICING%' OR li.suggested_labels_json::text ILIKE '%ACCESS%' OR li.text_snapshot ILIKE '%price%' OR li.text_snapshot ILIKE '%доступ%')";
            case "error_fix" -> " AND (li.suggested_labels_json::text ILIKE '%ERROR%' OR li.text_snapshot ILIKE '%error%' OR li.text_snapshot ILIKE '%exception%' OR li.text_snapshot ILIKE '%ошиб%')";
            case "duplicate_source_support" -> " AND (li.text_snapshot ILIKE '%duplicate%' OR li.context_snapshot_json::text ILIKE '%duplicate%' OR kir.verdict IN ('BAD_SOURCE_SUPPORT','DUPLICATE'))";
            default -> "";
        };
        List<Object> args = new ArrayList<>();
        args.add(lastHours);
        args.addAll(excluded);
        args.add(limit);
        String sql = base + predicate + notIn + " ORDER BY li.priority DESC, COALESCE(mc.confidence, li.confidence) ASC NULLS FIRST, li.id LIMIT ?";
        List<Long> ids = jdbc.queryForList(sql, Long.class, args.toArray());
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE labeling_items SET batch_bucket = ? WHERE id IN (" + placeholders(ids.size()) + ")", prepend(bucket, ids).toArray());
        }
        return ids;
    }

    private List<Long> candidateLabelingIds(long runId, String bucket, int limit, Set<Long> excluded) {
        String notIn = excluded.isEmpty() ? "" : " AND li.id NOT IN (" + placeholders(excluded.size()) + ")";
        String base = """
                SELECT li.id FROM labeling_items li
                LEFT JOIN message_intelligence mi ON mi.run_id = li.run_id AND mi.dataset_message_id = li.dataset_message_id
                LEFT JOIN message_classifications mc ON mc.run_id = li.run_id AND mc.dataset_message_id = li.dataset_message_id
                WHERE li.run_id = ? AND li.status IN ('PENDING','DISAGREEMENT','NEEDS_REVIEW')
                """;
        String predicate = switch (bucket) {
            case "suspected_false_noise" -> " AND (mc.top_label = 'NOISE_OR_CHAT' OR li.suggested_label = 'NOISE_OR_CHAT') AND COALESCE(mi.hard_signal,false) = true";
            case "low_confidence" -> " AND COALESCE(mc.confidence, li.confidence, 1) < 0.60";
            case "useful_unclustered" -> " AND COALESCE(mi.hard_signal,false) = true AND li.microcluster_id IS NULL AND li.macrocluster_id IS NULL";
            case "question_answer" -> " AND (li.suggested_labels_json::text ILIKE '%QUESTION%' OR li.text_snapshot LIKE '%?%')";
            case "price_access" -> " AND (li.suggested_labels_json::text ILIKE '%PRICING%' OR li.suggested_labels_json::text ILIKE '%ACCESS%' OR li.text_snapshot ILIKE '%price%')";
            case "error_fix" -> " AND (li.suggested_labels_json::text ILIKE '%ERROR%' OR li.text_snapshot ILIKE '%error%' OR li.text_snapshot ILIKE '%exception%')";
            case "resource_links" -> " AND (li.suggested_labels_json::text ILIKE '%RESOURCE%' OR li.text_snapshot ILIKE '%http%')";
            case "generated_item_source_review" -> " AND li.knowledge_item_id IS NOT NULL";
            default -> "";
        };
        List<Object> args = new ArrayList<>();
        args.add(runId);
        args.addAll(excluded);
        args.add(limit);
        String sql = base + predicate + notIn + " ORDER BY li.priority DESC, COALESCE(mc.confidence, li.confidence) ASC NULLS FIRST, li.id LIMIT ?";
        List<Long> ids = jdbc.queryForList(sql, Long.class, args.toArray());
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE labeling_items SET batch_bucket = ? WHERE id IN (" + placeholders(ids.size()) + ")", prepend(bucket, ids).toArray());
        }
        return ids;
    }

    private List<Map<String, Object>> batchItems(long batchId) {
        return jdbc.queryForList("""
                SELECT id, batch_id, batch_bucket, priority, item_type, text_snapshot, context_snapshot_json::text AS context_snapshot_json,
                       suggested_label, suggested_labels_json::text AS suggested_labels_json, suggested_artifact_type,
                       suggested_decision, confidence, status, dataset_message_id, microcluster_id, macrocluster_id,
                       knowledge_item_id, usefulness_label, decision_label, artifact_type_label,
                       message_role_labels_json::text AS message_role_labels_json, evidence_labels_json::text AS evidence_labels_json,
                       actionability_label, context_need_label
                FROM labeling_items
                WHERE batch_id = ?
                ORDER BY priority DESC, confidence ASC NULLS FIRST, id
                """, batchId);
    }

    private ObjectNode objectFrom(Map<String, Integer> values) {
        ObjectNode node = object();
        values.forEach(node::put);
        return node;
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private Path trainingPath(String outputPath) {
        String normalized = outputPath.replace('\\', '/');
        if (normalized.startsWith("backend/2.0/")) {
            normalized = normalized.substring("backend/2.0/".length());
        }
        return Path.of(normalized).normalize();
    }

    private List<Object> prepend(Object first, Collection<?> rest) {
        List<Object> args = new ArrayList<>();
        args.add(first);
        args.addAll(rest);
        return args;
    }

    private List<Object> prepend(Object first, Object second, Collection<?> rest) {
        List<Object> args = new ArrayList<>();
        args.add(first);
        args.add(second);
        args.addAll(rest);
        return args;
    }

    private ObjectNode features(Message m, String text) {
        ObjectNode f = json.createObjectNode();
        ArrayNode links = f.putArray("links");
        Set<String> seenLinks = new LinkedHashSet<>();
        URL.matcher(text).results().forEach(match -> addFeatureLink(links, seenLinks, match.group()));
        int hidden = appendEntityLinks(parseArray(m.entitiesJson()), links, seenLinks);
        hidden += appendEntityLinks(rawJsonEntities(m.rawJson()), links, seenLinks);
        ArrayNode domains = f.putArray("domains");
        for (JsonNode link : links) { var matcher = DOMAIN.matcher(link.asText()); if (matcher.find()) domains.add(matcher.group(1).toLowerCase(Locale.ROOT)); }
        f.put("linkCount", links.size()); f.put("hiddenLinkCount", hidden); f.put("domainCount", domains.size());
        f.put("hasCode", CODE.matcher(text).find()); f.put("hasError", ERROR.matcher(text).find()); f.put("hasPriceOrAccess", PRICE.matcher(text).find()); f.put("hasTool", TOOL.matcher(text).find());
        f.put("isQuestion", text.contains("?") || text.toLowerCase(Locale.ROOT).matches(".*\\b(как|how|why|почему|что делать)\\b.*"));
        f.put("isAnswerLike", text.toLowerCase(Locale.ROOT).matches(".*\\b(fix|решение|попробуй|нужно|use|install|run)\\b.*"));
        f.put("isNoiseLike", text.length() < 18 || text.toLowerCase(Locale.ROOT).matches("^(ok|спасибо|thanks|\\+1|лол|ага)[!.]*$"));
        f.set("code", object("hasCode", f.path("hasCode").asBoolean())); f.set("errors", object("hasError", f.path("hasError").asBoolean())); f.set("prices", object("hasPriceOrAccess", f.path("hasPriceOrAccess").asBoolean())); f.set("questionAnswer", object("question", f.path("isQuestion").asBoolean(), "answer", f.path("isAnswerLike").asBoolean()));
        return f;
    }

    private boolean addFeatureLink(ArrayNode links, Set<String> seenLinks, String url) {
        if (url == null || url.isBlank()) return false;
        String trimmed = url.trim();
        if (!seenLinks.add(trimmed)) return false;
        links.add(trimmed);
        return true;
    }

    private int appendEntityLinks(JsonNode entities, ArrayNode links, Set<String> seenLinks) {
        if (entities == null || !entities.isArray()) return 0;
        int hidden = 0;
        for (JsonNode entity : entities) if (addFeatureLink(links, seenLinks, entityUrl(entity))) hidden++;
        return hidden;
    }

    private JsonNode rawJsonEntities(String rawJson) {
        JsonNode raw = parse(rawJson);
        ArrayNode result = json.createArrayNode();
        appendArray(result, raw.path("entities"));
        appendArray(result, raw.path("captionEntities"));
        appendArray(result, raw.path("content").path("text").path("entities"));
        appendArray(result, raw.path("content").path("caption").path("entities"));
        return result;
    }

    private void appendArray(ArrayNode target, JsonNode source) {
        if (source != null && source.isArray()) source.forEach(target::add);
    }

    private String entityUrl(JsonNode entity) {
        String url = entity.path("url").asText(null);
        if (url == null) url = entity.path("textUrl").path("url").asText(null);
        if (url == null) url = entity.path("type").path("url").asText(null);
        return url;
    }

    private String textWithFeatureLinks(String text, JsonNode features) {
        String value = text == null ? "" : text;
        JsonNode links = features == null ? null : features.path("links");
        if (links == null || !links.isArray() || links.isEmpty()) return value;
        StringBuilder result = new StringBuilder(value);
        for (JsonNode link : links) {
            String url = link.asText("").trim();
            if (!url.isBlank() && !value.contains(url)) result.append(' ').append(url);
        }
        return result.toString().trim();
    }

    private ArrayNode labels(JsonNode f, String decision) { ArrayNode a = json.createArrayNode(); if (f.path("hasError").asBoolean()) a.add("ERROR_LOG_WITH_FIX"); if (f.path("hasCode").asBoolean()) a.add("API_OR_CONFIG_SNIPPET"); if (f.path("hasPriceOrAccess").asBoolean()) a.add("PRICING_OR_ACCESS_SIGNAL"); if (f.path("linkCount").asInt() > 0) a.add("RESOURCE_LINK_COLLECTION"); if (f.path("isQuestion").asBoolean()) a.add("QUESTION_WITH_VALUABLE_ANSWER"); if (a.isEmpty() && decision.equals("SUPPRESS")) a.add("NOISE_OR_CHAT"); return a; }
    private String prompt(Cluster c, List<Intel> intel, String type) { return prompt(c, intel, type, null); }
    private String prompt(Cluster c, List<Intel> intel, String type, RouteIntelligenceDecision routeDecision) { ArrayNode evidence = json.createArrayNode(); Set<Long> ids = new HashSet<>(c.members()); for (Intel i : intel) if (ids.contains(i.message().id())) evidence.addObject().put("datasetMessageId", i.message().id()).put("text", i.normalizedText()); return "Return strict JSON. Type=" + type + ". Required artifactType=" + c.artifactType() + ". For judge use {decision,artifactType,title,summary,evidenceIds,confidence,safetyNotes}. For generation use {artifactType,title,summary,body,sources}; artifactType MUST equal required artifactType. " + generationStyleInstructions() + " Cluster=" + c.title() + routePromptHint(routeDecision) + " evidence=" + write(evidence); }
    private long knowledge(long runId, Cluster c, JsonNode g, double confidence) { JsonNode material = materialWithRequiredArtifactType(g, c.artifactType()); long id = JdbcIds.insertReturningId(jdbc, "INSERT INTO knowledge_items (run_id, source_cluster_type, source_cluster_id, artifact_type, vertical, title, summary, body_json, knowledge_value_score, status) VALUES (?, 'MACRO', ?, ?, 'telegram-intelligence', ?, ?, ?::jsonb, ?, 'DRAFT')", runId, c.id(), material.path("artifactType").asText("NOTE"), material.path("title").asText(c.title()), material.path("summary").asText(""), write(material), confidence); for (Long member : materialSourceMembers(runId, c).stream().limit(5).toList()) jdbc.update("INSERT INTO knowledge_item_sources (knowledge_item_id, dataset_message_id, source_role, quote, confidence) VALUES (?, ?, 'EVIDENCE', '', ?)", id, member, confidence); assignKnowledgeItemTopics(id, material.path("artifactType").asText(""), c.members(), confidence); return id; }

    private ObjectNode lightweightMaterial(Cluster c, List<Intel> intel, JsonNode judge) {
        JsonNode payload = judgePayload(judge);
        ArrayNode evidence = json.createArrayNode();
        Set<Long> ids = new HashSet<>(c.members());
        for (Intel i : intel) if (ids.contains(i.message().id())) evidence.addObject().put("datasetMessageId", i.message().id()).put("text", i.normalizedText());
        String summary = payload.path("summary").asText("");
        ObjectNode g = object();
        g.put("artifactType", lightweightArtifactType(payload, evidence));
        g.put("title", payload.path("title").asText(c.title()));
        g.put("summary", summary);
        g.put("body", lightweightBody(summary, evidence));
        g.set("sources", evidence);
        g.put("generationMode", "LIGHTWEIGHT_FALLBACK");
        return g;
    }

    private ObjectNode lightweightMaterial(SingleMessageCandidate sc, Intel match, JsonNode judge) {
        JsonNode payload = judgePayload(judge);
        ArrayNode evidence = json.createArrayNode();
        evidence.addObject().put("datasetMessageId", match.message().id()).put("text", match.normalizedText());
        String summary = payload.path("summary").asText(match.normalizedText());
        ObjectNode g = object();
        g.put("artifactType", lightweightArtifactType(payload, evidence));
        g.put("title", payload.path("title").asText(match.normalizedText().substring(0, Math.min(80, match.normalizedText().length()))));
        g.put("summary", summary);
        g.put("body", lightweightBody(summary, evidence));
        g.set("sources", evidence);
        g.put("generationMode", "LIGHTWEIGHT_FALLBACK");
        return g;
    }

    private String lightweightArtifactType(JsonNode payload, ArrayNode evidence) {
        String text = (payload.path("title").asText("") + " " + payload.path("summary").asText("")).toLowerCase(Locale.ROOT);
        if (text.contains("github") || text.contains("resource") || text.contains("reference") || text.contains("ресурс") || text.contains("ссыл")) return "REFERENCE";
        if (text.contains("checklist") || text.contains("чеклист")) return "CHECKLIST";
        return evidence.size() <= 1 ? "NOTE" : "SUMMARY";
    }

    private JsonNode materialWithRequiredArtifactType(JsonNode material, String requiredArtifactType) {
        ObjectNode normalized = material != null && material.isObject() ? (ObjectNode) material.deepCopy() : object();
        String required = normalizeArtifactType(requiredArtifactType);
        String provider = normalizeArtifactType(normalized.path("artifactType").asText(""));
        if (blank(provider)) provider = "NOTE";
        if (!required.equals(provider)) normalized.put("providerArtifactType", provider);
        normalized.put("artifactType", required);
        return normalized;
    }

    private boolean generationUsable(JsonNode generation) {
        JsonNode payload = judgePayload(generation);
        String decision = payload.path("decision").asText("").trim().toUpperCase(Locale.ROOT);
        if (decision.startsWith("REJECT") || decision.contains("NEEDS_MORE_CONTEXT")) return false;
        return !blank(payload.path("body").asText("")) || payload.has("sources");
    }

    private List<Long> materialSourceMembers(long runId, Cluster c) {
        List<Long> sourceMembers = new ArrayList<>();
        Set<String> seenSourceKeys = new HashSet<>();
        for (Long member : c.members()) {
            String reason = jdbc.query("SELECT llm_skip_reason FROM replay_run_messages WHERE run_id = ? AND dataset_message_id = ?", rs -> rs.next() ? rs.getString(1) : null, runId, member);
            String text = jdbc.query("SELECT COALESCE(text, caption, '') FROM dataset_messages WHERE id = ?", rs -> rs.next() ? rs.getString(1) : "", member);
            String key = canonicalSourceKey(text);
            if (!"DUPLICATE".equals(reason) && seenSourceKeys.add(key)) sourceMembers.add(member);
        }
        return sourceMembers.isEmpty() ? c.members() : sourceMembers;
    }

    private String canonicalSourceKey(String text) {
        return (text == null ? "" : text)
                .replaceFirst("^\\[[^]]+]", "")
                .replaceFirst("(?i)^NIG[A-Z0-9-]*[-_: ]+S\\d{1,3}[-_: ][A-Z_ -]+\\.\\s*", "")
                .replaceFirst("(?i)^NIG[A-Z0-9-]*[-_: ]+S\\d{1,3}[-_: ]", "")
                .replaceFirst("(?i)^NIG[A-Z0-9-]*\\s+", "")
                .replaceFirst("(?i)^S\\d{1,3}[-_: ][A-Z_ -]+\\.\\s*", "")
                .replaceFirst("(?i)^S\\d{1,3}[-_: ]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String requiredArtifactType(JsonNode usefulness, String text) {
        String proposed = usefulness == null ? "" : usefulness.path("proposedMaterialType").asText("");
        if (!blank(proposed)) return normalizeArtifactType(proposed);
        return inferArtifactTypeFromText(text);
    }

    private String materialBucketForMembers(List<Long> members, Map<Long, Intel> byId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Long member : members) {
            String type = materialBucketForIntel(byId.get(member));
            counts.merge(type, 1, Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("SUMMARY");
    }

    private String materialBucketForIntel(Intel intel) {
        if (intel == null) return "SUMMARY";
        String text = textWithFeatureLinks(intel.normalizedText(), intel.features());
        MessageUsefulnessResult usefulness = usefulnessClassifier.classify(text, "NOISE_OR_CHAT", 0.0, intel.hardSignal());
        if (usefulness.proposedMaterialType() != null) return normalizeArtifactType(usefulness.proposedMaterialType());
        return inferArtifactTypeFromText(text);
    }

    private String inferArtifactTypeFromText(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "prompt", "generate", "generation", "сгенер", "шаблон", "template")) return "GENERATION";
        if (containsAny(lower, "github", "repo", "repository", "reference", "ресурс", "ссыл", "документац")) return "REFERENCE";
        if (containsAny(lower, "summary", "итог", "сводк", "резюме", "обзор")) return "SUMMARY";
        if (containsAny(lower, "?", "ответ", "почему", "что делать", "объясни")) return "ANSWER";
        if (containsAny(lower, "шаг", "чеклист", "guide", "гайд", "инструкц", "проверь", "настрой")) return "GUIDE";
        return "SUMMARY";
    }

    private String normalizeArtifactType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GUIDE", "GENERATION", "ANSWER", "SUMMARY", "REFERENCE", "OTHER" -> normalized;
            case "RESOURCE", "RESOURCE_CARD", "LINK_WITH_CONTEXT" -> "REFERENCE";
            case "NOTE" -> "OTHER";
            default -> blank(normalized) ? "SUMMARY" : normalized;
        };
    }

    private String lightweightBody(String summary, ArrayNode evidence) {
        StringBuilder body = new StringBuilder();
        if (!blank(summary)) body.append(summary).append("\n\n");
        body.append("Материал создан в lightweight fallback режиме по предоставленным сообщениям без добавления внешних фактов.");
        if (evidence.size() > 0) body.append("\n\nИсточники: ");
        for (int i = 0; i < evidence.size(); i++) {
            if (i > 0) body.append(", ");
            body.append("dataset_message_id=").append(evidence.get(i).path("datasetMessageId").asLong());
        }
        if (summary.toLowerCase(Locale.ROOT).contains("provider") || summary.toLowerCase(Locale.ROOT).contains("api") || summary.toLowerCase(Locale.ROOT).contains("github")) {
            body.append("\n\nПроверьте актуальную документацию провайдера: лимиты, тарифы, поведение cache и доступность моделей могут меняться.");
        }
        return body.toString();
    }
    private long knowledgeSingle(long runId, SingleMessageCandidate sc, Intel intel, JsonNode g, double confidence) {
        JsonNode material = materialWithRequiredArtifactType(g, sc.requiredArtifactType());
        long id = JdbcIds.insertReturningId(jdbc, "INSERT INTO knowledge_items (run_id, source_cluster_type, source_cluster_id, artifact_type, vertical, title, summary, body_json, knowledge_value_score, status) VALUES (?, 'SINGLE_MESSAGE', ?, ?, 'telegram-intelligence', ?, ?, ?::jsonb, ?, 'DRAFT')", runId, sc.messageId(), material.path("artifactType").asText("NOTE"), material.path("title").asText(material.path("recommendedTitle").asText("Single message")), material.path("summary").asText(""), write(material), confidence);
        jdbc.update("INSERT INTO knowledge_item_sources (knowledge_item_id, dataset_message_id, source_role, quote, confidence) VALUES (?, ?, 'EVIDENCE', '', ?)", id, sc.messageId(), confidence);
        assignKnowledgeItemTopics(id, material.path("artifactType").asText(""), List.of(sc.messageId()), confidence);
        jdbc.update("UPDATE replay_run_messages SET llm_used = true, updated_at = now() WHERE run_id = ? AND dataset_message_id = ?", runId, sc.messageId());
        return id;
    }
    private long knowledgeDiscussion(long runId, DiscussionSegmentCandidate dc, JsonNode g, double confidence) {
        JsonNode material = materialWithRequiredArtifactType(g, dc.proposedMaterialType());
        long id = JdbcIds.insertReturningId(jdbc, "INSERT INTO knowledge_items (run_id, source_cluster_type, source_cluster_id, artifact_type, vertical, title, summary, body_json, knowledge_value_score, status) VALUES (?, 'DISCUSSION_SEGMENT', ?, ?, 'telegram-intelligence', ?, ?, ?::jsonb, ?, 'DRAFT')", runId, dc.segmentId(), material.path("artifactType").asText(dc.proposedMaterialType()), material.path("title").asText(material.path("recommendedTitle").asText("Discussion segment")), material.path("summary").asText(""), write(material), confidence);
        for (Intel source : dc.messages()) {
            jdbc.update("INSERT INTO knowledge_item_sources (knowledge_item_id, dataset_message_id, source_role, quote, confidence) VALUES (?, ?, ?, ?, ?)", id, source.message().id(), roleFor(source).toUpperCase(Locale.ROOT), preview(normalized(source), 500), confidence);
            jdbc.update("UPDATE replay_run_messages SET llm_used = true, knowledge_item_id = ?, updated_at = now() WHERE run_id = ? AND dataset_message_id = ?", id, runId, source.message().id());
        }
        assignKnowledgeItemTopics(id, material.path("artifactType").asText(""), dc.messages().stream().map(i -> i.message().id()).toList(), confidence);
        if (dc.segmentId() != null) jdbc.update("UPDATE discussion_segments SET decision = 'DISCUSSION_SEGMENT_MATERIAL_CANDIDATE', updated_at = now() WHERE id = ?", dc.segmentId());
        return id;
    }

    private void assignKnowledgeItemTopics(long knowledgeItemId, String artifactType, List<Long> sourceMessageIds, double confidence) {
        Set<String> slugs = new LinkedHashSet<>();
        StringBuilder text = new StringBuilder(normalizeArtifactType(artifactType)).append(' ');
        for (Long messageId : sourceMessageIds) {
            String value = jdbc.query("SELECT COALESCE(text, caption, '') FROM dataset_messages WHERE id = ?", rs -> rs.next() ? rs.getString(1) : "", messageId);
            text.append(value == null ? "" : value).append(' ');
        }
        String lower = text.toString().toLowerCase(Locale.ROOT);
        if (containsAny(lower, "github", "gitlab", "repo", "repository", "open-source", "sdk repo", "npm package", "pypi")) slugs.add("tools-repos");
        if (containsAny(lower, "prompt", "промпт", "agent", "агент", "cursor", "claude code", "codex", "workflow", "шаблон промп")) slugs.add("agents-prompts");
        if (containsAny(lower, "free-tier", "free tier", "бесплат", "quota", "квот", "токенов в сутки")) slugs.add("free-tokens-quotas");
        if (containsAny(lower, "outage", "сбой", "degraded", "429", "timeout", "rate limit", "лимит", "quota exhausted")) slugs.add("outages-limits");
        if (containsAny(lower, "provider", "router", "openrouter", "modelhub", "litellm", "gateway", "fallback endpoint")) slugs.add("providers-routers");
        if (containsAny(lower, "api", "endpoint", "/v1", "openai-compatible", "sdk", "auth", "bearer", "webhook", "rest", "graphql")) slugs.add("api-integrations");
        if (containsAny(lower, "gpt", "claude", "gemini", "qwen", "deepseek", "llama", "mistral", "benchmark", "context window", "release", "релиз", "новая модель")) slugs.add("models-releases");
        if (containsAny(lower, "pricing", "billing", "стоим", "цена", "тариф", "cost", "cache cost", "token spend", "оплата", "invoice")) slugs.add("pricing-costs");
        if (containsAny(lower, "privacy", "приват", "логи", "leak", "утеч", "secret", "password", "credential", "безопас")) slugs.add("security-privacy");
        if (containsAny(lower, "bypass", "loophole", "накрут", "фарм", "anti-abuse", "антиабьюз")) slugs.add("abuse-risk");
        if (slugs.isEmpty()) slugs.add(switch (normalizeArtifactType(artifactType)) {
            case "GENERATION" -> "agents-prompts";
            case "REFERENCE" -> "tools-repos";
            default -> "api-integrations";
        });
        for (String slug : slugs) {
            jdbc.update("""
                    INSERT INTO knowledge_item_topics (knowledge_item_id, topic_id, confidence, source, reason)
                    SELECT ?, id, ?, 'RULE', ?
                    FROM knowledge_topics
                    WHERE slug = ?
                    ON CONFLICT DO NOTHING
                    """, knowledgeItemId, Math.max(0.50, Math.min(0.95, confidence)), "material topic keyword match", slug);
        }
    }
    private List<SingleMessageCandidate> singleMessageDetection(long runId, List<Intel> intel, List<Embedding> embeddings, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        int minTextLen = settings.getInt("minSingleMessageTextLength", 500);
        double candidateThreshold = settings.getDouble("singleMessageCandidateThreshold", 0.55);
        double directThreshold = settings.getDouble("directMaterialReadyThreshold", 0.72);
        Set<Long> embedded = embeddings.stream().map(Embedding::messageId).collect(java.util.stream.Collectors.toSet());
        List<SingleMessageCandidate> candidates = new ArrayList<>();
        Map<Long, String> labels = new HashMap<>();
        Map<Long, Double> confidences = new HashMap<>();
        jdbc.query("SELECT dataset_message_id, top_label, confidence FROM message_classifications WHERE run_id = ?", (RowCallbackHandler) rs -> { labels.put(rs.getLong("dataset_message_id"), rs.getString("top_label")); confidences.put(rs.getLong("dataset_message_id"), rs.getDouble("confidence")); }, runId);
        Set<Long> duplicates = new HashSet<>();
        jdbc.query("SELECT dataset_message_id FROM replay_run_messages WHERE run_id = ? AND llm_skip_reason = 'DUPLICATE'", (RowCallbackHandler) rs -> duplicates.add(rs.getLong("dataset_message_id")), runId);
        Set<Long> clustered = new HashSet<>();
        jdbc.query("SELECT dataset_message_id FROM replay_run_messages WHERE run_id = ? AND (microcluster_id IS NOT NULL OR macrocluster_id IS NOT NULL)", (RowCallbackHandler) rs -> clustered.add(rs.getLong("dataset_message_id")), runId);
        int evaluatedCount = 0;
        int embeddedSingletonEvaluatedCount = 0;
        int skippedEmbeddedCount = 0;
        int skippedClusteredCount = 0;
        int skippedSuppressedCount = 0;
        int rejectedLowScoreCount = 0;
        int rejectedTooShortCount = 0;
        int rejectedLowSignalCount = 0;
        int rejectedChatContextCount = 0;
        int directReadyCount = 0;
        for (Intel i : intel) {
            if (duplicates.contains(i.message().id())) { skippedSuppressedCount++; continue; }
            if (clustered.contains(i.message().id())) { skippedClusteredCount++; continue; }
            if (i.ruleDecision().equals("SUPPRESS")) { skippedSuppressedCount++; rejectSingleMessage(runId, i.message().id(), null, "SUPPRESSED_BY_CLASSIFIER", json.createArrayNode()); continue; }
            String topLabel = labels.getOrDefault(i.message().id(), "NOISE_OR_CHAT");
            double confidence = confidences.getOrDefault(i.message().id(), 0.0);
            String text = textWithFeatureLinks(i.normalizedText(), i.features());
            if (text == null || text.isBlank()) { rejectSingleMessage(runId, i.message().id(), null, "NO_TEXT", json.createArrayNode()); continue; }
            MessageUsefulnessResult usefulness = usefulnessClassifier.classify(text, topLabel, confidence, i.hardSignal());
            ObjectNode usefulnessJson = usefulnessJson(usefulness);
            if (usefulness.rejected() && !"LOW_VALUE".equals(usefulness.rejectReason())) {
                rejectedLowSignalCount++;
                persistRejectedSignal(runId, i, text, usefulness.rejectReason(), usefulness);
                rejectSingleMessage(runId, i.message().id(), usefulness.overallScore(), usefulness.rejectReason(), usefulnessSignals(usefulness), usefulnessJson);
                continue;
            }
            evaluatedCount++;
            if (embedded.contains(i.message().id())) embeddedSingletonEvaluatedCount++;
            if (topLabel.equals("NOISE_OR_CHAT") && !i.hardSignal() && !"SINGLE_MESSAGE".equals(usefulness.candidateRoute())) { rejectedChatContextCount++; rejectSingleMessage(runId, i.message().id(), 0.0, "CHAT_CONTEXT_ONLY", usefulnessSignals(usefulness), usefulnessJson); continue; }
            if (text.length() < Math.min(minTextLen, 80)) { rejectedTooShortCount++; rejectSingleMessage(runId, i.message().id(), 0.0, "TOO_SHORT", json.createArrayNode()); continue; }
            String lowerText = text.toLowerCase(Locale.ROOT);
            double textLen = Math.min(1.0, text.length() / (double) minTextLen);
            boolean hasStructure = text.matches("(?is).*(?:^|\\n|[.!?]\\s*)(?:проблема|причина|решение|шаг \\d|1\\.|2\\.|если).*");
            boolean hasError = text.matches("(?i).*(?:error|exception|failed|ошиб|не работает|cannot|401|403|404|timeout|unauthorized|not found).*");
            boolean hasSolution = text.matches("(?i).*(?:fix|решение|попробуй|проверь|провер|check|ensure|verify|install|run|настрой|должен|переда[её]тся|указан|сначала).*");
            boolean hasGuide = text.matches("(?i).*(?:шаг|how to|guide|tutorial|инструкц|как|пошагов|мини-гайд).*");
            boolean hasApiSignal = text.matches("(?i).*(?:api|endpoint|base url|/v1|bearer|token|model id|openai-compatible|cursor|authorization|ключ|модель).*");
            boolean hasDiagnosisMapping = text.matches("(?is).*если.+(?:значит|проблема|чаще всего|то).+.*");
            boolean hasQuestionAnswer = text.matches("(?is).*(?:если|когда|при).+(?:проверь|значит|решение|проблема|чаще всего).*");
            hasStructure = hasStructure || lowerText.contains("если ") || lowerText.startsWith("если");
            hasError = hasError || lowerText.contains("401") || lowerText.contains("404");
            hasSolution = hasSolution || lowerText.contains("проверь") || lowerText.contains("check") || lowerText.contains("verify");
            hasApiSignal = hasApiSignal || lowerText.contains("api") || lowerText.contains("bearer") || lowerText.contains("base url") || lowerText.contains("model id") || lowerText.contains("/v1");
            hasDiagnosisMapping = hasDiagnosisMapping || (lowerText.contains("если") && (lowerText.contains("значит") || lowerText.contains("проблема") || lowerText.contains("чаще всего")));
            hasQuestionAnswer = hasQuestionAnswer || (lowerText.contains("если") && (lowerText.contains("проверь") || lowerText.contains("значит") || lowerText.contains("проблема")));
            double lengthScore = Math.min(0.10, textLen * 0.10);
            double structureScore = hasStructure ? 0.10 : 0.0;
            double guideHowToScore = hasGuide ? 0.14 : 0.0;
            double troubleshootingScore = hasError ? 0.12 : 0.0;
            double solutionScore = hasSolution ? 0.10 : 0.0;
            double hardSignalScore = i.hardSignal() ? 0.10 : 0.0;
            double codeApiScore = hasApiSignal ? 0.12 : 0.0;
            double urlLinkScore = text.matches("(?i).*(?:https?://|www\\.|/v\\d+).*") ? 0.03 : 0.0;
            double qaScore = hasDiagnosisMapping || hasQuestionAnswer ? 0.12 : 0.0;
            double troubleshootingExplanationScore = hasError && hasSolution && (hasDiagnosisMapping || hasQuestionAnswer) ? 0.08 : 0.0;
            double classifierConfidenceScore = confidence >= 0.65 && !topLabel.equals("NOISE_OR_CHAT") ? 0.06 : 0.0;
            double noisePenalty = topLabel.equals("NOISE_OR_CHAT") && !i.hardSignal() ? 0.20 : 0.0;
            double usefulnessScore = "SINGLE_MESSAGE".equals(usefulness.candidateRoute()) ? Math.max(0.0, usefulness.overallScore() - candidateThreshold) : 0.0;
            double heuristicScore = Math.max(0.0, Math.min(1.0, lengthScore + structureScore + guideHowToScore + troubleshootingScore + solutionScore + hardSignalScore + codeApiScore + urlLinkScore + qaScore + troubleshootingExplanationScore + classifierConfidenceScore + usefulnessScore - noisePenalty));
            double score = "SINGLE_MESSAGE".equals(usefulness.candidateRoute()) ? Math.max(heuristicScore, usefulness.overallScore()) : heuristicScore;
            ObjectNode breakdown = object("lengthScore", lengthScore, "structureScore", structureScore, "guideHowToScore", guideHowToScore, "troubleshootingScore", troubleshootingScore, "hardSignalScore", hardSignalScore);
            breakdown.put("solutionScore", solutionScore);
            breakdown.put("codeApiScore", codeApiScore);
            breakdown.put("urlLinkScore", urlLinkScore);
            breakdown.put("qaScore", qaScore);
            breakdown.put("troubleshootingExplanationScore", troubleshootingExplanationScore);
            breakdown.put("classifierConfidenceScore", classifierConfidenceScore);
            breakdown.put("usefulnessScore", usefulnessScore);
            breakdown.put("noisePenalty", noisePenalty);
            breakdown.put("finalScore", score);
            breakdown.set("usefulnessClassification", usefulnessJson);
            String decision = score >= directThreshold && text.length() >= minTextLen ? "DIRECT_MATERIAL_READY" : "SINGLE_MESSAGE_MATERIAL_CANDIDATE";
            ArrayNode signals = json.createArrayNode();
            if (hasError) signals.add("ERROR_SIGNAL");
            if (hasSolution) signals.add("SOLUTION_SIGNAL");
            if (hasGuide) signals.add("GUIDE_OR_HOWTO");
            if (hasStructure) signals.add("HAS_STRUCTURE");
            if (hasApiSignal) signals.add("API_OR_STATUS_SIGNAL");
            if (hasDiagnosisMapping) signals.add("DIAGNOSIS_MAPPING");
            if (i.hardSignal()) signals.add("HARD_SIGNAL");
            usefulness.positiveSignals().forEach(signals::add);
            if (usefulness.proposedMaterialType() != null) signals.add("PROPOSED_MATERIAL_TYPE_" + usefulness.proposedMaterialType());
            if ("SINGLE_MESSAGE".equals(usefulness.candidateRoute())) signals.add("USEFULNESS_" + usefulness.contentClass());
            if (score < candidateThreshold) {
                String rejectionReason = "LOW_VALUE".equals(usefulness.rejectReason()) ? "LOW_VALUE" : (signals.isEmpty() ? "LOW_SIGNAL" : "LOW_SINGLE_MESSAGE_SCORE");
                persistRejectedSignal(runId, i, text, rejectionReason, usefulness);
                if (signals.isEmpty()) {
                    rejectedLowSignalCount++;
                    rejectSingleMessage(runId, i.message().id(), score, rejectionReason, signals, breakdown);
                } else {
                    rejectedLowScoreCount++;
                    rejectSingleMessage(runId, i.message().id(), score, rejectionReason, signals, breakdown);
                }
                continue;
            }
            if (decision.equals("DIRECT_MATERIAL_READY")) directReadyCount++;
            // Active Material Eligibility Gate: block obvious non-material (rules/onboarding/test
            // artifacts, roleplay/fiction/system-prompt, article digests, LLM refusals) from becoming
            // single-message material candidates, even when the score clears the threshold. This is the
            // production admission layer ported from the offline v2 lab. Conservative: only REJECT_SAFE
            // and CONTEXT_ONLY routes are blocked; MANUAL_REVIEW/NEEDS_ENRICHMENT/REVIEW_HIGH_RECALL still
            // reach LLM Judge. Gated by materialEligibilityGateActiveEnabled (default 0).
            if (settings.getInt("materialEligibilityGateActiveEnabled", 0) == 1) {
                java.util.List<String> gateExtraUrls = new java.util.ArrayList<>();
                JsonNode featLinks = i.features() == null ? null : i.features().path("links");
                if (featLinks != null && featLinks.isArray()) for (JsonNode u : featLinks) if (u.isTextual()) gateExtraUrls.add(u.asText());
                JsonNode featHidden = i.features() == null ? null : i.features().path("rawJsonUrls");
                if (featHidden != null && featHidden.isArray()) for (JsonNode u : featHidden) if (u.isTextual()) gateExtraUrls.add(u.asText());
                MaterialEligibilityGate.MessageVerdict gateVerdict = MaterialEligibilityGate.evaluateMessage(
                    i.normalizedText(), text, (featLinks == null ? 0 : featLinks.size()),
                    usefulness.candidateRoute(), usefulness.rejectReason(), usefulness.proposedMaterialType(), gateExtraUrls);
                if (MaterialEligibilityGate.ROUTE_REJECT_SAFE.equals(gateVerdict.route())
                    || MaterialEligibilityGate.ROUTE_CONTEXT_ONLY.equals(gateVerdict.route())) {
                    String gateReason = MaterialEligibilityGate.ROUTE_REJECT_SAFE.equals(gateVerdict.route())
                        ? "MATERIAL_ELIGIBILITY_GATE_REJECT_SAFE" : "MATERIAL_ELIGIBILITY_GATE_NON_MATERIAL_LONG_FORM";
                    ArrayNode gateSignals = json.createArrayNode();
                    gateSignals.add("MATERIAL_ELIGIBILITY_GATE_BLOCKED");
                    gateSignals.add(gateReason);
                    persistRejectedSignal(runId, i, text, gateReason, usefulness);
                    rejectSingleMessage(runId, i.message().id(), score, gateReason, gateSignals, breakdown);
                    continue;
                }
            }
            jdbc.update("UPDATE replay_run_messages SET final_decision = ?, single_message_score = ?, single_message_rejection_reason = NULL, single_message_signals_json = ?::jsonb, single_message_score_breakdown_json = ?::jsonb, updated_at = now() WHERE run_id = ? AND dataset_message_id = ?",
                decision, score, write(signals), write(breakdown), runId, i.message().id());
            Long decisionObjectId = decisionCoreMessageId(runId, i.message().id());
            String requiredType = requiredArtifactType(usefulnessJson, text);
            decisionCoreLedger(decisionObjectId, runId, "message:" + i.message().id(), "single:" + i.message().id(), DecisionCoreEnums.CandidateType.SINGLE_MESSAGE, DecisionCoreEnums.LedgerEventType.CANDIDATE_CREATED, false, List.of(), null, null, BigDecimal.valueOf(score), null, DecisionCoreEnums.FinalRoute.ROUTE_TO_SINGLE_CANDIDATE, null, requiredType, List.of("SINGLE_MESSAGE_CANDIDATE"), List.of(), List.of(), breakdown);
            decisionCoreRank(decisionObjectId, runId, "message:" + i.message().id(), "single_message", 1, BigDecimal.valueOf(score), object("valueScore", usefulness.overallScore(), "evidenceScore", hardSignalScore, "coherenceScore", 0.0, "sourceQualityScore", classifierConfidenceScore, "riskPenalty", usefulness.safetyClass().contains("RISK") ? 1.0 : 0.0, "promoPenalty", "PROMO_ALONE".equals(usefulness.contentClass()) ? 1.0 : 0.0, "duplicatePenalty", 0.0, "freshnessScore", 0.5, "noveltyScore", 0.5, "actionabilityScore", solutionScore + guideHowToScore, "materialReadinessScore", score, "alreadyCoveredPenalty", 0.0, "contradictionPenalty", 0.0), false, List.of("SHADOW_RANK_ONLY"));
            decisionCoreSafety(decisionObjectId, runId, usefulness.safetyClass(), usefulness.safetyClass().contains("UNSAFE") || usefulness.safetyClass().contains("RISK"), usefulness.safetyClass().contains("RISK"), usefulness.safetyClass().contains("RISK"), false, usefulness.safetyClass().contains("RISK") ? BigDecimal.ONE : BigDecimal.ZERO, usefulness.negativeSignals(), usefulnessJson);
            candidates.add(new SingleMessageCandidate(i.message().id(), score, decision, signals, usefulnessJson, requiredType));
        }
        metric(metrics, "single_message_candidates", candidates.size());
        metric(metrics, "single_message_evaluated", evaluatedCount);
        metric(metrics, "single_message_rejected_low_score", rejectedLowScoreCount);
        metric(metrics, "direct_material_ready", directReadyCount);
        metric(metrics, "discussion_segment_candidates", 0);
        ObjectNode stageMetrics = object("inputCount", intel.size(), "evaluatedCount", evaluatedCount, "embeddedSingletonEvaluatedCount", embeddedSingletonEvaluatedCount, "skippedEmbeddedCount", skippedEmbeddedCount, "skippedClusteredCount", skippedClusteredCount);
        stageMetrics.put("skippedSuppressedCount", skippedSuppressedCount);
        stageMetrics.put("rejectedLowScoreCount", rejectedLowScoreCount);
        stageMetrics.put("rejectedTooShortCount", rejectedTooShortCount);
        stageMetrics.put("rejectedLowSignalCount", rejectedLowSignalCount);
        stageMetrics.put("rejectedChatContextCount", rejectedChatContextCount);
        stageMetrics.put("candidatesCount", candidates.size());
        stageMetrics.put("discussionSegmentCandidates", 0);
        stageMetrics.put("directReadyCount", directReadyCount);
        stage(runId, "SINGLE_MESSAGE_DETECTION", "COMPLETED", intel.size(), evaluatedCount, skippedClusteredCount + skippedSuppressedCount, 0, 0, stageMetrics, null, elapsed(started));
        log.info("[SINGLE_MESSAGE_DETECTION] input={} evaluated={} candidates={} directReady={}", intel.size(), evaluatedCount, candidates.size(), directReadyCount);
        return candidates;
    }

    private List<DiscussionSegmentCandidate> discussionSegmentDetection(long runId, List<Intel> intel, Map<String, BigDecimal> metrics) {
        long started = System.nanoTime();
        if (intel.size() < 2) {
            metric(metrics, "discussion_segment_candidates", 0);
            return List.of();
        }
        int maxMessages = settings.getInt("discussionSegmentMaxMessages", 6);
        int maxWindowMinutes = settings.getInt("discussionSegmentMaxWindowMinutes", 20);
        double threshold = settings.getDouble("discussionSegmentCandidateThreshold", 0.55);
        List<Intel> ordered = intel.stream()
            .filter(i -> normalized(i).length() > 0)
            .sorted((a, b) -> compareMessageOrder(a.message(), b.message()))
            .toList();
        List<DiscussionSegmentCandidate> scoredCandidates = new ArrayList<>();
        List<DiscussionSegmentCandidate> rejectedCandidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int startIndex = 0; startIndex < ordered.size(); startIndex++) {
            List<Intel> window = new ArrayList<>();
            Intel start = ordered.get(startIndex);
            for (int j = startIndex; j < ordered.size() && window.size() < maxMessages; j++) {
                Intel next = ordered.get(j);
                if (!sameDiscussionScope(start.message(), next.message())) break;
                if (!withinWindow(start.message(), next.message(), maxWindowMinutes) && !extendedSameThreadContext(start, next)) break;
                window.add(next);
            }
            if (window.size() < 2) continue;
            DiscussionSegmentCandidate candidate = scoreDiscussionWindow(runId, window, threshold);
            if (candidate == null) continue;
            String key = candidate.messages().stream().map(i -> Long.toString(i.message().id())).collect(Collectors.joining(","));
            if (!seen.add(key)) continue;
            if ("DISCUSSION_SEGMENT_CANDIDATE".equals(candidate.decision())) scoredCandidates.add(candidate);
            else rejectedCandidates.add(candidate);
        }
        DiscussionDedupeResult deduped = dedupeDiscussionSegments(scoredCandidates);
        List<DiscussionSegmentCandidate> candidates = applyDiscussionWindowLimit(deduped.accepted(), deduped.rejected());
        rejectedCandidates.addAll(deduped.rejected());
        rejectedCandidates.addAll(candidates.stream().filter(c -> !"DISCUSSION_SEGMENT_CANDIDATE".equals(c.decision())).toList());
        candidates = candidates.stream().filter(c -> "DISCUSSION_SEGMENT_CANDIDATE".equals(c.decision())).toList();
        for (DiscussionSegmentCandidate candidate : candidates) persistDiscussionSegment(runId, candidate);
        for (DiscussionSegmentCandidate candidate : rejectedCandidates) persistDiscussionSegment(runId, candidate);
        for (DiscussionSegmentCandidate candidate : candidates) decisionCoreDiscussion(runId, candidate, true);
        for (DiscussionSegmentCandidate candidate : rejectedCandidates) decisionCoreDiscussion(runId, candidate, false);
        metric(metrics, "discussion_segment_candidates", candidates.size());
        long overlapRejected = rejectedCandidates.stream().filter(c -> "DISCUSSION_SEGMENT_REJECTED_OVERLAP_DUPLICATE".equals(c.rejectionReason())).count();
        long windowLimitRejected = rejectedCandidates.stream().filter(c -> "DISCUSSION_SEGMENT_REJECTED_WINDOW_LIMIT".equals(c.rejectionReason())).count();
        long lowValueRejected = rejectedCandidates.stream().filter(c -> c.rejectionReason() != null && c.rejectionReason().contains("LOW_VALUE")).count();
        stage(runId, "DISCUSSION_SEGMENT_WINDOW_FORMED", "COMPLETED", ordered.size(), scoredCandidates.size() + rejectedCandidates.size(), Math.max(0, ordered.size() - candidates.size()), 0, 0, object("maxMessages", maxMessages, "maxWindowMinutes", maxWindowMinutes), null, elapsed(started));
        stage(runId, "DISCUSSION_SEGMENT_SCORING", "COMPLETED", ordered.size(), candidates.size(), rejectedCandidates.size(), 0, 0, object("threshold", threshold, "overlapRejected", overlapRejected, "windowLimitRejected", windowLimitRejected, "lowValueAdjacentRejected", lowValueRejected), null, elapsed(started));
        stage(runId, "DISCUSSION_SEGMENT_CANDIDATE_DECISION", "COMPLETED", candidates.size(), candidates.size(), rejectedCandidates.size(), 0, 0, object("candidateCount", candidates.size(), "rejectedCount", rejectedCandidates.size()), null, elapsed(started));
        return candidates;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private DiscussionSegmentCandidate scoreDiscussionWindow(long runId, List<Intel> messages, double threshold) {
        String combined = messages.stream().map(this::normalized).collect(Collectors.joining("\n"));
        String lower = combined.toLowerCase(Locale.ROOT);
        ArrayNode signals = json.createArrayNode();
        ArrayNode suppressions = json.createArrayNode();
        if (containsAny(lower, "промокод", "скидк", "купи", "hosting", "vps", "реф ссыл", "акция")) suppressions.add("PROMO_OR_AD");
        if (containsAny(lower, "лонг", "шорт", "ликвид", "депо", "бирж", "prop firm", "трейд")) suppressions.add("CRYPTO_TRADING_OFFTOPIC");
        if (containsAny(lower, "добро пожаловать", "welcome", "правила сообщества", "ознакомься")) suppressions.add("WELCOME_TEMPLATE");
        if (containsAny(lower, "gpt-5.6", "новости ai", "rutube", "новая модель") && !containsAny(lower, "как", "решение", "чеклист", "проверь")) suppressions.add("LOW_ACTIONABILITY_NEWS");
        if (suppressions.size() > 0) return null;

        boolean question = containsAny(lower, "?", "как ", "можно ли", "что делать", "почему", "какая цель", "хочу задать");
        boolean solution = containsAny(lower, "проверь", "нужно", "надо", "лучше", "решение", "работает", "если", "значит", "отключ", "сделай");
        boolean error = containsAny(lower, "error", "ошиб", "401", "404", "timeout", "тупит", "не работает", "лимит");
        boolean checklist = containsAny(lower, "чеклист", "1.", "2.", "3.", "список", "пункт", "фидбек");
        boolean api = containsAny(lower, "api", "endpoint", "model", "fallback", "openai", "hermes", "anthropic", "cache", "token", "лимит", "context", "контекст", "кеш");
        boolean resource = containsAny(lower, "github", "http://", "https://", "repo", "репо", "ссылка");
        boolean resume = containsAny(lower, "резюме", "hh", "достижен", "формулиров", "hr", "ai-фильтр", "фильтр");
        boolean caution = containsAny(lower, "риск", "security", "тревож", "негатив", "не медицин", "осторож", "спалить", "цель");
        boolean finalSummary = containsAny(lower, "итог", "вывод", "резюме:", "summary");
        boolean productFeedback = sameTimestampBurst(messages) && containsAny(lower, "боли", "фидбек", "пользовател", "нет контроля", "дорого", "слабые модели", "собираю ос", "буду рад ос", "горение", "горит", "существующий список", "отсутствие", "траты токенов");
        boolean highRiskNeedsContext = caution && question && messages.size() <= 2 && containsAny(lower, "security", "location", "локац", "компани", "работать из другой страны", "спалить", "юрид", "медицин", "здоров", "финанс");
        boolean repeatedEntity = repeatedEntity(messages);
        if (question && solution) signals.add("Q_AND_A_PAIR");
        if (error && solution) signals.add("PROBLEM_SOLUTION");
        if (error && api) signals.add("ERROR_OR_STATUS_DIAGNOSIS");
        if (checklist) signals.add("CHECKLIST_OR_LIST");
        if (api) signals.add("TECHNICAL_API_CACHE_COST");
        if (resource && solution) signals.add("RESOURCE_LINK_WITH_EXPLANATION");
        if (resume) signals.add("CAREER_RESUME_SIGNAL");
        if (caution) signals.add("RISK_OR_CAUTION_SIGNAL");
        if (finalSummary) signals.add("FINAL_SUMMARY_SIGNAL");
        if (productFeedback) signals.add("PRODUCT_FEEDBACK_BURST");
        if (repeatedEntity) signals.add("REPEATED_ENTITY");
        if (messages.size() >= 3) signals.add("MULTI_MESSAGE_CONTEXT");
        int usefulSignals = signals.size() - (repeatedEntity ? 1 : 0) - (messages.size() >= 3 ? 1 : 0);
        if (highRiskNeedsContext) return rejectedDiscussionCandidate(runId, messages, "DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT", "DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT", signals, suppressions, lower);
        if (usefulSignals <= 1 && repeatedEntity) return rejectedDiscussionCandidate(runId, messages, "DISCUSSION_SEGMENT_REJECTED_ENTITY_ONLY", "DISCUSSION_SEGMENT_REJECTED_ENTITY_ONLY", signals, suppressions, lower);
        if (usefulSignals <= 1 && messages.size() >= 3) return rejectedDiscussionCandidate(runId, messages, "DISCUSSION_SEGMENT_REJECTED_LOW_VALUE_ADJACENT", "DISCUSSION_SEGMENT_REJECTED_LOW_VALUE_ADJACENT", signals, suppressions, lower);
        double score = 0.18 + signals.size() * 0.10 + Math.min(0.12, messages.size() * 0.02);
        if (sameTimestampBurst(messages)) score += 0.10;
        if (question && solution) score += 0.08;
        if (score < threshold || usefulSignals < 2) return rejectedDiscussionCandidate(runId, messages, "DISCUSSION_SEGMENT_REJECTED_LOW_SCORE", "LOW_COMBINED_SCORE_OR_SIGNAL_COUNT", signals, suppressions, lower);
        int guideSignals = 0;
        if (question && solution) guideSignals++;
        if (error && solution) guideSignals++;
        if (error && api) guideSignals++;
        if (checklist && !productFeedback) guideSignals++;
        if (api) guideSignals++;
        if (finalSummary) guideSignals++;
        String type = guideSignals >= 2 && !productFeedback ? "GUIDE" : (caution || checklist || productFeedback ? "SUMMARY" : "ANSWER");
        if (resume && !api) type = "ANSWER";
        if (caution && !api) type = "SUMMARY";
        if (resource && !api && !solution) type = "REFERENCE";
        String text = combined.length() > 4000 ? combined.substring(0, 4000) : combined;
        return new DiscussionSegmentCandidate(null, runId, score, "DISCUSSION_SEGMENT_CANDIDATE", null, type, signals, suppressions, List.copyOf(messages), text);
    }

    private DiscussionSegmentCandidate rejectedDiscussionCandidate(long runId, List<Intel> messages, String decision, String rejectionReason, JsonNode signals, JsonNode suppressions, String combinedLower) {
        String text = combinedLower.length() > 4000 ? combinedLower.substring(0, 4000) : combinedLower;
        return new DiscussionSegmentCandidate(null, runId, 0.0, decision, rejectionReason, "IGNORE", signals, suppressions, List.copyOf(messages), text);
    }

    private String normalized(Intel intel) { return intel.normalizedText() == null ? "" : intel.normalizedText().trim(); }
    private int compareMessageOrder(Message a, Message b) {
        int byDate = nullSafeInstant(a.messageDate()).compareTo(nullSafeInstant(b.messageDate()));
        if (byDate != 0) return byDate;
        int byIngested = nullSafeInstant(a.ingestedAt()).compareTo(nullSafeInstant(b.ingestedAt()));
        if (byIngested != 0) return byIngested;
        return Long.compare(a.id(), b.id());
    }
    private Instant nullSafeInstant(Timestamp timestamp) { return timestamp == null ? Instant.EPOCH : timestamp.toInstant(); }
    private boolean sameDiscussionScope(Message first, Message next) {
        return first.accountId() == next.accountId()
            && first.telegramChatId() == next.telegramChatId()
            && java.util.Objects.equals(first.forumTopicId(), next.forumTopicId())
            && java.util.Objects.equals(first.messageThreadId(), next.messageThreadId());
    }
    private boolean withinWindow(Message first, Message next, int maxWindowMinutes) {
        if (first.messageDate() == null || next.messageDate() == null) return true;
        return Math.abs(Duration.between(first.messageDate().toInstant(), next.messageDate().toInstant()).toMinutes()) <= maxWindowMinutes;
    }
    private boolean extendedSameThreadContext(Intel first, Intel next) {
        if (!sameDiscussionScope(first.message(), next.message()) || first.message().messageDate() == null || next.message().messageDate() == null) return false;
        long minutes = Math.abs(Duration.between(first.message().messageDate().toInstant(), next.message().messageDate().toInstant()).toMinutes());
        if (minutes > 360) return false;
        String combined = (normalized(first) + " " + normalized(next)).toLowerCase(Locale.ROOT);
        return containsAny(combined, "ретрит", "психодел", "тревож", "цель", "риск", "не медицин", "security", "location", "локац");
    }
    private boolean sameTimestampBurst(List<Intel> messages) {
        if (messages.size() < 2) return false;
        Instant first = nullSafeInstant(messages.get(0).message().messageDate());
        Instant last = nullSafeInstant(messages.get(messages.size() - 1).message().messageDate());
        return Duration.between(first, last).abs().getSeconds() <= 5;
    }
    private boolean repeatedEntity(List<Intel> messages) {
        String combined = messages.stream().map(this::normalized).collect(Collectors.joining(" ")).toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String entity : List.of("api", "model", "fallback", "cache", "кеш", "резюме", "hh", "security", "cli", "github")) if (combined.contains(entity)) hits++;
        return hits >= 1 && messages.size() >= 2;
    }

    private boolean discussionEligibleForControlledGeneration(DiscussionSegmentCandidate candidate, long runId) {
        if (!"CONTROLLED".equalsIgnoreCase(settings.getString("discussionSegmentGenerationMode", "OFF"))) {
            markDiscussionSkipped(runId, candidate, "DISCUSSION_SEGMENT_CONTROLLED_MODE_DISABLED");
            return false;
        }
        if (settings.getInt("discussionSegmentDraftOnly", 1) != 1) {
            markDiscussionSkipped(runId, candidate, "DISCUSSION_SEGMENT_DRAFT_ONLY_REQUIRED");
            return false;
        }
        if (!"DISCUSSION_SEGMENT_CANDIDATE".equals(candidate.decision())) {
            markDiscussionSkipped(runId, candidate, "DISCUSSION_SEGMENT_REJECTED_NOT_CANDIDATE");
            return false;
        }
        if (candidate.rejectionReason() != null) {
            markDiscussionSkipped(runId, candidate, candidate.rejectionReason());
            return false;
        }
        if (settings.getInt("discussionSegmentSkipRiskSensitive", 1) == 1 && riskSensitiveDiscussion(candidate)) {
            markDiscussionSkipped(runId, candidate, "DISCUSSION_SEGMENT_REJECTED_RISK_SENSITIVE_CONTROLLED_MODE");
            return false;
        }
        if (settings.getInt("discussionSegmentFreshOnly", 1) == 1 && !freshAfterControlledEnableTime(candidate)) {
            markDiscussionSkipped(runId, candidate, "DISCUSSION_SEGMENT_REJECTED_NOT_FRESH_AFTER_ENABLE_TIME");
            return false;
        }
        if (discussionDailyLimitReached()) {
            markDiscussionSkipped(runId, candidate, "DISCUSSION_SEGMENT_REJECTED_DAILY_LIMIT");
            return false;
        }
        if (discussionChatTopicLimitReached(candidate)) {
            markDiscussionSkipped(runId, candidate, "DISCUSSION_SEGMENT_REJECTED_CHAT_TOPIC_DAILY_LIMIT");
            return false;
        }
        if (discussionDuplicateExists(candidate)) {
            markDiscussionSkipped(runId, candidate, "DISCUSSION_SEGMENT_REJECTED_DUPLICATE_MATERIAL");
            return false;
        }
        String type = candidate.proposedMaterialType();
        if (!Set.of("GUIDE", "SUMMARY", "ANSWER", "REFERENCE").contains(type)) {
            markDiscussionSkipped(runId, candidate, "DISCUSSION_SEGMENT_REJECTED_UNSUPPORTED_TYPE");
            return false;
        }
        return true;
    }

    private boolean riskSensitiveDiscussion(DiscussionSegmentCandidate candidate) {
        String lower = candidate.segmentText().toLowerCase(Locale.ROOT);
        return containsAny(lower, "ретрит", "психодел", "медицин", "здоров", "боляч", "тревож", "security", "локац", "location", "работать из другой страны", "юрид", "legal", "финанс", "кредит", "увол", "hr", "резюме", "employment", "безопасн");
    }

    private boolean freshAfterControlledEnableTime(DiscussionSegmentCandidate candidate) {
        String value = settings.getString("discussionSegmentControlledEnableTime", "");
        if (value == null || value.isBlank()) return false;
        try {
            Instant enableTime = Instant.parse(value.trim());
            Message last = candidate.messages().get(candidate.messages().size() - 1).message();
            return nullSafeInstant(last.messageDate()).isAfter(enableTime) || nullSafeInstant(last.messageDate()).equals(enableTime);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean discussionDailyLimitReached() {
        int limit = settings.getInt("discussionSegmentMaxMaterialsPerDay", 3);
        if (limit <= 0) return true;
        return count("SELECT count(*) FROM knowledge_items WHERE source_cluster_type = 'DISCUSSION_SEGMENT' AND deleted_at IS NULL AND created_at >= date_trunc('day', now())") >= limit;
    }

    private boolean discussionChatTopicLimitReached(DiscussionSegmentCandidate candidate) {
        int limit = settings.getInt("discussionSegmentMaxMaterialsPerChatTopicPerDay", 1);
        if (limit <= 0) return true;
        Message first = candidate.messages().get(0).message();
        Long topic = first.forumTopicId() == null ? first.messageThreadId() : first.forumTopicId();
        return count("""
                SELECT count(*)
                FROM knowledge_items ki
                JOIN discussion_segments ds ON ds.id = ki.source_cluster_id AND ki.source_cluster_type = 'DISCUSSION_SEGMENT'
                WHERE ki.deleted_at IS NULL
                  AND ki.created_at >= date_trunc('day', now())
                  AND ds.account_id = ?
                  AND ds.telegram_chat_id = ?
                  AND COALESCE(ds.forum_topic_id, ds.message_thread_id, -1) = COALESCE(?, -1)
                """, first.accountId(), first.telegramChatId(), topic) >= limit;
    }

    private boolean discussionDuplicateExists(DiscussionSegmentCandidate candidate) {
        List<Long> rawIds = candidate.messages().stream().map(i -> i.message().id()).toList();
        if (rawIds.isEmpty()) return false;
        String values = rawIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String sql = """
                WITH selected(raw_id) AS (SELECT unnest(ARRAY[
                """ + values + """
                ]::bigint[])), existing AS (
                    SELECT ki.id, count(DISTINCT rm.id) AS overlap
                    FROM knowledge_items ki
                    JOIN knowledge_item_sources kis ON kis.knowledge_item_id = ki.id
                    JOIN dataset_messages dm ON dm.id = kis.dataset_message_id
                    LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
                    JOIN selected s ON s.raw_id = rm.id
                    WHERE ki.deleted_at IS NULL
                    GROUP BY ki.id
                    HAVING count(DISTINCT rm.id)::numeric / ?::numeric >= 0.8
                ) SELECT count(*) FROM existing
                """;
        return count(sql, rawIds.size()) > 0;
    }

    private DiscussionDedupeResult dedupeDiscussionSegments(List<DiscussionSegmentCandidate> candidates) {
        List<DiscussionSegmentCandidate> accepted = new ArrayList<>();
        List<DiscussionSegmentCandidate> rejected = new ArrayList<>();
        for (DiscussionSegmentCandidate candidate : candidates.stream().sorted(this::compareDiscussionBestFirst).toList()) {
            DiscussionSegmentCandidate duplicateOf = accepted.stream()
                .filter(existing -> sameDiscussionScope(existing.messages().get(0).message(), candidate.messages().get(0).message()))
                .filter(existing -> rawOverlapRatio(existing, candidate) >= 0.60)
                .filter(existing -> timeWindowsOverlap(existing, candidate))
                .findFirst()
                .orElse(null);
            if (duplicateOf == null) accepted.add(candidate);
            else rejected.add(candidate.rejectedCopy("DISCUSSION_SEGMENT_REJECTED_OVERLAP_DUPLICATE", rawOverlapRatio(duplicateOf, candidate)));
        }
        accepted.sort((a, b) -> compareMessageOrder(a.messages().get(0).message(), b.messages().get(0).message()));
        return new DiscussionDedupeResult(accepted, rejected);
    }

    private List<DiscussionSegmentCandidate> applyDiscussionWindowLimit(List<DiscussionSegmentCandidate> candidates, List<DiscussionSegmentCandidate> rejected) {
        Map<String, List<DiscussionSegmentCandidate>> buckets = candidates.stream().collect(Collectors.groupingBy(this::discussionBucketKey));
        List<DiscussionSegmentCandidate> kept = new ArrayList<>();
        for (List<DiscussionSegmentCandidate> bucket : buckets.values()) {
            List<DiscussionSegmentCandidate> ordered = bucket.stream().sorted(this::compareDiscussionBestFirst).toList();
            List<DiscussionSegmentCandidate> bucketKept = new ArrayList<>();
            for (DiscussionSegmentCandidate candidate : ordered) {
                boolean distinctThird = bucketKept.size() == 2
                    && bucketKept.stream().noneMatch(existing -> existing.proposedMaterialType().equals(candidate.proposedMaterialType()))
                    && bucketKept.stream().allMatch(existing -> rawOverlapRatio(existing, candidate) < 0.30);
                if (bucketKept.size() < 2 || (bucketKept.size() < 3 && distinctThird)) bucketKept.add(candidate);
                else rejected.add(candidate.rejectedCopy("DISCUSSION_SEGMENT_REJECTED_WINDOW_LIMIT", null));
            }
            kept.addAll(bucketKept);
        }
        kept.sort((a, b) -> compareMessageOrder(a.messages().get(0).message(), b.messages().get(0).message()));
        return kept;
    }

    private int compareDiscussionBestFirst(DiscussionSegmentCandidate a, DiscussionSegmentCandidate b) {
        int byScore = Double.compare(b.score(), a.score());
        if (byScore != 0) return byScore;
        int bySignals = Integer.compare(usefulSignalCount(b), usefulSignalCount(a));
        if (bySignals != 0) return bySignals;
        int byRange = Integer.compare(sourceCountPenalty(a), sourceCountPenalty(b));
        if (byRange != 0) return byRange;
        int bySolution = Boolean.compare(hasSolutionLikeSignal(b), hasSolutionLikeSignal(a));
        if (bySolution != 0) return bySolution;
        return compareMessageOrder(a.messages().get(0).message(), b.messages().get(0).message());
    }

    private int usefulSignalCount(DiscussionSegmentCandidate candidate) {
        int count = 0;
        if (candidate.signals().isArray()) for (JsonNode signal : candidate.signals()) {
            String value = signal.asText();
            if (!"REPEATED_ENTITY".equals(value) && !"MULTI_MESSAGE_CONTEXT".equals(value)) count++;
        }
        return count;
    }

    private int sourceCountPenalty(DiscussionSegmentCandidate candidate) {
        int count = candidate.sourceCount();
        return count >= 2 && count <= 6 ? 0 : Math.abs(count - 6);
    }

    private boolean hasSolutionLikeSignal(DiscussionSegmentCandidate candidate) {
        String signals = candidate.signals().toString();
        return signals.contains("Q_AND_A_PAIR") || signals.contains("PROBLEM_SOLUTION") || signals.contains("FINAL_SUMMARY_SIGNAL") || signals.contains("CHECKLIST_OR_LIST");
    }

    private double rawOverlapRatio(DiscussionSegmentCandidate a, DiscussionSegmentCandidate b) {
        Set<Long> left = a.messages().stream().map(i -> i.message().id()).collect(Collectors.toSet());
        Set<Long> right = b.messages().stream().map(i -> i.message().id()).collect(Collectors.toSet());
        long intersection = left.stream().filter(right::contains).count();
        return intersection == 0 ? 0.0 : intersection / (double) Math.min(left.size(), right.size());
    }

    private boolean timeWindowsOverlap(DiscussionSegmentCandidate a, DiscussionSegmentCandidate b) {
        Instant aStart = nullSafeInstant(a.messages().get(0).message().messageDate());
        Instant aEnd = nullSafeInstant(a.messages().get(a.messages().size() - 1).message().messageDate());
        Instant bStart = nullSafeInstant(b.messages().get(0).message().messageDate());
        Instant bEnd = nullSafeInstant(b.messages().get(b.messages().size() - 1).message().messageDate());
        return !aEnd.isBefore(bStart) && !bEnd.isBefore(aStart);
    }

    private String discussionBucketKey(DiscussionSegmentCandidate candidate) {
        Message first = candidate.messages().get(0).message();
        long epochMinutes = nullSafeInstant(first.messageDate()).getEpochSecond() / 60;
        long bucket = epochMinutes / 20;
        return first.accountId() + ":" + first.telegramChatId() + ":" + first.forumTopicId() + ":" + first.messageThreadId() + ":" + bucket;
    }

    private void persistDiscussionSegment(long runId, DiscussionSegmentCandidate candidate) {
        try {
            Message first = candidate.messages().get(0).message();
            Message last = candidate.messages().get(candidate.messages().size() - 1).message();
            Long id = JdbcIds.insertReturningId(jdbc, """
                    INSERT INTO discussion_segments (account_id, telegram_chat_id, forum_topic_id, message_thread_id, start_message_date, end_message_date, source_count, combined_score, proposed_material_type, decision, rejection_reason, signals_json, suppression_reasons_json, segment_text, run_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                    """, first.accountId(), first.telegramChatId(), first.forumTopicId(), first.messageThreadId(), first.messageDate(), last.messageDate(), candidate.sourceCount(), candidate.score(), candidate.proposedMaterialType(), candidate.decision(), candidate.rejectionReason(), write(candidate.signals()), write(candidate.suppressionReasons()), candidate.segmentText(), runId);
            candidate.segmentId(id);
            persistShadowSemanticDecisionForDiscussionSegment(candidate);
            int index = 0;
            for (Intel item : candidate.messages()) {
                jdbc.update("""
                        INSERT INTO discussion_segment_sources (discussion_segment_id, raw_message_id, dataset_message_id, replay_run_message_id, order_index, role, text_preview, message_date)
                        SELECT ?, rm.id, ?, rrm.id, ?, ?, ?, ?
                        FROM dataset_messages dm
                        LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
                        LEFT JOIN replay_run_messages rrm ON rrm.run_id = ? AND rrm.dataset_message_id = dm.id
                        WHERE dm.id = ?
                        """, id, item.message().id(), index++, roleFor(item), preview(normalized(item), 500), item.message().messageDate(), runId, item.message().id());
            }
        } catch (RuntimeException ex) {
            log.debug("Discussion segment persistence skipped in non-database context: {}", ex.getMessage());
        }
    }

    private String roleFor(Intel item) {
        String lower = normalized(item).toLowerCase(Locale.ROOT);
        if (containsAny(lower, "?", "как ", "можно ли", "что делать", "хочу задать")) return "question";
        if (containsAny(lower, "решение", "работает", "помогает", "проверь", "лучше", "надо", "нужно")) return "solution";
        if (containsAny(lower, "например", "пример", "9.8$", "1.", "2.")) return "example";
        if (containsAny(lower, "github", "http://", "https://", "repo")) return "resource";
        return "context";
    }

    private String preview(String value, int max) { return value == null ? null : value.substring(0, Math.min(max, value.length())); }

    private void markDiscussionStage(long runId, DiscussionSegmentCandidate candidate, String stageName, String status, JsonNode details, Long providerCallId, String error) {
        ObjectNode metrics = object("segmentId", candidate.segmentId(), "sourceCount", candidate.sourceCount(), "providerCallId", providerCallId);
        metrics.set("details", details == null ? object() : details);
        stage(runId, stageName, status, candidate.sourceCount(), status != null && status.startsWith("REJECT") ? 0 : 1, 0, error == null ? 0 : 1, providerCallId == null ? 0 : 1, metrics, error, null);
    }

    private void markDiscussionSkipped(long runId, DiscussionSegmentCandidate candidate, String reason) {
        if (candidate.segmentId() != null) jdbc.update("UPDATE discussion_segments SET rejection_reason = ?, decision = ?, updated_at = now() WHERE id = ?", reason, reason.startsWith("LLM") ? "REJECTED_LOW_VALUE" : "DISCUSSION_SEGMENT_REJECTED_LOW_SCORE", candidate.segmentId());
        markDiscussionStage(runId, candidate, "KNOWLEDGE_GENERATION", "SKIPPED", object("reason", reason), null, reason);
    }
    private void persistShadowSemanticDecisionForMessage(long runId, long messageId, Message message) {
        if (!semanticTraceEnabled()) return;
        try {
            SemanticDecisionObject decision = SemanticDecisionObject.shadowForMessage(
                messageId,
                messageUnitScope(message),
                processableScope(runId),
                json
            );
            jdbc.update(
                "UPDATE replay_run_messages SET semantic_decision_json = ?::jsonb, updated_at = now() WHERE run_id = ? AND dataset_message_id = ?",
                write(json.valueToTree(decision)),
                runId,
                messageId
            );
        } catch (RuntimeException ex) {
            handleSemanticTraceFailure("message", ex);
        }
    }

    private void persistShadowSemanticDecisionForDiscussionSegment(DiscussionSegmentCandidate candidate) {
        if (!semanticTraceEnabled() || candidate.segmentId() == null) return;
        try {
            List<Long> sourceIds = candidate.messages().stream().map(item -> item.message().id()).toList();
            SemanticDecisionObject decision = SemanticDecisionObject.shadowForDiscussionSegment(
                candidate.segmentId(),
                sourceIds,
                discussionUnitScope(candidate),
                "REPLAY_DISCUSSION_SEGMENT",
                json
            );
            jdbc.update(
                "UPDATE discussion_segments SET semantic_decision_json = ?::jsonb, updated_at = now() WHERE id = ?",
                write(json.valueToTree(decision)),
                candidate.segmentId()
            );
        } catch (RuntimeException ex) {
            handleSemanticTraceFailure("discussion-segment", ex);
        }
    }

    private boolean semanticTraceEnabled() {
        return settings.getInt("semanticTraceShadowEnabled", 1) == 1;
    }

    private boolean classicalMlShadowEnabled() {
        return settings.getInt("classicalMlShadowEnabled", 1) == 1;
    }

    private boolean classicalMlSoftFail() {
        return settings.getInt("classicalMlSoftFail", 1) == 1;
    }

    private void classicalMlMessageShadow(long runId, List<Intel> intel, Map<String, BigDecimal> metrics) {
        if (!classicalMlShadowEnabled()) return;
        long started = System.nanoTime();
        int traced = 0;
        for (Intel item : intel) {
            if (blank(item.normalizedText())) continue;
            try {
                JsonNode classifierLabels = oneOrNull("""
                        SELECT labels_json::text
                        FROM message_classifications
                        WHERE run_id = ? AND dataset_message_id = ?
                        """, runId, item.message().id());
                ObjectNode featureStore = featureVectorBuilder.buildMessageFeatures(
                    runId,
                    item.message().id(),
                    item.normalizedText(),
                    item.features(),
                    item.hardSignal(),
                    item.ruleDecision(),
                    item.features().path("weakLabels"),
                    classifierLabels == null ? json.createArrayNode() : classifierLabels
                );
                StageDecisionSnapshot snapshot = stageDecisionEngine.decideMessage(String.valueOf(item.message().id()), item.normalizedText(), featureStore);
                RouteIntelligenceDecision routeDecision = routeIntelligenceEngine.decide(
                    "MESSAGE",
                    String.valueOf(item.message().id()),
                    featureStore,
                    snapshot.stageResults(),
                    object("ruleDecision", item.ruleDecision(), "hardSignal", item.hardSignal())
                );
                jdbc.update("""
                        UPDATE message_intelligence
                        SET feature_store_json = ?::jsonb,
                            feature_version = ?,
                            classical_ml_stage_results_json = ?::jsonb
                        WHERE run_id = ? AND dataset_message_id = ?
                        """,
                    write(snapshot.featureStore()),
                    snapshot.featureVersion(),
                    write(snapshot.stageResults()),
                    runId,
                    item.message().id()
                );
                updateSemanticDecisionTrace(runId, item.message().id(), "MESSAGE", snapshot, routeDecision);
                traced++;
            } catch (RuntimeException ex) {
                handleClassicalMlFailure("message", ex);
            }
        }
        metric(metrics, "classical_ml_message_traces", traced);
        stage(runId, "CLASSICAL_ML_MESSAGE", "COMPLETED", intel.size(), traced, Math.max(0, intel.size() - traced), 0, traced > 0 ? 1 : 0,
            object("featureVersion", FeatureVectorBuilder.FEATURE_VERSION, "traced", traced), null, elapsed(started));
    }

    private void classicalMlDiscussionShadow(long runId, List<DiscussionSegmentCandidate> candidates, Map<String, BigDecimal> metrics) {
        if (!classicalMlShadowEnabled()) return;
        long started = System.nanoTime();
        int traced = 0;
        for (DiscussionSegmentCandidate candidate : candidates) {
            if (candidate.segmentId() == null) continue;
            try {
                List<Long> sourceMessageIds = candidate.messages().stream().map(message -> message.message().id()).toList();
                ObjectNode aggregatedSignals = semanticAggregationEngine.aggregateDiscussionSignals(sourceMessageIds, candidate.signals());
                ObjectNode featureStore = featureVectorBuilder.buildDiscussionSegmentFeatures(
                    runId,
                    candidate.segmentId(),
                    candidate.segmentText(),
                    sourceMessageIds,
                    aggregatedSignals
                );
                StageDecisionSnapshot snapshot = stageDecisionEngine.decideDiscussionSegment(String.valueOf(candidate.segmentId()), candidate.segmentText(), featureStore);
                RouteIntelligenceDecision routeDecision = routeIntelligenceEngine.decide(
                    "DISCUSSION_SEGMENT",
                    String.valueOf(candidate.segmentId()),
                    featureStore,
                    snapshot.stageResults(),
                    object("proposedMaterialType", candidate.proposedMaterialType(), "score", candidate.score(), "sourceCount", candidate.sourceCount())
                );
                jdbc.update("""
                        UPDATE discussion_segments
                        SET feature_store_json = ?::jsonb,
                            feature_version = ?,
                            classical_ml_stage_results_json = ?::jsonb,
                            updated_at = now()
                        WHERE id = ?
                        """,
                    write(snapshot.featureStore()),
                    snapshot.featureVersion(),
                    write(snapshot.stageResults()),
                    candidate.segmentId()
                );
                updateDiscussionSemanticDecisionTrace(candidate.segmentId(), snapshot, routeDecision);
                traced++;
            } catch (RuntimeException ex) {
                handleClassicalMlFailure("discussion-segment", ex);
            }
        }
        metric(metrics, "classical_ml_discussion_traces", traced);
        stage(runId, "CLASSICAL_ML_DISCUSSION_SEGMENT", "COMPLETED", candidates.size(), traced, Math.max(0, candidates.size() - traced), 0, traced > 0 ? 1 : 0,
            object("featureVersion", FeatureVectorBuilder.FEATURE_VERSION, "traced", traced), null, elapsed(started));
    }

    private void classicalMlClusterShadow(long runId, List<Cluster> clusters, Map<Long, Double> finalScores, Map<String, JsonNode> topicAnalytics, Map<String, BigDecimal> metrics) {
        if (!classicalMlShadowEnabled()) return;
        long started = System.nanoTime();
        int traced = 0;
        for (Cluster cluster : clusters) {
            try {
                JsonNode clusterScore = oneOrNull("""
                        SELECT row_to_json(t)::jsonb::text
                        FROM (
                            SELECT usefulness_score, pain_score, wtp_score, publishability_score, novelty_score, trend_score,
                                   duplicate_penalty, spam_penalty, unsafe_penalty, final_score
                            FROM cluster_scores
                            WHERE run_id = ? AND cluster_type = 'MACRO' AND cluster_id = ?
                            ORDER BY id DESC LIMIT 1
                        ) t
                        """, runId, cluster.id());
                JsonNode analytics = topicAnalytics.getOrDefault(firstWord(cluster.title()), object("topicKey", firstWord(cluster.title()), "messageCount", cluster.members().size()));
                ObjectNode featureStore = featureVectorBuilder.buildClusterFeatures(
                    runId,
                    cluster.id(),
                    cluster.title(),
                    finalScores.getOrDefault(cluster.id(), cluster.score()),
                    cluster.members(),
                    analytics,
                    clusterScore == null ? object() : clusterScore
                );
                StageDecisionSnapshot snapshot = stageDecisionEngine.decideCluster(String.valueOf(cluster.id()), cluster.title(), featureStore);
                RouteIntelligenceDecision routeDecision = routeIntelligenceEngine.decide(
                    "CLUSTER",
                    String.valueOf(cluster.id()),
                    featureStore,
                    snapshot.stageResults(),
                    object("finalScore", finalScores.getOrDefault(cluster.id(), cluster.score()), "memberCount", cluster.members().size())
                );
                jdbc.update("""
                        UPDATE macroclusters
                        SET feature_store_json = ?::jsonb,
                            feature_version = ?,
                            classical_ml_stage_results_json = ?::jsonb,
                            metadata_json = COALESCE(metadata_json, '{}'::jsonb) || jsonb_build_object('routeIntelligence', ?::jsonb)
                        WHERE id = ?
                        """,
                    write(snapshot.featureStore()),
                    snapshot.featureVersion(),
                    write(snapshot.stageResults()),
                    write(json.valueToTree(routeDecision)),
                    cluster.id()
                );
                traced++;
            } catch (RuntimeException ex) {
                handleClassicalMlFailure("cluster", ex);
            }
        }
        metric(metrics, "classical_ml_cluster_traces", traced);
        stage(runId, "CLASSICAL_ML_CLUSTER", "COMPLETED", clusters.size(), traced, Math.max(0, clusters.size() - traced), 0, traced > 0 ? 1 : 0,
            object("featureVersion", FeatureVectorBuilder.FEATURE_VERSION, "traced", traced), null, elapsed(started));
    }

    private void updateSemanticDecisionTrace(long runId, long datasetMessageId, String targetType, StageDecisionSnapshot snapshot, RouteIntelligenceDecision routeDecision) {
        JsonNode existing = oneOrNull("""
                SELECT semantic_decision_json::text
                FROM replay_run_messages
                WHERE run_id = ? AND dataset_message_id = ?
                """, runId, datasetMessageId);
        ObjectNode root = existing != null && existing.isObject() ? (ObjectNode) existing.deepCopy() : object();
        root.put("modelVersion", "classical-ml-bootstrap-v1");
        root.put("featureVersion", snapshot.featureVersion());
        root.set("classicalMlResult", snapshot.stageResults());
        root.set("routeIntelligence", json.valueToTree(routeDecision));
        ObjectNode trace = root.with("trace");
        trace.put("targetType", targetType);
        trace.put("classicalMlShadow", true);
        trace.set("stageDecisionSnapshot", json.valueToTree(snapshot));
        jdbc.update(
            "UPDATE replay_run_messages SET semantic_decision_json = ?::jsonb, updated_at = now() WHERE run_id = ? AND dataset_message_id = ?",
            write(root),
            runId,
            datasetMessageId
        );
    }

    private void updateDiscussionSemanticDecisionTrace(long segmentId, StageDecisionSnapshot snapshot, RouteIntelligenceDecision routeDecision) {
        JsonNode existing = oneOrNull("SELECT semantic_decision_json::text FROM discussion_segments WHERE id = ?", segmentId);
        ObjectNode root = existing != null && existing.isObject() ? (ObjectNode) existing.deepCopy() : object();
        root.put("modelVersion", "classical-ml-bootstrap-v1");
        root.put("featureVersion", snapshot.featureVersion());
        root.set("classicalMlResult", snapshot.stageResults());
        root.set("routeIntelligence", json.valueToTree(routeDecision));
        ObjectNode trace = root.with("trace");
        trace.put("targetType", "DISCUSSION_SEGMENT");
        trace.put("classicalMlShadow", true);
        trace.set("stageDecisionSnapshot", json.valueToTree(snapshot));
        jdbc.update(
            "UPDATE discussion_segments SET semantic_decision_json = ?::jsonb, updated_at = now() WHERE id = ?",
            write(root),
            segmentId
        );
    }

    private void handleClassicalMlFailure(String scope, RuntimeException ex) {
        if (classicalMlSoftFail()) {
            log.warn("[CLASSICAL_ML_SHADOW_FAILED] scope={} error={}", scope, ex.toString());
            return;
        }
        throw ex;
    }

    private void handleSemanticTraceFailure(String scope, RuntimeException ex) {
        if (settings.getInt("semanticTraceSoftFail", 1) == 1) {
            log.warn("[SEMANTIC_TRACE_SOFT_FAIL] scope={} error={}", scope, ex.getMessage());
            return;
        }
        throw ex;
    }

    private String processableScope(long runId) {
        ReplayRun run = getRun(runId);
        if (run != null && "LIVE_AUTO_RAW_MESSAGES".equals(run.pipelineVersion())) {
            return "LIVE_AUTO_REPLAY";
        }
        return "FULL_REPLAY";
    }

    private String messageUnitScope(Message message) {
        return "account:" + message.accountId()
            + "/chat:" + message.telegramChatId()
            + "/topic:" + message.forumTopicId()
            + "/thread:" + message.messageThreadId();
    }

    private String discussionUnitScope(DiscussionSegmentCandidate candidate) {
        Message first = candidate.messages().get(0).message();
        return messageUnitScope(first);
    }

    private Long decisionCoreMessage(long runId, long datasetMessageId, JsonNode features, String normalizedText, String ruleDecision, boolean hardSignal) {
        return decisionCore == null ? null : decisionCore.upsertMessageDecision(runId, datasetMessageId, features, normalizedText, ruleDecision, hardSignal);
    }

    private Long decisionCoreMessageId(long runId, long datasetMessageId) {
        return decisionCore == null ? null : decisionCore.messageDecisionObjectId(runId, datasetMessageId);
    }

    private Long decisionCoreCluster(long runId, String level, long clusterId, Collection<Long> members, String artifactType, double score, JsonNode report) {
        return decisionCore == null ? null : decisionCore.upsertClusterDecision(runId, level, clusterId, members, artifactType, BigDecimal.valueOf(score), report);
    }

    private Long decisionCoreClusterId(long runId, String level, long clusterId) {
        if (decisionCore == null || !decisionCore.shadowEnabled()) return null;
        return jdbc.query("""
            SELECT id FROM semantic_decision_objects
            WHERE decision_version = ? AND object_type = 'cluster' AND run_id = ? AND cluster_level = ? AND cluster_id = ?
            ORDER BY id DESC LIMIT 1
            """, rs -> rs.next() ? rs.getLong(1) : null, com.larbcorp.neuroinfogrinder2.decisioncore.SemanticDecisionCoreObject.DECISION_VERSION, runId, level, clusterId);
    }

    private void decisionCoreDiscussion(long runId, DiscussionSegmentCandidate candidate, boolean accepted) {
        if (decisionCore == null || candidate.segmentId() == null) return;
        List<Long> members = candidate.messages().stream().map(i -> i.message().id()).toList();
        Long decisionObjectId = decisionCore.upsertDiscussionDecision(runId, candidate.segmentId(), members, candidate.proposedMaterialType(), BigDecimal.valueOf(candidate.score()), candidate.decision(), candidate.rejectionReason(), candidate.signals());
        decisionCoreLedger(decisionObjectId, runId, "discussion:" + candidate.segmentId(), "discussion:" + candidate.segmentId(), DecisionCoreEnums.CandidateType.DISCUSSION_SEGMENT, accepted ? DecisionCoreEnums.LedgerEventType.CANDIDATE_CREATED : DecisionCoreEnums.LedgerEventType.CANDIDATE_REJECTED, false, List.of(), null, null, BigDecimal.valueOf(candidate.score()), null, accepted ? DecisionCoreEnums.FinalRoute.ROUTE_TO_DISCUSSION_ASSEMBLY : DecisionCoreEnums.FinalRoute.REJECT_FOR_MATERIAL_NOW, null, candidate.proposedMaterialType(), List.of(accepted ? "DISCUSSION_SEGMENT_CANDIDATE" : candidate.rejectionReason()), members, List.of(), object("sourceCount", candidate.sourceCount(), "signals", candidate.signals()));
    }

    private Long decisionCoreDiscussionId(long runId, Long segmentId) {
        if (decisionCore == null || segmentId == null || !decisionCore.shadowEnabled()) return null;
        return jdbc.query("""
            SELECT id FROM semantic_decision_objects
            WHERE decision_version = ? AND object_type = 'discussion_segment' AND run_id = ? AND object_id = ?
            ORDER BY id DESC LIMIT 1
            """, rs -> rs.next() ? rs.getLong(1) : null, com.larbcorp.neuroinfogrinder2.decisioncore.SemanticDecisionCoreObject.DECISION_VERSION, runId, segmentId);
    }

    private void decisionCoreObserve(Long decisionObjectId, long runId, String stageName, String observerName, String observationType, JsonNode payload, Collection<String> reasons, Double confidence) {
        if (decisionCore != null) decisionCore.observe(decisionObjectId, runId, stageName, observerName, observationType, payload, reasons, confidence);
    }

    private void decisionCoreRetain(long runId, long datasetMessageId, Long decisionObjectId, String role, String reason) {
        if (decisionCore != null) decisionCore.retainContext(decisionObjectId, runId, datasetMessageId, role, reason, Duration.ofDays(14));
    }

    private void decisionCoreLedger(Long decisionObjectId, long runId, String candidateGroupId, String candidateId, DecisionCoreEnums.CandidateType candidateType, DecisionCoreEnums.LedgerEventType eventType, boolean winner, List<String> competitors, String duplicateAnchorId, Long providerCallId, BigDecimal rankScore, DecisionCoreEnums.FinalRoute routeBefore, DecisionCoreEnums.FinalRoute routeAfter, String artifactTypeBefore, String artifactTypeAfter, Collection<String> reasonCodes, Collection<Long> contextNodesRetained, Collection<Long> excludedMessageIds, JsonNode details) {
        if (decisionCore != null) decisionCore.ledger(decisionObjectId, runId, candidateGroupId, candidateId, candidateType, eventType, winner, competitors, duplicateAnchorId, providerCallId, rankScore, routeBefore, routeAfter, artifactTypeBefore, artifactTypeAfter, reasonCodes, contextNodesRetained, excludedMessageIds, details);
    }

    private void decisionCoreRank(Long decisionObjectId, long runId, String candidateGroupId, String candidateType, int rankPosition, BigDecimal rankScore, JsonNode components, boolean selectedForLlm, Collection<String> reasons) {
        if (decisionCore != null) decisionCore.rankCandidate(decisionObjectId, runId, candidateGroupId, candidateType, rankPosition, rankScore, components, selectedForLlm, reasons);
    }

    private void decisionCoreSafety(Long decisionObjectId, long runId, String safetyClass, boolean hardBlock, boolean manualReview, boolean riskSignal, boolean warningAllowed, BigDecimal riskScore, Collection<String> riskFlags, JsonNode observations) {
        if (decisionCore != null) decisionCore.recordSafety(decisionObjectId, runId, safetyClass, hardBlock, manualReview, riskSignal, warningAllowed, riskScore, riskFlags, observations);
    }

    private void decisionCoreEnqueueLink(Long decisionObjectId, long datasetMessageId, String url) {
        if (decisionCore != null) decisionCore.enqueueLink(decisionObjectId, datasetMessageId, url, 50);
    }

    private void decisionCoreRelation(long runId, long sourceMessageId, long targetMessageId, String edgeType, BigDecimal weight, boolean negative, JsonNode evidence) {
        if (decisionCore != null) decisionCore.relationEdge(runId, sourceMessageId, targetMessageId, edgeType, weight, negative, evidence);
    }

    private void decisionCoreDedupeIdentity(String type, String value, Long decisionObjectId, JsonNode metadata) {
        if (decisionCore != null) decisionCore.dedupeIdentity(type, value, BigDecimal.ONE, decisionObjectId, null, metadata);
    }

    private String singleMessageMaterialBlockReason(SingleMessageCandidate candidate, Intel intel) {
        String text = (intel == null || intel.normalizedText() == null) ? "" : intel.normalizedText().toLowerCase(Locale.ROOT);
        if (containsAny(text,
                "правила группы",
                "ознакомились с правилами",
                "подтвердите", "#whois", "!report", "chatkeeperbot",
                "закрепленными сообщениями", "закреплёнными сообщениями")) {
            return "RULES_ONBOARDING_SINGLE_MESSAGE_BLOCKED";
        }
        if ("OTHER".equals(candidate.requiredArtifactType()) && settings.getInt("allowOtherSingleMessageMaterials", 0) != 1) {
            return "OTHER_SINGLE_MESSAGE_BLOCKED";
        }
        return null;
    }

    private void rejectSingleMessage(long runId, long messageId, Double score, String reason, JsonNode signals) {
        rejectSingleMessage(runId, messageId, score, reason, signals, object());
    }
    private void rejectSingleMessage(long runId, long messageId, Double score, String reason, JsonNode signals, JsonNode breakdown) {
        jdbc.update("UPDATE replay_run_messages SET final_decision = 'REJECTED_SINGLE_MESSAGE', single_message_score = ?, single_message_rejection_reason = ?, single_message_signals_json = ?::jsonb, single_message_score_breakdown_json = ?::jsonb, updated_at = now() WHERE run_id = ? AND dataset_message_id = ?", score, reason, write(signals == null ? json.createArrayNode() : signals), write(breakdown == null ? object() : breakdown), runId, messageId);
        Long decisionObjectId = decisionCoreMessageId(runId, messageId);
        DecisionCoreEnums.FinalRoute route = "NEEDS_LINK_ENRICHMENT".equals(reason) ? DecisionCoreEnums.FinalRoute.ROUTE_TO_LINK_ENRICHMENT : ("LOW_VALUE".equals(reason) ? DecisionCoreEnums.FinalRoute.REJECT_FOR_MATERIAL_NOW : DecisionCoreEnums.FinalRoute.RETAIN_FOR_CONTEXT);
        decisionCoreLedger(decisionObjectId, runId, "message:" + messageId, "single:" + messageId, DecisionCoreEnums.CandidateType.SINGLE_MESSAGE, DecisionCoreEnums.LedgerEventType.CANDIDATE_REJECTED, false, List.of(), null, null, score == null ? null : BigDecimal.valueOf(score), DecisionCoreEnums.FinalRoute.ROUTE_TO_SINGLE_CANDIDATE, route, null, null, List.of(reason), route == DecisionCoreEnums.FinalRoute.RETAIN_FOR_CONTEXT ? List.of(messageId) : List.of(), List.of(), breakdown == null ? object() : breakdown);
        if (route == DecisionCoreEnums.FinalRoute.RETAIN_FOR_CONTEXT) decisionCoreRetain(runId, messageId, decisionObjectId, "context", reason);
    }
    private void persistRejectedSignal(long runId, Intel intel, String text, String rejectionReason, MessageUsefulnessResult usefulness) {
        if (knowledgeSignalService == null || "LOW_VALUE".equals(rejectionReason)) return;
        try {
            JsonNode source = oneOrNull("""
                    SELECT rm.id AS raw_id, rrm.id AS replay_run_message_id
                    FROM dataset_messages dm
                    LEFT JOIN raw_messages rm ON rm.account_id = dm.account_id
                        AND rm.telegram_chat_id = dm.telegram_chat_id
                        AND rm.telegram_message_id = dm.telegram_message_id
                    LEFT JOIN replay_run_messages rrm ON rrm.run_id = ? AND rrm.dataset_message_id = dm.id
                    WHERE dm.id = ?
                    """, runId, intel.message().id());
            Long rawId = jsonLong(source, "raw_id");
            Long replayRunMessageId = jsonLong(source, "replay_run_message_id");
            knowledgeSignalService.upsertRejectedSingleMessageSignal(new KnowledgeSignalService.RejectedSingleMessageSignal(
                    rawId,
                    intel.message().id(),
                    runId,
                    replayRunMessageId,
                    intel.message().telegramChatId(),
                    intel.message().forumTopicId(),
                    text,
                    rejectionReason,
                    intel.features(),
                    usefulness
            ));
        } catch (RuntimeException ex) {
            log.warn("knowledge signal persistence skipped runId={} datasetMessageId={} reason={} error={}", runId, intel.message().id(), rejectionReason, ex.getMessage());
        }
    }
    private Long jsonLong(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asLong() : null;
    }
    private ObjectNode usefulnessJson(MessageUsefulnessResult result) {
        ObjectNode node = json.createObjectNode();
        node.put("contentClass", result.contentClass());
        node.put("usefulnessClass", result.usefulnessClass());
        node.put("sourceContextClass", result.sourceContextClass());
        node.put("safetyClass", result.safetyClass());
        node.put("candidateRoute", result.candidateRoute());
        node.put("proposedMaterialType", result.proposedMaterialType());
        node.put("overallScore", result.overallScore());
        ObjectNode dimensions = json.createObjectNode();
        result.scoreDimensions().forEach(dimensions::put);
        node.set("scoreDimensions", dimensions);
        ArrayNode positive = json.createArrayNode();
        result.positiveSignals().forEach(positive::add);
        node.set("positiveSignals", positive);
        ArrayNode negative = json.createArrayNode();
        result.negativeSignals().forEach(negative::add);
        node.set("negativeSignals", negative);
        node.put("rejectReason", result.rejectReason());
        node.put("humanReason", result.humanReason());
        return node;
    }
    private ArrayNode usefulnessSignals(MessageUsefulnessResult result) {
        ArrayNode signals = json.createArrayNode();
        result.positiveSignals().forEach(signals::add);
        result.negativeSignals().forEach(signals::add);
        signals.add("USEFULNESS_" + result.contentClass());
        if (result.proposedMaterialType() != null) signals.add("PROPOSED_MATERIAL_TYPE_" + result.proposedMaterialType());
        return signals;
    }
    private String promptSingle(SingleMessageCandidate sc, Intel intel, String type) {
        return promptSingle(sc, intel, List.of(intel), type, null);
    }
    private String promptSingle(SingleMessageCandidate sc, Intel intel, String type, RouteIntelligenceDecision routeDecision) {
        return promptSingle(sc, intel, List.of(intel), type, routeDecision);
    }
    private String promptSingle(SingleMessageCandidate sc, Intel intel, List<Intel> allIntel, String type, RouteIntelligenceDecision routeDecision) {
        ArrayNode localContext = nearbySingleMessageContext(intel, allIntel);
        return "Return strict JSON. Mode=SINGLE_MESSAGE. Type=" + type
            + ". Required artifactType=" + sc.requiredArtifactType()
            + ". Treat localContext as evidence for whether the message is standalone, part of a chain, promo noise, or risk content."
            + ". For judge use {decision,artifactType,title,summary,confidence,reason,recommendedTitle,generationInstructions}."
            + " Positive judge decisions may include NEWS_REFERENCE_MATERIAL_CANDIDATE, RESOURCE_REFERENCE_MATERIAL_CANDIDATE, STATUS_OUTAGE_MATERIAL_CANDIDATE, DIRECT_MATERIAL_READY."
            + " Reject decisions may include REJECTED_LOW_ACTIONABILITY, REJECTED_PROMO, REJECTED_LINK_ONLY, REJECTED_NEEDS_MORE_CONTEXT, REJECTED_ACCESS_CIRCUMVENTION, RISK_SENSITIVE_MANUAL_ONLY."
            + " For generation use {artifactType,title,summary,body,sources}; artifactType MUST equal required artifactType. " + generationStyleInstructions()
            + " Single message id=" + sc.messageId() + " score=" + sc.score() + " decision=" + sc.decision()
            + " signals=" + write(sc.signals())
            + " usefulness=" + write(sc.usefulness())
            + routePromptHint(routeDecision)
            + " text=\"" + (intel.normalizedText().length() > 2000 ? intel.normalizedText().substring(0, 2000) : intel.normalizedText()) + "\""
            + " localContext=" + write(localContext);
    }
    private String promptDiscussion(DiscussionSegmentCandidate dc, String type, JsonNode judge) {
        return promptDiscussion(dc, type, judge, null);
    }
    private String promptDiscussion(DiscussionSegmentCandidate dc, String type, JsonNode judge, RouteIntelligenceDecision routeDecision) {
        ArrayNode sources = json.createArrayNode();
        int index = 0;
        for (Intel source : dc.messages()) {
            sources.addObject()
                .put("order", index++)
                .put("datasetMessageId", source.message().id())
                .put("role", roleFor(source))
                .put("messageDate", source.message().messageDate() == null ? null : source.message().messageDate().toInstant().toString())
                .put("text", normalized(source));
        }
        return "Return strict JSON. Mode=DISCUSSION_SEGMENT. Type=" + type
            + ". Use only the ordered source messages. Do not hallucinate. Output language follows source language."
            + " For judge use {accepted,decision,material_type,title,reason,confidence,source_message_roles,suggested_outline,rejection_reason}."
            + " Positive decisions: DISCUSSION_SEGMENT_MATERIAL_CANDIDATE or DIRECT_MATERIAL_READY. Reject decisions: REJECTED_LOW_VALUE, REJECTED_NEEDS_MORE_CONTEXT, REJECTED_DUPLICATE, REJECTED_UNSAFE_OR_UNSUPPORTED, REJECTED_PROMO_OR_NOISE."
            + " For generation use {artifactType,title,summary,body,sources}; artifactType MUST equal proposedMaterialType. Keep careful/source-aware tone for health, legal, security, and employment topics. " + generationStyleInstructions()
            + " If sources discuss external provider docs, API limits, pricing/tariffs, cache timing, model availability, rate limits, billing, cost behavior, or provider-specific behavior, include this caveat verbatim in the material body: Проверьте актуальную документацию провайдера: лимиты, тарифы, поведение cache и доступность моделей могут меняться."
            + " segmentId=" + dc.segmentId() + " score=" + dc.score() + " proposedMaterialType=" + dc.proposedMaterialType()
            + " signals=" + write(dc.signals())
            + " judge=" + (judge == null ? "{}" : write(judge))
            + routePromptHint(routeDecision)
            + " orderedSources=" + write(sources);
    }

    private String generationStyleInstructions() {
        return "Write the final material as a standalone guide, answer, summary, or reference, not as a recap of chat messages."
            + " Start with useful information directly. Do not write as a recap of chat messages. Do not use meta-chat framing."
            + " Forbidden phrases in body: участники сообщества; участники обсуждения; пользователи обсуждали; в чате; в обсуждении; авторы сообщений; из сообщений следует; в источнике говорится; сообщество пришло к выводу; один из участников; по словам участников."
            + " If source limits must be stated, use neutral caveat: Данные основаны на предоставленном контексте."
            + " Prefer compact materials over long articles."
            + " For GUIDE output include title, short summary, practical steps, checks/checklist, caveats, and what to avoid."
            + " GUIDE should usually be a mini-guide: 2-5 short sections or 4-8 short bullets, normally under 220 words unless the provided evidence clearly requires more."
            + " If the evidence is narrow, write a focused checklist instead of a broad tutorial."
            + " For ANSWER output include direct answer first, explanation, caveats, and related checks."
            + " For SUMMARY output include key points, grouped observations, implications, and possible next actions."
            + " For REFERENCE output include what it is, when useful, how to verify/use, and caveats."
            + " Preserve useful facts from source context. Material body may use only facts from provided source context."
            + " Do not invent exact API docs, pricing, time limits, model availability, SLA, provider behavior, legal, medical, or security claims."
            + " For external provider/API behavior always add caveat: Проверьте актуальную документацию провайдера: лимиты, тарифы, поведение cache и доступность моделей могут меняться.";
    }
    private ArrayNode nearbySingleMessageContext(Intel center, List<Intel> allIntel) {
        ArrayNode rows = json.createArrayNode();
        if (center == null || allIntel == null || allIntel.size() <= 1) {
            return rows;
        }
        List<Intel> nearby = allIntel.stream()
            .filter(item -> item != null && item.message().id() != center.message().id())
            .filter(item -> sameDiscussionScope(center.message(), item.message()))
            .filter(item -> withinWindow(center.message(), item.message(), 10))
            .sorted((a, b) -> compareMessageOrder(a.message(), b.message()))
            .limit(6)
            .toList();
        Instant centerAt = nullSafeInstant(center.message().messageDate());
        for (Intel item : nearby) {
            long relativeMinutes = Duration.between(centerAt, nullSafeInstant(item.message().messageDate())).toMinutes();
            rows.addObject()
                .put("datasetMessageId", item.message().id())
                .put("relativeMinutes", relativeMinutes)
                .put("text", preview(normalized(item), 240));
        }
        return rows;
    }
    private String routePromptHint(RouteIntelligenceDecision routeDecision) {
        if (settings.getInt("classicalMlRoutingEnabled", 0) != 1) {
            return "";
        }
        if (routeDecision == null || routeDecision.reasonBundle() == null || routeDecision.reasonBundle().isMissingNode()) {
            return "";
        }
        return " routeHint=" + write(routeDecision.reasonBundle());
    }
    private RouteIntelligenceDecision loadMessageRouteDecision(long runId, long datasetMessageId) {
        JsonNode node = oneOrNull("""
                SELECT semantic_decision_json -> 'routeIntelligence'
                FROM replay_run_messages
                WHERE run_id = ? AND dataset_message_id = ?
                """, runId, datasetMessageId);
        return parseRouteDecision(node);
    }
    private RouteIntelligenceDecision loadDiscussionRouteDecision(Long segmentId) {
        if (segmentId == null) return null;
        JsonNode node = oneOrNull("SELECT semantic_decision_json -> 'routeIntelligence' FROM discussion_segments WHERE id = ?", segmentId);
        return parseRouteDecision(node);
    }
    private RouteIntelligenceDecision loadClusterRouteDecision(long clusterId) {
        JsonNode node = oneOrNull("SELECT metadata_json -> 'routeIntelligence' FROM macroclusters WHERE id = ?", clusterId);
        return parseRouteDecision(node);
    }
    private RouteIntelligenceDecision parseRouteDecision(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return null;
        }
        try {
            return json.treeToValue(node, RouteIntelligenceDecision.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
    private String firstPolicyReason(RouteIntelligenceDecision routeDecision, String fallback) {
        if (routeDecision == null || routeDecision.policyFlags() == null || !routeDecision.policyFlags().isArray() || routeDecision.policyFlags().isEmpty()) {
            return fallback;
        }
        return routeDecision.policyFlags().get(0).asText(fallback);
    }
    private JsonNode enrichReviewFeedbackContext(Map<String, Object> item, JsonNode existingContext) {
        ObjectNode root = existingContext != null && existingContext.isObject() ? (ObjectNode) existingContext.deepCopy() : object();
        String itemType = String.valueOf(item.getOrDefault("item_type", "MESSAGE"));
        long runId = ((Number) item.getOrDefault("run_id", 0L)).longValue();
        Long datasetMessageId = item.get("dataset_message_id") instanceof Number number ? number.longValue() : null;
        Long macroclusterId = item.get("macrocluster_id") instanceof Number number ? number.longValue() : null;
        JsonNode stageResults = null;
        JsonNode routeDecision = null;
        if (datasetMessageId != null && datasetMessageId > 0) {
            stageResults = oneOrNull("""
                    SELECT classical_ml_stage_results_json::text
                    FROM message_intelligence
                    WHERE run_id = ? AND dataset_message_id = ?
                    """, runId, datasetMessageId);
            JsonNode routeNode = oneOrNull("""
                    SELECT semantic_decision_json -> 'routeIntelligence'
                    FROM replay_run_messages
                    WHERE run_id = ? AND dataset_message_id = ?
                    """, runId, datasetMessageId);
            routeDecision = routeNode == null ? object() : routeNode;
        } else if (macroclusterId != null && macroclusterId > 0) {
            stageResults = oneOrNull("SELECT classical_ml_stage_results_json::text FROM macroclusters WHERE id = ?", macroclusterId);
            JsonNode routeNode = oneOrNull("SELECT metadata_json -> 'routeIntelligence' FROM macroclusters WHERE id = ?", macroclusterId);
            routeDecision = routeNode == null ? object() : routeNode;
        }
        root.set("reviewFeedback", reviewFeedbackService.buildStageFeedbackContext(itemType, datasetMessageId != null ? datasetMessageId : (macroclusterId == null ? 0L : macroclusterId), stageResults));
        if (routeDecision != null) {
            root.set("routeIntelligence", routeDecision);
        }
        return root;
    }
    private void createLabeling(long runId, Long messageId, Long microId, Long macroId, Long knowledgeId, String itemType, String text, JsonNode context, String label, String artifactType, String status, double confidence, int priority) {
        JsonNode suggestedLabels = context != null && context.path("labels").isArray()
                ? context.path("labels")
                : (label == null ? json.createArrayNode() : array(label));
        jdbc.update("""
                INSERT INTO labeling_items (
                    run_id, dataset_message_id, microcluster_id, macrocluster_id, knowledge_item_id, item_type,
                    text_snapshot, context_snapshot_json, suggested_label, suggested_labels_json, suggested_artifact_type,
                    suggested_decision, confidence, priority, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """, runId, messageId, microId, macroId, knowledgeId, itemType, text, write(context == null ? object() : context), label,
                write(suggestedLabels), artifactType, status, confidence, priority, status);
    }
    private void markClusterLlmSkip(long runId, Cluster c, String reason) { for (Long member : c.members()) jdbc.update("UPDATE replay_run_messages SET llm_skip_reason = ?, updated_at = now() WHERE run_id = ? AND dataset_message_id = ?", reason, runId, member); }
    private void upsertMessage(long runId, long messageId, String status, String ruleDecision, JsonNode ruleLabels, JsonNode scores, Long embeddingId, Long dedupeId, Long microId, Long macroId, boolean llmUsed, String skip, Long labelingId) { jdbc.update("INSERT INTO replay_run_messages (run_id, dataset_message_id, status, rule_decision, final_decision, scores_json, rule_labels_json, embedding_id, dedupe_group_id, microcluster_id, macrocluster_id, llm_used, llm_skip_reason, labeling_item_id) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (run_id, dataset_message_id) DO UPDATE SET status = EXCLUDED.status, rule_decision = EXCLUDED.rule_decision, final_decision = EXCLUDED.final_decision, rule_labels_json = EXCLUDED.rule_labels_json, updated_at = now()", runId, messageId, status, ruleDecision, ruleDecision, write(scores == null ? object() : scores), write(ruleLabels), embeddingId, dedupeId, microId, macroId, llmUsed, skip, labelingId); }
    private void syncRawBackedPipelineTrace(long runId) {
        jdbc.update("""
                UPDATE pipeline_message_intake i
                SET status = 'PROCESSED', reason = 'FULL_REPLAY_COMPLETED', replay_run_id = ?, updated_at = now()
                FROM dataset_messages dm
                JOIN replay_runs rr ON rr.dataset_id = dm.dataset_id
                JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
                WHERE rr.id = ? AND i.raw_message_id = rm.id
                """, runId, runId);
        markUnmatchedTraceStages(runId);
        syncTraceStage(runId, "normalization", "PROCESSED", null, null, object("source", "message_intelligence"), "EXISTS (SELECT 1 FROM message_intelligence mi WHERE mi.run_id = ? AND mi.dataset_message_id = dm.id)");
        syncTraceStage(runId, "cleanup", "PROCESSED", null, null, object("source", "rule_decision"), "EXISTS (SELECT 1 FROM message_intelligence mi WHERE mi.run_id = ? AND mi.dataset_message_id = dm.id)");
        syncTraceStage(runId, "dedupe", "PROCESSED", null, null, object("source", "dedupe_groups"), "EXISTS (SELECT 1 FROM replay_run_messages rrm WHERE rrm.run_id = ? AND rrm.dataset_message_id = dm.id)");
        syncTraceStage(runId, "rule_signals", "PROCESSED", null, null, object("source", "rule_scores"), "EXISTS (SELECT 1 FROM message_intelligence mi WHERE mi.run_id = ? AND mi.dataset_message_id = dm.id)");
        syncTraceStage(runId, "bootstrap_classification", "PROCESSED", null, null, object("source", "message_classifications"), "EXISTS (SELECT 1 FROM message_classifications mc WHERE mc.run_id = ? AND mc.dataset_message_id = dm.id)");
        String embeddingStatus = count("SELECT count(*) FROM replay_run_stages WHERE run_id = ? AND stage = 'BGE_EMBEDDINGS' AND status = 'PROCESSED_DEGRADED'", runId) > 0 ? "PROCESSED_DEGRADED" : "PROCESSED";
        String embeddingError = "PROCESSED_DEGRADED".equals(embeddingStatus) ? "DEGRADED_EMBEDDINGS" : null;
        syncTraceStage(runId, "embeddings", embeddingStatus, embeddingError, embeddingError, object("source", "message_embeddings"), "EXISTS (SELECT 1 FROM message_embeddings me WHERE me.run_id = ? AND me.dataset_message_id = dm.id)");
        syncTraceStage(runId, "clustering", "PROCESSED", null, null, object("source", "microclusters/macroclusters"), "EXISTS (SELECT 1 FROM replay_run_messages rrm WHERE rrm.run_id = ? AND rrm.dataset_message_id = dm.id AND rrm.microcluster_id IS NOT NULL)");
        syncTraceStage(runId, "single_message_detection", "PROCESSED", null, null, object("source", "replay_run_messages"), "EXISTS (SELECT 1 FROM replay_run_messages rrm WHERE rrm.run_id = ? AND rrm.dataset_message_id = dm.id AND rrm.final_decision IN ('SINGLE_MESSAGE_MATERIAL_CANDIDATE','DIRECT_MATERIAL_READY','REJECTED_SINGLE_MESSAGE'))");
        syncTraceStage(runId, "llm_judge", "PROCESSED", null, null, object("source", "provider_calls/cluster_scores"), "EXISTS (SELECT 1 FROM replay_run_messages rrm WHERE rrm.run_id = ? AND rrm.dataset_message_id = dm.id AND (rrm.macrocluster_id IS NOT NULL OR rrm.final_decision IN ('SINGLE_MESSAGE_MATERIAL_CANDIDATE','DIRECT_MATERIAL_READY')) AND EXISTS (SELECT 1 FROM provider_calls pc WHERE pc.run_id = rrm.run_id))");
        syncTraceStage(runId, "material_generation", "PROCESSED", null, null, object("source", "knowledge_items"), "EXISTS (SELECT 1 FROM knowledge_item_sources kis JOIN knowledge_items ki ON ki.id = kis.knowledge_item_id WHERE ki.run_id = ? AND kis.dataset_message_id = dm.id)");
        syncTraceStage(runId, "materials_publish", "PROCESSED", null, null, object("source", "knowledge_items"), "EXISTS (SELECT 1 FROM knowledge_item_sources kis JOIN knowledge_items ki ON ki.id = kis.knowledge_item_id WHERE ki.run_id = ? AND kis.dataset_message_id = dm.id)");
        markMaterialStagesRejectedByLlm(runId);
    }

    private void markMaterialStagesRejectedByLlm(long runId) {
        jdbc.update("""
                UPDATE pipeline_message_trace tr
                SET replay_run_id = ?, status = 'SKIPPED', error_code = 'LLM_REJECTED_ALL', error_message = 'LLM did not approve material generation for this candidate', updated_at = now()
                FROM dataset_messages dm
                JOIN replay_runs rr ON rr.dataset_id = dm.dataset_id
                JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
                WHERE rr.id = ?
                  AND tr.raw_message_id = rm.id
                  AND tr.stage_id IN ('material_generation','materials_publish')
                  AND EXISTS (SELECT 1 FROM replay_run_messages rrm WHERE rrm.run_id = ? AND rrm.dataset_message_id = dm.id AND rrm.final_decision IN ('SINGLE_MESSAGE_MATERIAL_CANDIDATE','DIRECT_MATERIAL_READY'))
                  AND EXISTS (SELECT 1 FROM provider_calls pc WHERE pc.run_id = ?)
                  AND NOT EXISTS (SELECT 1 FROM knowledge_item_sources kis JOIN knowledge_items ki ON ki.id = kis.knowledge_item_id WHERE ki.run_id = ? AND kis.dataset_message_id = dm.id)
                """, runId, runId, runId, runId, runId);
    }

    private void markUnmatchedTraceStages(long runId) {
        jdbc.update("""
                UPDATE pipeline_message_trace tr
                SET status = 'SKIPPED',
                    error_code = CASE WHEN EXISTS (SELECT 1 FROM replay_run_stages WHERE run_id = ? AND stage = 'BGE_EMBEDDINGS' AND error = 'NO_ELIGIBLE_EMBEDDING_INPUT') THEN 'NO_ELIGIBLE_EMBEDDING_INPUT' ELSE 'NO_EMBEDDING_ARTIFACTS' END,
                    error_message = CASE WHEN EXISTS (SELECT 1 FROM replay_run_stages WHERE run_id = ? AND stage = 'BGE_EMBEDDINGS' AND error = 'NO_ELIGIBLE_EMBEDDING_INPUT') THEN 'No eligible text input for embeddings' ELSE 'Embedding artifacts were not created' END,
                    updated_at = now()
                WHERE tr.replay_run_id = ?
                  AND tr.stage_id = 'embeddings'
                  AND tr.status IN ('PENDING','WAITING_FOR_WORKER')
                """, runId, runId, runId);
        jdbc.update("""
                UPDATE pipeline_message_trace tr
                SET status = 'SKIPPED', error_code = 'UPSTREAM_NOT_REACHED', error_message = 'Stage did not produce artifacts for this message', updated_at = now()
                WHERE tr.replay_run_id = ?
                  AND tr.stage_id IN ('embeddings','clustering','single_message_detection','llm_judge','material_generation','materials_publish')
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dataset_messages dm
                      JOIN replay_runs rr ON rr.dataset_id = dm.dataset_id
                      JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
                      WHERE rr.id = ? AND rm.id = tr.raw_message_id
                  )
                """, runId, runId);
        jdbc.update("""
                UPDATE pipeline_message_trace tr
                SET status = 'SKIPPED', error_code = 'NO_MATERIAL_CANDIDATES', error_message = 'No cluster or single-message material candidate was produced', updated_at = now()
                WHERE tr.replay_run_id = ?
                  AND tr.stage_id IN ('clustering','single_message_detection','llm_judge','material_generation','materials_publish')
                  AND tr.status IN ('PENDING','WAITING_FOR_WORKER')
                """, runId);
    }
    private void syncTraceStage(long runId, String stageId, String status, String errorCode, String errorMessage, JsonNode output, String existsPredicate) {
        jdbc.update(("""
                UPDATE pipeline_message_trace tr
                SET replay_run_id = ?, status = ?, error_code = ?, error_message = ?, output_json = ?::jsonb, started_at = COALESCE(tr.started_at, now()), finished_at = now(), duration_ms = COALESCE(tr.duration_ms, 0), updated_at = now()
                FROM dataset_messages dm
                JOIN replay_runs rr ON rr.dataset_id = dm.dataset_id
                JOIN raw_messages rm ON rm.account_id = dm.account_id AND rm.telegram_chat_id = dm.telegram_chat_id AND rm.telegram_message_id = dm.telegram_message_id
                WHERE rr.id = ? AND tr.raw_message_id = rm.id AND tr.stage_id = ? AND %s
                """).formatted(existsPredicate), runId, status, errorCode, errorMessage, write(output == null ? object() : output), runId, stageId, runId);
    }
    private void stage(long runId, String stage, String status, long input, long output, long skipped, long errors, long localCalls, JsonNode metrics, String error, Long latency) { jdbc.update("INSERT INTO replay_run_stages (run_id, stage, status, started_at, finished_at, input_count, output_count, skipped_count, error_count, local_model_call_count, latency_ms, metrics_json, error) VALUES (?, ?, ?, now(), now(), ?, ?, ?, ?, ?, ?, ?::jsonb, ?)", runId, stage, status, input, output, skipped, errors, localCalls, latency, write(metrics == null ? object() : metrics), error); }

    private void stage(long runId, String stage, String status, long input, long output, long skipped, long errors, long localCalls, JsonNode metrics, String error, long latency) { stage(runId, stage, status, input, output, skipped, errors, localCalls, metrics, error, Long.valueOf(latency)); }

    private Long nullableLong(ResultSet rs, String column) throws SQLException { long value = rs.getLong(column); return rs.wasNull() ? null : value; }
    private void recordModelCall(long runId, String stage, String op, int in, int out, String status, long latency, String error) { jdbc.update("INSERT INTO local_model_calls (run_id, stage, worker_id, operation, request_hash, input_count, output_count, status, latency_ms, error) VALUES (?, ?, (SELECT id FROM local_model_workers WHERE name='local-fastapi-worker' LIMIT 1), ?, ?, ?, ?, ?, ?, ?)", runId, stage, op, Hashing.sha256(stage + op + in + out + status), in, out, status, latency, error); }
    private void finish(long runId, String status, Map<String, BigDecimal> metrics, String error) { jdbc.update("UPDATE replay_runs SET status = ?, finished_at = now(), processed_messages = total_messages, provider_calls_total = (SELECT count(*) FROM provider_calls WHERE run_id = ?), estimated_cost_usd = (SELECT COALESCE(sum(estimated_cost_usd),0) FROM provider_calls WHERE run_id = ?), error = ? WHERE id = ?", status, runId, runId, error, runId); }
    private String terminalReason(List<Embedding> embeddings, List<Cluster> macro, List<SingleMessageCandidate> singleCandidates, long runId) { long runMessages = count("SELECT count(*) FROM replay_run_messages WHERE run_id = ?", runId); if (runMessages == 0) return "NO_RUN_MESSAGES"; if (embeddings.isEmpty() && count("SELECT count(*) FROM replay_run_stages WHERE run_id = ? AND stage = 'BGE_EMBEDDINGS' AND error = 'NO_ELIGIBLE_EMBEDDING_INPUT'", runId) == 0) return "NO_EMBEDDING_ARTIFACTS"; if (macro.isEmpty() && singleCandidates.isEmpty()) return "NO_MATERIAL_CANDIDATES"; if (!providerConfigured()) return "PROVIDER_NOT_CONFIGURED"; long materials = count("SELECT count(*) FROM knowledge_items WHERE run_id = ?", runId); return materials == 0 ? "LLM_REJECTED_ALL" : null; }
    private void updateWorker(ModelWorkerClient.WorkerHealth h) { jdbc.update("UPDATE local_model_workers SET health_status = ?, last_health_check_at = now(), metadata_json = ?::jsonb, updated_at = now() WHERE name = 'local-fastapi-worker'", h.status(), write(h.raw())); }
    private List<Message> loadMessages(long datasetId, int limit) { return jdbc.query("""
            SELECT id, text, caption, raw_json::text, entities_json::text, media_json::text,
                   account_id, telegram_chat_id, telegram_topic_id, telegram_topic_id AS message_thread_id,
                   message_date, created_at AS ingested_at
            FROM dataset_messages
            WHERE dataset_id = ?
            ORDER BY message_date NULLS LAST, id
            LIMIT ?
            """, (rs, n) -> new Message(rs.getLong("id"), rs.getString("text"), rs.getString("caption"), rs.getString("raw_json"), rs.getString("entities_json"), rs.getString("media_json"), rs.getLong("account_id"), rs.getLong("telegram_chat_id"), nullableLong(rs, "telegram_topic_id"), nullableLong(rs, "message_thread_id"), rs.getTimestamp("message_date"), rs.getTimestamp("ingested_at")), datasetId, limit); }
    private ReplayRun mapRun(ResultSet rs, int row) throws SQLException { return new ReplayRun(rs.getLong("id"), rs.getLong("dataset_id"), rs.getString("run_name"), rs.getString("mode"), rs.getString("pipeline_version"), rs.getString("status"), iso(rs, "started_at"), iso(rs, "finished_at"), rs.getInt("total_messages"), rs.getInt("processed_messages"), rs.getInt("provider_calls_total"), rs.getBigDecimal("estimated_cost_usd"), rs.getString("error")); }
    private JsonNode one(String sql, Object... args) { ArrayNode r = rows(sql, args); return r.isEmpty() ? object() : r.get(0); }
    private ArrayNode rows(String sql, Object... args) { ArrayNode a = json.createArrayNode(); jdbc.query(sql, (RowCallbackHandler) rs -> a.add(parse(rs.getString(1))), args); return a; }
    private long classifierModelId() { return jdbc.queryForObject("SELECT id FROM classifier_models WHERE name = 'BOOTSTRAP_BERT_CLASSIFIER'", Long.class); }
    private long embeddingModelId() { return jdbc.queryForObject("SELECT id FROM embedding_models WHERE name = 'BAAI/bge-m3'", Long.class); }
    private boolean providerConfigured() { String key = environment.getProperty("MODELHUB_API_KEY"); return key != null && !key.isBlank(); }
    private boolean approvedJudgeDecision(JsonNode response) { String decision = judgePayload(response).path("decision").asText("").trim().toUpperCase(Locale.ROOT); return Set.of("APPROVE", "APPROVED", "ACCEPT", "ACCEPTED", "VALID", "KEEP", "GENERATE", "YES", "SINGLE_MESSAGE_MATERIAL_CANDIDATE", "DISCUSSION_SEGMENT_MATERIAL_CANDIDATE", "DIRECT_MATERIAL_READY", "NEWS_REFERENCE_MATERIAL_CANDIDATE", "RESOURCE_REFERENCE_MATERIAL_CANDIDATE", "STATUS_OUTAGE_MATERIAL_CANDIDATE").contains(decision); }
    boolean isHardRouteBlock(RouteIntelligenceDecision routeDecision) {
        if (routeDecision == null || !routeDecision.blocked()) return false;
        String flags = routeDecision.policyFlags() == null ? "" : routeDecision.policyFlags().toString().toUpperCase(Locale.ROOT);
        return flags.contains("RISK") || flags.contains("DUPLICATE") || flags.contains("PROMO") || flags.contains("ABUSE") || flags.contains("FRAUD") || flags.contains("FINAL_GATE_REJECTED") || flags.contains("MANUAL_REVIEW");
    }
    boolean shouldApplyClassicalRouteBlock(RouteIntelligenceDecision routeDecision) {
        return settings.getInt("classicalMlRoutingEnabled", 0) == 1 && isHardRouteBlock(routeDecision);
    }
    boolean shouldAllowLightweightMaterialAfterJudgeReject(JsonNode response, RouteIntelligenceDecision routeDecision, double score, int sourceCount) {
        if (response == null || score < 0.55) return false;
        if (shouldApplyClassicalRouteBlock(routeDecision)) return false;
        String route = routeDecision == null || routeDecision.recommendedRoute() == null ? "" : routeDecision.recommendedRoute().trim().toUpperCase(Locale.ROOT);
        JsonNode payload = judgePayload(response);
        String text = (payload.path("decision").asText("") + " " + payload.path("title").asText("") + " " + payload.path("summary").asText("") + " " + payload.path("reason").asText("") + " " + payload.path("rationale").asText("")).toLowerCase(Locale.ROOT);
        if (text.contains("unsafe") || text.contains("risk-sensitive") || text.contains("safety-sensitive") || text.contains("promo") || text.contains("referral") || text.contains("fraud") || text.contains("psychedelic") || text.contains("microdosing") || text.contains("controlled substance") || text.contains("небезопас") || text.contains("микродоз") || text.contains("психоделик") || text.contains("реклам")) return false;
        boolean knownSafeRoute = Set.of("GUIDE", "REFERENCE", "CHECKLIST", "ANSWER").contains(route);
        boolean narrowChecklist = text.contains("narrow checklist") || text.contains("узк") && text.contains("чеклист") || text.contains("достаточно") && text.contains("чеклист") || text.contains("checklist") && sourceCount >= 2;
        boolean contextualReference = "REFERENCE".equals(route) && sourceCount >= 2 && (text.contains("resource") || text.contains("reference") || text.contains("ссыл") || text.contains("ресурс") || text.contains("github"));
        boolean conciseAnswer = "ANSWER".equals(route) && sourceCount >= 1 && (text.contains("sufficient") || text.contains("достаточно"));
        boolean genericRoute = route.isBlank() || Set.of("NO_MATERIAL", "IGNORE", "TRACE_ONLY", "ABSTAIN").contains(route);
        boolean inferredReference = genericRoute && sourceCount >= 2 && score >= 0.55 && (text.contains("resource") || text.contains("reference") || text.contains("ссыл") || text.contains("ресурс") || text.contains("github"));
        boolean inferredChecklist = genericRoute && sourceCount >= 2 && score >= 0.70 && narrowChecklist;
        return (knownSafeRoute && (narrowChecklist || contextualReference || conciseAnswer)) || inferredChecklist || inferredReference;
    }

    private JsonNode judgePayload(JsonNode response) {
        if (response == null || response.isMissingNode() || response.isNull()) return json.createObjectNode();
        if (response.has("decision") || response.has("summary") || response.has("reason")) return response;
        String content = response.path("choices").path(0).path("message").path("content").asText("");
        if (!content.isBlank()) {
            try { return json.readTree(content); } catch (Exception ignored) { return response; }
        }
        return response;
    }
    private String modelConfig() { return write(object("classifier", "BOOTSTRAP_BERT_CLASSIFIER", "embeddings", "BAAI/bge-m3")); }
    private String providerConfig() { return write(object("provider", "modelhub", "baseUrl", "https://modelhub.my/v1", "apiKeyRef", "MODELHUB_API_KEY")); }
    private ArrayNode calibrationRecommendations() { ArrayNode a = json.createArrayNode(); a.addObject().put("name", "semanticSimilarityThreshold").put("old", 0.72).put("recommended", 0.66).put("reason", "Run 6 produced only 11 semantic neighbors from 223 embeddings; a moderate threshold should recover related troubleshooting/pricing clusters without exploding LLM calls."); a.addObject().put("name", "llmClusterGate").put("old", "macroclusters.score").put("recommended", "cluster_scores.final_score").put("reason", "Run 6 final_score had five clusters at 0.65, but legacy gate used raw macro score around 0.59 and skipped them."); a.addObject().put("name", "judgeDecisionSynonyms").put("old", "APPROVE only").put("recommended", "APPROVE, ACCEPT, VALID, KEEP, GENERATE").put("reason", "Real ModelHub judge returned accept/valid with high confidence, so generation was incorrectly skipped."); a.addObject().put("name", "minClusterScoreForJudge").put("recommended", 0.50).put("reason", "Let more useful 2-message clusters reach judge while maxProviderCalls limits cost."); a.addObject().put("name", "minJudgeConfidenceForGeneration").put("recommended", 0.60).put("reason", "Keep high-signal clusters moving to generation while still rejecting low-confidence judge results."); return a; }
    private long count(String sql, Object... args) { Number n = jdbc.queryForObject(sql, Number.class, args); return n == null ? 0 : n.longValue(); }
    private int candidates(List<Intel> i) { return (int) i.stream().filter(x -> !x.ruleDecision().equals("SUPPRESS")).count(); }
    private int suppressed(List<Intel> i) { return (int) i.stream().filter(x -> x.ruleDecision().equals("SUPPRESS")).count(); }
    private double cosine(List<Double> a, List<Double> b) { double dot = 0, aa = 0, bb = 0; int n = Math.min(a.size(), b.size()); for (int i = 0; i < n; i++) { dot += a.get(i) * b.get(i); aa += a.get(i) * a.get(i); bb += b.get(i) * b.get(i); } return aa == 0 || bb == 0 ? 0 : dot / (Math.sqrt(aa) * Math.sqrt(bb)); }
    private List<Double> vector(JsonNode node) { List<Double> r = new ArrayList<>(); if (node.isArray()) node.forEach(v -> r.add(v.asDouble())); return r; }
    private ObjectNode object() { return json.createObjectNode(); }
    private ObjectNode object(String k, Object v) { ObjectNode o = object(); put(o, k, v); return o; }
    private ObjectNode object(String k1, Object v1, String k2, Object v2) { ObjectNode o = object(k1, v1); put(o, k2, v2); return o; }
    private ObjectNode object(String k1, Object v1, String k2, Object v2, String k3, Object v3) { ObjectNode o = object(k1, v1, k2, v2); put(o, k3, v3); return o; }
    private ObjectNode object(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4) { ObjectNode o = object(k1, v1, k2, v2, k3, v3); put(o, k4, v4); return o; }
    private ObjectNode object(String k1, Object v1, String k2, Object v2, String k3, Object v3, String k4, Object v4, String k5, Object v5) { ObjectNode o = object(k1, v1, k2, v2, k3, v3, k4, v4); put(o, k5, v5); return o; }
    private ObjectNode object(Object... fields) { ObjectNode o = object(); for (int i = 0; i + 1 < fields.length; i += 2) put(o, String.valueOf(fields[i]), fields[i + 1]); return o; }
    private ArrayNode array(String... values) { ArrayNode a = json.createArrayNode(); for (String v : values) a.add(v); return a; }
    private void put(ObjectNode o, String k, Object v) { if (v instanceof Boolean b) o.put(k, b); else if (v instanceof Number n) o.put(k, n.doubleValue()); else o.put(k, String.valueOf(v)); }
    private JsonNode parse(String value) { try { return json.readTree(blank(value) ? "{}" : value); } catch (JsonProcessingException e) { return object(); } }
    private JsonNode parseArray(String value) { JsonNode n = parse(value); return n.isArray() ? n : json.createArrayNode(); }
    private JsonNode oneOrNull(String sql, Object... args) {
        List<String> rows = jdbc.query(sql, (rs, rowNum) -> rs.getString(1), args);
        return rows.isEmpty() || blank(rows.get(0)) ? null : parse(rows.get(0));
    }
    private String write(JsonNode node) { try { return json.writeValueAsString(node == null ? object() : node); } catch (JsonProcessingException e) { throw new IllegalArgumentException("Cannot serialize JSON", e); } }
    private String coalesce(String... values) { for (String v : values) if (!blank(v)) return v; return ""; }
    private boolean blank(String v) { return v == null || v.isBlank(); }
    private String language(String text) { return text.matches(".*[А-Яа-я].*") ? "ru" : "en"; }
    private String firstWord(String value) { String[] parts = value.toLowerCase(Locale.ROOT).split("\\s+"); return parts.length == 0 || parts[0].isBlank() ? "misc" : parts[0]; }
    private String title(List<Long> members, Map<Long, Intel> byId) { for (Long id : members) { Intel i = byId.get(id); if (i != null && !i.normalizedText().isBlank()) return i.normalizedText().substring(0, Math.min(64, i.normalizedText().length())); } return "Semantic cluster"; }
    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000L; }
    private String iso(ResultSet rs, String f) throws SQLException { OffsetDateTime t = rs.getObject(f, OffsetDateTime.class); return t == null ? null : t.toString(); }
    private void metric(Map<String, BigDecimal> metrics, String name, long value) { metrics.put(name, BigDecimal.valueOf(value)); }
    private void metric(Map<String, BigDecimal> metrics, String name, BigDecimal value) { metrics.put(name, value == null ? BigDecimal.ZERO : value); }

    public record ReplayRequest(long datasetId, String runName, Integer maxMessages, Integer maxProviderCalls, Double maxEstimatedCostUsd, Double semanticSimilarityThreshold, Double minClusterScoreForJudge, Double minJudgeConfidenceForGeneration) {
        int maxMessagesOrDefault() { return Math.max(1, Math.min(maxMessages == null ? 500 : maxMessages, 5000)); }
        int maxProviderCallsOrDefault() { return maxProviderCalls == null ? 50 : maxProviderCalls; }
        double maxEstimatedCostUsdOrDefault() { return maxEstimatedCostUsd == null ? 2.0 : maxEstimatedCostUsd; }
        double semanticSimilarityThresholdOrDefault() { return semanticSimilarityThreshold == null ? 0.66 : semanticSimilarityThreshold; }
        double minClusterScoreForJudgeOrDefault() { return minClusterScoreForJudge == null ? 0.65 : minClusterScoreForJudge; }
        double minJudgeConfidenceForGenerationOrDefault() { return minJudgeConfidenceForGeneration == null ? 0.72 : minJudgeConfidenceForGeneration; }
    }
    public record ReplayPlan(long datasetId, long messagesTotal, String workerHealth, boolean bertConfigured, boolean bgeConfigured, boolean providerConfigured, long estimatedEmbeddings, long estimatedClusters, long estimatedLlmCalls, double estimatedCost, String warningsJson) {}
    public record ReplayRun(long id, long datasetId, String runName, String mode, String pipelineVersion, String status, String startedAt, String finishedAt, int totalMessages, int processedMessages, int providerCallsTotal, BigDecimal estimatedCostUsd, String error) {}
    public record LabelEvent(String userId, String eventType, String oldValueJson, String newValueJson, String comment, String label, String artifactType, String decision, String reviewAction) {}
    public record TrainingDatasetExportRequest(String format, Double minConfidence, Boolean includeWeakLabels, String splitStrategy, String outputPath) {}
    public record ActiveLearningBatchRequest(long runId, Integer targetSize, String strategy) {}
    public record DailyReviewBatchRequest(String date, Integer targetSize, String strategy, Integer includeRunsFromLastHours) {}
    private record Message(long id, String text, String caption, String rawJson, String entitiesJson, String mediaJson, long accountId, long telegramChatId, Long forumTopicId, Long messageThreadId, Timestamp messageDate, Timestamp ingestedAt) {}
    private record Intel(long id, Message message, String normalizedText, JsonNode features, boolean hardSignal, String ruleDecision) {}
    private record Embedding(long id, long messageId, List<Double> vector, String kind) {}
    private record Neighbor(long sourceId, long targetId, double similarity) {}
    private record Cluster(long id, String title, double score, String artifactType, List<Long> members) {
        private Cluster(long id, String title, double score, List<Long> members) {
            this(id, title, score, "SUMMARY", members);
        }
    }
    private record SingleMessageCandidate(long messageId, double score, String decision, JsonNode signals, JsonNode usefulness, String requiredArtifactType) {
        private SingleMessageCandidate(long messageId, double score, String decision, JsonNode signals) {
            this(messageId, score, decision, signals, null, null);
        }
    }
    private record DiscussionDedupeResult(List<DiscussionSegmentCandidate> accepted, List<DiscussionSegmentCandidate> rejected) {}
    private static final class DiscussionSegmentCandidate {
        private Long segmentId;
        private final long runId;
        private final double score;
        private final String decision;
        private final String rejectionReason;
        private final String proposedMaterialType;
        private final Double overlapRatio;
        private final JsonNode signals;
        private final JsonNode suppressionReasons;
        private final List<Intel> messages;
        private final String segmentText;
        DiscussionSegmentCandidate(Long segmentId, long runId, double score, String decision, String rejectionReason, String proposedMaterialType, JsonNode signals, JsonNode suppressionReasons, List<Intel> messages, String segmentText) { this(segmentId, runId, score, decision, rejectionReason, proposedMaterialType, null, signals, suppressionReasons, messages, segmentText); }
        DiscussionSegmentCandidate(Long segmentId, long runId, double score, String decision, String rejectionReason, String proposedMaterialType, Double overlapRatio, JsonNode signals, JsonNode suppressionReasons, List<Intel> messages, String segmentText) { this.segmentId = segmentId; this.runId = runId; this.score = score; this.decision = decision; this.rejectionReason = rejectionReason; this.proposedMaterialType = proposedMaterialType; this.overlapRatio = overlapRatio; this.signals = signals; this.suppressionReasons = suppressionReasons; this.messages = messages; this.segmentText = segmentText; }
        Long segmentId() { return segmentId; }
        void segmentId(Long segmentId) { this.segmentId = segmentId; }
        long runId() { return runId; }
        double score() { return score; }
        String decision() { return decision; }
        String rejectionReason() { return rejectionReason; }
        String proposedMaterialType() { return proposedMaterialType; }
        Double overlapRatio() { return overlapRatio; }
        JsonNode signals() { return signals; }
        JsonNode suppressionReasons() { return suppressionReasons; }
        List<Intel> messages() { return messages; }
        String segmentText() { return segmentText; }
        int sourceCount() { return messages.size(); }
        DiscussionSegmentCandidate rejectedCopy(String reason, Double overlapRatio) { return new DiscussionSegmentCandidate(segmentId, runId, score, reason, reason, proposedMaterialType, overlapRatio, signals, suppressionReasons, messages, segmentText); }
    }
}
