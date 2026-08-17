package dev.astron.backend.controller;

import dev.astron.backend.model.Developer;
import dev.astron.backend.model.Project;
import dev.astron.backend.model.Task;
import dev.astron.backend.repository.DeveloperRepository;
import dev.astron.backend.repository.ProjectRepository;
import dev.astron.backend.repository.TaskRepository;
import dev.astron.backend.util.IdSequence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired private TaskRepository taskRepo;
    @Autowired private DeveloperRepository devRepo;
    @Autowired private ProjectRepository projectRepo;
    @Autowired private IdSequence idSequence;

    // GET /api/tasks - all tasks
    @GetMapping
    public Map<String,Object> getAll() {
        return Map.of("success", true, "data", taskRepo.findAll());
    }

    // GET /api/tasks/my - tasks assigned to the logged-in developer
    // JwtAuthFilter puts the token's "sub" claim (the user's EMAIL) in as
    // the principal, same as ProjectController's currentUserId. Developer
    // records aren't linked to User accounts by id anywhere in this app -
    // the only field they share is email, so that's what we match on.
    // No matching Developer (e.g. an Admin/Manager account, or a Developer
    // whose email doesn't line up with a seeded Developer record) just
    // means no assigned tasks, not an error.
    @GetMapping("/my")
    public Map<String,Object> getMyTasks(Authentication authentication) {
        Developer dev = devRepo.findByEmail(authentication.getName());
        if (dev == null) {
            return Map.of("success", true, "data", List.of());
        }
        return Map.of("success", true, "data", taskRepo.findByAssignedTo(dev.getId()));
    }

    // POST /api/tasks - create a task
    // The frontend calls Flask first for the prediction, then sends
    // the result here to be saved.
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Task body) {

        // @RequestBody already turned the incoming JSON into a Task
        // object for us. We just need to make sure it has a title.
        if (body.getTitle() == null || body.getTitle().isBlank()) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false, "error", "Title is required"));
        }

        // Every task must belong to a project - there is no "loose" task.
        if (body.getProjectId() == null || body.getProjectId().isBlank()) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false, "error", "A project is required"));
        }

        // findByProjectId matches our own "id" field ("proj-a1b2c3d4"),
        // not MongoDB's _id - same pattern as findByTaskId below.
        Project project = projectRepo.findByProjectId(body.getProjectId());
        if (project == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false, "error", "Project not found"));
        }

        // Copy the display fields in from the project itself, so the
        // client can't send a code or name that disagrees with it.
        body.setProjectCode(project.getCode());
        body.setProjectName(project.getName());

        // Build a friendly task id like "TASK-101", "TASK-102", ...
        // Counting documents here would reuse the id of a deleted task
        // and collide with assignment/audit records that still name it,
        // so IdSequence keeps a high-water mark that only moves up.
        List<String> issuedIds = taskRepo.findAll().stream()
            .map(Task::getId)
            .toList();
        body.setId("TASK-" + idSequence.next("tasks", issuedIds, "TASK-", 101));

        // A brand-new task always starts in these default states,
        // no matter what the frontend sent us.
        body.setStatus("To Do");
        body.setActualHours(null);
        body.setCompletedAt(null);
        body.setCreatedAt(LocalDateTime.now().toString());

        // save() inserts the document into MongoDB and returns the
        // saved copy (now including its generated mongoId).
        Task saved = taskRepo.save(body);
        return ResponseEntity.ok(Map.of("success", true, "data", saved));
    }

    // ============================================================
    // PUT /api/tasks/{id}/status
    // THIS IS THE HEARTBEAT OF THE WHOLE SYSTEM.
    // When a task completes, the developer's experience grows,
    // their accuracy is recomputed, and their workload drops.
    // Those new numbers feed straight into the MCDM ranking in Flask.
    // ============================================================
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            // @PathVariable pulls "TASK-101" out of the URL itself,
            // e.g. PUT /api/tasks/TASK-101/status
            @PathVariable String id,
            // @RequestBody here is just a generic Map, since we only
            // care about reading one field ("status") out of the JSON.
            @RequestBody Map<String,Object> body) {

        // findByTaskId looks up our own "id" field (like "TASK-101"),
        // NOT MongoDB's internal _id.
        Task task = taskRepo.findByTaskId(id);
        if (task == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false, "error", "Task not found"));
        }

        String newStatus = (String) body.get("status");
        task.setStatus(newStatus);

        // Only when a task is COMPLETED do we update the developer
        if ("Completed".equals(newStatus)) {

            // the developer logs how long it really took
            if (body.get("actual_hours") != null) {
                // The value arrives as a generic Object (from JSON),
                // so we convert it to text first, then parse it as
                // a Double, whether the frontend sent a number or a string.
                double actualHours = Double.valueOf(body.get("actual_hours").toString());
                if (actualHours < 0) {
                    return ResponseEntity.status(400).body(Map.of(
                        "success", false, "error", "actual_hours cannot be negative"));
                }
                task.setActualHours(actualHours);
            }
            task.setCompletedAt(LocalDateTime.now().toString());
            taskRepo.save(task);

            // This is where the "heartbeat" happens - recompute the
            // assigned developer's stats now that they finished something.
            updateDeveloperStats(task.getAssignedTo());
        } else {
            // Any other status change (e.g. "In Progress") just saves
            // as-is, no developer stats to update yet.
            taskRepo.save(task);
        }

        return ResponseEntity.ok(Map.of("success", true, "data", task));
    }

    // Recalculates a developer's experience, accuracy, and workload
    // from their real task history.
    private void updateDeveloperStats(String developerId) {
        // A task might not be assigned to anyone - nothing to update.
        if (developerId == null) return;

        // Look through every developer to find the one whose "id"
        // field (like "dev-001") matches. We can't use findById()
        // here because that only searches by MongoDB's own _id.
        Developer dev = null;
        for (Developer d : devRepo.findAll()) {
            if (developerId.equals(d.getId())) { dev = d; break; }
        }
        if (dev == null) return;

        // Every task this developer has ever finished.
        List<Task> completed = taskRepo.findCompletedByDeveloper(developerId);

        // 1. EXPERIENCE: how many tasks have they finished?
        dev.setCompletedTasks(completed.size());

        // 2. ACCURACY: how close were predictions to reality?
        double totalError = 0;
        int counted = 0;
        for (Task t : completed) {
            // Only tasks that have BOTH a prediction and a real result
            // (and didn't actually take 0 hours) can be scored.
            if (t.getPredictedHours() != null && t.getActualHours() != null
                    && t.getActualHours() > 0) {
                // % error = how far off the prediction was, as a
                // fraction of the real time it took.
                double err = Math.abs(t.getPredictedHours() - t.getActualHours())
                             / t.getActualHours();
                totalError += err;
                counted++;
            }
        }
        if (counted > 0) {
            // Average error across all scoreable tasks, turned into
            // an accuracy percentage (100% = zero error).
            double avgError = totalError / counted;
            double accuracy = Math.max(0, (1 - avgError) * 100);
            // Round to 1 decimal place, e.g. 96.2
            dev.setAvgAccuracy(Math.round(accuracy * 10) / 10.0);
        }

        // 3. WORKLOAD: count their unfinished tasks
        List<Task> theirTasks = taskRepo.findByAssignedTo(developerId);
        int active = 0;
        for (Task t : theirTasks) {
            if (!"Completed".equals(t.getStatus())) active++;
        }
        dev.setActiveTasks(active);

        // Simple formula: each active task = 20% workload, capped at 100%.
        int workload = Math.min(100, active * 20);
        dev.setWorkloadPercent(workload);

        // Translate the workload number into a human-friendly label,
        // which is what the MCDM burnout penalty (in Flask) reads.
        if (workload < 50)       dev.setAvailability("available");
        else if (workload < 80)  dev.setAvailability("moderate");
        else                     dev.setAvailability("high_load");

        devRepo.save(dev);

        System.out.println("Updated " + dev.getName()
            + " - completed: " + dev.getCompletedTasks()
            + ", accuracy: " + dev.getAvgAccuracy()
            + "%, workload: " + dev.getWorkloadPercent() + "%");
    }
}
