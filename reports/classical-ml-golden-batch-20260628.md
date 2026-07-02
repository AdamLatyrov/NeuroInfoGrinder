# Classical ML Golden Batch - 2026-06-28

Input source: `reports/golden-batch-real-posts-20260628.json` evidence snapshot. Full raw text requires DB/API fetch; this run is offline and read-only.

| case | anchor | expected | actual | preprocessing | meaning | value | usefulness | evidence | assembly | route | dedupe | llmGate | pass |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GB01 | 11142 | ACCEPT_AS_REFERENCE_NOT_GUIDE | ABSTAIN | CLEAN | RESOURCE_REFERENCE | MATERIAL_CANDIDATE | REFERENCE | ENOUGH_SINGLE_MESSAGE | SINGLE_MESSAGE | REFERENCE | UNIQUE | APPROVE_FOR_JUDGE | True |
| GB02 | 11484 | ACCEPT_AS_REFERENCE_NOT_GUIDE | ABSTAIN | CLEAN | RESOURCE_REFERENCE | MATERIAL_CANDIDATE | REFERENCE | ENOUGH_SINGLE_MESSAGE | SINGLE_MESSAGE | REFERENCE | UNIQUE | APPROVE_FOR_JUDGE | True |
| GB03 | 11193 | REJECT_LINK_ONLY_NEEDS_ENRICHMENT | ABSTAIN | CLEAN | RESOURCE_REFERENCE | NOT_GARBAGE_NO_MATERIAL | REFERENCE | NEEDS_LINK_ENRICHMENT | LINK_ENRICHED_SINGLE | NO_MATERIAL | UNIQUE | HOLD_FOR_CONTEXT | True |
| GB04 | CL01 | DISCUSSION_SEGMENT_GUIDE | ABSTAIN | CLEAN | TROUBLESHOOTING | MATERIAL_CANDIDATE | DIAGNOSTIC | NEEDS_DISCUSSION_CONTEXT | DISCUSSION_SEGMENT | GUIDE | CLUSTER_MEMBER | APPROVE_FOR_JUDGE | True |
| GB05 | CL02 | DISCUSSION_SEGMENT_GUIDE | ABSTAIN | CLEAN | PRACTICAL_INSTRUCTION | MATERIAL_CANDIDATE | ACTIONABLE | NEEDS_DISCUSSION_CONTEXT | DISCUSSION_SEGMENT | GUIDE | CLUSTER_MEMBER | APPROVE_FOR_JUDGE | True |
| GB06 | CL05 | GUIDE_OR_STRONG_ANSWER | ABSTAIN | CLEAN | PRACTICAL_INSTRUCTION | MATERIAL_CANDIDATE | ACTIONABLE | NEEDS_DISCUSSION_CONTEXT | DISCUSSION_SEGMENT | GUIDE | CLUSTER_MEMBER | APPROVE_FOR_JUDGE | True |
| GB07 | CL07 | NEEDS_SAFETY_SUMMARY_OR_REJECT | BLOCKED_PREPROCESSING | RISK_SENSITIVE | RESOURCE_REFERENCE | NOT_GARBAGE_NO_MATERIAL | CONTEXTUAL | NEEDS_DISCUSSION_CONTEXT | DISCUSSION_SEGMENT | NO_MATERIAL | CLUSTER_MEMBER | REJECT_BEFORE_JUDGE | True |
| GB08 | CL10 | REJECT_PROMO | BLOCKED_PREPROCESSING | NOISE | PROMO_AD | NOT_GARBAGE_NO_MATERIAL | CONTEXTUAL | NEEDS_DISCUSSION_CONTEXT | DISCUSSION_SEGMENT | NO_MATERIAL | CLUSTER_MEMBER | REJECT_BEFORE_JUDGE | True |

## Inputs

### GB01 `11142`

Input preview: Expected positive candidate; no LOW_SINGLE_MESSAGE_SCORE.

### GB02 `11484`

Input preview: Expected GitHub/resource candidate including hidden-link-style context. OpenMontage GitHub video-agent system broader_day_candidate_raw=11484; broader_day_message_date=2026-06-27T06:01:19+00:00; hit_count=5 nearest timestamp raw does not match expected content

### GB03 `11193`

Input preview: Expected reject; no generic TOO_SHORT.

### GB04 `CL01`

Input preview: Checklist for diagnosing ModelHub/API slowdowns and model fallback Problem symptoms; endpoint/region check; status/model availability; client fallback strategy; product diagnostics request. Actionable support/product feedback for API users and model fallback. 

### GB05 `CL02`

Input preview: How Claude/Anthropic prompt caching affects limits and API cost Cache write/read; why first request is costly; /new/clear caveat; long context cost; practical rules. Strong practical explanation with examples and user-facing advice. DISCUSSION_SEGMENT and comb

### GB06 `CL05`

Input preview: How to improve hh.ru resume visibility: activity checklist and AI-filter limitations Disclaimer; checklist of activity signals; contact-data caveat; timing; AI-filter limitation; resume review recommendation. Strong standalone guide-like answer. Fixed by appro

### GB07 `CL07`

Input preview: Psychedelic retreats: cautionary observations and goal-setting alternative Question context; observed harms; goal-setting alternative; caveats and non-medical framing. Coherent Q&A chain but sensitive and context-dependent. DISCUSSION_SEGMENT with safety frami

### GB08 `CL10`

Input preview: Hosting promo post N/A Ad/promo, not user knowledge. Promo suppression

