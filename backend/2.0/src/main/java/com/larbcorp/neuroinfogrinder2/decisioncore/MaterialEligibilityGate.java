package com.larbcorp.neuroinfogrinder2.decisioncore;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Material Eligibility Gate — Java port of the offline Material Selection Lab v2
 * (scripts/material_lab_v2.py) routing matrix, technical-entity requirement,
 * non-material long-form filter, and evidence-sufficiency gate.
 *
 * <p>This is a <b>pure, side-effect-free</b> evaluator. It is wired into the replay
 * pipeline in <b>shadow mode only</b>: it computes a v2-style eligibility verdict
 * and a reason, which the shadow service logs to {@code semantic_decision_objects}.
 * It does NOT change materialization, generation, or routing behaviour — the legacy
 * {@code MessageUsefulnessClassifier} + score thresholds remain the authority.
 *
 * <p>The gate's added value over the legacy classifier:
 * <ul>
 *   <li>Non-material long-form filter (roleplay / fiction / system prompts / article digests /
 *       channel descriptions / contest announcements) — blocks these from becoming guide
 *       candidates even when they have numbered structure.</li>
 *   <li>Technical-entity requirement for single-message guides — a bare e-commerce/content
 *       domain (shopping list, marketing blog) cannot qualify a guide; a model/tool/api/code/
 *       dev-domain anchor is required.</li>
 *   <li>Promo / free-token / temp-email credit-farming hardening.</li>
 *   <li>Evidence sufficiency for clusters (size &gt;= 3, independent senders &gt;= 2, shared
 *       strong entity, no risk) instead of a pure score gate.</li>
 * </ul>
 *
 * <p>Regexes are kept faithful to the verified v2 lab (gold 34/34 messages, 15/15 clusters).
 */
public final class MaterialEligibilityGate {

    private MaterialEligibilityGate() {}

    /** v2 routes mirrored from the lab. */
    public static final String ROUTE_REJECT_SAFE = "REJECT_SAFE";
    public static final String ROUTE_MANUAL_REVIEW = "MANUAL_REVIEW";
    public static final String ROUTE_NEEDS_ENRICHMENT = "NEEDS_ENRICHMENT";
    public static final String ROUTE_AGGREGATE_ONLY = "AGGREGATE_ONLY";
    public static final String ROUTE_SIGNAL_ONLY = "SIGNAL_ONLY";
    public static final String ROUTE_REVIEW_HIGH_RECALL = "REVIEW_HIGH_RECALL";
    public static final String ROUTE_CONTEXT_ONLY = "CONTEXT_ONLY";

    /** v2 single-message eligibility tiers (eligible for LLM Judge). */
    public static final String TIER_SINGLE_MESSAGE_GUIDE_CANDIDATE = "SINGLE_MESSAGE_GUIDE_CANDIDATE";
    public static final String TIER_SINGLE_MESSAGE_REFERENCE_CANDIDATE = "SINGLE_MESSAGE_REFERENCE_CANDIDATE";
    public static final String TIER_EVIDENCE_GROUP_REVIEW = "EVIDENCE_GROUP_REVIEW";
    public static final String TIER_REVIEW_SINGLE_SIGNAL = "REVIEW_SINGLE_SIGNAL";

    private static final int UNICODE = Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE;

