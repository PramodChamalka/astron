"""
ASTRON - Derived task complexity
=================================
Complexity used to be a Fibonacci number the user picked by hand, which
made it a guess rather than a measurement. This module DERIVES it from
parameters the task already carries, so the same task always gets the
same complexity level no matter who submits it.

IMPORTANT (thesis note): the formula and the cut-offs below are ASTRON's
own definition of complexity. They are not an official or standard
software-complexity metric (they are not Cyclomatic Complexity, COCOMO,
Function Points, or Story Points). They are calibrated against this
project's own historical dataset, described below.

THE FORMULA
-----------
    score = hours_estimate + (priority_numeric * 2)

  - hours_estimate  is the requester's rough time estimate in hours. It
                    dominates the score, which matches the trained model
                    where HoursEstimate carries 67.8% of feature
                    importance.
  - priority_numeric is 1 (Low) to 4 (Urgent). It is doubled so that
                    urgency shifts the score by a meaningful amount
                    (2-8 points) without ever overpowering the estimate.

THE CUT-OFFS AND WHY THEY ARE THESE NUMBERS
-------------------------------------------
Calibrated on data/Sip-task-info.csv (11,977 cleaned historical tasks).
The score distribution over that real data is:
    p25 = 4.0    p50 = 7.5    p75 = 12.0    p90 = 23.0    p95 = 37.0

So the boundaries sit near real percentiles rather than round numbers
picked by feel: 8 is about the median, 15 sits between p75 and p90, and
30 is just past p90 - which leaves each band populated:

    score <  8   -> "Low"        52.2% of historical tasks
    score < 15   -> "Medium"     29.3%
    score < 30   -> "High"       11.0%
    score >= 30  -> "Very High"   7.5%

Each band also separates real recorded effort, which is the property
that makes the measure meaningful. Median ACTUAL hours per band:

    Low 1.8h  ->  Medium 3.5h  ->  High 13.5h  ->  Very High 23.9h

An earlier draft used 10/25/45. Those cut-offs were rejected because
this dataset is smaller-scale than they assume (median estimate is only
2.5h), so they filed 69.5% of all tasks under "Low" and the measure
stopped discriminating between most tasks.
"""

# Band upper bounds, exclusive. Kept as data (not buried in if/else) so
# the thresholds can be cited and adjusted in one obvious place.
COMPLEXITY_BANDS = [
    (8, "Low"),
    (15, "Medium"),
    (30, "High"),
]
HIGHEST_BAND = "Very High"

PRIORITY_WEIGHT = 2


def compute_complexity(hours_estimate, priority_numeric):
    """
    Work out a task's complexity from its own parameters.

    Returns a dict the API can merge straight into its response:
      complexity_score   the raw number, so the UI can show the working
      complexity_level   "Low" | "Medium" | "High" | "Very High"
      complexity_formula a human-readable trace of how it was derived
    """
    # Guard the inputs: these arrive from JSON, so they can be strings,
    # None, or missing entirely. Anything unusable falls back to 0 so
    # this can never raise and take the whole prediction down with it.
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
