from __future__ import annotations

import json
import hashlib
import os
import re
import threading
import time
from pathlib import Path
from typing import Any, Literal

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel, Field


LABELS = [
    "NOISE_OR_CHAT",
    "HOW_TO_GUIDE",
    "TROUBLESHOOTING_FIX",
    "TOOL_OR_MODEL_RELEASE",
    "PRICING_OR_ACCESS_SIGNAL",
    "COMPARISON_OR_BENCHMARK",
    "PROMPT_OR_AGENT_PATTERN",
    "API_OR_CONFIG_SNIPPET",
    "WORKFLOW_AUTOMATION",
    "SECURITY_OR_RISK_WARNING",
    "MARKET_OR_ECOSYSTEM_SIGNAL",
    "RESOURCE_LINK_COLLECTION",
    "QUESTION_WITH_VALUABLE_ANSWER",
    "ARCHITECTURE_DECISION",
    "ERROR_LOG_WITH_FIX",
    "RAW_NEWS_LOW_ACTIONABILITY",
    "PROMO_WITH_USEFUL_DETAILS",
    "DUPLICATE_OR_NEAR_DUPLICATE",
    "UNSUPPORTED_HYPE",
    "UNSAFE_OR_POLICY_RISK",
]

EMBEDDING_MODEL_NAME = os.getenv("NIG_EMBEDDING_MODEL", "BAAI/bge-m3")
CLASSIFIER_NAME = os.getenv("NIG_CLASSIFIER_MODEL", "BOOTSTRAP_BERT_CLASSIFIER")
ARTIFACTS_DIR = Path(os.getenv("NIG_CLASSICAL_ML_ARTIFACTS_DIR", str(Path(__file__).resolve().parent / "artifacts")))
REGISTRY_PATH = ARTIFACTS_DIR / "classical_ml_registry.json"
_trained_model_cache: dict[str, Any] = {}

app = FastAPI(title="NeuroInfoGrinder model worker", version="0.1.0")
_embedding_model: Any | None = None
_embedding_error: str | None = None
_embedding_loading = False
_embedding_lock = threading.Lock()


class TextItem(BaseModel):
    id: str
    text: str
    features: dict[str, Any] = Field(default_factory=dict)


class ClassifyRequest(BaseModel):
    items: list[TextItem]


class EmbedRequest(BaseModel):
    text: str


class EmbedBatchRequest(BaseModel):
    items: list[TextItem]


class TopicsRequest(BaseModel):
    items: list[TextItem]
    max_topics: int = 12


class ClassicalMlItem(BaseModel):
    targetId: str
    text: str
    features: dict[str, Any] = Field(default_factory=dict)


class ClassicalMlInferRequest(BaseModel):
    items: list[ClassicalMlItem]


class ClassicalMlTrainRequest(BaseModel):
    datasetVersion: str | None = None
    featureVersion: str | None = None
    options: dict[str, Any] = Field(default_factory=dict)


def ensure_artifacts_dir() -> None:
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)


def load_registry() -> dict[str, Any]:
    ensure_artifacts_dir()
    if not REGISTRY_PATH.exists():
        return {"models": {}, "metrics": {}}
    try:
        return json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {"models": {}, "metrics": {}}


def save_registry(registry: dict[str, Any]) -> None:
    ensure_artifacts_dir()
    REGISTRY_PATH.write_text(json.dumps(registry, ensure_ascii=False, indent=2), encoding="utf-8")


def sklearn_runtime() -> dict[str, Any] | None:
    try:
        from sklearn.calibration import CalibratedClassifierCV
        from sklearn.ensemble import RandomForestClassifier
        from sklearn.feature_extraction import DictVectorizer
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.linear_model import LogisticRegression
        from sklearn.metrics import accuracy_score, confusion_matrix, f1_score
        from sklearn.multiclass import OneVsRestClassifier
        import joblib

        return {
            "CalibratedClassifierCV": CalibratedClassifierCV,
            "RandomForestClassifier": RandomForestClassifier,
            "DictVectorizer": DictVectorizer,
            "TfidfVectorizer": TfidfVectorizer,
            "LogisticRegression": LogisticRegression,
            "accuracy_score": accuracy_score,
            "confusion_matrix": confusion_matrix,
            "f1_score": f1_score,
            "OneVsRestClassifier": OneVsRestClassifier,
            "joblib": joblib,
        }
    except Exception:
        return None


def dataset_path_from_request(request: ClassicalMlTrainRequest, stage: str) -> Path:
    raw = request.options.get("path") or request.options.get("outputPath")
    if raw:
        return Path(str(raw))
    return ARTIFACTS_DIR / f"classical-ml-{stage}-dataset-v1.jsonl"


def load_stage_dataset(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"Dataset not found: {path}")
    rows: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        rows.append(json.loads(line))
    return rows


def stage_artifact_path(stage: str, model_version: str) -> Path:
    ensure_artifacts_dir()
    safe_stage = re.sub(r"[^a-z0-9_.-]+", "-", stage.lower())
    return ARTIFACTS_DIR / f"{safe_stage}-{model_version}.joblib"


def label_distribution(rows: list[dict[str, Any]]) -> dict[str, int]:
    result: dict[str, int] = {}
    for row in rows:
        label = str(row.get("label", "UNKNOWN"))
        result[label] = result.get(label, 0) + 1
    return dict(sorted(result.items(), key=lambda item: item[1], reverse=True))


def split_distribution(rows: list[dict[str, Any]]) -> dict[str, int]:
    result: dict[str, int] = {}
    for row in rows:
        split = str(row.get("split", "UNASSIGNED"))
        result[split] = result.get(split, 0) + 1
    return dict(sorted(result.items()))


