package dev.astron.backend.repository;

import dev.astron.backend.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UserRepository extends MongoRepository<User, String> {

    // Spring generates these queries automatically from the method names
    User findByEmail(String email);
    boolean existsByEmail(String email);

    // Spring Data couldn't derive this one from the method name alone -
    // "findByIdField" parses as "id" + "Field", and String has no "field"
    // property to traverse. @Query spells out the MongoDB query directly:
    // find the document whose "id" field matches the first argument (?0).
    @Query("{ 'id': ?0 }")
    User findByIdField(String id);
}
