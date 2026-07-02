================================================================================
СЦЕНАРИЙ: Обсуждение r-api балансировщика (3 сообщения, 3 разных автора, одна тема)
id: S1-multi-source-technical-material
================================================================================

>>> ШАГ 0 — prepare (фиксация raw, conversation_key)
  msg-00001 raw_id=s1-1 conv_key=1|-1003919536687|1302 reply_to=- sender=dev(1001)
  msg-00002 raw_id=s1-2 conv_key=1|-1003919536687|1302 reply_to=- sender=dev2(1002)
  msg-00003 raw_id=s1-3 conv_key=1|-1003919536687|1302 reply_to=- sender=dev3(1003)

>>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)
  msg-00001 len_tokens=13 has_link=False hidden_url_count=0
     strong_entities=['r-api']
     weak_entities=['api']
     domains=[] model_names=[] error_codes=[]
  msg-00002 len_tokens=1 has_link=False hidden_url_count=0
     strong_entities=['r-api']
     weak_entities=['api']
     domains=[] model_names=[] error_codes=[]
  msg-00003 len_tokens=9 has_link=False hidden_url_count=0
     strong_entities=['r-api']
     weak_entities=['api']
     domains=[] model_names=[] error_codes=[]

>>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)
  msg-00001 route=REVIEW_HIGH_RECALL marker=api_troubleshooting_marker risk=LOW reason=technical_or_code_terms;resource_or_tool_terms;interview_or_product_design_task;technical_or_resource_candidate_retained_for_context_evidence_review
  msg-00002 route=REVIEW_HIGH_RECALL marker=api_troubleshooting_marker risk=LOW reason=technical_or_code_terms;resource_or_tool_terms;question_or_qna_shape;interview_or_product_design_task;technical_or_resource_candidate_retained_for_context_evidence_review
  msg-00003 route=REVIEW_HIGH_RECALL marker=api_troubleshooting_marker risk=LOW reason=technical_or_code_terms;resource_or_tool_terms;question_or_qna_shape;interview_or_product_design_task;technical_or_resource_candidate_retained_for_context_evidence_review

>>> ШАГ 3 — conversation reconstruction (reply chain resolve)
  msg-00001 reply_resolved=-
  msg-00002 reply_resolved=-
  msg-00003 reply_resolved=-

>>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)
  local-cluster 0: ['msg-00003', 'msg-00001', 'msg-00002']

>>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)
  merged-cluster 0: size=3 conversations=1 shared=['r-api'] ids=['msg-00003', 'msg-00001', 'msg-00002']

>>> ШАГ 6 — cluster decision (material eligibility gate)
  decision=EVIDENCE_GROUP_REVIEW (группа доказательств на проверку материала)
    size=3 independent_sources=3 routes={'REVIEW_HIGH_RECALL': 3}
    shared_strong=['r-api']

>>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)
  decision=EVIDENCE_GROUP_REVIEW -> ELIGIBLE_FOR_LLM_JUDGE — кластер допущен к LLM Judge (мульти-источник + strong entity + REVIEW_HIGH_RECALL)

================================================================================
СЦЕНАРИЙ: Жалобы на droid от одного автора (не independent sources)
id: S2-single-source-tool-problem
================================================================================

>>> ШАГ 0 — prepare (фиксация raw, conversation_key)
  msg-00001 raw_id=s2-1 conv_key=1|-100111|5 reply_to=- sender=userA(2001)
  msg-00002 raw_id=s2-2 conv_key=1|-100111|5 reply_to=- sender=userA(2001)
  msg-00003 raw_id=s2-3 conv_key=1|-100111|5 reply_to=- sender=userA(2001)

