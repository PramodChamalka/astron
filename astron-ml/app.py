"""
ASTRON - Flask ML API
======================
This is the web server. It takes HTTP requests from the frontend and
uses our trained model (ml/predict.py) to answer them.
"""

import json
import os
from functools import wraps

import certifi
import jwt
from dotenv import load_dotenv
from flask import Flask, jsonify, request
from flask_cors import CORS
from pymongo import MongoClient

from mcdm import recommend_top_3
from ml.predict import predictor

load_dotenv()

app = Flask(__name__)
CORS(app, origins=["http://localhost:5173"])

client = MongoClient(os.getenv("MONGO_URI"), tlsCAFile=certifi.where())
db = client.get_database("astron")
developers_col = db["developers"]
tasks_col = db["tasks"]

JWT_SECRET = os.getenv("JWT_SECRET")

def token_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return jsonify({"success": False, "error": "Missing token"}), 401

        token = auth_header[len("Bearer "):]
        try:
            jwt.decode(token, JWT_SECRET, algorithms=["HS256"])
        except jwt.ExpiredSignatureError:
            return jsonify({"success": False, "error": "Token expired"}), 401
        except jwt.InvalidTokenError:
            return jsonify({"success": False, "error": "Invalid token"}), 401

        return f(*args, **kwargs)

    return decorated


@app.route("/api/health", methods=["GET"])
def health():
    """Simple check to confirm the server is running. No token needed."""
    return {"status": "ok", "service": "flask-ml"}


@app.route("/api/predict", methods=["POST"])
@token_required
def predict():
    """Take task info from the frontend and return a predicted duration."""
    data = request.get_json()
    result = predictor.predict(data)
    return jsonify({"success": True, "data": result})


@app.route("/api/model-evaluation", methods=["GET"])
@token_required
def model_evaluation():
    """Return the saved evaluation stats from when the model was trained."""
    with open("ml/artifacts/evaluation.json") as f:
        evaluation = json.load(f)
    return jsonify({"success": True, "data": evaluation})


@app.route("/api/recommend/<task_id>", methods=["GET"])
@token_required
def recommend(task_id):
    # find the task
    task = tasks_col.find_one({"id": task_id}, {"_id": 0})
    if not task:
        return jsonify({"success": False,
                        "error": "task not found"}), 404

    # get all developers
    devs = list(developers_col.find({}, {"_id": 0}))

    # rank them with MCDM
    top3 = recommend_top_3(devs, task.get("skills_required", []))
    return jsonify({"success": True, "data": top3})


if __name__ == "__main__":
    app.run(debug=True, port=5000)
