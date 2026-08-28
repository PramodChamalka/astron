package dev.astron.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.List;

@Data
@Document(collection = "developers")
public class Developer {

    @Id
    @JsonIgnore
    private String mongoId;

    private String id;
    private String name;
    private String initials;
    private String role;
    private String email;
    private List<String> skills;
    private String availability;

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
