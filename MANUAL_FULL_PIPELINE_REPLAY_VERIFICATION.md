# Manual Full Pipeline Replay Verification

1. Start local PostgreSQL on `127.0.0.1:5433` and verify the database `neuroinfogrinder2_dev` exists.
2. Set backend DB env to `jdbc:postgresql://127.0.0.1:5433/neuroinfogrinder2_dev`; do not use port `5432`.
3. Start the model worker from `backend/2.0/model-worker` with `uvicorn app:app --host 127.0.0.1 --port 8095`.
4. Check worker health: `GET http://127.0.0.1:8095/health`.
5. Confirm classifier is `BOOTSTRAP_BERT_CLASSIFIER` unless a fine-tuned local model is configured.
6. Confirm embeddings model is `BAAI/bge-m3` and health reports embeddings `OK`.
7. Start backend 2.0 from `backend/2.0`.
8. Import dataset with `POST /api/v2/datasets/import` using `reports/latest-500-messages-20260622.jsonl`.
9. Re-run import and confirm dataset message count does not duplicate.
10. Run dry-run plan with `POST /api/v2/replay-runs/plan` for 50 messages.
11. Confirm dry-run reports worker health, BERT configured, BGE configured, provider configured/missing, estimated embeddings, clusters, LLM calls, warnings.
12. Run replay with `POST /api/v2/replay-runs` for 50 messages.
13. Check `GET /api/v2/replay-runs/{id}/stages` shows every stage and real statuses.
14. Check BERT results in `message_classifications` and `GET /api/v2/replay-runs/{id}/messages`.
15. Check BGE embeddings in `message_embeddings`; count must be less than total messages for noisy datasets.
16. Check dedupe data in `dedupe_groups` and `dedupe_group_members`.
17. Check semantic neighbors in `semantic_neighbors`.
18. Check microclusters in `microclusters` and `microcluster_members`.
19. Check macroclusters in `macroclusters` and `macrocluster_members`.
20. Check discovered topics in `discovered_topics`.
21. Check cluster scores in `cluster_scores`.
22. Check labeling queue in `labeling_items`.
23. Submit a labeling event through `POST /api/v2/labeling/items/{id}/events`.
24. Export training examples with `POST /api/v2/training-examples/export`.
25. Set `MODELHUB_API_KEY` locally only when LLM stages should run; never paste it into UI or logs.
26. Run a replay with provider configured and confirm `provider_calls` is far smaller than message count.
27. Confirm each generated item has at most two automatic calls: `LLM_CLUSTER_JUDGE_AND_ROUTING` and `KNOWLEDGE_GENERATION`.
28. Confirm manual review routes are present but not automatically executed.
29. Export result JSON with `GET /api/v2/replay-runs/{id}/export-json`.
30. Check `backend/2.0/runs/replay-{runId}/result.json` exists and includes rules, BERT, BGE, clusters, topics, LLM avoidance, provider calls, knowledge, labeling, training examples, message results, and errors.
31. Check SQL views: `v_replay_run_summary`, `v_replay_stage_summary`, `v_classifier_summary_by_run`, `v_embedding_summary_by_run`, `v_cluster_summary_by_run`, `v_topic_summary_by_run`, `v_labeling_summary_by_run`, `v_provider_costs_by_run`, `v_llm_avoidance_by_run`, `v_training_examples_summary`.
32. Start frontend from `frontend`.
33. Open Pipeline page and verify Datasets, Replay Runs, Run Detail, Stages, Metrics, Messages, Clusters, Knowledge Items, JSON Export, Labeling tabs show backend data.
34. Run dry-run plan from UI.
35. Run replay from UI.
36. Confirm UI shows `MODEL_WORKER_DOWN` if worker is stopped.
37. Confirm UI shows `MISSING_API_KEY`/`PROVIDER_NOT_CONFIGURED` if `MODELHUB_API_KEY` is absent.
38. Open AI page and verify provider is `modelhub`, base URL is `https://modelhub.my/v1`, only `MODELHUB_API_KEY` ref is displayed, and no key value is shown.
39. Open AI Health section and verify BERT/BGE worker status.
40. Open Clusters tab and verify cluster members/reasons are visible via backend rows/export.
41. Open Knowledge Items tab and verify generated items and sources.
42. Open Labeling tab and verify queue items exist.
43. Run backend tests.
44. Run frontend build.
