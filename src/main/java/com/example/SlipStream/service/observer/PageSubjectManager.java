package com.example.SlipStream.service.observer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Manages instances of ConcretePageSubject for different pages.
 */
@Component
public class PageSubjectManager {

    private static final Logger logger = LoggerFactory.getLogger(PageSubjectManager.class);
    private final Map<String, ConcretePageSubject> subjects = new ConcurrentHashMap<>();
    private final WebSocketPageObserver webSocketObserver; // The single observer instance

    @Autowired
    public PageSubjectManager(WebSocketPageObserver webSocketObserver) {
        this.webSocketObserver = webSocketObserver;
        logger.info("PageSubjectManager initialized.");
    }

    /**
     * Gets or creates the PageSubject for a given pageId.
     * Attaches the standard WebSocket observer if the subject is newly created.
     *
     * @param pageId The ID of the page.
     * @return The ConcretePageSubject for the page.
     */
    public ConcretePageSubject getSubject(String pageId) {
        return subjects.computeIfAbsent(pageId, id -> {
            ConcretePageSubject newSubject = new ConcretePageSubject(id);
            // Automatically attach the WebSocket observer to every subject
            newSubject.attach(webSocketObserver);
            logger.info("Created and attached WebSocket observer to new subject for page {}", id);
            return newSubject;
        });
    }

    /**
     * Removes the subject for a given pageId if it exists and has no observers.
     * (Optional: Could be called during cleanup or based on activity)
     * @param pageId The ID of the page.
     */
    public void removeSubjectIfUnused(String pageId) {
        subjects.computeIfPresent(pageId, (id, subject) -> {
            if (!subject.hasObservers()) {
                logger.info("Removing unused subject for page {}", id);
                return null; // Remove from map
            }
            return subject; // Keep in map
        });
    }
}
