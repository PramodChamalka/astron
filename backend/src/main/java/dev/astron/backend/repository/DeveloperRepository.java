package dev.astron.backend.repository;

import dev.astron.backend.model.Developer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

// @Repository marks this as a Spring "data access" component, so Spring
// knows to create and manage an instance of it for us automatically.
//
// Extending MongoRepository<Developer, String> gives us a full set of
// ready-made database methods for free - findAll(), findById(), save(),
// deleteById(), etc. - without writing any query code ourselves.
// Developer = the type of object we're storing.
// String    = the type of that object's @Id field (mongoId).
@Repository
public interface DeveloperRepository
        extends MongoRepository<Developer, String> {
}