    // --- Hard-routing guards (expanded v2 versions) ---
    private static final Pattern RULES_ONBOARDING = Pattern.compile(
        "(добро\\s+пожаловать|приветствуем|ознакомьтесь\\s+с\\s+правилам|ознакомились\\s+с\\s+правилам|подтвердите.*правил|правила\\s+(сообщества|чата|группы)|перед\\s+тем\\s+как\\s+писать|путеводитель\\s+по\\s+сообществу|chatkeeper|lolsbot|помощник\\s+о[мн])", UNICODE);
    private static final Pattern TEST_ARTIFACT = Pattern.compile(
        "\\b(?:NIGTEST|NIGTOP|ZAUR)[-\\s]*[A-Z]\\d+\\b|\\[NIGTEST[^\\]]*\\]|\\[NIGTOP[^\\]]*\\]", UNICODE);
    private static final Pattern MODERATION_BOT = Pattern.compile(
        "\\b(lols\\s+ban|заблокировал|бан\\b|разбан|mute|мут\\b|предупреждени|кикнут)\\b", UNICODE);
    private static final Pattern RISK_PROMO_REFERRAL = Pattern.compile(
        "(ref=|ref_|start=ref|реферал\\w*|рефк\\w*|инвайт\\w*|партнерск\\w*|бесконечн\\w+\\s+подп|получи\\s+бесплатн\\w*|бесплатн\\w+\\s+токен\\w*|тестов\\w+\\s+токен\\w*|100\\s?\\$\\s+за\\s+регистрац\\w*|бесплатн\\w+\\s+100\\s?\\$|залетаем.*заявк\\w*|senpi|hyperliquid.*100\\s?\\$|airdrop|presale|пресейл|мемкоин|discord\\s+boost|no\\s+card|temp\\s*email|unlimited\\s+accounts|free\\s+access|бесплатн\\w+\\s+доступ\\b|без\\s+карты|халявн\\w+\\s+(токен|нейронк|api|подп|доступ|аккаунт)|бесплатн\\w+\\s+нейронк\\w*|бесконечн\\w+\\s+(нейронк|токен|api|подп|аккаунт)|до\\s+конца\\s+жизни|полтора\\s+доллар|доллар\\w*\\s+за\\s+(труд|регистр|подарк)|(?:получи|даю|начисля)\\s+\\d+\\s+кредит|получ\\w+\\s+\\d+\\s+кредит|\\d+\\s+кредитов\\s+за\\s+регистр|free\\s+credits?|бесплатн\\w+\\s+кредит)", UNICODE);
    private static final Pattern ABUSE_OR_FRAUD = Pattern.compile(
        "\\b(bin|cvv|carding|слив\\s+карт|обход\\s+лимит|фарм\\s+аккаунт|sms\\s*activation|free\\s*trial\\s*bypass|abuse\\s+access|exploit\\s+access|temp\\s*email|temp\\s*mail|temporam|10minutemail|guerrillamail|tempmail|временн\\w+\\s+(почт|email|mail)|одноразов\\w+\\s+(почт|email|mail)|no\\s+card|unlimited\\s+accounts)\\b", UNICODE);
    private static final Pattern JOB_POST = Pattern.compile(
        "\\b(ваканси\\w*|ищем\\w*|нанима\\w*|резюме|hr\\b|офер\\w*|зарплат\\w*|вилка\\s+\\d|#ваканси\\w*|#работ\\w*)\\b", UNICODE);
    private static final Pattern EVENT_ANNOUNCEMENT = Pattern.compile(
        "\\b(митап\\w*|вебинар\\w*|конференц\\w*|доклад\\w*|регистрация\\s+на|анонс\\s+встречи|мероприят\\w*)\\b", UNICODE);

