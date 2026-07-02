# Draft Material Eligibility Policy

This is a draft after lab evidence collection, not production behavior.

States:
- `REJECT_NEVER_MATERIAL`: rules, onboarding, moderation bot templates, pure spam/noise.
- `CONTEXT_ONLY`: chatter, short acknowledgements without entity/reply/context, weak surrounding context.
- `SIGNAL_ONLY`: useful but insufficient or unverified claims; keep for trend/source tracking.
- `NEEDS_LINK_ENRICHMENT`: link-only or link-thin messages before content/source extraction.
- `MANUAL_REVIEW`: risk/referral/abuse/security-sensitive content.
- `AGGREGATE_ONLY`: jobs/events/outages/provider claims requiring multiple independent sources.
- `DUPLICATE_OR_UPDATE`: near duplicate of an existing accepted evidence group/material.
- `ELIGIBLE_MATERIAL`: multi-source or context-supported durable knowledge with enough evidence and no risk/enrichment blockers.

Defaults:
- Single-message is not material.
- Single-source is not material.
- Job/event single-source is not material.
- Rules/onboarding never material.
- Risk/referral never guide/material.
- Link-only never material before enrichment.
- Model/pricing/provider claims require official or corroborating source.
