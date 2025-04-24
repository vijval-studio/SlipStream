package com.example.SlipStream.service.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.SlipStream.model.PageComponent;

/**
 * Concrete implementation of PageSubject representing a specific page.
 */
public class ConcretePageSubject implements PageSubject {

    private static final Logger logger = LoggerFactory.getLogger(ConcretePageSubject.class);
    private final String pageId;
    private final List<PageObserver> observers = new CopyOnWriteArrayList<>(); // Thread-safe list

    public ConcretePageSubject(String pageId) {
        this.pageId = pageId;
        logger.debug("Created ConcretePageSubject for pageId: {}", pageId);
    }

    @Override
    public void attach(PageObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
            logger.debug("Attached observer {} to page {}", observer.getClass().getSimpleName(), pageId);
        }
    }

    @Override
    public void detach(PageObserver observer) {
        observers.remove(observer);
        logger.debug("Detached observer {} from page {}", observer.getClass().getSimpleName(), pageId);
    }

    @Override
    public void notifyObservers(PageComponent page) {
        if (!page.getPageId().equals(this.pageId)) {
            logger.warn("Attempted to notify observers for page {} with data from page {}. Aborting.", this.pageId, page.getPageId());
            return;
        }
        logger.debug("Notifying {} observers for page {}", observers.size(), pageId);
        for (PageObserver observer : observers) {
            try {
                observer.update(page);
            } catch (Exception e) {
                logger.error("Error notifying observer {} for page {}: {}", observer.getClass().getSimpleName(), pageId, e.getMessage(), e);
                // Decide if the observer should be detached on error
                // detach(observer);
            }
        }
    }

    public boolean hasObservers() {
        return !observers.isEmpty();
    }

    public String getPageId() {
        return pageId;
    }
}