    // --- Non-material long form (roleplay / fiction / system prompts / disclaimers / digests) ---
    private static final Pattern NON_MATERIAL_LONG = Pattern.compile(
        "(системн\\w*\\s+промпт|\\bты\\s+—\\s+\\w+|твоя\\s+идентичн|твоя\\s+задача|идентичность|игров\\w+\\s+сценар|roleplay|представь\\s+что|метод\\s+«|вот\\s+тебе\\s+(топ|три|3)|base64|jpeg\\s+image|file\\s+signatures|transcription\\s+of\\s+the|database\\s+schema|atomic\\s+numbers|химическ|осталось\\s+[\\d.,]+\\s+час|эфир\\s+№|запустили\\s+серию|пресс-релиз|you\\s+are\\s+a|as\\s+an\\s+ai|i\\s+don'?t\\s+have\\s+access\\s+to\\s+your|я\\s+не\\s+имею\\s+доступа\\s+к\\s+вашем|я\\s+не\\s+могу\\s+открыть|ключевые\\s+темы\\s+чата|короткий\\s+обзор|по\\s+(сохраненн|найденн)|обзор\\s+по\\s+(найденн|сохраненн)|о\\s+чем\\s+канал|инструкц\\w*\\s+для\\s+новоприбывш|объявляем\\s+конкурс|запустил\\s+сервис|анонс\\w*|дайджест|итоги\\s+розыгрыш|таймкоды\\s+со\\s+всеми|обзор\\s+сообщений|summary\\s+за|саммари\\s+дня|разбор\\s+твоего|обобщающ\\w+\\s+пост|мысли\\s+из\\s+стат|не\\s+дословно|обзор\\s+стат|мотивационн\\w+\\s+(пост|стат)|plane\\s+crashed|passengers?\\s+survived|struggling\\s+to\\s+survive|survival\\s+(scenario|situation|story|mode)|role\\s*-?play\\s+(scenario|story|game|mode)|how\\s+to\\s+make\\s+(guns|weapons|drugs)|survivors?\\s+come\\s+together|cut\\s+off\\s+from\\s+society|narrative\\s+scenario|краткий\\s+пересказ|если\\s+коротко|коротко:\\s|таймкоды:\\s*\\d|сожалею,\\s+что\\s+(?:текущ|работ|интеграц|возник)|я\\s+не\\s+могу\\s+(?:помочь|выполнить|предоставить|сгенерировать|подсказать|дать)|краткий\\s+обзор\\s+(?:того|чата)|пересказ\\s+(?:того|чата|сообщений))", UNICODE);

    // --- Claim / technical-problem / howto / guide-structure signals ---
    private static final Pattern CLAIM_INDICATOR = Pattern.compile(
        "\\b(бесплатн\\w*|доступен|доступн\\w*|free\\s*access|claim|выпустил|запустил|релиз\\w*|цена|стоимость|токеномик|пресейл|дропнул|drop|представил|анонс\\w*|тариф\\w*|прайс|подписк\\w*)\\b", UNICODE);
    private static final Pattern TECH_PROBLEM = Pattern.compile(
        "\\b(проблем\\w*|не\\s+работает|обрезает\\w*|слетел\\w*|сломал\\w*|ломается|ошибк\\w*|сталкивал\\w*|баг\\w*|отвалил\\w*|не\\s+коннект|реконнект\\w*|упал\\w*|зависает|тормозит|не\\s+отвечает|вшив\\w*|спайвар|подлог)\\b", UNICODE);
    private static final Pattern QUESTION = Pattern.compile(
        "\\?|\\b(почему|зачем|как\\s+понять|кто\\s+знает|кто-нибудь|сталкивал|можно\\s+ли)\\b", UNICODE);
    private static final Pattern HOWTO = Pattern.compile(
        "\\b(как\\s+(проверить|сделать|настроить|запустить|починить|перенести|подключить|использовать|получить|установить|развернуть|собрать|запустить)|инструкц\\w*|чеклист|пошагов|гайд\\b|tutorial|что\\s+нужно\\s+сделать|шаг\\s*\\d|step\\s*\\d)\\b", UNICODE);
    private static final Pattern GUIDE_STRUCTURE = Pattern.compile(
        "(###\\s*\\d|вариант\\w*|способы\\b|лучшие\\b|проверенн\\w*|надежн\\w*|1\\.\\s|2\\.\\s|3\\.\\s|→\\s|\\bизучи\\b|\\bопредели\\b|\\bпроверь\\b|\\bустанови\\b|\\bнастрой\\b|\\bзапусти\\b|\\bскачай\\b)", UNICODE);

    // --- Code presence (tightened: ambiguous words require code context) ---
    private static final Pattern CODE = Pattern.compile(
        "(```|\\b(?:curl|npm|pip|docker|kubectl|async|await|cargo|gradle|mvn|gcc)\\b|class\\s+\\w+\\s*[{:]|interface\\s+\\w+\\s*[{:]|def\\s+\\w+\\s*\\(|function\\s+\\w+\\s*\\(|public\\s+(?:class|static|interface|final|void|protected|override|readonly)|private\\s+(?:class|static|final|void|protected|readonly)|\\b(?:select|insert|update)\\s+\\w[^.\\n]{0,80}?(?:from|into|set)\\b)", UNICODE);

