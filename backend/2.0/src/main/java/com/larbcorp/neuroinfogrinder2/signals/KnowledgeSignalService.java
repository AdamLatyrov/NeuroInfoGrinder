package com.larbcorp.neuroinfogrinder2.signals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.larbcorp.neuroinfogrinder2.replay.MessageUsefulnessResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class KnowledgeSignalService {
    private static final String[] COMMUNITY_WELCOME_MARKERS = {
            "добро пожаловать",
            "welcome",
            "помощник ом",
            "путеводитель по сообществу",
            "пройди онбординг",
            "пройдите онбординг",
            "база знаний",
            "список всех доступных чатов",
            "список доступных чатов",
            "контакты поддержки",
            "карта всего вышедшего контента",
            "карта контента",
            "для новичков",
            "правила сообщества",
            "подтвердите, что ознакомились",
            "ознакомились с правилами",
            "правила группы",
            "закрепленными сообщениями",
            "закреплёнными сообщениями"
    };

    // Non-material / noise patterns that must never become signals (ported from the v2
    // MaterialEligibilityGate + the 20k signal audit). These are by-design NOT useful signals:
    // controlled-run test artifacts, roleplay/system-prompt fiction, LLM safety refusals, and
    // bot-activation spam. They were leaking into the signals table and polluting the UI.
    private static final int U = Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE;
    private static final Pattern TEST_ARTIFACT = Pattern.compile(
            "\\b(?:NIGTEST|NIGTOP|ZAUR)[-\\s]*[A-Z]\\d+\\b|\\[NIGTEST[^\\]]*\\]|\\[NIGTOP[^\\]]*\\]", U);
    private static final Pattern ROLEPLAY_OR_SYSTEM_PROMPT = Pattern.compile(
            "ABSOLUTE COMMUNICATION PROTOCOL|системн\\w*\\s+промпт|\\bты\\s+—\\s+\\w+,\\s+интеллект|role\\s*-?play|представь\\s+что|игров\\w+\\s+сценар|приватк\\s+и\\s+жесткий\\s+донат|ROLEPLAY!|ты\\s+—\\s+Мира", U);
    private static final Pattern LLM_REFUSAL_OR_DISCLAIMER = Pattern.compile(
            "safeguards flagged this message|Fable 5's safeguards|я\\s+не\\s+могу\\s+(?:выполнить|предоставить|помочь|сгенерировать|подсказать|дать)|as\\s+an\\s+ai|i\\s+don'?t\\s+have\\s+access\\s+to\\s+your|я\\s+не\\s+имею\\s+доступа\\s+к\\s+вашем", U);
    private static final Pattern BOT_ACTIVATION_SPAM = Pattern.compile(
            "Please activate me in DM|activate me in dm|напиши мне в лс для актив|пиши в лс для актив|залетаем\\s+в\\s+лс", U);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public KnowledgeSignalService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void upsertRejectedSingleMessageSignal(RejectedSingleMessageSignal input) {
        if (input == null || input.datasetMessageId() == null || input.text() == null || input.text().isBlank()) return;
        if (!shouldStore(input)) return;

        SignalClassification classification = classify(input);
        Long signalId = existingSignalId(input);
        if (signalId == null) {
            signalId = insertSignal(input, classification);
        } else {
            updateSignal(signalId, input, classification);
        }

        if (signalId == null) return;
        jdbc.update("DELETE FROM knowledge_signal_topics WHERE signal_id = ?", signalId);
        for (TopicAssignment topic : classification.topics()) {
            jdbc.update("""
                    INSERT INTO knowledge_signal_topics (signal_id, topic_id, confidence, source, reason)
                    SELECT ?, id, ?, 'RULE', ?
                    FROM knowledge_topics
                    WHERE slug = ?
                    """, signalId, BigDecimal.valueOf(topic.confidence()), topic.reason(), topic.slug());
        }
    }

    private Long existingSignalId(RejectedSingleMessageSignal input) {
        List<Long> ids = jdbc.query("""
                SELECT id
                FROM knowledge_signals
                WHERE (dataset_message_id = ? AND replay_run_id = ?)
                   OR (?::bigint IS NOT NULL AND raw_message_id = ?)
                ORDER BY id
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), input.datasetMessageId(), input.runId(), input.rawMessageId(), input.rawMessageId());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long insertSignal(RejectedSingleMessageSignal input, SignalClassification classification) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO knowledge_signals (
                        raw_message_id, dataset_message_id, replay_run_id, replay_run_message_id,
                        source_chat_id, source_topic_id, title, summary, signal_type, status, readiness,
                        reason, risk_flags_json, evidence_json, confidence, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_MATERIAL_READY', ?, ?::jsonb, ?::jsonb, ?, now())
                    """, new String[]{"id"});
            Object[] values = signalValues(input, classification);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("Insert did not return generated signal id");
        return key.longValue();
    }

    private void updateSignal(Long signalId, RejectedSingleMessageSignal input, SignalClassification classification) {
        Object[] values = signalValues(input, classification);
        Object[] args = new Object[values.length + 1];
        System.arraycopy(values, 0, args, 0, values.length);
        args[values.length] = signalId;
        jdbc.update("""
                UPDATE knowledge_signals
                SET raw_message_id = COALESCE(raw_message_id, ?),
                    dataset_message_id = ?,
                    replay_run_id = ?,
                    replay_run_message_id = ?,
                    source_chat_id = ?,
                    source_topic_id = ?,
                    title = ?,
                    summary = ?,
                    signal_type = ?,
                    status = ?,
                    readiness = 'NOT_MATERIAL_READY',
                    reason = ?,
                    risk_flags_json = ?::jsonb,
                    evidence_json = ?::jsonb,
                    confidence = ?,
                    updated_at = now()
                WHERE id = ?
                """, args);
    }

    private Object[] signalValues(RejectedSingleMessageSignal input, SignalClassification classification) {
        return new Object[]{
                input.rawMessageId(), input.datasetMessageId(), input.runId(), input.replayRunMessageId(),
                input.sourceChatId(), input.sourceTopicId(), classification.title(), classification.summary(),
                classification.signalType(), classification.status(), classification.reason(), write(classification.riskFlags()),
                write(evidence(input, classification)), BigDecimal.valueOf(classification.confidence())
        };
    }

    public Map<String, Object> topics() {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT kt.id, kt.slug, kt.name, kt.description,
                       COALESCE(mi.material_count, 0) AS material_count,
                       COALESCE(si.signal_count, 0) AS signal_count,
                       COALESCE(si.review_count, 0) AS review_count,
                       COALESCE(si.risk_count, 0) AS risk_count
                FROM knowledge_topics kt
                LEFT JOIN (
                    SELECT topic_id, count(*) AS material_count
                    FROM knowledge_item_topics kit
                    JOIN knowledge_items ki ON ki.id = kit.knowledge_item_id
                    WHERE ki.deleted_at IS NULL
                    GROUP BY topic_id
                ) mi ON mi.topic_id = kt.id
                LEFT JOIN (
                    SELECT topic_id,
                           count(*) AS signal_count,
                           count(*) FILTER (WHERE ks.status IN ('NEEDS_LINK_ENRICHMENT','NEEDS_REVIEW')) AS review_count,
                           count(*) FILTER (WHERE ks.risk_flags_json <> '[]'::jsonb) AS risk_count
                    FROM knowledge_signal_topics kst
                    JOIN knowledge_signals ks ON ks.id = kst.signal_id
                    GROUP BY topic_id
                ) si ON si.topic_id = kt.id
                WHERE kt.status = 'ACTIVE'
                ORDER BY kt.sort_order, kt.name
                """, (rs, rowNum) -> topicRow(rs));
        return Map.of("content", rows, "totalElements", rows.size());
    }

    public Map<String, Object> topic(String slug, String tab, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Map<String, Object> topic = jdbc.query("""
                SELECT id, slug, name, description
                FROM knowledge_topics
                WHERE slug = ? AND status = 'ACTIVE'
                """, rs -> {
            if (!rs.next()) throw new IllegalArgumentException("Topic not found: " + slug);
            return Map.of(
                    "id", rs.getLong("id"),
                    "slug", rs.getString("slug"),
                    "name", rs.getString("name"),
                    "description", rs.getString("description") == null ? "" : rs.getString("description")
            );
        }, slug);
        String safeTab = tab == null || tab.isBlank() ? "all" : tab.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> signals = safeTab.equals("materials") ? List.of() : topicSignals(slug, safeTab, safeSize, safePage * safeSize);
        return Map.of("topic", topic, "signals", signals, "number", safePage, "size", safeSize, "tab", safeTab);
    }

    public Map<String, Object> signal(long id) {
        return jdbc.query("""
                SELECT ks.id, ks.dataset_message_id, ks.replay_run_id, ks.replay_run_message_id,
                       COALESCE(ks.raw_message_id, rm_direct.id, rm_match.id) AS resolved_raw_message_id,
                       ks.title, ks.summary, ks.signal_type, ks.status, ks.readiness, ks.reason,
                       ks.risk_flags_json, ks.evidence_json, ks.confidence, ks.created_at, ks.updated_at,
                       COALESCE(rm_direct.chat_title, rm_match.chat_title, dm.chat_title) AS resolved_chat_title,
                       COALESCE(rm_direct.sender_name, rm_match.sender_name, dm.sender_name) AS resolved_sender_name,
                       COALESCE(rm_direct.sender_username, rm_match.sender_username, dm.sender_username) AS resolved_sender_username,
                       COALESCE(rm_direct.message_date, rm_match.message_date, dm.message_date) AS resolved_message_date,
                       COALESCE(rm_direct.telegram_chat_id, rm_match.telegram_chat_id, dm.telegram_chat_id) AS resolved_telegram_chat_id,
                       COALESCE(rm_direct.telegram_message_id, rm_match.telegram_message_id, dm.telegram_message_id) AS resolved_telegram_message_id,
                       COALESCE(NULLIF(dm.text, ''), NULLIF(dm.caption, ''), NULLIF(rm_direct.text, ''), NULLIF(rm_direct.caption, ''), NULLIF(rm_match.text, ''), NULLIF(rm_match.caption, '')) AS source_text
                FROM knowledge_signals ks
                LEFT JOIN dataset_messages dm ON dm.id = ks.dataset_message_id
                LEFT JOIN raw_messages rm_direct ON rm_direct.id = ks.raw_message_id
                LEFT JOIN raw_messages rm_match ON rm_match.account_id = dm.account_id
                    AND rm_match.telegram_chat_id = dm.telegram_chat_id
                    AND rm_match.telegram_message_id = dm.telegram_message_id
                WHERE ks.id = ?
                """, rs -> {
            if (!rs.next()) throw new IllegalArgumentException("Signal not found: " + id);
            Map<String, Object> row = signalRow(rs);
            row.put("topics", jdbc.query("""
                    SELECT kt.slug, kt.name, kst.confidence, kst.source, kst.reason
                    FROM knowledge_signal_topics kst
                    JOIN knowledge_topics kt ON kt.id = kst.topic_id
                    WHERE kst.signal_id = ?
                    ORDER BY kst.confidence DESC, kt.sort_order
                    """, (topicRs, rowNum) -> Map.of(
                    "slug", topicRs.getString("slug"),
                    "name", topicRs.getString("name"),
                    "confidence", topicRs.getBigDecimal("confidence"),
                    "source", topicRs.getString("source"),
                    "reason", topicRs.getString("reason") == null ? "" : topicRs.getString("reason")
            ), id));
            return row;
        }, id);
    }

    private boolean shouldStore(RejectedSingleMessageSignal input) {
        String reason = input.rejectionReason() == null ? "" : input.rejectionReason();
        String text = input.text() == null ? "" : input.text();
        // Non-material noise: never persist as a signal (ported from v2 gate + 20k audit).
        if (isNonMaterialNoise(text)) return false;
        if (isCommunityWelcomeMessage(text)) return false;
        if ("PROMO_ALONE".equals(reason)) return false;
        if (Set.of("NEEDS_LINK_ENRICHMENT", "ABUSE_OR_FRAUD", "RISK_SENSITIVE_MANUAL_ONLY", "RISK_SENSITIVE_MANUAL_REVIEW", "ENTITY_ONLY").contains(reason)) return true;
        MessageUsefulnessResult usefulness = input.usefulness();
        if (usefulness == null) return false;
        return !"PROMO_ALONE".equals(usefulness.contentClass())
                && !"PROMO_ALONE".equals(usefulness.rejectReason())
                && !"LOW_VALUE".equals(usefulness.contentClass())
                && !"LOW_VALUE".equals(usefulness.rejectReason());
    }

    private SignalClassification classify(RejectedSingleMessageSignal input) {
        String text = input.text();
        String lower = text.toLowerCase(Locale.ROOT);
        ArrayNode risks = json.createArrayNode();
        Set<TopicAssignment> topics = new LinkedHashSet<>();
        JsonNode links = input.features() == null ? null : input.features().path("links");
        int hiddenLinks = input.features() == null ? 0 : input.features().path("hiddenLinkCount").asInt(0);
        if (hiddenLinks > 0) risks.add("HIDDEN_LINK");
        if (containsAny(lower, "ref=", "referral", "реферал", "register?", "invite=")) risks.add("REFERRAL_LIKE_LINK");
        if (containsAny(lower, "loophole", "bypass", "накрут", "ботов", "фарм") || riskyBypassMention(lower)) risks.add("ABUSE_OR_ACCESS_RISK");

        TopicAssignment primary = primaryTopic(lower, links, risks);
        if (primary != null) topics.add(primary);
        if (risks.size() > 0 && (primary == null || !"abuse-risk".equals(primary.slug()))) topics.add(new TopicAssignment("abuse-risk", 0.70, "risk flag present"));
        if (topics.isEmpty() && links != null && links.size() > 0) topics.add(new TopicAssignment("providers-routers", 0.50, "unclassified link signal"));

        String status = input.rejectionReason() == null || input.rejectionReason().isBlank() ? "NEEDS_REVIEW" : input.rejectionReason();
        String signalType = signalType(input, risks);
        return new SignalClassification(
                title(text),
                input.usefulness() == null ? "Полезный сигнал без готового материала." : input.usefulness().humanReason(),
                signalType,
                status,
                reason(input, risks),
                risks,
                List.copyOf(topics),
                input.usefulness() == null ? 0.50 : Math.max(0.50, input.usefulness().overallScore())
        );
    }

    private TopicAssignment primaryTopic(String lower, JsonNode links, JsonNode risks) {
        if (containsAny(lower, "privacy", "приват", "лог", "логи", "leak", "утеч", "secret", "password", "парол", "credential", "ключ утек", "token leak", "безопас")) {
            return new TopicAssignment("security-privacy", 0.86, "security/privacy signal");
        }
        if (containsAny(lower, "outage", "сбой", "не работает", "degraded", "429", "timeout", "исчерпан", "упал", "недоступ", "rate limit", "лимит", "quota exhausted")) {
            return new TopicAssignment("outages-limits", 0.84, "outage/limit/fallback signal");
        }
        if (containsAny(lower, "github", "gitlab", "repo", "repository", "open-source", "opensource", "library", "framework", "sdk repo", "npm package", "pypi", "docker image")) {
            return new TopicAssignment("tools-repos", 0.82, "resource/repository signal");
        }
        if (containsAny(lower, "prompt", "промпт", "agent", "агент", "cursor", "claude code", "codex", "workflow", "orchestration", "шаблон промп", "system prompt")) {
            return new TopicAssignment("agents-prompts", 0.80, "agent/prompt workflow signal");
        }
        if (containsAny(lower, "free-tier", "free tier", "бесплат", "бонус", "free", "daily quota", "квот", "quota", "миллион токен", "токенов в сутки")) {
            return new TopicAssignment("free-tokens-quotas", 0.88, "free-tier/quota/token signal");
        }
        if (containsAny(lower, "provider", "router", "роутер", "openrouter", "modelhub", "litellm", "gateway", "model routing", "маршрутизац", "fallback endpoint")) {
            return new TopicAssignment("providers-routers", 0.74, "provider/router signal");
        }
        if (containsAny(lower, "api", "endpoint", "/v1", "openai-compatible", "sdk", "auth", "bearer", "ключ", "webhook", "rest", "graphql")) {
            return new TopicAssignment("api-integrations", 0.78, "API/config signal");
        }
        if (containsAny(lower, "gpt", "claude", "gemini", "qwen", "deepseek", "llama", "mistral", "benchmark", "context window", "release", "релиз", "новая модель")) {
            return new TopicAssignment("models-releases", 0.76, "model/vendor signal");
        }
        if (containsAny(lower, "pricing", "billing", "стоим", "цена", "тариф", "cost", "cache cost", "token spend", "оплата", "счёт", "invoice")) {
            return new TopicAssignment("pricing-costs", 0.72, "pricing/cost signal");
        }
        if (risks.size() > 0) return new TopicAssignment("abuse-risk", 0.70, "risk flag present");
        return null;
    }

    private String signalType(RejectedSingleMessageSignal input, JsonNode risks) {
        String contentClass = input.usefulness() == null ? "UNKNOWN" : input.usefulness().contentClass();
        if ("LINK_ONLY".equals(contentClass) && input.text().toLowerCase(Locale.ROOT).contains("токен")) return "PROVIDER_FREE_TIER_CLAIM";
        if (risks.size() > 0) return "RISK_OR_REFERRAL_SIGNAL";
        return contentClass;
    }

    private String reason(RejectedSingleMessageSignal input, JsonNode risks) {
        if (risks.size() > 0) return "Сообщение сохранено как полезный сигнал, но не готово к материалу из-за link/risk evidence.";
        return "Сообщение сохранено как полезный сигнал, но pipeline не счёл его готовым материалом.";
    }

    private ObjectNode evidence(RejectedSingleMessageSignal input, SignalClassification classification) {
        ObjectNode node = json.createObjectNode();
        node.put("pipelineRejectionReason", input.rejectionReason());
        node.put("materialReadiness", "NOT_MATERIAL_READY");
        if (input.features() != null) node.set("features", input.features());
        if (input.usefulness() != null) {
            node.put("usefulnessClass", input.usefulness().contentClass());
            node.put("sourceContextClass", input.usefulness().sourceContextClass());
            node.put("humanReason", input.usefulness().humanReason());
        }
        ArrayNode topics = node.putArray("assignedTopics");
        classification.topics().forEach(topic -> topics.add(topic.slug()));
        return node;
    }

    private List<Map<String, Object>> topicSignals(String slug, String tab, int limit, int offset) {
        String statusFilter = switch (tab) {
            case "review" -> " AND ks.status IN ('NEEDS_LINK_ENRICHMENT','NEEDS_REVIEW') ";
            case "risk" -> " AND ks.risk_flags_json <> '[]'::jsonb ";
            case "signals", "all" -> "";
            default -> "";
        };
        return jdbc.query("""
                SELECT ks.id, ks.dataset_message_id, ks.replay_run_id, ks.replay_run_message_id,
                       COALESCE(ks.raw_message_id, rm_direct.id, rm_match.id) AS resolved_raw_message_id,
                       ks.title, ks.summary, ks.signal_type, ks.status, ks.readiness, ks.reason,
                       ks.risk_flags_json, ks.evidence_json, ks.confidence, ks.created_at, ks.updated_at,
                       COALESCE(rm_direct.chat_title, rm_match.chat_title, dm.chat_title) AS resolved_chat_title,
                       COALESCE(rm_direct.sender_name, rm_match.sender_name, dm.sender_name) AS resolved_sender_name,
                       COALESCE(rm_direct.sender_username, rm_match.sender_username, dm.sender_username) AS resolved_sender_username,
                       COALESCE(rm_direct.message_date, rm_match.message_date, dm.message_date) AS resolved_message_date,
                       COALESCE(rm_direct.telegram_chat_id, rm_match.telegram_chat_id, dm.telegram_chat_id) AS resolved_telegram_chat_id,
                       COALESCE(rm_direct.telegram_message_id, rm_match.telegram_message_id, dm.telegram_message_id) AS resolved_telegram_message_id,
                       COALESCE(NULLIF(dm.text, ''), NULLIF(dm.caption, ''), NULLIF(rm_direct.text, ''), NULLIF(rm_direct.caption, ''), NULLIF(rm_match.text, ''), NULLIF(rm_match.caption, '')) AS source_text
                FROM knowledge_signals ks
                JOIN knowledge_signal_topics kst ON kst.signal_id = ks.id
                JOIN knowledge_topics kt ON kt.id = kst.topic_id
                LEFT JOIN dataset_messages dm ON dm.id = ks.dataset_message_id
                LEFT JOIN raw_messages rm_direct ON rm_direct.id = ks.raw_message_id
                LEFT JOIN raw_messages rm_match ON rm_match.account_id = dm.account_id
                    AND rm_match.telegram_chat_id = dm.telegram_chat_id
                    AND rm_match.telegram_message_id = dm.telegram_message_id
                WHERE kt.slug = ?
                """ + statusFilter + """
                ORDER BY ks.updated_at DESC, ks.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> signalRow(rs), slug, limit, offset);
    }

    private Map<String, Object> topicRow(ResultSet rs) throws SQLException {
        return Map.of(
                "id", rs.getLong("id"),
                "slug", rs.getString("slug"),
                "name", rs.getString("name"),
                "description", rs.getString("description") == null ? "" : rs.getString("description"),
                "materialCount", rs.getLong("material_count"),
                "signalCount", rs.getLong("signal_count"),
                "reviewCount", rs.getLong("review_count"),
                "riskCount", rs.getLong("risk_count")
        );
    }

    private Map<String, Object> signalRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("rawId", nullableLong(rs, "resolved_raw_message_id"));
        row.put("datasetMessageId", nullableLong(rs, "dataset_message_id"));
        row.put("runId", nullableLong(rs, "replay_run_id"));
        row.put("title", rs.getString("title"));
        row.put("summary", rs.getString("summary"));
        row.put("signalType", rs.getString("signal_type"));
        row.put("status", rs.getString("status"));
        row.put("readiness", rs.getString("readiness"));
        row.put("reason", rs.getString("reason"));
        row.put("riskFlags", parse(rs.getString("risk_flags_json")));
        row.put("evidence", parse(rs.getString("evidence_json")));
        row.put("confidence", rs.getBigDecimal("confidence"));
        row.put("chatTitle", rs.getString("resolved_chat_title"));
        row.put("senderName", rs.getString("resolved_sender_name"));
        row.put("senderUsername", rs.getString("resolved_sender_username"));
        row.put("messageDate", iso(rs.getObject("resolved_message_date", OffsetDateTime.class)));
        row.put("sourceText", rs.getString("source_text"));
        Long chatId = nullableLong(rs, "resolved_telegram_chat_id");
        Long messageId = nullableLong(rs, "resolved_telegram_message_id");
        Long rawId = nullableLong(rs, "resolved_raw_message_id");
        row.put("appMessageUrl", chatId == null || rawId == null ? null : "/groups?chatId=" + chatId + "&message=" + rawId + "&rawId=" + rawId);
        row.put("createdAt", iso(rs.getObject("created_at", OffsetDateTime.class)));
        row.put("updatedAt", iso(rs.getObject("updated_at", OffsetDateTime.class)));
        return row;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private boolean riskyBypassMention(String lower) {
        if (!lower.contains("обход")) return false;
        if (containsAny(lower, "не инструкция по обход", "не гайд по обход", "не превращать в гайд")) return false;
        return true;
    }

    private boolean isCommunityWelcomeMessage(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        int matches = 0;
        for (String marker : COMMUNITY_WELCOME_MARKERS) {
            if (lower.contains(marker)) {
                matches++;
            }
        }
        return matches >= 2
                || (lower.contains("подтвердите") && lower.contains("правила"))
                || lower.contains("ознакомились с правилами")
                || lower.contains("правила группы");
    }

    /** Non-material noise that must never become a signal: test artifacts, roleplay/system prompts, LLM refusals, bot-activation spam. */
    private boolean isNonMaterialNoise(String text) {
        if (text == null || text.isBlank()) return false;
        return TEST_ARTIFACT.matcher(text).find()
                || ROLEPLAY_OR_SYSTEM_PROMPT.matcher(text).find()
                || LLM_REFUSAL_OR_DISCLAIMER.matcher(text).find()
                || BOT_ACTIVATION_SPAM.matcher(text).find();
    }

    private String title(String text) {
        String value = text == null ? "Сигнал без текста" : text.replaceAll("(?i)https?://\\S+|www\\.\\S+", "").replaceAll("\\s+", " ").trim();
        if (value.isBlank()) return "Сигнал со ссылкой";
        return value.length() <= 90 ? value : value.substring(0, 87) + "...";
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }

    private String write(JsonNode node) {
        try {
            return json.writeValueAsString(node == null ? json.createObjectNode() : node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize JSON", e);
        }
    }

    private String iso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    public record RejectedSingleMessageSignal(
            Long rawMessageId,
            Long datasetMessageId,
            Long runId,
            Long replayRunMessageId,
            Long sourceChatId,
            Long sourceTopicId,
            String text,
            String rejectionReason,
            JsonNode features,
            MessageUsefulnessResult usefulness
    ) {}

    private record SignalClassification(String title, String summary, String signalType, String status, String reason,
                                        ArrayNode riskFlags, List<TopicAssignment> topics, double confidence) {}

    private record TopicAssignment(String slug, double confidence, String reason) {}
}