>>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)
  msg-00001 len_tokens=7 has_link=False hidden_url_count=0
     strong_entities=['droid']
     weak_entities=[]
     domains=[] model_names=['droid'] error_codes=[]
  msg-00002 len_tokens=5 has_link=False hidden_url_count=0
     strong_entities=['droid']
     weak_entities=[]
     domains=[] model_names=['droid'] error_codes=[]
  msg-00003 len_tokens=8 has_link=False hidden_url_count=0
     strong_entities=['droid']
     weak_entities=[]
     domains=[] model_names=['droid'] error_codes=[]

>>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)
  msg-00001 route=REVIEW_HIGH_RECALL marker=unknown_potential_signal_marker risk=LOW reason=question_or_qna_shape;no_material_evidence;technical_troubleshooting_or_resource_with_strong_entity
  msg-00002 route=REVIEW_HIGH_RECALL marker=unknown_potential_signal_marker risk=LOW reason=no_material_evidence;technical_troubleshooting_or_resource_with_strong_entity
  msg-00003 route=REVIEW_HIGH_RECALL marker=unknown_potential_signal_marker risk=LOW reason=no_material_evidence;technical_troubleshooting_or_resource_with_strong_entity

>>> ШАГ 3 — conversation reconstruction (reply chain resolve)
  msg-00001 reply_resolved=-
  msg-00002 reply_resolved=-
  msg-00003 reply_resolved=-

>>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)
  local-cluster 0: ['msg-00003', 'msg-00001', 'msg-00002']

>>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)
  merged-cluster 0: size=3 conversations=1 shared=['droid'] ids=['msg-00003', 'msg-00001', 'msg-00002']

>>> ШАГ 6 — cluster decision (material eligibility gate)
  decision=SIGNAL_GROUP (группа сигналов (недостаточно источников))
    size=3 independent_sources=1 routes={'REVIEW_HIGH_RECALL': 3}
    shared_strong=['droid']

>>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)
  decision=SIGNAL_GROUP -> NOT_ELIGIBLE — остаётся сигналом/контекстом, до LLM не доходит

================================================================================
СЦЕНАРИЙ: Senpi free-token кампания от разных авторов (promo, не материал)
id: S3-promo-airdrop-multi-source
================================================================================

>>> ШАГ 0 — prepare (фиксация raw, conversation_key)
  msg-00001 raw_id=s3-1 conv_key=1|-100222|1 reply_to=- sender=dropper(3001)
  msg-00002 raw_id=s3-2 conv_key=1|-100222|1 reply_to=- sender=dropper2(3002)

>>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)
  msg-00001 len_tokens=10 has_link=False hidden_url_count=0
     strong_entities=[]
     weak_entities=[]
     domains=[] model_names=[] error_codes=[]
  msg-00002 len_tokens=11 has_link=False hidden_url_count=0
     strong_entities=[]
     weak_entities=[]
     domains=[] model_names=[] error_codes=[]

>>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)
  msg-00001 route=MANUAL_REVIEW marker=risk_referral_marker risk=LOW reason=no_material_evidence;risk_referral_or_promo_never_auto_material
  msg-00002 route=MANUAL_REVIEW marker=risk_referral_marker risk=LOW reason=no_material_evidence;risk_referral_or_promo_never_auto_material

>>> ШАГ 3 — conversation reconstruction (reply chain resolve)
  msg-00001 reply_resolved=-
  msg-00002 reply_resolved=-

>>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)
  local-cluster 0: ['msg-00001']
  local-cluster 1: ['msg-00002']

>>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)
  merged-cluster 0: size=1 conversations=1 shared=[] ids=['msg-00001']
  merged-cluster 1: size=1 conversations=1 shared=[] ids=['msg-00002']

>>> ШАГ 6 — cluster decision (material eligibility gate)
  decision=MANUAL_REVIEW (ручная проверка (риск))
    size=1 independent_sources=1 routes={'MANUAL_REVIEW': 1}
    shared_strong=[]
  decision=MANUAL_REVIEW (ручная проверка (риск))
    size=1 independent_sources=1 routes={'MANUAL_REVIEW': 1}
    shared_strong=[]