    // --- Strong entities (models / tools / apis) ---
    private static final Pattern STRONG_MODEL = Pattern.compile(
        "\\b(?:gpt[-\\s]?(?:4o|5|5\\.5)|claude\\s*code|codex|cursor|sonnet|opus|haiku|gemini|qwen|deepseek|llama|mistral|bge[-\\s]?m3|o3|o4|hermes|droid|zcode)\\b", UNICODE);
    private static final Pattern STRONG_TOOL = Pattern.compile(
        "\\b(?:claude\\s*code|codex|cursor|zcode|hermes|droid|r[-\\s]?api|vibemod|bynara|graph\\s*api|responses[-\\s]?api|messages[-\\s]?api|openai[-\\s]?compatible|codebase-memory-mcp|\\w+-mcp|\\bmcp\\b|cline|clinepass|vimit|vibecraft|omnirouter)\\b", UNICODE);
    private static final Pattern STRONG_API = Pattern.compile(
        "\\b(?:graph\\s*api|openai[-\\s]?compatible|responses[-\\s]?api|messages[-\\s]?api|webhook|oauth)\\b", UNICODE);
    private static final Pattern API_TERMS = Pattern.compile(
        "\\b(?:api|endpoint|sdk|json|oauth|token|rate\\s*limit|docker|postgres|redis|http|webhook|openai-compatible)\\b", UNICODE);
    private static final Pattern ERROR_CODE = Pattern.compile(
        "(?<![\\d.])(?:HTTP\\s*)?(?:400|401|403|404|408|409|422|429|500|502|503|504|522|524)(?![\\d.])", UNICODE);
    private static final Pattern ERROR_CONTEXT = Pattern.compile(
        "\\b(?:ошибк|error|status|упал|не\\s+работает|timeout|таймаут|лимит|rate\\s*limit|недоступ|forbidden|unauthorized|bad\\s+gateway|отвалил|не\\s+коннект|не\\s+подключ)\\b", UNICODE);
    private static final Pattern OFFICIAL_DOMAIN = Pattern.compile(
        "(?:openai\\.com|anthropic\\.com|google\\.com|ai\\.google|mistral\\.ai|github\\.com|docs\\.|documentation)", UNICODE);
    private static final Pattern DOMAIN = Pattern.compile(
        "(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:\\.[a-z]{2,})?", Pattern.CASE_INSENSITIVE);

    /** Technical domains that may anchor a single-message guide/reference. */
    private static final Set<String> TECHNICAL_DOMAINS = Set.of(
        "github.com", "gitlab.com", "stackoverflow.com", "npmjs.com", "pypi.org",
        "huggingface.co", "arxiv.org", "developer.mozilla.org", "kaggle.com",
        "docker.com", "docs.docker.com", "crates.io", "rubygems.org", "mvnrepository.com",
        "cve.org", "nvd.nist.gov", "kernel.org", "python.org");

    /** v2 eligibility verdict for a single message. */
    public record MessageVerdict(
        String route,
        String reason,
        boolean eligible,
        boolean technicalEntity,
        List<String> strongEntities,
        String tier
    ) {}

    /** v2 eligibility verdict for a cluster. */
    public record ClusterVerdict(
        boolean eligible,
        String tier,
        String reason,
        int independentSources,
        List<String> sharedStrongEntities
    ) {}

