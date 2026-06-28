package com.larbcorp.neuroinfogrinder2.api;

import com.larbcorp.neuroinfogrinder2.ai.AiCatalogService;
import com.larbcorp.neuroinfogrinder2.dataset.DatasetReplayService;
import com.larbcorp.neuroinfogrinder2.dataset.RawMessagesReplayService;
import com.larbcorp.neuroinfogrinder2.pipeline.PipelineLiveService;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.ClassicalMlStageDatasetExport;
import com.larbcorp.neuroinfogrinder2.replay.classicalml.ClassicalMlStageEvaluationRequest;
import com.larbcorp.neuroinfogrinder2.replay.KnowledgeItemReviewService;
import com.larbcorp.neuroinfogrinder2.replay.ModelhubProviderGateway;
import com.larbcorp.neuroinfogrinder2.replay.ModelWorkerClient;
import com.larbcorp.neuroinfogrinder2.replay.ReplayV2Service;
import com.larbcorp.neuroinfogrinder2.classifier.ClassificationRuleService;
import com.larbcorp.neuroinfogrinder2.settings.PipelineSettingsService;
import com.larbcorp.neuroinfogrinder2.prompts.PromptTemplateService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin
public class Stage1B2Api {
    private final DatasetReplayService datasetReplayService;
    private final AiCatalogService aiCatalogService;
    private final ReplayV2Service replayV2Service;
    private final KnowledgeItemReviewService knowledgeItemReviewService;
    private final ModelhubProviderGateway providerGateway;
    private final ModelWorkerClient modelWorkerClient;
    private final RawMessagesReplayService rawMessagesReplayService;
    private final PipelineLiveService pipelineLiveService;
    private final ClassificationRuleService classificationRuleService;
    private final PipelineSettingsService pipelineSettingsService;
    private final PromptTemplateService promptTemplateService;

