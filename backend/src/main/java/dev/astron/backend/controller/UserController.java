package dev.astron.backend.controller;

import dev.astron.backend.model.User;
import dev.astron.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired private UserRepository userRepo;

    // GET /api/users - list all users (Admin only, enforced by SecurityConfig)
    @GetMapping
    public Map<String,Object> getAll() {
        List<User> users = userRepo.findAll();
        return Map.of("success", true, "data", users);
    }

    // PUT /api/users/{id}/status - approve or reject a pending user
    // body: { "status": "active" }  or  { "status": "rejected" }
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String,String> body) {

        String newStatus = body.get("status");
        if (!List.of("active","pending","rejected").contains(newStatus)) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false, "error", "Invalid status"));
        }

        User user = userRepo.findByIdField(id);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false, "error", "User not found"));
        }

        user.setStatus(newStatus);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("success", true, "data", user));
    }

    // PUT /api/users/{id}/role - assign a role
    // body: { "role": "Manager" }
    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(
            @PathVariable String id,
            @RequestBody Map<String,String> body) {

        String newRole = body.get("role");
        if (!List.of("Admin","Manager","Developer","Viewer").contains(newRole)) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false, "error", "Invalid role"));
        }

        User user = userRepo.findByIdField(id);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false, "error", "User not found"));
        }

        user.setRole(newRole);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("success", true, "data", user));
    }
}