    /**
     * Evaluate a single message the v2 way, using the backend classifier's verdict as the base
     * and applying the v2-specific filters (technical-entity, non-material long form, risk/promo
     * hardening) on top. This is a shadow comparator — it does not replace the classifier.
     *
     * @param normalizedText        lowercase-normalized message text (may be empty)
     * @param rawText               original-cased text (used for code/domain/entity extraction)
     * @param linkCount             total link count (visible + hidden) for link-thin detection
     * @param classifierCandidateRoute backend {@code MessageUsefulnessClassifier} candidateRoute ("REJECT" / "SINGLE_MESSAGE" / null)
     * @param classifierRejectReason   backend reject reason (e.g. "LOW_VALUE", "ABUSE_OR_FRAUD", "NEEDS_LINK_ENRICHMENT", "UNVERIFIED_MODEL_CLAIM", null)
     * @param proposedMaterialType     backend proposed material type ("GUIDE" / "ANSWER" / "REFERENCE" / null)
     * @return v2 verdict with route, reason, eligibility, technical-entity flag, strong entities, tier
     */
    public static MessageVerdict evaluateMessage(String normalizedText, String rawText, int linkCount,
                                                 String classifierCandidateRoute, String classifierRejectReason,
                                                 String proposedMaterialType) {
        return evaluateMessage(normalizedText, rawText, linkCount, classifierCandidateRoute, classifierRejectReason, proposedMaterialType, null);
    }

