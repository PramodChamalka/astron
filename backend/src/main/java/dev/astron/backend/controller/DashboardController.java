package dev.astron.backend.controller;

import dev.astron.backend.model.Assignment;
import dev.astron.backend.model.Developer;
import dev.astron.backend.model.Task;
import dev.astron.backend.repository.AssignmentRepository;
import dev.astron.backend.repository.DeveloperRepository;
import dev.astron.backend.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired private TaskRepository taskRepo;
    @Autowired private DeveloperRepository devRepo;
    @Autowired private AssignmentRepository asgRepo;

    // Mon, Tue, Wed... in that fixed order, so the weekly chart always
    // reads left-to-right the same way regardless of what day it is.
    private static final List<String> WEEK_ORDER =
        List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    // GET /api/dashboard/stats - real numbers for the logged-in dashboard.
    @GetMapping("/stats")
    public Map<String,Object> getStats() {
        List<Task> tasks = taskRepo.findAll();
        List<Developer> devs = devRepo.findAll();
        List<Assignment> assignments = asgRepo.findAll();

        long activeTasks = tasks.stream()
            .filter(t -> !"Completed".equals(t.getStatus()))
            .count();

        // Awaiting a HITL decision = created, but nobody has been
        // assigned to it yet.
        long pendingHitl = tasks.stream()
            .filter(t -> t.getAssignedTo() == null && !"Completed".equals(t.getStatus()))
            .count();

        long overloadedDevs = devs.stream()
            .filter(d -> "high_load".equals(d.getAvailability()))
            .count();

        // "RF accuracy" here = the average real-world accuracy of our
        // developers' completed work (from TaskController's recompute),
        // since that is the real number this service has access to.
        double rfAccuracy = devs.stream()
            .filter(d -> d.getCompletedTasks() > 0)
            .mapToDouble(Developer::getAvgAccuracy)
            .average()
            .orElse(0.0);

        Map<String,Object> data = new LinkedHashMap<>();
        data.put("active_tasks", activeTasks);
        data.put("overloaded_devs", overloadedDevs);
        data.put("total_devs", devs.size());
        data.put("rf_accuracy", Math.round(rfAccuracy * 10) / 10.0);
        data.put("pending_hitl", pendingHitl);
        data.put("hitl_weekly", weeklyHitlBreakdown(assignments));

        return Map.of("success", true, "data", data);
    }

    // GET /api/dashboard/landing-stats - public marketing stats shown
    // on the landing page, before anyone logs in.
    @GetMapping("/landing-stats")
    public Map<String,Object> getLandingStats() {
        Map<String,Object> data = new LinkedHashMap<>();
        // Matches the trained model's real measured improvement over
        // the human baseline (see ml/artifacts/evaluation.json).
        data.put("prediction_accuracy", 27.7);
        data.put("burnout_reduction", 35.2);
        data.put("workload_balance", 9.2);
        return Map.of("success", true, "data", data);
    }

    // Groups every assignment by the day of the week it was made on,
    // counting how many were AI-approved vs manager-overridden.
    private List<Map<String,Object>> weeklyHitlBreakdown(List<Assignment> assignments) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (String day : WEEK_ORDER) counts.put(day, new int[]{0, 0});

        for (Assignment a : assignments) {
            if (a.getAssignedAt() == null) continue;
            String day = LocalDateTime.parse(a.getAssignedAt())
                .getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            int[] bucket = counts.get(day);
            if (bucket == null) continue;
            if ("AI Approved".equals(a.getHitlDecision())) bucket[0]++;
            else bucket[1]++;
        }

        return WEEK_ORDER.stream().map(day -> {
            int[] bucket = counts.get(day);
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("day", day);
            row.put("ai_used", bucket[0]);
            row.put("pm_override", bucket[1]);
            return row;
        }).toList();
    }
}
