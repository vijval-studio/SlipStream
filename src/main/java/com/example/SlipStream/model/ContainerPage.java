package com.example.SlipStream.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ContainerPage extends PageComponent {
    private List<String> childrenIds; // Store only IDs of child pages
    private String summary;
    // Transient field - not stored in database, used for in-memory operations only
    private transient List<PageComponent> loadedChildren;

    // Default constructor for Firebase
    public ContainerPage() {
        super();
        this.childrenIds = new ArrayList<>();
        this.loadedChildren = new ArrayList<>();
    }

    // Constructor with fields
    public ContainerPage(String title, String summary, String parentPageId, String owner) {
        super(title, owner, parentPageId);
        this.summary = summary;
        this.childrenIds = new ArrayList<>();
        this.loadedChildren = new ArrayList<>();
    }

    @Override
    public String getContent() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
        this.lastUpdated = new Date();
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getChildrenIds() {
        if (this.childrenIds == null) {
            this.childrenIds = new ArrayList<>();
        }
        return childrenIds;
    }

    public void setChildrenIds(List<String> childrenIds) {
        this.childrenIds = childrenIds;
    }

    /**
     * This returns the in-memory loaded children, not for persistence
     */
    @Override
    public List<PageComponent> getChildren() {
        if (this.loadedChildren == null) {
            this.loadedChildren = new ArrayList<>();
        }
        return loadedChildren;
    }

    /**
     * This method is used to load children from the repository
     * It populates the in-memory loadedChildren list with the actual PageComponent objects
     * This is NOT for persistence to the database
     */
    public void setLoadedChildren(List<PageComponent> children) {
        if (this.loadedChildren == null) {
            this.loadedChildren = new ArrayList<>();
        } else {
            this.loadedChildren.clear();
        }
        
        if (children != null) {
            this.loadedChildren.addAll(children);
        }
    }

    /**
     * Adds a child page to this container - only stores the ID for persistence
     * Also adds to in-memory loadedChildren if available
     */
    @Override
    public void addChild(PageComponent component) {
        if (component != null && component.getPageId() != null) {
            // Add to the ID list for persistence
            if (this.childrenIds == null) {
                this.childrenIds = new ArrayList<>();
            }
            if (!this.childrenIds.contains(component.getPageId())) {
                this.childrenIds.add(component.getPageId());
            }
            
            // Add to in-memory list for operations (not for persistence)
            if (this.loadedChildren == null) {
                this.loadedChildren = new ArrayList<>();
            }
            // Check if the child is already in the list
            boolean exists = false;
            for (PageComponent existingChild : loadedChildren) {
                if (existingChild.getPageId().equals(component.getPageId())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                this.loadedChildren.add(component);
            }
            
            this.lastUpdated = new Date();
        }
    }

    /**
     * Removes a child page from this container
     */
    @Override
    public void removeChild(String componentId) {
        if (this.childrenIds != null) {
            this.childrenIds.remove(componentId);
        }
        
        if (this.loadedChildren != null) {
            this.loadedChildren.removeIf(child -> child.getPageId().equals(componentId));
        }
        
        this.lastUpdated = new Date();
    }

    @Override
    public boolean isLeaf() {
        return false;
    }
}