    /**
     * Overload accepting extra URLs (e.g. hidden/caption {@code textEntityTypeTextUrl} links extracted
     * from {@code raw_json} by the caller). These are merged into entity/domain extraction so that a
     * technical anchor only present as a caption URL (e.g. a GitHub repo behind a link button) is seen
     * by the gate, matching the offline v2 lab which parses {@code raw_json} entities.
     *
     * @param extraUrls hidden/caption URLs from raw_json entities (null or empty if none)
     */
    public static MessageVerdict evaluateMessage(String normalizedText, String rawText, int linkCount,
                                                 String classifierCandidateRoute, String classifierRejectReason,
                                                 String proposedMaterialType, java.util.Collection<String> extraUrls) {
        String norm = normalizedText == null ? "" : normalizedText;
        String raw = rawText == null ? "" : rawText;
        // Build an entity-scan text that includes hidden/caption URLs so domains/strong entities
        // behind link buttons are detected (the offline lab parses raw_json for the same reason).
        StringBuilder scan = new StringBuilder(raw);
        if (extraUrls != null) {
            for (String u : extraUrls) {
                if (u != null && !u.isBlank()) scan.append('\n').append(u);
            }
        }
        String scanText = scan.toString();
        int tokens = Math.max(1, norm.length() / 4);
        List<String> strong = extractStrongEntities(scanText);
        boolean hasModel = find(STRONG_MODEL, scanText);
        boolean hasTool = find(STRONG_TOOL, scanText);
        // API_TERMS contains generic tokens like "http"/"token"/"json" that appear inside URL
        // syntax; match it against the raw text only (not hidden URLs) so a caption link does not
        // flip a non-technical news post into a technical candidate.
        boolean hasApiTerm = find(API_TERMS, raw);
        boolean hasApi = find(STRONG_API, scanText) || hasApiTerm;
        boolean hasCode = find(CODE, raw);
        boolean hasLink = linkCount > 0 || find(DOMAIN, scanText);
        boolean linkThin = hasLink && tokens <= 8;
        boolean hasStrongEntity = !strong.isEmpty();
        boolean hasGuideStruct = find(GUIDE_STRUCTURE, norm) || find(HOWTO, norm);
        boolean officialSource = OFFICIAL_DOMAIN.matcher(scanText).find();
        boolean isNonMaterial = find(NON_MATERIAL_LONG, norm);
        boolean riskRegex = find(ABUSE_OR_FRAUD, norm) || find(RISK_PROMO_REFERRAL, norm);
        boolean rulesLike = find(RULES_ONBOARDING, norm) || find(MODERATION_BOT, norm);
        boolean techEntity = hasTechnicalEntity(hasModel, hasTool, hasApi, hasCode, strong, scanText);
        boolean isModelPricingClaim = (hasModel && find(CLAIM_INDICATOR, norm)) || (find(claimPriceRe(), scanText) && hasModel);
        String route = classifierCandidateRoute == null ? "" : classifierCandidateRoute;
        String reason = classifierRejectReason == null ? "" : classifierRejectReason;
        boolean candidate = "SINGLE_MESSAGE".equals(classifierCandidateRoute);

        // --- v2 route matrix (overrides applied on top of classifier verdict) ---
        if (find(TEST_ARTIFACT, norm)) {
            route = ROUTE_REJECT_SAFE;
            reason = "test_artifact_controlled_run_message_never_material";
        } else if (riskRegex) {
            route = ROUTE_MANUAL_REVIEW;
            reason = "risk_referral_or_promo_never_auto_material";
        } else if (rulesLike && !techEntity) {
            route = ROUTE_REJECT_SAFE;
            reason = "hard_rule_never_material_rules_onboarding_or_moderation";
        } else if (isNonMaterial) {
            route = ROUTE_CONTEXT_ONLY;
            reason = "non_material_long_form_roleplay_or_system_prompt_or_disclaimer_or_fiction";
        } else if (find(JOB_POST, norm)) {
            route = ROUTE_AGGREGATE_ONLY;
            reason = "job_post_single_source_not_material";
        } else if (find(EVENT_ANNOUNCEMENT, norm)) {
            route = ROUTE_AGGREGATE_ONLY;
            reason = "event_announcement_single_source_not_material";
        } else if (candidate) {
            route = ROUTE_REVIEW_HIGH_RECALL;
            reason = "single_message_candidate_retained_for_v2_eligibility_review";
        } else if ("REJECT".equals(classifierCandidateRoute)) {
            // Map classifier reject reasons to v2 routes.
            route = mapRejectReason(classifierRejectReason);
            reason = classifierRejectReason == null ? "classifier_rejected" : classifierRejectReason;
        } else if (linkThin) {
            route = ROUTE_NEEDS_ENRICHMENT;
            reason = "link_thin_message_requires_enrichment_first";
        } else if (isModelPricingClaim && !officialSource) {
            route = ROUTE_SIGNAL_ONLY;
            reason = "unverified_model_pricing_or_provider_claim";
        } else if (hasGuideStruct || (hasStrongEntity && (find(TECH_PROBLEM, norm) || hasApi || hasCode || find(QUESTION, norm)))) {
            route = ROUTE_REVIEW_HIGH_RECALL;
            reason = "technical_or_resource_candidate_retained_for_context_evidence_review";
        } else {
            route = ROUTE_CONTEXT_ONLY;
            reason = "no_material_signal";
        }

        // --- v2 single-message eligibility tier (eligible for LLM Judge) ---
        String tier = TIER_REVIEW_SINGLE_SIGNAL;
        boolean eligible = false;
        if (ROUTE_REVIEW_HIGH_RECALL.equals(route)) {
            if (tokens >= 60 && hasGuideStruct && techEntity && !linkThin && !riskRegex && !isNonMaterial) {
                tier = TIER_SINGLE_MESSAGE_GUIDE_CANDIDATE;
                eligible = true;
            } else if (tokens >= 60 && techEntity && (hasCode || hasApi) && !linkThin && !riskRegex && !isNonMaterial) {
                tier = TIER_SINGLE_MESSAGE_REFERENCE_CANDIDATE;
                eligible = true;
            }
        }
        return new MessageVerdict(route, reason, eligible, techEntity, strong, tier);
    }

    private static String mapRejectReason(String rejectReason) {
        if (rejectReason == null) return ROUTE_CONTEXT_ONLY;
        return switch (rejectReason) {
            case "ABUSE_OR_FRAUD", "RISK_SENSITIVE_MANUAL_ONLY", "RISK_SENSITIVE_MANUAL_REVIEW", "ACCESS_CIRCUMVENTION", "PROMO_ALONE" -> ROUTE_MANUAL_REVIEW;
            case "NEEDS_LINK_ENRICHMENT" -> ROUTE_NEEDS_ENRICHMENT;
            case "UNVERIFIED_MODEL_CLAIM" -> ROUTE_SIGNAL_ONLY;
            default -> ROUTE_CONTEXT_ONLY;
        };
    }

