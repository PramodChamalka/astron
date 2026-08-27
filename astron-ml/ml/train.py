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

DATA_PATH = "data/Sip-task-info.csv"
ARTIFACTS_DIR = "ml/artifacts"

FEATURE_COLUMNS = ["HoursEstimate", "Priority", "Category", "SubCategory", "ProjectCode"]

TEXT_COLUMNS = ["Category", "SubCategory", "ProjectCode"]
TARGET_COLUMN = "HoursActual"
MODEL_VERSION = "1.0.0"

def main():

    df = pd.read_csv(DATA_PATH, encoding="latin-1")
    print(f"Loaded {len(df)} rows from {DATA_PATH}")
    df = df[(df["HoursActual"] > 0) & (df["HoursEstimate"] > 0)]
    df = df[df["HoursActual"] < 80]

    print(f"{len(df)} rows remain after cleaning")

    X = df[FEATURE_COLUMNS].copy()
    y = df[TARGET_COLUMN].copy()
    encoders = {}
    for column in TEXT_COLUMNS:
        encoder = LabelEncoder()
        X[column] = encoder.fit_transform(X[column].astype(str))
        encoders[column] = encoder

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42
    )
    model = RandomForestRegressor(
        n_estimators=200,
        min_samples_leaf=2,
        random_state=42,
        n_jobs=-1,
    )
    model.fit(X_train, y_train)
    y_pred = model.predict(X_test)
    
    mae = mean_absolute_error(y_test, y_pred)
    rmse = np.sqrt(mean_squared_error(y_test, y_pred))
    r2 = r2_score(y_test, y_pred)
    
    percent_error = np.abs(y_pred - y_test) / y_test
    within_20_percent = (percent_error <= 0.20).mean() * 100

    print("\n--- Model performance on test set ---")
    print(f"MAE:  {mae:.2f} hours")
    print(f"RMSE: {rmse:.2f} hours")
    print(f"R^2:  {r2:.3f}")
    print(f"Within 20% of true value: {within_20_percent:.1f}%")

    human_mae = mean_absolute_error(y_test, X_test["HoursEstimate"])
    improvement_over_human_percent = ((human_mae - mae) / human_mae) * 100

    print("\n--- Model vs Human baseline ---")
    print(f"Model MAE:            {mae:.2f} hours")
    print(f"Human estimate MAE:   {human_mae:.2f} hours")
    print(f"Improvement over human: {improvement_over_human_percent:.1f}%")

    importance_percentages = model.feature_importances_ * 100
    feature_importance = dict(
        zip(FEATURE_COLUMNS, [float(v) for v in importance_percentages])
    )

    print("\n--- Feature importance ---")
    for feature, importance in sorted(
        feature_importance.items(), key=lambda item: item[1], reverse=True
    ):
        print(f"{feature}: {importance:.1f}%")

    joblib.dump(model, f"{ARTIFACTS_DIR}/rf_model.pkl")
    joblib.dump(encoders, f"{ARTIFACTS_DIR}/encoders.pkl")
    joblib.dump(FEATURE_COLUMNS, f"{ARTIFACTS_DIR}/feature_names.pkl")

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
