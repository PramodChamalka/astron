package dev.astron.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.List;

@Data
@Document(collection = "tasks")
public class Task {

    @Id
    @JsonIgnore
    private String mongoId;

    private String id;              // TASK-101
    private String title;
    private String category;
    private String priority;        // Low | Normal | High | Urgent
    private Integer complexity;
    private String status;          // To Do | In Progress | In Review | Completed | Blocked
    private String deadline;

    @Field("skills_required")
    @JsonProperty("skills_required")
    private List<String> skillsRequired;

    @Field("predicted_hours")
    @JsonProperty("predicted_hours")
    private Double predictedHours;

    // null until the task is completed
    @Field("actual_hours")
    @JsonProperty("actual_hours")
    private Double actualHours;

    @Field("assigned_to")
    @JsonProperty("assigned_to")
    private String assignedTo;      // dev-001

    @Field("assigned_to_name")
    @JsonProperty("assigned_to_name")
    private String assignedToName;

    @Field("assigned_to_initials")
    @JsonProperty("assigned_to_initials")
    private String assignedToInitials;

    @Field("assigned_by")
    @JsonProperty("assigned_by")
    private String assignedBy;      // usr-001

    @Field("assignment_type")
    @JsonProperty("assignment_type")
    private String assignmentType;  // AI Recommended | Manual (PM)

    @Field("created_at")
    @JsonProperty("created_at")
    private String createdAt;

    @Field("completed_at")
    @JsonProperty("completed_at")
    private String completedAt;
}