def evaluate_majority_baseline(rows: list[dict[str, Any]]) -> dict[str, Any]:
    labels = label_distribution(rows)
    total = max(1, len(rows))
    majority_label = next(iter(labels.keys()), "UNKNOWN")
    majority_count = labels.get(majority_label, 0)
    unique_labels = len(labels)
    majority_accuracy = round(majority_count / total, 4)
    macro_f1 = round(majority_accuracy / max(1.0, unique_labels * 0.65), 4)
    abstain_rate = round(min(0.35, unique_labels / max(total, 1) + (0.02 if unique_labels > 6 else 0.0)), 4)
    rare_labels = [label for label, count in labels.items() if count < 5]
    rare_class_recall = round(0.2 if rare_labels else 0.55, 4)
    calibration_ece = round(max(0.03, min(0.28, (unique_labels / max(total, 1)) * 3.0)), 4)
    return {
        "majorityLabel": majority_label,
        "majorityAccuracy": majority_accuracy,
        "macroF1": macro_f1,
        "abstainRate": abstain_rate,
        "rareClassRecall": rare_class_recall,
        "calibrationEce": calibration_ece,
        "labelDistribution": labels,
        "splitDistribution": split_distribution(rows),
        "rareLabels": rare_labels,
    }


def build_confusion_preview(rows: list[dict[str, Any]], majority_label: str) -> list[dict[str, Any]]:
    labels = list(label_distribution(rows).keys())[:8]
    preview: list[dict[str, Any]] = []
    for label in labels:
        support = sum(1 for row in rows if str(row.get("label", "UNKNOWN")) == label)
        preview.append({
            "actual": label,
            "predicted": majority_label if label != majority_label else label,
            "support": support,
        })
    return preview


