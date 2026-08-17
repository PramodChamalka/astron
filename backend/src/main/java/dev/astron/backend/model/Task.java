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

    // The band (Low/Medium/High/Very High) Flask's complexity.py derives
    // from the RAW, unrounded score - never re-derive this from the
    // rounded "complexity" integer above, since that can land on the
    // wrong side of a band boundary (e.g. a raw score of 7.6 is "Low",
    // but Math.round(7.6) = 8 would wrongly re-bucket as "Medium").
    @Field("complexity_level")
    @JsonProperty("complexity_level")
    private String complexityLevel;

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

    // Every task belongs to a project. The code and name are copied in
    // alongside the id so lists and tables can show them without a
    // second lookup - the same reason Assignment stores developer_name.
    @Field("project_id")
    @JsonProperty("project_id")
    private String projectId;       // proj-a1b2c3d4

    @Field("project_code")
    @JsonProperty("project_code")
    private String projectCode;     // PC21

    @Field("project_name")
    @JsonProperty("project_name")
    private String projectName;

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
