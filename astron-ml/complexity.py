COMPLEXITY_BANDS = [
    (8, "Low"),
    (15, "Medium"),
    (30, "High"),
]
HIGHEST_BAND = "Very High"
PRIORITY_WEIGHT = 2

def compute_complexity(hours_estimate, priority_numeric):
    try:
        hours = float(hours_estimate)
    except (TypeError, ValueError):
        hours = 0.0
    try:
        priority = float(priority_numeric)
    except (TypeError, ValueError):
        priority = 0.0
    # Negative hours would be nonsense, so clamp at 0.
    hours = max(0.0, hours)
    priority = max(0.0, priority)
    score = hours + (priority * PRIORITY_WEIGHT)
    # Walk the bands in order and take the first one the score fits in.
    level = HIGHEST_BAND
    for upper_bound, band_name in COMPLEXITY_BANDS:
        if score < upper_bound:
            level = band_name
            break

    return {
        "complexity_score": round(score, 1),
        "complexity_level": level,
        "complexity_formula": (
            f"{hours:g}h estimate + ({priority:g} priority x {PRIORITY_WEIGHT})"
            f" = {round(score, 1):g}"
        ),
    }