    /**
     * Evaluate a cluster's evidence sufficiency the v2 way.
     *
     * @param size                 cluster size (candidate messages)
     * @param independentSources   distinct sender count
     * @param sharedStrongEntities shared strong entities across members
     * @param hasRisk              any member flagged HIGH/MEDIUM risk
     * @param allReviewHighRecall  all members routed REVIEW_HIGH_RECALL
     * @param shortAcks            count of &lt;=3-token ack messages
     * @return v2 cluster verdict; eligible only when EVIDENCE_GROUP_REVIEW gates pass
     */
    public static ClusterVerdict evaluateCluster(int size, int independentSources,
                                                 List<String> sharedStrongEntities, boolean hasRisk,
                                                 boolean allReviewHighRecall, int shortAcks) {
        boolean shared = sharedStrongEntities != null && !sharedStrongEntities.isEmpty();
        if (size >= 3 && independentSources >= 2 && shared && allReviewHighRecall && !hasRisk && shortAcks < size) {
            return new ClusterVerdict(true, TIER_EVIDENCE_GROUP_REVIEW,
                "multi_source_evidence_with_shared_strong_entity", independentSources, sharedStrongEntities == null ? List.of() : sharedStrongEntities);
        }
        String tier = shared ? "SIGNAL_GROUP" : "CONTEXT_GROUP";
        String reason = shared ? "signal_group_insufficient_sources_or_evidence" : "context_group_no_shared_strong_entity";
        return new ClusterVerdict(false, tier, reason, independentSources, sharedStrongEntities == null ? List.of() : sharedStrongEntities);
    }

    /** True if the message carries a technical anchor (not a bare e-commerce/content domain). */
    static boolean hasTechnicalEntity(boolean hasModel, boolean hasTool, boolean hasApi, boolean hasCode,
                                      List<String> strongEntities, String rawText) {
        if (hasModel || hasTool || hasApi || hasCode) return true;
        if (strongEntities != null) {
            for (String e : strongEntities) {
                if (TECHNICAL_DOMAINS.contains(e.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    private static List<String> extractStrongEntities(String rawText) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = DOMAIN.matcher(rawText);
        Set<String> seen = new java.util.HashSet<>();
        while (m.find()) {
            String d = rootDomain(m.group());
            if (d != null && seen.add(d)) out.add(d);
        }
        for (Pattern p : new Pattern[]{STRONG_MODEL, STRONG_TOOL, STRONG_API}) {
            m = p.matcher(rawText);
            while (m.find()) {
                String e = m.group().toLowerCase(Locale.ROOT).replace(" ", "");
                if (seen.add(e)) out.add(e);
            }
        }
        if (find(ERROR_CONTEXT, rawText)) {
            m = ERROR_CODE.matcher(rawText);
            while (m.find()) {
                String e = m.group().toUpperCase(Locale.ROOT);
                if (seen.add(e)) out.add(e);
            }
        }
        return out;
    }

    private static String rootDomain(String domain) {
        if (domain == null) return null;
        String d = domain.toLowerCase(Locale.ROOT);
        if (d.startsWith("www.")) d = d.substring(4);
        // strip path/query
        int cut = d.indexOf('/');
        if (cut > 0) d = d.substring(0, cut);
        // take last two labels
        String[] parts = d.split("\\.");
        if (parts.length < 2) return null;
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    private static boolean find(Pattern p, String text) {
        return text != null && !text.isEmpty() && p.matcher(text).find();
    }

    private static Pattern claimPriceRe() {
        return Pattern.compile("(?:[$€₽]\\s?\\d+(?:[.,]\\d+)?|\\b\\d+(?:[.,]\\d+)?\\s?(?:usd|eur|руб|р\\.?|₽|токен|tokens?)\\b)", Pattern.CASE_INSENSITIVE);
    }
}
