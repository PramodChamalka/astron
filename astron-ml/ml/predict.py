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

# Read variables from a .env file (if one exists) into the environment.
load_dotenv()

# Where the trained model files live. Falls back to "ml/artifacts" if the
# MODEL_DIR variable isn't set in .env.
MODEL_DIR = os.getenv("MODEL_DIR", "ml/artifacts")

# The 3 text columns that were turned into numbers (LabelEncoded) during
# training. We need to encode NEW values the exact same way.
CATEGORICAL_COLUMNS = ["Category", "SubCategory", "ProjectCode"]

# Default values to use if the frontend forgets to send a field.
DEFAULTS = {
    "hours_estimate": 8,
    "priority_numeric": 3,
    "category": "Development",
    "subcategory": "Enhancement",
    "project_code": "PC2",
}

# The model was trained only on tasks under 80 hours, so it has never
# "seen" anything bigger and cannot be trusted to extrapolate that far.
MAX_TRAINING_HOURS = 80


class ModelPredictor:
    """Loads the trained Random Forest model once, then predicts task duration."""

    def __init__(self):
        # joblib.load() reads a Python object (model, encoders, etc.) back
        # from the .pkl file it was saved to during training.
        self.model = joblib.load(os.path.join(MODEL_DIR, "rf_model.pkl"))
        self.encoders = joblib.load(os.path.join(MODEL_DIR, "encoders.pkl"))
        self.feature_names = joblib.load(os.path.join(MODEL_DIR, "feature_names.pkl"))

    def _encode(self, col, value):
        """
        Turn a text value (like "Development") into the integer the model
        expects, using the SAME encoder that was fitted during training.

        Returns a tuple: (encoded_number, was_known)
          - If the value WAS seen during training  -> (the int, True)
          - If the value was NEVER seen before      -> (0, False)
        """
        encoder = self.encoders[col]
        try:
            # .transform() expects a list, and gives back a list, so we
            # pass in [value] and pull out the single result with [0].
            encoded_value = encoder.transform([str(value)])[0]
            return int(encoded_value), True
        except ValueError:
            # LabelEncoder raises ValueError when it has never seen this
            # exact text before (it wasn't in the training data).
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

        # ---------------------------------------------------------
        # Step 1: Pull out values from the task, using defaults for
        # anything that's missing.
        # ---------------------------------------------------------
        hours_estimate = task.get("hours_estimate", DEFAULTS["hours_estimate"])
        priority_numeric = task.get("priority_numeric", DEFAULTS["priority_numeric"])
        category = task.get("category", DEFAULTS["category"])
        subcategory = task.get("subcategory", DEFAULTS["subcategory"])
        project_code = task.get("project_code", DEFAULTS["project_code"])

        # ---------------------------------------------------------
        # Step 2: Encode the 3 text fields into numbers, and keep
        # track of any value the model has never seen before.
        # ---------------------------------------------------------
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

        # ---------------------------------------------------------
        # Step 3: Build the feature row in the EXACT order the model
        # was trained on: HoursEstimate, Priority, Category,
        # SubCategory, ProjectCode.
        # ---------------------------------------------------------
        X = np.array([[
            hours_estimate,
            priority_numeric,
            category_enc,
            subcategory_enc,
            project_enc,
        ]])

        # ---------------------------------------------------------
        # Step 4: Ask the model for its prediction.
        # ---------------------------------------------------------
        predicted_hours = float(self.model.predict(X)[0])

        # ---------------------------------------------------------
        # Step 5: Work out how confident we are.
        # A Random Forest is many trees. If all the trees agree with
        # each other, we can be more confident. If they disagree a
        # lot (high standard deviation), we should be less confident.
        # ---------------------------------------------------------
        tree_preds = np.array([tree.predict(X)[0] for tree in self.model.estimators_])
        std = float(tree_preds.std())

        confidence = 100 - (std / max(predicted_hours, 1)) * 100
        # Keep confidence in a sensible range: never below 50, never above 99.
        confidence = int(max(50, min(99, confidence)))

        # ---------------------------------------------------------
        # Step 6: Lower our confidence (and warn the user) in cases
        # where the prediction is less trustworthy.
        # ---------------------------------------------------------
        warnings = []

        # 6a) The task uses values the model has never seen -> big red flag.
        if unseen:
            confidence = min(confidence, 45)

        # 6b) The estimate is bigger than anything the model trained on ->
        # Random Forests can't extrapolate past their training data.
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

        # ---------------------------------------------------------
        # Step 7: Build the confidence interval, using the standard
        # 95% interval formula: prediction +/- 1.96 * std.
        # ---------------------------------------------------------
        low = max(0.0, predicted_hours - 1.96 * std)
        high = predicted_hours + 1.96 * std

        # ---------------------------------------------------------
        # Step 8: Return everything the frontend needs, in one dict.
        # ---------------------------------------------------------
        return {
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


# Create ONE shared instance when this module is first imported, so Flask
# loads the model into memory once at startup instead of on every request.
predictor = ModelPredictor()
