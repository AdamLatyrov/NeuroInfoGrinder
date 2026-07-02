import json
import unittest
from unittest.mock import patch

import app as worker


class ClassicalMlTelemetryTests(unittest.TestCase):
    @patch.object(worker, "load_registry", return_value={"models": {}, "metrics": {}})
    def test_emptyRegistryReportsHeuristicOnly(self, _load_registry):
        response = worker.classical_ml_models()

        self.assertEqual("HEURISTIC_ONLY", response["status"])
        self.assertEqual([], response["trainedStages"])
        self.assertEqual({}, response["trainedRegistry"])
        self.assertNotIn("xgboost-placeholder", json.dumps(response))

    @patch.object(worker, "load_registry", return_value={"models": {}, "metrics": {}})
    def test_emptyRegistryReportsMetricsNotEvaluated(self, _load_registry):
        response = worker.classical_ml_metrics("classical-ml-bootstrap-v1")

        self.assertEqual("NOT_EVALUATED", response["status"])
        self.assertEqual({}, response["reports"])

    def test_bootstrapClassifierIdentifiesRegexImplementation(self):
        response = worker.classify_one(worker.TextItem(id="1", text="API error 429", features={}))

        self.assertEqual("REGEX_BOOTSTRAP_CLASSIFIER", response["metadata"]["classifierKind"])
        self.assertFalse(response["metadata"]["trained"])


if __name__ == "__main__":
    unittest.main()
