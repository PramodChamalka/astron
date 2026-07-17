package dev.astron.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "assignments")
public class Assignment {

    @Id
    @JsonIgnore
    private String mongoId;

    private String id;   // ASG-001

    @Field("task_id")
    @JsonProperty("task_id")
    private String taskId;

    @Field("task_title")
    @JsonProperty("task_title")
    private String taskTitle;

    @Field("developer_id")
    @JsonProperty("developer_id")
    private String developerId;

    @Field("developer_name")
    @JsonProperty("developer_name")
    private String developerName;

    @Field("developer_initials")
    @JsonProperty("developer_initials")
    private String developerInitials;

    // Did the manager accept the AI's pick, or override it?
    // This is the Human-in-the-Loop record.
    @Field("hitl_decision")
    @JsonProperty("hitl_decision")
    private String hitlDecision;   // AI Approved | Override (PM Selected)

    @Field("assignment_type")
    @JsonProperty("assignment_type")
    private String assignmentType; // AI Recommended | Manual (PM)

    @Field("mcdm_score")
    @JsonProperty("mcdm_score")
    private Double mcdmScore;

    @Field("predicted_hours")
    @JsonProperty("predicted_hours")
    private Double predictedHours;

    @Field("assigned_by")
    @JsonProperty("assigned_by")
    private String assignedBy;

    @Field("assigned_at")
    @JsonProperty("assigned_at")
    private String assignedAt;
}
