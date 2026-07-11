import requests, jwt, datetime, os, json
from dotenv import load_dotenv

load_dotenv()
SECRET = os.getenv("JWT_SECRET")

# make a fresh token
token = jwt.encode({
    "sub": "admin@astron.dev",
    "role": "Admin",
    "id": "usr-001",
    "exp": datetime.datetime.now(datetime.timezone.utc)
           + datetime.timedelta(hours=24)
}, SECRET, algorithm="HS256")

# call the prediction endpoint
response = requests.post(
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

print("Status code:", response.status_code)
print(json.dumps(response.json(), indent=2))
