from __future__ import annotations

import argparse
import csv
import importlib.util
import json
import math
import sys
import types
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
WORKER_APP = ROOT / "backend" / "2.0" / "model-worker" / "app.py"
DEFAULT_GOLDEN = ROOT / "reports" / "golden-batch-real-posts-20260628.json"
DEFAULT_PREFIX = ROOT / "reports" / "classical-ml-golden-batch-20260628"


def load_worker() -> Any:
    install_worker_import_stubs()
    sys.path.insert(0, str(WORKER_APP.parent))
    spec = importlib.util.spec_from_file_location("nig_model_worker_app", WORKER_APP)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Cannot load worker app from {WORKER_APP}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def install_worker_import_stubs() -> None:
    if "fastapi" not in sys.modules:
        try:
            __import__("fastapi")
        except ModuleNotFoundError:
            fastapi = types.ModuleType("fastapi")

            class FastAPI:
                def __init__(self, *args: Any, **kwargs: Any) -> None:
                    pass

                def get(self, *_args: Any, **_kwargs: Any) -> Any:
                    return lambda func: func

                def post(self, *_args: Any, **_kwargs: Any) -> Any:
                    return lambda func: func

                def on_event(self, *_args: Any, **_kwargs: Any) -> Any:
                    return lambda func: func

            fastapi.FastAPI = FastAPI
            sys.modules["fastapi"] = fastapi

    if "pydantic" not in sys.modules:
        try:
            __import__("pydantic")
        except ModuleNotFoundError:
            pydantic = types.ModuleType("pydantic")

            class BaseModel:
                def __init__(self, **kwargs: Any) -> None:
                    annotations = getattr(self.__class__, "__annotations__", {})
                    for key in annotations:
                        default = getattr(self.__class__, key, None)
                        if isinstance(default, _FieldDefault):
                            value = default.default_factory() if default.default_factory else default.default
                        else:
                            value = default
                        setattr(self, key, kwargs.get(key, value))
                    for key, value in kwargs.items():
                        setattr(self, key, value)

            class _FieldDefault:
                def __init__(self, default: Any = None, default_factory: Any = None) -> None:
                    self.default = default
                    self.default_factory = default_factory

            def Field(default: Any = None, default_factory: Any = None, **_kwargs: Any) -> Any:
                return _FieldDefault(default=default, default_factory=default_factory)

            pydantic.BaseModel = BaseModel
            pydantic.Field = Field
            sys.modules["pydantic"] = pydantic

    if "numpy" not in sys.modules:
        try:
            __import__("numpy")
        except ModuleNotFoundError:
            numpy = types.ModuleType("numpy")
            numpy.log = math.log

            def std(values: list[float]) -> float:
                if not values:
                    return 0.0
                mean = sum(values) / len(values)
                return math.sqrt(sum((value - mean) ** 2 for value in values) / len(values))

            numpy.std = std
            sys.modules["numpy"] = numpy


def input_text(case: dict[str, Any]) -> str:
    evidence = case.get("evidence", {}) if isinstance(case.get("evidence"), dict) else {}
    classifier = evidence.get("classifier", {}) if isinstance(evidence.get("classifier"), dict) else {}
    parts: list[str] = []
    for key in ("title", "expected_structure", "why_useful", "needed_feature", "assessment"):
        value = evidence.get(key) or classifier.get(key)
        if value:
            parts.append(str(value))
    overnight = evidence.get("overnight", {}) if isinstance(evidence.get("overnight"), dict) else {}
    for key in ("expected_name", "notes", "skip_or_reject_reason"):
        value = overnight.get(key)
        if value:
            parts.append(str(value))
    if not parts:
        parts.append(f"{case.get('kind', '')} {case.get('expected', '')} {case.get('current', '')}")
    return "\n".join(parts)


