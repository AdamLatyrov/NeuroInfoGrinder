#!/usr/bin/env python3
"""Compare offline message classification and clustering algorithms.

This script is intentionally local/offline. It reads exported messages and writes
reports only; it does not call production APIs, databases, providers, or model
workers.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from message_lab_core import (
    Classification,
    MessageRecord,
    add_common_args,
    classify_text,
    cluster_messages,
    jaccard,
    load_messages,
    markdown_table,
    semantic_pool,
    similarity,
    summarize_cluster,
    tokenize,
    utc_now_iso,
    weighted_tokens,
    write_json,
)


REVIEW_DECISIONS = {"REVIEW_CLUSTER_FOR_MATERIAL", "REVIEW_SINGLE_SIGNAL", "LINK_ENRICHMENT_FIRST"}
GOOD_REVIEW_CLASSES = {"TECH_SIGNAL", "OUTAGE_STATUS", "DIGEST_NEWS", "RESOURCE_LINK"}
LINK_CLASSES = {"LINK_SHARE", "RESOURCE_LINK", "SOCIAL_MEDIA_LINK", "INTERNAL_TELEGRAM_LINK"}
BLOCKED_CLASSES = {"RULES_ONBOARDING", "MODERATION_BOT_EVENT", "ABUSE_OR_CIRCUMVENTION"}


@dataclass(frozen=True)
class AlgorithmSpec:
    name: str
    description: str
    runner: Callable[[list[MessageRecord], dict[str, Classification], argparse.Namespace], list[dict]]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compare offline message lab algorithms on the same exported batch")
    parser.add_argument("--input", required=True, type=Path, help="Input JSON/JSONL/CSV/TXT file")
    parser.add_argument("--limit", type=int, default=0, help="Limit records after loading; 0 means all")
    parser.add_argument("--algorithms", default="all", help="Comma-separated algorithm names, or all")
    parser.add_argument("--graph-threshold", type=float, default=0.34, help="Similarity threshold for graph algorithms")
    parser.add_argument("--pool-threshold", type=float, default=0.31, help="Similarity threshold for pool graph")
    parser.add_argument("--window-threshold", type=float, default=0.29, help="Similarity threshold for window graph")
    parser.add_argument("--dbscan-eps", type=float, default=0.27, help="Token DBSCAN similarity threshold")
    parser.add_argument("--dbscan-min-samples", type=int, default=2, help="Token DBSCAN min samples including the point")
    parser.add_argument("--max-pairwise", type=int, default=700, help="Max rows per pairwise bucket")
    parser.add_argument("--window-size", type=int, default=90, help="Max previous rows checked per temporal bucket")
    add_common_args(parser)
    return parser.parse_args()


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    args = parse_args()
    records = load_messages(args.input)
    if args.limit and args.limit > 0:
        records = records[: args.limit]

    classifications = {record.id: classify_text(record.text, chat=record.chat, topic=record.topic) for record in records}
    specs = algorithm_specs()
    selected = select_algorithms(specs, args.algorithms)

    runs = []
    for spec in selected:
        clusters = spec.runner(records, classifications, args)
        runs.append(summarize_algorithm_run(spec, records, classifications, clusters))

    comparison = sorted((run["metrics"] for run in runs), key=lambda item: (-item["recommendationScore"], item["reviewClusterCount"]))
    payload = {
        "generatedUtc": utc_now_iso(),
        "mode": "offline-message-lab-algorithm-comparison",
        "input": str(args.input),
        "count": len(records),
        "classCounts": dict(Counter(classification.primary_class for classification in classifications.values()).most_common()),
        "routeCounts": dict(Counter(classification.material_route for classification in classifications.values()).most_common()),
        "poolCounts": dict(Counter(semantic_pool(record, classifications[record.id]) for record in records).most_common()),
        "comparison": comparison,
        "runs": runs,
        "recommendation": recommendation_text(comparison),
    }

    print(json.dumps({k: v for k, v in payload.items() if k != "runs"}, ensure_ascii=False, indent=2))
    if args.output_json:
        write_json(args.output_json, payload)
    if args.output_md:
        args.output_md.parent.mkdir(parents=True, exist_ok=True)
        args.output_md.write_text(build_markdown(payload), encoding="utf-8")
    return 0


def algorithm_specs() -> dict[str, AlgorithmSpec]:
    specs = [
        AlgorithmSpec(
            "baseline_graph",
            "Existing similarity graph over class/chat/topic buckets; connected components after pairwise similarity.",
            run_baseline_graph,
        ),
        AlgorithmSpec(
            "pool_graph",
            "Semantic-pool first, then similarity graph. Prevents noise/risk/link/template rows from mixing with material candidates.",
            run_pool_graph,
        ),
        AlgorithmSpec(
            "temporal_pool_graph",
            "Semantic-pool graph that only checks nearby rows in each chat/topic stream. Better for Telegram bursts and issue threads.",
            run_temporal_pool_graph,
        ),
        AlgorithmSpec(
            "token_dbscan",
            "Dependency-free DBSCAN-like clustering over weighted tokens with an inverted index and explicit noise handling.",
            run_token_dbscan,
        ),
        AlgorithmSpec(
            "canopy_signatures",
            "Fast deterministic canopy grouping by pool/class/domain/topic/key terms. Useful as a cheap pre-cluster stage.",
            run_canopy_signatures,
        ),
    ]
    return {spec.name: spec for spec in specs}


def select_algorithms(specs: dict[str, AlgorithmSpec], selected: str) -> list[AlgorithmSpec]:
    if selected.strip().lower() == "all":
        return list(specs.values())
    names = [name.strip() for name in selected.split(",") if name.strip()]
    missing = [name for name in names if name not in specs]
    if missing:
        raise ValueError(f"Unknown algorithms: {', '.join(missing)}. Available: {', '.join(specs)}")
    return [specs[name] for name in names]


def run_baseline_graph(records: list[MessageRecord], classifications: dict[str, Classification], args: argparse.Namespace) -> list[dict]:
    return relabel_clusters(cluster_messages(records, classifications, threshold=args.graph_threshold, max_pairwise=args.max_pairwise), "baseline")


def run_pool_graph(records: list[MessageRecord], classifications: dict[str, Classification], args: argparse.Namespace) -> list[dict]:
    return pairwise_union_clusters(
        records,
        classifications,
        threshold=args.pool_threshold,
        max_pairwise=args.max_pairwise,
        bucket_fn=pool_bucket,
        prefix="pool",
        pairwise_pools={"review_candidates", "links", "risk_links", "context", "manual_risk"},
    )


def run_temporal_pool_graph(records: list[MessageRecord], classifications: dict[str, Classification], args: argparse.Namespace) -> list[dict]:
    parent = {record.id: record.id for record in records}

    def find(x: str) -> str:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a: str, b: str) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    buckets: dict[str, list[MessageRecord]] = defaultdict(list)
    for record in records:
        classification = classifications[record.id]
        if semantic_pool(record, classification) in {"noise", "blocked_templates"}:
            buckets[f"singleton|{record.id}"].append(record)
        else:
            buckets[stream_bucket(record, classification)].append(record)

    for bucket_records in buckets.values():
        for index, right in enumerate(bucket_records):
            start = max(0, index - args.window_size)
            for left in bucket_records[start:index]:
                score = similarity(left, right, classifications[left.id], classifications[right.id])
                if score >= args.window_threshold:
                    union(left.id, right.id)

    grouped: dict[str, list[MessageRecord]] = defaultdict(list)
    for record in records:
        grouped[find(record.id)].append(record)
    clusters = [summarize_cluster(index, rows, classifications) for index, rows in enumerate(grouped.values(), start=1)]
    return relabel_clusters(sorted(clusters, key=lambda item: (-item["qualityScore"], -item["size"], item["id"])), "temporal")


def run_token_dbscan(records: list[MessageRecord], classifications: dict[str, Classification], args: argparse.Namespace) -> list[dict]:
    candidate_records = [record for record in records if semantic_pool(record, classifications[record.id]) not in {"noise", "blocked_templates"}]
    non_candidate_ids = {record.id for record in records} - {record.id for record in candidate_records}
    tokens_by_id = {record.id: weighted_tokens(record, classifications[record.id]) for record in candidate_records}
    inverted: dict[str, set[str]] = defaultdict(set)
    for record in candidate_records:
        for token in important_tokens(tokens_by_id[record.id]):
            inverted[token].add(record.id)

    by_id = {record.id: record for record in candidate_records}
    neighbor_cache: dict[str, list[str]] = {}

    def neighbors(record_id: str) -> list[str]:
        cached = neighbor_cache.get(record_id)
        if cached is not None:
            return cached
        candidate_ids: set[str] = set()
        for token in important_tokens(tokens_by_id[record_id]):
            candidate_ids.update(inverted[token])
        result = []
        record = by_id[record_id]
        c_record = classifications[record_id]
        pool = semantic_pool(record, c_record)
        for other_id in candidate_ids:
            other = by_id[other_id]
            c_other = classifications[other_id]
            if semantic_pool(other, c_other) != pool:
                continue
            score = 0.72 * jaccard(tokens_by_id[record_id], tokens_by_id[other_id]) + 0.28 * similarity(record, other, c_record, c_other)
            if score >= args.dbscan_eps:
                result.append(other_id)
        neighbor_cache[record_id] = result
        return result

    cluster_labels: dict[str, int] = {}
    visited: set[str] = set()
    cluster_id = 0
    for record in candidate_records:
        if record.id in visited:
            continue
        visited.add(record.id)
        record_neighbors = neighbors(record.id)
        if len(record_neighbors) < args.dbscan_min_samples:
            continue
        cluster_id += 1
        cluster_labels[record.id] = cluster_id
        seeds = list(record_neighbors)
        seed_index = 0
        while seed_index < len(seeds):
            next_id = seeds[seed_index]
            seed_index += 1
            if next_id not in visited:
                visited.add(next_id)
                next_neighbors = neighbors(next_id)
                if len(next_neighbors) >= args.dbscan_min_samples:
                    for item in next_neighbors:
                        if item not in seeds:
                            seeds.append(item)
            cluster_labels.setdefault(next_id, cluster_id)

    grouped: dict[str, list[MessageRecord]] = defaultdict(list)
    singleton_index = 0
    all_by_id = {record.id: record for record in records}
    for record in candidate_records:
        label = cluster_labels.get(record.id)
        if label is None:
            singleton_index += 1
            grouped[f"noise-{singleton_index}"].append(record)
        else:
            grouped[f"cluster-{label}"].append(record)
    for record_id in non_candidate_ids:
        singleton_index += 1
        grouped[f"noncandidate-{singleton_index}"].append(all_by_id[record_id])

    clusters = [summarize_cluster(index, rows, classifications) for index, rows in enumerate(grouped.values(), start=1)]
    return relabel_clusters(sorted(clusters, key=lambda item: (-item["qualityScore"], -item["size"], item["id"])), "dbscan")


def run_canopy_signatures(records: list[MessageRecord], classifications: dict[str, Classification], args: argparse.Namespace) -> list[dict]:
    grouped: dict[str, list[MessageRecord]] = defaultdict(list)
    for record in records:
        grouped[signature_key(record, classifications[record.id])].append(record)
    clusters = [summarize_cluster(index, rows, classifications) for index, rows in enumerate(grouped.values(), start=1)]
    return relabel_clusters(sorted(clusters, key=lambda item: (-item["qualityScore"], -item["size"], item["id"])), "canopy")


def pairwise_union_clusters(
    records: list[MessageRecord],
    classifications: dict[str, Classification],
    *,
    threshold: float,
    max_pairwise: int,
    bucket_fn: Callable[[MessageRecord, Classification], str],
    prefix: str,
    pairwise_pools: set[str] | None = None,
) -> list[dict]:
    parent = {record.id: record.id for record in records}

    def find(x: str) -> str:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a: str, b: str) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    buckets: dict[str, list[MessageRecord]] = defaultdict(list)
    for record in records:
        classification = classifications[record.id]
        if pairwise_pools is not None and semantic_pool(record, classification) not in pairwise_pools:
            buckets[f"singleton|{record.id}"].append(record)
        else:
            buckets[bucket_fn(record, classification)].append(record)

    for bucket_records in buckets.values():
        sample = bucket_records[:max_pairwise]
        for index, left in enumerate(sample):
            for right in sample[index + 1 :]:
                score = similarity(left, right, classifications[left.id], classifications[right.id])
                if score >= threshold:
                    union(left.id, right.id)

    grouped: dict[str, list[MessageRecord]] = defaultdict(list)
    for record in records:
        grouped[find(record.id)].append(record)
    clusters = [summarize_cluster(index, rows, classifications) for index, rows in enumerate(grouped.values(), start=1)]
    return relabel_clusters(sorted(clusters, key=lambda item: (-item["qualityScore"], -item["size"], item["id"])), prefix)


def pool_bucket(record: MessageRecord, classification: Classification) -> str:
    pool = semantic_pool(record, classification)
    if pool in {"links", "risk_links"}:
        domains = classification.features.get("domains") or []
        domain = domains[0] if domains else "no-domain"
        return f"{pool}|{classification.primary_class}|{domain}"
    if pool == "review_candidates":
        return f"{pool}|{record.chat.lower()}|{topic_or_signature(record)}|{classification.primary_class}"
    return f"{pool}|{classification.primary_class}|{record.chat.lower()}|{record.topic.lower()}"


def stream_bucket(record: MessageRecord, classification: Classification) -> str:
    pool = semantic_pool(record, classification)
    if pool in {"links", "risk_links"}:
        domains = classification.features.get("domains") or []
        domain = domains[0] if domains else "no-domain"
        return f"{pool}|{record.chat.lower()}|{topic_or_signature(record)}|{classification.primary_class}|{domain}"
    if pool == "review_candidates":
        return f"{pool}|{record.chat.lower()}|{topic_or_signature(record)}|{classification.primary_class}"
    return f"{pool}|{record.chat.lower()}|{record.topic.lower()}|{classification.primary_class}"


def topic_or_signature(record: MessageRecord) -> str:
    topic = (record.topic or "main").lower().strip()
    if topic and topic not in {"main", "unknown", "unknown-topic"}:
        return topic
    return f"main:{'-'.join(top_signature_terms(review_signature_tokens(record), limit=2))}"


def review_signature_tokens(record: MessageRecord) -> list[str]:
    generic = {"api", "model", "модель", "модели", "токен", "токены", "claude", "gpt", "openai", "это", "если"}
    return [token for token in tokenize(record.text) if len(token) >= 4 and token not in generic]


def signature_key(record: MessageRecord, classification: Classification) -> str:
    pool = semantic_pool(record, classification)
    domains = classification.features.get("domains") or []
    if pool in {"links", "risk_links"}:
        return f"{pool}|{classification.primary_class}|{domains[0] if domains else 'no-domain'}"
    tokens = [token for token in tokenize(record.text) if len(token) >= 4]
    key_terms = top_signature_terms(tokens, limit=3)
    topic = (record.topic or "main").lower()
    chat = (record.chat or "unknown").lower()
    if pool == "review_candidates":
        return f"{pool}|{classification.primary_class}|{topic}|{'-'.join(key_terms)}"
    if pool == "context":
        return f"{pool}|{classification.primary_class}|{chat}|{topic}|{'-'.join(key_terms[:2])}"
    return f"{pool}|{classification.primary_class}|{chat}|{topic}"


def top_signature_terms(tokens: list[str], limit: int) -> list[str]:
    ranked = sorted(set(tokens), key=lambda token: (-len(token), token))
    return ranked[:limit] or ["empty"]


def important_tokens(tokens: set[str]) -> list[str]:
    ranked = sorted((token for token in tokens if len(token) >= 4), key=lambda token: (-len(token), token))
    return ranked[:12]


def relabel_clusters(clusters: list[dict], prefix: str) -> list[dict]:
    for index, cluster in enumerate(clusters, start=1):
        cluster["id"] = f"{prefix}-cluster-{index}"
    return clusters


def summarize_algorithm_run(
    spec: AlgorithmSpec,
    records: list[MessageRecord],
    classifications: dict[str, Classification],
    clusters: list[dict],
) -> dict:
    metrics = algorithm_metrics(spec.name, records, classifications, clusters)
    return {
        "algorithm": spec.name,
        "description": spec.description,
        "metrics": metrics,
        "topClusters": clusters[:60],
        "reviewClusters": [cluster for cluster in clusters if cluster["decision"] in REVIEW_DECISIONS][:80],
    }


def algorithm_metrics(
    name: str,
    records: list[MessageRecord],
    classifications: dict[str, Classification],
    clusters: list[dict],
) -> dict:
    review_clusters = [cluster for cluster in clusters if cluster["decision"] in REVIEW_DECISIONS]
    review_records = [record for record in records if classifications[record.id].material_route in {"REVIEW_SIGNAL_OR_DISCUSSION", "NEEDS_LINK_ENRICHMENT", "SIGNAL_ONLY_RISK_REVIEW"}]
    useful_review_records = [record for record in records if classifications[record.id].primary_class in GOOD_REVIEW_CLASSES]
    clustered_review_ids = {
        sample["id"]
        for cluster in review_clusters
        if cluster["size"] >= 2
        for sample in cluster.get("samples", [])
        if sample.get("route") in {"REVIEW_SIGNAL_OR_DISCUSSION", "NEEDS_LINK_ENRICHMENT"}
    }
    useful_clustered_ids = {
        sample["id"]
        for cluster in review_clusters
        if cluster["size"] >= 2
        for sample in cluster.get("samples", [])
        if sample.get("class") in GOOD_REVIEW_CLASSES
    }
    singleton_review = sum(1 for cluster in review_clusters if cluster["size"] == 1)
    multi_review = len(review_clusters) - singleton_review
    pure_review = sum(1 for cluster in review_clusters if dominant_share(cluster.get("classes", {})) >= 0.8)
    blocked_review_leaks = sum(1 for cluster in review_clusters if any(name in BLOCKED_CLASSES for name in cluster.get("classes", {})))
    oversized_review = sum(1 for cluster in review_clusters if cluster["size"] > 50)
    top_quality = sum(cluster["qualityScore"] for cluster in review_clusters[:20])
    useful_grouping_rate = len(useful_clustered_ids) / max(1, len(useful_review_records))
    review_grouping_rate = len(clustered_review_ids) / max(1, len(review_records))
    purity_rate = pure_review / max(1, len(review_clusters))
    singleton_penalty = singleton_review / max(1, len(review_clusters))
    oversize_penalty = oversized_review * 0.1
    leak_penalty = blocked_review_leaks * 0.2
    score = (useful_grouping_rate * 3.0) + (review_grouping_rate * 1.5) + (purity_rate * 1.2) + math.log1p(max(0, top_quality)) * 0.25 - singleton_penalty - oversize_penalty - leak_penalty
    return {
        "algorithm": name,
        "clusterCount": len(clusters),
        "reviewClusterCount": len(review_clusters),
        "multiMessageReviewClusters": multi_review,
        "singletonReviewClusters": singleton_review,
        "pureReviewClusters": pure_review,
        "blockedReviewLeaks": blocked_review_leaks,
        "oversizedReviewClusters": oversized_review,
        "top20ReviewQualityScore": round(top_quality, 3),
        "usefulGroupingRate": round(useful_grouping_rate, 3),
        "reviewGroupingRate": round(review_grouping_rate, 3),
        "reviewPurityRate": round(purity_rate, 3),
        "recommendationScore": round(score, 3),
    }


def dominant_share(counts: dict[str, int]) -> float:
    total = sum(counts.values())
    if total <= 0:
        return 0.0
    return max(counts.values()) / total


def recommendation_text(comparison: list[dict]) -> str:
    if not comparison:
        return "No algorithms were run."
    best = comparison[0]
    return (
        f"Start with `{best['algorithm']}` for the next calibration round. "
        f"It has the highest recommendationScore={best['recommendationScore']}, "
        f"usefulGroupingRate={best['usefulGroupingRate']}, and "
        f"reviewPurityRate={best['reviewPurityRate']}. Keep the runner offline until Adam accepts sample quality."
    )


def build_markdown(payload: dict) -> str:
    lines: list[str] = []
    lines.append("# Offline Message Lab Algorithm Comparison")
    lines.append("")
    lines.append(f"- Generated UTC: `{payload['generatedUtc']}`")
    lines.append(f"- Input: `{payload['input']}`")
    lines.append(f"- Messages: `{payload['count']}`")
    lines.append(f"- Recommendation: {payload['recommendation']}")
    lines.append("")
    lines.append("## Experiment Contract")
    lines.append("")
    lines.append("- Hypothesis: semantic-pool classification plus algorithm comparison will produce cleaner review clusters than one global similarity graph.")
    lines.append("- Success: top review clusters are coherent, blocked/noise classes do not leak into review queues, and useful candidate messages are grouped instead of mostly singletons.")
    lines.append("- Failure: algorithms over-merge unrelated topics, create many singleton review clusters, or surface moderation/rules/noise as material candidates.")
    lines.append("- Scope: offline exported messages only; no production writes, provider calls, model-worker calls, deploy, reprocess, or backfill.")
    lines.append("")
    lines.append("## Class Counts")
    lines.append("")
    lines.append(markdown_table([[key, value] for key, value in payload["classCounts"].items()], ["Class", "Count"]))
    lines.append("")
    lines.append("## Pool Counts")
    lines.append("")
    lines.append(markdown_table([[key, value] for key, value in payload["poolCounts"].items()], ["Pool", "Count"]))
    lines.append("")
    lines.append("## Algorithm Comparison")
    lines.append("")
    rows = [
        [
            item["algorithm"],
            item["recommendationScore"],
            item["clusterCount"],
            item["reviewClusterCount"],
            item["multiMessageReviewClusters"],
            item["singletonReviewClusters"],
            item["usefulGroupingRate"],
            item["reviewPurityRate"],
            item["blockedReviewLeaks"],
            item["oversizedReviewClusters"],
        ]
        for item in payload["comparison"]
    ]
    lines.append(
        markdown_table(
            rows,
            [
                "Algorithm",
                "Score",
                "Clusters",
                "Review",
                "Multi Review",
                "Singleton Review",
                "Useful Grouping",
                "Review Purity",
                "Blocked Leaks",
                "Oversized",
            ],
        )
    )
    lines.append("")
    for run in payload["runs"]:
        metrics = run["metrics"]
        lines.append(f"## {run['algorithm']}")
        lines.append("")
        lines.append(run["description"])
        lines.append("")
        lines.append(
            markdown_table(
                [[key, value] for key, value in metrics.items() if key != "algorithm"],
                ["Metric", "Value"],
            )
        )
        lines.append("")
        lines.append("### Top Review Clusters")
        lines.append("")
        review_rows = []
        for cluster in run["reviewClusters"][:12]:
            review_rows.append(
                [
                    cluster["id"],
                    cluster["size"],
                    cluster["decision"],
                    cluster["qualityScore"],
                    ", ".join(cluster["classes"].keys()),
                    ", ".join(cluster["sourceTopics"].keys()),
                    ", ".join(cluster["sampleMessageIds"][:8]),
                ]
            )
        lines.append(markdown_table(review_rows, ["Cluster", "Size", "Decision", "Score", "Classes", "Topics", "Sample IDs"]))
        lines.append("")
        for cluster in run["reviewClusters"][:6]:
            lines.append(f"#### {cluster['id']} · {cluster['decision']} · size {cluster['size']} · score {cluster['qualityScore']}")
            lines.append("")
            rows = [
                [sample["id"], sample["chat"], sample["topic"], sample["class"], sample["route"], sample["preview"]]
                for sample in cluster.get("samples", [])[:10]
            ]
            lines.append(markdown_table(rows, ["ID", "Chat", "Topic", "Class", "Route", "Preview"]))
            lines.append("")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    raise SystemExit(main())
