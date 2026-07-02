package com.larbcorp.neuroinfogrinder2.decisioncore;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticDecisionCoreObjectTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void coreObjectCarriesIndependentEligibilityAxes() {
        SemanticDecisionCoreObject object = new SemanticDecisionCoreObject(
            SemanticDecisionCoreObject.TRACE_VERSION,
            SemanticDecisionCoreObject.DECISION_VERSION,
            DecisionCoreEnums.ObjectType.MESSAGE,
            42L,
            List.of(42L),
            List.of("LINK_SHARE"),
            List.of("LINK_ONLY"),
            json.createArrayNode(),
            json.createArrayNode(),
            json.createArrayNode(),
            new BigDecimal("0.20"),
            new BigDecimal("0.10"),
            new BigDecimal("0.90"),
            new BigDecimal("0.10"),
            new BigDecimal("0.30"),
            BigDecimal.ZERO,
            new BigDecimal("0.50"),
            new BigDecimal("0.20"),
            new BigDecimal("0.10"),
            json.createArrayNode(),
            DecisionCoreEnums.FinalRoute.ROUTE_TO_LINK_ENRICHMENT,
            json.createArrayNode(),
            null,
            "url:example.com/path",
            null,
            null,
            "chat:1:20260630T0300",
            DecisionCoreEnums.Eligibility.ELIGIBLE,
            DecisionCoreEnums.Eligibility.INELIGIBLE,
            DecisionCoreEnums.Eligibility.ELIGIBLE,
            DecisionCoreEnums.Eligibility.ELIGIBLE,
            DecisionCoreEnums.Eligibility.INELIGIBLE,
            DecisionCoreEnums.Eligibility.PENDING,
            List.of("NEEDS_LINK_ENRICHMENT"),
            List.of(),
            List.of("NOT_MATERIAL_READY"),
            json.createObjectNode(),
            json.createObjectNode(),
            OffsetDateTime.now(),
            null
        );

        assertThat(object.finalRoute()).isEqualTo(DecisionCoreEnums.FinalRoute.ROUTE_TO_LINK_ENRICHMENT);
        assertThat(object.materialEligibility()).isEqualTo(DecisionCoreEnums.Eligibility.INELIGIBLE);
        assertThat(object.contextEligibility()).isEqualTo(DecisionCoreEnums.Eligibility.ELIGIBLE);
        assertThat(object.signalEligibility()).isEqualTo(DecisionCoreEnums.Eligibility.ELIGIBLE);
        assertThat(object.reasonCodes()).contains("NEEDS_LINK_ENRICHMENT");
    }

    @Test
    void ledgerEntryCanRecordLoserAgainstCompetingDiscussionCandidate() {
        CandidateDecisionLedgerEntry entry = new CandidateDecisionLedgerEntry(
            10L,
            99L,
            "source-set:42-43-44",
            "single:42",
            DecisionCoreEnums.CandidateType.SINGLE_MESSAGE,
            DecisionCoreEnums.LedgerEventType.LOSER_RECORDED,
            "RECORDED",
            false,
            List.of("discussion:77"),
            null,
            null,
            null,
            null,
            new BigDecimal("0.41"),
            DecisionCoreEnums.FinalRoute.ROUTE_TO_SINGLE_CANDIDATE,
            DecisionCoreEnums.FinalRoute.RETAIN_FOR_CONTEXT,
            "ANSWER",
            null,
            List.of("DISCUSSION_CANDIDATE_HAS_STRONGER_EVIDENCE"),
            List.of(42L),
            List.of(),
            json.createObjectNode(),
            OffsetDateTime.now()
        );

        assertThat(entry.winner()).isFalse();
        assertThat(entry.competingCandidateIds()).containsExactly("discussion:77");
        assertThat(entry.routeAfter()).isEqualTo(DecisionCoreEnums.FinalRoute.RETAIN_FOR_CONTEXT);
    }
}
