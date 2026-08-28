package dev.astron.backend.controller;

import dev.astron.backend.model.Developer;
import dev.astron.backend.repository.DeveloperRepository;
import dev.astron.backend.util.IdSequence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// @RestController tells Spring: this class handles HTTP requests, and
// every method's return value should be converted straight to JSON
// (instead of, say, an HTML page).
@RestController
// @RequestMapping sets the base URL path for every endpoint in this
// class. So the method below actually answers GET /api/developers.
@RequestMapping("/api/developers")
public class DeveloperController {

    @Autowired
    private DeveloperRepository repo;

    @Autowired
    private IdSequence idSequence;

    // @GetMapping (with no path) means this method runs when someone
    // sends a GET request to exactly /api/developers.
    @GetMapping
    public Map<String, Object> getAll() {
        List<Developer> devs = repo.findAll();
        return Map.of("success", true, "data", devs);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Developer body) {
        if (body == null) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "error", "Developer payload is required"));
        }

        if (body.getName() == null || body.getName().isBlank()) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "error", "Name is required"));
        }

        if (body.getEmail() == null || body.getEmail().isBlank()) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "error", "Email is required"));
        }

        if (repo.findByEmail(body.getEmail()) != null) {
            return ResponseEntity.status(409).body(Map.of(
                "success", false,
                "error", "Developer with this email already exists"));
        }

        String name = body.getName().trim();
        String initials = name.split("\\s+").length > 1
            ? String.valueOf(name.charAt(0)) + name.split("\\s+")[1].charAt(0)
            : String.valueOf(name.charAt(0));
        body.setInitials(initials.toUpperCase());

        List<String> issuedIds = repo.findAll().stream()
            .map(Developer::getId)
            .toList();
        body.setId("dev-" + idSequence.next("developers", issuedIds, "dev-", 1));

        body.setAvailability(body.getAvailability() == null ? "available" : body.getAvailability());
        body.setWorkloadPercent(body.getWorkloadPercent() == 0 ? 0 : body.getWorkloadPercent());
        body.setActiveTasks(0);
        body.setCompletedTasks(0);
        body.setAvgAccuracy(0.0);
        body.setPerfScore(body.getPerfScore() == 0 ? 0 : body.getPerfScore());
        body.setCapacityHours(body.getCapacityHours() == 0 ? 40 : body.getCapacityHours());

        Developer saved = repo.save(body);
        return ResponseEntity.ok(Map.of("success", true, "data", saved));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        Developer dev = repo.findByEmail(authentication.getName());
        if (dev == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", "No developer profile is linked to this account"));
        }
        return ResponseEntity.ok(Map.of("success", true, "data", dev));
    }
}
