package dev.astron.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.List;

// @Data (from Lombok) auto-generates getters, setters, toString(),
// equals(), and hashCode() for us, so we don't have to write them by hand.
@Data
// @Document tells Spring Data MongoDB that this class maps to documents
// in the "developers" collection.
@Document(collection = "developers")
public class Developer {

    // @Id marks this field as the document's unique MongoDB _id.
    // We call it mongoId (not "id") because the documents ALSO have
    // their own separate "id" field (like "dev-001") that we want to
    // keep as a normal, plain field below.
    @Id
    private String mongoId;

    private String id;
    private String name;
    private String initials;
    private String role;
    private String email;
    private List<String> skills;
    private String availability;

    // CRITICAL: MongoDB stores snake_case, Java uses camelCase.
    // @Field maps the DB field, @JsonProperty maps the JSON output.
    // Both are needed.

    // @Field("workload_percent") tells Spring Data MongoDB: when reading
    // from / writing to the database, use the name "workload_percent"
    // instead of the Java field name "workloadPercent".
    //
    // @JsonProperty("workload_percent") tells Jackson (the library that
    // turns this object into JSON for our API responses): when writing
    // this out as JSON, also use "workload_percent" as the key name.
    @Field("workload_percent")
    @JsonProperty("workload_percent")
    private int workloadPercent;

    @Field("active_tasks")
    @JsonProperty("active_tasks")
    private int activeTasks;

    @Field("completed_tasks")
    @JsonProperty("completed_tasks")
    private int completedTasks;

    @Field("avg_accuracy")
    @JsonProperty("avg_accuracy")
    private double avgAccuracy;

    @Field("perf_score")
    @JsonProperty("perf_score")
    private double perfScore;

    @Field("capacity_hours")
    @JsonProperty("capacity_hours")
    private int capacityHours;
}
