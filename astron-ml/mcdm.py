def score_developer(dev, required_skills):

    # Turn the lists into sets so we can easily compare them.
    req = set(required_skills or [])
    have = set(dev.get("skills", []))
    matched = req & have      # skills the task needs AND the dev has
    missing = req - have      # skills the task needs but the dev lacks

    skill_score = (len(matched) / len(req) * 100) if req else 50

    wl = dev.get("workload_percent", 0)
    workload_score = 100 - wl
    penalty = False
    if wl > 80:
        workload_score *= 0.3      # heavy penalty - very overloaded
        penalty = True
    elif wl > 60:
        workload_score *= 0.7      # moderate penalty - getting busy
        penalty = True

    experience_score = min(100, dev.get("completed_tasks", 0) * 1.5)


    total = (skill_score * 0.40
           + workload_score * 0.35
           + experience_score * 0.25)

    # Return every detail the frontend needs to explain WHY this
    # developer got this score, not just the final number.
    return {
        "mcdm_score": round(total / 10, 2),
        "skill_match_percent": round(skill_score),
        "workload_percent": wl,
        "matched_skills": sorted(matched),
        "missing_skills": sorted(missing),
        "penalty_applied": penalty,
        "criteria": {
            "skill_suitability":      round(skill_score),
            "current_availability":   round(100 - wl),
            "historical_performance": round(dev.get("avg_accuracy", 0)),
            "experience_match":       round(experience_score),
            "workload_balance":       round(workload_score)
        }
    }


def recommend_top_3(developers, required_skills):
    """Score all developers, sort them, return the best 3."""

    # Score every developer one at a time, and attach their basic info
    # so the frontend can display who they are.
    scored = []
    for d in developers:
        s = score_developer(d, required_skills)
        s["developer"] = {
            "id": d["id"],
            "name": d["name"],
            "initials": d["initials"],
            "role": d["role"]
        }
        scored.append(s)

    # Sort so the HIGHEST mcdm_score comes first.
    scored.sort(key=lambda x: -x["mcdm_score"])
    top = scored[:3]

    # Label the top 3 so the frontend can show a friendly badge on
    # each one: the best overall fit, a balanced choice, and a backup.
    badges = ["BEST MATCH", "BALANCED", "BACKUP"]
    for i, r in enumerate(top):
        r["rank"] = i + 1
        r["badge"] = badges[i]
        wl = r["workload_percent"]
        label = "Light" if wl < 50 else ("Medium" if wl < 80 else "Heavy")
        r["workload_label"] = f"{wl}% ({label})"

    return top
