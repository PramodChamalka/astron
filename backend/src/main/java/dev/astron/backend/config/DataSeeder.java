package dev.astron.backend.config;

import dev.astron.backend.model.User;
import dev.astron.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private UserRepository userRepo;

    // Runs once when the app starts. Creates the bootstrap admin,
    // because nobody exists yet to approve the first account.
    @Override
    public void run(String... args) {
        if (userRepo.count() > 0) {
            System.out.println("Users already exist - skipping seed.");
            return;
        }

        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

        User admin = new User();
        admin.setId("usr-001");
        admin.setName("Prof. Sarah Jenkins");
        admin.setEmail("admin@astron.dev");
        admin.setPassword(enc.encode("admin123"));
        admin.setRole("Admin");
        admin.setStatus("active");
        admin.setInitials("SJ");
        admin.setCreatedAt(LocalDateTime.now().toString());
        userRepo.save(admin);

        User manager = new User();
        manager.setId("usr-002");
        manager.setName("James Taylor");
        manager.setEmail("manager@astron.dev");
        manager.setPassword(enc.encode("manager123"));
        manager.setRole("Manager");
        manager.setStatus("active");
        manager.setInitials("JT");
        manager.setCreatedAt(LocalDateTime.now().toString());
        userRepo.save(manager);

        System.out.println("Seeded admin and manager accounts.");
    }
}
