package dev.astron.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "users")
public class User {

    @Id
    @JsonIgnore
    private String mongoId;

    private String id;
    private String name;
    private String email;

    // NEVER send the password hash to the frontend
    @JsonIgnore
    private String password;

    private String role;      // Admin | Manager | Developer | Viewer
    private String status;    // active | pending | rejected
    private String initials;

    @Field("created_at")
    @JsonProperty("created_at")
    private String createdAt;

    @Field("last_login")
    @JsonProperty("last_login")
    private String lastLogin;
}