>>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)
  decision=MANUAL_REVIEW -> NOT_ELIGIBLE — риск/promo, ручная проверка, до LLM не доходит
  decision=MANUAL_REVIEW -> NOT_ELIGIBLE — риск/promo, ручная проверка, до LLM не доходит

================================================================================
СЦЕНАРИЙ: Rules-бот приветствие (никогда не материал)
id: S4-rules-onboarding
================================================================================

>>> ШАГ 0 — prepare (фиксация raw, conversation_key)
  msg-00001 raw_id=s4-1 conv_key=1|-100333|1 reply_to=- sender=Vibecoder Rules(4001)
  msg-00002 raw_id=s4-2 conv_key=1|-100333|1 reply_to=- sender=Бот-Помощник ОМ(4001)

>>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)
  msg-00001 len_tokens=9 has_link=False hidden_url_count=0
     strong_entities=[]
     weak_entities=[]
     domains=[] model_names=[] error_codes=[]
  msg-00002 len_tokens=7 has_link=False hidden_url_count=0
     strong_entities=[]
     weak_entities=[]
     domains=[] model_names=[] error_codes=[]

>>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)
  msg-00001 route=REJECT_SAFE marker=rules_onboarding_marker risk=LOW reason=rules_or_onboarding_template;hard_rule_never_material_rules_onboarding_or_moderation
  msg-00002 route=REJECT_SAFE marker=rules_onboarding_marker risk=LOW reason=rules_or_onboarding_template;hard_rule_never_material_rules_onboarding_or_moderation

>>> ШАГ 3 — conversation reconstruction (reply chain resolve)
  msg-00001 reply_resolved=-
  msg-00002 reply_resolved=-

>>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)

>>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)

>>> ШАГ 6 — cluster decision (material eligibility gate)

>>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)

================================================================================
СЦЕНАРИЙ: Скрытая реферальная ссылка в raw_json caption entity
id: S5-hidden-referral-link
================================================================================

>>> ШАГ 0 — prepare (фиксация raw, conversation_key)
  msg-00001 raw_id=s5-1 conv_key=1|-100444|1 reply_to=- sender=anon(5001)

>>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)
  msg-00001 len_tokens=4 has_link=True hidden_url_count=1
     strong_entities=['bynara.id']
     weak_entities=['сервис']
     domains=['bynara.id'] model_names=[] error_codes=[]

>>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)
  msg-00001 route=MANUAL_REVIEW marker=risk_referral_marker risk=LOW reason=resource_or_tool_terms;no_material_evidence;risk_referral_or_promo_never_auto_material

>>> ШАГ 3 — conversation reconstruction (reply chain resolve)
  msg-00001 reply_resolved=-

>>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)
  local-cluster 0: ['msg-00001']

>>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)
  merged-cluster 0: size=1 conversations=1 shared=['bynara.id'] ids=['msg-00001']

>>> ШАГ 6 — cluster decision (material eligibility gate)
  decision=MANUAL_REVIEW (ручная проверка (риск))
    size=1 independent_sources=1 routes={'MANUAL_REVIEW': 1}
    shared_strong=['bynara.id']

>>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)
  decision=MANUAL_REVIEW -> NOT_ELIGIBLE — риск/promo, ручная проверка, до LLM не доходит

================================================================================
СЦЕНАРИЙ: Только ссылка без контекста (нужно обогащение)
id: S6-link-only-needs-enrichment
================================================================================

>>> ШАГ 0 — prepare (фиксация raw, conversation_key)
  msg-00001 raw_id=s6-1 conv_key=1|-100555|1 reply_to=- sender=a(6001)
  msg-00002 raw_id=s6-2 conv_key=1|-100555|1 reply_to=- sender=b(6002)

>>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)
  msg-00001 len_tokens=3 has_link=True hidden_url_count=0
     strong_entities=['github.com']
     weak_entities=['github']
     domains=['github.com'] model_names=[] error_codes=[]
  msg-00002 len_tokens=3 has_link=True hidden_url_count=0
     strong_entities=['github.com']
     weak_entities=['github']
     domains=['github.com'] model_names=[] error_codes=[]

