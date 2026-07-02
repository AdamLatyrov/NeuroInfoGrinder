# Material Lab v2 Automated Verification

Message cases: `37/37` passed
Cluster cases: `15/15` passed

## Message Case Summary

| id | category | expected | actual | ok |
|---|---|---|---|---|
| `tc-rules-01` | rules_onboarding | `REJECT_SAFE` | `REJECT_SAFE` | PASS |
| `tc-rules-02` | rules_onboarding | `REJECT_SAFE` | `REJECT_SAFE` | PASS |
| `tc-mod-01` | moderation_bot | `REJECT_SAFE` | `REJECT_SAFE` | PASS |
| `tc-risk-01` | risk_referral | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `tc-risk-02` | abuse_fraud | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `tc-risk-03` | promo_airdrop | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `tc-risk-04` | promo_airdrop | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `tc-job-01` | job_post | `AGGREGATE_ONLY` | `AGGREGATE_ONLY` | PASS |
| `tc-event-01` | event_announcement | `AGGREGATE_ONLY` | `AGGREGATE_ONLY` | PASS |
| `tc-link-01` | link_only | `NEEDS_ENRICHMENT` | `NEEDS_ENRICHMENT` | PASS |
| `tc-link-02` | link_thin | `NEEDS_ENRICHMENT` | `NEEDS_ENRICHMENT` | PASS |
| `tc-chatter-01` | short_chatter | `CONTEXT_ONLY` | `CONTEXT_ONLY` | PASS |
| `tc-chatter-02` | short_chatter | `CONTEXT_ONLY` | `CONTEXT_ONLY` | PASS |
| `tc-chatter-03` | short_chatter | `CONTEXT_ONLY` | `CONTEXT_ONLY` | PASS |
| `tc-claim-01` | unverified_model_claim | `SIGNAL_ONLY` | `SIGNAL_ONLY` | PASS |
| `tc-claim-02` | unverified_model_claim | `SIGNAL_ONLY` | `SIGNAL_ONLY` | PASS |
| `tc-tech-01` | technical_signal | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-tech-02` | outage_status | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-tech-03` | howto | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-tech-04` | technical_signal | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-tech-05` | technical_signal | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-tool-01` | tool_resource | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-hidden-01` | hidden_referral | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `tc-medium-01` | weak_signal | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-medium-02` | weak_signal | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-guide-01` | guide_sim_cards | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-guide-02` | guide_task_list | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-job-hashtag` | job_hashtag | `AGGREGATE_ONLY` | `AGGREGATE_ONLY` | PASS |
| `tc-free-access-abuse` | free_access_abuse | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `tc-rules-work-not-chat` | rules_work_not_chat | `REVIEW_HIGH_RECALL` | `REVIEW_HIGH_RECALL` | PASS |
| `tc-digest-01` | article_digest | `CONTEXT_ONLY` | `CONTEXT_ONLY` | PASS |
| `tc-promo-freetokens-02` | promo_free_tokens | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `tc-abuse-tempemail-01` | temp_email_credit_farming | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `tc-fiction-01` | english_fiction_roleplay | `CONTEXT_ONLY` | `CONTEXT_ONLY` | PASS |
| `tc-credit-farming-01` | free_credit_farming | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `tc-llm-refusal-01` | llm_safety_refusal | `CONTEXT_ONLY` | `CONTEXT_ONLY` | PASS |
| `tc-test-artifact-01` | test_artifact | `REJECT_SAFE` | `REJECT_SAFE` | PASS |

## Cluster Case Summary

| group | category | expected | actual | ok |
|---|---|---|---|---|
| `ct-r-api` | multi_source_technical | `EVIDENCE_GROUP_REVIEW` | `EVIDENCE_GROUP_REVIEW` | PASS |
| `ct-droid-single-source` | single_source_signal_group | `SIGNAL_GROUP` | `SIGNAL_GROUP` | PASS |
| `ct-senpi-promo` | promo_blocked | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `ct-rules` | rules_not_material | `not EVIDENCE_GROUP_REVIEW` | `NO_CLUSTER` | PASS |
| `ct-weak-pair` | weak_pair_not_material | `not EVIDENCE_GROUP_REVIEW` | `NO_CLUSTER` | PASS |
| `ct-link-enrich` | link_enrichment | `LINK_ENRICHMENT_FIRST` | `LINK_ENRICHMENT_FIRST` | PASS |
| `ct-single-guide` | single_message_non_tech_guide_signal | `REVIEW_SINGLE_SIGNAL` | `REVIEW_SINGLE_SIGNAL` | PASS |
| `ct-single-tech-guide` | single_message_tech_guide | `SINGLE_MESSAGE_GUIDE_CANDIDATE` | `SINGLE_MESSAGE_GUIDE_CANDIDATE` | PASS |
| `ct-weak-single` | weak_single_not_guide | `REVIEW_SINGLE_SIGNAL` | `REVIEW_SINGLE_SIGNAL` | PASS |
| `ct-shopping-guide` | single_message_shopping_guide_not_material | `REVIEW_SINGLE_SIGNAL` | `REVIEW_SINGLE_SIGNAL` | PASS |
| `ct-omniroute-promo` | free_token_promo_blocked | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `ct-article-digest` | article_digest_not_material | `not SINGLE_MESSAGE_GUIDE_CANDIDATE` | `NO_CLUSTER` | PASS |
| `ct-cometapi-abuse` | temp_email_credit_farming_blocked | `MANUAL_REVIEW` | `MANUAL_REVIEW` | PASS |
| `ct-buildinpublic-marketing` | marketing_blog_not_material | `REVIEW_SINGLE_SIGNAL` | `REVIEW_SINGLE_SIGNAL` | PASS |
| `ct-fiction-roleplay` | english_fiction_not_material | `not SINGLE_MESSAGE_GUIDE_CANDIDATE` | `NO_CLUSTER` | PASS |
