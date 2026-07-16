package dev.astron.backend.controller;

import dev.astron.backend.config.JwtUtil;
import dev.astron.backend.model.User;
import dev.astron.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private UserRepository userRepo;
    @Autowired private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // Which pages each role can see. The frontend uses this to build
    // the sidebar. It is UX only - the server still checks roles itself.
    private static final Map<String, List<String>> PERMISSIONS = Map.of(
        "Admin", List.of("dashboard","new-task","developer-pool","ai-predictions",
            "mcdm","hitl","workload","assignments","all-tasks","reports",
            "settings","user-management"),
        "Manager", List.of("dashboard","new-task","developer-pool","mcdm",
            "hitl","assignments","workload","all-tasks"),
        "Developer", List.of("dashboard","workload","assignments","all-tasks"),
        "Viewer", List.of("dashboard","developer-pool","workload","all-tasks")
    );

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String,String> body) {
        String email = body.get("email");
        String password = body.get("password");

        User user = userRepo.findByEmail(email);

        // Same message for wrong email and wrong password, so an
        // attacker cannot discover which emails exist.
        if (user == null || !encoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of(
                "success", false, "error", "Invalid email or password"));
        }

        if ("pending".equals(user.getStatus())) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", "Your account is awaiting admin approval"));
        }
        if ("rejected".equals(user.getStatus())) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", "Your account access was denied"));
        }

        String token = jwtUtil.generateToken(
            user.getEmail(), user.getRole(), user.getId());

        user.setLastLogin(LocalDateTime.now().toString());
        userRepo.save(user);

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", user.getId());
        out.put("name", user.getName());
        out.put("email", user.getEmail());
        out.put("role", user.getRole());
        out.put("initials", user.getInitials());
        out.put("permissions", PERMISSIONS.get(user.getRole()));
        out.put("token", token);

        return ResponseEntity.ok(Map.of("success", true, "user", out));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String,String> body) {
        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");

        if (name == null || email == null || password == null) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false, "error", "Name, email and password required"));
        }

        if (userRepo.existsByEmail(email)) {
            return ResponseEntity.status(409).body(Map.of(
                "success", false, "error", "An account with this email exists"));
        }

        User u = new User();
        u.setId("usr-" + UUID.randomUUID().toString().substring(0, 8));
        u.setName(name);
        u.setEmail(email);
        u.setPassword(encoder.encode(password));   // hashed, never plain

        // NOTE: role is NOT taken from the request. Anyone could send
        // "Admin" otherwise. Admin assigns the real role on approval.
        u.setRole("Developer");
        u.setStatus("pending");
        u.setInitials(makeInitials(name));
        u.setCreatedAt(LocalDateTime.now().toString());
        u.setLastLogin(null);

        userRepo.save(u);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Registration submitted. An admin will review your account."));
    }

    // "Grace Hopper" -> "GH"
    private String makeInitials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length-1].charAt(0)).toUpperCase();
    }
}
