package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class KnowledgeItemReviewService {
    private static final Path REPORT_PATH = Path.of("runs", "quality-review-run-8-and-7.json");
    private static final Set<String> ACTIONS = Set.of("useful", "not useful", "wrong artifact", "duplicate", "hallucination", "needs edit");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public KnowledgeItemReviewService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public ReviewRunResult review(ReviewRequest request) {
        List<Long> runIds = normalizeRunIds(request == null ? null : request.runIds());
        List<KnowledgeItem> items = loadItems(runIds);
        Map<Long, List<SourceMessage>> sources = loadSources(items.stream().map(KnowledgeItem::id).toList());
        long eventsBefore = count("labeling_events");
        long examplesBefore = count("training_examples");
        List<ReviewDetail> details = new ArrayList<>();

        for (KnowledgeItem item : items) {
            ReviewDecision decision = score(item, sources.getOrDefault(item.id(), List.of()), items);
            upsertReview(item, decision);
            long labelingItemId = upsertLabelingItem(item, decision);
            upsertLabelingEvent(labelingItemId, item, decision, "AUTO_KNOWLEDGE_ITEM_REVIEW", "AUTO", decision.verdict());
            upsertTrainingExample(item, decision);
            details.add(toDetail(item, decision, sources.getOrDefault(item.id(), List.of())));
        }

        long eventsCreated = Math.max(0, count("labeling_events") - eventsBefore);
        long examplesCreated = Math.max(0, count("training_examples") - examplesBefore);
        Map<String, Long> breakdown = verdictBreakdown(details);
        writeReport(runIds, details, breakdown, eventsCreated, examplesCreated);
        return new ReviewRunResult(runIds, details.size(), breakdown, eventsCreated, examplesCreated, reportPath(), best(details), worst(details), sourceProblems(details), hallucinationProblems(details), duplicateProblems(details));
    }

    public ReviewSummary summary(List<Long> runIds) {
        List<Long> normalized = normalizeRunIds(runIds);
        List<KnowledgeItem> items = loadItems(normalized);
        Map<Long, List<SourceMessage>> sources = loadSources(items.stream().map(KnowledgeItem::id).toList());
        Map<Long, ReviewDecision> reviews = loadReviews(items.stream().map(KnowledgeItem::id).toList());
        List<ReviewDetail> details = items.stream()
                .map(item -> toDetail(item, reviews.getOrDefault(item.id(), score(item, sources.getOrDefault(item.id(), List.of()), items)), sources.getOrDefault(item.id(), List.of())))
                .toList();
        return new ReviewSummary(normalized, details.size(), verdictBreakdown(details), best(details), worst(details), details);
    }

    public ReviewDetail detail(long knowledgeItemId) {
        KnowledgeItem item = loadItem(knowledgeItemId);
        List<SourceMessage> sources = loadSources(List.of(knowledgeItemId)).getOrDefault(knowledgeItemId, List.of());
        ReviewDecision decision = loadReviews(List.of(knowledgeItemId)).getOrDefault(knowledgeItemId, score(item, sources, List.of(item)));
        return toDetail(item, decision, sources);
    }

    @Transactional
    public ReviewActionResult action(long knowledgeItemId, ReviewActionRequest request) {
        String action = request == null || request.action() == null ? "" : request.action().trim().toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) throw new IllegalArgumentException("Unsupported review action: " + action);
        KnowledgeItem item = loadItem(knowledgeItemId);
        ReviewDetail current = detail(knowledgeItemId);
        String verdict = switch (action) {
            case "useful" -> "PUBLISHABLE";
            case "not useful" -> "LOW_VALUE";
            case "wrong artifact" -> "WRONG_ARTIFACT_TYPE";
            case "duplicate" -> "DUPLICATE";
            case "hallucination" -> "BAD_SOURCE_SUPPORT";
            case "needs edit" -> "NEEDS_EDIT";
            default -> current.verdict();
        };
        List<String> reasons = new ArrayList<>(current.reasons());
        reasons.add("UI action: " + action);
        ReviewDecision decision = new ReviewDecision(verdict, current.scores(), reasons.stream().distinct().toList());
        upsertReview(item, decision);
        long labelingItemId = upsertLabelingItem(item, decision);
        upsertLabelingEvent(labelingItemId, item, decision, "UI_REVIEW_ACTION", "local-ui", action);
        upsertTrainingExample(item, decision);
        return new ReviewActionResult(knowledgeItemId, action, verdict, labelingItemId);
    }

    private List<Long> normalizeRunIds(List<Long> runIds) {
        if (runIds == null || runIds.isEmpty()) return List.of(8L, 7L);
        return runIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private List<KnowledgeItem> loadItems(List<Long> runIds) {
        if (runIds.isEmpty()) return List.of();
        String placeholders = placeholders(runIds.size());
        return jdbc.query("""
                SELECT id, run_id, title, artifact_type, summary, body_json::text, content_json::text, confidence
                FROM knowledge_items
                WHERE run_id IN (%s)
                ORDER BY run_id DESC, id
                """.formatted(placeholders), (rs, rowNum) -> new KnowledgeItem(rs.getLong("id"), rs.getLong("run_id"), rs.getString("title"), rs.getString("artifact_type"), rs.getString("summary"), rs.getString("body_json"), rs.getString("content_json"), nullableDouble(rs.getBigDecimal("confidence"))), runIds.toArray());
    }

    private KnowledgeItem loadItem(long id) {
        return jdbc.queryForObject("""
                SELECT id, run_id, title, artifact_type, summary, body_json::text, content_json::text, confidence
                FROM knowledge_items WHERE id = ?
                """, (rs, rowNum) -> new KnowledgeItem(rs.getLong("id"), rs.getLong("run_id"), rs.getString("title"), rs.getString("artifact_type"), rs.getString("summary"), rs.getString("body_json"), rs.getString("content_json"), nullableDouble(rs.getBigDecimal("confidence"))), id);
    }

    private Map<Long, List<SourceMessage>> loadSources(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = placeholders(ids.size());
        List<SourceMessage> rows = jdbc.query("""
                SELECT kis.knowledge_item_id, kis.dataset_message_id, kis.source_role, kis.quote, kis.confidence,
                       dm.text, dm.caption, dm.raw_json::text, dm.chat_title, dm.message_date
                FROM knowledge_item_sources kis
                JOIN dataset_messages dm ON dm.id = kis.dataset_message_id
                WHERE kis.knowledge_item_id IN (%s)
                ORDER BY kis.knowledge_item_id, kis.id
                """.formatted(placeholders), (rs, rowNum) -> new SourceMessage(rs.getLong("knowledge_item_id"), rs.getLong("dataset_message_id"), rs.getString("source_role"), rs.getString("quote"), nullableDouble(rs.getBigDecimal("confidence")), rs.getString("text"), rs.getString("caption"), rs.getString("raw_json"), rs.getString("chat_title"), rs.getObject("message_date", OffsetDateTime.class)), ids.toArray());
        Map<Long, List<SourceMessage>> result = new LinkedHashMap<>();
        for (SourceMessage row : rows) result.computeIfAbsent(row.knowledgeItemId(), ignored -> new ArrayList<>()).add(row);
        return result;
    }

    private Map<Long, ReviewDecision> loadReviews(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = placeholders(ids.size());
        List<Map.Entry<Long, ReviewDecision>> rows = jdbc.query("""
                SELECT knowledge_item_id,
                       source_supported_score,
                       hallucination_risk_score,
                       publishability_score,
                       commercial_value_score,
                       actionability_score,
                       specificity_score,
                       duplicate_risk_score,
                       artifact_type_correct,
                       title_quality_score,
                       body_quality_score,
                       verdict,
                       review_reasons_json::text
                FROM knowledge_item_reviews
                WHERE reviewer_type = 'AUTO' AND knowledge_item_id IN (%s)
                """.formatted(placeholders), (rs, rowNum) -> Map.entry(rs.getLong("knowledge_item_id"), new ReviewDecision(rs.getString("verdict"), new Scores(
                        rs.getDouble("source_supported_score"),
                        rs.getDouble("hallucination_risk_score"),
                        rs.getDouble("publishability_score"),
                        rs.getDouble("commercial_value_score"),
                        rs.getDouble("actionability_score"),
                        rs.getDouble("specificity_score"),
                        rs.getDouble("duplicate_risk_score"),
                        rs.getBoolean("artifact_type_correct"),
                        rs.getDouble("title_quality_score"),
                        rs.getDouble("body_quality_score")
                ), stringList(rs.getString("review_reasons_json")))), ids.toArray());
        Map<Long, ReviewDecision> result = new LinkedHashMap<>();
        for (Map.Entry<Long, ReviewDecision> row : rows) result.put(row.getKey(), row.getValue());
        return result;
    }

    private ReviewDecision score(KnowledgeItem item, List<SourceMessage> sources, List<KnowledgeItem> allItems) {
        String title = valueOr(item.title(), "").trim();
        String body = itemText(item);
        String combined = (title + " " + body).toLowerCase(Locale.ROOT);
        String sourceText = sources.stream().map(SourceMessage::combinedText).reduce("", (a, b) -> a + " " + b).toLowerCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();

        double sourceSupport = sourceText.isBlank() ? 0.15 : overlapScore(combined, sourceText);
        double hallucinationRisk = clamp(1.0 - sourceSupport + unsupportedClaimPenalty(combined));
        double commercialValue = keywordScore(combined, List.of("price", "pricing", "cost", "paid", "market", "customer", "revenue", "tool", "api", "workflow", "automation", "security", "agent", "model"));
        double actionability = keywordScore(combined, List.of("how", "step", "fix", "use", "setup", "configure", "deploy", "run", "compare", "choose", "пример", "как", "ошибка"));
        double specificity = clamp((title.length() >= 18 ? 0.25 : 0) + Math.min(0.45, body.length() / 900.0) + Math.min(0.30, countNumbersOrCode(body) / 8.0));
        double duplicateRisk = duplicateRisk(item, allItems);
        boolean artifactTypeCorrect = artifactTypeCorrect(item.artifactType(), combined);
        double titleQuality = clamp((title.length() >= 12 ? 0.45 : 0.15) + (title.length() <= 120 ? 0.35 : 0.05) + (genericTitle(title) ? 0 : 0.2));
        double bodyQuality = clamp((body.length() >= 160 ? 0.35 : 0.1) + (sentenceCount(body) >= 2 ? 0.25 : 0) + (body.length() <= 6000 ? 0.2 : 0.05) + (sourceSupport * 0.2));
        double publishability = clamp(sourceSupport * 0.28 + (1 - hallucinationRisk) * 0.17 + commercialValue * 0.16 + actionability * 0.12 + specificity * 0.12 + titleQuality * 0.08 + bodyQuality * 0.07 - duplicateRisk * 0.18);

        if (sources.isEmpty()) reasons.add("No source messages linked");
        if (sourceSupport < 0.35) reasons.add("Weak lexical overlap with source messages");
        if (duplicateRisk >= 0.72) reasons.add("Likely duplicate of another knowledge item in selected runs");
        if (!artifactTypeCorrect) reasons.add("Artifact type does not match content signals");
        if (commercialValue < 0.35) reasons.add("Low commercial/product value signals");
        if (actionability < 0.30) reasons.add("Low actionability");
        if (titleQuality < 0.45) reasons.add("Title is generic or too short/long");
        if (bodyQuality < 0.45) reasons.add("Body needs clearer detail or structure");
        if (containsUnsafe(combined)) reasons.add("Unsafe or policy-risk content signal");

        Scores scores = new Scores(round(sourceSupport), round(hallucinationRisk), round(publishability), round(commercialValue), round(actionability), round(specificity), round(duplicateRisk), artifactTypeCorrect, round(titleQuality), round(bodyQuality));
        if (reasons.isEmpty()) reasons.add("Good source support, useful detail, and publishable structure");
        return new ReviewDecision(verdict(scores, combined), scores, reasons.stream().distinct().toList());
    }

    private String verdict(Scores s, String combined) {
        if (containsUnsafe(combined)) return "UNSAFE";
        if (!s.artifactTypeCorrect()) return "WRONG_ARTIFACT_TYPE";
        if (s.duplicateRiskScore() >= 0.72) return "DUPLICATE";
        if (s.sourceSupportedScore() < 0.32 || s.hallucinationRiskScore() >= 0.72) return "BAD_SOURCE_SUPPORT";
        if (s.commercialValueScore() < 0.30 && s.actionabilityScore() < 0.30) return "LOW_VALUE";
        if (s.publishabilityScore() >= 0.62 && s.bodyQualityScore() >= 0.50 && s.titleQualityScore() >= 0.50) return "PUBLISHABLE";
        return "NEEDS_EDIT";
    }

    private void upsertReview(KnowledgeItem item, ReviewDecision decision) {
        jdbc.update("""
                INSERT INTO knowledge_item_reviews (
                    knowledge_item_id,
                    run_id,
                    reviewer_type,
                    source_supported_score,
                    hallucination_risk_score,
                    publishability_score,
                    commercial_value_score,
                    actionability_score,
                    specificity_score,
                    duplicate_risk_score,
                    artifact_type_correct,
                    title_quality_score,
                    body_quality_score,
                    needs_human_review,
                    verdict,
                    review_reasons_json,
                    updated_at
                ) VALUES (?, ?, 'AUTO', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                ON CONFLICT (knowledge_item_id, reviewer_type) DO UPDATE SET
                    run_id = EXCLUDED.run_id,
                    source_supported_score = EXCLUDED.source_supported_score,
                    hallucination_risk_score = EXCLUDED.hallucination_risk_score,
                    publishability_score = EXCLUDED.publishability_score,
                    commercial_value_score = EXCLUDED.commercial_value_score,
                    actionability_score = EXCLUDED.actionability_score,
                    specificity_score = EXCLUDED.specificity_score,
                    duplicate_risk_score = EXCLUDED.duplicate_risk_score,
                    artifact_type_correct = EXCLUDED.artifact_type_correct,
                    title_quality_score = EXCLUDED.title_quality_score,
                    body_quality_score = EXCLUDED.body_quality_score,
                    needs_human_review = EXCLUDED.needs_human_review,
                    verdict = EXCLUDED.verdict,
                    review_reasons_json = EXCLUDED.review_reasons_json,
                    updated_at = now()
                """,
                item.id(),
                item.runId(),
                decision.scores().sourceSupportedScore(),
                decision.scores().hallucinationRiskScore(),
                decision.scores().publishabilityScore(),
                decision.scores().commercialValueScore(),
                decision.scores().actionabilityScore(),
                decision.scores().specificityScore(),
                decision.scores().duplicateRiskScore(),
                decision.scores().artifactTypeCorrect(),
                decision.scores().titleQualityScore(),
                decision.scores().bodyQualityScore(),
                !"PUBLISHABLE".equals(decision.verdict()),
                decision.verdict(),
                json(decision.reasons())
        );
    }

    private long upsertLabelingItem(KnowledgeItem item, ReviewDecision decision) {
        Long existing = jdbc.query("""
                SELECT id FROM labeling_items
                WHERE run_id = ? AND knowledge_item_id = ? AND item_type = 'KNOWLEDGE_ITEM_REVIEW'
                ORDER BY id LIMIT 1
                """, rs -> rs.next() ? rs.getLong("id") : null, item.runId(), item.id());
        String text = item.title() + "\n\n" + itemText(item);
        String context = json(Map.of("reviewScores", decision.scores(), "reviewReasons", decision.reasons()));
        if (existing != null) {
            jdbc.update("""
                    UPDATE labeling_items SET text_snapshot = ?, context_snapshot_json = ?::jsonb, suggested_label = ?, suggested_labels_json = ?::jsonb,
                        suggested_artifact_type = ?, suggested_decision = ?, confidence = ?, priority = ?, status = ?, updated_at = now()
                    WHERE id = ?
                    """, text, context, decision.verdict(), json(decision.reasons()), item.artifactType(), decision.verdict(), decision.scores().publishabilityScore(), priority(decision), status(decision), existing);
            return existing;
        }
        return jdbc.queryForObject("""
                INSERT INTO labeling_items (run_id, knowledge_item_id, item_type, text_snapshot, context_snapshot_json, suggested_label, suggested_labels_json, suggested_artifact_type, suggested_decision, confidence, priority, status)
                VALUES (?, ?, 'KNOWLEDGE_ITEM_REVIEW', ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, item.runId(), item.id(), text, context, decision.verdict(), json(decision.reasons()), item.artifactType(), decision.verdict(), decision.scores().publishabilityScore(), priority(decision), status(decision));
    }

    private void upsertLabelingEvent(long labelingItemId, KnowledgeItem item, ReviewDecision decision, String eventType, String userId, String marker) {
        int existing = jdbc.queryForObject("""
                SELECT count(*) FROM labeling_events
                WHERE labeling_item_id = ? AND event_type = ? AND user_id = ? AND comment = ?
                """, Integer.class, labelingItemId, eventType, userId, marker);
        String newValue = json(Map.of("verdict", decision.verdict(), "scores", decision.scores(), "reasons", decision.reasons()));
        if (existing > 0) {
            jdbc.update("""
                    UPDATE labeling_events SET new_value_json = ?::jsonb
                    WHERE id = (SELECT id FROM labeling_events WHERE labeling_item_id = ? AND event_type = ? AND user_id = ? AND comment = ? ORDER BY id LIMIT 1)
                    """, newValue, labelingItemId, eventType, userId, marker);
            return;
        }
        jdbc.update("""
                INSERT INTO labeling_events (labeling_item_id, run_id, user_id, event_type, old_value_json, new_value_json, comment)
                VALUES (?, ?, ?, ?, '{}'::jsonb, ?::jsonb, ?)
                """, labelingItemId, item.runId(), userId, eventType, newValue, marker);
    }

    private void upsertTrainingExample(KnowledgeItem item, ReviewDecision decision) {
        Long existing = jdbc.query("""
                SELECT id FROM training_examples
                WHERE run_id = ? AND source = 'KNOWLEDGE_ITEM_REVIEW_AUTO' AND context_json->>'knowledgeItemId' = ?
                ORDER BY id LIMIT 1
                """, rs -> rs.next() ? rs.getLong("id") : null, item.runId(), String.valueOf(item.id()));
        String text = item.title() + "\n\n" + itemText(item);
        String context = json(Map.of("knowledgeItemId", item.id(), "runId", item.runId(), "scores", decision.scores(), "reasons", decision.reasons()));
        if (existing != null) {
            jdbc.update("""
                    UPDATE training_examples SET text = ?, context_json = ?::jsonb, label = ?, artifact_type = ?, decision = ?, confidence = ?
                    WHERE id = ?
                    """, text, context, decision.verdict(), item.artifactType(), decision.verdict(), decision.scores().publishabilityScore(), existing);
            return;
        }
        jdbc.update("""
                INSERT INTO training_examples (source, text, context_json, label, artifact_type, decision, confidence, split, run_id)
                VALUES ('KNOWLEDGE_ITEM_REVIEW_AUTO', ?, ?::jsonb, ?, ?, ?, ?, 'UNASSIGNED', ?)
                """, text, context, decision.verdict(), item.artifactType(), decision.verdict(), decision.scores().publishabilityScore(), item.runId());
    }

    private ReviewDetail toDetail(KnowledgeItem item, ReviewDecision decision, List<SourceMessage> sources) {
        return new ReviewDetail(item.id(), item.runId(), item.title(), item.artifactType(), item.summary(), itemText(item), decision.verdict(), decision.scores(), decision.reasons(), sources);
    }

    private void writeReport(List<Long> runIds, List<ReviewDetail> details, Map<String, Long> breakdown, long eventsCreated, long examplesCreated) {
        ObjectNode root = mapper.createObjectNode();
        root.putPOJO("runIds", runIds);
        root.put("itemsReviewed", details.size());
        root.putPOJO("verdictBreakdown", breakdown);
        root.put("labelingEventsCreated", eventsCreated);
        root.put("trainingExamplesCreated", examplesCreated);
        root.putPOJO("bestItems", best(details));
        root.putPOJO("worstItems", worst(details));
        root.putPOJO("sourceSupportProblems", sourceProblems(details));
        root.putPOJO("hallucinationProblems", hallucinationProblems(details));
        root.putPOJO("duplicateProblems", duplicateProblems(details));
        root.putPOJO("items", details);
        try {
            Files.createDirectories(REPORT_PATH.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), root);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write knowledge item review report", e);
        }
    }

    private Map<String, Long> verdictBreakdown(List<ReviewDetail> details) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (ReviewDetail detail : details) result.merge(detail.verdict(), 1L, Long::sum);
        return result;
    }

    private List<ItemSummary> best(List<ReviewDetail> details) {
        return details.stream().sorted(Comparator.comparingDouble((ReviewDetail d) -> d.scores().publishabilityScore()).reversed()).limit(5).map(this::summaryItem).toList();
    }

    private List<ItemSummary> worst(List<ReviewDetail> details) {
        return details.stream().sorted(Comparator.comparingDouble(d -> d.scores().publishabilityScore())).limit(5).map(this::summaryItem).toList();
    }

    private List<ItemSummary> sourceProblems(List<ReviewDetail> details) {
        return details.stream().filter(d -> d.scores().sourceSupportedScore() < 0.45).sorted(Comparator.comparingDouble(d -> d.scores().sourceSupportedScore())).map(this::summaryItem).toList();
    }

    private List<ItemSummary> hallucinationProblems(List<ReviewDetail> details) {
        return details.stream().filter(d -> d.scores().hallucinationRiskScore() >= 0.55).sorted(Comparator.comparingDouble((ReviewDetail d) -> d.scores().hallucinationRiskScore()).reversed()).map(this::summaryItem).toList();
    }

    private List<ItemSummary> duplicateProblems(List<ReviewDetail> details) {
        return details.stream().filter(d -> d.scores().duplicateRiskScore() >= 0.55).sorted(Comparator.comparingDouble((ReviewDetail d) -> d.scores().duplicateRiskScore()).reversed()).map(this::summaryItem).toList();
    }

    private ItemSummary summaryItem(ReviewDetail detail) {
        return new ItemSummary(detail.id(), detail.runId(), detail.title(), detail.verdict(), detail.scores().publishabilityScore(), detail.reasons().isEmpty() ? "-" : detail.reasons().get(0));
    }

    private String itemText(KnowledgeItem item) {
        StringBuilder text = new StringBuilder();
        if (item.summary() != null) text.append(item.summary()).append('\n');
        appendJsonText(text, item.bodyJson());
        appendJsonText(text, item.contentJson());
        return text.toString().trim();
    }

    private void appendJsonText(StringBuilder target, String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return;
        try {
            appendNodeText(target, mapper.readTree(json));
        } catch (JsonProcessingException e) {
            target.append(json).append('\n');
        }
    }

    private void appendNodeText(StringBuilder target, JsonNode node) {
        if (node == null || node.isNull()) return;
        if (node.isTextual() || node.isNumber() || node.isBoolean()) target.append(node.asText()).append('\n');
        else if (node.isObject() || node.isArray()) node.forEach(child -> appendNodeText(target, child));
    }

    private double overlapScore(String itemText, String sourceText) {
        Set<String> itemTokens = tokens(itemText);
        Set<String> sourceTokens = tokens(sourceText);
        if (itemTokens.isEmpty() || sourceTokens.isEmpty()) return 0.0;
        long common = itemTokens.stream().filter(sourceTokens::contains).count();
        return clamp((double) common / Math.max(8, itemTokens.size()) + Math.min(0.25, (double) sourceTokens.size() / 250));
    }

    private Set<String> tokens(String value) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : value.toLowerCase(Locale.ROOT).split("[^A-Za-zА-Яа-я0-9_+-]+")) {
            if (token.length() >= 4) result.add(token);
        }
        return result;
    }

    private double duplicateRisk(KnowledgeItem item, List<KnowledgeItem> allItems) {
        Set<String> tokens = tokens(item.title() + " " + itemText(item));
        double max = 0.0;
        for (KnowledgeItem other : allItems) {
            if (other.id() == item.id()) continue;
            Set<String> otherTokens = tokens(other.title() + " " + itemText(other));
            if (tokens.isEmpty() || otherTokens.isEmpty()) continue;
            Set<String> intersection = new LinkedHashSet<>(tokens);
            intersection.retainAll(otherTokens);
            Set<String> union = new LinkedHashSet<>(tokens);
            union.addAll(otherTokens);
            max = Math.max(max, (double) intersection.size() / union.size());
        }
        return clamp(max);
    }

    private double keywordScore(String text, List<String> keywords) {
        long hits = keywords.stream().filter(text::contains).count();
        return clamp(0.20 + hits * 0.16);
    }

    private double unsupportedClaimPenalty(String text) {
        return keywordScore(text, List.of("guaranteed", "revolutionary", "always", "never", "100%", "best ever", "безусловно")) * 0.25;
    }

    private boolean artifactTypeCorrect(String artifactType, String text) {
        String type = valueOr(artifactType, "NOTE").toUpperCase(Locale.ROOT);
        if (type.contains("NONE")) return text.length() < 80;
        if (type.contains("CODE") || type.contains("SNIPPET")) return text.contains("http") || text.contains("{") || text.contains("api") || text.contains("config") || text.contains("code");
        return text.length() >= 60;
    }

    private boolean genericTitle(String title) {
        String lower = title.toLowerCase(Locale.ROOT).trim();
        return lower.length() < 10 || Set.of("note", "summary", "item", "insight", "update", "новость", "заметка").contains(lower);
    }

    private int countNumbersOrCode(String body) {
        int count = 0;
        for (String token : body.split("\\s+")) if (token.matches(".*[0-9/{}=_-].*")) count++;
        return count;
    }

    private int sentenceCount(String body) {
        return Math.max(0, body.split("[.!?\\n]+").length);
    }

    private boolean containsUnsafe(String text) {
        return List.of("malware", "credential theft", "phishing", "exploit", "bypass payment", "steal token").stream().anyMatch(text::contains);
    }

    private int priority(ReviewDecision decision) {
        return switch (decision.verdict()) {
            case "BAD_SOURCE_SUPPORT", "UNSAFE", "DUPLICATE", "WRONG_ARTIFACT_TYPE" -> 90;
            case "NEEDS_EDIT" -> 70;
            case "LOW_VALUE" -> 55;
            default -> 35;
        };
    }

    private String status(ReviewDecision decision) {
        return "PUBLISHABLE".equals(decision.verdict()) ? "PENDING" : "NEEDS_REVIEW";
    }

    private Scores scoresFromJson(String json) {
        if (json == null || json.isBlank()) return new Scores(0, 1, 0, 0, 0, 0, 0, true, 0, 0);
        try {
            JsonNode node = mapper.readTree(json);
            return new Scores(node.path("sourceSupportedScore").asDouble(), node.path("hallucinationRiskScore").asDouble(), node.path("publishabilityScore").asDouble(), node.path("commercialValueScore").asDouble(), node.path("actionabilityScore").asDouble(), node.path("specificityScore").asDouble(), node.path("duplicateRiskScore").asDouble(), node.path("artifactTypeCorrect").asBoolean(true), node.path("titleQualityScore").asDouble(), node.path("bodyQualityScore").asDouble());
        } catch (JsonProcessingException e) {
            return new Scores(0, 1, 0, 0, 0, 0, 0, true, 0, 0);
        }
    }

    private List<String> stringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> result = new ArrayList<>();
            mapper.readTree(json).forEach(node -> result.add(node.asText()));
            return result;
        } catch (JsonProcessingException e) {
            return List.of(json);
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize review JSON", e);
        }
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private String reportPath() {
        return REPORT_PATH.toString().replace('\\', '/');
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Double nullableDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double round(double value) {
        return Math.round(clamp(value) * 10000.0) / 10000.0;
    }

    private record KnowledgeItem(long id, long runId, String title, String artifactType, String summary, String bodyJson, String contentJson, Double confidence) {}
    private record ReviewDecision(String verdict, Scores scores, List<String> reasons) {}

    public record ReviewRequest(List<Long> runIds) {}
    public record ReviewActionRequest(String action) {}
    public record ReviewActionResult(long knowledgeItemId, String action, String verdict, long labelingItemId) {}
    public record ReviewRunResult(List<Long> runIds, int itemsReviewed, Map<String, Long> verdictBreakdown, long labelingEventsCreated, long trainingExamplesCreated, String reportPath, List<ItemSummary> bestItems, List<ItemSummary> worstItems, List<ItemSummary> sourceSupportProblems, List<ItemSummary> hallucinationProblems, List<ItemSummary> duplicateProblems) {}
    public record ReviewSummary(List<Long> runIds, int itemsReviewed, Map<String, Long> verdictBreakdown, List<ItemSummary> bestItems, List<ItemSummary> worstItems, List<ReviewDetail> items) {}
    public record ItemSummary(long id, long runId, String title, String verdict, double publishabilityScore, String reason) {}
    public record ReviewDetail(long id, long runId, String title, String artifactType, String summary, String body, String verdict, Scores scores, List<String> reasons, List<SourceMessage> sources) {}
    public record Scores(double sourceSupportedScore, double hallucinationRiskScore, double publishabilityScore, double commercialValueScore, double actionabilityScore, double specificityScore, double duplicateRiskScore, boolean artifactTypeCorrect, double titleQualityScore, double bodyQualityScore) {}
    public record SourceMessage(long knowledgeItemId, long datasetMessageId, String sourceRole, String quote, Double confidence, String text, String caption, String rawJson, String chatTitle, OffsetDateTime messageDate) {
        String combinedText() {
            return String.join(" ", valueOr(text, ""), valueOr(caption, ""), valueOr(quote, ""));
        }
    }
}
