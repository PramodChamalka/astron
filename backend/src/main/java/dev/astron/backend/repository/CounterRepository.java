package dev.astron.backend.repository;

import dev.astron.backend.model.Counter;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CounterRepository extends MongoRepository<Counter, String> {
    // The series name IS the mongo _id, so the inherited findById(String)
    // is all we need here.
}
