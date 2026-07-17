package dev.astron.backend.controller;

import dev.astron.backend.model.*;
import dev.astron.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    @Autowired private AssignmentRepository asgRepo;
    @Autowired private TaskRepository taskRepo;
    @Autowired private DeveloperRepository devRepo;

    @GetMapping
    public Map<String,Object> getAll() {
        return Map.of("success", true, "data", asgRepo.findAll());
    }

    // ============================================================
    // POST /api/assignments
    // The manager confirms who gets the task. This is the HITL
    // decision point - they either accept the AI's recommendation
    // or override it, and we record which.
    //
    // CRITICAL SIDE EFFECT: the developer's workload goes UP.
    // Flask's MCDM reads that new number, so this developer will
    // rank LOWER next time. That is the burnout prevention working.
    // ============================================================
    @PostMapping
    public ResponseEntity<?> confirm(@RequestBody Map<String,Object> body) {

        // Read the two required fields out of the generic JSON body.
        String taskId = (String) body.get("task_id");
        String devId  = (String) body.get("developer_id");

        if (taskId == null || devId == null) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "error", "task_id and developer_id are required"));
        }

        // findByTaskId looks up our own "id" field (like "TASK-101"),
        // NOT MongoDB's internal _id - same trick as in TaskController.
        Task task = taskRepo.findByTaskId(taskId);
        if (task == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false, "error", "Task not found"));
        }

        // Same reason we can't use findById() here: "dev-001" is our
        // own id field, not MongoDB's _id, so we search manually.
        Developer dev = null;
        for (Developer d : devRepo.findAll()) {
            if (devId.equals(d.getId())) { dev = d; break; }
        }
        if (dev == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false, "error", "Developer not found"));
        }

        // 1. Save the assignment record
        // Build a friendly id like "ASG-001", "ASG-002", ...
        long count = asgRepo.count();
        Assignment a = new Assignment();
        a.setId(String.format("ASG-%03d", count + 1));
        a.setTaskId(taskId);
        a.setTaskTitle(task.getTitle());
        a.setDeveloperId(devId);
        a.setDeveloperName(dev.getName());
        a.setDeveloperInitials(dev.getInitials());
        // getOrDefault falls back to a sensible value if the frontend
        // didn't explicitly send hitl_decision / assignment_type.
        a.setHitlDecision((String) body.getOrDefault(
            "hitl_decision", "AI Approved"));
        a.setAssignmentType((String) body.getOrDefault(
            "assignment_type", "AI Recommended"));
        if (body.get("mcdm_score") != null) {
            // Comes in as a generic Object from JSON, so convert to
            // text first, then parse as a Double either way.
            a.setMcdmScore(Double.valueOf(body.get("mcdm_score").toString()));
        }
        a.setPredictedHours(task.getPredictedHours());
        a.setAssignedBy((String) body.get("assigned_by"));
        a.setAssignedAt(LocalDateTime.now().toString());
        asgRepo.save(a);

        // 2. Stamp the developer onto the task
        task.setAssignedTo(devId);
        task.setAssignedToName(dev.getName());
        task.setAssignedToInitials(dev.getInitials());
        task.setAssignmentType(a.getAssignmentType());
        task.setAssignedBy(a.getAssignedBy());
        // Only bump a brand-new task forward. If it was already
        // "In Progress" or further along, leave its status alone.
        if ("To Do".equals(task.getStatus())) {
            task.setStatus("In Progress");
        }
        taskRepo.save(task);

        // 3. THE IMPORTANT BIT - workload goes UP
        // Same 20%-per-active-task formula as TaskController, but in
        // reverse: assigning work INCREASES workload, capped at 100%.
        int newActive = dev.getActiveTasks() + 1;
        int newWorkload = Math.min(100, newActive * 20);

        dev.setActiveTasks(newActive);
        dev.setWorkloadPercent(newWorkload);

        // Same availability thresholds used everywhere else, so the
        // label always matches the workload number.
        if (newWorkload < 50)      dev.setAvailability("available");
        else if (newWorkload < 80) dev.setAvailability("moderate");
        else                       dev.setAvailability("high_load");

        devRepo.save(dev);

        System.out.println(dev.getName() + " workload now "
            + newWorkload + "% (" + dev.getAvailability() + ")");

        return ResponseEntity.ok(Map.of("success", true, "data", a));
    }

    // Stats for the assignment history page
    @GetMapping("/stats")
    public Map<String,Object> stats() {
        List<Assignment> all = asgRepo.findAll();
        int aiApproved = 0, overrides = 0;
        for (Assignment a : all) {
            // Anything that isn't exactly "AI Approved" counts as a
            // manager override, whatever label it uses.
            if ("AI Approved".equals(a.getHitlDecision())) aiApproved++;
            else overrides++;
        }
        // % of assignments where the manager trusted the AI's pick,
        // rounded to 1 decimal place (e.g. 82.5%).
        double rate = all.isEmpty() ? 0
            : Math.round((aiApproved * 1000.0 / all.size())) / 10.0;

        return Map.of("success", true, "data", Map.of(
            "total_assignments", all.size(),
            "ai_approved", aiApproved,
            "overrides", overrides,
            "ai_acceptance_rate", rate
        ));
    }
}
