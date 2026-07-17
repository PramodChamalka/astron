package dev.astron.backend.controller;

import dev.astron.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Minimal Admin-only endpoint so SecurityConfig's
// "/api/users/**" -> hasRole("Admin") rule has something real to guard.
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired private UserRepository userRepo;

    // GET /api/users - list every user account.
    // User.password is @JsonIgnore'd, so hashes never leave the server.
    @GetMapping
    public Map<String,Object> getAll() {
        return Map.of("success", true, "data", userRepo.findAll());
    }
}