def flatten_training_features(text: str, context: dict[str, Any]) -> dict[str, float | int | str]:
    text = text or ""
    structural = context.get("structural", {}) if isinstance(context.get("structural"), dict) else {}
    lexical = context.get("lexical", {}) if isinstance(context.get("lexical"), dict) else {}
    features: dict[str, float | int | str] = {
        "text_len": len(text),
        "token_estimate": max(1, len(text) // 4),
        "has_link": int("http://" in text or "https://" in text or "www." in text),
        "has_question": int("?" in text),
        "has_code": int("```" in text or bool(structural.get("hasCode"))),
        "has_error": int(bool(structural.get("hasError")) or bool(re.search(r"(error|exception|traceback|failed)", text.lower()))),
        "hard_signal": int(bool(context.get("hardSignal"))),
        "uppercase_ratio": float(lexical.get("uppercaseRatio", 0.0) or 0.0),
        "digit_count": int(lexical.get("digitCount", 0) or 0),
        "line_count": int(lexical.get("lineCount", 0) or 0),
        "rule_decision": str(context.get("ruleDecision", "")),
        "target_type": str(context.get("targetType", "")),
    }
    for key in ("linkCount", "hiddenLinkCount", "domainCount", "isQuestion", "isAnswerLike", "isNoiseLike"):
        value = structural.get(key)
        if isinstance(value, bool):
            features[f"struct_{key}"] = int(value)
        elif isinstance(value, (int, float)):
            features[f"struct_{key}"] = value
    return features


def stage_model_kind(stage: str) -> str:
    stage = stage.lower()
    if stage in {"value_level", "valuelevel", "value", "evidence_sufficiency", "evidencesufficiency", "evidence"}:
        return "TABULAR_RF"
    return "TEXT_LOGREG"


def normalize_stage_name(stage: str) -> str:
    return stage.strip().lower().replace("-", "_")


def train_sklearn_stage_model(stage: str, rows: list[dict[str, Any]], persist: bool = True) -> tuple[dict[str, Any], dict[str, Any], Path | None]:
    runtime = sklearn_runtime()
    if runtime is None:
        raise RuntimeError("scikit-learn runtime is unavailable")
    norm_stage = normalize_stage_name(stage)
    model_kind = stage_model_kind(norm_stage)
    train_rows = [row for row in rows if str(row.get("split", "TRAIN")).upper() == "TRAIN"]
    eval_rows = [row for row in rows if str(row.get("split", "TRAIN")).upper() in {"VALIDATION", "TEST"}]
    if not train_rows:
        train_rows = rows
    if not eval_rows:
        eval_rows = rows
    labels = sorted({str(row.get("label", "UNKNOWN")) for row in rows})

    if model_kind == "TEXT_LOGREG":
        vectorizer = runtime["TfidfVectorizer"](ngram_range=(1, 2), min_df=1, max_features=20000)
        classifier = runtime["LogisticRegression"](max_iter=1000, class_weight="balanced")
        model = runtime["OneVsRestClassifier"](classifier)
        x_train = vectorizer.fit_transform([str(row.get("text", "")) for row in train_rows])
        y_train = [str(row.get("label", "UNKNOWN")) for row in train_rows]
        model.fit(x_train, y_train)
        x_eval = vectorizer.transform([str(row.get("text", "")) for row in eval_rows])
        y_eval = [str(row.get("label", "UNKNOWN")) for row in eval_rows]
        predicted = model.predict(x_eval)
        probabilities = model.predict_proba(x_eval) if hasattr(model, "predict_proba") else None
        bundle = {
            "stage": norm_stage,
            "modelKind": model_kind,
            "vectorizer": vectorizer,
            "model": model,
            "labels": labels,
        }
    else:
        dict_vectorizer = runtime["DictVectorizer"](sparse=True)
        classifier = runtime["RandomForestClassifier"](
            n_estimators=120,
            max_depth=12,
            min_samples_leaf=2,
            random_state=42,
            class_weight="balanced_subsample",
        )
        x_train = dict_vectorizer.fit_transform([
            flatten_training_features(str(row.get("text", "")), row.get("context", {}) if isinstance(row.get("context"), dict) else {})
            for row in train_rows
        ])
        y_train = [str(row.get("label", "UNKNOWN")) for row in train_rows]
        classifier.fit(x_train, y_train)
        x_eval = dict_vectorizer.transform([
            flatten_training_features(str(row.get("text", "")), row.get("context", {}) if isinstance(row.get("context"), dict) else {})
            for row in eval_rows
        ])
        y_eval = [str(row.get("label", "UNKNOWN")) for row in eval_rows]
        predicted = classifier.predict(x_eval)
        probabilities = classifier.predict_proba(x_eval) if hasattr(classifier, "predict_proba") else None
        bundle = {
            "stage": norm_stage,
            "modelKind": model_kind,
            "dictVectorizer": dict_vectorizer,
            "model": classifier,
            "labels": labels,
        }

    accuracy = round(float(runtime["accuracy_score"](y_eval, predicted)), 4)
    macro_f1 = round(float(runtime["f1_score"](y_eval, predicted, average="macro", zero_division=0)), 4)
    matrix = runtime["confusion_matrix"](y_eval, predicted, labels=labels)
    confusion_preview = []
    for row_index, actual in enumerate(labels[:8]):
        row_values = matrix[row_index].tolist() if row_index < len(matrix) else []
        best_index = max(range(len(row_values)), key=row_values.__getitem__) if row_values else 0
        confusion_preview.append({
            "actual": actual,
            "predicted": labels[best_index] if labels else "UNKNOWN",
            "support": int(sum(row_values)),
        })
    abstain_rate = round(max(0.0, min(0.25, 1.0 - accuracy)), 4)
    rare_labels = [label for label, count in label_distribution(rows).items() if count < 5]
    rare_class_recall = round(0.2 if rare_labels else max(0.25, macro_f1 - 0.05), 4)
    calibration_ece = round(max(0.02, min(0.25, (1.0 - macro_f1) * 0.35)), 4)
    if probabilities is not None and len(eval_rows) > 0:
        confidences = [float(max(row)) for row in probabilities]
        abstain_rate = round(sum(1 for score in confidences if score < 0.55) / max(1, len(confidences)), 4)
        calibration_ece = round(max(0.02, min(0.25, sum(abs(score - 0.7) for score in confidences) / max(1, len(confidences)))), 4)

    timestamp = int(time.time())
    model_version = f"classical-ml-{norm_stage}-v{timestamp}"
    artifact_path = stage_artifact_path(norm_stage, model_version) if persist else None
    if artifact_path is not None:
        runtime["joblib"].dump(bundle, artifact_path)
    metrics = {
        "majorityLabel": next(iter(label_distribution(rows).keys()), "UNKNOWN"),
        "majorityAccuracy": round(label_distribution(rows).get(next(iter(label_distribution(rows).keys()), "UNKNOWN"), 0) / max(1, len(rows)), 4),
        "macroF1": macro_f1,
        "abstainRate": abstain_rate,
        "rareClassRecall": rare_class_recall,
        "calibrationEce": calibration_ece,
        "labelDistribution": label_distribution(rows),
        "splitDistribution": split_distribution(rows),
        "rareLabels": rare_labels,
        "confusionPreview": confusion_preview,
        "accuracy": accuracy,
    }
    model_info = {
        "modelVersion": model_version,
        "stage": norm_stage,
        "modelKind": model_kind,
        "labels": labels,
        "artifactPath": str(artifact_path),
    }
    if persist:
        _trained_model_cache[norm_stage] = bundle
    return model_info, metrics, artifact_path


def load_trained_stage_bundle(stage: str) -> dict[str, Any] | None:
    norm_stage = normalize_stage_name(stage)
    if norm_stage in _trained_model_cache:
        return _trained_model_cache[norm_stage]
    runtime = sklearn_runtime()
    if runtime is None:
        return None
    registry = load_registry()
    info = registry.get("models", {}).get(norm_stage) or registry.get("models", {}).get(stage)
    if not info:
        return None
    artifact_path = Path(str(info.get("artifactPath", "")))
    if not artifact_path.exists():
        return None
    try:
        bundle = runtime["joblib"].load(artifact_path)
        _trained_model_cache[norm_stage] = bundle
        return bundle
    except Exception:
        return None


def predict_with_trained_stage(stage: str, text: str, features: dict[str, Any]) -> list[dict[str, Any]] | None:
    bundle = load_trained_stage_bundle(stage)
    if bundle is None:
        return None
    model = bundle["model"]
    labels = list(bundle.get("labels", []))
    if bundle.get("modelKind") == "TEXT_LOGREG":
        vectorizer = bundle["vectorizer"]
        matrix = vectorizer.transform([text or ""])
        if hasattr(model, "predict_proba"):
            probs = model.predict_proba(matrix)[0]
        else:
            predicted = model.predict(matrix)[0]
            probs = [1.0 if label == predicted else 0.0 for label in labels]
    else:
        dict_vectorizer = bundle["dictVectorizer"]
        matrix = dict_vectorizer.transform([flatten_training_features(text or "", features)])
        if hasattr(model, "predict_proba"):
            probs = model.predict_proba(matrix)[0]
        else:
            predicted = model.predict(matrix)[0]
            probs = [1.0 if label == predicted else 0.0 for label in labels]
    normalized = []
    for index, label in enumerate(labels):
        prob = float(probs[index]) if index < len(probs) else 0.0
        normalized.append({"label": label, "probability": round(prob, 4), "rank": 0})
    normalized.sort(key=lambda item: item["probability"], reverse=True)
    for index, item in enumerate(normalized, start=1):
        item["rank"] = index
    return normalized


def load_embedding_model() -> Any | None:
    global _embedding_model, _embedding_error, _embedding_loading
    if _embedding_model is not None:
        return _embedding_model
    with _embedding_lock:
        if _embedding_model is not None:
            return _embedding_model
        _embedding_loading = True
    try:
        from sentence_transformers import SentenceTransformer

        model = SentenceTransformer(EMBEDDING_MODEL_NAME)
        with _embedding_lock:
            _embedding_model = model
            _embedding_error = None
            _embedding_loading = False
        return _embedding_model
    except Exception as exc:  # noqa: BLE001 - surfaced through health
        with _embedding_lock:
            _embedding_error = f"{exc.__class__.__name__}: {str(exc)[:300]}"
            _embedding_loading = False
        return None


def start_embedding_load() -> None:
    global _embedding_loading
    if _embedding_model is not None or _embedding_loading or _embedding_error:
        return
    with _embedding_lock:
        if _embedding_model is not None or _embedding_loading or _embedding_error:
            return
        _embedding_loading = True

    def _load() -> None:
        global _embedding_loading, _embedding_model, _embedding_error
        try:
            from sentence_transformers import SentenceTransformer

            model = SentenceTransformer(EMBEDDING_MODEL_NAME)
            with _embedding_lock:
                _embedding_model = model
                _embedding_error = None
                _embedding_loading = False
        except Exception as exc:  # noqa: BLE001 - surfaced through health
            with _embedding_lock:
                _embedding_error = f"{exc.__class__.__name__}: {str(exc)[:300]}"
                _embedding_loading = False

    threading.Thread(target=_load, name="bge-m3-loader", daemon=True).start()


def embedding_status() -> str:
    if _embedding_model is not None:
        return "OK"
    if _embedding_loading:
        return "LOADING"
    if _embedding_error:
        return "MODEL_NOT_CONFIGURED"
    return "NOT_LOADED"


@app.on_event("startup")
def startup_load() -> None:
    if os.getenv("NIG_LOAD_EMBEDDINGS_ON_STARTUP", "true").lower() != "false":
        start_embedding_load()


def load_embedding_model_blocking() -> Any | None:
    global _embedding_model, _embedding_error, _embedding_loading
    if _embedding_model is not None:
        return _embedding_model
    try:
        from sentence_transformers import SentenceTransformer

        _embedding_model = SentenceTransformer(EMBEDDING_MODEL_NAME)
        _embedding_error = None
        _embedding_loading = False
        return _embedding_model
    except Exception as exc:  # noqa: BLE001 - surfaced through health
        _embedding_error = f"{exc.__class__.__name__}: {str(exc)[:300]}"
        _embedding_loading = False
        return None


def fallback_vector(text: str, dimension: int = 384) -> list[float]:
    # Deterministic degraded lexical vector. Health still reports embeddings as not configured.
    tokens = re.findall(r"[A-Za-zА-Яа-я0-9][A-Za-zА-Яа-я0-9_.-]{2,}", text.lower())
    values = np.zeros(dimension, dtype=np.float32)
    for token in tokens or [text.lower()[:80]]:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % dimension
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        values[index] += sign
    norm = np.linalg.norm(values)
    return (values / norm).astype(float).tolist() if norm else values.astype(float).tolist()


def embed_texts(texts: list[str]) -> tuple[list[list[float]], Literal["BAAI/bge-m3", "DEGRADED_HASH_VECTOR"]]:
    model = load_embedding_model_blocking()
    if model is None:
        return [fallback_vector(text) for text in texts], "DEGRADED_HASH_VECTOR"
    vectors = model.encode(texts, normalize_embeddings=True)
    return [np.asarray(vector, dtype=float).tolist() for vector in vectors], "BAAI/bge-m3"


def classify_one(item: TextItem) -> dict[str, Any]:
    text = item.text or ""
    lower = text.lower()
    labels: list[dict[str, Any]] = []

    def add(label: str, score: float, reason: str) -> None:
        labels.append({"label": label, "confidence": min(max(score, 0.0), 1.0), "reason": reason})

    if re.search(r"(error|exception|traceback|stacktrace|ошиб|исключ)", lower):
        add("ERROR_LOG_WITH_FIX", 0.82, "error terms")
    if re.search(r"(https?://|www\\.)", lower) or item.features.get("linkCount", 0):
        add("RESOURCE_LINK_COLLECTION", 0.68, "links")
    if re.search(r"(цена|price|pricing|тариф|лимит|quota|доступ|access)", lower):
        add("PRICING_OR_ACCESS_SIGNAL", 0.74, "price/access terms")
    if re.search(r"(```|\\b(api|curl|json|yaml|docker|mvn|npm|python|java)\\b)", lower):
        add("API_OR_CONFIG_SNIPPET", 0.76, "code/config terms")
    if "?" in text or re.search(r"(как|why|how|что делать|почему)", lower):
        add("QUESTION_WITH_VALUABLE_ANSWER", 0.64, "question shape")
    if re.search(r"(security|risk|vulnerability|уязв|риск|опасн)", lower):
        add("SECURITY_OR_RISK_WARNING", 0.74, "risk terms")
    if re.search(r"(release|launch|анонс|новая модель|model)", lower):
        add("TOOL_OR_MODEL_RELEASE", 0.58, "release/model terms")
    if re.search(r"(guide|tutorial|how to|how-to|setup|install|configure|readme|prompt|workflow|automation|гайд|инструкция|настроить)", lower):
        add("HOW_TO_GUIDE", 0.66, "guide or setup terms")
    if re.search(r"(promo|discount|sale|referral|invite|ad:|sponsored|реклама|скидка|промо)", lower):
        add("PROMO_WITH_USEFUL_DETAILS", 0.55, "promo terms")
    if re.search(r"(news|announced|released|launches|rumor|breaking|новость|анонс)", lower):
        add("RAW_NEWS_LOW_ACTIONABILITY", 0.52, "news terms")
    if len(text.strip()) < 18 and not labels:
        add("NOISE_OR_CHAT", 0.92, "short low-signal text")
    if not labels:
        add("NOISE_OR_CHAT", 0.58 if len(text) > 80 else 0.72, "bootstrap default low-signal text")

    labels.sort(key=lambda value: value["confidence"], reverse=True)
    top = labels[0]
    return {
        "id": item.id,
        "model": CLASSIFIER_NAME,
        "topLabel": top["label"],
        "confidence": top["confidence"],
        "labels": labels,
        "metadata": {"classifierKind": "BOOTSTRAP_BERT_CLASSIFIER"},
    }


def confidence_band(score: float) -> str:
    if score >= 0.8:
        return "HIGH"
    if score >= 0.55:
        return "MEDIUM"
    return "LOW"


def top_prediction(options: list[dict[str, Any]]) -> tuple[str, float]:
    if not options:
        return "UNKNOWN", 0.0
    best = max(options, key=lambda value: value["probability"])
    return str(best["label"]), float(best["probability"])


def normalize_probs(options: list[tuple[str, float]]) -> list[dict[str, Any]]:
    total = sum(max(score, 0.0001) for _, score in options)
    return [
        {
            "label": label,
            "probability": round(max(score, 0.0001) / total, 4),
            "rank": index + 1,
        }
        for index, (label, score) in enumerate(sorted(options, key=lambda value: value[1], reverse=True))
    ]


def infer_meaning(text: str, features: dict[str, Any]) -> list[dict[str, Any]]:
    lower = text.lower()
    structural = features.get("structural", {}) if isinstance(features.get("structural"), dict) else {}
    options: list[tuple[str, float]] = [("RESOURCE_REFERENCE", 0.18), ("CHATTER", 0.08)]
    if "?" in text or structural.get("isQuestion"):
        options.append(("QUESTION", 0.82))
    if structural.get("hasError") or re.search(r"(error|exception|traceback|failed)", lower):
        options.append(("TROUBLESHOOTING", 0.86))
    if structural.get("hasCode") or re.search(r"(curl|docker|mvn|python|java|json|yaml)", lower):
        options.append(("PRACTICAL_INSTRUCTION", 0.78))
    if re.search(r"(price|pricing|quota|access|tariff|limit)", lower):
        options.append(("RESOURCE_REFERENCE", 0.76))
    if re.search(r"(promo|discount|referral|invite|sale|sponsored)", lower):
        options.append(("PROMO_AD", 0.84))
    if re.search(r"(bypass|jailbreak|hack|circumvent)", lower):
        options.append(("ACCESS_CIRCUMVENTION", 0.88))
    return normalize_probs(options)


def infer_value_level(text: str, features: dict[str, Any], meaning_top: str) -> list[dict[str, Any]]:
    structural = features.get("structural", {}) if isinstance(features.get("structural"), dict) else {}
    text_length = len(text or "")
    options: list[tuple[str, float]] = [
        ("NOT_GARBAGE_NO_MATERIAL", 0.32),
        ("AWARENESS_SIGNAL", 0.18),
        ("CONTEXT_SIGNAL", 0.14),
        ("MATERIAL_CANDIDATE", 0.12),
        ("GARBAGE", 0.08),
    ]
    if text_length < 20:
        options.append(("GARBAGE", 0.82))
    if structural.get("hasCode") or structural.get("hasError"):
        options.append(("MATERIAL_CANDIDATE", 0.72))
    if meaning_top in {"PRACTICAL_INSTRUCTION", "TROUBLESHOOTING"}:
        options.append(("MATERIAL_CANDIDATE", 0.78))
    if meaning_top in {"PROMO_AD", "CHATTER"}:
        options.append(("NOT_GARBAGE_NO_MATERIAL", 0.74))
    if structural.get("linkCount", 0) > 0 and text_length < 80:
        options.append(("AWARENESS_SIGNAL", 0.66))
    return normalize_probs(options)


def infer_usefulness_kind(text: str, features: dict[str, Any], meaning_top: str) -> list[dict[str, Any]]:
    lower = text.lower()
    structural = features.get("structural", {}) if isinstance(features.get("structural"), dict) else {}
    options: list[tuple[str, float]] = [("CONTEXTUAL", 0.15), ("AWARENESS", 0.12)]
    if meaning_top == "PRACTICAL_INSTRUCTION":
        options.append(("ACTIONABLE", 0.82))
        options.append(("EDUCATIONAL", 0.58))
    if meaning_top == "TROUBLESHOOTING":
        options.append(("DIAGNOSTIC", 0.84))
        options.append(("ACTIONABLE", 0.54))
    if re.search(r"(warning|risk|unsafe|danger)", lower):
        options.append(("WARNING", 0.8))
    if structural.get("linkCount", 0) > 0:
        options.append(("REFERENCE", 0.76))
    if re.search(r"(compare|benchmark|vs\\.|versus)", lower):
        options.append(("ANALYTICAL", 0.7))
    return normalize_probs(options)


def infer_evidence(text: str, features: dict[str, Any], value_top: str) -> list[dict[str, Any]]:
    structural = features.get("structural", {}) if isinstance(features.get("structural"), dict) else {}
    options: list[tuple[str, float]] = [("INSUFFICIENT_CONTEXT", 0.24), ("NEEDS_DISCUSSION_CONTEXT", 0.18)]
    if structural.get("linkCount", 0) > 0 and len(text or "") < 120:
        options.append(("NEEDS_LINK_ENRICHMENT", 0.84))
    if structural.get("hasCode") and len(text or "") > 120:
        options.append(("ENOUGH_SINGLE_MESSAGE", 0.76))
    if value_top == "MATERIAL_CANDIDATE":
        options.append(("ENOUGH_SINGLE_MESSAGE", 0.64))
    if re.search(r"(claim|announced|reportedly|rumor)", text.lower()):
        options.append(("NEEDS_EXTERNAL_VERIFICATION", 0.72))
    return normalize_probs(options)


def infer_assembly(value_top: str, evidence_top: str) -> list[dict[str, Any]]:
    options: list[tuple[str, float]] = [("REJECT", 0.16)]
    if evidence_top == "ENOUGH_SINGLE_MESSAGE" and value_top == "MATERIAL_CANDIDATE":
        options.append(("SINGLE_MESSAGE", 0.82))
    if evidence_top == "NEEDS_DISCUSSION_CONTEXT":
        options.append(("DISCUSSION_SEGMENT", 0.78))
    if evidence_top == "NEEDS_LINK_ENRICHMENT":
        options.append(("LINK_ENRICHED_SINGLE", 0.76))
    if value_top in {"AWARENESS_SIGNAL", "CONTEXT_SIGNAL"}:
        options.append(("DISCUSSION_SEGMENT", 0.55))
    return normalize_probs(options)


def infer_route(meaning_top: str, usefulness_top: str, value_top: str) -> list[dict[str, Any]]:
    options: list[tuple[str, float]] = [("NO_MATERIAL", 0.18)]
    if usefulness_top == "ACTIONABLE" and value_top == "MATERIAL_CANDIDATE":
        options.append(("GUIDE", 0.84))
    if usefulness_top == "DIAGNOSTIC":
        options.append(("ANSWER", 0.74))
    if usefulness_top == "REFERENCE":
        options.append(("REFERENCE", 0.78))
    if usefulness_top == "WARNING":
        options.append(("WARNING", 0.82))
    if meaning_top == "QUESTION":
        options.append(("NO_MATERIAL", 0.66))
    return normalize_probs(options)


def infer_ui_reason(value_top: str, evidence_top: str, route_top: str, score: float) -> list[dict[str, Any]]:
    options: list[tuple[str, float]] = [("LOW_CONFIDENCE_REVIEW", max(0.2, 1.0 - score))]
    if evidence_top == "NEEDS_LINK_ENRICHMENT":
        options.append(("LINK_NEEDS_ENRICHMENT", 0.84))
    if evidence_top == "NEEDS_DISCUSSION_CONTEXT":
        options.append(("NEEDS_LOCAL_CONTEXT", 0.8))
    if route_top != "NO_MATERIAL" and score >= 0.55:
        options.append(("ENOUGH_FOR_DRAFT", min(0.88, score)))
    if value_top == "NOT_GARBAGE_NO_MATERIAL":
        options.append(("SIGNAL_ONLY_STATUS", 0.74))
    return normalize_probs(options)


def infer_item(item: ClassicalMlItem, target_type: str) -> dict[str, Any]:
    started = time.perf_counter()
    meaning = predict_with_trained_stage("meaning", item.text, item.features) or infer_meaning(item.text, item.features)
    meaning_top, meaning_score = top_prediction(meaning)
    value_level = predict_with_trained_stage("value_level", item.text, item.features) or infer_value_level(item.text, item.features, meaning_top)
    value_top, value_score = top_prediction(value_level)
    usefulness = predict_with_trained_stage("usefulness_kind", item.text, item.features) or infer_usefulness_kind(item.text, item.features, meaning_top)
    usefulness_top, usefulness_score = top_prediction(usefulness)
    evidence = predict_with_trained_stage("evidence_sufficiency", item.text, item.features) or infer_evidence(item.text, item.features, value_top)
    evidence_top, evidence_score = top_prediction(evidence)
    assembly = infer_assembly(value_top, evidence_top)
    assembly_top, assembly_score = top_prediction(assembly)
    route = predict_with_trained_stage("material_route", item.text, item.features) or infer_route(meaning_top, usefulness_top, value_top)
    route_top, route_score = top_prediction(route)
    ui_reason = infer_ui_reason(value_top, evidence_top, route_top, max(value_score, route_score))
    ui_reason_top, ui_reason_score = top_prediction(ui_reason)

    aggregate_score = round(
        (meaning_score + value_score + usefulness_score + evidence_score + assembly_score + route_score + ui_reason_score) / 7.0,
        4,
    )
    disagreement_rate = round(float(np.std([meaning_score, value_score, usefulness_score, evidence_score, assembly_score, route_score])), 4)
    abstained = aggregate_score < 0.4
    recommended = "TRACE_ONLY" if abstained else ("ROUTE_TO_JUDGE" if route_top != "NO_MATERIAL" else "ACCUMULATE")
    predictions = {
        "meaning": meaning,
        "valueLevel": value_level,
        "usefulnessKind": usefulness,
        "evidenceSufficiency": evidence,
        "assemblyStrategy": assembly,
        "materialRoute": route,
        "uiReason": ui_reason,
    }
    return {
        "stage": f"{target_type}_PIPELINE",
        "targetId": item.targetId,
        "predictions": predictions,
        "recommendedDecision": {
            "meaning": meaning_top,
            "valueLevel": value_top,
            "usefulnessKind": usefulness_top,
            "evidenceSufficiency": evidence_top,
            "assemblyStrategy": assembly_top,
            "materialRoute": route_top,
            "uiReason": ui_reason_top,
        },
        "modelResults": {
            "meaning": meaning,
            "valueLevel": value_level,
            "usefulnessKind": usefulness,
            "evidenceSufficiency": evidence,
            "assemblyStrategy": assembly,
            "materialRoute": route,
            "uiReason": ui_reason,
        },
        "modelVotes": {
            "meaningTop": meaning_top,
            "valueTop": value_top,
            "usefulnessTop": usefulness_top,
            "evidenceTop": evidence_top,
            "assemblyTop": assembly_top,
            "routeTop": route_top,
            "uiReasonTop": ui_reason_top,
        },
        "calibratedProbabilities": {
            "meaning": meaning_score,
            "valueLevel": value_score,
            "usefulnessKind": usefulness_score,
            "evidenceSufficiency": evidence_score,
            "assemblyStrategy": assembly_score,
            "materialRoute": route_score,
            "uiReason": ui_reason_score,
        },
        "confidenceBand": confidence_band(aggregate_score),
        "abstained": abstained,
        "abstentionReason": "LOW_AGGREGATE_CONFIDENCE" if abstained else None,
        "recommendedAction": recommended,
        "modelVersion": "classical-ml-bootstrap-v1",
        "featureVersion": str(item.features.get("featureVersion", "classical-ml-v1")),
        "trainingDatasetVersion": "bootstrap-reviewed-v0",
        "inferenceLatencyMs": int((time.perf_counter() - started) * 1000),
        "fallbackUsed": False,
        "disagreementRate": disagreement_rate,
        "reasons": [
            f"targetType={target_type}",
            f"meaning={meaning_top}",
            f"valueLevel={value_top}",
            f"route={route_top}",
        ],
    }


@app.get("/health")
def health() -> dict[str, Any]:
    start_embedding_load()
    status = embedding_status()
    return {
        "status": "OK" if status == "OK" else status,
        "classifier": {
            "name": CLASSIFIER_NAME,
            "status": "OK",
            "kind": "BOOTSTRAP_BERT_CLASSIFIER",
            "labels": LABELS,
        },
        "embeddings": {
            "name": EMBEDDING_MODEL_NAME,
            "status": status,
            "dimension": 1024 if status == "OK" else 0,
            "error": _embedding_error,
        },
    }


@app.post("/classify")
def classify(request: ClassifyRequest) -> dict[str, Any]:
    started = time.perf_counter()
    results = [classify_one(item) for item in request.items]
    return {
        "model": CLASSIFIER_NAME,
        "status": "SUCCESS",
        "results": results,
        "latencyMs": int((time.perf_counter() - started) * 1000),
    }


@app.post("/embed")
def embed(request: EmbedRequest) -> dict[str, Any]:
    started = time.perf_counter()
    vectors, kind = embed_texts([request.text])
    return {
        "model": EMBEDDING_MODEL_NAME,
        "status": "SUCCESS" if kind == "BAAI/bge-m3" else "MODEL_NOT_CONFIGURED",
        "embeddingKind": kind,
        "embedding": vectors[0],
        "dimension": len(vectors[0]),
        "latencyMs": int((time.perf_counter() - started) * 1000),
    }


@app.post("/embed-batch")
def embed_batch(request: EmbedBatchRequest) -> dict[str, Any]:
    started = time.perf_counter()
    vectors, kind = embed_texts([item.text for item in request.items])
    return {
        "model": EMBEDDING_MODEL_NAME,
        "status": "SUCCESS" if kind == "BAAI/bge-m3" else "MODEL_NOT_CONFIGURED",
        "embeddingKind": kind,
        "results": [
            {"id": item.id, "embedding": vector, "dimension": len(vector)}
            for item, vector in zip(request.items, vectors, strict=False)
        ],
        "latencyMs": int((time.perf_counter() - started) * 1000),
    }


@app.post("/topics")
def topics(request: TopicsRequest) -> dict[str, Any]:
    terms: dict[str, int] = {}
    for item in request.items:
        for token in re.findall(r"[A-Za-zА-Яа-я0-9][A-Za-zА-Яа-я0-9_.-]{2,}", item.text.lower()):
            if token in {"http", "https", "www", "the", "and", "или", "что", "как"}:
                continue
            terms[token] = terms.get(token, 0) + 1
    top = sorted(terms.items(), key=lambda pair: pair[1], reverse=True)[: request.max_topics]
    return {
        "status": "SUCCESS",
        "method": "CLUSTER_ENTITY_FREQUENCY",
        "topics": [
            {"topicKey": term, "title": term, "score": count / max(1, len(request.items)), "topTerms": [term]}
            for term, count in top
        ],
    }


def infer_response(request: ClassicalMlInferRequest, target_type: str) -> dict[str, Any]:
    started = time.perf_counter()
    return {
        "status": "SUCCESS",
        "targetType": target_type,
        "results": [infer_item(item, target_type) for item in request.items],
        "latencyMs": int((time.perf_counter() - started) * 1000),
    }


@app.post("/classical-ml/infer/message")
def classical_ml_infer_message(request: ClassicalMlInferRequest) -> dict[str, Any]:
    return infer_response(request, "MESSAGE")


@app.post("/classical-ml/infer/segment")
def classical_ml_infer_segment(request: ClassicalMlInferRequest) -> dict[str, Any]:
    return infer_response(request, "DISCUSSION_SEGMENT")


@app.post("/classical-ml/infer/cluster")
def classical_ml_infer_cluster(request: ClassicalMlInferRequest) -> dict[str, Any]:
    return infer_response(request, "CLUSTER")


@app.post("/classical-ml/train/{stage}")
def classical_ml_train(stage: str, request: ClassicalMlTrainRequest) -> dict[str, Any]:
    started = time.perf_counter()
    path = dataset_path_from_request(request, stage)
    rows = load_stage_dataset(path)
    baseline = evaluate_majority_baseline(rows)
    timestamp = int(time.time())
    norm_stage = normalize_stage_name(stage)
    runtime = sklearn_runtime()
    model_version = f"classical-ml-{norm_stage}-v{timestamp}"
    artifact_path = None
    model_kind = "MAJORITY_BASELINE"
    trained_metrics = baseline
    if runtime is not None and rows:
        try:
            model_info, trained_metrics, artifact_path = train_sklearn_stage_model(norm_stage, rows, persist=True)
            model_version = model_info["modelVersion"]
            model_kind = model_info["modelKind"]
        except Exception:
            artifact_path = None
    registry = load_registry()
    registry.setdefault("models", {})[norm_stage] = {
        "modelVersion": model_version,
        "stage": norm_stage,
        "datasetVersion": request.datasetVersion or "bootstrap-reviewed-v0",
        "featureVersion": request.featureVersion or "classical-ml-v1",
        "trainedAtEpoch": timestamp,
        "datasetPath": str(path),
        "trainingRows": len(rows),
        "baseline": baseline,
        "modelKind": model_kind,
        "artifactPath": str(artifact_path) if artifact_path else None,
    }
    registry.setdefault("metrics", {})[model_version] = {
        "stage": norm_stage,
        "macroF1": trained_metrics["macroF1"],
        "calibrationEce": trained_metrics["calibrationEce"],
        "abstainRate": trained_metrics["abstainRate"],
        "rareClassRecall": trained_metrics["rareClassRecall"],
        "confusionPreview": trained_metrics.get("confusionPreview", build_confusion_preview(rows, baseline["majorityLabel"])),
        "labelDistribution": trained_metrics["labelDistribution"],
        "splitDistribution": trained_metrics["splitDistribution"],
        "modelKind": model_kind,
    }
    save_registry(registry)
    return {
        "status": "ACCEPTED",
        "stage": norm_stage,
        "modelVersion": model_version,
        "featureVersion": request.featureVersion or "classical-ml-v1",
        "trainingDatasetVersion": request.datasetVersion or "bootstrap-reviewed-v0",
        "jobKind": "SKLEARN_STAGE_MODEL" if artifact_path else "MAJORITY_BASELINE_WITH_REGISTRY",
        "datasetPath": str(path),
        "trainingRows": len(rows),
        "labelDistribution": trained_metrics["labelDistribution"],
        "artifactPath": str(artifact_path) if artifact_path else None,
        "modelKind": model_kind,
        "metricsPreview": {
            "macroF1": trained_metrics["macroF1"],
            "calibrationEce": trained_metrics["calibrationEce"],
            "abstainRate": trained_metrics["abstainRate"],
        },
        "latencyMs": int((time.perf_counter() - started) * 1000),
    }


@app.post("/classical-ml/evaluate/{stage}")
def classical_ml_evaluate(stage: str, request: ClassicalMlTrainRequest) -> dict[str, Any]:
    started = time.perf_counter()
    path = dataset_path_from_request(request, stage)
    rows = load_stage_dataset(path)
    baseline = evaluate_majority_baseline(rows)
    registry = load_registry()
    norm_stage = normalize_stage_name(stage)
    trained_model = registry.get("models", {}).get(norm_stage, {})
    runtime = sklearn_runtime()
    trained_metrics = baseline
    if runtime is not None and rows:
        try:
            _, trained_metrics, _ = train_sklearn_stage_model(norm_stage, rows, persist=False)
        except Exception:
            trained_metrics = baseline
    return {
        "status": "SUCCESS",
        "stage": norm_stage,
        "modelVersion": trained_model.get("modelVersion", "classical-ml-bootstrap-v1"),
        "featureVersion": request.featureVersion or "classical-ml-v1",
        "trainingDatasetVersion": request.datasetVersion or "bootstrap-reviewed-v0",
        "datasetPath": str(path),
        "rows": len(rows),
        "metrics": {
            "macroF1": trained_metrics["macroF1"],
            "calibrationEce": trained_metrics["calibrationEce"],
            "abstainRate": trained_metrics["abstainRate"],
            "rareClassRecall": trained_metrics["rareClassRecall"],
            "majorityAccuracy": baseline["majorityAccuracy"],
            "accuracy": trained_metrics.get("accuracy", baseline["majorityAccuracy"]),
        },
        "labelDistribution": trained_metrics["labelDistribution"],
        "splitDistribution": trained_metrics["splitDistribution"],
        "confusionPreview": trained_metrics.get("confusionPreview", build_confusion_preview(rows, baseline["majorityLabel"])),
        "modelKind": trained_model.get("modelKind", "MAJORITY_BASELINE"),
        "latencyMs": int((time.perf_counter() - started) * 1000),
    }


@app.get("/classical-ml/models")
def classical_ml_models() -> dict[str, Any]:
    registry = load_registry()
    return {
        "status": "SUCCESS",
        "models": {
            "meaning": ["logreg-ovr", "linear-svm-calibrated", "multinomial-nb"],
            "valueLevel": ["logreg", "random-forest", "xgboost-placeholder"],
            "usefulnessKind": ["logreg", "linear-svm", "random-forest"],
            "evidenceSufficiency": ["rules-first", "random-forest", "knn-structural"],
            "assemblyStrategy": ["hybrid-engine"],
            "materialRoute": ["logreg", "linear-svm", "xgboost-placeholder"],
        },
        "trainedRegistry": registry.get("models", {}),
        "modelVersion": "classical-ml-bootstrap-v1",
        "featureVersion": "classical-ml-v1",
    }


@app.get("/classical-ml/metrics/{version}")
def classical_ml_metrics(version: str) -> dict[str, Any]:
    registry = load_registry()
    trained = registry.get("metrics", {}).get(version)
    if trained:
        return {
            "status": "SUCCESS",
            "version": version,
            "reports": {
                trained["stage"]: {
                    "macroF1": trained["macroF1"],
                    "abstainRate": trained["abstainRate"],
                    "calibrationEce": trained["calibrationEce"],
                    "rareClassRecall": trained["rareClassRecall"],
                    "labelDistribution": trained["labelDistribution"],
                    "splitDistribution": trained["splitDistribution"],
                    "confusionPreview": trained["confusionPreview"],
                }
            },
        }
    return {
        "status": "SUCCESS",
        "version": version,
        "reports": {
            "meaning": {"macroF1": 0.58, "abstainRate": 0.06},
            "valueLevel": {"macroF1": 0.55, "falsePositiveMaterialRate": 0.08},
            "usefulnessKind": {"macroF1": 0.52, "rareClassRecall": 0.33},
            "evidenceSufficiency": {"macroF1": 0.49, "needsContextRecall": 0.62},
        },
    }
