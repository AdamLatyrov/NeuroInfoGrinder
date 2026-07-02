=== ПЕРЕПРОВЕРКА ПОСЛЕ RENORMALIZE ===
  raw=27737 [SIM-гайд] -> route=REVIEW_HIGH_RECALL marker=model_pricing_marker
  raw=29577 [codebase-mcp how-to] -> route=REVIEW_HIGH_RECALL marker=model_pricing_marker
  raw=29651 [codebase-mcp правила работы] -> route=REVIEW_HIGH_RECALL marker=rules_onboarding_marker
  raw=29934 [Claude Code spyware] -> route=SIGNAL_ONLY marker=model_pricing_marker
  raw=26045 [#вакансия] -> route=AGGREGATE_ONLY marker=job_event_marker
  raw=19413 [free-access g0i.ai] -> route=MANUAL_REVIEW marker=risk_referral_marker
  raw=27968 [SEO/GEO гайд] -> route=REVIEW_HIGH_RECALL marker=workflow_howto_marker
  raw=26958 [vimit проект] -> route=REVIEW_HIGH_RECALL marker=workflow_howto_marker

routes={'CONTEXT_ONLY': 18721, 'REJECT_SAFE': 469, 'REVIEW_HIGH_RECALL': 320, 'AGGREGATE_ONLY': 156, 'NEEDS_ENRICHMENT': 140, 'MANUAL_REVIEW': 104, 'SIGNAL_ONLY': 90}
decisions={'REVIEW_SINGLE_SIGNAL': 215, 'LINK_ENRICHMENT_FIRST': 116, 'CONTEXT_ONLY': 93, 'MANUAL_REVIEW': 75, 'SIGNAL_PAIR': 20, 'CONTEXT_GROUP': 15, 'WEAK_PAIR': 8, 'BLOCKED': 6, 'SIGNAL_GROUP': 6, 'EVIDENCE_GROUP_REVIEW': 1}
material_candidates=1
