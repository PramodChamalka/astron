package dev.astron.backend.repository;

import dev.astron.backend.model.Assignment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface AssignmentRepository
        extends MongoRepository<Assignment, String> {

    @Query("{ 'task_id': ?0 }")
    Assignment findByTaskId(String taskId);

    @Query("{ 'developer_id': ?0 }")
    List<Assignment> findByDeveloperId(String developerId);
}