    public Stage1B2Api(
            DatasetReplayService datasetReplayService,
            AiCatalogService aiCatalogService,
            ReplayV2Service replayV2Service,
            KnowledgeItemReviewService knowledgeItemReviewService,
            ModelhubProviderGateway providerGateway,
            ModelWorkerClient modelWorkerClient,
            RawMessagesReplayService rawMessagesReplayService,
            PipelineLiveService pipelineLiveService,
            ClassificationRuleService classificationRuleService,
            PipelineSettingsService pipelineSettingsService,
            PromptTemplateService promptTemplateService
    ) {
        this.datasetReplayService = datasetReplayService;
        this.aiCatalogService = aiCatalogService;
        this.replayV2Service = replayV2Service;
        this.knowledgeItemReviewService = knowledgeItemReviewService;
        this.providerGateway = providerGateway;
        this.modelWorkerClient = modelWorkerClient;
        this.rawMessagesReplayService = rawMessagesReplayService;
        this.pipelineLiveService = pipelineLiveService;
        this.classificationRuleService = classificationRuleService;
        this.pipelineSettingsService = pipelineSettingsService;
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping("/api/v2/datasets")
    public List<DatasetReplayService.DatasetDto> datasets() {
        return datasetReplayService.datasets();
    }

    @GetMapping("/api/v2/datasets/{id}")
    public DatasetReplayService.DatasetDto dataset(@PathVariable long id) {
        return datasetReplayService.dataset(id);
    }

    @GetMapping("/api/v2/datasets/{id}/messages")
    public List<DatasetReplayService.DatasetMessageDto> datasetMessages(@PathVariable long id) {
        return datasetReplayService.datasetMessages(id, 1000);
    }

    @PostMapping("/api/v2/datasets/import")
    public DatasetReplayService.DatasetImportResult importDataset(
            @RequestBody DatasetReplayService.ImportDatasetRequest request
    ) throws IOException {
        return datasetReplayService.importJsonl(request);
    }

    @PostMapping("/api/v2/datasets/upload")
    public DatasetReplayService.DatasetImportResult uploadDataset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Выберите непустой JSONL файл");
        }
        String filename = file.getOriginalFilename() == null ? "dataset.jsonl" : file.getOriginalFilename();
        if (!filename.toLowerCase().endsWith(".jsonl")) {
            throw new IllegalArgumentException("Поддерживаются только файлы .jsonl");
        }
        return datasetReplayService.importUploadedJsonl(new DatasetReplayService.UploadDatasetRequest(
                name,
                "UI_JSONL_UPLOAD",
                description,
                "ui",
                filename,
                new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8)
        ));
    }

    @DeleteMapping("/api/v2/datasets/{id}")
    public ResponseEntity<Void> deleteDataset(@PathVariable long id) {
        datasetReplayService.deleteDataset(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v2/pipeline/replay")
    public DatasetReplayService.ReplayRunDto replay(@RequestBody DatasetReplayService.ReplayRequest request) {
        return datasetReplayService.replay(request);
    }

    @PostMapping("/api/v2/pipeline/replay/raw-messages")
    public DatasetReplayService.ReplayRunDto replayRawMessages(@RequestBody RawMessagesReplayService.RawMessagesReplayRequest request) {
        return rawMessagesReplayService.run(request);
    }

    @GetMapping("/api/v2/pipeline/replay")
    public List<DatasetReplayService.ReplayRunDto> replayRuns() {
        return datasetReplayService.replayRuns();
    }

    @GetMapping("/api/v2/pipeline/replay/{runId}")
    public DatasetReplayService.ReplayRunDto replayRun(@PathVariable long runId) {
        return datasetReplayService.replayRun(runId);
    }

    @GetMapping("/api/v2/pipeline/replay/{runId}/messages")
    public List<DatasetReplayService.ReplayRunMessageDto> replayRunMessages(@PathVariable long runId) {
        return datasetReplayService.replayRunMessages(runId);
    }

    @GetMapping("/api/v2/pipeline/live/summary")
    public PipelineLiveService.Summary pipelineLiveSummary() {
        return pipelineLiveService.summary();
    }

    @GetMapping("/api/v2/pipeline/live/acceptance")
    public PipelineLiveService.LiveAcceptance pipelineLiveAcceptance() {
        return pipelineLiveService.liveAcceptance();
    }

    @GetMapping("/api/v2/pipeline/live/stages")
    public List<PipelineLiveService.StageSummary> pipelineLiveStages() {
        return pipelineLiveService.stages();
    }

    @GetMapping("/api/v2/pipeline/live/events")
    public Map<String, Object> pipelineLiveEvents(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return Map.of("events", pipelineLiveService.liveEvents(limit));
    }

    @GetMapping("/api/v2/pipeline/live/batches")
    public Map<String, Object> pipelineLiveBatches(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Map.of("batches", pipelineLiveService.liveBatches(limit));
    }

    @GetMapping("/api/v2/pipeline/pending-breakdown")
    public List<PipelineLiveService.PendingBreakdownRow> pipelinePendingBreakdown() {
        return pipelineLiveService.pendingBreakdown();
    }

    @GetMapping("/api/v2/pipeline/live/stages/{stageId}/messages")
    public List<PipelineLiveService.StageMessage> pipelineLiveStageMessages(
            @PathVariable String stageId,
            @RequestParam(value = "status", defaultValue = "active") String status,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return pipelineLiveService.stageMessages(stageId, status, limit);
    }

    @GetMapping("/api/v2/pipeline/live/stages/{stageId}/details")
    public PipelineLiveService.StageDetails pipelineLiveStageDetails(@PathVariable String stageId) {
        return pipelineLiveService.stageDetails(stageId);
    }

    @GetMapping("/api/v2/pipeline/live/runs/{runId}/details")
    public Map<String, Object> pipelineLiveRunDetails(@PathVariable long runId) {
        return pipelineLiveService.liveRunDetails(runId);
    }

    @GetMapping("/api/v2/pipeline/live/clusters")
    public Map<String, Object> pipelineLiveClusters(
            @RequestParam(value = "runId", required = false) Long runId,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        return pipelineLiveService.liveClusters(runId, limit);
    }

    @GetMapping("/api/v2/pipeline/live/embeddings")
    public Map<String, Object> pipelineLiveEmbeddings(
            @RequestParam(value = "runId", required = false) Long runId,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        return pipelineLiveService.liveEmbeddings(runId, limit);
    }

    @GetMapping("/api/v2/pipeline/live/llm-judge")
    public Map<String, Object> pipelineLiveLlmJudge(
            @RequestParam(value = "runId", required = false) Long runId,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        return pipelineLiveService.liveLlmJudge(runId, limit);
    }

    @GetMapping("/api/v2/pipeline/live/material-generation")
    public Map<String, Object> pipelineLiveMaterialGeneration(
            @RequestParam(value = "runId", required = false) Long runId,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        return pipelineLiveService.liveMaterialGeneration(runId, limit);
    }

    @GetMapping("/api/v2/pipeline/messages/{rawMessageId}/trace")
    public PipelineLiveService.MessageTrace pipelineMessageTrace(@PathVariable long rawMessageId) {
        return pipelineLiveService.messageTrace(rawMessageId);
    }

    @PostMapping("/api/v2/pipeline/messages/{rawMessageId}/retry")
    public Map<String, Object> retryPipelineMessage(@PathVariable long rawMessageId) {
        return pipelineLiveService.retryDisabled(rawMessageId, null);
    }

    @PostMapping("/api/v2/pipeline/messages/{rawMessageId}/retry-stage/{stageId}")
    public Map<String, Object> retryPipelineStage(@PathVariable long rawMessageId, @PathVariable String stageId) {
        return pipelineLiveService.retryDisabled(rawMessageId, stageId);
    }

    @GetMapping("/api/v2/pipeline/materials/why-empty")
    public PipelineLiveService.WhyEmpty pipelineMaterialsWhyEmpty() {
        return pipelineLiveService.whyEmpty();
    }

    @GetMapping("/api/v2/pipeline/auto/settings")
    public List<PipelineLiveService.AutoPipelineSettingDto> pipelineAutoSettings() {
        return pipelineLiveService.autoSettings();
    }

    @GetMapping("/api/v2/pipeline/auto/status")
    public PipelineLiveService.AutoPipelineStatusDto pipelineAutoStatus(
            @RequestParam long accountId,
            @RequestParam long chatId,
            @RequestParam(required = false) Long topicId
    ) {
        return pipelineLiveService.autoStatus(accountId, chatId, topicId);
    }

    @PostMapping("/api/v2/pipeline/auto/enable")
    public PipelineLiveService.AutoPipelineSettingDto enablePipelineAuto(@RequestBody PipelineLiveService.AutoPipelineRequest request) {
        return pipelineLiveService.enableAuto(request);
    }

    @PostMapping("/api/v2/pipeline/auto/disable")
    public PipelineLiveService.AutoPipelineSettingDto disablePipelineAuto(@RequestBody PipelineLiveService.AutoPipelineRequest request) {
        return pipelineLiveService.disableAuto(request);
    }

    @PostMapping("/api/v2/pipeline/intake/backfill-from-raw")
    public PipelineLiveService.IntakeBackfillResult pipelineIntakeBackfill(@RequestBody PipelineLiveService.IntakeBackfillRequest request) {
        return pipelineLiveService.backfillFromRaw(request);
    }

    @PostMapping("/api/v2/pipeline/queue-pending-enabled-scopes")
    public PipelineLiveService.QueuePendingResult queuePipelinePendingEnabledScopes(@RequestBody PipelineLiveService.QueuePendingRequest request) {
        return pipelineLiveService.queuePendingEnabledScopes(request);
    }

    @PostMapping("/api/v2/pipeline/runs/{runId}/start")
    public PipelineLiveService.LiveAutoRunStartResult startPipelineRun(@PathVariable long runId) {
        return pipelineLiveService.startLiveAutoRun(runId);
    }

    @GetMapping("/api/v2/pipeline/runs/{runId}/health")
    public PipelineLiveService.RunHealth pipelineRunHealth(@PathVariable long runId) {
        return pipelineLiveService.runHealth(runId);
    }

    @GetMapping("/api/v2/pipeline/diagnostics")
    public Map<String, Object> pipelineDiagnostics() {
        return pipelineLiveService.diagnostics();
    }

    @PostMapping("/api/v2/replay-runs/plan")
    public ReplayV2Service.ReplayPlan replayPlan(@RequestBody ReplayV2Service.ReplayRequest request) {
        return replayV2Service.plan(request);
    }

    @PostMapping("/api/v2/replay-runs")
    public ReplayV2Service.ReplayRun createReplayRun(@RequestBody ReplayV2Service.ReplayRequest request) {
        return replayV2Service.run(request);
    }

    @PostMapping("/api/v2/replay-runs/start")
    public ReplayV2Service.ReplayRun startReplayRun(@RequestBody ReplayV2Service.ReplayRequest request) {
        return replayV2Service.run(request);
    }

    @GetMapping("/api/v2/replay-runs")
    public List<ReplayV2Service.ReplayRun> replayRunsV2() {
        return replayV2Service.runs();
    }

    @GetMapping("/api/v2/replay-runs/{runId}")
    public ReplayV2Service.ReplayRun replayRunV2(@PathVariable long runId) {
        return replayV2Service.getRun(runId);
    }

    @GetMapping("/api/v2/replay-runs/{runId}/stages")
    public List<Map<String, Object>> replayStages(@PathVariable long runId) {
        return replayV2Service.stages(runId);
    }

    @GetMapping("/api/v2/replay-runs/{runId}/messages")
    public List<Map<String, Object>> replayMessagesV2(@PathVariable long runId) {
        return replayV2Service.messages(runId);
    }

    @PostMapping("/api/v2/replay-runs/{runId}/cancel")
    public ResponseEntity<Void> cancelReplay(@PathVariable long runId) {
        replayV2Service.cancel(runId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v2/replay-runs/{runId}/metrics")
    public Map<String, Object> replayMetrics(@PathVariable long runId) {
        return replayV2Service.metrics(runId);
    }

    @GetMapping("/api/v2/replay-runs/{runId}/summary")
    public List<Map<String, Object>> replaySummary(@PathVariable long runId) {
        return replayV2Service.summary(runId);
    }

    @GetMapping("/api/v2/replay-runs/{runId}/provider-costs")
    public List<Map<String, Object>> replayProviderCosts(@PathVariable long runId) {
        return replayV2Service.providerCosts(runId);
    }

    @GetMapping("/api/v2/replay-runs/{runId}/knowledge-items")
    public List<Map<String, Object>> replayKnowledgeItems(@PathVariable long runId) {
        return replayV2Service.knowledgeItems(runId);
    }

    @PostMapping("/api/v2/knowledge-items/review")
    public KnowledgeItemReviewService.ReviewRunResult reviewKnowledgeItems(
            @RequestBody KnowledgeItemReviewService.ReviewRequest request
    ) {
        return knowledgeItemReviewService.review(request);
    }

    @GetMapping("/api/v2/knowledge-items/review-summary")
    public KnowledgeItemReviewService.ReviewSummary knowledgeItemReviewSummary(@RequestParam List<Long> runIds) {
        return knowledgeItemReviewService.summary(runIds);
    }

    @GetMapping("/api/v2/knowledge-items/{id}/review")
    public KnowledgeItemReviewService.ReviewDetail knowledgeItemReview(@PathVariable long id) {
        return knowledgeItemReviewService.detail(id);
    }

    @PostMapping("/api/v2/knowledge-items/{id}/review-actions")
    public KnowledgeItemReviewService.ReviewActionResult knowledgeItemReviewAction(
            @PathVariable long id,
            @RequestBody KnowledgeItemReviewService.ReviewActionRequest request
    ) {
        return knowledgeItemReviewService.action(id, request);
    }

    @GetMapping("/api/v2/replay-runs/{runId}/clusters")
    public List<Map<String, Object>> replayClusters(@PathVariable long runId) {
        return replayV2Service.clusters(runId);
    }

    @GetMapping("/api/v2/replay-runs/{runId}/export-json")
    public Object replayExportJson(@PathVariable long runId) {
        return replayV2Service.exportJson(runId, true, false, true);
    }

    @GetMapping("/api/v2/replay-runs/{runId}/audit-json")
    public Object replayAuditJson(@PathVariable long runId) {
        return replayV2Service.auditJson(runId);
    }

    @GetMapping("/api/v2/replay-runs/{runId}/classification-audit-json")
    public Object replayClassificationAuditJson(@PathVariable long runId) {
        return replayV2Service.classificationAuditJson(runId);
    }

    @GetMapping("/api/v2/labeling/items")
    public List<Map<String, Object>> labelingItems(String status, Integer priority) {
        return replayV2Service.labelingItems(status, priority);
    }

    @GetMapping("/api/v2/labeling/active-learning-queue")
    public List<Map<String, Object>> activeLearningQueue(String reason, String label) {
        return replayV2Service.activeLearningQueue(reason, label);
    }

    @PostMapping("/api/v2/labeling/batches/create-active-learning-batch")
    public Map<String, Object> createActiveLearningBatch(
            @RequestBody ReplayV2Service.ActiveLearningBatchRequest request
    ) {
        return replayV2Service.createActiveLearningBatch(request);
    }

    @PostMapping("/api/v2/labeling/batches/create-daily-review-batch")
    public Map<String, Object> createDailyReviewBatch(
            @RequestBody ReplayV2Service.DailyReviewBatchRequest request
    ) {
        return replayV2Service.createDailyReviewBatch(request);
    }

    @GetMapping("/api/v2/labeling/batches/{batchId}")
    public Map<String, Object> activeLearningBatch(@PathVariable long batchId) {
        return replayV2Service.activeLearningBatch(batchId);
    }

    @PostMapping("/api/v2/labeling/batches/{batchId}/export")
    public Map<String, Object> exportActiveLearningBatch(@PathVariable long batchId) {
        return replayV2Service.exportActiveLearningBatch(batchId);
    }

    @GetMapping("/api/v2/labeling/items/{id}")
    public Map<String, Object> labelingItem(@PathVariable long id) {
        return replayV2Service.labelingItem(id);
    }

    @PostMapping("/api/v2/labeling/items/{id}/events")
    public Map<String, Object> labelingEvent(
            @PathVariable long id,
            @RequestBody ReplayV2Service.LabelEvent request
    ) {
        return replayV2Service.addLabelingEvent(id, request);
    }

    @GetMapping("/api/v2/training-examples")
    public List<Map<String, Object>> trainingExamples() {
        return replayV2Service.trainingExamples();
    }

    @GetMapping("/api/v2/training-examples/summary")
    public Map<String, Object> trainingExamplesSummary() {
        return replayV2Service.trainingExamplesSummary();
    }

    @GetMapping("/api/v2/training/data-readiness")
    public Map<String, Object> trainingDataReadiness() {
        return replayV2Service.trainingDataReadiness();
    }

    @PostMapping("/api/v2/training-examples/export")
    public Object trainingExamplesExport() {
        return replayV2Service.exportTrainingExamples();
    }

    @PostMapping("/api/v2/training-examples/export-classifier-dataset")
    public Object classifierDatasetExport(@RequestBody ReplayV2Service.TrainingDatasetExportRequest request) {
        return replayV2Service.exportClassifierDataset(request);
    }

    @PostMapping("/api/v2/training-examples/export-reviewed-classifier-dataset")
    public Map<String, Object> reviewedClassifierDatasetExport() {
        return replayV2Service.exportReviewedClassifierDataset();
    }

    @PostMapping("/api/v2/training-examples/export-weak-classifier-dataset")
    public Map<String, Object> weakClassifierDatasetExport() {
        return replayV2Service.exportWeakClassifierDataset();
    }

    @PostMapping("/api/v2/classical-ml/export-stage-dataset")
    public Map<String, Object> classicalMlStageDatasetExport(@RequestBody ClassicalMlStageDatasetExport request) {
        return replayV2Service.exportStageDataset(request);
    }

    @PostMapping("/api/v2/classical-ml/evaluate-stage")
    public Map<String, Object> classicalMlEvaluateStage(@RequestBody ClassicalMlStageEvaluationRequest request) {
        return replayV2Service.evaluateClassicalMlStage(request);
    }

    @PostMapping("/api/v2/classical-ml/train-stage")
    public Map<String, Object> classicalMlTrainStage(@RequestBody ClassicalMlStageEvaluationRequest request) {
        return replayV2Service.trainClassicalMlStage(request);
    }

    @GetMapping("/api/v2/classical-ml/models")
    public Map<String, Object> classicalMlModels() {
        return replayV2Service.classicalMlModels();
    }

    @GetMapping("/api/v2/classical-ml/metrics")
    public Map<String, Object> classicalMlMetrics(@RequestParam(required = false) String version) {
        return replayV2Service.classicalMlMetrics(version);
    }

    @GetMapping("/api/v2/classical-ml/readiness")
    public Map<String, Object> classicalMlReadiness() {
        return replayV2Service.classicalMlPhaseReadiness();
    }

    @GetMapping("/api/v2/classifiers/status")
    public Map<String, Object> classifierStatus() {
        var health = modelWorkerClient.health();
        var status = new java.util.LinkedHashMap<String, Object>();
        status.put("workerReachable", health.reachable());
        status.put("workerStatus", health.status());
        status.put("classifierStatus", health.classifierStatus());
        status.put("classifierName", health.classifierName());
        status.put("embeddingStatus", health.embeddingStatus());
        status.put("embeddingName", health.embeddingName());
        status.put("embeddingDimension", health.embeddingDimension());
        status.put("degraded", !health.embeddingsReady());
        status.put("ruleEngineEnabled", true);
        status.put("ruleCount", classificationRuleService.listRules().size());
        status.put("singleMessageDetectorEnabled", true);
        status.put("singleMessageThresholdDirect", pipelineSettingsService.getDouble("directMaterialReadyThreshold", 0.72));
        status.put("singleMessageThresholdCandidate", pipelineSettingsService.getDouble("singleMessageCandidateThreshold", 0.55));
        status.put("clusterGenerationThreshold", pipelineSettingsService.getDouble("clusterGenerationThreshold", 0.60));
        status.put("minSingleMessageTextLength", pipelineSettingsService.getInt("minSingleMessageTextLength", 500));
        status.put("thresholds", pipelineSettingsService.getThresholds());
        return status;
    }

    @GetMapping("/api/v2/classifiers/rules")
    public List<ClassificationRuleService.RuleDto> listRules() { return classificationRuleService.listRules(); }

    @GetMapping("/api/v2/classifiers/rules/{ruleId}")
    public ClassificationRuleService.RuleDto getRule(@PathVariable long ruleId) { return classificationRuleService.getRule(ruleId); }

    @PostMapping("/api/v2/classifiers/rules")
    public ClassificationRuleService.RuleDto createRule(@RequestBody ClassificationRuleService.CreateRuleRequest request) { return classificationRuleService.createRule(request); }

    @PutMapping("/api/v2/classifiers/rules/{ruleId}")
    public ClassificationRuleService.RuleDto updateRule(@PathVariable long ruleId, @RequestBody ClassificationRuleService.UpdateRuleRequest request) { return classificationRuleService.updateRule(ruleId, request); }

    @PostMapping("/api/v2/classifiers/rules/{ruleId}/activate")
    public ClassificationRuleService.RuleDto activateRule(@PathVariable long ruleId, @RequestParam(defaultValue = "system") String updatedBy) { return classificationRuleService.activateRule(ruleId, updatedBy); }

    @PostMapping("/api/v2/classifiers/rules/{ruleId}/deactivate")
    public ClassificationRuleService.RuleDto deactivateRule(@PathVariable long ruleId, @RequestParam(defaultValue = "system") String updatedBy) { return classificationRuleService.deactivateRule(ruleId, updatedBy); }

    @GetMapping("/api/v2/classifiers/rules/{ruleId}/versions")
    public List<ClassificationRuleService.RuleVersionDto> listRuleVersions(@PathVariable long ruleId) { return classificationRuleService.listRuleVersions(ruleId); }

    @PostMapping("/api/v2/classifiers/rules/{ruleId}/rollback/{version}")
    public ClassificationRuleService.RuleDto rollbackRule(@PathVariable long ruleId, @PathVariable int version, @RequestParam(defaultValue = "system") String updatedBy) { return classificationRuleService.rollbackRule(ruleId, version, updatedBy); }

    @PostMapping("/api/v2/classifiers/test")
    public ClassificationRuleService.ClassifierTestResult testClassifier(@RequestBody ClassificationRuleService.ClassifierTestRequest request) { return classificationRuleService.testMessage(request); }

    @GetMapping("/api/v2/classifiers/distribution")
    public Map<String, Long> classifierDistribution(@RequestParam(defaultValue = "0") long runId) { return classificationRuleService.distribution(runId); }

    @GetMapping("/api/v2/classifiers/recent-decisions")
    public List<Map<String, Object>> recentDecisions(@RequestParam(defaultValue = "0") long runId, @RequestParam(defaultValue = "50") int limit) { return classificationRuleService.recentDecisions(runId, limit); }

    @GetMapping("/api/v2/classifiers/single-message-candidates")
    public List<Map<String, Object>> singleMessageCandidates(@RequestParam(defaultValue = "0") long runId, @RequestParam(defaultValue = "20") int limit) { return classificationRuleService.singleMessageCandidates(runId, limit); }

    @GetMapping("/api/v2/ai/providers")
    public List<AiCatalogService.AiProviderDto> providers() {
        return aiCatalogService.providers();
    }

    @PostMapping("/api/v2/ai/providers/{id}/health-check")
    public ModelhubProviderGateway.ProviderHealth providerHealth(@PathVariable long id) {
        return providerGateway.healthCheck(id);
    }

    @PostMapping("/api/v2/ai/providers/{id}/test")
    public ModelhubProviderGateway.ProviderHealth providerTest(@PathVariable long id) {
        return providerGateway.healthCheck(id);
    }

    @GetMapping("/api/v2/ai/model-worker/health")
    public ModelWorkerClient.WorkerHealth modelWorkerHealth() {
        return modelWorkerClient.health();
    }

    @GetMapping("/api/v2/model-worker/status")
    public Map<String, Object> modelWorkerStatus() {
        var health = modelWorkerClient.health();
        var status = new java.util.LinkedHashMap<String, Object>();
        status.put("reachable", health.reachable());
        status.put("status", health.status());
        status.put("classifierStatus", health.classifierStatus());
        status.put("classifierName", health.classifierName());
        status.put("embeddingStatus", health.embeddingStatus());
        status.put("embeddingName", health.embeddingName());
        status.put("embeddingDimension", health.embeddingDimension());
        status.put("degraded", !health.embeddingsReady());
        status.put("lastError", health.raw().path("error").asText(null));
        return status;
    }

    @PostMapping("/api/v2/ai/providers")
    public AiCatalogService.AiProviderDto createProvider(@RequestBody AiCatalogService.ProviderRequest request) {
        return aiCatalogService.createProvider(request);
    }

    @PatchMapping("/api/v2/ai/providers/{id}")
    public AiCatalogService.AiProviderDto updateProvider(
            @PathVariable long id,
            @RequestBody AiCatalogService.ProviderRequest request
    ) {
        return aiCatalogService.updateProvider(id, request);
    }

    @PutMapping("/api/v2/ai/providers/{id}")
    public AiCatalogService.AiProviderDto putProvider(
            @PathVariable long id,
            @RequestBody AiCatalogService.ProviderRequest request
    ) {
        return aiCatalogService.updateProvider(id, request);
    }

    @DeleteMapping("/api/v2/ai/providers/{id}")
    public ResponseEntity<Void> deleteProvider(@PathVariable long id) {
        aiCatalogService.deleteProvider(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v2/ai/models")
    public List<AiCatalogService.AiModelDto> models() {
        return aiCatalogService.models();
    }

    @PostMapping("/api/v2/ai/models")
    public AiCatalogService.AiModelDto createModel(@RequestBody AiCatalogService.ModelRequest request) {
        return aiCatalogService.createModel(request);
    }

    @GetMapping("/api/v2/ai/providers/{id}/models")
    public List<AiCatalogService.AiModelDto> providerModels(@PathVariable long id) {
        return aiCatalogService.models().stream().filter(model -> model.providerId() == id).toList();
    }

    @PostMapping("/api/v2/ai/providers/{id}/models")
    public AiCatalogService.AiModelDto createProviderModel(
            @PathVariable long id,
            @RequestBody AiCatalogService.ModelRequest request
    ) {
        return aiCatalogService.createModel(new AiCatalogService.ModelRequest(
                id,
                request.modelName(),
                request.displayName(),
                request.inputPricePerMillion(),
                request.outputPricePerMillion(),
                request.cachePricePerMillion(),
                request.contextWindow(),
                request.supportsJson(),
                request.supportsTools(),
                request.supportsStreaming(),
                request.enabled(),
                request.metadataJson()
        ));
    }

    @PatchMapping("/api/v2/ai/models/{id}")
    public AiCatalogService.AiModelDto updateModel(
            @PathVariable long id,
            @RequestBody AiCatalogService.ModelRequest request
    ) {
        return aiCatalogService.updateModel(id, request);
    }

    @PutMapping("/api/v2/ai/providers/{providerId}/models/{modelId}")
    public AiCatalogService.AiModelDto putProviderModel(
            @PathVariable long providerId,
            @PathVariable long modelId,
            @RequestBody AiCatalogService.ModelRequest request
    ) {
        return aiCatalogService.updateModel(modelId, new AiCatalogService.ModelRequest(
                providerId,
                request.modelName(),
                request.displayName(),
                request.inputPricePerMillion(),
                request.outputPricePerMillion(),
                request.cachePricePerMillion(),
                request.contextWindow(),
                request.supportsJson(),
                request.supportsTools(),
                request.supportsStreaming(),
                request.enabled(),
                request.metadataJson()
        ));
    }

    @DeleteMapping("/api/v2/ai/models/{id}")
    public ResponseEntity<Void> deleteModel(@PathVariable long id) {
        aiCatalogService.deleteModel(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v2/ai/providers/{providerId}/models/{modelId}")
    public ResponseEntity<Void> deleteProviderModel(@PathVariable long providerId, @PathVariable long modelId) {
        aiCatalogService.deleteModel(modelId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v2/ai/model-routes")
    public List<AiCatalogService.ModelRouteDto> modelRoutes() {
        return aiCatalogService.routes();
    }

    @GetMapping("/api/v2/ai-providers/status")
    public Map<String, Object> aiProvidersStatus() {
        var status = new java.util.LinkedHashMap<String, Object>();
        status.put("providers", aiCatalogService.providers());
        status.put("routes", aiCatalogService.routes());
        status.put("promptRoutes", promptTemplateService.routeMapping());
        return status;
    }

    @PatchMapping("/api/v2/ai/model-routes/{id}")
    public AiCatalogService.ModelRouteDto updateRoute(
            @PathVariable long id,
            @RequestBody AiCatalogService.RouteRequest request
    ) {
        return aiCatalogService.updateRoute(id, request);
    }

    @PutMapping("/api/v2/ai/model-routes/{id}")
    public AiCatalogService.ModelRouteDto putRoute(
            @PathVariable long id,
            @RequestBody AiCatalogService.RouteRequest request
    ) {
        return aiCatalogService.updateRoute(id, request);
    }

    // ── Pipeline Settings (thresholds) endpoints ──

    @GetMapping("/api/v2/pipeline/settings")
    public Map<String, Object> pipelineSettings() {
        return pipelineSettingsService.getAllSettings();
    }

    @GetMapping("/api/v2/settings")
    public Map<String, Object> settingsOverview() {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("pipeline", pipelineLiveService.summary());
        result.put("pipelineSettings", pipelineSettingsService.getAllSettings());
        result.put("thresholds", pipelineSettingsService.getThresholds());
        result.put("worker", modelWorkerStatus());
        result.put("providers", aiProvidersStatus());
        result.put("promptRoutes", promptTemplateService.routeMapping());
        result.put("autoPipeline", pipelineLiveService.autoSettings());
        return result;
    }

    @GetMapping("/api/v2/pipeline/settings/{key}")
    public PipelineSettingsService.SettingDto pipelineSetting(@PathVariable String key) {
        return pipelineSettingsService.getSetting(key);
    }

    @PostMapping("/api/v2/pipeline/settings/{key}")
    public PipelineSettingsService.SettingDto updatePipelineSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body
    ) {
        String value = body.get("value");
        String updatedBy = body.getOrDefault("updatedBy", "ui");
        return pipelineSettingsService.updateSetting(key, value, updatedBy);
    }

    @PostMapping("/api/v2/pipeline/settings/{key}/reset")
    public PipelineSettingsService.SettingDto resetPipelineSetting(@PathVariable String key) {
        return pipelineSettingsService.resetSetting(key);
    }

    @GetMapping("/api/v2/pipeline/settings/thresholds")
    public List<Map<String, Object>> pipelineThresholds() {
        return pipelineSettingsService.getThresholds();
    }

    // ── Prompt Template endpoints ──

    @GetMapping("/api/v2/prompts")
    public List<PromptTemplateService.PromptDto> listPrompts() {
        return promptTemplateService.listAll();
    }

    @GetMapping("/api/v2/prompts/stage/{stage}")
    public List<PromptTemplateService.PromptDto> listPromptsByStage(@PathVariable String stage) {
        return promptTemplateService.listByStage(stage);
    }

    @GetMapping("/api/v2/prompts/{id}")
    public PromptTemplateService.PromptDto getPrompt(@PathVariable long id) {
        return promptTemplateService.getById(id);
    }

    @GetMapping("/api/v2/prompts/code/{code}")
    public PromptTemplateService.PromptDto getPromptByCode(@PathVariable String code) {
        return promptTemplateService.getByCode(code);
    }

    @PostMapping("/api/v2/prompts")
    public PromptTemplateService.PromptDto createPrompt(@RequestBody PromptTemplateService.CreatePromptRequest request) {
        return promptTemplateService.createPrompt(request);
    }

    @PutMapping("/api/v2/prompts/{id}")
    public PromptTemplateService.PromptDto updatePrompt(@PathVariable long id, @RequestBody PromptTemplateService.UpdatePromptRequest request) {
        return promptTemplateService.updatePrompt(id, request);
    }

    @PostMapping("/api/v2/prompts/{id}/activate")
    public PromptTemplateService.PromptDto activatePrompt(@PathVariable long id, @RequestParam(defaultValue = "system") String updatedBy) {
        return promptTemplateService.activatePrompt(id, updatedBy);
    }

    @GetMapping("/api/v2/prompts/{id}/versions")
    public List<PromptTemplateService.PromptVersionDto> listPromptVersions(@PathVariable long id) {
        return promptTemplateService.listVersions(id);
    }

    @PostMapping("/api/v2/prompts/{id}/rollback/{version}")
    public PromptTemplateService.PromptDto rollbackPrompt(@PathVariable long id, @PathVariable int version, @RequestParam(defaultValue = "system") String updatedBy) {
        return promptTemplateService.rollbackPrompt(id, version, updatedBy);
    }

    @PostMapping("/api/v2/prompts/{id}/test")
    public PromptTemplateService.TestPromptResult testPrompt(@PathVariable long id, @RequestBody PromptTemplateService.TestPromptRequest request) {
        return promptTemplateService.testPrompt(id, request);
    }

    @GetMapping("/api/v2/prompts/route-mapping")
    public Map<String, Object> promptRouteMapping() {
        return promptTemplateService.routeMapping();
    }

    // ── Classifier extended endpoints ──

    @GetMapping("/api/v2/classifiers/route-mapping")
    public Map<String, Object> classifierRouteMapping() {
        return promptTemplateService.routeMapping();
    }
}
