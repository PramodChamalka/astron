"""
ASTRON - one-off cleanup of pre-fix demo tasks
===============================================
Before the predict-contract fix, the frontend sent field names that
ml/predict.py never read, so every task fell back to the same defaults
and came out of the model at exactly 9.7h. Those tasks also carry a
hand-picked Fibonacci "complexity" (5, 8, ...), which is a different
scale from the derived complexity score the fixed path now stores.

Rather than back-fill them - impossible, because Task has no
hours_estimate field, so the model's dominant input was never saved -
this removes them, along with the records that hang off them:

  1. the tasks themselves
  2. their assignment records (otherwise the assignment history page
     shows rows pointing at tasks that no longer exist)
  3. the assigned developer's workload counters, which were driven up
     by those assignments and would otherwise claim active work that
     is no longer there

Run once, from the astron-ml directory:  python cleanup_legacy_tasks.py
"""

import os

import certifi
from dotenv import load_dotenv
from pymongo import MongoClient

load_dotenv()
client = MongoClient(os.getenv("MONGO_URI"), tlsCAFile=certifi.where())
db = client.get_database("astron")
tasks = db["tasks"]
assignments = db["assignments"]
developers = db["developers"]

# The constant every task from the broken path came out at. Defined here
# rather than hard-coding task ids, so the script explains its own
# selection rule instead of relying on a list someone has to trust.
BUGGY_CONSTANT = 9.7


def main():
    # -----------------------------------------------------------------
    # Step 1: Find the affected tasks and show them BEFORE touching
    # anything, so the deletion is never a surprise.
    # -----------------------------------------------------------------
    doomed = list(tasks.find({"predicted_hours": BUGGY_CONSTANT}, {"_id": 0}))

    if not doomed:
        print(f"No tasks with the {BUGGY_CONSTANT}h constant. Nothing to do.")
        return

    task_ids = [t["id"] for t in doomed]

    print(f"Tasks carrying the {BUGGY_CONSTANT}h constant:")
    for t in doomed:
        print(f"  {t['id']}  complexity={t.get('complexity')}  "
              f"status={t.get('status')}  assigned_to={t.get('assigned_to_name')}")

    # Assignments that would be orphaned if we deleted only the tasks.
    doomed_assignments = list(
        assignments.find({"task_id": {"$in": task_ids}}, {"_id": 0}))
    print("\nAssignment records referencing them:")
    for a in doomed_assignments:
        print(f"  {a['id']} -> {a['task_id']}  {a.get('developer_name')}")

    # Which developers had their counters driven up by those assignments.
    affected_devs = sorted({a["developer_id"] for a in doomed_assignments
                            if a.get("developer_id")})

    # -----------------------------------------------------------------
    # Step 2: Delete the tasks and their assignments.
    # -----------------------------------------------------------------
    deleted_tasks = tasks.delete_many({"id": {"$in": task_ids}}).deleted_count
    deleted_assignments = assignments.delete_many(
        {"task_id": {"$in": task_ids}}).deleted_count
    print(f"\nDeleted {deleted_tasks} tasks and {deleted_assignments} assignments")

    # -----------------------------------------------------------------
    # Step 3: Rebuild each affected developer's counters from the tasks
    # that ACTUALLY remain, using the same formula the Spring backend
    # uses (TaskController.updateDeveloperStats): 20% per active task,
    # capped at 100, with matching availability thresholds.
    # -----------------------------------------------------------------
    for dev_id in affected_devs:
        active = tasks.count_documents({
            "assigned_to": dev_id,
            "status": {"$ne": "Completed"},
        })
        workload = min(100, active * 20)
        availability = ("available" if workload < 50
                        else "moderate" if workload < 80
                        else "high_load")

        developers.update_one({"id": dev_id}, {"$set": {
            "active_tasks": active,
            "workload_percent": workload,
            "availability": availability,
        }})

        dev = developers.find_one({"id": dev_id}, {"_id": 0, "name": 1})
        name = dev["name"] if dev else dev_id
        print(f"Recalculated {name}: active={active}, "
              f"workload={workload}%, {availability}")

    # -----------------------------------------------------------------
    # Step 4: Show what survived, so the result is verifiable at a glance.
    # -----------------------------------------------------------------
    print("\nRemaining tasks:")
    for t in tasks.find({}, {"_id": 0}).sort("id", 1):
        print(f"  {t['id']}  predicted={t.get('predicted_hours')}h  "
              f"complexity={t.get('complexity')} (derived)")


if __name__ == "__main__":
    main()
