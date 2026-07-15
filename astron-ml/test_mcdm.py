import os, json, certifi
from pymongo import MongoClient
from dotenv import load_dotenv
from mcdm import recommend_top_3

load_dotenv()
client = MongoClient(
    os.getenv("MONGO_URI"),
    tlsCAFile=certifi.where()
)
db = client.get_database("astron")
developers = list(db["developers"].find({}, {"_id": 0}))

print(f"Found {len(developers)} developers in the database\n")

# Show everyone's workload first
print("--- All developers ---")
for d in developers:
    print(f"  {d['name']:20} workload {d['workload_percent']}%  "
          f"skills: {d['skills']}")

# Ask MCDM: who should do a Python + MongoDB task?
required = ["Python", "MongoDB"]
print(f"\n--- Task requires: {required} ---\n")

top3 = recommend_top_3(developers, required)

print("--- MCDM RANKING ---")
for r in top3:
    dev = r["developer"]
    print(f"\n#{r['rank']} {r['badge']}: {dev['name']}")
    print(f"    MCDM score      : {r['mcdm_score']}/10")
    print(f"    Skill match     : {r['skill_match_percent']}%")
    print(f"    Workload        : {r['workload_label']}")
    print(f"    Burnout penalty : {'YES' if r['penalty_applied'] else 'no'}")
