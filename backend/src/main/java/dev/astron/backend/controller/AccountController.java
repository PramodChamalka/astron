package dev.astron.backend.controller;

import dev.astron.backend.model.Developer;
import dev.astron.backend.model.User;
import dev.astron.backend.repository.DeveloperRepository;
import dev.astron.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Self-service account management: a logged-in user's OWN profile and
// password. Deliberately NOT under /api/users/** - SecurityConfig
// locks that whole path to Admin only, and this needs to work for
// every role. Every method here resolves its target from the JWT's
// "sub" claim (the caller's own email, via Authentication.getName())
// and NEVER from a client-supplied id, so there is no path by which
// one account can edit another's record.
@RestController
@RequestMapping("/api/account")
public class AccountController {

    @Autowired private UserRepository userRepo;
    @Autowired private DeveloperRepository devRepo;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // PUT /api/account/profile - update the CALLER's own name, and (if
    // this account is linked to a Developer record by email) their
    // skills and job-title text too. User.name and Developer.name are
    // two separate fields in two separate collections - kept in sync
    // here so the dashboard greeting and the sidebar never disagree.
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            Authentication authentication,
            @RequestBody Map<String,Object> body) {

        User user = userRepo.findByEmail(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false, "error", "Account not found"));
        }

        Object nameObj = body.get("name");
        String name = nameObj == null ? null : nameObj.toString().trim();
        if (name == null || name.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false, "error", "Name is required"));
        }

        user.setName(name);
        userRepo.save(user);

        // Skills/role-title only mean anything for an account that IS
        // a developer. No linked Developer record just means these
        // fields are silently no-ops - the frontend won't show them
        // to non-developer accounts in the first place.
        Developer dev = devRepo.findByEmail(authentication.getName());
        if (dev != null) {
            dev.setName(name);

            Object skillsObj = body.get("skills");
            if (skillsObj instanceof List<?> rawSkills) {
                List<String> skills = rawSkills.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
                dev.setSkills(skills);
            }

            Object roleTitleObj = body.get("role_title");
            if (roleTitleObj != null && !roleTitleObj.toString().trim().isEmpty()) {
                dev.setRole(roleTitleObj.toString().trim());
            }

            devRepo.save(dev);
        }

        Map<String,Object> data = new LinkedHashMap<>();
        data.put("user", user);
        data.put("developer", dev);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    // PUT /api/account/password - change the CALLER's own password.
    // Verifies the current password before setting the new one, so
    // a stolen/left-open session can't silently take over the account
    // even though the request itself is already authenticated.
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @RequestBody Map<String,String> body) {

        User user = userRepo.findByEmail(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false, "error", "Account not found"));
        }

        String currentPassword = body.get("current_password");
        String newPassword = body.get("new_password");

        if (currentPassword == null || !encoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false, "error", "Current password is incorrect"));
        }
        if (newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.status(400).body(Map.of(
                "success", false, "error", "New password must be at least 8 characters"));
        }

        user.setPassword(encoder.encode(newPassword));
        userRepo.save(user);

        return ResponseEntity.ok(Map.of("success", true));
    }
}
