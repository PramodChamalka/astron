"""
ASTRON - Task Effort Prediction Model
======================================
This script trains a Random Forest model that predicts how many hours
a task will actually take (HoursActual), based on info that is known
BEFORE the task is done (like the estimate, priority, category, etc).

The key thesis result: we compare the model's prediction error against
a "human baseline" (just using the human's HoursEstimate as the guess)
to see if the model is actually better than a human estimator.
"""

import json

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
DATA_PATH = "data/Sip-task-info.csv"
ARTIFACTS_DIR = "ml/artifacts"

# The 5 inputs the model is allowed to use to make a prediction.
FEATURE_COLUMNS = ["HoursEstimate", "Priority", "Category", "SubCategory", "ProjectCode"]

# Which of those features are text (categories) and need to be turned into
# numbers before the model can use them.
TEXT_COLUMNS = ["Category", "SubCategory", "ProjectCode"]

# What we are trying to predict.
TARGET_COLUMN = "HoursActual"

MODEL_VERSION = "1.0.0"


def main():
    # -----------------------------------------------------------------
    # Step 1: Load the data
    # -----------------------------------------------------------------
    # The file is NOT plain UTF-8, so we must tell pandas to read it as
    # latin-1, otherwise pandas will crash trying to decode it.
    df = pd.read_csv(DATA_PATH, encoding="latin-1")

    # Step 2: Print how many rows we loaded in total.
    print(f"Loaded {len(df)} rows from {DATA_PATH}")

    # -----------------------------------------------------------------
    # Step 3: Clean the data
    # -----------------------------------------------------------------
    # Some tasks have 0 (or negative/missing) hours, which doesn't make
    # sense for a real completed task, so we throw those rows away.
    df = df[(df["HoursActual"] > 0) & (df["HoursEstimate"] > 0)]

    # Some tasks have huge, unusual hour counts (outliers) that could
    # confuse the model, so we only keep "normal" tasks under 80 hours.
    df = df[df["HoursActual"] < 80]

    print(f"{len(df)} rows remain after cleaning")

    # -----------------------------------------------------------------
    # Step 4: Build X (inputs) and y (the answer we want to predict)
    # -----------------------------------------------------------------
    # .copy() so we can safely edit X without touching the original df.
    X = df[FEATURE_COLUMNS].copy()
    y = df[TARGET_COLUMN].copy()

    # -----------------------------------------------------------------
    # Step 5: Encode the text columns into numbers
    # -----------------------------------------------------------------
    # Random Forest needs numbers, not text, so we convert each text
    # column (like "Development", "Enhancement", "PC2") into a unique
    # integer using LabelEncoder. We keep the fitted encoders in a dict
    # so that later, when a NEW task comes in, we can convert its text
    # fields the exact same way (same word -> same number).
    encoders = {}
    for column in TEXT_COLUMNS:
        encoder = LabelEncoder()
        X[column] = encoder.fit_transform(X[column].astype(str))
        encoders[column] = encoder

    # -----------------------------------------------------------------
    # Step 6: Split into training data and testing data
    # -----------------------------------------------------------------
    # We train the model on 80% of the tasks, and hold back 20% that the
    # model never sees during training, so we can fairly test it later.
    # random_state=42 just makes the split repeatable every time we run this.
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42
    )

    # -----------------------------------------------------------------
    # Step 7: Train the Random Forest model
    # -----------------------------------------------------------------
    # A Random Forest is many decision trees that each "vote" on the
    # answer, and we average their votes for the final prediction.
    #   n_estimators=200   -> build 200 trees
    #   min_samples_leaf=2 -> each tree "leaf" needs at least 2 examples
    #                         (helps avoid overfitting to single tasks)
    #   random_state=42    -> repeatable results
    #   n_jobs=-1          -> use all CPU cores to train faster
    model = RandomForestRegressor(
        n_estimators=200,
        min_samples_leaf=2,
        random_state=42,
        n_jobs=-1,
    )
    model.fit(X_train, y_train)

    # -----------------------------------------------------------------
    # Step 8: Evaluate the model on the TEST set (data it hasn't seen)
    # -----------------------------------------------------------------
    y_pred = model.predict(X_test)

    # MAE (Mean Absolute Error): on average, how many hours off is the
    # model's prediction? Lower is better.
    mae = mean_absolute_error(y_test, y_pred)

    # RMSE (Root Mean Squared Error): similar to MAE, but punishes big
    # mistakes more heavily. Lower is better.
    rmse = np.sqrt(mean_squared_error(y_test, y_pred))

    # R-squared: how much of the variation in HoursActual is explained
    # by the model. 1.0 = perfect, 0.0 = no better than guessing the average.
    r2 = r2_score(y_test, y_pred)

    # "Within 20%" accuracy: for how many tasks was the prediction within
    # 20% of the true value? This is an easy-to-understand accuracy measure.
    percent_error = np.abs(y_pred - y_test) / y_test
    within_20_percent = (percent_error <= 0.20).mean() * 100

    print("\n--- Model performance on test set ---")
    print(f"MAE:  {mae:.2f} hours")
    print(f"RMSE: {rmse:.2f} hours")
    print(f"R^2:  {r2:.3f}")
    print(f"Within 20% of true value: {within_20_percent:.1f}%")

    # -----------------------------------------------------------------
    # Step 9: Compare against the human baseline (KEY THESIS RESULT)
    # -----------------------------------------------------------------
    # The "human baseline" pretends the human's original HoursEstimate
    # IS the prediction, with no model at all. We compare its error to
    # the model's error, to see how much better (or worse) the model is.
    human_mae = mean_absolute_error(y_test, X_test["HoursEstimate"])

    # % improvement = how much smaller the model's error is than the
    # human's error, as a percentage of the human's error.
    improvement_over_human_percent = ((human_mae - mae) / human_mae) * 100

    print("\n--- Model vs Human baseline (key thesis result) ---")
    print(f"Model MAE:            {mae:.2f} hours")
    print(f"Human estimate MAE:   {human_mae:.2f} hours")
    print(f"Improvement over human: {improvement_over_human_percent:.1f}%")

    # -----------------------------------------------------------------
    # Step 10: Feature importance
    # -----------------------------------------------------------------
    # This tells us which of the 5 inputs the model relied on most to
    # make its predictions, as a percentage (they all add up to 100%).
    importance_percentages = model.feature_importances_ * 100
    feature_importance = dict(
        zip(FEATURE_COLUMNS, [float(v) for v in importance_percentages])
    )

    print("\n--- Feature importance ---")
    for feature, importance in sorted(
        feature_importance.items(), key=lambda item: item[1], reverse=True
    ):
        print(f"{feature}: {importance:.1f}%")

    # -----------------------------------------------------------------
    # Step 11: Save the model, encoders, and feature names to disk
    # -----------------------------------------------------------------
    # joblib.dump() saves Python objects to a file so we can load them
    # again later in our API without retraining the model every time.
    joblib.dump(model, f"{ARTIFACTS_DIR}/rf_model.pkl")
    joblib.dump(encoders, f"{ARTIFACTS_DIR}/encoders.pkl")
    joblib.dump(FEATURE_COLUMNS, f"{ARTIFACTS_DIR}/feature_names.pkl")

    # -----------------------------------------------------------------
    # Step 12: Save an evaluation.json summary of everything above
    # -----------------------------------------------------------------
    evaluation = {
        "mae": float(mae),
        "rmse": float(rmse),
        "r2": float(r2),
        "within_20_percent": float(within_20_percent),
        "human_baseline_mae": float(human_mae),
        "improvement_over_human_percent": float(improvement_over_human_percent),
        "training_samples": int(len(X_train)),
        "test_samples": int(len(X_test)),
        "feature_importance": feature_importance,
        "model_version": MODEL_VERSION,
    }

    with open(f"{ARTIFACTS_DIR}/evaluation.json", "w") as f:
        json.dump(evaluation, f, indent=2)

    print(f"\nSaved model, encoders, and evaluation results to {ARTIFACTS_DIR}/")


if __name__ == "__main__":
    main()
