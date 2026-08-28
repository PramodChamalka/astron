package dev.astron.backend.repository;

import dev.astron.backend.model.Project;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ProjectRepository extends MongoRepository<Project, String> {

    @Query("{ 'id': ?0 }")
    Project findByProjectId(String id);

    @Query("{ 'code': ?0 }")
    Project findByCode(String code);

    boolean existsByCode(String code);
}
