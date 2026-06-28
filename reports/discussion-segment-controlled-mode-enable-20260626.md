# DISCUSSION_SEGMENT Controlled Mode Enable - 2026-06-26

## Verdict

Controlled production mode is enabled on production with safe caps and fresh-only gates. No backlog or reprocess was run as part of enablement.

## Effective Settings

| setting | before | after | effective | source |
|---|---:|---:|---:|---|
| discussionSegmentGenerationEnabled | 0 | 1 | 1 | production DB `pipeline_settings` |
| discussionSegmentGenerationMode | OFF | CONTROLLED | CONTROLLED | production DB `pipeline_settings` |
| discussionSegmentMaxMaterialsPerDay | 3 | 3 | 3 | production DB `pipeline_settings` |
| discussionSegmentMaxMaterialsPerChatTopicPerDay | 1 | 1 | 1 | production DB `pipeline_settings` |
| discussionSegmentRequireLlmAccepted | 1 | 1 | 1 | production DB `pipeline_settings` |
| discussionSegmentDraftOnly | 1 | 1 | 1 | production DB `pipeline_settings` |
| discussionSegmentSkipRiskSensitive | 1 | 1 | 1 | production DB `pipeline_settings` |
| discussionSegmentFreshOnly | 1 | 1 | 1 | production DB `pipeline_settings` |
| discussionSegmentStopOnProviderError | 1 | 1 | 1 | production DB `pipeline_settings` |
| discussionSegmentStopOnGenerationError | 1 | 1 | 1 | production DB `pipeline_settings` |
| discussionSegmentControlledEnableTime | empty | 2026-06-26T16:23:22Z | 2026-06-26T16:23:22Z | production DB `pipeline_settings` |

## Enable Evidence

| check | result |
|---|---:|
| Captured effective time | 2026-06-26T16:38:00Z |
| Effective enable time | 2026-06-26T16:23:22Z |
| Active knowledge items | 24 |
| Active DISCUSSION_SEGMENT materials | 3 |
| DISCUSSION_SEGMENT materials after effective enable time | 0 |
| PUBLISHED materials after effective enable time | 0 |

## Notes

The raw pre-enable artifact was refreshed after enable while collecting evidence, so this report uses the known pre-enable safe-default state from the preceding deployment checks: `discussionSegmentGenerationEnabled=0`, `discussionSegmentGenerationMode=OFF`, and empty controlled enable time.

The three existing DISCUSSION_SEGMENT DRAFT materials were manually controlled materializations from earlier today and predate the effective enable time.

## Artifacts

| artifact | purpose |
|---|---|
| `reports/discussion-controlled-mode-enable-raw-20260626.json` | raw enable command output |
| `reports/discussion-controlled-mode-effective-raw-20260626.json` | effective production settings snapshot |
| `reports/discussion-controlled-production-monitor-raw-20260626.json` | initial production monitoring snapshot |
