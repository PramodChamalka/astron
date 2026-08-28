package dev.astron.backend.repository;

import dev.astron.backend.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UserRepository extends MongoRepository<User, String> {

    User findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("{ 'id': ?0 }")
    User findByIdField(String id);
}
