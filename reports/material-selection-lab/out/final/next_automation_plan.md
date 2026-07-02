# Next Automation Plan

1. Finish marker, cluster, and gold labeling.
2. Tune deterministic routing until important false negatives in `REJECT_SAFE` are zero on the labeled sample.
3. Tune evidence grouping until duplicate collapse and cluster quality are acceptable by manual review.
4. Port only validated hard routes, evidence sufficiency rules, and pre-LLM admission logging to backend.
5. Do not port exploratory cluster names, aggressive normalization, optional algorithms, or generation/prompt changes until selection quality is proven.
