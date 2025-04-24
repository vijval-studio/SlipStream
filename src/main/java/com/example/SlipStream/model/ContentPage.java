package com.example.SlipStream.model;

import java.util.Collections;
import java.util.List;
import java.util.Date;


/**
 * Content page is a leaf node in the composite pattern.
 * It represents a basic page with content but no children.
 */
public class ContentPage extends PageComponent {
    private String content;

    // Default constructor for Firebase
    public ContentPage() {
        super();
    }

    // Constructor with fields
    public ContentPage(String title, String content, String parentPageId, String owner) {
        super(title, owner, parentPageId); // Call super constructor
        this.content = content;
    }

    @Override
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.lastUpdated = new Date(); // Update timestamp when content changes
    }

    @Override
    public List<PageComponent> getChildren() {
        return Collections.emptyList(); // Leaf node has no children
    }

    @Override
    public void addChild(PageComponent component) {
        // Cannot add children to a leaf node
        throw new UnsupportedOperationException("Cannot add child to a ContentPage");
    }

    @Override
    public void removeChild(String componentId) {
        // Cannot remove children from a leaf node
        throw new UnsupportedOperationException("Cannot remove child from a ContentPage");
    }

    @Override
    public boolean isLeaf() {
        return true;
    }
}