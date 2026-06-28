package com.larbcorp.neuroinfogrinder2.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.replay.ModelWorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ClassificationRuleService {
    private static final Logger log = LoggerFactory.getLogger(ClassificationRuleService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ModelWorkerClient worker;

    public ClassificationRuleService(JdbcTemplate jdbc, ObjectMapper json, ModelWorkerClient worker) {
        this.jdbc = jdbc;
        this.json = json;
        this.worker = worker;
    }

    public record RuleDto(
        long id, String code, String name, String description, String decision, int priority,
        boolean enabled, String conditionType, JsonNode keywordsJson, JsonNode regexJson,
        Integer minTextLength, Integer maxTextLength, boolean requiresLink, boolean requiresCodeBlock,
        boolean requiresErrorPattern, boolean requiresPricePattern, boolean requiresQuestionPattern,
        boolean requiresSolutionPattern, JsonNode examplesJson, JsonNode scoringWeightsJson,
        int version, boolean isActive, OffsetDateTime createdAt, OffsetDateTime updatedAt, String updatedBy
    ) {}

    public record RuleVersionDto(long id, long ruleId, int version, JsonNode snapshotJson, String changeReason, OffsetDateTime createdAt, String createdBy) {}

    public List<RuleDto> listRules() {
        return jdbc.query("SELECT * FROM classification_rules ORDER BY priority, id", this::mapRule);
    }

    public RuleDto getRule(long id) {
        return jdbc.queryForObject("SELECT * FROM classification_rules WHERE id = ?", this::mapRule, id);
    }

    @Transactional
    public RuleDto createRule(CreateRuleRequest req) {
        long id = jdbc.queryForObject("""
            INSERT INTO classification_rules (code, name, description, decision, priority, enabled, condition_type,
                keywords_json, regex_json, min_text_length, max_text_length,
                requires_link, requires_code_block, requires_error_pattern, requires_price_pattern,
                requires_question_pattern, requires_solution_pattern, examples_json, scoring_weights_json,
                version, is_active, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, ?,
                ?::jsonb, ?::jsonb, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?::jsonb, ?::jsonb,
                1, true, ?)
            RETURNING id
            """, Long.class,
            req.code(), req.name(), req.description(), req.decision(), req.priority(),
            req.enabled() != null && req.enabled(), req.conditionType() != null ? req.conditionType() : "KEYWORD_AND_LENGTH",
            write(req.keywordsJson() != null ? req.keywordsJson() : json.createArrayNode()),
            write(req.regexJson() != null ? req.regexJson() : json.createArrayNode()),
            req.minTextLength(), req.maxTextLength(),
            req.requiresLink() != null && req.requiresLink(),
            req.requiresCodeBlock() != null && req.requiresCodeBlock(),
            req.requiresErrorPattern() != null && req.requiresErrorPattern(),
            req.requiresPricePattern() != null && req.requiresPricePattern(),
            req.requiresQuestionPattern() != null && req.requiresQuestionPattern(),
            req.requiresSolutionPattern() != null && req.requiresSolutionPattern(),
            write(req.examplesJson() != null ? req.examplesJson() : json.createArrayNode()),
            write(req.scoringWeightsJson() != null ? req.scoringWeightsJson() : json.createObjectNode()),
            req.updatedBy()
        );
        return getRule(id);
    }

    @Transactional
    public RuleDto updateRule(long id, UpdateRuleRequest req) {
        RuleDto existing = getRule(id);
        jdbc.update("""
            UPDATE classification_rules SET
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                decision = COALESCE(?, decision),
                priority = COALESCE(?, priority),
                enabled = COALESCE(?, enabled),
                condition_type = COALESCE(?, condition_type),
                keywords_json = CASE WHEN ? IS NOT NULL THEN ?::jsonb ELSE keywords_json END,
                regex_json = CASE WHEN ? IS NOT NULL THEN ?::jsonb ELSE regex_json END,
                min_text_length = ?, max_text_length = ?,
                requires_link = COALESCE(?, requires_link),
                requires_code_block = COALESCE(?, requires_code_block),
                requires_error_pattern = COALESCE(?, requires_error_pattern),
                requires_price_pattern = COALESCE(?, requires_price_pattern),
                requires_question_pattern = COALESCE(?, requires_question_pattern),
                requires_solution_pattern = COALESCE(?, requires_solution_pattern),
                examples_json = CASE WHEN ? IS NOT NULL THEN ?::jsonb ELSE examples_json END,
                scoring_weights_json = CASE WHEN ? IS NOT NULL THEN ?::jsonb ELSE scoring_weights_json END,
                version = version + 1,
                updated_by = COALESCE(?, updated_by),
                updated_at = now()
            WHERE id = ?
            """,
            req.name(), req.description(), req.decision(), req.priority(),
            req.enabled(), req.conditionType(),
            req.keywordsJson() != null ? req.keywordsJson().toString() : null,
            req.keywordsJson() != null ? req.keywordsJson().toString() : null,
            req.regexJson() != null ? req.regexJson().toString() : null,
            req.regexJson() != null ? req.regexJson().toString() : null,
            req.minTextLength(), req.maxTextLength(),
            req.requiresLink(), req.requiresCodeBlock(), req.requiresErrorPattern(),
            req.requiresPricePattern(), req.requiresQuestionPattern(), req.requiresSolutionPattern(),
            req.examplesJson() != null ? req.examplesJson().toString() : null,
            req.examplesJson() != null ? req.examplesJson().toString() : null,
            req.scoringWeightsJson() != null ? req.scoringWeightsJson().toString() : null,
            req.scoringWeightsJson() != null ? req.scoringWeightsJson().toString() : null,
            req.updatedBy(), id
        );
        jdbc.update("""
            INSERT INTO classification_rule_versions (rule_id, version, snapshot_json, change_reason, created_by)
            SELECT ?, version, row_to_json(t)::jsonb, ?, ?
            FROM (SELECT * FROM classification_rules WHERE id = ?) t
            """, id, req.changeReason(), req.updatedBy(), id);
        return getRule(id);
    }

    @Transactional
    public RuleDto activateRule(long id, String updatedBy) {
        jdbc.update("UPDATE classification_rules SET is_active = true, updated_at = now(), updated_by = COALESCE(?, updated_by) WHERE id = ?", updatedBy, id);
        return getRule(id);
    }

    @Transactional
    public RuleDto deactivateRule(long id, String updatedBy) {
        jdbc.update("UPDATE classification_rules SET is_active = false, updated_at = now(), updated_by = COALESCE(?, updated_by) WHERE id = ?", updatedBy, id);
        return getRule(id);
    }

    @Transactional
    public RuleDto rollbackRule(long id, int version, String updatedBy) {
        RuleVersionDto ver = jdbc.queryForObject("""
            SELECT * FROM classification_rule_versions WHERE rule_id = ? AND version = ? ORDER BY created_at DESC LIMIT 1
            """, this::mapVersion, id, version);
        if (ver == null) throw new IllegalArgumentException("Version " + version + " not found for rule " + id);
        jdbc.update("""
            UPDATE classification_rules SET
                name = ?::jsonb->>'name', description = ?::jsonb->>'description',
                decision = ?::jsonb->>'decision', priority = (?::jsonb->>'priority')::int,
                enabled = (?::jsonb->>'enabled')::boolean,
                keywords_json = COALESCE(?::jsonb->'keywords_json', '[]'::jsonb)::jsonb,
                regex_json = COALESCE(?::jsonb->'regex_json', '[]'::jsonb)::jsonb,
                version = version + 1,
                updated_by = COALESCE(?, updated_by),
                updated_at = now()
            WHERE id = ?
            """,
            write(ver.snapshotJson()), write(ver.snapshotJson()), write(ver.snapshotJson()),
            write(ver.snapshotJson()), write(ver.snapshotJson()),
            write(ver.snapshotJson()), write(ver.snapshotJson()),
            updatedBy, id
        );
        return getRule(id);
    }

    public List<RuleVersionDto> listRuleVersions(long ruleId) {
        return jdbc.query("SELECT * FROM classification_rule_versions WHERE rule_id = ? ORDER BY version DESC", this::mapVersion, ruleId);
    }

    public ClassifierTestResult testMessage(ClassifierTestRequest request) {
        String text = request.text() != null ? request.text() : "";
        String normalized = text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        List<RuleMatch> matches = new ArrayList<>();
        List<RuleDto> rules = listRules().stream().filter(RuleDto::enabled).toList();
        for (RuleDto rule : rules) {
            RuleMatch match = evaluateRule(rule, normalized);
            if (match != null) matches.add(match);
        }
        ObjectNode classifierResult = json.createObjectNode();
        classifierResult.put("label", "NOISE_OR_CHAT");
        classifierResult.put("confidence", 0.0);
        classifierResult.put("model", "BOOTSTRAP_CLASSIFIER");
        try {
            if (!normalized.isBlank()) {
                JsonNode workerResponse = worker.classify(List.of(new ModelWorkerClient.WorkerItem("1", normalized, json.createObjectNode())));
                if (workerResponse.path("results").isArray() && workerResponse.path("results").size() > 0) {
                    JsonNode first = workerResponse.path("results").get(0);
                    classifierResult = (ObjectNode) first;
                    if (!classifierResult.hasNonNull("label")) {
                        classifierResult.put("label", classifierResult.path("topLabel").asText("NOISE_OR_CHAT"));
                    }
                    classifierResult.put("model", "BOOTSTRAP_CLASSIFIER");
                    ObjectNode metadata = classifierResult.withObject("metadata");
                    metadata.put("classifierKind", "BOOTSTRAP_CLASSIFIER");
                }
            }
        } catch (Exception e) {
            classifierResult.put("status", "WORKER_DOWN");
            classifierResult.put("error", e.getMessage());
        }
        ObjectNode smScore = computeSingleMessageScore(normalized, matches, classifierResult);
        String finalDecision = resolveFinalDecision(matches, smScore);
        String pipelineAction = resolvePipelineAction(finalDecision);
        ObjectNode llmNextRoute = resolveLlmNextRoute(finalDecision);
        ObjectNode result = json.createObjectNode();
        result.set("ruleMatches", json.valueToTree(matches));
        result.set("classifier", classifierResult);
        result.set("singleMessage", smScore);
        result.put("finalDecision", finalDecision);
        result.put("pipelineAction", pipelineAction);
        result.set("llmNextRoute", llmNextRoute);
        result.put("explanation", buildExplanation(matches, smScore, finalDecision));
        return new ClassifierTestResult(
            matches,
            classifierResult,
            smScore,
            finalDecision,
            pipelineAction,
            llmNextRoute,
            result.path("explanation").asText()
        );
    }

    public Map<String, Long> distribution(long runId) {
        Map<String, Long> dist = new LinkedHashMap<>();
        jdbc.query("SELECT top_label, count(*) AS cnt FROM message_classifications WHERE run_id = ? GROUP BY top_label ORDER BY cnt DESC", (RowCallbackHandler) rs -> dist.put(rs.getString("top_label"), rs.getLong("cnt")), runId);
        return dist;
    }

    public List<Map<String, Object>> recentDecisions(long runId, int limit) {
        return jdbc.queryForList("""
            SELECT mi.dataset_message_id, LEFT(mi.normalized_text, 200) AS text_preview,
                   mi.rule_decision, mc.top_label AS classifier_label, mc.confidence AS classifier_confidence,
                   rrm.final_decision, rrm.status AS pipeline_action
            FROM message_intelligence mi
            LEFT JOIN message_classifications mc ON mc.run_id = mi.run_id AND mc.dataset_message_id = mi.dataset_message_id
            LEFT JOIN replay_run_messages rrm ON rrm.run_id = mi.run_id AND rrm.dataset_message_id = mi.dataset_message_id
            WHERE mi.run_id = ?
            ORDER BY mi.id DESC
            LIMIT ?
            """, runId, limit);
    }

    public List<Map<String, Object>> singleMessageCandidates(long runId, int limit) {
        return jdbc.queryForList("""
            SELECT mi.dataset_message_id, LEFT(mi.normalized_text, 200) AS text_preview,
                   mi.rule_decision, rrm.final_decision, rrm.status AS pipeline_action,
                   mc.top_label AS classifier_label, mc.confidence AS classifier_confidence,
                   ki.id AS knowledge_item_id, ki.title AS knowledge_item_title
            FROM message_intelligence mi
            JOIN replay_run_messages rrm ON rrm.run_id = mi.run_id AND rrm.dataset_message_id = mi.dataset_message_id
            LEFT JOIN message_classifications mc ON mc.run_id = mi.run_id AND mc.dataset_message_id = mi.dataset_message_id
            LEFT JOIN knowledge_item_sources kis ON kis.dataset_message_id = mi.dataset_message_id AND kis.knowledge_item_id IN (SELECT id FROM knowledge_items WHERE run_id = mi.run_id)
            LEFT JOIN knowledge_items ki ON ki.id = kis.knowledge_item_id
            WHERE mi.run_id = ? AND mi.rule_decision IN ('CANDIDATE', 'ACCUMULATE') AND mi.hard_signal = true
            ORDER BY LENGTH(mi.normalized_text) DESC
            LIMIT ?
            """, runId, limit);
    }

    private RuleMatch evaluateRule(RuleDto rule, String text) {
        boolean lengthOk = true;
        if (rule.minTextLength() != null && text.length() < rule.minTextLength()) lengthOk = false;
        if (rule.maxTextLength() != null && text.length() > rule.maxTextLength()) lengthOk = false;
        if (!lengthOk) return null;

        List<String> keywordMatches = new ArrayList<>();
        for (JsonNode kw : rule.keywordsJson()) {
            String keyword = kw.asText();
            if (text.toLowerCase().contains(keyword.toLowerCase())) keywordMatches.add(keyword);
        }
        List<String> regexMatches = new ArrayList<>();
        for (JsonNode rx : rule.regexJson()) {
            try {
                Pattern p = Pattern.compile(rx.asText());
                var m = p.matcher(text);
                if (m.find()) regexMatches.add(rx.asText());
            } catch (Exception ignored) {}
        }
        if (rule.requiresLink() && !hasLink(text)) return null;
        if (rule.requiresCodeBlock() && !hasCode(text)) return null;
        if (rule.requiresErrorPattern() && !hasError(text)) return null;
        if (rule.requiresPricePattern() && !hasPrice(text)) return null;
        if (rule.requiresQuestionPattern() && !hasQuestion(text)) return null;
        if (rule.requiresSolutionPattern() && !hasSolution(text)) return null;

        if (keywordMatches.isEmpty() && regexMatches.isEmpty() && !hasAnyRequirement(rule)) return null;
        return new RuleMatch(rule.id(), rule.name(), rule.decision(), keywordMatches, regexMatches);
    }

    private boolean hasAnyRequirement(RuleDto r) { return r.requiresLink() || r.requiresCodeBlock() || r.requiresErrorPattern() || r.requiresPricePattern() || r.requiresQuestionPattern() || r.requiresSolutionPattern(); }
    private boolean hasLink(String t) { return t.matches("(?i).*https?://.*"); }
    private boolean hasCode(String t) { return t.matches("(?is).*(```|\\b(curl|json|yaml|docker|npm|mvn|python|java|class|function|const|select|insert|update)\\b).*"); }
    private boolean hasError(String t) { return t.matches("(?i).*(error|exception|traceback|stacktrace|failed|ошиб|исключ|не работает|cannot|timeout).*"); }
    private boolean hasPrice(String t) { return t.matches("(?i).*(\\$\\s?\\d+|\\d+\\s?(usd|eur|руб|₽)|price|pricing|тариф|лимит|quota|access|доступ).*"); }
    private boolean hasQuestion(String t) { return t.contains("?") || t.matches("(?i).*\\b(как|how|why|почему|что делать)\\b.*"); }
    private boolean hasSolution(String t) { return t.matches("(?i).*\\b(fix|решение|попробуй|нужно|use|install|run|шаг|провер|убедись)\\b.*"); }

    private ObjectNode computeSingleMessageScore(String text, List<RuleMatch> matches, JsonNode classifier) {
        ObjectNode score = json.createObjectNode();
        if (text.isBlank()) {
            score.put("score", 0.0); score.put("decision", "NONE"); score.set("signals", json.createArrayNode());
            return score;
        }
        double textLengthScore = Math.min(1.0, text.length() / 500.0);
        double structureScore = text.matches("(?is).*(?:^|\\n)(?:проблема|причина|решение|ожидаемый|шаг \\d|1\\.|2\\.|3\\.).*") ? 0.85 : text.matches("(?is).*(?:^|\\n)(?:problem|cause|solution|step \\d).*") ? 0.80 : text.length() > 400 ? 0.45 : 0.15;
        double guideHowToScore = text.matches("(?i).*(?:шаг|этап|инструкц|руководств|как настроить|как установить|как исправить|пошагов|гайд|tutorial|guide|how to|setup|configure).*") ? 0.80 : text.length() > 300 ? 0.35 : 0.05;
        double troubleshootingScore = text.matches("(?i).*(?:ошибка|error|exception|не работает|проблема|проблем|баг|bug|crash|fail|ошибк).*") ? 0.75 : 0.05;
        double errorSignalScore = text.matches("(?i).*(?:error|exception|traceback|stack.?trace|failed|ошиб|исключ|401|403|500|timeout|not found).*") ? 0.85 : text.matches("(?i).*(?:не работает|crash|bug|fail).*") ? 0.55 : 0.0;
        double solutionSignalScore = text.matches("(?i).*(?:решение|fix|workaround|попробуй|нужно|исправ|подключ|настрой|установ|remedy).*") ? 0.80 : text.matches("(?i).*(?:check|ensure|verify|run|install|update|restart).*") ? 0.50 : 0.0;
        double resourceSignalScore = text.matches("(?i).*(?:ресурс|ссылка|link|resource|инструмент|tool|сервис|platform|service|website).*") ? 0.70 : 0.05;
        double priceAccessSignalScore = text.matches("(?i).*(?:цена|price|cost|тариф|стоимость|доступ|access|subscription|подписк|бесплатно|free).*") ? 0.80 : 0.05;
        double questionAnswerScore = (text.contains("?") && text.length() > 80) ? 0.45 : text.matches("(?i).*(?:как|how|why|что делать|помогите).*") ? 0.35 : 0.0;
        double codeOrCommandScore = text.matches("(?is).*(?:```|curl |npm |pip |docker |git |ssh |kubectl|SELECT |INSERT |UPDATE |DELETE ).*") ? 0.85 : text.matches("(?i).*(?:команда|command|код|code|script|config).*") ? 0.50 : 0.0;
        double linkScore = hasLink(text) ? Math.min(0.8, text.split("https?://").length * 0.15) : 0.0;
        double classificationConfidence = classifier.path("confidence").asDouble(0) * (classifier.path("topLabel").asText("").equals("NOISE_OR_CHAT") ? 0.3 : 0.6);
        double noisePenalty = text.length() < 50 ? 0.30 : text.length() < 100 ? 0.15 : text.matches("(?is).*(спасибо|thanks|понял|ок|okay|да|нет|ага|лол).*") && text.length() < 150 ? 0.25 : 0.0;

        double finalScore = textLengthScore * 0.10 + structureScore * 0.15 + guideHowToScore * 0.12
            + troubleshootingScore * 0.10 + errorSignalScore * 0.10 + solutionSignalScore * 0.12
            + resourceSignalScore * 0.05 + priceAccessSignalScore * 0.05 + questionAnswerScore * 0.08
            + codeOrCommandScore * 0.08 + linkScore * 0.05 + classificationConfidence * 0.05 - noisePenalty;
        finalScore = Math.max(0, Math.min(1.0, finalScore));
        String decision = finalScore >= 0.72 ? "DIRECT_MATERIAL_READY" : finalScore >= 0.55 ? "SINGLE_MESSAGE_MATERIAL_CANDIDATE" : "ACCUMULATE";
        ArrayNode signals = json.createArrayNode();
        if (errorSignalScore > 0.5) signals.add("ERROR_SIGNAL");
        if (solutionSignalScore > 0.5) signals.add("SOLUTION_SIGNAL");
        if (codeOrCommandScore > 0.5) signals.add("CODE_OR_COMMAND");
        if (guideHowToScore > 0.5) signals.add("GUIDE_OR_HOWTO");
        if (troubleshootingScore > 0.5) signals.add("TROUBLESHOOTING");
        if (resourceSignalScore > 0.4) signals.add("RESOURCE_SIGNAL");
        if (priceAccessSignalScore > 0.4) signals.add("PRICE_ACCESS_SIGNAL");
        if (questionAnswerScore > 0.3) signals.add("QUESTION_ANSWER");
        if (linkScore > 0.3) signals.add("HAS_LINKS");
        if (structureScore > 0.5) signals.add("HAS_STRUCTURE");
        score.put("score", Math.round(finalScore * 100.0) / 100.0);
        score.put("textLengthScore", Math.round(textLengthScore * 100.0) / 100.0);
        score.put("structureScore", Math.round(structureScore * 100.0) / 100.0);
        score.put("guideHowToScore", Math.round(guideHowToScore * 100.0) / 100.0);
        score.put("troubleshootingScore", Math.round(troubleshootingScore * 100.0) / 100.0);
        score.put("errorSignalScore", Math.round(errorSignalScore * 100.0) / 100.0);
        score.put("solutionSignalScore", Math.round(solutionSignalScore * 100.0) / 100.0);
        score.put("resourceSignalScore", Math.round(resourceSignalScore * 100.0) / 100.0);
        score.put("priceAccessSignalScore", Math.round(priceAccessSignalScore * 100.0) / 100.0);
        score.put("questionAnswerScore", Math.round(questionAnswerScore * 100.0) / 100.0);
        score.put("codeOrCommandScore", Math.round(codeOrCommandScore * 100.0) / 100.0);
        score.put("linkScore", Math.round(linkScore * 100.0) / 100.0);
        score.put("classificationConfidence", Math.round(classificationConfidence * 100.0) / 100.0);
        score.put("noisePenalty", Math.round(noisePenalty * 100.0) / 100.0);
        score.put("decision", decision);
        score.set("signals", signals);
        return score;
    }

    private String resolveFinalDecision(List<RuleMatch> matches, ObjectNode smScore) {
        if (matches.stream().anyMatch(m -> "SUPPRESS".equals(m.decision()))) return "SUPPRESS";
        if (matches.stream().anyMatch(m -> "DIRECT_MATERIAL_READY".equals(m.decision()))) return "DIRECT_MATERIAL_READY";
        if (matches.stream().anyMatch(m -> "SINGLE_MESSAGE_MATERIAL_CANDIDATE".equals(m.decision()))) return "SINGLE_MESSAGE_MATERIAL_CANDIDATE";
        String smDecision = smScore.path("decision").asText("ACCUMULATE");
        if ("DIRECT_MATERIAL_READY".equals(smDecision)) return "DIRECT_MATERIAL_READY";
        if ("SINGLE_MESSAGE_MATERIAL_CANDIDATE".equals(smDecision)) return "SINGLE_MESSAGE_MATERIAL_CANDIDATE";
        if (matches.stream().anyMatch(m -> "CANDIDATE".equals(m.decision()))) return "CANDIDATE";
        return "ACCUMULATE";
    }

    private String resolvePipelineAction(String decision) {
        return switch (decision) {
            case "DIRECT_MATERIAL_READY" -> "GENERATE_MATERIAL_DIRECTLY";
            case "SINGLE_MESSAGE_MATERIAL_CANDIDATE" -> "LLM_JUDGE_FOR_SINGLE_MESSAGE";
            case "CANDIDATE" -> "WAIT_FOR_CLUSTER";
            case "ACCUMULATE" -> "ACCUMULATE_FOR_CLUSTER";
            case "SUPPRESS" -> "SKIP";
            default -> "NO_ACTION";
        };
    }

    private ObjectNode resolveLlmNextRoute(String decision) {
        if ("DIRECT_MATERIAL_READY".equals(decision) || "SINGLE_MESSAGE_MATERIAL_CANDIDATE".equals(decision)) {
            ObjectNode route = json.createObjectNode();
            route.put("stage", "LLM_CLUSTER_JUDGE_AND_ROUTING");
            route.put("promptCode", "LLM_CLUSTER_JUDGE_AND_ROUTING");
            route.put("promptName", "LLM Judge & Routing");
            route.put("mode", "SINGLE_MESSAGE");
            return route;
        }
        if ("CANDIDATE".equals(decision)) {
            ObjectNode route = json.createObjectNode();
            route.put("stage", "BGE-M3 clustering -> LLM_CLUSTER_JUDGE_AND_ROUTING");
            route.put("promptCode", "LLM_CLUSTER_JUDGE_AND_ROUTING");
            route.put("promptName", "LLM Judge & Routing");
            route.put("mode", "CLUSTER");
            return route;
        }
        return null;
    }

    private String buildExplanation(List<RuleMatch> matches, ObjectNode smScore, String decision) {
        StringBuilder sb = new StringBuilder();
        if (!matches.isEmpty()) {
            sb.append("Rule matches: ").append(matches.stream().map(RuleMatch::ruleName).collect(Collectors.joining(", "))).append(". ");
        }
        double score = smScore.path("score").asDouble(0);
        if (score > 0) {
            sb.append("Single-message score: ").append(String.format("%.2f", score)).append(". ");
        }
        sb.append("Final decision: ").append(decision).append(".");
        return sb.toString();
    }

    private RuleDto mapRule(ResultSet rs, int rowNum) throws SQLException {
        return new RuleDto(
            rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("description"),
            rs.getString("decision"), rs.getInt("priority"), rs.getBoolean("enabled"),
            rs.getString("condition_type"), parseJson(rs.getString("keywords_json")),
            parseJson(rs.getString("regex_json")),
            (Integer) rs.getObject("min_text_length"), (Integer) rs.getObject("max_text_length"),
            rs.getBoolean("requires_link"), rs.getBoolean("requires_code_block"),
            rs.getBoolean("requires_error_pattern"), rs.getBoolean("requires_price_pattern"),
            rs.getBoolean("requires_question_pattern"), rs.getBoolean("requires_solution_pattern"),
            parseJson(rs.getString("examples_json")), parseJson(rs.getString("scoring_weights_json")),
            rs.getInt("version"), rs.getBoolean("is_active"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
            rs.getString("updated_by")
        );
    }

    private RuleVersionDto mapVersion(ResultSet rs, int rowNum) throws SQLException {
        return new RuleVersionDto(rs.getLong("id"), rs.getLong("rule_id"), rs.getInt("version"),
            parseJson(rs.getString("snapshot_json")), rs.getString("change_reason"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getString("created_by"));
    }

    private JsonNode parseJson(String value) {
        try { return json.readTree(value != null ? value : "{}"); } catch (Exception e) { return json.createObjectNode(); }
    }
    private String write(JsonNode node) {
        try { return json.writeValueAsString(node == null ? json.createObjectNode() : node); } catch (Exception e) { return "{}"; }
    }

    public record ClassifierTestRequest(String text) {}
    public record ClassifierTestResult(List<RuleMatch> ruleMatches, JsonNode classifier, JsonNode singleMessage, String finalDecision, String pipelineAction, JsonNode llmNextRoute, String explanation) {}
    public record CreateRuleRequest(String code, String name, String description, String decision, Integer priority, Boolean enabled, String conditionType, JsonNode keywordsJson, JsonNode regexJson, Integer minTextLength, Integer maxTextLength, Boolean requiresLink, Boolean requiresCodeBlock, Boolean requiresErrorPattern, Boolean requiresPricePattern, Boolean requiresQuestionPattern, Boolean requiresSolutionPattern, JsonNode examplesJson, JsonNode scoringWeightsJson, String updatedBy) {}
    public record UpdateRuleRequest(String name, String description, String decision, Integer priority, Boolean enabled, String conditionType, JsonNode keywordsJson, JsonNode regexJson, Integer minTextLength, Integer maxTextLength, Boolean requiresLink, Boolean requiresCodeBlock, Boolean requiresErrorPattern, Boolean requiresPricePattern, Boolean requiresQuestionPattern, Boolean requiresSolutionPattern, JsonNode examplesJson, JsonNode scoringWeightsJson, String changeReason, String updatedBy) {}
    public record RuleMatch(long ruleId, String ruleName, String decision, List<String> keywordMatches, List<String> regexMatches) {}
}