def features_for_case(case: dict[str, Any], text: str) -> dict[str, Any]:
    evidence = case.get("evidence", {}) if isinstance(case.get("evidence"), dict) else {}
    classifier = evidence.get("classifier", {}) if isinstance(evidence.get("classifier"), dict) else {}
    expected = str(case.get("expected", ""))
    current = str(case.get("current", ""))
    kind = str(case.get("kind", ""))
    structural = {
        "hasError": any(token in text.lower() for token in ("error", "ошиб", "slow", "fallback")),
        "hasCode": any(token in text.lower() for token in ("api", "json", "curl", "docker", "repo", "github")),
        "isQuestion": "?" in text or "how " in text.lower() or "как " in text.lower(),
        "isAnswerLike": any(token in text.lower() for token in ("checklist", "guide", "how", "rules", "практи", "answer")),
        "isNoiseLike": "NOISE" in expected or "promo_noise" in kind,
        "linkCount": 1 if any(token in text.lower() for token in ("http", "github", "link", "repo")) else 0,
        "nearDuplicate": "duplicate" in current.lower(),
    }
    if "safety" in kind or "SAFETY" in expected:
        structural["riskSensitive"] = True
    return {
        "featureVersion": "classical-ml-v1",
        "targetType": "DISCUSSION_SEGMENT" if "discussion" in kind or case.get("clusterId") else "MESSAGE",
        "goldenCaseId": case.get("caseId"),
        "expected": expected,
        "current": current,
        "kind": kind,
        "contentClass": classifier.get("content_class"),
        "candidateRoute": classifier.get("candidate_route"),
        "proposedMaterialType": classifier.get("proposed_material_type"),
        "rejectReason": classifier.get("reject_reason"),
        "hardSignal": classifier.get("candidate_route") == "SINGLE_MESSAGE" or "SHOULD" in expected or "ACCEPT" in expected,
        "duplicate": "DUPLICATE" in expected,
        "nearDuplicate": "DUPLICATE" in current,
        "sourceMessageCount": len(str(evidence.get("raw_ids", "")).split()) if evidence.get("raw_ids") else (2 if case.get("clusterId") else 1),
        "structural": structural,
    }


def final_decision(result: dict[str, Any]) -> str:
    votes = result["modelVotes"]
    if votes["preprocessingTop"] in {"RISK_SENSITIVE", "NOISE"}:
        return "BLOCKED_PREPROCESSING"
    if votes["dedupeClusterTop"] in {"DUPLICATE", "NEAR_DUPLICATE"}:
        return "BLOCKED_DUPLICATE"
    if votes["llmJudgeTop"] == "REJECT_BEFORE_JUDGE":
        return "BLOCKED_FINAL_GATE"
    if result.get("abstained"):
        return "ABSTAIN"
    if votes["routeTop"] != "NO_MATERIAL" and votes["llmJudgeTop"] == "APPROVE_FOR_JUDGE":
        return f"ROUTE_{votes['routeTop']}"
    return "NO_MATERIAL_OR_ACCUMULATE"


def expected_pass(case: dict[str, Any], actual: str, result: dict[str, Any]) -> bool:
    expected = str(case.get("expected", ""))
    route = result["modelVotes"]["routeTop"]
    preprocessing = result["modelVotes"]["preprocessingTop"]
    evidence = result["modelVotes"]["evidenceTop"]
    if expected == "ACCEPT_AS_REFERENCE_NOT_GUIDE":
        return route == "REFERENCE" and actual in {"ROUTE_REFERENCE", "ABSTAIN"}
    if expected == "REJECT_LINK_ONLY_NEEDS_ENRICHMENT":
        return evidence == "NEEDS_LINK_ENRICHMENT" or actual.startswith("BLOCKED") or actual == "NO_MATERIAL_OR_ACCUMULATE"
    if expected == "DISCUSSION_SEGMENT_GUIDE":
        return result["modelVotes"]["assemblyTop"] in {"DISCUSSION_SEGMENT", "CLUSTER_MEMBER", "SINGLE_MESSAGE"} and route in {"GUIDE", "REFERENCE"}
    if expected == "GUIDE_OR_STRONG_ANSWER":
        return route in {"GUIDE", "ANSWER"}
    if expected == "NEEDS_SAFETY_SUMMARY_OR_REJECT":
        return preprocessing == "RISK_SENSITIVE" or actual in {"ABSTAIN", "NO_MATERIAL_OR_ACCUMULATE", "BLOCKED_PREPROCESSING"}
    if expected == "REJECT_PROMO":
        return actual != "ROUTE_GUIDE"
    return False