>>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)
  msg-00001 route=NEEDS_ENRICHMENT marker=link_only_marker risk=LOW reason=link_only_or_link_thin_context;repo_or_docs_domain;link_thin_message_requires_enrichment_first
  msg-00002 route=NEEDS_ENRICHMENT marker=link_only_marker risk=LOW reason=link_only_or_link_thin_context;repo_or_docs_domain;link_thin_message_requires_enrichment_first

>>> ШАГ 3 — conversation reconstruction (reply chain resolve)
  msg-00001 reply_resolved=-
  msg-00002 reply_resolved=-

>>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)
  local-cluster 0: ['msg-00001', 'msg-00002']

>>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)
  merged-cluster 0: size=2 conversations=1 shared=['github.com'] ids=['msg-00001', 'msg-00002']

>>> ШАГ 6 — cluster decision (material eligibility gate)
  decision=LINK_ENRICHMENT_FIRST (сначала обогащение ссылки)
    size=2 independent_sources=2 routes={'NEEDS_ENRICHMENT': 2}
    shared_strong=['github.com']

>>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)
  decision=LINK_ENRICHMENT_FIRST -> NOT_ELIGIBLE — сначала обогащение ссылок, потом пересмотр

================================================================================
СЦЕНАРИЙ: Unverified model/pricing claim (signal, не материал)
id: S7-unverified-model-claim
================================================================================

>>> ШАГ 0 — prepare (фиксация raw, conversation_key)
  msg-00001 raw_id=s7-1 conv_key=1|-100666|1 reply_to=- sender=anon(7001)

>>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)
  msg-00001 len_tokens=13 has_link=False hidden_url_count=0
     strong_entities=['gpt5', 'opus']
     weak_entities=['gpt']
     domains=[] model_names=['gpt5', 'opus'] error_codes=[]

>>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)
  msg-00001 route=SIGNAL_ONLY marker=model_pricing_marker risk=MEDIUM reason=model_pricing_or_availability_claim_requires_source;unverified_model_pricing_or_provider_claim

>>> ШАГ 3 — conversation reconstruction (reply chain resolve)
  msg-00001 reply_resolved=-

>>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)
  local-cluster 0: ['msg-00001']

>>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)
  merged-cluster 0: size=1 conversations=1 shared=['gpt5', 'opus'] ids=['msg-00001']

>>> ШАГ 6 — cluster decision (material eligibility gate)
  decision=REVIEW_SINGLE_SIGNAL (одиночный сигнал на проверку)
    size=1 independent_sources=1 routes={'SIGNAL_ONLY': 1}
    shared_strong=['gpt5', 'opus']

>>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)
  decision=REVIEW_SINGLE_SIGNAL -> NOT_ELIGIBLE — остаётся сигналом/контекстом, до LLM не доходит

================================================================================
СЦЕНАРИЙ: Короткий chatter без сущностей (context only)
id: S8-short-chatter-no-context
================================================================================

>>> ШАГ 0 — prepare (фиксация raw, conversation_key)
  msg-00001 raw_id=s8-1 conv_key=1|-100777|1 reply_to=- sender=anon(8001)
  msg-00002 raw_id=s8-2 conv_key=1|-100777|1 reply_to=- sender=anon2(8002)

>>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)
  msg-00001 len_tokens=1 has_link=False hidden_url_count=0
     strong_entities=[]
     weak_entities=[]
     domains=[] model_names=[] error_codes=[]
  msg-00002 len_tokens=1 has_link=False hidden_url_count=0
     strong_entities=[]
     weak_entities=[]
     domains=[] model_names=[] error_codes=[]

