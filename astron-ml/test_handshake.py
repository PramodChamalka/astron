import requests, json

# 1. Log in to SPRING BOOT to get a token
login = requests.post(
    "http://localhost:8080/api/auth/login",
    json={"email": "admin@astron.dev", "password": "admin123"}
)
print("Spring Boot login:", login.status_code)
token = login.json()["user"]["token"]
print("Token:", token[:50] + "...")

# 2. Send that SAME token to FLASK
pred = requests.post(
    "http://127.0.0.1:5000/api/predict",
    headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    },
    json={
        "hours_estimate": 16,
        "priority_numeric": 2,
        "category": "Development",
        "subcategory": "Enhancement",
        "project_code": "PC2"
    }
)
print("\nFlask response:", pred.status_code)
print(json.dumps(pred.json(), indent=2))
