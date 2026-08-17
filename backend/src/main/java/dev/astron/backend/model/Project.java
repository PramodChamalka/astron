package dev.astron.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "projects")
public class Project {

    @Id
    @JsonIgnore
    private String mongoId;

    private String id;            // proj-a1b2c3d4
    private String code;          // PC21 - unique, required
    private String name;          // required
    private String description;   // optional
    private String deadline;      // optional

    // MongoDB stores snake_case, Java uses camelCase - @Field maps the
    // DB field and @JsonProperty maps the JSON output. Both are needed.
    @Field("created_at")
    @JsonProperty("created_at")
    private String createdAt;

    @Field("created_by")
    @JsonProperty("created_by")
    private String createdBy;     // usr-001
}
