import os
import certifi
from pymongo import MongoClient
from dotenv import load_dotenv

load_dotenv()
client = MongoClient(
    os.getenv("MONGO_URI"),
    tlsCAFile=certifi.where()
)
db = client.get_database("astron")
developers = db["developers"]

# clear any old data first
developers.delete_many({})
print("Cleared old developers")

# give each developer a DIFFERENT workload so we can see
# the burnout penalty working in the MCDM ranking
data = [
  {
    "id": "dev-001", "name": "Grace Hopper", "initials": "GH",
    "role": "Senior Backend Engineer",
    "email": "grace@astron.dev",
    "skills": ["Python", "MongoDB", "Flask", "SQL"],
    "workload_percent": 40, "active_tasks": 2,
    "completed_tasks": 47, "avg_accuracy": 96.2,
    "perf_score": 9.8, "availability": "available",
    "capacity_hours": 40
  },
  {
    "id": "dev-002", "name": "Alan Turing", "initials": "AT",
    "role": "Database Architect",
    "email": "alan@astron.dev",
    "skills": ["Python", "SQL", "MongoDB", "C++"],
    "workload_percent": 78, "active_tasks": 4,
    "completed_tasks": 52, "avg_accuracy": 94.1,
    "perf_score": 9.4, "availability": "high_load",
    "capacity_hours": 40
  },
  {
    "id": "dev-003", "name": "Ada Lovelace", "initials": "AL",
    "role": "Full Stack Engineer",
    "email": "ada@astron.dev",
    "skills": ["React", "Python", "Testing"],
    "workload_percent": 90, "active_tasks": 5,
    "completed_tasks": 38, "avg_accuracy": 91.5,
    "perf_score": 9.2, "availability": "high_load",
    "capacity_hours": 40
  },
  {
    "id": "dev-004", "name": "Margaret Hamilton", "initials": "MH",
    "role": "Systems Engineer",
    "email": "margaret@astron.dev",
    "skills": ["Python", "Flask", "Docker", "MongoDB"],
    "workload_percent": 30, "active_tasks": 1,
    "completed_tasks": 61, "avg_accuracy": 97.3,
    "perf_score": 9.9, "availability": "available",
    "capacity_hours": 40
  }
]

developers.insert_many(data)
print(f"Inserted {len(data)} developers")

# show what's now in the database
for d in developers.find({}, {"_id": 0, "name": 1, "workload_percent": 1}):
    print(f"  {d['name']} - workload {d['workload_percent']}%")