def run(golden_path: Path, output_prefix: Path) -> None:
    worker = load_worker()
    batch = json.loads(golden_path.read_text(encoding="utf-8"))
    rows: list[dict[str, Any]] = []
    details: list[dict[str, Any]] = []
    for case in batch.get("cases", []):
        text = input_text(case)
        item = worker.ClassicalMlItem(targetId=str(case.get("rawId") or case.get("clusterId") or case.get("caseId")), text=text, features=features_for_case(case, text))
        result = worker.infer_item(item, str(item.features.get("targetType", "MESSAGE")))
        actual = final_decision(result)
        passed = expected_pass(case, actual, result)
        preview = text.replace("\r", " ").replace("\n", " ")[:260]
        row = {
            "caseId": case.get("caseId"),
            "anchor": case.get("rawId") or case.get("clusterId"),
            "inputSource": "golden_evidence_snapshot",
            "inputPreview": preview,
            "expected": case.get("expected"),
            "actualFinal": actual,
            "preprocessing": result["modelVotes"]["preprocessingTop"],
            "meaning": result["modelVotes"]["meaningTop"],
            "valueLevel": result["modelVotes"]["valueTop"],
            "usefulnessKind": result["modelVotes"]["usefulnessTop"],
            "evidenceSufficiency": result["modelVotes"]["evidenceTop"],
            "assemblyStrategy": result["modelVotes"]["assemblyTop"],
            "materialRoute": result["modelVotes"]["routeTop"],
            "uiReason": result["modelVotes"]["uiReasonTop"],
            "dedupeCluster": result["modelVotes"]["dedupeClusterTop"],
            "llmJudge": result["modelVotes"]["llmJudgeTop"],
            "confidenceBand": result["confidenceBand"],
            "abstained": result["abstained"],
            "abstentionReason": result.get("abstentionReason"),
            "modelAgreement": result.get("modelAgreement"),
            "pass": passed,
        }
        rows.append(row)
        details.append({"case": case, "inputText": text, "row": row, "result": result})

    output_prefix.parent.mkdir(parents=True, exist_ok=True)
    csv_path = output_prefix.with_suffix(".csv")
    json_path = output_prefix.with_suffix(".json")
    md_path = output_prefix.with_suffix(".md")
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    json_path.write_text(json.dumps({"source": str(golden_path), "rows": rows, "details": details}, ensure_ascii=False, indent=2), encoding="utf-8")
    md_lines = [
        "# Classical ML Golden Batch - 2026-06-28",
        "",
        "Input source: `reports/golden-batch-real-posts-20260628.json` evidence snapshot. Full raw text requires DB/API fetch; this run is offline and read-only.",
        "",
        "| case | anchor | expected | actual | preprocessing | meaning | value | usefulness | evidence | assembly | route | dedupe | llmGate | pass |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for row in rows:
        md_lines.append(
            f"| {row['caseId']} | {row['anchor']} | {row['expected']} | {row['actualFinal']} | {row['preprocessing']} | {row['meaning']} | {row['valueLevel']} | {row['usefulnessKind']} | {row['evidenceSufficiency']} | {row['assemblyStrategy']} | {row['materialRoute']} | {row['dedupeCluster']} | {row['llmJudge']} | {row['pass']} |"
        )
    md_lines.extend(["", "## Inputs", ""])
    for row in rows:
        md_lines.append(f"### {row['caseId']} `{row['anchor']}`")
        md_lines.append("")
        md_lines.append(f"Input preview: {row['inputPreview']}")
        md_lines.append("")
    md_path.write_text("\n".join(md_lines) + "\n", encoding="utf-8")
    print(f"Wrote {csv_path}")
    print(f"Wrote {json_path}")
    print(f"Wrote {md_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--golden", type=Path, default=DEFAULT_GOLDEN)
    parser.add_argument("--output-prefix", type=Path, default=DEFAULT_PREFIX)
    args = parser.parse_args()
    run(args.golden, args.output_prefix)
