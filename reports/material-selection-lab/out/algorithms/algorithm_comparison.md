# Algorithm Comparison

Algorithms help grouping only; material-worthiness is decided later by labels and evidence sufficiency.

## tfidf_cosine_baseline

Threshold: `0.34`
Clusters: `405`; review clusters: `190`; multi-review: `27`

## bm25_token_overlap_baseline

Threshold: `0.3`
Clusters: `358`; review clusters: `174`; multi-review: `33`

## simhash_near_duplicate

Threshold: `0.46`
Clusters: `540`; review clusters: `223`; multi-review: `17`

## entity_overlap

Threshold: `0.24`
Clusters: `343`; review clusters: `171`; multi-review: `34`

## time_thread_entity

Threshold: `0.32`
Clusters: `373`; review clusters: `178`; multi-review: `29`

## Ranking heuristic

- `entity_overlap`: multi-review `34`, singleton-review `137`
- `bm25_token_overlap_baseline`: multi-review `33`, singleton-review `141`
- `time_thread_entity`: multi-review `29`, singleton-review `149`
- `tfidf_cosine_baseline`: multi-review `27`, singleton-review `163`
- `simhash_near_duplicate`: multi-review `17`, singleton-review `206`