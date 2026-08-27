"""
ASTRON - Task Duration Prediction
==================================
This file loads the model we already trained (see ml/train.py) and uses
it to predict how many hours a NEW task will take, based on info sent
from the frontend (estimate, priority, category, etc).

We only load the model ONCE, when this file is first imported, and then
reuse it for every prediction request. Loading a model from disk is slow,
so we do NOT want to do it every single time someone calls the API.
"""

import os

import joblib
import numpy as np
from dotenv import load_dotenv

from complexity import compute_complexity

from complexity import compute_complexity

load_dotenv()

MODEL_DIR = os.getenv("MODEL_DIR", "ml/artifacts")
CATEGORICAL_COLUMNS = ["Category", "SubCategory", "ProjectCode"]

DEFAULTS = {
    "hours_estimate": 8,
    "priority_numeric": 3,
    "category": "Development",
    "subcategory": "Enhancement",
    "project_code": "PC2",
}

MAX_TRAINING_HOURS = 80

class ModelPredictor:
    """Loads the trained Random Forest model once, then predicts task duration."""

    def __init__(self):
        self.model = joblib.load(os.path.join(MODEL_DIR, "rf_model.pkl"))
        self.encoders = joblib.load(os.path.join(MODEL_DIR, "encoders.pkl"))
        self.feature_names = joblib.load(os.path.join(MODEL_DIR, "feature_names.pkl"))

    def _encode(self, col, value):
        encoder = self.encoders[col]
        try:
            encoded_value = encoder.transform([str(value)])[0]
            return int(encoded_value), True
        except ValueError:
            return 0, False

    def predict(self, task):
        """
        task: a dict coming from the frontend, e.g.
          {
            "hours_estimate": 16,
            "priority_numeric": 2,
            "category": "Development",
            "subcategory": "Enhancement",
            "project_code": "PC2"
          }
        """

        hours_estimate = task.get("hours_estimate", DEFAULTS["hours_estimate"])
        priority_numeric = task.get("priority_numeric", DEFAULTS["priority_numeric"])
        category = task.get("category", DEFAULTS["category"])
        subcategory = task.get("subcategory", DEFAULTS["subcategory"])
        project_code = task.get("project_code", DEFAULTS["project_code"])

        unseen = []

        category_enc, category_known = self._encode("Category", category)
        if not category_known:
            unseen.append(f"category '{category}'")

        subcategory_enc, subcategory_known = self._encode("SubCategory", subcategory)
        if not subcategory_known:
            unseen.append(f"subcategory '{subcategory}'")

        project_enc, project_known = self._encode("ProjectCode", project_code)
        if not project_known:
            unseen.append(f"project '{project_code}'")

        X = np.array([[
            hours_estimate,
            priority_numeric,
            category_enc,
            subcategory_enc,
            project_enc,
        ]])

        predicted_hours = float(self.model.predict(X)[0])

        tree_preds = np.array([tree.predict(X)[0] for tree in self.model.estimators_])
        std = float(tree_preds.std())

        confidence = 100 - (std / max(predicted_hours, 1)) * 100
        confidence = int(max(50, min(99, confidence)))

        warnings = []

        if unseen:
            confidence = min(confidence, 45)

        if hours_estimate > MAX_TRAINING_HOURS:
            confidence = min(confidence, 40)
            warnings.append(
                "Estimate exceeds the model's training range (max 80h). "
                "Random Forests cannot extrapolate beyond their training data."
            )

        if unseen:
            warnings.append(
                f"This task uses values the model has never seen: {unseen}. "
                "Treat this estimate with caution."
            )

        low = max(0.0, predicted_hours - 1.96 * std)
        high = predicted_hours + 1.96 * std

        complexity = compute_complexity(hours_estimate, priority_numeric)

        return {
            **complexity,
            "predicted_hours": round(predicted_hours, 1),
            "confidence_interval": {
                "low": round(low, 1),
                "high": round(high, 1),
            },
            "model_confidence": confidence,
            "algorithm": "Random Forest Regression",
            "estimator_trees": len(self.model.estimators_),
            "model_version": "v1.0",
            "unseen_values": unseen,
            "reliability_warning": " ".join(warnings) if warnings else None,
        }

predictor = ModelPredictor()
