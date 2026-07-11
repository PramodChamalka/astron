import json

from ml.predict import predictor

print("\n===== TEST 1 - normal task =====")
result = predictor.predict({
    "hours_estimate": 16,
    "priority_numeric": 2,
    "category": "Development",
    "subcategory": "Enhancement",
    "project_code": "PC2",
})
print(json.dumps(result, indent=2))

print("\n===== TEST 2 - unseen category =====")
result = predictor.predict({
    "hours_estimate": 16,
    "priority_numeric": 2,
    "category": "Research",
    "subcategory": "Enhancement",
    "project_code": "PC2",
})
print(json.dumps(result, indent=2))

print("\n===== TEST 3 - estimate outside training range =====")
result = predictor.predict({
    "hours_estimate": 300,
    "priority_numeric": 2,
    "category": "Development",
    "subcategory": "Enhancement",
    "project_code": "PC2",
})
print(json.dumps(result, indent=2))

print("\n===== TEST 4 - unseen project code =====")
result = predictor.predict({
    "hours_estimate": 16,
    "priority_numeric": 2,
    "category": "Development",
    "subcategory": "Enhancement",
    "project_code": "PC99",
})
print(json.dumps(result, indent=2))
