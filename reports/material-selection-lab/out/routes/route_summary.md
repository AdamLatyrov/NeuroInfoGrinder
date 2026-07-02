# Route Summary

Messages: `20000`

## Routes

- `CONTEXT_ONLY`: `18975`
- `REJECT_SAFE`: `410`
- `SIGNAL_ONLY`: `254`
- `NEEDS_ENRICHMENT`: `123`
- `REVIEW_HIGH_RECALL`: `111`
- `AGGREGATE_ONLY`: `109`
- `MANUAL_REVIEW`: `18`

## Primary Classes

- `LOW_VALUE_CHATTER`: `18926`
- `RULES_ONBOARDING`: `335`
- `WEAK_SIGNAL`: `194`
- `TECH_SIGNAL`: `117`
- `CAREER_JOB_POST`: `95`
- `MODERATION_BOT_EVENT`: `75`
- `MODEL_RUMOR_OR_PRICING_CLAIM`: `69`
- `SOCIAL_MEDIA_LINK`: `42`
- `LINK_SHARE`: `39`
- `INTERNAL_TELEGRAM_LINK`: `36`
- `OUTAGE_STATUS`: `20`
- `EVENT_ANNOUNCEMENT`: `14`
- `RISK_PROMO_REFERRAL`: `11`
- `RESOURCE_LINK`: `8`
- `REFERRAL_OR_INVITE_LINK`: `7`
- `DIGEST_NEWS`: `7`
- `POTENTIAL_DISCUSSION_SIGNAL`: `3`
- `ABUSE_OR_CIRCUMVENTION`: `2`

## Marker Groups

- `short_chatter_marker`: `17745`
- `workflow_howto_marker`: `1064`
- `rules_onboarding_marker`: `410`
- `model_pricing_marker`: `258`
- `outage_status_marker`: `135`
- `link_only_marker`: `129`
- `job_event_marker`: `109`
- `api_troubleshooting_marker`: `77`
- `tool_resource_marker`: `56`
- `risk_referral_marker`: `16`
- `unknown_potential_signal_marker`: `1`

Every routed row has `route_reason`; `REJECT_SAFE` is auditable and does not delete data.
