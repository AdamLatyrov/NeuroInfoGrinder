=== ПЕРЕПРОВЕРКА РУЧНО НАЙДЕННЫХ КЕЙСОВ ===
  raw=27737 [SIM-гайд] -> route=REVIEW_HIGH_RECALL marker=model_pricing_marker class=WEAK_SIGNAL
  raw=29577 [codebase-mcp how-to] -> route=REVIEW_HIGH_RECALL marker=model_pricing_marker class=WEAK_SIGNAL
  raw=29651 [codebase-mcp правила работы] -> route=REJECT_SAFE marker=rules_onboarding_marker class=RULES_ONBOARDING
  raw=29934 [Claude Code spyware] -> route=SIGNAL_ONLY marker=model_pricing_marker class=WEAK_SIGNAL
  raw=26045 [#вакансия] -> route=AGGREGATE_ONLY marker=job_event_marker class=LOW_VALUE_CHATTER
  raw=19413 [free-access g0i.ai] -> route=MANUAL_REVIEW marker=risk_referral_marker class=MODEL_RUMOR_OR_PRICING_CLAIM
  raw=27968 [SEO/GEO гайд] -> route=REVIEW_HIGH_RECALL marker=workflow_howto_marker class=WEAK_SIGNAL
  raw=26958 [vimit проект] -> route=REVIEW_HIGH_RECALL marker=workflow_howto_marker class=TECH_SIGNAL

routes={'CONTEXT_ONLY': 18723, 'REJECT_SAFE': 470, 'REVIEW_HIGH_RECALL': 317, 'AGGREGATE_ONLY': 156, 'NEEDS_ENRICHMENT': 140, 'MANUAL_REVIEW': 104, 'SIGNAL_ONLY': 90}
clusters=554
decisions={'REVIEW_SINGLE_SIGNAL': 216, 'LINK_ENRICHMENT_FIRST': 116, 'CONTEXT_ONLY': 93, 'MANUAL_REVIEW': 75, 'SIGNAL_PAIR': 19, 'CONTEXT_GROUP': 15, 'WEAK_PAIR': 8, 'SIGNAL_GROUP': 6, 'BLOCKED': 5, 'EVIDENCE_GROUP_REVIEW': 1}
material_candidates=1
