package dev.astron.backend.controller;

import dev.astron.backend.model.Project;
import dev.astron.backend.model.User;
import dev.astron.backend.repository.ProjectRepository;
import dev.astron.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired private ProjectRepository projectRepo;
    @Autowired private UserRepository userRepo;

    // GET /api/projects - list all (any authenticated user)
    @GetMapping
    public Map<String,Object> getAll() {
        return Map.of("success", true, "data", projectRepo.findAll());
    }

    // GET /api/projects/{id} - one project by our own "proj-..." id
    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable String id) {
        Project project = projectRepo.findByProjectId(id);
        if (project == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false, "error", "Project not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "data", project));
    }

    // POST /api/projects - create a project
    // Restricted to Admin/Manager in SecurityConfig, same as POST /api/tasks.
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Project body,
                                    Authentication authentication) {

        // Code and name are the two fields a project can't do without.
        if (body.getCode() == null || body.getCode().isBlank()) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false, "error", "Code is required"));
        }
        if (body.getName() == null || body.getName().isBlank()) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false, "error", "Name is required"));
        }

        // Project codes are how tasks will refer to their project, so two
        // projects sharing one code would make that reference ambiguous.
        if (projectRepo.existsByCode(body.getCode())) {
            return ResponseEntity.status(409).body(Map.of(
                "success", false,
                "error", "A project with code " + body.getCode() + " already exists"));
        }

        // Same short-uuid id format AuthController uses for users.
        body.setId("proj-" + UUID.randomUUID().toString().substring(0, 8));
        body.setCreatedAt(LocalDateTime.now().toString());
        body.setCreatedBy(currentUserId(authentication));

        Project saved = projectRepo.save(body);
        return ResponseEntity.ok(Map.of("success", true, "data", saved));
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String email = authentication.getName();
        User user = userRepo.findByEmail(email);
        return user != null ? user.getId() : email;
    }
}
