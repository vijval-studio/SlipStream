package com.example.SlipStream.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class Workspace {
    private String id; // Assuming the ID field is named 'id'
    private String name;
    private String owner; // User email or ID
    private List<String> members; // List of user emails or IDs
    private List<String> rootPageIds; // IDs of top-level pages in this workspace
    private Date createdAt;
    private Date lastUpdated;

    // Default constructor for Firestore
    public Workspace() {
        this.members = new ArrayList<>();
        this.rootPageIds = new ArrayList<>();
        this.createdAt = new Date();
        this.lastUpdated = new Date();
    }

    // Constructor
    public Workspace(String name, String owner) {
        this(); // Call default constructor
        this.id = "ws_" + UUID.randomUUID().toString(); // Generate unique ID
        this.name = name;
        this.owner = owner;
        this.members.add(owner); // Owner is initially the only member
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public List<String> getMembers() {
        if (this.members == null) {
            this.members = new ArrayList<>();
        }
        return members;
    }

    public List<String> getRootPageIds() {
        if (this.rootPageIds == null) {
            this.rootPageIds = new ArrayList<>();
        }
        return rootPageIds;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
        // Note: Typically, you don't update 'lastUpdated' when just setting the ID during object creation/retrieval.
    }

    public void setName(String name) {
        this.name = name;
        this.lastUpdated = new Date();
    }

    public void setMembers(List<String> members) {
        this.members = members;
        this.lastUpdated = new Date();
    }

    public void setRootPageIds(List<String> rootPageIds) {
        this.rootPageIds = rootPageIds;
        this.lastUpdated = new Date();
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // Business logic methods (optional, could also be in service)
    public void addMember(String userId) {
        if (this.members == null) {
            this.members = new ArrayList<>();
        }
        if (!this.members.contains(userId)) {
            this.members.add(userId);
            this.lastUpdated = new Date();
        }
    }

    public void removeMember(String userId) {
        if (this.members != null && !this.owner.equals(userId)) { // Prevent removing the owner
            if (this.members.remove(userId)) {
                this.lastUpdated = new Date();
            }
        }
    }

    public void addRootPage(String pageId) {
        if (this.rootPageIds == null) {
            this.rootPageIds = new ArrayList<>();
        }
        if (!this.rootPageIds.contains(pageId)) {
            this.rootPageIds.add(pageId);
            this.lastUpdated = new Date();
        }
    }

    public void removeRootPage(String pageId) {
        if (this.rootPageIds != null) {
            if (this.rootPageIds.remove(pageId)) {
                this.lastUpdated = new Date();
            }
        }
    }
}
