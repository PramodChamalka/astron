package dev.astron.backend.repository;

import dev.astron.backend.model.Task;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface TaskRepository extends MongoRepository<Task, String> {

    // Spring builds these queries from the method names
    List<Task> findByAssignedTo(String developerId);
    List<Task> findByStatus(String status);

    // our own "id" field, not the mongo _id
    @Query("{ 'id': ?0 }")
    Task findByTaskId(String id);

    // for recomputing accuracy: completed tasks by one developer
    @Query("{ 'assigned_to': ?0, 'status': 'Completed' }")
    List<Task> findCompletedByDeveloper(String developerId);
}
