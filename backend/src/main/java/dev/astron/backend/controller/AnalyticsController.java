package dev.astron.backend.controller;

import dev.astron.backend.model.Developer;
import dev.astron.backend.model.Task;
import dev.astron.backend.repository.DeveloperRepository;
import dev.astron.backend.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired private TaskRepository taskRepo;
    @Autowired private DeveloperRepository devRepo;

    // Real, fixed facts about the trained Random Forest model itself
    // (see ml/train.py and ml/artifacts/evaluation.json) - not per-task
    // data, since a Task only ever stores its own predicted_hours.
    private static final Map<String, Double> FEATURE_IMPORTANCE = Map.of(
        "HoursEstimate", 67.8,
        "ProjectCode", 12.9,
        "SubCategory", 10.3,
        "Priority", 7.1,
        "Category", 1.9
    );

    // GET /api/analytics/predictions?task_id=TASK-101
    // Returns the REAL stored prediction for one task. There is no
    // per-task confidence/feature-breakdown stored anywhere, so we
    // only return what genuinely exists.
    @GetMapping("/predictions")
    public ResponseEntity<?> getPrediction(@RequestParam("task_id") String taskId) {
        Task task = taskRepo.findByTaskId(taskId);
        if (task == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false, "error", "Task not found"));
        }

        Map<String,Object> data = new LinkedHashMap<>();
        data.put("task_id", task.getId());
        data.put("title", task.getTitle());
        data.put("category", task.getCategory());
        data.put("predicted_hours", task.getPredictedHours());
        data.put("actual_hours", task.getActualHours());
        data.put("algorithm", "Random Forest Regression");
        data.put("estimator_trees", 200);
        data.put("training_samples", 9581);
        data.put("feature_importance", FEATURE_IMPORTANCE);

        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    // GET /api/analytics/workload - team-wide workload numbers computed
    // live from the real Developer records.
    @GetMapping("/workload")
    public Map<String,Object> getWorkload() {
        List<Developer> devs = devRepo.findAll();

        double avgLoad = devs.stream()
            .mapToInt(Developer::getWorkloadPercent)
            .average()
            .orElse(0.0);

        long atRisk = devs.stream()
            .filter(d -> d.getWorkloadPercent() >= 80)
            .count();

        double overloadRiskPercent = devs.isEmpty() ? 0.0
            : (atRisk * 100.0) / devs.size();

        // How evenly spread the workload is: turn the standard
        // deviation of everyone's workload_percent into a 0-10 score,
        // where 10 = perfectly even, lower = more lopsided.
        double variance = devs.stream()
            .mapToDouble(d -> Math.pow(d.getWorkloadPercent() - avgLoad, 2))
            .average()
            .orElse(0.0);
        double fairnessScore = Math.max(0, 10 - (Math.sqrt(variance) / 10));

        // % of the team currently within healthy capacity (not high_load).
        long healthy = devs.stream()
            .filter(d -> !"high_load".equals(d.getAvailability()))
            .count();
        double allocationEfficiency = devs.isEmpty() ? 0.0
            : (healthy * 100.0) / devs.size();

        List<Map<String,Object>> devSummaries = devs.stream().map(d -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("name", d.getName());
            row.put("workload_percent", d.getWorkloadPercent());
            row.put("availability", d.getAvailability());
            return row;
        }).toList();

        String mcdmAlert = devs.stream()
            .max(Comparator.comparingInt(Developer::getWorkloadPercent))
            .filter(d -> d.getWorkloadPercent() >= 80)
            .map(d -> "The decision engine has applied a workload penalty on " + d.getName()
                + ". Upcoming predictions will rank them lower to prevent overload fatigue.")
            .orElse("No developer is currently overloaded - no burnout penalties are active.");

        Map<String,Object> data = new LinkedHashMap<>();
        data.put("avg_load", Math.round(avgLoad * 10) / 10.0);
        data.put("avg_load_label", avgLoad >= 80 ? "High" : avgLoad >= 50 ? "Optimal" : "Light");
        data.put("overload_risk_percent", Math.round(overloadRiskPercent * 10) / 10.0);
        data.put("at_risk_count", atRisk);
        data.put("allocation_efficiency", Math.round(allocationEfficiency * 10) / 10.0);
        data.put("allocation_efficiency_label", "of team within healthy capacity");
        data.put("fairness_score", Math.round(fairnessScore * 10) / 10.0);
        data.put("fairness_label", fairnessScore >= 8 ? "Excellent" : fairnessScore >= 6 ? "Good" : "Needs attention");
        data.put("developers", devSummaries);
        data.put("mcdm_alert", mcdmAlert);

        return Map.of("success", true, "data", data);
    }
}
