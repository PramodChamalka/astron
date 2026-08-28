package dev.astron.backend.repository;

import dev.astron.backend.model.Developer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeveloperRepository
        extends MongoRepository<Developer, String> {

    Developer findByEmail(String email);
}
