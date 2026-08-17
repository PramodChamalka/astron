package dev.astron.backend.controller;

import dev.astron.backend.model.Developer;
import dev.astron.backend.model.Task;
import dev.astron.backend.repository.DeveloperRepository;
import dev.astron.backend.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired private TaskRepository taskRepo;
    @Autowired private DeveloperRepository devRepo;

    @Value("${astron.flask.uri}")
    private String flaskUri;

    private final RestClient restClient = RestClient.create();

    // n_estimators is a fixed training hyperparameter (ml/train.py),
    // not a data-derived metric like MAE or feature_importance - it
    // can't silently drift on retrain the way those can, since it's
    // set by the training code itself rather than measured from data.
    // There's also no standalone Flask endpoint that reports it (it's
    // only echoed back per-prediction in ml/predict.py), so unlike
    // training_samples/feature_importance below, there's no live
    // "model metadata" source to defer to here.
    private static final int ESTIMATOR_TREES = 200;

    // GET /api/analytics/predictions?task_id=TASK-101
    // Returns the REAL stored prediction for one task, plus the REAL
    // model-wide training_samples/feature_importance pulled live from
    // Flask's /api/model-evaluation (which itself reads
    // ml/artifacts/evaluation.json) - not hardcoded constants that
    // would silently go stale the next time the model is retrained.
    @GetMapping("/predictions")
    public ResponseEntity<?> getPrediction(
            @RequestParam("task_id") String taskId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

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
        data.put("estimator_trees", ESTIMATOR_TREES);

        Map<String,Object> evaluation = fetchModelEvaluation(authorization);
        if (evaluation != null) {
            data.put("training_samples", evaluation.get("training_samples"));
            data.put("feature_importance", evaluation.get("feature_importance"));
        } else {
            // Flask is unreachable or returned something unusable - say
            // so explicitly rather than silently falling back to old
            // hardcoded numbers that may no longer match the real model.
            data.put("training_samples", null);
            data.put("feature_importance", null);
            data.put("model_metadata_stale", true);
            data.put("model_metadata_error",
                "Could not reach the model evaluation service for live training stats");
        }

        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    // Calls the SAME endpoint the frontend already trusts directly
    // (see api.js getModelEvaluation) - forwards the caller's own JWT,
    // since Flask's token_required only checks the signature against
    // the same shared secret (see JwtUtil), not who the user is.
    // Returns null on any failure so the caller can label the response
    // as stale instead of guessing at a fallback value.
    @SuppressWarnings("unchecked")
    private Map<String,Object> fetchModelEvaluation(String authorization) {
        try {
            Map<String,Object> body = restClient.get()
                .uri(flaskUri + "/model-evaluation")
                .headers(h -> {
                    if (authorization != null) h.set(HttpHeaders.AUTHORIZATION, authorization);
                })
                .retrieve()
                .body(Map.class);

            if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
                return null;
            }
            return (Map<String,Object>) body.get("data");
        } catch (RestClientException | ClassCastException e) {
            return null;
        }
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
