package dev.astron.backend.repository;

import dev.astron.backend.model.Project;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ProjectRepository extends MongoRepository<Project, String> {

    // Same trick as UserRepository.findByIdField: "findByProjectId" can't
    // be derived from the method name, so @Query spells out the MongoDB
    // query - match our own "id" field (?0), not the mongo _id.
    @Query("{ 'id': ?0 }")
    Project findByProjectId(String id);

    @Query("{ 'code': ?0 }")
    Project findByCode(String code);

    // Spring generates this one from the method name automatically.
    boolean existsByCode(String code);
}
