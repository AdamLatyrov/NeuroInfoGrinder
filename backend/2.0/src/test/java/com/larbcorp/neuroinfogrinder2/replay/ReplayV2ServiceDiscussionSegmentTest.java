package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder2.settings.PipelineSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplayV2ServiceDiscussionSegmentTest {
    @Test
    void apiStatusFallbackThreadBecomesDiscussionSegmentCandidate() throws Exception {
        List<?> segments = buildSegments(List.of(
                intel(6034L, "статус модельки надо где-то показывать", 1, -100L, 11L, "2026-06-25T22:10:00Z"),
                intel(6042L, "а если openai status зеленый, но у нас api тупит?", 1, -100L, 11L, "2026-06-25T22:11:00Z"),
                intel(6052L, "сделайте status page по моделям и api", 1, -100L, 11L, "2026-06-25T22:12:00Z"),
                intel(6062L, "для hermes нужна инфа какая модель fallback включилась", 1, -100L, 11L, "2026-06-25T22:13:00Z"),
                intel(6105L, "чеклист: проверить endpoint, регион, vpn, model id и fallback", 1, -100L, 11L, "2026-06-25T22:17:00Z")
        ));

        assertThat(decision(segments.get(0))).isEqualTo("DISCUSSION_SEGMENT_CANDIDATE");
        assertThat(sourceCount(segments.get(0))).isGreaterThan(1);
    }

    @Test
    void anthropicCacheCostThreadBecomesDiscussionSegmentCandidate() throws Exception {
        List<?> segments = buildSegments(List.of(
                intel(6765L, "в anthropic cache write стоит дороже, read дешевле, но лимиты все равно жрет", 1, -200L, 22L, "2026-06-26T06:49:00Z"),
                intel(6776L, "простыми словами первый запрос кладет хвост в кеш", 1, -200L, 22L, "2026-06-26T06:50:00Z"),
                intel(6799L, "если сделать /new или /clear кеш сбрасывается", 1, -200L, 22L, "2026-06-26T06:53:00Z"),
                intel(6817L, "если задача не требует 1m context лучше отключить", 1, -200L, 22L, "2026-06-26T06:56:00Z"),
                intel(6876L, "один запрос с 910k токенов в кеше сожрал 9.8$", 1, -200L, 22L, "2026-06-26T06:59:00Z")
        ));

        assertThat(decision(segments.get(0))).isEqualTo("DISCUSSION_SEGMENT_CANDIDATE");
    }

    @Test
    void sameTimestampCliPainBurstBecomesDiscussionSegmentCandidate() throws Exception {
        List<?> segments = buildSegments(List.of(
                intel(6221L, "главный принцип singular cli: не IDE, а автономный агент", 1, -300L, 33L, "2026-06-25T23:30:00Z"),
                intel(6223L, "какие боли у пользователей cli coding tools?", 1, -300L, 33L, "2026-06-25T23:30:00Z"),
                intel(6224L, "1. дорого 2. нет контроля 3. слабые модели 4. мало open-source 5. плохо с доменом 6. нет истории", 1, -300L, 33L, "2026-06-25T23:30:01Z"),
                intel(6225L, "жду фидбек по списку", 1, -300L, 33L, "2026-06-25T23:30:02Z")
        ));

        assertThat(decision(segments.get(0))).isEqualTo("DISCUSSION_SEGMENT_CANDIDATE");
        assertThat(proposedType(segments.get(0))).isEqualTo("SUMMARY");
    }

    @Test
    void productionLikeCliFeedbackBurstPrefersSummary() throws Exception {
        List<?> segments = buildSegments(List.of(
                intel(6220L, "собираю ОС по кли", 1, -300L, 33L, "2026-06-25T23:30:54Z"),
                intel(6221L, "на каком принципе мы основываемся: сделать работу простой удобной и эффективной без кучи скиллов плагинов и установок", 1, -300L, 33L, "2026-06-25T23:30:54Z"),
                intel(6222L, "ГОРЕНИЕ ЖОПЫ", 1, -300L, 33L, "2026-06-25T23:30:54Z"),
                intel(6223L, "поэтому мне нужно знать от чего у тебя чаще всего горит жопа в ВК", 1, -300L, 33L, "2026-06-25T23:30:54Z"),
                intel(6224L, "актуальный уже существующий список: 1. кривой фронт 2. отсутствие фри моделей 3. типичные ошибки деплоя 4. траты токенов", 1, -300L, 33L, "2026-06-25T23:30:54Z"),
                intel(6225L, "если есть какие-то идеи буду рад ОС", 1, -300L, 33L, "2026-06-25T23:30:54Z")
        ));

        assertThat(segments).hasSize(1);
        assertThat(proposedType(segments.get(0))).isEqualTo("SUMMARY");
    }

    @Test
    void retreatRiskThreadBecomesCarefulDiscussionSegmentCandidate() throws Exception {
        List<?> segments = buildSegments(List.of(
                intel(6341L, "ездил кто на психоделические ретриты? хочу задать вопросы", 1, -400L, 44L, "2026-06-25T23:35:00Z"),
                intel(6543L, "видел негативные кейсы: людей уводило в эзотерику, тревожность усиливалась", 1, -400L, 44L, "2026-06-26T01:37:00Z"),
                intel(6592L, "часто лучше начать с формулировки целей и работы с тревожностью", 1, -400L, 44L, "2026-06-26T02:10:00Z"),
                intel(6688L, "это не медицинский совет, просто наблюдения", 1, -400L, 44L, "2026-06-26T06:40:00Z")
        ));

        assertThat(decision(segments.get(0))).isEqualTo("DISCUSSION_SEGMENT_CANDIDATE");
        assertThat(proposedType(segments.get(0))).isIn("ANSWER", "SUMMARY");
    }

    @Test
    void remoteWorkRiskPairRequiresMoreContext() throws Exception {
        Object candidate = scoreWindow(List.of(
                intel(6144L, "можно ли работать из другой страны и не говорить компании?", 1, -500L, 55L, "2026-06-25T22:40:00Z"),
                intel(6367L, "риск зависит от компании и security, лучше спросить менеджера и коллег, location могут спалить", 1, -500L, 55L, "2026-06-25T22:45:00Z")
        ));

        assertThat(candidate).isNotNull();
        assertThat(decision(candidate)).isEqualTo("DISCUSSION_SEGMENT_NEEDS_MORE_CONTEXT");
    }

    @Test
    void overlappingDiscussionWindowsKeepOneBestCandidate() throws Exception {
        List<?> segments = buildSegments(List.of(
                intel(7001L, "api тупит после fallback", 1, -700L, 77L, "2026-06-26T01:00:00Z"),
                intel(7002L, "ошибка timeout и 404 на endpoint", 1, -700L, 77L, "2026-06-26T01:01:00Z"),
                intel(7003L, "решение: проверить model id и статус provider", 1, -700L, 77L, "2026-06-26T01:02:00Z"),
                intel(7004L, "чеклист: endpoint, токены, fallback, регион", 1, -700L, 77L, "2026-06-26T01:03:00Z"),
                intel(7005L, "итог: сначала status page, потом retry", 1, -700L, 77L, "2026-06-26T01:04:00Z")
        ));

        assertThat(segments).hasSize(1);
        assertThat(sourceCount(segments.get(0))).isGreaterThanOrEqualTo(4);
    }

    @Test
    void entityOnlyRepeatedMentionsAreRejected() throws Exception {
        List<?> segments = buildSegments(List.of(
                intel(7101L, "api model fallback cache", 1, -710L, 71L, "2026-06-26T01:00:00Z"),
                intel(7102L, "api model fallback cache", 1, -710L, 71L, "2026-06-26T01:01:00Z"),
                intel(7103L, "api model fallback cache", 1, -710L, 71L, "2026-06-26T01:02:00Z")
        ));

        assertThat(segments).isEmpty();
    }

    @Test
    void perTwentyMinuteBucketKeepsAtMostTwoSameTypeSegments() throws Exception {
        List<?> segments = buildSegments(List.of(
                intel(7201L, "api error timeout", 1, -720L, 72L, "2026-06-26T01:00:00Z"),
                intel(7202L, "решение проверь endpoint token", 1, -720L, 72L, "2026-06-26T01:01:00Z"),
                intel(7203L, "чеклист api fallback", 1, -720L, 72L, "2026-06-26T01:02:00Z"),
                intel(7211L, "openai 401 error", 1, -720L, 72L, "2026-06-26T01:08:00Z"),
                intel(7212L, "решение обновить token", 1, -720L, 72L, "2026-06-26T01:09:00Z"),
                intel(7213L, "чеклист endpoint region", 1, -720L, 72L, "2026-06-26T01:10:00Z"),
                intel(7221L, "anthropic cache лимит", 1, -720L, 72L, "2026-06-26T01:16:00Z"),
                intel(7222L, "надо отключить 1m context", 1, -720L, 72L, "2026-06-26T01:17:00Z"),
                intel(7223L, "итог проверь кеш и токены", 1, -720L, 72L, "2026-06-26T01:18:00Z")
        ));

        assertThat(segments).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void promoHostingPostIsRejected() throws Exception {
        List<?> segments = buildSegments(List.of(
                intel(6306L, "дешевый hosting vps купите по промокоду скидка тариф акция", 1, -600L, 66L, "2026-06-25T23:36:00Z"),
                intel(6307L, "еще добавили скидку и реф ссылку", 1, -600L, 66L, "2026-06-25T23:37:00Z")
        ));

        assertThat(segments).isEmpty();
    }

    @Test
    void approvedJudgeDecisionAcceptsDiscussionSegmentDecision() throws Exception {
        ReplayV2Service service = service();
        ObjectMapper mapper = new ObjectMapper();

        boolean approved = approvedJudgeDecision(service, mapper.readTree("""
                {"decision":"DISCUSSION_SEGMENT_MATERIAL_CANDIDATE","confidence":0.81}
                """));

        assertThat(approved).isTrue();
    }

    @Test
    void providerCacheGenerationPromptRequiresProviderDocumentationCaveat() throws Exception {
        Object candidate = scoreWindow(List.of(
                intel(6765L, "почитайте теорию как работает кеширование в антропике, лимиты в подписке как деньги в api", 1, -200L, 22L, "2026-06-26T06:49:00Z"),
                intel(6776L, "первые запросы пишут в cache дорого, следующие берутся из cache дешевле", 1, -200L, 22L, "2026-06-26T06:50:00Z"),
                intel(6799L, "если долго ждать cache может сброситься, тарифы и лимиты отличаются", 1, -200L, 22L, "2026-06-26T06:53:00Z")
        ));

        String prompt = promptDiscussion(service(), candidate, "generation");

        assertThat(prompt).contains("Проверьте актуальную документацию провайдера");
        assertThat(prompt).contains("лимиты, тарифы, поведение cache и доступность моделей могут меняться");
    }

    @Test
    void discussionSegmentGuideGenerationPromptForbidsMetaChatFraming() throws Exception {
        Object candidate = scoreWindow(List.of(
                intel(8001L, "api зависает после fallback", 1, -800L, 80L, "2026-06-26T10:00:00Z"),
                intel(8002L, "проверь base_url ключ model id и статус провайдера", 1, -800L, 80L, "2026-06-26T10:01:00Z"),
                intel(8003L, "чеклист диагностики: endpoint, token, fallback, retry", 1, -800L, 80L, "2026-06-26T10:02:00Z")
        ));

        String prompt = promptDiscussion(service(), candidate, "generation");

        assertThat(prompt).contains("Do not use meta-chat framing");
        assertThat(prompt).contains("участники сообщества");
        assertThat(prompt).contains("в обсуждении");
        assertThat(prompt).contains("For GUIDE output include title, short summary, practical steps, checks/checklist, caveats, and what to avoid");
    }

    @Test
    void discussionSegmentSummaryGenerationPromptForbidsMetaChatFraming() throws Exception {
        Object candidate = scoreWindow(List.of(
                intel(8101L, "главные боли cli: потеря контекста", 1, -810L, 81L, "2026-06-26T10:00:00Z"),
                intel(8102L, "дорогие токены и слабый контроль состояния", 1, -810L, 81L, "2026-06-26T10:01:00Z"),
                intel(8103L, "нужны история, контроль расходов и понятные статусы", 1, -810L, 81L, "2026-06-26T10:02:00Z")
        ));

        String prompt = promptDiscussion(service(), candidate, "generation");

        assertThat(prompt).contains("For SUMMARY output include key points, grouped observations, implications, and possible next actions");
        assertThat(prompt).contains("Start with useful information directly");
        assertThat(prompt).contains("Do not write as a recap of chat messages");
    }

    @Test
    void singleMessageGuideGenerationPromptForbidsMetaChatFraming() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Object single = singleMessageCandidate(9001L, 0.91, "SINGLE_MESSAGE_MATERIAL_CANDIDATE", mapper.createArrayNode().add("API_TROUBLESHOOTING"));
        Object source = intel(9001L, "Проверьте base_url, ключ и выбранную модель", 1, -900L, 90L, "2026-06-26T11:00:00Z");

        String prompt = promptSingle(service(), single, source, "generation");

        assertThat(prompt).contains("Do not use meta-chat framing");
        assertThat(prompt).contains("For GUIDE output include title, short summary, practical steps, checks/checklist, caveats, and what to avoid");
    }

    @Test
    void clusterGenerationPromptPreservesFactsAndBlocksUnsupportedExactValues() throws Exception {
        Object cluster = cluster(1L, "Provider API diagnostics", 0.78, List.of(1L, 2L));
        List<?> intel = List.of(
                intel(1L, "проверь endpoint и model id", 1, -910L, 91L, "2026-06-26T11:00:00Z"),
                intel(2L, "точные лимиты и тарифы надо сверять с документацией", 1, -910L, 91L, "2026-06-26T11:01:00Z")
        );

        String prompt = promptCluster(service(), cluster, intel, "generation");

        assertThat(prompt).contains("Preserve useful facts from source context");
        assertThat(prompt).contains("Do not invent exact API docs, pricing, time limits, model availability, SLA, provider behavior, legal, medical, or security claims");
        assertThat(prompt).contains("Проверьте актуальную документацию провайдера");
    }

    @Test
    void controlledModeRiskSensitiveDiscussionIsNotEligible() throws Exception {
        Object candidate = scoreWindow(List.of(
                intel(6341L, "ездил кто на психоделические ретриты? хочу задать вопросы", 1, -400L, 44L, "2026-06-25T23:35:00Z"),
                intel(6543L, "видел негативные кейсы, это не медицинский совет, риски есть", 1, -400L, 44L, "2026-06-26T01:37:00Z"),
                intel(6592L, "лучше начать с формулировки целей и работы с тревожностью", 1, -400L, 44L, "2026-06-26T02:10:00Z")
        ));

        boolean eligible = discussionEligibleForControlledGeneration(service(), candidate);

        assertThat(eligible).isFalse();
    }

    private static ReplayV2Service service() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        PipelineSettingsService settings = mock(PipelineSettingsService.class);
        when(settings.getInt(eq("discussionSegmentMaxMessages"), any(Integer.class))).thenReturn(6);
        when(settings.getInt(eq("discussionSegmentMaxWindowMinutes"), any(Integer.class))).thenReturn(20);
        when(settings.getDouble(eq("discussionSegmentCandidateThreshold"), any(Double.class))).thenReturn(0.55);
        when(settings.getInt(eq("discussionSegmentSkipRiskSensitive"), any(Integer.class))).thenReturn(1);
        return new ReplayV2Service(jdbc, new ObjectMapper(), mock(ModelWorkerClient.class), mock(ModelhubProviderGateway.class), mock(Environment.class), settings);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<?> buildSegments(List intel) throws Exception {
        Method method = ReplayV2Service.class.getDeclaredMethod("discussionSegmentDetection", long.class, List.class, Map.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(service(), 99L, intel, new HashMap<String, BigDecimal>());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object scoreWindow(List intel) throws Exception {
        Method method = ReplayV2Service.class.getDeclaredMethod("scoreDiscussionWindow", long.class, List.class, double.class);
        method.setAccessible(true);
        return method.invoke(service(), 99L, intel, 0.55);
    }

    private static Object intel(long id, String text, long accountId, long chatId, Long topicId, String at) throws Exception {
        Object message = message(id, text, accountId, chatId, topicId, at);
        Class<?> type = Class.forName("com.larbcorp.neuroinfogrinder2.replay.ReplayV2Service$Intel");
        Constructor<?> constructor = type.getDeclaredConstructor(long.class, message.getClass(), String.class, com.fasterxml.jackson.databind.JsonNode.class, boolean.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(id, message, text, new ObjectMapper().createObjectNode(), true, "CANDIDATE");
    }

    private static Object message(long id, String text, long accountId, long chatId, Long topicId, String at) throws Exception {
        Class<?> type = Class.forName("com.larbcorp.neuroinfogrinder2.replay.ReplayV2Service$Message");
        Constructor<?> constructor = type.getDeclaredConstructor(long.class, String.class, String.class, String.class, String.class, String.class, long.class, long.class, Long.class, Long.class, Timestamp.class, Timestamp.class);
        constructor.setAccessible(true);
        return constructor.newInstance(id, text, null, "{}", "[]", "{}", accountId, chatId, topicId, topicId, Timestamp.from(Instant.parse(at)), Timestamp.from(Instant.parse(at)));
    }

    private static String decision(Object candidate) throws Exception {
        Method method = candidate.getClass().getDeclaredMethod("decision");
        method.setAccessible(true);
        return (String) method.invoke(candidate);
    }

    private static int sourceCount(Object candidate) throws Exception {
        Method method = candidate.getClass().getDeclaredMethod("sourceCount");
        method.setAccessible(true);
        return (int) method.invoke(candidate);
    }

    private static String proposedType(Object candidate) throws Exception {
        Method method = candidate.getClass().getDeclaredMethod("proposedMaterialType");
        method.setAccessible(true);
        return (String) method.invoke(candidate);
    }

    private static boolean approvedJudgeDecision(ReplayV2Service service, Object response) throws Exception {
        Method method = ReplayV2Service.class.getDeclaredMethod("approvedJudgeDecision", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, response);
    }

    private static String promptDiscussion(ReplayV2Service service, Object candidate, String type) throws Exception {
        Method method = ReplayV2Service.class.getDeclaredMethod("promptDiscussion", candidate.getClass(), String.class, com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);
        return (String) method.invoke(service, candidate, type, new ObjectMapper().createObjectNode());
    }

    private static Object singleMessageCandidate(long messageId, double score, String decision, Object signals) throws Exception {
        Class<?> type = Class.forName("com.larbcorp.neuroinfogrinder2.replay.ReplayV2Service$SingleMessageCandidate");
        Constructor<?> constructor = type.getDeclaredConstructor(long.class, double.class, String.class, com.fasterxml.jackson.databind.JsonNode.class);
        constructor.setAccessible(true);
        return constructor.newInstance(messageId, score, decision, signals);
    }

    private static String promptSingle(ReplayV2Service service, Object candidate, Object intel, String type) throws Exception {
        Method method = ReplayV2Service.class.getDeclaredMethod("promptSingle", candidate.getClass(), intel.getClass(), String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, candidate, intel, type);
    }

    private static Object cluster(long id, String title, double score, List<Long> members) throws Exception {
        Class<?> type = Class.forName("com.larbcorp.neuroinfogrinder2.replay.ReplayV2Service$Cluster");
        Constructor<?> constructor = type.getDeclaredConstructor(long.class, String.class, double.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(id, title, score, members);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String promptCluster(ReplayV2Service service, Object cluster, List intel, String type) throws Exception {
        Method method = ReplayV2Service.class.getDeclaredMethod("prompt", cluster.getClass(), List.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, cluster, intel, type);
    }

    private static boolean discussionEligibleForControlledGeneration(ReplayV2Service service, Object candidate) throws Exception {
        Method method = ReplayV2Service.class.getDeclaredMethod("discussionEligibleForControlledGeneration", candidate.getClass(), long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, candidate, 99L);
    }
}
