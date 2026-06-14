-- V18: Disable the default linear classifier until it is properly recalibrated.
-- The current heuristic linear weights overvalue generic AI chatter and produce
-- too many false positives. LLM classification is cheap enough with modern
-- low-cost models and gives materially better precision for this product.

UPDATE classifiers
SET status = 'DISABLED',
    updated_at = CURRENT_TIMESTAMP
WHERE type = 'LINEAR_MODEL'
  AND status = 'ACTIVE';
