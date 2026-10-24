package com.quickdeliver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Mirrors the Mongoose "User" model (collection: users). */
@Document(collection = "users")
public class User {

    @Id
    @JsonProperty("_id")
    private String id;

    private String name;

    @Indexed(unique = true, name = "email_1")
    private String email;

    @JsonIgnore
    private String password;

    private String phone = "";

    private String role = "customer";

    private String address = "";

    private Boolean isActive = true;

    private String agentStatus = "offline";

    private Instant createdAt = Instant.now();

    /** Accounts created by older tooling may lack the flag; Mongoose defaulted it to true. */
    @JsonIgnore
    public boolean activeAccount() { return isActive == null || isActive; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getAgentStatus() { return agentStatus; }
    public void setAgentStatus(String agentStatus) { this.agentStatus = agentStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