>>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)
  msg-00001 route=CONTEXT_ONLY marker=short_chatter_marker risk=LOW reason=short_or_ack_noise;short_message_without_entities_link_or_reply
  msg-00002 route=CONTEXT_ONLY marker=short_chatter_marker risk=LOW reason=short_or_ack_noise;short_message_without_entities_link_or_reply

>>> ШАГ 3 — conversation reconstruction (reply chain resolve)
  msg-00001 reply_resolved=-
  msg-00002 reply_resolved=-

>>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)

>>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)

>>> ШАГ 6 — cluster decision (material eligibility gate)

>>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)

================================================================================
СЦЕНАРИЙ: How-to / чеклист (technical, single message)
id: S9-howto-checklist
================================================================================

>>> ШАГ 0 — prepare (фиксация raw, conversation_key)
  msg-00001 raw_id=s9-1 conv_key=1|-100888|1 reply_to=- sender=dev(9001)

>>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)
  msg-00001 len_tokens=15 has_link=False hidden_url_count=0
     strong_entities=['openai-compatible']
     weak_entities=['api']
     domains=[] model_names=[] error_codes=[]

>>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)
  msg-00001 route=REVIEW_HIGH_RECALL marker=workflow_howto_marker risk=LOW reason=guide_intent;technical_or_code_terms;resource_or_tool_terms;interview_or_product_design_task;technical_or_resource_candidate_retained_for_context_evidence_review

>>> ШАГ 3 — conversation reconstruction (reply chain resolve)
  msg-00001 reply_resolved=-

>>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)
  local-cluster 0: ['msg-00001']

>>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)
  merged-cluster 0: size=1 conversations=1 shared=['openai-compatible'] ids=['msg-00001']

>>> ШАГ 6 — cluster decision (material eligibility gate)
  decision=REVIEW_SINGLE_SIGNAL (одиночный сигнал на проверку)
    size=1 independent_sources=1 routes={'REVIEW_HIGH_RECALL': 1}
    shared_strong=['openai-compatible']

>>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)
  decision=REVIEW_SINGLE_SIGNAL -> NOT_ELIGIBLE — остаётся сигналом/контекстом, до LLM не доходит

================================================================================
СЦЕНАРИЙ: Слабая пара по generic tech-словам (не материал)
id: S10-weak-pair-generic-keywords
================================================================================

>>> ШАГ 0 — prepare (фиксация raw, conversation_key)
  msg-00001 raw_id=s10-1 conv_key=1|-100999|1 reply_to=- sender=a(10001)
  msg-00002 raw_id=s10-2 conv_key=1|-100999|1 reply_to=- sender=b(10002)

>>> ШАГ 1 — normalize + features (display/normalized/feature text + strong/weak entities)
  msg-00001 len_tokens=3 has_link=False hidden_url_count=0
     strong_entities=[]
     weak_entities=['solid']
     domains=[] model_names=[] error_codes=[]
  msg-00002 len_tokens=2 has_link=False hidden_url_count=0
     strong_entities=[]
     weak_entities=['faang', 'java']
     domains=[] model_names=[] error_codes=[]

>>> ШАГ 2 — route (первичная воронка, не финальный material eligibility)
  msg-00001 route=CONTEXT_ONLY marker=short_chatter_marker risk=LOW reason=no_material_evidence;short_message_without_entities_link_or_reply
  msg-00002 route=CONTEXT_ONLY marker=api_troubleshooting_marker risk=LOW reason=technical_or_code_terms;interview_or_product_design_task;short_message_without_entities_link_or_reply

>>> ШАГ 3 — conversation reconstruction (reply chain resolve)
  msg-00001 reply_resolved=-
  msg-00002 reply_resolved=-

>>> ШАГ 4 — conversation-local clustering (union по reply-chain OR strong-entity; время = context)

>>> ШАГ 5 — cross-conversation merge (только по merge-eligible сущности, generic исключены)

>>> ШАГ 6 — cluster decision (material eligibility gate)

>>> ШАГ 7 — финальный MaterialEligibilityGate (допуск к LLM)
